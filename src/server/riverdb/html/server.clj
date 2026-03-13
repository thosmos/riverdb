(ns riverdb.html.server
  "Server-side HTML web application using Datastar and Basecoat"
  (:require [clojure.tools.logging :refer [debug]]
            [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route :as route]
            [mount.core :as mount :refer [defstate]]
            [riverdb.html.handlers :as handlers]))

(def common-interceptors
  [(body-params/body-params)
   http/html-body])

(defn routes
  "Define all routes for the HTML app"
  []
  #{["/" :get (conj common-interceptors `handlers/home-page)
     :route-name :home]
    ["/about" :get (conj common-interceptors `handlers/about-page)
     :route-name :about]
    ["/demo" :get (conj common-interceptors `handlers/demo-page)
     :route-name :demo]
    ["/html/increment" :get (conj common-interceptors `handlers/increment-handler)
     :route-name :increment]
    ["/html/search" :get (conj common-interceptors `handlers/search-handler)
     :route-name :search]})

(defn create-service-map
  "Create Pedestal service map for HTML app"
  []
  {::http/routes (route/expand-routes (routes))
   ::http/type :jetty
   ::http/port 9595
   ::http/host "0.0.0.0"
   ::http/join? false
   ::http/allowed-origins (constantly true)
   ::http/secure-headers {:content-security-policy-settings
                          {:object-src "*"
                           :script-src "'unsafe-inline' 'unsafe-eval' *"}}})

(defn start-service
  "Start the HTML service"
  []
  (debug "Starting HTML service on port 9595")
  (let [service-map (create-service-map)
        service-map (http/default-interceptors service-map)
        service-map (http/dev-interceptors service-map)
        runnable-service (http/create-server service-map)]
    (http/start runnable-service)))

(defn stop-service
  "Stop the HTML service"
  [service]
  (debug "Stopping HTML service")
  (http/stop service))

(defstate html-server
  :start (start-service)
  :stop (stop-service html-server))

(comment
  (mount/start #'html-server)
  (mount/stop #'html-server))
