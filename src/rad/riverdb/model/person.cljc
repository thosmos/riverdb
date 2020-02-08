(ns riverdb.model.person
  (:require
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.database-adapters.datomic :as db]))


(def uuid (attr/new-attribute :person/uuid :uuid
            {::attr/identity?     true
             ::db/schema          :production
             ::auth/authority     :local}))

(defattr Name :person/Name :string
  {::db/entity-ids #{:person/uuid}
   ::db/schema     :production})

(defattr IsStaff :person/isStaff :boolean
  {::db/entity-ids #{:person/uuid}
   ::db/schema     :production})

(defattr Agency :person/Agency :ref
  {::db/entity-ids #{:person/uuid}
   ::attr/target   :agencylookup/uuid
   ::db/schema     :production
   :db/isComponent false})

(def attributes [uuid Name IsStaff Agency])