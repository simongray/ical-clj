(ns ^:no-doc dk.simongray.ical.impl.expansion
  "Recurrence rules expanded into the local times they make, by RFC 5545,
  section 3.3.10.

  Each interval of the rule is a period: a year, a month, a week, a day,
  an hour, a minute or a second. A period's set is the days of the period
  that pass the rule's BYxxx parts, at the times that pass them, then
  narrowed by BYSETPOS. Where RFC 5545's table says that a part expands a
  set, filtering the days of the whole period gives the same set, once
  the parts that the rule leaves out are filled in from DTSTART.

  Times are local seconds, as in the civil namespace."
  (:require [dk.simongray.ical.impl.civil :as civil]))

(def weekdays
  "The weekday of each code of RFC 5545, from 0 for Sunday."
  {"SU" 0 "MO" 1 "TU" 2 "WE" 3 "TH" 4 "FR" 5 "SA" 6})

(def ^:private ranks
  "The rank of each frequency, from the longest."
  (zipmap ["YEARLY" "MONTHLY" "WEEKLY" "DAILY" "HOURLY" "MINUTELY" "SECONDLY"]
          (range)))

(defn- longer?
  "Whether the frequency `freq` is longer than `than`."
  [freq than]
  (< (ranks freq) (ranks than)))

(defn- in-range
  "The numbers `xs` that are allowed between `lo` and `hi`, counting
  negative ones from the end when `signed?`. Text (a leap month of RFC
  7529) is left out."
  [xs lo hi signed?]
  (when xs
    (vec (filter #(and (number? %)
                       (or (<= lo % hi) (and signed? (<= (- hi) % (- lo)))))
                 xs))))

(defn- read-byday
  "The BYDAY value `s` as a pair of its ordinal, or nil, and its weekday,
  e.g. -1FR as [-1 5]."
  [s]
  (let [[_ ordinal code] (re-matches #"([+-]?\d+)?([A-Z]{2})" s)]
    [(when ordinal #?(:clj (Long/parseLong ordinal) :cljs (js/parseInt ordinal 10)))
     (weekdays code)]))

;; RFC 5545, section 3.3.10: "Information, not contained in the rule,
;; necessary to determine the various recurrence instance start time and
;; dates are derived from the Start Time ("DTSTART") component attribute."
(defn- day-defaults
  "The `rule` with the day it leaves out taken from the date `y`-`m`-`d`
  with the weekday `wd`."
  [{:keys [freq bymonth byweekno byyearday bymonthday byday] :as rule} m d wd]
  (cond
    ;; as libical does: the days of the month of DTSTART
    (and (= "YEARLY" freq) bymonthday (not (or bymonth byweekno byyearday)))
    (assoc rule :bymonth [m])

    (or byyearday bymonthday byday)
    rule

    ;; as libical does: the weekday of DTSTART in the weeks of BYWEEKNO
    byweekno
    (cond-> rule (= "YEARLY" freq) (assoc :byday [[nil wd]]))

    :else
    (case freq
      "YEARLY"  (assoc rule :bymonth (or bymonth [m]) :bymonthday [d])
      "MONTHLY" (assoc rule :bymonthday [d])
      "WEEKLY"  (assoc rule :byday [[nil wd]])
      rule)))

;; RFC 5545, section 3.3.10: BYSECOND, BYMINUTE and BYHOUR are ignored when
;; DTSTART is a date
(defn- time-defaults
  "The `rule` with the times it leaves out taken from `hh`:`mm`:`ss`, or
  midnight alone when DTSTART is a date."
  [{:keys [freq] :as rule} hh mm ss date?]
  (if date?
    (assoc rule :byhour [0] :byminute [0] :bysecond [0])
    (cond-> rule
      (and (nil? (:byhour rule)) (longer? freq "HOURLY"))     (assoc :byhour [hh])
      (and (nil? (:byminute rule)) (longer? freq "MINUTELY")) (assoc :byminute [mm])
      (and (nil? (:bysecond rule)) (longer? freq "SECONDLY")) (assoc :bysecond [ss]))))

(declare week-one)

;; As libical does: a YEARLY rule with BYWEEKNO counts its years from the
;; year that the week of DTSTART belongs to
(defn- week-year
  "The year that the week of the day `day` belongs to, in the year `y`,
  with weeks that start on the weekday `wkst`."
  [y day wkst]
  (cond
    (>= day (week-one (inc y) wkst)) (inc y)
    (< day (week-one y wkst))        (dec y)
    :else                            y))

(defn normalize
  "The recurrence `rule`, as the values namespace parses it, ready to
  expand from the local time `start`: its numbers in range, BYDAY as pairs
  of ordinal and weekday, WKST as a weekday, and the parts it leaves out
  filled in from `start`. A `date?` start is a date."
  [rule start date?]
  (let [[y m d hh mm ss] (civil/civil-from-seconds start)
        day              (civil/floor-div start 86400)
        wd               (civil/weekday day)
        rule             (-> rule
                             ;; RFC 7529, section 3: SKIP needs RSCALE
                             (cond-> (not= "GREGORIAN" (:rscale rule)) (dissoc :skip))
                             (update :interval #(max 1 (or % 1)))
                             (update :wkst #(weekdays (or % "MO")))
                             (update :byday #(some->> % (mapv read-byday)))
                             (update :bymonth in-range 1 12 false)
                             (update :byweekno in-range 1 53 true)
                             (update :byyearday in-range 1 366 true)
                             (update :bymonthday in-range 1 31 true)
                             (update :byhour in-range 0 23 false)
                             (update :byminute in-range 0 59 false)
                             (update :bysecond in-range 0 60 false)
                             (update :bysetpos in-range 1 366 true))]
    (-> rule
        (day-defaults m d wd)
        (time-defaults hh mm ss date?)
        (assoc :year (if (and (:byweekno rule) (= "YEARLY" (:freq rule)))
                       (week-year y day (:wkst rule))
                       y)
               :month m
               :day day))))

;; RFC 5545, section 3.3.10: week number one is the first week, starting
;; on WKST, with at least four days in the year
(defn week-one
  "The first day of week one of the year `y`, with weeks that start on the
  weekday `wkst`."
  [y wkst]
  (let [jan1   (civil/days-from-civil y 1 1)
        offset (mod (- (civil/weekday jan1) wkst) 7)]
    (if (<= offset 3) (- jan1 offset) (+ jan1 (- 7 offset)))))

(defn- week-days
  "The days of the weeks `byweekno` of the year `y`, which can fall in the
  years before and after it."
  [y wkst byweekno]
  (let [start (week-one y wkst)
        weeks (quot (- (week-one (inc y) wkst) start) 7)]
    (sort (distinct
           (for [n     byweekno
                 :let  [n (if (neg? n) (+ weeks 1 n) n)]
                 :when (<= 1 n weeks)
                 day   (range (+ start (* 7 (dec n))) (+ start (* 7 n)))]
             day)))))

(defn- nth-in
  "Whether the day `day` is the `ordinal`th of its weekday in the span of
  `length` days that starts on the day `first`, counting a negative
  ordinal from the end."
  [day ordinal first length]
  (let [i (- day first)]
    (if (pos? ordinal)
      (= ordinal (inc (quot i 7)))
      (= ordinal (- (inc (quot (- length 1 i) 7)))))))

(defn- byday-match?
  "Whether the day `day` is one of the `byday` pairs of the rule. An
  ordinal counts within the month for a MONTHLY rule or a YEARLY rule with
  BYMONTH, and within the year for a YEARLY rule without (erratum 3779).
  Other rules ignore it."
  [{:keys [freq bymonth byweekno byday]} day]
  (let [wd    (civil/weekday day)
        scope (cond
                (= "MONTHLY" freq)                             :month
                (and (= "YEARLY" freq) bymonth)                :month
                (and (= "YEARLY" freq) (not byweekno))         :year)]
    (some (fn [[ordinal w]]
            (and (= w wd)
                 (or (nil? ordinal) (nil? scope)
                     (let [[y m] (civil/civil-from-days day)]
                       (if (= :month scope)
                         (nth-in day ordinal (civil/days-from-civil y m 1)
                                 (civil/days-in-month y m))
                         (nth-in day ordinal (civil/days-from-civil y 1 1)
                                 (civil/days-in-year y)))))))
          byday)))

(defn- in-signed?
  "Whether the `n`th of `length`, or the same counted from the end, is
  one of the numbers `xs`."
  [xs n length]
  (some #(or (= % n) (= % (- n length 1))) xs))

(defn- in-weeks?
  "Whether the day `day` of the year `y` falls in one of the weeks
  `byweekno` of its week-numbering year, with weeks that start on the
  weekday `wkst`."
  [byweekno y day wkst]
  (let [wy    (week-year y day wkst)
        start (week-one wy wkst)]
    (in-signed? byweekno (inc (quot (- day start) 7))
                (quot (- (week-one (inc wy) wkst) start) 7))))

;; RFC 5545, section 3.3.10: BYWEEKNO is only for YEARLY rules, whose
;; periods start from its weeks. Any other rule takes it as a limit, as
;; dateutil does.
(defn day-passes?
  "Whether the day `day` passes the BYMONTH, BYYEARDAY, BYMONTHDAY and
  BYDAY parts of the `rule`, and its BYWEEKNO when it isn't YEARLY."
  [{:keys [freq bymonth byweekno byyearday bymonthday byday wkst] :as rule} day]
  (let [[y m d] (civil/civil-from-days day)]
    (and (or (nil? bymonth) (some #{m} bymonth))
         (or (nil? byweekno) (= "YEARLY" freq) (in-weeks? byweekno y day wkst))
         (or (nil? byyearday)
             (in-signed? byyearday (inc (- day (civil/days-from-civil y 1 1)))
                         (civil/days-in-year y)))
         (or (nil? bymonthday) (in-signed? bymonthday d (civil/days-in-month y m)))
         (or (nil? byday) (byday-match? rule day)))))

(def ^:private units
  "The seconds of each frequency shorter than a day."
  {"HOURLY" 3600 "MINUTELY" 60 "SECONDLY" 1})

(defn- month-days
  "The days of the month `m` of the year `y`."
  [y m]
  (let [first (civil/days-from-civil y m 1)]
    (range first (+ first (civil/days-in-month y m)))))

(defn- period-days
  "The days of the `k`th period of the `rule`, before its BYxxx parts. A
  YEARLY period with BYMONTH has only the days of those months, since
  BYMONTH leaves out the others anyway."
  [{:keys [freq interval year month day wkst byweekno bymonth]} k start]
  (case freq
    "YEARLY"  (let [y (+ year (* k interval))]
                (cond
                  byweekno (week-days y wkst byweekno)
                  bymonth  (mapcat #(month-days y %) (sort (distinct bymonth)))
                  :else    (let [jan1 (civil/days-from-civil y 1 1)]
                             (range jan1 (+ jan1 (civil/days-in-year y))))))
    "MONTHLY" (let [i (+ (dec month) (* k interval))
                    y (+ year (civil/floor-div i 12))
                    m (inc (mod i 12))
                    first (civil/days-from-civil y m 1)]
                (range first (+ first (civil/days-in-month y m))))
    "WEEKLY"  (let [first (- day (mod (- (civil/weekday day) wkst) 7))
                    w     (+ first (* 7 k interval))]
                (range w (+ w 7)))
    "DAILY"   [(+ day (* k interval))]
    [(civil/floor-div (+ start (* k interval (units freq))) 86400)]))

(defn- times
  "The seconds of the day of the `k`th period of the `rule`. A rule
  shorter than daily fixes the hour, minute or second of its period, and
  its BYxxx parts of those only limit."
  [{:keys [freq interval byhour byminute bysecond]} k start]
  (let [at (when-let [unit (units freq)]
             (mod (+ start (* k interval unit)) 86400))
        hh (some-> at (quot 3600))
        mm (some-> at (rem 3600) (quot 60))
        ss (some-> at (rem 60))
        ok #(or (nil? %1) (some #{%2} %1))]
    (case freq
      "HOURLY"   (when (ok byhour hh)
                   (for [m byminute s bysecond] (+ (* 3600 hh) (* 60 m) s)))
      "MINUTELY" (when (and (ok byhour hh) (ok byminute mm))
                   (for [s bysecond] (+ (* 3600 hh) (* 60 mm) s)))
      "SECONDLY" (when (and (ok byhour hh) (ok byminute mm) (ok bysecond ss))
                   [at])
      (for [h byhour m byminute s bysecond] (+ (* 3600 h) (* 60 m) s)))))

;; RFC 5545, section 3.3.10: "BYSETPOS operates on a set of recurrence
;; instances in one interval of the recurrence rule."
(defn- set-positions
  "The times `xs` of a period at the positions `bysetpos`, counting
  negative ones from the end."
  [bysetpos xs]
  (let [xs (vec xs)
        n  (count xs)]
    (->> bysetpos
         (keep #(let [i (if (neg? %) (+ n %) (dec %))]
                  (when (< -1 i n) (nth xs i))))
         distinct
         sort)))

(defn- moved-day
  "The day that SKIP moves the `n`th day of the span of `length` days from
  the day `first` to, when there's no such day: the last day of the span
  (or the one before it) going BACKWARD, and the day after it (or its first
  day) going FORWARD, for a positive (or negative) `n`."
  [skip n first length]
  (cond
    (<= 1 (abs n) length) nil
    (pos? n)              (if (= "BACKWARD" skip) (+ first length -1) (+ first length))
    :else                 (if (= "BACKWARD" skip) (dec first) first)))

(defn- period-months
  "The months of the `k`th period of a MONTHLY or YEARLY `rule`, as pairs
  of year and month."
  [{:keys [freq interval year month bymonth]} k]
  (case freq
    "MONTHLY" (let [i (+ (dec month) (* k interval))
                    m (inc (mod i 12))]
                (when (or (nil? bymonth) (some #{m} bymonth))
                  [[(+ year (civil/floor-div i 12)) m]]))
    "YEARLY"  (let [y (+ year (* k interval))]
                (for [m (sort (distinct (or bymonth (range 1 13))))] [y m]))
    nil))

;; RFC 7529, section 4.1: SKIP moves a day of BYMONTHDAY that a month
;; lacks after BYMONTHDAY and before BYDAY, and libical moves a day of
;; BYYEARDAY that a year lacks too
(defn- skipped-days
  "The days that the SKIP of a Gregorian `rule` moves the days of its
  BYMONTHDAY and BYYEARDAY that the `k`th period lacks to."
  [{:keys [skip freq year interval bymonthday byyearday] :as rule} k]
  (when (and (#{"BACKWARD" "FORWARD"} skip) (#{"MONTHLY" "YEARLY"} freq))
    (concat
     (for [[y m] (period-months rule k)
           n     bymonthday
           :let  [day (moved-day skip n (civil/days-from-civil y m 1) (civil/days-in-month y m))]
           :when day]
       day)
     (when (= "YEARLY" freq)
       (let [y (+ year (* k interval))]
         (for [n     byyearday
               :let  [day (moved-day skip n (civil/days-from-civil y 1 1) (civil/days-in-year y))]
               :when day]
           day))))))

(defn period-set
  "The local times of the `k`th period of the `rule`, from the local time
  `start`, in order."
  [rule k start]
  (let [ts   (sort (distinct (times rule k start)))
        days (->> (concat (filter #(day-passes? rule %) (period-days rule k start))
                          (filter #(or (nil? (:byday rule)) (byday-match? rule %))
                                  (skipped-days rule k)))
                  distinct
                  sort)
        xs   (for [day days, t ts] (+ (* 86400 day) t))]
    (cond->> xs
      (:bysetpos rule) (set-positions (:bysetpos rule)))))

(defn generates?
  "Whether the normalized `rule` generates its start, the local time
  `start`."
  [rule start]
  (boolean (some #(= start %) (period-set rule 0 start))))

(defn- period-start
  "The earliest local time that the `k`th period of the `rule` can hold.
  A YEARLY period with BYWEEKNO can start in the year before."
  [{:keys [freq interval year month day wkst byweekno]} k start]
  (* 86400
     (case freq
       "YEARLY"  (let [y    (+ year (* k interval))
                       jan1 (civil/days-from-civil y 1 1)]
                   (if byweekno (min jan1 (week-one y wkst)) jan1))
       "MONTHLY" (let [i (+ (dec month) (* k interval))]
                   (civil/days-from-civil (+ year (civil/floor-div i 12))
                                          (inc (mod i 12)) 1))
       "WEEKLY"  (+ (- day (mod (- (civil/weekday day) wkst) 7)) (* 7 k interval))
       "DAILY"   (+ day (* k interval))
       (civil/floor-div (+ start (* k interval (units freq))) 86400))))

(def ^:private cycle-seconds
  "The seconds of 400 years, after which the Gregorian calendar repeats
  itself, weekdays included."
  (* 146097 86400))

(def ^:private last-second
  "The last local time that iCalendar can write: the end of the year
  9999."
  (+ 59 (* 86400 (civil/days-from-civil 9999 12 31)) (* 23 3600) (* 59 60)))

;; A rule's periods come round to the same place in the calendar after
;; 400 years times its interval, so a rule that has made nothing in that
;; long never will. An interval of more than 10,000 reaches past the year
;; 9999 first.
(defn- exhausted?
  "Whether the `k`th period of the `rule` starts past the last time
  iCalendar can write, or so long after the time `latest` that the rule
  will make nothing more."
  [rule k start latest]
  (let [at (period-start rule k start)]
    (or (> at last-second)
        (> at (+ latest (* (min (:interval rule) 10000) cycle-seconds))))))

(defn- skip-to-day
  "The first period of the sub-daily `rule` on or after the day `day`."
  [{:keys [freq interval]} start day]
  (let [step (* interval (units freq))]
    (long (Math/ceil (/ (- (* 86400 day) start) (double step))))))

(defn- next-above
  "The least of the numbers `xs` that is greater than `x`, or nil."
  [xs x]
  (reduce #(if (and (> %2 x) (or (nil? %1) (< %2 %1))) %2 %1) nil xs))

;; The times of day from the time of the period up to the next hour,
;; minute or second that BYHOUR, BYMINUTE or BYSECOND allow can't pass,
;; so the periods that start in them are skipped, as dateutil does
(defn- skip-to-time
  "The first period of the sub-daily `rule` after the `k`th that starts at
  a time of day that its BYHOUR, BYMINUTE and BYSECOND could allow, or at
  the next day."
  [{:keys [freq interval byhour byminute bysecond]} start k]
  (let [step   (* interval (units freq))
        at     (+ start (* k step))
        day    (civil/floor-div at 86400)
        tod    (- at (* 86400 day))
        hh     (quot tod 3600)
        mm     (quot (rem tod 3600) 60)
        ss     (rem tod 60)
        target (cond
                 (and byhour (not (some #{hh} byhour)))
                 (if-let [h (next-above byhour hh)] (* 3600 h) 86400)

                 (and byminute (not= "HOURLY" freq) (not (some #{mm} byminute)))
                 (if-let [m (next-above byminute mm)]
                   (+ (* 3600 hh) (* 60 m))
                   (* 3600 (inc hh)))

                 (and bysecond (= "SECONDLY" freq) (not (some #{ss} bysecond)))
                 (if-let [s (next-above bysecond ss)]
                   (+ (* 3600 hh) (* 60 mm) s)
                   (+ (* 3600 hh) (* 60 (inc mm))))

                 :else
                 (inc tod))]
    (max (inc k)
         (long (Math/ceil (/ (- (+ (* 86400 day) target) start) (double step)))))))

(defn- gcd
  "The greatest common divisor of `a` and `b`."
  [a b]
  (if (zero? b) a (recur b (mod a b))))

;; A sub-daily rule's periods start at the times of day that are
;; congruent to the time of `start`, modulo the greatest common divisor of
;; its step and a day, e.g. only the even hours for FREQ=HOURLY;INTERVAL=2
;; from midnight
(defn- reachable?
  "Whether a period of the sub-daily `rule` from the local time `start`
  can start at a time of day that its BYHOUR, BYMINUTE and BYSECOND
  allow."
  [{:keys [freq interval byhour byminute bysecond]} start]
  (let [limits? (case freq
                  "HOURLY"   byhour
                  "MINUTELY" (or byhour byminute)
                  (or byhour byminute bysecond))]
    (or (not limits?)
        (let [g   (gcd (* interval (units freq)) 86400)
              tod (mod start 86400)
              ms  (if (= "HOURLY" freq) [(quot (rem tod 3600) 60)] (or byminute (range 60)))
              ss  (if (= "SECONDLY" freq) (or bysecond (range 60)) [(rem tod 60)])]
          (boolean (some #(zero? (mod (- % tod) g))
                         (for [h (or byhour (range 24)), m ms, s ss :when (< s 60)]
                           (+ (* 3600 h) (* 60 m) s))))))))

(defn- next-period
  "The `k`th period of the `rule` or the first after it with any time on
  or after `from`, as a pair of its number and those times, or nil when
  the rule is exhausted. A rule shorter than daily skips the days and the
  times of day that its BYxxx parts leave out."
  [rule k start from]
  (let [sub-daily? (units (:freq rule))]
    (loop [k k]
      (when-not (exhausted? rule k start from)
        (let [days (when sub-daily? (period-days rule k start))]
          (if (and days (not (day-passes? rule (first days))))
            (recur (max (inc k) (skip-to-day rule start (inc (first days)))))
            (let [xs (drop-while #(< % from) (period-set rule k start))]
              (cond
                (seq xs)   [k xs]
                sub-daily? (recur (skip-to-time rule start k))
                :else      (recur (inc k))))))))))

;; A period can hold a time that the one before it held too, when SKIP
;; moves a day across a month's end or BYSECOND has 60, and those are left
;; out
(defn local-times
  "The local times that the normalized `rule` generates from the local
  time `start`, in order, as a lazy sequence that ends where the rule
  does, or never, and at the end of the year 9999 at the latest. It
  ignores COUNT and UNTIL, and leaves out `start` unless the rule
  generates it."
  [rule start]
  (letfn [(step [k from]
            (lazy-seq
             (when-let [[k xs] (next-period rule k start from)]
               (concat xs (lazy-seq (step (inc k) (inc (last xs))))))))]
    (when (or (not (units (:freq rule))) (reachable? rule start))
      (take-while #(<= % last-second) (step 0 start)))))

;; RFC 5545, section 3.8.5.3: "The "DTSTART" property defines the first
;; instance in the recurrence set", and section 3.3.10: "The "DTSTART"
;; property value always counts as the first occurrence."
(defn rule-times
  "The local times of the recurrence `rule`, as the values namespace
  parses it, from the local time `start`. They're `start` itself, even
  when the rule doesn't generate it, and then the times the rule
  generates until `past?` is true of one, at most COUNT in all (and at
  least `start`). A `date?` start is a date."
  [rule start date? past?]
  (let [more (lazy-seq
              (let [xs (cond->> (local-times (normalize rule start date?) start)
                         past? (take-while (complement past?)))]
                (if (= start (first xs)) (rest xs) xs)))]
    (cond->> (cons start more)
      (:count rule) (take (max 1 (:count rule))))))

;; RFC 2445, section 4.8.5.2: an EXRULE excludes the times it generates,
;; which needn't include DTSTART
(defn exclusion-times
  "The local times that the recurrence `rule` generates from the local
  time `start`, as an EXRULE: without `start` unless the rule generates
  it, until `past?` is true of one, and at most COUNT."
  [rule start date? past?]
  (lazy-seq
   (cond->> (local-times (normalize rule start date?) start)
     past?         (take-while (complement past?))
     (:count rule) (take (:count rule)))))
