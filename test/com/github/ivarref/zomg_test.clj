(ns com.github.ivarref.zomg-test
  (:require [clojure.test :refer :all]
            [rewrite-clj.zip :as z]))

(require '[com.github.ivarref.gen-fn :refer [gen-fn!]])
(require '[com.github.ivarref.my-add :refer [my-add]])

(println "**********")
(gen-fn! #'my-add :my-add "test/com/github/ivarref/generated.clj")
(println "**********")

(comment
  (do
    (require '[rewrite-clj.zip :as z])
    (require '[rewrite-clj.node :as n])
    (let [s (-> (z/of-string "(def a {})")
                (z/find-value z/next 'a)
                (z/right)
                (z/replace "xyz: \"quoted\" xyz:")
                (z/root-string))])))
