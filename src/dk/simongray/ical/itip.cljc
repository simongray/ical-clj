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
            [dk.simongray.ical.impl.locale :as locale]
            [dk.simongray.ical.impl.recurrence :as recurrence]
            [dk.simongray.ical.impl.restrictions :as restrictions]
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

(defn- sequence-of
  "The SEQUENCE of the component `c`, 0 when it has none."
  [c]
  (let [n (value c :sequence)]
    (if (number? n) n 0)))

(defn- kind
  "The name of the first scheduled component of the calendar `cal`."
  [cal]
  (some #(when (scheduled (:name %)) (:name %)) (:components cal)))

(defn- table
  "The restriction table of RFC 5546, section 3, for the `method` and the
  calendar `cal`, or nil when the method doesn't schedule its component."
  [cal method]
  (get restrictions/tables [method (kind cal)]))

(defn- restrict
  "The scheduled component `c` without the properties and components that
  the restrictions `allowed` forbid, and with a SEQUENCE when they
  require one."
  [allowed c]
  (let [forbidden? #(= "0" (allowed (:name %)))
        c          (-> (remove-properties c forbidden?)
                       (update :components #(vec (remove forbidden? %))))]
    (cond-> c
      (empty? (:components c))                            (dissoc :components)
      (and (= "1" (allowed :sequence)) (not (prop c :sequence))) (set-property {:name :sequence :value 0}))))

;; RFC 5546, section 1.4: the METHOD of an iCalendar object makes it a
;; scheduling message, and section 3 says what a message of each method
;; holds
(defn- message
  "The calendar `cal` as a message of the `method`: with its METHOD, a
  DTSTAMP of now in each scheduled component and a VTIMEZONE for each TZID
  it uses, but without what the method's restriction table forbids."
  [cal method opts]
  (let [stamp (dtstamp (now-of opts))
        msg   (-> cal
                  (set-property {:name :method :value method})
                  (update-scheduled #(set-property % stamp))
                  tz/with-timezones)]
    (if-let [{:keys [calendar component]} (table msg method)]
      (-> msg
          (update :components #(vec (remove (fn [c] (= "0" (calendar (:name c)))) %)))
          (update-scheduled #(restrict component %)))
      msg)))

(defn address
  "The calendar user address `s` (RFC 5545, section 3.3.3) without mailto:
  and in lower case, so that two addresses compare."
  [s]
  (locale/lower-case (str/replace-first (str/trim (str s)) #"(?i)^mailto:" "")))

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

(defn- attendee-of
  "The ATTENDEE of the component `c` with the address `attendee`, or a new
  one."
  [c attendee]
  (or (some #(when (attendee? attendee %) %) (:properties c))
      {:name :attendee :value attendee}))

;; RFC 5546, sections 3.2.3 and 3.2.6: a reply or a refresh has the one
;; attendee who sends it, and what identifies the component
(defn- from-attendee
  "The scheduled component `c` with only what identifies it and the
  ATTENDEE property `a`."
  [c a]
  (-> (remove-properties c #(not (#{:uid :recurrence-id :sequence :dtstart :organizer} (:name %))))
      (update :properties conj a)
      (dissoc :components)))

;; RFC 5546, section 3.2.3
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
   (-> (update-scheduled cal (fn [c]
                               (from-attendee c (update (attendee-of c attendee) :params
                                                        #(-> % (dissoc :rsvp)
                                                             (assoc :partstat partstat))))))
       (message "REPLY" opts))))

;; RFC 5546, section 3.2.6
(defn refresh
  "The REFRESH from the attendee with the address `attendee`, which asks
  the organizer for the latest version of the calendar `cal`. The `opts`
  can give :now, the inst to use for its DTSTAMPs."
  ([cal attendee]
   (refresh cal attendee {}))
  ([cal attendee opts]
   (-> (update-scheduled cal #(from-attendee % (attendee-of % attendee)))
       (message "REFRESH" opts))))

;; RFC 5546, section 3.2.7: an attendee proposes a change with the whole
;; component, and doesn't increment SEQUENCE
(defn counter
  "The calendar `cal`, as an attendee would change it, as a COUNTER that
  proposes the change to the organizer. The `opts` can give :now, the inst
  to use for its DTSTAMPs."
  ([cal]
   (counter cal {}))
  ([cal opts]
   (message cal "COUNTER" opts)))

;; RFC 5546, section 3.2.8
(defn decline-counter
  "The DECLINECOUNTER with which an organizer turns down the proposal of
  the COUNTER message `msg`. The `opts` can give :now, the inst to use for
  its DTSTAMPs."
  ([msg]
   (decline-counter msg {}))
  ([msg opts]
   (message msg "DECLINECOUNTER" opts)))

;; RFC 5546, sections 2.1.4 and 3.2.4: the organizer increments SEQUENCE
;; with each ADD
(defn add
  "The calendar `cal`, with instances to add to a recurring event that
  the attendees already have, as an ADD message with an incremented
  SEQUENCE. The `opts` can give :now, the inst to use for its DTSTAMPs."
  ([cal]
   (add cal {}))
  ([cal opts]
   (-> (update-scheduled cal #(set-property % {:name :sequence :value (inc (sequence-of %))}))
       (message "ADD" opts))))

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
  (let [method (locale/upper-case (str (value msg :method)))
        ms     (filter #(scheduled (:name %)) (:components msg))]
    (case method
      "REPLY"                       (reduce apply-reply cal ms)
      ("REQUEST" "PUBLISH" "ADD")   (reduce #(apply-update %1 msg %2) cal ms)
      "CANCEL"                      (reduce apply-cancel cal ms)
      cal)))

(defn- presence-errors
  "The errors of the counts `counts` of names against the `presences` of a
  restriction table, at the `path`, where `what` is :property or
  :component."
  [counts presences path what]
  (for [[k p] presences
        :let  [n (get counts k 0)
               e (cond
                   (and (#{"1" "1+"} p) (zero? n))      :missing
                   (and (#{"1" "0 or 1"} p) (< 1 n))    :repeated
                   (and (= "0" p) (pos? n))             :not-allowed)]
        :when e]
    {:error (keyword (str (name e) "-" (name what))) :level :error :path path what k}))

;; RFC 5546, section 3
(defn validation-errors
  "The ways that the message `msg` breaks the restriction table of RFC
  5546, section 3, for its METHOD and its component, as maps like those of
  ical/validation-errors, e.g. {:error :not-allowed-property :level :error
  :path [:components 1] :property :attendee}. They come on top of the
  message's errors as a calendar, which ical/validation-errors gives."
  [msg]
  (let [method (some-> (value msg :method) str locale/upper-case)
        k      (kind msg)
        t      (table msg method)
        err    #(merge {:error %1 :level :error :path []} %2)]
    (cond
      (nil? method) [(err :missing-property {:property :method})]
      (nil? k)      [(err :missing-component {:component :vevent})]
      (nil? t)      [(err :method-not-allowed {:method method :component k})]
      :else
      (vec (concat
            (presence-errors (frequencies (map :name (:components msg))) (:calendar t) [] :component)
            (for [[i c] (map-indexed vector (:components msg))
                  :when (= k (:name c))
                  :let  [path [:components i]]
                  e     (concat
                         (presence-errors (frequencies (map :name (:properties c)))
                                          (dissoc (:component t) :valarm) path :property)
                         (presence-errors (frequencies (map :name (:components c)))
                                          (select-keys (:component t) [:valarm]) path :component))]
              e)
            ;; RFC 5546, section 3.2: all the components have the same UID
            (when (< 1 (count (distinct (for [c (:components msg) :when (= k (:name c))]
                                          (value c :uid)))))
              [(err :different-uids {})]))))))

(defn- organizer-of
  "The ORGANIZER of the first scheduled component of the calendar `cal`."
  [cal]
  (some #(when (scheduled (:name %)) (value % :organizer)) (:components cal)))

(defn- attendees-of
  "The addresses of the attendees of the scheduled components of the
  calendar `cal`, as written, without the `organizer`, by their form for
  comparing."
  [cal organizer]
  (into {} (for [c     (:components cal)
                 :when (scheduled (:name c))
                 p     (:properties c)
                 :when (and (= :attendee (:name p)) (not= (address organizer) (address (:value p))))]
             [(address (:value p)) (:value p)])))

(defn- partstats
  "The participation status of the attendee `a` in each scheduled
  component of the calendar `cal`, by the identity of the component."
  [cal a]
  (into {} (for [c     (:components cal)
                 :when (scheduled (:name c))
                 p     (:properties c)
                 :when (attendee? a p)]
             [(identity-of cal c) (get-in p [:params :partstat] "NEEDS-ACTION")])))

(defn- organizer-messages
  "The messages that the organizer sends when the calendar changes from
  `old` to `new`: a REQUEST to each attendee of `new` when anything
  changed or the attendee is new, and a CANCEL to each attendee that
  `new` doesn't have, or to all of them when `new` is nil."
  [old new organizer opts]
  (let [before  (if old (attendees-of old organizer) {})
        after   (if new (attendees-of new organizer) {})
        changed (not= old new)
        olds    (into {} (for [c (:components old) :when (scheduled (:name c))]
                           [(identity-of old c) c]))
        sig?    (boolean (or (nil? old)
                             (some #(let [o (get olds (identity-of new %))]
                                      (or (nil? o) (significant-change? o %)))
                                   (filter #(scheduled (:name %)) (:components new)))))]
    (concat
     (when (and new changed)
       (let [msg (request new opts)]
         (for [[k a] (sort after)]
           {:method "REQUEST" :recipient a :calendar msg
            :significant? (or sig? (not (contains? before k)))})))
     (when old
       (for [[k a] (sort before)
             :when (not (contains? after k))]
         {:method "CANCEL" :recipient a :calendar (cancel old (when new [a]) opts)
          :significant? true})))))

(defn- attendee-messages
  "The REPLY that the attendee `user` sends to the organizer when the
  calendar changes from `old` to `new`, with the components where their
  participation status changed, or declining them all when `new` is nil."
  [old new user organizer opts]
  (let [before (if old (partstats old user) {})]
    (if (nil? new)
      [{:method "REPLY" :recipient organizer :calendar (reply old user "DECLINED" opts)
        :significant? true}]
      (let [changed (filter #(and (scheduled (:name %))
                                  (let [id (identity-of new %)
                                        ps (get (partstats new user) id)]
                                    (and ps (not= ps (get before id)))))
                            (:components new))]
        (when (seq changed)
          [{:method       "REPLY"
            :recipient    organizer
            :calendar     (-> (assoc new :components
                                     (concat (remove #(scheduled (:name %)) (:components new))
                                             (map #(from-attendee % (attendee-of % user)) changed)))
                              (message "REPLY" opts))
            :significant? true}])))))

;; This is the design of sabre/vobject's ITip\Broker, which Nextcloud uses
(defn messages
  "The messages that the calendar user with the address `user` sends when
  their copy of a calendar changes from `old` to `new`, either of which
  can be nil for an event that's new or deleted. Each is a map of :method,
  :recipient, :calendar, the message, and :significant?, whether the
  change is one that RFC 5546, section 2.1.4, calls significant.

  The organizer sends a REQUEST to each attendee when anything changed,
  and a CANCEL to those who were removed. An attendee sends a REPLY to the
  organizer when their PARTSTAT changed, and declines when they delete the
  event. The `opts` can give :now, the inst to use for the DTSTAMPs.

      (messages old new \"mailto:board@gf.example\")
      ;; => [{:method \"REQUEST\" :recipient \"mailto:simon@gf.example\"
      ;;      :calendar {...} :significant? true}]"
  ([old new user]
   (messages old new user {}))
  ([old new user opts]
   (let [organizer (or (organizer-of new) (organizer-of old))]
     (cond
       (nil? organizer)                       []
       (= (address user) (address organizer)) (vec (organizer-messages old new organizer opts))
       :else                                  (vec (attendee-messages old new user organizer opts))))))

;; RFC 6047, section 2.4
(defn content-type
  "The MIME type of the message `msg` in an iMIP e-mail.

      (content-type msg)
      ;; => \"text/calendar; method=REQUEST; charset=UTF-8\""
  [msg]
  (str "text/calendar; method=" (locale/upper-case (str (value msg :method))) "; charset=UTF-8"))

;; RFC 6047, section 2.2.1 and RFC 5546, section 1.3: an attendee sends
;; a REPLY, a REFRESH or a COUNTER, and the organizer the others
(defn originator
  "The calendar user address that the message `msg` should come from: the
  ATTENDEE for a REPLY, a REFRESH or a COUNTER, and the ORGANIZER for any
  other method."
  [msg]
  (let [c (some #(when (scheduled (:name %)) %) (:components msg))]
    (if (#{"REPLY" "REFRESH" "COUNTER"} (locale/upper-case (str (value msg :method))))
      (value c :attendee)
      (value c :organizer))))
