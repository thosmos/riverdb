(ns riverdb.model.session
  (:require
    ;[riverdb.model.mock-database :as db]
    [riverdb.auth :as auth]
    [riverdb.state :as st :refer [db cx]]
    [riverdb.model.user :as user]
    [com.fulcrologic.guardrails.core :refer [>defn => | ?]]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [taoensso.timbre :as log]
    [clojure.spec.alpha :as s]
    [com.fulcrologic.fulcro.server.api-middleware :as fmw]
    [thosmos.util :as tu]))

(defonce account-database (atom {}))

(defresolver current-session-resolver [env input]
  {::pc/output [{::current-session [:session/valid? :account/name :account/auth]}]}
  (let [{:keys [account/name session/valid? account/auth]} (get-in env [:ring/request :session])]
    (if valid?
      (do
        (log/info name "already logged in!")
        {::current-session {:session/valid? true :account/name name :account/auth auth}})
      {::current-session {:session/valid? false}})))

(defn response-updating-session
  "Uses `mutation-response` as the actual return value for a mutation, but also stores the data into the (cookie-based) session."
  [mutation-env mutation-response]
  (let [existing-session (some-> mutation-env :ring/request :session)]
    (fmw/augment-response
      mutation-response
      (fn [resp]
        (let [new-session (merge existing-session mutation-response)
              token       (:token mutation-response)]
          (-> resp
            (assoc :session new-session)
            (assoc :cookies {"riverdb-auth-token" {:value token}})))))))

(defmutation login [env {:keys [username password] :as args}]
  {::pc/output [:session/valid? :session/error :account/name :account/auth]}
  (log/info "Authenticating" username)
  (let [user? (user/pull-email->user username
                [:db/id :user/uuid :user/name :user/email :user/password
                 {:user/role [:db/id
                              :db/ident
                              :role.type/label
                              :role.type/uuid]}
                 {:user/agency [:db/id :agencylookup/AgencyCode :agencylookup/Name :agencylookup/uuid]}
                 #_{:user/roles '[* {:role/agency [:db/id :agencylookup/AgencyCode :agencylookup/Name :agencylookup/uuid]}]}])
        res   (when user?
                (auth/auth-password {:email username :password password :verify (:user/password user?)}))
        _     (log/debug "AUTH RESULT" res)]
    (if (:success res)
      (let [res (-> res
                  (dissoc :success)
                  (dissoc :msg)
                  (assoc :user (dissoc user? :user/password)))]
        (response-updating-session env
          {:session/valid? true
           :account/name   username
           :account/auth   res}))
      (do
        (log/error "Invalid credentials supplied for" username)
        {:session/valid? false
         :account/name username
         :session/error (str "Invalid credentials")}))))
;        (throw (ex-info "Invalid credentials" {:username username}))))))

(defmutation logout [env params]
  {::pc/output [:session/valid?]}
  (response-updating-session env {:session/valid? false :account/name "" :account/auth nil}))

;(defmutation signup! [env {:keys [email password]}]
;  {::pc/output [:signup/result]}
;  (swap! account-database assoc email {:email    email
;                                       :password password})
;  {:signup/result "OK"})

(def resolvers [current-session-resolver login logout])
