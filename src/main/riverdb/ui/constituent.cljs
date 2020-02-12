(ns riverdb.ui.constituent
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.algorithms.tempid :refer [tempid]]
    [riverdb.ui.lookups :as looks]))

(defsc Constituent [this props]
  {:ident         [:org.riverdb.db.constituentlookup/gid :db/id],
   :initial-state {:db/id (tempid) :riverdb.entity/ns :entity.ns/constituentlookup},
   :query         [:db/id
                   :riverdb.entity/ns
                   :constituentlookup/Active
                   {:constituentlookup/AnalyteCode (comp/get-query looks/matrixlookup-sum)}
                   :constituentlookup/ConstituentCode
                   :constituentlookup/DeviceType
                   {:constituentlookup/FractionCode (comp/get-query looks/fractionlookup-sum)}
                   :constituentlookup/HighValue
                   :constituentlookup/LowValue
                   {:constituentlookup/MatrixCode (comp/get-query looks/matrixlookup-sum)}
                   :constituentlookup/MaxValue
                   {:constituentlookup/MethodCode (comp/get-query looks/methodlookup-sum)}
                   :constituentlookup/MinValue
                   :constituentlookup/Name
                   {:constituentlookup/UnitCode (comp/get-query looks/unitlookup-sum)}]})