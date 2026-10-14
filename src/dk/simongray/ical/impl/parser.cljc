(ns ^:no-doc dk.simongray.ical.impl.parser
  "Parse the text of an iCalendar stream into components, by RFC 5545,
  sections 3.4 to 3.6.

  A component is a map of :name, :properties and :components, and a
  property is a map of :name, :params, :type and :value, as the root
  namespace describes them. A key is left out when it would be empty, and
  :type when it's the property's default."
  (:require [clojure.string :as str]
            [dk.simongray.ical.impl.lines :as lines]
            [dk.simongray.ical.impl.registry :as registry]
            [dk.simongray.ical.impl.values :as values]))

(def ^:private type-order
  "The order in which a value without VALUE is tried as the other types of
  its property."
  [:date-time :date :period :duration :uri :uid :xml-reference :binary
   :text])

(defn- candidate-types
  "The types to try for a value of the property `entry`, the default first."
  [entry]
  (cons (:type entry) (filter (or (:types entry) #{}) type-order)))

(defn- read-typed
  "The value of the type `type` in the text `text` of a property with the
  registry `entry`, or ::values/invalid."
  [entry type text]
  (let [parts (:parts entry)]
    (cond
      (:list? entry)
      (let [vs (mapv #(values/read-value type %) (values/split-list type text))]
        (if (some #{::values/invalid} vs) ::values/invalid vs))

      parts
      (let [vs (mapv #(values/read-value type %) (values/split-escaped text \;))]
        (if (or (some #{::values/invalid} vs)
                (and (number? parts) (not= parts (count vs))))
          ::values/invalid
          vs))

      :else
      (values/read-value type text))))

(defn- unknown
  "The property named `k` with the `params` and the value `text` kept as it
  was written."
  [k params text]
  (cond-> {:name k}
    (seq params) (assoc :params params)
    true         (assoc :type :unknown :value text)))

(defn- typed
  "The property named `k` of the registry `entry` with the `params`, a
  value `v` of the type `type`."
  [k entry params type v]
  (cond-> {:name k}
    (seq params)               (assoc :params params)
    (not= type (:type entry))  (assoc :type type)
    true                       (assoc :value v)))

(defn- explicit-type
  "The value type that the VALUE parameter `v` names, :unknown for one
  that RFC 5545 doesn't define, or nil without one."
  [v]
  (when v
    (let [t (when (string? v) (lines/->keyword v))]
      (if (contains? registry/value-types t) t :unknown))))

;; RFC 5545, section 3.2.20: VALUE gives the type of the value, and a
;; property without it has its default type. Values that a client wrote
;; without VALUE, e.g. a DTSTART that is a date, are read as the first of
;; the property's other types that fits.
(defn read-property
  "The property of the content line `line`, as lines/read-line returns it."
  [{k :name :keys [params text]}]
  (let [entry    (registry/property k)
        explicit (explicit-type (:value params))
        others   (dissoc params :value)]
    (cond
      (= :unknown explicit)
      (unknown k params text)

      explicit
      (let [v (read-typed entry explicit text)]
        (if (= ::values/invalid v)
          (unknown k params text)
          (typed k entry others explicit v)))

      ;; RFC 5545, sections 3.8.8.1 and 3.8.8.2: an unknown property is
      ;; text, but its text is kept as written when unescaping and
      ;; escaping it again would change it
      (:unknown? entry)
      (let [v (values/read-text text)]
        (if (= text (values/write-text v))
          (typed k entry others :text v)
          (unknown k params text)))

      :else
      (or (some (fn [type]
                  (let [v (read-typed entry type text)]
                    (when (not= ::values/invalid v)
                      (typed k entry others type v))))
                (candidate-types entry))
          (unknown k params text)))))

(defn- finish
  "The open component `c`, without its empty keys."
  [c]
  (cond-> c
    (empty? (:properties c)) (dissoc :properties)
    (empty? (:components c)) (dissoc :components)))

(defn- close-top
  "The `stack` of open components with its top one closed into the one
  below it, or into the `done` components when there's none."
  [[stack done]]
  (let [c (finish (peek stack))
        stack (pop stack)]
    (if (seq stack)
      [(update-in stack [(dec (count stack)) :components] conj c) done]
      [stack (conj done c)])))

(defn- close
  "The `stack` and the `done` components with the open component named `k`
  closed, and those opened inside it, or nil when none is open."
  [stack done k]
  (when-let [i (some #(when (= k (:name (nth stack %))) %)
                     (range (dec (count stack)) -1 -1))]
    (nth (iterate close-top [stack done]) (- (count stack) i))))

;; Calendars nest four deep at most, and the functions that walk a
;; component recurse into the ones inside it
(def ^:private max-depth
  "The deepest that components nest in what the parser reads."
  64)

;; RFC 5545, section 3.4: icalstream = 1*icalobject, and each component is
;; between a BEGIN and an END. The number of open components of each name
;; tells at once whether an END closes one.
(defn read-stream
  "The components at the top of the text `s`, as a map of:

  - :components, in order
  - :dropped, the numbered lines that were left out: those that aren't
    content lines, an END without its BEGIN, a BEGIN nested deeper than
    64 components, and properties outside any component
  - :unclosed, the names of the components that the text didn't end"
  [s]
  (loop [[[_ text :as line] & more] (lines/lines s)
         stack   []
         open    {}
         done    []
         dropped []]
    (if (nil? line)
      {:components (second (nth (iterate close-top [stack done]) (count stack)))
       :dropped    dropped
       :unclosed   (mapv :name stack)}
      (let [{k :name v :text :as parsed} (lines/read-line text)]
        (cond
          (nil? parsed)
          (recur more stack open done (conj dropped line))

          (and (= :begin k) (not (str/blank? v)) (< (count stack) max-depth))
          (let [c (lines/->keyword v)]
            (recur more (conj stack {:name c :properties [] :components []})
                   (update open c (fnil inc 0)) done dropped))

          (and (= :end k) (pos? (get open (lines/->keyword v) 0)))
          (let [[stack' done] (close stack done (lines/->keyword v))]
            (recur more stack'
                   (reduce #(update %1 (:name %2) dec) open (subvec stack (count stack')))
                   done dropped))

          (or (#{:begin :end} k) (empty? stack))
          (recur more stack open done (conj dropped line))

          :else
          (recur more
                 (update-in stack [(dec (count stack)) :properties] conj
                            (read-property parsed))
                 open done dropped))))))
