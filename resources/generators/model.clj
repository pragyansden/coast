(ns __project.models.__table
  (:require [__project.db.__table :as db.__table]
            [coast.utils :as utils]))
(:refer-clojure :exclude [list find update])

(def columns [__columns])

(defn list [request]
  (->> (:params request)
       (db.__table/list)
       (assoc request :__table)))

(defn find [request]
  (->> (:params request)
       (hash-map :id)
       (db.__table/find)
       (assoc request :__singular)))

(defn insert [request]
  (as-> (:params request) %
        (select-keys % columns)
        (db.__table/insert %)
        (assoc request :__singular %)))

(defn update [request]
  (as-> (:params request) %
        (select-keys % columns)
        (db.__table/update %)
        (assoc request :__singular %)))

(defn delete [request]
  (as-> (:params request) %
        (select-keys % columns)
        (db.__table/delete %)
        (assoc request :__singular %)))
