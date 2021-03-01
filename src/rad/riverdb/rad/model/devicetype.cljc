(ns riverdb.rad.model.devicetype
  (:require
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.database-adapters.datomic :as datomic]))

(def name
  {::attr/type :string
   ::attr/qualified-key :samplingdevicelookup/SampleDevice})




;(assoc ::type type)
;(assoc ::qualified-key kw)