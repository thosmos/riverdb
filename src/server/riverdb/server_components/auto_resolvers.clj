(ns riverdb.server-components.auto-resolvers
  (:require
    [riverdb.model :refer [all-attributes]]
    [mount.core :refer [defstate]]
    [com.fulcrologic.rad.resolvers :as res]
    [com.fulcrologic.rad.database-adapters.datomic :as datomic]
    [theta.log :as log]))

(defstate automatic-resolvers
  :start
  (vec
    (concat
      (res/generate-resolvers all-attributes)
      (datomic/generate-resolvers all-attributes :production))))
