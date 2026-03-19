(ns riverdb.rad.model.constituent
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

(defattr uid :constituentlookup/uuid :uuid
  {ao/identity? true
   ao/schema    :production})

(defattr Active :constituentlookup/Active :boolean
  {ao/identities #{:constituentlookup/uuid}
   ao/schema     :production})

(defattr Name :constituentlookup/Name :string
  {ao/identities #{:constituentlookup/uuid}
   ao/schema     :production})

(defattr ConstituentCode :constituentlookup/ConstituentCode :string
  {ao/identities #{:constituentlookup/uuid}
   ao/schema     :production})

(pc/defresolver all-constituents-resolver [env input]
  {::pc/output [{:constituentlookups/all [:constituentlookup/uuid]}]}
  #?(:clj {:constituentlookups/all ((find-uuids-factory :constituentlookup/uuid) env)}))

(def resolvers [all-constituents-resolver])
(def attributes [uid Name ConstituentCode])