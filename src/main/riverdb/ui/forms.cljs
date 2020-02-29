(ns riverdb.ui.forms
  (:require
    [clojure.data]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.application :as fapp]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li a p h2 h3 button table thead
                                                tbody th tr td form label input select
                                                option span]]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [goog.object :as gobj]
    [riverdb.application :refer [SPA]]
    [riverdb.api.mutations :as rm]
    [riverdb.util :refer [sort-maps-by with-index]]
    [theta.log :as log :refer [debug info]]))

(defsc SampleTypeCodeForm [this {:keys [] :as props}]
  {:ident       [:org.riverdb.db.sampletypelookup/gid :db/id]
   :query       [:db/id
                 :db/ident
                 fs/form-config-join
                 :riverdb.entity/ns
                 :sampletypelookup/uuid
                 :sampletypelookup/SampleTypeCode
                 :sampletypelookup/CollectionType]
   :form-fields #{:db/id :db/ident :riverdb.entity/ns}})