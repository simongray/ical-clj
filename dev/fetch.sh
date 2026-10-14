#!/bin/sh
# Download the test data of other iCalendar implementations, and CLDR's
# table of Windows zone names, at the commits that the library follows,
# into dev-resources/.
set -eu

cldr=b5a68e61ac365706856ea8796585783579c9a3ff
libical=7dba02b3ab4a9536eafe3b9bbedb57e28d83775e
icaljs=33ba266282fac7c721d792e04124513df73fe140
icalendar=5035ff8f445856c078f5514d1a604e5600a8a7ce

cd "$(dirname "$0")/.."
mkdir -p dev-resources/cldr dev-resources/rfc dev-resources/libical dev-resources/icaljs \
  dev-resources/icalendar

curl -sSfL -o dev-resources/cldr/windowsZones.xml \
  "https://raw.githubusercontent.com/unicode-org/cldr/$cldr/common/supplemental/windowsZones.xml"

# the restriction tables of iTIP
curl -sSfL -o dev-resources/rfc/rfc5546.txt https://www.rfc-editor.org/rfc/rfc5546.txt

# the recurrence rules with their instances (MPL-2.0)
for f in icalrecur_test.txt icalrecur_test_rscale.txt; do
  curl -sSfL -o "dev-resources/libical/$f" \
    "https://raw.githubusercontent.com/libical/libical/$libical/src/test/$f"
done

# the files of the parser tests, each with its jCal (MPL-2.0)
curl -sSfL "https://codeload.github.com/kewisch/ical.js/tar.gz/$icaljs" |
  tar -xz -C dev-resources/icaljs --strip-components=3 "ical.js-$icaljs/test/parser"

# calendars from Google, Exchange, Thunderbird and other clients (BSD-2)
curl -sSfL "https://codeload.github.com/collective/icalendar/tar.gz/$icalendar" |
  tar -xz -C dev-resources/icalendar --strip-components=5 \
    "icalendar-$icalendar/src/icalendar/tests/calendars"
