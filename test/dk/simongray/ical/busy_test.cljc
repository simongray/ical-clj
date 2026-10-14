(ns dk.simongray.ical.busy-test
  (:require [clojure.test :refer [deftest is testing]]
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

(defn busy
  "The FREEBUSY periods of the calendar `cal` from the inst `from` to the
  inst `to`, by their type, each as a pair of its start and its end."
  [cal from to]
  (->> (:properties (ical/free-busy cal from to {:now #inst "2026-10-01T00:00:00Z" :uid "fb"}))
       (filter #(= :freebusy (:name %)))
       (into {} (map (fn [p] [(get-in p [:params :fbtype] "BUSY")
                              (mapv (juxt :start :end) (:value p))])))))

(deftest events
  (let [cal (calendar "BEGIN:VEVENT" "UID:a" "DTSTART:20261005T080000Z" "DTEND:20261005T100000Z" "END:VEVENT"
                      "BEGIN:VEVENT" "UID:b" "DTSTART:20261005T090000Z" "DTEND:20261005T110000Z" "END:VEVENT"
                      "BEGIN:VEVENT" "UID:c" "STATUS:TENTATIVE" "DTSTART:20261005T120000Z" "DURATION:PT1H" "END:VEVENT"
                      "BEGIN:VEVENT" "UID:d" "TRANSP:TRANSPARENT" "DTSTART:20261005T140000Z" "DURATION:PT1H" "END:VEVENT"
                      "BEGIN:VEVENT" "UID:e" "STATUS:CANCELLED" "DTSTART:20261005T150000Z" "DURATION:PT1H" "END:VEVENT"
                      "BEGIN:VEVENT" "UID:f" "DTSTART:20261005T220000Z" "DURATION:PT4H" "END:VEVENT")]
    (testing "overlapping periods merge, and the range clips them"
      (is (= {"BUSY"           [["2026-10-05T08:00:00Z" "2026-10-05T11:00:00Z"]
                                ["2026-10-05T22:00:00Z" "2026-10-06T00:00:00Z"]]
              "BUSY-TENTATIVE" [["2026-10-05T12:00:00Z" "2026-10-05T13:00:00Z"]]}
             (busy cal #inst "2026-10-05T00:00:00Z" #inst "2026-10-06T00:00:00Z"))))
    (testing "a VFREEBUSY that validates"
      (is (ical/valid? {:name       :vcalendar
                        :properties [{:name :version :value "2.0"} {:name :prodid :value "t"}]
                        :components [(ical/free-busy cal #inst "2026-10-05T00:00:00Z"
                                                     #inst "2026-10-06T00:00:00Z"
                                                     {:uid "fb"})]})))))

(deftest availability
  (let [weekdays ["BEGIN:VAVAILABILITY" "UID:hus" "DTSTAMP:20261001T000000Z"
                  "BEGIN:AVAILABLE" "UID:hus-1" "DTSTAMP:20261001T000000Z"
                  "DTSTART;TZID=Europe/Copenhagen:20261005T080000"
                  "DTEND;TZID=Europe/Copenhagen:20261005T220000"
                  "RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR" "END:AVAILABLE" "END:VAVAILABILITY"]
        closed   ["BEGIN:VAVAILABILITY" "UID:closed" "DTSTAMP:20261001T000000Z" "PRIORITY:5"
                  "BUSYTYPE:BUSY" "DTSTART:20261006T000000Z" "DTEND:20261007T000000Z"
                  "END:VAVAILABILITY"]
        booking  ["BEGIN:VEVENT" "UID:booking" "DTSTAMP:20261001T000000Z"
                  "DTSTART;TZID=Europe/Copenhagen:20261005T180000"
                  "DTEND;TZID=Europe/Copenhagen:20261005T210000" "END:VEVENT"]]
    (testing "outside the AVAILABLE times, the time is unavailable, and events overlay it"
      (is (= {"BUSY"             [["2026-10-05T16:00:00Z" "2026-10-05T19:00:00Z"]]
              "BUSY-UNAVAILABLE" [["2026-10-05T00:00:00Z" "2026-10-05T06:00:00Z"]
                                  ["2026-10-05T20:00:00Z" "2026-10-06T00:00:00Z"]]}
             (busy (apply calendar (concat weekdays booking))
                   #inst "2026-10-05T00:00:00Z" #inst "2026-10-06T00:00:00Z"))))
    (testing "a higher priority paints over a lower one"
      (is (= {"BUSY"             [["2026-10-06T00:00:00Z" "2026-10-07T00:00:00Z"]]
              "BUSY-UNAVAILABLE" [["2026-10-05T00:00:00Z" "2026-10-05T06:00:00Z"]
                                  ["2026-10-05T20:00:00Z" "2026-10-06T00:00:00Z"]]}
             (busy (apply calendar (concat closed weekdays))
                   #inst "2026-10-05T00:00:00Z" #inst "2026-10-07T00:00:00Z"))))
    (testing "a year of hourly AVAILABLE times"
      (is (= 1 (count (busy (calendar "BEGIN:VAVAILABILITY" "UID:a" "DTSTAMP:20261001T000000Z"
                                      "DTSTART:20260101T000000Z"
                                      "BEGIN:AVAILABLE" "UID:b" "DTSTAMP:20261001T000000Z"
                                      "DTSTART:20260101T000000Z" "DTEND:20260101T003000Z"
                                      "RRULE:FREQ=HOURLY" "END:AVAILABLE" "END:VAVAILABILITY")
                            #inst "2026-01-01T00:00:00Z" #inst "2027-01-01T00:00:00Z")))))))

(deftest edge-cases
  (testing "floating times are in the zone :floating"
    (is (= {"BUSY" [["2026-10-15T08:00:00Z" "2026-10-15T09:00:00Z"]]}
           (->> (:properties (ical/free-busy (calendar "BEGIN:VEVENT" "UID:f" "DTSTART:20261015T100000"
                                                       "DTEND:20261015T110000" "END:VEVENT")
                                             #inst "2026-10-15T00:00:00Z" #inst "2026-10-16T00:00:00Z"
                                             {:now #inst "2026-10-01T00:00:00Z" :uid "fb"
                                              :floating "Europe/Copenhagen"}))
                (filter #(= :freebusy (:name %)))
                (into {} (map (fn [p] [(get-in p [:params :fbtype] "BUSY")
                                       (mapv (juxt :start :end) (:value p))])))))))
  (testing "an FBTYPE that RFC 5545 doesn't define is busy"
    (is (= {"BUSY" [["2026-10-15T10:00:00Z" "2026-10-15T12:00:00Z"]]}
           (busy (calendar "BEGIN:VFREEBUSY" "UID:f" "DTSTAMP:20261001T090000Z"
                           "FREEBUSY;FBTYPE=X-OOO:20261015T100000Z/20261015T110000Z"
                           "FREEBUSY:20261015T110000Z/20261015T120000Z" "END:VFREEBUSY")
                 #inst "2026-10-15T00:00:00Z" #inst "2026-10-16T00:00:00Z")))))
