(ns riverdb.rad.middleware
  (:require
    [com.fulcrologic.rad.middleware.save-middleware :as r.s.middleware]
    [com.fulcrologic.rad.database-adapters.datomic :as datomic]
    ;[com.example.components.datomic :refer [datomic-connections]]
    [com.fulcrologic.rad.blob :as blob]
    [riverdb.rad.model :as model]
    [riverdb.auth :as auth]
    [theta.log :as log]
    [theta.util]
    [datomic.api :as d]))

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

;(defmethod r.s.middleware/rewrite-value
;  :org.riverdb.db.worktime/gid
;  [save-env ident save-diff]
;  (log/debug "REWRITE WORKTIME" ident)
;  (if-let [pass-diff (:user/password save-diff)]
;    (let [hashed (auth/hash-password (:after pass-diff))
;          new-diff (assoc-in save-diff [:user/password :after] hashed)]
;      (log/debug "REWRITE PASSWORD!" new-diff)
;      new-diff)
;    save-diff))

(defn get-uuident [env gid uuid-k]
  (if-let [db (some-> (get-in env [::datomic/databases :production]) deref)]
    (let [uuid (d/q '[:find ?u
                      :in $ ?gid ?uuid-k
                      :where
                      [?gid ?uuid-k ?u]])]
      (if uuid
        [uuid-k uuid]
        (log/error "Failed to find uuid for gid!" gid)))

    (log/error "No database atom for production schema!")))

(defn rewrite-gids [env delta]
  (let [result (into {}
                 (for [[ident diff] delta]
                   (do
                     (log/debug "doing" ident diff)
                     (if-let [ident-ns (namespace (first ident))]
                       (let [[id-k id] ident]
                         (if (= id-k :org.riverdb.db.worktime/gid)
                           (let [id (if (= (type id) java.lang.String)
                                      (theta.util/parse-long id)
                                      id)
                                 uuident (get-uuident env id (keyword "worktime" "uuid"))]
                             (log/debug "returning GID save" [uuident diff])
                             [uuident diff])
                           [ident diff]))
                       [ident diff]))))]
    (log/debug "rewrite GIDs" result)
    result))

(def save
  (->
    (datomic/wrap-datomic-save)
    (r.s.middleware/wrap-rewrite-delta
      (fn [pathom-env delta]
        (log/debug "SAVE DELTA" delta)))
        ;(rewrite-gids pathom-env delta)))


    (r.s.middleware/wrap-rewrite-values)))

(def delete
  (->
    (datomic/wrap-datomic-delete)))