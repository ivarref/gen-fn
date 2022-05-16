# gen-fn

Generate Datomic function literals from regular Clojure namespaces.

## Installation

...

## 1-minute example

Add a namespace like the following.
It will contain the generated Datomic function literals.

```clojure
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
```

Add a new namespace containing the function you would like to create:
```clojure
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
```

Let's write this function to the `generated` namespace: 
```clojure
(require '[com.github.ivarref.gen-fn :refer [gen-fn!]])
(require '[com.github.ivarref.my-add :refer [my-add]])

(gen-fn! #'my-add :my-add "test/com/github/ivarref/generated.clj")
```
