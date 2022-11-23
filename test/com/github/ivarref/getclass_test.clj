(ns com.github.ivarref.getclass-test
  (:require
    [clojure.edn :as edn]
    [clojure.test :refer [deftest is]]
    [com.github.sikt-no.datomic-testcontainers :as dtc]
    [datomic.api :as d]))

(def get-clazz "{:db/ident :get-clazz
                           :db/fn #db/fn {:lang \"clojure\"
                                          :requires []
                                          :imports []
                                          :params [db t]
                                          :code (do [[:db/add \"res\" :e/clazz (.getName (.getClass t))]])}}")

(def schema
  (mapv (fn [s]
          (edn/read-string
            {:readers {'db/id  datomic.db/id-literal
                       'db/fn  datomic.function/construct}}
            s))
        [get-clazz]))

(defn ensure-partition!
  "Ensures that `new-partition` is installed in the database."
  [conn new-partition]
  (assert (keyword? new-partition))
  (let [db (d/db conn)]
    (if-let [eid (d/q '[:find ?e .
                        :in $ ?part
                        :where
                        [?e :db/ident ?part]]
                      db
                      new-partition)]
      eid
      (get-in
        @(d/transact conn [{:db/id "new-part" :db/ident new-partition}
                           [:db/add :db.part/db :db.install/partition "new-part"]])
        [:tempids "new-part"]))))

(defn get-class [conn what]
  @(d/transact conn [#:db{:ident :e/id, :cardinality :db.cardinality/one, :valueType :db.type/string, :unique :db.unique/identity}
                     #:db{:ident :e/clazz, :cardinality :db.cardinality/one, :valueType :db.type/string}])
  @(d/transact conn schema)
  @(d/transact conn [{:db/id "res" :e/id "1"} [:get-clazz what]])
  (ensure-partition! conn :my-part)
  (:e/clazz (d/pull (d/db conn) [:e/clazz] [:e/id "1"])))

(deftest in-mem-vs-remote
  (let [remote (dtc/get-conn {})
        in-mem (let [uri (str "datomic:mem://test-" (random-uuid))]
                 (d/delete-database uri)
                 (d/create-database uri)
                 (d/connect uri))]
    (is (= "java.util.Arrays$ArrayList" (get-class remote [1 2 3])))
    (is (= "clojure.lang.PersistentVector" (get-class in-mem [1 2 3])))

    (is (= "java.util.HashSet" (get-class remote #{})))
    (is (= "clojure.lang.PersistentHashSet" (get-class in-mem #{})))

    (is (= "java.util.Date" (get-class remote #inst"2020")))
    (is (= "java.util.Date" (get-class in-mem #inst"2020")))

    (is (= "clojure.lang.Symbol" (get-class remote 'some-symbol)))
    (is (= "clojure.lang.Symbol" (get-class in-mem 'some-symbol)))

    (is (= "clojure.lang.PersistentArrayMap" (get-class remote {})))
    (is (= "clojure.lang.PersistentArrayMap" (get-class in-mem {})))

    (is (= "datomic.db.DbId" (get-class remote (d/tempid :my-part))))
    (is (= "datomic.db.DbId" (get-class in-mem (d/tempid :my-part))))))
