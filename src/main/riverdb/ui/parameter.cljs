(ns riverdb.ui.parameter
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.constituent :refer [Constituent]]))

(defsc Parameter [this props]
  {:ident [:org.riverdb.db.parameter/gid :db/id]
   :query [:db/id
           :riverdb.entity/ns
           :parameter/Active
           :parameter/Color
           :parameter/High
           :parameter/Lines
           :parameter/Low
           :parameter/Name
           :parameter/NameShort
           :parameter/Order
           :parameter/PrecisionCode
           :parameter/Replicates
           :parameter/ReplicatesEntry
           :parameter/ReplicatesElide
           :parameter/uuid
           {:parameter/Constituent (comp/get-query Constituent)}
           {:parameter/DeviceType (comp/get-query looks/samplingdevicelookup-sum)}
           {:parameter/SampleType (comp/get-query looks/sampletypelookup-sum)}]})