(ns dk.simongray.ical.tables
  "Write the tables in src from the files that dev/fetch.sh downloads:
  the Windows zone names from CLDR's windowsZones.xml, and the restriction
  tables of iTIP from RFC 5546. Also write the table of past zone
  transitions that the tests compare the platforms with.

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

(def rfc-5546
  "The text of RFC 5546, which holds the restriction tables of iTIP."
  "dev-resources/rfc/rfc5546.txt")

(def restrictions-target
  "The namespace that holds the restriction tables."
  "src/dk/simongray/ical/impl/restrictions.cljc")

(defn- table-rows
  "The rows of the restriction table that starts at the line `i` of
  `lines`, each a vector of its indentation, its name and its presence,
  until the next table or section."
  [lines i]
  (->> (subvec lines (inc i))
       (take-while #(not (re-find #"Constraints for a METHOD|^\d+\.\d+\.\d+\. " %)))
       (keep #(re-matches #"\s*\|( *)([A-Z][A-Z-]*)\s*\|\s*([^|]*?)\s*\|.*\|\s*" %))
       (remove #(#{"Component" "Presence"} (nth % 2)))
       (map (fn [[_ indent name presence]] [(count indent) name presence]))))

(defn restriction-tables
  "The restriction tables of RFC 5546, section 3, in the text `text`, by
  pairs of method and component name. Each is a map of :calendar, the
  presence of each component of the calendar, and :component, the
  presence of each property and component inside the scheduled component.
  The IANA and X- extensions, which may always be there, are left out."
  [text]
  (let [lines (vec (str/split-lines text))]
    (into (sorted-map)
          (for [[i line] (map-indexed vector lines)
                :let     [[_ method component] (re-find #"Constraints for a METHOD:(\w+) of a (\w+)" line)]
                :when    method
                :let     [rows (remove (fn [[_ n]] (or (= "METHOD" n) (re-find #"^(IANA|X)-" n)))
                                       (table-rows lines i))
                          kw   #(keyword (str/lower-case %))]]
            [[method (kw component)]
             {:calendar  (into (sorted-map) (for [[ind n p] rows :when (= 1 ind)] [(kw n) p]))
              :component (into (sorted-map) (for [[ind n p] rows :when (< 1 ind)] [(kw n) p]))}]))))

(defn write-restrictions!
  "Write the restriction tables to the restrictions-target namespace."
  []
  (let [tables (restriction-tables (slurp (io/file rfc-5546)))]
    (spit restrictions-target
          (str "(ns ^:no-doc dk.simongray.ical.impl.restrictions\n"
               "  \"The restriction tables of iTIP, RFC 5546, section 3: for each method\n"
               "  and component, which components and properties a message may hold,\n"
               "  and how many. A presence is \\\"1\\\", \\\"1+\\\", \\\"0\\\", \\\"0+\\\" or\n"
               "  \\\"0 or 1\\\". Made by dev/dk/simongray/ical/tables.clj; don't edit it\n"
               "  by hand.\")\n\n"
               "(def tables\n"
               "  \"The table of each pair of method and component name, as a map of\n"
               "  :calendar, the presence of each component of the calendar, and\n"
               "  :component, the presence of each property and component inside the\n"
               "  scheduled component.\"\n"
               "  {"
               (str/join "\n   "
                         (for [[k {:keys [calendar component]}] tables
                               :let [pairs (fn [m] (map (fn [[pk pv]] (str (pr-str pk) " " (pr-str pv))) m))]]
                           (str (pr-str k)
                                "\n   {:calendar  {" (str/join " " (pairs calendar)) "}"
                                "\n    :component {"
                                (str/join "\n                "
                                          (map #(str/join " " %) (partition-all 4 (pairs component))))
                                "}}")))
               "})\n"))
    (println "Wrote" (count tables) "restriction tables to" restrictions-target)))

(defn write!
  "Write the table of Windows zone names and the restriction tables of
  iTIP to their namespaces."
  [_]
  (write-restrictions!)
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
