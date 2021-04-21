(ns riverdb.model.session
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as uism]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.rad.routing.html5-history :as hist5 :refer [html5-history]]
    [com.fulcrologic.rad.routing.history :as history]
    [riverdb.api.mutations :as rm]
    [riverdb.application :refer [SPA]]
    [riverdb.roles :as roles]
    [riverdb.ui.agency :as agency]
    [riverdb.ui.globals :as globals]
    [riverdb.ui.routes :as routes]
    [riverdb.ui.project-years :as py]
    [theta.log :refer [debug info]]
    [com.fulcrologic.fulcro.algorithms.denormalize :as fdn]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.rad.routing :as rroute]))

(defn clear [env]
  (uism/assoc-aliased env :error ""))

(defn sesh->user [sesh]
  (get-in sesh [:account/auth :user]))

(defn logout [env]
  (comp/transact! SPA `[(rm/clear-tac-report) (rm/clear-agency-project-years)])
  (dr/change-route! SPA ["main"])
  (history/push-route! SPA ["main"] {})

  (-> env
    (clear)
    (update ::uism/state-map
      (fn [st]
        (-> st
          (dissoc :ui.riverdb/current-agency)
          (dissoc :ui.riverdb/current-project)
          (dissoc :ui.riverdb/current-project-sites)
          (dissoc :ui.riverdb/current-year)
          (dissoc :user/id)
          (dissoc :person/uuid)
          (dissoc :org.riverdb.db.stationlookup/gid)
          (dissoc :org.riverdb.db.agencylookup/gid)
          (dissoc :org.riverdb.db.projectslookup/gid)
          (dissoc :tac-report-data)
          (dissoc :dataviz-data)
          (dissoc :riverdb.theta.options/ns)
          (update-in [:component/id :proj-years] dissoc :agency-project-years)
          (update-in [:component/id :projects] dissoc :projects)
          (update-in [:component/id :sitevisit-list] dissoc :sitevisits))))
    (uism/assoc-aliased :username "" :session-valid? false :current-user "" :account-auth nil)
    (uism/trigger-remote-mutation :actor/login-form 'riverdb.model.session/logout {})
    (uism/activate :state/logged-out)))

(defn login [{::uism/keys [event-data] :as env}]
  ;(do)
  ;((debug "DOING LOGIN, WHAT'S OUR CURRENT ROUTE LIKE?" @routes/match)))
  (-> env
    (clear)
    (uism/trigger-remote-mutation :actor/login-form 'riverdb.model.session/login
      {:username          (:username event-data)
       :password          (:password event-data)
       ::m/returning      (uism/actor-class env :actor/current-session)
       ::uism/ok-event    :event/complete
       ::uism/error-event :event/failed})
    (uism/activate :state/checking-session)))

(defn process-session-result [env error-message]
  (let [success? (uism/alias-value env :session-valid?)]
    (debug "SESSION RESULT" success?)

    (cond-> (clear env)
      true (-> (assoc-in [::uism/state-map :root/ready] true))
      success? (->
                 ((fn [env]
                    (let [actor-data (fdn/db->tree
                                       (comp/get-query (uism/actor-class env :actor/current-session))
                                       (uism/actor->ident env :actor/current-session)
                                       (::uism/state-map env))
                          globals    (get actor-data [:component/id :globals])
                          _          (debug "GLOBALS" globals)
                          user       (get-in actor-data [:account/auth :user])
                          agency     (:user/agency user)
                          ;role
                          ;roles      (roles/user->roles user)
                          ;agencies   (when roles (roles/roles->agencies2 roles))
                          ;agency     (first agencies)
                          agencyCode (or (:agencylookup/AgencyCode agency))
                          agID       (:db/id agency)
                          {:keys [desired-route] :as config} (uism/retrieve env :config)]
                      (df/load! SPA :agency-project-years nil
                        {:params               {:agencies [agencyCode]}
                         :target               [:component/id :proj-years :agency-project-years]
                         :post-mutation        `rm/process-project-years
                         :post-mutation-params {:desired-route desired-route}})
                      (agency/preload-agency agID)
                      #_(df/load! SPA [:component/id :globals] globals/Globals)
                      (debug "AUTH STUFF" agencyCode (keys env))
                      (-> env
                        (uism/store :config (dissoc config :desired-route))
                        (assoc-in [::uism/state-map :ui.riverdb/current-agency]
                          [:org.riverdb.db.agencylookup/gid (:db/id agency)])))))

                 (uism/activate :state/logged-in))
      (not success?) (->
                       ((fn [env]
                          (debug "LOGIN FAILED" "error-message" error-message)
                          env))
                       (uism/assoc-aliased :error error-message)
                       (uism/activate :state/logged-out)))))

(def global-events
  {:event/toggle-modal {::uism/handler (fn [env] (uism/update-aliased env :modal-open? not))}})

(uism/defstatemachine session-machine
  {::uism/actors
   #{:actor/login-form :actor/logout-menu :actor/current-session :actor/project-years :actor/globals}

   ::uism/aliases
   {:username       [:actor/logout-menu :account/email]
    :error          [:actor/login-form :ui/error]
    :session-valid? [:actor/current-session :session/valid?]
    :current-user   [:actor/current-session :account/name]
    :account-auth   [:actor/current-session :account/auth]}

   ::uism/states
   {:initial
    {::uism/target-states #{:state/logged-in :state/logged-out}
     ::uism/events        {::uism/started  {::uism/handler (fn [{::uism/keys [event-data] :as env}]
                                                             (debug "SESSION STARTED")
                                                             (-> env
                                                               (uism/store :config event-data)
                                                               (uism/assoc-aliased :error "")
                                                               (uism/load ::current-session :actor/current-session
                                                                 {::uism/ok-event    :event/complete
                                                                  ::uism/error-event :event/failed})))}
                           :event/failed   {::uism/target-state :state/logged-out}
                           :event/complete {::uism/target-states #{:state/logged-in :state/logged-out}
                                            ::uism/handler       #(process-session-result % "")}}}

    :state/checking-session
    {::uism/events (merge global-events
                     {:event/failed   {::uism/target-states #{:state/logged-out}
                                       ::uism/handler       (fn [env]
                                                              (-> env
                                                                (clear)
                                                                (uism/assoc-aliased :error "Server error.")))}
                      :event/complete {::uism/target-states #{:state/logged-out :state/logged-in}
                                       ::uism/handler       #(process-session-result % "Invalid Credentials")}})}

    :state/logged-in
    {::uism/events (merge global-events
                     {:event/logout {::uism/target-states #{:state/logged-out}
                                     ::uism/handler       logout}})}

    :state/logged-out
    {::uism/events (merge global-events
                     {:event/login    {::uism/target-states #{:state/checking-session}
                                       ::uism/handler       login}
                      :event/complete {::uism/handler (fn [env]
                                                        (debug "LOGGED OUT")
                                                        (dr/change-route! SPA ["main"])

                                                        env)}})}}})


(def signup-ident [:component/id :signup])
(defn signup-class [] (comp/registry-key->class :app.ui.root/Signup))

(defn clear-signup-form*
  "Mutation helper: Updates state map with a cleared signup form that is configured for form state support."
  [state-map]
  (-> state-map
    (assoc-in signup-ident
      {:account/email          ""
       :account/password       ""
       :account/password-again ""})
    (fs/add-form-config* (signup-class) signup-ident)))

(defmutation clear-signup-form [_]
  (action [{:keys [state]}]
    (swap! state clear-signup-form*)))

(defn valid-email? [email] (str/includes? email "@"))
(defn valid-password? [password] (> (count password) 7))

(defmutation signup! [_]
  (action [{:keys [state]}]
    (info "Marking complete")
    (swap! state fs/mark-complete* signup-ident))
  (ok-action [{:keys [app state]}]
    (dr/change-route app ["signup-success"]))
  (remote [{:keys [state] :as env}]
    (let [{:account/keys [email password password-again]} (get-in @state signup-ident)]
      (boolean (and (valid-email? email) (valid-password? password)
                 (= password password-again))))))
