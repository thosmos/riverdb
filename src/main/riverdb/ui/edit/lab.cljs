(ns riverdb.ui.edit.lab
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]
    [clojure.data]
    [com.cognitect.transit.types :as ty]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.normalized-state :refer [integrate-ident remove-ident]]
    [com.fulcrologic.fulcro.application :as fapp :refer [current-state]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li a p h2 h3 button table thead
                                                tbody th tr td form label input select
                                                option span]]
    [com.fulcrologic.fulcro.mutations :as fm]
    [com.fulcrologic.fulcro.networking.file-upload :as file-upload]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.rad.type-support.decimal :as decimal]
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
    [com.fulcrologic.semantic-ui.addons.textarea.ui-text-area :refer [ui-text-area]]
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
    [riverdb.ui.forms.sample :refer [SampleForm LabResultForm]]
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
    ;[tick.core :as t]
    [theta.log :as log :refer [debug info]]
    [thosmos.util :as tu]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [riverdb.roles :as roles]
    [edn-query-language.core :as eql]
    [tick.core :as t]
    [testdouble.cljs.csv :as csv]))

(defn remove-sample* [state sv-ident samp-ident db-id]
  (debug "MUTATION remove sample" sv-ident db-id)
  (let [path     (conj sv-ident :sitevisit/Samples)
        sas      (get-in state path)
        str-dbid (str db-id)
        new-sas  (vec
                   (remove nil?
                     (for [sa sas]
                       (let [id    (second sa)
                             strid (str id)]
                         (debug "SA" strid)
                         (if (= strid str-dbid)
                           nil
                           sa)))))]
    (-> state
      (assoc-in path new-sas)
      (#(if (tempid/tempid? db-id)
          (update % :org.riverdb.db.sample/gid dissoc db-id)
          %)))))

(fm/defmutation set-grab [{:keys [sv-ident samp-ident param]}]
  (action [{:keys [state]}]
    ;(debug "MUTATION set-grab" "sv-ident" sv-ident "samp-ident" samp-ident "lr-ident" lr-ident "K" k "V" v)
    (if samp-ident
      (let []
        (debug "delete sample" samp-ident)
        (swap! state remove-ident samp-ident (conj sv-ident :sitevisit/Samples)))
      (let [sa (comp/get-initial-state SampleForm {:param param})
            fr (comp/get-initial-state LabResultForm {})
            sa (assoc sa :sample/LabResults [fr])
            sa-form (fs/add-form-config SampleForm sa)]
        (debug "create sample and lr-result" sa)
        (swap! state
          (fn [st]
            (-> st
              (merge/merge-component SampleForm sa-form :append (conj sv-ident :sitevisit/Samples))
              (fs/mark-complete* sv-ident :sitevisit/Samples))))))))

(fm/defmutation set-result [{:keys [lr-ident v]}]
  (action [{:keys [state]}]
    (let [sigs (rutil/sigfigs v)]
      ;(debug "MUTATION set-result" "sv-ident" sv-ident "lr-ident" lr-ident "K" k "V" v "sigfig" sigs)
      (when lr-ident
        (swap! state
          (fn [s]
            (-> s
              (update-in lr-ident
                (fn [lr]
                  (-> lr
                    (assoc :labresult/Result v)
                    (assoc :labresult/SigFig sigs))))
              (fs/mark-complete* lr-ident))))))))

(fm/defmutation set-ident-value [{:keys [ident k v]}]
  (action [{:keys [state]}]
    (when ident
      (swap! state
        (fn [s]
          (-> s
            (update-in ident
              (fn [lr]
                (assoc lr k v)))
            (fs/mark-complete* ident)))))))

(defsc LabSampleForm [this
                      {:keys [param sample] :as props}
                      {:keys [sv-comp qualLookup onChangeSample]}]
  {:query          [:param :sample]
   :initLocalState (fn [this props]
                     ;(debug "INIT LOCAL STATE FieldMeasureParamForm" props)
                     (let [;comps  (:fulcro.client.primitives/computed props)
                           sample (:sample props)
                           stuff  {:setDeviceType #(do
                                                     (debug "SET STATE :devType" %))
                                                     ;(comp/set-state! this {:devType %}))
                                   :setDeviceID   #(do
                                                     (debug "SET STATE :devID" %))}]
                                                     ;(comp/set-state! this {:devID %}))}]
                       ;(debug "INIT LOCAL STATE FieldMeasureSampleForm" stuff)
                       stuff))}
  (let [p-id       (:db/id param)
        p-name     (:parameter/Name param)
        p-const    (:parameter/Constituent param)
        {:parameter/keys [PrecisionCode Low High Replicates]} param
        sv-ident   (comp/get-ident sv-comp)
        samp-ident (when sample (rutil/thing->ident sample))
        lab-result (first (:sample/LabResults sample))
        {:labresult/keys [ResQualCode LabResultComments]} lab-result
        lr-ident   (when lab-result (rutil/thing->ident lab-result))
        ;_          (debug "LR-IDENT" lr-ident "qualLookup" qualLookup)
        qualLookup (update qualLookup :ui/options (fn [opts] (vec (remove #(= (:text %) ": no qualifier") opts))))
        rslt       (-> lab-result :labresult/Result parse-float)]
    (tr {}
      (td {} (:parameter/Name param))
      (td {} (ui-checkbox {:checked (if sample true false)
                           :onChange (fn []
                                       (debug "GRAB CHANGED")
                                       (comp/transact! SPA [(set-grab
                                                              {:sv-ident sv-ident
                                                               :samp-ident samp-ident
                                                               :param param})]))}))
      (td {} #_(ui-form-input {:value rslt :onChange (fn [a b] (debug "RESULT CHANGE " a b))})
        (ui-float-input {:disabled (not (some? samp-ident))
                         :type     "text"
                         :value    (or rslt "")
                         :style    {:width "100px" :paddingLeft 7 :paddingRight 7}
                         :onChange (fn [v]
                                     (debug "RESULT CHANGE" v)
                                     (comp/transact! SPA [(set-result
                                                            {:lr-ident lr-ident
                                                             :v        v})])
                                     #_(comp/transact! SPA `[]))}))
      ;:onChange setDeviceID}})))
      (td {} (ui-theta-options (comp/computed
                                 (merge qualLookup
                                   {:riverdb.entity/ns :entity.ns/resquallookup
                                    :value             (or (:db/id ResQualCode) "")
                                    :opts              {:load false
                                                        :disabled (not (some? samp-ident))}})
                                 {:changeMutation `set-ident-value
                                  :changeParams   {:ident lr-ident
                                                   :k :labresult/ResQualCode}})))
      (td {} (ui-text-area {:rows     1
                            :disabled (not (some? samp-ident))
                            :value    (or LabResultComments "")
                            :onChange (fn [e]
                                        (let [v (-> e .-target .-value)]
                                          (debug "textarea change" v)
                                          (when v
                                            (comp/transact! this
                                              [(set-ident-value {:ident lr-ident
                                                                 :k     :labresult/LabResultComments
                                                                 :v     v})]))))})))))

(def ui-lab-sample-form (comp/computed-factory LabSampleForm {:keyfn :key}))

(defsc LabResultList [this
                      {:keys [params samples param-samples] :as props}
                      {:keys [sv-comp onChangeSample] :as comps}]
  {:query [:sample-type
           :params
           :samples
           :param-samples
           :riverdb.theta.options/ns]}
  (let [qualLookup  (get-in props [:riverdb.theta.options/ns :entity.ns/resquallookup])]
    (debug "RENDER LabResultList" "param-samps" param-samples "params" params "samples" samples)
    (div :.ui.segment {:key "lr-list"}
      (ui-header {} "Labs")
      (table :.ui.very.compact.mini.table {:key "lrtab"}
        (thead {:key 1}
          (tr {}
            (th {:key "nm"} "Param")
            (th {:key "grab"} "Grab")
            (th {:key "result"} "Result")
            (th {:key "qual"} "Qual")
            (th {:key "notes"} "Notes")))
        (tbody {:key 2}
          (vec
            (for [param params]
              (let [sample (get param-samples (:db/id param))
                    #_#_sample (if sample
                                 sample
                                 (let [sa-frm (comp/get-initial-state SampleForm {:type :sampletypelookup.SampleTypeCode/FieldMeasure})]))]
                (tr {:key (:db/id param)}
                  (td (:parameter/Name param))
                  (td (str sample)))
                (ui-lab-sample-form
                  (comp/computed {:key (:db/id param) :param param :sample sample}
                    {:onChangeSample   onChangeSample
                     :sv-comp          sv-comp
                     :qualLookup       qualLookup}))
                #_(ui-fieldmeasure-sample-form
                    (comp/computed {:key (:db/id param) :param param :sample sample}
                      {:onChangeSample   onChangeSample
                       :deviceTypeLookup deviceTypeLookup
                       :deviceLookup     deviceLookup
                       :sv-comp          sv-comp
                       :reps             max-reps}))))))))))


(def ui-lr-list (comp/factory LabResultList {:keyfn #(str (:sample-type %))}))