(ns ^:no-doc dk.simongray.ical.impl.recurrence
  "The recurrence set of a component, by RFC 5545, section 3.8.5: the
  instances of its RRULEs and RDATEs without those of its EXDATEs, with
  the components that override an instance by its RECURRENCE-ID
  (section 3.8.4.4).

  A frame says how to interpret a date or date-time property: it's a map
  of :date?, :utc? and :zone, the zone of its TZID (or nil for a floating
  time). The instances are local seconds in the frame of DTSTART."
  (:require [clojure.string :as str]
            [dk.simongray.ical.impl.civil :as civil]
            [dk.simongray.ical.impl.expansion :as expansion]
            [dk.simongray.ical.impl.locale :as locale]
            [dk.simongray.ical.impl.zones :as zones]
            [dk.simongray.ical.rrule :as rrule]
            [dk.simongray.ical.tz :as tz]))

(defn- props
  "The properties named `k` of the component `c`."
  [c k]
  (filter #(= k (:name %)) (:properties c)))

(defn- prop
  "The first property named `k` of the component `c`."
  [c k]
  (first (props c k)))

(defn frame
  "The frame of the property `p` in the calendar `cal`, with its TZID
  resolved by tz/zone with `opts`. A floating time, or one with a TZID
  that doesn't resolve, takes the zone :floating of `opts`, when given. A
  value that isn't a date or a date-time has no frame."
  [cal p opts]
  (let [v (cond-> (:value p) (sequential? (:value p)) first)
        v (if (map? v) (:start v) v)]
    (when (civil/date-text? v)
      (let [tzid (get-in p [:params :tzid])]
        {:date? (not (str/includes? v "T"))
         :utc?  (str/ends-with? v "Z")
         :zone  (when-not (str/ends-with? v "Z")
                  (or (when tzid (tz/zone cal tzid opts)) (:floating opts)))}))))

(defn epoch
  "The instant of the local time `x` in the `frame`, or nil for a floating
  time."
  [frame x]
  (cond
    (:utc? frame)  x
    (:zone frame)  (zones/instant (:zone frame) x)))

(defn- local-of
  "The local time of the instant `t` in the `frame`, or nil for a floating
  frame."
  [frame t]
  (cond
    (:utc? frame) t
    (:zone frame) (zones/local (:zone frame) t)))

(defn- in-frame
  "The local time `x` of the frame `from` as a local time of the frame
  `to`, by its instant when both have one."
  [from x to]
  (let [t (epoch from x)]
    (or (when t (local-of to t)) x)))

(defn text
  "The local time `x` as text in the form of the `frame`: a date, or a
  date-time with a Z in UTC."
  [frame x]
  (if (:date? frame) (civil/date-text x) (civil/date-time-text x (:utc? frame))))

(defn- times-of
  "The local times in the frame `to` of the values of the date or
  date-time properties `ps` of the calendar `cal`, sorted. A period gives
  its start."
  [cal ps to opts]
  (sort (for [p     ps
              :let  [from (frame cal p opts)]
              :when from
              v     (:value p)
              :let  [v (if (map? v) (:start v) v)]
              :when (civil/date-text? v)]
          (in-frame from (civil/seconds v) to))))

(defn- periods-of
  "The RDATE periods of the component `c` by the local time of their start
  in the frame `to`, each a map of its :end or its :duration, with :end in
  the frame `to`."
  [cal c to opts]
  (into {} (for [p     (props c :rdate)
                 :let  [from (frame cal p opts)]
                 :when from
                 v     (:value p)
                 :when (map? v)]
             [(in-frame from (civil/seconds (:start v)) to)
              (if (:end v)
                {:end (in-frame from (civil/seconds (:end v)) to)}
                {:duration (:duration v)})])))

;; RFC 5545, section 3.3.10: UNTIL is in UTC when DTSTART has a zone or is
;; in UTC, and floating when DTSTART is
(defn- until-past
  "A predicate of whether a local time of the frame `fr` is past the
  UNTIL `until` of a rule, or nil without one. A date as UNTIL for a
  date-time DTSTART ends with its day."
  [fr until]
  (when (civil/date-text? until)
    (let [limit (cond-> (civil/seconds until)
                  (and (not (str/includes? until "T")) (not (:date? fr))) (+ 86399))]
      (if (and (str/ends-with? until "Z") (:zone fr))
        #(> (epoch fr %) limit)
        #(> % limit)))))

(defn- rule-times
  "The local times that the rules of the properties `ps` generate from the
  local time `start` in the frame `fr`, as one sorted seq per rule, by
  the function `f` of the expansion namespace."
  [f ps start fr]
  (for [p     ps
        :let  [rule (:value p)]
        :when (and (map? rule) (rrule/expandable? rule))]
    (f rule start (:date? fr) (until-past fr (:until rule)))))

(defn- merge-sorted
  "The sorted sequences `xss` merged into one sorted sequence without
  duplicates, lazily."
  [xss]
  (lazy-seq
   (let [xss (remove empty? (map seq xss))]
     (when (seq xss)
       (let [x (apply min (map first xss))]
         (cons x (merge-sorted (map (fn [xs] (drop-while #(= x %) xs)) xss))))))))

(defn- remove-sorted
  "The sorted sequence `xs` without the times of the sorted sequence `ys`,
  lazily. A `date` exclusion removes every time of its day."
  [xs ys day?]
  (lazy-seq
   (when-let [[x & more] (seq xs)]
     (let [key   #(if day? (civil/floor-div % 86400) %)
           ys    (drop-while #(< (key %) (key x)) ys)]
       (if (and (seq ys) (= (key (first ys)) (key x)))
         (remove-sorted more ys day?)
         (cons x (remove-sorted more ys day?)))))))

;; RFC 5545, section 3.8.5.3: the recurrence set is DTSTART with the
;; instances of the RRULEs and RDATEs, without those of the EXDATEs and
;; EXRULEs, and duplicates are ignored. The rules' times that the clocks
;; skip have moved forward by the time they're excluded, as the EXDATEs
;; of them have.
(defn ids
  "The recurrence ids of the component `c` in the calendar `cal`, local
  times in the frame `fr` of its DTSTART, which is `start`, in order, as a
  lazy sequence."
  [cal c fr start opts]
  (let [wall     (if (and (:zone fr) (not (:date? fr)))
                   #(zones/wall-times (:zone fr) %)
                   identity)
        rules    (rule-times expansion/rule-times (props c :rrule) start fr)
        rdates   (times-of cal (props c :rdate) fr opts)
        included (wall (merge-sorted (concat (if (seq rules) rules [[start]]) [rdates])))
        exdates  (props c :exdate)
        ;; a date EXDATE of a date-time DTSTART removes its day, whatever
        ;; the zone
        by-date  (when-not (:date? fr)
                   (filter #(some (fn [v] (not (str/includes? (str v) "T"))) (:value %))
                           exdates))
        days     (sort (for [p by-date, v (:value p) :when (civil/date-text? v)]
                         (civil/seconds v)))
        excluded (merge-sorted
                  (cons (times-of cal (remove (set by-date) exdates) fr opts)
                        (map wall (rule-times expansion/exclusion-times (props c :exrule) start fr))))]
    (cond-> (remove-sorted included excluded false)
      (seq days) (remove-sorted days true))))

(defn- shift-end
  "The property `p` of the end of a component that started at the local
  time `start` and now starts at `start'`, in the frame `fr`, moved so
  that it keeps its exact duration (section 3.8.5.3)."
  [cal p fr start start' opts]
  (let [efr (frame cal p opts)]
    (cond
      (nil? efr)
      p

      ;; dates have days of nominal length
      (:date? fr)
      (assoc p :value (text efr (+ start' (- (civil/seconds (:value p)) start))))

      :else
      (let [end   (civil/seconds (:value p))
            exact (if (and (epoch efr end) (epoch fr start))
                    (- (epoch efr end) (epoch fr start))
                    (- (in-frame efr end fr) start))
            end'  (or (some->> (epoch fr start') (+ exact) (local-of efr))
                      (+ start' exact))]
        (assoc p :value (text efr end'))))))

(defn- with-property
  "The component `c` with the property `p` in place of the first of its
  name, or added after DTSTART."
  [c p]
  (let [k     (:name p)
        ps    (vec (:properties c))
        i     (first (keep-indexed #(when (= k (:name %2)) %1) ps))
        after (or (first (keep-indexed #(when (= :dtstart (:name %2)) %1) ps)) -1)]
    (assoc c :properties
           (if i
             (assoc ps i p)
             (into (conj (subvec ps 0 (inc after)) p) (subvec ps (inc after)))))))

(defn- without
  "The component `c` without its properties named in the set `ks`."
  [c ks]
  (update c :properties #(vec (remove (comp ks :name) %))))

(defn- recurrence-id
  "The RECURRENCE-ID property of the instance at the local time `id` of a
  component whose DTSTART is the property `dtstart`, in its frame `fr`."
  [dtstart fr id]
  (let [params (dissoc (:params dtstart) :range)]
    (cond-> {:name :recurrence-id}
      (seq params)    (assoc :params params)
      (:type dtstart) (assoc :type (:type dtstart))
      true            (assoc :value (text fr id)))))

(defn- instance
  "The instance of the component `c`, whose DTSTART is the local time
  `start` in the frame `fr`, that starts at the local time `start'` and has
  the RECURRENCE-ID property `rid`: `c` without its rules, with its
  DTSTART moved, its DTEND or DUE moved to keep its duration (or the
  `period` of an RDATE), and `rid`."
  [cal c fr start start' rid period opts]
  (let [dtstart (prop c :dtstart)
        moved   (-> c
                    (without #{:rrule :rdate :exdate :exrule :recurrence-id})
                    (with-property (assoc dtstart :value (text fr start')))
                    (with-property rid))]
    (cond
      (:end period)
      (-> (without moved #{:duration :due})
          (with-property (cond-> (assoc dtstart :name :dtend :value (text fr (:end period)))
                           true (dissoc :type)
                           (:date? fr) (assoc :type :date))))

      (:duration period)
      (-> (without moved #{:dtend :due})
          (with-property {:name :duration :value (:duration period)}))

      :else
      (reduce (fn [m k]
                (if-let [p (prop m k)]
                  (with-property m (shift-end cal p fr start start' opts))
                  m))
              moved
              [:dtend :due]))))

;; RFC 5545, section 3.3.6: a duration's days are added on the wall clock,
;; and its hours, minutes and seconds on the timeline
(defn plus-duration
  "The local time `x` of the frame `fr` after the duration `d`."
  [fr x d]
  (let [[days secs] (civil/duration-parts d)
        wall        (+ x (* 86400 days))]
    (or (some->> (epoch fr wall) (+ secs) (local-of fr))
        (+ wall secs))))

;; RFC 5545, sections 3.6.1 and 3.6.2
(defn end
  "The property that ends the component `c`: its DTEND (or DUE for a
  VTODO), or else a DTEND (or DUE) of its DTSTART plus its DURATION, or
  the day after a DTSTART that is a date, or its DTSTART. It's nil without
  a DTSTART or an end."
  [cal c opts]
  (let [k        (if (= :vtodo (:name c)) :due :dtend)
        dtstart  (prop c :dtstart)
        duration (prop c :duration)
        fr       (frame cal dtstart opts)
        moved    #(-> dtstart
                      (assoc :name k :value (text fr %))
                      (cond-> (:date? fr) (assoc :type :date)))]
    (cond
      (prop c k)               (prop c k)
      (nil? fr)                nil
      (and (string? (:value duration)) (not= :unknown (:type duration)))
      (moved (plus-duration fr (civil/seconds (:value dtstart)) (:value duration)))
      (:date? fr)              (moved (+ 86400 (civil/seconds (:value dtstart))))
      :else                    (assoc dtstart :name k))))

;; expand looks up the components of each UID in an index, rather than in
;; the whole calendar each time
(defn- related
  "The components of the calendar `cal` with the name and the UID of the
  component `c`, itself included, from the index ::related of `opts` when
  it has one."
  [cal c opts]
  (let [k [(:name c) (:value (prop c :uid))]]
    (if-let [index (::related opts)]
      (get index k)
      (filter #(= k [(:name %) (:value (prop % :uid))]) (:components cal)))))

(defn- sequence-number
  "The SEQUENCE of the component `c`, or 0 when it has none that's a
  number."
  [c]
  (let [v (:value (prop c :sequence))]
    (if (number? v) v 0)))

(defn- overrides
  "The components of the calendar `cal` that override instances of the
  component `c`, by their recurrence ids as local times in the frame `fr`.
  Of two for the same instance, the one with the higher SEQUENCE wins."
  [cal c fr opts]
  (->> (related cal c opts)
       (filter #(prop % :recurrence-id))
       (keep (fn [o]
               (let [p   (prop o :recurrence-id)
                     ofr (frame cal p opts)]
                 (when ofr
                   [(in-frame ofr (civil/seconds (:value p)) fr) o]))))
       (sort-by #(sequence-number (second %)))
       (into (sorted-map))))

;; RFC 5545, section 3.8.4.4: with RANGE=THISANDFUTURE, an override moves
;; the instances after it by the same time, and gives them its duration
(defn- future-override
  "The override among the `ranged` ones, a sorted map of recurrence id to
  component, that covers the recurrence id `id`, as a pair, or nil."
  [ranged id]
  (when (seq ranged)
    (first (rsubseq ranged <= id))))

(defn instant-of
  "The instant of the date or date-time property `p` of the calendar
  `cal`, or nil for a floating time without the zone :floating of `opts`,
  or for what isn't a date or a date-time. A date is its first moment."
  [cal p opts]
  (when-let [fr (frame cal p opts)]
    (let [v (cond-> (:value p) (sequential? (:value p)) first)]
      (when (string? v)
        (epoch fr (civil/seconds v))))))

(defn- floating-or-utc
  "The `opts` with UTC as the zone :floating, unless they give one."
  [opts]
  (update opts :floating #(or % {:tzid "UTC" :offset 0})))

(defn- span
  "The instants of the start and the end of the component `c`, as a pair,
  with a floating time in the zone :floating of `opts`, or else in UTC."
  [cal c opts]
  (let [opts  (floating-or-utc opts)
        start (instant-of cal (prop c :dtstart) opts)
        end   (some->> (end cal c opts) (#(instant-of cal % opts)))]
    (when start [start (or end start)])))

;; RFC 4791, section 9.9: a component without a duration overlaps a range
;; when it starts in it
(defn overlaps?
  "Whether the component `c` of the calendar `cal` overlaps the instants
  `from` to `to`."
  [cal c from to opts]
  (when-let [[s e] (span cal c opts)]
    (if (< s e)
      (and (< s to) (> e from))
      (and (<= from s) (< s to)))))

(declare occurrences)

(defn- recurring?
  "Whether the component `c` has a recurrence rule or dates."
  [c]
  (boolean (or (prop c :rrule) (prop c :rdate))))

(defn- override?
  "Whether the component `c` overrides an instance of another, recurring
  component in the calendar `cal`."
  [cal c opts]
  (when (prop c :recurrence-id)
    (some #(and (not (identical? c %)) (recurring? %)) (related cal c opts))))

(defn- earliest-move
  "The longest time by which a component of the calendar `cal` moves an
  instance of the component `c` earlier than its recurrence id, in
  seconds, or 0. A floating time is in the zone :floating of `opts`, or
  else in UTC."
  [cal c opts]
  (let [opts (floating-or-utc opts)]
    (reduce max 0 (for [o     (related cal c opts)
                        :let  [r (instant-of cal (prop o :recurrence-id) opts)
                               s (instant-of cal (prop o :dtstart) opts)]
                        :when (and r s)]
                    (- r s)))))

;; The instances come in the order of their recurrence ids, but an
;; override can move one earlier, into the range, from past its end
(defn- instances-in
  "The instances of the recurring component `c` of the calendar `cal` that
  overlap the instants `from` to `to`. They're looked for up to the first
  whose recurrence id is past `to` by more than any override moves one."
  [cal c from to opts]
  (let [fopts (floating-or-utc opts)
        until (+ to (earliest-move cal c opts))]
    (->> (occurrences cal c (assoc opts ::after from))
         (take-while #(when-let [t (instant-of cal (prop % :recurrence-id) fopts)] (< t until)))
         (filter #(overlaps? cal % from to opts)))))

;; RFC 4791, section 9.6.5
(defn expand
  "The calendar `cal` with its recurring components replaced by their
  instances that overlap the instants `from` to `to`, and its other
  components with a DTSTART kept when they overlap them."
  [cal from to opts]
  (let [opts (assoc opts ::related (group-by #(vector (:name %) (:value (prop % :uid)))
                                             (:components cal)))]
    (assoc cal :components
           (vec (mapcat (fn [c]
                          (cond
                            (override? cal c opts) []
                            (recurring? c)         (instances-in cal c from to opts)
                            (prop c :dtstart)      (if (overlaps? cal c from to opts) [c] [])
                            :else                  [c]))
                        (:components cal))))))

(defn- local-in
  "The local time in the frame `fr` of the date or date-time property `p`
  of the calendar `cal`, or nil."
  [p cal fr opts]
  (when-let [pfr (and p (frame cal p opts))]
    (in-frame pfr (civil/seconds (:value p)) fr)))

;; A day either side covers the difference between a local time and an
;; instant
(defn- reach
  "The longest time in seconds from the recurrence id of an instance of
  the component `c`, whose DTSTART is the local time `start` in the frame
  `fr`, to its end, unless an override that isn't `ranged` replaces it:
  the time to its DTEND or DUE, to the end of an RDATE's `periods`, or by
  a `ranged` override, plus a day."
  [cal c fr start periods ranged opts]
  (+ 86400
     (reduce max 0
             (concat
              [(some-> (end cal c opts) (local-in cal fr opts) (- start))]
              (for [[t v] periods]
                (- (or (:end v) (plus-duration fr t (:duration v))) t))
              (for [[r o] ranged
                    :let  [e (some-> (end cal o opts) (local-in cal fr opts))]
                    :when e]
                (- e r))))))

(defn occurrences
  "The instances of the component `c` of the calendar `cal`, each a
  component with its RECURRENCE-ID, in the order of their recurrence ids,
  as a lazy sequence. An instance that a component of `cal` overrides is
  that component. A component that doesn't recur is its only instance,
  and one without a DTSTART has none.

  With the instant ::after in `opts`, it can leave out the instances that
  end before it."
  [cal c opts]
  (let [dtstart (prop c :dtstart)
        fr      (frame cal dtstart opts)]
    (cond
      (nil? fr)                                       []
      (not (or (prop c :rrule) (prop c :rdate)))      [c]
      :else
      (let [start   (civil/seconds (:value dtstart))
            periods (periods-of cal c fr opts)
            overs   (overrides cal c fr opts)
            ranged  (into (sorted-map)
                          (filter #(and (= "THISANDFUTURE"
                                           (some-> (prop (val %) :recurrence-id) :params :range
                                                   str locale/upper-case))
                                        (frame cal (prop (val %) :dtstart) opts))
                                  overs))
            after   (when-let [t (::after opts)]
                      (- t (reach cal c fr start periods ranged opts)))
            ids     (cond->> (merge-sorted [(ids cal c fr start opts) (keys overs)])
                      after (remove #(and (< % after) (not (contains? overs %)))))]
        (map (fn [id]
               (if-let [o (get overs id)]
                 o
                 (if-let [[r o] (future-override ranged id)]
                   ;; the instance moves as far as the override did, and
                   ;; starts in the override's frame
                   (let [ofr    (frame cal (prop o :dtstart) opts)
                         olocal (civil/seconds (:value (prop o :dtstart)))
                         shift  (- (in-frame ofr olocal fr) r)]
                     (instance cal o ofr olocal (in-frame fr (+ id shift) ofr)
                               (recurrence-id dtstart fr id) nil opts))
                   (instance cal c fr start id (recurrence-id dtstart fr id)
                             (get periods id) opts))))
             ids)))))
