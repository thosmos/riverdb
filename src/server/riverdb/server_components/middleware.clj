(ns riverdb.server-components.middleware
  (:require
    [clojure.tools.logging :as log :refer [debug info warn error]]
    [cognitect.transit :as t]
    [riverdb.server-components.config :refer [config]]
    [riverdb.server-components.pathom :refer [parser]]

    [mount.core :refer [defstate]]
    [com.fulcrologic.fulcro.networking.file-upload :as file-upload]
    [com.fulcrologic.fulcro.server.api-middleware :refer [handle-api-request
                                                          wrap-transit-params
                                                          wrap-transit-response]]
    [ring.middleware.defaults :refer [wrap-defaults]]
    [ring.middleware.gzip :refer [wrap-gzip]]
    [ring.util.response :refer [response file-response resource-response]]
    [ring.util.response :as resp]
    [ring.util.io :as ring-io]
    [hiccup.page :refer [html5]]
    [taoensso.timbre :as tlog]
    [clojure.string :as str]
    [clojure.java.io :as io]
    [riverdb.api.tac-report :as tac]
    [riverdb.state :refer [db state]]))


(def ^:private not-found-handler
  (fn [req]
    {:status  404
     :headers {"Content-Type" "text/plain"}
     :body    "NOPE"}))

;; ================================================================================
;; Replace this with a pathom Parser once you get past the beginner stage.
;; This one supports the defquery-root, defquery-entity, and defmutation as
;; defined in the book, but you'll have a much better time parsing queries with
;; Pathom.
;; ================================================================================

;(def server-parser (server/fulcro-parser))

;(defn wrap-api [handler uri]
;  (fn [request]
;    (if (= uri (:uri request))
;      (server/handle-api-request
;        ;; Sub out a pathom parser here if you want to use pathom.
;        server-parser
;        ;; this map is `env`. Put other defstate things in this map and they'll be
;        ;; in the mutations/query env on server.
;        {:config config}
;        (:transit-params request))
;      (handler request))))

(defn wrap-api [handler uri]
  (fn [request]
    (if (= uri (:uri request))
      (do
        ;(debug "WRAP API" (:transit-params request))
        (handle-api-request
          (:transit-params request)
          (fn [tx] (parser {:ring/request request} tx))))
      (handler request))))

;; ================================================================================
;; Dynamically generated HTML. We do this so we can safely embed the CSRF token
;; in a js var for use by the client.
;; ================================================================================
(defn index [csrf-token]
  ;(debug "GENERATE ADMIN CLIENT HTML" csrf-token)
  (html5
    [:html {:lang "en"}
     [:head {:lang "en"}
      [:title "RiverDB"]
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
      [:link {:href "/admin/css/semantic.min.css" :rel "stylesheet"}]
      ;[:link {:href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.2/semantic.min.css"
      ;        :rel  "stylesheet"}]
      [:link {:href "/admin/css/app.css" :rel "stylesheet"}]
      [:link {:href "/admin/css/treeview.css" :rel "stylesheet"}]
      [:link {:href "/admin/css/react-datepicker.min.css" :rel "stylesheet"}]
      [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
      [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]]
     [:body
      [:div#app]
      [:script {:src "/admin/js/main/main.js"}]]]))

(defn disabled []
  (debug "GENERATE ADMIN DISABLED HTML")
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
  [{:keys [params] :as req}]
  (let [db (db)
        agency (:agency params)]
    (debug "RENDER CSV" req)
    (-> (resp/response
          (ring-io/piped-input-stream
            #(tac/csv-all-years (io/make-writer % {}) db agency))))))
;(response/content-type "text/plain")
;(response/charset "ANSI")

(defn wrap-html-routes [ring-handler]
  (fn [{:keys [uri params path-info anti-forgery-token] :as req}]
    ;(debug "HTML ROUTES HANDLER" uri path-info params (keys req))
    (debug "HTML ROUTES HANDLER :admin-disabled?" (:admin-disabled state))
    (cond

      (#{"/sitevisits.csv"} uri)
      (render-csv req)
      ;(resp/content-type "text/html"))

      (#{"/" "/index.html"} uri)
      (->
        (if (:admin-disabled state)
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
          (= "/api" uri)
          (str/ends-with? uri ".css")
          (str/ends-with? uri ".csv")
          (str/ends-with? uri ".png")
          (str/ends-with? uri ".map")
          (str/ends-with? uri ".jpg")
          (str/ends-with? uri ".js"))
      (handler req)
      (handler (assoc req :uri "/index.html")))))



(defstate middleware
  :start
  (let [defaults-config (:ring.middleware/defaults-config config)
        legal-origins   (get config :legal-origins #{"localhost"})]
    ;(debug "STARTING MIDDLEWARE" config)
    (-> not-found-handler
      (wrap-api "/api")
      (file-upload/wrap-mutation-file-uploads {})
      wrap-transit-params
      ;(wrap-transit-response {:opts {:transform t/write-meta}})
      wrap-transit-response
      (wrap-html-routes)
      (all-routes-to-index)
      ;; If you want to set something like session store, you'd do it against
      ;; the defaults-config here (which comes from an EDN file, so it can't have
      ;; code initialized).
      ;; E.g. (wrap-defaults (assoc-in defaults-config [:session :store] (my-store)))
      (wrap-defaults defaults-config)
      wrap-gzip)))


;(comment
;  (ns riverdb.server-components.middleware
;    (:require
;      [riverdb.server-components.config :refer [config]]
;      [riverdb.server-components.pathom :refer [parser]]
;      [mount.core :refer [defstate]]
;      [com.fulcrologic.fulcro.server.api-middleware :refer [handle-api-request
;                                                            wrap-transit-params
;                                                            wrap-transit-response]]
;      [ring.middleware.defaults :refer [wrap-defaults]]
;      [ring.middleware.gzip :refer [wrap-gzip]]
;      [ring.util.response :refer [response file-response resource-response]]
;      [ring.util.response :as resp]
;      [hiccup.page :refer [html5]]
;      [taoensso.timbre :as log]))
;
;  (def ^:private not-found-handler
;    (fn [req]
;      {:status  404
;       :headers {"Content-Type" "text/plain"}
;       :body    "NOPE"}))
;
;
;  (defn wrap-api [handler uri]
;    (fn [request]
;      (if (= uri (:uri request))
;        (handle-api-request
;          (:transit-params request)
;          (fn [tx] (parser {:ring/request request} tx)))
;        (handler request))))
;
;  ;; ================================================================================
;  ;; Dynamically generated HTML. We do this so we can safely embed the CSRF token
;  ;; in a js var for use by the client.
;  ;; ================================================================================
;  (defn index [csrf-token]
;    (log/debug "Serving index.html")
;    (html5
;      [:html {:lang "en"}
;       [:head {:lang "en"}
;        [:title "Application"]
;        [:meta {:charset "utf-8"}]
;        [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
;        [:link {:href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.css"
;                :rel  "stylesheet"}]
;        [:link {:href "/css/main.css" :rel "stylesheet"}]
;        [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
;        [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]]
;       [:body
;        [:div#app]
;        [:script {:src "js/main/main.js"}]]]))
;
;  ;; ================================================================================
;  ;; Workspaces can be accessed via shadow's http server on http://localhost:8023/workspaces.html
;  ;; but that will not allow full-stack fulcro cards to talk to your server. This
;  ;; page embeds the CSRF token, and is at `/wslive.html` on your server (i.e. port 3000).
;  ;; ================================================================================
;  (defn wslive [csrf-token]
;    (log/debug "Serving wslive.html")
;    (html5
;      [:html {:lang "en"}
;       [:head {:lang "en"}
;        [:title "devcards"]
;        [:meta {:charset "utf-8"}]
;        [:meta {:name "viewport" :content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"}]
;        [:link {:href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.css"
;                :rel  "stylesheet"}]
;        [:link {:href "/css/main.css"}
;           :rel  "stylesheet"]
;        [:link {:rel "shortcut icon" :href "data:image/x-icon;," :type "image/x-icon"}]
;        [:script (str "var fulcro_network_csrf_token = '" csrf-token "';")]]
;       [:body
;        [:div#app]
;        [:script {:src "workspaces/js/main.js"}]]]))
;
;  (defn wrap-html-routes [ring-handler]
;    (fn [{:keys [uri anti-forgery-token] :as req}]
;      (cond
;        (#{"/" "/index.html"} uri)
;        (-> (resp/response (index anti-forgery-token))
;          (resp/content-type "text/html"))
;
;        ;; See note above on the `wslive` function.
;        (#{"/wslive.html"} uri)
;        (-> (resp/response (wslive anti-forgery-token))
;          (resp/content-type "text/html"))
;
;        :else
;        (ring-handler req))))
;
;  (defstate middleware
;    :start
;    (let [defaults-config (:ring.middleware/defaults-config config)
;          legal-origins   (get config :legal-origins #{"localhost"})]
;      (-> not-found-handler
;        (wrap-api "/api")
;        wrap-transit-params
;        wrap-transit-response
;        (wrap-html-routes)
;        ;; If you want to set something like session store, you'd do it against
;        ;; the defaults-config here (which comes from an EDN file, so it can't have
;        ;; code initialized).
;        ;; E.g. (wrap-defaults (assoc-in defaults-config [:session :store] (my-store)))
;        (wrap-defaults defaults-config)
;        wrap-gzip))))




