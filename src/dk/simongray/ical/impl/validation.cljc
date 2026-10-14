(ns ^:no-doc dk.simongray.ical.impl.validation
  "The ways a calendar breaks RFC 5545 and its extensions, as data.

  Each error is a map of:

  - :error, a keyword
  - :level, :error where the RFC says MUST or MUST NOT, and :warning
    where it says SHOULD or SHOULD NOT
  - :path, the path that get-in takes to the component or property in the
    calendar
  - keys with the details, e.g. :property"
  (:require [clojure.string :as str]
            [dk.simongray.ical.impl.civil :as civil]
            [dk.simongray.ical.impl.recurrence :as recurrence]
            [dk.simongray.ical.impl.registry :as registry]
            [dk.simongray.ical.impl.rules :as rules]
            [dk.simongray.ical.tz :as tz]))

(defn- error
  "An error `k` at the `path`, of the `level`, with the `more` keys."
  ([k level path]
   (error k level path {}))
  ([k level path more]
   (merge {:error k :level level :path path} more)))

(defn- indexed
  "The properties of the component `c` named `k`, each as a pair of its
  index and itself."
  [c k]
  (keep-indexed #(when (= k (:name %2)) [%1 %2]) (:properties c)))

(defn- first-of
  "The first property of the component `c` named `k`."
  [c k]
  (second (first (indexed c k))))

;; RFC 5545, section 3.2.20: a value must be of its type
(defn- invalid-values
  "The properties of the component `c` at `path` that the registry knows,
  and whose values didn't parse as their type."
  [c path]
  (for [[i p] (map-indexed vector (:properties c))
        :when (and (= :unknown (:type p))
                   (not (:unknown? (registry/property (:name p)))))]
    (error :invalid-value :error (conj path :properties i) {:property (:name p)})))

;; RFC 5545, sections 3.6 to 3.6.6: which properties a component must
;; have, and which it may have only once
(defn- cardinality
  "The errors in how many of each property the component `c` at `path`
  has, in a calendar `with-method?`."
  [c path with-method?]
  (let [{:keys [required once should-once exclusive]} (registry/components (:name c))
        counts   (frequencies (map :name (:properties c)))
        ;; RFC 5545, section 3.6.1: DTSTART is required without METHOD
        required (cond-> (or required #{})
                   (and (= :vevent (:name c)) (not with-method?)) (conj :dtstart))]
    (concat
     (for [k (sort required) :when (zero? (get counts k 0))]
       (error :missing-property :error path {:property k}))
     (for [k (sort (into required once)) :when (< 1 (get counts k 0))]
       (error :repeated-property :error path {:property k}))
     (for [k (sort should-once) :when (< 1 (get counts k 0))]
       (error :repeated-property :warning path {:property k}))
     (for [[a b] exclusive :when (and (counts a) (counts b))]
       (error :conflicting-properties :error path {:properties [a b]})))))

;; RFC 5545, section 3.6.6: each action needs its own properties, and
;; DURATION and REPEAT go together
(defn- alarm-errors
  "The errors in the properties of the VALARM `c` at `path` for its
  action."
  [c path]
  (let [action (some-> (first-of c :action) :value str str/upper-case)
        has?   #(boolean (first-of c %))]
    (concat
     (when (not= (has? :duration) (has? :repeat))
       [(error :conflicting-properties :error path {:properties [:duration :repeat]})])
     (for [k     (case action
                   "DISPLAY" [:description]
                   "EMAIL"   [:description :summary :attendee]
                   [])
           :when (not (has? k))]
       (error :missing-property :error path {:property k})))))

(defn- date?
  "Whether the value of the date or date-time property `p` is a date."
  [p]
  (let [v (cond-> (:value p) (sequential? (:value p)) first)]
    (and (string? v) (not (str/includes? v "T")))))

;; RFC 5545, sections 3.6.1, 3.8.2.2 and 3.8.2.3: the end has the type of
;; the start and comes after it
(defn- span-errors
  "The errors in how the component `c` at `path` of the calendar `cal`
  starts and ends."
  [cal c path]
  (let [start   (first-of c :dtstart)
        [i end] (first (concat (indexed c :dtend) (indexed c :due)))]
    (when (and start end (civil/date-text? (:value start)) (civil/date-text? (:value end)))
      (cond
        (not= (date? start) (date? end))
        [(error :mismatched-types :error (conj path :properties i) {:property (:name end)})]

        :else
        (let [opts {:floating {:tzid "UTC" :offset 0}}
              s    (recurrence/instant-of cal start opts)
              e    (recurrence/instant-of cal end opts)]
          (when (and s e (if (= :due (:name end)) (< e s) (<= e s)))
            [(error :end-before-start :error (conj path :properties i))]))))))

;; RFC 5545, section 3.3.10, and RFC 7529
(defn- rule-errors
  "The errors in the recurrence rule of the property `p` at `path` of the
  component whose DTSTART is the property `start`. DTSTART needn't be
  synchronized with an EXRULE."
  [p path start]
  (when (map? (:value p))
    (let [v      (:value start)
          zoned? (boolean (or (get-in start [:params :tzid]) (and (string? v) (str/ends-with? v "Z"))))]
      (for [e     (rules/errors (:value p) v zoned?)
            :when (not (and (= :exrule (:name p)) (= :unsynchronized-dtstart (:error e))))]
        (assoc e :path path)))))

;; RFC 5545, section 3.8.7.2: DTSTAMP is in UTC
(defn- dtstamp-errors
  "An error when the DTSTAMP of the component `c` at `path` isn't in UTC."
  [c path]
  (for [[i p] (indexed c :dtstamp)
        :when (and (string? (:value p)) (not (str/ends-with? (:value p) "Z")))]
    (error :dtstamp-not-utc :error (conj path :properties i))))

;; RFC 5545, section 3.2.19: each TZID needs a VTIMEZONE in the calendar,
;; though RFC 7809 lets CalDAV leave out those of IANA zones
(defn- tzid-errors
  "The errors in the TZIDs of the properties of the component `c` at
  `path` of the calendar `cal`."
  [cal c path]
  (let [defined (set (for [z (:components cal)
                           :when (= :vtimezone (:name z))]
                       (:value (first-of z :tzid))))]
    (for [[i p] (map-indexed vector (:properties c))
          :let  [tzid (get-in p [:params :tzid])]
          :when (and tzid (not (defined tzid)))]
      (if (tz/zone tzid)
        (error :missing-vtimezone :warning (conj path :properties i) {:tzid tzid})
        (error :unknown-tzid :error (conj path :properties i) {:tzid tzid})))))

(defn- component-errors
  "The errors of the component `c` at `path` of the calendar `cal`, and of
  the components inside it."
  [cal c path with-method?]
  (let [start (first-of c :dtstart)]
    (concat
     (invalid-values c path)
     (cardinality c path with-method?)
     (when (= :valarm (:name c)) (alarm-errors c path))
     (dtstamp-errors c path)
     (span-errors cal c path)
     (tzid-errors cal c path)
     (for [k [:rrule :exrule]
           [i p] (indexed c k)
           e (rule-errors p (conj path :properties i) start)]
       e)
     (when (and (= :vtimezone (:name c))
                (not-any? #(#{:standard :daylight} (:name %)) (:components c)))
       [(error :missing-component :error path {:component :standard})])
     (for [[i sub] (map-indexed vector (:components c))
           e       (component-errors cal sub (conj path :components i) with-method?)]
       e))))

(defn calendar-errors
  "The errors of the calendar `cal`, a component, in order."
  [cal]
  (let [with-method? (boolean (first-of cal :method))]
    (vec (concat
          (when (and (= :vcalendar (:name cal)) (empty? (:components cal)))
            [(error :missing-component :error [] {:component :vevent})])
          (component-errors cal cal [] with-method?)))))

(defn stream-errors
  "The errors of a text that the parser turned into `stream`, a map of
  :components, :dropped and :unclosed. They're the lines it skipped and
  the components it closed, then the errors of each component, with a
  path that starts with the component's index."
  [{:keys [components dropped unclosed]}]
  (vec (concat
        (for [[n text] dropped]
          (error :not-a-content-line :error [] {:line n :text text}))
        (for [k unclosed]
          (error :unclosed-component :error [] {:component k}))
        (for [[i c] (map-indexed vector components)
              e     (calendar-errors c)]
          (update e :path #(into [i] %))))))
