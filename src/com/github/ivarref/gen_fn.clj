(ns com.github.ivarref.gen-fn
  (:require [datomic.api]
            [rewrite-clj.zip :as z]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))


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
    {:readers {'db/id  datomic.db/id-literal
               'db/fn  datomic.function/construct}}
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


(defn file-str->datomic-fn-str [clj-file-str db-fn-name]
  (assert (string? clj-file-str) "clj-file-str must be string")
  (assert (keyword? db-fn-name) "db-fn-name must be keyword")
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
                                       (apply list 'do n)))))
                     (z/sexpr))
        db-fn {:lang     "clojure"
               :requires (vec (drop 1 requires))
               :imports  (vec (drop 1 imports))
               :params   params
               :code     code}
        out-str (str "{:db/ident " db-fn-name
                     " :db/fn #db/fn "
                     (binding [*print-dup* false
                               *print-meta* false
                               *print-readably* true
                               *print-length* nil
                               *print-level* nil
                               *print-namespace-maps* false]
                       (pr-str db-fn))
                     "}")]
    out-str))


(defn patch-string [generated-string db-fn-name fn-str]
  (let [patched-value (-> (z/of-string generated-string)
                          (z/find-value z/next 'def)
                          (z/right)
                          (z/right)
                          (z/assoc db-fn-name fn-str)
                          (z/find-value z/next db-fn-name))]
    (def p patched-value)
    (if (or
          (nil? (z/left* patched-value))
          (and (z/whitespace? (z/left* patched-value))
               (z/whitespace? (z/left* (z/left* patched-value)))))
      (z/root-string patched-value)
      (-> patched-value
          (z/insert-newline-left)
          (z/insert-space-left 16)
          (z/root-string)))))

(defonce lock (Object.))

(defn gen-fn! [fn-var db-fn-name output-file]
  (assert (var? fn-var) "fn-var must be variable")
  (assert (keyword? db-fn-name) "db-fn-name must be keyword")
  (assert (string? output-file) "output-file must be a string")
  (let [fn-str (file-str->datomic-fn-str (var->file-str fn-var) db-fn-name)]
    (locking lock
      (let [org-file (slurp output-file)]
        (spit output-file (patch-string org-file db-fn-name fn-str))))))
