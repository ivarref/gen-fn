# gen-fn

Generate Datomic function literals from regular Clojure namespaces. On-prem.

## Installation

[![Clojars Project](https://img.shields.io/clojars/v/com.github.ivarref/gen-fn.svg)](https://clojars.org/com.github.ivarref/gen-fn)

## 2-minute example

Add a namespace that will contain the generated Datomic function literals:

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

Add a namespace containing the function you would like to run on the transactor:
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

Generate the Datomic function literal:
 
```clojure
(require '[com.github.ivarref.gen-fn :refer [gen-fn!]])
(require '[com.github.ivarref.my-add :refer [my-add]])

(gen-fn! :my-add #'my-add "test/com/github/ivarref/generated.clj")
```

You can now see the following contents in the generated namespace (formatting added):
```clojure
; Generated code below, do not edit:
(def generated {:my-add "
{:db/ident :my-add 
 :db/fn #db/fn {:lang \"clojure\", 
               :requires [[datomic.api :as d]], 
               :imports [], 
               :params [db e a v], 
               :code (let [] (letfn [(curr-val [db e a] 
                                       (do (d/q (quote [:find ?v . :in $ ?e ?a :where [?e ?a ?v]]) db e a)))]
                               (let [genfn-coerce-arg (clojure.core/fn [x]
                                 (clojure.walk/prewalk 
                                   (clojure.core/fn [e]
                                     (clojure.core/when (clojure.core/instance? clojure.lang.PersistentTreeMap e)
                                     (throw (clojure.core/ex-info \"Using sorted-map will cause different types in transactor for in-mem and remote\" {:val e})))
                                     
                                     (clojure.core/when (clojure.core/var? e)
                                     (throw (clojure.core/ex-info \"Using var does not work for remote transactor\" {:val e})))
                                     
                                     (clojure.core/when (clojure.core/or (clojure.core/= clojure.lang.PersistentList$EmptyList (.getClass e))
                                                                         (clojure.core/instance? clojure.lang.PersistentList e))
                                     (throw (clojure.core/ex-info \"Using list will cause indistinguishable types in transactor for in-mem and remote\" {:val e})))
                                     
                                     (clojure.core/when (clojure.core/instance? clojure.lang.PersistentQueue e)
                                     (throw (clojure.core/ex-info \"Using clojure.lang.PersistentQueue does not work for remote transactor\" {:val e})))
                                     
                                     (clojure.core/cond
                                       (clojure.core/instance? java.util.HashSet e)
                                       (clojure.core/into #{} e)
                                       
                                       (clojure.core/and (clojure.core/instance? java.util.List e) (clojure.core/not (clojure.core/vector? e)))
                                       (clojure.core/vec e)
                                       
                                       :else e)) x))]
                                     (let [e (genfn-coerce-arg e)
                                           a (genfn-coerce-arg a)
                                           v (genfn-coerce-arg v)]
                                       (do [[:db/add e a (+ (curr-val db e a) v)]])))))}}"})
; End of generated code
```

You can see that:
* The `(ns ...` declaration is rewritten to `#db/fn`.
* The last `defn` is used as the main body.
* Other `defn`s are inlined using `letfn`.
* `defn` bodies are wrapped in `do` in case you need side effects for debugging.
* `def`s are inlined in the top `let`. No `def`s are used in the example namespace, thus the top `let` is empty.
* Arguments are converted to "proper" Clojure types. This is to say that types will be identical in the in-memory transactor and the remote transactor.
 
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

### Note: fressian serialization and deserialization

One thing that may surprise you is that parameters may be 
slightly different on an in-memory transactor and on a remote transactor.
This is due to tx-data being serialized and deserialized using
[fressian](https://github.com/Datomic/fressian) only when using
a remote transactor.

As can be seen by the example above, `gen-fn` will write functions that
automatically handles this for you.

## Limitations

Only a single namespace per database function is supported, i.e.
the database function must be contained in a single namespace. It may
refer code that already exists on the transactor classpath.

## Alternatives

There is also [classpath functions](https://docs.datomic.com/on-prem/reference/database-functions.html#classpath-functions). This means that you will need to update your
transactor if you need to add or change a function.

## Change log

#### 0.2.45 - 2022-12-14
Added auto conversion to "proper" Clojure types.

#### 0.1.35 - 2022-06-13

Prettier generated files output. Thanks to [rewrite-clj](https://github.com/clj-commons/rewrite-clj).

#### 0.1.33 - 2022-05-24
Add optional keyword argument `:reset?` to `gen-fn`.
If `:reset?` is set to true, it will clear the generated map
before associng the given function. Example usage:
```clojure
(gen-fn! :my-add #'my-add output-file :reset? true)
```

#### 0.1.30 - 2022-05-24
First publicly announced release.

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
