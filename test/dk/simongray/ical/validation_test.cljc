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
                                       ["DTSTART:20261012T100000Z" "END:VEVENT" "END:VCALENDAR"]))))
  (testing "what isn't a calendar isn't valid"
    (is (not-any? ical/valid? ["" nil {} (crlf "BEGIN:VEVENT" "UID:a" "DTSTAMP:20261001T090000Z"
                                               "END:VEVENT")]))))

(deftest more-rules
  (testing "where components can be"
    (is (= [:misplaced-component]
           (errors :error "BEGIN:VALARM" "ACTION:DISPLAY" "TRIGGER:-PT15M" "DESCRIPTION:x" "END:VALARM"))))
  (testing "what must be in UTC, and what must be local"
    (is (= [:not-utc] (apply errors :error (concat ["BEGIN:VEVENT"] event
                                                   ["DTSTART:20261012T100000Z" "CREATED:20261001T090000"
                                                    "END:VEVENT"]))))
    (is (= [:tzid-not-allowed] (apply errors :error (concat ["BEGIN:VEVENT"] event
                                                            ["DTSTART;TZID=Europe/Copenhagen:20261012T100000Z"
                                                             "END:VEVENT"]))))
    (is (= [:not-local] (errors :error "BEGIN:VTIMEZONE" "TZID:A" "BEGIN:STANDARD" "DTSTART:19700101T000000Z"
                                "TZOFFSETFROM:+0100" "TZOFFSETTO:+0100" "END:STANDARD" "END:VTIMEZONE"))))
  (testing "a VTIMEZONE's UNTIL is in UTC"
    (is (= [] (errors :error "BEGIN:VTIMEZONE" "TZID:A" "BEGIN:STANDARD" "DTSTART:19671029T020000"
                      "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU;UNTIL=20061029T060000Z"
                      "TZOFFSETFROM:-0400" "TZOFFSETTO:-0500" "END:STANDARD" "END:VTIMEZONE"))))
  (testing "a RECURRENCE-ID of the type of DTSTART, and numbers in range"
    (is (= [:mismatched-types :property-out-of-range]
           (apply errors :error (concat ["BEGIN:VEVENT"] event
                                        ["DTSTART:20261012T100000Z" "RECURRENCE-ID;VALUE=DATE:20261012"
                                         "PRIORITY:10" "END:VEVENT"])))))
  (testing "a DURATION needs a DTSTART, and a TZID one VTIMEZONE"
    (is (= [:missing-property] (apply errors :error (concat ["BEGIN:VTODO"] event ["DURATION:PT1H" "END:VTODO"]))))
    (is (= [:repeated-vtimezone]
           (apply errors :error (for [_ (range 2)
                                      line ["BEGIN:VTIMEZONE" "TZID:A" "BEGIN:STANDARD" "DTSTART:19700101T000000"
                                            "TZOFFSETFROM:+0100" "TZOFFSETTO:+0100" "END:STANDARD" "END:VTIMEZONE"]]
                                  line))))))

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
    (is (= [:rule-part-out-of-range] (rule-errors "FREQ=MONTHLY;BYMONTHDAY=32")))
    (is (= [:rule-part-out-of-range] (rule-errors "FREQ=MONTHLY;BYDAY=0MO")))
    (is (= [:bymonthday-not-allowed] (rule-errors "FREQ=WEEKLY;BYMONTHDAY=3"))))
  (testing "a DTSTART and a DTEND that didn't parse are only invalid"
    (is (= [:invalid-value :invalid-value]
           (apply errors :error (concat ["BEGIN:VEVENT"] event
                                        ["DTSTART;VALUE=DATE:20261012T100000"
                                         "DTEND:2026" "RRULE:FREQ=DAILY" "END:VEVENT"])))))
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
