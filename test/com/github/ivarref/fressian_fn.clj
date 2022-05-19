(ns com.github.ivarref.fressian-fn
  (:require [clojure.walk :as walk])
  (:import (java.util HashSet List)))

(defn to-clojure-types [m]
  (walk/prewalk
    (fn [e]
      (cond (instance? String e)
            e

            (instance? HashSet e)
            (into #{} e)

            (and (instance? List e) (not (vector? e)))
            (vec e)

            :else e))
    m))

(defn fressian-inner [db e arg]
  [[:db/add e :e/bool (vector? arg)]])

(defn fressian [db e arg]
  (fressian-inner
    db
    (to-clojure-types e)
    (to-clojure-types arg)))
