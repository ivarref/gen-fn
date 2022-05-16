(ns com.github.ivarref.my-add
  (:require [datomic.api :as d]))

(defn curr-val [db e a]
  (d/q '[:find ?v .
         :in $ ?e ?a
         :where
         [?e ?a ?v]]
       db e a))

(defn my-add [db e a v]
  [[:db/add e a (+ (curr-val db e a) v)]])
