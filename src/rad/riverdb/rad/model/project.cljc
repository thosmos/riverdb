(ns riverdb.rad.model.project
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.form :as form]
    #?(:clj [riverdb.api.resolvers :refer [find-uuids-factory]])
    [com.wsscode.pathom.connect :as pc]
    [theta.log :as log]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.picker-options :as po]))

(defattr uid :projectslookup/uuid :uuid
  {ao/identity? true
   ao/schema    :production})

(defattr ProjectID :projectslookup/ProjectID :string
  {ao/identities #{:projectslookup/uuid}
   ao/identity?  true
   ao/schema     :production
   ao/required?  true})

(defattr Name :projectslookup/Name :string
  {ao/identities #{:projectslookup/uuid}
   ao/schema     :production})

(defattr Agency :projectslookup/AgencyRef :ref
  {ao/identities #{:projectslookup/uuid}
   ao/schema     :production
   ao/target     :agencylookup/uuid
   ao/required?  true})

(defattr Parameters :projectslookup/Parameters :ref
  {ao/identities  #{:projectslookup/uuid}
   ao/schema      :production
   ao/target      :parameter/uuid
   ao/cardinality :many
   ao/required?   false})

(pc/defresolver all-projects-resolver [env input]
  {::pc/output [{:projectslookup/all [:projectslookup/uuid]}]}
  #?(:clj {:projectslookup/all ((find-uuids-factory :projectslookup/uuid) env)}))

(def resolvers [all-projects-resolver])
(def attributes [uid ProjectID Name Agency Parameters])