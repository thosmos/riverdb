(ns riverdb.ui.user
  (:require
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h3 button]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [riverdb.ui.lookups :as looks]))

(defsc Agency [this props]
  {:ident [:org.riverdb.db.agencylookup/gid :db/id]
   :query [:db/id
           :agencylookup/AgencyCode
           :agencylookup/Name]})

(defsc Role [this props]
  {:ident [:org.riverdb.db.role/gid :db/id]
   :query [:db/id
           {:role/agency (comp/get-query Agency)}
           :role/name
           :role/type
           :role/uuid]})

(defsc User [this {:user/keys [name] :as props}]
  {:query [:db/id
           :user/email
           :user/name
           :user/uuid
           {:user/roles (comp/get-query Role)}]
   :ident [:user/id :db/id]}
  (dom/span name))

(def ui-user (comp/factory User {:keyfn :user/uuid}))
