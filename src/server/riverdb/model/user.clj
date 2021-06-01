(ns riverdb.model.user
  (:require [clojure.tools.logging :as log :refer [debug info warn error]]
            [com.fulcrologic.guardrails.core :refer [>defn => | ?]]
            [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
            [com.fulcrologic.rad.database-adapters.datomic :as datomic]
            [datomic.api :as d]
            [riverdb.state :as st :refer [db cx]]
            [buddy.sign.jwt :as jwt]
            [buddy.core.hash :as hash]
            [buddy.hashers :as hashers]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [thosmos.util :as tu]
            [riverdb.auth :as auth]
            [riverdb.rad.model.db-queries :as queries]))

(defn return-user [user]
  (-> user
    (select-keys [:user/name :user/email :user/uuid :user/role :user/agency])))

(defn pull-email->user
  ([email]
   (pull-email->user email '[*]))
  ([email query]
   (let [eid (d/q '[:find ?e .
                    :in $ ?email
                    :where [?e :user/email ?email]]
               (db) email)
         user (when eid
                (d/pull (db) query eid))]
     user)))

(defn set-name [current-user name]
  (if-let [email (:user/email current-user)]
    (let [tx   (try @(d/transact (cx) [{:user/email email :user/name name}])
                    (catch Exception ex (.getMessage ex)))
          user (when (map? tx)
                 (return-user (assoc current-user :user/name name)))]
      (if user
        {:success true
         :msg     "name changed"
         :user    user}
        {:success false
         :msg     (str "DB TX failed: " tx)}))
    {:success false
     :msg     "unauthorized access"}))

(defn set-password
  ([current-user password]
   (if-let [email (:user/email current-user)]
     (let [password (auth/hash-password password)
           tx   (try @(d/transact (cx) [{:user/email email :user/password password}])
                     (catch Exception ex (.getMessage ex)))
           user (when (map? tx)
                  (return-user current-user))]
       (if user
         {:success true
          :msg     "password changed"
          :user    user}
         {:success false
          :msg     (str "DB TX failed: " tx)}))
     {:success false
      :msg     "unauthorized access"})))

(defn new-user
  ([user-data]
   (let [{:user/keys [email password]} user-data]
     (if-not (and email password)
       (throw (Exception. "user must have both an email and a password"))
       (let [user (merge user-data {:user/uuid (d/squuid)
                                    :user/password (auth/hash-password password)})]
         (log/debug "NEW USER" user)
         (d/transact (cx) [user]))))))

(>defn all-user-ids
  "Returns a sequence of UUIDs for all of the active accounts in the system"
  [db query-params]
  [any? map? => (s/coll-of uuid? :kind vector?)]
  (let [agency (:user/agency query-params)]
    (if agency
      (d/q '[:find [?u ...]
             :in $ ?ag
             :where
             [?e :user/agency ?ag]
             [?e :user/uuid ?u]] db agency)
      (d/q '[:find [?u ...]
             :where
             [_ :user/uuid ?u]] db))))


(defresolver all-users-resolver [{:keys [query-params] :as env} input]
  {;;GIVEN nothing (e.g. this is usable as a root query)
   ;; I can output all accounts. NOTE: only ID is needed...other resolvers resolve the rest
   ::pc/output [{:all-users [:user/uuid]}]}

  {:all-users (if-let [db (some-> (get-in env [::datomic/databases :production]) deref)]
                (mapv
                  (fn [id] {:user/uuid id})
                  (all-user-ids db query-params))
                (log/error "No database atom for production schema!"))})

(defresolver all-role-types [{:keys [db] :as env} input]
  {::pc/output [{:role/role-types [:db/ident :role.type/uuid :role.type/label]}]}
  (log/debug "RESOLVE :role/role-types")
  {:role/role-types (queries/get-all-role-types env)})

(>defn get-account [db id subquery]
  [any? uuid? vector? => (? map?)]
  (d/pull db subquery [:user/uuid id]))

(defresolver role-resolver [env {:user/keys [uuid]}]
  {::pc/input  #{:user/uuid}
   ::pc/output [{:user/role [:role.type/uuid :db/ident :role.type/label]}]}
  {:user/role (if-let [db (some-> (get-in env [::datomic/databases :production]) deref)]
                (d/q '[:find (pull ?r [*]) .
                       :in $ ?u
                       :where
                       [?e :user/uuid ?u]
                       [?e :user/role ?r]] db uuid)
                (log/error "No database atom for production schema!"))})

(defresolver account-resolver [env {:user/keys [uuid]}]
  {::pc/input  #{:user/uuid}
   ::pc/output [:user/email :user/verified? :user/name :user/agency :user/projects]}
  (if-let [db (some-> (get-in env [::datomic/databases :production]) deref)]
    (get-account db uuid [:user/email :user/verified? :user/name {:user/agency [:agencylookup/AgencyCode :agencylookup/uuid]} :user/projects])
    (log/error "No database atom for production schema!")))


(def resolvers [all-users-resolver account-resolver role-resolver all-role-types])