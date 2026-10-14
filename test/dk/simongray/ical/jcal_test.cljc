(ns dk.simongray.ical.jcal-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.ical :as ical]
            [dk.simongray.ical.jcal :as jcal]))

(deftest values
  (testing "a float that JSON wrote without its fraction"
    (is (= [37.0 -122.0]
           (ical/value (jcal/jcal->calendar ["vevent" [["geo" {} "float" [37 -122]]] []]) :geo))))
  (testing "BYDAY reads as the parser reads it"
    (is (= {:freq "MONTHLY" :byday ["1MO" "-1SU"]}
           (ical/value (jcal/jcal->calendar ["vevent" [["rrule" {} "recur"
                                                        {"freq" "MONTHLY" "byday" ["+1MO" "-1su"]}]]
                                             []])
                       :rrule))))
  (testing "a property that isn't a list, with several values"
    (is (= "BEGIN:VEVENT\r\nX-FOO:a,b\r\nSUMMARY:5\r\nEND:VEVENT\r\n"
           (ical/serialize (jcal/jcal->calendar ["vevent" [["x-foo" {} "text" "a" "b"]
                                                           ["summary" {} "text" 5]]
                                                 []])))))
  (testing "a period without its end doesn't throw"
    (is (= ["2026-10-12T19:00:00Z"]
           (ical/value (jcal/jcal->calendar ["vevent" [["dtstart" {} "period" ["2026-10-12T19:00:00Z"]]] []])
                       :dtstart)))))

(deftest parameters
  (testing "more than eight keep their order both ways"
    (let [cal (ical/parse (str "BEGIN:VEVENT\r\n"
                               "X-A;P1=1;P2=2;P3=3;P4=4;P5=5;P6=6;P7=7;P8=8;P9=9;P10=10:v\r\n"
                               "END:VEVENT\r\n"))
          a   (second (first (second (jcal/calendar->jcal cal))))]
      (is (= ["p1" "p2" "p3" "p4" "p5" "p6" "p7" "p8" "p9" "p10"] (keys a)))
      (is (= (ical/serialize cal)
             (ical/serialize (jcal/jcal->calendar (jcal/calendar->jcal cal))))))))
