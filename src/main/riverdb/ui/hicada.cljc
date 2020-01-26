(ns riverdb.ui.hicada
  #?(:clj (:require [hicada.compiler]))
  #?(:cljs (:require-macros [riverdb.ui.hicada])))

#?(:clj (defmacro html
          [body]
          (hicada.compiler/compile body {:create-element  'js/React.createElement
                                         :transform-fn    (comp)
                                         :array-children? false}
            {} &env)))