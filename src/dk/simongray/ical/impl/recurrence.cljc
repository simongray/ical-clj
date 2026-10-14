(ns ^:no-doc dk.simongray.ical.impl.recurrence
  "The recurrence set of a component, by RFC 5545, section 3.8.5: the
  instances of its RRULEs and RDATEs without those of its EXDATEs, with
  the components that override an instance by its RECURRENCE-ID
  (section 3.8.4.4).

  A frame is how a date or date-time property reads: a map of :date?,
  :utc? and :zone, the zone of its TZID, or nil for a floating time. The
  instances are local seconds in the frame of DTSTART."
  (:require [clojure.string :as str]
            [dk.simongray.ical.impl.civil :as civil]
            [dk.simongray.ical.impl.expansion :as expansion]
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

(defn- date-text?
  "Whether `v` is a date or a date-time in the form of jCal."
  [v]
  (and (string? v) (boolean (re-matches #"\d{4}-\d{2}-\d{2}(?:T\d{2}:\d{2}:\d{2}Z?)?" v))))

(defn frame
  "The frame of the property `p` in the calendar `cal`, reading a TZID by
  tz/zone with `opts`. A floating time takes the zone :floating of `opts`,
  when given. A value that isn't a date or a date-time has no frame."
  [cal p opts]
  (let [v (cond-> (:value p) (sequential? (:value p)) first)
        v (if (map? v) (:start v) v)]
    (when (date-text? v)
      (let [tzid (get-in p [:params :tzid])]
        {:date? (not (str/includes? v "T"))
         :utc?  (str/ends-with? v "Z")
         :zone  (cond
                  (str/ends-with? v "Z") nil
                  tzid                   (tz/zone cal tzid opts)
                  :else                  (:floating opts))}))))

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
  "The local time `x` in the form of jCal of the `frame`: a date, or a
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
              :when (date-text? v)]
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
  "A function that tells whether a local time of the frame `fr` is past the
  UNTIL `until` of a rule, or nil without one. A date as UNTIL for a
  date-time DTSTART ends with its day."
  [fr until]
  (when (date-text? until)
    (let [limit (cond-> (civil/seconds until)
                  (and (not (str/includes? until "T")) (not (:date? fr))) (+ 86399))]
      (if (and (str/ends-with? until "Z") (:zone fr))
        #(> (epoch fr %) limit)
        #(> % limit)))))

(defn- rule-times
  "The local times that the rules of the properties `ps` make from the
  local time `start` in the frame `fr`, each sorted, as a seq of seqs."
  [ps start fr]
  (for [p     ps
        :let  [rule (:value p)]
        :when (and (map? rule) (rrule/expandable? rule))]
    (expansion/rule-times rule start (:date? fr) (until-past fr (:until rule)))))

(defn- merge-sorted
  "The sorted sequences `xss` merged into one sorted sequence without
  duplicates, lazily."
  [xss]
  (lazy-seq
   (let [xss (remove empty? (map seq xss))]
     (when (seq xss)
       (let [x (apply min (map first xss))]
         (cons x (merge-sorted (map #(if (= x (first %)) (rest %) %) xss))))))))

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
;; EXRULEs, and duplicates are ignored
(defn ids
  "The recurrence ids of the component `c` in the calendar `cal`, local
  times in the frame `fr` of its DTSTART, which is `start`, in order, as a
  lazy sequence."
  [cal c fr start opts]
  (let [rules     (rule-times (props c :rrule) start fr)
        rdates    (times-of cal (props c :rdate) fr opts)
        included  (merge-sorted (concat (if (seq rules) rules [[start]]) [rdates]))
        exdates   (props c :exdate)
        ;; a date EXDATE of a date-time DTSTART removes its day
        by-date   (when-not (:date? fr)
                    (filter #(some (fn [v] (not (str/includes? (str v) "T"))) (:value %))
                            exdates))
        by-time   (remove (set by-date) exdates)
        excluded  (merge-sorted (cons (times-of cal by-time fr opts)
                                      (rule-times (props c :exrule) start fr)))]
    (cond-> (remove-sorted included excluded false)
      (seq by-date)
      (remove-sorted (times-of cal by-date (assoc fr :date? true) opts) true))))

(defn- shift-end
  "The property `p` of the end of a component that started at the local
  time `start` and now starts at `start'`, in the frame `fr`, moved so
  that it keeps its exact duration (section 3.8.5.3)."
  [cal p fr start start' opts]
  (let [efr (frame cal p opts)]
    (if (nil? efr)
      p
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

(defn instance
  "The instance of the component `c`, whose DTSTART is the local time
  `start` in the frame `fr`, that starts at the local time `start'` and has
  the recurrence id `id`: `c` without its rules, with its DTSTART moved,
  its DTEND or DUE moved to keep its duration (or the `period` of an
  RDATE), and the RECURRENCE-ID `id`."
  [cal c fr start start' id period opts]
  (let [dtstart (prop c :dtstart)
        params  (dissoc (:params dtstart) :range)
        moved   (-> c
                    (without #{:rrule :rdate :exdate :exrule :recurrence-id})
                    (with-property (assoc dtstart :value (text fr start')))
                    (with-property (cond-> {:name :recurrence-id}
                                     (seq params)    (assoc :params params)
                                     (:type dtstart) (assoc :type (:type dtstart))
                                     true            (assoc :value (text fr id)))))]
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
  (let [k       (if (= :vtodo (:name c)) :due :dtend)
        dtstart (prop c :dtstart)
        fr      (frame cal dtstart opts)
        moved   #(-> dtstart
                     (assoc :name k :value (text fr %))
                     (cond-> (:date? fr) (assoc :type :date)))]
    (cond
      (prop c k)               (prop c k)
      (nil? fr)                nil
      (prop c :duration)       (let [d (:value (prop c :duration))]
                                 (when (string? d)
                                   (moved (plus-duration fr (civil/seconds (:value dtstart)) d))))
      (:date? fr)              (moved (+ 86400 (civil/seconds (:value dtstart))))
      :else                    (assoc dtstart :name k))))

(defn- overrides
  "The components of the calendar `cal` that override instances of the
  component `c`, by their recurrence ids as local times in the frame `fr`.
  Of two for the same instance, the one with the higher SEQUENCE wins."
  [cal c fr opts]
  (let [uid (:value (prop c :uid))]
    (->> (:components cal)
         (filter #(and (= (:name c) (:name %))
                       (= uid (:value (prop % :uid)))
                       (prop % :recurrence-id)))
         (keep (fn [o]
                 (let [p   (prop o :recurrence-id)
                       ofr (frame cal p opts)]
                   (when ofr
                     [(in-frame ofr (civil/seconds (:value p)) fr) o]))))
         (sort-by #(or (:value (prop (second %) :sequence)) 0))
         (into (sorted-map)))))

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

(defn- span
  "The instants of the start and the end of the component `c`, as a pair,
  with a floating time read as if in UTC."
  [cal c opts]
  (let [utc   (assoc opts :floating {:tzid "UTC" :offset 0})
        start (instant-of cal (prop c :dtstart) utc)
        end   (some->> (end cal c opts) (#(instant-of cal % utc)))]
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
  "Whether the component `c` overrides an instance of a recurring one in
  the calendar `cal`."
  [cal c]
  (when (prop c :recurrence-id)
    (let [uid (:value (prop c :uid))]
      (some #(and (= (:name c) (:name %)) (= uid (:value (prop % :uid))) (recurring? %))
            (:components cal)))))

(defn- instances-in
  "The instances of the recurring component `c` of the calendar `cal` that
  overlap the instants `from` to `to`, up to the first whose recurrence id
  is past `to`."
  [cal c from to opts]
  (let [utc (assoc opts :floating {:tzid "UTC" :offset 0})]
    (->> (occurrences cal c opts)
         (take-while #(< (or (instant-of cal (prop % :recurrence-id) utc) 0) to))
         (filter #(overlaps? cal % from to opts)))))

;; RFC 4791, section 9.6.5
(defn expand
  "The calendar `cal` with its recurring components replaced by their
  instances that overlap the instants `from` to `to`, and its other
  components with a DTSTART kept when they overlap them."
  [cal from to opts]
  (assoc cal :components
         (vec (mapcat (fn [c]
                        (cond
                          (override? cal c)  []
                          (recurring? c)     (instances-in cal c from to opts)
                          (prop c :dtstart)  (if (overlaps? cal c from to opts) [c] [])
                          :else              [c]))
                      (:components cal)))))

(defn occurrences
  "The instances of the component `c` of the calendar `cal`, each a
  component with its RECURRENCE-ID, in the order of their recurrence ids,
  as a lazy sequence. An instance that a component of `cal` overrides is
  that component. A component that doesn't recur is its only instance,
  and one without a DTSTART has none."
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
                          (filter #(= "THISANDFUTURE"
                                      (some-> (prop (val %) :recurrence-id) :params :range
                                              str str/upper-case))
                                  overs))
            ids     (merge-sorted [(ids cal c fr start opts) (keys overs)])]
        (map (fn [id]
               (if-let [o (get overs id)]
                 o
                 (if-let [[r o] (future-override ranged id)]
                   (let [ofr    (frame cal (prop o :dtstart) opts)
                         ostart (in-frame ofr (civil/seconds (:value (prop o :dtstart))) fr)]
                     (instance cal o fr ostart (+ id (- ostart r)) id nil opts))
                   (instance cal c fr start id id (get periods id) opts))))
             ids)))))
