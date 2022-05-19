(ns com.github.ivarref.fressian-test
  (:require [clojure.test :refer [deftest is] :as test]
            [datomic.api :as d]
            [com.github.ivarref.gen-fn :as gen-fn]
            [com.github.ivarref.fressian-fn :as ffn]))

(def ^:dynamic *conn* nil)

(def schema
  [#:db{:ident :e/id :cardinality :db.cardinality/one :valueType :db.type/string :unique :db.unique/identity}
   #:db{:ident :e/bool :cardinality :db.cardinality/one :valueType :db.type/boolean}])

(defn with-new-conn [f]
  (let [conn (let [uri (str "datomic:mem://test-" (random-uuid))]
               (d/delete-database uri)
               (d/create-database uri)
               (d/connect uri))]
    @(d/transact conn schema)
    (try
      (binding [*conn* conn]
        (f))
      (finally
        (d/release conn)))))

(test/use-fixtures :each with-new-conn)

(deftest fressian-test
  @(d/transact *conn* [(gen-fn/datomic-fn :vector? #'ffn/fressian)])
  @(d/transact *conn* [{:e/id "a" :db/id "res"}
                       [:vector? "res" []]])
  (is (true? (:e/bool (d/pull (d/db *conn*) [:e/bool] [:e/id "a"])))))
