(ns riverdb.client
  (:require
    [cljs.core]
    [com.fulcrologic.fulcro.algorithms.denormalize :as fdn]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro-css.css-injection :as cssi]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.inspect.inspect-client :as inspect]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.networking.http-remote :as net]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as uism]
    [com.fulcrologic.rad.application :as rad-app]
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.rendering.semantic-ui.semantic-ui-controls :as sui]
    [com.fulcrologic.rad.routing :as routing]
    [com.fulcrologic.rad.routing.html5-history :as hist5 :refer [html5-history]]
    [com.fulcrologic.rad.routing.history :as history]
    [com.fulcrologic.rad.type-support.date-time :as datetime]
    [riverdb.application :refer [SPA]]
    [riverdb.model.session :as session]
    [riverdb.rad.ui.controls.autocomplete]
    [riverdb.rad.ui.controls.inputlist]
    [riverdb.rad.ui.controls.reverse-many-picker]
    [riverdb.rad.ui.controls.decimal-field]
    [riverdb.ui.globals :as globals]
    ;[riverdb.model :as model]
    [riverdb.ui.root :as root]
    [riverdb.ui.session :as ui-session]
    [riverdb.ui.routes :as routes]
    [riverdb.ui.project-years :as py]
    [theta.log :as log]))
    ;[com.fulcrologic.rad.attributes :as attr]
    ;[com.fulcrologic.rad.form :as form]
    ;[com.fulcrologic.rad.rendering.semantic-ui.semantic-ui-controls :as sui]))

(goog-define version "hmm")

(defn setup-RAD [app]
  (history/install-route-history! app (html5-history))
  (let [all-controls (-> sui/all-controls
                       (assoc-in [::form/type->style->control :string :autocomplete]
                         riverdb.rad.ui.controls.autocomplete/render-autocomplete-field)
                       (assoc-in [::form/type->style->control :string :inputlist]
                         riverdb.rad.ui.controls.inputlist/render-inputlist-field)
                       (assoc-in [::form/type->style->control :ref :pick-many-reverse]
                         riverdb.rad.ui.controls.reverse-many-picker/to-many-picker)
                       (assoc-in [::form/type->style->control :decimal :river-decimal]
                         riverdb.rad.ui.controls.decimal-field/render-field))]


    (rad-app/install-ui-controls! app all-controls)))

  ;;(report/install-formatter! app :boolean :affirmation (fn [_ value] (if value "yes" "no"))))

(m/defmutation fix-route
  "Mutation. Called after auth startup. Looks at the session. If the user is not logged in, it triggers authentication"
  [_]
  (action [{:keys [app]}]
    (let [logged-in (auth/verified-authorities app)]
      (if (empty? logged-in)
        (routing/route-to! app root/LoginForm {})
        (hist5/restore-route! app root/Root {})))))

(defn ^:export refresh []
  (log/info "Hot code Remount")
  (cssi/upsert-css "componentcss" {:component root/Root})
  (setup-RAD SPA)
  (comp/refresh-dynamic-queries! SPA)
  (app/mount! SPA root/Root "app"))

(defn ^:export init []
  (log/test-logs)

  (log/info "Application starting.")
  (log/debug "Version" version)
  (datetime/set-timezone! "America/Los_Angeles")
  (cssi/upsert-css "componentcss" {:component root/Root})
  ;(inspect/app-started! SPA)
  (log/info "Setting Fulcro Root and Initializing State")
  (app/set-root! SPA root/Root {:initialize-state? true})
  ;(routes/start-routing SPA {:use-fragment false})
  (log/info "Initializing Fulcro Dynamic Routing")
  (dr/initialize! SPA)
  (setup-RAD SPA)
  (log/info "Starting Session Machine.")
  (uism/begin! SPA session/session-machine ::session/session
    {:actor/login-form      root/LoginForm
     :actor/logout-menu     root/Login
     :actor/current-session ui-session/Session
     :actor/project-years   py/ProjectYears
     :actor/globals         globals/Globals}
    {:desired-route (hist5/url->route)})
  ;(auth/start! app [root/LoginForm] {:after-session-check `fix-route})
  ;(log/info "Starting Pushy")
  ;(routes/start!)

  (log/info "Mounting Root")
  (app/mount! SPA root/Root "app" {:initialize-state? false}))

(comment
  (inspect/app-started! SPA)
  (app/mounted? SPA)
  (app/set-root! SPA root/Root {:initialize-state? true})
  (uism/begin! SPA session/session-machine ::session/session
    {:actor/login-form      root/Login
     :actor/current-session ui-session/Session})

  (reset! (::app/state-atom SPA) {})

  (merge/merge-component! my-app Settings {:account/time-zone "America/Los_Angeles"
                                           :account/real-name "Joe Schmoe"})
  (dr/initialize! SPA)
  (app/current-state SPA)
  (dr/change-route SPA ["settings"])
  (app/mount! SPA root/Root "app")
  (comp/get-query root/Root {})
  (comp/get-query root/Root (app/current-state SPA))

  (-> SPA ::app/runtime-atom deref ::app/indexes)
  (comp/class->any SPA root/Root)
  (let [s (app/current-state SPA)]
    (fdn/db->tree [{[:component/id :login] [:ui/open? :ui/error :account/email
                                            {[:root/current-session '_] (comp/get-query root/Session)}
                                            [::uism/asm-id ::session/session]]}] {} s)))
