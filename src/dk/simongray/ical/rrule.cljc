(ns dk.simongray.ical.rrule
  "Parse, write and expand recurrence rules (RFC 5545, section 3.3.10) on
  their own.

  A rule is the map that an RRULE property holds as its value, with its
  parts by lower-case keyword, e.g. {:freq \"MONTHLY\" :byday [\"2MO\"]}.
  FREQ, WKST and BYDAY are upper-case strings, and UNTIL is a date or a
  date-time. COUNT and INTERVAL are numbers, and the other BYxxx parts
  are vectors of numbers."
  (:require [clojure.string :as str]
            [dk.simongray.ical.impl.civil :as civil]
            [dk.simongray.ical.impl.expansion :as expansion]
            [dk.simongray.ical.impl.values :as values]
            [dk.simongray.ical.impl.zones :as zones]))

(defn parse
  "The recurrence rule in the text `s`, or nil when it isn't one: when it
  has no FREQ, or a part that doesn't parse or appears twice.

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
  "Whether the library can expand the recurrence `rule`. It can unless the
  rule has an RSCALE other than GREGORIAN, or a SKIP other than OMIT."
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
  "A predicate of whether a local time of a rule from `start` in the `zone`
  is past the rule's `until`, or nil without one."
  [until start zone]
  (when until
    (let [utc?  (str/ends-with? until "Z")
          limit (cond-> (civil/seconds until)
                  (and (date? until) (not (date? start))) (+ 86399))]
      (cond
        (and utc? zone)                        #(> (zones/instant zone %) limit)
        :else                                  #(> % limit)))))

(defn occurrences
  "The instances that the `rule` generates from `start`, a date or a
  date-time, as a lazy sequence of dates or date-times in order. It's nil
  for a rule that the library can't expand (see expandable?).

  The first instance is always `start`, as section 3.8.5.3 says, even
  when the rule wouldn't generate it, and COUNT counts it. Given the
  `zone` of a local `start` (from tz/zone), an instance at a time that
  the clocks skip moves forward by the gap (section 3.3.5), and an UNTIL
  in UTC compares with the instances' instants. Without it, the times
  are as the rule generates them, and compare as if they were in UTC.

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
           text  (if date? civil/date-text #(civil/date-time-text % utc?))
           xs    (expansion/rule-times rule (civil/seconds start) date?
                                       (past-until (:until rule) start zone))]
       (map text (cond->> xs
                   (and zone (not date?) (not utc?)) (zones/wall-times zone)))))))

(comment
  (take 5 (occurrences (parse "FREQ=MONTHLY;BYDAY=TU,WE,TH;BYSETPOS=3")
                       "1997-09-04T09:00:00"))
  (take 5 (occurrences (parse "FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30")
                       "2026-01-01"))
  #_.)
