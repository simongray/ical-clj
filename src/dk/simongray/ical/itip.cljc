(ns dk.simongray.ical.itip
  "Build the scheduling messages of iTIP (RFC 5546) from a calendar, and
  apply those you receive to a stored one. A message is a calendar with a
  METHOD.

  The namespace sends nothing. For e-mail by iMIP (RFC 6047),
  content-type gives a message's MIME type and originator the address it
  should come from, which the receiver checks against the mail's verified
  sender (RFC 6047, section 3)."
  (:refer-clojure :exclude [apply])
  (:require [clojure.string :as str]
            [dk.simongray.ical.impl.civil :as civil]
            [dk.simongray.ical.impl.recurrence :as recurrence]
            [dk.simongray.ical.tz :as tz]))

(def ^:private scheduled
  "The components that iTIP schedules."
  #{:vevent :vtodo :vjournal :vfreebusy})

(defn- prop
  "The first property named `k` of the component `c`."
  [c k]
  (some #(when (= k (:name %)) %) (:properties c)))

(defn- value
  "The value of the first property named `k` of the component `c`."
  [c k]
  (:value (prop c k)))

(defn- set-property
  "The component `c` with the property `p` in place of those of its name,
  or added at the end."
  [c p]
  (let [k  (:name p)
        ps (vec (:properties c))
        i  (first (keep-indexed #(when (= k (:name %2)) %1) ps))]
    (assoc c :properties
           (if i
             (into (assoc (subvec ps 0 (inc i)) i p)
                   (remove #(= k (:name %)) (subvec ps (inc i))))
             (conj ps p)))))

(defn- remove-properties
  "The component `c` without its properties that `pred` is true of."
  [c pred]
  (update c :properties #(vec (remove pred %))))

(defn- dtstamp
  "The DTSTAMP of the inst `now`, in UTC (RFC 5545, section 3.8.7.2)."
  [now]
  {:name :dtstamp :value (civil/date-time-text (civil/floor-div (inst-ms now) 1000) true)})

(defn- now-of
  "The inst :now of `opts`, or the current time."
  [opts]
  (or (:now opts) #?(:clj (java.util.Date.) :cljs (js/Date.))))

(defn- update-scheduled
  "The calendar `cal` with `f` applied to each of its scheduled
  components."
  [cal f]
  (update cal :components (fn [cs] (mapv #(if (scheduled (:name %)) (f %) %) cs))))

;; RFC 5546, section 1.4: the METHOD of an iCalendar object makes it a
;; scheduling message
(defn- message
  "The calendar `cal` as a message of the `method`: with its METHOD, a
  DTSTAMP of now in each scheduled component, and a VTIMEZONE for each
  TZID it uses."
  [cal method opts]
  (let [stamp (dtstamp (now-of opts))]
    (-> cal
        (set-property {:name :method :value method})
        (update-scheduled #(set-property % stamp))
        tz/with-timezones)))

(defn address
  "The calendar user address `s` (RFC 5545, section 3.3.3) without mailto:
  and in lower case, so that two addresses compare."
  [s]
  (str/lower-case (str/replace-first (str/trim (str s)) #"(?i)^mailto:" "")))

(defn- attendee?
  "Whether the property `p` is an ATTENDEE with the address `a`."
  [a p]
  (and (= :attendee (:name p)) (= (address a) (address (:value p)))))

;; RFC 5546, section 3.2.1: a published component has no attendees
(defn publish
  "The calendar `cal` as a PUBLISH message, without attendees. The `opts`
  can give :now, the inst to use for its DTSTAMPs."
  ([cal]
   (publish cal {}))
  ([cal opts]
   (-> (update-scheduled cal #(remove-properties % (comp #{:attendee} :name)))
       (message "PUBLISH" opts))))

;; RFC 5546, section 3.2.2
(defn request
  "The calendar `cal` as a REQUEST message, which an organizer sends to
  invite the attendees or to update the event. The `opts` can give :now,
  the inst to use for its DTSTAMPs."
  ([cal]
   (request cal {}))
  ([cal opts]
   (message cal "REQUEST" opts)))

;; RFC 5546, section 3.2.3: a reply has the one attendee who replies,
;; and what identifies the component
(defn reply
  "The REPLY from the attendee with the address `attendee` to the calendar
  `cal`, with the participation status `partstat`, e.g. \"ACCEPTED\",
  \"DECLINED\" or \"TENTATIVE\". Each scheduled component keeps only its
  UID, RECURRENCE-ID, SEQUENCE, DTSTART and ORGANIZER, and the attendee,
  who is added if the organizer didn't invite them. The `opts` can give
  :now, the inst to use for its DTSTAMPs.

      (reply cal \"mailto:simon@gf.example\" \"ACCEPTED\")"
  ([cal attendee partstat]
   (reply cal attendee partstat {}))
  ([cal attendee partstat opts]
   (let [kept #{:uid :recurrence-id :sequence :dtstart :organizer}
         one  (fn [c]
                (let [found (some #(when (attendee? attendee %) %) (:properties c))
                      a     (-> (or found {:name :attendee :value attendee})
                                (update :params #(-> % (dissoc :rsvp) (assoc :partstat partstat))))]
                  (-> (remove-properties c #(not (kept (:name %))))
                      (update :properties conj a)
                      (dissoc :components))))]
     (-> (update-scheduled cal one)
         (message "REPLY" opts)))))

(defn- sequence-of
  "The SEQUENCE of the component `c`, 0 when it has none."
  [c]
  (let [n (value c :sequence)]
    (if (number? n) n 0)))

;; RFC 5546, section 3.2.5: a cancellation increments SEQUENCE
(defn cancel
  "The calendar `cal` as a CANCEL message, with STATUS:CANCELLED and an
  incremented SEQUENCE in each scheduled component. With the addresses
  `attendees`, it's instead the message that removes those attendees from
  the event: it lists only them, and leaves STATUS alone. The `opts` can
  give :now, the inst to use for its DTSTAMPs."
  ([cal]
   (cancel cal nil {}))
  ([cal attendees]
   (cancel cal attendees {}))
  ([cal attendees opts]
   (-> (update-scheduled
        cal
        (fn [c]
          (cond-> (set-property c {:name :sequence :value (inc (sequence-of c))})
            (nil? attendees) (set-property {:name :status :value "CANCELLED"})
            attendees        (remove-properties #(and (= :attendee (:name %))
                                                      (not-any? (fn [a] (attendee? a %)) attendees))))))
       (message "CANCEL" opts))))

(def ^:private significant
  "The properties whose change makes the organizer increment SEQUENCE
  (RFC 5546, section 2.1.4)."
  [:dtstart :dtend :duration :due :rrule :rdate :exdate :status])

(defn- comparable
  "The properties named `k` of the component `c`, in a form where equal
  ones are equal: a rule without its defaults, and lists as sets."
  [c k]
  (set (for [p     (:properties c)
             :when (= k (:name p))]
         [(:params p)
          (let [v (:value p)]
            (cond
              (map? v)        (cond-> v
                                (= 1 (:interval v))    (dissoc :interval)
                                (= "MO" (:wkst v))     (dissoc :wkst))
              (sequential? v) (set v)
              :else           v))])))

(defn significant-change?
  "Whether the component `new` changes a property of `old` that calls for
  a new SEQUENCE, by RFC 5546, section 2.1.4: DTSTART, DTEND, DURATION,
  DUE, RRULE, RDATE, EXDATE or STATUS."
  [old new]
  (boolean (some #(not= (comparable old %) (comparable new %)) significant)))

(defn- identity-of
  "The name, the UID and the recurrence id of the component `c` in the
  calendar `cal`, with the recurrence id as an instant when it has one."
  [cal c]
  [(:name c) (value c :uid)
   (when-let [p (prop c :recurrence-id)]
     (or (recurrence/instant-of cal p {:floating {:tzid "UTC" :offset 0}})
         (:value p)))])

(defn revise
  "The calendar `new` with a higher SEQUENCE in each scheduled component
  that changes significantly from its version in the calendar `old` (see
  significant-change?). The new SEQUENCE is one more than the higher of
  the two, and a component that `old` lacks keeps its own."
  [old new]
  (let [olds (into {} (for [c (:components old) :when (scheduled (:name c))]
                        [(identity-of old c) c]))]
    (update-scheduled new (fn [c]
                            (let [o (get olds (identity-of new c))]
                              (if (and o (significant-change? o c))
                                (set-property c {:name  :sequence
                                                 :value (inc (max (sequence-of o) (sequence-of c)))})
                                c))))))

;; RFC 5546, section 2.1.5: of two versions of the same component, the
;; one with the higher SEQUENCE is newer, and then the one with the later
;; DTSTAMP
(defn newer?
  "Whether the component `a` is a newer version than the component `b`."
  [a b]
  (let [sa (sequence-of a)
        sb (sequence-of b)]
    (or (> sa sb)
        (and (= sa sb) (pos? (compare (str (value a :dtstamp)) (str (value b :dtstamp))))))))

(defn- replace-components
  "The calendar `cal` with `cs` in place of the components that match
  `pred`, where the first of them was, or at the end."
  [cal pred cs]
  (let [old (:components cal)
        i   (first (keep-indexed #(when (pred %2) %1) old))
        kept (vec (remove pred old))
        at  (if i (count (filter (complement pred) (take i old))) (count kept))]
    (assoc cal :components (into (conj (subvec kept 0 at)) (concat cs (subvec kept at))))))

(defn- with-partstat
  "The component `c` with the ATTENDEE `a` of a reply in place of its
  attendee with the same address, or added."
  [c a]
  (if (some #(attendee? (:value a) %) (:properties c))
    (update c :properties (fn [ps] (mapv #(if (attendee? (:value a) %)
                                            (update % :params merge (:params a))
                                            %)
                                         ps)))
    (update c :properties conj a)))

(defn- instance-for
  "The instance of a recurring component of the calendar `cal` whose
  recurrence id is that of the component `m`, or nil."
  [cal m]
  (let [[k uid rid] (identity-of cal m)
        master      (some #(when (and (= k (:name %)) (= uid (value % :uid))
                                      (nil? (prop % :recurrence-id)))
                             %)
                          (:components cal))]
    (when (and master (number? rid))
      (->> (recurrence/occurrences cal master {})
           (take-while #(<= (or (first (drop 2 (identity-of cal %))) 0) rid))
           (some #(when (= rid (nth (identity-of cal %) 2)) %))))))

;; RFC 5546, section 3.2.3: the organizer takes an attendee's status from
;; a reply
(defn- apply-reply
  "The stored calendar `cal` with the attendee of the reply component `m`."
  [cal m]
  (let [id (identity-of cal m)
        a  (prop m :attendee)
        s  (some #(when (= id (identity-of cal %)) %) (:components cal))]
    (cond
      (nil? a)                         cal
      (and s (< (sequence-of m) (sequence-of s))) cal
      s                                (replace-components cal #(= id (identity-of cal %))
                                                           [(with-partstat s a)])
      :else                            (if-let [i (instance-for cal m)]
                                         (update cal :components conj (with-partstat i a))
                                         cal))))

(defn- apply-update
  "The stored calendar `cal` with the components of the message `msg`
  with the same UID as the component `m`, when they're newer. A master
  component replaces all the stored ones with its UID."
  [cal msg m]
  (let [[k uid rid] (identity-of msg m)
        stored      (filter #(= [k uid] (take 2 (identity-of cal %))) (:components cal))
        same        (some #(when (= (identity-of cal %) (identity-of msg m)) %) stored)]
    (cond
      (and same (not (newer? m same)))
      cal

      (nil? rid)
      (replace-components cal #(= [k uid] (take 2 (identity-of cal %)))
                          (filter #(= [k uid] (take 2 (identity-of msg %))) (:components msg)))

      :else
      (replace-components cal #(= (identity-of cal %) (identity-of msg m)) [m]))))

;; RFC 5546, section 3.2.5: a cancelled component keeps its place, with
;; its STATUS
(defn- apply-cancel
  "The stored calendar `cal` with the component `m` of a CANCEL message
  in place of its stored version, when it's newer."
  [cal m]
  (let [id   (identity-of cal m)
        same (some #(when (= id (identity-of cal %)) %) (:components cal))]
    (cond
      (and same (not (newer? m same))) cal
      same                             (replace-components cal #(= id (identity-of cal %)) [m])
      :else                            (update cal :components conj m))))

(defn apply
  "The stored calendar `cal` with the message `msg` applied to it, by the
  message's method:

  - REPLY: the organizer's copy takes the attendee's PARTSTAT, unless the
    reply is to an older SEQUENCE. A reply to one instance adds an
    override for it.
  - REQUEST, PUBLISH or ADD: the newer components (see newer?) replace
    the stored ones, and a new master replaces all the stored components
    with its UID.
  - CANCEL: the cancelled components, with their STATUS, replace the
    stored ones or are added.

  Any other method leaves `cal` unchanged."
  [cal msg]
  (let [method (str/upper-case (str (value msg :method)))
        ms     (filter #(scheduled (:name %)) (:components msg))]
    (case method
      "REPLY"                       (reduce apply-reply cal ms)
      ("REQUEST" "PUBLISH" "ADD")   (reduce #(apply-update %1 msg %2) cal ms)
      "CANCEL"                      (reduce apply-cancel cal ms)
      cal)))

;; RFC 6047, section 2.4
(defn content-type
  "The MIME type of the message `msg` in an iMIP e-mail.

      (content-type msg)
      ;; => \"text/calendar; method=REQUEST; charset=UTF-8\""
  [msg]
  (str "text/calendar; method=" (str/upper-case (str (value msg :method))) "; charset=UTF-8"))

;; RFC 6047, section 2.2.1 and RFC 5546, section 1.3: an attendee sends
;; a REPLY, a REFRESH or a COUNTER, and the organizer the others
(defn originator
  "The calendar user address that the message `msg` should come from: the
  ATTENDEE for a REPLY, a REFRESH or a COUNTER, and the ORGANIZER for any
  other method."
  [msg]
  (let [c (some #(when (scheduled (:name %)) %) (:components msg))]
    (if (#{"REPLY" "REFRESH" "COUNTER"} (str/upper-case (str (value msg :method))))
      (value c :attendee)
      (value c :organizer))))
