(ns ^:no-doc dk.simongray.ical.impl.locale
  "Upper and lower case of the ASCII letters only, which names and the
  values of enumerations are made of. clojure.string/lower-case depends
  on the JVM's default locale, so that in a Turkish one PRIORITY becomes
  prıorıty, which no table knows, and full case conversion doesn't come
  back: ß in upper case is SS."
  (:require [clojure.string :as str]))

(defn- ascii?
  "Whether the string `s` holds only ASCII characters."
  [^String s]
  #?(:clj  (loop [i 0]
             (cond
               (= i (.length s))           true
               (< 127 (int (.charAt s i))) false
               :else                       (recur (inc i))))
     :cljs (not (re-find #"[^\x00-\x7F]" s))))

(defn lower-case
  "The string `s` with its ASCII letters in lower case."
  [s]
  (let [s (str s)]
    (if (ascii? s)
      #?(:clj (.toLowerCase s java.util.Locale/ROOT) :cljs (.toLowerCase s))
      (str/replace s #"[A-Z]+" #(lower-case %)))))

(defn upper-case
  "The string `s` with its ASCII letters in upper case."
  [s]
  (let [s (str s)]
    (if (ascii? s)
      #?(:clj (.toUpperCase s java.util.Locale/ROOT) :cljs (.toUpperCase s))
      (str/replace s #"[a-z]+" #(upper-case %)))))
