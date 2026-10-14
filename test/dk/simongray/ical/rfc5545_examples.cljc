(ns dk.simongray.ical.rfc5545-examples
  "The recurrence examples of RFC 5545, section 3.8.5.3, with the
  instances they list, in America/New_York.

  The example \"every 3 hours from 9:00 AM to 5:00 PM\" has the UNTIL of
  erratum 3883. The errata against the other examples were rejected.
  Where an example goes on forever, :expected is only the start of it, as
  the RFC lists it.")

(defn- pad
  "The number `n` as text of at least `width` digits."
  [n width]
  (let [s (str n)]
    (str (apply str (repeat (- width (count s)) "0")) s)))

(defn at
  "The local date-times at the time `t`, e.g. \"09:00:00\", on the days of
  `months`, each a vector of a year, a month and its days."
  [t & months]
  (vec (for [[y m days] months
             d          days]
         (str y "-" (pad m 2) "-" (pad d 2) "T" t))))

(defn- times
  "The local date-times on the date `date` at each of `ts`."
  [date & ts]
  (mapv #(str date "T" %) ts))

(def examples
  "Each example as a map of its :title, :dtstart (in America/New_York),
  :rrules (one or more that give the same instances), :exdate when it has
  one, :expected and :complete?, true when it lists every instance."
  [{:title     "Daily for 10 occurrences"
    :dtstart   "1997-09-02T09:00:00"
    :rrules    ["FREQ=DAILY;COUNT=10"]
    :expected  (at "09:00:00" [1997 9 (range 2 12)])
    :complete? true}
   {:title     "Daily until December 24, 1997"
    :dtstart   "1997-09-02T09:00:00"
    :rrules    ["FREQ=DAILY;UNTIL=19971224T000000Z"]
    :expected  (at "09:00:00" [1997 9 (range 2 31)] [1997 10 (range 1 32)]
                   [1997 11 (range 1 31)] [1997 12 (range 1 24)])
    :complete? true}
   {:title     "Every other day - forever"
    :dtstart   "1997-09-02T09:00:00"
    :rrules    ["FREQ=DAILY;INTERVAL=2"]
    :expected  (at "09:00:00" [1997 9 (range 2 31 2)] [1997 10 (range 2 31 2)]
                   [1997 11 (range 1 30 2)] [1997 12 [1 3]])
    :complete? false}
   {:title     "Every 10 days, 5 occurrences"
    :dtstart   "1997-09-02T09:00:00"
    :rrules    ["FREQ=DAILY;INTERVAL=10;COUNT=5"]
    :expected  (at "09:00:00" [1997 9 [2 12 22]] [1997 10 [2 12]])
    :complete? true}
   {:title     "Every day in January, for 3 years"
    :dtstart   "1998-01-01T09:00:00"
    :rrules    ["FREQ=YEARLY;UNTIL=20000131T140000Z;BYMONTH=1;BYDAY=SU,MO,TU,WE,TH,FR,SA"
                "FREQ=DAILY;UNTIL=20000131T140000Z;BYMONTH=1"]
    :expected  (at "09:00:00" [1998 1 (range 1 32)] [1999 1 (range 1 32)]
                   [2000 1 (range 1 32)])
    :complete? true}
   {:title     "Weekly for 10 occurrences"
    :dtstart   "1997-09-02T09:00:00"
    :rrules    ["FREQ=WEEKLY;COUNT=10"]
    :expected  (at "09:00:00" [1997 9 [2 9 16 23 30]] [1997 10 [7 14 21 28]]
                   [1997 11 [4]])
    :complete? true}
   {:title     "Weekly until December 24, 1997"
    :dtstart   "1997-09-02T09:00:00"
    :rrules    ["FREQ=WEEKLY;UNTIL=19971224T000000Z"]
    :expected  (at "09:00:00" [1997 9 [2 9 16 23 30]] [1997 10 [7 14 21 28]]
                   [1997 11 [4 11 18 25]] [1997 12 [2 9 16 23]])
    :complete? true}
   {:title     "Every other week - forever"
    :dtstart   "1997-09-02T09:00:00"
    :rrules    ["FREQ=WEEKLY;INTERVAL=2;WKST=SU"]
    :expected  (at "09:00:00" [1997 9 [2 16 30]] [1997 10 [14 28]] [1997 11 [11 25]]
                   [1997 12 [9 23]] [1998 1 [6 20]] [1998 2 [3 17]])
    :complete? false}
   {:title     "Weekly on Tuesday and Thursday for five weeks"
    :dtstart   "1997-09-02T09:00:00"
    :rrules    ["FREQ=WEEKLY;UNTIL=19971007T000000Z;WKST=SU;BYDAY=TU,TH"
                "FREQ=WEEKLY;COUNT=10;WKST=SU;BYDAY=TU,TH"]
    :expected  (at "09:00:00" [1997 9 [2 4 9 11 16 18 23 25 30]] [1997 10 [2]])
    :complete? true}
   {:title     "Every other week on Monday, Wednesday, and Friday until December 24, 1997"
    :dtstart   "1997-09-01T09:00:00"
    :rrules    ["FREQ=WEEKLY;INTERVAL=2;UNTIL=19971224T000000Z;WKST=SU;BYDAY=MO,WE,FR"]
    :expected  (at "09:00:00" [1997 9 [1 3 5 15 17 19 29]] [1997 10 [1 3 13 15 17 27 29 31]]
                   [1997 11 [10 12 14 24 26 28]] [1997 12 [8 10 12 22]])
    :complete? true}
   {:title     "Every other week on Tuesday and Thursday, for 8 occurrences"
    :dtstart   "1997-09-02T09:00:00"
    :rrules    ["FREQ=WEEKLY;INTERVAL=2;COUNT=8;WKST=SU;BYDAY=TU,TH"]
    :expected  (at "09:00:00" [1997 9 [2 4 16 18 30]] [1997 10 [2 14 16]])
    :complete? true}
   {:title     "Monthly on the first Friday for 10 occurrences"
    :dtstart   "1997-09-05T09:00:00"
    :rrules    ["FREQ=MONTHLY;COUNT=10;BYDAY=1FR"]
    :expected  (at "09:00:00" [1997 9 [5]] [1997 10 [3]] [1997 11 [7]] [1997 12 [5]]
                   [1998 1 [2]] [1998 2 [6]] [1998 3 [6]] [1998 4 [3]] [1998 5 [1]]
                   [1998 6 [5]])
    :complete? true}
   {:title     "Monthly on the first Friday until December 24, 1997"
    :dtstart   "1997-09-05T09:00:00"
    :rrules    ["FREQ=MONTHLY;UNTIL=19971224T000000Z;BYDAY=1FR"]
    :expected  (at "09:00:00" [1997 9 [5]] [1997 10 [3]] [1997 11 [7]] [1997 12 [5]])
    :complete? true}
   {:title     "Every other month on the first and last Sunday of the month for 10 occurrences"
    :dtstart   "1997-09-07T09:00:00"
    :rrules    ["FREQ=MONTHLY;INTERVAL=2;COUNT=10;BYDAY=1SU,-1SU"]
    :expected  (at "09:00:00" [1997 9 [7 28]] [1997 11 [2 30]] [1998 1 [4 25]]
                   [1998 3 [1 29]] [1998 5 [3 31]])
    :complete? true}
   {:title     "Monthly on the second-to-last Monday of the month for 6 months"
    :dtstart   "1997-09-22T09:00:00"
    :rrules    ["FREQ=MONTHLY;COUNT=6;BYDAY=-2MO"]
    :expected  (at "09:00:00" [1997 9 [22]] [1997 10 [20]] [1997 11 [17]] [1997 12 [22]]
                   [1998 1 [19]] [1998 2 [16]])
    :complete? true}
   {:title     "Monthly on the third-to-the-last day of the month, forever"
    :dtstart   "1997-09-28T09:00:00"
    :rrules    ["FREQ=MONTHLY;BYMONTHDAY=-3"]
    :expected  (at "09:00:00" [1997 9 [28]] [1997 10 [29]] [1997 11 [28]] [1997 12 [29]]
                   [1998 1 [29]] [1998 2 [26]])
    :complete? false}
   {:title     "Monthly on the 2nd and 15th of the month for 10 occurrences"
    :dtstart   "1997-09-02T09:00:00"
    :rrules    ["FREQ=MONTHLY;COUNT=10;BYMONTHDAY=2,15"]
    :expected  (at "09:00:00" [1997 9 [2 15]] [1997 10 [2 15]] [1997 11 [2 15]]
                   [1997 12 [2 15]] [1998 1 [2 15]])
    :complete? true}
   {:title     "Monthly on the first and last day of the month for 10 occurrences"
    :dtstart   "1997-09-30T09:00:00"
    :rrules    ["FREQ=MONTHLY;COUNT=10;BYMONTHDAY=1,-1"]
    :expected  (at "09:00:00" [1997 9 [30]] [1997 10 [1 31]] [1997 11 [1 30]]
                   [1997 12 [1 31]] [1998 1 [1 31]] [1998 2 [1]])
    :complete? true}
   {:title     "Every 18 months on the 10th thru 15th of the month for 10 occurrences"
    :dtstart   "1997-09-10T09:00:00"
    :rrules    ["FREQ=MONTHLY;INTERVAL=18;COUNT=10;BYMONTHDAY=10,11,12,13,14,15"]
    :expected  (at "09:00:00" [1997 9 (range 10 16)] [1999 3 [10 11 12 13]])
    :complete? true}
   {:title     "Every Tuesday, every other month"
    :dtstart   "1997-09-02T09:00:00"
    :rrules    ["FREQ=MONTHLY;INTERVAL=2;BYDAY=TU"]
    :expected  (at "09:00:00" [1997 9 [2 9 16 23 30]] [1997 11 [4 11 18 25]]
                   [1998 1 [6 13 20 27]] [1998 3 [3 10 17 24 31]])
    :complete? false}
   {:title     "Yearly in June and July for 10 occurrences"
    :dtstart   "1997-06-10T09:00:00"
    :rrules    ["FREQ=YEARLY;COUNT=10;BYMONTH=6,7"]
    :expected  (apply at "09:00:00" (for [y (range 1997 2002), m [6 7]] [y m [10]]))
    :complete? true}
   {:title     "Every other year on January, February, and March for 10 occurrences"
    :dtstart   "1997-03-10T09:00:00"
    :rrules    ["FREQ=YEARLY;INTERVAL=2;COUNT=10;BYMONTH=1,2,3"]
    :expected  (apply at "09:00:00" [1997 3 [10]]
                      (for [y [1999 2001 2003], m [1 2 3]] [y m [10]]))
    :complete? true}
   {:title     "Every third year on the 1st, 100th, and 200th day for 10 occurrences"
    :dtstart   "1997-01-01T09:00:00"
    :rrules    ["FREQ=YEARLY;INTERVAL=3;COUNT=10;BYYEARDAY=1,100,200"]
    :expected  (at "09:00:00" [1997 1 [1]] [1997 4 [10]] [1997 7 [19]]
                   [2000 1 [1]] [2000 4 [9]] [2000 7 [18]]
                   [2003 1 [1]] [2003 4 [10]] [2003 7 [19]] [2006 1 [1]])
    :complete? true}
   {:title     "Every 20th Monday of the year, forever"
    :dtstart   "1997-05-19T09:00:00"
    :rrules    ["FREQ=YEARLY;BYDAY=20MO"]
    :expected  (at "09:00:00" [1997 5 [19]] [1998 5 [18]] [1999 5 [17]])
    :complete? false}
   {:title     "Monday of week number 20, forever"
    :dtstart   "1997-05-12T09:00:00"
    :rrules    ["FREQ=YEARLY;BYWEEKNO=20;BYDAY=MO"]
    :expected  (at "09:00:00" [1997 5 [12]] [1998 5 [11]] [1999 5 [17]])
    :complete? false}
   {:title     "Every Thursday in March, forever"
    :dtstart   "1997-03-13T09:00:00"
    :rrules    ["FREQ=YEARLY;BYMONTH=3;BYDAY=TH"]
    :expected  (at "09:00:00" [1997 3 [13 20 27]] [1998 3 [5 12 19 26]]
                   [1999 3 [4 11 18 25]])
    :complete? false}
   {:title     "Every Thursday, but only during June, July, and August, forever"
    :dtstart   "1997-06-05T09:00:00"
    :rrules    ["FREQ=YEARLY;BYDAY=TH;BYMONTH=6,7,8"]
    :expected  (at "09:00:00" [1997 6 [5 12 19 26]] [1997 7 [3 10 17 24 31]] [1997 8 [7 14 21 28]]
                   [1998 6 [4 11 18 25]] [1998 7 [2 9 16 23 30]] [1998 8 [6 13 20 27]]
                   [1999 6 [3 10 17 24]] [1999 7 [1 8 15 22 29]] [1999 8 [5 12 19 26]])
    :complete? false}
   {:title     "Every Friday the 13th, forever"
    :dtstart   "1997-09-02T09:00:00"
    :exdate    ["1997-09-02T09:00:00"]
    :rrules    ["FREQ=MONTHLY;BYDAY=FR;BYMONTHDAY=13"]
    :expected  (at "09:00:00" [1998 2 [13]] [1998 3 [13]] [1998 11 [13]] [1999 8 [13]]
                   [2000 10 [13]])
    :complete? false}
   {:title     "The first Saturday that follows the first Sunday of the month, forever"
    :dtstart   "1997-09-13T09:00:00"
    :rrules    ["FREQ=MONTHLY;BYDAY=SA;BYMONTHDAY=7,8,9,10,11,12,13"]
    :expected  (at "09:00:00" [1997 9 [13]] [1997 10 [11]] [1997 11 [8]] [1997 12 [13]]
                   [1998 1 [10]] [1998 2 [7]] [1998 3 [7]] [1998 4 [11]] [1998 5 [9]]
                   [1998 6 [13]])
    :complete? false}
   {:title     "Every 4 years, the first Tuesday after a Monday in November, forever"
    :dtstart   "1996-11-05T09:00:00"
    :rrules    ["FREQ=YEARLY;INTERVAL=4;BYMONTH=11;BYDAY=TU;BYMONTHDAY=2,3,4,5,6,7,8"]
    :expected  (at "09:00:00" [1996 11 [5]] [2000 11 [7]] [2004 11 [2]])
    :complete? false}
   {:title     "The third instance into the month of one of Tuesday, Wednesday, or Thursday, for the next 3 months"
    :dtstart   "1997-09-04T09:00:00"
    :rrules    ["FREQ=MONTHLY;COUNT=3;BYDAY=TU,WE,TH;BYSETPOS=3"]
    :expected  (at "09:00:00" [1997 9 [4]] [1997 10 [7]] [1997 11 [6]])
    :complete? true}
   {:title     "The second-to-last weekday of the month"
    :dtstart   "1997-09-29T09:00:00"
    :rrules    ["FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-2"]
    :expected  (at "09:00:00" [1997 9 [29]] [1997 10 [30]] [1997 11 [27]] [1997 12 [30]]
                   [1998 1 [29]] [1998 2 [26]] [1998 3 [30]])
    :complete? false}
   {:title     "Every 3 hours from 9:00 AM to 5:00 PM on a specific day"
    :dtstart   "1997-09-02T09:00:00"
    :rrules    ["FREQ=HOURLY;INTERVAL=3;UNTIL=19970902T210000Z"]
    :expected  (times "1997-09-02" "09:00:00" "12:00:00" "15:00:00")
    :complete? true}
   {:title     "Every 15 minutes for 6 occurrences"
    :dtstart   "1997-09-02T09:00:00"
    :rrules    ["FREQ=MINUTELY;INTERVAL=15;COUNT=6"]
    :expected  (times "1997-09-02" "09:00:00" "09:15:00" "09:30:00" "09:45:00"
                      "10:00:00" "10:15:00")
    :complete? true}
   {:title     "Every hour and a half for 4 occurrences"
    :dtstart   "1997-09-02T09:00:00"
    :rrules    ["FREQ=MINUTELY;INTERVAL=90;COUNT=4"]
    :expected  (times "1997-09-02" "09:00:00" "10:30:00" "12:00:00" "13:30:00")
    :complete? true}
   {:title     "Every 20 minutes from 9:00 AM to 4:40 PM every day"
    :dtstart   "1997-09-02T09:00:00"
    :rrules    ["FREQ=DAILY;BYHOUR=9,10,11,12,13,14,15,16;BYMINUTE=0,20,40"
                "FREQ=MINUTELY;INTERVAL=20;BYHOUR=9,10,11,12,13,14,15,16"]
    :expected  (vec (for [date ["1997-09-02" "1997-09-03"]
                          h    (range 9 17)
                          m    ["00" "20" "40"]]
                      (str date "T" (pad h 2) ":" m ":00")))
    :complete? false}
   {:title     "Every other week, by WKST=MO"
    :dtstart   "1997-08-05T09:00:00"
    :rrules    ["FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=MO"]
    :expected  (at "09:00:00" [1997 8 [5 10 19 24]])
    :complete? true}
   {:title     "Every other week, by WKST=SU"
    :dtstart   "1997-08-05T09:00:00"
    :rrules    ["FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=SU"]
    :expected  (at "09:00:00" [1997 8 [5 17 19 31]])
    :complete? true}
   {:title     "An invalid date (February 30) is ignored"
    :dtstart   "2007-01-15T09:00:00"
    :rrules    ["FREQ=MONTHLY;BYMONTHDAY=15,30;COUNT=5"]
    :expected  (at "09:00:00" [2007 1 [15 30]] [2007 2 [15]] [2007 3 [15 30]])
    :complete? true}])
