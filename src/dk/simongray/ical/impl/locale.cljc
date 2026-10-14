(ns ^:no-doc dk.simongray.ical.impl.locale
  "Upper and lower case that don't depend on the JVM's default locale. In
  a Turkish locale, clojure.string/lower-case turns PRIORITY into
  prıorıty, which no table knows.")

(defn lower-case
  "The string `s` in lower case, by the rules of no particular language."
  [s]
  #?(:clj  (.toLowerCase (str s) java.util.Locale/ROOT)
     :cljs (.toLowerCase (str s))))

(defn upper-case
  "The string `s` in upper case, by the rules of no particular language."
  [s]
  #?(:clj  (.toUpperCase (str s) java.util.Locale/ROOT)
     :cljs (.toUpperCase (str s))))
