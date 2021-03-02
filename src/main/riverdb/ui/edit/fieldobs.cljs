(ns riverdb.ui.edit.fieldobs
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]
    [clojure.data]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.normalized-state :refer [integrate-ident]]
    [com.fulcrologic.fulcro.application :as fapp :refer [current-state]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li a p h2 h3 button table thead
                                                tbody th tr td form label input select
                                                option span]]
    [com.fulcrologic.fulcro.mutations :as fm :refer [defmutation]]
    [com.fulcrologic.fulcro.networking.file-upload :as file-upload]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as uism :refer [defstatemachine]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-dropdown :refer [ui-form-dropdown]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-input :refer [ui-form-input]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-text-area :refer [ui-form-text-area]]
    [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :refer [ui-dropdown]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form :refer [ui-form]]
    [com.fulcrologic.semantic-ui.elements.input.ui-input :refer [ui-input]]
    [com.fulcrologic.semantic-ui.elements.header.ui-header :refer [ui-header]]
    [com.fulcrologic.semantic-ui.elements.loader.ui-loader :refer [ui-loader]]
    [com.fulcrologic.semantic-ui.modules.dimmer.ui-dimmer :refer [ui-dimmer]]
    [com.fulcrologic.semantic-ui.addons.pagination.ui-pagination :refer [ui-pagination]]
    [com.fulcrologic.semantic-ui.modules.checkbox.ui-checkbox :refer [ui-checkbox]]
    [com.fulcrologic.semantic-ui.addons.radio.ui-radio :refer [ui-radio]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table :refer [ui-table]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table-body :refer [ui-table-body]]
    [com.fulcrologic.semantic-ui.modules.popup.ui-popup :refer [ui-popup]]
    [com.fulcrologic.semantic-ui.addons.confirm.ui-confirm :refer [ui-confirm]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal :refer [ui-modal]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-header :refer [ui-modal-header]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-content :refer [ui-modal-content]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-actions :refer [ui-modal-actions]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-description :refer [ui-modal-description]]

    [riverdb.application :refer [SPA]]
    [riverdb.api.mutations :as rm]
    [riverdb.util :refer [sort-maps-by with-index]]
    [riverdb.ui.agency :refer [preload-agency Agency]]
    [riverdb.ui.components :refer [ui-datepicker]]
    [riverdb.ui.forms :refer [SampleTypeCodeForm]]
    [riverdb.ui.forms.FieldResult :refer [FieldResultForm]]
    [riverdb.ui.forms.sample :refer [SampleForm ConstituentRefForm FieldObsResultForm FieldObsVarForm]]
    [riverdb.ui.globals :refer [Globals]]
    [riverdb.ui.lookup-options :refer [preload-options ui-theta-options ThetaOptions]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.parameter :refer [Parameter]]
    [riverdb.ui.project-years :refer [ProjectYears ui-project-years]]
    [riverdb.ui.routes :as routes]
    [riverdb.ui.session :refer [Session]]
    [riverdb.ui.inputs :refer [ui-float-input]]
    [riverdb.ui.upload :refer [ui-upload-modal]]
    [riverdb.ui.util :as rutil :refer [walk-ident-refs* walk-ident-refs make-tempid make-validator parse-float rui-checkbox rui-int rui-bigdec rui-input ui-cancel-save set-editing set-value set-value!! set-refs! set-ref! set-ref set-refs get-ref-set-val lookup-db-ident filter-param-typecode]]
    [riverdb.util :refer [paginate nest-by filter-sample-typecode]]
    [com.rpl.specter :as sp :refer [ALL LAST]]
    ;[tick.alpha.api :as t]
    [theta.log :as log :refer [debug info]]
    [thosmos.util :as tu]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [riverdb.roles :as roles]
    [edn-query-language.core :as eql]
    [tick.core :as t]
    [testdouble.cljs.csv :as csv]))


(defn load-all-obsvars [this target]
  (let []
    (debug "LOAD OBSVARS" target)
    (df/load! this :org.riverdb.db.fieldobsvarlookup FieldObsVarForm
      {:params  {:limit -1}
       :target  target
       :marker  ::obsvars
       :without #{}})))


(defmutation load-obsvars [{:keys [this-ident const-ident]}]
  (action [{:keys [app state]}]

    (let [st        @state
          ana-ident (get-in st (conj const-ident :constituentlookup/AnalyteCode))
          ana-name  (get-in st (conj ana-ident :analytelookup/AnalyteName))]
      (debug "LOAD OBSVARS" this-ident const-ident ana-ident ana-name)
      (df/load! app :org.riverdb.db.fieldobsvarlookup FieldObsVarForm
        {:params  {:filter {:fieldobsvarlookup/AnalyteName ana-name}}
         :target               (conj this-ident :ui/obsvars)
         :marker  ::obsvars
         :without #{}}))))

(defn load-obsvars-const [this const]
  (let [this-ident (comp/get-ident this)
        Analyte (get-in const [:constituentlookup/AnalyteCode :analytelookup/AnalyteName])]
    (debug "LOAD OBS VAR CONST" Analyte const)
    (df/load! this [:org.riverdb.db.constituentlookup/gid (:db/id const)] ConstituentRefForm
      {;:params               {:filter}
       :post-mutation        `load-obsvars
       :post-mutation-params {:this-ident this-ident
                              :const-ident [:org.riverdb.db.constituentlookup/gid (:db/id const)]}
       :without              #{}})))


(fm/defmutation save-obs [{:keys [sv-ident sample obsresult value param intval]}]
  (action [{:keys [state]}]
    #_(debug "SAVE OBS RESULT" "value" value "intval" intval "sv-ident" sv-ident "sample" sample "obsresult" obsresult "param" param)
    ;; are we dealing with an existing observation or a new one?
    (try
      (if obsresult

        ;; are we updating or deleting?
        (let [rm (or (nil? value) (= value ""))]

          (if rm

            ;; value is missing so we remove the sample
            (let [sample-ident (rutil/thing->ident sample)
                  path         (conj sv-ident :sitevisit/Samples)]
              (debug "REMOVE SAMPLE" sample-ident)
              (swap! state merge/remove-ident* sample-ident path))

            ;; update the existing observation
            (let [obs-ident (rutil/thing->ident obsresult)
                  obs-type  (:parameter/FieldObsType param)]
              (debug "UPDATE OBS!" "value" value)
              (swap! state
                (fn [st]
                  (-> st
                    (update-in obs-ident
                      (fn [obs]
                        (cond->
                          (-> obs
                            (dissoc :fieldobsresult/TextResult)
                            (dissoc :fieldobsresult/ConstituentRowID))

                          (= obs-type :ref)
                          (->
                            (assoc :fieldobsresult/RefResult
                                   (rutil/ent-ns->ident :entity.ns/fieldobsvarlookup value))
                            (assoc :fieldobsresult/IntResult intval))

                          (= obs-type :refs)
                          (assoc :fieldobsresult/RefResults
                                 (mapv #(rutil/ent-ns->ident :entity.ns/fieldobsvarlookup %) value))

                          (= obs-type :bigdec)
                          (assoc :fieldobsresult/BigDecResult value)

                          (= obs-type :text)
                          (assoc :fieldobsresult/TextResult value))))

                    (fs/mark-complete* obs-ident)))))))

        ;; we don't have an existing observation, so create a new sample & obs
        (let [new-id   (tempid/tempid)
              obs-type (:parameter/FieldObsType param)
              init-obs (cond-> {:id new-id :uuid (tempid/uuid)}
                         (and (= obs-type :ref) value)
                         (->
                           (assoc :ref (rutil/ent-ns->ident :entity.ns/fieldobsvarlookup value))
                           (assoc :int intval))
                         (and (= obs-type :refs) value)
                         (assoc :refs (mapv #(rutil/ent-ns->ident :entity.ns/fieldobsvarlookup %) value))
                         (= obs-type :bigdec)
                         (assoc :bigdec value)
                         (= obs-type :text)
                         (assoc :text value))
              new-fr   (comp/get-initial-state FieldObsResultForm init-obs)
              new-sa   (comp/get-initial-state SampleForm
                         {:param param})
              new-sa   (assoc new-sa :sample/FieldObsResults [new-fr])
              new-form (fs/add-form-config SampleForm new-sa)]

          (debug "NEW OBS" sv-ident obs-type new-form)
          (swap! state
            (fn [st]
              (-> st
                (merge/merge-component SampleForm new-form :append (conj sv-ident :sitevisit/Samples))
                (fs/mark-complete* sv-ident :sitevisit/Samples))))))
      (catch js/Object ex (log/error "OBS UPDATE ERROR" ex)))))




(defsc FieldObsList [this {:keys [sv-ident sample-type params samples param-samples ui/obsvars] :as props} {:keys [sv-comp onChangeSample] :as comps}]
  {:query [:sv-ident
           :sample-type
           :params
           :samples
           :param-samples
           {:ui/obsvars (comp/get-query FieldObsVarForm)}
           :riverdb.theta.options/ns]}
  (let [fieldobsvarlookup-options (get-in props [:riverdb.theta.options/ns :entity.ns/fieldobsvarlookup])]
    #_(debug "RENDER FieldObsList" "param-samps" param-samples "fieldobsvarlookup-options" fieldobsvarlookup-options)

    (div :.ui.segment {:key "fm-list"}
      (ui-header {} "Field Observations")

      (table :.ui.very.compact.mini.table {:key "wqtab"}
        (thead {:key 1}
          (tr {}
            (th {:key "nm"} "Parameter")
            (th {:key "opts"} "Value")))
        (tbody {:key 2}
          (vec
            (for [{:parameter/keys [Name Constituent] :as param} params]
              (let [sample    (get param-samples (:db/id param))
                    obstype   (:parameter/FieldObsType param)
                    ana-nm    (:analytelookup/AnalyteName (:constituentlookup/AnalyteCode Constituent))
                    obsresult (first (:sample/FieldObsResults sample))
                    textvalue (or (:fieldobsresult/TextResult obsresult) "")
                    intval    (or (:fieldobsresult/IntResult obsresult) 0)
                    decvalue  (:fieldobsresult/BigDecResult obsresult)
                    refvalue  (:fieldobsresult/RefResult obsresult)

                    RefResults (:fieldobsresult/RefResults obsresult)
                    refsvalue (when (= obstype :refs)
                                (let [refsvalue (mapv #(rutil/thing->id %) (or RefResults []))]
                                  (debug "FieldObs refsvalue" refsvalue)
                                  refsvalue))

                    ana       (select-keys (:constituentlookup/AnalyteCode Constituent) [:db/id])
                    opts-filt (cond
                                (#{:ref :refs} obstype)
                                #(and (= (:filt %) ana) (> (:sort %) 0)))
                    options   (when opts-filt
                                (filter opts-filt
                                  (:ui/options fieldobsvarlookup-options)))
                    options   (if (and (seq options) (= :ref obstype))
                                (conj options {:value "" :text "NR" :sort 0})
                                options)]

                (tr {:key (:db/id param)}
                  (td {:key "pm"} Name)
                  (td {:key "vars"}
                    (cond
                      (= obstype :bigdec)
                      (rutil/rui-bigdec-input
                        {:value decvalue
                         :style {:width 80}
                         :onChange (fn [v]
                                     (debug "BigDec CHANGE" v "obsresult" obsresult)
                                     (comp/transact! this `[(save-obs {:sv-ident ~sv-ident :sample ~sample :obsresult ~obsresult :value ~v :param ~param})]))})
                      (= obstype :text)
                      (ui-input
                        {:value    textvalue
                         :style {:width "100%"}
                         :onChange (fn [e]
                                     (let [v (-> e .-target .-value)]
                                       (debug "Text CHANGE" v)
                                       (comp/transact! this `[(save-obs {:sv-ident ~sv-ident :sample ~sample :obsresult ~obsresult :value ~v :param ~param})])))})

                      (seq options)
                      (vec
                        (map-indexed
                          (fn [j {:keys [value text sort] :as opt}]
                            (let [checked (cond
                                            (= obstype :ref)
                                            (= intval sort)
                                            (= obstype :refs)
                                            (some? ((set refsvalue) value)))]
                              (ui-checkbox
                                {:radio    (= :ref obstype)
                                 :key      value
                                 :label    text
                                 :name     Name
                                 :value    value
                                 :checked  checked
                                 :style    {:marginLeft 10}
                                 :onChange (fn []
                                             (let [checked (not checked)
                                                   val     (if (= obstype :refs)
                                                             (if checked
                                                               (vec (set (conj refsvalue value)))
                                                               (vec (remove #(= % value) refsvalue)))
                                                             value)
                                                   val     (if (and (= obstype :refs) (empty? val))
                                                             nil
                                                             val)]
                                               (debug "ON CHANGE" Name "sample:" (:db/id sample) "value:" val "intval:" sort "checked:" checked)
                                               (comp/transact! this `[(save-obs {:sv-ident ~sv-ident :sample ~sample :obsresult ~obsresult :value ~val :intval ~sort :param ~param :checked ~checked})])))})))
                          options)))))))))))))

(def ui-fo-list (comp/factory FieldObsList {:keyfn :sample-type}))