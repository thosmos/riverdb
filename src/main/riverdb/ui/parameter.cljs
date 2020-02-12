(ns riverdb.ui.parameter
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.constituent :refer [Constituent]]))

(defsc Parameter [this props]
  {:ident [:org.riverdb.db.parameter/gid :db/id]
   :query [:db/id
           :riverdb.entity/ns
           :parameter/active
           :parameter/color
           :parameter/high
           :parameter/lines
           :parameter/low
           :parameter/name
           :parameter/nameShort
           :parameter/precisionCode
           :parameter/replicates
           :parameter/replicatesEntry
           :parameter/replicatesElide
           :parameter/uuid
           {:parameter/constituentlookupRef (comp/get-query Constituent)}
           {:parameter/samplingdevicelookupRef (comp/get-query looks/samplingdevicelookup-sum)}
           {:parameter/sampleTypeRef (comp/get-query looks/sampletypelookup-sum)}]})