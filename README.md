ical-clj
========

This library reads and writes [iCalendar](https://www.rfc-editor.org/rfc/rfc5545),
the format of .ics files, calendar feeds and meeting invitations, in
Clojure and ClojureScript. A calendar is plain data, and the library
expands recurring events in their time zones and builds the scheduling
messages that invitations and replies are made of.

- **Nothing lost.** A calendar that you write back has every component,
  property and parameter it came with, in order, including those of
  other vendors. A value that doesn't read keeps its text.
- **Recurrence by the RFC.** Every part of a recurrence rule, with extra
  and excluded dates and moved instances, in the event's own time zone.
  It passes the examples of RFC 5545 and the recurrence tests of libical.
- **Time zones from the platform.** IANA zones come from the database of
  the JVM or the JavaScript engine, Outlook's Windows zone names are
  mapped by [CLDR](https://cldr.unicode.org/), and a file's own
  definitions fill in the rest.
- **Validation errors as data.** The parser is lenient with what real
  calendars contain, and it can tell you what it forgave.
- **The same results everywhere.** One codebase runs on the JVM, in Node
  and in browsers, with no dependencies.

> The design and the reasons for it are in the [proposal](doc/proposal.md).
> Like Simon Gray's other libraries, it was developed with assistance
> from frontier LLMs.

Getting started
---------------

The library needs Clojure 1.11+ and Java 11+. It isn't on Clojars yet, so
use the SHA of the latest commit on `master` in your `deps.edn`:

```clojure
dk.simongray/ical-clj {:git/url "https://github.com/simongray/ical-clj"
                       :git/sha "…"}
```

In shadow-cljs a Git dependency only works through `deps.edn`, so set
`:deps true` in your `shadow-cljs.edn` too.

Reading and writing
-------------------

Parse a calendar with `ical/parse` and write it back with
`ical/serialize`:

```clojure
(require '[dk.simongray.ical :as ical])

(def cal
  (ical/parse "BEGIN:VCALENDAR\r
VERSION:2.0\r
PRODID:-//Grundejerforeningen//DA\r
BEGIN:VEVENT\r
UID:bestyrelse@gf.example\r
DTSTAMP:20261001T090000Z\r
DTSTART;TZID=Europe/Copenhagen:20261012T190000\r
DURATION:PT2H\r
RRULE:FREQ=MONTHLY;BYDAY=2MO\r
SUMMARY;LANGUAGE=da:Bestyrelsesmøde\r
END:VEVENT\r
END:VCALENDAR\r
"))

(def event (first (ical/components cal :vevent)))

(ical/property event :dtstart)
;; => {:name :dtstart, :params {:tzid "Europe/Copenhagen"},
;;     :value "2026-10-12T19:00:00"}
```

A component is a map of its `:name`, its `:properties` and its
`:components`. A property has a `:name`, its `:params` and a `:value` in
the form that [jCal](https://www.rfc-editor.org/rfc/rfc7265) gives it: a
date-time is a string like the one above, a recurrence rule a map of its
parts, text is unescaped, and numbers are numbers. Get a value with
`ical/value`:

```clojure
(ical/value event :rrule)
;; => {:freq "MONTHLY", :byday ["2MO"]}
```

For a stream with several calendars, use `ical/parse-all`.

Recurrence
----------

Get the instances of an event with `ical/occurrences`, as a lazy
sequence of events with a RECURRENCE-ID each:

```clojure
(->> (ical/occurrences cal event)
     (take 3)
     (map #(ical/value % :dtstart)))
;; => ("2026-10-12T19:00:00" "2026-11-09T19:00:00" "2026-12-14T19:00:00")
```

An event that another one overrides by its RECURRENCE-ID is replaced by
it, and RANGE=THISANDFUTURE moves the instances after it too. To get all
the instances between two dates, as a calendar feed or a CalDAV server
would, use `ical/expand`. For a recurrence rule on its own, the
[`rrule`](src/dk/simongray/ical/rrule.cljc) namespace parses, writes and
expands it:

```clojure
(require '[dk.simongray.ical.rrule :as rrule])

(take 3 (rrule/occurrences (rrule/parse "FREQ=MONTHLY;BYMONTHDAY=31")
                           "2026-01-31"))
;; => ("2026-01-31" "2026-03-31" "2026-05-31")
```

As RFC 5545 says, a monthly rule from the 31st skips the months that are
shorter. A time that doesn't occur, when the clocks go forward, is moved
forward by the gap, and DTSTART is always the first instance.

Time zones
----------

Get the instant of a date-time with `ical/instant`, which reads its TZID
in the calendar:

```clojure
(ical/instant cal (ical/property event :dtstart))
;; => #inst "2026-10-12T17:00:00.000-00:00"
```

The [`tz`](src/dk/simongray/ical/tz.cljc) namespace finds the zone that a
TZID names. It tries the platform's database first, then a Windows name
such as W. Europe Standard Time, then the calendar's own VTIMEZONE:

```clojure
(require '[dk.simongray.ical.tz :as tz])

(tz/local (tz/zone "W. Europe Standard Time") #inst "2026-10-12T17:00:00Z")
;; => "2026-10-12T19:00:00"
```

A calendar for others should define every zone it uses, which
`tz/with-timezones` takes care of.

Scheduling
----------

The [`itip`](src/dk/simongray/ical/itip.cljc) namespace builds the messages
of [iTIP](https://www.rfc-editor.org/rfc/rfc5546), such as a REQUEST from
the organizer and a REPLY from an attendee, and applies those that come
in. It sends nothing: `itip/content-type` gives the MIME type to send a
message by e-mail with, and `itip/originator` the address it should come
from.

```clojure
(require '[dk.simongray.ical.itip :as itip])

(def reply (itip/reply cal "mailto:simon@gf.example" "ACCEPTED"))

(itip/content-type reply)
;; => "text/calendar; method=REPLY; charset=UTF-8"
```

Validation errors
-----------------

The parser takes what it can from a broken file. To check a calendar,
ask for its validation errors, each with a `:path` into the calendar:

```clojure
(ical/validation-errors (ical/parse "BEGIN:VCALENDAR\r
BEGIN:VEVENT\r
DTSTART:20261012T100000Z\r
DTEND:20261012T090000Z\r
END:VEVENT\r
END:VCALENDAR\r
"))
;; => [{:error :missing-property, :level :error, :path [], :property :prodid}
;;     {:error :missing-property, :level :error, :path [], :property :version}
;;     {:error :missing-property, :level :error, :path [:components 0], :property :dtstamp}
;;     {:error :missing-property, :level :error, :path [:components 0], :property :uid}
;;     {:error :end-before-start, :level :error, :path [:components 0 :properties 1]}]
```

To ask whether there are errors, as opposed to warnings, use
`ical/valid?`.

Principles
----------

- **Standards first.** The code follows RFC 5545 with its verified
  errata, its extensions for new properties, event publishing, alarms,
  relationships and availability, and iTIP. It cites their sections.
- **Plain data.** Calendars are maps and vectors, and values are strings,
  numbers and maps, so they print, compare and convert to JSON as they
  are.
- **No time library.** The library does its own date arithmetic, because
  iCalendar skips dates that java.time would move, and gets zone offsets
  from the platform. Since dates and times are ISO 8601 strings, tick or
  java.time reads them as they are.
- **One codebase.** The library is written in `.cljc` and gives the same
  results everywhere, where the platforms' copies of the time zone
  database are the same version.

Development
-----------

```bash
dev/fetch.sh                      # once, for the test data and CLDR's table
clojure -X:test                   # the tests on the JVM
npm install                       # once, for the Node tests
clojure -M:cljs compile test      # the tests in Node
clojure -X:tables                 # the Windows zone names, from CLDR
```

The fetch script downloads the test data of
[libical](https://github.com/libical/libical),
[ical.js](https://github.com/kewisch/ical.js) and
[python icalendar](https://github.com/collective/icalendar) into
`dev-resources/` (which Git ignores), at the commits that the library
follows. The examples of RFC 5545 are in the tests themselves.

The public namespaces are the root, `rrule`, `tz`, `itip` and `jcal`. The
internal ones are under `impl`:

- `impl.lines`, content lines and their folding
- `impl.values`, the value types
- `impl.parser` and `impl.serializer`, which read and write components
- `impl.registry`, what the RFCs say about each property and component
- `impl.civil` and `impl.expansion`, date arithmetic and recurrence rules
- `impl.recurrence`, recurrence sets and their instances
- `impl.zones` and `impl.windows`, time zones and Windows zone names
- `impl.validation`, validation errors

License
-------

The ical-clj project is licensed under the [MIT licence](LICENSE). The
table of Windows zone names in `impl/windows.cljc` is made from CLDR,
under the [Unicode License v3](https://www.unicode.org/license.txt).
