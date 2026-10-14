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

(deftest restriction-tables
  (testing "every message that the namespace builds keeps to its table"
    (doseq [msg [(itip/publish calendar {:now now})
                 (itip/request calendar {:now now})
                 (itip/reply calendar "mailto:simon@gf.example" "ACCEPTED" {:now now})
                 (itip/cancel calendar nil {:now now})
                 (itip/cancel calendar ["mailto:anna@gf.example"] {:now now})
                 (itip/refresh calendar "mailto:anna@gf.example" {:now now})
                 (itip/counter calendar {:now now})
                 (itip/decline-counter (itip/counter calendar) {:now now})
                 (itip/add calendar {:now now})]]
      (is (= [] (itip/validation-errors msg)) (ical/value msg :method))))
  (testing "a refresh has no DTSTART, and an ADD no rule"
    (is (nil? (ical/property (event (itip/refresh calendar "mailto:anna@gf.example")) :dtstart)))
    (is (nil? (ical/property (event (itip/add calendar)) :rrule))))
  (testing "what a message breaks"
    (let [msg (-> (itip/reply calendar "mailto:simon@gf.example" "ACCEPTED" {:now now})
                  (update-in [:components 1 :properties] conj
                             {:name :attendee :value "mailto:anna@gf.example"}))]
      (is (= [{:error :repeated-property :level :error :path [:components 1] :property :attendee}]
             (itip/validation-errors msg))))
    (is (= [{:error :missing-property :level :error :path [] :property :method}]
           (itip/validation-errors calendar)))))

(deftest deciding-who-gets-what
  (let [moved   (update-in calendar [:components 0 :properties 3] assoc :value "2026-10-12T20:00:00")
        renamed (update-in calendar [:components 0 :properties 6] assoc :value "Møde")
        no-anna (update-in calendar [:components 0 :properties] #(vec (remove (fn [p] (= "mailto:anna@gf.example" (:value p))) %)))
        summary #(mapv (juxt :method :recipient :significant?) %)]
    (testing "the organizer requests a new event from every attendee"
      (is (= [["REQUEST" "mailto:anna@gf.example" true] ["REQUEST" "mailto:simon@gf.example" true]]
             (summary (itip/messages nil calendar "mailto:board@gf.example")))))
    (testing "a change is significant when it moves the event"
      (is (= [true true] (map :significant? (itip/messages calendar moved "mailto:board@gf.example"))))
      (is (= [false false] (map :significant? (itip/messages calendar renamed "mailto:board@gf.example")))))
    (testing "an attendee who was removed gets a CANCEL"
      (is (= [["REQUEST" "mailto:simon@gf.example" false] ["CANCEL" "mailto:anna@gf.example" true]]
             (summary (itip/messages calendar no-anna "mailto:board@gf.example")))))
    (testing "deleting the event cancels it for everyone"
      (is (= ["CANCEL" "CANCEL"] (map :method (itip/messages calendar nil "mailto:board@gf.example")))))
    (testing "nothing changed, nothing to send"
      (is (= [] (itip/messages calendar calendar "mailto:board@gf.example"))))
    (testing "an attendee replies when their status changes"
      (let [accepted (update-in calendar [:components 0 :properties 8 :params] assoc :partstat "ACCEPTED")
            [msg]    (itip/messages calendar accepted "mailto:simon@gf.example")]
        (is (= ["REPLY" "mailto:board@gf.example"] ((juxt :method :recipient) msg)))
        (is (= [] (itip/validation-errors (:calendar msg))))
        (is (= "ACCEPTED" (get-in (ical/property (event (:calendar msg)) :attendee) [:params :partstat])))))))

(defn- starts
  "The DTSTARTs of the instances of the calendar `cal` in the last quarter
  of 2026."
  [cal]
  (mapv #(ical/value % :dtstart)
        (filter #(= :vevent (:name %))
                (:components (ical/expand cal #inst "2026-10-01T00:00:00Z" #inst "2027-01-01T00:00:00Z")))))

(defn- with-attendee
  "The calendar `cal` with only the attendees whose addresses `keep?` is
  true of."
  [cal keep?]
  (update-in cal [:components 0 :properties]
             #(vec (remove (fn [p] (and (= :attendee (:name p)) (not (keep? (:value p))))) %))))

(deftest scheduling-edge-cases
  (testing "an ADD adds an instance and keeps the series"
    (let [extra (-> (update-in calendar [:components 0 :properties]
                               #(vec (remove (fn [p] (= :rrule (:name p))) %)))
                    (update-in [:components 0 :properties 3] assoc :value "2026-10-26T19:00:00")
                    (update-in [:components 0 :properties 4] assoc :value "2026-10-26T21:00:00"))]
      (is (= ["2026-10-12T19:00:00" "2026-10-26T19:00:00" "2026-11-09T19:00:00" "2026-12-14T19:00:00"]
             (starts (itip/apply calendar (itip/add extra {:now now})))))))
  (testing "a CANCEL that removes an attendee cancels their copy, which keeps what it held"
    (let [copy (itip/apply calendar (itip/cancel calendar ["mailto:anna@gf.example"] {:now now}))]
      (is (= "CANCELLED" (ical/value (event copy) :status)))
      (is (= "Bestyrelsesmøde" (ical/value (event copy) :summary)))))
  (testing "a CANCEL of one instance makes it a cancelled override"
    (let [msg  (ical/parse (crlf "BEGIN:VCALENDAR" "METHOD:CANCEL" "BEGIN:VEVENT" "UID:gf-1@gf.example"
                                 "DTSTAMP:20261003T090000Z" "SEQUENCE:1"
                                 "RECURRENCE-ID;TZID=Europe/Copenhagen:20261109T190000"
                                 "END:VEVENT" "END:VCALENDAR"))
          copy (itip/apply calendar msg)]
      (is (= "CANCELLED" (ical/value (last (ical/components copy :vevent)) :status)))))
  (testing "a removed attendee who's invited again gets the event back"
    (let [revised (itip/revise calendar (with-attendee calendar #(not= % "mailto:anna@gf.example")))
          [_ c]   (itip/messages calendar revised "mailto:board@gf.example" {:now now})
          anna    (itip/apply calendar (:calendar c))
          back    (itip/revise revised calendar)
          request (some #(when (= "mailto:anna@gf.example" (:recipient %)) (:calendar %))
                        (itip/messages revised back "mailto:board@gf.example"
                                       {:now #inst "2026-10-03T10:00:00Z"}))]
      (is (= [1 1] [(ical/value (event revised) :sequence) (ical/value (event (:calendar c)) :sequence)]))
      (is (nil? (ical/value (event (itip/apply anna request)) :status)))))
  (testing "a message's VTIMEZONEs come along"
    (let [msg (ical/parse (crlf "BEGIN:VCALENDAR" "METHOD:REQUEST"
                                "BEGIN:VTIMEZONE" "TZID:Customized Time Zone" "BEGIN:STANDARD"
                                "DTSTART:16010101T000000" "TZOFFSETFROM:+0100" "TZOFFSETTO:+0100"
                                "END:STANDARD" "END:VTIMEZONE"
                                "BEGIN:VEVENT" "UID:x" "DTSTAMP:20261001T090000Z"
                                "DTSTART;TZID=Customized Time Zone:20261015T180000" "END:VEVENT"
                                "END:VCALENDAR"))]
      (is (= [:vtimezone :vevent]
             (mapv :name (:components (itip/apply {:name :vcalendar :components []} msg)))))))
  (testing "a published feed can have several UIDs"
    (is (not-any? #(= :different-uids (:error %))
                  (itip/validation-errors
                   (itip/publish (update calendar :components conj
                                         (assoc-in (event calendar) [:properties 0 :value] "other"))
                                 {:now now})))))
  (testing "a moved instance gets a higher SEQUENCE"
    (let [moved (-> (event calendar)
                    (update :properties conj {:name :recurrence-id :params {:tzid "Europe/Copenhagen"}
                                              :value "2026-11-09T19:00:00"})
                    (update :properties #(vec (remove (fn [p] (= :rrule (:name p))) %)))
                    (assoc-in [:properties 3 :value] "2026-11-10T19:00:00"))]
      (is (= [0 1] (mapv #(ical/value % :sequence)
                         (ical/components (itip/revise calendar (update calendar :components conj moved))
                                          :vevent))))))
  (testing "the organizer who cancels the event sends CANCELs"
    (is (= ["CANCEL" "CANCEL"]
           (mapv :method (itip/messages calendar
                                        (update-in calendar [:components 0 :properties] conj
                                                   {:name :status :value "CANCELLED"})
                                        "mailto:board@gf.example")))))
  (testing "someone who wasn't invited sends nothing when they delete the event"
    (is (= [] (itip/messages (itip/publish calendar {:now now}) nil "mailto:stranger@gf.example"))))
  (testing "a COUNTER doesn't tell who sent it when it has several attendees"
    (is (nil? (itip/originator (itip/counter calendar {:now now}))))))

(deftest newer
  (let [v #(hash-map :properties [{:name :sequence :value %1} {:name :dtstamp :value %2}])]
    (is (itip/newer? (v 1 "2026-10-01T00:00:00Z") (v 0 "2026-10-02T00:00:00Z")))
    (is (itip/newer? (v 1 "2026-10-02T00:00:00Z") (v 1 "2026-10-01T00:00:00Z")))
    (is (not (itip/newer? (v 1 "2026-10-01T00:00:00Z") (v 1 "2026-10-01T00:00:00Z"))))))
