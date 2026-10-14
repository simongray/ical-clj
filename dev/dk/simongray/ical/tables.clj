(ns dk.simongray.ical.tables
  "Write the table of Windows zone names in src from CLDR's
  windowsZones.xml, which dev/fetch.sh downloads.

      clojure -X:tables"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

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
