(ns riverdb.ui.util
  (:require
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    ["shortid" :as shortid]))

(defn shortid []
  (.generate shortid))

(defn make-tempid []
  (tempid/tempid (str "t" (.generate shortid))))

