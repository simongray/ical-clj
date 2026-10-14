(ns dk.simongray.ical.tables
  "Write the table of Windows zone names in src from CLDR's
  windowsZones.xml, which dev/fetch.sh downloads, and the table of past
  zone transitions that the tests compare the platforms with.

      clojure -X:tables
      clojure -X:tables dk.simongray.ical.tables/write-transitions!"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [dk.simongray.ical.impl.civil :as civil]
            [dk.simongray.ical.tz :as tz]
            [dk.simongray.ical.impl.zones :as zones]))

(def source
  "The CLDR file that the table is made from."
  "dev-resources/cldr/windowsZones.xml")

(def target
  "The namespace that holds the table."
  "src/dk/simongray/ical/impl/windows.cljc")

(defn mappings
  "The default IANA zone of each Windows zone in the text `xml` of
  windowsZones.xml, by its Windows name and by the display name in the
  comment above it, e.g. (UTC+01:00) Amsterdam, Berlin, Bern, Rome,
  Stockholm, Vienna. Each name is in lower case."
  [xml]
  (->> (re-seq #"<!-- (.+?) -->\s*<mapZone other=\"([^\"]+)\" territory=\"001\" type=\"([^\" ]+)" xml)
       (mapcat (fn [[_ display windows iana]]
                 [[(str/lower-case windows) iana]
                  [(str/lower-case display) iana]]))
       (into (sorted-map))))

(defn write!
  "Write the table to the target namespace."
  [_]
  (let [table (mappings (slurp (io/file source)))]
    (spit target
          (str "(ns ^:no-doc dk.simongray.ical.impl.windows\n"
               "  \"The zone names of Windows, e.g. W. Europe Standard Time, and their\n"
               "  display names, with the IANA zone that CLDR's windowsZones.xml gives\n"
               "  each by default. Made by dev/dk/simongray/ical/tables.clj; don't edit\n"
               "  it by hand.\")\n\n"
               "(def zones\n"
               "  \"The IANA zone of each Windows name, by the name in lower case.\"\n"
               "  {"
               (str/join "\n   " (for [[k v] table] (str (pr-str k) " " (pr-str v))))
               "})\n"))
    (println "Wrote" (count table) "names to" target)))

(def zone-names
  "Zones whose past has the quirks of the time zone database: southern
  summers, half and quarter hours, negative summer time, abolished summer
  time, changed standard offsets and a day skipped at the date line."
  ["Europe/Copenhagen" "America/New_York" "Australia/Sydney" "Asia/Tokyo"
   "Asia/Kolkata" "Asia/Kathmandu" "Australia/Lord_Howe" "Europe/Dublin"
   "Africa/Casablanca" "America/Sao_Paulo" "Asia/Tehran" "Europe/Moscow"
   "Pacific/Apia" "America/Caracas" "Pacific/Chatham" "America/Santiago"])

(def transitions-target
  "The test namespace that holds the transitions."
  "test/dk/simongray/ical/zone_transitions.cljc")

(defn write-transitions!
  "Write the transitions of the zone-names from 1990 to 2025, as the JVM
  gives them, to the transitions-target."
  [_]
  (let [from  (* 86400 (civil/days-from-civil 1990 1 1))
        to    (* 86400 (civil/days-from-civil 2025 1 1))
        table (into (sorted-map)
                    (for [z zone-names]
                      [z (zones/transitions (tz/zone z) from to)]))]
    (spit transitions-target
          (str "(ns dk.simongray.ical.zone-transitions\n"
               "  \"The transitions of a few zones from 1990 to 2025, each a vector of\n"
               "  its instant, the offset before and the offset after, in seconds, as\n"
               "  the JVM gives them. The past no longer changes, so every platform\n"
               "  should give the same. Made by dev/dk/simongray/ical/tables.clj.\")\n\n"
               "(def from\n  \"The start of the span, 1990-01-01T00:00:00Z.\"\n  " from ")\n\n"
               "(def to\n  \"The end of the span, 2025-01-01T00:00:00Z.\"\n  " to ")\n\n"
               "(def transitions\n"
               "  \"The transitions of each zone, by its name.\"\n"
               "  {"
               (str/join "\n   " (for [[k v] table]
                                   (str (pr-str k) "\n   ["
                                        (str/join "\n    " (for [row (partition-all 4 v)]
                                                             (str/join " " (map pr-str row))))
                                        "]")))
               "})\n"))
    (println "Wrote the transitions of" (count table) "zones to" transitions-target)))
