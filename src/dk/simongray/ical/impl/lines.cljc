(ns ^:no-doc dk.simongray.ical.impl.lines
  "Content lines, by RFC 5545, section 3.1: unfolding and folding, and a
  line read into its name, its parameters and the text of its value, with
  the parameter values encoded by RFC 6868.

  A read line is a map of :name, a lower-case keyword, :params, a map of
  lower-case keyword to a string or a vector of strings (when a parameter
  has several values), and :text, the value as written."
  (:refer-clojure :exclude [read-line])
  (:require [clojure.string :as str]))

;; RFC 5545, section 3.1: a line break followed by a space or a tab is a
;; fold. Bare LF and CR are taken as line breaks too, since files use them.
(defn lines
  "The lines of the unfolded text `s`, each a pair of its number (from 1)
  and its text, without the empty ones. A line's number is that of its
  first line in `s` before unfolding."
  [s]
  (let [physical (str/split s #"\r\n|\n|\r" -1)]
    (loop [i 0, number 1, acc (transient [])]
      (if (< i (count physical))
        (let [line (nth physical i)
              ;; the folded lines that follow this one
              folds (count (take-while #(re-find #"^[ \t]" %)
                                       (subvec physical (inc i))))
              text (apply str line (map #(subs % 1)
                                        (subvec physical (inc i) (+ i 1 folds))))]
          (recur (+ i 1 folds)
                 (+ number 1 folds)
                 (cond-> acc (seq text) (conj! [number text]))))
        (persistent! acc)))))

;; RFC 6868, section 3
(defn decode-param
  "The parameter value `s` with ^n, ^^ and ^' decoded. A ^ before any
  other character stays as it is."
  [s]
  (if (str/includes? s "^")
    (str/replace s #"\^([n^'])" (fn [[_ c]]
                                  (case c
                                    "n" "\n"
                                    "^" "^"
                                    "'" "\"")))
    s))

;; RFC 6868, section 3
(defn encode-param
  "The parameter value `s` with ^, newlines and double quotes encoded."
  [s]
  (if (re-find #"[\^\n\r\"]" s)
    (str/replace s #"\^|\r\n|\n|\r|\"" (fn [m]
                                       (case m
                                         "^"  "^^"
                                         "\"" "^'"
                                         "^n")))
    s))

(defn- scan-param-value
  "The end in `line` of the parameter value that starts at `i`: after its
  closing quote when it's quoted, or else at the next comma, semicolon or
  colon."
  [line i]
  (if (= \" (get line i))
    (let [close (str/index-of line "\"" (inc i))]
      (if close (inc close) (count line)))
    (loop [j i]
      (if (and (< j (count line)) (not (#{\, \; \:} (get line j))))
        (recur (inc j))
        j))))

(defn- unquote-value
  "The parameter value `s` without its quotes, decoded by RFC 6868."
  [s]
  (decode-param (if (and (str/starts-with? s "\"") (str/ends-with? s "\"")
                         (< 1 (count s)))
                  (subs s 1 (dec (count s)))
                  (str/replace s "\"" ""))))

(defn- delimiter
  "The index of the first of the characters `cs` in `line` from `i`, or the
  length of `line`."
  [line cs i]
  (loop [j i]
    (if (or (>= j (count line)) (contains? cs (get line j)))
      j
      (recur (inc j)))))

(defn- scan-param
  "The parameter that starts at `i` in `line`, as a vector of its name, its
  values and the index after it. A parameter without = has no values."
  [line i]
  (let [eq   (delimiter line #{\= \; \:} i)
        name (subs line i eq)]
    (if (not= \= (get line eq))
      [name nil eq]
      (loop [j (inc eq), values []]
        (let [end    (scan-param-value line j)
              values (conj values (unquote-value (subs line j end)))]
          (if (= \, (get line end))
            (recur (inc end) values)
            [name values end]))))))

(defn- add-param
  "The `params` with the values `vs` of the parameter `k` added, a string
  when there's one and a vector when there are several."
  [params k vs]
  (let [vs (into (let [v (get params k)]
                   (cond (nil? v) [] (string? v) [v] :else v))
                 vs)]
    (assoc params k (if (= 1 (count vs)) (first vs) vs))))

(defn ->keyword
  "The name `s` of a component, a property or a parameter as a lower-case
  keyword."
  [s]
  (keyword (str/lower-case (str/trim s))))

;; RFC 5545, section 3.1: contentline = name *(";" param) ":" value, where
;; a name is an iana-token or an x-name, of letters, digits and dashes.
;; vCard (RFC 6350, section 3.3) puts a group and a dot before it.
(defn read-line
  "The content line `line` as a map of :name, :params and :text, or nil
  when it isn't one: its name isn't a name, or no colon follows its name
  and parameters. A parameter without a value is left out."
  [line]
  (let [end (delimiter line #{\; \:} 0)]
    (when (re-matches #"(?:[A-Za-z0-9-]+\.)?[A-Za-z0-9-]+" (subs line 0 end))
      (loop [i end, params {}]
        (case (get line i)
          \: {:name   (->keyword (subs line 0 end))
              :params params
              :text   (subs line (inc i))}
          \; (let [[pname values j] (scan-param line (inc i))]
               (if (or (str/blank? pname) (nil? values))
                 (recur j params)
                 (recur j (add-param params (->keyword pname) values))))
          nil)))))

;; RFC 5545, section 3.2: a value with a colon, a semicolon or a comma is
;; quoted
(defn- write-param-value
  "The parameter value `s` as written, encoded and quoted when it needs to
  be."
  [s]
  (let [s (encode-param (str s))]
    (if (re-find #"[:;,]" s) (str "\"" s "\"") s)))

(defn write-params
  "The text of the parameters `params`, each after a semicolon."
  [params]
  (apply str (for [[k v] params]
               (str ";" (str/upper-case (name k)) "="
                    (if (sequential? v)
                      (str/join "," (map write-param-value v))
                      (write-param-value v))))))

(defn- utf-8-length
  "The number of octets that the UTF-16 code unit `c` takes in UTF-8, with
  each half of a surrogate pair taking two."
  ^long [c]
  (let [n #?(:clj (int c) :cljs (.charCodeAt c 0))]
    (cond
      (< n 0x80)                    1
      (< n 0x800)                   2
      (<= 0xD800 n 0xDFFF)          2
      :else                         3)))

(defn- high-surrogate?
  "Whether the UTF-16 code unit `c` is the first half of a surrogate pair."
  [c]
  (<= 0xD800 #?(:clj (int c) :cljs (.charCodeAt c 0)) 0xDBFF))

(defn- fold-point
  "The index in `line` at which to break it, from `start`, so that the
  part takes at most `octets` octets of UTF-8. The break never splits a
  surrogate pair or a backslash escape."
  [line start octets]
  (loop [i start, used 0]
    (if (>= i (count line))
      i
      (let [c    (get line i)
            pair (and (high-surrogate? c) (< (inc i) (count line)))
            n    (if pair 4 (utf-8-length c))
            next (+ i (if pair 2 1))]
        (cond
          (> (+ used n) octets) i
          ;; keep a backslash with the character it escapes
          (and (= \\ c) (< next (count line))
               (> (+ used n (utf-8-length (get line next))) octets)) i
          :else (recur next (+ used n)))))))

;; RFC 5545, section 3.1: lines are at most 75 octets, without the line
;; break, and a continuation line starts with a space
(defn fold
  "The content line `line` folded into lines of at most 75 octets, each
  ending with CRLF."
  [line]
  (loop [start 0, octets 75, acc (transient [])]
    (let [end (fold-point line start octets)]
      (if (>= end (count line))
        (str (str/join "\r\n " (persistent! (conj! acc (subs line start))))
             "\r\n")
        (recur end 74 (conj! acc (subs line start end)))))))

(defn write-line
  "The content line of the property named `k` with the `params` and the
  value text `text`, folded."
  [k params text]
  (fold (str (str/upper-case (name k)) (write-params params) ":" text)))

(comment
  (lines (str "BEGIN:VEVENT\r\nSUMMARY:A long\r\n  summary\r\n"
              "END:VEVENT\r\n"))
  (read-line "ATTENDEE;CN=\"Gray, Simon\";PARTSTAT=ACCEPTED:mailto:s@x.dk")
  (read-line "X-A;X-B=a,\"b,c\";X-C=^'q^':v")
  (fold (apply str "DESCRIPTION:" (repeat 100 "ø")))
  #_.)
