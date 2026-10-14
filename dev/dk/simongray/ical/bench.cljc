(ns dk.simongray.ical.bench
  "Benchmarks of parsing, writing, recurrence and time zones, timed by the
  clock of the platform.

      clojure -M:bench -m dk.simongray.ical.bench
      clojure -M:cljs release bench && node target/bench.js

  In a REPL, (run) gives the mean of each case in milliseconds."
  (:require [clojure.string :as str]
            [dk.simongray.ical :as ical]
            [dk.simongray.ical.rrule :as rrule]
            [dk.simongray.ical.tz :as tz]))

(def text
  "A calendar of 500 events in a zone, a third of them recurring."
  (str "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//bench//EN\r\n"
       (str/join (for [i (range 500)]
                   (str "BEGIN:VEVENT\r\nUID:" i "@bench\r\nDTSTAMP:20261001T090000Z\r\n"
                        "DTSTART;TZID=Europe/Copenhagen:20261012T190000\r\n"
                        "DTEND;TZID=Europe/Copenhagen:20261012T210000\r\n"
                        (when (zero? (mod i 3)) "RRULE:FREQ=WEEKLY;BYDAY=MO,WE\r\n")
                        "SUMMARY:Event number " i "\\, with a comma\r\n"
                        "DESCRIPTION:" (str/join (repeat 8 "Some longer text here. ")) "\r\n"
                        "END:VEVENT\r\n")))
       "END:VCALENDAR\r\n"))

(def cal
  "The calendar of text, parsed."
  (delay (ical/parse text)))

(def cases
  "Each case by name, as a function to time."
  (let [daily   (rrule/parse "FREQ=DAILY")
        zone    (tz/zone "Europe/Copenhagen")
        cal     @cal
        weekly  (first (ical/components cal :vevent))]
    {:parse        #(ical/parse text)
     :serialize    #(ical/serialize cal)
     :rule-1000    #(doall (take 1000 (rrule/occurrences daily "2026-01-01T10:00:00")))
     :zoned-1000   #(doall (take 1000 (ical/occurrences cal weekly)))
     :instant      #(tz/instant zone "2026-10-12T19:00:00")
     :expand-month #(ical/expand cal #inst "2026-10-01T00:00:00Z" #inst "2026-11-01T00:00:00Z")
     :free-busy    #(ical/free-busy cal #inst "2026-10-01T00:00:00Z" #inst "2026-11-01T00:00:00Z"
                                    {:uid "bench" :now #inst "2026-10-01T00:00:00Z"})}))

(defn- now-ms
  "The time of the platform's clock in milliseconds."
  []
  #?(:clj (/ (System/nanoTime) 1e6) :cljs (js/performance.now)))

(defn- mean
  "The mean time of a call of `f` in milliseconds, over at least a second
  of calls after a second of warming up."
  [f]
  (let [calls (fn [ms]
                (let [end (+ (now-ms) ms)]
                  (loop [n 0]
                    (if (< (now-ms) end)
                      (do (f) (recur (inc n)))
                      n))))]
    (calls 1000)
    (let [start (now-ms)
          n     (calls 1000)]
      (/ (- (now-ms) start) n))))

(defn run
  "The mean time of each case, or of those named `ks`, in milliseconds."
  ([]
   (run (sort (keys cases))))
  ([ks]
   (into (sorted-map) (for [k ks] [k (mean (cases k))]))))

(defn -main
  "Print the mean time of each case."
  [& _]
  (doseq [[k ms] (run)]
    (println (str (name k) ": " (/ (Math/round (* ms 1000.0)) 1000.0) " ms")))
  #?(:clj (shutdown-agents)))
