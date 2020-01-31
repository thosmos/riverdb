(ns riverdb.model.app
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
    [riverdb.ui.routes :as routes]
    [riverdb.ui.project-years :as py]
    [theta.log :refer [debug info]]
    [com.fulcrologic.fulcro.algorithms.denormalize :as fdn]
    [com.fulcrologic.fulcro.data-fetch :as df]))

(uism/defstatemachine app-machine
  {::uism/actors
   #{:actor/login-form :actor/current-session :actor/project-years}

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
