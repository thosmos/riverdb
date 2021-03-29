(ns riverdb.rad.middleware
  (:require
    [com.fulcrologic.rad.middleware.save-middleware :as r.s.middleware]
    [com.fulcrologic.rad.database-adapters.datomic :as datomic]
    ;[com.example.components.datomic :refer [datomic-connections]]
    [com.fulcrologic.rad.blob :as blob]
    [riverdb.rad.model :as model]
    [theta.log :as log]))

(defmethod r.s.middleware/rewrite-value
  :person/uuid
  [save-env ident save-diff]
  (log/debug "REWRITE PERSON" ident save-diff)
  save-diff)

(def save
  (->
    (datomic/wrap-datomic-save)
    (r.s.middleware/wrap-rewrite-delta
      (fn [pathom-env delta]
        (log/debug "SAVE DELTA" delta)))
    (r.s.middleware/wrap-rewrite-values)))

(def delete
  (->
    (datomic/wrap-datomic-delete)))