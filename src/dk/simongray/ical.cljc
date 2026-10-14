(ns dk.simongray.ical
  "Read and write iCalendar (RFC 5545) as plain data.

  A component is a map of:

  - :name, a lower-case keyword, e.g. :vcalendar or :vevent
  - :properties, a vector of its properties in order
  - :components, a vector of the components inside it, e.g. the VALARMs
    of a VEVENT

  A property is a map of:

  - :name, a lower-case keyword, e.g. :dtstart or :x-wr-calname
  - :params, a map of lower-case keywords to strings (or to vectors of
    strings, for a parameter with several values), e.g. {:tzid
    \"Europe/Copenhagen\"}
  - :value, in the form that jCal gives it (RFC 7265, section 3.6)
  - :type, the value type as a keyword, but only when it isn't the
    property's default, e.g. :date for a DTSTART that is a date

  A key is left out when it would be empty. A date is a string like
  \"2026-10-12\", a date-time one like \"2026-10-12T19:00:00\" (with a Z
  in UTC) and a duration one like \"PT2H\". A recurrence rule is a map
  like {:freq \"WEEKLY\" :byday [\"MO\"]}. Text is unescaped, numbers and
  booleans are numbers and booleans, and a property that holds a list
  (e.g. CATEGORIES or EXDATE) has a vector.

  A value that doesn't parse as its type keeps its original text, with
  the :type :unknown. So does an unknown property whose text would change
  if it were unescaped."
  (:require [dk.simongray.ical.impl.busy :as busy]
            [dk.simongray.ical.impl.lines :as lines]
            [dk.simongray.ical.impl.parser :as parser]
            [dk.simongray.ical.impl.recurrence :as recurrence]
            [dk.simongray.ical.impl.serializer :as serializer]
            [dk.simongray.ical.impl.validation :as validation]
            [dk.simongray.ical.tz :as tz]))

(defn parse-all
  "The top-level components of the text `s`, usually VCALENDARs, in
  order. RFC 5545 lets a stream hold several (section 3.4).

  The text can also be bytes of UTF-8 (a byte array, or in JavaScript a
  Uint8Array), which keeps a character intact when a fold splits it. The
  parser is lenient: it skips the lines that aren't content lines and
  closes the components that the text leaves open. To see what it
  forgave, call validation-errors on the text."
  [s]
  (:components (parser/read-stream (lines/text s))))

(defn parse
  "The first top-level component of the text `s`, usually a VCALENDAR, or
  nil when there's none. It parses the text as parse-all does.

      (parse \"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nEND:VCALENDAR\\r\\n\")
      ;; => {:name :vcalendar :properties [{:name :version :value \"2.0\"}]}"
  [s]
  (first (parse-all s)))

(defn serialize
  "The text of the component `c`, or of each component in the sequence
  `c`, as folded content lines that end with CRLF (RFC 5545, section
  3.1)."
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
  "The properties of the component `c` in order, or only those named `k`."
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
  "The `opts` of a function that resolves times, with a TZID as :floating
  replaced by its zone."
  [opts]
  (cond-> opts
    (string? (:floating opts)) (update :floating tz/zone)))

(defn- ->inst
  "The inst of `t` seconds from the epoch."
  [t]
  #?(:clj (java.util.Date. (long (* 1000 t))) :cljs (js/Date. (* 1000 t))))

(defn instant
  "The inst of the date-time property `p` in the calendar `cal`, in the
  zone of its TZID (see tz/zone), or nil when it has none. A date gives
  the first moment of its day. Besides the `opts` of tz/zone, it takes:

  - :floating, a zone or a TZID for floating times and dates without a
    TZID, which otherwise have no instant. A time whose TZID doesn't
    resolve counts as floating.

      (instant cal (property event :dtstart))
      ;; => #inst \"2026-10-12T17:00:00.000-00:00\""
  ([cal p]
   (instant cal p {}))
  ([cal p opts]
   (some-> (recurrence/instant-of cal p (zone-opts opts)) ->inst)))

(defn end
  "The property that ends the component `c` in the calendar `cal`, by RFC
  5545, sections 3.6.1 and 3.6.2. It's the first of these that applies:

  - its DTEND, or its DUE for a VTODO
  - a DTEND (or DUE) of its DTSTART plus its DURATION
  - a DTEND of the next day, when DTSTART is a date
  - its DTSTART

  It's nil when the component has neither a DTSTART nor an end. It takes
  the same `opts` as instant."
  ([cal c]
   (end cal c {}))
  ([cal c opts]
   (recurrence/end cal c (zone-opts opts))))

(defn occurrences
  "The instances of the component `c` in the calendar `cal`, as a lazy
  sequence in the order of their recurrence ids (RFC 5545, section
  3.8.5). It takes the same `opts` as instant.

  The instances are DTSTART and those of the RRULEs and RDATEs, without
  those of the EXDATEs. Each one is `c` without its rules, moved to the
  start of the instance with its duration kept, and with a RECURRENCE-ID.
  A component of `cal` with the same UID and that RECURRENCE-ID takes the
  place of the instance. With RANGE=THISANDFUTURE, it moves the later
  instances too. A component that doesn't recur is its own only instance.

      (->> (occurrences cal event) (take 2) (map #(value % :dtstart)))
      ;; => (\"2026-10-12T19:00:00\" \"2026-11-09T19:00:00\")"
  ([cal c]
   (occurrences cal c {}))
  ([cal c opts]
   (recurrence/occurrences cal c (zone-opts opts))))

(defn expand
  "The calendar `cal` with its recurring components replaced by their
  instances that overlap the range from the inst `from` to the inst `to`,
  as CalDAV's expand does (RFC 4791, section 9.6.5). Its other components
  with a DTSTART stay only when they overlap the range. A floating time
  counts as UTC unless :floating in `opts` gives a zone. It takes the same
  `opts` as instant."
  ([cal from to]
   (expand cal from to {}))
  ([cal from to opts]
   (recurrence/expand cal (quot (inst-ms from) 1000) (quot (inst-ms to) 1000)
                      (zone-opts opts))))

(defn free-busy
  "A VFREEBUSY of the busy time of the calendar `cal` between the insts
  `from` and `to`, as a CalDAV server answers a free-busy query (RFC 4791,
  section 7.10). It has a FREEBUSY of the periods of each type, in UTC,
  with the overlapping ones merged:

  - BUSY, of the events that are neither TRANSPARENT nor CANCELLED, and
    of the busy periods of the calendar's VFREEBUSYs
  - BUSY-TENTATIVE, of the TENTATIVE events

  It takes the same `opts` as instant, and :now and :uid, the inst of its
  DTSTAMP (now by default) and its UID (a random one by default)."
  ([cal from to]
   (free-busy cal from to {}))
  ([cal from to opts]
   (busy/free-busy cal (quot (inst-ms from) 1000) (quot (inst-ms to) 1000)
                   (-> (zone-opts opts)
                       (update :now #(quot (inst-ms (or % #?(:clj (java.util.Date.) :cljs (js/Date.))))
                                           1000))
                       (update :uid #(or % (str (random-uuid))))))))

(defn validation-errors
  "The ways that `x`, a calendar or the text of one, breaks RFC 5545 and
  its extensions, as a vector of maps. Each has:

  - :error, a keyword, e.g. :missing-property or :end-before-start
  - :level, :error where the RFC says MUST and :warning where it says
    SHOULD
  - :path, the path that get-in takes to the component or property in the
    calendar (or for text, in the vector that parse-all returns)
  - keys with the details, e.g. :property, :tzid or :line

  For text, the errors also include the lines that the parser skipped and
  the components it had to close.

      (validation-errors cal)
      ;; => [{:error :missing-property :level :error :path [:components 0]
      ;;      :property :dtstamp}]"
  [x]
  (if (map? x)
    (validation/calendar-errors x)
    (validation/stream-errors (parser/read-stream (lines/text x)))))

(defn valid?
  "Whether `x`, a calendar or the text of one, has no validation errors
  other than warnings."
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
