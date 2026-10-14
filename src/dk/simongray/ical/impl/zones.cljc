(ns ^:no-doc dk.simongray.ical.impl.zones
  "Time zones as offsets from UTC, from the platform's database or from a
  VTIMEZONE, and local times turned into instants by RFC 5545, section
  3.3.5.

  A zone is a map of its :tzid and one of:

  - :iana, the name of a zone that the platform knows
  - :offset, a fixed offset in seconds
  - :observances, the STANDARD and DAYLIGHT parts of a VTIMEZONE, with
    :transitions, a delay of the instants where its offset changes

  Instants are seconds from the epoch, and local times are local seconds,
  as in the civil namespace. The only code that differs per platform gives
  the offset of an IANA zone at an instant."
  (:require [clojure.string :as str]
            [dk.simongray.ical.impl.civil :as civil]
            [dk.simongray.ical.impl.expansion :as expansion]
            [dk.simongray.ical.impl.locale :as locale])
  #?(:clj (:import [java.time Instant ZoneId])))

#?(:clj
   (def ^:private zone-ids
     "The platform's zone names by their names in lower case, without the
     SystemV zones that only Java has."
     (delay (into {}
                  (comp (remove #(str/starts-with? % "SystemV/"))
                        (map (juxt locale/lower-case identity)))
                  (ZoneId/getAvailableZoneIds)))))

#?(:cljs
   (defn- new-formatter
     "An Intl.DateTimeFormat that gives the local time in the zone named
     `tzid`, or nil when the platform doesn't know the zone."
     [tzid]
     (try
       (js/Intl.DateTimeFormat.
        "en-US"
        #js {:timeZone tzid :hourCycle "h23" :era "short"
             :year "numeric" :month "numeric" :day "numeric"
             :hour "numeric" :minute "numeric" :second "numeric"})
       (catch :default _ nil))))

#?(:cljs
   (def ^:private formatters
     "The Intl.DateTimeFormat of each zone by the platform's name for it,
     since making one is slow."
     (atom {})))

#?(:cljs
   (defn- formatter
     "The Intl.DateTimeFormat of the zone that the platform names `iana`."
     [iana]
     (or (get @formatters iana)
         (let [f (new-formatter iana)]
           (swap! formatters assoc iana f)
           f))))

;; Each spelling of a TZID is looked up once, those that the platform
;; doesn't know included, and the formatters are kept by the platform's
;; own names, of which there are a few hundred
#?(:cljs
   (def ^:private names
     "The platform's name of each zone by a TZID in lower case that has been
     asked for, or nil, which is emptied when it holds too many."
     (atom {})))

(defn platform-name
  "The name by which the platform knows the zone `tzid`, ignoring case, or
  nil."
  [tzid]
  #?(:clj  (get @zone-ids (locale/lower-case tzid))
     :cljs (when (re-matches #"[A-Za-z][A-Za-z0-9_+/-]*" tzid)
             (let [k (locale/lower-case tzid)]
               (if (contains? @names k)
                 (get @names k)
                 (let [iana (some-> (new-formatter tzid) .resolvedOptions .-timeZone)]
                   (swap! names #(assoc (if (< (count %) 4096) % {}) k iana))
                   iana))))))

#?(:cljs
   (defn- local-fields
     "The local time of the instant `t` that the formatter `f` gives, as a
     map of the names of its parts to their numbers."
     [f t]
     (reduce (fn [m part]
               (let [v (.-value part)]
                 (case (.-type part)
                   "era"     (assoc m :bc? (str/starts-with? v "B"))
                   "literal" m
                   ;; an engine that ignores hourCycle gives midnight as 24
                   "hour"    (assoc m :hour (mod (js/parseInt v 10) 24))
                   (assoc m (keyword (.-type part)) (js/parseInt v 10)))))
             {}
             (.formatToParts f (js/Date. (* 1000 t))))))

(defn- bisect
  "The first instant after `a` and at most `b` when the offset that the
  function `f` gives is no longer that at `a`, by bisection."
  [f a b]
  (let [before (f a)]
    (loop [a a, b b]
      (if (<= (- b a) 1)
        b
        (let [m (+ a (quot (- b a) 2))]
          (if (= before (f m)) (recur m b) (recur a m)))))))

;; No two transitions in the time zone database are closer than six days,
;; so a scan in steps of three days finds them all
(defn- scan
  "The transitions of the offsets that the function `f` gives at each
  instant, from the instant `from` to the instant `to`, each a vector of
  its instant, the offset before it and the offset after it, in order."
  [f from to]
  (let [step (* 3 86400)]
    (loop [t from, acc []]
      (if (>= t to)
        acc
        (let [u (min to (+ t step))]
          (if (= (f t) (f u))
            (recur u acc)
            (let [at (bisect f t u)]
              (recur at (conj acc [at (f t) (f at)])))))))))

(defn- offset-in
  "The offset at the instant `t` of the `transitions`, a vector of pairs
  of an instant and the offset from then on, in order, or `initial`
  before the first, by bisection."
  [transitions initial t]
  (loop [lo 0, hi (dec (count transitions)), current initial]
    (if (> lo hi)
      current
      (let [mid       (quot (+ lo hi) 2)
            [at off]  (nth transitions mid)]
        (if (<= at t)
          (recur (inc mid) hi off)
          (recur lo (dec mid) current))))))

#?(:cljs
   (defn- intl-offset
     "The offset in seconds of the zone `iana` at the instant `t`, from the
     parts of its Intl.DateTimeFormat."
     [iana t]
     (let [{:keys [year month day hour minute second bc?]} (local-fields (formatter iana) t)
           year (if bc? (- 1 year) year)]
       (- (civil/local-seconds year month day hour minute second) t))))

#?(:cljs
   (def ^:private year-tables
     "The offsets of each zone and year that have been asked for, which is
     emptied when it holds too many."
     (atom {})))

;; Formatting a date with Intl takes microseconds, and resolving one local
;; time takes three or four offsets, so a year's transitions are found
;; once and the offsets in it come from them
#?(:cljs
   (defn- year-table
     "The offset of the zone `iana` at the start of the year `y` in UTC, as
     :initial, and its :transitions in that year, pairs of an instant and
     the offset from then on."
     [iana y]
     (let [k [iana y]]
       (or (get @year-tables k)
           (let [from  (* 86400 (civil/days-from-civil y 1 1))
                 to    (* 86400 (civil/days-from-civil (inc y) 1 1))
                 f     #(intl-offset iana %)
                 table {:initial     (f from)
                        :transitions (mapv (fn [[at _ after]] [at after]) (scan f from to))}]
             (swap! year-tables #(assoc (if (< (count %) 4096) % {}) k table))
             table)))))

(defn- platform-offset
  "The offset in seconds of the platform's zone `iana` at the instant
  `t`."
  [iana t]
  #?(:clj  (-> (ZoneId/of iana) .getRules
               (.getOffset (Instant/ofEpochSecond t)) .getTotalSeconds)
     :cljs (let [y                              (first (civil/civil-from-days (civil/floor-div t 86400)))
                 {:keys [initial transitions]}  (year-table iana y)]
             (offset-in transitions initial t))))

(defn- first-value
  "The value of the first property named `k` of the component `c`."
  [c k]
  (some #(when (= k (:name %)) (:value %)) (:properties c)))

(defn- local-text?
  "Whether `v` is a date or a date-time in the form of jCal."
  [v]
  (and (string? v) (boolean (re-matches #"\d{4}-\d{2}-\d{2}(?:T\d{2}:\d{2}:\d{2}Z?)?" v))))

(defn- merge-all
  "The sequences `xss`, each sorted by `f`, merged into one, lazily. Each
  step looks at all of them, rather than nesting a lazy sequence for each,
  which would overflow the stack for thousands."
  [f xss]
  (lazy-seq
   (let [xss (filterv seq xss)]
     (when (seq xss)
       (let [i (apply min-key #(f (first (nth xss %))) (range (count xss)))]
         (cons (first (nth xss i)) (merge-all f (update xss i rest))))))))

;; RFC 5545, section 3.6.5: an observance starts at its DTSTART, and at
;; each time of its RDATEs and RRULE, all in the local time before it (the
;; time of TZOFFSETFROM), with UNTIL in UTC. Some write a local UNTIL,
;; which is taken as local time, and a date as the end of its day. No
;; zone's clocks change more often than monthly, and a rule that would
;; make millions of onsets is left out.
(defn- observance
  "The STANDARD or DAYLIGHT component `c` as a map of its :from and :to
  offsets in seconds, its :onsets, a lazy sequence of the instants when
  it starts, in order, and either :periodic, the instant from which only
  a rule that repeats every 400 years makes them, or :until, an instant
  that they all come before. It's nil when it lacks a DTSTART or an
  offset."
  [c]
  (let [start  (first-value c :dtstart)
        from   (civil/offset-seconds (first-value c :tzoffsetfrom))
        to     (civil/offset-seconds (first-value c :tzoffsetto))
        rule   (let [r (first-value c :rrule)]
                 (when (and (map? r) (#{"YEARLY" "MONTHLY"} (:freq r))) r))
        rdates (->> (filter #(= :rdate (:name %)) (:properties c))
                    (mapcat :value)
                    (filter local-text?))]
    (when (and (local-text? start) from to)
      (let [x     (civil/seconds start)
            until (:until rule)
            past? (when (local-text? until)
                    (let [limit (cond-> (civil/seconds until)
                                  (not (str/includes? until "T")) (+ 86399))]
                      (if (str/ends-with? until "Z")
                        #(> (- % from) limit)
                        #(> % limit))))
            xs    (if rule (expansion/rule-times rule x false past?) [x])
            final (- (reduce max x (map civil/seconds rdates)) from)]
        (merge {:from   from
                :to     to
                :start  x
                :onsets (map #(- % from)
                             (merge-all identity [xs (sort (map civil/seconds rdates))]))}
               (cond
                 ;; a day either side of UNTIL covers its offset
                 (nil? rule)                        {:until (inc final)}
                 (:count rule)                      nil
                 past?                              {:until (+ 86400 (max final (civil/seconds until)))}
                 (= 1 (or (:interval rule) 1))      {:periodic final}))))))

(def ^:private horizon
  "The instant up to which the transitions of a VTIMEZONE are kept in a
  vector: the start of the year 2200."
  (* 86400 (civil/days-from-civil 2200 1 1)))

(def ^:private cycle-seconds
  "The seconds of 400 years, after which the Gregorian calendar repeats
  itself, weekdays included."
  (* 146097 86400))

;; Outlook's VTIMEZONEs start their rules in 1601, so the transitions up
;; to now are many, and a vector is searched by bisection. Those after the
;; horizon aren't kept, since a zone is cached and the years go on to 9999.
;; When only yearly or monthly rules make the transitions of the 400 years
;; before the horizon, those after it repeat them; otherwise they're worked
;; out when asked for.
(defn- transition-table
  "The transitions of the `observances`, pairs of an instant and the
  offset from then on, as a map of :near, a vector of those before the
  horizon in order, :periodic?, whether those after it repeat those of
  the 400 years before it, and :far, a function of no arguments that
  gives those after it in order, lazily."
  [observances]
  (let [pairs (fn [{:keys [to onsets]}] (map (fn [t] [t to]) onsets))
        since (- horizon cycle-seconds)]
    {:near      (vec (sort-by first (mapcat #(take-while (fn [[t]] (< t horizon)) (pairs %))
                                            observances)))
     :periodic? (every? #(or (some-> (:periodic %) (<= since)) (some-> (:until %) (< since)))
                        observances)
     :far       (fn []
                  (merge-all first (map #(drop-while (fn [[t]] (< t horizon)) (pairs %))
                                        observances)))}))

(defn- vtimezone-zone*
  "The zone that the VTIMEZONE component `c` defines, or nil when it has
  no observance."
  [c]
  (let [observances (keep observance (:components c))]
    (when (seq observances)
      {:tzid        (first-value c :tzid)
       :observances {:table   (delay (transition-table observances))
                     ;; before the first onset, the offset it starts from
                     :initial (:from (apply min-key :start observances))}})))

(def ^:private vtimezone-cache
  "The zones of the VTIMEZONE components that have been asked for, which
  is emptied when it holds too many."
  (atom {}))

;; A calendar's times are resolved one at a time, and each would otherwise
;; expand the rules of its VTIMEZONE again
(defn vtimezone-zone
  "The zone that the VTIMEZONE component `c` defines, or nil when it has
  no observance."
  [c]
  (if-let [z (find @vtimezone-cache c)]
    (val z)
    (let [z (vtimezone-zone* c)]
      (swap! vtimezone-cache #(assoc (if (< (count %) 256) % {}) c z))
      z)))

(defn- transition-offset
  "The offset at the instant `t` of the zone with the `transitions`, pairs
  of an instant and the offset from then on, in order. Before the first,
  the offset is `initial`."
  [transitions initial t]
  (loop [[[at offset] & more] transitions, current initial]
    (if (and at (<= at t))
      (recur more offset)
      current)))

(defn offset
  "The offset in seconds of the `zone` at the instant `t`."
  [zone t]
  (cond
    (:iana zone)   (platform-offset (:iana zone) t)
    (:offset zone) (:offset zone)
    :else          (let [{:keys [table initial]}       (:observances zone)
                         {:keys [near far periodic?]}  @table]
                     (cond
                       (< t horizon)
                       (offset-in near initial t)

                       periodic?
                       (offset-in near initial
                                  (- t (* cycle-seconds (inc (quot (- t horizon) cycle-seconds)))))

                       :else
                       (transition-offset (far) (offset-in near initial t) t)))))

(defn local
  "The local time of the instant `t` in the `zone`."
  [zone t]
  (+ t (offset zone t)))

(defn transitions
  "The transitions of the `zone` from the instant `from` to the instant
  `to`, each a vector of its instant, the offset before it and the offset
  after it, in order."
  [zone from to]
  (scan #(offset zone %) from to))

;; RFC 5545, section 3.3.5: a local time that occurs twice is the first
;; of them, and one in a gap takes the offset from before the gap. The two
;; candidates use the offsets of a day before and a day after, since no
;; two transitions are closer than six days.
(defn instant
  "The instant of the local time `x` in the `zone`."
  [zone x]
  (let [before     (- x (offset zone (- x 86400)))
        after      (- x (offset zone (+ x 86400)))
        candidates (filter #(= x (local zone %)) (distinct [before after]))]
    (if (seq candidates)
      (apply min candidates)
      before)))

;; A time only moves forward, when it's in a gap, so once the times have
;; passed x, none of them can move to before it
(defn wall-times
  "The local times `xs` of the `zone`, which are in order, as the times
  that the clock shows when they happen: a time in a gap moves forward by
  the gap (RFC 5545, section 3.3.5). They stay in order without
  duplicates, lazily."
  [zone xs]
  (letfn [(step [pending xs]
            (lazy-seq
             (if-let [[x & more] (seq xs)]
               (let [pending (conj pending (local zone (instant zone x)))
                     done    (take-while #(<= % x) pending)]
                 (if (seq done)
                   (concat done (step (reduce disj pending done) more))
                   (step pending more)))
               (seq pending))))]
    (step (sorted-set) xs)))
