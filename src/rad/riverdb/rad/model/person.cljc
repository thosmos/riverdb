(ns riverdb.rad.model.person
  (:require
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.authorization :as auth]))

;; not using the alias to avoid needing to import for the UI build
;[com.fulcrologic.rad.database-adapters.datomic :as datomic]))


(def uid (attr/new-attribute :person/uuid :uuid
           {ao/identity?     true
            ao/schema       :production
            ::auth/authority     :local}))

(defattr Name :person/Name :string
  {ao/identities #{:person/uuid}
   ao/schema        :production})

(defattr IsStaff :person/IsStaff :boolean
  {ao/identities #{:person/uuid}
   ao/schema        :production})


;(defattr Agency :person/Agency :ref
;  {::datomic/entity-ids #{:person/uuid}
;   ::attr/target   :agencylookup/uuid
;   ::datomic/schema     :production
;   ::datomic/isComponent false})

(def attributes [uid Name IsStaff #_Agency])
