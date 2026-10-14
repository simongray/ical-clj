(ns dk.simongray.ical.dateutil-cases
  "The rules of python-dateutil's tests/test_rrule.py at commit
  2642afacc33fb839c404b75b230fff58b79793f2, with the instances they
  expect. Made by dev/dateutil_cases.py.

  python-dateutil is Copyright (c) 2003-2011 Gustavo Niemeyer, 2012-2014
  Tomi Pieviläinen, 2014-2016 Yaron de Leeuw, and 2015- Paul Ganssle
  and the dateutil contributors, under the 3-clause BSD licence, with the
  Apache License 2.0 for contributions since 1 December 2017. See
  https://github.com/dateutil/dateutil/blob/master/LICENSE.")

(def cases
  "Each case as a map of its :name in dateutil, :rrule, :dtstart and
  :expected."
  [{:name "testYearly"
    :rrule "FREQ=YEARLY;COUNT=3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1998-09-02T09:00:00" "1999-09-02T09:00:00"]}
   {:name "testYearlyInterval"
    :rrule "FREQ=YEARLY;COUNT=3;INTERVAL=2"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1999-09-02T09:00:00" "2001-09-02T09:00:00"]}
   {:name "testYearlyIntervalLarge"
    :rrule "FREQ=YEARLY;COUNT=3;INTERVAL=100"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "2097-09-02T09:00:00" "2197-09-02T09:00:00"]}
   {:name "testYearlyByMonth"
    :rrule "FREQ=YEARLY;COUNT=3;BYMONTH=1,3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-02T09:00:00" "1998-03-02T09:00:00" "1999-01-02T09:00:00"]}
   {:name "testYearlyByMonthDay"
    :rrule "FREQ=YEARLY;COUNT=3;BYMONTHDAY=1,3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-03T09:00:00" "1997-10-01T09:00:00" "1997-10-03T09:00:00"]}
   {:name "testYearlyByMonthAndMonthDay"
    :rrule "FREQ=YEARLY;COUNT=3;BYMONTH=1,3;BYMONTHDAY=5,7"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-05T09:00:00" "1998-01-07T09:00:00" "1998-03-05T09:00:00"]}
   {:name "testYearlyByWeekDay"
    :rrule "FREQ=YEARLY;COUNT=3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-04T09:00:00" "1997-09-09T09:00:00"]}
   {:name "testYearlyByNWeekDay"
    :rrule "FREQ=YEARLY;COUNT=3;BYDAY=1TU,-1TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-25T09:00:00" "1998-01-06T09:00:00" "1998-12-31T09:00:00"]}
   {:name "testYearlyByNWeekDayLarge"
    :rrule "FREQ=YEARLY;COUNT=3;BYDAY=3TU,-3TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-11T09:00:00" "1998-01-20T09:00:00" "1998-12-17T09:00:00"]}
   {:name "testYearlyByMonthAndWeekDay"
    :rrule "FREQ=YEARLY;COUNT=3;BYMONTH=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-01-06T09:00:00" "1998-01-08T09:00:00"]}
   {:name "testYearlyByMonthAndNWeekDay"
    :rrule "FREQ=YEARLY;COUNT=3;BYMONTH=1,3;BYDAY=1TU,-1TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-06T09:00:00" "1998-01-29T09:00:00" "1998-03-03T09:00:00"]}
   {:name "testYearlyByMonthAndNWeekDayLarge"
    :rrule "FREQ=YEARLY;COUNT=3;BYMONTH=1,3;BYDAY=3TU,-3TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-15T09:00:00" "1998-01-20T09:00:00" "1998-03-12T09:00:00"]}
   {:name "testYearlyByMonthDayAndWeekDay"
    :rrule "FREQ=YEARLY;COUNT=3;BYMONTHDAY=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-02-03T09:00:00" "1998-03-03T09:00:00"]}
   {:name "testYearlyByMonthAndMonthDayAndWeekDay"
    :rrule "FREQ=YEARLY;COUNT=3;BYMONTH=1,3;BYMONTHDAY=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-03-03T09:00:00" "2001-03-01T09:00:00"]}
   {:name "testYearlyByYearDay"
    :rrule "FREQ=YEARLY;COUNT=4;BYYEARDAY=1,100,200,365"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-31T09:00:00" "1998-01-01T09:00:00" "1998-04-10T09:00:00" "1998-07-19T09:00:00"]}
   {:name "testYearlyByYearDayNeg"
    :rrule "FREQ=YEARLY;COUNT=4;BYYEARDAY=-365,-266,-166,-1"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-31T09:00:00" "1998-01-01T09:00:00" "1998-04-10T09:00:00" "1998-07-19T09:00:00"]}
   {:name "testYearlyByMonthAndYearDay"
    :rrule "FREQ=YEARLY;COUNT=4;BYMONTH=4,7;BYYEARDAY=1,100,200,365"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-04-10T09:00:00" "1998-07-19T09:00:00" "1999-04-10T09:00:00" "1999-07-19T09:00:00"]}
   {:name "testYearlyByMonthAndYearDayNeg"
    :rrule "FREQ=YEARLY;COUNT=4;BYMONTH=4,7;BYYEARDAY=-365,-266,-166,-1"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-04-10T09:00:00" "1998-07-19T09:00:00" "1999-04-10T09:00:00" "1999-07-19T09:00:00"]}
   {:name "testYearlyByWeekNo"
    :rrule "FREQ=YEARLY;COUNT=3;BYWEEKNO=20"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-05-11T09:00:00" "1998-05-12T09:00:00" "1998-05-13T09:00:00"]}
   {:name "testYearlyByWeekNoAndWeekDay"
    :rrule "FREQ=YEARLY;COUNT=3;BYWEEKNO=1;BYDAY=MO"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-29T09:00:00" "1999-01-04T09:00:00" "2000-01-03T09:00:00"]}
   {:name "testYearlyByWeekNoAndWeekDayLarge"
    :rrule "FREQ=YEARLY;COUNT=3;BYWEEKNO=52;BYDAY=SU"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-28T09:00:00" "1998-12-27T09:00:00" "2000-01-02T09:00:00"]}
   {:name "testYearlyByWeekNoAndWeekDayLast"
    :rrule "FREQ=YEARLY;COUNT=3;BYWEEKNO=-1;BYDAY=SU"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-28T09:00:00" "1999-01-03T09:00:00" "2000-01-02T09:00:00"]}
   {:name "testYearlyByWeekNoAndWeekDay53"
    :rrule "FREQ=YEARLY;COUNT=3;BYWEEKNO=53;BYDAY=MO"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-12-28T09:00:00" "2004-12-27T09:00:00" "2009-12-28T09:00:00"]}
   {:name "testYearlyByHour"
    :rrule "FREQ=YEARLY;COUNT=3;BYHOUR=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:00:00" "1998-09-02T06:00:00" "1998-09-02T18:00:00"]}
   {:name "testYearlyByMinute"
    :rrule "FREQ=YEARLY;COUNT=3;BYMINUTE=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:06:00" "1997-09-02T09:18:00" "1998-09-02T09:06:00"]}
   {:name "testYearlyBySecond"
    :rrule "FREQ=YEARLY;COUNT=3;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:06" "1997-09-02T09:00:18" "1998-09-02T09:00:06"]}
   {:name "testYearlyByHourAndMinute"
    :rrule "FREQ=YEARLY;COUNT=3;BYHOUR=6,18;BYMINUTE=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:06:00" "1997-09-02T18:18:00" "1998-09-02T06:06:00"]}
   {:name "testYearlyByHourAndSecond"
    :rrule "FREQ=YEARLY;COUNT=3;BYHOUR=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:00:06" "1997-09-02T18:00:18" "1998-09-02T06:00:06"]}
   {:name "testYearlyByMinuteAndSecond"
    :rrule "FREQ=YEARLY;COUNT=3;BYMINUTE=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:06:06" "1997-09-02T09:06:18" "1997-09-02T09:18:06"]}
   {:name "testYearlyByHourAndMinuteAndSecond"
    :rrule "FREQ=YEARLY;COUNT=3;BYHOUR=6,18;BYMINUTE=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:06:06" "1997-09-02T18:06:18" "1997-09-02T18:18:06"]}
   {:name "testYearlyBySetPos"
    :rrule "FREQ=YEARLY;COUNT=3;BYMONTHDAY=15;BYHOUR=6,18;BYSETPOS=3,-3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-11-15T18:00:00" "1998-02-15T06:00:00" "1998-11-15T18:00:00"]}
   {:name "testMonthly"
    :rrule "FREQ=MONTHLY;COUNT=3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-10-02T09:00:00" "1997-11-02T09:00:00"]}
   {:name "testMonthlyInterval"
    :rrule "FREQ=MONTHLY;COUNT=3;INTERVAL=2"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-11-02T09:00:00" "1998-01-02T09:00:00"]}
   {:name "testMonthlyIntervalLarge"
    :rrule "FREQ=MONTHLY;COUNT=3;INTERVAL=18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1999-03-02T09:00:00" "2000-09-02T09:00:00"]}
   {:name "testMonthlyByMonth"
    :rrule "FREQ=MONTHLY;COUNT=3;BYMONTH=1,3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-02T09:00:00" "1998-03-02T09:00:00" "1999-01-02T09:00:00"]}
   {:name "testMonthlyByMonthDay"
    :rrule "FREQ=MONTHLY;COUNT=3;BYMONTHDAY=1,3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-03T09:00:00" "1997-10-01T09:00:00" "1997-10-03T09:00:00"]}
   {:name "testMonthlyByMonthAndMonthDay"
    :rrule "FREQ=MONTHLY;COUNT=3;BYMONTH=1,3;BYMONTHDAY=5,7"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-05T09:00:00" "1998-01-07T09:00:00" "1998-03-05T09:00:00"]}
   {:name "testMonthlyByWeekDay"
    :rrule "FREQ=MONTHLY;COUNT=3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-04T09:00:00" "1997-09-09T09:00:00"]}
   {:name "testMonthlyByNWeekDay"
    :rrule "FREQ=MONTHLY;COUNT=3;BYDAY=1TU,-1TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-25T09:00:00" "1997-10-07T09:00:00"]}
   {:name "testMonthlyByNWeekDayLarge"
    :rrule "FREQ=MONTHLY;COUNT=3;BYDAY=3TU,-3TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-11T09:00:00" "1997-09-16T09:00:00" "1997-10-16T09:00:00"]}
   {:name "testMonthlyByMonthAndWeekDay"
    :rrule "FREQ=MONTHLY;COUNT=3;BYMONTH=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-01-06T09:00:00" "1998-01-08T09:00:00"]}
   {:name "testMonthlyByMonthAndNWeekDay"
    :rrule "FREQ=MONTHLY;COUNT=3;BYMONTH=1,3;BYDAY=1TU,-1TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-06T09:00:00" "1998-01-29T09:00:00" "1998-03-03T09:00:00"]}
   {:name "testMonthlyByMonthAndNWeekDayLarge"
    :rrule "FREQ=MONTHLY;COUNT=3;BYMONTH=1,3;BYDAY=3TU,-3TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-15T09:00:00" "1998-01-20T09:00:00" "1998-03-12T09:00:00"]}
   {:name "testMonthlyByMonthDayAndWeekDay"
    :rrule "FREQ=MONTHLY;COUNT=3;BYMONTHDAY=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-02-03T09:00:00" "1998-03-03T09:00:00"]}
   {:name "testMonthlyByMonthAndMonthDayAndWeekDay"
    :rrule "FREQ=MONTHLY;COUNT=3;BYMONTH=1,3;BYMONTHDAY=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-03-03T09:00:00" "2001-03-01T09:00:00"]}
   {:name "testMonthlyByYearDay"
    :rrule "FREQ=MONTHLY;COUNT=4;BYYEARDAY=1,100,200,365"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-31T09:00:00" "1998-01-01T09:00:00" "1998-04-10T09:00:00" "1998-07-19T09:00:00"]}
   {:name "testMonthlyByYearDayNeg"
    :rrule "FREQ=MONTHLY;COUNT=4;BYYEARDAY=-365,-266,-166,-1"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-31T09:00:00" "1998-01-01T09:00:00" "1998-04-10T09:00:00" "1998-07-19T09:00:00"]}
   {:name "testMonthlyByMonthAndYearDay"
    :rrule "FREQ=MONTHLY;COUNT=4;BYMONTH=4,7;BYYEARDAY=1,100,200,365"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-04-10T09:00:00" "1998-07-19T09:00:00" "1999-04-10T09:00:00" "1999-07-19T09:00:00"]}
   {:name "testMonthlyByMonthAndYearDayNeg"
    :rrule "FREQ=MONTHLY;COUNT=4;BYMONTH=4,7;BYYEARDAY=-365,-266,-166,-1"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-04-10T09:00:00" "1998-07-19T09:00:00" "1999-04-10T09:00:00" "1999-07-19T09:00:00"]}
   {:name "testMonthlyByWeekNo"
    :rrule "FREQ=MONTHLY;COUNT=3;BYWEEKNO=20"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-05-11T09:00:00" "1998-05-12T09:00:00" "1998-05-13T09:00:00"]}
   {:name "testMonthlyByWeekNoAndWeekDay"
    :rrule "FREQ=MONTHLY;COUNT=3;BYWEEKNO=1;BYDAY=MO"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-29T09:00:00" "1999-01-04T09:00:00" "2000-01-03T09:00:00"]}
   {:name "testMonthlyByWeekNoAndWeekDayLarge"
    :rrule "FREQ=MONTHLY;COUNT=3;BYWEEKNO=52;BYDAY=SU"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-28T09:00:00" "1998-12-27T09:00:00" "2000-01-02T09:00:00"]}
   {:name "testMonthlyByWeekNoAndWeekDayLast"
    :rrule "FREQ=MONTHLY;COUNT=3;BYWEEKNO=-1;BYDAY=SU"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-28T09:00:00" "1999-01-03T09:00:00" "2000-01-02T09:00:00"]}
   {:name "testMonthlyByWeekNoAndWeekDay53"
    :rrule "FREQ=MONTHLY;COUNT=3;BYWEEKNO=53;BYDAY=MO"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-12-28T09:00:00" "2004-12-27T09:00:00" "2009-12-28T09:00:00"]}
   {:name "testMonthlyByHour"
    :rrule "FREQ=MONTHLY;COUNT=3;BYHOUR=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:00:00" "1997-10-02T06:00:00" "1997-10-02T18:00:00"]}
   {:name "testMonthlyByMinute"
    :rrule "FREQ=MONTHLY;COUNT=3;BYMINUTE=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:06:00" "1997-09-02T09:18:00" "1997-10-02T09:06:00"]}
   {:name "testMonthlyBySecond"
    :rrule "FREQ=MONTHLY;COUNT=3;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:06" "1997-09-02T09:00:18" "1997-10-02T09:00:06"]}
   {:name "testMonthlyByHourAndMinute"
    :rrule "FREQ=MONTHLY;COUNT=3;BYHOUR=6,18;BYMINUTE=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:06:00" "1997-09-02T18:18:00" "1997-10-02T06:06:00"]}
   {:name "testMonthlyByHourAndSecond"
    :rrule "FREQ=MONTHLY;COUNT=3;BYHOUR=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:00:06" "1997-09-02T18:00:18" "1997-10-02T06:00:06"]}
   {:name "testMonthlyByMinuteAndSecond"
    :rrule "FREQ=MONTHLY;COUNT=3;BYMINUTE=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:06:06" "1997-09-02T09:06:18" "1997-09-02T09:18:06"]}
   {:name "testMonthlyByHourAndMinuteAndSecond"
    :rrule "FREQ=MONTHLY;COUNT=3;BYHOUR=6,18;BYMINUTE=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:06:06" "1997-09-02T18:06:18" "1997-09-02T18:18:06"]}
   {:name "testMonthlyBySetPos"
    :rrule "FREQ=MONTHLY;COUNT=3;BYMONTHDAY=13,17;BYHOUR=6,18;BYSETPOS=3,-3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-13T18:00:00" "1997-09-17T06:00:00" "1997-10-13T18:00:00"]}
   {:name "testWeekly"
    :rrule "FREQ=WEEKLY;COUNT=3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-09T09:00:00" "1997-09-16T09:00:00"]}
   {:name "testWeeklyInterval"
    :rrule "FREQ=WEEKLY;COUNT=3;INTERVAL=2"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-16T09:00:00" "1997-09-30T09:00:00"]}
   {:name "testWeeklyIntervalLarge"
    :rrule "FREQ=WEEKLY;COUNT=3;INTERVAL=20"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1998-01-20T09:00:00" "1998-06-09T09:00:00"]}
   {:name "testWeeklyByMonth"
    :rrule "FREQ=WEEKLY;COUNT=3;BYMONTH=1,3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-06T09:00:00" "1998-01-13T09:00:00" "1998-01-20T09:00:00"]}
   {:name "testWeeklyByMonthDay"
    :rrule "FREQ=WEEKLY;COUNT=3;BYMONTHDAY=1,3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-03T09:00:00" "1997-10-01T09:00:00" "1997-10-03T09:00:00"]}
   {:name "testWeeklyByMonthAndMonthDay"
    :rrule "FREQ=WEEKLY;COUNT=3;BYMONTH=1,3;BYMONTHDAY=5,7"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-05T09:00:00" "1998-01-07T09:00:00" "1998-03-05T09:00:00"]}
   {:name "testWeeklyByWeekDay"
    :rrule "FREQ=WEEKLY;COUNT=3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-04T09:00:00" "1997-09-09T09:00:00"]}
   {:name "testWeeklyByNWeekDay"
    :rrule "FREQ=WEEKLY;COUNT=3;BYDAY=1TU,-1TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-04T09:00:00" "1997-09-09T09:00:00"]}
   {:name "testWeeklyByMonthAndWeekDay"
    :rrule "FREQ=WEEKLY;COUNT=3;BYMONTH=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-01-06T09:00:00" "1998-01-08T09:00:00"]}
   {:name "testWeeklyByMonthAndNWeekDay"
    :rrule "FREQ=WEEKLY;COUNT=3;BYMONTH=1,3;BYDAY=1TU,-1TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-01-06T09:00:00" "1998-01-08T09:00:00"]}
   {:name "testWeeklyByMonthDayAndWeekDay"
    :rrule "FREQ=WEEKLY;COUNT=3;BYMONTHDAY=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-02-03T09:00:00" "1998-03-03T09:00:00"]}
   {:name "testWeeklyByMonthAndMonthDayAndWeekDay"
    :rrule "FREQ=WEEKLY;COUNT=3;BYMONTH=1,3;BYMONTHDAY=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-03-03T09:00:00" "2001-03-01T09:00:00"]}
   {:name "testWeeklyByYearDay"
    :rrule "FREQ=WEEKLY;COUNT=4;BYYEARDAY=1,100,200,365"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-31T09:00:00" "1998-01-01T09:00:00" "1998-04-10T09:00:00" "1998-07-19T09:00:00"]}
   {:name "testWeeklyByYearDayNeg"
    :rrule "FREQ=WEEKLY;COUNT=4;BYYEARDAY=-365,-266,-166,-1"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-31T09:00:00" "1998-01-01T09:00:00" "1998-04-10T09:00:00" "1998-07-19T09:00:00"]}
   {:name "testWeeklyByMonthAndYearDay"
    :rrule "FREQ=WEEKLY;COUNT=4;BYMONTH=1,7;BYYEARDAY=1,100,200,365"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-07-19T09:00:00" "1999-01-01T09:00:00" "1999-07-19T09:00:00"]}
   {:name "testWeeklyByMonthAndYearDayNeg"
    :rrule "FREQ=WEEKLY;COUNT=4;BYMONTH=1,7;BYYEARDAY=-365,-266,-166,-1"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-07-19T09:00:00" "1999-01-01T09:00:00" "1999-07-19T09:00:00"]}
   {:name "testWeeklyByWeekNo"
    :rrule "FREQ=WEEKLY;COUNT=3;BYWEEKNO=20"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-05-11T09:00:00" "1998-05-12T09:00:00" "1998-05-13T09:00:00"]}
   {:name "testWeeklyByWeekNoAndWeekDay"
    :rrule "FREQ=WEEKLY;COUNT=3;BYWEEKNO=1;BYDAY=MO"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-29T09:00:00" "1999-01-04T09:00:00" "2000-01-03T09:00:00"]}
   {:name "testWeeklyByWeekNoAndWeekDayLarge"
    :rrule "FREQ=WEEKLY;COUNT=3;BYWEEKNO=52;BYDAY=SU"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-28T09:00:00" "1998-12-27T09:00:00" "2000-01-02T09:00:00"]}
   {:name "testWeeklyByWeekNoAndWeekDayLast"
    :rrule "FREQ=WEEKLY;COUNT=3;BYWEEKNO=-1;BYDAY=SU"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-28T09:00:00" "1999-01-03T09:00:00" "2000-01-02T09:00:00"]}
   {:name "testWeeklyByWeekNoAndWeekDay53"
    :rrule "FREQ=WEEKLY;COUNT=3;BYWEEKNO=53;BYDAY=MO"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-12-28T09:00:00" "2004-12-27T09:00:00" "2009-12-28T09:00:00"]}
   {:name "testWeeklyByHour"
    :rrule "FREQ=WEEKLY;COUNT=3;BYHOUR=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:00:00" "1997-09-09T06:00:00" "1997-09-09T18:00:00"]}
   {:name "testWeeklyByMinute"
    :rrule "FREQ=WEEKLY;COUNT=3;BYMINUTE=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:06:00" "1997-09-02T09:18:00" "1997-09-09T09:06:00"]}
   {:name "testWeeklyBySecond"
    :rrule "FREQ=WEEKLY;COUNT=3;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:06" "1997-09-02T09:00:18" "1997-09-09T09:00:06"]}
   {:name "testWeeklyByHourAndMinute"
    :rrule "FREQ=WEEKLY;COUNT=3;BYHOUR=6,18;BYMINUTE=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:06:00" "1997-09-02T18:18:00" "1997-09-09T06:06:00"]}
   {:name "testWeeklyByHourAndSecond"
    :rrule "FREQ=WEEKLY;COUNT=3;BYHOUR=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:00:06" "1997-09-02T18:00:18" "1997-09-09T06:00:06"]}
   {:name "testWeeklyByMinuteAndSecond"
    :rrule "FREQ=WEEKLY;COUNT=3;BYMINUTE=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:06:06" "1997-09-02T09:06:18" "1997-09-02T09:18:06"]}
   {:name "testWeeklyByHourAndMinuteAndSecond"
    :rrule "FREQ=WEEKLY;COUNT=3;BYHOUR=6,18;BYMINUTE=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:06:06" "1997-09-02T18:06:18" "1997-09-02T18:18:06"]}
   {:name "testWeeklyBySetPos"
    :rrule "FREQ=WEEKLY;COUNT=3;BYDAY=TU,TH;BYHOUR=6,18;BYSETPOS=3,-3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:00:00" "1997-09-04T06:00:00" "1997-09-09T18:00:00"]}
   {:name "testDaily"
    :rrule "FREQ=DAILY;COUNT=3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-03T09:00:00" "1997-09-04T09:00:00"]}
   {:name "testDailyInterval"
    :rrule "FREQ=DAILY;COUNT=3;INTERVAL=2"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-04T09:00:00" "1997-09-06T09:00:00"]}
   {:name "testDailyIntervalLarge"
    :rrule "FREQ=DAILY;COUNT=3;INTERVAL=92"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-12-03T09:00:00" "1998-03-05T09:00:00"]}
   {:name "testDailyByMonth"
    :rrule "FREQ=DAILY;COUNT=3;BYMONTH=1,3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-01-02T09:00:00" "1998-01-03T09:00:00"]}
   {:name "testDailyByMonthDay"
    :rrule "FREQ=DAILY;COUNT=3;BYMONTHDAY=1,3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-03T09:00:00" "1997-10-01T09:00:00" "1997-10-03T09:00:00"]}
   {:name "testDailyByMonthAndMonthDay"
    :rrule "FREQ=DAILY;COUNT=3;BYMONTH=1,3;BYMONTHDAY=5,7"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-05T09:00:00" "1998-01-07T09:00:00" "1998-03-05T09:00:00"]}
   {:name "testDailyByWeekDay"
    :rrule "FREQ=DAILY;COUNT=3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-04T09:00:00" "1997-09-09T09:00:00"]}
   {:name "testDailyByNWeekDay"
    :rrule "FREQ=DAILY;COUNT=3;BYDAY=1TU,-1TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-04T09:00:00" "1997-09-09T09:00:00"]}
   {:name "testDailyByMonthAndWeekDay"
    :rrule "FREQ=DAILY;COUNT=3;BYMONTH=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-01-06T09:00:00" "1998-01-08T09:00:00"]}
   {:name "testDailyByMonthAndNWeekDay"
    :rrule "FREQ=DAILY;COUNT=3;BYMONTH=1,3;BYDAY=1TU,-1TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-01-06T09:00:00" "1998-01-08T09:00:00"]}
   {:name "testDailyByMonthDayAndWeekDay"
    :rrule "FREQ=DAILY;COUNT=3;BYMONTHDAY=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-02-03T09:00:00" "1998-03-03T09:00:00"]}
   {:name "testDailyByMonthAndMonthDayAndWeekDay"
    :rrule "FREQ=DAILY;COUNT=3;BYMONTH=1,3;BYMONTHDAY=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-03-03T09:00:00" "2001-03-01T09:00:00"]}
   {:name "testDailyByYearDay"
    :rrule "FREQ=DAILY;COUNT=4;BYYEARDAY=1,100,200,365"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-31T09:00:00" "1998-01-01T09:00:00" "1998-04-10T09:00:00" "1998-07-19T09:00:00"]}
   {:name "testDailyByYearDayNeg"
    :rrule "FREQ=DAILY;COUNT=4;BYYEARDAY=-365,-266,-166,-1"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-31T09:00:00" "1998-01-01T09:00:00" "1998-04-10T09:00:00" "1998-07-19T09:00:00"]}
   {:name "testDailyByMonthAndYearDay"
    :rrule "FREQ=DAILY;COUNT=4;BYMONTH=1,7;BYYEARDAY=1,100,200,365"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-07-19T09:00:00" "1999-01-01T09:00:00" "1999-07-19T09:00:00"]}
   {:name "testDailyByMonthAndYearDayNeg"
    :rrule "FREQ=DAILY;COUNT=4;BYMONTH=1,7;BYYEARDAY=-365,-266,-166,-1"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T09:00:00" "1998-07-19T09:00:00" "1999-01-01T09:00:00" "1999-07-19T09:00:00"]}
   {:name "testDailyByWeekNo"
    :rrule "FREQ=DAILY;COUNT=3;BYWEEKNO=20"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-05-11T09:00:00" "1998-05-12T09:00:00" "1998-05-13T09:00:00"]}
   {:name "testDailyByWeekNoAndWeekDay"
    :rrule "FREQ=DAILY;COUNT=3;BYWEEKNO=1;BYDAY=MO"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-29T09:00:00" "1999-01-04T09:00:00" "2000-01-03T09:00:00"]}
   {:name "testDailyByWeekNoAndWeekDayLarge"
    :rrule "FREQ=DAILY;COUNT=3;BYWEEKNO=52;BYDAY=SU"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-28T09:00:00" "1998-12-27T09:00:00" "2000-01-02T09:00:00"]}
   {:name "testDailyByWeekNoAndWeekDayLast"
    :rrule "FREQ=DAILY;COUNT=3;BYWEEKNO=-1;BYDAY=SU"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-28T09:00:00" "1999-01-03T09:00:00" "2000-01-02T09:00:00"]}
   {:name "testDailyByWeekNoAndWeekDay53"
    :rrule "FREQ=DAILY;COUNT=3;BYWEEKNO=53;BYDAY=MO"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-12-28T09:00:00" "2004-12-27T09:00:00" "2009-12-28T09:00:00"]}
   {:name "testDailyByHour"
    :rrule "FREQ=DAILY;COUNT=3;BYHOUR=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:00:00" "1997-09-03T06:00:00" "1997-09-03T18:00:00"]}
   {:name "testDailyByMinute"
    :rrule "FREQ=DAILY;COUNT=3;BYMINUTE=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:06:00" "1997-09-02T09:18:00" "1997-09-03T09:06:00"]}
   {:name "testDailyBySecond"
    :rrule "FREQ=DAILY;COUNT=3;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:06" "1997-09-02T09:00:18" "1997-09-03T09:00:06"]}
   {:name "testDailyByHourAndMinute"
    :rrule "FREQ=DAILY;COUNT=3;BYHOUR=6,18;BYMINUTE=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:06:00" "1997-09-02T18:18:00" "1997-09-03T06:06:00"]}
   {:name "testDailyByHourAndSecond"
    :rrule "FREQ=DAILY;COUNT=3;BYHOUR=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:00:06" "1997-09-02T18:00:18" "1997-09-03T06:00:06"]}
   {:name "testDailyByMinuteAndSecond"
    :rrule "FREQ=DAILY;COUNT=3;BYMINUTE=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:06:06" "1997-09-02T09:06:18" "1997-09-02T09:18:06"]}
   {:name "testDailyByHourAndMinuteAndSecond"
    :rrule "FREQ=DAILY;COUNT=3;BYHOUR=6,18;BYMINUTE=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:06:06" "1997-09-02T18:06:18" "1997-09-02T18:18:06"]}
   {:name "testDailyBySetPos"
    :rrule "FREQ=DAILY;COUNT=3;BYHOUR=6,18;BYMINUTE=15,45;BYSETPOS=3,-3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:15:00" "1997-09-03T06:45:00" "1997-09-03T18:15:00"]}
   {:name "testHourly"
    :rrule "FREQ=HOURLY;COUNT=3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-02T10:00:00" "1997-09-02T11:00:00"]}
   {:name "testHourlyInterval"
    :rrule "FREQ=HOURLY;COUNT=3;INTERVAL=2"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-02T11:00:00" "1997-09-02T13:00:00"]}
   {:name "testHourlyIntervalLarge"
    :rrule "FREQ=HOURLY;COUNT=3;INTERVAL=769"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-10-04T10:00:00" "1997-11-05T11:00:00"]}
   {:name "testHourlyByMonth"
    :rrule "FREQ=HOURLY;COUNT=3;BYMONTH=1,3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T00:00:00" "1998-01-01T01:00:00" "1998-01-01T02:00:00"]}
   {:name "testHourlyByMonthDay"
    :rrule "FREQ=HOURLY;COUNT=3;BYMONTHDAY=1,3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-03T00:00:00" "1997-09-03T01:00:00" "1997-09-03T02:00:00"]}
   {:name "testHourlyByMonthAndMonthDay"
    :rrule "FREQ=HOURLY;COUNT=3;BYMONTH=1,3;BYMONTHDAY=5,7"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-05T00:00:00" "1998-01-05T01:00:00" "1998-01-05T02:00:00"]}
   {:name "testHourlyByWeekDay"
    :rrule "FREQ=HOURLY;COUNT=3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-02T10:00:00" "1997-09-02T11:00:00"]}
   {:name "testHourlyByNWeekDay"
    :rrule "FREQ=HOURLY;COUNT=3;BYDAY=1TU,-1TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-02T10:00:00" "1997-09-02T11:00:00"]}
   {:name "testHourlyByMonthAndWeekDay"
    :rrule "FREQ=HOURLY;COUNT=3;BYMONTH=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T00:00:00" "1998-01-01T01:00:00" "1998-01-01T02:00:00"]}
   {:name "testHourlyByMonthAndNWeekDay"
    :rrule "FREQ=HOURLY;COUNT=3;BYMONTH=1,3;BYDAY=1TU,-1TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T00:00:00" "1998-01-01T01:00:00" "1998-01-01T02:00:00"]}
   {:name "testHourlyByMonthDayAndWeekDay"
    :rrule "FREQ=HOURLY;COUNT=3;BYMONTHDAY=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T00:00:00" "1998-01-01T01:00:00" "1998-01-01T02:00:00"]}
   {:name "testHourlyByMonthAndMonthDayAndWeekDay"
    :rrule "FREQ=HOURLY;COUNT=3;BYMONTH=1,3;BYMONTHDAY=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T00:00:00" "1998-01-01T01:00:00" "1998-01-01T02:00:00"]}
   {:name "testHourlyByYearDay"
    :rrule "FREQ=HOURLY;COUNT=4;BYYEARDAY=1,100,200,365"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-31T00:00:00" "1997-12-31T01:00:00" "1997-12-31T02:00:00" "1997-12-31T03:00:00"]}
   {:name "testHourlyByYearDayNeg"
    :rrule "FREQ=HOURLY;COUNT=4;BYYEARDAY=-365,-266,-166,-1"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-31T00:00:00" "1997-12-31T01:00:00" "1997-12-31T02:00:00" "1997-12-31T03:00:00"]}
   {:name "testHourlyByMonthAndYearDay"
    :rrule "FREQ=HOURLY;COUNT=4;BYMONTH=4,7;BYYEARDAY=1,100,200,365"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-04-10T00:00:00" "1998-04-10T01:00:00" "1998-04-10T02:00:00" "1998-04-10T03:00:00"]}
   {:name "testHourlyByMonthAndYearDayNeg"
    :rrule "FREQ=HOURLY;COUNT=4;BYMONTH=4,7;BYYEARDAY=-365,-266,-166,-1"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-04-10T00:00:00" "1998-04-10T01:00:00" "1998-04-10T02:00:00" "1998-04-10T03:00:00"]}
   {:name "testHourlyByWeekNo"
    :rrule "FREQ=HOURLY;COUNT=3;BYWEEKNO=20"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-05-11T00:00:00" "1998-05-11T01:00:00" "1998-05-11T02:00:00"]}
   {:name "testHourlyByWeekNoAndWeekDay"
    :rrule "FREQ=HOURLY;COUNT=3;BYWEEKNO=1;BYDAY=MO"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-29T00:00:00" "1997-12-29T01:00:00" "1997-12-29T02:00:00"]}
   {:name "testHourlyByWeekNoAndWeekDayLarge"
    :rrule "FREQ=HOURLY;COUNT=3;BYWEEKNO=52;BYDAY=SU"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-28T00:00:00" "1997-12-28T01:00:00" "1997-12-28T02:00:00"]}
   {:name "testHourlyByWeekNoAndWeekDayLast"
    :rrule "FREQ=HOURLY;COUNT=3;BYWEEKNO=-1;BYDAY=SU"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-28T00:00:00" "1997-12-28T01:00:00" "1997-12-28T02:00:00"]}
   {:name "testHourlyByWeekNoAndWeekDay53"
    :rrule "FREQ=HOURLY;COUNT=3;BYWEEKNO=53;BYDAY=MO"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-12-28T00:00:00" "1998-12-28T01:00:00" "1998-12-28T02:00:00"]}
   {:name "testHourlyByHour"
    :rrule "FREQ=HOURLY;COUNT=3;BYHOUR=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:00:00" "1997-09-03T06:00:00" "1997-09-03T18:00:00"]}
   {:name "testHourlyByMinute"
    :rrule "FREQ=HOURLY;COUNT=3;BYMINUTE=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:06:00" "1997-09-02T09:18:00" "1997-09-02T10:06:00"]}
   {:name "testHourlyBySecond"
    :rrule "FREQ=HOURLY;COUNT=3;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:06" "1997-09-02T09:00:18" "1997-09-02T10:00:06"]}
   {:name "testHourlyByHourAndMinute"
    :rrule "FREQ=HOURLY;COUNT=3;BYHOUR=6,18;BYMINUTE=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:06:00" "1997-09-02T18:18:00" "1997-09-03T06:06:00"]}
   {:name "testHourlyByHourAndSecond"
    :rrule "FREQ=HOURLY;COUNT=3;BYHOUR=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:00:06" "1997-09-02T18:00:18" "1997-09-03T06:00:06"]}
   {:name "testHourlyByMinuteAndSecond"
    :rrule "FREQ=HOURLY;COUNT=3;BYMINUTE=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:06:06" "1997-09-02T09:06:18" "1997-09-02T09:18:06"]}
   {:name "testHourlyByHourAndMinuteAndSecond"
    :rrule "FREQ=HOURLY;COUNT=3;BYHOUR=6,18;BYMINUTE=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:06:06" "1997-09-02T18:06:18" "1997-09-02T18:18:06"]}
   {:name "testHourlyBySetPos"
    :rrule "FREQ=HOURLY;COUNT=3;BYMINUTE=15,45;BYSECOND=15,45;BYSETPOS=3,-3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:15:45" "1997-09-02T09:45:15" "1997-09-02T10:15:45"]}
   {:name "testMinutely"
    :rrule "FREQ=MINUTELY;COUNT=3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-02T09:01:00" "1997-09-02T09:02:00"]}
   {:name "testMinutelyInterval"
    :rrule "FREQ=MINUTELY;COUNT=3;INTERVAL=2"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-02T09:02:00" "1997-09-02T09:04:00"]}
   {:name "testMinutelyIntervalLarge"
    :rrule "FREQ=MINUTELY;COUNT=3;INTERVAL=1501"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-03T10:01:00" "1997-09-04T11:02:00"]}
   {:name "testMinutelyByMonth"
    :rrule "FREQ=MINUTELY;COUNT=3;BYMONTH=1,3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T00:00:00" "1998-01-01T00:01:00" "1998-01-01T00:02:00"]}
   {:name "testMinutelyByMonthDay"
    :rrule "FREQ=MINUTELY;COUNT=3;BYMONTHDAY=1,3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-03T00:00:00" "1997-09-03T00:01:00" "1997-09-03T00:02:00"]}
   {:name "testMinutelyByMonthAndMonthDay"
    :rrule "FREQ=MINUTELY;COUNT=3;BYMONTH=1,3;BYMONTHDAY=5,7"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-05T00:00:00" "1998-01-05T00:01:00" "1998-01-05T00:02:00"]}
   {:name "testMinutelyByWeekDay"
    :rrule "FREQ=MINUTELY;COUNT=3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-02T09:01:00" "1997-09-02T09:02:00"]}
   {:name "testMinutelyByNWeekDay"
    :rrule "FREQ=MINUTELY;COUNT=3;BYDAY=1TU,-1TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-02T09:01:00" "1997-09-02T09:02:00"]}
   {:name "testMinutelyByMonthAndWeekDay"
    :rrule "FREQ=MINUTELY;COUNT=3;BYMONTH=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T00:00:00" "1998-01-01T00:01:00" "1998-01-01T00:02:00"]}
   {:name "testMinutelyByMonthAndNWeekDay"
    :rrule "FREQ=MINUTELY;COUNT=3;BYMONTH=1,3;BYDAY=1TU,-1TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T00:00:00" "1998-01-01T00:01:00" "1998-01-01T00:02:00"]}
   {:name "testMinutelyByMonthDayAndWeekDay"
    :rrule "FREQ=MINUTELY;COUNT=3;BYMONTHDAY=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T00:00:00" "1998-01-01T00:01:00" "1998-01-01T00:02:00"]}
   {:name "testMinutelyByMonthAndMonthDayAndWeekDay"
    :rrule "FREQ=MINUTELY;COUNT=3;BYMONTH=1,3;BYMONTHDAY=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T00:00:00" "1998-01-01T00:01:00" "1998-01-01T00:02:00"]}
   {:name "testMinutelyByYearDay"
    :rrule "FREQ=MINUTELY;COUNT=4;BYYEARDAY=1,100,200,365"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-31T00:00:00" "1997-12-31T00:01:00" "1997-12-31T00:02:00" "1997-12-31T00:03:00"]}
   {:name "testMinutelyByYearDayNeg"
    :rrule "FREQ=MINUTELY;COUNT=4;BYYEARDAY=-365,-266,-166,-1"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-31T00:00:00" "1997-12-31T00:01:00" "1997-12-31T00:02:00" "1997-12-31T00:03:00"]}
   {:name "testMinutelyByMonthAndYearDay"
    :rrule "FREQ=MINUTELY;COUNT=4;BYMONTH=4,7;BYYEARDAY=1,100,200,365"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-04-10T00:00:00" "1998-04-10T00:01:00" "1998-04-10T00:02:00" "1998-04-10T00:03:00"]}
   {:name "testMinutelyByMonthAndYearDayNeg"
    :rrule "FREQ=MINUTELY;COUNT=4;BYMONTH=4,7;BYYEARDAY=-365,-266,-166,-1"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-04-10T00:00:00" "1998-04-10T00:01:00" "1998-04-10T00:02:00" "1998-04-10T00:03:00"]}
   {:name "testMinutelyByWeekNo"
    :rrule "FREQ=MINUTELY;COUNT=3;BYWEEKNO=20"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-05-11T00:00:00" "1998-05-11T00:01:00" "1998-05-11T00:02:00"]}
   {:name "testMinutelyByWeekNoAndWeekDay"
    :rrule "FREQ=MINUTELY;COUNT=3;BYWEEKNO=1;BYDAY=MO"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-29T00:00:00" "1997-12-29T00:01:00" "1997-12-29T00:02:00"]}
   {:name "testMinutelyByWeekNoAndWeekDayLarge"
    :rrule "FREQ=MINUTELY;COUNT=3;BYWEEKNO=52;BYDAY=SU"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-28T00:00:00" "1997-12-28T00:01:00" "1997-12-28T00:02:00"]}
   {:name "testMinutelyByWeekNoAndWeekDayLast"
    :rrule "FREQ=MINUTELY;COUNT=3;BYWEEKNO=-1;BYDAY=SU"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-28T00:00:00" "1997-12-28T00:01:00" "1997-12-28T00:02:00"]}
   {:name "testMinutelyByWeekNoAndWeekDay53"
    :rrule "FREQ=MINUTELY;COUNT=3;BYWEEKNO=53;BYDAY=MO"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-12-28T00:00:00" "1998-12-28T00:01:00" "1998-12-28T00:02:00"]}
   {:name "testMinutelyByHour"
    :rrule "FREQ=MINUTELY;COUNT=3;BYHOUR=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:00:00" "1997-09-02T18:01:00" "1997-09-02T18:02:00"]}
   {:name "testMinutelyByMinute"
    :rrule "FREQ=MINUTELY;COUNT=3;BYMINUTE=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:06:00" "1997-09-02T09:18:00" "1997-09-02T10:06:00"]}
   {:name "testMinutelyBySecond"
    :rrule "FREQ=MINUTELY;COUNT=3;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:06" "1997-09-02T09:00:18" "1997-09-02T09:01:06"]}
   {:name "testMinutelyByHourAndMinute"
    :rrule "FREQ=MINUTELY;COUNT=3;BYHOUR=6,18;BYMINUTE=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:06:00" "1997-09-02T18:18:00" "1997-09-03T06:06:00"]}
   {:name "testMinutelyByHourAndSecond"
    :rrule "FREQ=MINUTELY;COUNT=3;BYHOUR=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:00:06" "1997-09-02T18:00:18" "1997-09-02T18:01:06"]}
   {:name "testMinutelyByMinuteAndSecond"
    :rrule "FREQ=MINUTELY;COUNT=3;BYMINUTE=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:06:06" "1997-09-02T09:06:18" "1997-09-02T09:18:06"]}
   {:name "testMinutelyByHourAndMinuteAndSecond"
    :rrule "FREQ=MINUTELY;COUNT=3;BYHOUR=6,18;BYMINUTE=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:06:06" "1997-09-02T18:06:18" "1997-09-02T18:18:06"]}
   {:name "testMinutelyBySetPos"
    :rrule "FREQ=MINUTELY;COUNT=3;BYSECOND=15,30,45;BYSETPOS=3,-3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:15" "1997-09-02T09:00:45" "1997-09-02T09:01:15"]}
   {:name "testSecondly"
    :rrule "FREQ=SECONDLY;COUNT=3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-02T09:00:01" "1997-09-02T09:00:02"]}
   {:name "testSecondlyInterval"
    :rrule "FREQ=SECONDLY;COUNT=3;INTERVAL=2"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-02T09:00:02" "1997-09-02T09:00:04"]}
   {:name "testSecondlyIntervalLarge"
    :rrule "FREQ=SECONDLY;COUNT=3;INTERVAL=90061"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-03T10:01:01" "1997-09-04T11:02:02"]}
   {:name "testSecondlyByMonth"
    :rrule "FREQ=SECONDLY;COUNT=3;BYMONTH=1,3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T00:00:00" "1998-01-01T00:00:01" "1998-01-01T00:00:02"]}
   {:name "testSecondlyByMonthDay"
    :rrule "FREQ=SECONDLY;COUNT=3;BYMONTHDAY=1,3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-03T00:00:00" "1997-09-03T00:00:01" "1997-09-03T00:00:02"]}
   {:name "testSecondlyByMonthAndMonthDay"
    :rrule "FREQ=SECONDLY;COUNT=3;BYMONTH=1,3;BYMONTHDAY=5,7"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-05T00:00:00" "1998-01-05T00:00:01" "1998-01-05T00:00:02"]}
   {:name "testSecondlyByWeekDay"
    :rrule "FREQ=SECONDLY;COUNT=3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-02T09:00:01" "1997-09-02T09:00:02"]}
   {:name "testSecondlyByNWeekDay"
    :rrule "FREQ=SECONDLY;COUNT=3;BYDAY=1TU,-1TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-02T09:00:01" "1997-09-02T09:00:02"]}
   {:name "testSecondlyByMonthAndWeekDay"
    :rrule "FREQ=SECONDLY;COUNT=3;BYMONTH=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T00:00:00" "1998-01-01T00:00:01" "1998-01-01T00:00:02"]}
   {:name "testSecondlyByMonthAndNWeekDay"
    :rrule "FREQ=SECONDLY;COUNT=3;BYMONTH=1,3;BYDAY=1TU,-1TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T00:00:00" "1998-01-01T00:00:01" "1998-01-01T00:00:02"]}
   {:name "testSecondlyByMonthDayAndWeekDay"
    :rrule "FREQ=SECONDLY;COUNT=3;BYMONTHDAY=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T00:00:00" "1998-01-01T00:00:01" "1998-01-01T00:00:02"]}
   {:name "testSecondlyByMonthAndMonthDayAndWeekDay"
    :rrule "FREQ=SECONDLY;COUNT=3;BYMONTH=1,3;BYMONTHDAY=1,3;BYDAY=TU,TH"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-01T00:00:00" "1998-01-01T00:00:01" "1998-01-01T00:00:02"]}
   {:name "testSecondlyByYearDay"
    :rrule "FREQ=SECONDLY;COUNT=4;BYYEARDAY=1,100,200,365"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-31T00:00:00" "1997-12-31T00:00:01" "1997-12-31T00:00:02" "1997-12-31T00:00:03"]}
   {:name "testSecondlyByYearDayNeg"
    :rrule "FREQ=SECONDLY;COUNT=4;BYYEARDAY=-365,-266,-166,-1"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-31T00:00:00" "1997-12-31T00:00:01" "1997-12-31T00:00:02" "1997-12-31T00:00:03"]}
   {:name "testSecondlyByMonthAndYearDay"
    :rrule "FREQ=SECONDLY;COUNT=4;BYMONTH=4,7;BYYEARDAY=1,100,200,365"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-04-10T00:00:00" "1998-04-10T00:00:01" "1998-04-10T00:00:02" "1998-04-10T00:00:03"]}
   {:name "testSecondlyByMonthAndYearDayNeg"
    :rrule "FREQ=SECONDLY;COUNT=4;BYMONTH=4,7;BYYEARDAY=-365,-266,-166,-1"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-04-10T00:00:00" "1998-04-10T00:00:01" "1998-04-10T00:00:02" "1998-04-10T00:00:03"]}
   {:name "testSecondlyByWeekNo"
    :rrule "FREQ=SECONDLY;COUNT=3;BYWEEKNO=20"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-05-11T00:00:00" "1998-05-11T00:00:01" "1998-05-11T00:00:02"]}
   {:name "testSecondlyByWeekNoAndWeekDay"
    :rrule "FREQ=SECONDLY;COUNT=3;BYWEEKNO=1;BYDAY=MO"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-29T00:00:00" "1997-12-29T00:00:01" "1997-12-29T00:00:02"]}
   {:name "testSecondlyByWeekNoAndWeekDayLarge"
    :rrule "FREQ=SECONDLY;COUNT=3;BYWEEKNO=52;BYDAY=SU"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-28T00:00:00" "1997-12-28T00:00:01" "1997-12-28T00:00:02"]}
   {:name "testSecondlyByWeekNoAndWeekDayLast"
    :rrule "FREQ=SECONDLY;COUNT=3;BYWEEKNO=-1;BYDAY=SU"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-12-28T00:00:00" "1997-12-28T00:00:01" "1997-12-28T00:00:02"]}
   {:name "testSecondlyByWeekNoAndWeekDay53"
    :rrule "FREQ=SECONDLY;COUNT=3;BYWEEKNO=53;BYDAY=MO"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-12-28T00:00:00" "1998-12-28T00:00:01" "1998-12-28T00:00:02"]}
   {:name "testSecondlyByHour"
    :rrule "FREQ=SECONDLY;COUNT=3;BYHOUR=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:00:00" "1997-09-02T18:00:01" "1997-09-02T18:00:02"]}
   {:name "testSecondlyByMinute"
    :rrule "FREQ=SECONDLY;COUNT=3;BYMINUTE=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:06:00" "1997-09-02T09:06:01" "1997-09-02T09:06:02"]}
   {:name "testSecondlyBySecond"
    :rrule "FREQ=SECONDLY;COUNT=3;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:06" "1997-09-02T09:00:18" "1997-09-02T09:01:06"]}
   {:name "testSecondlyByHourAndMinute"
    :rrule "FREQ=SECONDLY;COUNT=3;BYHOUR=6,18;BYMINUTE=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:06:00" "1997-09-02T18:06:01" "1997-09-02T18:06:02"]}
   {:name "testSecondlyByHourAndSecond"
    :rrule "FREQ=SECONDLY;COUNT=3;BYHOUR=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:00:06" "1997-09-02T18:00:18" "1997-09-02T18:01:06"]}
   {:name "testSecondlyByMinuteAndSecond"
    :rrule "FREQ=SECONDLY;COUNT=3;BYMINUTE=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:06:06" "1997-09-02T09:06:18" "1997-09-02T09:18:06"]}
   {:name "testSecondlyByHourAndMinuteAndSecond"
    :rrule "FREQ=SECONDLY;COUNT=3;BYHOUR=6,18;BYMINUTE=6,18;BYSECOND=6,18"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T18:06:06" "1997-09-02T18:06:18" "1997-09-02T18:18:06"]}
   {:name "testSecondlyByHourAndMinuteAndSecondBug"
    :rrule "FREQ=SECONDLY;COUNT=3;BYSECOND=0;BYMINUTE=1"
    :dtstart "2010-03-22T12:01:00"
    :expected ["2010-03-22T12:01:00" "2010-03-22T13:01:00" "2010-03-22T14:01:00"]}
   {:name "testLongIntegers"
    :rrule "FREQ=YEARLY;COUNT=2;BYMONTHDAY=5;BYWEEKNO=2"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1998-01-05T09:00:00" "2004-01-05T09:00:00"]}
   {:name "testUntilNotMatching"
    :rrule "FREQ=DAILY;UNTIL=19970905T080000"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-03T09:00:00" "1997-09-04T09:00:00"]}
   {:name "testUntilMatching"
    :rrule "FREQ=DAILY;UNTIL=19970904T090000"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-03T09:00:00" "1997-09-04T09:00:00"]}
   {:name "testUntilSingle"
    :rrule "FREQ=DAILY;UNTIL=19970902T090000"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00"]}
   {:name "testUntilEmpty"
    :rrule "FREQ=DAILY;UNTIL=19970901T090000"
    :dtstart "1997-09-02T09:00:00"
    :expected []}
   {:name "testUntilWithDate"
    :rrule "FREQ=DAILY;UNTIL=19970905"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-03T09:00:00" "1997-09-04T09:00:00"]}
   {:name "testWkStIntervalMO"
    :rrule "FREQ=WEEKLY;COUNT=3;INTERVAL=2;BYDAY=TU,SU;WKST=MO"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-07T09:00:00" "1997-09-16T09:00:00"]}
   {:name "testWkStIntervalSU"
    :rrule "FREQ=WEEKLY;COUNT=3;INTERVAL=2;BYDAY=TU,SU;WKST=SU"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-14T09:00:00" "1997-09-16T09:00:00"]}
   {:name "testDTStartIsDate"
    :rrule "FREQ=DAILY;COUNT=3"
    :dtstart "1997-09-02"
    :expected ["1997-09-02T00:00:00" "1997-09-03T00:00:00" "1997-09-04T00:00:00"]}
   {:name "testDTStartWithMicroseconds"
    :rrule "FREQ=DAILY;COUNT=3"
    :dtstart "1997-09-02T09:00:00"
    :expected ["1997-09-02T09:00:00" "1997-09-03T09:00:00" "1997-09-04T09:00:00"]}
   {:name "testMaxYear"
    :rrule "FREQ=YEARLY;COUNT=3;BYMONTH=2;BYMONTHDAY=31"
    :dtstart "9997-09-02T09:00:00"
    :expected []}])
