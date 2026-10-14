(ns dk.simongray.ical.recurrence-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dk.simongray.ical :as ical]))

(defn crlf
  "The `lines` joined as an iCalendar text, each ending with CRLF."
  [& lines]
  (apply str (map #(str % "\r\n") lines)))

(defn calendar
  "A VCALENDAR of the components written as the `lines`."
  [& lines]
  (ical/parse (apply crlf (concat ["BEGIN:VCALENDAR" "VERSION:2.0" "PRODID:-//t//t//EN"]
                                  lines
                                  ["END:VCALENDAR"]))))

(defn starts
  "The DTSTARTs of the first `n` instances of the first VEVENT of `cal`."
  ([cal]
   (starts cal 100))
  ([cal n]
   (->> (ical/occurrences cal (first (ical/components cal :vevent)))
        (take n)
        (mapv #(ical/value % :dtstart)))))

(deftest recurrence-sets
  (testing "RDATE adds instances, and EXDATE removes them"
    (is (= ["2026-10-05T10:00:00" "2026-10-07T12:00:00" "2026-10-19T10:00:00"]
           (starts (calendar "BEGIN:VEVENT" "UID:a" "DTSTART:20261005T100000"
                             "RRULE:FREQ=WEEKLY;COUNT=3"
                             "RDATE:20261007T120000"
                             "EXDATE:20261012T100000"
                             "END:VEVENT")))))
  (testing "an EXDATE in another zone removes the same instant"
    (is (= ["2026-10-05T10:00:00" "2026-10-19T10:00:00"]
           (starts (calendar "BEGIN:VEVENT" "UID:a"
                             "DTSTART;TZID=Europe/Copenhagen:20261005T100000"
                             "RRULE:FREQ=WEEKLY;COUNT=3"
                             "EXDATE:20261012T080000Z"
                             "END:VEVENT")))))
  (testing "a date as EXDATE removes the instances of its day"
    (is (= ["2026-10-05T10:00:00" "2026-10-07T10:00:00"]
           (starts (calendar "BEGIN:VEVENT" "UID:a" "DTSTART:20261005T100000"
                             "RRULE:FREQ=DAILY;COUNT=3"
                             "EXDATE;VALUE=DATE:20261006"
                             "END:VEVENT")))))
  (testing "an RDATE period sets the start and end of its instance"
    (let [cal (calendar "BEGIN:VEVENT" "UID:a" "DTSTART:20261005T100000"
                        "DTEND:20261005T110000"
                        "RDATE;VALUE=PERIOD:20261008T140000/20261008T170000"
                        "END:VEVENT")
          [_ rdate] (ical/occurrences cal (first (ical/components cal :vevent)))]
      (is (= ["2026-10-08T14:00:00" "2026-10-08T17:00:00"]
             [(ical/value rdate :dtstart) (ical/value rdate :dtend)])))))

(deftest clock-changes
  (testing "a time that the clocks skip moves forward, without a duplicate"
    (is (= ["2026-03-29T00:30:00" "2026-03-29T01:30:00" "2026-03-29T03:30:00"
            "2026-03-29T04:30:00"]
           (starts (calendar "BEGIN:VEVENT" "UID:a"
                             "DTSTART;TZID=Europe/Copenhagen:20260329T003000"
                             "RRULE:FREQ=HOURLY;COUNT=5" "END:VEVENT")))))
  (testing "a daily time in the gap is an hour later on that day only"
    (is (= ["2026-03-28T02:30:00" "2026-03-29T03:30:00" "2026-03-30T02:30:00"]
           (starts (calendar "BEGIN:VEVENT" "UID:a"
                             "DTSTART;TZID=Europe/Copenhagen:20260328T023000"
                             "RRULE:FREQ=DAILY;COUNT=3" "END:VEVENT"))))))

(deftest overrides
  (let [cal (calendar "BEGIN:VEVENT" "UID:a" "SEQUENCE:0" "SUMMARY:Møde"
                      "DTSTART;TZID=Europe/Copenhagen:20261005T100000"
                      "DTEND;TZID=Europe/Copenhagen:20261005T110000"
                      "RRULE:FREQ=WEEKLY;COUNT=4"
                      "END:VEVENT"
                      ;; iCloud writes a RECURRENCE-ID in UTC
                      "BEGIN:VEVENT" "UID:a" "SUMMARY:Flyttet møde"
                      "RECURRENCE-ID:20261012T080000Z"
                      "DTSTART;TZID=Europe/Copenhagen:20261013T140000"
                      "DTEND;TZID=Europe/Copenhagen:20261013T150000"
                      "END:VEVENT")
        instances (ical/occurrences cal (first (ical/components cal :vevent)))]
    (testing "an override takes the place of its instance"
      (is (= ["2026-10-05T10:00:00" "2026-10-13T14:00:00" "2026-10-19T10:00:00"
              "2026-10-26T10:00:00"]
             (mapv #(ical/value % :dtstart) instances)))
      (is (= "Flyttet møde" (ical/value (second instances) :summary))))
    (testing "the others keep their exact duration after the end of summer time"
      (is (= "2026-10-26T11:00:00" (ical/value (last instances) :dtend))))
    (testing "an instance has a RECURRENCE-ID and no rule"
      (is (= {:name :recurrence-id :params {:tzid "Europe/Copenhagen"}
              :value "2026-10-19T10:00:00"}
             (ical/property (nth instances 2) :recurrence-id)))
      (is (nil? (ical/property (nth instances 2) :rrule)))))
  (testing "RANGE=THISANDFUTURE moves the instances after it"
    (is (= ["2026-10-05T10:00:00" "2026-10-12T12:00:00" "2026-10-19T12:00:00"]
           (starts (calendar "BEGIN:VEVENT" "UID:a" "DTSTART:20261005T100000"
                             "RRULE:FREQ=WEEKLY;COUNT=3" "END:VEVENT"
                             "BEGIN:VEVENT" "UID:a"
                             "RECURRENCE-ID;RANGE=THISANDFUTURE:20261012T100000"
                             "DTSTART:20261012T120000" "END:VEVENT"))))))

(deftest durations
  (testing "a nominal day across the end of summer time is 25 hours"
    (let [cal (calendar "BEGIN:VEVENT" "UID:a"
                        "DTSTART;TZID=Europe/Copenhagen:20261024T120000"
                        "DURATION:P1D" "END:VEVENT")
          e   (first (ical/components cal :vevent))]
      (is (= "2026-10-25T12:00:00" (:value (ical/end cal e))))
      (is (= (* 25 3600 1000)
             (- (inst-ms (ical/instant cal (ical/end cal e)))
                (inst-ms (ical/instant cal (ical/property e :dtstart))))))))
  (testing "an event on a date lasts the day"
    (let [cal (calendar "BEGIN:VEVENT" "UID:a" "DTSTART;VALUE=DATE:20261012" "END:VEVENT")]
      (is (= {:name :dtend :type :date :value "2026-10-13"}
             (ical/end cal (first (ical/components cal :vevent))))))))

(deftest zones
  (testing "a zone that only the calendar's VTIMEZONE defines"
    (let [cal (calendar "BEGIN:VTIMEZONE" "TZID:Customized Time Zone"
                        "BEGIN:STANDARD" "DTSTART:16010101T030000"
                        "TZOFFSETFROM:+0200" "TZOFFSETTO:+0100"
                        "RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=-1SU;BYMONTH=10"
                        "END:STANDARD"
                        "BEGIN:DAYLIGHT" "DTSTART:16010101T020000"
                        "TZOFFSETFROM:+0100" "TZOFFSETTO:+0200"
                        "RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=-1SU;BYMONTH=3"
                        "END:DAYLIGHT" "END:VTIMEZONE"
                        "BEGIN:VEVENT" "UID:a"
                        "DTSTART;TZID=Customized Time Zone:20261012T190000"
                        "DTEND;TZID=Customized Time Zone:20261102T190000"
                        "END:VEVENT")
          e   (first (ical/components cal :vevent))]
      (is (= #inst "2026-10-12T17:00:00Z" (ical/instant cal (ical/property e :dtstart))))
      (is (= #inst "2026-11-02T18:00:00Z" (ical/instant cal (ical/property e :dtend))))))
  (testing "a Windows zone name"
    (let [cal (calendar "BEGIN:VEVENT" "UID:a"
                        "DTSTART;TZID=Romance Standard Time:20261012T190000"
                        "END:VEVENT")]
      (is (= #inst "2026-10-12T17:00:00Z"
             (ical/instant cal (ical/property (first (ical/components cal :vevent)) :dtstart))))))
  (testing "a floating time has an instant only in a zone that's given"
    (let [cal (calendar "BEGIN:VEVENT" "UID:a" "DTSTART:20261012T190000" "END:VEVENT")
          p   (ical/property (first (ical/components cal :vevent)) :dtstart)]
      (is (nil? (ical/instant cal p)))
      (is (= #inst "2026-10-12T17:00:00Z"
             (ical/instant cal p {:floating "Europe/Copenhagen"}))))))

(deftest outlook-zones
  (testing "a year of instances in a zone of Outlook's VTIMEZONE, from 1601"
    (let [vevent  ["BEGIN:VEVENT" "UID:a" "DTSTART;TZID=%s:20260105T090000"
                   "RRULE:FREQ=DAILY" "END:VEVENT"]
          event   (fn [tzid] (map #(str/replace % "%s" tzid) vevent))
          outlook (apply calendar (concat ["BEGIN:VTIMEZONE" "TZID:Customized Time Zone"
                                           "BEGIN:STANDARD" "DTSTART:16010101T030000"
                                           "TZOFFSETFROM:+0200" "TZOFFSETTO:+0100"
                                           "RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=-1SU;BYMONTH=10"
                                           "END:STANDARD"
                                           "BEGIN:DAYLIGHT" "DTSTART:16010101T020000"
                                           "TZOFFSETFROM:+0100" "TZOFFSETTO:+0200"
                                           "RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=-1SU;BYMONTH=3"
                                           "END:DAYLIGHT" "END:VTIMEZONE"]
                                          (event "Customized Time Zone")))
          iana    (apply calendar (event "Europe/Copenhagen"))
          insts   (fn [cal]
                    (->> (:components (ical/expand cal #inst "2026-01-01T00:00:00Z"
                                                   #inst "2027-01-01T00:00:00Z"))
                         (filter #(= :vevent (:name %)))
                         (map #(ical/instant cal (ical/property % :dtstart)))))]
      (is (= 361 (count (insts outlook))))
      (is (= (insts iana) (insts outlook))))))

(deftest expanding
  (testing "an override that moves an instance into the range from after it"
    (let [cal (calendar "BEGIN:VEVENT" "UID:a" "DTSTART:20261005T100000Z"
                        "DTEND:20261005T110000Z" "RRULE:FREQ=WEEKLY" "END:VEVENT"
                        "BEGIN:VEVENT" "UID:a" "RECURRENCE-ID:20261102T100000Z"
                        "DTSTART:20261008T100000Z" "DTEND:20261008T110000Z" "END:VEVENT")]
      (is (= ["2026-10-05T10:00:00Z" "2026-10-08T10:00:00Z"]
             (mapv #(ical/value % :dtstart)
                   (:components (ical/expand cal #inst "2026-10-01T00:00:00Z"
                                             #inst "2026-10-10T00:00:00Z")))))))
  (let [cal (calendar "BEGIN:VEVENT" "UID:a" "DTSTART:20261005T100000Z"
                      "DTEND:20261005T110000Z" "RRULE:FREQ=DAILY" "END:VEVENT"
                      "BEGIN:VEVENT" "UID:b" "DTSTART:20261009T100000Z" "END:VEVENT"
                      "BEGIN:VEVENT" "UID:c" "DTSTART:20261001T100000Z" "END:VEVENT")]
    (is (= [["a" "2026-10-07T10:00:00Z"] ["a" "2026-10-08T10:00:00Z"]
            ["a" "2026-10-09T10:00:00Z"] ["b" "2026-10-09T10:00:00Z"]]
           (mapv (juxt #(ical/value % :uid) #(ical/value % :dtstart))
                 (:components (ical/expand cal #inst "2026-10-07T00:00:00Z"
                                           #inst "2026-10-10T00:00:00Z")))))))

(deftest edge-cases
  (testing "an unknown TZID counts as floating, so expand ends"
    (let [cal (calendar "BEGIN:VEVENT" "UID:a" "DTSTART;TZID=Nowhere/Special:20261005T100000"
                        "DURATION:PT1H" "RRULE:FREQ=WEEKLY" "END:VEVENT")]
      (is (= ["2026-10-05T10:00:00" "2026-10-12T10:00:00"]
             (mapv #(ical/value % :dtstart)
                   (:components (ical/expand cal #inst "2026-10-01T00:00:00Z"
                                             #inst "2026-10-15T00:00:00Z")))))))
  (testing "values that don't parse are left out, not computed with"
    (let [cal (calendar "BEGIN:VEVENT" "UID:a" "DTSTART:20240101T000000Z"
                        "DURATION:P999999999999999999W" "END:VEVENT")]
      (is (= {:name :dtend :value "2024-01-01T00:00:00Z"}
             (ical/end cal (first (ical/components cal :vevent)))))))
  (testing "a RANGE=THISANDFUTURE override in another zone moves the later instances"
    (let [cal (calendar "BEGIN:VEVENT" "UID:a" "DTSTART;TZID=Europe/Copenhagen:20260105T100000"
                        "RRULE:FREQ=WEEKLY;COUNT=3" "END:VEVENT"
                        "BEGIN:VEVENT" "UID:a"
                        "RECURRENCE-ID;TZID=Europe/Copenhagen;RANGE=THISANDFUTURE:20260112T100000"
                        "DTSTART;TZID=America/New_York:20260112T050000" "END:VEVENT")
          moved (last (ical/occurrences cal (first (ical/components cal :vevent))))]
      (is (= [{:name :dtstart :params {:tzid "America/New_York"} :value "2026-01-19T05:00:00"}
              {:name :recurrence-id :params {:tzid "Europe/Copenhagen"} :value "2026-01-19T10:00:00"}]
             [(ical/property moved :dtstart) (ical/property moved :recurrence-id)]))))
  (testing "an EXRULE removes only what it generates, and counts only that"
    (is (= ["2026-01-01T10:00:00" "2026-01-02T10:00:00" "2026-01-04T10:00:00"
            "2026-01-05T10:00:00" "2026-01-06T10:00:00" "2026-01-07T10:00:00"]
           (starts (calendar "BEGIN:VEVENT" "UID:a" "DTSTART:20260101T100000"
                             "RRULE:FREQ=DAILY;COUNT=12" "EXRULE:FREQ=WEEKLY;BYDAY=SA;COUNT=2"
                             "END:VEVENT")
                   6))))
  (testing "an EXDATE removes an instance that the clocks skip"
    (is (= ["2026-03-27T02:30:00" "2026-03-28T02:30:00" "2026-03-30T02:30:00"]
           (starts (calendar "BEGIN:VEVENT" "UID:a"
                             "DTSTART;TZID=Europe/Copenhagen:20260327T023000"
                             "RRULE:FREQ=DAILY;COUNT=4"
                             "EXDATE;TZID=Europe/Copenhagen:20260329T023000" "END:VEVENT")))))
  (testing "a date as EXDATE removes its day, whatever the zone of floating times"
    (let [cal (calendar "BEGIN:VEVENT" "UID:a" "DTSTART;TZID=America/New_York:20260101T100000"
                        "RRULE:FREQ=DAILY;COUNT=4" "EXDATE;VALUE=DATE:20260103" "END:VEVENT")]
      (is (= ["2026-01-01T10:00:00" "2026-01-02T10:00:00" "2026-01-04T10:00:00"]
             (mapv #(ical/value % :dtstart)
                   (ical/occurrences cal (first (ical/components cal :vevent))
                                     {:floating "Europe/Copenhagen"}))))))
  (testing "the same instance from two RDATEs comes once"
    (is (= ["2026-01-05T10:00:00Z" "2026-01-10T10:00:00Z"]
           (starts (calendar "BEGIN:VEVENT" "UID:a" "DTSTART:20260105T100000Z"
                             "RDATE:20260110T100000Z,20260110T100000Z"
                             "RDATE;TZID=Europe/Copenhagen:20260110T110000" "END:VEVENT")))))
  (testing "expand puts floating times in the zone :floating"
    (let [cal (calendar "BEGIN:VEVENT" "UID:a" "DTSTART:20260101T220000"
                        "DTEND:20260101T230000" "RRULE:FREQ=DAILY;COUNT=3" "END:VEVENT")]
      (is (= ["2026-01-01T22:00:00"]
             (mapv #(ical/value % :dtstart)
                   (:components (ical/expand cal #inst "2026-01-02T02:00:00Z"
                                             #inst "2026-01-02T05:00:00Z"
                                             {:floating "America/New_York"})))))))
  (testing "a recurring component with a RECURRENCE-ID of its own expands"
    (let [cal (calendar "BEGIN:VEVENT" "UID:a" "RECURRENCE-ID:20260105T100000"
                        "DTSTART:20260105T100000" "RRULE:FREQ=DAILY;COUNT=2" "END:VEVENT")]
      (is (= 2 (count (:components (ical/expand cal #inst "2026-01-01T00:00:00Z"
                                                #inst "2026-02-01T00:00:00Z")))))))
  (testing "a SEQUENCE that isn't a number counts as 0"
    (is (= ["2026-01-05T10:00:00" "2026-01-06T13:00:00"]
           (starts (calendar "BEGIN:VEVENT" "UID:a" "DTSTART:20260105T100000"
                             "RRULE:FREQ=DAILY;COUNT=2" "END:VEVENT"
                             "BEGIN:VEVENT" "UID:a" "RECURRENCE-ID:20260106T100000"
                             "SEQUENCE:2" "DTSTART:20260106T130000" "END:VEVENT"
                             "BEGIN:VEVENT" "UID:a" "RECURRENCE-ID:20260106T100000"
                             "SEQUENCE:x" "DTSTART:20260106T120000" "END:VEVENT")))))
  (testing "all-day instances keep their days across a clock change"
    (let [cal (calendar "BEGIN:VEVENT" "UID:a" "DTSTART;VALUE=DATE:20260328"
                        "DTEND;VALUE=DATE:20260330" "RRULE:FREQ=WEEKLY;COUNT=2" "END:VEVENT")]
      (is (= ["2026-03-30" "2026-04-06"]
             (mapv #(ical/value % :dtend)
                   (ical/occurrences cal (first (ical/components cal :vevent))
                                     {:floating "Europe/Copenhagen"})))))))
