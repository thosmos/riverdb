(ns riverdb.rad.ui.constituent
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]

    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.picker-options :as po]
    [com.fulcrologic.rad.report-options :as ro]

    [theta.log :as log]))

(defsc ConstituentQuery [this props]
  {:query [:db/id :riverdb.entity/ns :constituentlookup/uuid :constituentlookup/Active :constituentlookup/ConstituentCode :constituentlookup/Name]
   ;:constituentlookup/DerivedName :constituentlookup/AnalyteCode :constituentlookup/FractionCode :constituentlookup/MatrixCode :constituentlookup/MethodCode :constituentlookup/UnitCode
   :ident :constituentlookup/uuid})

(def constituent-picker
  {po/cache-key       :picker/constituent
   po/query-key       :constituentlookups/all
   po/cache-time-ms   3600000
   po/query-component ConstituentQuery
   po/options-xform   (fn [_ options]
                        (let [opts
                              (mapv
                                (fn [{:constituentlookup/keys [uuid Name]}]
                                  {:text Name :value [:constituentlookup/uuid uuid]})
                                (sort-by :constituentlookup/Name options))]
                          (log/debug "CONSTITUENT PICKER OPTS" opts)
                          opts))})