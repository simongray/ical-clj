(ns dk.simongray.ical.test-data
  "The files that dev/fetch.sh puts in dev-resources, read the same way on
  the JVM and in Node. Each is nil when it isn't there, and the tests of
  its data then check nothing."
  (:refer-clojure :exclude [bytes])
  (:require #?(:cljs ["fs" :as fs])
            #?(:clj [clojure.data.json :as json])
            #?(:clj [clojure.java.io :as io])
            [clojure.string :as str]))

(defn text
  "The text of the file at `path` in dev-resources, or nil."
  [path]
  (let [path (str "dev-resources/" path)]
    #?(:clj  (let [f (io/file path)]
               (when (.exists f) (slurp f :encoding "UTF-8")))
       :cljs (when (fs/existsSync path)
               (fs/readFileSync path "utf8")))))

(defn bytes
  "The bytes of the file at `path` in dev-resources, a byte array or a
  Node Buffer, or nil."
  [path]
  (let [path (str "dev-resources/" path)]
    #?(:clj  (let [f (io/file path)]
               (when (.exists f) (java.nio.file.Files/readAllBytes (.toPath f))))
       :cljs (when (fs/existsSync path)
               (fs/readFileSync path)))))

(defn json
  "The data of the JSON file at `path` in dev-resources, with string keys,
  or nil."
  [path]
  (when-let [s (text path)]
    #?(:clj  (json/read-str s)
       :cljs (js->clj (js/JSON.parse s)))))

(defn files
  "The names of the files in the directory `dir` of dev-resources, sorted,
  or nil."
  [dir]
  (let [path (str "dev-resources/" dir)]
    #?(:clj  (let [f (io/file path)]
               (when (.isDirectory f) (sort (.list f))))
       :cljs (when (fs/existsSync path)
               (sort (js->clj (fs/readdirSync path)))))))

(defn libical-cases
  "The cases of a recurrence test file of libical, `s`, each a map of
  :rrule, :dtstart, :start-at (when it has one), :instances (or :unimplemented
  true) and :comment, the lines above it that start with #."
  [s]
  (for [block (str/split s #"\n\s*\n")
        :let  [lines (str/split-lines block)
               kvs   (into {} (for [line  lines
                                    :let  [[_ k v] (re-matches #"([A-Z-]+):(.*)" line)]
                                    :when k]
                                [(keyword (str/lower-case k)) (str/trim v)]))]
        :when (:rrule kvs)]
    (cond-> {:rrule   (:rrule kvs)
             :dtstart (:dtstart kvs)
             :comment (str/join " " (keep #(second (re-matches #"#\s*(.*)" %)) lines))}
      (:start-at kvs)                                (assoc :start-at (:start-at kvs))
      (str/includes? (:instances kvs) "UNIMPLEMENTED") (assoc :unimplemented true)
      (not (str/includes? (:instances kvs) "UNIMPLEMENTED"))
      (assoc :instances (str/split (:instances kvs) #",")))))
