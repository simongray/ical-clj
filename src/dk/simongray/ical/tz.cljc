(ns dk.simongray.ical.tz
  "Find the zone that a TZID names, and convert between its local
  date-times and instants.

  A zone comes from the platform's time zone database (ZoneRules on the
  JVM, Intl.DateTimeFormat in JavaScript), from a VTIMEZONE in a calendar,
  or from a fixed UTC offset. The two platforms agree as long as their
  copies of the database are the same version.

  Local date-times are strings in the form of jCal, e.g.
  \"2026-10-12T19:00:00\", and instants are insts."
  (:require [clojure.string :as str]
            [dk.simongray.ical.impl.civil :as civil]
            [dk.simongray.ical.impl.locale :as locale]
            [dk.simongray.ical.impl.windows :as windows]
            [dk.simongray.ical.impl.zones :as zones]))

(defn- platform-zone
  "The platform's zone named `s`, as the zone of the TZID `tzid`, or nil."
  [tzid s]
  (when-let [iana (zones/platform-name s)]
    {:tzid tzid :iana iana}))

;; RFC 5545, section 3.2.19: a TZID that starts with a solidus is meant to
;; be globally unique, e.g. /mozilla.org/20050126_1/Europe/Berlin, and
;; clients put a prefix of their own before the IANA name
(defn- suffixes
  "The ends of the TZID `tzid` after each of its solidi, longest first,
  when it starts with one."
  [tzid]
  (when (str/starts-with? tzid "/")
    (->> (range (count tzid))
         (filter #(= \/ (get tzid %)))
         (map #(subs tzid (inc %)))
         (remove str/blank?))))

(defn- fixed-zone
  "The zone of the fixed UTC offset `s`, e.g. +02:00 or Z, or nil."
  [s]
  (cond
    (= "Z" s)                                       {:tzid s :offset 0}
    (re-matches #"[+-]\d{2}:\d{2}(?::\d{2})?" s)   {:tzid s :offset (civil/offset-seconds s)}))

(defn- calendar-zone
  "The zone of the VTIMEZONE of the calendar `cal` with the TZID `tzid`,
  or nil."
  [cal tzid]
  (some->> (:components cal)
           (filter #(and (= :vtimezone (:name %))
                         (some (fn [p] (and (= :tzid (:name p)) (= tzid (:value p))))
                               (:properties %))))
           first
           zones/vtimezone-zone))

(defn zone
  "The zone that the TZID `tzid` names, or nil when nothing defines it.
  Given the calendar `cal`, its VTIMEZONEs can define the zone too.

  The function tries `tzid`, in order:

  1. as a zone name of the platform's database, ignoring case
  2. without its prefix, when it starts with a solidus, e.g.
     /mozilla.org/20050126_1/Europe/Berlin
  3. as a Windows zone name, e.g. W. Europe Standard Time, mapped by
     CLDR's windowsZones
  4. as the TZID of a VTIMEZONE in `cal`

  The platform comes first because a calendar's own definition is often
  stale or covers only a few years. To prefer the calendar's, pass
  :vtimezone? true in `opts`. A fixed UTC offset such as +02:00 is a zone
  too."
  ([tzid]
   (zone nil tzid {}))
  ([cal tzid]
   (zone cal tzid {}))
  ([cal tzid {:keys [vtimezone?]}]
   (when (string? tzid)
     (let [platform #(or (platform-zone tzid tzid)
                         (some (partial platform-zone tzid) (suffixes tzid))
                         (some->> (get windows/zones (locale/lower-case tzid))
                                  (platform-zone tzid))
                         (fixed-zone tzid))]
       (if vtimezone?
         (or (calendar-zone cal tzid) (platform))
         (or (platform) (calendar-zone cal tzid)))))))

(defn- ->inst
  "The inst of `t` seconds from the epoch."
  [t]
  #?(:clj (java.util.Date. (long (* 1000 t))) :cljs (js/Date. (* 1000 t))))

(defn- seconds-of
  "The whole seconds from the epoch to the inst `at`."
  [at]
  (civil/floor-div (inst-ms at) 1000))

(defn offset
  "The UTC offset of the `zone` at the inst `at`, e.g. \"+02:00\"."
  [zone at]
  (civil/offset-text (zones/offset zone (seconds-of at))))

;; RFC 5545, section 3.3.5
(defn instant
  "The inst of the local date-time `s` in the `zone`. When the clocks go
  back, a time that occurs twice means the first of them. When they go
  forward, a time that doesn't occur takes the offset from before.

      (instant (zone \"America/New_York\") \"2007-03-11T02:30:00\")
      ;; => #inst \"2007-03-11T07:30:00.000-00:00\" (3:30 EDT)"
  [zone s]
  (->inst (zones/instant zone (civil/seconds s))))

(defn local
  "The local date-time of the inst `at` in the `zone`, e.g.
  \"2026-10-12T19:00:00\"."
  [zone at]
  (civil/date-time-text (zones/local zone (seconds-of at)) false))

(def ^:private weekday-codes
  "The weekday codes of RFC 5545, from Sunday."
  ["SU" "MO" "TU" "WE" "TH" "FR" "SA"])

(defn- onset
  "The local time when the transition `tr` happens, on the clock before
  it."
  [[t from _]]
  (+ t from))

(defn- yearly-key
  "The yearly rule that the transition `tr` follows: its month, its
  weekday with its ordinal in the month (-1 for the last), its time of day
  and its offsets."
  [[_ from to :as tr]]
  (let [x                 (onset tr)
        [_ m d hh mm ss]  (civil/civil-from-seconds x)
        [y]               (civil/civil-from-seconds x)
        ordinal           (if (> (+ d 7) (civil/days-in-month y m)) -1 (inc (quot (dec d) 7)))
        wd                (civil/weekday (civil/floor-div x 86400))]
    [m (str ordinal (weekday-codes wd)) hh mm ss from to]))

(defn- kind
  "Whether the transition `tr` starts daylight time or standard time, by
  whether the offset goes up."
  [[_ from to]]
  (if (> to from) :daylight :standard))

(defn- observance
  "A STANDARD or DAYLIGHT component of the transitions `trs`, which share
  their offsets: the first one's onset as DTSTART, and the others' as
  RDATEs, or the yearly `rule`."
  [trs rule]
  (let [[_ from to :as first-tr] (first trs)
        local #(civil/date-time-text (onset %) false)]
    {:name       (kind first-tr)
     :properties (cond-> [{:name :dtstart :value (local first-tr)}
                          {:name :tzoffsetfrom :value (civil/offset-text from)}
                          {:name :tzoffsetto :value (civil/offset-text to)}]
                   rule           (conj {:name :rrule :value rule})
                   (and (not rule) (next trs))
                   (conj {:name :rdate :value (mapv local (rest trs))}))}))

(defn- yearly-rule
  "The yearly recurrence rule of the transitions that share the
  `yearly-key` k."
  [[m byday]]
  {:freq "YEARLY" :bymonth [m] :byday [byday]})

(defn- tail
  "The transitions at the end of `trs` that follow the same yearly rule
  as the last, when there are at least two of them."
  [trs]
  (let [k   (some-> (last trs) yearly-key)
        run (reverse (take-while #(= k (yearly-key %)) (reverse trs)))]
    (when (< 1 (count run)) run)))

(defn- observances
  "The STANDARD and DAYLIGHT components of the transitions `trs`: those
  of the yearly rules that the zone follows at the end, when it follows
  one for both, and the earlier transitions grouped by their offsets."
  [trs]
  (let [by-kind (group-by kind trs)
        tails   (keep #(tail (get by-kind %)) [:standard :daylight])
        ruled?  (= 2 (count tails))
        ruled   (if ruled? (set (apply concat tails)) #{})
        earlier (remove ruled trs)]
    (concat (for [[_ group] (sort-by (comp first first second)
                                     (group-by (fn [[_ from to]] [from to]) earlier))]
              (observance group nil))
            (when ruled?
              (for [run (sort-by (comp first first) tails)]
                (observance run (yearly-rule (yearly-key (first run)))))))))

(defn vtimezone
  "A VTIMEZONE with the TZID `tzid` for the platform's `zone`, covering
  the years `from` to `to`. If the zone follows yearly rules at the end of
  that span, they become RRULEs, so the definition goes on after it.

      (vtimezone (zone \"Europe/Copenhagen\") \"Europe/Copenhagen\" 2026 2027)"
  [zone tzid from to]
  (let [start (* 86400 (civil/days-from-civil (dec from) 1 1))
        end   (* 86400 (civil/days-from-civil (+ to 2) 1 1))
        trs   (zones/transitions zone start end)
        off   (zones/offset zone start)]
    {:name       :vtimezone
     :properties [{:name :tzid :value tzid}]
     :components (vec (if (seq trs)
                        (observances trs)
                        [{:name       :standard
                          :properties [{:name :dtstart :value "1970-01-01T00:00:00"}
                                       {:name :tzoffsetfrom :value (civil/offset-text off)}
                                       {:name :tzoffsetto :value (civil/offset-text off)}]}]))}))

(defn- tzid-years
  "The years of the date and date-time values of the properties with each
  TZID in the component `c` and the components inside it, as a map of TZID
  to a set of years."
  [c]
  (apply merge-with into
         (concat
          (for [p     (:properties c)
                :let  [tzid (get-in p [:params :tzid])]
                :when tzid
                v     (let [v (:value p)] (if (sequential? v) v [v]))
                :let  [v (if (map? v) (:start v) v)]
                :when (and (string? v) (re-matches #"\d{4}-.*" v))]
            {tzid #{#?(:clj (Long/parseLong (subs v 0 4)) :cljs (js/parseInt (subs v 0 4) 10))}})
          (map tzid-years (:components c)))))

;; RFC 5545, section 3.2.19: each TZID of a calendar needs a VTIMEZONE in
;; it
(defn with-timezones
  "The calendar `cal` with a VTIMEZONE added at the front for each TZID
  that it uses without defining, when the platform knows the zone (see
  vtimezone). The TZIDs that `cal` defines, and those that name no known
  zone, stay as they are."
  [cal]
  (let [defined (set (for [c     (:components cal)
                           :when (= :vtimezone (:name c))
                           p     (:properties c)
                           :when (= :tzid (:name p))]
                       (:value p)))
        added   (for [[tzid years] (sort (tzid-years cal))
                      :when        (not (defined tzid))
                      :let         [z (zone tzid)]
                      :when        (:iana z)]
                  (vtimezone z tzid (apply min years) (apply max years)))]
    (cond-> cal
      (seq added) (update :components #(into (vec added) %)))))

(comment
  (instant (zone "Europe/Copenhagen") "2026-10-12T19:00:00")
  (instant (zone "W. Europe Standard Time") "2026-10-12T19:00:00")
  (local (zone "/mozilla.org/20050126_1/Europe/Berlin") #inst "2026-10-12T17:00:00Z")
  (offset (zone "+02:00") #inst "2026-01-01")
  #_.)
