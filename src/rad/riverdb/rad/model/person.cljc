(ns riverdb.rad.model.person
  (:require
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.authorization :as auth]))

;; not using the alias to avoid needing to import for the UI build
;[com.fulcrologic.rad.database-adapters.datomic :as datomic]))

(defattr uid :person/uuid :uuid
  {ao/identity? true
   ao/schema    :production})

(defattr Name :person/Name :string
  {ao/identities #{:person/uuid}
   ao/schema        :production
   ao/required? true})

(defattr IsStaff :person/IsStaff :boolean
  {ao/identities #{:person/uuid}
   ao/schema        :production})

(defattr Agency :person/Agency :ref
  {ao/identities #{:person/uuid}
   ao/target :agencylookup/uuid
   ao/required? true
   ao/schema     :production})

(def attributes [uid Name IsStaff Agency])
