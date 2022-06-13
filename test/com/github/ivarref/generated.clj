(ns com.github.ivarref.generated
  (:require [clojure.edn :as edn]
            [datomic.api]))

; Generated code below, do not edit:
(def generated {:my-add "{:db/ident :my-add :db/fn #db/fn {:lang \"clojure\", :requires [[datomic.api :as d]], :imports [], :params [db e a v], :code (let [] (letfn [(curr-val [db e a] (do (d/q (quote [:find ?v . :in $ ?e ?a :where [?e ?a ?v]]) db e a)))] (do [[:db/add e a (+ (curr-val db e a) v)]])))}}" 
                :my-add-2 "{:db/ident :my-add-2 :db/fn #db/fn {:lang \"clojure\", :requires [[datomic.api :as d]], :imports [], :params [db e a v], :code (let [] (letfn [(curr-val [db e a] (do (d/q (quote [:find ?v . :in $ ?e ?a :where [?e ?a ?v]]) db e a)))] (do [[:db/add e a (+ (curr-val db e a) v)]])))}}"})
; End of generated code

(def schema
  (mapv (fn [s]
          (edn/read-string
            {:readers {'db/id  datomic.db/id-literal
                       'db/fn  datomic.function/construct}}
            s))
        (vals generated)))
