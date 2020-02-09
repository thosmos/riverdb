(ns riverdb.server-components.datomic
  (:require
    [com.fulcrologic.rad.database-adapters.datomic :as datomic]
    [mount.core :refer [defstate]]
    [riverdb.model :refer [all-attributes]]
    [riverdb.server-components.config :refer [config]]))

(defstate ^{:on-reload :noop} datomic-connections
  :start
  (datomic/start-databases all-attributes config))
