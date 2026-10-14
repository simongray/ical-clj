(ns dk.simongray.ical.corpus-test
  "Real files from other implementations' tests, from dev/fetch.sh: each
  parses, and parses the same after it's written back."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [clojure.walk :as walk]
            [dk.simongray.ical :as ical]
            [dk.simongray.ical.jcal :as jcal]
            [dk.simongray.ical.test-data :as test-data]))

(defn calendars
  "The paths in dev-resources of the iCalendar files of the directories
  `dirs`."
  [& dirs]
  (for [dir   dirs
        f     (test-data/files dir)
        :when (str/ends-with? f ".ics")]
    (str dir "/" f)))

(deftest round-trips
  (doseq [path (calendars "icaljs" "icalendar")]
    (let [cs (ical/parse-all (test-data/text path))]
      (is (= cs (ical/parse-all (ical/serialize cs))) path)
      (is (= cs (mapv (comp jcal/jcal->calendar jcal/calendar->jcal) cs))
          (str path " through jCal")))))

(def coerced
  "The files of ical.js that hold invalid values, which ical.js reads as
  false or 0, where this library keeps their text."
  #{"boolean.ics" "float.ics" "integer.ics"})

(defn- without-unknown-type
  "The components `cs` without the type :unknown, which ical.js gives the
  properties it doesn't know, where RFC 5545 makes them text."
  [cs]
  (walk/postwalk #(cond-> % (and (map? %) (= :unknown (:type %))) (dissoc :type)) cs))

(deftest as-icaljs-reads-them
  (doseq [path  (calendars "icaljs")
          :let  [f (subs path (count "icaljs/"))]
          :when (not (coerced f))]
    (let [js     (test-data/json (str/replace path ".ics" ".json"))
          theirs (if (string? (first js)) [(jcal/jcal->calendar js)] (mapv jcal/jcal->calendar js))]
      (is (= (without-unknown-type (ical/parse-all (test-data/text path)))
             (without-unknown-type theirs))
          path))))
