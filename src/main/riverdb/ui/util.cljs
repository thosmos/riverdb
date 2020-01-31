(ns riverdb.ui.util
  (:require
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    ["shortid" :as shorty :refer [generate]]))

(defn shortid []
  (shorty))

(defn make-tempid []
  (tempid/tempid (str "t" (generate))))

