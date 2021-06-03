(ns riverdb.server-components.auto-resolvers
  (:require
    [riverdb.rad.model :refer [all-attributes]]
    [mount.core :refer [defstate]]
    [com.fulcrologic.rad.resolvers :as res]
    [com.fulcrologic.rad.database-adapters.datomic :as datomic]
    [theta.log :as log]))

;(defstate automatic-resolvers
;  :start
;  (let [all-attributes (remove #(= (:riverdb.rad.attributes/reverse-ref? %) true) all-attributes)
;        reversers (remove #(= (:riverdb.rad.attributes/reverse-ref? %) true) all-attributes)]
;    (log/debug "Auto Attr Resolvers" "reversers" reversers)
;    (vec
;      (concat
;        (res/generate-resolvers all-attributes)
;        (datomic/generate-resolvers all-attributes :production)))))
