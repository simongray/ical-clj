(ns ^:no-doc dk.simongray.ical.impl.lines
  "Content lines, by RFC 5545, section 3.1: unfold and fold them, and
  split a line into its name, its parameters and the text of its value,
  with parameter values encoded by RFC 6868.

  A parsed line is a map of :name, a lower-case keyword, :params, a map
  of lower-case keywords to strings (or vectors of strings, for a
  parameter with several values), and :text, the value as written."
  (:refer-clojure :exclude [read-line])
  (:require [clojure.string :as str]
            [dk.simongray.ical.impl.locale :as locale]))

(defn- byte-at
  "The byte at the index `i` of the `bytes`."
  ^long [bytes ^long i]
  #?(:clj (long (aget ^bytes bytes (int i))) :cljs (aget bytes i)))

;; The same line breaks as those that lines splits at: any CRs and a LF,
;; or a CR alone
(defn- fold-length
  "The length of the fold at the index `i` of the UTF-8 `bytes` of `n`
  bytes: a line break and a space or a tab, or 0."
  ^long [bytes ^long n ^long i]
  (let [ws? #(and (< % n) (let [b (byte-at bytes %)] (or (== 32 b) (== 9 b))))
        lf  (loop [k i] (if (and (< k n) (== 13 (byte-at bytes k))) (recur (inc k)) k))]
    (cond
      (and (< lf n) (== 10 (byte-at bytes lf)) (ws? (inc lf))) (- (+ lf 2) i)
      (and (== 13 (byte-at bytes i)) (ws? (inc i)))           2
      :else                                                   0)))

;; RFC 5545, section 3.1: a fold can split a character of UTF-8, so the
;; folds of bytes are removed before they're decoded. The bytes between
;; folds are copied as runs.
(defn- decode
  "The text of the UTF-8 `bytes` without their folds."
  [bytes]
  (let [n     #?(:clj (alength ^bytes bytes) :cljs (.-length bytes))
        out   #?(:clj (byte-array n) :cljs (js/Uint8Array. n))
        copy! (fn [from to length]
                #?(:clj  (System/arraycopy bytes (int from) out (int to) (int length))
                   :cljs (.set out (.subarray bytes from (+ from length)) to)))]
    (loop [i 0, from 0, j 0]
      (if (< i n)
        (let [b    (byte-at bytes i)
              fold (if (or (== 10 b) (== 13 b)) (fold-length bytes n i) 0)]
          (if (pos? fold)
            (do (copy! from j (- i from))
                (recur (+ i fold) (+ i fold) (+ j (- i from))))
            (recur (inc i) from j)))
        (let [length (+ j (- n from))]
          (copy! from j (- n from))
          #?(:clj  (String. ^bytes out 0 (int length) "UTF-8")
             :cljs (.decode (js/TextDecoder. "utf-8") (.subarray out 0 length))))))))

(defn text
  "The text of `x`: a string as it is, the UTF-8 of a byte array (or in
  JavaScript a Uint8Array) decoded without its folds, or else the string
  of `x`."
  [x]
  (cond
    (string? x)                                           x
    #?(:clj (bytes? x) :cljs (instance? js/Uint8Array x)) (decode x)
    :else                                                 (str x)))

;; RFC 5545, section 3.1: a line break followed by a space or a tab is a
;; fold. Bare LF and CR are taken as line breaks too, since files use them,
;; as are CRs before a LF. A byte order mark is left out, also at the start
;; of a calendar that was appended to another.
(defn lines
  "The lines of the unfolded text `s`, each a pair of its number (from 1)
  and its text, without the empty ones. A line's number is that of its
  first line in `s` before unfolding."
  [s]
  (let [physical (str/split s #"\r*\n|\r" -1)]
    (loop [i 0, number 1, acc (transient [])]
      (if (< i (count physical))
        (let [line (cond-> (nth physical i)
                     (str/starts-with? (nth physical i) "﻿") (subs 1))
              ;; the folded lines that follow this one
              folds (count (take-while #(re-find #"^[ \t]" %)
                                       (subvec physical (inc i))))
              text (apply str line (map #(subs % 1)
                                        (subvec physical (inc i) (+ i 1 folds))))]
          (recur (+ i 1 folds)
                 (+ number 1 folds)
                 (cond-> acc (seq text) (conj! [number text]))))
        (persistent! acc)))))

;; RFC 6868, section 3
(defn decode-param
  "The parameter value `s` with ^n, ^^ and ^' decoded. A ^ before any
  other character stays as it is."
  [s]
  (if (str/includes? s "^")
    (str/replace s #"\^([n^'])" (fn [[_ c]]
                                  (case c
                                    "n" "\n"
                                    "^" "^"
                                    "'" "\"")))
    s))

;; RFC 6868, section 3
(defn encode-param
  "The parameter value `s` with ^, newlines and double quotes encoded."
  [s]
  (if (re-find #"[\^\n\r\"]" s)
    (str/replace s #"\^|\r\n|\n|\r|\"" (fn [m]
                                       (case m
                                         "^"  "^^"
                                         "\"" "^'"
                                         "^n")))
    s))

;; A quoted value can't hold a quote, but some clients write one, e.g.
;; CN="John "JJ" Doe", so what follows a closing quote belongs to the value
;; until the next comma, semicolon or colon outside quotes
(defn- scan-param-value
  "The end in `line` of the parameter value that starts at `i`: at the
  next comma, semicolon or colon, outside quotes when it's quoted."
  [line i]
  (let [n (count line)]
    (if (= \" (get line i))
      (loop [j (inc i), quoted? true]
        (let [c (get line j)]
          (cond
            (>= j n)                              n
            (= \" c)                              (recur (inc j) (not quoted?))
            (and (not quoted?) (#{\, \; \:} c))   j
            :else                                 (recur (inc j) quoted?))))
      (loop [j i]
        (if (and (< j n) (not (#{\, \; \:} (get line j))))
          (recur (inc j))
          j)))))

(defn- unquote-value
  "The parameter value `s` without its quotes, decoded by RFC 6868."
  [s]
  (decode-param (if (and (str/starts-with? s "\"") (str/ends-with? s "\"")
                         (< 1 (count s)))
                  (subs s 1 (dec (count s)))
                  (str/replace s "\"" ""))))

(defn- delimiter
  "The index of the first of the characters `cs` in `line` from `i`, or the
  length of `line`."
  [line cs i]
  (loop [j i]
    (if (or (>= j (count line)) (contains? cs (get line j)))
      j
      (recur (inc j)))))

(defn- scan-param
  "The parameter that starts at `i` in `line`, as a vector of its name, its
  values and the index after it. A parameter without = has no values."
  [line i]
  (let [eq   (delimiter line #{\= \; \:} i)
        name (subs line i eq)]
    (if (not= \= (get line eq))
      [name nil eq]
      (loop [j (inc eq), values []]
        (let [end    (scan-param-value line j)
              values (conj values (unquote-value (subs line j end)))]
          (if (= \, (get line end))
            (recur (inc end) values)
            [name values end]))))))

;; An array map keeps its order, which a hash map of more than eight
;; entries doesn't
(defn- param-map
  "The parameters `pairs`, each a pair of a name and its values, as a map
  in their order. A name's values are a string when there's one and a
  vector when there are several, those of a repeated name included."
  [pairs]
  (let [values (reduce (fn [m [k vs]] (update m k (fnil into []) vs)) {} pairs)]
    (apply array-map (mapcat (fn [k]
                               (let [vs (values k)]
                                 [k (if (= 1 (count vs)) (first vs) vs)]))
                             (distinct (map first pairs))))))

(defn ->keyword
  "The name `s` of a component, a property or a parameter as a lower-case
  keyword."
  [s]
  (keyword (locale/lower-case (str/trim s))))

;; a name with a slash, which a BEGIN line or a parameter can have, reads
;; as a keyword with a namespace
(defn keyword-text
  "The text of the keyword `k` of a name, with its namespace."
  [k]
  (subs (str k) 1))

;; RFC 5545, section 3.1: contentline = name *(";" param) ":" value, where
;; a name is an iana-token or an x-name, of letters, digits and dashes.
;; vCard (RFC 6350, section 3.3) puts a group and a dot before it.
(defn read-line
  "The content line `line` as a map of :name, :params and :text, or nil
  when it isn't one: its name isn't a name, or no colon follows its name
  and parameters. A parameter without a value is left out."
  [line]
  (let [end (delimiter line #{\; \:} 0)]
    (when (re-matches #"(?:[A-Za-z0-9-]+\.)?[A-Za-z0-9-]+" (subs line 0 end))
      (loop [i end, params []]
        (case (get line i)
          \: {:name   (->keyword (subs line 0 end))
              :params (param-map params)
              :text   (subs line (inc i))}
          \; (let [[pname values j] (scan-param line (inc i))]
               (if (or (str/blank? pname) (nil? values))
                 (recur j params)
                 (recur j (conj params [(->keyword pname) values]))))
          nil)))))

;; RFC 5545, section 3.2: a value with a colon, a semicolon or a comma is
;; quoted
(defn- write-param-value
  "The parameter value `s` as written, encoded and quoted when it needs to
  be."
  [s]
  (let [s (encode-param (str s))]
    (if (re-find #"[:;,]" s) (str "\"" s "\"") s)))

(defn write-params
  "The text of the parameters `params`, each after a semicolon."
  [params]
  (apply str (for [[k v] params]
               (str ";" (locale/upper-case (keyword-text k)) "="
                    (if (sequential? v)
                      (str/join "," (map write-param-value v))
                      (write-param-value v))))))

;; A surrogate on its own becomes U+FFFD, of three octets
(defn- utf-8-length
  "The number of octets that the UTF-16 code unit `c` takes in UTF-8, at
  most, when it isn't half of a surrogate pair."
  ^long [c]
  (let [n #?(:clj (int c) :cljs (.charCodeAt c 0))]
    (cond
      (< n 0x80)  1
      (< n 0x800) 2
      :else       3)))

(defn- high-surrogate?
  "Whether the UTF-16 code unit `c` is the first half of a surrogate pair."
  [c]
  (<= 0xD800 #?(:clj (int c) :cljs (.charCodeAt c 0)) 0xDBFF))

(defn- fold-point
  "The index in `line` at which to break it, from `start`, so that the
  part takes at most `octets` octets of UTF-8. The break never splits a
  surrogate pair or a backslash escape."
  ^long [line ^long start ^long octets]
  (loop [i start, used 0]
    (if (>= i (count line))
      i
      (let [c    (get line i)
            pair (and (high-surrogate? c) (< (inc i) (count line)))
            n    (if pair 4 (utf-8-length c))
            next (+ i (if pair 2 1))]
        (cond
          (> (+ used n) octets) i
          ;; keep a backslash with the character it escapes
          (and (= \\ c) (< next (count line))
               (> (+ used n (utf-8-length (get line next))) octets)) i
          :else (recur next (+ used n)))))))

;; RFC 5545, section 3.1: lines are at most 75 octets, without the line
;; break, and a continuation line starts with a space
(defn fold
  "The content line `line` folded into lines of at most 75 octets, each
  ending with CRLF."
  [line]
  (loop [start 0, octets 75, acc (transient [])]
    (let [end (fold-point line start octets)]
      (if (>= end (count line))
        (str (str/join "\r\n " (persistent! (conj! acc (subs line start))))
             "\r\n")
        (recur end 74 (conj! acc (subs line start end)))))))

;; RFC 5545, section 3.1: a value can't hold a control character, and a
;; line break in one that isn't escaped, e.g. in a URI that came from
;; user input, would start a property of its own
(defn write-line
  "The content line of the property named `k` with the `params` and the
  value text `text`, folded, without line breaks."
  [k params text]
  (fold (str/replace (str (locale/upper-case (keyword-text k)) (write-params params) ":" text)
                     #"[\r\n]" "")))

(comment
  (lines (str "BEGIN:VEVENT\r\nSUMMARY:A long\r\n  summary\r\n"
              "END:VEVENT\r\n"))
  (read-line "ATTENDEE;CN=\"Gray, Simon\";PARTSTAT=ACCEPTED:mailto:s@x.dk")
  (read-line "X-A;X-B=a,\"b,c\";X-C=^'q^':v")
  (fold (apply str "DESCRIPTION:" (repeat 100 "ø")))
  #_.)
