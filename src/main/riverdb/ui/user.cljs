(ns riverdb.ui.user
  (:require
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h3 button]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.parameter :refer [Parameter]]
    [riverdb.ui.agency :refer [Agency]]))


;(defsc Role [this props]
;  {:ident [:org.riverdb.db.role/gid :db/id]
;   :query [:db/id
;           {:role/agency (comp/get-query Agency)}
;           :role/name
;           :role/type
;           :role/uuid]})

(defsc RoleType [this props]
  {:ident :role.type/uuid
   :query [:db/ident
           :role.type/label
           :role.type/uuid]})

(defsc User [this {:user/keys [name] :as props}]
  {:query [:db/id
           :user/email
           :user/name
           :user/uuid
           {:user/agency (comp/get-query Agency)}
           {:user/role (comp/get-query RoleType)}]
   :ident [:user/id :db/id]}
  (dom/span name))

(def ui-user (comp/factory User {:keyfn :user/uuid}))
