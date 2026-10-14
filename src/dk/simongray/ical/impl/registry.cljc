(ns ^:no-doc dk.simongray.ical.impl.registry
  "What RFC 5545 and its extensions say about each property: its default
  value type, the other types its VALUE parameter may give it, and whether
  its value is a list.")

;; Each entry has:
;;
;; - :type, the default value type, or the type to read a value without
;;   VALUE as, when the property has no default
;; - :types, the other types that VALUE can give
;; - :list?, true when the value is a comma-separated list
;; - :parts, the number of parts of a value separated by semicolons, or
;;   :many
;; - :value?, true when the property has no default type, so that VALUE
;;   is always written
(def properties
  "The properties that RFC 5545 and its extensions define, by name."
  {;; RFC 5545, section 3.7
   :calscale             {:type :text}
   :method               {:type :text}
   :prodid               {:type :text}
   :version              {:type :text}
   ;; RFC 5545, section 3.8.1
   :attach               {:type :uri :types #{:binary}}
   :categories           {:type :text :list? true}
   :class                {:type :text}
   :comment              {:type :text}
   :description          {:type :text}
   :geo                  {:type :float :parts 2}
   :location             {:type :text}
   :percent-complete     {:type :integer}
   :priority             {:type :integer}
   :resources            {:type :text :list? true}
   :status               {:type :text}
   :summary              {:type :text}
   ;; RFC 5545, section 3.8.2
   :completed            {:type :date-time}
   :dtend                {:type :date-time :types #{:date}}
   :due                  {:type :date-time :types #{:date}}
   :dtstart              {:type :date-time :types #{:date}}
   :duration             {:type :duration}
   :freebusy             {:type :period :list? true}
   :transp               {:type :text}
   ;; RFC 5545, section 3.8.3
   :tzid                 {:type :text}
   :tzname               {:type :text}
   :tzoffsetfrom         {:type :utc-offset}
   :tzoffsetto           {:type :utc-offset}
   :tzurl                {:type :uri}
   ;; RFC 5545, section 3.8.4, with RELATED-TO by RFC 9253, section 9.1
   :attendee             {:type :cal-address}
   :contact              {:type :text}
   :organizer            {:type :cal-address}
   :recurrence-id        {:type :date-time :types #{:date}}
   :related-to           {:type :text :types #{:uri :uid}}
   :url                  {:type :uri}
   :uid                  {:type :text}
   ;; RFC 5545, section 3.8.5, and EXRULE of RFC 2445, which RFC 5545
   ;; deprecates
   :exdate               {:type :date-time :types #{:date} :list? true}
   :rdate                {:type :date-time :types #{:date :period} :list? true}
   :rrule                {:type :recur}
   :exrule               {:type :recur}
   ;; RFC 5545, section 3.8.6
   :action               {:type :text}
   :repeat               {:type :integer}
   :trigger              {:type :duration :types #{:date-time}}
   ;; RFC 5545, section 3.8.7
   :created              {:type :date-time}
   :dtstamp              {:type :date-time}
   :last-modified        {:type :date-time}
   :sequence             {:type :integer}
   ;; RFC 5545, section 3.8.8.3
   :request-status       {:type :text :parts :many}
   ;; RFC 7986, section 5
   :name                 {:type :text}
   :refresh-interval     {:type :duration :value? true}
   :source               {:type :uri :value? true}
   :color                {:type :text}
   :image                {:type :uri :types #{:binary} :value? true}
   :conference           {:type :uri :value? true}
   ;; RFC 7953, section 3.2
   :busytype             {:type :text}
   ;; RFC 9073, section 6
   :location-type        {:type :text :list? true}
   :participant-type     {:type :text}
   :resource-type        {:type :text}
   :calendar-address     {:type :cal-address}
   :styled-description   {:type :text :types #{:uri} :value? true}
   :structured-data      {:type :text :types #{:binary :uri} :value? true}
   ;; RFC 9074, sections 6 and 8
   :acknowledged         {:type :date-time}
   :proximity            {:type :text}
   ;; RFC 9253, section 8
   :concept              {:type :uri}
   :link                 {:type :uri :types #{:uid :xml-reference} :value? true}
   :refid                {:type :text}})

;; Each entry has :required, the properties a component must have once,
;; :once, those it may have once at most, :should-once, those it should
;; have once at most, and :exclusive, the pairs that can't both be there
(def components
  "What RFC 5545 and its extensions say about the properties of each
  component they define, by name."
  {;; RFC 5545, section 3.6, with RFC 7986, section 4
   :vcalendar {:required #{:prodid :version}
               :once     #{:calscale :method :uid :last-modified :url
                           :refresh-interval :source :color}}
   ;; RFC 5545, section 3.6.1, with COLOR of RFC 7986
   :vevent    {:required    #{:dtstamp :uid}
               :once        #{:class :created :description :dtstart :geo
                              :last-modified :location :organizer :priority
                              :sequence :status :summary :transp :url
                              :recurrence-id :dtend :duration :color}
               :should-once #{:rrule}
               :exclusive   [[:dtend :duration]]}
   ;; RFC 5545, section 3.6.2
   :vtodo     {:required    #{:dtstamp :uid}
               :once        #{:class :completed :created :description :dtstart
                              :geo :last-modified :location :organizer
                              :percent-complete :priority :recurrence-id
                              :sequence :status :summary :url :due :duration
                              :color}
               :should-once #{:rrule}
               :exclusive   [[:due :duration]]}
   ;; RFC 5545, section 3.6.3
   :vjournal  {:required    #{:dtstamp :uid}
               :once        #{:class :created :dtstart :last-modified :organizer
                              :recurrence-id :sequence :status :summary :url
                              :color}
               :should-once #{:rrule}}
   ;; RFC 5545, section 3.6.4
   :vfreebusy {:required #{:dtstamp :uid}
               :once     #{:contact :dtstart :dtend :organizer :url}}
   ;; RFC 5545, section 3.6.5
   :vtimezone {:required #{:tzid}
               :once     #{:last-modified :tzurl}}
   :standard  {:required    #{:dtstart :tzoffsetto :tzoffsetfrom}
               :should-once #{:rrule}}
   :daylight  {:required    #{:dtstart :tzoffsetto :tzoffsetfrom}
               :should-once #{:rrule}}
   ;; RFC 5545, section 3.6.6, with RFC 9074, section 5
   :valarm    {:required #{:action :trigger}
               :once     #{:duration :repeat :uid :acknowledged}}
   ;; RFC 7953, section 3.1
   :vavailability {:required  #{:dtstamp :uid}
                   :once      #{:busytype :class :created :description :dtstart
                                :last-modified :location :organizer :priority
                                :sequence :summary :url :dtend :duration}
                   :exclusive [[:dtend :duration]]}
   :available     {:required  #{:dtstamp :dtstart :uid}
                   :once      #{:created :description :last-modified :location
                                :recurrence-id :rrule :summary :dtend :duration}
                   :exclusive [[:dtend :duration]]}
   ;; RFC 9073, sections 7.1 to 7.3
   :participant   {:required #{:participant-type :uid}
                   :once     #{:calendar-address :created :description :dtstamp
                               :geo :last-modified :priority :sequence :status
                               :summary :url}}
   :vlocation     {:required #{:uid}
                   :once     #{:description :geo :location-type :name}}
   :vresource     {:required #{:uid}
                   :once     #{:description :geo :name :resource-type}}})

;; RFC 5545, section 3.2.20, and RFC 9253, section 7
(def value-types
  "The value types that a VALUE parameter can name."
  #{:binary :boolean :cal-address :date :date-time :duration :float
    :integer :period :recur :text :time :uri :utc-offset :uid
    :xml-reference})

;; RFC 5545, sections 3.8.8.1 and 3.8.8.2: other properties are text
(defn property
  "What the registry says about the property named `k`, with a default of
  text for a property it doesn't know."
  [k]
  (get properties k {:type :text :unknown? true}))
