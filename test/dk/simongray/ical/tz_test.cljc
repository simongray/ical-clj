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

(defn- inst-at
  "The inst of the date-time `s` in UTC."
  [s]
  #?(:clj (java.util.Date. (.toEpochMilli (java.time.Instant/parse s))) :cljs (js/Date. s)))

(defn- generated
  "The zone of the VTIMEZONE that tz/vtimezone makes for the zone `tzid`
  over the years `from` to `to`, written and parsed again."
  [tzid from to]
  (let [cal (ical/parse (ical/serialize {:name       :vcalendar
                                         :components [(tz/vtimezone (tz/zone tzid) tzid from to)]}))]
    (tz/zone cal tzid {:vtimezone? true})))

(deftest generated-vtimezones
  (testing "a year in which the zone skipped its rule"
    (is (= "-05:00" (tz/offset (generated "America/Port-au-Prince" 2016 2016)
                               (inst-at "2016-07-01T12:00:00Z")))))
  (testing "years after the zone gave up summer time"
    (let [z (generated "America/Mexico_City" 2021 2024)]
      (is (= "-05:00" (tz/offset z (inst-at "2021-06-01T12:00:00Z"))))
      (is (= "-06:00" (tz/offset z (inst-at "2024-06-01T12:00:00Z"))))))
  (testing "a rule on the first weekday on or after a day, Fri>=23"
    (let [z (generated "Asia/Jerusalem" 2026 2026)]
      (is (= "+03:00" (tz/offset z (inst-at "2028-03-24T12:00:00Z"))))
      (is (= "+02:00" (tz/offset z (inst-at "2029-03-22T12:00:00Z")))))))

(deftest edge-cases
  (testing "an exact duration that ends in the repeated hour ends in UTC"
    (let [cal (ical/parse (str "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:a\r\n"
                               "DTSTART;TZID=Europe/Copenhagen:20261025T023000\r\n"
                               "DURATION:PT45M\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"))]
      (is (= {:name :dtend :value "2026-10-25T01:15:00Z"}
             (ical/end cal (first (ical/components cal :vevent)))))))
  (testing "a TZID with a comma that isn't quoted"
    (let [cal (ical/parse (str "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:a\r\n"
                               "DTSTART;TZID=Eastern Standard Time, US:20261012T190000\r\n"
                               "END:VEVENT\r\nEND:VCALENDAR\r\n"))]
      (is (= {:tzid "Eastern Standard Time, US"}
             (:params (ical/property (first (ical/components cal :vevent)) :dtstart))))))
  (testing "a Windows display name with a comment after it in CLDR"
    (is (some? (tz/zone "(UTC+06:00) Astana"))))
  (testing "an offset that can't be"
    (is (nil? (tz/zone "+99:99"))))
  (let [vtimezone (fn [& lines]
                    (ical/parse (apply str (map #(str % "\r\n")
                                                (concat ["BEGIN:VCALENDAR" "BEGIN:VTIMEZONE" "TZID:X"]
                                                        lines
                                                        ["END:VTIMEZONE" "END:VCALENDAR"])))))]
    (testing "a VTIMEZONE's rules go on for centuries, and repeat every 400 years"
      (let [cal (vtimezone "BEGIN:STANDARD" "DTSTART:16010101T030000" "TZOFFSETFROM:+0200"
                           "TZOFFSETTO:+0100" "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10" "END:STANDARD"
                           "BEGIN:DAYLIGHT" "DTSTART:16010101T020000" "TZOFFSETFROM:+0100"
                           "TZOFFSETTO:+0200" "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=3" "END:DAYLIGHT")
            z   (tz/zone cal "X")]
        (is (= "+02:00" (tz/offset z (inst-at "9999-07-01T12:00:00Z"))))
        (is (= "+01:00" (tz/offset z (inst-at "9999-12-01T12:00:00Z"))))))
    (testing "a local UNTIL, which some write, is local time"
      (let [cal (vtimezone "BEGIN:DAYLIGHT" "DTSTART:19870405T020000" "TZOFFSETFROM:-0500"
                           "TZOFFSETTO:-0400" "RRULE:FREQ=YEARLY;BYDAY=1SU;BYMONTH=4;UNTIL=20060402T020000"
                           "END:DAYLIGHT"
                           "BEGIN:STANDARD" "DTSTART:19671029T020000" "TZOFFSETFROM:-0400"
                           "TZOFFSETTO:-0500" "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10" "END:STANDARD")]
        (is (= "-04:00" (tz/offset (tz/zone cal "X") (inst-at "2006-07-01T12:00:00Z"))))))
    (testing "a rule more frequent than monthly is left out"
      (let [cal (vtimezone "BEGIN:STANDARD" "DTSTART:21500101T000000" "RRULE:FREQ=HOURLY"
                           "TZOFFSETFROM:+0100" "TZOFFSETTO:+0100" "END:STANDARD")]
        (is (= "+01:00" (tz/offset (tz/zone cal "X") (inst-at "2160-01-01T00:00:00Z"))))))))
