ical-clj: a proposal
====================

This proposes ical-clj, a neutral iCalendar library for Clojure and
ClojureScript, with the coordinate dk.simongray/ical-clj and the root
namespace `dk.simongray.ical`. The name is free on Clojars. The proposal
comes from research alone on 12 October 2026, and none of its code runs
yet.

Clojure has no iCalendar parser that runs in ClojureScript. On the JVM
there are only ical4j wrappers that generate files. In ClojureScript
there are stale wrappers of rrule.js and ical.js. The library would read
and write calendars as plain data, expand recurrences in their time
zones and build scheduling messages. It would work like whatwg-url:

- one cljc codebase with the same results on the JVM, in Node and in
  browsers, on Clojure 1.11 and Java 11 or later, with no dependencies
- the platform's own code where every platform agrees, and the rest
  written in the library
- a short citation of the RFC section beside each rule
- errors as data
- test data from other implementations, fetched at pinned commits

It leaves out every choice that the RFCs leave open. Those belong in the
events library, which would build on it.


Scope
-----

The library follows RFC 5545 with its verified errata, and these
neighbours:

| RFC | What ical-clj does with it |
|---|---|
| [5545](https://www.rfc-editor.org/rfc/rfc5545) iCalendar | all of it: content lines, every component, the value types, recurrence and time zones |
| [6868](https://www.rfc-editor.org/rfc/rfc6868) parameter encoding | reads and writes ^n, ^^ and ^' (without them a parameter with a quote or a newline can't round-trip) |
| [7986](https://www.rfc-editor.org/rfc/rfc7986) new properties | knows COLOR, IMAGE, CONFERENCE, NAME and the rest, which Google, Apple and Fastmail write |
| [9073](https://www.rfc-editor.org/rfc/rfc9073) event publishing | knows PARTICIPANT, VLOCATION, VRESOURCE, STYLED-DESCRIPTION and STRUCTURED-DATA |
| [9074](https://www.rfc-editor.org/rfc/rfc9074) alarms | knows ACKNOWLEDGED and PROXIMITY, and UID and RELATED-TO in a VALARM |
| [9253](https://www.rfc-editor.org/rfc/rfc9253) relationships | knows LINK, CONCEPT, REFID and the new relation types |
| [7953](https://www.rfc-editor.org/rfc/rfc7953) availability | knows VAVAILABILITY and AVAILABLE (for bookings) and expands their rules |
| [5546](https://www.rfc-editor.org/rfc/rfc5546) iTIP | builds, checks and applies the messages of every method |
| [6047](https://www.rfc-editor.org/rfc/rfc6047) iMIP | gives the content type and who should have sent a message |
| [7809](https://www.rfc-editor.org/rfc/rfc7809) time zones by reference | reads calendars without VTIMEZONEs and can write them without |
| [7265](https://www.rfc-editor.org/rfc/rfc7265) jCal | converts both ways, since the values already take jCal's forms |

"Knows" means a row in the registry: a property's default value type,
where it may appear and how often. An extension costs rows rather than
code. The round trip keeps whatever the registry doesn't know anyway.

Left out:

- **RSCALE beyond the Gregorian calendar**
  ([RFC 7529](https://www.rfc-editor.org/rfc/rfc7529)). The other
  calendars need conversions that neither java.time nor Intl offers on
  every platform. Such rules are kept and reported, not expanded.
- **xCal** ([RFC 6321](https://www.rfc-editor.org/rfc/rfc6321)). I found
  no current client or server that uses it.
- **CalDAV, TZDIST and sending mail.** These are protocols for other
  libraries. A CalDAV library would test against caldav-server-tester.

For later:

- **RSCALE=GREGORIAN with SKIP,** which is small. It's how a rule says
  "the last day of the month when the 31st doesn't exist", which is what
  Outlook does anyway.
- **vCard** as a second namespace on the same content lines. Most cards
  in use are vCard 3.0 (RFC 2426), so it should read both. A revision of
  vCard 4.0 (draft-ietf-calext-vcard4-bis) started in July 2026.
- **JSCalendar.** None of it is an RFC yet. The revision
  (draft-ietf-calext-jscalendarbis-22, 5 October 2026) is in AD
  evaluation, and the conversion
  (draft-ietf-calext-jscalendar-icalendar-28, 16 September 2026) waits
  for a revised draft. JMAP Calendars sits blocked in the RFC Editor
  queue until the revision is done. The conversion keeps what it can't
  map as jCal, which ical-clj will already have.
- **The calext drafts on their way,** e.g. ical-tasks (in the RFC Editor
  queue) and the JSCalendar extensions (in last call until 14 October,
  adding COORDINATES). They'll be registry rows.


The data model
--------------

A calendar is plain data that prints as EDN:

- **A component** is a map of its :name, its :properties and its
  :components, e.g. a VCALENDAR that holds VEVENTs that hold VALARMs.
- **A property** is a map of its :name, its :params and its :value. It
  has a :type only when the value isn't of the property's default type,
  e.g. a DTSTART that's a DATE. Empty :params and :components are left
  out.
- **Names are lower-case keywords** for components, properties and
  parameters alike. Values are never keywords, so PARTSTAT=ACCEPTED is
  {:partstat "ACCEPTED"}.
- **Values take the forms of jCal** (RFC 7265, section 3.6): a date is
  "2026-10-12", a date-time "2026-10-12T19:00:00" (with a Z in UTC), a
  duration "PT2H" and a recurrence rule a map of its parts. Text is
  unescaped and numbers are numbers. A property that holds a list (e.g.
  CATEGORIES or EXDATE) has a vector.

This VEVENT:

```
BEGIN:VEVENT
UID:bestyrelse@gf.example
DTSTAMP:20261001T090000Z
DTSTART;TZID=Europe/Copenhagen:20261012T190000
DURATION:PT2H
RRULE:FREQ=MONTHLY;BYDAY=2MO
EXDATE;TZID=Europe/Copenhagen:20261214T190000
SUMMARY;LANGUAGE=da:Bestyrelsesmøde
ATTENDEE;CN=Simon Gray;PARTSTAT=ACCEPTED:mailto:simon@gf.example
X-MICROSOFT-CDO-BUSYSTATUS:BUSY
BEGIN:VALARM
ACTION:DISPLAY
TRIGGER:-PT15M
DESCRIPTION:Bestyrelsesmøde
END:VALARM
END:VEVENT
```

is this map:

```clojure
{:name       :vevent
 :properties [{:name :uid :value "bestyrelse@gf.example"}
              {:name :dtstamp :value "2026-10-01T09:00:00Z"}
              {:name   :dtstart
               :params {:tzid "Europe/Copenhagen"}
               :value  "2026-10-12T19:00:00"}
              {:name :duration :value "PT2H"}
              {:name :rrule :value {:freq "MONTHLY" :byday ["2MO"]}}
              {:name   :exdate
               :params {:tzid "Europe/Copenhagen"}
               :value  ["2026-12-14T19:00:00"]}
              {:name   :summary
               :params {:language "da"}
               :value  "Bestyrelsesmøde"}
              {:name   :attendee
               :params {:cn "Simon Gray" :partstat "ACCEPTED"}
               :value  "mailto:simon@gf.example"}
              {:name :x-microsoft-cdo-busystatus :value "BUSY"}]
 :components [{:name       :valarm
               :properties [{:name :action :value "DISPLAY"}
                            {:name :trigger :value "-PT15M"}
                            {:name :description :value "Bestyrelsesmøde"}]}]}
```

**Why ISO strings rather than objects or maps of fields.** They're
checked and canonical, so two equal values are equal. They're the strings
that java.time, Temporal, tick and JSCalendar all read as they are. They
can hold a leap second (23:59:60) and a TZID that only the file defines,
which java.time can't. They also make the conversion to jCal little more
than renaming keys, and the JSCalendar conversion keeps what it doesn't
map as jCal.

**Why maps rather than jCal's arrays.** Maps read better in Clojure and
can leave out what's empty, as whatwg-url's URL maps do. Properties stay
a vector, though. A map by name would lose their order and make repeated
properties awkward (icalendar-rs lost repeated properties that way).

**Lossless means that parsing what `serialize` wrote gives the same
calendar.** Everything is kept in order: every component, property and
parameter, X- and IANA names, and a value that doesn't parse, as its
text. Writing never sorts, trims, adds defaults or re-escapes a value it
doesn't know. What may change is what RFC 5545 says doesn't matter: the
folding of lines, the case of names, quotes that aren't needed and the
spelling of numbers. The text isn't kept byte for byte.


The API
-------

| Namespace | What it holds |
|---|---|
| `dk.simongray.ical` | parse, serialize, read a component, expand recurrences, validate |
| `dk.simongray.ical.rrule` | recurrence rules alone: `parse`, `serialize`, `occurrences` |
| `dk.simongray.ical.tz` | time zones: `zone`, `offset`, `instant`, `local`, `vtimezone`, `with-timezones` |
| `dk.simongray.ical.itip` | scheduling: `publish`, `request`, `reply`, `cancel` and the other methods, `messages`, `apply`, `newer?`, `validation-errors` |
| `dk.simongray.ical.jcal` | `calendar->jcal` and `jcal->calendar` |
| `dk.simongray.ical.impl.*` | content lines, the registry, date arithmetic, the steps of expansion, the platform's zones (all ^:no-doc) |

The root namespace has:

- `parse` and `parse-all`: the first calendar in a text, or all of them
  (a file may hold several, as section 3.4 allows)
- `serialize`: the text of a calendar, folded at 75 octets, with CRLF
  line ends
- `components`, `properties`, `property` and `value`: what a component
  holds, by name
- `instant`: the inst of a date-time property, in its calendar's zones
- `end`: the end of a component by section 3.6.1 (DTEND, DTSTART plus
  DURATION, or the next day for a DATE)
- `occurrences`: a lazy sequence of the instances of a recurring
  component, each a component with its RECURRENCE-ID
- `expand`: a calendar whose recurring components are replaced by their
  instances between two instants, as CalDAV's expand does (RFC 4791,
  section 9.6.5)
- `validation-errors` and `valid?`

```clojure
(require '[dk.simongray.ical :as ical])

(def cal (ical/parse (slurp "bestyrelse.ics")))
(def event (first (ical/components cal :vevent)))

(ical/value event :summary)
;; => "Bestyrelsesmøde"

(ical/instant cal (ical/property event :dtstart))
;; => #inst "2026-10-12T17:00:00.000-00:00"

(->> (ical/occurrences cal event)
     (take 3)
     (map #(ical/value % :dtstart)))
;; => ("2026-10-12T19:00:00" "2026-11-09T19:00:00" "2027-01-11T19:00:00")
```

Functions that need zones take the calendar, since its VTIMEZONEs may
define them. There's no global registry of zones, since ical4j's leaked
memory and broke Android. Nor is there a setting for the whole JVM like
ical4j's compatibility hints: leniency and the choice of zones are
options of each call.


Recurrence
----------

The engine is date arithmetic in cljc, grown from podcast-clj's
`util.time`. It reads all 14 rule parts with one algorithm that composes
them, and the table in section 3.3.10 (which parts expand a set and which
limit it) is data. The alternative shows in libical, which has separate
code for each frequency. Its BYSETPOS worked only for monthly and yearly
rules until version 4.

- **Rules run on the wall clock.** A rule makes local date-times in the
  zone of DTSTART, and only then does each one become an instant. That's
  how "every Monday at 19:00" stays at 19:00 when the clocks change.
- **Invalid dates are skipped** (section 3.3.10). A monthly rule from 31
  January has no instance in February, while java.time's plusMonths
  would give 28 February. Outlook clamps as java.time does. The RFC is
  clear, though, and RFC 7529 gives a rule that wants clamping a way to
  say so.
- **Times that don't exist are moved, not skipped.** RFC 5545 says both,
  and erratum 4271 settles it: a local time in a gap takes the offset
  from before the gap, as section 3.3.5 says for any date-time. So 02:30
  on the day the clocks go forward becomes 03:30. A time that occurs
  twice means the first of them.
- **DTSTART is always the first instance** and counts towards COUNT,
  even when the rule wouldn't make it. Section 3.8.5.3 calls such a set
  undefined, but the RFC's own "every Friday the 13th" example needs
  DTSTART in the set (its EXDATE removes it). RFC 8984, Apple and Google
  include it too. Those that leave it out are libical, dateutil, rrule.js
  and podcast-clj.
- **UNTIL compares instants** when DTSTART has a zone, since UNTIL is in
  UTC then. A floating DTSTART takes a floating UNTIL (erratum 4414).
  Google writes a DATE as UNTIL for a timed DTSTART, which is read as the
  end of that day and reported.
- **Durations follow section 3.8.5.3.** Each instance gets the exact
  duration from DTSTART to DTEND, or the nominal one of DURATION. A day
  is a day on the wall clock and an hour is an hour on the timeline
  (section 3.3.6).
- **A rule that can't make an instance ends.** The Gregorian calendar
  repeats itself every 400 years (weekdays included), so a rule such as
  FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30 that has made nothing once its
  periods have come round again never will. On such a rule ical.js
  hangs, sabre/vobject searches until the year 9999, and ical4j gives up
  after 1,000 steps without saying why.
- **The recurrence set is complete:** RRULE, RDATE (dates, date-times
  and periods), EXDATE, and the components that override an instance by
  its RECURRENCE-ID, with RANGE=THISANDFUTURE moving the later ones as
  well. RECURRENCE-IDs and EXDATEs are matched as instants, since iCloud
  writes a RECURRENCE-ID in UTC for a zoned DTSTART. A DATE EXDATE
  removes that day.


Time zones
----------

**One function differs per platform.** It gives the offset of an IANA zone
at an instant: ZoneRules on the JVM, and in JavaScript the parts of a
cached Intl.DateTimeFormat (which keep the seconds of old offsets, as
java.time does). The rest is cljc built on it:

- **A local time becomes an instant** by trying the offsets of the day
  before and the day after. In an overlap it takes the first, and in a
  gap the offset from before the gap (section 3.3.5). In the research
  this matched java.time's ZonedDateTime.ofLocal at every transition of
  every zone from 1850 to 2040.
- **Transitions are found by bisection,** since Intl doesn't expose them.
  No two transitions from 1800 to 2100 are closer than six days, so a
  scan in steps of a few days finds them all.
- **A VTIMEZONE becomes a zone** when the recurrence engine expands its
  STANDARD and DAYLIGHT rules.

**Which rules a TZID gets:**

1. The prefixes that some clients add are stripped, e.g.
   /mozilla.org/20050126_1/Europe/Berlin. For six years python icalendar
   read such TZIDs as UTC.
2. An IANA name that the platform knows takes the platform's rules. The
   match ignores case and renames (Kiev and Kyiv), since the engines
   disagree on both.
3. A Windows name such as W. Europe Standard Time takes its IANA zone
   from CLDR's windowsZones (142 names, under the Unicode licence). The
   table is generated into the library the way whatwg-url's Unicode
   tables are. The hand-made table in ical4j has wrong entries.
4. Otherwise the file's VTIMEZONE defines it, e.g. Outlook's "Customized
   Time Zone".
5. Otherwise the time has no instant, and validation reports the TZID.
   Where ical.js silently treats such a time as floating, ical-clj says
   so.

The platform wins over the file's VTIMEZONE because the file's copy is
often stale or covers only a few years. So does python icalendar, and
RFC 7809 notes that clients mostly ignore the VTIMEZONEs they get.
Thunderbird, Outlook and ical.js prefer the file, so an option lets the
file win. Either way the VTIMEZONE stays in the calendar.

**Writing:** Serializing a calendar adds nothing, but `tz/with-timezones`
adds a VTIMEZONE for each TZID that the calendar uses, made from the
platform's transitions over the years it spans. iTIP messages always get
them, since Outlook needs them.

**The data drifts, not the code.** The JDK and each browser carry their
own copy of the time zone database, at different versions, and several
releases in 2026 changed future offsets. On 15 December 2026,
America/Vancouver is -08:00 on JDK 24 and Node 24 but -07:00 in Firefox
157 and Safari 26.3. The library won't carry a database of its own to
hide this, as tick does in ClojureScript. Its tests compare the platforms only
where both report the same database version.

**No tick, and not Chronos yet.** The reasons against tick still hold. A
java.time value can't hold a leap second or a zone that only a VTIMEZONE
defines, and plusMonths clamps where the RFC skips. In ClojureScript,
tick also ships js-joda with its own zone database. The research found
one more reason: ZonedDateTime's plusDays keeps the old offset in an
overlap, where iCalendar wants the first occurrence. Since the values are
ISO 8601 strings, tick reads them as they are. The inst that `instant`
returns is what t/instant takes.

Tempo is now [Chronos](https://github.com/henryw374/chronos)
(com.widdindustries/chronos 0.9.2, November 2025). It needs Temporal at
runtime and has no offsets or transitions, which is all ical-clj would
want from it. Temporal reached stage 4 in March 2026 and ships in Firefox
139, Chrome 144 and Node 26. Safari has it only in Technology Preview
249 (July 2026), not in a stable release. Look again once stable Safari
has it, and then at Temporal itself rather than a wrapper: its
getTimeZoneTransition would replace the bisection.


iTIP
----

The `itip` namespace builds, checks and applies scheduling messages, and
sends nothing. Each message is a calendar with a METHOD, shaped by the
restriction tables in RFC 5546, section 3.

- **Building:** The functions `publish`, `request`, `reply`, `cancel`,
  `add`, `refresh`, `counter` and `decline-counter` each make a message
  of that method from a calendar, with the VTIMEZONEs it needs. E.g.
  `(itip/reply cal "mailto:simon@gf.example" "ACCEPTED")` keeps only
  that attendee and sets its PARTSTAT.
- **Deciding who gets what:** The `messages` function compares the old
  and the new version of a calendar, as one calendar user sees it. It
  returns the messages to send, each with its recipients, e.g. a REQUEST
  for the attendees and a CANCEL for those who were removed. SEQUENCE
  goes up when DTSTART, DTEND, DURATION, DUE, RRULE, RDATE, EXDATE or
  STATUS changed (section 2.1.4). This is the design of sabre/vobject's
  ITip\Broker, which Nextcloud uses. Its bugs show what to do better:
  compare typed values rather than text (an explicit INTERVAL=1 isn't a
  change), compare calendar addresses in one normalised form, and update
  one instance without replacing the rest.
- **Applying:** An incoming message updates a stored calendar through
  `apply`, e.g. with an attendee's PARTSTAT from a REPLY, unless it's
  older than what's stored. The `newer?` function decides that by UID,
  RECURRENCE-ID, SEQUENCE and DTSTAMP (section 2.1.5).
- **iMIP** is only data. The `content-type` function gives e.g.
  text/calendar; method=REQUEST; charset=UTF-8. The `originator`
  function gives the address a message should come from (the ORGANIZER
  of a request, the ATTENDEE of a reply). Comparing it with the mail's
  verified sender is up to the app.


Errors and validation
---------------------

Real files often break the RFC. iCloud's Danish holiday feed has a
DTSTAMP that is a DATE, and Outlook names zones that no database has. So
the parser is lenient, and like whatwg-url's it reports what it forgave
rather than refusing.

- **Parsing doesn't throw.** For text that has no VCALENDAR, `parse`
  returns nil.
- **A value that doesn't parse keeps its text,** with :type :unknown, and
  it's written back as it came (as python icalendar's vBroken does). A
  line that isn't a content line is dropped.
- **Errors never enter the calendar.** In libical they went into the tree
  as X-LIC-ERROR properties, which leaked into exports and CalDAV
  uploads.
- **Validation errors are maps.** Each one that `validation-errors`
  returns has an :error keyword, a :level (:error for a MUST of the RFC,
  :warning for a SHOULD) and a :path that `get-in` takes into the
  calendar. Given text rather than a calendar, it also reports the lines
  it dropped, by number. A calendar is `valid?` when there's no :error.

```clojure
(ical/validation-errors cal)
;; => [{:error :missing-property :property :dtstamp
;;      :level :error :path [:components 0]}
;;     {:error :unsynchronized-dtstart :level :warning
;;      :path [:components 0 :properties 2]}]
```

The rules about which properties a component must have, or may have
once, are rows in the registry with the section they come from. For a
calendar with a METHOD, `itip/validation-errors` adds the restriction
tables of iTIP.


Tests
-----

There's no official suite, so the tests gather what other implementations
check. A script downloads them at pinned commits into `dev-resources/`
(which Git ignores), as whatwg-url's `dev/fetch.sh` does. Fetching isn't
redistribution, so the copyleft sources are fine too.

| Source | What it gives | Licence |
|---|---|---|
| RFC 5545, section 3.8.5.3 | the recurrence examples, typed into an EDN table with erratum 3883 applied (the errata against the other examples were rejected) | IETF Trust |
| [libical](https://github.com/libical/libical) | 151 rules with DTSTART and every instance in plain text, written by a developer of Cyrus (the server behind Fastmail), plus 34 RSCALE rules for later | MPL-2.0 or LGPL-2.1 |
| [ical.js](https://github.com/kewisch/ical.js) | 21 files each paired with its jCal, which makes a parse and round-trip corpus | MPL-2.0 |
| [rust-rrule](https://github.com/fmeringdal/rust-rrule) | the RFC examples in America/New_York with their offsets | MIT or Apache-2.0 |
| [dateutil](https://github.com/dateutil/dateutil) | about 219 rules that RFC 5545 can express, with expected dates in Python | BSD-3 |
| [sabre/vobject](https://github.com/sabre-io/vobject) | 73 rules and 52 tests of overridden instances, in PHP | BSD-3 |
| [recurring-ical-events](https://github.com/niccokunzmann/python-recurring-ical-events) | 97 calendars from bug reports | LGPL-3.0 |
| [python icalendar](https://github.com/collective/icalendar), [ical4j](https://github.com/ical4j/ical4j) | files from Google, Exchange, Outlook, Apple iCal, Thunderbird, Lotus Notes and others | BSD-2, BSD-3 |

A one-off script turns the tests written in code (dateutil's and
sabre's) into EDN, which is committed with its licence and source. Where
DTSTART doesn't match its rule, libical's and dateutil's expectations
leave DTSTART out, so those cases get it added. The suites of rrule.js,
rrule-go and most of rust-rrule are ports of dateutil's, so they add
nothing new.

The real files above are old. For current ones, throwaway accounts at
Google, Outlook.com, iCloud and Fastmail can each export a calendar of
made-up events with the hard cases: a recurring event with exceptions,
an all-day event, attendees, alarms and a zone. Those files are free of
anyone else's rights. Public feeds work too, e.g. Google's Danish
holidays, but they change with every download, so they're kept as
snapshots.

Generative tests check what the RFC promises:

- parsing what `serialize` wrote gives the same calendar, and no folded
  line is longer than 75 octets or splits a UTF-8 character (python
  icalendar once folded between a backslash and its n)
- every instance matches its rule, the instances come in order without
  duplicates, and COUNT holds
- a local time that exists comes back unchanged from its instant


Who builds on it
----------------

**In podcast-clj,** ical-clj would replace `util/time/rrule.cljc`, which
handles 8 of the 14 rule parts and counts days in UTC. Its rule serves
podcast:updateFrequency. (There's no liveSchedule in podcast-clj. Its
live items have a start and an end, and could be offered as an .ics of
VEVENTs.)

- The `coerce` namespace reads and writes the rule with `rrule/parse`
  and `rrule/serialize`. UNTIL becomes a date-time string instead of an
  inst, which changes podcast-clj's model.
- The `schedule/next-episode` function takes `rrule/occurrences` in the
  zone of the dtstart's offset. BYDAY=MO then means Monday where the
  podcast is made, not in UTC.
- Rules that give nil today start to work, e.g. HOURLY, BYHOUR,
  BYSETPOS and BYMONTHDAY in a weekly rule.
- DTSTART becomes the first instance, as above.

**The events library** keeps everything that the RFCs leave open, with
ical-clj and tick as its only dependencies. It holds:

- the event model, a profile of JSCalendar, mapped to and from a VEVENT
  through ical-clj (and later through the calext conversion)
- the mappings to schema.org, h-event, Activity Streams and the AT
  Protocol lexicon
- the guest list, with iMIP replies read by `itip/apply` and PARTSTAT
  turned into yes, no and maybe
- recurring events published as single occurrences up to a horizon,
  from `ical/occurrences`
- which property wins when a calendar has both, e.g. STYLED-DESCRIPTION
  over DESCRIPTION, or VLOCATION over LOCATION

**The association app** gets its calendar feed from `serialize` and its
invitations from `itip`. A booking is an event with the room as an
attendee (CUTYPE=ROOM), and a VAVAILABILITY can say when the fælleshus
may be booked. Turning events into free and busy time comes later.


The first release
-----------------

- content lines with RFC 6868, `parse`, `parse-all` and `serialize`
- the registry for every RFC in the scope table
- the reading functions, `instant` and `end`
- recurrence: all 14 rule parts and the whole recurrence set, as
  `occurrences`, `expand` and the `rrule` namespace
- time zones from the platform, from VTIMEZONEs and from Windows names,
  and `tz/with-timezones`
- `validation-errors` for RFC 5545
- iTIP: `publish`, `request`, `reply` and `cancel`, `newer?`, `apply` for
  replies, and the iMIP data

That covers phase 1 of the events note (an .ics per event, a webcal
feed, and invitations and replies by e-mail) and podcast-clj's switch.
The next release adds `messages`, the other iTIP methods and jCal.


Size
----

It would come to 3,000 to 4,000 lines of cljc, close to whatwg-url's
3,100:

| Part | Lines |
|---|---|
| content lines, values and the registry (a third of it tables) | 900 |
| reading, writing and validation | 600 |
| recurrence | 900 |
| time zones | 500 |
| iTIP | 500 |

The tests would add about 1,500 lines besides the fetched data. The
recurrence engine is the hard part. The rest follows the RFC closely.


Open questions
--------------

1. **Are ISO strings right for dates and times?** The alternative is a
   map of fields, e.g. {:year 2026 :month 10 :day 12 :hour 19}, which
   needs no parsing to read an hour but is longer, needs converting for
   every time library and moves away from jCal. I recommend the strings.
2. **Should the platform or the file's VTIMEZONE win for an IANA TZID?**
   Practice is split. I recommend the platform, with an option.
3. **Is a semantic round trip enough?** Keeping each line's original text
   until it changes would give a byte-for-byte round trip, at the cost of
   a second layer in every property. I recommend the semantic one, since
   iCalendar gives the bytes no meaning.
4. **Where does vCard go?** As `dk.simongray.ical.vcard` in this artifact,
   or as vcard-clj on the same content-line code? The first is simpler,
   while the second has the better name.
5. **Should RSCALE=GREGORIAN with SKIP come in the first release?** It's
   small, and it's the standard way to write what Outlook does with the
   31st.
6. **Will you make the throwaway accounts** at Google, Outlook.com,
   iCloud and Fastmail for the interop files? Only a person can.
