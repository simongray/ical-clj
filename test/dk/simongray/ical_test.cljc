(ns dk.simongray.ical-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dk.simongray.ical :as ical]))

(defn crlf
  "The `lines` joined as an iCalendar text, each ending with CRLF."
  [& lines]
  (apply str (map #(str % "\r\n") lines)))

(def calendar
  (crlf "BEGIN:VCALENDAR"
        "VERSION:2.0"
        "PRODID:-//ical-clj//test//EN"
        "BEGIN:VEVENT"
        "UID:bestyrelse@gf.example"
        "DTSTAMP:20261001T090000Z"
        "DTSTART;TZID=Europe/Copenhagen:20261012T190000"
        "DURATION:PT2H"
        "RRULE:FREQ=MONTHLY;BYDAY=2MO"
        "EXDATE;TZID=Europe/Copenhagen:20261214T190000"
        "SUMMARY;LANGUAGE=da:Bestyrelsesmøde"
        "ATTENDEE;CN=Simon Gray;PARTSTAT=ACCEPTED:mailto:simon@gf.example"
        "X-MICROSOFT-CDO-BUSYSTATUS:BUSY"
        "BEGIN:VALARM"
        "ACTION:DISPLAY"
        "TRIGGER:-PT15M"
        "DESCRIPTION:Bestyrelsesmøde"
        "END:VALARM"
        "END:VEVENT"
        "END:VCALENDAR"))

(deftest the-model
  (testing "the VEVENT of the proposal"
    (is (= {:name       :vevent
            :properties [{:name :uid :value "bestyrelse@gf.example"}
                         {:name :dtstamp :value "2026-10-01T09:00:00Z"}
                         {:name   :dtstart
                          :params {:tzid "Europe/Copenhagen"}
                          :value  "2026-10-12T19:00:00"}
                         {:name :duration :value "PT2H"}
                         {:name :rrule :value {:freq "MONTHLY" :byday ["2MO"]}}
                         {:name   :exdate
                          :params {:tzid "Europe/Copenhagen"}
                          :value  ["2026-12-14T19:00:00"]}
                         {:name   :summary
                          :params {:language "da"}
                          :value  "Bestyrelsesmøde"}
                         {:name   :attendee
                          :params {:cn "Simon Gray" :partstat "ACCEPTED"}
                          :value  "mailto:simon@gf.example"}
                         {:name :x-microsoft-cdo-busystatus :value "BUSY"}]
            :components [{:name       :valarm
                          :properties [{:name :action :value "DISPLAY"}
                                       {:name :trigger :value "-PT15M"}
                                       {:name :description :value "Bestyrelsesmøde"}]}]}
           (first (ical/components (ical/parse calendar) :vevent)))))
  (testing "written back as it came"
    (is (= calendar (ical/serialize (ical/parse calendar))))))

(deftest values
  (let [prop (fn [line]
               (-> (ical/parse (crlf "BEGIN:VEVENT" line "END:VEVENT"))
                   :properties
                   first))]
    (testing "dates and times, with VALUE or without"
      (is (= {:name :dtstart :type :date :value "2026-10-12"}
             (prop "DTSTART;VALUE=DATE:20261012")
             (prop "DTSTART:20261012")))
      (is (= {:name :trigger :type :date-time :value "2026-10-12T17:00:00Z"}
             (prop "TRIGGER;VALUE=DATE-TIME:20261012T170000Z")))
      (is (= {:name :tzoffsetfrom :value "+01:00"} (prop "TZOFFSETFROM:+0100")))
      (is (= {:name :dtstamp :value "2016-12-31T23:59:60Z"}
             (prop "DTSTAMP:20161231T235960Z"))
          "a leap second"))
    (testing "periods, lists and parts"
      (is (= {:name :freebusy
              :value [{:start "1997-03-08T16:00:00Z" :end "1997-03-08T20:00:00Z"}
                      {:start "1997-03-08T16:00:00Z" :duration "PT8H30M"}]}
             (prop "FREEBUSY:19970308T160000Z/19970308T200000Z,19970308T160000Z/PT8H30M")))
      (is (= {:name :categories :value ["a,b" "c"]} (prop "CATEGORIES:a\\,b,c")))
      (is (= {:name :geo :value [37.386013 -122.082932]}
             (prop "GEO:37.386013;-122.082932")))
      (is (= {:name :request-status :value ["3.1" "Invalid property value" "DTSTART:96-Apr-01"]}
             (prop "REQUEST-STATUS:3.1;Invalid property value;DTSTART:96-Apr-01"))))
    (testing "text, escaped"
      (is (= {:name :summary :value "a, b; c\\d\ne"}
             (prop "SUMMARY:a\\, b\\; c\\\\d\\ne"))))
    (testing "recurrence rules"
      (is (= {:name :rrule :value {:freq "YEARLY" :until "2000-01-31T14:00:00Z" :bymonth [1]
                                   :byday ["SU" "-1MO"] :x-name "v"}}
             (prop "RRULE:FREQ=YEARLY;UNTIL=20000131T140000Z;BYMONTH=1;BYDAY=SU,-1MO;X-NAME=v"))))
    (testing "what doesn't read keeps its text"
      (is (= {:name :dtstart :type :unknown :value "20261332T190000"}
             (prop "DTSTART:20261332T190000")))
      (is (= {:name :dtstart :params {:value "DATE"} :type :unknown :value "x"}
             (prop "DTSTART;VALUE=DATE:x")))
      (is (= {:name :rrule :type :unknown :value "BYDAY=MO"} (prop "RRULE:BYDAY=MO")))
      (is (= {:name :x-thing :type :unknown :value "geo:55.6,12.5"}
             (prop "X-THING:geo:55.6,12.5")))
      (is (= {:name :x-wr-calname :value "Arbejde, privat"}
             (prop "X-WR-CALNAME:Arbejde\\, privat"))))))

(deftest parameters
  (let [params (fn [line]
                 (-> (ical/parse (crlf "BEGIN:VEVENT" line "END:VEVENT"))
                     :properties first :params))]
    (is (= {:cn "Gray, Simon" :partstat "ACCEPTED"}
           (params "ATTENDEE;CN=\"Gray, Simon\";PARTSTAT=ACCEPTED:mailto:s@x.dk")))
    (is (= {:delegated-to ["mailto:a@x.dk" "mailto:b@x.dk"]}
           (params "ATTENDEE;DELEGATED-TO=\"mailto:a@x.dk\",\"mailto:b@x.dk\":mailto:c@x.dk")))
    (testing "RFC 6868"
      (is (= {:x-address "Vej 1\n2000 \"F\""}
             (params "LOCATION;X-ADDRESS=Vej 1^n2000 ^'F^':here"))))))

(deftest leniency
  (testing "LF line ends, folds, junk and unclosed components"
    (let [cal (ical/parse (str "junk\nBEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:a\n"
                               " b\nnot a line\nEND:VTODO\n"))]
      (is (= {:name       :vcalendar
              :components [{:name :vevent :properties [{:name :summary :value "ab"}]}]}
             cal))))
  (testing "a text with no component"
    (is (nil? (ical/parse "nothing here"))))
  (testing "a byte order mark"
    (is (= {:name :vcalendar :properties [{:name :version :value "2.0"}]}
           (ical/parse "﻿BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n"))))
  (testing "names with a solidus, which read as keywords with a namespace"
    (let [s (crlf "BEGIN:VCALENDAR;TZID=EUROPE/COPENHAGEN" "X-A;X-P/Q=1:b"
                  "END:VCALENDAR;TZID=EUROPE/COPENHAGEN")]
      (is (= s (ical/serialize (ical/parse s)))))))

(defn utf-8
  "The bytes of the string `s` in UTF-8, a byte array or a Uint8Array."
  [s]
  #?(:clj (.getBytes ^String s "UTF-8") :cljs (.encode (js/TextEncoder.) s)))

(defn splice
  "The bytes `a` with the bytes `b` inserted at the index `i`."
  [a b i]
  #?(:clj  (byte-array (concat (take i a) b (drop i a)))
     :cljs (js/Uint8Array. (clj->js (concat (take i (array-seq a)) (array-seq b)
                                            (drop i (array-seq a)))))))

(deftest utf-8-bytes
  (let [text   (crlf "BEGIN:VEVENT" "SUMMARY:Bestyrelsesmøde" "END:VEVENT")
        bs     (utf-8 text)
        ;; a fold between the two bytes of ø
        split  (+ (count "BEGIN:VEVENT\r\nSUMMARY:Bestyrelsesm") 1)
        folded (splice bs (utf-8 "\r\n ") split)]
    (testing "bytes of UTF-8 parse as their text does"
      (is (= (ical/parse text) (ical/parse bs))))
    (testing "a fold that splits a character keeps it whole"
      (is (= "Bestyrelsesmøde" (ical/value (ical/parse folded) :summary))))))

(deftest folding
  (let [summary (apply str (repeat 100 "ø€😀"))
        text    (ical/serialize {:name       :vevent
                                 :properties [{:name :summary :value summary}]})]
    (is (every? #(<= #?(:clj  (count (.getBytes ^String % "UTF-8"))
                        :cljs (.-length (.encode (js/TextEncoder.) %)))
                     75)
                (str/split-lines text)))
    (is (= summary (ical/value (ical/parse text) :summary)))))

(deftest edge-cases
  (testing "a line break in a value that isn't text starts no property"
    (is (= (crlf "BEGIN:VEVENT" "URL:https://x.example/ATTACH:https://evil.example/" "END:VEVENT")
           (ical/serialize {:name       :vevent
                            :properties [{:name  :url
                                          :value "https://x.example/\r\nATTACH:https://evil.example/"}]}))))
  (testing "components nest 64 deep at most, and unmatched ENDs are quick"
    (is (= 64 (count (take-while some? (iterate #(first (:components %))
                                                (ical/parse (apply str (repeat 1000 "BEGIN:X-A\r\n"))))))))
    (is (= 1 (count (ical/parse-all (str (apply str (repeat 5000 "BEGIN:X-A\r\n"))
                                         (apply str (repeat 5000 "END:X-B\r\n"))))))))
  (testing "CRs before a LF make one line break, in text and in bytes"
    (let [s "BEGIN:VEVENT\r\r\nSUMMARY:the budget for next\r\r\n  year\r\r\nEND:VEVENT\r\r\n"]
      (is (= "the budget for next year" (ical/value (ical/parse s) :summary)))
      (is (= "the budget for next year" (ical/value (ical/parse (utf-8 s)) :summary)))))
  (testing "a byte order mark at the start of an appended calendar"
    (let [one (crlf "BEGIN:VCALENDAR" "VERSION:2.0" "END:VCALENDAR")]
      (is (= [:vcalendar :vcalendar] (mapv :name (ical/parse-all (str "﻿" one "﻿" one)))))))
  (testing "what follows a quoted parameter value belongs to it"
    (is (= {:cn "Simon " :partstat "ACCEPTED"}
           (:params (ical/property (ical/parse (crlf "BEGIN:VEVENT"
                                                     "ATTENDEE;CN=\"Simon\" ;PARTSTAT=ACCEPTED:mailto:s@x"
                                                     "END:VEVENT"))
                                   :attendee)))))
  (testing "more than eight parameters keep their order"
    (let [s (crlf "BEGIN:VEVENT"
                  "X-A;P1=1;P2=2;P3=3;P4=4;P5=5;P6=6;P7=7;P8=8;P9=9;P10=10:v"
                  "END:VEVENT")]
      (is (= s (ical/serialize (ical/parse s))))))
  (testing "numbers that don't fit a double, or JavaScript's numbers exactly, keep their text"
    (is (= :unknown (:type (ical/property (ical/parse (crlf "BEGIN:VEVENT"
                                                            (str "GEO:1" (apply str (repeat 400 "0")) ";1.5")
                                                            "END:VEVENT"))
                                          :geo))))
    (is (= "9999999999999999" (ical/value (ical/parse (crlf "BEGIN:VEVENT"
                                                            "X-A;VALUE=INTEGER:9999999999999999"
                                                            "END:VEVENT"))
                                          :x-a))))
  (testing "floats are written the same on every platform"
    (is (= (crlf "BEGIN:VEVENT" "GEO:0.00000001;0" "END:VEVENT")
           (ical/serialize {:name :vevent :properties [{:name :geo :value [1.0E-8 -0.0]}]})))))
