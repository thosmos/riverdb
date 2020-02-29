(ns riverdb.ui.globals
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [theta.log :refer [debug]]))

(defsc Attribute [this props]
  {:ident :attr/key
   :query [:attr/key
           :attr/type
           :attr/name
           :attr/identity
           :attr/cardinality
           :attr/ref
           :attr/refkey
           :attr/derived
           :attr/deriveFn
           :attr/deriveAtttrs]})

(defsc Entity [this props]
  {:ident :entity/ns
   :query [:entity/ns
           :entity/name
           :entity/lookup
           :entity/prKeys
           :entity/nameKey
           {:entity/attrs (comp/get-query Attribute)}]})

(defsc DBIdents [this props]
  {:ident :db/ident
   :query [:db/id :db/ident :riverdb.entity/ns]})

(defsc Globals
  "a single root query of some things at initial session"
  [this props]
  {:ident (fn [] [:component/id :globals])
   :query [{:db-idents (comp/get-query DBIdents)}]})
           ;{:meta-entities (comp/get-query Entity)}
           ;{:meta-global-attrs (comp/get-query Attribute)}]})
