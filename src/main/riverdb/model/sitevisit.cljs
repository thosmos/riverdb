(ns riverdb.model.sitevisit
  (:require
    [cljs.spec.alpha :as s]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.ui-state-machines :as uism]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [theta.log :refer [debug info]]))

(s/def ::SiteVisitID int?)

(uism/defstatemachine sv-sm
  {::uism/actors
   #{:actor/sv :actor/list}

   ::uism/aliases
   {:visitors     [:actor/sv :sitevisit/Visitors]
    :worktime     [:actor/sv :worktime/_sitevisit]
    :error        [:actor/sv :ui/error]}


   ::uism/states
   {:initial
    {;::uism/target-states #{:state/edit :state/new}
     ::uism/events {::uism/started    {::uism/handler
                                       (fn [{::uism/keys [event-data] :as env}]
                                         (debug "SV UISM STARTED" env)
                                         env

                                         #_(-> env
                                             (uism/store :config event-data)
                                             (uism/assoc-aliased :error "")
                                             (uism/load ::current-session :actor/current-session
                                               {::uism/ok-event    :event/complete
                                                ::uism/error-event :event/failed}))
                                         #_(uism/activate env :state/edit))}

                    :event/begin-edit {::uism/handler (fn [env]
                                                        (debug "SV UISM EDIT")
                                                        (-> env
                                                          (uism/assoc-aliased :error "EDIT ERROR")
                                                          (uism/activate :state/edit)))}

                    :event/new        {::uism/handler (fn [{::uism/keys [event-data] :as env}]
                                                        (debug "SV UISM NEW")
                                                        env
                                                        ;; prep for new SV
                                                        ;; load visitors & monitorhours
                                                        #_(uism/activate env :state/edit)
                                                        #_(-> env
                                                            (uism/store :config event-data)
                                                            (uism/assoc-aliased :error "")
                                                            (uism/load ::current-session :actor/current-session
                                                              {::uism/ok-event    :event/complete
                                                               ::uism/error-event :event/failed})))}
                    :event/failed     {::uism/target-state :state/list}}}

    :state/edit
    {::uism/events {:event/add-visitor {::uism/handler (fn [env]
                                                         (-> env
                                                           ((fn [env] (debug "Add Visitor" env)))
                                                           (uism/assoc-aliased :error "")))}}}}})
