(ns riverdb.ui.comps.sample-list
  (:require
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li a p h2 h3 button table thead
                                                tbody th tr td form label input select
                                                option span]]
    [com.fulcrologic.semantic-ui.elements.header.ui-header :refer [ui-header]]
    [riverdb.application :refer [SPA]]
    [riverdb.ui.edit.fieldmeasure :refer [ui-fm-list FieldMeasureList]]
    [riverdb.ui.edit.fieldobs :refer [ui-fo-list]]
    [riverdb.ui.edit.lab :refer [ui-lr-list]]
    [theta.log :as log :refer [debug info]]))

(defn calc-param-samples [params samples]
  (reduce
    (fn [out sa]
      (let [sa-param (get-in sa [:sample/Parameter :db/id])
            sa-const (get-in sa [:sample/Constituent :db/id])]
        (if sa-param
          ;; if the sample already has a parameter ref, just use it
          (assoc out sa-param sa)
          ;; otherwise, find a param that matches the const + devType
          (let [match-ps (filter #(= sa-const (get-in % [:parameter/Constituent :db/id])) params)]
            ;; if one matches, link them, otherwise, add to :other
            (if (seq match-ps)
              (assoc out (:db/id (first match-ps)) sa)
              (update out :other (fnil conj []) sa))))))
    {} samples))

(defsc SampleList [this {:keys [sample-type params samples] :as props} {:keys [sv-comp onChangeSample] :as comps}]
  {:query [:sv-ident :sample-type :params :samples :riverdb.theta.options/ns]}
  (let [param-samples (calc-param-samples params samples)
        props         (assoc props :param-samples param-samples)]
    (case sample-type
      :sampletypelookup.SampleTypeCode/FieldMeasure
      (ui-fm-list (comp/computed props comps))
      :sampletypelookup.SampleTypeCode/FieldObs
      (ui-fo-list (comp/computed props comps))
      :sampletypelookup.SampleTypeCode/Grab
      (ui-lr-list (comp/computed props comps))

      (do
        (debug "SAMPLE LIST TYPE" (str sample-type) props)
        (if sample-type
          (div {:key (str sample-type)}
            (div :.ui.segment {:key "fm-list"}
              (ui-header {} (str (name sample-type)))))
          (div :.ui.segment {:key "error"}
            (div "no samples")))))))

(def ui-sample-list (comp/factory SampleList {:keyfn #(str (:sample-type %))}))