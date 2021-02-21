(ns riverdb.rad.model.person
  (:require
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.authorization :as auth]))


(def uid (attr/new-attribute :person/uuid :uuid
           {::attr/identity?     true
            :com.fulcrologic.rad.database-adapters.datomic/schema          :production
            ::auth/authority     :local}))

(defattr Name :person/Name :string
  {:com.fulcrologic.rad.database-adapters.datomic/entity-ids #{:person/uuid}
   :com.fulcrologic.rad.database-adapters.datomic/schema     :production})

(defattr IsStaff :person/IsStaff :boolean
  {:com.fulcrologic.rad.database-adapters.datomic/entity-ids #{:person/uuid}
   :com.fulcrologic.rad.database-adapters.datomic/schema     :production})

(defattr Agency :person/Agency :ref
  {:com.fulcrologic.rad.database-adapters.datomic/entity-ids #{:person/uuid}
   ::attr/target   :agencylookup/uuid
   :com.fulcrologic.rad.database-adapters.datomic/schema     :production
   :com.fulcrologic.rad.database-adapters.datomic/isComponent false})

(def attributes [uid Name IsStaff Agency])