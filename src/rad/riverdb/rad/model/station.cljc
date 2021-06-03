(ns riverdb.rad.model.station
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


(defattr uid :stationlookup/uuid :uuid
  {ao/identity? true
   ao/schema    :production})

(defattr StationCode :stationlookup/StationCode :string
  {ao/identity?  true
   ao/identities #{:stationlookup/uuid}
   ao/schema     :production
   ao/required?  true})

(defattr StationID :stationlookup/StationID :string
  {ao/identities #{:stationlookup/uuid}
   ao/schema     :production
   ao/required?  true})

(defattr StationIDLong :stationlookup/StationIDLong :long
  {ao/identities #{:stationlookup/uuid}
   ao/schema     :production
   ao/required?  true})

(defattr Active :stationlookup/Active :boolean
  {ao/identities #{:stationlookup/uuid}
   ao/schema     :production
   ao/required?  true})

(defattr Agency :stationlookup/Agency :ref
  {ao/identities #{:stationlookup/uuid}
   ao/schema     :production
   ao/target     :agencylookup/uuid
   ao/required?  true})

(defattr StationName :stationlookup/StationName :string
  {ao/identities #{:stationlookup/uuid}
   ao/schema     :production
   ao/required?  true})

(defattr Description :stationlookup/Description :string
  {ao/identities #{:stationlookup/uuid}
   ao/schema     :production
   ao/required?  false})

(defattr StationComments :stationlookup/StationComments :string
  {ao/identities #{:stationlookup/uuid}
   ao/schema     :production
   ao/required?  false})

(defattr ForkTribGroup :stationlookup/ForkTribGroup :string
  {ao/identities #{:stationlookup/uuid}
   ao/schema     :production
   ao/required?  false})

(defattr LocalWatershed :stationlookup/LocalWatershed :string
  {ao/identities #{:stationlookup/uuid}
   ao/schema     :production
   ao/required?  false})

(defattr RiverFork :stationlookup/RiverFork :string
  {ao/identities #{:stationlookup/uuid}
   ao/schema     :production
   ao/required?  false})

(defattr NHDWaterbody :stationlookup/NHDWaterbody :string
  {ao/identities #{:stationlookup/uuid}
   ao/schema     :production
   ao/required?  false})

(defattr Lat :stationlookup/TargetLat :decimal
  {ao/identities #{:stationlookup/uuid}
   ao/schema     :production
   ao/required?  false})

(defattr Lon :stationlookup/TargetLong :decimal
  {ao/identities #{:stationlookup/uuid}
   ao/schema     :production
   ao/required?  false})

(defattr DOcorrection :stationlookup/DOcorrection :decimal
  {ao/identities #{:stationlookup/uuid}
   ao/schema     :production
   ao/required?  false})

(defattr Elevation :stationlookup/Elevation :decimal
  {ao/identities #{:stationlookup/uuid}
   ao/schema     :production
   ao/required?  false})

(defattr Projects :projectslookup/_Stations :ref
  {ao/identities  #{:stationlookup/uuid}
   ;; no schema for this, instead we intercept the delta and
   ;; rewrite to a project diff in riverdb.rad.middleware
   ;; ao/schema     :production
   ao/target      :projectslookup/uuid
   ao/cardinality :many})

;(defn station-projects-resolver [{:keys [query-params] :as env} input]
;  (log/debug "STATION PROJECTS RESOLVER" input query-params)
;  [])

;(defattr StationProjects :stationlookup/Projects :ref
;  {ao/identities  #{:stationlookup/uuid}
;   ao/target      :projectslookup/uuid
;   ao/cardinality :many
;   ao/pc-input    #{:stationlookup/uuid}
;   ao/pc-output   [{:stationlookup/Projects [:projectslookup/uuid :projectslookup/ProjectID]}]
;   ao/pc-resolve  station-projects-resolver})

(pc/defresolver stationlookup-resolver [env input]
  {::pc/output [{:stationlookup/all [:stationlookup/uuid]}]}
  #?(:clj {:stationlookup/all ((find-uuids-factory :stationlookup/uuid) env)}))

(def resolvers [stationlookup-resolver])
(def attributes [uid Active StationCode StationID StationName StationIDLong Agency Description StationComments ForkTribGroup RiverFork LocalWatershed NHDWaterbody Lat Lon DOcorrection Elevation Projects])