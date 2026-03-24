(ns riverdb.rad.model.devicetype
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.form :as form]
    #?(:clj [riverdb.rad.model.db-queries :as queries])
    #?(:clj [riverdb.api.resolvers :refer [find-uuids-factory]])
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

(defattr Description :samplingdevicelookup/SampleDeviceDescr :string
  {ao/identities #{:samplingdevicelookup/uuid}
   ao/schema     :production
   ao/required?  false})

(defattr Constituent :samplingdevicelookup/Constituent :ref
         {ao/identities #{:samplingdevicelookup/uuid}
          ao/schema     :production
          ao/target     :constituentlookup/uuid
          ao/required?  false})

(defattr SamplingMatrix :samplingdevicelookup/SamplingMatrix :string
  {ao/identities #{:samplingdevicelookup/uuid}
   ao/schema     :production
   ao/required?  false})

(defattr Max :samplingdevicelookup/DeviceMax :bigdec
        {ao/identities #{:samplingdevicelookup/uuid}
         ao/schema      :production
         ao/required?  false})

(defattr Min :samplingdevicelookup/DeviceMin :bigdec
  {ao/identities #{:samplingdevicelookup/uuid}
   ao/schema      :production
   ao/required?  false})

(defattr QAMax :samplingdevicelookup/QAmax :bigdec
  {ao/identities #{:samplingdevicelookup/uuid}
   ao/schema      :production
   ao/required?  false})

(defattr QAMin :samplingdevicelookup/QAmin :bigdec
  {ao/identities #{:samplingdevicelookup/uuid}
   ao/schema      :production
   ao/required?  false})

(defattr Resolution :samplingdevicelookup/Resolution :bigdec
  {ao/identities #{:samplingdevicelookup/uuid}
   ao/schema      :production
   ao/required?  false})

(defattr Scale :samplingdevicelookup/Scale :long
  {ao/identities #{:samplingdevicelookup/uuid}
   ao/schema      :production
   ao/required?  false})

(pc/defresolver samplingdevicelookups-resolver [env input]
  {::pc/output [{:samplingdevicelookups/all [:samplingdevicelookup/uuid]}]}
  #?(:clj {:samplingdevicelookups/all ((find-uuids-factory :samplingdevicelookup/uuid) env)}))

;(pc/defresolver samplingdevicelookups-resolver [{:keys [query-params] :as env} input]
;  {::pc/output [{:samplingdevicelookups/all [:samplingdevicelookup/uuid]}]}
;  #?(:clj {:samplingdevicelookups/all (queries/get-all-samplingdevicelookups env query-params)}))

(def resolvers [samplingdevicelookups-resolver])
(def attributes [uid Active SampleDevice Description Constituent SamplingMatrix Max Min QAMax QAMin Resolution Scale])