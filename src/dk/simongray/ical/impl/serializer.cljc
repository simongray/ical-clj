(ns ^:no-doc dk.simongray.ical.impl.serializer
  "Write components as the text of an iCalendar stream, by RFC 5545,
  sections 3.1 to 3.6: folded content lines that end with CRLF."
  (:require [clojure.string :as str]
            [dk.simongray.ical.impl.lines :as lines]
            [dk.simongray.ical.impl.registry :as registry]
            [dk.simongray.ical.impl.values :as values]))

(defn- value-text
  "The text of the value `v` of the type `type` of the property `entry`."
  [entry type v]
  (cond
    (= :unknown type) (str v)
    (:list? entry)    (str/join "," (map #(values/write-value type %)
                                         (if (sequential? v) v [v])))
    (:parts entry)    (str/join ";" (map #(values/write-value type %) v))
    :else             (values/write-value type v)))

;; RFC 5545, section 3.2.20: VALUE is written when the type isn't the
;; property's default, or when the property has none
(defn- params-with-value
  "The `params` of a property of the registry `entry`, with VALUE when the
  value of the type `type` needs it."
  [entry type params]
  (if (and (not= :unknown type)
           (or (:value? entry) (not= type (:type entry))))
    (merge {:value (str/upper-case (name type))} params)
    params))

(defn write-property
  "The content line of the property `prop`, folded."
  [{k :name :keys [params value] :as prop}]
  (let [entry (registry/property k)
        type  (or (:type prop) (:type entry))]
    (lines/write-line k
                      (params-with-value entry type params)
                      (value-text entry type value))))

(defn write-component
  "The content lines of the component `c`, from its BEGIN to its END."
  [{k :name :keys [properties components]}]
  (let [n (str/upper-case (name k))]
    (str "BEGIN:" n "\r\n"
         (apply str (map write-property properties))
         (apply str (map write-component components))
         "END:" n "\r\n")))
