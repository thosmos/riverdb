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
    [riverdb.api.mutations :as rm]
    [riverdb.application :refer [SPA]]
    [riverdb.roles :as roles]
    [riverdb.ui.globals :as globals]
    [riverdb.ui.routes :as routes]
    [riverdb.ui.project-years :as py]
    [theta.log :refer [debug info]]
    [com.fulcrologic.fulcro.algorithms.denormalize :as fdn]
    [com.fulcrologic.fulcro.data-fetch :as df]))

(defn clear [env]
  (uism/assoc-aliased env :error ""))

(defn sesh->user [sesh]
  (get-in sesh [:account/auth :user]))

(defn logout [env]
  (comp/transact! SPA `[(rm/clear-tac-report) (rm/clear-agency-project-years)])
  (routes/route-to! "/")
  ;(dr/change-route SPA ["main"])
  (-> env
    (clear)
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
    (debug "SESSION RESULT " (keys (::uism/state-map env)))
    (if success?
      (let [{:keys [desired-path]} (uism/retrieve env :config)]
        (debug "ROUTE TO existing path?" desired-path)
        (if desired-path
          (routes/route-to! desired-path)
          (routes/route-to! "/")))
      (routes/route-to! "/"))



    (cond-> (clear env)
      true (-> (assoc-in [::uism/state-map :root/ready] true))
      success? (->
                 ((fn [env]
                    (let [actor-data (fdn/db->tree
                                       (comp/get-query (uism/actor-class env :actor/current-session))
                                       (uism/actor->ident env :actor/current-session)
                                       (::uism/state-map env))
                          globals    (get actor-data [:component/id :globals])
                          _ (debug "GLOBALS" globals)
                          user       (get-in actor-data [:account/auth :user])
                          roles      (roles/user->roles user)
                          agencies   (when roles (roles/roles->agencies2 roles))
                          agency     (first agencies)
                          agencyCode (:agencylookup/AgencyCode agency)]
                      (df/load! SPA :agency-project-years nil
                        {:params        {:agencies [agencyCode]}
                         :target        [:component/id :proj-years :agency-project-years]
                         :post-mutation `rm/process-project-years})
                      #_(df/load! SPA [:component/id :globals] globals/Globals)
                      (debug "AUTH STUFF" agencyCode (keys env))
                      (assoc-in env [::uism/state-map :riverdb.ui.root/current-agency]
                        [:org.riverdb.db.agencylookup/gid (:db/id agency)]))))

                 (uism/assoc-aliased :modal-open? false)
                 (uism/activate :state/logged-in))
      (not success?) (->
                       (uism/assoc-aliased :error error-message)
                       (uism/activate :state/logged-out)))))

(def global-events
  {:event/toggle-modal {::uism/handler (fn [env] (uism/update-aliased env :modal-open? not))}})

(uism/defstatemachine session-machine
  {::uism/actors
   #{:actor/login-form :actor/current-session :actor/project-years :actor/globals}

   ::uism/aliases
   {:username       [:actor/login-form :account/email]
    :error          [:actor/login-form :ui/error]
    :modal-open?    [:actor/login-form :ui/open?]
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
                                       ::uism/handler       #(process-session-result % "Invalid Credentials.")}})}

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
