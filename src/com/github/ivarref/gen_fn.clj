(ns com.github.ivarref.gen-fn
  (:require [clojure.data.fressian :as fress]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [datomic.api :as d]
            [datomic.api]
            [rewrite-clj.zip :as z])
  (:import (clojure.lang PersistentList$EmptyList)))

(defn fressian-ize [tx-data]
  (fress/read (fress/write tx-data)))

(defn find-fns [start-pos]
  (loop [pos start-pos
         fns []]
    (if-let [next-fn (some-> pos
                             (z/find-value z/next 'defn)
                             (z/up))]
      (recur (z/right next-fn)
             (conj fns (z/sexpr next-fn)))
      fns)))

(defn find-defs [start-pos]
  (loop [pos start-pos
         defs []]
    (if-let [next-def (some-> pos
                              (z/find-value z/next 'def)
                              (z/up))]
      (recur (z/right next-def)
             (conj defs (z/sexpr next-def)))
      defs)))


(defn read-dbfn [s]
  (edn/read-string
    {:readers {'db/id datomic.db/id-literal
               'db/fn datomic.function/construct}}
    s))

(defn fn-remove-docstring [fn-def]
  (cond (string? (nth fn-def 2))
        (into (vec (take 2 fn-def))
              (vec (drop 3 fn-def)))

        (string? (nth fn-def 3))
        (into (vec (take 3 fn-def))
              (vec (drop 4 fn-def)))

        :else fn-def))


(defn var->file-str [v]
  (if-let [fil (:file (meta v))]
    (let [slurpable (or (io/resource fil)
                        fil)]
      (slurp slurpable))
    (throw (ex-info "No :file for variable" {:var v}))))

(defn file-str->datomic-fn-map [clj-file-str]
  (assert (string? clj-file-str) "clj-file-str must be string")
  (let [fil (z/of-string clj-file-str)
        requires (-> fil
                     (z/find-value z/next 'ns)
                     (z/find-value z/next :require)
                     (z/up)
                     (z/child-sexprs))
        imports (-> fil
                    (z/find-value z/next 'ns)
                    (z/find-value z/next :import)
                    (z/up)
                    (z/child-sexprs))
        all-fns (find-fns fil)
        all-defs (->> (find-defs fil)
                      (mapcat (fn [[_def id val]]
                                [id val]))
                      (vec))
        ident (->> (last all-fns)
                   (drop 1)
                   (filter symbol?)
                   (first))
        other-defns (->> all-fns
                         (butlast)
                         (mapv fn-remove-docstring)
                         (mapv (fn [[_defn id args & body]]
                                 (list id args (apply list 'do body)))))
        params (some-> fil
                       (z/find-value z/next ident)
                       (z/right)
                       (z/sexpr))
        code (some-> fil
                     (z/find-value z/next ident)
                     (z/remove)
                     (z/remove)
                     (z/down)
                     (z/remove)
                     (z/edit (fn [n]
                               (list
                                 'let all-defs
                                 (list 'letfn other-defns
                                       (list 'let ['genfn-coerce-arg
                                                   `(fn [~'x]
                                                      (clojure.walk/prewalk
                                                        (fn [~'e]
                                                          (if (some? ~'e)
                                                            (do
                                                              (when (instance? clojure.lang.PersistentTreeMap ~'e)
                                                                (throw (ex-info "Using sorted-map will cause different types in transactor for in-mem and remote" {:val ~'e})))
                                                              (when (var? ~'e)
                                                                (throw (ex-info "Using var does not work for remote transactor" {:val ~'e})))
                                                              (when (or (= PersistentList$EmptyList (.getClass ~'e))
                                                                        (instance? clojure.lang.PersistentList ~'e))
                                                                (throw (ex-info "Using list will cause indistinguishable types in transactor for in-mem and remote" {:val ~'e})))
                                                              (when (instance? clojure.lang.PersistentQueue ~'e)
                                                                (throw (ex-info "Using clojure.lang.PersistentQueue does not work for remote transactor" {:val ~'e})))
                                                              (cond (instance? java.util.HashSet ~'e)
                                                                    (into #{} ~'e)

                                                                    (and (instance? java.util.List ~'e) (not (vector? ~'e)))
                                                                    (vec ~'e)

                                                                    :else
                                                                    ~'e))
                                                            ~'e))
                                                        ~'x))]
                                             (list 'let
                                                   (vec (mapcat (fn [param]
                                                                  [param (list 'genfn-coerce-arg param)])
                                                                (drop 1 params)))
                                                   (apply list 'do n)))))))
                     (z/sexpr))
        db-fn {:lang     "clojure"
               :requires (vec (drop 1 requires))
               :imports  (vec (drop 1 imports))
               :params   params
               :code     code}]
    db-fn))

(comment
  (:code
    (file-str->datomic-fn-map
      "(ns com.github.ivarref.gen-fn)
       (def abc 123)
       (def abc2 223)
       (defn xyz [] (+ 1))
       (defn my-fn [a b] (+ a b))")))

(comment
  (:code
    (file-str->datomic-fn-map
      "(ns some-ns)
               (defn my-fn [db t]
                 [[:db/add \"res\" :e/clazz (.getName (.getClass t))]])")))

(defn file-str->datomic-fn-str [clj-file-str db-fn-name]
  (assert (string? clj-file-str) "clj-file-str must be string")
  (assert (keyword? db-fn-name) "db-fn-name must be keyword")
  (let [out-str (str "{:db/ident " db-fn-name
                     " :db/fn #db/fn "
                     (binding [*print-dup* false
                               *print-meta* false
                               *print-readably* true
                               *print-length* nil
                               *print-level* nil
                               *print-namespace-maps* false]
                       (pr-str (file-str->datomic-fn-map clj-file-str)))
                     "}")]
    out-str))

(defn patch-string [gen-str db-fn-name fn-str reset?]
  (let [maybe-replace (fn [node]
                        (if reset?
                          (z/replace node {})
                          node))
        patched-value (-> (z/of-string gen-str)
                          (z/find-value z/next 'def)
                          (z/right)
                          (z/right)
                          (maybe-replace)
                          (z/assoc db-fn-name fn-str)
                          (z/find-value z/next db-fn-name))]
    (if (or
          (nil? (z/left* patched-value))
          (and (z/whitespace? (z/left* patched-value))
               (z/whitespace? (z/left* (z/left* patched-value)))))
      (z/root-string patched-value)
      (-> patched-value
          (z/insert-newline-left)
          (z/insert-space-left 16)
          (z/root-string)))))

(defn datomic-fn [db-fn-name fn-var]
  (read-dbfn (file-str->datomic-fn-str (var->file-str fn-var) db-fn-name)))

(defonce lock (Object.))

(defn fn-var->keyword [fn-var]
  (assert (var? fn-var) "fn-var must be variable")
  (or
    (:db/ident (meta fn-var))
    (keyword (str (:ns (meta fn-var))) (name (:name (meta fn-var))))))

(defn gen-fn-str
  [fn-var]
  (assert (var? fn-var) "fn-var must be variable")
  (datomic-fn (fn-var->keyword fn-var) fn-var))

(defn install-fn! [conn fn-var]
  @(d/transact conn [(gen-fn-str fn-var)]))

(defn exec-fn [fn-var & args]
  (reduce into [] [[(fn-var->keyword fn-var)]
                   (into [] args)]))

(defn gen-fn! [db-fn-name fn-var output-file & {:keys [reset?] :or {reset? false}}]
  (assert (var? fn-var) "fn-var must be variable")
  (assert (keyword? db-fn-name) "db-fn-name must be keyword")
  (assert (string? output-file) "output-file must be a string")
  (let [fn-str (file-str->datomic-fn-str (var->file-str fn-var) db-fn-name)]
    (locking lock
      (let [org-file-content (slurp output-file)
            new-file-content (patch-string org-file-content db-fn-name fn-str reset?)]
        (spit output-file new-file-content)
        new-file-content))))
