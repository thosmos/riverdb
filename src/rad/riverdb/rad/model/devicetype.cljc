(ns riverdb.rad.model.devicetype
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.form :as form]
    #?(:clj [riverdb.rad.model.db-queries :as queries])
    [com.wsscode.pathom.connect :as pc]
    [theta.log :as log]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.picker-options :as po]))

(defattr uid :samplingdevicelookup/uuid :uuid
  {ao/identity? true
   ao/schema    :production})

(defattr Active :samplingdevicelookup/Active :boolean
  {ao/identities #{:samplingdevicelookup/uuid}
   ao/schema     :production
   ao/required?  true})

(defattr SampleDevice :samplingdevicelookup/SampleDevice :string
  {ao/identities #{:samplingdevicelookup/uuid}
   ao/schema     :production
   ao/required?  true})

(defattr SamplingMatrix :samplingdevicelookup/SamplingMatrix :string
  {ao/identities #{:samplingdevicelookup/uuid}
   ao/schema     :production
   ao/required?  true})

(pc/defresolver samplingdevicelookups-resolver [{:keys [query-params] :as env} input]
  {::pc/output [{:samplingdevicelookups/all [:samplingdevicelookup/uuid]}]}
  #?(:clj {:samplingdevicelookups/all (queries/get-all-samplingdevicelookups env query-params)}))

(def resolvers [samplingdevicelookups-resolver])
(def attributes [uid Active SampleDevice SamplingMatrix])