# gen-fn

Generate Datomic function literals from regular Clojure namespaces. On-prem.

## Installation

...

## 2-minute example

Add a namespace like the following.
It will contain the generated Datomic function literals.

```clojure
(ns com.github.ivarref.generated
  (:require [clojure.edn :as edn]
            [datomic.api]))

; Generated code below, do not edit:
(def generated {})
; End of generated code

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

(gen-fn! :my-add #'my-add "test/com/github/ivarref/generated.clj")
```

You can now see the following contents in the generated namespace (formatting added):
```clojure
(def generated {:my-add "
{:db/ident :my-add 
 :db/fn #db/fn {:lang \"clojure\", 
               :requires [[datomic.api :as d]], 
               :imports [], 
               :params [db e a v], 
 :code (let [] 
        (letfn [(curr-val [db e a] (do (d/q (quote [:find ?v . 
                                                   :in $ ?e ?a 
                                                   :where [?e ?a ?v]])
                                                   db e a)))]
           (do [[:db/add e a (+ (curr-val db e a) v)]])))}}"})
```

You can see that:
* The `(ns ...` declaration is rewritten to `#db/fn`.
* The last `defn` is used as the main body.
* Other `defn`s are inlined using `letfn`.
* `defn` bodies are wrapped in `do` in case you need side effects for debugging.
* `def`s are inlined in the top `let`. No `def`s are used in the example namespace, thus the top `let` is empty.

## Test and development usage

One advantage of writing Datomic database functions using regular Clojure
code is that you may test them using plain Clojure.

If however you'd like to test the function in an actual transaction, you may use
the following if you'd like to avoid a hard dependency on generated files:

```clojure
(require '[com.github.ivarref.gen-fn :refer [datomic-fn]])
(require '[com.github.ivarref.my-add :refer [my-add]])

...
@(d/transact conn [(datomic-fn :my-add #'my-add)])

...
@(d/transact conn [[:my-add some-eid attr value-to-add]])
```

### Tips and tricks: fressian serialization and deserialization

One thing that may surprise you is that parameters may be 
slightly different on an in-memory transactor and on a remote transactor.
This is due to tx-data being serialized and deserialized using
[fressian](https://github.com/Datomic/fressian) only when using
a remote transactor.

An example of this difference (using [data.fressian](https://github.com/clojure/data.fressian)):
```clojure
(require '[clojure.data.fressian :as fress])
(vector? (fress/read (fress/write [])))
=> false
```

`com.github.ivarref.gen-fn/fressian-ize` is a function that
you may use if you want to fressian-ize your inputs:

```clojure
(ns my-test
  (:require [com.github.ivarref.gen-fn :as gen-fn]
  ...))

@(d/transact conn (gen-fn/fressian-ize tx-data))
```

How I usually solve this problem is by converting all parameters
to proper Clojure types:

```clojure
(ns my-db-fn
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

(defn my-fn-inner [db e arg]
  ...actual code)

(defn my-fn [db e arg]
  (my-fn-inner
    db
    (to-clojure-types e)
    (to-clojure-types arg)))
```

## Change log

## License

Copyright © 2022 Ivar Refsdal

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
