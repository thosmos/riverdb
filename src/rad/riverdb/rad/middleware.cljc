(ns riverdb.rad.middleware
  (:require
    [com.fulcrologic.rad.middleware.save-middleware :as r.s.middleware]
    [com.fulcrologic.rad.database-adapters.datomic :as datomic]
    ;[com.example.components.datomic :refer [datomic-connections]]
    [com.fulcrologic.rad.blob :as blob]
    [riverdb.rad.model :as model]))

(def middleware
  (->
    (datomic/wrap-datomic-save)
    (datomic/wrap-datomic-delete)
    (r.s.middleware/wrap-rewrite-values)))