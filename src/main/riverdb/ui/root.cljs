(ns riverdb.ui.root
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h3 button]]
    [com.fulcrologic.fulcro.dom.html-entities :as ent]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.application :as fapp]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as uism :refer [defstatemachine]]
    [com.fulcrologic.fulcro.mutations :as fm :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro-css.css-injection :as inj]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.semantic-ui.elements.loader.ui-loader :refer [ui-loader]]
    [com.fulcrologic.semantic-ui.modules.dimmer.ui-dimmer :refer [ui-dimmer]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form :refer [ui-form]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-input :refer [ui-form-input]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal :refer [ui-modal]]
    [riverdb.application :refer [SPA]]
    [riverdb.model.session :as session]
    [riverdb.roles :as roles]
    [riverdb.api.mutations :as rm :refer [TxResult ui-tx-result]]
    [riverdb.ui.bench :refer [Bench ui-bench]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.dataviz-page :refer [DataVizPage]]
    [riverdb.ui.components :refer [ui-treeview]]
    [riverdb.ui.globals :refer [DBIdents Globals]]
    [riverdb.ui.routes]
    [riverdb.ui.lookup-options :refer [ui-theta-options preload-options]]
    [riverdb.ui.sitevisits-page :refer [SiteVisitsPage]]
    [riverdb.ui.session :refer [ui-session Session]]
    [riverdb.ui.projects :refer [Projects ui-projects]]
    [riverdb.ui.tac-report-page :refer [TacReportPage]]
    [riverdb.ui.theta :refer [ThetaRoot]]
    [riverdb.ui.user]
    ;[riverdb.ui :refer [PersonForm PersonList]]
    [theta.log :as log :refer [debug]]
    [theta.util :as tutil]
    [com.fulcrologic.fulcro.components :as om]
    [com.fulcrologic.fulcro.data-fetch :as f]))


(defn field [{:keys [label valid? error-message] :as props}]
  (let [input-props (-> props (assoc :name label) (dissoc :label :valid? :error-message))]
    (div :.ui.field
      (dom/label {:htmlFor label} label)
      (ui-form-input input-props)
      (dom/div :.ui.error.message {:classes [(when valid? "hidden")]}
        error-message))))

(defsc SignupSuccess [this props]
  {:query         ['*]
   :initial-state {}
   :ident         (fn [] [:component/id :signup-success])
   :route-segment ["signup-success"]}
  (div
    (dom/h3 "Signup Complete!")
    (dom/p "You can now log in!")))

(defsc Signup [this {:account/keys [email password password-again] :as props}]
  {:query             [:account/email :account/password :account/password-again fs/form-config-join]
   :initial-state     (fn [_]
                        (fs/add-form-config Signup
                          {:account/email          ""
                           :account/password       ""
                           :account/password-again ""}))
   :form-fields       #{:account/email :account/password :account/password-again}
   :ident             (fn [] session/signup-ident)
   :route-segment     ["signup"]
   :componentDidMount (fn [this]
                        (comp/transact! this [(session/clear-signup-form)]))}
  (let [submit!  (fn [evt]
                   (when (or (identical? true evt) (evt/enter-key? evt))
                     (comp/transact! this [(session/signup! {:email email :password password})])
                     (log/info "Sign up")))
        checked? (fs/checked? props)]
    (div
      (dom/h3 "Signup")
      (div :.ui.form {:classes [(when checked? "error")]}
        (field {:label         "Email"
                :value         (or email "")
                :valid?        (session/valid-email? email)
                :error-message "Must be an email address"
                :autoComplete  "off"
                :onKeyDown     submit!
                :onChange      #(fm/set-string! this :account/email :event %)})
        (field {:label         "Password"
                :type          "password"
                :value         (or password "")
                :valid?        (session/valid-password? password)
                :error-message "Password must be at least 8 characters."
                :onKeyDown     submit!
                :autoComplete  "off"
                :onChange      #(fm/set-string! this :account/password :event %)})
        (field {:label         "Repeat Password" :type "password" :value (or password-again "")
                :autoComplete  "off"
                :valid?        (= password password-again)
                :error-message "Passwords do not match."
                :onChange      #(fm/set-string! this :account/password-again :event %)})
        (dom/button :.ui.primary.button {:onClick #(submit! true)}
          "Sign Up")))))

(defsc LoginForm [this {:account/keys [email]
                        :ui/keys      [error] :as props}]
  {:ident         (fn [] [:component/id :login-form])
   :query         [:ui/error :account/email
                   {[:component/id :session] (comp/get-query Session)}
                   [::uism/asm-id ::session/session]]
   :initial-state {:account/email "" :ui/error ""}}
  (let [current-state (uism/get-active-state this ::session/session)
        ;{current-user :account/name} (get props [:component/id :session])
        ;initial?      (= :initial current-state)
        loading?      (= :state/checking-session current-state)
        ;logged-in?    (= :state/logged-in current-state)
        password      (or (comp/get-state this :password) "")
        submit-fn     (fn [] (uism/trigger! this ::session/session :event/login {:username email
                                                                                 :password password}))]
    ;submit!  (fn [evt]
    ;           (when (or (identical? true evt) (evt/enter-key? evt))
    ;             (uism/trigger! this ::session/session :event/login {:username email
    ;                                                                 :password password})))]
    (ui-form {:onSubmit #(fn [e]
                           (println "Login Form Submit" e)
                           (submit-fn)
                           (.preventDefault e))
              :classes  [(when (seq error) "error")]}
      (field {:id           "email"
              :label        "Email"
              :value        email
              :autoComplete "on"
              :onChange     #(fm/set-string! this :account/email :event %)})
      (field {:id           "password"
              :type         "password"
              :label        "Password"
              :value        password
              :autoComplete "on"
              :onChange     #(comp/set-state! this {:password (evt/target-value %)})})
      (when error
        "ERROR"
        (div :.ui.error.message error))
      (div :.ui.field
        (dom/button :.ui.button
          {:onClick submit-fn
           :type    "submit"
           :classes [(when loading? "loading")]} "Login")))))

(def ui-login-form (om/factory LoginForm))

(defsc Login [this {:account/keys [email]
                    :ui/keys      [error] :as props}]
  {:query         [:ui/error :account/email
                   {[:component/id :session] (comp/get-query Session)}
                   [::uism/asm-id ::session/session]]
   :css           [[:.floating-menu {:position "absolute !important"
                                     :z-index  1000
                                     :width    "300px"
                                     :right    "0px"
                                     :top      "50px"}]]
   :initial-state {:account/email "" :ui/error ""}
   :ident         (fn [] [:component/id :logout-menu])}
  (let [current-state (uism/get-active-state this ::session/session)
        {current-user :account/name} (get props [:component/id :session])
        initial?      (= :initial current-state)
        loading?      (= :state/checking-session current-state)
        logged-in?    (= :state/logged-in current-state)
        {:keys [floating-menu]} (css/get-classnames Login)
        password      (or (comp/get-state this :password) "")] ; c.l. state for security
    (when-not initial?
      (if logged-in?
        (dom/button :.item
          {:onClick #(uism/trigger! this ::session/session :event/logout)}
          (dom/span current-user) ent/nbsp "Log out")
        #_(dom/div :.item {:style   {:position "relative"}
                           :onClick #(uism/trigger! this ::session/session :event/toggle-modal)}
            "Login"
            (when open?
              (dom/div :.four.wide.ui.raised.teal.segment {:onClick (fn [e]
                                                                      ;; Stop bubbling (would trigger the menu toggle)
                                                                      (evt/stop-propagation! e))
                                                           :classes [floating-menu]}
                (dom/h3 :.ui.header "Login")
                (div :.ui.form {:classes [(when (seq error) "error")]}

                  (field {:label        "Email"
                          :name         "email"
                          :value        email
                          :autoComplete "on"
                          :onChange     #(fm/set-string! this :account/email :event %)})
                  (field {:label        "Password"
                          :name         "password"
                          :type         "password"
                          :autoComplete "on"
                          :value        password
                          :onChange     #(comp/set-state! this {:password (evt/target-value %)})})
                  (div :.ui.error.message error)
                  (div :.ui.field
                    (dom/button :.ui.button
                      {:onClick (fn [] (uism/trigger! this ::session/session :event/login {:username email
                                                                                           :password password}))
                       :classes [(when loading? "loading")]} "Login"))
                  #_(div :.ui.message
                      (dom/p "Don't have an account?")
                      (dom/a {:onClick (fn []
                                         (uism/trigger! this ::session/session :event/toggle-modal {})
                                         (goto :signup))}
                        "Please sign up!"))))))))))

(def ui-login (comp/factory Login))

(defsc Main [this {:keys [main/welcome-message session login-form] :as props}]
  {:query         [:main/welcome-message
                   {:login-form (comp/get-query LoginForm)}
                   {:session (comp/get-query Session)}]
   :initial-state {:main/welcome-message "Hi!"
                   :session              {}
                   :login-form           {}}
   :ident         (fn [] [:component/id :main])
   :componentDidMount (fn [this])
   :route-segment ["main"]}
  (let []
    (div :.ui.segment
      (h3 "Welcome")
      (let [{current-user :account/name
             logged-in?   :session/valid?
             error :session/error} session]
        (if-not logged-in?
          (div {}
            (when error
              (div {:style {:color "red" :margin 10}} "ERROR: " error))
            (p {} "Please log in")
            (ui-login-form login-form))

          (div {}
            (dom/ul
              ;; More nav links here
              (dom/li (dom/a {:href "/tac-report"} "TAC Report"))
              (dom/li (dom/a {:href "/dataviz"} "Data Viz")))))))))




(dr/defrouter TopRouter [this props]
  {:router-targets        [Main Signup SignupSuccess ThetaRoot Projects TacReportPage
                           DataVizPage SiteVisitsPage]
   :shouldComponentUpdate (fn [_ _ _] true)})
(def ui-top-router (comp/factory TopRouter))


(defsc Activity [this {::fapp/keys [active-remotes]}]
  {:query         [[::fapp/active-remotes '_]]
   :ident         (fn [] [:component/id :activity])
   :initial-state {}}
  (let [loading? (boolean (seq active-remotes))]
    ;(debug "ACTIVITY" active-remotes)
    (when loading?
      (debug "LOADING ...")
      (dom/i :.ui.spinner.loading.icon))))
(def ui-activity (comp/factory Activity))


(defsc AgencyMenu [this {::keys [current-agency] :as props}]
  {:query         [{[::current-agency '_] (om/get-query looks/agencylookup-sum)}
                   {[:component/id :session] (comp/get-query Session)}
                   [::uism/asm-id ::session/session]]
   :initial-state {::current-agency {}}}
  (let [current-state (uism/get-active-state this ::session/session)
        {logged-in? :session/valid?} (get props [:component/id :session])]
    (when (and logged-in? current-agency)
      (div :.item (:agencylookup/AgencyCode current-agency)))))
(def ui-agency-menu (comp/factory AgencyMenu))

(defsc TopChrome [this {:root/keys [ready router current-session login activity agency-menu tx-result] :as props}]
  {:ident                 (fn [] [:component/id :top-chrome])
   :query                 [{:root/router (comp/get-query TopRouter)}
                           {:root/current-session (comp/get-query Session)}
                           [:root/ready '_]
                           {:root/login (comp/get-query Login)}
                           {:root/activity (comp/get-query Activity)}
                           {:root/agency-menu (comp/get-query AgencyMenu)}
                           {[:root/tx-result '_] (comp/get-query TxResult)}]
   :initial-state         {:root/router          {}
                           :root/ready           false
                           :root/login           {}
                           :root/current-session {}
                           :root/activity        {}
                           :root/agency-menu     {}}
   :shouldComponentUpdate (fn [_ _ _] true)}
  (let [current-route (dr/current-route this this)
        ;_             (println "TOP CHROME" ready tx-result (keys props))
        ;tx-result {:error "testing 1 2 3"}
        current-tab   (some-> current-route first keyword)
        {current-user :account/name
         logged-in?   :session/valid?} current-session]
    (div {:style {:display "grid" :gridTemplateRows "45px 1fr" :gridRowGap "0.2em" :height "100%"}}
      (div :.ui.secondary.pointing.menu {:style {:height "40px"}}
        (dom/a :.item {:classes [(when (= :main current-tab) "active")]
                       :href    "/"} "RiverDB Admin")
        (when (and ready logged-in?)
          [(dom/a :.item {:key  "tac" :classes [(when (= :tac-report current-tab) "active")]
                          :href "/tac-report"} "TAC Report")
           (dom/a :.item {:key  "dataviz" :classes [(when (= :dataviz current-tab) "active")]
                          :href "/dataviz"} "Dataviz")])
        (when (and ready logged-in?)
                ;(= tutil/app-env "dev"))
          [(dom/a :.item {:key  "theta" :classes [(when (= :theta current-tab) "active")]
                          :href "/theta/index"} "Tables")
           (dom/a :.item {:key  "projects" :classes [(when (= :projects current-tab) "active")]
                          :href "/projects"} "Projects")
           (dom/a :.item {:key  "sitevisit" :classes [(when (= :sitevisit current-tab) "active")]
                          :href "/sitevisit/list"} "Sitevisits")])
        (div :.right.menu
          ;(div :.item (ui-activity {}))
          (ui-agency-menu agency-menu)
          (ui-login login)))
      (if ready
        (div :.ui.container {}
          (ui-top-router router)
          (when (not (empty? tx-result))
            (ui-tx-result tx-result)))

        (ui-loader {:active true})))))

(def ui-top-chrome (comp/factory TopChrome))

(defsc Root [this {:root/keys [top-chrome]}]
  {:query                 [{:root/top-chrome (comp/get-query TopChrome)}]
   :initial-state         {:root/top-chrome {}}
   :shouldComponentUpdate (fn [_ _ _] true)}

  (inj/style-element {:component Root})
  (ui-top-chrome top-chrome))
