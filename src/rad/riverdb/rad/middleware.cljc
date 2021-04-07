(ns riverdb.rad.middleware
  (:require
    [com.fulcrologic.rad.middleware.save-middleware :as r.s.middleware]
    [com.fulcrologic.rad.database-adapters.datomic :as datomic]
    ;[com.example.components.datomic :refer [datomic-connections]]
    [com.fulcrologic.rad.blob :as blob]
    [riverdb.rad.model :as model]
    [riverdb.auth :as auth]
    [theta.log :as log]))

(defmethod r.s.middleware/rewrite-value
  :user/uuid
  [save-env ident save-diff]
  ;(log/debug "REWRITE USER" ident)
  (if-let [pass-diff (:user/password save-diff)]
    (let [hashed (auth/hash-password (:after pass-diff))
          new-diff (assoc-in save-diff [:user/password :after] hashed)]
      (log/debug "REWRITE PASSWORD!" new-diff)
      new-diff)
    save-diff))

(def save
  (->
    (datomic/wrap-datomic-save)
    (r.s.middleware/wrap-rewrite-delta
      (fn [pathom-env delta]
        #_(log/debug "SAVE DELTA")))
    (r.s.middleware/wrap-rewrite-values)))

(def delete
  (->
    (datomic/wrap-datomic-delete)))