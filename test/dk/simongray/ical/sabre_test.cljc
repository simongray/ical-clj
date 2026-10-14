(ns dk.simongray.ical.sabre-test
  "The recurrence tests of sabre/vobject, the library of Nextcloud's
  calendar server."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [dk.simongray.ical.rrule :as rrule]
            [dk.simongray.ical.sabre-cases :as sabre]
            [dk.simongray.ical.tz :as tz]))

(def differences
  "The cases where this library differs from sabre/vobject, and why."
  {;; sabre refuses a rule with a value out of range, where this library
   ;; expands it without that value, and validation reports it
   "testYearlyByMonthInvalidValue1" :out-of-range
   "testYearlyByYearDayInvalid390"  :out-of-range
   "testYearlyByYearDayInvalid0"    :out-of-range
   "testByDayBadOffset"             :out-of-range
   ;; a year of 365 days has no -366th day, and 2012's week -20 is week 33
   "testYearlyByYearDayMaxNegative" :sabre-differs-from-the-rfc
   "testNegativeValidByWeekNo"      :sabre-differs-from-the-rfc
   ;; RFC 5545's table makes BYMINUTE and BYSECOND expand a DAILY rule,
   ;; and this library limits by BYYEARDAY and BYWEEKNO as dateutil does,
   ;; where sabre ignores all four
   "testIgnoredStuff"               :sabre-ignores-parts})

(defn date-time
  "The date or date-time `s` in sabre's form, e.g. 2011-10-07 12:00:00, in
  the form of jCal, in UTC when `utc?`."
  [s utc?]
  (str (str/replace (if (= 10 (count s)) (str s " 00:00:00") s) " " "T")
       (when utc? "Z")))

(deftest sabre
  (doseq [{:keys [name rrule start tz expected]} sabre/cases
          :when (not (differences name))]
    (let [utc?     (= "UTC" tz)
          rule     (rrule/parse rrule)
          expected (mapv #(date-time % utc?) expected)
          actual   (cond->> (rrule/occurrences rule (date-time start utc?)
                                               (when-not utc? (tz/zone tz)))
                     (not (or (:count rule) (:until rule))) (take (count expected))
                     true                                   vec)]
      (is (= expected actual) (str name ": " rrule)))))
