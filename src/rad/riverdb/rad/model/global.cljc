(ns riverdb.rad.model.global
  (:require
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.authorization :as auth]))

(def EntityNS
  (attr/new-attribute :riverdb.entity/ns :keyword
    {ao/identity?     false
     ao/schema       :production
     ao/identities #{:person/uuid :samplingdevicelookup/uuid :samplingdevice/uuid :agencylookup/uuid}
     ::auth/authority     :local}))

(def attributes [EntityNS])