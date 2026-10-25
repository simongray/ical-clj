Klart: a plan
=============

Klart is a calendar of good nights for observing the sky from Denmark. You
subscribe to a feed for your place, and a night shows up in your calendar
when the forecast says the sky will be clear and dark. The nights that look
exceptional also come as a push notification, since a subscribed calendar
can lag by hours. It does for telescope nights what el.simongray.dk does
for cheap electricity, and it runs on the same server. Klart is a working
name, from *klart vejr*.

The plan comes from research on 24 and 25 October 2026: the weather
services' documentation, terms and live responses, the calendar clients'
help pages and source code, and an analysis of ERA5 and DMI data for nights
at Copenhagen. It covers the service, the libraries it needs, and the steps
from the first commit to a service running on your server.


Clear Outside and Klart
-----------------------

Clear Outside (First Light Optics) shows a week of hourly forecasts for any
place: cloud at each height, visibility, fog, precipitation, wind, dew
point, humidity and more, under bands of civil, nautical and astronomical
darkness, with the Moon's phase and times. Its data comes from the
commercial Meteosource API. Its method isn't published, but 614 hours of
its pages suggest a traffic light on total cloud, good below about 20%. It
rates daylight hours too, and its road map still lists "Clear Sky Alerts"
as not done.

Klart differs in these ways:

- **Nights, not hours.** It scores the dark hours of each night for a kind
  of observing, e.g. deep sky, and publishes the night as one event.
- **Several models.** A night counts as clear only when independent models
  agree. In a pilot, when three models agreed on a clear night, about one
  in ten turned out cloudy.
- **Your calendar and your phone.** There's no app to open in the evening.

I found no service that publishes good observing nights as an iCalendar
feed.


What the Danish sky allows
--------------------------

These facts shape the choices below.

- **Good nights are rare.** By my count from ERA5 for 2001–2025 at the
  Copenhagen grid cell, there are about 60 nights a year with three or more
  clear hours of astronomical darkness, and about 33 of them have the Moon
  down or less than a quarter lit. About 13 are exceptional: four hours or
  more with little cloud at any height, no Moon and little wind. A forecast
  catches fewer of them than hindsight does, so expect at most about one
  exceptional night a month in the dark season, and fewer from November to
  January. ERA5 is a reanalysis, and it probably flatters the clear nights
  a little.
- **Summer has light nights.** At 55.7°N the Sun stays above −18° from
  about 5 May to 7 August, and above −12° from about 3 June to 8 July.
  Klart computes this for each night with almanac-clj, so it hard-codes no
  dates.
- **Cloud forecasts fade fast.** A pilot scored the forecasts for 2025's
  nights at Kastrup against the airport's ceilometer, using Open-Meteo's
  archive of past runs. ECMWF's Peirce score (1 is perfect, 0 is no skill)
  fell from 0.48 for the coming night to 0.26 by day 5. When ECMWF, ICON
  and GFS all forecast a clear night a day ahead, about one in ten of those
  nights turned out cloudy (between 2% and 20%, from one year at one
  station), but they caught fewer than half of the clear nights. The pilot
  used Open-Meteo's seamless blends for ICON and GFS, and DINI scored below
  ECMWF, so Klart's own rules still need testing on its archive. Low
  stratus and fog in winter are the known weak spot.
- **Subscribed calendars are slow.** Google can take a day to refresh a
  feed, and most clients drop the alarms in it, so a "go out tonight" alert
  has to be a push notification. The table under "The feed" has the
  details.


Decisions to make first
-----------------------

Each has a default, which the rest of the plan assumes. Change any you
disagree with.

| Decision | Default | Why |
|---|---|---|
| Name and domain | Klart, at klart.simongray.dk | It sits beside el.simongray.dk and stays clear of Clear Outside, which is First Light Optics' name |
| Places | Your own sites at first, e.g. home and Møn, then cells of 0.1° in Denmark, up to a cap | Fixed sites keep the first versions small, and cells let subscribers share one fetch |
| Kinds of observing | Deep sky (astronomical darkness, the Moon down or less than a quarter lit) and planets (the planets, the Moon and double stars: nautical darkness, any Moon) | These are the two ways that darkness and the Moon matter |
| What the feed holds | Good and exceptional nights, with `?min=exceptional` for only the best | A feed with one event a month looks broken |
| How far ahead | Five days, tentative beyond 36 hours | The skill of cloud forecasts halves by day 4 or 5 |
| Notifications | Exceptional nights only, through ntfy | It reaches a phone without new code |
| Languages | Danish and English, as separate feeds | Calendar servers send no Accept-Language |
| Cost | Free, with no ads or subscriptions | Open-Meteo's free tier is for non-commercial use, and so are EUMETSAT's images and the light-pollution atlas |


How it fits together
--------------------

```
weather sources ─▶ poller ─▶ archive ─▶ scoring ─▶ nights ─┬─▶ feeds  ─▶ calendars
                                │          ▲               └─▶ alerts ─▶ phones
                                │    almanac, thresholds
                                ▼          ▲
DMI observations, your log ─▶ verification ┘
```

- **The poller** fetches each source when it has a new run, by that
  source's rules. Klart never fetches upstream while serving a request, as
  el does.
- **The archive** keeps each response in a write-once gzip file: the raw
  responses for your own sites, which the verification needs, and for
  other cells only the hourly records derived from them, so it stays small.
  It's the source of truth, as the post files are for indieblog, and a
  replay of it shows what new thresholds would change. It has to start long
  before the feed, because Open-Meteo keeps no past runs of cloud by height
  or of its ensembles, and tuning needs them.
- **The almanac** gives the Sun and the Moon for each place and night.
- **Scoring** turns the latest runs into a verdict for each place, kind of
  observing and night.
- **The nights** are the published state of each night (tentative,
  confirmed or cancelled), with its stamps and the time its push went out.
  Only the state machine changes them, and a replay never does.
- **The feeds** are each feed's bytes and ETag, rendered after every
  scoring run.
- **Alerts** are push notifications for the nights that become exceptional.
- **Verification** compares the archive with what the sky did, to tune the
  thresholds.


Weather data
------------

| Source | What Klart takes from it | Its rules |
|---|---|---|
| [Open-Meteo](https://open-meteo.com/en/docs) | DMI's HARMONIE DINI (2 km, 60 h ahead), DWD's ICON-D2 (2 km, 48 h) and ECMWF's IFS (9 km, 15 days) for cloud at each height, visibility, precipitation, dew point, humidity, wind and gusts. IFS at 0.25° for the jet stream. The ECMWF ensemble (51 members, with cloud at each height) and ICON-D2-EPS (20 members, total cloud only). CAMS for aerosols | Name the native models, never `best_match` or a seamless model. Ask for `timezone=GMT` and `wind_speed_unit=ms`. Fetch a model only when its `meta.json` shows a new run |
| [MET Norway](https://docs.api.met.no/doc/locationforecast/datamodel), Locationforecast `complete` | MEPS (2.5 km, a run every hour) from the MetCoOp consortium: cloud at each height, fog and gusts | Send a User-Agent with a contact and If-Modified-Since, round coordinates to 4 decimals, wait for Expires, and avoid requesting on the hour. Use only the hourly steps of the first 60 hours, since the rest comes from the ECMWF ensemble |
| [ECCC GeoMet](https://eccc-msc.github.io/open-data/) | The seeing and sky transparency indices of Canada's RDPS model, which has covered Denmark since August 2025 | Labels only |
| [NOAA SWPC](https://services.swpc.noaa.gov/products/) | The Kp forecast, for aurora | Its JSON changed shape in March 2026, so parse it defensively |
| [DMI Open Data](https://www.dmi.dk/friedata/) | Cloud cover observed every 10 minutes at synop stations such as Kastrup, for verification. Its EDR API is a fallback for DINI, with no key since December 2025 | Back off after a 429 or a 504 |

What the research turned up about them:

- The seamless models fill their gaps with ECMWF. At Copenhagen
  `metno_seamless` returned the same low cloud as `ecmwf_ifs`, hour for
  hour, so comparing two seamless models can count one model twice.
- DINI and MEPS run the same HARMONIE-AROME code in two consortia, so they
  agree more often than independent models would.
- The providers split the cloud at different heights: Open-Meteo at 3 and
  8 km, MET Norway at 2 and 5 km.
- ECMWF's 9 km model has visibility but no pressure levels, and its 0.25°
  model has the reverse, so Klart needs both. ICON's visibility ends in
  November 2026.
- DINI has no precipitation probability, so its hours are vetoed on the
  amount instead.
- Open-Meteo sends no cache headers with a forecast. MET Norway's Varnish
  cache sends an Age header that makes wary-fetch count a fresh response as
  stale, as RFC 9111 says it should. Read Expires directly instead, as
  MET's terms ask.

**The call budget.** Open-Meteo's free tier allows 600 calls a minute,
5,000 an hour and 10,000 a day. A request costs one call per ten
variables, counted again for each model and ensemble member. Fetching each
model only on a new run costs about 100 calls a day per cell, three
quarters of them for the ensembles. That's enough for about 100 active
cells at the full tier, and more when neighbouring cells share their
ensembles on ECMWF's 0.25° grid, which they should from the start. Denmark
has about 600 cells of 0.1°, so:

- Commit a list of the valid cells, and answer any other cell with a 404.
  The rule against 404s under "The feed" covers only feeds that once
  existed.
- Cap the active cells from the budget, and queue the rest with an event
  saying their forecast is on the way.
- Fetch a new cell's latest run of each model at once. Spread the fetches
  of a new run over several minutes, to stay under the limits per minute
  and per hour.
- Set `forecast_days` on every request (6 for IFS), so none is charged for
  more than 14 days.
- Count the calls against all three limits, and warn at 80%.

A paid plan starts at €29 a month, and running Open-Meteo yourself takes at
least 100 GB of NVMe and 8 to 16 GB of memory.


Astronomy
---------

For a place and a night, Klart needs:

- sunset and sunrise
- the times the Sun crosses −6°, −12° and −18°, or that it never does
- the Sun's lowest point
- moonrise and moonset
- the Moon's altitude and illuminated fraction each hour

Accuracy to the minute is plenty. At 55.7°N an arcminute of error moves a
rise time by 7 to 10 seconds, which is less than refraction varies.

No Clojure library computes the Moon's position or searches robustly for
rise and set times. Write almanac-clj, a cljc library ported from Don
Cross's [Astronomy Engine](https://github.com/cosinekitty/astronomy), which
is MIT licensed and accurate to an arcminute:

- The part Klart needs is about 2,500 lines of its TypeScript by my count.
  Its VSOP87 tables take about 640 lines for all the planets, of which the
  Sun needs only Earth's 75. Generate the tables from the upstream source
  rather than retyping them, and give the tests tolerances, since the JVM
  and JavaScript can differ in the last digit.
- Its tests come along: 5,909 rise and set times from USNO's tables,
  twilight cases and JPL Horizons tables. Since USNO's API reset the
  connection, commit Horizons vectors for the start and end of Copenhagen's
  light nights (around 5 May, 3 June, 8 July and 7 August).
- It returns nil for an event that doesn't happen, which covers the light
  nights.
- Like ical-clj, its API takes and returns insts and plain maps, with no
  time library. A `night` function gives the evening's events, the darkest
  moment and the dark hours without the Moon for a place and a local date.

Release 0.1 covers the Sun and the Moon, and the planets can follow in 0.2,
with their tables generated alongside Earth's. If the port slips, the
Kotlin build of Astronomy Engine on JitPack can stand in on the JVM.

Unlike the weather clients, the library doesn't have to grow inside the app
first. It's pure maths checked against outside sources, and its tests exist
before the app does.


Scoring
-------

Only cloud, darkness and a few vetoes decide whether a night counts.
Seeing, transparency and dew rank and label a clear night. Seeing
forecasted from model data correlates weakly with what a telescope sees:
r ≈ 0.3 for total seeing even at Paranal with ECMWF's 137 levels, and
Open-Meteo has 13 of them.

For each place and kind of observing, a scoring run takes these steps:

1. **Dark hours.** The almanac gives the hours with the Sun below the
   limit: −18° for deep sky, −12° for planets. Deep sky also needs the Moon
   below the horizon or less than about a quarter lit.
2. **Vetoes.** An hour fails with rain (a precipitation probability above
   40%, or more than 0.1 mm in DINI, which has no probability), visibility
   below 5 km, or fog (humidity of 95% or more with wind under 2 m/s). The
   vetoes come from the deterministic models, and a vetoed hour counts as
   cloudy for every model and member.
3. **Clear in each model.** A night is clear in a model or ensemble member
   when it has at least three dark hours in a row with total cloud at or
   below 20% and low cloud at or below 10%. ICON-D2-EPS has total cloud
   only, so its members are judged on that alone.
4. **The chance of a clear night.** P_clear blends the share of ensemble
   members with a clear night and the clear nights of the deterministic
   models, in three families: HARMONIE (DINI and MEPS, which share their
   code), ICON and ECMWF. The models that count depend on how far ahead the
   night is: within 48 hours all of them, up to 60 hours all but ICON, and
   beyond that only IFS and the ECMWF ensemble. Start with equal weights,
   and calibrate them once a season of the archive exists. GFS could add an
   independent vote for a few calls a day.
5. **Quality.** The clear hours get a score from their length,
   transparency (CAMS's aerosol optical depth and the humidity aloft), wind
   and gusts, and the Moon. Seeing (ECCC's index and the jet stream) is an
   experimental label. So is dew: a spread of 2 °C or less between
   temperature and dew point on a clear, still night means "dew heater
   needed". About two thirds of the clear nights in ERA5 at Copenhagen reach
   that spread at some point, so it can't be a veto.
6. **Tier.** A night is good when P_clear reaches the tentative threshold
   (0.5). It's exceptional when P_clear reaches 0.8 and it has four hours
   or more with total and high cloud at or below 10%, wind under 5 m/s and
   clean air (an aerosol optical depth under 0.15). Once exceptional, it
   stays so while P_clear stays at 0.7 or more, so it doesn't flip between
   the tiers.

Keep the thresholds in one versioned EDN file, so that a replay of the
archive with a new version shows what would have changed:

```clojure
{:version     1
 :deep-sky    {:sun-below -18 :moon-max-fraction 0.25}
 :planets     {:sun-below -12}
 :clear-hour  {:total-cloud 20 :low-cloud 10}
 :veto        {:precipitation-probability 40
               :precipitation             0.1
               :visibility                5000
               :fog-humidity              95
               :fog-wind                  2}
 :clear-hours 3
 :p-clear     {:tentative 0.5 :confirm 0.7 :downgrade 0.45 :cancel 0.3}
 :exceptional {:p-clear               0.8
               :keep                  0.7
               :hours                 4
               :total-cloud           10
               :high-cloud            10
               :wind                  5
               :aerosol-optical-depth 0.15}
 :state       {:horizon-days         5
               :confirm-within-hours 36
               :runs-apart-hours     3
               :last-call-hours      6
               :round-minutes        30
               :republish-move-hours 1
               :keep-cancelled-hours 48
               :keep-past-days       90}}
```

Use fixed gates, and use ERA5 only for how many nights to expect. From
November to January even the best tenth of nights averages about 35%
cloud in ERA5, and its cloud differs from both the forecasts and the
stations. Later, Open-Meteo's Historical Forecast API can rank the nights
against what the same models usually forecast for the time of year.

### Keeping the verdict steady

A night that comes, goes and comes back teaches people to ignore it. In
Open-Meteo's blend last winter, a clear night forecast two days ahead was
still clear in the latest run for only 22 of 42 nights. So P_clear drives
a state machine.

Scoring runs every hour on the latest run of each source, and whenever the
clock passes one of the times below, so a source that stops can't leave a
night hanging. "Two runs" means two scoring runs at least three hours
apart, so that DINI and ICON-D2 have each had a new run in between.

| From | When | To |
|---|---|---|
| nothing | P_clear reaches 0.5, up to five days ahead | tentative |
| tentative | Within 36 hours of dusk, P_clear is 0.7 or more in two runs, and two of the three families agree | confirmed |
| confirmed | P_clear is below 0.45 in two runs | tentative |
| tentative or confirmed | P_clear is below 0.3 | cancelled |
| tentative | It's six hours before the start, and the latest run is below 0.7 | cancelled |
| cancelled | P_clear reaches 0.5 again before the start | tentative |

- Within the last six hours a single run is enough, so a night that clears
  late, e.g. behind a front, can still be confirmed.
- Once the night starts, it's frozen.
- The window is rounded to 30 minutes. An event is republished when its
  state or tier changes, or when its window moves by an hour.
- A night that comes back after a cancellation gets a higher SEQUENCE, but
  no second push.

Write the state machine as a pure function of the night, the scoring run
and the time, and test it with properties. The Flip-Flop Index measures how
much the forecast for one night jumps from run to run. Replay the archive
and tune the thresholds until it's low, before the feed goes public.


The feed
--------

Each place, kind of observing and language gets a feed, e.g.
`https://klart.simongray.dk/calendar/da/55.7N12.6E/deep-sky.ics`.

**One event a night.** Each good night becomes a timed event over its best
clear hours:

- SUMMARY holds the verdict and the local times, e.g. "Klar nat
  21:30–04:00" or "Fremragende nat 21:30–04:00". A tentative night starts
  with "Mulig", and a cancelled one with "Aflyst". Don't let an emoji carry
  the meaning, since screen readers read them out.
- DESCRIPTION gives the hours of the run that last changed the event
  (cloud, wind, dew, the Moon) and names that run. It links to the night's
  page for the latest numbers and ends with the credits. URL links to the
  page too.
- STATUS is TENTATIVE, CONFIRMED or CANCELLED. A cancelled night stays for
  48 hours.
- TRANSP:TRANSPARENT keeps a good night from making you busy.
- DTSTART and DTEND are in UTC, so the feed needs no VTIMEZONE. The text
  shows Danish time, converted with ical-clj's tz and tested on the nights
  when the clocks change.
- UID is a version 5 UUID of the place, the kind of observing and the date
  of the night's evening. RFC 7986 forbids a UID that names a host or a
  domain, so `…@klart.simongray.dk` won't do.

**Stamps.** The feed has no METHOD, so an event's DTSTAMP is the time it
last changed (RFC 5545, section 3.8.7.2). The nights store keeps each
event's DTSTAMP and LAST-MODIFIED, and both change only when the event's
SUMMARY, DESCRIPTION, DTSTART, DTEND or STATUS do. SEQUENCE goes up only for
the changes that `itip/significant-change?` names. Stamps only ever go up.
ICSx5 ignores an update whose LAST-MODIFIED isn't newer, and a DTSTAMP of
"now" changes the bytes on every request, which defeats the ETag. That's
why `itip/publish`, which stamps every component with the current time,
doesn't suit a feed.

**Never empty.** RFC 5545 requires a component, and ical-clj reports a
calendar that has none. Every feed has an event on the day its cell became
active, which says what the calendar is and stays in the feed. Keep the
good nights of the past 90 days as a log, since ICSx5 and Thunderbird
delete events that leave the feed. Each kind of observing gets an event
over its own light nights, e.g. "Lyse nætter: ingen astronomisk mørke før
natten til 8. august" for deep sky, with the date from the almanac, so a
quiet feed doesn't look broken.

**The calendar's own properties.** Send both conventions: NAME and
X-WR-CALNAME, DESCRIPTION and X-WR-CALDESC, COLOR (a CSS name such as
midnightblue) and X-APPLE-CALENDAR-COLOR. Add SOURCE, URL, and a
REFRESH-INTERVAL and X-PUBLISHED-TTL of an hour. Few clients follow the
last two, but they cost nothing.

**Alarms.** Leave VALARM out by default. Offer it with `?alarm=60` for the
clients that keep it, and say for each client how to give the calendar a
default notification instead.

**HTTP.**

- Render each feed's bytes after a scoring run, and keep them with the time
  they last changed. Serve them from memory with a strong ETag (a SHA-256
  of the bytes), Last-Modified, 304s, HEAD and `text/calendar;
  charset=utf-8`.
- Leave the compression to Caddy, so each feed has one representation and
  one ETag.
- On start, render every feed from the nights store before opening the
  port.
- Validate the serialized text in the tests, not the data, since ical-clj's
  validation skips the syntax of values built by hand.
- Keep a feed under 1 MB, since Proton rejects larger ones.
- Don't filter by User-Agent, and don't limit the feeds' rate by IP, since
  Google fetches every subscriber's feed from its own servers.
- Keep port 80 open with Caddy's 308 redirect. ICSx5 turns webcal:// into
  http://, and it saves the new URL only after a 308.
- Never let a feed that once existed 404, since Apple devices keep polling
  a dead feed long after it's gone. To retire a feed, serve a last event
  saying it ended.

**Subscribing.** The landing page links to:

- webcal://, for Apple Calendar and ICSx5
- Google's `calendar.google.com/calendar/u/0/r?cid=` link, marked as
  desktop only, since Google's apps can't subscribe
- Outlook's `addfromweb` links, for Outlook.com and Microsoft 365
- the https:// URL to copy, for Proton, Fastmail and Thunderbird

| Client | Refreshes | Alarms in the feed |
|---|---|---|
| Apple Calendar on macOS | As you choose, down to every 5 minutes | Removed unless you untick "Remove alerts" |
| Apple Calendar on iOS | When iOS gets round to it | Each calendar has an Event Alerts switch |
| Google Calendar | Every 8 to 24 hours or more, by most reports | Reportedly dropped |
| Outlook.com and Microsoft 365 | About every 3 and 6 hours, and sometimes over a day | Not verified |
| Proton Calendar | Every 4 to 16 hours | Only the calendar's default notifications |
| Thunderbird | Every 30 minutes by default | Each calendar has a switch |
| ICSx5 on Android | Every 15 minutes to once a week | Kept, unless you ignore them |

Some of this comes from third parties. Build a probe early: log each feed
request's User-Agent, conditional headers and interval, subscribe from
every client in the table, and check what each one shows of STATUS, URL and
DESCRIPTION.


Notifications
-------------

Only exceptional nights get a push, at most once per place and night, after
two runs agree, timed for the afternoon before dusk.

1. **For you, from the start.** Post to an unguessable topic on ntfy.sh,
   which costs nothing, and keep the topic secret, since anyone who knows it
   can publish to it. A message has a title such as "Fremragende nat
   tirsdag 21:30–04:30", a link to the night's page, and an action that
   adds a one-night .ics with an alarm to your own calendar. The one-night
   .ics needs a UID of its own, apart from the feed's event. Schedule the
   message with X-At and give it an X-Sequence-ID for the place and night.
   Until it's delivered, a new message with the same ID replaces it. After
   delivery, the ID updates or deletes it on Android and the web, but not
   in the iOS app, so a downgrade on an iPhone takes a new message, e.g.
   "Aflyst: skyer".
2. **For others, at the public beta.** Run ntfy yourself beside Klart, with
   read-only access for everyone and a token for Klart to write with. Give
   each cell and kind of observing a topic. Instant delivery to iOS needs
   `upstream-base-url: https://ntfy.sh` in your server's config, so ntfy.sh
   sees a hash of each topic and the timing, but not the message, and
   Firebase and Apple deliver it.
3. **Web Push, later.** It reaches an iPhone through a web app on the Home
   Screen (iOS 16.4 and later), with no third-party app. Safari's
   declarative push (18.4) needs no service worker. It needs webpush-clj,
   under "Libraries". It also makes you a data controller under GDPR, since
   a subscription with a rough location is personal data.

The ntfy app for iOS is the weak point. It had no release for three years
until April 2026, and complaints about delivery are common. If it lets you
down, Web Push moves up the list.


Pages
-----

The server renders the site in Danish or English, picked with conneg-clj.

- **The landing page** says what Klart does, how often to expect a night
  (at most about one exceptional night a month in the dark season, and none
  in the light nights) and how to subscribe in each client. You pick a
  place with the browser's location or from a list of towns and dark-sky
  places (Møn and Nyord, Bulbjerg, Mandø, Anholt). The page's script snaps
  it to a cell, so exact coordinates never reach the server. DAWA, the free
  Danish address API, reportedly closed in 2026, so there's no Danish
  geocoder to use without a key, and a foreign one such as Nominatim would
  send the search to a third party.
- **A place's page** is the Clear Outside part: the next five nights hour by
  hour, with the darkness bands, cloud at each height, wind, dew, the Moon,
  how far the models agree and each night's verdict. A text table carries
  the same for screen readers.
- **Credits and privacy** pages give the full statements that the licences
  ask for, and say who sees what.


Verification
------------

Without a check against the sky, the thresholds are guesses. Every
morning, score the night against what happened:

- DMI's observed cloud cover at Kastrup and three to five other synop
  stations, which needs no key. Their ceilometers can't see cirrus, so they
  flatter a forecast of a clear night.
- Optionally the Meteosat cloud mask, one pixel at a time through
  EUMETView's WMS, which does see cirrus.
- Your own log: whether you went out, how clear it was, the seeing from 1
  to 5, and dew. An EDN file you edit is enough at first.

Each week, compute the hit rate, the false alarm ratio, the Peirce score,
the Brier score, the reliability and the Flip-Flop Index, with bootstrap
intervals. Retune once a season by replaying the archive, and hold the
latest season out. With a few dozen good nights a year at one station,
don't trust a split by season before you have two years of data. These
scores can live in the app, with no library of their own.


Libraries
---------

| Library | What it adds | When |
|---|---|---|
| almanac-clj, new | The Sun and the Moon: positions, rise and set, twilight, the night, phases, and the Sun's longitude for meteor showers | First, beside the poller |
| A conditional-requests library, new | ETags and the preconditions of RFC 9110, answering with a 304 or a 412 | Before the private feed, on its Git SHA until it's released |
| ical-clj 0.3 | A public function for the date-time text of an inst | When convenient |
| webpush-clj, new | Web Push: the records of RFC 8188, the encryption of RFC 8291, VAPID from RFC 8292, the JSON of declarative push, and what each response means | Before Web Push |

- The feed needs nothing new from ical-clj to start. Its only formatter of
  an inst is internal, but `tz/local` in UTC gives the same text without
  the "Z". The nights store keeps the stamps, so Klart doesn't need a
  function in ical-clj that restamps a revised calendar.
- You've written conditional requests twice already, as
  `conditions/evaluate` in webdav-clj and `not-modified?` in podcast-clj.
  Klart makes a third user, so spin them out before the private feed, the
  way wary-fetch came out of podcast-clj: copy them at a commit, depend on
  the SHA, then on the release.
- The webpush-clj library builds on webcrypto-clj, which has every
  primitive that Web Push needs: P-256 ECDH and ECDSA, HKDF and AES-GCM.
  RFC 8291's appendix gives every step's value as a test vector (mind
  erratum 5230, and erratum 5516 for RFC 8188). On the JVM,
  zerodep-web-push-java is maintained, while webpush-java's last release
  still uses the old aesgcm encoding when it sends synchronously. A cljc
  library would run in Node and the browser too.
- The clients for Open-Meteo, MET Norway and later DMI's EDR API grow
  inside Klart. They come out when a second user appears, and DMI's
  CoverageJSON would be the part with a standard to follow. MET's rule
  about Expires belongs in its client, not in wary-fetch, which follows
  RFC 9111.
- An SGP4 library for ISS passes can wait, perhaps for good. Clear Outside
  shows them, but they mostly get in a telescope's way. If it comes, port
  python-sgp4 or satellite.js (MIT), not CelesTrak's code, which is AGPL.


The app
-------

Klart is a new uberjar on indieblog's stack: Pedestal 0.8, Telemere, maps
of conf for dev and prod, and the atomic EDN store for the cells and the
nights. The pure parts go in cljc, so they can come out as libraries later.

| Namespace under `dk.simongray.klart` | What it does |
|---|---|
| `service` | Reads the conf and secrets, binds the connector to 127.0.0.1 (indieblog binds 0.0.0.0), and starts and stops it |
| `routes`, `pages` | Serve the feeds, the pages, `/subscribe` and `/health` |
| `http` | Wraps wary-fetch with Klart's User-Agent and limits |
| `sources.open-meteo`, `sources.met`, `sources.eccc`, `sources.dmi` | Follow one source's rules each |
| `poller` | Runs one scheduled executor, as podcast-clj's server does, catches Throwable and fetches whatever is due |
| `archive`, `cells`, `nights` | Store the responses, the active cells and the published nights |
| `score`, `calendar` (cljc) | Make the verdicts, and the events from them |
| `alerts`, `verify` | Send the pushes, and score the nights against the sky |

The dependencies are Clojure 1.12, Pedestal 0.8, Telemere, ical-clj,
almanac-clj, wary-fetch, conneg-clj and Replicant, built with tools.build.
Leave out what el used: tick, clj-http, clj-icalendar, tongue and rum.

**After a restart or an outage.** The poller compares each source's
`meta.json` or Expires with its last archived run, fetches the latest run
at once, and logs the gap for the verification, since Open-Meteo keeps no
past runs. The nights store records when each push went out, so a restart
never sends one twice. Stored files carry a version, so an old jar refuses
newer data rather than corrupting it.

**Tests:**

- the almanac's vectors, on the JVM and in Node
- a feed that round-trips, has no validation errors as text, and gives the
  same bytes and ETag for the same nights
- the state machine's paths, including the flip-flops and cancellations,
  as properties and as replays of recorded runs
- each source's responses, recorded from the archive
- the nights when the clocks change, the edges of the light nights and an
  empty feed
- the Danish texts


Running it on your server
-------------------------

Klart goes on the same server as el and the blog, as a third JVM behind
Caddy. Pedestal 0.8 runs on Jetty 12, which needs Java 17 or later. The
blog needs that already, but check `java -version` once.

**Deploying.** Build the jar with `clojure -T:build uber`, copy it to
`/opt/klart/klart-<sha>.jar`, point the symlink `/opt/klart/klart.jar` at
it, and restart. Never overwrite the running jar. To roll back, point the
symlink at the previous one.

**The unit.** The units of el and the blog run the JVM as root, never
restart it, and report the service as active after it dies, because of
RemainAfterExit. Klart's is stricter. Create its user once with `useradd
--system --no-create-home --shell /usr/sbin/nologin klart`, and keep the
jars owned by root.

```ini
# /etc/systemd/system/klart.service

[Unit]
Description=klart.simongray.dk
Wants=network-online.target
After=network-online.target

[Service]
User=klart
StateDirectory=klart
WorkingDirectory=/var/lib/klart
LoadCredential=ntfy:/etc/klart/ntfy
ExecStart=/usr/bin/java -Xmx384m -XX:+ExitOnOutOfMemoryError -jar /opt/klart/klart.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
```

The archive and the nights go in `/var/lib/klart`, which StateDirectory
creates and keeps writable, and the conf takes the path from
$STATE_DIRECTORY. The ntfy topic, and later the token, goes in
`/etc/klart/ntfy`, readable by root only. Klart reads it from
$CREDENTIALS_DIRECTORY and trims the newline at the end.

**Backups.** The archive can't be fetched again. Back up `/var/lib/klart`
every night with a systemd timer, e.g. with restic or rsync to another
machine, and put a restore drill in the docs.

**Caddy.** The rate-limit plugin that your Caddy is built with
(RussellLuo/caddy-ext) has had no commit since April 2023. Rebuild Caddy
with xcaddy and mholt/caddy-ratelimit, which is maintained, before
Klart's first deploy:

- Both plugins register the same `rate_limit` directive with different
  syntax, so rewrite the lines for el and the blog as zones at the same
  time.
- Delete the global `order` option, since the new plugin orders
  `rate_limit` before `basic_auth` itself.
- Check the Caddyfile with `caddy validate` and the new binary before you
  swap it in, and keep the old binary to roll back.
- Give each site a file of its own under `/etc/caddy/sites/`, imported by
  the main Caddyfile, so that el's install.sh can't overwrite Klart's
  block.

Limit the pages by IP but not the feeds. Check this sketch against the
plugin's README:

```
# /etc/caddy/sites/klart.caddy

klart.{$DOMAIN:localhost} {
    encode gzip
    rate_limit {
        zone pages {
            match {
                not path /calendar/*
            }
            key    {remote_host}
            events 100
            window 1m
        }
    }
    reverse_proxy 127.0.0.1:9877
}
```

Point a DNS record for klart.simongray.dk at the server, and Caddy gets the
certificate. Keep Caddy's access log off, as it is now. At the public beta,
ntfy gets a unit and a site block of its own.

**Watching it.** Today el answers HTTP 500, and the Energi Data Service
dataset it reads ended on 30 September 2025. Klart has to say when it
breaks:

- The `/health` route reports the age of each source's last good run and
  the calls used today.
- Klart pushes to your private ntfy topic when a source is six hours stale,
  a variable starts coming back null (after a model upgrade, or when ICON's
  visibility ends), a response stops parsing, or the calls pass 80%.
- A dead JVM sends no alerts, so check from outside too: a cron job on
  another machine that fetches `/health`, or a ping to healthchecks.io
  after each good run, which raises the alarm when the pings stop.
- When a source fails, Klart keeps serving the last good feed and marks its
  nights as stale rather than guessing.

Write `doc/how-to-deploy.md` and `doc/how-to-operate.md` as for indieblog,
the latter with recipes for replaying the archive, retuning, adding a site,
changing the ntfy token and restoring a backup.


Privacy, licences and credits
-----------------------------

**Privacy.** The first versions store nothing about subscribers. Feeds and
topics are keyed by cells of about 11 by 6 km, no UID contains
coordinates, and Caddy keeps no access log. A cell counts as active while
its feed has been fetched in the past two weeks, which says nothing about
who fetched it. The privacy page says who sees what:

- Caddy's rate limiter and ntfy see IP addresses.
- Firebase and Apple deliver the pushes to iPhones.
- A hash of each topic reaches ntfy.sh. Since the landing page says which
  topic belongs to which cell, anyone can map the hashes back to cells.

Web Push or saved places would change this, and bring a notice, deletion
on unsubscribe and limits on how long anything is kept.

**Licences.** The sources are under CC BY 4.0 or similar terms, so the
feed's description, every event's description and the pages carry a short
credit that says the data was changed, with a link to a credits page with
the full statements:

- "Weather data by Open-Meteo.com", next to wherever its data shows
- ECMWF's "This service is based on data and products of the European
  Centre for Medium-Range Weather Forecasts (ECMWF)", with its disclaimer
- "Contains modified Copernicus Atmosphere Monitoring Service information
  2026", for the aerosols
- DWD, for ICON, in the wording its terms ask for
- "Data from MET Norway", DMI, "Data Source: Environment and Climate Change
  Canada" and NOAA SWPC
- Astronomy Engine's MIT notice, in almanac-clj's ported files

The atlas of light pollution behind Clear Outside's Bortle values (Falchi
and others, 2016) is reportedly licensed CC BY-NC. That's one more reason
to stay non-commercial if Klart shows sky quality later.


Milestones
----------

The dark season lasts until early May. Starting now gives a winter of data
to tune on before the light nights, and a launch for the next season.

| When | Milestone | Done when |
|---|---|---|
| Now | Decisions and the name | The decisions above are settled, and the DNS record points at the server |
| November | The poller on the server, with the archive, `/health` and the rebuilt Caddy, for your own sites | Every source has a week of runs archived without gaps, and stopping one sends you an alert |
| November and December | almanac-clj 0.1 | The ported tests pass on the JVM and in Node, and the Copenhagen vectors match Horizons to the minute |
| December | Scoring in the REPL | Replays of the archive give verdicts you agree with, beside Clear Outside and DMI's observations |
| December and January | A private feed, on the conditional-requests library by Git SHA | The feed shows in every client in the table, the same nights give the same bytes, and the probe has logged each client's refresh |
| January | Alerts for you on ntfy.sh | An exceptional night gives one push before dusk, and a downgrade replaces it or follows it |
| January and February | Verification | The weekly scores run, and the first retune on the archive is done |
| March | The public beta | Cells across Denmark up to the cap, the pages in both languages and your own ntfy, with the calls under half the free tier |
| April to July | Libraries, through the light nights | The conditional-requests library and almanac-clj 0.2 are released, and the feeds show the light nights |
| August and September | The second season | The thresholds are retuned on a full season, Web Push works from the Home Screen, and aurora alerts go out |

The aurora alerts need SWPC's forecast of a Kp of 5 or more and a clear sky
to the north. They're push only, since aurora can be forecast reliably only
an hour or three ahead. After that, in no order:

- the peaks of meteor showers, from the IMO's calendar (its solar
  longitudes are for J2000, not the date)
- sky quality for each cell
- el, moved onto the same stack


Open questions
--------------

1. Which observing do you do: visual deep sky, imaging, planets and the
   Moon, or wide-field? That decides the first kinds of feed.
2. Do you decide in the afternoon, or two or three days ahead to drive to a
   dark site? That sets when a night is confirmed and when the push comes.
3. How much wind does your mount take, and do you have dew heaters?
4. Will others accept installing the ntfy app, or should Web Push come
   before the public beta?
5. Which machine is the server, and how much memory and disk can a third
   JVM and its archive have?
6. Will you keep an observing log for the verification?
7. What should happen to el? Energi Data Service replaced its Elspotprices
   dataset with DayAheadPrices, in periods of 15 minutes. Like the old
   dataset it carries prices in DKK, so el could drop the exchange-rate
   fetch unless it keeps other currencies.
