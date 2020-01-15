(ns riverdb.api.mutations
  (:require
;    [fulcro.server :as oms]
    [taoensso.timbre :as timbre]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    ;[com.fulcrologic.fulcro.server :as server]
;    [rimdb-ui.api.user-db :as users]
    [riverdb.api.tac-report :as tac]
    [riverdb.api.riverdb]
    [datomic.api :as d]))

;;;;  SERVER

;(defn commit-new [user-db [table id] entity]
;  (log/info "Committing new " table entity)
;  (case table
;    :user/by-id (users/add-user user-db entity)
;    {}))

;(defmethod core/server-mutate 'fulcro.ui.forms/commit-to-entity [{:keys [user-db]} k {:keys [form/new-entities] :as p}]
;  {:action (fn []
;             (log/info "Commit entity: " k p)
;             (when (seq new-entities)
;               {:tempids (reduce (fn [remaps [k v]]
;                                   (log/info "Create new " k v)
;                                   (merge remaps (commit-new user-db k v))) {} new-entities)}))})
;
;(defmutation logout
;  "Server mutation: Log the given UI out. This mutation just removes the session, so that the server won't recognize
;  the user anymore."
;  [ignored-params]
;  ; if you wanted to directly access the session store, you can
;  (action [{:keys [request session-store user-db]}]
;    (let [uid  (-> request :session :uid)
;          user (users/get-user user-db uid)]
;      (timbre/info "Logout for user: " uid)
;      (server/augment-response {}
;        (fn [resp] (assoc resp :session nil))))))
;
;;(defmutation import-data-entry
;;  [params]
;;  (action [{:keys [request datomic user-db]}]
;;    (let [cx     (:cx datomic)
;;          uid    (-> request :session :uid)
;;          user   (users/get-user user-db uid)
;;          agency (:agency user)
;;          name   (:name user)]
;;      (timbre/debug "IMPORTING RIMDB DATA for " agency " by " name)
;;      (rimdb-ui.api.rimdb/migrate-rimdb cx)
;;      )))

;(defmutation begin-tac-report
;  "begin the TAC Report"
;  [env]
;  (action [{:keys [request datomic]}]
;    (let [cx (:cx datomic)]
;      (tac/begin-query cx))))
