(ns dk.simongray.ical.validation-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.ical :as ical]))

(defn crlf
  "The `lines` joined as an iCalendar text, each ending with CRLF."
  [& lines]
  (apply str (map #(str % "\r\n") lines)))

(defn errors
  "The :error keywords of the validation errors of a VCALENDAR with the
  `lines` inside, of the `level`."
  [level & lines]
  (->> (ical/validation-errors
        (ical/parse (apply crlf (concat ["BEGIN:VCALENDAR" "VERSION:2.0" "PRODID:-//t//t//EN"]
                                        lines ["END:VCALENDAR"]))))
       (filter #(= level (:level %)))
       (mapv :error)))

(def event
  ["UID:a" "DTSTAMP:20261001T090000Z"])

(deftest valid-calendars
  (is (ical/valid? (apply crlf (concat ["BEGIN:VCALENDAR" "VERSION:2.0" "PRODID:-//t//t//EN"
                                        "BEGIN:VEVENT"]
                                       event
                                       ["DTSTART:20261012T100000Z" "END:VEVENT" "END:VCALENDAR"])))))

(deftest properties
  (testing "required, repeated and conflicting"
    (is (= [:missing-property :missing-property :missing-property]
           (errors :error "BEGIN:VEVENT" "SUMMARY:x" "END:VEVENT")))
    (is (= [:repeated-property]
           (apply errors :error (concat ["BEGIN:VEVENT"] event
                                        ["DTSTART:20261012T100000Z" "SUMMARY:a" "SUMMARY:b"
                                         "END:VEVENT"]))))
    (is (= [:conflicting-properties]
           (apply errors :error (concat ["BEGIN:VEVENT"] event
                                        ["DTSTART:20261012T100000Z" "DTEND:20261012T110000Z"
                                         "DURATION:PT1H" "END:VEVENT"])))))
  (testing "a value that isn't of its type"
    (is (= [:invalid-value]
           (apply errors :error (concat ["BEGIN:VEVENT"] event
                                        ["DTSTART:20261012T100000Z" "PRIORITY:high"
                                         "END:VEVENT"])))))
  (testing "an alarm's action needs its properties"
    (is (= [:missing-property]
           (apply errors :error (concat ["BEGIN:VEVENT"] event
                                        ["DTSTART:20261012T100000Z" "BEGIN:VALARM"
                                         "ACTION:DISPLAY" "TRIGGER:-PT15M" "END:VALARM"
                                         "END:VEVENT"]))))))

(deftest times
  (testing "an end before its start, or of another type"
    (is (= [:end-before-start]
           (apply errors :error (concat ["BEGIN:VEVENT"] event
                                        ["DTSTART:20261012T100000Z" "DTEND:20261012T090000Z"
                                         "END:VEVENT"]))))
    (is (= [:mismatched-types]
           (apply errors :error (concat ["BEGIN:VEVENT"] event
                                        ["DTSTART;VALUE=DATE:20261012" "DTEND:20261013T090000Z"
                                         "END:VEVENT"])))))
  (testing "a TZID that nothing defines, and one without its VTIMEZONE"
    (is (= [:unknown-tzid]
           (apply errors :error (concat ["BEGIN:VEVENT"] event
                                        ["DTSTART;TZID=Nowhere:20261012T100000" "END:VEVENT"]))))
    (is (= [:missing-vtimezone]
           (apply errors :warning (concat ["BEGIN:VEVENT"] event
                                          ["DTSTART;TZID=Europe/Copenhagen:20261012T100000"
                                           "END:VEVENT"]))))))

(deftest rules
  (let [rule-errors #(apply errors :error (concat ["BEGIN:VEVENT"] event
                                                  ["DTSTART;TZID=Europe/Copenhagen:20261012T100000"
                                                   (str "RRULE:" %) "END:VEVENT"]))]
    (is (= [:count-and-until] (rule-errors "FREQ=DAILY;COUNT=2;UNTIL=20261020T000000Z")))
    (is (= [:until-mismatch] (rule-errors "FREQ=DAILY;UNTIL=20261020T000000")))
    (is (= [:byweekno-not-yearly] (rule-errors "FREQ=MONTHLY;BYWEEKNO=3")))
    (is (= [:bysetpos-alone] (rule-errors "FREQ=MONTHLY;BYSETPOS=1")))
    (is (= [:rule-part-out-of-range] (rule-errors "FREQ=MONTHLY;BYMONTHDAY=32"))))
  (testing "a DTSTART that the rule doesn't make"
    (is (= [:unsynchronized-dtstart]
           (remove #{:missing-vtimezone}
                   (apply errors :warning (concat ["BEGIN:VEVENT"] event
                                                  ["DTSTART:20261014T100000Z"
                                                   "RRULE:FREQ=WEEKLY;BYDAY=MO" "END:VEVENT"])))))))

(deftest text
  (testing "the lines that were left out, and an unclosed component"
    (is (= [{:error :not-a-content-line :level :error :path [] :line 1 :text "junk"}
            {:error :unclosed-component :level :error :path [] :component :vcalendar}]
           (take 2 (ical/validation-errors (crlf "junk" "BEGIN:VCALENDAR" "VERSION:2.0"
                                                 "PRODID:-//t//t//EN")))))))
