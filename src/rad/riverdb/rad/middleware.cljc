(ns riverdb.rad.middleware
  (:require
    [com.fulcrologic.rad.middleware.save-middleware :as r.s.middleware]
    [com.fulcrologic.rad.database-adapters.datomic :as datomic]
    ;[com.example.components.datomic :refer [datomic-connections]]
    [com.fulcrologic.rad.blob :as blob]
    [riverdb.rad.model :as model]))

(def save
  (->
    (datomic/wrap-datomic-save)
    (r.s.middleware/wrap-rewrite-values)))

(def delete
  (->
    (datomic/wrap-datomic-delete)))