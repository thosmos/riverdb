(ns riverdb.ui.sitevisits-page
  (:require
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.application :refer [current-state]]
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
    [com.fulcrologic.semantic-ui.elements.loader.ui-loader :refer [ui-loader]]
    [com.fulcrologic.semantic-ui.modules.dimmer.ui-dimmer :refer [ui-dimmer]]
    [com.fulcrologic.semantic-ui.addons.pagination.ui-pagination :refer [ui-pagination]]
    [com.fulcrologic.semantic-ui.modules.checkbox.ui-checkbox :refer [ui-checkbox]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table :refer [ui-table]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table-body :refer [ui-table-body]]
    [riverdb.application :refer [SPA]]
    [riverdb.model.sitevisit :as sv]
    [riverdb.model.user :as user]
    [riverdb.api.mutations :as rm]
    [riverdb.util :refer [sort-maps-by with-index]]
    [riverdb.ui.components :as c :refer [ui-datepicker]]
    [riverdb.ui.lookup]
    [riverdb.ui.lookup-options :refer [preload-options ui-theta-options]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.routes :as routes]
    [riverdb.ui.session :refer [Session]]
    [riverdb.ui.project-years :refer [ProjectYears ui-project-years]]
    [riverdb.ui.util :refer [make-tempid]]
    [riverdb.util :refer [paginate]]
    [theta.log :as log :refer [debug info]]
    [tick.alpha.api :as t]
    [com.fulcrologic.fulcro.application :as app]
    [goog.object :as gobj]))

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
   :query [:db/id :constituentlookup/Name]})

(defsc FieldResultForm [this {:keys [] :as props}]
  {:ident         [:org.riverdb.db.fieldresult/gid :db/id]
   :query         [:db/id
                   fs/form-config-join
                   :fieldresult/FieldResultRowID
                   :fieldresult/Result
                   :fieldresult/ResultTime
                   :fieldresult/FieldReplicate
                   :fieldresult/ConstituentRowID]
   :form-fields   #{:fieldresult/Result :fieldresult/ResultTime}
   :initial-state (fn [{:keys [replicate constituentRef]}]
                    {:db/id                        (make-tempid)
                     :fieldresult/ConstituentRowID constituentRef
                     :fieldresult/FieldReplicate   replicate})})

(defsc FieldObsResult [this {:keys [] :as props}]
  {:ident [:org.riverdb.db.fieldobsresult/gid :db/id]
   :query [:db/id
           :fieldobsresult/FieldObsResultRowID
           :fieldobsresult/IntResult
           :fieldobsresult/TextResult
           {:fieldobsresult/ConstituentRowID
            (comp/get-query Constituent)}]})

(defsc LabResult [this {:keys [] :as props}]
  {:ident [:org.riverdb.db.labresult/gid :db/id]
   :query [:db/id
           :labresult/LabResultRowID
           :labresult/Result
           :labresult/ResQualCode
           :labresult/AnalysisDate
           :labresult/Basis
           :labresult/LabReplicate
           :labresult/SigFig
           {:labresult/ConstituentRowID
            (comp/get-query Constituent)}]})


;(comp/get-initial-state Sample {:type :field})
;(comp/get-initial-state Sample {:type :obs})
;(comp/get-initial-state Sample {:type :lab})

(defn set-value! [this k value]
  (let [props (comp/props this)
        ready (:ui/ready props)]
    (when ready
      (fm/set-value! this k value)
      (transact! this `[(fs/mark-complete! nil k)]))))

(defn set-ref! [this k id]
  (let [props (comp/props this)
        ready (:ui/ready props)]
    (when
      ready
      (set-value! this k {:db/id id})
      (transact! this `[(fs/mark-complete! nil k)]))))

(defn set-refs! [this k ids]
  (let [props (comp/props this)
        ready (:ui/ready props)]
    (when ready
      (set-value! this k (mapv #(identity {:db/id %}) ids))
      (transact! this `[(fs/mark-complete! nil k)]))))

(defn set-in* [state path val]
  (assoc-in state path val))
(fm/defmutation set-in [{:keys [path val]}]
  (action [{:keys [state]}]
    (swap! state set-in* path val)))
(defn set-in! [path val refresh-ident]
  (comp/transact! SPA `[(set-in {:path ~path :val ~val})]
    {:only-refresh refresh-ident}))

(defn set-field-result* [state db-id val]
  (assoc-in state [:org.riverdb.db.fieldresult/gid db-id :fieldresult/Result] val))
(fm/defmutation set-field-result [{:keys [db-id val]}]
  (action [{:keys [state]}]
    (swap! state set-field-result* db-id val)))
(defn set-field-result! [db-id val only-refresh]
  (comp/transact! SPA `[(set-field-result {:db-id ~db-id :val ~val})]
    {:only-refresh only-refresh}))

(defn set-number [e db-id refresh-ident]
  (let [v (js/Number (-> e .-target .-value))
        v (if (js/Number.isNaN v) nil v)]
    (debug "CHANGE FIELD RESULT" db-id v)
    (set-field-result! db-id v refresh-ident)))

;(defn set-number [e db-id refresh-ident]
;  (let [v     (-> e .-target .-value)]
;    (debug "CHANGE FIELD RESULT" db-id v)
;    (set-field-result! db-id v refresh-ident)))

(defn rui-fieldres [f-res only-refresh]
  (let [val          (:fieldresult/Result f-res)
        only-refresh (conj only-refresh
                       [:org.riverdb.db.fieldresult/gid (:db/id f-res)]
                       :fieldresult/Result)]
    (dom/input {:type     "text"
                :value    (or (str val) "")
                :style    {:width "60px"}
                :onChange #(let [db-id (:db/id f-res)
                                 v     (-> % .-target .-value)]
                             (debug "CHANGE FIELD RESULT" db-id v)
                             (set-field-result! db-id v only-refresh))})))

(defsc SampleParamForm
  [this
   {:keys [db/id inst instID v1 v2 v3 v4 time
           unit reps mean range stddev rds] :as props}
   {:keys [samplingdevicelookup-options
           comp sample]}]
  {:query                 (fn [] [:db/id
                                  :inst :instID :v1 :v2 :v3 :v4 :time :unit
                                  :ui/reps :ui/mean :ui/range :ui/stddev :ui/rds
                                  :parameter/name
                                  :parameter/samplingdevicelookupRef])}
   ;:shouldComponentUpdate (fn [_ _ _] true)}
  (let [p-name       (:parameter/name props)
        p-device     (:parameter/samplingdevicelookupRef props)
        p-const      (:parameter/constituentlookupRef props)
        results      (filter #(= (:fieldresult/ConstituentRowID %) p-const)
                       (:sample/FieldResults sample))
        only-refresh [(comp/get-ident comp)]
        rs           (into {} (map #(identity [(:fieldresult/FieldReplicate %) %]) results))
        r1           (get rs 1)
        r2           (get rs 2)
        r3           (get rs 3)
        v1           (:fieldresult/Result r1)
        v2           (:fieldresult/Result r2)
        v3           (:fieldresult/Result r3)
        t1           (:fieldresult/ResultTime r1)
        t2           (:fieldresult/ResultTime r2)
        t3           (:fieldresult/ResultTime r3)]
    (debug "SampleParamForm" v1 v2 v3 t1 t2 t3)
    (tr {:key id}
      (td {:key "name"} p-name)
      (td {:key "inst"}
        (ui-form-dropdown {:search       false
                           :selection    true
                           :value        (or (:db/id inst) (:db/id p-device) "")
                           :options      samplingdevicelookup-options
                           :autoComplete "off"
                           :tabIndex     "-1"
                           :style        {:width "100px"}}))
      ;:onChange     (fn [_ d]
      ;                (when-let [value (-> d .-value)]
      ;                  (set-ref! this :sitevisit/QAPerson value)))}))
      (td {:key "instID"} (dom/input {:type "text" :value (or instID "") :style {:width "50px"}}))
      (td {:key "unit"} (or unit ""))
      (td {:key "v1"} (rui-fieldres r1 only-refresh))
      (td {:key "v2"} (rui-fieldres r2 only-refresh))
      (td {:key "v3"} (rui-fieldres r3 only-refresh))
      ;(td {:key "v4"} (dom/input {:type "text" :value (or v4 "") :style {:width "60px"}}))
      (td {:key "time"} (dom/input {:type     "text" :value (or (str t1) "") :style {:width "80px"}
                                    :onChange #(set-in!
                                                 [:org.riverdb.db.fieldresult/gid (:db/id r1) :fieldresult/ResultTime]
                                                 (-> % .-target .-value)
                                                 (conj only-refresh [:org.riverdb.db.fieldresult/gid (:db/id r1)]))}))
      ;:onChange #(set-field-result! v refresh-ident)}))
      (td {:key "reps"} "")
      (td {:key "mean"} (or (str (/ (+ v1 v2 v3) 3)) ""))
      (td {:key "range"} "")
      (td {:key "stddev"} "")
      (td {:key "rds"} ""))))
(def ui-sample-param-form (comp/factory SampleParamForm {:keyfn :db/id}))


(defsc FieldMeasureSampleForm [this props {:keys [samplingdevicelookup-options
                                                  field-measure-params
                                                  sv-comp]}]
  {:ident                 [:org.riverdb.db.sample/gid :db/id]
   :query                 [:db/id
                           fs/form-config-join
                           :ui/ready
                           {:sample/EventType [:db/id :eventtypelookup/EventType]}
                           {:sample/FieldResults (comp/get-query FieldResultForm)}
                           :sample/SampleReplicate
                           :sample/Unit]
   :initial-state         (fn [p] {:db/id            (make-tempid)
                                   :sample/EventType {:db/id :eventtypelookup/WaterChem}})
   ;:shouldComponentUpdate (fn [_ _ _] true)
   :form-fields           #{:sample/FieldResults}}
  (table :.ui.very.compact.mini.table {:key "wqtab"}
    (thead {:key 1}
      (tr {}
        (th {:key "nm"} "Param")
        (th {:key "inst"} "Device")
        (th {:key "instID"} "Inst ID")
        (th {:key "units"} "Units")
        (th {:key "1"} "Test 1")
        (th {:key "2"} "Test 2")
        (th {:key "3"} "Test 3")
        ;(th {:key "4"} "Test 4")
        (th {:key "time"} "Time")
        (th {:key "reps"} "# Tests")
        (th {:key "mean"} "Mean")
        (th {:key "range"} "Range")
        (th {:key "stddev"} "Std Dev")
        (th {:key "rds"} "% Prec")))
    (tbody {:key 2}
      (mapv
        #(ui-sample-param-form
           (comp/computed %
             {:samplingdevicelookup-options samplingdevicelookup-options
              :comp                         sv-comp
              :sample                       props}))
        field-measure-params))))
(def ui-fieldmeasure-sample-form (comp/factory FieldMeasureSampleForm {:keyfn :db/id}))


;(defsc FieldMeasureSampleForm [this props]
;  {:ident [:org.riverdb.db.sample/gid :db/id]
;   :query [:db/id
;           fs/form-config-join
;           :sample/EventType
;           :sample/FieldResults]})


(defn filter-sample-type-1 [ident samples]
  (let [app-state (current-state SPA)
        ident-id  (get-in app-state [:db/ident ident :db/id])]
    (filter #(= (get-in % [:sample/EventType :db/id]) ident-id) samples)))

(defn filter-sample-type [type samples]
  (filter #(= (get-in % [:sample/EventType :eventtypelookup/EventType]) type) samples))

(defsc SiteVisitForm [this
                      {:ui/keys [ready]
                       :db/keys [id]
                       :sitevisit/keys [StationID DataEntryDate SiteVisitDate Time
                                        Visitors VisitType StationFailCode
                                        DataEntryPersonRef CheckPersonRef
                                        QAPersonRef QACheck QADate Samples] :as props}
                      {:keys [station-options person-options sitevisittype-options
                              stationfaillookup-options samplingdevicelookup-options
                              current-project parameters] :as cprops}]
  {:ident                 [:org.riverdb.db.sitevisit/gid :db/id]
   :query                 (fn [] [:db/id
                                  fs/form-config-join
                                  :ui/ready
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
                                  {:sitevisit/Visitors (comp/get-query looks/person)}
                                  {:sitevisit/Samples (comp/get-query FieldMeasureSampleForm)}
                                  [:riverdb.theta.options/ns '_]])
   :initial-state         (fn [{:keys [id VisitType StationFailCode] :as params}]
                            {:db/id                       (or id (make-tempid))
                             :ui/ready                    false
                             :riverdb.entity/ns           :entity.ns/sitevisit
                             :sitevisit/CreationTimestamp (js/Date.)
                             :sitevisit/Visitors          []
                             :sitevisit/Samples           [(comp/get-initial-state FieldMeasureSampleForm {})]
                             :sitevisit/VisitType         (or VisitType {:db/id :sitevisittype/Monthly})
                             :sitevisit/StationFailCode   (or StationFailCode {:db/id :stationfaillookup/None})})
   ;:shouldComponentUpdate (fn [_ _ _] true)

   :form-fields           #{:sitevisit/StationID :sitevisit/DataEntryDate :sitevisit/SiteVisitDate :sitevisit/Time
                            :sitevisit/Visitors :sitevisit/VisitType :sitevisit/StationFailCode
                            :sitevisit/DataEntryPersonRef :sitevisit/CheckPersonRef
                            :sitevisit/QAPersonRef :sitevisit/QACheck :sitevisit/QADate :sitevisit/Samples}}
  ;:pre-merge     (fn [{:keys [current-normalized data-tree] :as env}]
  ;                 (debug "PREMERGE SiteVisitForm" current-normalized data-tree)
  ;                 (->> data-tree
  ;                   (merge current-normalized)
  ;                   (clojure.walk/postwalk
  ;                     #(if (= % :com.fulcrologic.fulcro.algorithms.merge/not-found) nil %))))}
  (let [isLoading           false;(not (and station-options person-options sitevisittype-options stationfaillookup-options samplingdevicelookup-options))
        dirty-fields        (fs/dirty-fields props false)
        dirty?              (some? (seq dirty-fields))
        active-params       (filter :parameter/active parameters)
        fieldmeasure-sample (first (filter-sample-type-1 :eventtypelookup/WaterChem Samples))]
    ;(debug "RENDER Site Visit Form " id "DIRTY FIELDS" dirty-fields "SAMPLE" fieldmeasure-sample)
    (div :.dimmable.fields {:key "sv-form"}
      (ui-dimmer {:inverted true :active isLoading}
        (ui-loader {:indeterminate true}))
      (ui-form {:key "form" :size "tiny"}
        (div :.dimmable.fields {:key "lookups"}
          (div :.field {:key "people"}
            (label {:style {}} "Monitors")
            (ui-theta-options
              (comp/computed
                {:riverdb.entity/ns :entity.ns/person
                 :value             (mapv :db/id Visitors)
                 :opts              {:multiple         true
                                     :clearable        true
                                     :allowAdditions   true
                                     :additionPosition "bottom"
                                     :style            {:maxWidth 168}}}
                {:onChange  #(do
                               (log/debug "people change" %)
                               (set-refs! this :sitevisit/Visitors %))
                 :onAddItem #(do
                               (debug "ADD MONITOR" %))})))

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

        (div :.dimmable.fields {:key "dates"}
          (div :.field {:key "svdate" :style {:width 180}}
            (label {} "Site Visit Date")
            (ui-datepicker {:selected SiteVisitDate
                            :onChange #(when (inst? %)
                                         (log/debug "date change" %)
                                         (set-value! this :sitevisit/SiteVisitDate %))}))

          (ui-form-input
            {:label    "Time"
             :value    (or Time "")
             :style    {:width 150}
             :onChange #(do
                          (log/debug "Time change" (-> % .-target .-value))
                          (set-value! this :sitevisit/Time (-> % .-target .-value)))}))

        (div :.dimmable.fields {:key "entry"}
          (div :.field {:key "enterer"}
            (label {:style {}} "Entered By")
            (ui-theta-options
              (comp/computed
                {:riverdb.entity/ns :entity.ns/person
                 :value             (:db/id DataEntryPersonRef)
                 :opts              {:clearable        true
                                     :allowAdditions   true
                                     :additionPosition "bottom"
                                     :style            {:maxWidth 168}}}
                {:onChange  #(do
                               (log/debug "data person changed" %)
                               (set-ref! this :sitevisit/DataEntryPersonRef %))
                 :onAddItem #(do
                               (debug "ADD Entry Person" %))})))

          (div :.field {:key "dedate" :style {:width 180}}
            (label {:style {}} "Data Entry Date")
            (do
              (debug "RENDER DataEntryDate" DataEntryDate)
              (ui-datepicker {:selected DataEntryDate
                              :onChange #(when (inst? %)
                                           (log/debug "date change" %)
                                           (set-value! this :sitevisit/DataEntryDate %))})))

          (div :.field {:key "checker"}
            (label {:style {}} "Checked By")
            (ui-theta-options
              (comp/computed
                {:riverdb.entity/ns :entity.ns/person
                 :value             (:db/id CheckPersonRef)
                 :opts              {:clearable        true
                                     :allowAdditions   true
                                     :additionPosition "bottom"
                                     :style            {:maxWidth 168}}}
                {:onChange  #(do
                               (log/debug "check person changed" %)
                               (set-ref! this :sitevisit/CheckPersonRef %))
                 :onAddItem #(do
                               (debug "ADD Check Person" %))}))))

        (div :.dimmable.fields {:key "qa"}
          (div :.field {:key "qcer"}
            (label {:style {}} "QA'd By")
            (ui-theta-options
              (comp/computed
                {:riverdb.entity/ns :entity.ns/person
                 :value             (:db/id QAPersonRef)
                 :opts              {:clearable        true
                                     :allowAdditions   true
                                     :additionPosition "bottom"
                                     :style            {:maxWidth 168}}}
                {:onChange  #(do
                               (log/debug "QA person changed" %)
                               (set-ref! this :sitevisit/QAPersonRef %))
                 :onAddItem #(do
                               (debug "ADD QA Person" %))})))

          (div :.field {:key "qadate" :style {:width 180}}
            (label {:style {}} "QA Date")
            (ui-datepicker {:selected QADate
                            :onChange #(when (inst? %)
                                         (log/debug "QADate change" %)
                                         (set-value! this :sitevisit/QADate %))}))

          (div :.field {:key "publish"}
            (label {:style {}} "Publish?")
            (ui-checkbox {:size     "big" :fitted true :label "" :type "checkbox" :toggle true
                          :disabled (not (and QADate QAPersonRef))
                          :checked  QACheck
                          :onChange #(let [value (not QACheck)]
                                       (when (and QADate QAPersonRef)
                                         (log/debug "publish change" value)
                                         (set-value! this :sitevisit/QACheck value)))})))


        (ui-fieldmeasure-sample-form
          (comp/computed fieldmeasure-sample
            {:field-measure-params         active-params
             :samplingdevicelookup-options samplingdevicelookup-options
             :sv-comp                      this}))

        ;(table :.ui.very.compact.mini.table {:key "wqtab"}
        ;  (thead {:key 1}
        ;    (tr {}
        ;      (th {:key "nm"} "Param")
        ;      (th {:key "inst"} "Device")
        ;      (th {:key "instID"} "Inst ID")
        ;      (th {:key "units"} "Units")
        ;      (th {:key "1"} "Test 1")
        ;      (th {:key "2"} "Test 2")
        ;      (th {:key "3"} "Test 3")
        ;      (th {:key "4"} "Test 4")
        ;      (th {:key "time"} "Time")
        ;      (th {:key "reps"} "# Tests")
        ;      (th {:key "mean"} "Mean")
        ;      (th {:key "range"} "Range")
        ;      (th {:key "stddev"} "Std Dev")
        ;      (th {:key "rds"} "% Prec")))
        ;  (tbody {:key 2}
        ;    (mapv
        ;      #(ui-sample-param-form
        ;         (comp/computed %
        ;           {:samplingdevicelookup-options samplingdevicelookup-options}))
        ;      active-params)))


        (div {}
          (dom/button :.ui.button.secondary
            {:onClick #(do
                         (debug "CANCEL!" dirty? (fs/dirty-fields props false))
                         (if dirty?
                           (comp/transact! this
                             `[(rm/reset-form ~{:ident (comp/get-ident this)})])
                           (do
                             (fm/set-value! this :ui/editing false)
                             (routes/route-to! "/sitevisit/list"))))}
            (if dirty?
              "Cancel"
              "Close"))

          (dom/button :.ui.button.primary
            {:disabled (not dirty?)
             :onClick  #(let [dirty-fields (fs/dirty-fields props true)]
                          (debug "SAVE!" dirty? dirty-fields)
                          (comp/transact! this
                            `[(rm/save-entity ~{:ident (comp/get-ident this)
                                                :diff  dirty-fields})])
                          (fm/set-value! this :ui/editing false))}
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

(defn replace-ident-refs* [state-map ident]
  (let [props     (get-in state-map ident)
        new-props (reduce-kv
                    (fn [props k v]
                      (if (and (map? v) (keyword? (:db/id v)))
                        (if-let [db-id (get-in state-map [:db/ident (:db/id v) :db/id])]
                          (assoc-in props [k :db/id] db-id)
                          props)
                        props))
                    props props)]
    (assoc-in state-map ident new-props)))

(defn walk-ident-refs* [state-map ident]
  (let [props     (get-in state-map ident)
        f         (fn [[k v]]
                    (if (and (map? v) (keyword? (:db/id v)))
                      (if-let [db-id (get-in state-map [:db/ident (:db/id v) :db/id])]
                        [k (assoc v :db/id db-id)]
                        [k v])
                      [k v]))
        new-props (clojure.walk/prewalk
                    (fn [x] (if (map? x) (into {} (map f x)) x))
                    props)]
    (assoc-in state-map ident new-props)))

(fm/defmutation form-ready
  [{:keys [route-target form-target form-class-key]}]
  (action [{:keys [state]}]
    (debug "MUTATION FORM READY" route-target form-target form-class-key)
    (let [form-class (comp/registry-key->class form-class-key)]
      (dr/target-ready! SPA route-target)
      (swap! state (fn [st]
                     (-> st
                       (fs/add-form-config* SiteVisitForm form-target)
                       (assoc-in (conj form-target :ui/ready) true)))))))

(fm/defmutation merge-new-sv
  [{:keys [sv-ident]}]
  (action [{:keys [state]}]
    (let [sv (comp/get-initial-state SiteVisitForm {:id (second sv-ident)})]
      (swap! state
        (fn [st]
          (-> st
            (merge/merge-component SiteVisitForm sv
              :replace [:riverdb.ui.root/current-sv]
              :replace [:component/id :sitevisit-editor :sitevisit])
            (walk-ident-refs* sv-ident)
            (fs/add-form-config* SiteVisitForm sv-ident)
            (assoc-in (conj sv-ident :ui/ready) true)))))))

(defsc ParamInfo [this props]
  {:ident [:org.riverdb.db.parameter/gid :db/id]
   :query [:db/id
           :parameter/active
           :parameter/high
           :parameter/low
           :parameter/name
           :parameter/precisionCode
           :parameter/replicates
           :parameter/constituentlookupRef
           :parameter/samplingdevicelookupRef]})

(defsc ProjectInfo [this props]
  {:ident [:org.riverdb.db.projectslookup/gid :db/id]
   :query [:db/id
           :projectslookup/ProjectID
           :projectslookup/Name
           {:projectslookup/Parameters (comp/get-query ParamInfo)}]})


(defsc SiteVisitEditor [this {:keys [sitevisit]
                              :riverdb.ui.root/keys [current-project]
                              :ui/keys [station-options person-options sitevisittype-options
                                        stationfaillookup-options samplingdevicelookup-options] :as props}]
  {:ident                 (fn [] [:component/id :sitevisit-editor])
   :query                 [:ui/station-options
                           :ui/person-options
                           :ui/sitevisittype-options
                           :ui/stationfaillookup-options
                           :ui/samplingdevicelookup-options
                           {[:riverdb.ui.root/current-agency '_] (comp/get-query looks/agencylookup-sum)}
                           {[:riverdb.ui.root/current-project '_] (comp/get-query ProjectInfo)}
                           {:sitevisit (comp/get-query SiteVisitForm)}]
   :initial-state         {:ui/station-options              nil
                           :ui/person-options               nil
                           :ui/sitevisittype-options        nil
                           :ui/stationfaillookup-options    nil
                           :ui/samplingdevicelookup-options nil}
   :route-segment         ["edit" :sv-id]
   ;:shouldComponentUpdate (fn [_ _ _] true)
   :will-enter            (fn [app {:keys [sv-id] :as params}]
                            (let [is-new?      (= sv-id "new")
                                  ident-key    :org.riverdb.db.sitevisit/gid
                                  editor-ident [:component/id :sitevisit-editor]]
                              (log/debug "WILL ENTER SiteVisitEditor" "NEW?" is-new? "PARAMS" params)
                              (if is-new?
                                (dr/route-deferred editor-ident
                                  #(let [sv-ident [ident-key (make-tempid)]]
                                     (log/debug "CREATING A NEW SITEVISIT" sv-ident)
                                     (comp/transact! app `[(merge-new-sv ~{:sv-ident sv-ident})])
                                     (dr/target-ready! app editor-ident)))
                                (dr/route-deferred editor-ident
                                  #(let [sv-ident [ident-key sv-id]]
                                     (log/debug "LOADING AN EXISTING SITEVISIT" sv-ident)
                                     (f/load! app sv-ident SiteVisitForm
                                       {:target               (targeting/multiple-targets
                                                                [:riverdb.ui.root/current-sv]
                                                                [:component/id :sitevisit-editor :sitevisit])
                                        :post-mutation        `form-ready
                                        :post-mutation-params {:route-target   editor-ident
                                                               :form-target    sv-ident
                                                               :form-class-key (comp/class->registry-key SiteVisitForm)}}))))))
   :will-leave            (fn [this props]
                            (debug "WILL LEAVE EDITOR")
                            (dr/route-immediate (comp/get-ident this)))


   ;:componentDidMount (fn [this]
   ;                     (let [props (comp/props this)]
   ;                       (preload-options :entity.ns/constituentlookup)
   ;                       (fm/set-value! this :ui/ready true)))

   :componentDidMount     (fn [this]


                            (let [props  (comp/props this)
                                  {:riverdb.ui.root/keys [current-agency current-project]
                                   :keys                 [sitevisit]} props
                                  {:sitevisit/keys [AgencyCode ProjectID]} sitevisit
                                  projID (:db/id current-project)
                                  agID   (:db/id current-agency)
                                  _      (log/debug "SITEVISIT EDITOR DID MOUNT" "AGENCY" AgencyCode "PROJECT" ProjectID "projID" projID "agID" agID)]

                              (preload-options :entity.ns/person {:limit  -1 :filter {:person/Agency agID}})
                              ;(preload-options :entity.ns/sitevisittype)
                              ;(preload-options :entity.ns/stationfaillookup)
                              ;(preload-options :entity.ns/samplingdevicelookup)
                              ;(preload-options :entity.ns/stationlookup {:filter {:stationlookup/Project projID}})))
                              ;
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
                              ;  (f/load! this :org.riverdb.db.person looks/person
                              ;    {:params               {:limit  -1
                              ;                            :filter {:person/Agency agID}}
                              ;     :parallel             true
                              ;     :post-mutation        `sort-load->select-options
                              ;     :post-mutation-params {:target        [:org.riverdb.db.person/gid]
                              ;                            :sort-fn       (fn [[_ m]]
                              ;                                             (:person/Name m))
                              ;                            :select-target [:component/id :sitevisit-editor :ui/person-options]
                              ;                            :text-fn       :person/Name}})
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
                                                          :text-fn       :stationfaillookup/FailureReason}})
                                (f/load! this :org.riverdb.db.samplingdevicelookup looks/samplingdevicelookup
                                  {:params               {:limit -1 :filter {:samplingdevicelookup/Active true}}
                                   :parallel             true
                                   :post-mutation        `sort-load->select-options
                                   :post-mutation-params {:target        [:org.riverdb.db.samplingdevicelookup/gid]
                                                          :sort-fn       (fn [[_ m]]
                                                                           (:samplingdevicelookup/SampleDevice m))
                                                          :select-target [:component/id :sitevisit-editor :ui/samplingdevicelookup-options]
                                                          :text-fn       :samplingdevicelookup/SampleDevice}}))))
   :css                   [[:.floating-menu {:position "absolute !important"
                                             :z-index  1000
                                             :width    "300px"
                                             :right    "0px"
                                             :top      "50px"}
                            :.ui.segment {:padding ".5em"}
                            :.ui.raised.segment {:padding ".3em"}
                            :.ui.table {:margin-top ".3em"}]]}
  (let [parameters (get-in current-project [:projectslookup/Parameters])]
    ;(debug "RENDER SiteVisitsEditor" "CURRENT-PROJECT" current-project)
    (div {}
      (ui-sv-form (comp/computed sitevisit {:station-options              station-options
                                            :person-options               person-options
                                            :sitevisittype-options        sitevisittype-options
                                            :stationfaillookup-options    stationfaillookup-options
                                            :samplingdevicelookup-options samplingdevicelookup-options
                                            :current-project              current-project
                                            :parameters                   parameters})))))


;  (dom/input {:value name :onBlur #(comp/transact! this `[(fs/mark-complete! ...)]) ...})
;  (when (fs/invalid-spec? props :person/name)
;   (dom/span "Invalid username!"
;    ...)))


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

;[:Pagination {:activePage "{activePage}" :boundaryRange "{boundaryRange}" :onPageChange "{this.handlePaginationChange}" :size "mini" :siblingRange "{siblingRange}" :totalPages "{totalPages}"}]

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