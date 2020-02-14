(ns riverdb.ui.sitevisits-page
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]
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
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as uism :refer [defstatemachine]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-dropdown :refer [ui-form-dropdown]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-input :refer [ui-form-input]]
    [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :refer [ui-dropdown]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form :refer [ui-form]]
    [com.fulcrologic.semantic-ui.elements.header.ui-header :refer [ui-header]]
    [com.fulcrologic.semantic-ui.elements.loader.ui-loader :refer [ui-loader]]
    [com.fulcrologic.semantic-ui.modules.dimmer.ui-dimmer :refer [ui-dimmer]]
    [com.fulcrologic.semantic-ui.addons.pagination.ui-pagination :refer [ui-pagination]]
    [com.fulcrologic.semantic-ui.modules.checkbox.ui-checkbox :refer [ui-checkbox]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table :refer [ui-table]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table-body :refer [ui-table-body]]
    [com.fulcrologic.semantic-ui.modules.popup.ui-popup :refer [ui-popup]]
    [riverdb.application :refer [SPA]]
    [riverdb.api.mutations :as rm]
    [riverdb.util :refer [sort-maps-by with-index]]
    [riverdb.ui.agency :refer [preload-agency Agency]]
    [riverdb.ui.components :refer [ui-datepicker]]
    [riverdb.ui.globals :refer [Globals]]
    [riverdb.ui.lookup]
    [riverdb.ui.lookup-options :refer [preload-options ui-theta-options ThetaOptions]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.parameter :refer [Parameter]]
    [riverdb.ui.project-years :refer [ProjectYears ui-project-years]]
    [riverdb.ui.routes :as routes]
    [riverdb.ui.session :refer [Session]]
    [riverdb.ui.inputs :refer [ui-float-input]]
    [riverdb.ui.util :refer [walk-ident-refs* walk-ident-refs make-tempid make-validator parse-float rui-checkbox rui-int rui-bigdec rui-input ui-cancel-save set-editing set-value! set-refs! set-ref! set-ref set-refs get-set-val]]
    [riverdb.util :refer [paginate]]
    [theta.log :as log :refer [debug info]]
    [thosmos.util :as tu]))

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



(defsc FieldResultForm [this {:keys [] :as props}]
  {:ident         [:org.riverdb.db.fieldresult/gid :db/id]
   :query         [:db/id
                   :riverdb.entity/ns
                   fs/form-config-join
                   :fieldresult/uuid
                   :fieldresult/Result
                   :fieldresult/ResultTime
                   :fieldresult/FieldReplicate
                   :fieldresult/ConstituentRowID
                   :fieldresult/SamplingDeviceID
                   :fieldresult/SamplingDeviceCode
                   :fieldresult/FieldResultComments]
   :form-fields   #{:db/id
                    :riverdb.entity/ns
                    :fieldresult/uuid
                    :fieldresult/Result
                    :fieldresult/ResultTime
                    :fieldresult/FieldReplicate
                    :fieldresult/ConstituentRowID
                    :fieldresult/SamplingDeviceID
                    :fieldresult/SamplingDeviceCode
                    :fieldresult/FieldResultComments}
   :initial-state {:db/id                          :param/id
                   :riverdb.entity/ns              :entity.ns/fieldresult
                   :fieldresult/uuid               :param/uuid
                   :fieldresult/Result             :param/result
                   :fieldresult/ResultTime         :param/time
                   :fieldresult/FieldReplicate     :param/rep
                   :fieldresult/ConstituentRowID   :param/const
                   :fieldresult/SamplingDeviceID   :param/devID
                   :fieldresult/SamplingDeviceCode :param/devType}})

(defsc FieldObsResultForm [this {:keys [] :as props}]
  {:ident         [:org.riverdb.db.fieldobsresult/gid :db/id]
   :query         [:db/id
                   :riverdb.entity/ns
                   fs/form-config-join
                   :fieldobsresult/uuid
                   :fieldobsresult/IntResult
                   :fieldobsresult/TextResult
                   :fieldobsresult/FieldObsResultComments
                   {:fieldobsresult/ConstituentRowID
                    (comp/get-query Constituent)}]
   :form-fields   #{:riverdb.entity/ns
                    :fieldobsresult/uuid
                    :fieldobsresult/IntResult
                    :fieldobsresult/TextResult
                    :fieldobsresult/FieldObsResultComments}
   :initial-state {:riverdb.entity/ns               :entity.ns/fieldobsresult
                   :db/id                           :param/id
                   :fieldobsresult/uuid             :param/uuid
                   :fieldobsresult/IntResult        :param/int
                   :fieldobsresult/TextResult       :param/text
                   :fieldobsresult/ConstituentRowID :param/const}})


(defsc LabResultForm [this {:keys [] :as props}]
  {:ident         [:org.riverdb.db.labresult/gid :db/id]
   :query         [:db/id
                   :riverdb.entity/ns
                   fs/form-config-join
                   :labresult/uuid
                   :labresult/AnalysisDate
                   :labresult/LabReplicate
                   :labresult/LabResultComments
                   :labresult/Result
                   :labresult/SigFig
                   {:labresult/ConstituentRowID
                    (comp/get-query Constituent)}]
   :form-fields   #{:db/id
                    :riverdb.entity/ns
                    :labresult/uuid
                    :labresult/AnalysisDate
                    :labresult/ExpectedValue
                    :labresult/LabResultComments
                    :labresult/Result
                    :labresult/SigFig}
   :initial-state {:db/id                      :param/id
                   :riverdb.entity/ns          :entity.ns/labresult
                   :labresult/uuid             :param/uuid
                   :labresult/LabReplicate     :param/rep
                   :labresult/ConstituentRowID :param/const}})

(defsc SampleTypeCodeForm [this {:keys [] :as props}]
  {:ident       [:org.riverdb.db.sampletypelookup/gid :db/id]
   :query       [:db/id
                 fs/form-config-join
                 :riverdb.entity/ns
                 :sampletypelookup/uuid
                 :sampletypelookup/SampleTypeCode
                 :sampletypelookup/CollectionType]
   :form-fields #{:riverdb.entity/ns :sampletypelookup/uuid}})

(defsc SampleTypeCode [this {:keys [] :as props}]
  {:ident       [:org.riverdb.db.sampletypelookup/gid :db/id]
   :query       [:db/id
                 :riverdb.entity/ns
                 :sampletypelookup/uuid
                 :sampletypelookup/SampleTypeCode
                 :sampletypelookup/CollectionType]})



(defn set-in* [state path val]
  (assoc-in state path val))
(fm/defmutation set-in [{:keys [path val]}]
  (action [{:keys [state]}]
    (swap! state set-in* path val)))
(defn set-in! [path val]
  (comp/transact! SPA `[(set-in {:path ~path :val ~val})]))

(defn set-field-result* [state db-id val]
  (-> state
    (assoc-in [:org.riverdb.db.fieldresult/gid db-id :fieldresult/Result] val)
    (fs/mark-complete* [:org.riverdb.db.fieldresult/gid db-id] :fieldresult/Result)))

(defn remove-field-result* [state samp-ident db-id]
  (debug "MUTATION remove field result" samp-ident db-id)
  (let [path     (conj samp-ident :sample/FieldResults)
        frs      (get-in state path)
        str-dbid (str db-id)
        new-frs  (vec (remove nil?
                        (for [fr frs]
                          (let [id    (second fr)
                                strid (str id)]
                            (debug "FR" strid)
                            (if (= strid str-dbid)
                              nil
                              fr)))))]
    (-> state
      (assoc-in path new-frs)
      (#(if (tempid/tempid? db-id)
          (update % :org.riverdb.db.fieldresult/gid dissoc db-id)
          %)))))

(fm/defmutation set-all [{:keys [k v isRef dbids onChange]}]
  (action [{:keys [state]}]
    (let [val (if isRef {:db/id v} v)]
      (debug "MUTATION set-all" k val dbids onChange)
      (when onChange
        (onChange v))
      (swap! state
        (fn [s]
          (reduce
            (fn [s id]
              (-> s
                (assoc-in [:org.riverdb.db.fieldresult/gid id k] val)
                (fs/mark-complete* [:org.riverdb.db.fieldresult/gid id] k)))
            s dbids))))))

(fm/defmutation set-field-result [{:keys [i samp-ident const-id db-id val]}]
  (action [{:keys [state]}]
    (debug "MUTATION set-field-result" i val)
    (cond
      (some? db-id)
      (if (some? val)
        (swap! state set-field-result* db-id val)
        (swap! state remove-field-result* samp-ident db-id))
      (and (nil? db-id) (some? val))
      (let [new-id   (tempid/tempid)
            new-fr   (comp/get-initial-state FieldResultForm
                       {:id new-id :uuid (tempid/uuid) :const {:db/id const-id} :rep i :result val})
            new-form (fs/add-form-config FieldResultForm new-fr)]
        (swap! state merge/merge-component FieldResultForm new-form :append (conj samp-ident :sample/FieldResults))))))



(defn rui-fieldres [samp-ident i const-id f-res]
  (let [val (:fieldresult/Result f-res)]
    (ui-float-input {:type     "text"
                     :value    val
                     :style    {:width "60px"}
                     :onChange #(let [db-id (:db/id f-res)
                                      v     %]
                                  (debug "CHANGE FIELD RESULT" db-id v)
                                  (comp/transact! SPA `[(set-field-result
                                                          {:samp-ident ~samp-ident
                                                           :const-id   ~const-id
                                                           :db-id      ~db-id
                                                           :val        ~v
                                                           :i          ~i})]))})))



(defsc ObsParamForm
  [this
   {:keys [] :as props}
   {:keys [sv-comp sample]}]
  {:query (fn [] [:db/id
                  :parameter/name
                  :parameter/samplingdevicelookupRef])}
  (let [p-name       (:parameter/name props)
        p-device     (:parameter/samplingdevicelookupRef props)
        p-const      (:parameter/constituentlookupRef props)
        results      (filter #(= (:fieldobsresult/ConstituentRowID %) p-const)
                       (:sample/FieldObsResults sample))]
    (debug "RENDER SampleObsForm" p-name results)
    (div p-name " " results)))
(def ui-obs-param-form (comp/factory ObsParamForm {:keyfn :db/id}))


(defsc FieldMeasureParamForm
  [this
   {:parameter/keys [replicates replicatesEntry
                     samplingdevicelookupRef constituentlookupRef
                     precisionCode high low]
    :keys           [db/id] :as props}
   {:keys [samplingdevicelookup-options
           sv-comp sample-props fieldresults reps
           samp-ident]}]
  {:query          (fn [] [:db/id
                           :parameter/name
                           :parameter/samplingdevicelookupRef])
   :initLocalState (fn [this props]
                     (debug "INIT LOCAL STATE FieldMeasureParamForm")
                     (let [cmp     (:fulcro.client.primitives/computed props)
                           results (:fieldresults cmp)
                           fst     (first results)
                           time    (:fieldresult/ResultTime fst)
                           inst    (:fieldresult/SamplingDeviceCode fst)
                           instID  (:fieldresult/SamplingDeviceID fst)
                           stuff   {:time          time
                                    :deviceType    inst
                                    :instID        instID
                                    :setDeviceType #(do
                                                      (debug "SET STATE deviceType" {:db/id %})
                                                      (comp/set-state! this {:deviceType {:db/id %}}))}]
                       (debug "saving things" stuff)
                       (comp/set-state! this stuff)))}
  ;stuff))}

  (let [p-name        (:parameter/name props)
        p-device      (:parameter/samplingdevicelookupRef props)
        p-const       (:parameter/constituentlookupRef props)
        p-const-id    (:db/id p-const)
        results       fieldresults
        ;results    (filterv #(= (get-in % [:fieldresult/ConstituentRowID :db/id]) p-const-id) fieldresults)
        rs            (into {} (map #(identity [(:fieldresult/FieldReplicate %) %]) fieldresults))
        rslts         (->> (mapv :fieldresult/Result results)
                        (mapv parse-float)
                        (remove nil?))
        fst           (first fieldresults)
        devFst        (:fieldresult/SamplingDeviceCode fst)
        devStat       (comp/get-state this :deviceType)
        deviceType    (or devStat devFst p-device)
        ;_          (debug "deviceType" deviceType devFst devStat p-device)
        deviceType    (:db/id deviceType)
        devID         (:fieldresult/SamplingDeviceID fst)
        devStat       (comp/get-state this :instID)
        instID        (or devID devStat)
        instID        (:db/id instID)
        ;_          (debug "instID" instID devID devStat)
        mean          (/ (reduce + rslts) (count rslts))
        stddev        (tu/std-dev rslts)
        rsd           (tu/round2 2 (tu/percent-prec mean stddev))
        rnge          (tu/round2 2 (- (reduce max rslts) (reduce min rslts)))
        mean          (tu/round2 2 mean)
        stddev        (tu/round2 2 stddev)

        prec          (clojure.edn/read-string precisionCode)
        precRSD       (:rsd prec)
        precRange     (:range prec)
        precThreshold (:threshold prec)
        rangeExc      (when precRange
                        (if precThreshold
                          (and
                            (> rnge precRange)
                            (< mean precThreshold))
                          (> rnge precRange)))
        rsdExc        (when precRSD
                        (if precThreshold
                          (and
                            (> mean precThreshold)
                            (> rsd precRSD))
                          (> rsd precRSD)))

        _             (debug "prec" prec "rnge" rnge "precRange" precRange "typeRange" (type precRange)
                        "typeRSD" (type precRSD) "typeThresh" (type precThreshold) "range exceedance?" (> rnge precRange))



        {:keys [time setDeviceType]} (comp/get-state this)
        ;_          (debug "GET STATE" (comp/get-state this))
        time          (or (:fieldresult/ResultTime (first results)) time)
        unit          (get-in p-const [:constituentlookup/UnitCode :unitlookup/UnitCode])]
    ;(debug "RENDER SampleParamForm" p-name p-const-id (:db/id (:fieldresult/ConstituentRowID (first fieldresults))))


    (tr {:key id}
      (td {:key "name"} p-name)
      (td {:key "inst"}
        (ui-theta-options (comp/computed
                            {:riverdb.entity/ns :entity.ns/samplingdevicelookup
                             :value             (or deviceType "")
                             :opts              {:load false}}
                            {:changeMutation `set-all
                             :changeParams   {:dbids    (mapv :db/id results)
                                              :k        :fieldresult/SamplingDeviceCode
                                              :isRef    true
                                              :onChange setDeviceType}
                             :onChange       setDeviceType})))

      (td {:key "instID"}
        (ui-theta-options (comp/computed
                            {:riverdb.entity/ns :entity.ns/samplingdevice
                             :value             (or instID "")
                             :filter-key        :samplingdevice/DeviceType
                             :filter-val        (when deviceType {:db/id deviceType})}
                            {:opts           {:load false :style {:width 70 :minWidth 50}}
                             :changeMutation `set-all
                             :changeParams   {:dbids (mapv :db/id results)
                                              :k     :fieldresult/SamplingDeviceID
                                              :isRef true}})))

      (td {:key "unit"} (or unit ""))

      ;; NOTE this is the shizzle here
      (mapv
        (fn [i]
          (td {:key (str i)}
            (let [rs (get rs i)]
              (rui-fieldres samp-ident i p-const-id (or rs "")))))
        (range 1 (inc reps)))


      (td {:key "time"} (dom/input {:type     "text"
                                    :style    {:width "80px"}
                                    :value    (or (str time) "")
                                    :onChange #(let [value (-> % .-target .-value)]
                                                 (comp/transact! this `[(set-all {:dbids ~(mapv :db/id results)
                                                                                  :k     :fieldresult/ResultTime
                                                                                  :v     ~value})]))}))
      (td {:key "reps"} (or (str (count rslts)) ""))
      (ui-popup
        {:open    false
         :trigger (td {:key "range" :style {:color (if rangeExc "red" "black")}} (or (str rnge) ""))}
        "Range exceedance")
      (td {:key "mean"} (or (str mean) ""))
      (td {:key "stddev"} (or (str stddev) ""))
      (ui-popup
        {:open    false
         :trigger (td {:key "rsd" :style {:color (if rsdExc "red" "black")}} (or (str rsd) ""))}
        "RSD exceedance"))))

(def ui-fieldmeasure-param-form (comp/factory FieldMeasureParamForm {:keyfn :db/id}))

(defn filter-fieldresult-constituent [const results]
  (filter #(= (:db/id const) (get-in % [:fieldresult/ConstituentRowID :db/id])) results))

(defsc FieldMeasureParamList [this {:keys [fieldresults
                                           fieldmeasure-params
                                           samplingdevicelookup-options
                                           sv-comp
                                           sample-props
                                           samp-ident]} {:keys []}]
  {:query [{:fieldresults (comp/get-query FieldResultForm)}
           :fieldmeasure-params
           :samplingdevicelookup-options
           :sv-comp
           :sample-props
           :samp-ident]}
  (let [max-reps (reduce
                   (fn [mx p]
                     (if-let [reps (:parameter/replicatesEntry p)]
                       (max reps mx)
                       mx))
                   1 fieldmeasure-params)]
    ;(debug "RENDER FieldMeasureParamList max-reps" max-reps)
    (div {}
      (dom/h3 {} "Field Measurements")
      (table :.ui.very.compact.mini.table {:key "wqtab"}
        (thead {:key 1}
          (tr {}
            (th {:key "nm"} "Param")
            (th {:key "inst"} "Device")
            (th {:key "instID"} "Inst ID")
            (th {:key "units"} "Units")
            (map
              #(identity
                 (th {:key (str %)} (str "Test " %)))
              (range 1 (inc max-reps)))
            (th {:key "time"} "Time")
            (th {:key "reps"} "# Tests")
            (th {:key "range"} "Range")
            (th {:key "mean"} "Mean")
            (th {:key "stddev"} "Std Dev")
            (th {:key "rds"} "% RSD")))
        (tbody {:key 2}
          (vec
            (for [fm-param fieldmeasure-params]
              (let [const     (:parameter/constituentlookupRef fm-param)
                    f-results (filter-fieldresult-constituent const fieldresults)]
                (ui-fieldmeasure-param-form
                  (comp/computed fm-param
                    {:samplingdevicelookup-options samplingdevicelookup-options
                     :sv-comp                      sv-comp
                     :sample-props                 sample-props
                     :fieldresults                 f-results
                     :reps                         max-reps
                     :samp-ident                   samp-ident}))))))))))
(def ui-fieldmeasure-param-list (comp/factory FieldMeasureParamList))

(defn filter-sample-typecode [type samples]
  (filter #(= type (get-in % [:sample/SampleTypeCode :sampletypelookup/SampleTypeCode])) samples))

(defn filter-param-typecode [type params]
  (filter #(= type (get-in % [:parameter/sampleTypeRef :sampletypelookup/SampleTypeCode])) params))

(defsc SampleForm [this
                   {:ui/keys [ready]
                    :sample/keys [SampleTypeCode
                                  FieldResults
                                  FieldObsResults
                                  LabResults
                                  uuid] :as props}
                   {:keys [samplingdevicelookup-options
                           active-params
                           sv-comp]}]
  {:ident              [:org.riverdb.db.sample/gid :db/id]
   :query              [:db/id
                        :riverdb.entity/ns
                        fs/form-config-join
                        :ui/ready
                        :sample/uuid
                        :sample/SampleTime
                        {:sample/SampleTypeCode (comp/get-query SampleTypeCodeForm)}
                        {:sample/FieldResults (comp/get-query FieldResultForm)}
                        {:sample/FieldObsResults (comp/get-query FieldObsResultForm)}
                        {:sample/LabResults (comp/get-query LabResultForm)}]
   :initial-state      (fn [{:keys [type]}]
                         {:db/id                 (tempid/tempid) ;(riverdb.ui.util/shortid)
                          :ui/ready              false
                          :riverdb.entity/ns     :entity.ns/sample
                          :sample/uuid           (tempid/uuid)
                          :sample/FieldResults   []
                          :sample/SampleTypeCode {:db/id :sampletypelookup.SampleTypeCode/FieldMeasure}})
   ;:shouldComponentUpdate (fn [_ _ _] true)
   :form-fields        #{:db/id
                         :sample/uuid
                         :riverdb.entity/ns
                         :sample/SampleTime
                         :sample/FieldResults
                         :sample/FieldObsResults
                         :sample/LabResults
                         :sample/SampleTypeCode}
   :componentDidUpdate (fn [this]
                         (let [props    (comp/props this)
                               sampType (:sample/SampleTypeCode props)]
                           (debug "DID MOUNT SampleForm" sampType)
                           (fm/set-value! this :ui/ready true)))}

                         ;(let [samp  (comp/get-initial-state SampleForm)
                         ;      ident [:org.riverdb.db.sample/gid (:db/id samp)]]
                         ;  (-> (fapp/current-state SPA)
                         ;    (merge/merge-component SampleForm samp)
                         ;    (walk-ident-refs* ident)
                         ;    (get-in ident))))}



  (let [sample-type         (:sampletypelookup/SampleTypeCode SampleTypeCode)
        fieldmeasure-params (filter-param-typecode sample-type active-params)
        fieldobs-params     (filter-param-typecode sample-type active-params)
        grab-params         (filter-param-typecode sample-type active-params)]
    (debug "RENDER SampleForm" sample-type)
    (div :.ui.segment {}
      #_(ui-form-input {:label "UUID" :value (str uuid) :onChange #(fm/set-value! this :sample/uuid (tempid/uuid (-> % .-target .-value)))})
      (case sample-type
        "FieldMeasure"
        (when FieldResults
          (ui-fieldmeasure-param-list {:fieldresults                 FieldResults
                                       :fieldmeasure-params          fieldmeasure-params
                                       :samplingdevicelookup-options samplingdevicelookup-options
                                       :sv-comp                      sv-comp
                                       :sample-props                 props
                                       :samp-ident                   (comp/get-ident this)}))
        "FieldObs"
        (when FieldObsResults
          (debug "TODO: RENDER Sample->FieldObsResults"))
        "Grab"
        (when LabResults
          (debug "TODO: RENDER Sample->LabResults"))))))

(def ui-sample-form (comp/factory SampleForm {:keyfn :db/id}))


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

(fm/defmutation add-option [{:keys [target v field-k ent-ns options-target]}]
  (action [{:keys [state]}]
    ;(debug "ADD OPTION" form-ident field-k entity-ns v)
    (case ent-ns
      :entity.ns/person
      (swap! state riverdb.ui.person/add-person* v target field-k options-target))))

(fm/defmutation post-save-mutation [{:keys [form-ident field-k ent-ns orig new-id new-name options-target modal-ident]}]
  (action [{:keys [state]}]
    (debug "POST SAVE MUTATION" form-ident ent-ns field-k orig new-id new-name options-target)
    (let [st            @state
          field-val     (get-in st (conj form-ident field-k))
          new-field-val (if (vector? field-val)
                          (as-> field-val x
                            (remove #(= (:db/id %) orig) x)
                            (conj x {:db/id new-id})
                            (vec x))
                          {:db/id new-id})

          opts          (as-> (get-in st options-target) x
                          (remove #(= (:key %) new-id) x)
                          (conj x {:key new-id :value new-id :text new-name}))]
      (debug "SAVE! NEW VAL" new-field-val)
      (swap! state
        (fn [st]
          (-> st
            (rm/merge-ident* form-ident {field-k new-field-val})
            (assoc-in (conj modal-ident :ui/show) false)
            (assoc-in options-target opts)))))))


(defsc SiteVisitForm [this
                      {:ui/keys [ready create globals add-person-modal]
                       :db/keys [id]
                       :sitevisit/keys [StationID DataEntryDate SiteVisitDate Time
                                        Visitors VisitType StationFailCode
                                        DataEntryPersonRef CheckPersonRef
                                        QAPersonRef QACheck QADate Samples] :as props}
                      {:keys [station-options person-options sitevisittype-options
                              stationfaillookup-options samplingdevicelookup-options
                              current-project parameters current-agency person-lookup] :as cprops}]
  {:ident         [:org.riverdb.db.sitevisit/gid :db/id]
   :query         (fn [] [:db/id
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
                          {:ui/globals (comp/get-query Globals)}])

   ;{[:riverdb.theta.options/ns :entity.ns/person] (comp/get-query ThetaOptions)}])

   :initial-state (fn [{:keys [id agency project uuid VisitType StationFailCode] :as params}]
                    {:db/id                       (tempid/tempid)
                     :ui/ready                    true
                     :ui/create                   true
                     :riverdb.entity/ns           :entity.ns/sitevisit
                     :sitevisit/AgencyCode        agency
                     :sitevisit/ProjectID         project
                     :sitevisit/uuid              (or uuid (tempid/uuid))
                     :sitevisit/CreationTimestamp (js/Date.)
                     :sitevisit/Visitors          []
                     :sitevisit/Samples           [(comp/get-initial-state SampleForm {})]
                     :sitevisit/VisitType         (or VisitType {:db/id :sitevisittype/Monthly})
                     :sitevisit/StationFailCode   (or StationFailCode {:db/id :stationfaillookup/None})})

   ;:shouldComponentUpdate (fn [_ _ _] true)

   ;:initLocalState (fn [this props]
   ;
   ;                  {})

   :form-fields   #{:db/id :riverdb.entity/ns :sitevisit/uuid
                    :sitevisit/AgencyCode :sitevisit/ProjectID
                    :sitevisit/StationID :sitevisit/DataEntryDate :sitevisit/SiteVisitDate :sitevisit/Time
                    :sitevisit/Visitors :sitevisit/VisitType :sitevisit/StationFailCode
                    :sitevisit/DataEntryPersonRef :sitevisit/CheckPersonRef
                    :sitevisit/QAPersonRef :sitevisit/QACheck :sitevisit/QADate :sitevisit/Samples}}

  ;:componentDidMount (fn [this]
  ;                     (let [props (comp/props this)
  ;                           {:keys [current-agency]} (comp/get-computed this)]
  ;                       {[:riverdb.theta.options/ns :entity.ns/person] (comp/get-query ThetaOptions)}
  ;                       person-lookup       (get props [:riverdb.theta.options/ns :entity.ns/person])
  ;                       (debug "COMPONENT DID MOUNT SiteVisitForm agency:" current-agency)
  ;                       (preload-options :entity.ns/person {:limit -1 :filter {:person/Agency (:db/id current-agency)}})))}

  :componentDidMount (fn [this]
                       ;; NOTE this is just to see if we loaded fresh straight into this form before the globals
                       ;; and need to replace some lookup idents ... seems like there should be a better way ...
                       (debug "DID MOUNT SiteVisitForm" (comp/props this)))
                       ;(let [props           (comp/props this)
                       ;      this-ident      [:org.riverdb.db.sitevisit/gid (:db/id props)]
                       ;      lookup-keyword? (keyword? (-> props :sitevisit/VisitType first val))
                       ;      app-state       (fapp/current-state SPA)
                       ;      globals?        (get-in app-state [:component/id :globals])
                       ;      _               (debug "DID MOUNT SiteVisitForm" this-ident "GLOBALS YET?" globals?)]
                       ;  (when (and lookup-keyword? globals?)
                       ;    (let [ref-props  (get-in app-state this-ident)
                       ;          app-state' (walk-ident-refs* app-state this-ident)
                       ;          ref-props' (get-in app-state' this-ident)
                       ;          diff       (clojure.data/diff ref-props ref-props')]
                       ;      ;(debug "SV FORM globals?" globals? "PROPS DIFF" diff)
                       ;      (when (map? (second diff))
                       ;        ;(debug "MERGE IDENT!")
                       ;        (rm/merge-ident! this-ident ref-props'))))))



  ;:pre-merge (fn [{:keys [current-normalized data-tree] :as env}]
  ;             (debug "PREMERGE SiteVisitForm" current-normalized data-tree)
  ;             (->> data-tree
  ;               (merge current-normalized)
  ;               (clojure.walk/postwalk
  ;                 #(if (= % :com.fulcrologic.fulcro.algorithms.merge/not-found) nil %))))
  (let [dirty-fields  (fs/dirty-fields props false)
        dirty?        (some? (seq dirty-fields))
        active-params (filter :parameter/active parameters)
        this-ident    (comp/get-ident this)
        default-opts  {:clearable true
                       ;:allowAdditions   true
                       ;:additionPosition "bottom"
                       :style     {:maxWidth 168}}]
    (debug "RENDER SiteVisitForm")
    (div :.dimmable.fields {:key "sv-form"}
      (ui-dimmer {:inverted true :active (not ready)}
        (ui-loader {:indeterminate true}))
      (ui-form {:key "form" :size "tiny"}
        (div :.dimmable.fields {:key "visitors"}
          (div :.field {:key "people"}
            (label {:style {}} "Monitors")
            (ui-theta-options
              (comp/computed
                (merge person-lookup
                  {:riverdb.entity/ns :entity.ns/person
                   :value             (mapv :db/id Visitors)})
                {:changeMutation `set-refs
                 :changeParams   {:ident this-ident
                                  :k     :sitevisit/Visitors}
                 ;:onAddItem      `add-option
                 ;:addParams      {:field-k :sitevisit/Visitors
                 ;                 :ent-ns  :entity.ns/person
                 ;                 :target  (conj this-ident :ui/add-person-modal)}
                 :opts           (merge
                                   default-opts
                                   {:multiple true})})))

          (div :.field {:key "site"}
            (label {:style {}} "Station ID")
            (when station-options
              (ui-form-dropdown {:search       true
                                 :selection    true
                                 :autoComplete "off"
                                 :value        (:db/id StationID)
                                 :options      station-options
                                 :style        {:maxWidth 168}
                                 :onChange     (fn [_ d]
                                                 (when-let [value (-> d .-value)]
                                                   (log/debug "site change" value)
                                                   (set-ref! this :sitevisit/StationID value)))})))
          (div :.field {:key "visittype"}
            (label {:style {}} "Visit Type")
            (when sitevisittype-options
              (ui-form-dropdown {:selection true
                                 :value     (:db/id VisitType)
                                 :options   sitevisittype-options
                                 :style     {:maxWidth 168}
                                 :onChange  (fn [_ d]
                                              (when-let [value (-> d .-value)]
                                                ;(log/debug "site change" value)
                                                (set-ref! this :sitevisit/VisitType value)))})))

          (div :.field {:key "sitefail"}
            (label {:style {}} "Failure?")
            (when stationfaillookup-options
              (ui-form-dropdown {:search    true
                                 :selection true
                                 :value     (:db/id StationFailCode)
                                 :options   stationfaillookup-options
                                 :style     {:maxWidth 168}
                                 :onChange  (fn [_ d]
                                              (when-let [value (-> d .-value)]
                                                ;(log/debug "site change" value)
                                                (set-ref! this :sitevisit/StationFailCode value)))}))))

        (div :.dimmable.fields {:key "visittype"})


        (div :.dimmable.fields {:key "dates"}
          (div :.field {:key "svdate" :style {:width 180}}
            (label {} "Site Visit Date")
            (ui-datepicker {:selected SiteVisitDate
                            :onChange #(when (inst? %)
                                         (log/debug "date change" %)
                                         (set-value! this :sitevisit/SiteVisitDate %))}))

          (ui-form-input
            {:label    "Start Time"
             :value    (or Time "")
             :style    {:width 168}
             :onChange #(do
                          (log/debug "Time change" (-> % .-target .-value))
                          (set-value! this :sitevisit/Time (-> % .-target .-value)))})

          (div :.field {:key "enterer"}
            (label {:style {}} "Entered By")
            (ui-theta-options
              (comp/computed
                (merge person-lookup
                  {:riverdb.entity/ns :entity.ns/person
                   :value             (:db/id DataEntryPersonRef)})
                {:changeMutation `set-ref
                 :changeParams   {:ident this-ident :k :sitevisit/DataEntryPersonRef}
                 ;:onChange  #(do
                 ;              (log/debug "data person changed" %)
                 ;              (set-ref! this :sitevisit/DataEntryPersonRef %))
                 ;:onAddItem #(do
                 ;              (debug "ADD Entry Person" %))
                 :opts           default-opts})))

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
                (merge person-lookup
                  {:riverdb.entity/ns :entity.ns/person
                   :value             (:db/id CheckPersonRef)})
                {:changeMutation `set-ref
                 :changeParams   {:ident this-ident :k :sitevisit/CheckPersonRef}
                 :opts           default-opts})))

          (div :.field {:key "qcer"}
            (label {:style {}} "QA'd By")
            (ui-theta-options
              (comp/computed
                (merge person-lookup
                  {:riverdb.entity/ns :entity.ns/person
                   :value             (:db/id QAPersonRef)})
                {:changeMutation `set-ref
                 :changeParams   {:ident this-ident :k :sitevisit/QAPersonRef}
                 :opts           default-opts})))

          (div :.field {:key "qadate" :style {:width 180}}
            (label {:style {} :onClick #(set-value! this :sitevisit/QADate nil)} "QA Date")
            (ui-datepicker {:selected QADate
                            :onChange #(when (inst? %)
                                         (log/debug "QADate change" %)
                                         (set-value! this :sitevisit/QADate %))}))

          (div :.field {:key "publish"}
            (label {:style {}} "Publish?")
            (ui-checkbox {:size     "big" :fitted true :label "" :type "checkbox" :toggle true
                          :disabled (not (and QADate QAPersonRef))
                          :checked  (or QACheck false)
                          :onChange #(let [value (not QACheck)]
                                       (when (and QADate QAPersonRef)
                                         (log/debug "publish change" value)
                                         (set-value! this :sitevisit/QACheck value)))})))


        (when (not-empty Samples)
          (vec
            (for [sample Samples]
              (ui-sample-form
                (comp/computed sample
                  {:active-params                active-params
                   :samplingdevicelookup-options samplingdevicelookup-options
                   :sv-comp                      this})))))

        (div {:style {:marginTop 10}}
          (dom/button :.ui.button.secondary
            {:onClick #(do
                         (debug "CANCEL!" dirty? (fs/dirty-fields props false))
                         (if dirty?
                           (comp/transact! this
                             `[(rm/reset-form {:ident ~this-ident})])))}
            ;(do
            ;  (fm/set-value! this :ui/editing false)
            ;  (routes/route-to! "/sitevisit/list"))))}
            (if dirty?
              "Cancel"
              "Close"))

          (dom/button :.ui.button.primary
            {:disabled (not dirty?)
             :onClick  #(let [dirty-fields (fs/dirty-fields props true)
                              form-fields  (fs/get-form-fields SiteVisitForm)
                              form-props   (select-keys props form-fields)]
                          (debug "SAVE!" dirty? "dirty-fields" dirty-fields "form-props" form-props)
                          (debug "DIFF" (tu/ppstr dirty-fields))
                          (comp/transact! this
                            `[(rm/save-entity ~{:ident this-ident
                                                :diff  dirty-fields})]))}
                                                 ;:create form-props})])))}
                          ;  (comp/transact! this
                          ;    `[(rm/save-entity ~{:ident this-ident
                          ;                        :diff  dirty-fields})])))}
            ;(fm/set-value! this :ui/editing false)
            "Save"))))))


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


;(swap! state update-in [:tac-report-data :no-results-rs] sort-maps-by [:site :date])))
;:sitevisit/StationFailCode :entity.ns/stationfaillookup

;(defn replace-db-ident* [state-map entity]
;  (let [id?  (when (map? entity)
;               (get-in state-map [:db/ident (:db/id entity) :db/id]))]
;    (if id?
;      (assoc entity :db/id id?)
;      entity)))



(fm/defmutation form-ready
  [{:keys [route-target form-ident]}]
  (action [{:keys [state]}]
    (debug "MUTATION FORM READY" route-target form-ident)
    (swap! state
      (fn [st]
        (-> st
          (fs/add-form-config* SiteVisitForm form-ident)
          (update-in form-ident assoc :ui/ready true))))
    (dr/target-ready! SPA route-target)))

;(fm/defmutation merge-new-sv
;  [{:keys [sv-ident]}]
;  (action [{:keys [state]}]
;    (let [;_ (debug "Merge SV!" sv-ident)
;          sv (comp/get-initial-state SiteVisitForm {:id (second sv-ident)})
;          sv (walk-ident-refs @state sv)
;          sv (fs/add-form-config SiteVisitForm sv)
;          _   (debug "Merge SV! initial state" sv)]
;      ;sv (fs/add-form-config SiteVisitForm sv)]
;      ;_ (debug "Merge SV! updated ident refs" sv)]
;      ;samps (get sv :sitevisit/Samples)]
;      ;samps (mapv #(walk-ident-refs @state %) samps)
;      ;sv (assoc sv :sitevisit/Samples samps)]
;      ;(debug "Merge new SV! do we have samples?" samps)
;      (debug "Merge new SV! do we have DB globals yet?" (get-in @state [:component/id :globals]))
;      (swap! state
;        (fn [st]
;          (-> st
;            (merge/merge-component SiteVisitForm sv
;              :replace [:riverdb.ui.root/current-sv]
;              :replace [:component/id :sitevisit-editor :sitevisit])
;            (update-in sv-ident assoc :ui/create true)
;            (update-in sv-ident assoc :ui/ready true)))))))

(fm/defmutation merge-new-sv2
  [{:keys []}]
  (action [{:keys [state]}]
    (let [current-project (get @state :riverdb.ui.root/current-project)
          current-agency (get @state :riverdb.ui.root/current-agency)
          sv (comp/get-initial-state SiteVisitForm {:project current-project :agency current-agency})
          _ (debug "Merge SV!" sv current-project current-agency)
          ;sv (comp/get-initial-state SiteVisitForm {:id (second sv-ident)})
          sv (walk-ident-refs @state sv)
          sv (fs/add-form-config SiteVisitForm sv)]
          ;_   (debug "Merge SV! initial state" sv)]
          ;sv (fs/add-form-config SiteVisitForm sv)]
      ;_ (debug "Merge SV! updated ident refs" sv)]
      ;samps (get sv :sitevisit/Samples)]
      ;samps (mapv #(walk-ident-refs @state %) samps)
      ;sv (assoc sv :sitevisit/Samples samps)]
      ;(debug "Merge new SV! do we have samples?" samps)
      (debug "Merge new SV! do we have DB globals yet?" (get-in @state [:component/id :globals]))
      (swap! state
        (fn [st]
          (-> st
            ;; NOTE load the minimum so we can get globals after load
            (merge/merge-component SiteVisitForm sv

              #_{:db/id (second sv-ident)
                 :riverdb.entity/ns :entity.ns/sitevisit
                 :sitevisit/uuid (tempid/uuid)
                 :ui/ready false
                 :ui/create true}
              :replace [:riverdb.ui.root/current-sv]
              :replace [:component/id :sitevisit-editor :sitevisit])))))))
            ;(update-in sv-ident assoc :ui/create true)
            ;(update-in sv-ident assoc :ui/ready true)))))))



;(defsc ParamInfo [this props]
;  {:ident [:org.riverdb.db.parameter/gid :db/id]
;   :query [:db/id
;           :parameter/active
;           :parameter/high
;           :parameter/low
;           :parameter/name
;           :parameter/precisionCode
;           :parameter/replicates
;           {:parameter/constituentlookupRef looks/constituentlookup}
;           :parameter/samplingdevicelookupRef
;           {:parameter/sampleTypeRef [:db/id :sampletypelookup/SampleTypeCode]}]})

(defsc ProjectInfo [this props]
  {:ident [:org.riverdb.db.projectslookup/gid :db/id]
   :query [:db/id
           :projectslookup/ProjectID
           :projectslookup/Name
           {:projectslookup/Parameters (comp/get-query Parameter)}]})


(defsc SiteVisitEditor [this {:keys [sitevisit]
                              :riverdb.ui.root/keys [current-project current-agency]
                              :ui/keys [station-options sitevisittype-options ;person-options
                                        stationfaillookup-options samplingdevicelookup-options] :as props}]
  {:ident             (fn [] [:component/id :sitevisit-editor])
   :query             [:ui/station-options
                       ;:ui/person-options
                       :ui/sitevisittype-options
                       :ui/stationfaillookup-options
                       :ui/samplingdevicelookup-options
                       {[:riverdb.ui.root/current-agency '_] (comp/get-query looks/agencylookup-sum)}
                       {[:riverdb.ui.root/current-project '_] (comp/get-query ProjectInfo)}
                       {:sitevisit (comp/get-query SiteVisitForm)}
                       {[:riverdb.theta.options/ns :entity.ns/person] (comp/get-query ThetaOptions)}
                       [:riverdb.theta.options/ns '_]
                       [:db/ident '_]]
   :initial-state     {:ui/station-options              nil
                       :ui/sitevisittype-options        nil
                       :ui/stationfaillookup-options    nil
                       :ui/samplingdevicelookup-options nil}
   :route-segment     ["edit" :sv-id]
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
                                 (f/load! app sv-ident SiteVisitForm
                                   {:target               (targeting/multiple-targets
                                                            [:riverdb.ui.root/current-sv]
                                                            [:component/id :sitevisit-editor :sitevisit])
                                    :post-mutation        `form-ready
                                    :post-mutation-params {:route-target editor-ident
                                                           :form-ident   sv-ident}}))))))
   :will-leave        (fn [this props]
                        (debug "WILL LEAVE EDITOR")
                        (dr/route-immediate (comp/get-ident this)))


   ;:componentDidMount (fn [this]
   ;                     (let [props (comp/props this)]
   ;                       (preload-options :entity.ns/constituentlookup)
   ;                       (fm/set-value! this :ui/ready true)))

   :componentDidMount (fn [this]
                        (let [props  (comp/props this)
                              {:riverdb.ui.root/keys [current-agency current-project]
                               :keys                 [sitevisit]} props
                              {:sitevisit/keys [AgencyCode ProjectID]} sitevisit
                              projID (:db/id current-project)
                              agID   (:db/id current-agency)
                              _      (log/debug "DID MOUNT SiteVisitEditor" "AGENCY" AgencyCode "PROJECT" ProjectID "projID" projID "agID" agID)]


                          (preload-options :entity.ns/person {:limit -1 :filter {:person/Agency (:db/id current-agency)}})
                          (preload-options :entity.ns/samplingdevice {:limit -1} :samplingdevice/DeviceType)
                          (preload-options :entity.ns/samplingdevicelookup {:limit -1})

                          (preload-agency agID)

                          ;(preload-options :entity.ns/person {:limit -1 :filter {:person/Agency agID}})
                          ;(preload-options :entity.ns/sitevisittype)
                          ;(preload-options :entity.ns/stationfaillookup)
                          ;(preload-options :entity.ns/samplingdevicelookup)
                          ;(preload-options :entity.ns/stationlookup {:filter {:stationlookup/Project projID}})))

                          ;; FIXME replace with preload-options like above and ui-theta-options at dropdown location
                          (do
                            (f/load! this :org.riverdb.db.stationlookup looks/stationlookup-sum
                              {:params               {:limit  -1
                                                      :filter {:stationlookup/Project projID}}
                               :parallel             true
                               :post-mutation        `sort-load->select-options
                               :post-mutation-params {:target        [:org.riverdb.db.stationlookup/gid]
                                                      :sort-fn       (fn [[_ m]]
                                                                       (:stationlookup/StationID m))
                                                      :select-target [:component/id :sitevisit-editor :ui/station-options]
                                                      :text-fn       (fn [{:stationlookup/keys [StationID StationName]}]
                                                                       (str StationID ": " StationName))}})

                            (f/load! this :org.riverdb.db.sitevisittype looks/sitevisittype
                              {:params               {:limit -1}
                               :parallel             true
                               :post-mutation        `sort-load->select-options
                               :post-mutation-params {:target        [:org.riverdb.db.sitevisittype/gid]
                                                      :sort-fn       (fn [[_ m]]
                                                                       (:sitevisittype/name m))
                                                      :select-target [:component/id :sitevisit-editor :ui/sitevisittype-options]
                                                      :text-fn       :sitevisittype/name}})
                            (f/load! this :org.riverdb.db.stationfaillookup looks/stationfaillookup
                              {:params               {:limit -1}
                               :parallel             true
                               :post-mutation        `sort-load->select-options
                               :post-mutation-params {:target        [:org.riverdb.db.stationfaillookup/gid]
                                                      :sort-fn       (fn [[_ m]]
                                                                       (:stationfaillookup/FailureReason m))
                                                      :select-target [:component/id :sitevisit-editor :ui/stationfaillookup-options]
                                                      :text-fn       :stationfaillookup/FailureReason}}))))

   :css               [[:.floating-menu {:position "absolute !important"
                                         :z-index  1000
                                         :width    "300px"
                                         :right    "0px"
                                         :top      "50px"}
                        :.ui.segment {:padding ".5em"}
                        :.ui.raised.segment {:padding ".3em"}
                        :.ui.table {:margin-top ".3em"}]]}
  (let [parameters    (get-in current-project [:projectslookup/Parameters])
        person-lookup (get props [:riverdb.theta.options/ns :entity.ns/person])]
    ;(debug "RENDER SiteVisitsEditor" "CURRENT-PROJECT" current-project "add-person-modal" (:ui/add-person-modal sitevisit))
    (div {}
      (ui-sv-form (comp/computed sitevisit {:station-options              station-options
                                            :sitevisittype-options        sitevisittype-options
                                            :stationfaillookup-options    stationfaillookup-options
                                            :samplingdevicelookup-options samplingdevicelookup-options
                                            :current-project              current-project
                                            :current-agency               current-agency
                                            :person-lookup                person-lookup
                                            :parameters                   parameters})))))



(defsc SiteVisitSummary [this {:keys [db/id sitevisit/SiteVisitDate sitevisit/VisitType] :as props} {:keys [onEdit]}]
  {:query         [:db/id
                   :sitevisit/SiteVisitID
                   :sitevisit/SiteVisitDate
                   {:sitevisit/StationID (comp/get-query looks/stationlookup-sum)}
                   {:sitevisit/VisitType (comp/get-query looks/sitevisittype)}]
   :ident         [:org.riverdb.db.sitevisit/gid :db/id]
   :initial-state {:db/id                   (tempid/tempid)
                   :sitevisit/SiteVisitDate (js/Date.)
                   :sitevisit/StationID     {}
                   :sitevisit/VisitType     {}}}
  (let [type    (:sitevisittype/name VisitType)
        id      (if (tempid/tempid? id)
                  (.-id id)
                  id)
        edit-fn #(onEdit props)]
    ;(log/debug "RENDER SV SUM" id type SiteVisitDate)
    (tr {:key id :style {:cursor "pointer"} :onClick edit-fn} ;:onMouseOver #(println "HOVER" id)}
      (td {:key 1} (str SiteVisitDate))
      (td {:key 2} type)
      (td {:key 3 :padding 2}
        (button :.ui.primary.basic.icon.button
          {:style {:padding 5} :onClick edit-fn}
          (dom/i :.pointer.edit.icon {}))))))

(def ui-sv-summary (comp/factory SiteVisitSummary {:keyfn :db/id}))

(defn load-sitevisits
  ([this filter limit offset sort]
   (let [params {:filter filter :limit limit :offset offset :sort sort}]
     (debug "LOAD SITEVISITS" params)
     (f/load! this :org.riverdb.db.sitevisit-meta nil
       {:parallel true
        :params   {:filter filter :meta-count true :limit 1}
        :target   [:component/id :sitevisit-list :ui/list-meta]})
     (f/load! this :org.riverdb.db.sitevisit looks/sitevisit-sum
       {:parallel true
        :target   [:component/id :sitevisit-list :sitevisits]
        :params   {:filter filter :limit limit :offset offset :sort sort}}))))

(defn load-sites [this agencyCode]
  (debug "LOAD SITES" agencyCode)
  (let [target [:component/id :sitevisit-list :sites]]
    (f/load! this :org.riverdb.db.stationlookup looks/stationlookup-sum
      {:target               target
       :params               {:limit -1 :filter {:stationlookup/Agency {:agencylookup/AgencyCode agencyCode}}}
       :post-mutation        `rm/sort-ident-list-by
       :post-mutation-params {:idents-path target
                              :ident-key   :org.riverdb.db.stationlookup/gid
                              :sort-fn     :stationlookup/StationID}})))

(defn make-filter
  ([props]
   (let [{:riverdb.ui.root/keys [current-agency current-project current-year]
          :ui/keys              [site]} props
         year-from (when current-year
                     (js/parseInt current-year))
         year-to   (when year-from
                     (inc year-from))
         filter    (cond-> {}
                     (and year-from year-to)
                     (merge {:sitevisit/SiteVisitDate {:> (js/Date. (str year-from))
                                                       :< (js/Date. (str year-to))}})
                     site
                     (merge {:sitevisit/StationID site}))]
     (debug "MAKE FILTER" year-from year-to)
     filter)))



(defsc SiteVisitList
  "This tracks current-project, current-year, filter, limit, sort, and site"
  [this {:keys                 [sitevisits sites project-years]
         :riverdb.ui.root/keys [current-agency current-project current-year]
         :ui/keys              [site sort limit offset list-meta] :as props}]
  {:ident              (fn [] [:component/id :sitevisit-list])
   :query              [{:sitevisits (comp/get-query SiteVisitSummary)}
                        {:project-years (comp/get-query ProjectYears)}
                        {:sites (comp/get-query looks/stationlookup-sum)}
                        {[:riverdb.ui.root/current-agency '_] (comp/get-query looks/agencylookup-sum)}
                        {[:riverdb.ui.root/current-project '_] (comp/get-query looks/projectslookup-sum)}
                        [:riverdb.ui.root/current-year '_]
                        :ui/limit
                        :ui/offset
                        :ui/sort
                        :ui/site
                        :ui/list-meta]
   :route-segment      ["list"]
   :initial-state      {:sitevisits    []
                        :sites         []
                        :project-years {}
                        :ui/list-meta  nil
                        :ui/limit      15
                        :ui/offset     0
                        :ui/sort       nil
                        :ui/site       nil}
   :componentDidUpdate (fn [this prev-props prev-state]
                         (let [{:ui/keys              [limit offset sort site] :as props
                                :riverdb.ui.root/keys [current-project current-year]} (comp/props this)
                               prev-proj   (get prev-props :riverdb.ui.root/current-project)
                               prev-year   (get prev-props :riverdb.ui.root/current-year)
                               prev-offset (get prev-props :ui/offset)
                               prev-limit  (get prev-props :ui/limit)
                               prev-sort   (get prev-props :ui/sort)
                               prev-site   (get prev-props :ui/site)
                               runQuery?   (or
                                             (not= prev-proj current-project)
                                             (not= prev-year current-year)
                                             (not= offset prev-offset)
                                             (not= limit prev-limit)
                                             (not= sort prev-sort)
                                             (not= site prev-site))]
                           (debug "SV LIST :componentDidUpdate: RUN QUERY" runQuery?)
                           (when runQuery?
                             (let [filter (make-filter props)]
                               (load-sitevisits this filter limit offset sort)))))
   :componentDidMount  (fn [this]
                         (let [{:ui/keys              [limit offset sort] :as props
                                :riverdb.ui.root/keys [current-agency]} (comp/props this)
                               filter (make-filter props)
                               {:agencylookup/keys [AgencyCode]} current-agency]
                           (debug "SV LIST :componentDidMount RUN QUERY" AgencyCode)
                           (load-sitevisits this filter limit offset sort)
                           (when AgencyCode
                             (load-sites this AgencyCode))))}

  (let [activePage             (inc (/ offset limit))
        query-count            (get list-meta :org.riverdb.meta/query-count 0)
        totalPages             (int (Math/ceil (/ query-count limit)))
        handlePaginationChange (fn [e t]
                                 (let [page       (-> t .-activePage)
                                       new-offset (* (dec page) limit)]
                                   (log/debug "PAGINATION" "page" page "new-offset" new-offset)
                                   (when (> new-offset -1) (fm/set-integer! this :ui/offset :value new-offset))))
        onEdit                 (fn [sv]
                                 (routes/route-to! (str "/sitevisit/edit/" (or (:db/id sv) "new"))))]

    (div {}
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
                                    (fm/set-value! this :ui/site st))}
                (into
                  [(option {:value "" :key "none"} "")]
                  (doall
                    (for [{:keys [db/id stationlookup/StationName stationlookup/StationID]} sites]
                      (option {:value id :key id} (str StationID ": " StationName)))))))))
        (div :.item
          (button {:key "create" :onClick #(onEdit nil)} "New"))

        (div :.item.right
          (ui-pagination
            {:id            "paginator"
             :activePage    activePage
             :boundaryRange 1
             :onPageChange  handlePaginationChange
             :size          "mini"
             :siblingRange  1
             :totalPages    totalPages})))

      (when (seq sitevisits)
        (table :.ui.selectable.very.compact.small.table {:key "sv-table"}
          (thead {:key 1}
            (tr {}
              (th {:key 1} (str "Date " query-count))
              (th {:key 2} "Type")
              (th {:key 3} "Edit")))
          (tbody {:key 2}
            (vec
              (for [sv sitevisits]
                (ui-sv-summary (comp/computed sv {:onEdit onEdit}))))))))))


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