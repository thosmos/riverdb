(ns riverdb.server-components.nrepl
  (:require
    [dotenv]
    [mount.core :refer [defstate]]
    [nrepl.server :refer [start-server stop-server]]
    [riverdb.server-components.config :refer [config]]))

(defstate ^{:on-reload :noop} nrepl
  :start
  (let [nrepl-port (Long/parseLong (or (dotenv/env :NREPL_PORT) "5959"))
        nrepl-bind (or (dotenv/env :NREPL_BIND) "127.0.0.1")]
    (start-server :bind nrepl-bind :port nrepl-port)))
