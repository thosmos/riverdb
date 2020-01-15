(ns riverdb.server-components.crux-service)
  ;(:require
  ;  [mount.core :refer [defstate]]
  ;  [taoensso.timbre :as log :refer [debug]]
  ;  [crux.api :as crux])
  ;(:import (crux.api ICruxAPI)))

;(defn ^ICruxAPI start []
;  (crux/start-node {
;                    ;:kv-backend "avisi.crux.kv.xodus.XodusKv"
;                    ;:db-dir "data/db-dir-1"
;                    ;:event-log-dir "data/eventlog-1"
;
;                    ;:crux.node/topology                 :crux.standalone/topology
;                    ;:crux.node/kv-store                 "crux.kv.memdb/kv"
;                    ;:crux.kv/db-dir                     "data/db-dir-1"
;                    ;:crux.standalone/event-log-dir      "data/eventlog-1"
;                    ;:crux.standalone/event-log-kv-store "crux.kv.memdb/kv"
;
;                    :crux.node/topology                 :crux.standalone/topology
;                    :crux.node/kv-store                 "crux.kv.memdb/kv"
;                    :crux.kv/db-dir                     "data/db-dir-1"
;                    :crux.standalone/event-log-dir      "data/eventlog-1"
;                    :crux.standalone/event-log-kv-store "crux.kv.memdb/kv"}))
;
;
;
;(defstate node
;  :start (start)
;  :stop (.close node))
;
;(defn cdb []
;  (crux/db node))
