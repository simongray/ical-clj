(ns dk.simongray.ical
  "Read and write iCalendar (RFC 5545) as plain data.

  A component is a map of:

  - :name, a lower-case keyword, e.g. :vcalendar or :vevent
  - :properties, a vector of its properties in order
  - :components, a vector of the components inside it, e.g. a VEVENT's
    VALARMs

  A property is a map of:

  - :name, a lower-case keyword, e.g. :dtstart or :x-wr-calname
  - :params, a map of lower-case keyword to a string, or a vector of
    strings for a parameter with several values, e.g. {:tzid
    \"Europe/Copenhagen\"}
  - :value, the value in the form of jCal (RFC 7265, section 3.6)
  - :type, the value type as a keyword, only when it isn't the default
    type of the property, e.g. :date for a DTSTART that is a date

  Each key is left out when it's empty. Dates are strings like
  \"2026-10-12\", date-times like \"2026-10-12T19:00:00\" (with a Z in
  UTC), durations like \"PT2H\" and recurrence rules maps like {:freq
  \"WEEKLY\" :byday [\"MO\"]}. Text is unescaped, and numbers and
  booleans are numbers and booleans. A property that holds a list, e.g.
  CATEGORIES or EXDATE, has a vector. A value that doesn't read as its
  type keeps its text, with the :type :unknown, and so does an unknown
  property whose text would change if it were read as text."
  (:require [dk.simongray.ical.impl.parser :as parser]
            [dk.simongray.ical.impl.recurrence :as recurrence]
            [dk.simongray.ical.impl.serializer :as serializer]
            [dk.simongray.ical.impl.validation :as validation]
            [dk.simongray.ical.tz :as tz]))

(defn parse-all
  "The components at the top of the text `s`, usually VCALENDARs, in
  order. RFC 5545 lets a stream hold several (section 3.4).

  The parser is lenient: it leaves out the lines that aren't content lines,
  and closes the components that the text doesn't end. The function
  validation-errors reports what it left out."
  [s]
  (:components (parser/read-stream (str s))))

(defn parse
  "The first component at the top of the text `s`, usually a VCALENDAR, or
  nil when there's none. It reads the text as parse-all does.

      (parse \"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nEND:VCALENDAR\\r\\n\")
      ;; => {:name :vcalendar :properties [{:name :version :value \"2.0\"}]}"
  [s]
  (first (parse-all s)))

(defn serialize
  "The text of the component `c`, or of each of the components in the
  sequence `c`, as folded content lines that end with CRLF (RFC 5545,
  section 3.1)."
  [c]
  (if (map? c)
    (serializer/write-component c)
    (apply str (map serializer/write-component c))))

(defn components
  "The components inside the component `c`, or only those named `k`.

      (components cal :vevent)"
  ([c]
   (vec (:components c)))
  ([c k]
   (filterv #(= k (:name %)) (:components c))))

(defn properties
  "The properties of the component `c`, or only those named `k`, in order."
  ([c]
   (vec (:properties c)))
  ([c k]
   (filterv #(= k (:name %)) (:properties c))))

(defn property
  "The first property named `k` of the component `c`, or nil."
  [c k]
  (some #(when (= k (:name %)) %) (:properties c)))

(defn value
  "The value of the first property named `k` of the component `c`, or nil.

      (value event :summary)
      ;; => \"Bestyrelsesmøde\""
  [c k]
  (:value (property c k)))

(defn- zone-opts
  "The `opts` of a function that reads times, with a TZID as :floating
  turned into its zone."
  [opts]
  (cond-> opts
    (string? (:floating opts)) (update :floating tz/zone)))

(defn- ->inst
  "The inst of `t` seconds from the epoch."
  [t]
  #?(:clj (java.util.Date. (long (* 1000 t))) :cljs (js/Date. (* 1000 t))))

(defn instant
  "The inst of the date-time property `p` of the calendar `cal`, read in
  the zone of its TZID (see tz/zone), or nil when it has no instant. A date
  is the first moment of its day. The `opts` are those of tz/zone, and:

  - :floating, the zone or the TZID to read a floating time or a date
    without a TZID in, which otherwise has no instant

      (instant cal (property event :dtstart))
      ;; => #inst \"2026-10-12T17:00:00.000-00:00\""
  ([cal p]
   (instant cal p {}))
  ([cal p opts]
   (some-> (recurrence/instant-of cal p (zone-opts opts)) ->inst)))

(defn end
  "The property that ends the component `c` of the calendar `cal` (RFC
  5545, sections 3.6.1 and 3.6.2): its DTEND, or DUE for a VTODO, or else
  one made from its DTSTART and its DURATION, or the day after a DTSTART
  that is a date, or else its DTSTART. It's nil without a DTSTART or an
  end. The `opts` are those of instant."
  ([cal c]
   (end cal c {}))
  ([cal c opts]
   (recurrence/end cal c (zone-opts opts))))

(defn occurrences
  "The instances of the component `c` of the calendar `cal` as a lazy
  sequence, in the order of their recurrence ids (RFC 5545, section
  3.8.5). The `opts` are those of instant.

  Each instance is a component with a RECURRENCE-ID, without the rules,
  that starts when the instance does and lasts as long. The instances are
  those of DTSTART, the RRULEs and the RDATEs without those of the EXDATEs,
  and a component of `cal` with the same UID and a RECURRENCE-ID takes the
  place of its instance. With RANGE=THISANDFUTURE, it moves the instances
  after it too. A component that doesn't recur is its own only instance.

      (->> (occurrences cal event) (take 2) (map #(value % :dtstart)))
      ;; => (\"2026-10-12T19:00:00\" \"2026-11-09T19:00:00\")"
  ([cal c]
   (occurrences cal c {}))
  ([cal c opts]
   (recurrence/occurrences cal c (zone-opts opts))))

(defn expand
  "The calendar `cal` with its recurring components replaced by their
  instances that overlap the insts `from` to `to`, as CalDAV's expand does
  (RFC 4791, section 9.6.5), and its other components with a DTSTART left
  out unless they overlap them. A floating time is read as if in UTC,
  unless :floating of `opts` gives a zone. The `opts` are those of
  instant."
  ([cal from to]
   (expand cal from to {}))
  ([cal from to opts]
   (recurrence/expand cal (quot (inst-ms from) 1000) (quot (inst-ms to) 1000)
                      (zone-opts opts))))

(defn validation-errors
  "The ways that `x`, a calendar or the text of one, breaks RFC 5545 and
  its extensions, as a vector of maps. Each has:

  - :error, a keyword, e.g. :missing-property or :end-before-start
  - :level, :error for what the RFC says MUST be, :warning for what it
    says SHOULD be
  - :path, where get-in finds the component or the property in the
    calendar, or with text, in the vector that parse-all returns
  - what tells more, e.g. :property, :tzid or :line

  With text, the errors include the lines that the parser left out and
  the components it had to close.

      (validation-errors cal)
      ;; => [{:error :missing-property :level :error :path [:components 0]
      ;;      :property :dtstamp}]"
  [x]
  (if (map? x)
    (validation/calendar-errors x)
    (validation/stream-errors (parser/read-stream (str x)))))

(defn valid?
  "Whether `x`, a calendar or the text of one, has no validation errors
  of the level :error."
  [x]
  (not-any? #(= :error (:level %)) (validation-errors x)))

(comment
  (def cal
    (parse (str "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//x//y//EN\r\n"
                "BEGIN:VEVENT\r\nUID:1@x\r\nDTSTAMP:20261001T090000Z\r\n"
                "DTSTART;TZID=Europe/Copenhagen:20261012T190000\r\n"
                "RRULE:FREQ=MONTHLY;BYDAY=2MO\r\n"
                "SUMMARY;LANGUAGE=da:Bestyrelsesmøde\\, oktober\r\n"
                "END:VEVENT\r\nEND:VCALENDAR\r\n")))
  (components cal :vevent)
  (print (serialize cal))
  #_.)
