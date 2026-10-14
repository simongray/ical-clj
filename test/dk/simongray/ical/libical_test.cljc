(ns dk.simongray.ical.libical-test
  "The recurrence tests of libical, from the files that dev/fetch.sh
  downloads."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.ical.rrule :as rrule]
            [dk.simongray.ical.test-data :as test-data]))

(defn jcal
  "The date or date-time `s` of libical's files, e.g. 20130101T000000Z, in
  the form of jCal."
  [s]
  (let [[_ y m d t] (re-matches #"(\d{4})(\d{2})(\d{2})(?:T(\d{6}Z?))?" s)]
    (str y "-" m "-" d
         (when t (str "T" (subs t 0 2) ":" (subs t 2 4) ":" (subs t 4))))))

;; libical leaves DTSTART out when the rule doesn't make it, and so counts
;; COUNT without it. Here DTSTART is always the first instance.
(defn expected
  "The instances that this library gives for the libical `case`."
  [{:keys [rrule dtstart instances]}]
  (let [dtstart   (jcal dtstart)
        instances (mapv jcal instances)
        n         (:count (rrule/parse rrule))]
    (if (= dtstart (first instances))
      instances
      (vec (cons dtstart (cond->> instances n (take (dec n))))))))

(defn actual
  "The instances that this library gives for the libical `case`, as many as
  `expected` has when the rule has neither COUNT nor UNTIL."
  [{:keys [rrule dtstart]} expected]
  (let [rule (rrule/parse rrule)]
    (cond->> (rrule/occurrences rule (jcal dtstart))
      (not (or (:count rule) (:until rule))) (take (count expected))
      true                                   vec)))

(defn testable?
  "Whether this library expands the rule of the libical `case`."
  [{:keys [rrule unimplemented]}]
  (and (not unimplemented) (rrule/expandable? (rrule/parse rrule))))

(deftest libical
  (doseq [file  ["libical/icalrecur_test.txt" "libical/icalrecur_test_rscale.txt"]
          case  (some-> (test-data/text file) test-data/libical-cases)
          :when (testable? case)]
    (let [exp (expected case)]
      (is (= exp (actual case exp))
          (str (:comment case) " " (:rrule case) " from " (:dtstart case))))))
