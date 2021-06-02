(ns riverdb.rad.ui.project
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]

    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.picker-options :as po]
    [com.fulcrologic.rad.report-options :as ro]

    [theta.log :as log]))

(defsc ProjectOptsQuery [this props]
  {:query [:projectslookup/uuid :projectslookup/ProjectID]
   :ident :projectslookup/uuid})

(def proj-picker
  {po/cache-key        :picker/projects
   po/query-key        :projectslookup/all
   po/query-parameters (fn [app cls props]
                         (log/debug "Projects Picker Query Params FN" props)
                         {:projectslookup/AgencyRef [:agencylookup/AgencyCode "SYRCL"]})
   po/cache-time-ms    3600000
   po/query-component  ProjectOptsQuery
   po/options-xform    (fn [_ options]
                         (let [opts
                               (mapv
                                 (fn [{:projectslookup/keys [uuid ProjectID Name]}]
                                   {:text ProjectID :value [:projectslookup/uuid uuid]})
                                 (sort-by :agencylookup/AgencyCode options))]
                           ;(log/debug "projectslookup PICKER OPTS" opts)
                           opts))})