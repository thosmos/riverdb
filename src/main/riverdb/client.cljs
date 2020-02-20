(ns riverdb.client
  (:require
    [riverdb.application :refer [SPA]]
    [com.fulcrologic.fulcro.application :as app]
    [riverdb.ui.root :as root]
    [com.fulcrologic.fulcro.networking.http-remote :as net]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.ui-state-machines :as uism]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro-css.css-injection :as cssi]
    [riverdb.model.session :as session]
    [riverdb.ui.globals :as globals]
    [riverdb.ui.session :as ui-session]
    [riverdb.ui.routes :as routes]
    [riverdb.ui.project-years :as py]
    ;[riverdb.model :as model]
    [theta.log :as log]
    [com.fulcrologic.fulcro.algorithms.denormalize :as fdn]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.inspect.inspect-client :as inspect]))
    ;[com.fulcrologic.rad.attributes :as attr]
    ;[com.fulcrologic.rad.form :as form]
    ;[com.fulcrologic.rad.rendering.semantic-ui.semantic-ui-controls :as sui]))

(defn ^:export refresh []
  (log/info "Hot code Remount")
  (cssi/upsert-css "componentcss" {:component root/Root})
  (app/mount! SPA root/Root "app"))

(defn ^:export init []
  (log/info "Application starting.")
  (cssi/upsert-css "componentcss" {:component root/Root})
  ;(inspect/app-started! SPA)
  (log/info "Setting Fulcro Root and Initializing State")
  (app/set-root! SPA root/Root {:initialize-state? true})
  ;(routes/start-routing SPA {:use-fragment false})
  (log/info "Initializing Fulcro Dynamic Routing")
  (dr/initialize! SPA)
  (log/info "Starting Session Machine.")
  (uism/begin! SPA session/session-machine ::session/session
    {:actor/login-form      root/LoginForm
     :actor/logout-menu     root/Login
     :actor/current-session ui-session/Session
     :actor/project-years   py/ProjectYears
     :actor/globals         globals/Globals}
    {:desired-path (some-> js/window .-location .-pathname)})
  (log/info "Starting Pushy")
  (routes/start!)

  ;(form/install-ui-controls! SPA sui/all-controls)
  ;(attr/register-attributes! model/all-attributes)
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
