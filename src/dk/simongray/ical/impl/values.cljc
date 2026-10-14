(ns ^:no-doc dk.simongray.ical.impl.values
  "The value types of RFC 5545, section 3.3, parsed from their text into the
  forms of jCal (RFC 7265, section 3.6) and written back.

  A date is \"2026-10-12\", a date-time \"2026-10-12T19:00:00\" (with a Z
  in UTC), a time \"19:00:00\", a UTC offset \"+02:00\", a duration
  \"PT2H\", a period a map of :start and :end or :duration, and a
  recurrence rule a map of its parts by lower-case keyword. Text is
  unescaped, integers and floats are numbers and booleans are booleans.
  The other types keep their text.

  Each reader returns ::invalid for text that isn't of its type."
  (:require [clojure.string :as str]
            [dk.simongray.ical.impl.civil :as civil]))

(defn- digits
  "The number in the string of digits `s`."
  [s]
  #?(:clj (Long/parseLong s) :cljs (js/parseInt s 10)))

;; RFC 5545, section 3.3.4
(defn read-date
  "The date in the text `s`, e.g. 20261012, as 2026-10-12."
  [s]
  (if-let [[_ y m d] (re-matches #"(\d{4})(\d{2})(\d{2})" s)]
    (if (civil/valid-date? (digits y) (digits m) (digits d))
      (str y "-" m "-" d)
      ::invalid)
    ::invalid))

(defn write-date
  "The text of the date `v`."
  [v]
  (str/replace v "-" ""))

;; RFC 5545, section 3.3.12
(defn read-time
  "The time in the text `s`, e.g. 190000 or 170000Z, as 19:00:00 or
  17:00:00Z."
  [s]
  (if-let [[_ hh mm ss z] (re-matches #"(\d{2})(\d{2})(\d{2})([Zz]?)" s)]
    (if (civil/valid-time? (digits hh) (digits mm) (digits ss))
      (str hh ":" mm ":" ss (str/upper-case z))
      ::invalid)
    ::invalid))

(defn write-time
  "The text of the time `v`."
  [v]
  (str/replace v ":" ""))

;; RFC 5545, section 3.3.5
(defn read-date-time
  "The date-time in the text `s`, e.g. 20261012T190000, as
  2026-10-12T19:00:00."
  [s]
  (let [[date time] (str/split s #"[Tt]" 2)]
    (if (nil? time)
      ::invalid
      (let [date (read-date date)
            time (read-time time)]
        (if (or (= ::invalid date) (= ::invalid time))
          ::invalid
          (str date "T" time))))))

(defn write-date-time
  "The text of the date-time `v`."
  [v]
  (str/replace v #"[-:]" ""))

;; RFC 5545, section 3.3.14
(defn read-utc-offset
  "The UTC offset in the text `s`, e.g. +0200, as +02:00."
  [s]
  (if-let [[_ sign hh mm ss] (re-matches #"([+-])(\d{2})(\d{2})(\d{2})?" s)]
    (if (and (<= (digits hh) 23) (<= (digits mm) 59)
             (or (nil? ss) (<= (digits ss) 59)))
      (str sign hh ":" mm (when ss (str ":" ss)))
      ::invalid)
    ::invalid))

(defn write-utc-offset
  "The text of the UTC offset `v`."
  [v]
  (str/replace v ":" ""))

;; RFC 5545, section 3.3.6: dur-value = (["+"] / "-") "P" (dur-date /
;; dur-time / dur-week), where the time's parts can't skip one in between
(def ^:private duration-pattern
  #"[+-]?P(?:\d+W|(?:\d+D)?(?:T(?:\d+H(?:\d+M(?:\d+S)?)?|\d+M(?:\d+S)?|\d+S))?)")

(defn read-duration
  "The duration in the text `s`, e.g. PT2H, in upper case."
  [s]
  (let [s (str/upper-case s)]
    (if (and (re-matches duration-pattern s) (not (re-matches #"[+-]?P" s)))
      s
      ::invalid)))

;; RFC 5545, section 3.3.9
(defn read-period
  "The period in the text `s`, e.g. 19970101T180000Z/PT5H30M, as a map of
  :start and :end or :duration."
  [s]
  (let [[start end] (str/split s #"/" 2)
        start      (read-date-time start)]
    (cond
      (or (nil? end) (= ::invalid start)) ::invalid

      (re-find #"^[+-]?P" end)
      (let [d (read-duration end)]
        (if (= ::invalid d) ::invalid {:start start :duration d}))

      :else
      (let [e (read-date-time end)]
        (if (= ::invalid e) ::invalid {:start start :end e})))))

(defn write-period
  "The text of the period `v`."
  [{:keys [start end duration]}]
  (str (write-date-time start) "/"
       (if end (write-date-time end) duration)))

;; RFC 5545, section 3.3.11
(defn read-text
  "The text value `s`, unescaped. A backslash before any character
  other than those that RFC 5545 escapes is dropped."
  [s]
  (if (str/includes? s "\\")
    (str/replace s #"\\(.)" (fn [[_ c]]
                             (case c
                               ("n" "N") "\n"
                               c)))
    s))

(defn write-text
  "The text `s`, escaped."
  [s]
  (str/replace s #"\\|;|,|\r\n|\n|\r" (fn [m]
                                      (case m
                                        "\\" "\\\\"
                                        ";"  "\\;"
                                        ","  "\\,"
                                        "\\n"))))

;; RFC 5545, section 3.3.8
(defn read-integer
  "The integer in the text `s`."
  [s]
  (if (re-matches #"[+-]?\d{1,18}" s)
    (digits (str/replace s "+" ""))
    ::invalid))

;; RFC 5545, section 3.3.7
(defn read-float
  "The float in the text `s`."
  [s]
  (if (re-matches #"[+-]?\d+(?:\.\d+)?" s)
    #?(:clj (Double/parseDouble s) :cljs (js/parseFloat s))
    ::invalid))

(defn- expand-exponent
  "The number `s`, which the platform wrote with an exponent, e.g. 1.5E-7
  or 1.5e-7, written without it."
  [s]
  (let [[_ sign int-part frac exp] (re-matches #"(-?)(\d+)(?:\.(\d+))?[eE]([+-]?\d+)" s)
        ds    (str int-part frac)
        point (+ (count int-part) (digits (str/replace exp "+" "")))
        zeros #(apply str (repeat % "0"))]
    (str sign
         (cond
           (<= point 0)          (str "0." (zeros (- point)) ds)
           (>= point (count ds)) (str ds (zeros (- point (count ds))))
           :else                 (str (subs ds 0 point) "." (subs ds point))))))

(defn write-float
  "The text of the float `x`, the same on every platform: with no
  exponent, and without a fraction when it's whole."
  [x]
  (let [s (str (double x))
        s (if (re-find #"[eE]" s) (expand-exponent s) s)]
    (str/replace s #"\.0$" "")))

;; RFC 5545, section 3.3.2
(defn read-boolean
  "The boolean in the text `s`."
  [s]
  (case (str/upper-case s)
    "TRUE"  true
    "FALSE" false
    ::invalid))

;; RFC 5545, section 3.3.10, with RSCALE and SKIP of RFC 7529, section 3
(def rule-parts
  "The parts of a recurrence rule, in the order they're written."
  [:rscale :freq :until :count :interval :bysecond :byminute :byhour :byday
   :bymonthday :byyearday :byweekno :bymonth :bysetpos :wkst :skip])

(def freqs
  "The values of FREQ."
  #{"SECONDLY" "MINUTELY" "HOURLY" "DAILY" "WEEKLY" "MONTHLY" "YEARLY"})

(def ^:private weekday-pattern
  "A weekday of BYDAY, perhaps after a signed ordinal."
  #"([+-]?\d{1,2})?(SU|MO|TU|WE|TH|FR|SA)")

(defn- split-trimmed
  "The comma-separated values of `s`, without the spaces around them that
  some clients write."
  [s]
  (mapv str/trim (str/split s #"," -1)))

(defn- read-numbers
  "The comma-separated signed numbers in the text `s`, or ::invalid. Month
  numbers can end with the L of a leap month (RFC 7529), which keeps them
  as text."
  [s leap?]
  (let [xs (split-trimmed s)]
    (if (every? #(re-matches (if leap? #"[+-]?\d{1,3}L?" #"[+-]?\d{1,3}") %) xs)
      (mapv #(if (str/ends-with? % "L") % (digits (str/replace % "+" ""))) xs)
      ::invalid)))

(defn- read-rule-part
  "The value of the rule part `k` in the text `s`, or ::invalid."
  [k s]
  (case k
    :freq     (let [f (str/upper-case s)] (if (freqs f) f ::invalid))
    :until    (let [dt (read-date-time s)]
                (if (= ::invalid dt) (read-date s) dt))
    (:count :interval)
    (if (re-matches #"\d{1,9}" s) (digits s) ::invalid)
    :byday    (let [days (split-trimmed (str/upper-case s))]
                (if (every? #(re-matches weekday-pattern %) days)
                  (mapv #(str/replace % "+" "") days)
                  ::invalid))
    :wkst     (let [d (str/upper-case s)]
                (if (re-matches #"SU|MO|TU|WE|TH|FR|SA" d) d ::invalid))
    (:rscale :skip)
    (str/upper-case s)
    (:bysecond :byminute :byhour :bymonthday :byyearday :byweekno :bysetpos)
    (read-numbers s false)
    :bymonth  (read-numbers s true)
    s))

(defn read-recur
  "The recurrence rule in the text `s`, e.g. FREQ=WEEKLY;BYDAY=MO,WE, as a
  map of its parts by lower-case keyword, e.g. {:freq \"WEEKLY\" :byday
  [\"MO\" \"WE\"]}. It's ::invalid without a FREQ, or with a part that
  doesn't parse or appears twice."
  [s]
  (let [rule (reduce (fn [rule part]
                       (let [[k v] (str/split part #"=" 2)
                             k     (keyword (str/lower-case (str/trim k)))
                             v     (when v (read-rule-part k v))]
                         (if (or (nil? v) (= ::invalid v) (contains? rule k))
                           (reduced ::invalid)
                           (assoc rule k v))))
                     {}
                     (remove str/blank? (str/split s #";")))]
    (if (and (map? rule) (:freq rule)) rule ::invalid)))

(defn- write-rule-part
  "The text of the value `v` of the rule part `k`."
  [k v]
  (cond
    (= :until k)    (if (str/includes? v "T") (write-date-time v) (write-date v))
    (sequential? v) (str/join "," v)
    :else           (str v)))

(defn write-recur
  "The text of the recurrence rule `rule`, with its parts in the order of
  rule-parts and any others last."
  [rule]
  (let [known (set rule-parts)]
    (->> (concat (keep #(when (contains? rule %) [% (get rule %)]) rule-parts)
                 (remove (comp known key) rule))
         (map (fn [[k v]]
                (str (str/upper-case (name k)) "=" (write-rule-part k v))))
         (str/join ";"))))

(defn read-value
  "The value of the type `type` in the text `s`, or ::invalid."
  [type s]
  (case type
    :date       (read-date s)
    :date-time  (read-date-time s)
    :time       (read-time s)
    :utc-offset (read-utc-offset s)
    :duration   (read-duration s)
    :period     (read-period s)
    :recur      (read-recur s)
    :text       (read-text s)
    :integer    (read-integer s)
    :float      (read-float s)
    :boolean    (read-boolean s)
    s))

(defn write-value
  "The text of the value `v` of the type `type`."
  [type v]
  (case type
    :date       (write-date v)
    :date-time  (write-date-time v)
    :time       (write-time v)
    :utc-offset (write-utc-offset v)
    :period     (write-period v)
    :recur      (write-recur v)
    :text       (write-text v)
    :float      (write-float v)
    :boolean    (if v "TRUE" "FALSE")
    (str v)))

(defn split-escaped
  "The parts of the text `s` separated by the character `sep`, where a
  backslash escapes the character after it. The parts keep their escapes."
  [s sep]
  (loop [i 0, start 0, acc (transient [])]
    (cond
      (>= i (count s))       (persistent! (conj! acc (subs s start)))
      (= \\ (get s i))       (recur (+ i 2) start acc)
      (= sep (get s i))      (recur (inc i) (inc i) (conj! acc (subs s start i)))
      :else                  (recur (inc i) start acc))))

(defn split-list
  "The values of the comma-separated list `s` of the type `type`. In text,
  an escaped comma doesn't separate."
  [type s]
  (if (= :text type)
    (split-escaped s \,)
    (str/split s #"," -1)))
