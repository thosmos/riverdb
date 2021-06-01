(ns riverdb.rad.model.device
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


(defattr uid :samplingdevice/uuid :uuid
  {ao/identity? true
   ao/schema    :production})

(defattr Active :samplingdevice/Active :boolean
  {ao/identities #{:samplingdevice/uuid}
   ao/schema     :production
   ao/required?  true})

(defattr DeviceType :samplingdevice/DeviceType :ref
  {ao/identities #{:samplingdevice/uuid}
   ao/schema     :production
   ao/target     :samplingdevicelookup/uuid
   ao/required?  true})

(defattr CommonID :samplingdevice/CommonID :string
  {ao/identities #{:samplingdevice/uuid}
   ao/schema     :production
   ao/required?  true})

(defattr Agency :samplingdevice/Agency :ref
  {ao/identities #{:samplingdevice/uuid}
   ao/schema     :production
   ao/target     :agencylookup/uuid
   ao/required?  true})

(pc/defresolver all-devices-resolver [env input]
  {::pc/output [{:devices/all [:samplingdevice/uuid]}]}
  #?(:clj {:devices/all ((find-uuids-factory :samplingdevice/uuid) env)}))

(def resolvers [all-devices-resolver])
(def attributes [uid Active DeviceType CommonID Agency])