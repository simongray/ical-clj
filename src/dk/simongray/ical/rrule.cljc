(ns dk.simongray.ical.rrule
  "Recurrence rules (RFC 5545, section 3.3.10) on their own: read,
  written and expanded.

  A rule is a map of its parts by lower-case keyword, as a property's
  value holds it, e.g. {:freq \"MONTHLY\" :byday [\"2MO\"]}. FREQ, WKST
  and BYDAY are upper-case text, UNTIL a date or a date-time in the form
  of jCal, COUNT and INTERVAL numbers, and the other BYxxx parts vectors of
  numbers."
  (:require [clojure.string :as str]
            [dk.simongray.ical.impl.civil :as civil]
            [dk.simongray.ical.impl.expansion :as expansion]
            [dk.simongray.ical.impl.values :as values]
            [dk.simongray.ical.impl.zones :as zones]))

(defn parse
  "The recurrence rule written as `s`, or nil when it isn't one: it has no
  FREQ, or a part that doesn't read or comes twice.

      (parse \"FREQ=WEEKLY;BYDAY=MO,WE\")
      ;; => {:freq \"WEEKLY\" :byday [\"MO\" \"WE\"]}"
  [s]
  (let [rule (values/read-recur (str/replace-first (str s) #"(?i)^RRULE:" ""))]
    (when (map? rule) rule)))

(defn serialize
  "The text of the recurrence `rule`, with FREQ first."
  [rule]
  (values/write-recur rule))

;; RFC 7529, section 3: a rule in another calendar than the Gregorian one
;; needs that calendar, and SKIP needs RSCALE
(defn expandable?
  "Whether the library can expand the recurrence `rule`: it has no RSCALE
  other than GREGORIAN, and no SKIP other than OMIT."
  [rule]
  (and (map? rule)
       (contains? values/freqs (:freq rule))
       (contains? #{nil "GREGORIAN"} (:rscale rule))
       (contains? #{nil "OMIT"} (:skip rule))))

(defn- date?
  "Whether `s` is a date rather than a date-time."
  [s]
  (not (str/includes? s "T")))

;; RFC 5545, section 3.3.10: UNTIL is in UTC when DTSTART has a zone or is
;; in UTC, and floating when DTSTART is (erratum 4414). A date as UNTIL
;; for a date-time DTSTART, which some clients write, ends with its day.
(defn- past-until
  "A function that tells whether a local time of the rule from `start` in
  the `zone` is past the rule's `until`, or nil without one."
  [until start zone]
  (when until
    (let [utc?  (str/ends-with? until "Z")
          limit (cond-> (civil/seconds until)
                  (and (date? until) (not (date? start))) (+ 86399))]
      (cond
        (and utc? zone)                        #(> (zones/instant zone %) limit)
        :else                                  #(> % limit)))))

(defn occurrences
  "The recurrence instances that the `rule` makes from `start`, a date or a
  date-time in the form of jCal, as a lazy sequence of the same form, in
  order. It's nil when the rule can't be expanded (see expandable?).

  `start` is always the first instance, as section 3.8.5.3 says, even when
  the rule wouldn't make it, and COUNT counts it. The `zone` of a local
  `start` (as tz/zone gives it) is needed only to compare it with an UNTIL
  in UTC. Without one, a local `start` is compared as if it were in UTC.

      (take 3 (occurrences {:freq \"MONTHLY\" :byday [\"-1FR\"]}
                           \"2026-01-30T18:00:00\"))
      ;; => (\"2026-01-30T18:00:00\" \"2026-02-27T18:00:00\"
      ;;     \"2026-03-27T18:00:00\")"
  ([rule start]
   (occurrences rule start nil))
  ([rule start zone]
   (when (expandable? rule)
     (let [date? (date? start)
           utc?  (str/ends-with? start "Z")
           text  (if date? civil/date-text #(civil/date-time-text % utc?))]
       (map text (expansion/rule-times rule (civil/seconds start) date?
                                       (past-until (:until rule) start zone)))))))

(comment
  (take 5 (occurrences (parse "FREQ=MONTHLY;BYDAY=TU,WE,TH;BYSETPOS=3")
                       "1997-09-04T09:00:00"))
  (take 5 (occurrences (parse "FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30")
                       "2026-01-01"))
  #_.)
