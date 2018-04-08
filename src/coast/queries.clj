(ns coast.queries
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [coast.utils :as utils]
            [clojure.set :as set])
  (:import [java.io FileNotFoundException]))

(def name-regex #"^--\s*name\s*:\s*(.+)$")
(def fn-regex #"^--\s*fn\s*:\s*(.+)$")
(def qualified-keyword-pattern #":([\w-\.]+/?[\w-\.]+)Z?")

(defn name-line? [s]
  (not (nil? (re-matches name-regex s))))

(defn fn-line? [s]
  (not (nil? (re-matches fn-regex s))))

(defn sql-line? [s]
  (nil? (re-matches #"^--.*$" s)))

(defn parse-name [s]
  (when s
    (let [[_ name] (re-matches name-regex s)]
      name)))

(defn parse-fn [s]
  (let [[_ f] (re-matches fn-regex (or s ""))]
    (if (nil? f)
      (resolve (symbol "identity"))
      (resolve (symbol f)))))

(defn parse-query-string [query-string]
  (let [query-lines (clojure.string/split query-string #"\n")
        name (-> (filter name-line? query-lines)
                 (first)
                 (parse-name))
        f-name (->> (filter fn-line? query-lines)
                    (first))
        f (parse-fn f-name)
        sql (clojure.string/join " " (filter sql-line? query-lines))]
    (if (nil? name)
      nil
      {:sql sql :f f :name name :fn f-name})))

(defn parse [lines]
  (let [query-lines (clojure.string/split lines #"\n\n")]
    (filter #(not (nil? %)) (map parse-query-string query-lines))))

(defn parameterize [s m]
  (string/replace s qualified-keyword-pattern (fn [[_ s]]
                                                (let [k (keyword s)
                                                      v (get m k)]
                                                  (if (coll? v)
                                                    (->> (map (fn [_] (str "?")) v)
                                                         (string/join ","))
                                                    "?")))))

(defn has-keys? [m keys]
  (every? #(contains? m %) keys))

(defn sql-ks [sql]
  (->> (mapv #(-> % second keyword) (re-seq qualified-keyword-pattern sql))
       (map utils/snake)))

(defn sql-params [sql m]
  (let [m (utils/map-keys utils/snake m)]
    (->> (sql-ks sql)
         (mapv (fn [k] [k (get m k)]))
         (into {}))))

(defn sql-vec [sql m]
  (when (string? sql)
    (let [m (->> (or m {})
                 (utils/map-keys utils/snake))
          params (sql-params sql m)
          f-sql (parameterize sql params)
          s-vec (vec (concat [f-sql] (->> (map (fn [[_ v]] (if (coll? v) (flatten v) v)) params)
                                          (flatten))))]
      (if (has-keys? m (keys params))
        s-vec
        (->> (set/difference (set (keys params)) (set (keys m)))
             (string/join ", ")
             (format "Missing keys: %s")
             (Exception.)
             (throw))))))

(defn slurp-resource [filename]
  (or (some-> filename io/resource slurp)
      (throw (FileNotFoundException. filename))))

(defn query [name filename]
  (->> (slurp-resource filename)
       (parse)
       (filter #(= (:name %) name))
       (first)))
