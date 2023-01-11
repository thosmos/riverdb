(ns riverdb.server
  (:gen-class) ; for -main method in uberjar
  (:require [clojure-csv.core :refer [write-csv]]
            [clojure.tools.logging :refer [debug]]
            [com.walmartlabs.lacinia.pedestal :as lacinia]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia.util :as util]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [datomic.api :as d]
            [dotenv]
            [hiccup.page :refer [html5 include-js include-css]]
            [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor :as interceptor]
            [mount.core :as mount :refer [defstate]]
            [ring.middleware.cookies :refer [cookies-request]] ;cookies-response
            [ring.util.response :as ring-resp]
            [riverdb.auth :as auth]
            [riverdb.model.user :as user]
            [riverdb.graphql.resolvers :refer [resolvers]]
            [riverdb.graphql.schema :as sch]
            [riverdb.server-components.config]
            [riverdb.server-components.nrepl]
            [riverdb.server-components.middleware :as middle :refer [middleware]]
            [riverdb.state :refer [start-dbs]]
            [theta.util]
            [theta.log :as log]))

;(set! *warn-on-reflection* 1)

(defn attach-resolvers [schemas]
  (debug "ATTACH RESOLVERS")
  (util/attach-resolvers
    ;(sch/load-all-schemas)
    schemas
    (resolvers)))

(defn compile-schemas [schemas]
  (debug "COMPILE SCHEMAS")

  (try
    (schema/compile schemas)
    (catch Exception ex
      (debug "COMPILE ERROR" ex))))


(defn process-schemas []
  (debug "PROCESS SCHEMAS")
  (let [schemas   (sch/merge-schemas)
        resolvers (attach-resolvers schemas)
        compiled  (compile-schemas resolvers)]
    (debug "PROCESS SCHEMAS COMPLETE")
    compiled))



(defn parse-cookies [context]
  (let [context (update context :request cookies-request)
        token?  (get-in context [:request :cookies "riverdb-auth-token" :value])]

    ;(debug "cookies: " (str (get-in context [:request :cookies])))

    (if (and token? (not= token? ""))
      (do
        (debug "found cookie:" token?)
        (assoc-in context [:request :auth-token] token?))
      context)))


(defn parse-headers [context]
  (let [auth    (get-in context [:request :headers "authorization"])
        bearer? (when auth (str/starts-with? auth "Bearer "))
        token?  (when bearer? (str/replace auth "Bearer " ""))]
    ;_       (when token? (debug "authorization bearer token: " token?))]

    (if token?
      (do
        (debug "found auth token:" token?)
        (-> context
          (assoc-in [:request :auth-token] token?)))
      ;(assoc-in [:response :body :data :auth :token] token?)))
      context)))

(comment
  (require '[crypto.random :as random])
  (random/bytes 16))

(defn auth-enter-fn [context]
  (debug "AUTH ENTER")
  (let [context (parse-cookies context)
        context (parse-headers context)
        token?  (get-in context [:request :auth-token])
        _       (when token? (debug "auth token: " token?))
        user?   (when token? (auth/check-token token?))
        user?   (when user? (user/pull-email->user (:user/email user?)))]

    (if user?
      (-> context
        (assoc-in [:request :lacinia-app-context :auth-token] token?)
        (assoc-in [:request :lacinia-app-context :user] user?))
      context)))

(defn auth-exit-fn [context]
  (debug "AUTH EXIT: \n\n")
  ;(tu/ppstr (keys context)) "\n\nRESPONSE:\n\n"
  ;(tu/ppstr (keys (get-in context [:response :body :data])))            ;(tu/ppstr context)
  ;(if (get-in context [:response :body :data :unauth])
  ;  (do
  ;    (debug "CLEARING AUTH COOKIE")
  ;    (update context :response
  ;      #(-> %
  ;         (assoc :cookies {"riverdb-auth-token" nil})
  ;         cookies-response)))
  ;  (if-let [token (get-in context [:response :body :data :auth :token])]
  ;    (do
  ;      (debug "SETTING OUR AUTH COOKIE")
  ;      (update context :response
  ;        #(-> %
  ;           (assoc :cookies {"riverdb-auth-token" {:value token}})
  ;           cookies-response)))))
  context)


(defn auth-interceptor
  "On entrance, checks for a valid JWT token in the request headers, and if present, adds a user record to the context.
  On exit, checks to see if a new JWT token has been created, and if so, adds a cookie with it"
  []
  (io.pedestal.interceptor/interceptor
    {:name  ::inject-auth
     :enter (fn [context]
              (auth-enter-fn context))
     :leave (fn [context]
              (auth-exit-fn context))}))

(defn gen-squuids [ctx]
  (let [req      (:request ctx)
        nbr      (try
                   (-> req :params :count Integer/parseInt)
                   (catch Exception _ 0))
        result   (write-csv
                   (vec
                     (for [_ (range nbr)]
                       [(str (d/squuid))])))
        result   result
        status   200
        ;status   (if (contains? result :data)
        ;           200
        ;           400)
        response {:status  status
                  :headers {"Content-Type" "text/plain"}
                  ;:headers {}
                  :body    result}]

    (debug "SQUUIDS request:\n\n"
      (with-out-str
        (pprint req)))
    (debug "SQUUIDS result:\n\n"
      (with-out-str
        (pprint result)))

    (assoc ctx :response response)))

(defn response?
  "A valid response is any map that includes an integer :status
  value."
  [resp]
  (and (map? resp)
    (integer? (:status resp))))

;(def not-found
;  "An interceptor that returns a 404 when routing failed to resolve a route."
;  (helpers/after
;    ::not-found
;    (fn [context]
;      (if-not (response? (:response context))
;        (do
;            (assoc context :response (ring-response/not-found "Not Found")))
;        context))))



(defn app-interceptor []
  (interceptor/interceptor
    {:name  ::all-paths-are-belong-to-app
     :leave (fn [ctx]
              ;(debug "LEAVE APP PRE" "\n\nresponse" (:response ctx) "\n\nrequest" (:request ctx))
              (let [ctx (if-not (response? (:response ctx))
                          (let [params (get-in ctx [:request :params])
                                ;_      (debug "REQUEST :params" params)
                                ctx (update ctx :request dissoc :params)]
                            (assoc ctx :response (middleware (:request ctx))))
                          ctx)]
                ;(debug "LEAVE APP POST" "response" (:response ctx))
                ctx))}))


(defn squuids-interceptor []
  (interceptor/interceptor
    {:name  ::create-squuids
     :enter (fn [ctx]
              (gen-squuids ctx))}))

(defn create-squuid-route []
  ["/squuids"
   :get [(squuids-interceptor)]
   :route-name ::squuids])


(defn generate-html []
  (let []
    (html5
      {:lang "en"}
      [:head
       (include-css "/admin/app.css")
       [:title "RiverDB Admin"]
       [:meta {:charset "utf-8"}]
       [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge,chrome=1"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]]
      ;[:link {:rel "shortcut icon" :href "/favicon.ico"}]
      ;[:script#state {:type "application/edn"} (pr-str results)]

      [:body
       [:div#app]
       (include-js "/admin/js/main/main.js")])))


(defn admin-fn [ctx]
  (debug "ADMIN INTERCEPTOR!!!!")
  (let [response {:status  200
                  ;:headers {"Content-Type" "text/plain"}
                  :headers {"Content-Type" "text/html"}
                  ;:headers {}
                  :body    (generate-html)}]
    (assoc ctx :response response)))

;(defn admin-interceptor []
;  (io.pedestal.interceptor/interceptor
;    {:name  ::admin-ui
;     :enter (fn [ctx] (admin-fn ctx))}))

(def common-interceptors [(body-params/body-params) http/html-body])

;(defn resource-handlers [request]
;  (let [handlers (->
;                   (ring.util.response/not-found nil)
;                   (wrap-resource "public")
;                   (wrap-content-type)
;                   (wrap-not-modified))
;        resp     (handlers request)]
;    (debug "RESOURCE HANDLER" (:status resp) (:uri request))
;    resp))

(defn add-admin-routes [routes]
  ;(debug "DEFAULT ROUTES" routes)
  (-> routes
    ;(conj ["/admin"
    ;       :get (conj common-interceptors (admin-interceptor))            ;[(admin-interceptor)]
    ;       :route-name ::admin-route])
    (conj ["/api"
           :any [`middleware]
           :route-name :admin-js-resources-2])
    (conj ["/admin/*path"
           :any [`middleware]
           :route-name :admin-js-resources-3])
    (conj ["/data/*path"
           :any [`middleware]
           :route-name :admin-js-resources-4])
    (conj ["/"
           :get [`middleware]
           :route-name :admin-js-resources-1])))

(defn hello-page
  [request]
  (debug "HELLO PARAMS" (get-in request [:params]))
  (ring-resp/response "Hello World!"))



(defn create-service-map []
  (let [options   {:graphiql      true
                   :ide-path      "/graphiql"
                   :subscriptions false
                   :port          (Long/parseLong (or (dotenv/env :PORT) "8989"))
                   :env           (or (keyword dotenv/app-env) :dev)}
        inceptors (vec (concat [] (lacinia/default-interceptors #(process-schemas) options)))
        inceptors (-> inceptors
                    (lacinia/inject (auth-interceptor)
                      :after :com.walmartlabs.lacinia.pedestal/inject-app-context))
        options   (assoc options :interceptors inceptors)
        routes    (lacinia/graphql-routes #(process-schemas) options)
        routes    (conj routes (create-squuid-route))
        routes    (add-admin-routes routes)
        routes    (conj routes ["/hello" :get (conj common-interceptors `hello-page)])
        options   (assoc options :routes routes)
        s-map     (lacinia/service-map #(process-schemas) options)
        s-map     (if (not= (theta.util/app-env) "prod")
                    (assoc s-map ::http/host "0.0.0.0")
                    s-map)]
    ;s-map     (http/dev-interceptors s-map)]
    (merge s-map
      {;;::http/router :linear-search
       ;;::http/host "0.0.0.0"
       ;; all origins are allowed in dev mode
       ;::http/allowed-origins {:creds true :allowed-origins (constantly true)}
       ::http/allowed-origins   (constantly true) ;{:allowed-origins (constantly true)} ;:creds true :allowed-origins "*"}
       ;; Content Security Policy (CSP) is mostly turned off in dev mode
       ::http/secure-headers    {:content-security-policy-settings {:object-src "*"
                                                                    :script-src "'unsafe-inline' 'unsafe-eval' *"}}})))


(defn start-service []
  (start-dbs)
  (let [sm               (create-service-map)
        sm               (merge sm
                           {::http/not-found-interceptor (app-interceptor)
                            ::http/resource-path "public"})
        ;_ (log/debug "SERVICE MAP KEYS" (keys sm) (:io.pedestal.http/routes sm))
        sm               (http/default-interceptors sm)
        sm               (http/dev-interceptors sm)
        ;_ (log/debug "SERVICE MAP KEYS" (keys sm) "\n\nInterceptors" (:io.pedestal.http/interceptors sm))
        runnable-service (http/create-server sm)]
    (http/start runnable-service)))

(defn stop-service [service]
  (http/stop service))

(defstate server
  :start (start-service)
  :stop (stop-service server))

(defn start []
  (mount/start-with-args {:config "config/prod.edn"}))

(defn stop []
  (mount/stop))

(defn restart []
  (stop)
  (start))

(defn -main
  "The entry-point"
  [& args]

  (start))




;; If you package the service up as a WAR,
;; some form of the following function sections is required (for io.pedestal.servlet.ClojureVarServlet).

;;(defonce servlet  (atom nil))
;;
;;(defn servlet-init
;;  [_ config]
;;  ;; Initialize your app here.
;;  (reset! servlet  (server/servlet-init service/service nil)))
;;
;;(defn servlet-service
;;  [_ request response]
;;  (server/servlet-service @servlet request response))
;;
;;(defn servlet-destroy
;;  [_]
;;  (server/servlet-destroy @servlet)
;;  (reset! servlet nil))

