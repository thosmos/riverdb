(ns riverdb.ui.comps.sample-list
  (:require
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li a p h2 h3 button table thead
                                                tbody th tr td form label input select
                                                option span]]
    [riverdb.application :refer [SPA]]
    [riverdb.ui.edit.fieldmeasure :refer [ui-fm-list]]
    [theta.log :as log :refer [debug info]]))


(defsc SampleList [this {:ui/keys [sample-type params samples] :as props} {:keys [sv-comp onChangeSample] :as comps}]
  {:query [:ui/sample-type :ui/params :ui/samples]}
  (case sample-type
    :sampletypelookup.SampleTypeCode/FieldMeasure
    (ui-fm-list (comp/computed props comps))
    (div {:key (str sample-type)} (str sample-type))))

(def ui-sample-list (comp/factory SampleList))