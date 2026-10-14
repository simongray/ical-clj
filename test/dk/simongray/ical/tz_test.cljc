(ns dk.simongray.ical.tz-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.ical :as ical]
            [dk.simongray.ical.tz :as tz]))

(deftest zones
  (testing "IANA names, ignoring case"
    (is (= {:tzid "europe/copenhagen" :iana "Europe/Copenhagen"}
           (update (tz/zone "europe/copenhagen") :iana #(when % "Europe/Copenhagen")))))
  (testing "a prefix, a Windows name and a display name"
    (is (= "2026-10-12T19:00:00"
           (tz/local (tz/zone "/mozilla.org/20050126_1/Europe/Berlin") #inst "2026-10-12T17:00:00Z")
           (tz/local (tz/zone "W. Europe Standard Time") #inst "2026-10-12T17:00:00Z")
           (tz/local (tz/zone "(UTC+01:00) Amsterdam, Berlin, Bern, Rome, Stockholm, Vienna")
                     #inst "2026-10-12T17:00:00Z"))))
  (testing "a fixed offset"
    (is (= "+02:00" (tz/offset (tz/zone "+02:00") #inst "2026-01-01T00:00:00Z"))))
  (testing "nothing"
    (is (nil? (tz/zone "Nowhere/Land")))))

(deftest gaps-and-overlaps
  (let [ny (tz/zone "America/New_York")]
    (testing "RFC 5545, section 3.3.5: a time in a gap takes the offset from before"
      (is (= #inst "2007-03-11T07:30:00Z" (tz/instant ny "2007-03-11T02:30:00"))))
    (testing "and a time that occurs twice is the first"
      (is (= #inst "2007-11-04T05:30:00Z" (tz/instant ny "2007-11-04T01:30:00"))))))

(def zone-names
  ["Europe/Copenhagen" "America/New_York" "Australia/Sydney" "Asia/Tokyo"
   "Europe/Dublin" "America/Sao_Paulo" "Asia/Kathmandu" "Pacific/Chatham"])

(deftest vtimezones
  (doseq [tzid zone-names]
    (let [platform (tz/zone tzid)
          cal      {:name :vcalendar :components [(tz/vtimezone platform tzid 2015 2030)]}
          own      (tz/zone cal tzid {:vtimezone? true})]
      (testing (str tzid ": the VTIMEZONE gives the platform's offsets")
        (is (nil? (:iana own)))
        (doseq [day (range 0 (* 20 365) 9)
                :let [at #?(:clj  (java.util.Date. (+ 1420070400000 (* day 86400000)))
                            :cljs (js/Date. (+ 1420070400000 (* day 86400000))))]]
          (is (= (tz/offset platform at) (tz/offset own at)) (str tzid " " at)))))))

(deftest with-timezones
  (let [cal (ical/parse (str "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//t//t//EN\r\n"
                             "BEGIN:VEVENT\r\nUID:a\r\nDTSTAMP:20261001T090000Z\r\n"
                             "DTSTART;TZID=Europe/Copenhagen:20261012T190000\r\n"
                             "DTEND;TZID=Nowhere:20261012T200000\r\n"
                             "END:VEVENT\r\nEND:VCALENDAR\r\n"))
        cal (tz/with-timezones cal)]
    (is (= [:vtimezone :vevent] (mapv :name (:components cal))))
    (is (= "Europe/Copenhagen" (ical/value (first (:components cal)) :tzid)))
    (is (= cal (tz/with-timezones cal)) "a second time adds nothing")))
