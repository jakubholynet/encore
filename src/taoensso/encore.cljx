(ns taoensso.encore
  "The utils you want, in the package you deserve™.
  Subset of the commonest Ensso utils w/o external dependencies."
  {:author "Peter Taoussanis"}
  #+clj  (:require [clojure.string      :as str]
                   [clojure.java.io     :as io]
                   ;; [clojure.core.async  :as async]
                   [clojure.edn         :as edn])
  ;; #+clj  (:import [org.apache.commons.codec.binary Base64])
  ;;;
  #+cljs (:require [clojure.string    :as str]
                   ;; [cljs.core.async   :as async]
                   [cljs.reader       :as edn]
                   ;;[goog.crypt.base64 :as base64]
                   [goog.string.StringBuffer])
  #+cljs (:require-macros [taoensso.encore :as encore-macros]))

;;;; Core

(defn name-with-attrs
  "Stolen from `clojure.tools.macro`.
  Handles optional docstrings & attr maps for a macro def's name."
  [name macro-args]
  (let [[docstring macro-args] (if (string? (first macro-args))
                                 [(first macro-args) (next macro-args)]
                                 [nil macro-args])
        [attr macro-args] (if (map? (first macro-args))
                            [(first macro-args) (next macro-args)]
                            [{} macro-args])
        attr (if docstring   (assoc attr :doc docstring) attr)
    attr (if (meta name) (conj (meta name) attr)     attr)]
    [(with-meta name attr) macro-args]))

(defmacro defonce*
  "Like `clojure.core/defonce` but supports optional docstring and attributes
  map for name symbol."
  {:arglists '([name expr])}
  [name & sigs]
  (let [[name [expr]] (name-with-attrs name sigs)]
    `(clojure.core/defonce ~name ~expr)))

(defmacro declare-remote
  "Declares the given ns-qualified names, preserving symbol metadata. Useful for
  circular dependencies."
  [& names]
  (let [original-ns (str *ns*)]
    `(do ~@(map (fn [n]
                  (let [ns (namespace n)
                        v  (name n)
                        m  (meta n)]
                    `(do (in-ns  '~(symbol ns))
                         (declare ~(with-meta (symbol v) m))))) names)
         (in-ns '~(symbol original-ns)))))

(defmacro defalias
  "Defines an alias for a var, preserving metadata. Adapted from
  clojure.contrib/def.clj, Ref. http://goo.gl/xpjeH"
  [name target & [doc]]
  `(let [^clojure.lang.Var v# (var ~target)]
     (alter-meta! (def ~name (.getRawRoot v#))
                  #(merge % (apply dissoc (meta v#) [:column :line :file :test :name])
                     (when-let [doc# ~doc] {:doc doc#})))
     (var ~name)))

(defmacro cond-throw "Like `cond` but throws on no-match like `case`, `condp`."
  [& clauses] `(cond ~@clauses :else (throw (ex-info "No matching clause" {}))))

(comment (cond false "false") (cond-throw false "false"))

(defmacro doto-cond "Diabolical cross between `doto`, `cond->` and `as->`."
  [[name x] & clauses]
  (assert (even? (count clauses)))
  (let [g (gensym)
        pstep (fn [[test-expr step]] `(when-let [~name ~test-expr]
                                       (-> ~g ~step)))]
    `(let [~g ~x]
       ~@(map pstep (partition 2 clauses))
       ~g)))

(defmacro case-eval
  "Like `case` but evaluates test constants for their compile-time value."
  [e & clauses]
  (let [;; Don't evaluate default expression!
        default (when (odd? (count clauses)) (last clauses))
        clauses (if default (butlast clauses) clauses)]
    `(case ~e
       ~@(map-indexed (fn [i# form#] (if (even? i#) (eval form#) form#))
                      clauses)
       ~(when default default))))

(defmacro if-lets
  "Like `if-let` but binds multiple values iff all tests are true."
  ([bindings then] `(if-lets ~bindings ~then nil))
  ([bindings then else]
     (let [[b1 b2 & bnext] bindings]
       (if bnext
         `(if-let [~b1 ~b2] (if-lets ~(vec bnext) ~then ~else) ~else)
         `(if-let [~b1 ~b2] ~then ~else)))))

(comment (if-lets [a :a]  a)
         (if-lets [a nil] a)
         (if-lets [a :a b :b]  [a b])
         (if-lets [a :a b nil] [a b]))

(defmacro when-lets
  "Like `when-let` but binds multiple values iff all tests are true."
  [bindings & body]
  (let [[b1 b2 & bnext] bindings]
    (if bnext
      `(when-let [~b1 ~b2] (when-lets ~(vec bnext) ~@body))
      `(when-let [~b1 ~b2] ~@body))))

(comment (when-lets [a :a b nil] "foo"))

(defn nnil? [x] (not (nil? x)))

;;;; Coercions

(defn as-bool
  "Returns x as a unambiguous Boolean, or nil on failure. Requires more
  explicit truthiness than (boolean x)."
  [x] (cond (or (true? x) (false? x)) x
            (or (= x "false") (= x "FALSE") (= x "0") (= x 0)) false
            (or (= x "true")  (= x "TRUE")  (= x "1") (= x 1)) true
            :else nil))

(defn as-int "Returns x as Long (or JavaScript integer), or nil on failure."
  [x]
  #+clj
  (cond (number? x) (long x)
        (string? x) (try (Long/parseLong x)
                         (catch NumberFormatException _
                           (try (long (Float/parseFloat x))
                                (catch NumberFormatException _ nil))))
        :else       nil)

  #+cljs
  (cond (number? x) (long x)
        (string? x) (let [x (js/parseInt x)]
                      (when-not (js/isNaN x) x))
        :else        nil))

(defn as-float "Returns x as Double (or JavaScript float), or nil on failure."
  [x]
  #+clj
  (cond (number? x) (double x)
        (string? x) (try (Double/parseDouble x)
                         (catch NumberFormatException _ nil))
        :else       nil)

  #+cljs
  (cond (number? x) (double x)
        (string? x) (let [x (js/parseFloat x)]
                      (when-not (js/isNan x) x))
        :else       nil))

;;;; Keywords

(defn stringy? [x] (or (keyword? x) (string? x)))
(defn fq-name "Like `name` but includes namespace in string when present."
  [x] (if (string? x) x
          (let [n (name x)]
            (if-let [ns (namespace x)] (str ns "/" n) n))))

(comment (map fq-name ["foo" :foo :foo.bar/baz]))

(defn explode-keyword [k] (str/split (fq-name k) #"[\./]"))
(comment (explode-keyword :foo.bar/baz))

(defn merge-keywords [ks & [as-ns?]]
  (let [parts (->> ks (filterv identity) (mapv explode-keyword) (reduce into []))]
    (when-not (empty? parts)
      (if as-ns? ; Don't terminate with /
        (keyword (str/join "." parts))
        (let [ppop (pop parts)]
          (keyword (when-not (empty? ppop) (str/join "." ppop))
                   (peek parts)))))))

(comment (merge-keywords [:foo.bar nil :baz.qux/end nil])
         (merge-keywords [:foo.bar nil :baz.qux/end nil] true)
         (merge-keywords [:a.b.c "d.e/k"])
         (merge-keywords [:a.b.c :d.e/k])
         (merge-keywords [nil :k])
         (merge-keywords [nil]))

;;;; Bytes

#+clj
(do
  (def ^:const bytes-class (Class/forName "[B"))
  (defn bytes? [x] (instance? bytes-class x))
  (defn ba= [^bytes x ^bytes y] (java.util.Arrays/equals x y))

  (defn ba-concat ^bytes [^bytes ba1 ^bytes ba2]
    (let [s1  (alength ba1)
          s2  (alength ba2)
          out (byte-array (+ s1 s2))]
      (System/arraycopy ba1 0 out 0  s1)
      (System/arraycopy ba2 0 out s1 s2)
      out))

  (defn ba-split [^bytes ba ^Integer idx]
    (let [s (alength ba)]
      (when (> s idx)
        [(java.util.Arrays/copyOf      ba idx)
         (java.util.Arrays/copyOfRange ba idx s)])))

  (comment (String. (ba-concat (.getBytes "foo") (.getBytes "bar")))
           (let [[x y] (ba-split (.getBytes "foobar") 5)]
             [(String. x) (String. y)])))

;;;; Types

#+clj (defn throwable? [x] (instance? Throwable x))
#+clj (defn exception? [x] (instance? Exception x))
(defn error? [x]
  #+clj  (exception? x)
  #+cljs (or (ex-data x) (instance? js/Error x)))

;; (defn- chan? [x]
;;   #+clj  (instance? clojure.core.async.impl.channels.ManyToManyChannel x)
;;   #+cljs (instance? cljs.core.async.impl.channels.ManyToManyChannel    x))

;;;; Math

(defn round
  [x & [type nplaces]]
  (let [modifier (when nplaces (Math/pow 10.0 nplaces))
        x* (if-not modifier x (* x modifier))
        rounded
        (case (or type :round)
          ;;; Note same API for both #+clj and #+cljs:
          :round (Math/round (double x*))        ; Round to nearest int or nplaces
          :floor (long (Math/floor (double x*))) ; Round down to -inf
          :ceil  (long (Math/ceil  (double x*))) ; Round up to +inf
          :trunc (long x*)                       ; Round up/down toward zero
          (throw (ex-info "Unknown round type" {:type type})))]
    (if-not modifier rounded
      (/ rounded modifier))))

(defn round2 "Optimized common case." [x] (/ (Math/round (* x 1000.0)) 1000.0))

(comment
  (round -1.5 :floor)
  (round -1.5 :trunc)
  (round 1.1234567 :floor 5)
  (round 1.1234567 :round 5))

(defn uuid-str
  "Returns a UUIDv4 string of form \"xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx\",
  Ref. http://www.ietf.org/rfc/rfc4122.txt,
       https://gist.github.com/franks42/4159427"
  []
  #+clj (str (java.util.UUID/randomUUID))
  #+cljs
  (let [fs (fn [n] (apply str (repeatedly n (fn [] (.toString (rand-int 16) 16)))))
        g  (fn [] (.toString (bit-or 0x8 (bit-and 0x3 (rand-int 15))) 16))
        sb (.append (goog.string.StringBuffer.)
             (fs 8) "-" (fs 4) "-4" (fs 3) "-" (g) (fs 3) "-" (fs 12))]
    ;;(UUID. sb) ; Equality fails on roundtrips
    (.toString sb)))

;;;; Date+time

(defn now-udt []
  #+clj  (System/currentTimeMillis)
  #+cljs (.valueOf (js/Date.)))

(defn secs->ms [secs] (*    secs  1000))
(defn ms->secs [ms]   (quot ms    1000))
(defn ms
  "Returns number of milliseconds in period defined by given args."
  [& {:as opts :keys [years months weeks days hours mins secs msecs ms]}]
  {:pre [(every? #{:years :months :weeks :days :hours :mins :secs :msecs :ms}
                 (keys opts))]}
  (round
   (+ (if years  (* years  1000 60 60 24 365)   0)
      (if months (* months 1000 60 60 24 29.53) 0)
      (if weeks  (* weeks  1000 60 60 24 7)     0)
      (if days   (* days   1000 60 60 24)       0)
      (if hours  (* hours  1000 60 60)          0)
      (if mins   (* mins   1000 60)             0)
      (if secs   (* secs   1000)                0)
      (if msecs  msecs                          0)
      (if ms     ms                             0))))

(def secs (comp ms->secs ms))
(comment (ms   :years 88 :months 3 :days 33)
         (secs :years 88 :months 3 :days 33))

;;;; Collections

#+clj (defn queue? [x] (instance? clojure.lang.PersistentQueue x))
#+clj
(defn queue "Returns a PersistentQueue containing the args."
  [& items]
  (if-not items clojure.lang.PersistentQueue/EMPTY
    (into clojure.lang.PersistentQueue/EMPTY items)))

(def seq-kvs
  "(seq     {:a :A}) => ([:a :A])
   (seq-kvs {:a :A}) => (:a :A)"
  (partial reduce concat))

(comment (seq-kvs {:a :A :b :B}))

(defn mapply
  "Like `apply` but assumes last arg is a map whose elements should be applied
  to `f` as an unpaired seq:
    (mapply (fn [x & {:keys [y z]}] (str x y z)) 1 {:y 2 :z 3})
      where fn will receive args as: `(1 :y 2 :z 3)`."
  [f & args]
  (apply f (apply concat (butlast args) (last args))))

(defn map-kvs [kf vf m]
  (when m
    (let [kf (if-not (identical? kf :keywordize) kf (fn [k _] (keyword k)))
          vf (if-not (identical? vf :keywordize) vf (fn [_ v] (keyword v)))]
      (persistent! (reduce-kv (fn [m k v] (assoc! m (if kf (kf k v) k)
                                                   (if vf (vf v v) v)))
                              (transient {}) (or m {}))))))

(defn map-keys [f m] (map-kvs (fn [k _] (f k)) nil m))
(defn map-vals [f m] (map-kvs nil (fn [_ v] (f v)) m))

(defn filter-kvs [predk predv m]
  (when m
    (reduce-kv (fn [m k v] (if (and (predk k) (predv v)) m (dissoc m k)))
               (or m {}) (or m {}))))

(defn filter-keys [pred m] (filter-kvs pred (constantly true) m))
(defn filter-vals [pred m] (filter-kvs (constantly true) pred m))

(comment (filter-vals (complement nil?) {:a :A :b :B :c false :d nil}))

(defn remove-vals
  "Smaller, common-case version of `filter-vals`. Esp useful with `nil?`/`blank?`
  pred when constructing maps: {:foo (when _ <...>) :bar (when _ <...>)} in a
  way that preservers :or semantics."
  [pred m] (reduce-kv (fn [m k v] (if (pred v) (dissoc m k) m )) m m))

(comment (remove-vals nil? {:a :A :b false :c nil :d :D}))

;; (def keywordize-map #(map-kvs :keywordize nil %))
(defn keywordize-map [m]
  (when m (reduce-kv (fn [m k v] (assoc m (keyword k) v)) {} m)))

(comment (keywordize-map nil)
         (keywordize-map {"akey" "aval" "bkey" "bval"}))

(defn as-map "Cross between `hash-map` & `map-kvs`."
  [coll & [kf vf]]
  {:pre  [(coll? coll) (or (nil? kf) (fn? kf) (identical? kf :keywordize))
                       (or (nil? vf) (fn? vf))]
   :post [(map? %)]}
  (when-let [s' (seq coll)]
    (let [kf (if-not (identical? kf :keywordize) kf
               (fn [k _] (keyword k)))]
      (loop [m (transient {}) [k v :as s] s']
        (let [k (if-not kf k (kf k v))
              v (if-not vf v (vf k v))
              new-m (assoc! m k v)]
          (if-let [n (nnext s)]
            (recur new-m n)
            (persistent! new-m)))))))

(comment (as-map ["a" "A" "b" "B" "c" "C"] :keywordize
           (fn [k v] (case k (:a :b) (str "boo-" v) v))))

(defn into-all "Like `into` but supports multiple \"from\"s."
  ([to from] (into to from))
  ([to from & more] (reduce into (into to from) more)))

(defn interleave-all
  "Greedy version of `interleave`.
  Ref. https://groups.google.com/d/msg/clojure/o4Hg0s_1Avs/rPn3P4Ig6MsJ"
  ([c1 c2]
     (lazy-seq
      (let [s1 (seq c1) s2 (seq c2)]
        (cond
         (and s1 s2)
         (cons (first s1) (cons (first s2)
                                (interleave-all (rest s1) (rest s2))))
         s1 s1
         s2 s2))))

  ([c1 c2 & colls]
     (lazy-seq
      (let [ss (filter identity (map seq (conj colls c2 c1)))]
        (concat (map first ss)
                (apply interleave-all (map rest ss)))))))

(comment (interleave-all [:a :b :c] [:A :B :C :D :E] [:1 :2]))

(defn distinctv "Prefer `set` when order doesn't matter (much faster)."
  ([coll] ; `distinctv`
     (-> (reduce (fn [[v seen] in]
                   (if-not (contains? seen in)
                     [(conj! v in) (conj seen in)]
                     [v seen]))
                 [(transient []) #{}]
                 coll)
         (nth 0)
         persistent!))
  ([keyfn coll] ; `distinctv-by`
     (-> (reduce (fn [[v seen] in]
                   (let [in* (keyfn in)]
                     (if-not (contains? seen in*)
                       [(conj! v in) (conj seen in*)]
                       [v seen])))
                 [(transient []) #{}]
                 coll)
         (nth 0)
         persistent!)))

(comment (distinctv        [[:a 1] [:a 1] [:a 2] [:b 1] [:b 3]])
         (distinctv second [[:a 1] [:a 1] [:a 2] [:b 1] [:b 3]]))

(comment
  (time (dotimes [_ 10000] (distinctv [:a :a :b :c :d :d :e :a :b :c :d])))
  (time (dotimes [_ 10000] (doall (distinct [:a :a :b :c :d :d :e :a :b :c :d]))))
  (time (dotimes [_ 10000] (set [:a :a :b :c :d :d :e :a :b :c :d]))))

(defn distinct-by "Like `sort-by` for distinct. Based on clojure.core/distinct."
  [keyfn coll]
  (let [step (fn step [xs seen]
               (lazy-seq
                ((fn [[v :as xs] seen]
                   (when-let [s (seq xs)]
                     (let [v* (keyfn v)]
                       (if (contains? seen v*)
                         (recur (rest s) seen)
                         (cons v (step (rest s) (conj seen v*)))))))
                 xs seen)))]
    (step coll #{})))

(defn rcompare "Reverse comparator." [x y] (compare y x))

(defn merge-deep-with ; From clojure.contrib.map-utils
  "Like `merge-with` but merges maps recursively, applying the given fn
  only when there's a non-map at a particular level.

  (merge-deep-with + {:a {:b {:c 1 :d {:x 1 :y 2}} :e 3} :f 4}
                    {:a {:b {:c 2 :d {:z 9} :z 3} :e 100}})
  => {:a {:b {:z 3, :c 3, :d {:z 9, :x 1, :y 2}}, :e 103}, :f 4}"
  [f & maps]
  (apply
   (fn m [& maps]
     (if (every? map? maps)
       (apply merge-with m maps)
       (apply f maps)))
   maps))

(def merge-deep (partial merge-deep-with (fn [x y] y)))

(comment (merge-deep {:a {:b {:c {:d :D :e :E}}}}
                     {:a {:b {:g :G :c {:c {:f :F}}}}}))

(defn greatest "Returns the 'greatest' element in coll in O(n) time."
  [coll & [?comparator]]
  (let [comparator (or ?comparator rcompare)]
    (reduce #(if (pos? (comparator %1 %2)) %2 %1) coll)))

(defn least "Returns the 'least' element in coll in O(n) time."
  [coll & [?comparator]]
  (let [comparator (or ?comparator rcompare)]
    (reduce #(if (neg? (comparator %1 %2)) %2 %1) coll)))

(comment (greatest ["a" "e" "c" "b" "d"]))

(defn repeatedly-into
  "Like `repeatedly` but faster and `conj`s items into given collection."
  [coll n f]
  (if (instance? clojure.lang.IEditableCollection coll)
    (loop [v (transient coll) idx 0]
      (if (>= idx n) (persistent! v)
        (recur (conj! v (f))
               (inc idx))))
    (loop [v coll idx 0]
      (if (>= idx n) v
        (recur (conj v (f))
               (inc idx))))))

(comment (repeatedly-into [] 10 rand))

(defmacro repeatedly* "Like `repeatedly` but faster and returns a vector."
  [n & body]
  `(let [n# ~n]
     (loop [v# (transient []) idx# 0]
       (if (>= idx# n#)
         (persistent! v#)
         (recur (conj! v# (do ~@body)) (inc idx#))))))

;;;; Strings

(defn str-contains? [s substr]
  #+clj  (.contains ^String s ^String substr)
  #+cljs (not= -1 (.indexOf s substr)))

(defn str-starts-with? [s substr]
  #+clj  (.startsWith ^String s ^String substr)
  #+cljs (zero? (.indexOf s substr)))

(defn str-ends-with? [s substr]
  #+clj  (.endsWith ^String s ^String substr)
  #+cljs (let [s-len      (.length s)
               substr-len (.length substr)]
           (when (>= s-len substr-len)
             (not= -1 (.indexOf s substr (- s-len substr-len))))))

(defn str-trunc [^String s max-len]
  (if (<= (.length s) max-len) s
      (.substring s 0 max-len)))

(comment (str-trunc "Hello this is a long string" 5))

(defn join-once
  "Like `clojure.string/join` but ensures no double separators."
  [separator & coll]
  (reduce
   (fn [s1 s2]
     (let [s1 (str s1) s2 (str s2)]
       (if (str-ends-with? s1 separator)
         (if (str-starts-with? s2 separator)
           (str s1 (.substring s2 1))
           (str s1 s2))
         (if (str-starts-with? s2 separator)
           (str s1 s2)
           (if (or (= s1 "") (= s2 ""))
             (str s1 s2)
             (str s1 separator s2))))))
   nil
   coll))

(defn path
  "Joins string paths (URLs, file paths, etc.) ensuring correct \"/\"
  interposition."
  [& parts] (apply join-once "/" parts))

(comment (path "foo/"  "/bar" "baz/" "/qux/")
         (path "foo" nil "" "bar"))

;; (defn base64-enc "Encodes string as URL-safe Base64 string."
;;   [s] {:pre [(string? s)]}
;;   #+clj  (Base64/encodeBase64URLSafeString (.getBytes ^String s "UTF-8"))
;;   #+cljs (base64/encodeString s (boolean :web-safe)))

;; (defn base64-dec "Decodes Base64 string to string."
;;   [s]
;;   #+clj  (String. (Base64/decodeBase64 ^String s) "UTF-8")
;;   #+cljs (base64/decodeString s (boolean :web-safe)))

;; (comment (-> "Hello this is a test" base64-enc base64-dec))

(defn norm-word-breaks
  "Converts all word breaks of any form and length (including line breaks of any
  form, tabs, spaces, etc.) to a single regular space."
  [s] (str/replace (str s) #"\s+" \space))

(defn count-words [s] (if (str/blank? s) 0 (count (str/split s #"\s+"))))
(count-words "Hello this is a    test")

;;;; IO

#+clj
(do
  (defn- file-resource-last-modified
    "Returns last-modified time for file backing given named resource, or nil if
    file doesn't exist."
    [resource-name]
    (when-let [file (try (->> resource-name io/resource io/file)
                         (catch Exception _))]
      (.lastModified ^java.io.File file)))

  (def file-resources-modified?
    "Returns true iff any files backing the given group of named resources
    have changed since this function was last called."
    (let [;; {#{file1A file1B ...#} (time1A time1A ...),
          ;;  #{file2A file2B ...#} (time2A time2B ...), ...}
          group-times (atom {})]
      (fn [resource-names]
        (let [file-group (into (sorted-set) resource-names)
              file-times (map file-resource-last-modified file-group)
              last-file-times (get @group-times file-group)]
          (when-not (= file-times last-file-times)
            (swap! group-times assoc file-group file-times)
            (boolean last-file-times)))))))

;;;; Memoization

;;; TODO
;; * Consider implementing a self-gc'ing hashmap for use here & elsewhere?
;;
;; * Invalidating memoize* cache doesn't scale horizontally; we could make a
;;   distributed version that maintains a local udt-last-invalidated and on
;;   each op checks it against a master udt-last-invalidated in Redis. Would
;;   need similar extra provisions if we want per-arg-seq invalidation, etc.
;;   It'd be doable.
;;
;; * The Clojure STM is optimistic. Would a `dissoc-ks`-based GC not maybe be
;;   better since it'd be less susceptible to contention? Probably wouldn't be a
;;   measurable difference anyway.

(def ^:private ^:const gc-rate (/ 1.0 8000))

(defn- locked [lock-object f]
  #+clj  (locking lock-object (f)) ; For thread racing
  #+cljs (f))

;;;;

(defn memoized
  "Like `(memoize* f)` but takes an explicit cache atom (possibly nil)
  and immediately applies memoized f to given arguments."
  [cache f & args]
  (let [lockf
        (fn []
          (if-let [dv (@cache args)] @dv ; Retry after lock acquisition!
            (let [dv (delay (apply f args))]
              (swap! cache assoc args dv)
              @dv)))]
    (if-not cache (apply f args)
      (if-let [dv (@cache args)] @dv
        (locked cache lockf)))))

(defn memoize*
  "Like `clojure.core/memoize` but:
    * Uses delays & a fn lock to prevent unnecessary race value recomputation.
    * Supports auto invalidation & gc with `ttl-ms` option.
    * Supports manual invalidation by prepending args with `:mem/del` or `:mem/fresh`.
    * Supports cache size limit & gc with `cache-size` option."
  ([f] ; De-raced, commands
    (let [cache (atom {})] ; {<args> <dval>}
      (fn ^{:arglists '([command & args] [& args])} [& [arg1 & argn :as args]]
        (if (identical? arg1 :mem/del)
          (do (if (identical? (first argn) :mem/all)
                (reset! cache {})
                (swap!  cache dissoc argn))
              nil)

          (let [fresh? (identical? arg1 :mem/fresh)
                args   (if fresh? argn args)
                try1   (fn [] (when-not fresh? (@cache args)))
                lockf  (fn [] (if-let [dv (try1)] @dv ; Retry
                               (let [dv (delay (apply f args))]
                                 (swap! cache assoc args dv)
                                 @dv)))]
            (if-let [dv (try1)] @dv
              (locked cache lockf)))))))

  ([ttl-ms f] ; De-raced, commands, ttl, gc
    (let [cache (atom {})] ; {<args> <[dval udt]>}
      (fn ^{:arglists '([command & args] [& args])} [& [arg1 & argn :as args]]
        (if (identical? arg1 :mem/del)
          (do (if (identical? (first argn) :mem/all)
                (reset! cache {})
                (swap!  cache dissoc argn))
              nil)

          (do
            (when (<= (rand) gc-rate) ; GC
              (let [instant (now-udt)]
                (swap! cache
                  (fn [m] (reduce-kv (fn [m* k [dv udt :as cv]]
                                      (if (> (- instant udt) ttl-ms) m*
                                          (assoc m* k cv))) {} m)))))

            (let [fresh? (identical? arg1 :mem/fresh)
                  args   (if fresh? argn args)
                  try1   (fn []
                           (when-let [[dv udt] (when-not fresh? (@cache args))]
                             (when (and dv (< (- (now-udt) udt)
                                              ttl-ms))
                               dv)))
                  lockf  (fn []
                           (if-let [dv (try1)] @dv ; Retry
                             (let [dv (delay (apply f args))
                                   cv [dv (now-udt)]]
                               (swap! cache assoc args cv)
                               @dv)))]
              (if-let [dv (try1)] @dv
                (locked cache lockf))))))))

  ([cache-size ttl-ms f] ; De-raced, commands, ttl, gc, max-size
    (let [state (atom {:tick 0})] ; {:tick _ <args> <[dval udt tick-lru tick-lfu]>}
      (fn ^{:arglists '([command & args] [& args])} [& [arg1 & argn :as args]]
        (if (identical? arg1 :mem/del)
          (do (if (identical? (first argn) :mem/all)
                (reset! state {:tick 0})
                (swap!  state dissoc argn))
              nil)

          (do
            (when (<= (rand) gc-rate) ; GC
              (let [instant (now-udt)]
                (swap! state
                  (fn [m]
                    (let [m* (dissoc m :tick)
                          ;; First prune expired stuff:
                          m* (if-not ttl-ms m*
                               (reduce-kv (fn [m* k [dv udt _ _ :as cv]]
                                            (if (> (- instant udt) ttl-ms) m*
                                                (assoc m* k cv))) {} m*))
                          n-to-prune (- (count m*) cache-size)
                          ;; Then prune by descending tick-sum:
                          m* (if-not (pos? n-to-prune) m*
                               (->>
                                (keys m*)
                                (mapv (fn [k] (let [[_ _ tick-lru tick-lfu] (m* k)]
                                               [(+ tick-lru tick-lfu) k])))
                                (sort-by #(nth % 0))
                                ;; (#(do (println %) %)) ; Debug
                                (take    n-to-prune)
                                (mapv    #(nth % 1))
                                (apply dissoc m*)))]
                      (assoc m* :tick (:tick m)))))))

            (let [fresh? (identical? arg1 :mem/fresh)
                  args   (if fresh? argn args)
                  try1
                  (fn []
                    (let [state' @state
                          tick'  (:tick state')]
                      (when-let [[dv udt tick-lru tick-lfu]
                                 (when-not fresh? (state' args))]
                        (when (and dv (or (nil? ttl-ms)
                                          (< (- (now-udt) udt)
                                             ttl-ms)))
                          ;; We don't particularly care about ticks being
                          ;; completely synchronized:
                          (let [sv [dv udt (inc tick') (inc tick-lfu)]]
                            (swap! state assoc :tick (inc tick') args sv))
                          dv))))
                  lockf (fn []
                          (if-let [dv (try1)] @dv ; Retry
                            (let [dv   (delay (apply f args))
                                  tick (:tick @state)
                                  sv   [dv (when ttl-ms (now-udt))
                                        (inc tick) 1]]
                              (swap! state assoc :tick (inc tick) args sv)
                              @dv)))]
              (if-let [dv (try1)] @dv
                (locked state lockf)))))))))

(comment
  (def f0 (memoize         (fn [& xs] (Thread/sleep 600) (rand))))
  (def f1 (memoize*        (fn [& xs] (Thread/sleep 600) (rand))))
  (def f2 (memoize* 5000   (fn [& xs] (Thread/sleep 600) (rand))))
  (def f3 (memoize* 2 nil  (fn [& xs] (Thread/sleep 600) (rand))))
  (def f4 (memoize* 2 5000 (fn [& xs] (Thread/sleep 600) (rand))))

  (time (dotimes [_ 10000] (f0))) ;  ~2ms
  (time (dotimes [_ 10000] (f1))) ;  ~3ms
  (time (dotimes [_ 10000] (f2))) ;  ~4ms
  (time (dotimes [_ 10000] (f3))) ; ~10ms
  (time (dotimes [_ 10000] (f4))) ; ~13ms

  (f1)
  (f1 :mem/del)
  (f1 :mem/fresh)

  ;; For testing, these need GC set to -always- run
  (f3 "a")
  (f3 "b")
  (f3 "c")
  (f3 "d")

  (println "--")
  (let [f0 (memoize  (fn [] (Thread/sleep 5) (println "compute0")))]
    (dotimes [_ 500] (future (f0)))) ; Prints many
  (let [f1 (memoize* (fn [] (Thread/sleep 5) (println "compute1")))]
    (dotimes [_ 500] (future (f1)))) ; NEVER prints >1
  (let [f4 (memoize* 2 5000 (fn [] (Thread/sleep 5) (println "compute1")))]
    (dotimes [_ 10] (future (f4)))))

(defn rate-limiter
  "Returns a `(fn [& [id]])` that returns either `nil` (limit okay) or number of
  msecs until next rate limit window (rate limited)."
  [ncalls-limit window-ms]
  (let [state (atom [nil {}])] ; [<pull> {<id> {[udt-window-start ncalls]}}]
    (fn [& [id]]

      (when (<= (rand) gc-rate) ; GC
        (let [instant (now-udt)]
          (swap! state
            (fn [[_ m]]
              [nil (reduce-kv
                    (fn [m* id [udt-window-start ncalls]]
                      (if (> (- instant udt-window-start) window-ms) m*
                          (assoc m* id [udt-window-start ncalls]))) {} m)]))))

      (->
       (let [instant (now-udt)]
         (swap! state
           (fn [[_ m]]
             (if-let [[udt-window-start ncalls] (m id)]
               (if (> (- instant udt-window-start) window-ms)
                 [nil (assoc m id [instant 1])]
                 (if (< ncalls ncalls-limit)
                   [nil (assoc m id [udt-window-start (inc ncalls)])]
                   [(- (+ udt-window-start window-ms) instant) m]))
               [nil (assoc m id [instant 1])]))))
       (nth 0)))))

(comment
  (def rl (rate-limiter 10 10000))
  (repeatedly 10 #(rl (rand-nth [:a :b :c])))
  (rl :a)
  (rl :b)
  (rl :c))

(defn rate-limited "Wraps fn so that it returns {:result _ :backoff-ms _}."
  [ncalls-limit window-ms f]
  (let [rl (rate-limiter ncalls-limit window-ms)]
    (fn [& args] (if-let [backoff-ms (rl)]
                  {:backoff-ms backoff-ms}
                  {:result     (f)}))))

(comment (def compute (rate-limited 3 5000 (fn [] "Compute!")))
         (compute))

;;;; Benchmarking

(defmacro time-ms "Returns number of milliseconds it takes to execute body."
  [& body] `(let [t0# (now-udt)] ~@body (- (now-udt) t0#)))

(defmacro time-ns "Returns number of nanoseconds it takes to execute body."
  [& body] `(let [t0# (System/nanoTime)] ~@body (- (System/nanoTime) t0#)))

#+clj
(defn bench*
  "Repeatedly executes fn and returns time taken to complete execution."
  [nlaps {:keys [nlaps-warmup nthreads as-ns?]
          :or   {nlaps-warmup 0
                 nthreads     1}} f]
  (try (dotimes [_ nlaps-warmup] (f))
    (let [nanosecs
          (if (= nthreads 1)
            (time-ns (dotimes [_ nlaps] (f)))
            (let [nlaps-per-thread (int (/ nlaps nthreads))]
              (time-ns
               (->> (fn [] (future (dotimes [_ nlaps-per-thread] (f))))
                    (repeatedly nthreads)
                    (doall)
                    (map deref)
                    (dorun)))))]
      (if as-ns? nanosecs (Math/round (/ nanosecs 1000000.0))))
    (catch Exception e (format "DNF: %s" (.getMessage e)))))

(defmacro bench [nlaps bench*-opts & body]
  `(bench* ~nlaps ~bench*-opts (fn [] ~@body)))
