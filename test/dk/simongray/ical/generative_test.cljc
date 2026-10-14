(ns dk.simongray.ical.generative-test
  "What RFC 5545 promises, checked on random calendars."
  (:require [clojure.string :as str]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [dk.simongray.ical :as ical]
            [dk.simongray.ical.rrule :as rrule]
            [dk.simongray.ical.tz :as tz]))

(def text
  "Text with the characters that content lines escape or fold around."
  (gen/fmap str/join (gen/vector (gen/elements ["a" "Z" " " "," ";" ":" "\\" "\n" "\""
                                               "^" "ø" "€" "😀" "日"])
                                 0 120)))

(defn utf-8-length
  "The number of octets of the string `s` in UTF-8."
  [s]
  #?(:clj  (count (.getBytes ^String s "UTF-8"))
     :cljs (.-length (.encode (js/TextEncoder.) s))))

(defspec text-and-parameters-round-trip 200
  (prop/for-all [summary text
                 param   (gen/such-that seq text)]
    (let [c {:name       :vevent
             :properties [{:name :summary :params {:x-note param} :value summary}
                          {:name :categories :value [summary "b"]}]}]
      (= c (ical/parse (ical/serialize c))))))

(defspec folded-lines-are-short 200
  (prop/for-all [summary text]
    (every? #(<= (utf-8-length %) 75)
            (str/split-lines (ical/serialize {:name       :vevent
                                              :properties [{:name :summary :value summary}]})))))

(def rules
  "Recurrence rules of a few parts."
  (gen/let [freq  (gen/elements ["DAILY" "WEEKLY" "MONTHLY" "YEARLY"])
            parts (gen/map (gen/elements [:interval :bymonth :bymonthday :byday :bysetpos])
                           gen/small-integer {:max-elements 3})]
    (into {:freq freq}
          (for [[k n] parts]
            [k (case k
                 :interval   (inc (abs n))
                 :bymonth    [(inc (mod n 12))]
                 :bymonthday [(if (zero? n) 1 (max -31 (min 31 n)))]
                 :byday      [(["SU" "MO" "TU" "WE" "TH" "FR" "SA"] (mod n 7))]
                 :bysetpos   [(if (zero? n) 1 (max -3 (min 3 n)))])]))))

(defspec instances-come-in-order 100
  (prop/for-all [rule rules]
    (let [xs (take 30 (rrule/occurrences rule "2026-01-15T10:00:00"))]
      (and (= "2026-01-15T10:00:00" (first xs))
           (= xs (sort xs))
           (= xs (distinct xs))))))

(defspec local-times-come-back 200
  (prop/for-all [day  (gen/choose 0 30000)
                 secs (gen/choose 0 86399)]
    (let [z     (tz/zone "Europe/Copenhagen")
          at    #?(:clj  (java.util.Date. (* 1000 (+ (* 86400 day) secs)))
                   :cljs (js/Date. (* 1000 (+ (* 86400 day) secs))))
          local (tz/local z at)]
      (= local (tz/local z (tz/instant z local))))))
