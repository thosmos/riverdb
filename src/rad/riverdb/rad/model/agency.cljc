(ns riverdb.rad.model.agency
  (:require
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.authorization :as auth]))

(defattr uid :agencylookup/uuid :uuid
  {ao/identity?     true
   ao/schema        :production})

(defattr AgencyDescr :agencylookup/AgencyDescr :string
  {ao/identities #{:agencylookup/uuid}
   ao/schema        :production})

(defattr AgencyCode :agencylookup/AgencyCode :string
  {ao/identities #{:agencylookup/uuid}
   ao/schema        :production
   ao/required? true})

(def attributes [uid AgencyDescr AgencyCode])
