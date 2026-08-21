(ns riverdb.html.routes
  "Reitit route data and the assembled ring handler.

  Deliberately separate from riverdb.html.server: this namespace is safe to
  reload at will, while the server namespace holds the mount defstate and is
  pinned. `handler` is rebuilt on every reload and the server resolves it by
  symbol per request in dev, so route edits land without restarting http-kit.

  Middleware is hand-assembled from ring-core rather than pulling in
  reitit-middleware, which would drag muuntaja, spec-tools and deep-diff along
  for two small wrappers we can write ourselves."
  (:require
    [hiccup.core :refer [html]]
    [reitit.coercion.malli :as rcm]
    [reitit.ring :as ring]
    [reitit.ring.coercion :as rrc]
    [ring.middleware.params :refer [wrap-params]]
    [riverdb.html.handlers :as h]
    [riverdb.html.layout :as layout]
    [riverdb.html.schema :as sc]))

(defn wrap-coercion-errors
  "A malli coercion failure on a path param is a bad URL, so answer 400 rather
  than letting the ex-info surface as a 500."
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (if (= :reitit.coercion/request-coercion (:type (ex-data e)))
          {:status 400
           :headers {"Content-Type" "text/html; charset=utf-8"}
           :body (layout/base-layout
                   {:title "Bad Request"}
                   (layout/nav {:brand "RiverDB" :items (layout/nav-items nil)})
                   (layout/container
                     [:div
                      (layout/alert {:variant "warning"}
                        [:h4 "Bad Request"]
                        [:p "That URL doesn't look right."])
                      [:a {:href "/sitevisits" :role "button"} "Site Visits"]]))}
          (throw e))))))

(defn wrap-no-store
  "Static assets are served straight off the classpath, so in dev an edit to
  app.css should show up on reload. Without this the browser heuristically
  caches it and you edit CSS wondering why nothing changes."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (cond-> response
        (some? response)
        (assoc-in [:headers "Cache-Control"] "no-store")))))

(def routes
  [["/"           {:get {:handler h/home-page}}]
   ["/about"      {:get {:handler h/about-page}}]
   ["/sitevisits" {:get {:handler h/sitevisits-page}}]
   ["/sitevisit/:id"
    {:parameters {:path sc/SiteVisitPath}
     :get {:handler h/sitevisit-page}}]
   ["/sitevisit/:id/save"
    {:parameters {:path sc/SiteVisitPath}
     :post {:handler h/save-sitevisit}}]
   ;; Cardinality-many ref editors. One route set serves every field in
   ;; riverdb.html.handlers/ref-fields, dispatching on :field — adding another
   ;; many-ref needs no new routes. POST to the collection means "add whatever
   ;; the server ranks top for the current query", which is what Enter does.
   ["/sitevisit/:id/ref/:field"
    {:parameters {:path sc/RefFieldPath}
     :get  {:handler h/search-ref}
     :post {:handler h/add-top-ref}}]
   ["/sitevisit/:id/ref/:field/:member"
    {:parameters {:path sc/RefMemberPath}
     :post   {:handler h/add-ref}
     :delete {:handler h/remove-ref}}]

   ["/sitevisit/:id/reload"
    {:parameters {:path sc/SiteVisitPath}
     :get {:handler h/reload-sitevisit}}]])

(def router-opts
  {:data {:coercion rcm/coercion
          :middleware [wrap-params
                       wrap-coercion-errors
                       rrc/coerce-request-middleware]}})

(defn build-handler []
  (ring/ring-handler
    (ring/router routes router-opts)
    (ring/routes
      (wrap-no-store (ring/create-resource-handler {:path "/"}))
      (ring/create-default-handler {:not-found h/not-found}))))

(def handler (build-handler))
