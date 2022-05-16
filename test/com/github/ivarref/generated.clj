(ns com.github.ivarref.generated
  (:require [clojure.edn :as edn]
            [datomic.api]))

(def generated {})

(def schema
  (mapv (fn [s]
          (edn/read-string
            {:readers {'db/id  datomic.db/id-literal
                       'db/fn  datomic.function/construct}}
            s))
        (vals generated)))