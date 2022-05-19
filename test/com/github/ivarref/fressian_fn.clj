(ns com.github.ivarref.fressian-fn)

(defn fressian [db e arg]
  [[:db/add e :e/bool (vector? arg)]])
