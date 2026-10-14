(ns dk.simongray.ical.platform-test
  "The platforms agree on the past: the transitions of a few awkward zones
  from 1990 to 2025, as this platform gives them, are those that the JVM
  gave when the table was made."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.ical.impl.zones :as zones]
            [dk.simongray.ical.tz :as tz]
            [dk.simongray.ical.zone-transitions :as table]))

(deftest past-transitions
  (doseq [[tzid expected] table/transitions]
    (is (= expected (zones/transitions (tz/zone tzid) table/from table/to)) tzid)))
