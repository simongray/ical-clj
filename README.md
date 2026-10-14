ical-clj
========

This library reads and writes [iCalendar](https://www.rfc-editor.org/rfc/rfc5545)
in Clojure and ClojureScript. It's the format of .ics files, calendar
feeds and meeting invitations. A calendar is plain data, and the library
expands its recurring events in their own time zones and builds
invitations and replies.

- **Nothing lost.** When you write a calendar back, it has every
  component, property and parameter it came with, in order, other
  vendors' included. A value that doesn't parse keeps its original text.
- **Recurrence by the RFC.** The library expands every part of a
  recurrence rule in the event's own time zone, with added and excluded
  dates and moved instances. It passes the examples of RFC 5545 and
  libical's recurrence tests.
- **Time zones from the platform.** The JVM or the JavaScript engine has
  the IANA zones, and [CLDR](https://cldr.unicode.org/) maps Outlook's
  Windows zone names onto them. A file's own definitions cover the rest.
- **Validation errors as data.** The parser forgives what real calendars
  get wrong, and can tell you what it forgave.
- **The same results everywhere.** One codebase runs on the JVM, in Node
  and in browsers, with no dependencies.

> The [proposal](doc/proposal.md) explains the design and the reasons for
> it. The library was developed with assistance from frontier LLMs.

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

Parse a calendar with `ical/parse`:

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
`:components`. A property has a `:name`, its `:params` and a `:value`.
Values take the forms of [jCal](https://www.rfc-editor.org/rfc/rfc7265):
a date-time is a string like the one above and a recurrence rule is a
map of its parts. Text comes unescaped, and numbers are numbers. Get a
value with `ical/value`:

```clojure
(ical/value event :rrule)
;; => {:freq "MONTHLY", :byday ["2MO"]}
```

To turn a calendar back into text, use `ical/serialize`. For a stream of
several calendars, use `ical/parse-all`. Both parse functions also take
the bytes of a file (a byte array, or a Uint8Array in JavaScript), which
is safer than its text, since a fold can split a character in two.

Recurrence
----------

To list the instances of a recurring event, call `ical/occurrences`. It
returns a lazy sequence of events, each with a RECURRENCE-ID:

```clojure
(->> (ical/occurrences cal event)
     (take 3)
     (map #(ical/value % :dtstart)))
;; => ("2026-10-12T19:00:00" "2026-11-09T19:00:00" "2026-12-14T19:00:00")
```

An event with the same UID and an instance's RECURRENCE-ID takes the
place of that instance. With RANGE=THISANDFUTURE, it moves the later
instances too. To get the instances between two dates, as a calendar
feed or a CalDAV server would, use `ical/expand`. For a recurrence rule on
its own, the [`rrule`](src/dk/simongray/ical/rrule.cljc) namespace
parses, writes and expands it:

```clojure
(require '[dk.simongray.ical.rrule :as rrule])

(take 3 (rrule/occurrences (rrule/parse "FREQ=MONTHLY;BYMONTHDAY=31")
                           "2026-01-31"))
;; => ("2026-01-31" "2026-03-31" "2026-05-31")
```

As RFC 5545 says, a monthly rule from the 31st skips the shorter months.
With RSCALE=GREGORIAN and SKIP=BACKWARD, as RFC 7529 has it, the rule
takes their last day instead.
A time that doesn't exist, because the clocks go forward, moves forward
by the length of the gap. DTSTART is always the first instance.

Time zones
----------

Get the instant of a date-time with `ical/instant`, which resolves its
TZID within the calendar:

```clojure
(ical/instant cal (ical/property event :dtstart))
;; => #inst "2026-10-12T17:00:00.000-00:00"
```

The [`tz`](src/dk/simongray/ical/tz.cljc) namespace finds the zone that a
TZID names. It tries the platform's database first, then the Windows
names such as W. Europe Standard Time, and then the calendar's own
VTIMEZONEs:

```clojure
(require '[dk.simongray.ical.tz :as tz])

(tz/local (tz/zone "W. Europe Standard Time") #inst "2026-10-12T17:00:00Z")
;; => "2026-10-12T19:00:00"
```

A calendar that you send to others should define every zone it uses.
Add the missing VTIMEZONEs with `tz/with-timezones`.

Free and busy time
------------------

To find when something is booked, e.g. a room, ask `ical/free-busy` for
the busy time of its calendar between two dates. It answers with a
VFREEBUSY, as a CalDAV server would. A VAVAILABILITY
([RFC 7953](https://www.rfc-editor.org/rfc/rfc7953)) in the calendar
makes the time outside its AVAILABLE times busy too, e.g. outside the
hours when the room can be booked.

Scheduling
----------

The [`itip`](src/dk/simongray/ical/itip.cljc) namespace builds the messages
of [iTIP](https://www.rfc-editor.org/rfc/rfc5546), such as an organizer's
REQUEST and an attendee's REPLY, and applies those you receive to a
stored calendar. Sending them is up to you. For e-mail,
`itip/content-type` gives the MIME type, and `itip/originator` the
address that a message should come from:

```clojure
(require '[dk.simongray.ical.itip :as itip])

(def reply (itip/reply cal "mailto:simon@gf.example" "ACCEPTED"))

(itip/content-type reply)
;; => "text/calendar; method=REPLY; charset=UTF-8"
```

When an event changes, `itip/messages` compares the old and the new
version and returns the messages to send, each with its recipient: e.g.
a REQUEST for the attendees and a CANCEL for those who were removed. Every
message keeps to the restrictions of its method in RFC 5546, which
`itip/validation-errors` checks.

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

To check for errors and ignore the warnings, use `ical/valid?`.

Principles
----------

- **Standards first.** The code follows RFC 5545 with its verified
  errata, the extensions it has had since, and iTIP. It cites the section
  of each rule.
- **Plain data.** Calendars are maps and vectors of strings and numbers,
  so they print, compare and convert to JSON as they are.
- **No time library.** Recurrence rules skip dates that java.time would
  move, so the library does its own date arithmetic and only asks the
  platform for zone offsets. Dates and times are ISO 8601 strings, which
  tick and java.time read as they are.
- **One codebase.** The library is written in `.cljc` and gives the same
  results everywhere, as long as the platforms have the same version of
  the time zone database.

Development
-----------

```bash
dev/fetch.sh                      # once, for the test data and the tables' sources
clojure -X:test                   # the tests on the JVM
npm install                       # once, for the Node tests
clojure -M:cljs compile test      # the tests in Node
clojure -M:cljs compile browser-test  # the tests in a browser, from target/browser-test
clojure -X:tables                 # the tables from CLDR and RFC 5546
```

The fetch script downloads the test data of
[libical](https://github.com/libical/libical),
[ical.js](https://github.com/kewisch/ical.js) and
[python icalendar](https://github.com/collective/icalendar) into
`dev-resources/` (which Git ignores), at the commits that the library
follows. The examples of RFC 5545 and the tests of dateutil and
sabre/vobject are in the tests themselves, the latter two made by the
scripts in `dev/`.

The public namespaces are the root, `rrule`, `tz`, `itip` and `jcal`. The
internal ones are under `impl`:

- `impl.lines`, content lines and their folding
- `impl.values`, the value types
- `impl.parser` and `impl.serializer`, which read and write components
- `impl.registry`, what the RFCs say about each property and component
- `impl.restrictions`, what each method of iTIP allows in a message
- `impl.civil` and `impl.expansion`, date arithmetic and recurrence rules
- `impl.recurrence`, recurrence sets and their instances
- `impl.zones` and `impl.windows`, time zones and Windows zone names
- `impl.validation`, validation errors

License
-------

The ical-clj project is licensed under the [MIT licence](LICENSE). The
table of Windows zone names in `impl/windows.cljc` is made from CLDR,
under the [Unicode License v3](https://www.unicode.org/license.txt).
