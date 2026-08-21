(ns ^{;; This namespace owns the mount defstate for the running http-kit server.
      ;; Reloading it would replace the state var and orphan the live server, so
      ;; tools.namespace is told to leave it alone. Change anything in here and
      ;; you need an explicit (restart); everything else in riverdb.html.* is
      ;; hot-reloadable.
      :clojure.tools.namespace.repl/load   false
      :clojure.tools.namespace.repl/unload false}
  riverdb.html.server
  "Server-side HTML app: http-kit + reitit + Datastar SSE.

  Independent of the Pedestal/Lacinia server on 8989. It shares this process
  only for the Datomic connection and mount lifecycle; nothing here depends on
  Pedestal, so when the GraphQL/SPA stack goes away this becomes the whole app."
  (:require
    [clojure.tools.logging :refer [debug]]
    [mount.core :as mount :refer [defstate]]
    [org.httpkit.server :as hk]
    [theta.util]))

(def default-port
  "9595 unless HTML_PORT says otherwise. Overridable so a second instance — a
  test harness, a side-by-side comparison — can run without fighting the REPL
  you already have on 9595."
  (or (some-> (dotenv/env :HTML_PORT) parse-long) 9595))

(defonce live-handler?
  ;; In dev, resolve the handler by symbol on each request so that reloading
  ;; riverdb.html.routes swaps in the new route table without a restart. A
  ;; compile-time reference would pin the var that existed at startup.
  (atom (not= (theta.util/app-env) "prod")))

(defn- resolve-handler []
  @(requiring-resolve 'riverdb.html.routes/handler))

(defn ring-handler []
  (if @live-handler?
    (fn [request] ((resolve-handler) request))
    (resolve-handler)))

(defn start-service
  ([] (start-service default-port))
  ([port]
   (debug "Starting HTML service on port" port
          (if @live-handler? "(live handler)" "(static handler)"))
   (hk/run-server (ring-handler)
     {:port port
      :ip "0.0.0.0"
      :server-header "riverdb"
      ;; Return the HttpServer rather than the legacy stop fn, so stop-service
      ;; can wait for shutdown to actually finish.
      :legacy-return-value? false
      :worker-name-prefix "riverdb-html-"})))

(defn stop-service
  "http-kit's shutdown is asynchronous: server-stop! returns a promise that is
  delivered once the server thread actually completes. The legacy stop fn
  discards that promise, so mount's :start would run before the listening
  socket was released and (restart) died with BindException. SSE connections
  are long-lived, which widens the window. Deref, with a ceiling so a wedged
  connection can't hang the REPL."
  [server]
  (when server
    (debug "Stopping HTML service on port" (hk/server-port server))
    (let [stopped (hk/server-stop! server {:timeout 100})
          result  (if stopped (deref stopped 5000 :timed-out) :already-stopping)]
      (when (= :timed-out result)
        (debug "HTML service did not stop within 5s"))
      result)))

(defstate html-server
  :start (start-service)
  :stop (stop-service html-server))

(comment
  (mount/start #'html-server)
  (mount/stop #'html-server))
