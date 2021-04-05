(ns riverdb.ui.routes
  (:import [goog History])
  (:require
    [edn-query-language.core :as eql]
    [goog.events :as gevents]
    [clojure.string :as str]
    [com.fulcrologic.fulcro.application :as fapp]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.rad.routing :as rroute]
    [com.fulcrologic.rad.routing.html5-history :as html5 :refer [url->route apply-route!]]
    [pushy.core :as pushy]
    [theta.log :as log :refer [debug]]
    [clojure.string :as str]
    [taoensso.timbre :as timbre]
    [riverdb.application :refer [SPA]]))

;(defonce page-history
;  (doto (History.)
;    (.setEnabled true)))
;
;(defn listen-nav-change [f]
;  (gevents/listen page-history "navigate" #(f % (.-token %))))
;
;(defn change-route [path]
;  (.setToken page-history (str/join "/" path)))
;
;(defn path->route [path]
;  (let [r-segments (vec (rest (str/split path "/")))]
;    (if (seq r-segments)
;      r-segments
;      ["main"])))
;
;(defn change-route-from-nav-event [app]
;  (fn [_ path]
;    (dr/change-route app (path->route path))))
;
;(comment (change-route (dr/path-to SearchPage)))
;
;(defn start-routing! []
;  (listen-nav-change #(change-route-from-nav-event SPA)))

(declare replace!)
(declare route-to!)

(defn find-route-target [app-or-comp router new-route]
  (let [app        (comp/any->app app-or-comp)
        state-map  (fapp/current-state app)
        ;_ (debug "FIND ROUTER TARGET" router state-map)
        root-query (comp/get-query router state-map)
        ast        (eql/query->ast root-query)
        root       (dr/ast-node-for-route ast new-route)]
    (loop [{:keys [component]} root path new-route]
      (when (and component (dr/router? component))
        (let [{:keys [target matching-prefix]} (dr/route-target component path)
              target-ast     (some-> target (comp/get-query state-map) eql/query->ast)
              prefix-length  (count matching-prefix)
              remaining-path (vec (drop prefix-length path))]
          (if (seq remaining-path)
            (recur (dr/ast-node-for-route target-ast remaining-path) remaining-path)
            target))))))

(defn path->route
  "Convert a URL path into a route path and parameter map. Returns:

   ```
   {:route [\"path\" \"segment\"]
    :params {:param value}}
   ```

   You can save this value and later use it with `html5/apply-route!`.
  "
  [path]
  (let [route (vec (drop 1 (str/split path #"/")))]
   {:route  route
    :params {}}))

(defn route-to-path! [path]
  (let [route        (:route (path->route path))
        target       (find-route-target SPA
                       (comp/registry-key->class :riverdb.ui.root/TopRouter) route)
        target-opts  (comp/component-options target)
        check-fn     (:check-session target-opts)
        session      (get-in (fapp/current-state SPA) [:component/id :session])
        sesh-valid?  (:session/valid? session)
        _            (log/info "URL Routing" path "=>" route "target?" target "target-opts?" target-opts)
        check-result (if sesh-valid?
                       (if check-fn
                         (check-fn SPA session)
                         true)
                       false)
        reject?      (= false check-result)]

    (debug "ROUTE Target" target "reject?" reject? "check-result" check-result)

    (cond
      (and (seq route) reject?)
      (html5/apply-route! SPA ["main"])
      (seq route)
      (html5/apply-route! SPA route)
      :else
      (html5/apply-route! SPA ["main"]))))

(defonce history
  (pushy/pushy
    (fn [p]
      (let [r-segments   (vec (rest (str/split p "/")))
            target       (find-route-target SPA
                           (comp/registry-key->class :riverdb.ui.root/TopRouter) r-segments)
            target-opts  (comp/component-options target)
            check-fn     (:check-session target-opts)
            session      (get-in (fapp/current-state SPA) [:component/id :session])
            sesh-valid?  (:session/valid? session)
            _            (log/info "URL Routing" p "=>" r-segments "target?" target "target-opts?" target-opts)
            check-result (if sesh-valid?
                           (if check-fn
                             (check-fn SPA session)
                             true)
                           false)
            reject?      (= false check-result)]

        (debug "ROUTE Target" target "reject?" reject? "check-result" check-result)

        (cond
          (and (seq r-segments) reject?)
          (replace! "/")
          (seq r-segments)
          (dr/change-route SPA r-segments)
          :else
          (dr/change-route SPA ["main"]))))
    identity))

(defn start! []
  (pushy/start! history)
  #_(listen-nav-change #(change-route-from-nav-event SPA)))

(defn route-to! [route-str]
  (pushy/set-token! history route-str)
  #_(.setToken page-history route-str))

(defn replace! [route-str]
  (pushy/replace-token! history route-str))




