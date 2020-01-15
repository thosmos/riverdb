(ns riverdb.model.user
  (:require
    [taoensso.timbre :as log]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]))

(defn user-path
  "Normalized path to a user entity or field in Fulcro state-map"
  ([id field] [:user/uuid id field])
  ([id] [:user/uuid id]))

(defn insert-user*
  "Insert a user into the correct table of the Fulcro state-map database."
  [state-map {:keys [:user/uuid] :as user}]
  (assoc-in state-map (user-path uuid) user))

(defmutation upsert-user
  "Client Mutation: Upsert a user (full-stack. see CLJ version for server-side)."
  [{:keys [:user/uuid :user/name] :as params}]
  (action [{:keys [state]}]
    (log/info "Upsert user action")
    (swap! state (fn [s]
                   (-> s
                     (insert-user* params)
                     (targeting/integrate-ident* [:user/uuid uuid] :append [:all-accounts])))))
  (ok-action [env]
    (log/info "OK action"))
  (error-action [env]
    (log/info "Error action"))
  (remote [env]
    (-> env
      (m/returning 'riverdb.ui.user/User)
      (m/with-target (targeting/append-to [:all-accounts])))))

