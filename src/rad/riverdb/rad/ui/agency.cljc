(ns riverdb.rad.ui.agency
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]

    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.picker-options :as po]
    [com.fulcrologic.rad.report-options :as ro]

    [theta.log :as log]))

(defsc AgencyQuery [this props]
  {:query [:agencylookup/uuid :agencylookup/AgencyCode]
   :ident :agencylookup/uuid})

(def agency-picker
  {po/cache-key       :picker/agency
   po/query-key       :all-agencies
   po/cache-time-ms   3600000
   po/query-component AgencyQuery
   po/options-xform   (fn [_ options]
                        (let [opts
                              (mapv
                                (fn [{:agencylookup/keys [uuid AgencyCode]}]
                                  {:text AgencyCode :value [:agencylookup/uuid uuid]})
                                (sort-by :agencylookup/AgencyCode options))]
                          ;(log/debug "AGENCY PICKER OPTS" opts)
                          opts))})