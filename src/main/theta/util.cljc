(ns theta.util
  #?(:cljs (:require-macros [theta.util]))
  #?(:clj (:require [dotenv])))

#?(:clj (defmacro app-env [] dotenv/app-env))
(println "APP_ENV" (theta.util/app-env))
