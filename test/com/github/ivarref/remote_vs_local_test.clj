(ns com.github.ivarref.remote-vs-local-test
  (:require
    [clojure.edn :as edn]
    [clojure.test :refer [deftest is]]
    [com.github.ivarref.gen-fn :as gen-fn]
    [com.github.sikt-no.datomic-testcontainers :as dtc]
    [datomic.api :as d])
  (:import (clojure.lang PersistentQueue)
           (java.util.concurrent ExecutionException)))

(def schema
  [#:db{:ident :e/id, :cardinality :db.cardinality/one, :valueType :db.type/string, :unique :db.unique/identity}
   #:db{:ident :e/clazz, :cardinality :db.cardinality/one, :valueType :db.type/string}
   #:db{:ident :e/debug, :cardinality :db.cardinality/one, :valueType :db.type/string}])

(defn empty-conn []
  (let [uri (str "datomic:mem://test-" (random-uuid))]
    (d/delete-database uri)
    (d/create-database uri)
    (d/connect uri)))

(def get-clazz "{:db/ident :get-clazz
                           :db/fn #db/fn {:lang \"clojure\"
                                          :requires []
                                          :imports []
                                          :params [db t]
                                          :code (do [[:db/add \"res\" :e/clazz (.getName (.getClass t))]])}}")

(def dbfn-schema
  (mapv (fn [s]
          (edn/read-string
            {:readers {'db/id datomic.db/id-literal
                       'db/fn datomic.function/construct}}
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

(defn get-class-raw [conn what]
  @(d/transact conn schema)
  @(d/transact conn dbfn-schema)
  @(d/transact conn [{:db/id "res" :e/id "1"} [:get-clazz what]])
  (ensure-partition! conn :my-part)
  (:e/clazz (d/pull (d/db conn) [:e/clazz] [:e/id "1"])))

(deftest in-mem-vs-remote
  (let [remote (dtc/get-conn {:delete? true})
        in-mem (empty-conn)
        remote-local (fn [what]
                       [(get-class-raw remote what)
                        (get-class-raw in-mem what)])
        assert-is-same (fn [expected what]
                         (let [remote-class (get-class-raw remote what)
                               in-mem-class (get-class-raw in-mem what)
                               err-msg (str "Expected " what " to be identical in remote "
                                            remote-class
                                            " and in-memory "
                                            in-mem-class
                                            " transactor and to be " expected)]
                           (is (= remote-class in-mem-class expected) err-msg)
                           (when-not (= remote-class in-mem-class expected)
                             (binding [*out* *err*]
                               (println err-msg)))))]
    (is (= ["java.util.Arrays$ArrayList" "clojure.lang.PersistentVector"] (remote-local [1 2 3])))
    (is (= ["java.util.Arrays$ArrayList" "clojure.lang.PersistentVector"] (remote-local [])))
    (is (= ["java.util.Arrays$ArrayList" "clojure.lang.PersistentList$EmptyList"] (remote-local (list))))
    (is (= ["java.util.Arrays$ArrayList" "clojure.lang.PersistentList"] (remote-local (list 1 2 3))))
    (is (= ["java.util.HashSet" "clojure.lang.PersistentHashSet"] (remote-local #{})))
    (is (= ["clojure.lang.PersistentArrayMap" "clojure.lang.PersistentTreeMap"] (remote-local (sorted-map :a 123))))

    (is (thrown? ExecutionException (get-class-raw remote PersistentQueue/EMPTY)))
    (is (= "clojure.lang.PersistentQueue" (get-class-raw in-mem PersistentQueue/EMPTY)))

    (assert-is-same "java.util.Date" #inst"2020")
    (assert-is-same "clojure.lang.Symbol" 'some-symbol)
    (assert-is-same "clojure.lang.PersistentArrayMap" {})
    (assert-is-same "clojure.lang.PersistentArrayMap" {:a 123})
    (assert-is-same "datomic.db.DbId" (d/tempid :my-part))))

(def db-fn (gen-fn/read-dbfn
             (gen-fn/file-str->datomic-fn-str
               "(ns some-ns)
                (defn my-fn [db t]
                  [[:db/add \"res\" :e/clazz (.getName (.getClass t))]
                   [:db/add \"res\" :e/debug (with-out-str (genfn-coerce-arg 123))]])"
               :get-clazz)))

(defn get-class-coerced [conn what]
  @(d/transact conn schema)
  @(d/transact conn [db-fn])
  @(d/transact conn [{:db/id "res" :e/id "1"} [:get-clazz what]])
  (ensure-partition! conn :my-part)
  (:e/clazz (d/pull (d/db conn) [:e/clazz :e/debug] [:e/id "1"])))

(deftest coerce-argument-test
  (let [in-mem (empty-conn)
        remote (dtc/get-conn {:db-name "coerce-test" :delete? true})
        assert-is-same (fn [expected what]
                         (let [remote-class (get-class-coerced remote what)
                               in-mem-class (get-class-coerced in-mem what)]
                           (is (= remote-class in-mem-class expected))))]
    (assert-is-same "clojure.lang.PersistentVector" [1 2 3])
    (assert-is-same "clojure.lang.PersistentVector" [])
    (assert-is-same "clojure.lang.PersistentHashSet" #{})
    (assert-is-same "clojure.lang.PersistentHashSet" #{1 2 3})
    (assert-is-same "java.lang.Long" 1)

    (is (thrown? Exception (get-class-coerced in-mem (sorted-map))))
    (is (thrown? Exception (get-class-coerced in-mem (sorted-map :a 1))))
    (is (thrown? Exception (get-class-coerced in-mem (list 1 2 3))))
    (is (thrown? Exception (get-class-coerced in-mem (list))))
    (is (thrown? Exception (get-class-coerced in-mem PersistentQueue/EMPTY)))
    (is (thrown? Exception (get-class-coerced in-mem #'get-class-coerced)))

    (assert-is-same "java.util.Date" #inst"2020")
    (assert-is-same "clojure.lang.Symbol" 'some-symbol)
    (assert-is-same "clojure.lang.PersistentArrayMap" {})
    (assert-is-same "clojure.lang.PersistentArrayMap" {:a 123})
    (assert-is-same "datomic.db.DbId" (d/tempid :my-part))))

(def db-fn-2 (gen-fn/read-dbfn
               (gen-fn/file-str->datomic-fn-str
                 "(ns some-ns)
                (defn my-fn [db t]
                  [[:db/add \"res\" :e/debug (str t)]])"
                 :get-str)))

(deftest coerce-works-with-nil
  (let [in-mem (empty-conn)
        remote (dtc/get-conn {:db-name "coerce-nil-test" :delete? true})
        transact! (fn [what]
                    @(d/transact in-mem what)
                    @(d/transact remote what))
        get-str (fn [conn what]
                  (let [{:keys [db-after]} @(d/transact conn [{:db/id "res" :e/id "1"}
                                                              [:get-str what]])]
                    (:e/debug (d/pull db-after [:e/debug] [:e/id "1"]))))]
    (transact! schema)
    (transact! [db-fn-2])
    (is (= (str false) (get-str in-mem false)))
    (is (= (str false) (get-str remote false)))
    (is (= (str true) (get-str in-mem true)))
    (is (= (str true) (get-str remote true)))
    (is (= (str nil) (get-str in-mem nil)))
    (is (= (str nil) (get-str remote nil)))))
