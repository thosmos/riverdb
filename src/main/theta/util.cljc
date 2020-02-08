(ns theta.util
  #?(:cljs (:require-macros [theta.util]))
  #?(:clj (:require [dotenv])))

#?(:clj (defmacro get-app-env [] dotenv/app-env))
#?(:clj (println "APP_ENV" dotenv/app-env))

#?(:cljs (def app-env (theta.util/get-app-env)))
#?(:cljs (println "APP_ENV" app-env))