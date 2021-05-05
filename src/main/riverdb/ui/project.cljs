(ns riverdb.ui.project
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [riverdb.ui.parameter :refer [Parameter]]))

(defsc Project [this props]
  {:ident [:org.riverdb.db.projectslookup/gid :db/id]
   :query [:db/id
           :riverdb.entity/ns
           :projectslookup/Active
           :projectslookup/AgencyCode
           :projectslookup/AgencyRef
           :projectslookup/Name
           {:projectslookup/Parameters (comp/get-query Parameter)}
           :projectslookup/ProjectID
           :projectslookup/ProjectsComments
           :projectslookup/Public
           :projectslookup/QAPPVersion
           :projectslookup/qappURL
           :projectslookup/Stations
           :projectslookup/uuid]})
