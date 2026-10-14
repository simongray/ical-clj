(ns dk.simongray.ical.dateutil-test
  "The recurrence tests of python-dateutil, which rrule.js, rrule-go and
  rust-rrule port too."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [dk.simongray.ical.dateutil-cases :as dateutil]
            [dk.simongray.ical.rrule :as rrule]))

(def differences
  "The cases where this library differs from dateutil, which RFC 5545
  leaves open, and why."
  {;; libical, ical.js and sabre/vobject take the month of DTSTART, where
   ;; dateutil takes every month
   "testYearlyByMonthDay"           :month-of-dtstart
   "testYearlyByMonthDayAndWeekDay" :month-of-dtstart
   "testYearlyBySetPos"             :month-of-dtstart
   ;; libical takes the weekday of DTSTART, where dateutil takes them all
   "testYearlyByWeekNo"             :weekday-of-dtstart
   ;; a date as UNTIL for a date-time DTSTART ends with its day, where
   ;; dateutil ends at its midnight
   "testUntilWithDate"              :until-ends-with-its-day})

;; dateutil leaves DTSTART out when the rule doesn't generate it, and so
;; counts COUNT without it. Here DTSTART is always the first instance.
(defn expected
  "The instances that this library gives for the dateutil `case`. For a
  date as DTSTART, dateutil gives midnights where this library gives
  dates."
  [{:keys [rrule dtstart expected]}]
  (let [expected (cond->> expected
                   (not (str/includes? dtstart "T")) (mapv #(str/replace % "T00:00:00" "")))
        n        (:count (rrule/parse rrule))]
    (if (= dtstart (first expected))
      expected
      (vec (cons dtstart (cond->> expected n (take (dec n))))))))

(defn actual
  "The instances that this library gives for the dateutil `case`, as many
  as `expected` has when the rule has neither COUNT nor UNTIL."
  [{:keys [rrule dtstart]} expected]
  (let [rule (rrule/parse rrule)]
    (cond->> (rrule/occurrences rule dtstart)
      (not (or (:count rule) (:until rule))) (take (count expected))
      true                                   vec)))

(deftest dateutil
  (doseq [case  dateutil/cases
          :when (not (differences (:name case)))]
    (let [exp (expected case)]
      (is (= exp (actual case exp)) (str (:name case) ": " (:rrule case))))))
