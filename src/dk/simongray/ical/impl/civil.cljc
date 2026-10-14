(ns ^:no-doc dk.simongray.ical.impl.civil
  "Dates and times of the proleptic Gregorian calendar, by arithmetic
  alone, so that the JVM and JavaScript agree.

  A day is a number of days from 1970-01-01, and a local time is a number
  of seconds from 1970-01-01T00:00:00 on the wall clock, as if it were
  UTC. Weekdays run from 0 for Sunday to 6 for Saturday.")

;; days_from_civil and civil_from_days of Howard Hinnant, in
;; chrono-Compatible Low-Level Date Algorithms
(defn days-from-civil
  "The number of days from 1970-01-01 to the date `y`-`m`-`d`."
  ^long [^long y ^long m ^long d]
  (let [y   (if (<= m 2) (dec y) y)
        era (quot (if (neg? y) (- y 399) y) 400)
        yoe (- y (* era 400))
        doy (+ (quot (+ (* 153 (if (> m 2) (- m 3) (+ m 9))) 2) 5) (dec d))
        doe (+ (* yoe 365) (quot yoe 4) (- (quot yoe 100)) doy)]
    (+ (* era 146097) doe -719468)))

(defn civil-from-days
  "The date that is `days` days after 1970-01-01, as a vector of the year,
  the month and the day."
  [^long days]
  (let [z   (+ days 719468)
        era (quot (if (neg? z) (- z 146096) z) 146097)
        doe (- z (* era 146097))
        yoe (quot (- doe
                     (quot doe 1460)
                     (- (quot doe 36524))
                     (quot doe 146096))
                  365)
        y   (+ yoe (* era 400))
        doy (- doe (+ (* 365 yoe) (quot yoe 4) (- (quot yoe 100))))
        mp  (quot (+ (* 5 doy) 2) 153)
        d   (inc (- doy (quot (+ (* 153 mp) 2) 5)))
        m   (if (< mp 10) (+ mp 3) (- mp 9))]
    [(if (<= m 2) (inc y) y) m d]))

(defn leap-year?
  "Whether the year `y` has a 29 February."
  [^long y]
  (and (zero? (mod y 4))
       (or (pos? (mod y 100)) (zero? (mod y 400)))))

(defn days-in-month
  "The number of days in the month `m` of the year `y`."
  ^long [^long y ^long m]
  (case (int m)
    2 (if (leap-year? y) 29 28)
    (4 6 9 11) 30
    31))

(defn days-in-year
  "The number of days in the year `y`."
  ^long [^long y]
  (if (leap-year? y) 366 365))

(defn weekday
  "The weekday of the day `days`, from 0 for Sunday to 6 for Saturday.
  1970-01-01 was a Thursday."
  ^long [^long days]
  (mod (+ 4 days) 7))

(defn valid-date?
  "Whether `y`-`m`-`d` is a date of the calendar."
  [y m d]
  (and (<= 1 m 12) (<= 1 d (days-in-month y m))))

;; RFC 5545, section 3.3.12: the second can be 60, for a leap second
(defn valid-time?
  "Whether `hh`:`mm`:`ss` is a time of day."
  [hh mm ss]
  (and (<= 0 hh 23) (<= 0 mm 59) (<= 0 ss 60)))

(defn local-seconds
  "The local time of `y`-`m`-`d` `hh`:`mm`:`ss`."
  [y m d hh mm ss]
  (+ (* 86400 (days-from-civil y m d)) (* 3600 hh) (* 60 mm) ss))

(defn floor-div
  "The quotient of `a` by the positive `b`, rounded down."
  ^long [^long a ^long b]
  (let [q (quot a b)]
    (if (neg? (rem a b)) (dec q) q)))

(defn civil-from-seconds
  "The local time `seconds` as a vector of the year, the month, the day,
  the hour, the minute and the second."
  [^long seconds]
  (let [days (floor-div seconds 86400)
        s    (- seconds (* days 86400))]
    (into (civil-from-days days)
          [(quot s 3600) (quot (rem s 3600) 60) (rem s 60)])))

(defn- number
  "The number in the string of digits `s`."
  [s]
  #?(:clj (Long/parseLong s) :cljs (js/parseInt s 10)))

(defn date-text?
  "Whether `v` is a date or a date-time in the form of jCal, e.g.
  2026-10-12 or 2026-10-12T19:00:00Z."
  [v]
  (and (string? v) (boolean (re-matches #"\d{4}-\d{2}-\d{2}(?:T\d{2}:\d{2}:\d{2}Z?)?" v))))

(defn seconds
  "The local time of the date or date-time `s` in the form of jCal, e.g.
  2026-10-12 or 2026-10-12T19:00:00Z, ignoring a Z."
  [s]
  (let [n #(number (subs s %1 %2))]
    (local-seconds (n 0 4) (n 5 7) (n 8 10)
                   (if (< 10 (count s)) (n 11 13) 0)
                   (if (< 10 (count s)) (n 14 16) 0)
                   (if (< 10 (count s)) (n 17 19) 0))))

(defn- pad
  "The number `n` as text of at least `width` digits."
  [n width]
  (let [s (str n)]
    (str (apply str (repeat (- width (count s)) "0")) s)))

(defn date-text
  "The local time `x` as a date in the form of jCal, e.g. 2026-10-12."
  [x]
  (let [[y m d] (civil-from-seconds x)]
    (str (pad y 4) "-" (pad m 2) "-" (pad d 2))))

(defn date-time-text
  "The local time `x` as a date-time in the form of jCal, e.g.
  2026-10-12T19:00:00, with a Z when `utc?`."
  [x utc?]
  (let [[_ _ _ hh mm ss] (civil-from-seconds x)]
    (str (date-text x) "T" (pad hh 2) ":" (pad mm 2) ":" (pad ss 2)
         (when utc? "Z"))))

;; RFC 5545, section 3.3.6: a duration's weeks and days are nominal, and
;; its hours, minutes and seconds exact
(defn duration-parts
  "The duration `s` in the form of jCal, e.g. -PT15M or P1DT2H, as a pair
  of its nominal days and its exact seconds, both negative for a negative
  duration."
  [s]
  (let [[_ sign w d h m sec] (re-matches #"([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?" s)
        n    #(if % (number %) 0)
        days (+ (* 7 (n w)) (n d))
        secs (+ (* 3600 (n h)) (* 60 (n m)) (n sec))]
    (if (= "-" sign) [(- days) (- secs)] [days secs])))

(defn offset-seconds
  "The seconds of the UTC offset `s` in the form of jCal, e.g. +02:00, or
  nil when `s` isn't one."
  [s]
  (when-let [[_ sign hh mm ss] (and (string? s) (re-matches #"([+-])(\d{2}):(\d{2})(?::(\d{2}))?" s))]
    (let [n (+ (* 3600 (number hh)) (* 60 (number mm)) (if ss (number ss) 0))]
      (if (= "-" sign) (- n) n))))

(defn offset-text
  "The offset of `n` seconds as a UTC offset in the form of jCal, e.g.
  +02:00, with seconds only when it has them."
  [n]
  (let [a (abs n)]
    (str (if (neg? n) "-" "+") (pad (quot a 3600) 2) ":" (pad (quot (rem a 3600) 60) 2)
         (when (pos? (rem a 60)) (str ":" (pad (rem a 60) 2))))))
