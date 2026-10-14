(ns ^:no-doc dk.simongray.ical.impl.busy
  "Free and busy time, as a CalDAV server gives it for a free-busy query
  (RFC 4791, section 7.10): the busy periods of a calendar's
  availability (RFC 7953), events and VFREEBUSY components between two
  instants, coalesced by their type.

  Periods are pairs of instants in seconds from the epoch."
  (:require [dk.simongray.ical.impl.civil :as civil]
            [dk.simongray.ical.impl.locale :as locale]
            [dk.simongray.ical.impl.recurrence :as recurrence]))

(defn- value
  "The value of the first property named `k` of the component `c`."
  [c k]
  (some #(when (= k (:name %)) (:value %)) (:properties c)))

;; RFC 4791, section 7.10: a transparent or cancelled event is free, a
;; tentative one busy-tentative, and the others busy
(defn- fbtype
  "The type of busy time of the event `c`, or nil when it's free."
  [c]
  (let [status (some-> (value c :status) str locale/upper-case)
        transp (some-> (value c :transp) str locale/upper-case)]
    (cond
      (= "TRANSPARENT" transp) nil
      (= "CANCELLED" status)   nil
      (= "TENTATIVE" status)   "BUSY-TENTATIVE"
      :else                    "BUSY")))

(defn- clip
  "The period from `s` to `e` within the instants `from` and `to`, or nil
  when it's outside them or empty."
  [s e from to]
  (let [s (max s from)
        e (min e to)]
    (when (< s e) [s e])))

(defn- event-periods
  "The busy periods of the events of the calendar `cal` between the
  instants `from` and `to`, each a pair of its type and its period."
  [cal from to opts]
  (let [utc (assoc opts :floating {:tzid "UTC" :offset 0})]
    (for [c     (:components (recurrence/expand cal from to opts))
          :when (= :vevent (:name c))
          :let  [type (fbtype c)
                 s    (recurrence/instant-of cal (some #(when (= :dtstart (:name %)) %)
                                                       (:properties c))
                                             utc)
                 e    (some-> (recurrence/end cal c opts) (#(recurrence/instant-of cal % utc)))
                 p    (when (and type s e) (clip s e from to))]
          :when p]
      [type p])))

;; RFC 5545, section 3.8.2.6: FREEBUSY periods are in UTC, and FBTYPE is
;; BUSY by default
(defn- freebusy-periods
  "The busy periods of the VFREEBUSY components of the calendar `cal`
  between the instants `from` and `to`, each a pair of its type and its
  period."
  [cal from to]
  (for [c     (:components cal)
        :when (= :vfreebusy (:name c))
        p     (:properties c)
        :when (and (= :freebusy (:name p)) (not= :unknown (:type p)))
        :let  [type (locale/upper-case (str (get-in p [:params :fbtype] "BUSY")))]
        :when (not= "FREE" type)
        v     (:value p)
        :let  [s (civil/seconds (:start v))
               e (if (:end v)
                   (civil/seconds (:end v))
                   (let [[days secs] (civil/duration-parts (:duration v))]
                     (+ s (* 86400 days) secs)))
               p (clip s e from to)]
        :when p]
    [type p]))

(defn- paint
  "The `segments`, pairs of a period and its type (nil for free) that
  cover a span in order, with the period from `s` to `e` given the type
  `type`."
  [segments s e type]
  (vec (mapcat (fn [[[a b] t]]
                 (if (or (<= b s) (<= e a))
                   [[[a b] t]]
                   (remove (fn [[[x y]]] (>= x y))
                           [[[a (max a s)] t]
                            [[(max a s) (min b e)] type]
                            [[(min b e) b] t]])))
               segments)))

;; RFC 7953, section 4: absent and 0 are the lowest, then 9 up to 1
(defn- priority-rank
  "The rank of the priority of the VAVAILABILITY `c`, lowest first, with
  the busy types of equal priorities ordered so that BUSY wins."
  [c]
  (let [p (value c :priority)
        t (locale/upper-case (str (or (value c :busytype) "BUSY-UNAVAILABLE")))]
    [(if (and (number? p) (pos? p)) (- 10 p) 0)
     (get {"BUSY-TENTATIVE" 0 "BUSY-UNAVAILABLE" 1 "BUSY" 2} t 1)]))

(defn- available-periods
  "The periods of the instances of the AVAILABLE components of the
  VAVAILABILITY `c` in the calendar `cal` between the instants `from` and
  `to`."
  [cal c from to opts]
  (let [utc   (assoc opts :floating {:tzid "UTC" :offset 0})
        inner (assoc cal :components (concat (filter #(= :vtimezone (:name %)) (:components cal))
                                             (:components c)))]
    (for [a     (:components (recurrence/expand inner from to opts))
          :when (= :available (:name a))
          :let  [s (recurrence/instant-of inner (some #(when (= :dtstart (:name %)) %)
                                                      (:properties a))
                                          utc)
                 e (some-> (recurrence/end inner a opts) (#(recurrence/instant-of inner % utc)))]
          :when (and s e)]
      [s e])))

;; RFC 7953, section 5: the time of each VAVAILABILITY is busy, by its
;; BUSYTYPE, except where its AVAILABLE components make it free, and the
;; higher priorities paint over the lower
(defn- availability-periods
  "The busy periods that the VAVAILABILITY components of the calendar
  `cal` give between the instants `from` and `to`, each a pair of its type
  and its period."
  [cal from to opts]
  (let [utc (assoc opts :floating {:tzid "UTC" :offset 0})
        prop #(some (fn [p] (when (= %2 (:name p)) p)) (:properties %1))]
    (->> (sort-by priority-rank (filter #(= :vavailability (:name %)) (:components cal)))
         (reduce (fn [segments c]
                   (let [s    (or (some-> (prop c :dtstart) (#(recurrence/instant-of cal % utc))) from)
                         e    (or (when (or (prop c :dtend) (prop c :duration))
                                    (some-> (recurrence/end cal c opts) (#(recurrence/instant-of cal % utc))))
                                  to)
                         type (locale/upper-case (str (or (value c :busytype) "BUSY-UNAVAILABLE")))]
                     (if-let [[s e] (clip s e from to)]
                       (reduce (fn [segments [a b]]
                                 (if-let [[a b] (clip a b s e)]
                                   (paint segments a b nil)
                                   segments))
                               (paint segments s e type)
                               (available-periods cal c from to opts))
                       segments)))
                 [[[from to] nil]])
         (keep (fn [[p t]] (when t [t p]))))))

(defn- coalesce
  "The `periods` with those that overlap or touch merged, in order."
  [periods]
  (reduce (fn [acc [s e]]
            (let [[ps pe] (peek acc)]
              (if (and pe (<= s pe))
                (conj (pop acc) [ps (max pe e)])
                (conj acc [s e]))))
          []
          (sort periods)))

(def ^:private type-order
  "The order of the types of busy time in a VFREEBUSY."
  {"BUSY" 0 "BUSY-UNAVAILABLE" 1 "BUSY-TENTATIVE" 2})

(defn- utc-text
  "The instant `t` as a date-time in UTC in the form of jCal."
  [t]
  (civil/date-time-text t true))

(defn free-busy
  "A VFREEBUSY of the busy time of the calendar `cal` between the instants
  `from` and `to`, with one FREEBUSY of the coalesced periods of each
  type. The `opts` are those of recurrence/expand, and :now and :uid for
  the DTSTAMP and the UID."
  [cal from to {:keys [now uid] :as opts}]
  (let [by-type (group-by first (concat (availability-periods cal from to opts)
                                        (event-periods cal from to opts)
                                        (freebusy-periods cal from to)))]
    {:name       :vfreebusy
     :properties (into [{:name :uid :value uid}
                        {:name :dtstamp :value (utc-text now)}
                        {:name :dtstart :value (utc-text from)}
                        {:name :dtend :value (utc-text to)}]
                       (for [type (sort-by #(get type-order % 3) (keys by-type))]
                         (cond-> {:name :freebusy}
                           (not= "BUSY" type) (assoc :params {:fbtype type})
                           true               (assoc :value (mapv (fn [[s e]] {:start (utc-text s)
                                                                               :end   (utc-text e)})
                                                                  (coalesce (map second (by-type type))))))))}))
