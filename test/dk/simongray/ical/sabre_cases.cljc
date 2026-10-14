(ns dk.simongray.ical.sabre-cases
  "The rules of sabre/vobject's tests/VObject/Recur/RRuleIteratorTest.php
  at commit 5e2f338811916c03927398bb24e527989de63194, with the instances
  they expect, in sabre's form (e.g. 2011-10-07 12:00:00) in the zone :tz.
  Made by dev/sabre_cases.py.

  sabre/vobject is Copyright (C) 2011-2016 fruux GmbH, under the 3-clause
  BSD licence. See https://github.com/sabre-io/vobject/blob/master/LICENSE.")

(def cases
  "Each case as a map of its :name in sabre/vobject, :rrule, :start, :tz
  and :expected."
  [{:name "testHourly"
    :rrule "FREQ=HOURLY;INTERVAL=3;COUNT=12"
    :start "2011-10-07 12:00:00"
    :tz "UTC"
    :expected ["2011-10-07 12:00:00" "2011-10-07 15:00:00" "2011-10-07 18:00:00" "2011-10-07 21:00:00" "2011-10-08 00:00:00" "2011-10-08 03:00:00" "2011-10-08 06:00:00" "2011-10-08 09:00:00" "2011-10-08 12:00:00" "2011-10-08 15:00:00" "2011-10-08 18:00:00" "2011-10-08 21:00:00"]}
   {:name "testDaily"
    :rrule "FREQ=DAILY;INTERVAL=3;UNTIL=20111025T000000Z"
    :start "2011-10-07"
    :tz "UTC"
    :expected ["2011-10-07 00:00:00" "2011-10-10 00:00:00" "2011-10-13 00:00:00" "2011-10-16 00:00:00" "2011-10-19 00:00:00" "2011-10-22 00:00:00" "2011-10-25 00:00:00"]}
   {:name "testDailyByDayByHour"
    :rrule "FREQ=DAILY;BYDAY=SA,SU;BYHOUR=6,7"
    :start "2011-10-08 06:00:00"
    :tz "UTC"
    :expected ["2011-10-08 06:00:00" "2011-10-08 07:00:00" "2011-10-09 06:00:00" "2011-10-09 07:00:00" "2011-10-15 06:00:00" "2011-10-15 07:00:00" "2011-10-16 06:00:00" "2011-10-16 07:00:00" "2011-10-22 06:00:00" "2011-10-22 07:00:00" "2011-10-23 06:00:00" "2011-10-23 07:00:00"]}
   {:name "testDailyByHour"
    :rrule "FREQ=DAILY;INTERVAL=2;BYHOUR=10,11,12,13,14,15"
    :start "2012-10-11 12:00:00"
    :tz "UTC"
    :expected ["2012-10-11 12:00:00" "2012-10-11 13:00:00" "2012-10-11 14:00:00" "2012-10-11 15:00:00" "2012-10-13 10:00:00" "2012-10-13 11:00:00" "2012-10-13 12:00:00" "2012-10-13 13:00:00" "2012-10-13 14:00:00" "2012-10-13 15:00:00" "2012-10-15 10:00:00" "2012-10-15 11:00:00"]}
   {:name "testDailyByDay"
    :rrule "FREQ=DAILY;INTERVAL=2;BYDAY=TU,WE,FR"
    :start "2011-10-07 12:00:00"
    :tz "UTC"
    :expected ["2011-10-07 12:00:00" "2011-10-11 12:00:00" "2011-10-19 12:00:00" "2011-10-21 12:00:00" "2011-10-25 12:00:00" "2011-11-02 12:00:00" "2011-11-04 12:00:00" "2011-11-08 12:00:00" "2011-11-16 12:00:00" "2011-11-18 12:00:00" "2011-11-22 12:00:00" "2011-11-30 12:00:00"]}
   {:name "testDailyCount"
    :rrule "FREQ=DAILY;COUNT=5"
    :start "2014-08-01 18:03:00"
    :tz "UTC"
    :expected ["2014-08-01 18:03:00" "2014-08-02 18:03:00" "2014-08-03 18:03:00" "2014-08-04 18:03:00" "2014-08-05 18:03:00"]}
   {:name "testWeekly"
    :rrule "FREQ=WEEKLY;INTERVAL=2;COUNT=10"
    :start "2011-10-07 00:00:00"
    :tz "UTC"
    :expected ["2011-10-07 00:00:00" "2011-10-21 00:00:00" "2011-11-04 00:00:00" "2011-11-18 00:00:00" "2011-12-02 00:00:00" "2011-12-16 00:00:00" "2011-12-30 00:00:00" "2012-01-13 00:00:00" "2012-01-27 00:00:00" "2012-02-10 00:00:00"]}
   {:name "testWeeklyByDay"
    :rrule "FREQ=WEEKLY;INTERVAL=1;COUNT=4;BYDAY=MO;WKST=SA"
    :start "2014-08-01 00:00:00"
    :tz "UTC"
    :expected ["2014-08-01 00:00:00" "2014-08-04 00:00:00" "2014-08-11 00:00:00" "2014-08-18 00:00:00"]}
   {:name "testWeeklyByDay2"
    :rrule "FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,WE,FR;WKST=SU"
    :start "2011-10-07 00:00:00"
    :tz "UTC"
    :expected ["2011-10-07 00:00:00" "2011-10-18 00:00:00" "2011-10-19 00:00:00" "2011-10-21 00:00:00" "2011-11-01 00:00:00" "2011-11-02 00:00:00" "2011-11-04 00:00:00" "2011-11-15 00:00:00" "2011-11-16 00:00:00" "2011-11-18 00:00:00" "2011-11-29 00:00:00" "2011-11-30 00:00:00"]}
   {:name "testWeeklyByDayByHour"
    :rrule "FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,WE,FR;WKST=MO;BYHOUR=8,9,10"
    :start "2011-10-07 08:00:00"
    :tz "UTC"
    :expected ["2011-10-07 08:00:00" "2011-10-07 09:00:00" "2011-10-07 10:00:00" "2011-10-18 08:00:00" "2011-10-18 09:00:00" "2011-10-18 10:00:00" "2011-10-19 08:00:00" "2011-10-19 09:00:00" "2011-10-19 10:00:00" "2011-10-21 08:00:00" "2011-10-21 09:00:00" "2011-10-21 10:00:00" "2011-11-01 08:00:00" "2011-11-01 09:00:00" "2011-11-01 10:00:00"]}
   {:name "testWeeklyByDaySpecificHour"
    :rrule "FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,WE,FR;WKST=SU"
    :start "2011-10-07 18:00:00"
    :tz "UTC"
    :expected ["2011-10-07 18:00:00" "2011-10-18 18:00:00" "2011-10-19 18:00:00" "2011-10-21 18:00:00" "2011-11-01 18:00:00" "2011-11-02 18:00:00" "2011-11-04 18:00:00" "2011-11-15 18:00:00" "2011-11-16 18:00:00" "2011-11-18 18:00:00" "2011-11-29 18:00:00" "2011-11-30 18:00:00"]}
   {:name "testWeeklyByDaySpecificHourOnDstTransition"
    :rrule "FREQ=WEEKLY;INTERVAL=2;BYDAY=SA,SU"
    :start "2023-03-11 02:30:00"
    :tz "Europe/Zurich"
    :expected ["2023-03-11 02:30:00" "2023-03-12 02:30:00" "2023-03-25 02:30:00" "2023-03-26 03:30:00" "2023-04-08 02:30:00" "2023-04-09 02:30:00"]}
   {:name "testWeeklyByDayByHourOnDstTransition"
    :rrule "FREQ=WEEKLY;INTERVAL=2;BYDAY=SA,SU;WKST=MO;BYHOUR=2,14"
    :start "2023-03-11 02:00:00"
    :tz "Europe/Zurich"
    :expected ["2023-03-11 02:00:00" "2023-03-11 14:00:00" "2023-03-12 02:00:00" "2023-03-12 14:00:00" "2023-03-25 02:00:00" "2023-03-25 14:00:00" "2023-03-26 03:00:00" "2023-03-26 14:00:00" "2023-04-08 02:00:00" "2023-04-08 14:00:00" "2023-04-09 02:00:00" "2023-04-09 14:00:00"]}
   {:name "testMonthly"
    :rrule "FREQ=MONTHLY;INTERVAL=3;COUNT=5"
    :start "2011-12-05 00:00:00"
    :tz "UTC"
    :expected ["2011-12-05 00:00:00" "2012-03-05 00:00:00" "2012-06-05 00:00:00" "2012-09-05 00:00:00" "2012-12-05 00:00:00"]}
   {:name "testMonthlyEndOfMonth"
    :rrule "FREQ=MONTHLY;INTERVAL=2;COUNT=12"
    :start "2011-12-31 00:00:00"
    :tz "UTC"
    :expected ["2011-12-31 00:00:00" "2012-08-31 00:00:00" "2012-10-31 00:00:00" "2012-12-31 00:00:00" "2013-08-31 00:00:00" "2013-10-31 00:00:00" "2013-12-31 00:00:00" "2014-08-31 00:00:00" "2014-10-31 00:00:00" "2014-12-31 00:00:00" "2015-08-31 00:00:00" "2015-10-31 00:00:00"]}
   {:name "testMonthlyByMonthDay"
    :rrule "FREQ=MONTHLY;INTERVAL=5;COUNT=9;BYMONTHDAY=1,31,-7"
    :start "2011-01-01 00:00:00"
    :tz "UTC"
    :expected ["2011-01-01 00:00:00" "2011-01-25 00:00:00" "2011-01-31 00:00:00" "2011-06-01 00:00:00" "2011-06-24 00:00:00" "2011-11-01 00:00:00" "2011-11-24 00:00:00" "2012-04-01 00:00:00" "2012-04-24 00:00:00"]}
   {:name "testMonthlyByMonthDayDstTransition"
    :rrule "FREQ=MONTHLY;INTERVAL=1;COUNT=8;BYMONTHDAY=1,26"
    :start "2023-01-01 02:15:00"
    :tz "Europe/Zurich"
    :expected ["2023-01-01 02:15:00" "2023-01-26 02:15:00" "2023-02-01 02:15:00" "2023-02-26 02:15:00" "2023-03-01 02:15:00" "2023-03-26 03:15:00" "2023-04-01 02:15:00" "2023-04-26 02:15:00"]}
   {:name "testMonthlyByDay"
    :rrule "FREQ=MONTHLY;INTERVAL=2;COUNT=16;BYDAY=MO,-2TU,+1WE,3TH"
    :start "2011-01-03 00:00:00"
    :tz "UTC"
    :expected ["2011-01-03 00:00:00" "2011-01-05 00:00:00" "2011-01-10 00:00:00" "2011-01-17 00:00:00" "2011-01-18 00:00:00" "2011-01-20 00:00:00" "2011-01-24 00:00:00" "2011-01-31 00:00:00" "2011-03-02 00:00:00" "2011-03-07 00:00:00" "2011-03-14 00:00:00" "2011-03-17 00:00:00" "2011-03-21 00:00:00" "2011-03-22 00:00:00" "2011-03-28 00:00:00" "2011-05-02 00:00:00"]}
   {:name "testMonthlyByDayUntil"
    :rrule "FREQ=MONTHLY;INTERVAL=1;BYDAY=WE;WKST=WE;UNTIL=20210317T000000Z"
    :start "2021-02-10 00:00:00"
    :tz "UTC"
    :expected ["2021-02-10 00:00:00" "2021-02-17 00:00:00" "2021-02-24 00:00:00" "2021-03-03 00:00:00" "2021-03-10 00:00:00" "2021-03-17 00:00:00"]}
   {:name "testMonthlyByDayOnDstTransition"
    :rrule "FREQ=MONTHLY;INTERVAL=2;COUNT=13;BYDAY=SU"
    :start "2023-01-01 02:30:00"
    :tz "Europe/Zurich"
    :expected ["2023-01-01 02:30:00" "2023-01-08 02:30:00" "2023-01-15 02:30:00" "2023-01-22 02:30:00" "2023-01-29 02:30:00" "2023-03-05 02:30:00" "2023-03-12 02:30:00" "2023-03-19 02:30:00" "2023-03-26 03:30:00" "2023-05-07 02:30:00" "2023-05-14 02:30:00" "2023-05-21 02:30:00" "2023-05-28 02:30:00"]}
   {:name "testMonthlyByDayUntilWithImpossibleNextOccurrence"
    :rrule "FREQ=MONTHLY;INTERVAL=1;BYDAY=2WE;BYMONTHDAY=2;WKST=WE;UNTIL=20210317T000000Z"
    :start "2021-02-10 00:00:00"
    :tz "UTC"
    :expected ["2021-02-10 00:00:00"]}
   {:name "testMonthlyByDayByMonthDay"
    :rrule "FREQ=MONTHLY;COUNT=10;BYDAY=MO;BYMONTHDAY=1"
    :start "2011-08-01 00:00:00"
    :tz "UTC"
    :expected ["2011-08-01 00:00:00" "2012-10-01 00:00:00" "2013-04-01 00:00:00" "2013-07-01 00:00:00" "2014-09-01 00:00:00" "2014-12-01 00:00:00" "2015-06-01 00:00:00" "2016-02-01 00:00:00" "2016-08-01 00:00:00" "2017-05-01 00:00:00"]}
   {:name "testMonthlyByDayBySetPos"
    :rrule "FREQ=MONTHLY;COUNT=10;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=1,-1"
    :start "2011-01-03 00:00:00"
    :tz "UTC"
    :expected ["2011-01-03 00:00:00" "2011-01-31 00:00:00" "2011-02-01 00:00:00" "2011-02-28 00:00:00" "2011-03-01 00:00:00" "2011-03-31 00:00:00" "2011-04-01 00:00:00" "2011-04-29 00:00:00" "2011-05-02 00:00:00" "2011-05-31 00:00:00"]}
   {:name "testYearly"
    :rrule "FREQ=YEARLY;COUNT=10;INTERVAL=3"
    :start "2011-01-01 00:00:00"
    :tz "UTC"
    :expected ["2011-01-01 00:00:00" "2014-01-01 00:00:00" "2017-01-01 00:00:00" "2020-01-01 00:00:00" "2023-01-01 00:00:00" "2026-01-01 00:00:00" "2029-01-01 00:00:00" "2032-01-01 00:00:00" "2035-01-01 00:00:00" "2038-01-01 00:00:00"]}
   {:name "testYearlyLeapYear"
    :rrule "FREQ=YEARLY;COUNT=3"
    :start "2012-02-29 00:00:00"
    :tz "UTC"
    :expected ["2012-02-29 00:00:00" "2016-02-29 00:00:00" "2020-02-29 00:00:00"]}
   {:name "testYearlyByMonth"
    :rrule "FREQ=YEARLY;COUNT=8;INTERVAL=4;BYMONTH=4,10"
    :start "2011-04-07 00:00:00"
    :tz "UTC"
    :expected ["2011-04-07 00:00:00" "2011-10-07 00:00:00" "2015-04-07 00:00:00" "2015-10-07 00:00:00" "2019-04-07 00:00:00" "2019-10-07 00:00:00" "2023-04-07 00:00:00" "2023-10-07 00:00:00"]}
   {:name "testYearlyByMonthOnDstTransition"
    :rrule "FREQ=YEARLY;COUNT=8;INTERVAL=2;BYMONTH=3,9"
    :start "2019-03-26 02:30:00"
    :tz "Europe/Zurich"
    :expected ["2019-03-26 02:30:00" "2019-09-26 02:30:00" "2021-03-26 02:30:00" "2021-09-26 02:30:00" "2023-03-26 03:30:00" "2023-09-26 02:30:00" "2025-03-26 02:30:00" "2025-09-26 02:30:00"]}
   {:name "testYearlyByMonthInvalidValue1"
    :rrule "FREQ=YEARLY;COUNT=6;BYMONTHDAY=24;BYMONTH=0"
    :start "2011-04-07 00:00:00"
    :tz "UTC"
    :expected []}
   {:name "testYearlyByMonthInvalidValue2"
    :rrule "FREQ=YEARLY;COUNT=6;BYMONTHDAY=24;BYMONTH=bla"
    :start "2011-04-07 00:00:00"
    :tz "UTC"
    :expected []}
   {:name "testYearlyByMonthManyInvalidValues"
    :rrule "FREQ=YEARLY;COUNT=6;BYMONTHDAY=24;BYMONTH=0,bla"
    :start "2011-04-07 00:00:00"
    :tz "UTC"
    :expected []}
   {:name "testYearlyByMonthEmptyValue"
    :rrule "FREQ=YEARLY;COUNT=6;BYMONTHDAY=24;BYMONTH="
    :start "2011-04-07 00:00:00"
    :tz "UTC"
    :expected []}
   {:name "testYearlyByMonthByDay"
    :rrule "FREQ=YEARLY;COUNT=8;INTERVAL=5;BYMONTH=4,10;BYDAY=1MO,-1SU"
    :start "2011-04-04 00:00:00"
    :tz "UTC"
    :expected ["2011-04-04 00:00:00" "2011-04-24 00:00:00" "2011-10-03 00:00:00" "2011-10-30 00:00:00" "2016-04-04 00:00:00" "2016-04-24 00:00:00" "2016-10-03 00:00:00" "2016-10-30 00:00:00"]}
   {:name "testYearlyByMonthByDayOnDstTransition"
    :rrule "FREQ=YEARLY;COUNT=13;INTERVAL=2;BYMONTH=3;BYDAY=SU"
    :start "2021-03-07 02:30:00"
    :tz "Europe/Zurich"
    :expected ["2021-03-07 02:30:00" "2021-03-14 02:30:00" "2021-03-21 02:30:00" "2021-03-28 03:30:00" "2023-03-05 02:30:00" "2023-03-12 02:30:00" "2023-03-19 02:30:00" "2023-03-26 03:30:00" "2025-03-02 02:30:00" "2025-03-09 02:30:00" "2025-03-16 02:30:00" "2025-03-23 02:30:00" "2025-03-30 03:30:00"]}
   {:name "testYearlyNewYearsDay"
    :rrule "FREQ=YEARLY;COUNT=7;INTERVAL=2;BYYEARDAY=1"
    :start "2011-01-01 03:07:00"
    :tz "UTC"
    :expected ["2011-01-01 03:07:00" "2013-01-01 03:07:00" "2015-01-01 03:07:00" "2017-01-01 03:07:00" "2019-01-01 03:07:00" "2021-01-01 03:07:00" "2023-01-01 03:07:00"]}
   {:name "testYearlyByYearDay"
    :rrule "FREQ=YEARLY;COUNT=7;INTERVAL=2;BYYEARDAY=190"
    :start "2011-07-09 03:07:00"
    :tz "UTC"
    :expected ["2011-07-09 03:07:00" "2013-07-09 03:07:00" "2015-07-09 03:07:00" "2017-07-09 03:07:00" "2019-07-09 03:07:00" "2021-07-09 03:07:00" "2023-07-09 03:07:00"]}
   {:name "testYearlyByYearDayMultiple"
    :rrule "FREQ=YEARLY;COUNT=8;INTERVAL=3;BYYEARDAY=190,301"
    :start "2011-07-10 14:53:11"
    :tz "UTC"
    :expected ["2011-07-10 14:53:11" "2011-10-28 14:53:11" "2014-07-09 14:53:11" "2014-10-28 14:53:11" "2017-07-09 14:53:11" "2017-10-28 14:53:11" "2020-07-08 14:53:11" "2020-10-27 14:53:11"]}
   {:name "testYearlyByYearDayByDay"
    :rrule "FREQ=YEARLY;COUNT=6;BYYEARDAY=97;BYDAY=SA"
    :start "2001-04-07 14:53:11"
    :tz "UTC"
    :expected ["2001-04-07 14:53:11" "2007-04-07 14:53:11" "2018-04-07 14:53:11" "2024-04-06 14:53:11" "2029-04-07 14:53:11" "2035-04-07 14:53:11"]}
   {:name "testYearlyByYearDayNegative"
    :rrule "FREQ=YEARLY;COUNT=8;BYYEARDAY=-97,-5"
    :start "2001-09-26 14:53:11"
    :tz "UTC"
    :expected ["2001-09-26 14:53:11" "2001-12-27 14:53:11" "2002-09-26 14:53:11" "2002-12-27 14:53:11" "2003-09-26 14:53:11" "2003-12-27 14:53:11" "2004-09-26 14:53:11" "2004-12-27 14:53:11"]}
   {:name "testYearlyByYearDayLargeNegative"
    :rrule "FREQ=YEARLY;COUNT=8;BYYEARDAY=-365"
    :start "2001-01-01 14:53:11"
    :tz "UTC"
    :expected ["2001-01-01 14:53:11" "2002-01-01 14:53:11" "2003-01-01 14:53:11" "2004-01-02 14:53:11" "2005-01-01 14:53:11" "2006-01-01 14:53:11" "2007-01-01 14:53:11" "2008-01-02 14:53:11"]}
   {:name "testYearlyByYearDayMaxNegative"
    :rrule "FREQ=YEARLY;COUNT=8;BYYEARDAY=-366"
    :start "2001-01-01 14:53:11"
    :tz "UTC"
    :expected ["2001-01-01 14:53:11" "2001-12-31 14:53:11" "2002-12-31 14:53:11" "2004-01-01 14:53:11" "2004-12-31 14:53:11" "2005-12-31 14:53:11" "2006-12-31 14:53:11" "2008-01-01 14:53:11"]}
   {:name "testYearlyByYearDayInvalid390"
    :rrule "FREQ=YEARLY;COUNT=8;INTERVAL=4;BYYEARDAY=390"
    :start "2011-04-07 00:00:00"
    :tz "UTC"
    :expected []}
   {:name "testYearlyByYearDayInvalid0"
    :rrule "FREQ=YEARLY;COUNT=8;INTERVAL=4;BYYEARDAY=0"
    :start "2011-04-07 00:00:00"
    :tz "UTC"
    :expected []}
   {:name "testYearlyByDayByWeekNo"
    :rrule "FREQ=YEARLY;COUNT=3;BYDAY=MO;BYWEEKNO=13,15,50"
    :start "2021-01-01 00:00:00"
    :tz "UTC"
    :expected ["2021-01-01 00:00:00" "2021-03-29 00:00:00" "2021-04-12 00:00:00"]}
   {:name "testFifthTuesdayProblem"
    :rrule "FREQ=MONTHLY;INTERVAL=1;UNTIL=20071030T035959Z;BYDAY=5TU"
    :start "2007-10-04 14:46:42"
    :tz "UTC"
    :expected ["2007-10-04 14:46:42"]}
   {:name "testFastForwardTooFar"
    :rrule "FREQ=WEEKLY;BYDAY=MO;UNTIL=20090704T205959Z;INTERVAL=1"
    :start "2009-04-20 18:00:00"
    :tz "UTC"
    :expected ["2009-04-20 18:00:00" "2009-04-27 18:00:00" "2009-05-04 18:00:00" "2009-05-11 18:00:00" "2009-05-18 18:00:00" "2009-05-25 18:00:00" "2009-06-01 18:00:00" "2009-06-08 18:00:00" "2009-06-15 18:00:00" "2009-06-22 18:00:00" "2009-06-29 18:00:00"]}
   {:name "testValidByWeekNo"
    :rrule "FREQ=YEARLY;BYWEEKNO=20;BYDAY=TU"
    :start "2011-02-07 00:00:00"
    :tz "UTC"
    :expected ["2011-02-07 00:00:00" "2011-05-17 00:00:00" "2012-05-15 00:00:00" "2013-05-14 00:00:00" "2014-05-13 00:00:00" "2015-05-12 00:00:00" "2016-05-17 00:00:00" "2017-05-16 00:00:00" "2018-05-15 00:00:00" "2019-05-14 00:00:00" "2020-05-12 00:00:00" "2021-05-18 00:00:00"]}
   {:name "testNegativeValidByWeekNo"
    :rrule "FREQ=YEARLY;BYWEEKNO=-20;BYDAY=TU,FR"
    :start "2011-09-02 00:00:00"
    :tz "UTC"
    :expected ["2011-09-02 00:00:00" "2012-08-07 00:00:00" "2012-08-10 00:00:00" "2013-08-06 00:00:00" "2013-08-09 00:00:00" "2014-08-05 00:00:00" "2014-08-08 00:00:00" "2015-08-11 00:00:00" "2015-08-14 00:00:00" "2016-08-09 00:00:00" "2016-08-12 00:00:00" "2017-08-08 00:00:00"]}
   {:name "testTwoValidByWeekNo"
    :rrule "FREQ=YEARLY;BYWEEKNO=20;BYDAY=TU,FR"
    :start "2011-09-07 09:00:00"
    :tz "UTC"
    :expected ["2011-09-07 09:00:00" "2012-05-15 09:00:00" "2012-05-18 09:00:00" "2013-05-14 09:00:00" "2013-05-17 09:00:00" "2014-05-13 09:00:00" "2014-05-16 09:00:00" "2015-05-12 09:00:00" "2015-05-15 09:00:00" "2016-05-17 09:00:00" "2016-05-20 09:00:00" "2017-05-16 09:00:00"]}
   {:name "testValidByWeekNoByDayDefault"
    :rrule "FREQ=YEARLY;BYWEEKNO=20"
    :start "2011-05-16 00:00:00"
    :tz "UTC"
    :expected ["2011-05-16 00:00:00" "2012-05-14 00:00:00" "2013-05-13 00:00:00" "2014-05-12 00:00:00" "2015-05-11 00:00:00" "2016-05-16 00:00:00" "2017-05-15 00:00:00" "2018-05-14 00:00:00" "2019-05-13 00:00:00" "2020-05-11 00:00:00" "2021-05-17 00:00:00" "2022-05-16 00:00:00"]}
   {:name "testMultipleValidByWeekNo"
    :rrule "FREQ=YEARLY;BYWEEKNO=20,50;BYDAY=TU,FR"
    :start "2011-01-16 00:00:00"
    :tz "UTC"
    :expected ["2011-01-16 00:00:00" "2011-05-17 00:00:00" "2011-05-20 00:00:00" "2011-12-13 00:00:00" "2011-12-16 00:00:00" "2012-05-15 00:00:00" "2012-05-18 00:00:00" "2012-12-11 00:00:00" "2012-12-14 00:00:00" "2013-05-14 00:00:00" "2013-05-17 00:00:00" "2013-12-10 00:00:00"]}
   {:name "testInvalidByWeekNo"
    :rrule "FREQ=YEARLY;BYWEEKNO=54"
    :start "2011-05-16 00:00:00"
    :tz "UTC"
    :expected []}
   {:name "testInvalidFreq"
    :rrule "FREQ=SMONTHLY;INTERVAL=3;UNTIL=20111025T000000Z"
    :start "2011-10-07"
    :tz "UTC"
    :expected []}
   {:name "testByDayBadOffset"
    :rrule "FREQ=WEEKLY;INTERVAL=1;COUNT=4;BYDAY=0MO;WKST=SA"
    :start "2014-08-01 00:00:00"
    :tz "UTC"
    :expected []}
   {:name "testUntilBeginHasTimezone"
    :rrule "FREQ=WEEKLY;UNTIL=20131118T183000"
    :start "2013-09-23 18:30:00"
    :tz "America/New_York"
    :expected ["2013-09-23 18:30:00" "2013-09-30 18:30:00" "2013-10-07 18:30:00" "2013-10-14 18:30:00" "2013-10-21 18:30:00" "2013-10-28 18:30:00" "2013-11-04 18:30:00" "2013-11-11 18:30:00" "2013-11-18 18:30:00"]}
   {:name "testUntilBeforeDtStart"
    :rrule "FREQ=DAILY;UNTIL=20140101T000000Z"
    :start "2014-08-02 00:15:00"
    :tz "UTC"
    :expected ["2014-08-02 00:15:00"]}
   {:name "testIgnoredStuff"
    :rrule "FREQ=DAILY;BYSECOND=1;BYMINUTE=1;BYYEARDAY=1;BYWEEKNO=1;COUNT=2"
    :start "2014-08-02 00:15:00"
    :tz "UTC"
    :expected ["2014-08-02 00:15:00" "2014-08-03 00:15:00"]}
   {:name "testMinusFifthThursday"
    :rrule "FREQ=MONTHLY;BYDAY=-4TH,-5TH;COUNT=4"
    :start "2015-01-01 00:15:00"
    :tz "UTC"
    :expected ["2015-01-01 00:15:00" "2015-01-08 00:15:00" "2015-02-05 00:15:00" "2015-03-05 00:15:00"]}
   {:name "testNeverEnding"
    :rrule "FREQ=MONTHLY;BYDAY=2TU;BYSETPOS=2"
    :start "2015-01-01 00:15:00"
    :tz "UTC"
    :expected ["2015-01-01 00:15:00"]}
   {:name "testUnsupportedPart"
    :rrule "FREQ=DAILY;BYWODAN=1"
    :start "2014-08-02 00:15:00"
    :tz "UTC"
    :expected []}])
