(ns riverdb.ui.project
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [riverdb.ui.parameter :refer [Parameter]]))

(defsc Project [this props]
  {:ident [:org.riverdb.db.projectslookup/gid :db/id]
   :query [:db/id
           {:projectslookup/Parameters (comp/get-query Parameter)}
           :projectslookup/Active
           :projectslookup/Name]})
