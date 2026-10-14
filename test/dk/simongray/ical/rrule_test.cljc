(ns dk.simongray.ical.rrule-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.ical.rfc5545-examples :as examples]
            [dk.simongray.ical.rrule :as rrule]
            [dk.simongray.ical.tz :as tz]))

(def new-york
  (tz/zone "America/New_York"))

(defn instances
  "The instances of the rule `s` from `dtstart` in New York, without the
  `exdate`, as many as `expected` has unless `complete?`."
  [s dtstart exdate expected complete?]
  (cond->> (remove (set exdate) (rrule/occurrences (rrule/parse s) dtstart new-york))
    (not complete?) (take (count expected))
    true            vec))

(deftest rfc-5545-examples
  (doseq [{:keys [title dtstart rrules exdate expected complete?]} examples/examples
          s rrules]
    (is (= expected (instances s dtstart exdate expected complete?))
        (str title ": " s))))

(deftest reading-and-writing
  (testing "parts by lower-case key, case and the RRULE prefix forgiven"
    (is (= {:freq "WEEKLY" :interval 2 :byday ["MO" "WE"] :until "2026-12-31"}
           (rrule/parse "RRULE:freq=weekly;INTERVAL=2;BYDAY=mo,WE;UNTIL=20261231"))))
  (testing "no FREQ, an unknown FREQ, a part that doesn't read or comes twice"
    (is (nil? (rrule/parse "BYDAY=MO")))
    (is (nil? (rrule/parse "FREQ=SOMETIMES")))
    (is (nil? (rrule/parse "FREQ=WEEKLY;INTERVAL=two")))
    (is (nil? (rrule/parse "FREQ=WEEKLY;FREQ=DAILY"))))
  (testing "written with FREQ first, and parts it doesn't know last"
    (is (= "FREQ=WEEKLY;UNTIL=20261231T230000Z;INTERVAL=2;BYDAY=MO,WE;X-FOO=bar"
           (rrule/serialize {:x-foo "bar" :byday ["MO" "WE"] :interval 2
                             :until "2026-12-31T23:00:00Z" :freq "WEEKLY"})))))

(deftest validation
  (testing "a rule on its own"
    (is (= [] (rrule/validation-errors "FREQ=MONTHLY;BYDAY=2MO")))
    (is (= [{:error :count-and-until :level :error}]
           (rrule/validation-errors {:freq "WEEKLY" :count 4 :until "2026-12-31"})))
    (is (= [{:error :rule-part-out-of-range :level :error :part :bymonthday :value 32}
            {:error :byweekno-not-yearly :level :error}]
           (rrule/validation-errors "FREQ=MONTHLY;BYMONTHDAY=32;BYWEEKNO=3")))
    (is (= [{:error :skip-without-rscale :level :error}]
           (rrule/validation-errors "FREQ=MONTHLY;SKIP=BACKWARD"))))
  (testing "a map with parts of the wrong form, or no FREQ"
    (is (= [{:error :invalid-part :level :error :part :freq :value "FORTNIGHTLY"}
            {:error :invalid-part :level :error :part :byday :value ["MO" "XX"]}]
           (rrule/validation-errors {:freq "FORTNIGHTLY" :byday ["MO" "XX"]})))
    (is (= [{:error :missing-part :level :error :part :freq}]
           (rrule/validation-errors {:byday ["MO"]})))
    (is (= [{:error :invalid-value :level :error}] (rrule/validation-errors "nonsense"))))
  (testing "with the start it would expand from"
    (is (= [{:error :unsynchronized-dtstart :level :warning}]
           (rrule/validation-errors "FREQ=WEEKLY;BYDAY=MO" "2026-10-14T10:00:00")))
    (is (rrule/valid? "FREQ=WEEKLY;BYDAY=MO" "2026-10-14T10:00:00"))
    (is (= [{:error :until-mismatch :level :error}]
           (rrule/validation-errors "FREQ=DAILY;UNTIL=20261231T230000Z" "2026-10-14T10:00:00"))
        "a floating start takes a floating UNTIL")
    (is (= [] (rrule/validation-errors "FREQ=DAILY;UNTIL=20261231T230000Z" "2026-10-14T10:00:00"
                                       (tz/zone "+02:00"))))))

(deftest expansion
  (let [occ #(vec (take %3 (rrule/occurrences (rrule/parse %1) %2)))]
    (testing "a monthly rule from the 31st skips the shorter months"
      (is (= ["2026-01-31T10:00:00" "2026-03-31T10:00:00" "2026-05-31T10:00:00"]
             (occ "FREQ=MONTHLY" "2026-01-31T10:00:00" 3))))
    (testing "a yearly rule from 29 February comes every leap year"
      (is (= ["2024-02-29" "2028-02-29" "2032-02-29"] (occ "FREQ=YEARLY" "2024-02-29" 3))))
    (testing "BYWEEKNO with the weekday of DTSTART, across the end of the year"
      (is (= ["2013-01-01T00:00:00" "2013-01-08T00:00:00" "2013-12-31T00:00:00"
              "2014-01-07T00:00:00"]
             (occ "FREQ=YEARLY;BYWEEKNO=1,2" "2013-01-01T00:00:00" 4))))
    (testing "DTSTART comes first even when the rule doesn't make it, and counts"
      (is (= ["2026-10-14T10:00:00" "2026-10-19T10:00:00"]
             (vec (rrule/occurrences (rrule/parse "FREQ=WEEKLY;BYDAY=MO;COUNT=2")
                                     "2026-10-14T10:00:00")))))
    (testing "a rule that can make nothing ends"
      (is (= ["2026-01-01"] (vec (rrule/occurrences (rrule/parse "FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30")
                                                    "2026-01-01"))))
      (is (= ["2026-01-01T00:00:00"]
             (vec (rrule/occurrences (rrule/parse "FREQ=MINUTELY;BYMONTH=2;BYMONTHDAY=30")
                                     "2026-01-01T00:00:00")))))
    (testing "a date as UNTIL for a date-time DTSTART ends with its day"
      (is (= ["2026-10-12T19:00:00" "2026-10-13T19:00:00"]
             (vec (rrule/occurrences (rrule/parse "FREQ=DAILY;UNTIL=20261013")
                                     "2026-10-12T19:00:00")))))
    (testing "RSCALE=GREGORIAN with SKIP moves the days a month lacks (RFC 7529)"
      (is (= ["2026-01-31" "2026-02-28" "2026-03-31"]
             (occ "RSCALE=GREGORIAN;FREQ=MONTHLY;SKIP=BACKWARD" "2026-01-31" 3)))
      (is (= ["2026-01-31" "2026-03-01" "2026-03-31"]
             (occ "RSCALE=GREGORIAN;FREQ=MONTHLY;SKIP=FORWARD" "2026-01-31" 3))))
    (testing "rules in another calendar aren't expanded"
      (is (nil? (rrule/occurrences (rrule/parse "RSCALE=CHINESE;FREQ=YEARLY")
                                   "2026-10-12"))))))
