(ns riverdb.ui.sitevisits-page
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]
    [clojure.data]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.application :as fapp :refer [current-state]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li a p h2 h3 button table thead
                                                tbody th tr td form label input select
                                                option span]]
    [com.fulcrologic.fulcro.mutations :as fm]
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
    [riverdb.ui.comps.sample-list :refer [ui-sample-list SampleList]]
    [riverdb.ui.forms.sample :refer [SampleForm]]
    [riverdb.ui.forms :refer [SampleTypeCodeForm]]
    [riverdb.ui.globals :refer [Globals]]
    [riverdb.ui.lookup-options :refer [preload-options ui-theta-options ThetaOptions]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.parameter :refer [Parameter]]
    [riverdb.ui.project-years :refer [ProjectYears ui-project-years]]
    [riverdb.ui.routes :as routes]
    [riverdb.ui.session :refer [Session]]
    [riverdb.ui.inputs :refer [ui-float-input]]
    [riverdb.ui.upload :refer [ui-upload-modal]]
    [riverdb.ui.util :as rutil :refer [walk-ident-refs* walk-ident-refs make-tempid make-validator parse-float rui-checkbox rui-int rui-bigdec rui-input ui-cancel-save set-editing set-value set-value! set-refs! set-ref! set-ref set-refs get-ref-set-val lookup-db-ident filter-param-typecode]]
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

(declare SVRouter)

(defn merge-form-config! [app class props & named-params]
  (let [props-with-config (fs/add-form-config class props)]
    (debug "NEW FORM PROPS" props-with-config)
    (apply merge/merge-component! app class props-with-config named-params)))

(fm/defmutation use-data-as-form [{:keys [form-class target] :as props}]
  (action [{:keys [state]}]
    (swap! state
      (fn [s]
        (-> s
          (fs/add-form-config* form-class target))))))

(defn load-lookup [k c target]
  (f/load! SPA k c {:target target}))

(defsc Constituent [this props]
  {:ident [:org.riverdb.db.constituentlookup/gid :db/id]
   :query [:db/id
           :constituentlookup/Name
           fs/form-config-join]})

;(defsc SampleConstituentForm [this props]
;  {:ident [:org.riverdb.db.constituentlookup/gid :db/id]
;   :query [:db/id
;           :constituentlookup/Name
;           fs/form-config-join]
;   :form-fields #{:db/id}})
;
;(defsc DeviceTypeForm [_ _]
;  {:ident [:org.riverdb.db.samplingdevicelookup/gid :db/id],
;   :initial-state {:db/id (tempid/tempid),
;                   :riverdb.entity/ns :entity.ns/samplingdevicelookup}
;   :query [:db/id
;           :riverdb.entity/ns
;           fs/form-config-join
;           :samplingdevicelookup/Active
;           :samplingdevicelookup/SampleDevice
;           :samplingdevicelookup/DeviceMax
;           :samplingdevicelookup/DeviceMin]})
;
;(defsc DeviceIDForm [_ _]
;  {:ident         [:org.riverdb.db.samplingdevice/gid :db/id],
;   :initial-state {:db/id (tempid/tempid),
;                   :riverdb.entity/ns :entity.ns/samplingdevice},
;   :query         [:db/id
;                   :riverdb.entity/ns
;                   fs/form-config-join
;                   :samplingdevice/CommonID
;                   :samplingdevice/DeviceType]})
;
;(defsc FieldResultForm [this {:keys [] :as props}]
;  {:ident         [:org.riverdb.db.fieldresult/gid :db/id]
;   :query         [:db/id
;                   :riverdb.entity/ns
;                   fs/form-config-join
;                   :fieldresult/uuid
;                   :fieldresult/Result
;                   :fieldresult/ResultTime
;                   :fieldresult/FieldReplicate
;                   :fieldresult/ConstituentRowID
;                   :fieldresult/SamplingDeviceID
;                   :fieldresult/SamplingDeviceCode
;                   :fieldresult/FieldResultComments]
;   :form-fields   #{:db/id
;                    :riverdb.entity/ns
;                    :fieldresult/uuid
;                    :fieldresult/Result
;                    :fieldresult/ResultTime
;                    :fieldresult/FieldReplicate
;                    :fieldresult/ConstituentRowID
;                    :fieldresult/SamplingDeviceID
;                    :fieldresult/SamplingDeviceCode
;                    :fieldresult/FieldResultComments}
;   :initial-state {:db/id                          :param/id
;                   :riverdb.entity/ns              :entity.ns/fieldresult
;                   :fieldresult/uuid               :param/uuid
;                   :fieldresult/Result             :param/result
;                   :fieldresult/ResultTime         :param/time
;                   :fieldresult/FieldReplicate     :param/rep
;                   :fieldresult/ConstituentRowID   :param/const
;                   :fieldresult/SamplingDeviceID   :param/devID
;                   :fieldresult/SamplingDeviceCode :param/devType}})
;
;(defsc FieldObsResultForm [this {:keys [] :as props}]
;  {:ident         [:org.riverdb.db.fieldobsresult/gid :db/id]
;   :query         [:db/id
;                   :riverdb.entity/ns
;                   fs/form-config-join
;                   :fieldobsresult/uuid
;                   :fieldobsresult/IntResult
;                   :fieldobsresult/TextResult
;                   :fieldobsresult/FieldObsResultComments
;                   {:fieldobsresult/ConstituentRowID
;                    (comp/get-query Constituent)}]
;   :form-fields   #{:riverdb.entity/ns
;                    :fieldobsresult/uuid
;                    :fieldobsresult/IntResult
;                    :fieldobsresult/TextResult
;                    :fieldobsresult/FieldObsResultComments}
;   :initial-state {:riverdb.entity/ns               :entity.ns/fieldobsresult
;                   :db/id                           :param/id
;                   :fieldobsresult/uuid             :param/uuid
;                   :fieldobsresult/IntResult        :param/int
;                   :fieldobsresult/TextResult       :param/text
;                   :fieldobsresult/ConstituentRowID :param/const}})
;
;
;(defsc LabResultForm [this {:keys [] :as props}]
;  {:ident         [:org.riverdb.db.labresult/gid :db/id]
;   :query         [:db/id
;                   :riverdb.entity/ns
;                   fs/form-config-join
;                   :labresult/uuid
;                   :labresult/AnalysisDate
;                   :labresult/LabReplicate
;                   :labresult/LabResultComments
;                   :labresult/Result
;                   :labresult/SigFig
;                   {:labresult/ConstituentRowID
;                    (comp/get-query Constituent)}]
;   :form-fields   #{:db/id
;                    :riverdb.entity/ns
;                    :labresult/uuid
;                    :labresult/AnalysisDate
;                    :labresult/ExpectedValue
;                    :labresult/LabResultComments
;                    :labresult/Result
;                    :labresult/SigFig}
;   :initial-state {:db/id                      :param/id
;                   :riverdb.entity/ns          :entity.ns/labresult
;                   :labresult/uuid             :param/uuid
;                   :labresult/LabReplicate     :param/rep
;                   :labresult/ConstituentRowID :param/const}})
;
;(fm/defmutation save-obs [{:keys [sample name text value obsvalue intval constituentlookupRef]}]
;  (action [{:keys [state]}]
;    (debug "SAVE OBS RESULT" "name" name "text" text "value" value "obsvalue" obsvalue "intval" intval "constituentlookupRef" constituentlookupRef)))
;
;
;(defsc FieldObsParamForm
;  [this
;   {:keys [fieldobs-results fieldobs-params] :as props}
;   {:keys [fieldobsvarlookup-options sample]}]
;  {:query             [:fieldobs-results :fieldobs-params]
;   :initial-state     {:fieldobs-params  [{:parameter/Name                 "CanopyCover"
;                                           :parameter/Constituent {:db/id "17592186158390"}}
;                                          {:parameter/Name                 "SkyCode"
;                                           :parameter/Constituent {:db/id "17592186157657"}}
;                                          {:parameter/Name                 "Precipitation"
;                                           :parameter/Constituent {:db/id "17592186156566"}}
;                                          {:parameter/Name                 "WaterClarity"
;                                           :parameter/Constituent {:db/id "17592186158389"}}
;                                          {:parameter/Name                 "WindSpeed"
;                                           :parameter/Constituent {:db/id "17592186158388"}}]
;                       :fieldobs-results [{:db/id                           (tempid/tempid)
;                                           :riverdb.entity/ns               :entity.ns/fieldobsresult
;                                           :fieldobsresult/uuid             (tempid/uuid)
;                                           :fieldobsresult/IntResult        1
;                                           :fieldobsresult/TextResult       "Less"
;                                           :fieldobsresult/ConstituentRowID {:db/id "17592186158390"}}
;                                          {:db/id                           (tempid/tempid)
;                                           :riverdb.entity/ns               :entity.ns/fieldobsresult
;                                           :fieldobsresult/uuid             (tempid/uuid)
;                                           :fieldobsresult/IntResult        1
;                                           :fieldobsresult/TextResult       "no clouds"
;                                           :fieldobsresult/ConstituentRowID {:db/id "17592186157657"}}
;                                          {:db/id                           (tempid/tempid)
;                                           :riverdb.entity/ns               :entity.ns/fieldobsresult
;                                           :fieldobsresult/uuid             (tempid/uuid)
;                                           :fieldobsresult/IntResult        1
;                                           :fieldobsresult/TextResult       "none"
;                                           :fieldobsresult/ConstituentRowID {:db/id "17592186156566"}}
;                                          {:db/id                           (tempid/tempid)
;                                           :riverdb.entity/ns               :entity.ns/fieldobsresult
;                                           :fieldobsresult/uuid             (tempid/uuid)
;                                           :fieldobsresult/IntResult        1
;                                           :fieldobsresult/TextResult       "Clear"
;                                           :fieldobsresult/ConstituentRowID {:db/id "17592186158389"}}
;                                          {:db/id                           (tempid/tempid)
;                                           :riverdb.entity/ns               :entity.ns/fieldobsresult
;                                           :fieldobsresult/uuid             (tempid/uuid)
;                                           :fieldobsresult/IntResult        1
;                                           :fieldobsresult/TextResult       "calm"
;                                           :fieldobsresult/ConstituentRowID {:db/id "17592186158388"}}]}
;   :componentDidMount (fn [this])}
;
;
;  (let []
;    (debug "RENDER SampleObsForm" fieldobsvarlookup-options fieldobs-results fieldobs-params)
;    (div {}
;      (dom/h3 {} "Field Observations")
;      (vec
;        (map-indexed
;          (fn [i {:parameter/keys [Name Constituent]}]
;            (let [obsresult (filter #(= (:db/id Constituent) (get-in % [:fieldobsresult/ConstituentRowID :db/id])) fieldobs-results)
;                  obsvalue  (when (seq obsresult) (:fieldobsresult/TextResult (first obsresult)))
;                  intval    (when (seq obsresult) (:fieldobsresult/IntResult (first obsresult)))
;                  options   (filter #(= (:filt %) Name) (:ui/options fieldobsvarlookup-options))]
;              (debug "Param" Name "obsvalue" obsvalue "intval" intval "fieldobs-results" fieldobs-results)
;              (div :.fields {:key i} (str Name ": (" intval ":" obsvalue ") ")
;                (vec
;                  (map-indexed
;                    (fn [j {:keys [value text]}]
;                      (let [checked  (= obsvalue text)
;                            ichecked (= (dec intval) j)]
;                        (ui-checkbox
;                          {:radio    true
;                           :key      value
;                           :label    text
;                           :name     Name
;                           :value    value
;                           :checked  ichecked
;                           :style    {:marginLeft 10}
;                           :onChange #(let []
;                                        (debug "ON CHANGE" Name value obsvalue text (not ichecked))
;                                        (comp/transact! this `[(save-obs {:sample ~sample :name ~Name :text ~text :value ~value :obsvalue ~obsvalue :intval ~intval :const ~Constituent})])
;                                        #_(fm/set-value! this :fieldobsresult/TextResult text))})))
;                    options)))))
;          fieldobs-params)))))
;
;
;(def ui-fieldobs-param-form (comp/factory FieldObsParamForm))


;(defn set-in* [state path val]
;  (assoc-in state path val))
;(fm/defmutation set-in [{:keys [path val]}]
;  (action [{:keys [state]}]
;    (swap! state set-in* path val)))
;(defn set-in! [path val]
;  (comp/transact! SPA `[(set-in {:path ~path :val ~val})]))

;(defn set-field-result* [state db-id val]
;  (-> state
;    (assoc-in [:org.riverdb.db.fieldresult/gid db-id :fieldresult/Result] val)
;    (fs/mark-complete* [:org.riverdb.db.fieldresult/gid db-id] :fieldresult/Result)))
;
;(defn remove-field-result* [state samp-ident db-id]
;  (debug "MUTATION remove field result" samp-ident db-id)
;  (let [path     (conj samp-ident :sample/FieldResults)
;        frs      (get-in state path)
;        str-dbid (str db-id)
;        new-frs  (vec (remove nil?
;                        (for [fr frs]
;                          (let [id    (second fr)
;                                strid (str id)]
;                            (debug "FR" strid)
;                            (if (= strid str-dbid)
;                              nil
;                              fr)))))]
;    (-> state
;      (assoc-in path new-frs)
;      (#(if (tempid/tempid? db-id)
;          (update % :org.riverdb.db.fieldresult/gid dissoc db-id)
;          %)))))
;
;(fm/defmutation set-all [{:keys [k v dbids onChange]}]
;  (action [{:keys [state]}]
;    (let [val v]
;      (debug "MUTATION set-all" k val dbids onChange)
;      (when onChange
;        (onChange v))
;      (swap! state
;        (fn [s]
;          (reduce
;            (fn [s id]
;              (-> s
;                (assoc-in [:org.riverdb.db.fieldresult/gid id k] val)
;                (fs/mark-complete* [:org.riverdb.db.fieldresult/gid id] k)))
;            s dbids))))))
;
;(defn filter-fieldresult-constituent [const results]
;  (filter #(= (rutil/get-ref-val const) (rutil/get-ref-val (:fieldresult/ConstituentRowID %))) results))
;
;(defn fieldresults->fr-map [fieldresults]
;  (nest-by [#(-> % :fieldresult/ConstituentRowID (rutil/get-ref-val)) #(-> % :fieldresult/SamplingDeviceCode (rutil/get-ref-val)) :fieldresult/FieldReplicate] fieldresults))
;
;(defn fr-map->fieldresults [fr-map]
;  (sp/select [ALL LAST ALL LAST ALL LAST] fr-map))
;
;(fm/defmutation set-field-result [{:keys [val i samp-ident p-const devType devID fr-map]}]
;  (action [{:keys [state]}]
;    (let [const    (rutil/map->ident p-const :org.riverdb.db.constituentlookup/gid)
;          const-id (rutil/get-ref-val const)
;          dev-id   (rutil/get-ref-val devType)
;          current  (get-in fr-map [const-id dev-id i])
;          db-id    (:db/id current)]
;      (debug "MUTATION set-field-result" "samp-ident" samp-ident i val "const" const "devType" devType "current" current "fr-m" fr-map)
;
;      (cond
;
;        ;; NOTE updated existing fieldresult
;        (some? db-id)
;        (do
;          (debug "UPDATE EXISTING" db-id)
;          (if (some? val)
;            (swap! state set-field-result* db-id val)
;            (swap! state remove-field-result* samp-ident db-id)))
;
;        ;; NOTE add a new fieldresult
;        (and (nil? db-id) (some? val))
;        (let [new-id   (tempid/tempid)
;              new-fr   (comp/get-initial-state FieldResultForm
;                         {:id new-id :uuid (tempid/uuid) :const p-const :rep i :result val :devID devID :devType devType})
;              new-fr'  (rutil/convert-db-refs new-fr)
;              new-form (fs/add-form-config FieldResultForm new-fr')
;              fr-map'  (assoc-in fr-map [const-id dev-id i] new-form)
;              frs      (mapv rutil/thing->ident (fr-map->fieldresults fr-map'))]
;          (debug "CREATE NEW" "fr-map'" fr-map' "frs" frs)
;          (swap! state
;            (fn [st]
;              (-> st
;                (merge/merge-component FieldResultForm new-form)
;                (assoc-in (conj samp-ident :sample/FieldResults) frs)))))))))
;
;(defn rui-fieldres [samp-ident i p-const rs-map fr-map devType devID]
;  (let [fr  (get rs-map i)
;        val (:fieldresult/Result fr)]
;    ;(debug "RENDER rui-fieldres" i (pr-str val))
;    (ui-float-input {:type     "text"
;                     :value    val
;                     :style    {:width "60px" :paddingLeft 7 :paddingRight 7}
;                     :onChange #(let [db-id (:db/id fr)
;                                      v     %]
;                                  (debug "CHANGE FIELD RESULT" db-id v)
;                                  (comp/transact! SPA `[(set-field-result
;                                                          ~{:samp-ident samp-ident
;                                                            :p-const    p-const
;                                                            :devType    devType
;                                                            :fr-map     fr-map
;                                                            :devID      devID
;                                                            :val        v
;                                                            :i          i})]))})))
;
;
;
;(defsc FieldMeasureParamForm
;  [this
;   {:parameter/keys [Replicates
;                     PrecisionCode High Low]
;    :keys           [db/id] :as props}
;   {:keys [sv-comp sample-props fieldresults fr-map reps
;           samp-ident deviceTypeLookup deviceLookup]}]
;  {:query          (fn [] [:db/id
;                           :parameter/Name
;                           :parameter/DeviceType])
;   :initLocalState (fn [this props]
;                     ;(debug "INIT LOCAL STATE FieldMeasureParamForm" props)
;                     (let [cmp   (:fulcro.client.primitives/computed props)
;                           fst   (first (:fieldresults cmp))
;                           stuff {:time          (:fieldresult/ResultTime fst)
;                                  ;:devType       (:fieldresult/SamplingDeviceCode fst)
;                                  ;:devID         (:fieldresult/SamplingDeviceID fst)
;                                  :setDeviceType #(do
;                                                    (debug "SET STATE :devType" %)
;                                                    (comp/set-state! this {:devType %}))
;                                  :setDeviceID   #(do
;                                                    (debug "SET STATE :devID" %)
;                                                    (comp/set-state! this {:devID %}))}]
;                       ;(debug "INIT LOCAL STATE FieldMeasureParamForm" stuff)
;                       stuff))}
;
;  (let [p-name        (:parameter/Name props)
;        p-device      (:parameter/DeviceType props)
;        p-const       (:parameter/Constituent props)
;        results       fieldresults
;        rs-map        (into {} (map #(identity [(:fieldresult/FieldReplicate %) %]) fieldresults))
;        rslts         (->> (mapv :fieldresult/Result results)
;                        (mapv parse-float)
;                        (remove nil?))
;        {:keys [time setDeviceType setDeviceID devType devID] :as state} (comp/get-state this)
;        ;_             (debug "READ STATE" state)
;        fst           (first fieldresults)
;        devFst        (:fieldresult/SamplingDeviceCode fst)
;        devType       (if fst
;                        devFst
;                        (or devType p-device))
;        devType       (rutil/map->ident devType :org.riverdb.db.samplingdevicelookup/gid)
;        devIDfst      (:fieldresult/SamplingDeviceID fst)
;        devID         (if fst
;                        devIDfst
;                        devID)
;        devID         (rutil/map->ident devID :org.riverdb.db.samplingdevice/gid)
;        mean          (/ (reduce + rslts) (count rslts))
;        stddev        (tu/std-dev rslts)
;        rsd           (tu/round2 2 (tu/percent-prec mean stddev))
;        rnge          (tu/round2 2 (- (reduce max rslts) (reduce min rslts)))
;        mean          (tu/round2 2 mean)
;
;        stddev        (tu/round2 2 stddev)
;        prec          (clojure.edn/read-string PrecisionCode)
;        precRSD       (:rsd prec)
;        precRange     (:range prec)
;        precThreshold (:threshold prec)
;        rangeExc      (when precRange
;                        (if precThreshold
;                          (and
;                            (> rnge precRange)
;                            (< mean precThreshold))
;                          (> rnge precRange)))
;        low (when Low (.-rep Low))
;        high (when High (.-rep High))
;
;        qualExc       (or
;                        (and high (> mean high))
;                        (and low (< mean low)))
;
;        ;_             (debug "prec" prec "rnge" rnge "precRange" precRange "typeRange" (type precRange)
;        ;                "typeRSD" (type precRSD) "typeThresh" (type precThreshold) "range exceedance?" (> rnge precRange))
;
;
;
;        rsdExc        (when precRSD
;                        (if precThreshold
;                          (and
;                            (> mean precThreshold)
;                            (> rsd precRSD))
;                          (> rsd precRSD)))
;        time          (or time (:fieldresult/ResultTime (first results)))
;        unit          (get-in p-const [:constituentlookup/UnitCode :unitlookup/Unit])]
;    (debug "RENDER FieldMeasureParamForm" p-name "rs-map" rs-map "props" props) ; "devType" devType "devID" devID "p-const" p-const "fieldresults" fieldresults)
;
;    (tr {:key id}
;      (td {:key "name"} p-name)
;      (td {:key "inst"}
;        (ui-theta-options (comp/computed
;                            (merge deviceTypeLookup
;                              {:riverdb.entity/ns :entity.ns/samplingdevicelookup
;                               :value             devType
;                               :opts              {:load  false
;                                                   :style {:paddingLeft 5 :paddingRight 5}}})
;                            {:changeMutation `set-all
;                             :changeParams   {:dbids    (mapv :db/id results)
;                                              :k        :fieldresult/SamplingDeviceCode
;                                              :onChange setDeviceType}})))
;
;      (td {:key "instID"}
;        (ui-theta-options (comp/computed
;                            (merge deviceLookup
;                              {:riverdb.entity/ns :entity.ns/samplingdevice
;                               :value             devID
;                               :filter-key        :samplingdevice/DeviceType
;                               :filter-val        {:db/id (second devType)}
;                               :opts              {:load false :style {:width 70 :minWidth 50 :paddingLeft 5 :paddingRight 5}}})
;                            {:changeMutation `set-all
;                             :changeParams   {:dbids    (mapv :db/id results)
;                                              :k        :fieldresult/SamplingDeviceID
;                                              :onChange setDeviceID}})))
;
;      (td {:key "unit"} (or unit ""))
;
;      ;; NOTE this is the (difficult) shizzle here
;      (mapv
;        (fn [i]
;          (td {:key i}
;            (rui-fieldres samp-ident i (select-keys p-const [:db/id]) rs-map fr-map devType devID)))
;        (range 1 (inc reps)))
;
;
;      (td {:key "time"} (dom/input {:type     "text"
;                                    :style    {:width "80px" :paddingLeft 7 :paddingRight 7}
;                                    :value    (or (str time) "")
;                                    :onChange #(let [value (-> % .-target .-value)]
;                                                 (comp/transact! this `[(set-all {:dbids ~(mapv :db/id results)
;                                                                                  :k     :fieldresult/ResultTime
;                                                                                  :v     ~value})]))}))
;      (td {:key "reps" :style {:color (if (< (count rslts) Replicates) "red" "black")}} (or (str (count rslts)) ""))
;      (ui-popup
;        {:open    false
;         :trigger (td {:key "range" :style {:color (if rangeExc "red" "black")}} (or (str rnge) ""))}
;        "Range exceedance")
;      (td {:key "mean" :style {:color (if qualExc "red" "black")}} (or (str mean) ""))
;      (td {:key "stddev"} (or (str stddev) "")))))
;      ;(ui-popup
;      ;  {:open    false
;      ;   :trigger (td {:key "rsd" :style {:color (if rsdExc "red" "black")}} (or (str rsd) ""))}
;      ;  "RSD exceedance"))))
;
;(def ui-fieldmeasure-param-form (comp/factory FieldMeasureParamForm {:keyfn :db/id}))

;(defsc FieldMeasureParamList [this
;                              {:keys [fieldresults
;                                      fieldmeasure-params]}
;                              {:keys [deviceTypeLookup
;                                      deviceLookup
;                                      samplingdevicelookup-options
;                                      sv-comp
;                                      sample-props
;                                      samp-ident]}]
;  {:query [{:fieldresults (comp/get-query FieldResultForm)}
;           :fieldmeasure-params]}
;  (let [max-reps (reduce
;                   (fn [mx p]
;                     (if-let [reps (:parameter/ReplicatesEntry p)]
;                       (max reps mx)
;                       mx))
;                   1 fieldmeasure-params)
;        fr-map   (fieldresults->fr-map (map rutil/convert-db-refs fieldresults))]
;    ;(debug "RENDER FieldMeasureParamList" "params" fieldmeasure-params) ;"fr-map" fr-map "results" fieldresults) ;"params" fieldmeasure-params)
;    (div {}
;      (dom/h3 {} "Field Measurements")
;      (table :.ui.very.compact.mini.table {:key "wqtab"}
;        (thead {:key 1}
;          (tr {}
;            (th {:key "nm"} "Param")
;            (th {:key "inst"} "Device")
;            (th {:key "instID"} "Inst ID")
;            (th {:key "units"} "Units")
;            (map
;              #(identity
;                 (th {:key (str %)} (str "Test " %)))
;              (range 1 (inc max-reps)))
;            (th {:key "time"} "Time")
;            (th {:key "reps"} "# Tests")
;            (th {:key "range"} "Range")
;            (th {:key "mean"} "Mean")
;            (th {:key "stddev"} "Std Dev")))
;            ;(th {:key "rds"} "Prec")))
;        (tbody {:key 2}
;          (vec
;            (for [fm-param fieldmeasure-params]
;              (let [const     (:parameter/Constituent fm-param)
;                    f-results (filter-fieldresult-constituent const fieldresults)]
;                (ui-fieldmeasure-param-form
;                  (comp/computed fm-param
;                    {:samplingdevicelookup-options samplingdevicelookup-options
;                     :deviceTypeLookup             deviceTypeLookup
;                     :deviceLookup                 deviceLookup
;                     :sv-comp                      sv-comp
;                     :sample-props                 sample-props
;                     :fieldresults                 f-results
;                     :fr-map                       fr-map
;                     :reps                         max-reps
;                     :samp-ident                   samp-ident}))))))))))
;
;(def ui-fieldmeasure-param-list (comp/factory FieldMeasureParamList))


;(defsc FieldMeasureParamList [this
;                              {:keys [fieldresults
;                                      fieldmeasure-params]}
;                              {:keys [deviceTypeLookup
;                                      deviceLookup
;                                      sv-comp
;                                      sample-props
;                                      samp-ident]}]
;  {:query [{:fieldresults (comp/get-query FieldResultForm)}
;           :fieldmeasure-params]}
;  (let [max-reps (reduce
;                   (fn [mx p]
;                     (if-let [reps (:parameter/ReplicatesEntry p)]
;                       (max reps mx)
;                       mx))
;                   1 fieldmeasure-params)
;        fr-map   (fieldresults->fr-map (map rutil/convert-db-refs fieldresults))]
;    ;(debug "RENDER FieldMeasureParamList" "params" fieldmeasure-params) ;"fr-map" fr-map "results" fieldresults) ;"params" fieldmeasure-params)
;    (div {}
;      (dom/h3 {} "Field Measurements")
;      (table :.ui.very.compact.mini.table {:key "wqtab"}
;        (thead {:key 1}
;          (tr {}
;            (th {:key "nm"} "Param")
;            (th {:key "inst"} "Device")
;            (th {:key "instID"} "Inst ID")
;            (th {:key "units"} "Units")
;            (map
;              #(identity
;                 (th {:key (str %)} (str "Test " %)))
;              (range 1 (inc max-reps)))
;            (th {:key "time"} "Time")
;            (th {:key "reps"} "# Tests")
;            (th {:key "range"} "Range")
;            (th {:key "mean"} "Mean")
;            (th {:key "stddev"} "Std Dev")))
;        ;(th {:key "rds"} "Prec")))
;        (tbody {:key 2}
;          (vec
;            (for [fm-param fieldmeasure-params]
;              (let [const     (:parameter/Constituent fm-param)
;                    f-results (filter-fieldresult-constituent const fieldresults)]
;                (ui-fieldmeasure-param-form
;                  (comp/computed fm-param
;                    {:deviceTypeLookup             deviceTypeLookup
;                     :deviceLookup                 deviceLookup
;                     :sv-comp                      sv-comp
;                     :sample-props                 sample-props
;                     :fieldresults                 f-results
;                     :fr-map                       fr-map
;                     :reps                         max-reps
;                     :samp-ident                   samp-ident}))))))))))
;
;(def ui-fieldmeasure-param-list (comp/factory FieldMeasureParamList))

;(defsc SampleForm [this
;                   {:ui/keys [ready]
;                    :sample/keys [SampleTypeCode
;                                  FieldResults
;                                  FieldObsResults
;                                  LabResults
;                                  uuid] :as props}
;                   {:keys [samplingdevicelookup-options
;                           fieldobsvarlookup-options
;                           active-params
;                           sv-comp
;                           onChange]}]
;  {:ident             [:org.riverdb.db.sample/gid :db/id]
;   :query             [:db/id
;                       :riverdb.entity/ns
;                       fs/form-config-join
;                       :ui/ready
;                       :sample/uuid
;                       :sample/Time
;                       {:sample/DeviceID (comp/get-query DeviceIDForm)}
;                       {:sample/DeviceType (comp/get-query DeviceTypeForm)}
;                       {:sample/Constituent (comp/get-query SampleConstituentForm)}
;                       {:sample/SampleTypeCode (comp/get-query SampleTypeCodeForm)}
;                       {:sample/FieldResults (comp/get-query FieldResultForm)}
;                       {:sample/FieldObsResults (comp/get-query FieldObsResultForm)}
;                       {:sample/LabResults (comp/get-query LabResultForm)}
;                       [:riverdb.theta.options/ns '_]]
;
;   ;:initLocalState    (fn [app props]
;   ;                     (let [st {:sampleTypes
;   ;                               {:sampletypelookup.SampleTypeCode/Grab         (lookup-db-ident {:db/ident :sampletypelookup.SampleTypeCode/Grab})
;   ;                                :sampletypelookup.SampleTypeCode/FieldObs     (lookup-db-ident {:db/ident :sampletypelookup.SampleTypeCode/FieldObs})
;   ;                                :sampletypelookup.SampleTypeCode/FieldMeasure (lookup-db-ident {:db/ident :sampletypelookup.SampleTypeCode/FieldMeasure})}}]
;   ;                       (debug "INIT LOCAL STATE SampleForm" "state" st)
;   ;                       st))
;
;
;   :initial-state     (fn [{:keys [type]}]
;                        {:db/id                 (tempid/tempid)
;                         :sample/uuid           (tempid/uuid)
;                         :riverdb.entity/ns     :entity.ns/sample
;                         :ui/ready              false
;                         :sample/SampleTypeCode (case type
;                                                  :fieldobs
;                                                  (lookup-db-ident {:db/ident :sampletypelookup.SampleTypeCode/FieldObs})
;                                                  :grab
;                                                  (lookup-db-ident {:db/ident :sampletypelookup.SampleTypeCode/Grab})
;                                                  :fieldmeasure
;                                                  (lookup-db-ident {:db/ident :sampletypelookup.SampleTypeCode/FieldMeasure}))})
;   :form-fields       #{:db/id
;                        :sample/uuid
;                        :riverdb.entity/ns
;                        :sample/Time
;                        :sample/SampleDate
;                        :sample/SampleTime
;                        :sample/DeviceID
;                        ;:sample/DeviceType
;                        ;:sample/Constituent
;                        :sample/FieldResults
;                        :sample/FieldObsResults
;                        :sample/LabResults
;                        :sample/SampleTypeCode}
;   :componentDidMount (fn [this]
;                        (let [props    (comp/props this)
;                              sampType (get-in props [:sample/SampleTypeCode])]
;                          (debug "DID MOUNT SampleForm" sampType)
;                          (fm/set-value! this :ui/ready true)))}
;
;  (let [sample-type      (:db/ident SampleTypeCode)
;        filtered-params  (filter-param-typecode sample-type active-params)
;        deviceTypeLookup (get-in props [:riverdb.theta.options/ns :entity.ns/samplingdevicelookup])
;        deviceLookup     (get-in props [:riverdb.theta.options/ns :entity.ns/samplingdevice])]
;    (debug "RENDER SampleForm" sample-type)
;    (div :.ui.segment {}
;      (case sample-type
;        :sampletypelookup.SampleTypeCode/FieldMeasure
;        (when true
;          ;(debug "active-params" active-params "filtered-params" filtered-params)
;          (ui-fieldmeasure-param-list
;            (comp/computed
;              {:fieldresults        FieldResults
;               :fieldmeasure-params filtered-params}
;              {:sv-comp          sv-comp
;               :sample-props     props
;               :samp-ident       (comp/get-ident this)
;               :deviceTypeLookup deviceTypeLookup
;               :deviceLookup     deviceLookup})))
;        :sampletypelookup.SampleTypeCode/FieldObs
;        (when true
;          ;(debug "TODO: RENDER Sample->FieldObsResults" "fieldobsvarlookup-options" fieldobsvarlookup-options)
;          (ui-fieldobs-param-form
;            (comp/computed
;              (tu/merge-tree
;                (comp/get-initial-state FieldObsParamForm)
;                {:fieldobs-results FieldObsResults})
;              {:fieldobsvarlookup-options fieldobsvarlookup-options
;               :sample                    this})))
;        :sampletypelookup.SampleTypeCode/Grab
;        (when true
;          (debug "TODO: RENDER Sample->LabResults")
;          (dom/h3 {} "Grabs and Labs"))
;        (div {} "Unknown Sample Type")))))
;
;(def ui-sample-form (comp/factory SampleForm {:keyfn :db/id}))


;(defsc FieldMeasureSampleForm [this props]
;  {:ident [:org.riverdb.db.sample/gid :db/id]
;   :query [:db/id
;           fs/form-config-join
;           :sample/EventType
;           :sample/FieldResults]})



;(defn filter-sample-type-1 [ident samples]
;  (let [app-state (current-state SPA)
;        ident-id  (get-in app-state [:db/ident ident :db/id])]
;    (filter #(= (get-in % [:sample/EventType :db/id]) ident-id) samples)))
;
;(defn filter-sample-type [type samples]
;  (filter #(= (get-in % [:sample/EventType :eventtypelookup/EventType]) type) samples))


;(fm/defmutation set-ref [{:keys [target value]}]
;  (action [{:keys [state]}]
;    (swap! state assoc-in target value)))

;(defsc
;  person
;  [_ {:keys [ui/msg ui/name]}]
;  {:ident [:org.riverdb.db.person/gid :db/id],
;   :initial-state {:ui/msg "hello", :db/id (tempid), :ui/name "person"},
;   :query
;   [:ui/msg
;    :ui/name
;    :db/id
;    :riverdb.entity/ns
;    :person/Agency
;    :person/Name
;    :person/PersonID]}
;  (do
;     (log/debug (str "RENDER " name))
;     (div (str msg " from the " name " component"))))

;(def onAddMonitor
;  (fn [e]
;    (debug "ADD MONITOR" e)))



;(defn add-person* [state-map new-name target]
;  (let [orig new-name
;        new-name   (str/trim new-name)
;        new-person (->>
;                     (comp/get-initial-state AddPersonModal {:name new-name :orig orig})
;                     (fs/add-form-config AddPersonModal))]
;    (debug "add-person*" new-person)
;    (merge/merge-component state-map AddPersonModal new-person :replace target)))

;(fm/defmutation add-option [{:keys [target v field-k ent-ns options-target]}]
;  (action [{:keys [state]}]
;    ;(debug "ADD OPTION" form-ident field-k entity-ns v)
;    (case ent-ns
;      :entity.ns/person
;      (swap! state riverdb.ui.person/add-person* v target field-k options-target))))


;(fm/defmutation post-save-mutation [{:keys [form-ident field-k ent-ns orig new-id new-name options-target modal-ident]}]
;  (action [{:keys [state]}]
;    (debug "POST SAVE MUTATION" form-ident ent-ns field-k orig new-id new-name options-target)
;    (let [st            @state
;          field-val     (get-in st (conj form-ident field-k))
;          new-field-val (if (vector? field-val)
;                          (as-> field-val x
;                            (remove #(= (:db/id %) orig) x)
;                            (conj x {:db/id new-id})
;                            (vec x))
;                          {:db/id new-id})
;
;          opts          (as-> (get-in st options-target) x
;                          (remove #(= (:key %) new-id) x)
;                          (conj x {:key new-id :value new-id :text new-name}))]
;      (debug "SAVE! NEW VAL" new-field-val)
;      (swap! state
;        (fn [st]
;          (-> st
;            (merge/merge-ident form-ident {field-k new-field-val})
;            (assoc-in (conj modal-ident :ui/show) false)
;            (assoc-in options-target opts)))))))

(fm/defmutation sv-deleted [{:keys [ident]}]
  (action [{:keys [state]}]
    (debug "SV deleted" ident)
    (routes/route-to! "/sitevisit/list")))

(defsc SiteVisitForm [this
                      {:ui/keys [ready create globals add-person-modal]
                       :db/keys [id]
                       :sitevisit/keys [CheckPersonRef DataEntryDate DataEntryPersonRef
                                        Notes QAPersonRef QACheck QADate
                                        Samples SiteVisitDate StationID StationFailCode
                                        Visitors VisitType] :as props}
                      {:keys [station-options person-options sitevisittype-options
                              stationfaillookup-options
                              current-project parameters current-agency person-lookup] :as cprops}]
  {:ident          [:org.riverdb.db.sitevisit/gid :db/id]
   :query          (fn [] [:db/id
                           :riverdb.entity/ns
                           fs/form-config-join
                           :ui/ready
                           :ui/create
                           :sitevisit/AgencyCode
                           :sitevisit/BacteriaCollected
                           :sitevisit/BacteriaTime
                           :sitevisit/CheckPersonRef
                           :sitevisit/CreationTimestamp
                           :sitevisit/DataEntryDate
                           :sitevisit/DataEntryNotes
                           :sitevisit/DataEntryPersonRef
                           :sitevisit/Datum
                           :sitevisit/DepthMeasured
                           :sitevisit/GPSDeviceCode
                           :sitevisit/HydroMod
                           :sitevisit/HydroModLoc
                           :sitevisit/Lat
                           :sitevisit/Lon
                           :sitevisit/MetalCollected
                           :sitevisit/MetalTime
                           :sitevisit/Notes
                           :sitevisit/PointID
                           :sitevisit/ProjectID
                           :sitevisit/QACheck
                           :sitevisit/QADate
                           :sitevisit/QAPersonRef
                           :sitevisit/SeasonCode
                           :sitevisit/SiteVisitDate
                           :sitevisit/SiteVisitID
                           :sitevisit/StationFailCode
                           :sitevisit/StationID
                           :sitevisit/StreamWidth
                           :sitevisit/Time
                           :sitevisit/TssCollected
                           :sitevisit/TssTime
                           :sitevisit/TurbidityCollected
                           :sitevisit/TurbidityTime
                           :sitevisit/UnitStreamWidth
                           :sitevisit/UnitWaterDepth
                           :sitevisit/VisitType
                           :sitevisit/WaterDepth
                           :sitevisit/WidthMeasured
                           :sitevisit/uuid
                           :sitevisit/Visitors
                           {:sitevisit/Samples (comp/get-query SampleForm)}
                           {:ui/globals (comp/get-query Globals)}
                           [:riverdb.theta.options/ns '_]])

   :initial-state  (fn [{:keys [id agency project uuid VisitType StationFailCode] :as params}]
                     {:db/id                       (tempid/tempid)
                      :ui/ready                    true
                      :ui/create                   true
                      :riverdb.entity/ns           :entity.ns/sitevisit
                      :sitevisit/AgencyCode        agency
                      :sitevisit/ProjectID         project
                      :sitevisit/uuid              (or uuid (tempid/uuid))
                      :sitevisit/CreationTimestamp (js/Date.)
                      :sitevisit/Visitors          []
                      :sitevisit/Samples           []
                      :sitevisit/VisitType         (or VisitType {:db/ident :sitevisittype/Monthly})
                      :sitevisit/StationFailCode   (or StationFailCode {:db/ident :stationfaillookup/None})})

   :initLocalState (fn [this props]
                     {:onChangeSample #(debug "ON CHANGE SAMPLE" %)})

   :form-fields    #{:riverdb.entity/ns
                     :sitevisit/uuid
                     :sitevisit/AgencyCode :sitevisit/ProjectID :sitevisit/Notes
                     :sitevisit/StationID :sitevisit/DataEntryDate :sitevisit/SiteVisitDate :sitevisit/Time
                     :sitevisit/Visitors :sitevisit/VisitType :sitevisit/StationFailCode
                     :sitevisit/DataEntryPersonRef :sitevisit/CheckPersonRef
                     :sitevisit/QAPersonRef :sitevisit/QACheck :sitevisit/QADate :sitevisit/Samples}}

  :componentDidMount (fn [this]
                       (debug "DID MOUNT SiteVisitForm" (comp/props this)))

  (let [{:keys [onChangeSample]} (comp/get-state this)
        dirty-fields  (fs/dirty-fields props false)
        dirty?        (some? (seq dirty-fields))
        active-params (filter :parameter/Active parameters)
        param-types   (set (map #(get-in % [:parameter/SampleType :db/ident]) active-params))
        this-ident    (when props
                        (comp/get-ident this))
        default-opts  {:clearable true
                       ;:allowAdditions   true
                       ;:additionPosition "bottom"
                       :style     {:maxWidth 168 :minWidth 168}}
        sv-time       (:sitevisit/Time props)
        sv-date       (:sitevisit/SiteVisitDate props)
        {person-options            :entity.ns/person
         stationlookup-options     :entity.ns/stationlookup
         sitevisittype-options     :entity.ns/sitevisittype
         stationfaillookup-options :entity.ns/stationfaillookup
         fieldobsvarlookup-options :entity.ns/fieldobsvarlookup} (:riverdb.theta.options/ns props)]

    ;(debug "RENDER SiteVisitForm" "props" props "params" active-params )
    (div :.dimmable.fields {:key "sv-form"}
      (ui-dimmer {:inverted true :active (or (not ready) (not props))}
        (ui-loader {:indeterminate true}))
      (when props
        (ui-form {:key "form" :size "tiny"}
          (div :.dimmable.fields {:key "visitors"}
            (div :.field {:key "people"}
              (label {:style {}} "Monitors")
              (ui-theta-options
                (comp/computed
                  (merge
                    person-options
                    {:riverdb.entity/ns :entity.ns/person
                     :value             (or Visitors [])
                     :opts              (merge
                                          default-opts
                                          {:multiple true})})
                  {:changeMutation `set-value
                   :changeParams   {:ident this-ident
                                    :k     :sitevisit/Visitors}})))
            ;:onAddItem      `add-option
            ;:addParams      {:field-k :sitevisit/Visitors
            ;                 :ent-ns  :entity.ns/person
            ;                 :target  (conj this-ident :ui/add-person-modal)}


            (div :.field {:key "site"}
              (label {:style {}} "Station ID")
              (ui-theta-options
                (comp/computed
                  (merge
                    stationlookup-options
                    {:riverdb.entity/ns :entity.ns/stationlookup
                     :value             StationID
                     :opts              default-opts})
                  {:changeMutation `set-value
                   :changeParams   {:ident this-ident
                                    :k     :sitevisit/StationID}})))

            (div :.field {:key "visittype"}
              (label {:style {}} "Visit Type")
              (ui-theta-options
                (comp/computed
                  (merge
                    sitevisittype-options
                    {:riverdb.entity/ns :entity.ns/sitevisittype
                     :value             VisitType
                     :opts              default-opts})
                  {:changeMutation `set-value
                   :changeParams   {:ident this-ident
                                    :k     :sitevisit/VisitType}})))


            (div :.field {:key "sitefail"}
              (label {:style {}} "Failure?")
              (ui-theta-options
                (comp/computed
                  (merge
                    stationfaillookup-options
                    {:riverdb.entity/ns :entity.ns/stationfaillookup
                     :value             StationFailCode
                     :opts              default-opts})
                  {:changeMutation `set-value
                   :changeParams   {:ident this-ident
                                    :k     :sitevisit/StationFailCode}}))))


          (div :.dimmable.fields {:key "dates"}
            (div :.field {:key "svdate" :style {:width 180}}
              (label {} "Site Visit Date")
              (ui-datepicker {:selected (or sv-date "")
                              :onChange #(when (inst? %)
                                           (log/debug "date change" %)
                                           (set-value! this :sitevisit/SiteVisitDate %))}))


            (ui-form-input
              {:label    "Start Time"
               :value    (or sv-time "")
               :style    {:width 168}
               :onChange #(do
                            (log/debug "Time change" (-> % .-target .-value))
                            (set-value! this :sitevisit/Time (-> % .-target .-value)))})

            (div :.field {:key "enterer"}
              (label {:style {}} "Entered By")
              (ui-theta-options
                (comp/computed
                  (merge person-options
                    {:riverdb.entity/ns :entity.ns/person
                     :value             DataEntryPersonRef
                     :opts              default-opts})
                  {:changeMutation `set-value
                   :changeParams   {:ident this-ident
                                    :k     :sitevisit/DataEntryPersonRef}})))
            ;:onChange  #(do
            ;              (log/debug "data person changed" %)
            ;              (set-ref! this :sitevisit/DataEntryPersonRef %))
            ;:onAddItem #(do
            ;              (debug "ADD Entry Person" %))


            (div :.field {:key "dedate" :style {:width 180}}
              (label {:style {}} "Data Entry Date")
              (do
                ;(debug "RENDER DataEntryDate" DataEntryDate)
                (ui-datepicker {:selected DataEntryDate
                                :onChange #(when (inst? %)
                                             (log/debug "date change" %)
                                             (set-value! this :sitevisit/DataEntryDate %))}))))



          (div :.dimmable.fields {:key "qa"}
            (div :.field {:key "checker"}
              (label {:style {}} "Checked By")
              (ui-theta-options
                (comp/computed
                  (merge person-options
                    {:riverdb.entity/ns :entity.ns/person
                     :value             CheckPersonRef
                     :opts              default-opts})
                  {:changeMutation `set-value
                   :changeParams   {:ident this-ident
                                    :k     :sitevisit/CheckPersonRef}})))


            (div :.field {:key "qcer"}
              (label {:style {}} "QA'd By")
              (ui-theta-options
                (comp/computed
                  (merge person-options
                    {:riverdb.entity/ns :entity.ns/person
                     :value             QAPersonRef
                     :opts              default-opts})
                  {:changeMutation `set-value
                   :changeParams   {:ident this-ident
                                    :k     :sitevisit/QAPersonRef}})))


            (div :.field {:key "qadate" :style {:width 180}}
              (label {:style {} :onClick #(set-value! this :sitevisit/QADate nil)} "QA Date")
              (ui-datepicker {:selected QADate
                              :onChange #(when (inst? %)
                                           (log/debug "QADate change" %)
                                           (set-value! this :sitevisit/QADate %))}))

            (div :.field {:key "publish"}
              (label {:style {}} "Publish?")
              (ui-checkbox {:size     "big" :fitted true :label "" :type "checkbox" :toggle true
                            :checked  (or QACheck false)
                            :onChange #(let [value (not QACheck)]
                                         (log/debug "publish change" value)
                                         (set-value! this :sitevisit/QACheck value))})))

          (ui-form-text-area {:label    "Notes"
                              :value    (or Notes "")
                              :onChange #(do
                                           (debug "CHANGED Notes" (-> % .-target .-value))
                                           (set-value! this :sitevisit/Notes (-> % .-target .-value)))})



          (div (str param-types))
          (for [sample-type param-types]
            (let [params (filter-param-typecode sample-type active-params)
                  samples (filter-sample-typecode sample-type Samples)]
              (ui-sample-list
                (comp/computed
                  {:sample-type              sample-type
                   :params                   params
                   :samples                  samples
                   :riverdb.theta.options/ns (:riverdb.theta.options/ns props)}

                  {:sv-comp        this
                   :onChangeSample onChangeSample}))))





          (div {:style {:marginTop 10}}
            (ui-confirm {:open          (comp/get-state this :confirm-delete)
                         :onConfirm     #(do
                                           (comp/set-state! this {:confirm-delete false})
                                           (comp/transact! this `[(rm/save-entity ~{:ident         this-ident
                                                                                    :delete        true
                                                                                    :post-mutation `sv-deleted
                                                                                    :post-params   {}})]))
                         :onCancel      #(comp/set-state! this {:confirm-delete false})
                         :content       "Are you sure?"
                         :confirmButton (dom/button :.ui.negative.button {} "Delete")})

            (dom/button :.ui.negative.button
              {:disabled (tempid/tempid? (second this-ident))
               :onClick  #(comp/set-state! this {:confirm-delete true})}
              "Delete")

            (dom/button :.ui.right.floated.button.primary
              {:disabled (not dirty?)
               :onClick  #(let [dirty-fields (fs/dirty-fields props true)
                                form-fields  (fs/get-form-fields SiteVisitForm)
                                form-props   (select-keys props form-fields)]
                            (debug "SAVE!" dirty? "dirty-fields" dirty-fields "form-props" form-props)
                            (debug "DIFF" (tu/ppstr dirty-fields))
                            (comp/transact! this
                              `[(rm/save-entity ~{:ident this-ident
                                                  :diff  dirty-fields})]))}
              "Save")

            (dom/button :.ui.right.floated.button.secondary
              {:onClick #(do
                           (debug "CANCEL!" dirty? (fs/dirty-fields props false))
                           (when dirty?
                             (comp/transact! this
                               `[(rm/reset-form {:ident ~this-ident})]))
                           (when (or (not dirty?) create)
                             (fm/set-value! this :ui/editing false)
                             (routes/route-to! "/sitevisit/list")))}
              (if dirty?
                "Cancel"
                "Close"))))))))


(def ui-sv-form (comp/factory SiteVisitForm {:keyfn :db/id}))

(fm/defmutation sort-load->select-options
  "sorts the load results, and then creates a seq that can be used as the options for a select dropdown"
  [{:keys [target select-target text-fn sort-fn]}]
  (action [{:keys [state]}]
    (debug "sort-load->select-options" target select-target text-fn sort-fn)
    (let [ms   (->>
                 (get-in @state target)
                 (sort-by sort-fn))
          opts (vec
                 (for [[k m] ms]
                   {:key k :value k :text (text-fn m)}))]
      (swap! state (fn [st]
                     (-> st
                       (assoc-in target (into {} ms))
                       (assoc-in select-target opts)))))))



(fm/defmutation form-ready
  [{:keys [route-target form-ident]}]
  (action [{:keys [state]}]
    (debug "MUTATION FORM READY" route-target form-ident)
    (try
      (swap! state
        (fn [st]
          (-> st
            (rutil/convert-db-refs* form-ident)
            (fs/add-form-config* SiteVisitForm form-ident)
            (fs/entity->pristine* form-ident)
            (update-in form-ident assoc :ui/ready true))))
      (catch js/Object ex (debug "FORM LOAD FAILED" ex)))
    (debug "DONE SETTING UP SV FORM READY")
    (dr/target-ready! SPA route-target)))

(fm/defmutation merge-new-sv2
  [{:keys []}]
  (action [{:keys [state]}]
    (let [current-project (get @state :riverdb.ui.root/current-project)
          current-agency  (get @state :riverdb.ui.root/current-agency)
          sv              (comp/get-initial-state SiteVisitForm {:project current-project :agency current-agency})
          _               (debug "Merge SV!" sv current-project current-agency)
          sv              (walk-ident-refs @state sv)
          sv              (fs/add-form-config SiteVisitForm sv)]

      (debug "Merge new SV! do we have DB globals yet?" (get-in @state [:component/id :globals]))
      (swap! state
        (fn [st]
          (-> st
            ;; NOTE load the minimum so we can get globals after load
            (merge/merge-component SiteVisitForm sv
              :replace [:riverdb.ui.root/current-sv]
              :replace [:component/id :sitevisit-editor :sitevisit])))))))


(defsc ProjectInfo [this props]
  {:ident [:org.riverdb.db.projectslookup/gid :db/id]
   :query [:db/id
           :projectslookup/ProjectID
           :projectslookup/Name
           {:projectslookup/Parameters (comp/get-query Parameter)}]})


(defsc SiteVisitEditor [this {:keys                 [sitevisit session]
                              :riverdb.ui.root/keys [current-project current-agency]
                              :ui/keys              [ready] :as props}]
  {:ident             (fn [] [:component/id :sitevisit-editor])
   :initial-state     {:ui/ready false}
   :query             [:ui/ready
                       {:sitevisit (comp/get-query SiteVisitForm)}
                       {[:riverdb.ui.root/current-agency '_] (comp/get-query looks/agencylookup-sum)}
                       {[:riverdb.ui.root/current-project '_] (comp/get-query ProjectInfo)}
                       {:session (comp/get-query Session)}
                       [:db/ident '_]
                       [df/marker-table ::sv]]
   :route-segment     ["edit" :sv-id]
   :check-session     (fn [app session]
                        (let [valid? (:session/valid? session)
                              admin? (->
                                       (:account/auth session)
                                       :user
                                       roles/user->roles
                                       roles/admin?)
                              result (and valid? admin?)]
                          (debug "CHECK SESSION SiteVisitEditor" "valid?" valid? "admin?" admin? "result" result)
                          result))

   :will-enter        (fn [app {:keys [sv-id] :as params}]
                        (let [is-new?      (= sv-id "new")
                              ident-key    :org.riverdb.db.sitevisit/gid
                              editor-ident [:component/id :sitevisit-editor]]
                          (log/debug "WILL ENTER SiteVisitEditor" "NEW?" is-new? "PARAMS" params)
                          (if is-new?
                            (dr/route-deferred editor-ident
                              #(let [] ;(riverdb.ui.util/shortid)]]
                                 (log/debug "CREATING A NEW SITEVISIT")
                                 (comp/transact! app `[(merge-new-sv2)])
                                 (dr/target-ready! app editor-ident)))
                            (dr/route-deferred editor-ident
                              #(let [sv-ident [ident-key sv-id]]
                                 (log/debug "LOADING AN EXISTING SITEVISIT" sv-ident)
                                 ;(dr/target-ready! SPA editor-ident)
                                 (f/load! app sv-ident SiteVisitForm
                                   {:target               (targeting/multiple-targets
                                                            [:riverdb.ui.root/current-sv]
                                                            [:component/id :sitevisit-editor :sitevisit])
                                    :marker               ::sv
                                    :post-mutation        `form-ready
                                    :post-mutation-params {:route-target editor-ident
                                                           :form-ident   sv-ident}
                                    :without              #{[:riverdb.theta.options/ns '_]}}))))))
   :will-leave        (fn [this props]
                        (debug "WILL LEAVE EDITOR")
                        (dr/route-immediate [:component/id :sitevisit-editor]))

   :componentDidMount (fn [this]
                        (let [props  (comp/props this)
                              {:keys [sitevisit]} props
                              {:sitevisit/keys [AgencyCode ProjectID]} sitevisit
                              projID (if (eql/ident? ProjectID) (second ProjectID) (:db/id ProjectID))
                              agID   (if (eql/ident? AgencyCode) (second AgencyCode) (:db/id AgencyCode))
                              _      (log/debug "DID MOUNT SiteVisitEditor" "AGENCY" AgencyCode "PROJECT" ProjectID "projID" projID "agID" agID)]

                          (preload-agency agID)

                          (preload-options :entity.ns/person {:query-params {:filter {:person/Agency agID}}})
                          (preload-options :entity.ns/stationlookup
                            {:query-params {:filter {:stationlookup/Project projID}}
                             :sort-key     :stationlookup/StationID
                             :text-fn      {:keys #{:stationlookup/StationID :stationlookup/StationName}
                                            :fn   (fn [{:stationlookup/keys [StationID StationName]}]
                                                    (str StationID ": " StationName))}})
                          (preload-options :entity.ns/sitevisittype)
                          (preload-options :entity.ns/stationfaillookup)
                          (preload-options :entity.ns/samplingdevice {:filter-key :samplingdevice/DeviceType})
                          (preload-options :entity.ns/samplingdevicelookup)
                          (preload-options :entity.ns/fieldobsvarlookup {:filter-key :fieldobsvarlookup/AnalyteName})))

   :css               [[:.floating-menu {:position "absolute !important"
                                         :z-index  1000
                                         :width    "300px"
                                         :right    "0px"
                                         :top      "50px"}
                        :.ui.segment {:padding ".5em"}
                        :.ui.raised.segment {:padding ".3em"}
                        :.ui.table {:margin-top ".3em"}]]}
  (let [parameters (get-in current-project [:projectslookup/Parameters])
        parameters (vec (riverdb.util/sort-maps-by parameters [:parameter/Order]))
        marker     (get props [df/marker-table ::sv])]
    (debug "RENDER SiteVisitsEditor")
    (div {}
      (if marker
        (ui-loader {:active true})
        (when sitevisit
          (ui-sv-form (comp/computed sitevisit {:current-project current-project
                                                :current-agency  current-agency
                                                :parameters      parameters})))))))


(defsc SiteVisitSummary [this {:keys           [db/id]
                               :sitevisit/keys [SiteVisitDate VisitType StationFailCode QACheck StationID]
                               :as             props} {:keys [onEdit]}]
  {:query         [:db/id
                   :sitevisit/SiteVisitID
                   :sitevisit/SiteVisitDate
                   :sitevisit/QACheck
                   {:sitevisit/StationFailCode (comp/get-query looks/stationfaillookup-sum)}
                   {:sitevisit/StationID (comp/get-query looks/stationlookup-sum)}
                   {:sitevisit/VisitType (comp/get-query looks/sitevisittype)}]
   :ident         [:org.riverdb.db.sitevisit/gid :db/id]
   :initial-state {:db/id                     (tempid/tempid)
                   :sitevisit/SiteVisitDate   (js/Date.)
                   :sitevisit/StationID       {}
                   :sitevisit/StationFailCode {}
                   :sitevisit/VisitType       {}}}
  (let [type    (:sitevisittype/name VisitType)
        fail    (:stationfaillookup/FailureReason StationFailCode)
        site    (:stationlookup/StationName StationID)
        siteID  (:stationlookup/StationID StationID)
        id      (if (tempid/tempid? id)
                  (.-id id)
                  id)
        edit-fn #(onEdit props)]
    (tr {:key id :style {:cursor "pointer"} :onClick edit-fn} ;:onMouseOver #(println "HOVER" id)}
      (td {:key 1} (str (t/date (t/instant SiteVisitDate)))) ;(str (t/date SiteVisitDate)))
      (td {:key 2} siteID)
      (td {:key 3} site)
      (td {:key 4} type)
      (td {:key 5} fail)
      #_(td {:key 3 :padding 2}
          (button :.ui.primary.basic.icon.button
            {:style {:padding 5} :onClick edit-fn}
            (dom/i :.pointer.edit.icon {}))))))

(def ui-sv-summary (comp/factory SiteVisitSummary {:keyfn :db/id}))

(defn load-sitevisits
  ([this filter limit offset sortField sortOrder]
   (let [params {:filter filter :limit limit :offset offset :sortField sortField :sortOrder sortOrder}]
     (debug "LOAD SITEVISITS" params)
     (f/load! this :org.riverdb.db.sitevisit-meta nil
       {:parallel true
        :params   {:filter filter :meta-count true :limit 1}
        :target   [:component/id :sitevisit-list :ui/list-meta]})
     (f/load! this :org.riverdb.db.sitevisit SiteVisitSummary
       {:parallel true
        :marker   ::svs
        :target   [:component/id :sitevisit-list :sitevisits]
        :params   params}))))

;(defn load-sites [this agencyCode]
;  (debug "LOAD SITES" agencyCode)
;  (let [target [:component/id :sitevisit-list :sites]]
;    (f/load! this :org.riverdb.db.stationlookup looks/stationlookup-sum
;      {:target               target
;       :params               {:limit -1 :filter {:stationlookup/Agency {:agencylookup/AgencyCode agencyCode}}}
;       :post-mutation        `rm/sort-ident-list-by
;       :marker               ::sites
;       :post-mutation-params {:idents-path target
;                              :ident-key   :org.riverdb.db.stationlookup/gid
;                              :sort-fn     :stationlookup/StationID}})))

(defn reset-sites [this]
  (let [sites  (get (comp/props this) :riverdb.ui.root/current-project-sites)
        target [:riverdb.ui.root/current-project-sites]]
    (debug "RESET SITES" sites)
    (fm/set-value! this :ui/site nil)
    (comp/transact! this `[(rm/sort-ident-list-by {:idents-path ~target
                                                   :ident-key   :org.riverdb.db.stationlookup/gid
                                                   :sort-fn     :stationlookup/StationID})])))

(defn make-filter
  ([props]
   (let [{:riverdb.ui.root/keys [current-agency current-project current-year]
          :ui/keys              [site]} props

         ag-id     (:db/id current-agency)
         pj-id     (:db/id current-project)

         year-from (when current-year
                     (js/parseInt current-year))
         year-to   (when year-from
                     (inc year-from))
         filter    (cond-> {}
                     (and year-from year-to)
                     (merge {:sitevisit/SiteVisitDate {:> (js/Date. (str year-from))
                                                       :< (js/Date. (str year-to))}})
                     site
                     (merge {:sitevisit/StationID site})

                     ;ag-id
                     ;(merge {:sitevisit/AgencyCode ag-id})

                     pj-id
                     (merge {:sitevisit/ProjectID pj-id}))]

     (debug "MAKE FILTER" "project" pj-id "from" year-from "to" year-to)
     filter)))



(defsc SiteVisitList
  "This tracks current-project, current-year, filter, limit, sort, and site"
  [this {:keys                 [sitevisits project-years]
         :riverdb.ui.root/keys [current-agency current-project current-year current-project-sites]
         :ui/keys              [site sortField sortOrder limit offset list-meta show-upload] :as props}]
  {:ident              (fn [] [:component/id :sitevisit-list])
   :query              [{:sitevisits (comp/get-query SiteVisitSummary)}
                        {:project-years (comp/get-query ProjectYears)}
                        ;{:sites (comp/get-query looks/stationlookup-sum)}
                        {[:riverdb.ui.root/current-agency '_] (comp/get-query looks/agencylookup-sum)}
                        {[:riverdb.ui.root/current-project '_] (comp/get-query looks/projectslookup-sum)}
                        {[:riverdb.ui.root/current-project-sites '_] (comp/get-query looks/stationlookup-sum)}
                        [:riverdb.ui.root/current-year '_]
                        [df/marker-table ::svs]
                        :ui/limit
                        :ui/offset
                        :ui/sortField
                        :ui/sortOrder
                        :ui/site
                        :ui/list-meta
                        :ui/show-upload]
   :route-segment      ["list"]
   :initial-state      {:sitevisits     []
                        ;:sites         []
                        :project-years  {}
                        :ui/list-meta   nil
                        :ui/limit       15
                        :ui/offset      0
                        :ui/sortField   :sitevisit/SiteVisitDate
                        :ui/sortOrder   :asc
                        :ui/site        nil
                        :ui/show-upload false}
   :componentDidUpdate (fn [this prev-props prev-state]
                         (let [{:ui/keys              [limit offset sortField sortOrder site] :as props
                                :riverdb.ui.root/keys [current-project current-year]} (comp/props this)
                               diff         (clojure.data/diff prev-props props)
                               changed-keys (clojure.set/union (keys (first diff)) (keys (second diff)))
                               runQuery?    (some #{:riverdb.ui.root/current-project
                                                    :riverdb.ui.root/current-year
                                                    :ui/offset
                                                    :ui/limit
                                                    :ui/sortField
                                                    :ui/sortOrder
                                                    :ui/site}
                                              changed-keys)
                               sortSites?   (some #{:riverdb.ui.root/current-project} changed-keys)]
                           (debug "DID UPDATE SiteVisitList" "runQuery?" runQuery? "sortSites?" sortSites?)
                           (when runQuery?
                             (let [filter (make-filter props)]
                               (load-sitevisits this filter limit offset sortField sortOrder)))
                           (when sortSites?
                             (reset-sites this))))
   :componentDidMount  (fn [this]
                         (let [{:ui/keys              [limit offset sortField sortOrder] :as props
                                :riverdb.ui.root/keys [current-agency]} (comp/props this)
                               filter (make-filter props)
                               {:agencylookup/keys [AgencyCode]} current-agency]
                           (debug "DID MOUNT SiteVisitList")
                           (load-sitevisits this filter limit offset sortField sortOrder)
                           (reset-sites this)))}
  ;(when AgencyCode
  ;  (load-sites this AgencyCode))))}

  (let [
        offset                 (or offset 0)
        limit                  (or limit 15)
        activePage             (if (> limit 0)
                                 (inc (/ offset limit))
                                 1)
        query-count            (get list-meta :org.riverdb.meta/query-count 0)
        totalPages             (if (> limit 0)
                                 (int (Math/ceil (/ query-count limit)))
                                 1)
        handlePaginationChange (fn [e t]
                                 (let [page       (-> t .-activePage)
                                       new-offset (* (dec page) limit)]
                                   (log/debug "PAGINATION" "page" page "new-offset" new-offset)
                                   (when (> new-offset -1)
                                     (fm/set-integer! this :ui/offset :value new-offset))))

        sites                  current-project-sites


        onEdit                 (fn [sv]
                                 (routes/route-to! (str "/sitevisit/edit/" (or (:db/id sv) "new"))))
        onUpload               (fn []
                                 (log/debug "onUpload")
                                 (fm/set-value! this :ui/show-upload true))
        onCloseUpload          (fn []
                                 (log/debug "onCloseUpload")
                                 (fm/set-value! this :ui/show-upload false))


        handleSort             (fn [field]
                                 (if (not= field sortField)
                                   (do
                                     (fm/set-value! this :ui/sortField field)
                                     (fm/set-value! this :ui/sortOrder :asc))
                                   (if (= sortOrder :desc)
                                     (do
                                       (fm/set-value! this :ui/sortField nil)
                                       (fm/set-value! this :ui/sortOrder nil))
                                     (do
                                       (fm/set-value! this :ui/sortOrder :desc)))))

        th-fn                  (fn [i label sort-field]
                                 (th {:key     i
                                      :onClick #(handleSort sort-field)
                                      :classes [(when (= sortField sort-field)
                                                  (str "ui sorted " (if (= sortOrder :desc) "descending" "ascending")))]} label))

        marker                 (get props [df/marker-table ::svs])]

    (debug "RENDER SiteVisitList" "sites" sites)
    (div {}
      (ui-upload-modal (comp/computed {:ui/show-upload show-upload} {:onClose onCloseUpload}))
      (div :#sv-list-menu.ui.menu {:key "title"}
        ;(div :.item {:style {}} "Site Visits")
        (div :.item (ui-project-years project-years))
        (div :.item
          (when sites
            (span {:key "site" :style {}}
              "Site: "
              (select {:style    {:width "150px"}
                       :value    (or site "")
                       :onChange #(let [st (.. % -target -value)
                                        st (if (= st "")
                                             nil
                                             st)]
                                    (debug "set site" st)
                                    (fm/set-value! this :ui/offset 0)
                                    (fm/set-value! this :ui/site st))}
                (into
                  [(option {:value "" :key "none"} "")]
                  (doall
                    (for [{:keys [db/id stationlookup/StationName stationlookup/StationID]} sites]
                      (option {:value id :key id} (str StationID ": " StationName)))))))))
        (div :.item.float.right
          (button {:key "upload" :onClick onUpload} "Upload")
          (button {:key "create" :onClick #(onEdit nil)} "New Site Visit")))

      (if marker
        (ui-loader {:active true})
        (when (seq sitevisits)
          (table :.ui.sortable.selectable.very.compact.small.table {:key "sv-table"}
            (thead {:key 1}
              (tr {}
                (th-fn 1 "Date" :sitevisit/SiteVisitDate)
                (th-fn 2 "Site ID" {:sitevisit/StationID :stationlookup/StationID})
                (th-fn 3 "Site" {:sitevisit/StationID :stationlookup/StationName})
                (th-fn 4 "Type" {:sitevisit/VisitType :sitevisittype/name})
                (th-fn 5 "Fail?" {:sitevisit/StationFailCode :stationfaillookup/FailureReason})))
            (tbody {:key 2}
              (vec
                (for [sv sitevisits]
                  (ui-sv-summary (comp/computed sv {:onEdit onEdit}))))))))

      (div :.ui.menu
        (div :.item {}
          (dom/span {:style {}} "Results Per Page")
          (ui-input {:style    {:width 70}
                     :value    (str (if (= limit -1) "" limit))
                     :onChange (fn [e]
                                 (let [value (-> e .-target .-value)
                                       value (if (= value "")
                                               -1 value)]
                                   (fm/set-integer! this :ui/limit :value value)))}))
        (let [limit (if (< limit 0) 0 limit)
              from  (-> activePage dec (* limit) inc)
              to    (* activePage limit)
              to    (if (= to 0) 1 to)]

          (debug "LIMIT" limit "activePage" activePage)
          (div :.item (str "Showing " from " to " to " of " query-count " results")))

        (div :.item.right
          (ui-pagination
            {:id            "paginator"
             :activePage    activePage
             :boundaryRange 1
             :onPageChange  handlePaginationChange
             :size          "mini"
             :siblingRange  1
             :totalPages    totalPages}))))))


(dr/defrouter SVRouter [this props]
  {:router-targets        [SiteVisitList SiteVisitEditor]
   :shouldComponentUpdate (fn [_ _ _] true)})

(def ui-sv-router (comp/factory SVRouter))


(defsc SiteVisitsPage [this {:keys [project-years svrouter] :as props}]
  {:ident                 (fn [] [:component/id :sitevisits])
   :query                 [{:project-years (comp/get-query ProjectYears)}
                           {:svrouter (comp/get-query SVRouter)}]
   :initial-state         {:svrouter      {}
                           :project-years {}}
   :route-segment         ["sitevisit"]
   :shouldComponentUpdate (fn [_ _ _] true)}

  (let [{:keys [ui/ui-year ui/ui-project agency-project-years]} project-years
        proj          (get agency-project-years (keyword ui-project))
        agency        (:agency proj)
        project-name  (:name proj)
        current-route (dr/current-route this this)]
    #_(debug "RENDER SiteVisitsPage")
    (div {}
      (div :.ui.raised.segment {}
        (ui-sv-router svrouter)))))

;(doseq [[index file] (with-index files)]
;  (println index file)
;  (.. formData (append (str "file" index) file)))
;
;(aset http-request "onreadystatechange" (fn [evt]
;                                          (if (= 4 (. http-request -readyState))
;                                            (js/console.log (. http-request -statusText)))
;                                          ))
;(. http-request open "post" "/upload" true)
;(. http-request setRequestHeader "Content-Type" "multipart/form-data; boundary=foo")
;(. http-request send formData)
;; an input triggering upload(s)
;(let [onChange (fn [evt]
;                 (let [js-file-list (.. evt -target -files)]
;                   (comp/transact! this
;                     (mapv (fn [file-idx]
;                             (let [fid     (comp/tempid)
;                                   js-file (with-meta {} {:js-file (.item js-file-list file-idx)})]
;                               `(upload-file ~{:id fid :file js-file})))
;                       (range (.-length js-file-list))))))]
;  (dom/input #js {:multiple true :onChange onChange}))