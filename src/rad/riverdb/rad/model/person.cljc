(ns riverdb.rad.model.person
  (:require
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.database-adapters.datomic :as datomic]))


(def uid (attr/new-attribute :person/uuid :uuid
           {::attr/identity?     true
            ::datomic/schema          :production
            ::auth/authority     :local}))

(defattr Name :person/Name :string
  {::datomic/entity-ids #{:person/uuid}
   ::datomic/schema     :production})

(defattr IsStaff :person/IsStaff :boolean
  {::datomic/entity-ids #{:person/uuid}
   ::datomic/schema     :production})

;(defattr Agency :person/Agency :ref
;  {::datomic/entity-ids #{:person/uuid}
;   ::attr/target   :agencylookup/uuid
;   ::datomic/schema     :production
;   ::datomic/isComponent false})

(def attributes [uid Name IsStaff #_Agency])