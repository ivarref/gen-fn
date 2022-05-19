(ns com.github.ivarref.fn-test
  (:require [clojure.test :refer [use-fixtures deftest is]]
            [datomic.api :as d]
            [com.github.ivarref.gen-fn :as gen-fn]
            [com.github.ivarref.my-add :as my-add]))

(def ^:dynamic *conn* nil)

(def schema
  [#:db{:ident :e/id :cardinality :db.cardinality/one :valueType :db.type/string :unique :db.unique/identity}
   #:db{:ident :e/res :cardinality :db.cardinality/one :valueType :db.type/long}])

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

(use-fixtures :each with-new-conn)

(defn transact! [tx]
  @(d/transact *conn* tx))

(defn pull [pat e]
  (d/pull (d/db *conn*) pat e))

(defn attr [attr e]
  (get (d/pull (d/db *conn*) [attr] e) attr))

(defn fn-str [s db-ident]
  (gen-fn/read-dbfn (gen-fn/file-str->datomic-fn-str s db-ident)))

(deftest basics
  (transact! [(fn-str "(defn sample-fn [db args])" :my/fn)])
  (transact! [[:my/fn "ignored"]]))


(deftest add
  (transact! [(fn-str "(defn my-fn [db e a b] [[:db/add e :e/res (+ a b)]])" :my/fn)])
  (transact! [{:db/id "tempid" :e/id "a"} [:my/fn "tempid" 1 2]])
  (is (= 3 (attr :e/res [:e/id "a"]))))


(deftest two-fn
  (transact! [(fn-str "(defn inner-fn [a b] (+ a b))
                       (defn my-fn [db e a b] [[:db/add e :e/res (inner-fn a b)]])" :my/fn)])
  (transact! [{:db/id "tempid" :e/id "a"} [:my/fn "tempid" 1 2]])
  (is (= 3 (attr :e/res [:e/id "a"]))))

(deftest docstring-1
  (transact! [(fn-str "(defn inner-fn \"Docstring\" [a b] (+ a b))
                       (defn my-fn [db e a b] [[:db/add e :e/res (inner-fn a b)]])" :my/fn)])
  (transact! [{:db/id "tempid" :e/id "a"} [:my/fn "tempid" 1 2]])
  (is (= 3 (attr :e/res [:e/id "a"]))))

(deftest docstring-2
  (transact! [(fn-str "(defn inner-fn [a b] \"Docstring\" (+ a b))
                       (defn my-fn [db e a b] [[:db/add e :e/res (inner-fn a b)]])" :my/fn)])
  (transact! [{:db/id "tempid" :e/id "a"} [:my/fn "tempid" 1 2]])
  (is (= 3 (attr :e/res [:e/id "a"]))))


(deftest defs
  (transact! [(fn-str "(def my-constant 1)
                       (defn my-fn [db e a] [[:db/add e :e/res (+ a my-constant)]])" :my/fn)])
  (transact! [{:db/id "tempid" :e/id "a"} [:my/fn "tempid" 1]])
  (is (= 2 (attr :e/res [:e/id "a"]))))

(deftest gen-fn
  (transact! [(gen-fn/datomic-fn :my-add #'my-add/my-add)])
  (transact! [{:e/id "a" :e/res 1}])
  (transact! [[:my-add [:e/id "a"] :e/res 2]])
  (is (= 3 (attr :e/res [:e/id "a"]))))
