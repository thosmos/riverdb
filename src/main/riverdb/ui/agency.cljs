(ns riverdb.ui.agency
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [riverdb.application :refer [SPA]]
    [riverdb.ui.project :refer [Project]]
    [theta.log :refer [debug]]))



(defsc Agency [this props]
  {:ident [:org.riverdb.db.agencylookup/gid :db/id]
   :query [:db/id
           :riverdb.entity/ns
           :agencylookup/uuid
           :agencylookup/Active
           :agencylookup/AgencyCode
           {:agencylookup/Projects (comp/get-query Project)}]})

(defn preload-agency [agID]
  (debug "PRELOAD AGENCY" agID)
  (df/load! SPA (comp/get-ident Agency {:db/id agID}) Agency))
