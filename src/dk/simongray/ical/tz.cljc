(ns dk.simongray.ical.tz
  "Find the zone that a TZID names, and convert between its local
  date-times and instants.

  A zone comes from the platform's time zone database (ZoneRules on the
  JVM, Intl.DateTimeFormat in JavaScript), from a VTIMEZONE in a calendar,
  or from a fixed UTC offset. The two platforms agree as long as their
  copies of the database are the same version.

  Local date-times are strings in the form of jCal, e.g.
  \"2026-10-12T19:00:00\", and instants are insts."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [dk.simongray.ical.impl.civil :as civil]
            [dk.simongray.ical.impl.locale :as locale]
            [dk.simongray.ical.impl.windows :as windows]
            [dk.simongray.ical.impl.zones :as zones]))

(defn- platform-zone
  "The platform's zone named `s`, as the zone of the TZID `tzid`, or nil."
  [tzid s]
  (when-let [iana (zones/platform-name s)]
    {:tzid tzid :iana iana}))

;; RFC 5545, section 3.2.19: a TZID that starts with a solidus is meant to
;; be globally unique, e.g. /mozilla.org/20050126_1/Europe/Berlin, and
;; clients put a prefix of their own before the IANA name
(defn- suffixes
  "The ends of the TZID `tzid` after each of its solidi, longest first,
  when it starts with one."
  [tzid]
  (when (str/starts-with? tzid "/")
    (->> (range (count tzid))
         (filter #(= \/ (get tzid %)))
         (map #(subs tzid (inc %)))
         (remove str/blank?))))

;; RFC 5545, section 3.3.14: hours from 00 to 23, minutes and seconds
;; from 00 to 59
(defn- fixed-zone
  "The zone of the fixed UTC offset `s`, e.g. +02:00 or Z, or nil."
  [s]
  (cond
    (= "Z" s)
    {:tzid s :offset 0}

    (re-matches #"[+-](?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?" s)
    {:tzid s :offset (civil/offset-seconds s)}))

(def ^:private last-vtimezones
  "The calendar whose VTIMEZONEs were looked up last, with them by TZID."
  (atom nil))

;; The times of a calendar are resolved one at a time, often by a caller
;; that goes through its events, so the VTIMEZONEs of the last calendar
;; are kept by TZID
(defn- vtimezones
  "The VTIMEZONEs of the calendar `cal` by TZID, the first of each."
  [cal]
  (let [[c index] @last-vtimezones]
    (if (identical? c cal)
      index
      (let [index (reduce (fn [m z]
                            (let [tzid (some #(when (= :tzid (:name %)) (:value %)) (:properties z))]
                              (cond-> m (and tzid (not (contains? m tzid))) (assoc tzid z))))
                          {}
                          (filter #(= :vtimezone (:name %)) (:components cal)))]
        (reset! last-vtimezones [cal index])
        index))))

(defn- calendar-zone
  "The zone of the VTIMEZONE of the calendar `cal` with the TZID `tzid`,
  or nil."
  [cal tzid]
  (some-> (get (vtimezones cal) tzid) zones/vtimezone-zone))

(defn zone
  "The zone that the TZID `tzid` names, or nil when nothing defines it.
  Given the calendar `cal`, its VTIMEZONEs can define the zone too.

  The function tries `tzid`, in order:

  1. as a zone name of the platform's database, ignoring case
  2. without its prefix, when it starts with a solidus, e.g.
     /mozilla.org/20050126_1/Europe/Berlin
  3. as a Windows zone name, e.g. W. Europe Standard Time, mapped by
     CLDR's windowsZones
  4. as the TZID of a VTIMEZONE in `cal`

  The platform comes first because a calendar's own definition is often
  stale or covers only a few years. To prefer the calendar's, pass
  :vtimezone? true in `opts`. A fixed UTC offset such as +02:00 is a zone
  too."
  ([tzid]
   (zone nil tzid {}))
  ([cal tzid]
   (zone cal tzid {}))
  ([cal tzid {:keys [vtimezone?]}]
   (when (string? tzid)
     (let [platform #(or (platform-zone tzid tzid)
                         (some (partial platform-zone tzid) (suffixes tzid))
                         (some->> (get windows/zones (locale/lower-case tzid))
                                  (platform-zone tzid))
                         (fixed-zone tzid))]
       (if vtimezone?
         (or (calendar-zone cal tzid) (platform))
         (or (platform) (calendar-zone cal tzid)))))))

(defn- ->inst
  "The inst of `t` seconds from the epoch."
  [t]
  #?(:clj (java.util.Date. (long (* 1000 t))) :cljs (js/Date. (* 1000 t))))

(defn- seconds-of
  "The whole seconds from the epoch to the inst `at`."
  [at]
  (civil/floor-div (inst-ms at) 1000))

(defn offset
  "The UTC offset of the `zone` at the inst `at`, e.g. \"+02:00\"."
  [zone at]
  (civil/offset-text (zones/offset zone (seconds-of at))))

;; RFC 5545, section 3.3.5
(defn instant
  "The inst of the local date-time `s` in the `zone`. When the clocks go
  back, a time that occurs twice means the first of them. When they go
  forward, a time that doesn't occur takes the offset from before.

      (instant (zone \"America/New_York\") \"2007-03-11T02:30:00\")
      ;; => #inst \"2007-03-11T07:30:00.000-00:00\" (3:30 EDT)"
  [zone s]
  (->inst (zones/instant zone (civil/seconds s))))

(defn local
  "The local date-time of the inst `at` in the `zone`, e.g.
  \"2026-10-12T19:00:00\"."
  [zone at]
  (civil/date-time-text (zones/local zone (seconds-of at)) false))

(def ^:private weekday-codes
  "The weekday codes of RFC 5545, from Sunday."
  ["SU" "MO" "TU" "WE" "TH" "FR" "SA"])

(defn- onset
  "The local time when the transition `tr` happens, on the clock before
  it."
  [[t from _]]
  (+ t from))

;; The yearly rules of the time zone database put a transition on the nth
;; or the last weekday of a month, or on the first weekday on or after a
;; day, e.g. Fri>=23 in Asia/Jerusalem
(defn- forms
  "The forms of yearly rule that the day `d` of a month of `length` days
  fits, for its weekday: [:nth n] for n up to 4, [:last], and [:from day]
  for each day on or after which it's the first of its weekday."
  [d length]
  (set (concat (when (<= d 28) [[:nth (inc (quot (dec d) 7))]])
               (when (> (+ d 7) length) [[:last]])
               (for [from (range (max 1 (- d 6)) (inc (min d (- length 6))))]
                 [:from from]))))

(defn- yearly
  "The transition `tr` as a map of its :year, the :key that each
  transition of its yearly rule shares (its month, weekday, time of day
  and offsets), and the :forms of rule that its day fits."
  [[_ from to :as tr]]
  (let [x                 (onset tr)
        [y m d hh mm ss]  (civil/civil-from-seconds x)]
    {:year  y
     :key   [m (civil/weekday (civil/floor-div x 86400)) hh mm ss from to]
     :forms (forms d (civil/days-in-month y m))}))

(defn- run
  "The run of the transitions `trs`, which come a year apart in the
  direction `step` (1 or -1) from the transition `tr`, that follow one
  yearly rule with it, of the forms `fits`: a map of its :trs, from `tr`
  on, its :year, the year of the last, and the :forms that they all fit."
  [tr trs step fits]
  (let [{:keys [year key]} (yearly tr)]
    (reduce (fn [acc t]
              (let [y  (yearly t)
                    fs (set/intersection (:forms acc) (:forms y))]
                (if (and (= (:year y) (+ (:year acc) step)) (= key (:key y)) (seq fs))
                  (assoc acc :trs (conj (:trs acc) t) :year (:year y) :forms fs)
                  (reduced acc))))
            {:trs [tr] :year year :forms fits}
            trs)))

(defn- kind
  "Whether the transition `tr` starts daylight time or standard time, by
  whether the offset goes up."
  [[_ from to]]
  (if (> to from) :daylight :standard))

(defn- observance
  "A STANDARD or DAYLIGHT component of the transitions `trs`, which share
  their offsets: the first one's onset as DTSTART, and the others' as
  RDATEs, or the yearly `rule`."
  [trs rule]
  (let [[_ from to :as first-tr] (first trs)
        local #(civil/date-time-text (onset %) false)]
    {:name       (kind first-tr)
     :properties (cond-> [{:name :dtstart :value (local first-tr)}
                          {:name :tzoffsetfrom :value (civil/offset-text from)}
                          {:name :tzoffsetto :value (civil/offset-text to)}]
                   rule           (conj {:name :rrule :value rule})
                   (and (not rule) (next trs))
                   (conj {:name :rdate :value (mapv local (rest trs))}))}))

(defn- yearly-rule
  "The yearly recurrence rule of a run of transitions of the `key` that
  fit the forms `fits`: the nth weekday rather than the last, and that
  rather than the first on or after a day."
  [[m wd] fits]
  (let [code (weekday-codes wd)
        n    (some #(when (= :nth (first %)) (second %)) (sort fits))
        day  (some->> (seq (keep #(when (= :from (first %)) (second %)) fits)) (apply max))]
    (cond
      n               {:freq "YEARLY" :bymonth [m] :byday [(str n code)]}
      (fits [:last])  {:freq "YEARLY" :bymonth [m] :byday [(str -1 code)]}
      :else           {:freq "YEARLY" :bymonth [m] :byday [code]
                       :bymonthday (vec (range day (+ day 7)))})))

;; The runs of transitions at the end of the span become RRULEs. The
;; transitions of the 30 years after it tell which form a rule has, and
;; whether the zone still follows it; when it doesn't, the RRULEs end with
;; their last transition.
(defn- observances
  "The STANDARD and DAYLIGHT components of the transitions `trs`, up to
  the instant `end`, and those after it up to the end of `last-year`:
  the yearly rules that the zone follows at the end of the span, for both
  kinds, and the other transitions grouped by their offsets."
  [trs end last-year]
  (let [runs    (for [k     [:standard :daylight]
                      :let  [[in after] (split-with #(< (first %) end) (filter #(= k (kind %)) trs))]
                      :when (seq in)
                      :let  [back    (run (last in) (reverse (butlast in)) -1
                                          (:forms (yearly (last in))))
                             forward (run (last in) after 1 (:forms back))]
                      :when (< 1 (count (:trs back)))]
                  {:trs     (vec (reverse (:trs back)))
                   :forms   (:forms forward)
                   :last    (peek (:trs forward))
                   :open?   (and (= (count (:trs forward)) (inc (count after)))
                                 (= last-year (:year forward)))})
        ruled?  (= 2 (count runs))
        open?   (every? :open? runs)
        ruled   (if ruled? (set (mapcat :trs runs)) #{})
        earlier (remove ruled (take-while #(< (first %) end) trs))]
    (concat (for [[_ group] (sort-by (comp first first second)
                                     (group-by (fn [[_ from to]] [from to]) earlier))]
              (observance group nil))
            (when ruled?
              (for [{:keys [trs forms last]} (sort-by (comp first first :trs) runs)]
                (observance trs (cond-> (yearly-rule (:key (yearly (first trs))) forms)
                                  (not open?) (assoc :until (civil/date-time-text (first last) true)))))))))

(defn vtimezone
  "A VTIMEZONE with the TZID `tzid` for the platform's `zone`, covering
  the years `from` to `to`. If the zone follows yearly rules at the end of
  that span, they become RRULEs, so the definition goes on after it, for
  as long as the platform knows that the zone follows them.

      (vtimezone (zone \"Europe/Copenhagen\") \"Europe/Copenhagen\" 2026 2027)"
  [zone tzid from to]
  (let [start (* 86400 (civil/days-from-civil (dec from) 1 1))
        end   (* 86400 (civil/days-from-civil (+ to 2) 1 1))
        ahead (* 86400 (civil/days-from-civil (+ to 32) 1 1))
        trs   (zones/transitions zone start ahead)
        off   (zones/offset zone start)]
    {:name       :vtimezone
     :properties [{:name :tzid :value tzid}]
     :components (vec (if (some #(< (first %) end) trs)
                        (observances trs end (+ to 31))
                        [{:name       :standard
                          :properties [{:name :dtstart :value "1970-01-01T00:00:00"}
                                       {:name :tzoffsetfrom :value (civil/offset-text off)}
                                       {:name :tzoffsetto :value (civil/offset-text off)}]}]))}))

(defn- tzid-years
  "The years of the date and date-time values of the properties with each
  TZID in the component `c` and the components inside it, as a map of TZID
  to a set of years."
  [c]
  (apply merge-with into
         (concat
          (for [p     (:properties c)
                :let  [tzid (get-in p [:params :tzid])]
                :when tzid
                v     (let [v (:value p)] (if (sequential? v) v [v]))
                :let  [v (if (map? v) (:start v) v)]
                :when (and (string? v) (re-matches #"\d{4}-.*" v))]
            {tzid #{#?(:clj (Long/parseLong (subs v 0 4)) :cljs (js/parseInt (subs v 0 4) 10))}})
          (map tzid-years (:components c)))))

;; RFC 5545, section 3.2.19: each TZID of a calendar needs a VTIMEZONE in
;; it
(defn with-timezones
  "The calendar `cal` with a VTIMEZONE added at the front for each TZID
  that it uses without defining, when the platform knows the zone (see
  vtimezone). The TZIDs that `cal` defines, and those that name no known
  zone, stay as they are."
  [cal]
  (let [defined (set (for [c     (:components cal)
                           :when (= :vtimezone (:name c))
                           p     (:properties c)
                           :when (= :tzid (:name p))]
                       (:value p)))
        added   (for [[tzid years] (sort (tzid-years cal))
                      :when        (not (defined tzid))
                      :let         [z (zone tzid)]
                      :when        (:iana z)]
                  (vtimezone z tzid (apply min years) (apply max years)))]
    (cond-> cal
      (seq added) (update :components #(into (vec added) %)))))

(comment
  (instant (zone "Europe/Copenhagen") "2026-10-12T19:00:00")
  (instant (zone "W. Europe Standard Time") "2026-10-12T19:00:00")
  (local (zone "/mozilla.org/20050126_1/Europe/Berlin") #inst "2026-10-12T17:00:00Z")
  (offset (zone "+02:00") #inst "2026-01-01")
  #_.)
