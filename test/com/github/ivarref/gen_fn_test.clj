(ns com.github.ivarref.gen-fn-test
  (:require [clojure.test :refer [deftest is]]))

(require '[com.github.ivarref.gen-fn :refer [gen-fn!]])
(require '[com.github.ivarref.my-add :refer [my-add]])

(def output-file "test/com/github/ivarref/generated.clj")

(deftest gen-fn-test
  (spit output-file (slurp "test/com/github/ivarref/generated.txt"))
  (gen-fn! :my-add #'my-add output-file)
  #_(gen-fn! #'my-add :my-add-2 output-file)
  #_(gen-fn! #'my-add :my-add-3 output-file))
