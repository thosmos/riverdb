(ns riverdb.model.user
  (:require [clojure.tools.logging :as log :refer [debug info warn error]]
            [com.fulcrologic.guardrails.core :refer [>defn => | ?]]
            [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
            [datomic.api :as d]
            [riverdb.state :as st :refer [db cx]]
            [buddy.sign.jwt :as jwt]
            [buddy.core.hash :as hash]
            [buddy.hashers :as hashers]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [thosmos.util :as tu]))

(defn return-user [user]
  (-> user
    (select-keys [:user/name :user/email :user/uuid :user/roles])))

(defn pull-email->user
  ([email]
   (pull-email->user email '[*]))
  ([email query]
   (let [eid (d/q '[:find ?e .
                    :in $ ?email
                    :where [?e :user/email ?email]]
               (db) email)]
     (when eid
       (d/pull (db) query eid)))))

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
     (let [tx   (try @(d/transact (cx) [{:user/email email :user/password password}])
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

(>defn all-user-ids
  "Returns a sequence of UUIDs for all of the active accounts in the system"
  [db]
  [any? => (s/coll-of uuid? :kind vector?)]
  (d/q '[:find [?v ...]
         :where
         [_ :user/uuid ?v]]
    db))

(defresolver all-users-resolver [{:keys [db]} input]
  {;;GIVEN nothing (e.g. this is usable as a root query)
   ;; I can output all accounts. NOTE: only ID is needed...other resolvers resolve the rest
   ::pc/output [{:all-users [:user/uuid]}]}
  {:all-users (mapv
                (fn [id] {:user/uuid id})
                (all-user-ids db))})

(>defn get-account [db id subquery]
  [any? uuid? vector? => (? map?)]
  (d/pull db subquery [:user/uuid id]))

(defresolver account-resolver [{:keys [db] :as env} {:user/keys [uuid]}]
  {::pc/input  #{:user/uuid}
   ::pc/output [:user/email :user/verified? :user/name :user/roles]}
  (get-account db uuid [:user/email :user/verified? :user/name :user/roles]))

(def resolvers [all-users-resolver account-resolver])