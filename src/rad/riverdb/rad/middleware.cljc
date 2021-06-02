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


(comment
  {[:stationlookup/uuid #uuid "5e3e50f1-5ec5-4e4c-8894-e75692f5cb97"]
   {:projectslookup/_Stations
    {:before
     [[:projectslookup/uuid #uuid "5e3e50f0-cc69-4738-92ff-9733307acb56"]
      [:projectslookup/uuid #uuid "5e3e50f0-3b3b-465a-a06a-a961bb0688a4"]],
     :after
     [[:projectslookup/uuid #uuid "5e3e50f0-cc69-4738-92ff-9733307acb56"]
      [:projectslookup/uuid #uuid "5e3e50f0-3b3b-465a-a06a-a961bb0688a4"]
      [:projectslookup/uuid #uuid "5e3e50f0-de49-422a-a98b-4950335c4a0a"]]}}})

(def reverse-keys {:stationlookup/uuid [:projectslookup/_Stations]})
(def reverse-keyers (set (keys reverse-keys)))

(defn reverse-many-refs [env delta]
  (reduce-kv
    (fn [delta k v]
      (let [keyer? (contains? reverse-keyers k)]
        delta))
    delta delta)

  #_(when-let [keyers (some reverse-keyers (map first (keys delta)))]
      (reduce
        (fn [delta keyer]
          (let [rkeys (get reverse-keys keyer)]
            delta))
        delta keyers)))


(def save
  (->
    (datomic/wrap-datomic-save)
    (r.s.middleware/wrap-rewrite-delta
      (fn [pathom-env delta]
        (log/debug "SAVE DELTA" delta)
        (reverse-many-refs pathom-env delta)))

        ;(rewrite-gids pathom-env delta)))


    (r.s.middleware/wrap-rewrite-values)))

(def delete
  (->
    (datomic/wrap-datomic-delete)))