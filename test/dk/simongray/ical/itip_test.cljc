(ns dk.simongray.ical.itip-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.ical :as ical]
            [dk.simongray.ical.itip :as itip]))

(defn crlf
  "The `lines` joined as an iCalendar text, each ending with CRLF."
  [& lines]
  (apply str (map #(str % "\r\n") lines)))

(def calendar
  (ical/parse (crlf "BEGIN:VCALENDAR" "VERSION:2.0" "PRODID:-//t//t//EN"
                    "BEGIN:VEVENT"
                    "UID:gf-1@gf.example"
                    "DTSTAMP:20261001T090000Z"
                    "SEQUENCE:0"
                    "DTSTART;TZID=Europe/Copenhagen:20261012T190000"
                    "DTEND;TZID=Europe/Copenhagen:20261012T210000"
                    "RRULE:FREQ=MONTHLY;BYDAY=2MO;INTERVAL=1"
                    "SUMMARY:Bestyrelsesmøde"
                    "ORGANIZER;CN=Bestyrelsen:mailto:board@gf.example"
                    "ATTENDEE;RSVP=TRUE;PARTSTAT=NEEDS-ACTION:mailto:simon@gf.example"
                    "ATTENDEE;RSVP=TRUE;PARTSTAT=NEEDS-ACTION:mailto:anna@gf.example"
                    "END:VEVENT"
                    "END:VCALENDAR")))

(def now
  #inst "2026-10-02T10:00:00Z")

(defn event
  "The first VEVENT of the calendar `cal`."
  [cal]
  (first (ical/components cal :vevent)))

(deftest building
  (testing "a request has its METHOD, a new DTSTAMP and the VTIMEZONE"
    (let [msg (itip/request calendar {:now now})]
      (is (= "REQUEST" (ical/value msg :method)))
      (is (= "2026-10-02T10:00:00Z" (ical/value (event msg) :dtstamp)))
      (is (= [:vtimezone :vevent] (mapv :name (:components msg))))
      (is (ical/valid? msg))))
  (testing "a published event has no attendees"
    (is (empty? (ical/properties (event (itip/publish calendar {:now now})) :attendee))))
  (testing "a reply keeps the one attendee, whatever case the address is in"
    (let [msg (itip/reply calendar "MAILTO:Simon@gf.example" "ACCEPTED" {:now now})]
      (is (= [{:name :attendee :params {:partstat "ACCEPTED"} :value "mailto:simon@gf.example"}]
             (ical/properties (event msg) :attendee)))
      (is (= #{:uid :sequence :dtstart :organizer :attendee :dtstamp}
             (set (map :name (:properties (event msg))))))
      (is (= "mailto:simon@gf.example" (itip/originator msg)))
      (is (= "text/calendar; method=REPLY; charset=UTF-8" (itip/content-type msg)))))
  (testing "a cancellation increments SEQUENCE"
    (let [msg (itip/cancel calendar nil {:now now})]
      (is (= ["CANCELLED" 1] ((juxt #(ical/value % :status) #(ical/value % :sequence))
                              (event msg))))
      (is (= "mailto:board@gf.example" (itip/originator msg)))))
  (testing "removing one attendee"
    (let [msg (itip/cancel calendar ["mailto:anna@gf.example"] {:now now})]
      (is (= ["mailto:anna@gf.example"] (map :value (ical/properties (event msg) :attendee))))
      (is (nil? (ical/value (event msg) :status))))))

(deftest revising
  (let [moved   (update-in calendar [:components 0 :properties 3] assoc :value "2026-10-12T20:00:00")
        renamed (update-in calendar [:components 0 :properties 6] assoc :value "Møde")
        same    (update-in calendar [:components 0 :properties 5] assoc :value {:freq "MONTHLY" :byday ["2MO"]})]
    (is (= 1 (ical/value (event (itip/revise calendar moved)) :sequence)))
    (is (= 0 (ical/value (event (itip/revise calendar renamed)) :sequence)))
    (is (= 0 (ical/value (event (itip/revise calendar same)) :sequence))
        "INTERVAL=1 is the default")))

(deftest applying
  (testing "the organizer takes the attendee's status from a reply"
    (let [stored (itip/apply calendar (itip/reply calendar "mailto:simon@gf.example" "DECLINED"))]
      (is (= ["DECLINED" "NEEDS-ACTION"]
             (map (comp :partstat :params) (ical/properties (event stored) :attendee))))))
  (testing "a reply to one instance makes an override"
    (let [instance (second (ical/occurrences calendar (event calendar)))
          msg      (itip/reply (assoc calendar :components [instance])
                               "mailto:anna@gf.example" "TENTATIVE")
          stored   (itip/apply calendar msg)
          override (second (ical/components stored :vevent))]
      (is (= 2 (count (ical/components stored :vevent))))
      (is (= "2026-11-09T19:00:00" (ical/value override :recurrence-id)))
      (is (= "TENTATIVE" (some #(when (= "mailto:anna@gf.example" (:value %))
                                  (get-in % [:params :partstat]))
                               (ical/properties override :attendee))))))
  (testing "an attendee takes a newer request, and not an older one"
    (let [newer  (itip/request (itip/revise calendar
                                            (update-in calendar [:components 0 :properties 3]
                                                       assoc :value "2026-10-12T20:00:00"))
                               {:now now})
          stored (itip/apply calendar newer)]
      (is (= "2026-10-12T20:00:00" (ical/value (event stored) :dtstart)))
      (is (= stored (itip/apply stored (itip/request calendar {:now now}))))))
  (testing "a cancellation"
    (is (= "CANCELLED" (ical/value (event (itip/apply calendar (itip/cancel calendar))) :status)))))

(deftest newer
  (let [v #(hash-map :properties [{:name :sequence :value %1} {:name :dtstamp :value %2}])]
    (is (itip/newer? (v 1 "2026-10-01T00:00:00Z") (v 0 "2026-10-02T00:00:00Z")))
    (is (itip/newer? (v 1 "2026-10-02T00:00:00Z") (v 1 "2026-10-01T00:00:00Z")))
    (is (not (itip/newer? (v 1 "2026-10-01T00:00:00Z") (v 1 "2026-10-01T00:00:00Z"))))))
