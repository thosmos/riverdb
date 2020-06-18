(ns riverdb.development-preload
  (:require
    [com.fulcrologic.fulcro.algorithms.timbre-support :as ts]
    [taoensso.timbre :as log]
    [clojure.string :as str]))

(js/console.log "Turning logging to :debug (in app.development-preload)")
(log/set-level! :debug)
(log/merge-config! {:output-fn ts/prefix-output-fn
                    :appenders {:console (ts/console-appender)}
                    :ns-blacklist  [
                                    "com.fulcrologic.fulcro.algorithms.indexing"
                                    "com.fulcrologic.fulcro.algorithms.tx-processing"
                                    "com.fulcrologic.fulcro.ui-state-machines"
                                    "com.fulcrologic.fulcro.routing.dynamic-routing"]})
