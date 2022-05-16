(ns com.github.ivarref.gen-fn-test
  (:require [clojure.test :refer [deftest is]]
            [rewrite-clj.zip :as z]
            [com.github.ivarref.generated :as gen]
            [com.github.ivarref.gen-fn :as dbfn]
            [clojure.java.io :as io]))


(defn slurp-ns [sym]
  (let [fil (:file (meta (requiring-resolve sym)))]
    #_(if ())))

#_(def data-string (slurp (io/resource (:file (meta (requiring-resolve 'com.github.ivarref.generated/generated))))))

#_((requiring-resolve 'com.github.ivarref.gen-fn/generate-function)
   #'my-fn
   :my-fn
   (requiring-resolve 'com.github.ivarref.generated/generated))

#_(def data-string (str #_"(ns com.github.ivarref.gen-fn-test\n  (:require [clojure.test :refer [deftest is]]\n            [rewrite-clj.zip :as z]))\n"
                         "(def a {})"))

(defn patch-string [generated-string fn-db-name fn-str]
  (let [patched-value (-> (z/of-string generated-string)
                          (z/find-value z/next 'def)
                          (z/right)
                          (z/right)
                          (z/assoc fn-db-name fn-str)
                          (z/find-value z/next fn-db-name))]
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

(comment
  (-> (patch-string data-string
                    :hello/fn
                    (dbfn/generate-function #'patch-string :hello/fn))
      (patch-string
        :hello/asdf
        (dbfn/generate-function #'patch-string :hello/asdf))
      #_(patch-string
          :hello/asdf
          (dbfn/generate-function #'patch-string :hello/asdf))
      #_(patch-string
          :hello/asdf
          (dbfn/generate-function #'patch-string :hello/asdf))
      (println)))

(comment
  (-> (z/of-string "{:a 123\n  :b 999}")
      (z/find-value z/next :a)
      (z/left*)))



#_(defn)

#_(def zloc (z/of-string data-string))

#_(comment
    (-> zloc
        (z/find-value z/next 'def)
        (z/right)
        (z/right)
        (z/assoc :a "a")
        (z/assoc :b "b")
        (z/assoc :c "c")
        (z/root-string)
        (println)
        #_(z/right)
        #_(z/node)))

(comment)

