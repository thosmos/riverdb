(ns riverdb.ui.sitevisits-page
  (:require
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li a p h2 h3 button table thead
                                                tbody th tr td form label input select
                                                option span]]
    [com.fulcrologic.fulcro.mutations :as fm]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as uism :refer [defstatemachine]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-dropdown :refer [ui-form-dropdown]]
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

(defsc FieldResult [this {:keys [] :as props}]
  {:query [:db/id :fieldresult/FieldResultRowID
           :fieldresult/Result :fieldresult/FieldReplicate
           {:fieldresult/ConstituentRowID
            [:constituentlookup/Name]}]})

(defsc FieldObsResult [this {:keys [] :as props}]
  {:query [:db/id :fieldobsresult/FieldObsResultRowID
           :fieldobsresult/IntResult
           :fieldobsresult/TextResult
           {:fieldobsresult/ConstituentRowID
            [:constituentlookup/Name]}]})

(defsc LabResult [this {:keys [] :as props}]
  {:query [:db/id :labresult/LabResultRowID
           :labresult/Result
           :labresult/ResQualCode
           :labresult/AnalysisDate
           :labresult/Basis
           :labresult/LabReplicate
           :labresult/SigFig
           {:labresult/ConstituentRowID
            [:constituentlookup/Name]}]})

(defsc Sample [this {:keys [] :as props}]
  {:query [:db/id :sample/SampleRowID :sample/SampleReplicate :sample/Unit
           {:sample/FieldResults (comp/get-query FieldResult)}
           {:sample/FieldObsResults (comp/get-query FieldObsResult)}
           {:sample/LabResults (comp/get-query LabResult)}
           {:sample/EventType [:db/id :eventtypelookup/EventType]}]
   :initial-state
          (fn [p]
            {:db/id (tempid/tempid)})})

(defsc SiteVisit [this props]
  {:ident         [:org.riverdb.db.sitevisit/gid :db/id]
   :query         [:db/id
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
                   {:sitevisit/Samples (comp/get-query Sample)}]

   :initial-state (fn [params] {:db/id                       (make-tempid)
                                :riverdb.entity/ns           :entity.ns/sitevisit
                                :sitevisit/CreationTimestamp (js/Date.)
                                :sitevisit/Visitors          []
                                :sitevisit/Samples           []
                                :sitevisit/VisitType         {:db/id :sitevisittype/Monthly}
                                :sitevisit/StationFailCode   {:db/id :stationfaillookup/NotRecorded}})})

;(comp/get-initial-state Sample {:type :field})
;(comp/get-initial-state Sample {:type :obs})
;(comp/get-initial-state Sample {:type :lab})

(defn set-value! [this k value]
  (fm/set-value! this k value)
  (transact! this `[(fs/mark-complete! nil k)]))

(defn set-ref! [this k id]
  (set-value! this k {:db/id id})
  (transact! this `[(fs/mark-complete! nil k)]))

(defn set-refs! [this k ids]
  (set-value! this k (mapv #(identity {:db/id %}) ids))
  (transact! this `[(fs/mark-complete! nil k)]))

(defsc SiteVisitForm [this
                      {:db/keys [id]
                       :sitevisit/keys [StationID DataEntryDate SiteVisitDate Time
                                        Visitors VisitType StationFailCode
                                        DataEntryPersonRef CheckPersonRef
                                        QAPersonRef QACheck QADate] :as props}
                      {:keys [station-options person-options sitevisittype-options
                              stationfaillookup-options samplingdevicelookup-options] :as cprops}]
  {:ident         [:org.riverdb.db.sitevisit/gid :db/id]
   :query         (fn [] (into [fs/form-config-join] (comp/get-query SiteVisit)))
   :initial-state (fn [params] (fs/add-form-config SiteVisitForm (comp/get-initial-state SiteVisit)))
   :form-fields   #{:sitevisit/StationID :sitevisit/DataEntryDate :sitevisit/SiteVisitDate :sitevisit/Time
                    :sitevisit/Visitors :sitevisit/VisitType :sitevisit/StationFailCode
                    :sitevisit/DataEntryPersonRef :sitevisit/CheckPersonRef
                    :sitevisit/QAPersonRef :sitevisit/QACheck :sitevisit/QADate}}
  (let [isLoading (not (and station-options person-options sitevisittype-options stationfaillookup-options samplingdevicelookup-options))]
    (debug "RENDER Site Visit Form " id) ;" props " props "computed" cprops)
    (div :.dimmable.fields {:key "sv-form"}
      (ui-dimmer {:inverted true :active isLoading}
        (ui-loader {:indeterminate true}))
      (ui-form {:key "form" :size "tiny"}
        (div :.dimmable.fields {:key "lookups"}
          (div :.field {:key "people"}
            (label {:style {}} "Monitors")
            (when person-options
              (ui-form-dropdown {:search       true
                                 :selection    true
                                 :multiple     true
                                 :value        (mapv :db/id Visitors)
                                 :options      person-options
                                 :autoComplete "off"
                                 :onChange     (fn [_ d]
                                                 (when-let [value (-> d .-value)]
                                                   (log/debug "people change" value)
                                                   (set-refs! this :sitevisit/Visitors value)))})))
          (div :.field {:key "site"}
            (label {:style {}} "Station ID")
            (when station-options
              (ui-form-dropdown {:search       true
                                 :selection    true
                                 :autoComplete "off"
                                 :value        (:db/id StationID)
                                 :options      station-options
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
                                 :onChange  (fn [_ d]
                                              (when-let [value (-> d .-value)]
                                                ;(log/debug "site change" value)
                                                (set-ref! this :sitevisit/StationFailCode value)))}))))


        (div :.dimmable.fields {:key "dates"}
          (div :.field {:key "svdate"}
            (label {:style {}} "Site Visit Date")
            (ui-datepicker {:selected SiteVisitDate
                            :onChange #(when (inst? %)
                                         (log/debug "date change" %)
                                         (set-value! this :sitevisit/SiteVisitDate %))}))
          (div :.field {:key "svtime"}
            (label {:style {}} "Time")
            (ui-datepicker {:selected           (js/Date.parse Time)
                            :showTimeSelect     true
                            :showTimeSelectOnly true
                            :timeIntervals      15
                            :timeCaption        "Time"
                            :dateFormat         "h:mm aa"
                            :onChange           #(do
                                                   (log/debug "Time change" %)
                                                   (set-value! this :sitevisit/Time (str %)))})))

        (div :.dimmable.fields {:key "entry"}
          (div :.field {:key "enterer"}
            (label {:style {}} "Entered By")
            (when person-options
              (ui-form-dropdown {:search       true
                                 :selection    true
                                 :value        (:db/id DataEntryPersonRef)
                                 :options      person-options
                                 :autoComplete "off"
                                 :onChange     (fn [_ d]
                                                 (when-let [value (-> d .-value)]
                                                   (log/debug "people change" value)
                                                   (set-ref! this :sitevisit/DataEntryPersonRef value)))})))
          (div :.field {:key "dedate"}
            (label {:style {}} "Data Entry Date")
            (do
              (debug "RENDER DataEntryDate" DataEntryDate)
              (ui-datepicker {:selected DataEntryDate
                              :onChange #(when (inst? %)
                                           (log/debug "date change" %)
                                           (set-value! this :sitevisit/DataEntryDate %))})))
          (div :.field {:key "checker"}
            (label {:style {}} "Checked By")
            (when person-options
              (ui-form-dropdown {:search       true
                                 :selection    true
                                 :value        (:db/id CheckPersonRef)
                                 :options      person-options
                                 :autoComplete "off"
                                 :onChange     (fn [_ d]
                                                 (when-let [value (-> d .-value)]
                                                   (log/debug "people change" value)
                                                   (set-ref! this :sitevisit/CheckPersonRef value)))}))))
        (div :.dimmable.fields {:key "qa"}
          (div :.field {:key "qcer"}
            (label {:style {}} "QA'd By")
            (when person-options
              (ui-form-dropdown {:search       true
                                 :selection    true
                                 :value        (:db/id QAPersonRef)
                                 :options      person-options
                                 :autoComplete "off"
                                 :onChange     (fn [_ d]
                                                 (when-let [value (-> d .-value)]
                                                   (log/debug "people change" value)
                                                   (set-ref! this :sitevisit/QAPersonRef value)))})))
          (div :.field {:key "qadate"}
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
                          :onChange  #(let [value (not QACheck)]
                                        (when (and QADate QAPersonRef)
                                          (log/debug "publish change" value)
                                          (set-value! this :sitevisit/QACheck value)))})))

        (table :.ui.very.compact.mini.table {:key "wqtab"}
          (thead {:key 1}
            (tr {}
              (th {:key 1} "Param")
              (th {:key 2} "Device")
              (th {:key 3} "Inst ID")
              (th {:key 4} "Units")
              (th {:key 5} "Test 1")
              (th {:key 6} "Test 2")
              (th {:key 7} "Test 3")
              (th {:key 8} "Time")
              (th {:key 9} "# Tests")
              (th {:key 10} "Mean")
              (th {:key 11} "Std Dev")
              (th {:key 12} "% Prec")))
          (tbody {:key 2}


            ;;; Air Temp
            (tr
              (td {:key 1} "Air Temp")
              (td {:key 2}
                (ui-form-dropdown {:search       true
                                   :selection    true
                                   :tabIndex     -1
                                   :value        "17592186046952"
                                   :options      samplingdevicelookup-options
                                   :autoComplete "off"
                                   :style        {:width "100px"}}))
              ;:onChange     (fn [_ d]
              ;                (when-let [value (-> d .-value)]
              ;                  (set-ref! this :sitevisit/QAPerson value)))}))
              (td {:key 3} (dom/input {:type "text" :value "57" :name "instID" :style {:width "50px"}}))
              (td {:key 4} "°C")
              (td {:key 5} (dom/input {:type "text" :value "25.5" :style {:width "50px"}}))
              (td {:key 6} (dom/input {:type "text" :value "" :style {:width "50px"}}))
              (td {:key 7} (dom/input {:type "text" :value "" :style {:width "50px"}}))
              (td {:key 8} (dom/input {:type "text" :value "10:37a" :style {:width "80px"}}))
              (td {:key 9} "1")
              (td {:key 10} "25.50")
              (td {:key 11} "0.00")
              (td {:key 12} "0.00"))


            ;;; H2O Temp
            (tr
              (td {:key 1} "H2O Temp")
              (td {:key 2}
                (ui-form-dropdown {:search       true
                                   :selection    true
                                   :tabIndex     -1
                                   :value        "17592186046953"
                                   :options      samplingdevicelookup-options
                                   :autoComplete "off"
                                   :style        {:width "100px"}}))
              ;:onChange     (fn [_ d]
              ;                (when-let [value (-> d .-value)]
              ;                  (set-ref! this :sitevisit/QAPerson value)))}))
              (td {:key 3} (dom/input {:type "text" :value "7" :name "instID" :style {:width "50px"}}))
              (td {:key 4} "°C")
              (td {:key 5} (dom/input {:type "text" :value "8.6" :style {:width "50px"}}))
              (td {:key 6} (dom/input {:type "text" :value "8.5" :style {:width "50px"}}))
              (td {:key 7} (dom/input {:type "text" :value "8.5" :style {:width "50px"}}))
              (td {:key 8} (dom/input {:type "text" :value "10:38a" :style {:width "80px"}}))
              (td {:key 9} "3")
              (td {:key 10} "8.53")
              (td {:key 11} "0.04")
              (td {:key 12} "0.55"))


            ;;; Cond
            (tr
              (td {:key 1} "Conductivity")
              (td {:key 2}
                (ui-form-dropdown {:search       true
                                   :selection    true
                                   :tabIndex     -1
                                   :value        "17592186046962"
                                   :options      samplingdevicelookup-options
                                   :autoComplete "off"
                                   :style        {:width "100px"}}))
              ;:onChange     (fn [_ d]
              ;                (when-let [value (-> d .-value)]
              ;                  (set-ref! this :sitevisit/QAPerson value)))}))
              (td {:key 3} (dom/input {:type "text" :value "7" :name "instID" :style {:width "50px"}}))
              (td {:key 4} "°C")
              (td {:key 5} (dom/input {:type "text" :value "40" :style {:width "50px"}}))
              (td {:key 6} (dom/input {:type "text" :value "40" :style {:width "50px"}}))
              (td {:key 7} (dom/input {:type "text" :value "40" :style {:width "50px"}}))
              (td {:key 8} (dom/input {:type "text" :value "10:38a" :style {:width "80px"}}))
              (td {:key 9} "3")
              (td {:key 10} "40.0")
              (td {:key 11} "0.00")
              (td {:key 12} "0.00"))


            ;; DO
            (tr
              (td {:key 1} "Dissolved Oxygen")
              (td {:key 2}
                (ui-form-dropdown {:search       true
                                   :selection    true
                                   :tabIndex     -1
                                   :value        "17592186046955"
                                   :options      samplingdevicelookup-options
                                   :autoComplete "off"
                                   :style        {:width "100px"}}))
              ;:onChange     (fn [_ d]
              ;                (when-let [value (-> d .-value)]
              ;                  (set-ref! this :sitevisit/QAPerson value)))}))
              (td {:key 3} (dom/input {:type "text" :value "80" :name "instID" :style {:width "50px"}}))
              (td {:key 4} "°C")
              (td {:key 5} (dom/input {:type "text" :value "8.7" :style {:width "50px"}}))
              (td {:key 6} (dom/input {:type "text" :value "8.6" :style {:width "50px"}}))
              (td {:key 7} (dom/input {:type "text" :value "8.6" :style {:width "50px"}}))
              (td {:key 8} (dom/input {:type "text" :value "10:39a" :style {:width "80px"}}))
              (td {:key 9} "3")
              (td {:key 10} "8.63")
              (td {:key 11} "0.04")
              (td {:key 12} "0.54"))


            ;;; pH
            (tr
              (td {:key 1} "pH")
              (td {:key 2}
                (ui-form-dropdown {:search       true
                                   :selection    true
                                   :tabIndex     -1
                                   :value        "17592186046953"
                                   :options      samplingdevicelookup-options
                                   :autoComplete "off"
                                   :style        {:width "100px"}}))
              ;:onChange     (fn [_ d]
              ;                (when-let [value (-> d .-value)]
              ;                  (set-ref! this :sitevisit/QAPerson value)))}))
              (td {:key 3} (dom/input {:type "text" :value "142" :name "instID" :style {:width "50px"}}))
              (td {:key 4} "°C")
              (td {:key 5} (dom/input {:type "text" :value "6.9" :style {:width "50px"}}))
              (td {:key 6} (dom/input {:type "text" :value "6.9" :style {:width "50px"}}))
              (td {:key 7} (dom/input {:type "text" :value "7.0" :style {:width "50px"}}))
              (td {:key 8} (dom/input {:type "text" :value "10:40a" :style {:width "80px"}}))
              (td {:key 9} "3")
              (td {:key 10} "6.93")
              (td {:key 11} "0.04")
              (td {:key 12} "0.67"))


            ;;; Turbidity
            (tr
              (td {:key 1} "Turbidity")
              (td {:key 2}
                (ui-dropdown {:search       true
                              :selection    true
                              :tabIndex     -1
                              :value        "17592186046957"
                              :options      samplingdevicelookup-options
                              :autoComplete "off"
                              :style        {:width "100px"}}))
              ;:onChange     (fn [_ d]
              ;                (when-let [value (-> d .-value)]
              ;                  (set-ref! this :sitevisit/QAPerson value)))}))
              (td {:key 3} (dom/input {:type "text" :value "" :name "instID" :style {:width "50px"}}))
              (td {:key 4} "°C")
              (td {:key 5} (dom/input {:type "text" :value "0.30" :style {:width "50px"}}))
              (td {:key 6} (dom/input {:type "text" :value "0.26" :style {:width "50px"}}))
              (td {:key 7} (dom/input {:type "text" :value "0.25" :style {:width "50px"}}))
              (td {:key 8} (dom/input {:type "text" :value "10:41a" :style {:width "80px"}}))
              (td {:key 9} "3")
              (td {:key 10} "6.93")
              (td {:key 11} "0.04")
              (td {:key 12} "0.67"))))





        (button :.ui.button {:type "submit"} "Save")))))

(def ui-sv-form (comp/factory SiteVisitForm {:keyfn :db/id}))

(fm/defmutation sort-load->select-options
  "sorts the load results, and then creates a seq that can be used as the options for a select dropdown"
  [{:keys [target select-target text-fn sort-fn]}]
  (action [{:keys [state]}]
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

(defsc SiteVisitEditor [this {:keys [sitevisit]
                              :riverdb.ui.root/keys [current-agency current-project]
                              :ui/keys [station-options person-options sitevisittype-options
                                        stationfaillookup-options samplingdevicelookup-options] :as props}]
  {:ident             (fn [] [:component/id :sitevisit-editor])
   :query             [:ui/station-options
                       :ui/person-options
                       :ui/sitevisittype-options
                       :ui/stationfaillookup-options
                       :ui/samplingdevicelookup-options
                       {[:riverdb.ui.root/current-agency '_] (comp/get-query looks/agencylookup-sum)}
                       {[:riverdb.ui.root/current-project '_] (comp/get-query looks/projectslookup-sum)}
                       {:sitevisit (comp/get-query SiteVisitForm)}]
   :initial-state     {:ui/station-options              nil
                       :ui/person-options               nil
                       :ui/sitevisittype-options        nil
                       :ui/stationfaillookup-options    nil
                       :ui/samplingdevicelookup-options nil}
   :route-segment     ["edit" :sv-id]
   :will-enter        (fn [app {:keys [sv-id] :as params}]
                        (let [is-new?    (clojure.string/starts-with? sv-id "t")
                              sv-id      (if is-new?
                                           (tempid/tempid sv-id)
                                           sv-id)
                              sv-ident   [:org.riverdb.db.sitevisit/gid sv-id]
                              edit-ident [:component/id :sitevisit-editor]]
                          (log/debug "WILL ENTER" sv-id params)
                          (if is-new?
                            (dr/route-immediate edit-ident)
                            (dr/route-deferred edit-ident
                              #(f/load app sv-ident looks/sitevisit
                                 {:target               [:component/id :sitevisit-editor :sitevisit]
                                  :post-mutation        `dr/target-ready
                                  :post-mutation-params {:target edit-ident}})))))
   :will-leave        (fn [this props]
                        (debug "WILL LEAVE EDITOR")
                        (dr/route-immediate (comp/get-ident this)))


   :componentDidMount (fn [this]
                        (let [props  (comp/props this)
                              {:riverdb.ui.root/keys [current-agency current-project]
                               :keys                 [sitevisit]} props
                              {:sitevisit/keys [AgencyCode ProjectID]} sitevisit
                              projID (:db/id current-project)
                              agID   (:db/id current-agency)
                              _      (log/debug "SITEVISIT EDITOR DID MOUNT" "AGENCY" AgencyCode "PROJECT" ProjectID "projID" projID "agID" agID)]

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
                            (f/load! this :org.riverdb.db.person looks/person
                              {:params               {:limit  -1
                                                      :filter {:person/Agency agID}}
                               :parallel             true
                               :post-mutation        `sort-load->select-options
                               :post-mutation-params {:target        [:org.riverdb.db.person/gid]
                                                      :sort-fn       (fn [[_ m]]
                                                                       (:person/LName m))
                                                      :select-target [:component/id :sitevisit-editor :ui/person-options]
                                                      :text-fn       (fn [{:person/keys [LName FName]}]
                                                                       (str LName ", " FName))
                                                      :next-mutation `filter-staff
                                                      :next-mutation-params
                                                                     {:target [:component/id :sitevisit-editor :ui/staff-options]}}})
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
   :css               [[:.floating-menu {:position "absolute !important"
                                         :z-index  1000
                                         :width    "300px"
                                         :right    "0px"
                                         :top      "50px"}
                        :.ui.segment {:padding ".5em"}
                        :.ui.raised.segment {:padding ".3em"}
                        :.ui.table {:margin-top ".3em"}]]}
  (div {}
    (ui-sv-form (comp/computed sitevisit {:station-options              station-options
                                          :person-options               person-options
                                          :sitevisittype-options        sitevisittype-options
                                          :stationfaillookup-options    stationfaillookup-options
                                          :samplingdevicelookup-options samplingdevicelookup-options}))))


;  (dom/input {:value name :onBlur #(comp/transact! this `[(fs/mark-complete! ...)]) ...})
;  (when (fs/invalid-spec? props :person/name)
;   (dom/span "Invalid username!"
;    ...)))


(defsc SiteVisitSummary [this {:keys [db/id sitevisit/SiteVisitDate sitevisit/VisitType] :as props}]
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
        goto-fn #(routes/route-to! (str "/sitevisit/edit/" id))]
    ;(log/debug "RENDER SV SUM" id type SiteVisitDate)
    (tr {:key id :style {:cursor "pointer"} :onClick goto-fn} ;:onMouseOver #(println "HOVER" id)}
      (td {:key 1} (str SiteVisitDate))
      (td {:key 2} type)
      (td {:key 3 :padding 2}
        (button :.ui.primary.basic.icon.button
          {:style {:padding 5} :onClick goto-fn}
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
  (f/load! this :org.riverdb.db.stationlookup looks/stationlookup-sum
    {:target               [:component/id :sitevisit-list :sites]
     :params               {:limit -1 :filter {:stationlookup/Agency {:agencylookup/AgencyCode agencyCode}}}
     :post-mutation        `rm/sort-ident-list-by
     :post-mutation-params {:idents-path [:component/id :sitevisit-list :sites]
                            :ident-key   :org.riverdb.db.stationlookup/gid
                            :sort-fn     :stationlookup/StationID}}))

(defn make-filter
  ([props]
   (let [{:riverdb.ui.root/keys [current-agency current-project current-year]
          :ui/keys [site]} props
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
  [this {:keys    [sitevisits sites project-years]
         :riverdb.ui.root/keys [current-agency current-project current-year]
         :ui/keys [site sort limit offset list-meta] :as props}]
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
                         (debug "SV LIST :componentDidMount RUN QUERY")
                         (let [{:ui/keys              [limit offset sort] :as props
                                :riverdb.ui.root/keys [current-agency]} (comp/props this)
                               filter (make-filter props)
                               {:agencylookup/keys [AgencyCode]} current-agency]
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
        onCreate               (fn []
                                 (let [new-sv (comp/get-initial-state SiteVisitForm)
                                       id     (:db/id new-sv)
                                       id-str (.-id id)]
                                   (merge/merge-component! this SiteVisitForm new-sv
                                     :replace [:riverdb.ui.root/current-sv]
                                     :replace [:component/id :sitevisit-editor :sitevisit])
                                   (routes/route-to! (str "/sitevisit/edit/" id-str))))]
    (div {}
      (div :#sv-list-menu.ui.menu {:key "title"}
        (div :.item {:style {}} "Site Visits")
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
          (button {:key "create" :onClick onCreate} "New"))

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
                (ui-sv-summary sv)))))))))

;[:Pagination {:activePage "{activePage}" :boundaryRange "{boundaryRange}" :onPageChange "{this.handlePaginationChange}" :size "mini" :siblingRange "{siblingRange}" :totalPages "{totalPages}"}]

(dr/defrouter SVRouter [this props]
  {:router-targets [SiteVisitList SiteVisitEditor]})

(def ui-sv-router (comp/factory SVRouter))


(defsc SiteVisitsPage [this {:keys [project-years svrouter] :as props}]
  {:ident         (fn [] [:component/id :sitevisits])
   :query         [{:project-years (comp/get-query ProjectYears)}
                   {:svrouter (comp/get-query SVRouter)}]
   :initial-state {:svrouter      {}
                   :project-years {}}
   :route-segment ["sitevisit"]}

  (let [{:keys [ui/ui-year ui/ui-project agency-project-years]} project-years
        proj          (get agency-project-years (keyword ui-project))
        agency        (:agency proj)
        project-name  (:name proj)
        current-route (dr/current-route this this)]
    #_(debug "RENDER SiteVisitsPage")
    (div :.ui.container {}
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