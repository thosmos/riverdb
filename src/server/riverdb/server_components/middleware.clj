(ns riverdb.server-components.middleware
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [cognitect.transit :as t]
    [com.fulcrologic.rad.blob :as blob]
    [com.fulcrologic.fulcro.networking.file-upload :as file-upload]
    [com.fulcrologic.fulcro.server.api-middleware :refer [handle-api-request
                                                          wrap-transit-params
                                                          wrap-transit-response]]
    [hiccup.page :refer [html5]]
    [mount.core :refer [defstate]]
    [ring.middleware.defaults :refer [wrap-defaults]]
    [ring.middleware.gzip :refer [wrap-gzip]]
    [ring.util.response :refer [response file-response resource-response]]
    [ring.util.response :as resp]
    [ring.util.io :as ring-io]
    [riverdb.api.tac-report :as tac]
    [riverdb.api.csv-report :refer [csv-report]]
    [riverdb.server-components.config :refer [config]]
    [riverdb.server-components.pathom :refer [parser]]
    [riverdb.rad.comps.blob-store :as bs]
    [riverdb.state :refer [db state]]
    [taoensso.timbre :as tlog]
    [theta.log :as log]))


(def ^:private not-found-handler
  (fn [req]
    {:status  404
     :headers {"Content-Type" "text/plain"}
     :body    "NOPE"}))


(defn wrap-api [handler uri]
  (fn [request]
    (if (= uri (:uri request))
      (do
        ;(log/debug "WRAP API" (:transit-params request))
        (handle-api-request
          (:transit-params request)
          (fn [tx] (parser {:ring/request request} tx))))
      (handler request))))

;; ================================================================================
;; Dynamically generated HTML. We do this so we can safely embed the CSRF token
;; in a js var for use by the client.
;; ================================================================================
(defn index [csrf-token]
  ;(log/debug "GENERATE ADMIN CLIENT HTML" csrf-token)
  (html5
    [:html {:lang "en"}
     [:head {:lang "en"}
      [:title "RiverDB"]
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
      [:link {:href "/admin/css/semantic.min.css" :rel "stylesheet"}]
      ;[:link {:href "https://cdn.jsdelivr.net/npm/fomantic-ui@2.7.8/dist/semantic.min.css"
      ;        :rel  "stylesheet"}]
      [:link {:href "/admin/css/app.css" :rel "stylesheet"}]
      ;[:link {:href "/admin/css/treeview.css" :rel "stylesheet"}]
      [:link {:href "/admin/css/react-datepicker.min.css" :rel "stylesheet"}]
      [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
      [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]]
     [:body
      [:div#app]
      [:script {:src "/admin/js/main/main.js"}]]]))


(defn disabled []
  (log/debug "GENERATE ADMIN DISABLED HTML")
  (html5
    [:html {:lang "en"}
     [:head {:lang "en"}
      [:title "RiverDB Admin DISABLED"]
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
      [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]]
     [:body
      [:h2 "RiverDB Admin is disabled for maintenance"]]]))

;; ================================================================================
;; Workspaces can be accessed via shadow's http server on http://localhost:8023/workspaces.html
;; but that will not allow full-stack fulcro cards to talk to your server. This
;; page embeds the CSRF token, and is at `/wslive.html` on your server (i.e. port 3000).
;; ================================================================================
(defn wslive [csrf-token]
  (html5
    [:html {:lang "en"}
     [:head {:lang "en"}
      [:title "devcards"]
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
      [:link {:href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.2/semantic.min.css"
              :rel  "stylesheet"}]
      [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
      [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]]
     [:body
      [:div#app]
      [:script {:src "/admin/workspaces/js/main.js"}]]]))

(defn render-csv
  [{:keys [params query-params] :as req}]
  (let [db (db)
        agency (:agency query-params)
        project (:project query-params)]
    (log/debug "RENDER CSV" query-params)
    (-> (resp/response
          (ring-io/piped-input-stream
            #(csv-report (io/make-writer % {}) db {:agencyCode agency :projectID project}))))))
;(response/content-type "text/plain")
;(response/charset "ANSI")


(defn htmx [csrf-token]
  ;(log/debug "GENERATE ADMIN CLIENT HTML" csrf-token)
  (html5
    [:html {:lang "en"}
     [:head {:lang "en"}
      [:title "HTMX"]
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
      [:link {:href "/admin/css/semantic.min.css" :rel "stylesheet"}]
      [:link {:href "/admin/css/app.css" :rel "stylesheet"}]
      [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
      [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]
      [:script {:src "https://unpkg.com/htmx.org@1.9.5"}]]

     [:body
      [:div#htmx
       [:div "Hello HTMX"]]]]))


(defn wrap-html-routes [ring-handler]
  (fn [{:keys [uri params query-params path-info anti-forgery-token] :as req}]
    ;(log/debug "HTML ROUTES HANDLER" uri path-info "PARAMS" params "QUERY PARAMS" query-params (keys req))
    (cond

      (#{"/sitevisits.csv"} uri)
      (render-csv req)
      ;(resp/content-type "text/html"))

      (#{"/htmx"} uri)
      (-> (resp/response (htmx anti-forgery-token))
        (resp/content-type "text/html"))

      (#{"/" "/index.html"} uri)
      (->
        (if (:admin-disabled @state)
          (resp/response (disabled))
          (resp/response (index anti-forgery-token)))
        (resp/content-type "text/html"))

      ;; FIXME all-routes-to-index (below) makes this inaccessible
      ;; See note above on the `wslive` function.
      (#{"/wslive.html"} uri)
      (-> (resp/response (wslive anti-forgery-token))
        (resp/content-type "text/html"))

      :else
      (ring-handler req))))


(defn all-routes-to-index [handler]
  (fn [{:keys [uri] :as req}]
    (if (or
          (str/starts-with? uri "/api")
          (str/starts-with? uri "/admin/images")
          (str/starts-with? uri "/admin/files")
          (str/starts-with? uri "/admin/js")
          (str/starts-with? uri "/admin/css")
          (str/ends-with? uri ".css")
          (str/ends-with? uri ".csv")
          (str/ends-with? uri ".ico")
          (str/ends-with? uri ".png")
          (str/ends-with? uri ".map")
          (str/ends-with? uri ".jpg")
          (str/ends-with? uri ".js"))
      (handler req)
      (handler (assoc req :uri "/index.html")))))

(defn wrap-hmm [handler]
  (fn [{:keys [uri params query-params path-info anti-forgery-token] :as req}]
    (log/debug "HMM params" params "query-params" query-params)
    (handler req)))

(defstate middleware
  :start
  (let [defaults-config (:ring.middleware/defaults-config config)
        legal-origins   (get config :legal-origins #{"localhost"})]
    ;(log/debug "STARTING MIDDLEWARE" config)
    (-> not-found-handler
      (wrap-api "/api")
      (file-upload/wrap-mutation-file-uploads {})
      (blob/wrap-blob-service "/admin/images" bs/image-blob-store)
      (blob/wrap-blob-service "/admin/files" bs/file-blob-store)
      (wrap-transit-params {})
      ;(wrap-transit-response {:opts {:transform t/write-meta}})
      (wrap-transit-response {})
      (wrap-html-routes)
      (all-routes-to-index)
      ;; If you want to set something like session store, you'd do it against
      ;; the defaults-config here (which comes from an EDN file, so it can't have
      ;; code initialized).
      ;; E.g. (wrap-defaults (assoc-in defaults-config [:session :store] (my-store)))
      (wrap-defaults defaults-config)
      ;(wrap-hmm)
      wrap-gzip)))




