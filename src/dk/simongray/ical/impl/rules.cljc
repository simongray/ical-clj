(ns ^:no-doc dk.simongray.ical.impl.rules
  "The ways a recurrence rule breaks RFC 5545, section 3.3.10, and RFC
  7529, on its own and with its DTSTART.

  Each error is a map of :error, a keyword, :level, :error for what the
  RFCs say MUST be and :warning for what they say SHOULD be, and keys with
  the details, e.g. :part and :value."
  (:require [clojure.string :as str]
            [dk.simongray.ical.impl.civil :as civil]
            [dk.simongray.ical.impl.expansion :as expansion]
            [dk.simongray.ical.impl.values :as values]))

(defn- error
  "An error `k` of the `level`, with the `more` keys."
  ([k]
   (error k :error {}))
  ([k more]
   (error k :error more))
  ([k level more]
   (merge {:error k :level level} more)))

(def ^:private numeric-parts
  "The parts whose values are vectors of numbers, with the range of each,
  and whether the numbers can count from the end."
  {:bysecond   [0 60 false]  :byminute  [0 59 false]  :byhour   [0 23 false]
   :bymonthday [1 31 true]   :byyearday [1 366 true]  :byweekno [1 53 true]
   :bymonth    [1 12 false]  :bysetpos  [1 366 true]})

(defn- shape-error
  "The error of the part `k` of a rule, whose value `v` isn't of the form
  that a parsed rule has, or nil."
  [rscale k v]
  (when-not (case k
              :freq     (contains? values/freqs v)
              (:count :interval) (and (integer? v) (not (neg? v)))
              :until    (and (string? v)
                             (re-matches #"\d{4}-\d{2}-\d{2}(?:T\d{2}:\d{2}:\d{2}Z?)?" v))
              :byday    (and (sequential? v)
                             (every? #(and (string? %)
                                           (re-matches #"(?:[+-]?\d{1,2})?(?:SU|MO|TU|WE|TH|FR|SA)" %))
                                     v))
              :wkst     (contains? expansion/weekdays v)
              :skip     (contains? #{"OMIT" "BACKWARD" "FORWARD"} v)
              ;; RFC 7529, section 3: a month can be a leap month, e.g. 5L
              :bymonth  (and (sequential? v)
                             (every? #(or (integer? %) (and rscale (string? %) (re-matches #"\d{1,2}L" %)))
                                     v))
              (if (contains? numeric-parts k)
                (and (sequential? v) (every? integer? v))
                true))
    (error :invalid-part {:part k :value v})))

(defn- shape-errors
  "The errors of the parts of the `rule` that aren't of the form that a
  parsed rule has, and of a missing FREQ."
  [{:keys [rscale] :as rule}]
  (concat
   (when-not (contains? rule :freq)
     [(error :missing-part {:part :freq})])
   (keep (fn [[k v]] (shape-error rscale k v)) rule)))

;; RFC 5545, section 3.3.10: the ranges are in the comments of the
;; grammar, e.g. ordwk = 1*2DIGIT ;1 to 53
(defn- range-errors
  "The errors of the numbers of the `rule` that are out of their range."
  [{:keys [byday interval] :as rule}]
  (concat
   (for [[k [lo hi signed?]] numeric-parts
         v                   (get rule k)
         :when               (and (number? v)
                                  (not (or (<= lo v hi) (and signed? (<= (- hi) v (- lo))))))]
     (error :rule-part-out-of-range {:part k :value v}))
   (for [d     byday
         :let  [ordinal (second (re-matches #"([+-]?\d+)[A-Z]{2}" d))]
         :when (and ordinal (not (<= 1 (abs (parse-long (str/replace ordinal "+" ""))) 53)))]
     (error :rule-part-out-of-range {:part :byday :value d}))
   (when (= 0 interval)
     [(error :rule-part-out-of-range {:part :interval :value 0})])))

(defn- combination-errors
  "The errors of the parts of the `rule` that can't go together."
  [{:keys [freq byday bymonthday byyearday byweekno bysetpos rscale skip] :as rule}]
  (concat
   (when (and (:count rule) (:until rule))
     [(error :count-and-until)])
   (when (and bymonthday (= "WEEKLY" freq))
     [(error :bymonthday-not-allowed)])
   (when (and byyearday (#{"DAILY" "WEEKLY" "MONTHLY"} freq))
     [(error :byyearday-not-allowed)])
   (when (and byweekno (not= "YEARLY" freq))
     [(error :byweekno-not-yearly)])
   (when (and (some #(re-find #"\d" %) byday)
              (or (not (#{"MONTHLY" "YEARLY"} freq)) byweekno))
     [(error :byday-ordinal-not-allowed)])
   (when (and bysetpos
              (not-any? rule [:bysecond :byminute :byhour :byday :bymonthday :byyearday
                              :byweekno :bymonth]))
     [(error :bysetpos-alone)])
   ;; RFC 7529, section 3: SKIP MUST NOT be present unless RSCALE is
   (when (and skip (not rscale))
     [(error :skip-without-rscale)])))

(defn- date?
  "Whether `s` is a date rather than a date-time."
  [s]
  (not (str/includes? s "T")))

;; RFC 5545, section 3.3.10, with erratum 4414: UNTIL has the type of
;; DTSTART, and is in UTC when DTSTART has a zone or is in UTC
(defn- start-errors
  "The errors of the `rule` with the DTSTART `start`, which is `zoned?`
  when it has a zone or is in UTC."
  [{:keys [until] :as rule} start zoned?]
  (concat
   (when (and (date? start) (some rule [:bysecond :byminute :byhour]))
     [(error :time-rule-with-date)])
   (when (and until
              (or (not= (date? start) (date? until))
                  (and (not (date? start)) (not= zoned? (str/ends-with? until "Z")))))
     [(error :until-mismatch)])))

;; RFC 5545, section 3.8.5.3: DTSTART SHOULD be synchronized with the rule
(defn- unsynchronized
  "A warning when the `rule` doesn't generate the DTSTART `start`."
  [rule start]
  (when (contains? #{nil "GREGORIAN"} (:rscale rule))
    (let [x (civil/seconds start)]
      (when (not= x (first (expansion/local-times (expansion/normalize rule x (date? start)) x)))
        [(error :unsynchronized-dtstart :warning {})]))))

(defn errors
  "The errors of the recurrence `rule`, and with its DTSTART `start` (a
  date or a date-time, or nil), which is `zoned?` when it has a zone or is
  in UTC, the errors of the rule with it. A rule whose parts aren't of
  the form of a parsed rule has only those errors."
  [rule start zoned?]
  (let [shape (shape-errors rule)]
    (vec (if (seq shape)
           shape
           (concat (range-errors rule)
                   (combination-errors rule)
                   (when (string? start)
                     (concat (start-errors rule start zoned?)
                             (unsynchronized rule start))))))))
