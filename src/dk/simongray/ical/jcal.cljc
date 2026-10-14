(ns dk.simongray.ical.jcal
  "Convert calendars to and from jCal, the JSON form of iCalendar (RFC
  7265). The jCal is Clojure data that any JSON library can write and
  read: vectors, maps with string keys, strings, numbers and booleans.

  A calendar's values already take jCal's forms, so only the shape of its
  components, properties and parameters changes."
  (:require [clojure.string :as str]
            [dk.simongray.ical.impl.registry :as registry]))

(defn- property->jcal
  "The jCal array of the property `p` (RFC 7265, section 3.4)."
  [{k :name :keys [params value] :as p}]
  (let [entry  (registry/property k)
        type   (or (:type p) (:type entry))
        params (into {} (for [[pk pv] params] [(name pk) pv]))
        jvalue (fn [v]
                 (cond
                   (and (= :period type) (map? v)) [(:start v) (or (:end v) (:duration v))]
                   (and (= :recur type) (map? v))  (into {} (for [[rk rv] v] [(name rk) rv]))
                   :else                           v))]
    (into [(name k) params (name type)]
          (if (and (:list? entry) (not= :unknown type) (sequential? value))
            (map jvalue value)
            [(jvalue value)]))))

(defn calendar->jcal
  "The component `c`, usually a VCALENDAR, as jCal (RFC 7265, section
  3.3): a vector of its name, its properties and its components."
  [c]
  [(name (:name c))
   (mapv property->jcal (:properties c))
   (mapv calendar->jcal (:components c))])

;; RFC 7265, section 3.6.10: a BYxxx part can be a single value or an
;; array, and FREQ and WKST keep their case
(defn- jcal->rule
  "The recurrence rule of the jCal object `m`."
  [m]
  (into {} (for [[k v] m
                 :let  [k (keyword (str/lower-case (name k)))]]
             [k (cond
                  (#{:freq :wkst :rscale :skip} k)    (str/upper-case v)
                  (= :byday k)                        (mapv str/upper-case (if (sequential? v) v [v]))
                  (str/starts-with? (name k) "by")    (if (sequential? v) (vec v) [v])
                  :else                               v)])))

(defn- jcal->value
  "The value `v` of the jCal type `type`."
  [type v]
  (cond
    (and (= :period type) (sequential? v))
    (let [[start end] v]
      (if (re-find #"^[+-]?P" end) {:start start :duration end} {:start start :end end}))

    (and (= :recur type) (map? v)) (jcal->rule v)
    :else                          v))

(defn- jcal->property
  "The property of the jCal array `a`."
  [[k params type & values :as a]]
  (let [k      (keyword (str/lower-case k))
        entry  (registry/property k)
        type   (keyword (str/lower-case type))
        vs     (mapv #(jcal->value type %) values)
        value  (if (and (not= :unknown type) (or (:list? entry) (< 1 (count vs))))
                 vs
                 (first vs))
        params (into {} (for [[pk pv] params]
                          [(keyword (str/lower-case (name pk))) (if (sequential? pv) (vec pv) pv)]))]
    (cond-> {:name k}
      (seq params)                          (assoc :params params)
      (not= type (:type entry))             (assoc :type type)
      true                                  (assoc :value value))))

(defn jcal->calendar
  "The component of the jCal array `a`, the inverse of calendar->jcal."
  [[k properties components :as a]]
  (cond-> {:name (keyword (str/lower-case k))}
    (seq properties) (assoc :properties (mapv jcal->property properties))
    (seq components) (assoc :components (mapv jcal->calendar components))))
