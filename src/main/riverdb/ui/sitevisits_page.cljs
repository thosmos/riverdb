(ns riverdb.ui.sitevisits-page
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]
    [clojure.data]
    [cognitect.transit :as transit]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.application :as fapp :refer [current-state]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li a p h2 h3 button table thead
                                                tbody th tr td form label input option span]]
    [com.fulcrologic.fulcro.mutations :as fm]
    [com.fulcrologic.fulcro.networking.file-upload :as file-upload]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as uism :refer [defstatemachine]]
    [com.fulcrologic.rad.routing :as rroute]
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
    [goog.object :as gobj]
    [riverdb.application :refer [SPA]]
    [riverdb.api.mutations :as rm]
    [riverdb.model.sitevisit :as rmsv]
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
    [riverdb.ui.util :as rutil :refer [walk-ident-refs* walk-ident-refs make-tempid make-validator parse-float rui-checkbox rui-int rui-bigdec rui-bigdec-input rui-input ui-cancel-save set-editing set-value set-value!! set-refs! set-ref! set-ref set-refs get-ref-set-val lookup-db-ident filter-param-typecode]]
    [riverdb.util :refer [paginate nest-by filter-sample-typecode]]
    [com.rpl.specter :as sp :refer [select ALL LAST]]
    [theta.log :as log :refer [debug info]]
    [thosmos.util :as tu]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [riverdb.roles :as roles]
    [edn-query-language.core :as eql]
    [tick.alpha.api :as t]
    [tick.timezone]
    [tick.locale-en-us]
    [testdouble.cljs.csv :as csv]
    [com.fulcrologic.fulcro.application :as app]))

(declare SVRouter)
(declare SiteVisitList)

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

(defsc Monitor [this props]
  {:ident [:org.riverdb.db.person/gid :db/id]
   :query [:db/id
           :riverdb.entity/ns
           fs/form-config-join
           :person/Name
           :person/FName
           :person/LName]
   :form-fields   #{:db/id}})

(defsc SV [this props]
  {:ident [:org.riverdb.db.sitevisit/gid :db/id]
   :query [:db/id
           fs/form-config-join]
   :form-fields   #{:db/id}})

(defsc WorkTime [this {:worktime/keys [hours person] :as props}]
  {:ident         [:org.riverdb.db.worktime/gid :db/id]
   :query         [:db/id
                   :riverdb.entity/ns
                   fs/form-config-join
                   :worktime/sitevisit
                   {:worktime/person (comp/get-query Monitor)}
                   :worktime/hours
                   :worktime/date
                   :worktime/uuid
                   :worktime/task
                   :worktime/agency]
   :initial-state (fn [{:keys [person sitevisit agency date] :as params}]
                    {:db/id              (tempid/tempid)
                     :riverdb.entity/ns  :entity.ns/worktime
                     :worktime/uuid      (tempid/uuid)
                     :worktime/sitevisit sitevisit
                     :worktime/person    person
                     :worktime/date      date
                     :worktime/hours     (transit/bigdec "1")
                     :worktime/task      "sitevisit"
                     :worktime/agency    agency})
   :form-fields   #{:db/id
                    :riverdb.entity/ns
                    :worktime/uuid
                    :worktime/hours
                    :worktime/sitevisit
                    :worktime/person
                    :worktime/task
                    :worktime/date
                    :worktime/agency}}

  (let [name (:person/Name person)]
    (dom/tr
      (dom/td name)
      (dom/td
        (rui-bigdec-input
          {:value    hours
           :style    {:width "5rem"}
           :onChange (fn [new-v]
                       (set-value!! this :worktime/hours new-v))})))))

(def ui-worktime-item (comp/factory WorkTime {:keyfn :db/id}))

(defsc WorkTimeList [this props]
  {:query [:visitors :worktimes]}
  (let [worktimes (:worktimes props)]
    (dom/table :.ui.table
      (dom/thead
        (dom/tr
          (dom/th "Monitor")
          (dom/th "Hours")))
      (dom/tbody
        (for [worktime worktimes]
          (ui-worktime-item worktime))))))

(def ui-worktime-list
  (comp/factory WorkTimeList))

(fm/defmutation sv-deleted [{:keys [ident]}]
  (action [{:keys [state]}]
    (debug "SV deleted" ident)
    (rroute/route-to! SPA SiteVisitList {})))

(fm/defmutation add-worktime [{:keys [sitevisit] :as args}]
  (action [{:keys [state]}]
    (debug "ADD worktime")
    (let [wt (comp/get-initial-state WorkTime args)
          wt (fs/add-form-config WorkTime wt)]
      (debug "New worktime" wt)
      (swap! state
        (fn [st]
          (-> st
            (merge/merge-component WorkTime wt
              :append (conj sitevisit :sitevisit/WorkTimes))))))))

(fm/defmutation del-worktime [{:keys [sitevisit worktime]}]
  (action [{:keys [state]}]
    (debug "DEL worktime" worktime)
    (let []
      (swap! state
        (fn [st]
          (-> st
            (update-in (conj sitevisit :sitevisit/WorkTimes)
              (fn [wktms]
                (vec (remove #{worktime} wktms))))))))))

;(comp/transact! this `[(rm/save-entity ~{:ident         this-ident
;                                         :delete        true
;                                         :post-mutation `sv-deleted
;                                         :post-params   {}})])

(defn sync-worktimes [this visitors worktimes]
  (let [props          (comp/props this)
        sv-id          (:db/id props)
        ag-id          (:sitevisit/AgencyCode props)
        sv-date        (:sitevisit/SiteVisitDate props)
        visitor-ids    (set (select [ALL LAST] visitors))
        worker-ids     (set (select [ALL :worktime/person :db/id] worktimes))
        add-workers    (clojure.set/difference visitor-ids worker-ids)
        delete-workers (clojure.set/difference worker-ids visitor-ids)]
    (debug "Sync WorkTimes" "add-workers" add-workers "del-workers" delete-workers "props" props)
    ;; add new workers
    (doseq [add-id add-workers]
      (let [args {:person    {:db/id add-id}
                  :sitevisit [:org.riverdb.db.sitevisit/gid sv-id]
                  :agency    ag-id
                  :date      sv-date}]
        (comp/transact! this `[(add-worktime ~args)])))

    ;; remove missing workers
    (doseq [del-id delete-workers]
      (let [worktime (first (select [ALL #(= del-id (get-in % [:worktime/person :db/id])) ] worktimes))
            worktime-id (:db/id worktime)
            args {:worktime [:org.riverdb.db.worktime/gid worktime-id]
                  :sitevisit [:org.riverdb.db.sitevisit/gid sv-id]}]
        ;(debug "REMOVE WORKTIME" worktime-id)
        (comp/transact! this `[(del-worktime ~args)])))))

(fm/defmutation set-sv-date
  [{:keys [date]}]
  (action [{:keys [state component]}]
    (let [sv-ident (comp/get-ident component)
          wts (get-in @state (conj sv-ident :sitevisit/WorkTimes))]
      ;(debug "SET DATE" date sv-ident wts)
      (swap! state
        (fn [st]
          (let [st (rutil/set-value* st sv-ident :sitevisit/SiteVisitDate date)]
            (reduce
              (fn [st wt]
                (rutil/set-value* st wt :worktime/date date))
              st wts)))))))

(defsc SiteVisitForm [this
                      {:ui/keys [ready create globals add-person-modal error]
                       :db/keys [id]
                       :sitevisit/keys [CheckPersonRef DataEntryDate DataEntryPersonRef
                                        Notes QAPersonRef QACheck QADate
                                        Samples SiteVisitDate StationID StationFailCode
                                        Visitors VisitType WorkTimes] :as props}
                      {:keys [station-options person-options sitevisittype-options
                              stationfaillookup-options
                              current-project parameters current-agency person-lookup] :as cprops}]
  {:ident             [:org.riverdb.db.sitevisit/gid :db/id]
   :query             (fn [] [:db/id
                              :riverdb.entity/ns
                              fs/form-config-join
                              :ui/ready
                              :ui/create
                              :ui/error
                              :sitevisit/AgencyCode
                              ;:sitevisit/BacteriaCollected
                              ;:sitevisit/BacteriaTime
                              :sitevisit/CheckPersonRef
                              ;:sitevisit/CreationTimestamp
                              :sitevisit/DataEntryDate
                              :sitevisit/DataEntryNotes
                              :sitevisit/DataEntryPersonRef
                              :sitevisit/Datum
                              ;:sitevisit/DepthMeasured
                              ;:sitevisit/GPSDeviceCode
                              ;:sitevisit/HydroMod
                              ;:sitevisit/HydroModLoc
                              :sitevisit/Lat
                              :sitevisit/Lon
                              ;:sitevisit/MetalCollected
                              ;:sitevisit/MetalTime
                              :sitevisit/Notes
                              ;:sitevisit/PointID
                              :sitevisit/ProjectID
                              :sitevisit/QACheck
                              :sitevisit/QADate
                              :sitevisit/QAPersonRef
                              ;:sitevisit/SeasonCode
                              :sitevisit/SiteVisitDate
                              :sitevisit/SiteVisitID
                              :sitevisit/StationFailCode
                              :sitevisit/StationID
                              :sitevisit/StreamWidth
                              :sitevisit/Time
                              ;:sitevisit/TssCollected
                              ;:sitevisit/TssTime
                              ;:sitevisit/TurbidityCollected
                              ;:sitevisit/TurbidityTime
                              ;:sitevisit/UnitStreamWidth
                              ;:sitevisit/UnitWaterDepth
                              :sitevisit/VisitType
                              ;:sitevisit/WaterDepth
                              ;:sitevisit/WidthMeasured
                              :sitevisit/uuid
                              :sitevisit/Visitors
                              {:sitevisit/Samples (comp/get-query SampleForm)}
                              {:sitevisit/WorkTimes (comp/get-query WorkTime)}
                              {:ui/globals (comp/get-query Globals)}
                              [:riverdb.theta.options/ns '_]])

   :initial-state     (fn [{:keys [id agency project uuid VisitType StationFailCode] :as params}]
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

   :initLocalState    (fn [this props]
                        {:onChangeSample #(debug "ON CHANGE SAMPLE" %)})

   :form-fields       #{:riverdb.entity/ns
                        :sitevisit/uuid
                        :sitevisit/AgencyCode :sitevisit/ProjectID :sitevisit/Notes
                        :sitevisit/StationID :sitevisit/DataEntryDate :sitevisit/SiteVisitDate :sitevisit/Time
                        :sitevisit/Visitors :sitevisit/VisitType :sitevisit/StationFailCode
                        :sitevisit/DataEntryPersonRef :sitevisit/CheckPersonRef
                        :sitevisit/QAPersonRef :sitevisit/QACheck :sitevisit/QADate :sitevisit/Samples
                        :sitevisit/WorkTimes}

   :componentDidMount (fn [this]
                        ;(debug "DID MOUNT SiteVisitForm")
                        ;; start state machine
                        (uism/begin! this rmsv/sv-sm ::uism-sv {:actor/sv (comp/get-ident this)})
                        (let [{:ui/keys [ready create globals add-person-modal error]
                               :db/keys [id]
                               :sitevisit/keys [CheckPersonRef DataEntryDate DataEntryPersonRef
                                                Notes QAPersonRef QACheck QADate
                                                Samples SiteVisitDate StationID StationFailCode
                                                Visitors VisitType WorkTimes] :as props} (comp/props this)]
                          (sync-worktimes this Visitors WorkTimes)))}




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
        ;worktimes     (:worktime/_sitevisit props)
        ;_ (debug "monitor hours" WorkTimes)

        {person-options            :entity.ns/person
         stationlookup-options     :entity.ns/stationlookup
         sitevisittype-options     :entity.ns/sitevisittype
         stationfaillookup-options :entity.ns/stationfaillookup
         fieldobsvarlookup-options :entity.ns/fieldobsvarlookup} (:riverdb.theta.options/ns props)]

    ;(debug "RENDER SiteVisitForm" "props" props "params" active-params )
    (div :.dimmable.fields {:key "sv-form"}
      (ui-dimmer {:inverted true :active (or (not ready) (not props))}
        (ui-loader {:indeterminate true}))
      #_(button :.ui.button
          {:onClick (fn []
                      (sync-worktimes this Visitors WorkTimes)
                      (debug "CLICK")
                      #_(uism/trigger! this ::uism-sv :event/begin-edit))}
          "SYNC")
      (when props
        (ui-form {:key "form" :size "tiny"}
          (div :.ui.grid {:key "sv-fields"}
            (div :.eight.wide.column
              (div :.dimmable.fields

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

                (div :.field {:key "svdate" :style {:width 180}}
                  (label {} "Site Visit Date")
                  (ui-datepicker {:selected (or sv-date "")
                                  :onChange #(when (inst? %)
                                               (comp/transact! this [(set-sv-date {:date %})]))}))


                (ui-form-input
                  {:label    "Start Time"
                   :value    (or sv-time "")
                   :style    {:width 168}
                   :onChange #(do
                                (log/debug "Time change" (-> % .-target .-value))
                                (set-value!! this :sitevisit/Time (-> % .-target .-value)))}))









              (div :.dimmable.fields {:key "dates"}



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




              (div :.dimmable.fields {:key "entry"}
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

               (div :.field {:key "dedate" :style {:width 180}}
                 (label {:style {}} "Data Entry Date")
                 (do
                   ;(debug "RENDER DataEntryDate" DataEntryDate)
                   (ui-datepicker {:selected DataEntryDate
                                   :onChange #(when (inst? %)
                                                (log/debug "date change" %)
                                                (set-value!! this :sitevisit/DataEntryDate %))})))

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
                                       :k     :sitevisit/CheckPersonRef}}))))





              (div :.dimmable.fields {:key "qa"}



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
                  (label {:style {} :onClick #(set-value!! this :sitevisit/QADate nil)} "QA Date")
                  (ui-datepicker {:selected QADate
                                  :onChange #(when (inst? %)
                                               (log/debug "QADate change" %)
                                               (set-value!! this :sitevisit/QADate %))}))

                (div :.field {:key "publish"}
                  (label {:style {}} "Publish?")
                  (ui-checkbox {:size     "big" :fitted true :label "" :type "checkbox" :toggle true
                                :checked  (or QACheck false)
                                :onChange #(let [value (not QACheck)]
                                             (log/debug "publish change" value)
                                             (set-value!! this :sitevisit/QACheck value))}))))



            (div :.three.wide.column
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
                                      :k     :sitevisit/Visitors}
                     :onChange       (fn [ref-val]
                                       (debug "person list changed" ref-val)
                                       (sync-worktimes this ref-val WorkTimes))}))))

            (div :.three.wide.column
              (div :.field {:key "monitor hours"}
                (label {:style {}} "Monitor Hours")
                (ui-worktime-list
                  {:worktimes WorkTimes
                   :visitors  Visitors}))))



          (ui-form-text-area {:key      "sv-notes"
                              :label    "Notes"
                              :value    (or Notes "")
                              :onChange #(do
                                           (debug "CHANGED Notes" (-> % .-target .-value))
                                           (set-value!! this :sitevisit/Notes (-> % .-target .-value)))})



          (for [sample-type param-types]
            (let [params  (filter-param-typecode sample-type active-params)
                  samples (filter-sample-typecode sample-type Samples)]
              (ui-sample-list
                (comp/computed
                  {:sv-ident                 this-ident
                   :sample-type              sample-type
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

            (dom/button :.ui.right.floated.button.primary
              {:disabled (not dirty?)
               :onClick  #(let [dirty-fields  (fs/dirty-fields props true)
                                form-fields   (fs/get-form-fields SiteVisitForm)
                                form-props    (select-keys props form-fields)
                                worktimes     (:sitevisit/WorkTimes (-> dirty-fields first last))
                                del-worktimes (when worktimes
                                                (not-empty
                                                  (clojure.set/difference
                                                    (set (:before worktimes))
                                                    (set (:after worktimes)))))]
                            (debug "SAVE!" "dirty?" dirty? "dirty-fields" dirty-fields "form-props" form-props)
                            (debug "DIFF" (tu/ppstr dirty-fields))
                            (comp/transact! this
                              `[(rm/save-entity ~{:ident this-ident
                                                  :diff  dirty-fields})])
                            (debug "DEL WORKTIMES" del-worktimes)
                            (when del-worktimes
                              (doseq [delwt del-worktimes]
                                (comp/transact! this
                                  `[(rm/save-entity ~{:ident delwt
                                                      :delete true})]))))}
              "Save")

            #_(dom/button :.ui.button.primary
                {:disabled (not dirty?)
                 :onClick  #(let [dirty-fields  (fs/dirty-fields props true)
                                  form-fields   (fs/get-form-fields SiteVisitForm)
                                  form-props    (select-keys props form-fields)
                                  worktimes     (:sitevisit/WorkTimes (-> dirty-fields first last))
                                  del-worktimes (when worktimes
                                                  (not-empty
                                                    (clojure.set/difference
                                                      (set (:before worktimes))
                                                      (set (:after worktimes)))))]
                              (debug "SAVE!" "dirty?" dirty? "dirty-fields" dirty-fields "form-props" form-props)
                              (debug "DIFF" (tu/ppstr dirty-fields))
                              (debug "DEL WORKTIMES" del-worktimes))}
                "Dirty")

            (dom/button :.ui.negative.button
              {:disabled (tempid/tempid? (second this-ident))
               :onClick  #(comp/set-state! this {:confirm-delete true})}
              "Delete")

            (dom/button :.ui.right.floated.button.secondary
              {:onClick #(do
                           (debug "CANCEL!" dirty? (fs/dirty-fields props false))
                           (when dirty?
                             (comp/transact! this
                               `[(rm/reset-form {:ident ~this-ident})]))
                           (when (or (not dirty?) create)
                             (fm/set-value! this :ui/editing false)
                             (rroute/route-to! this SiteVisitList {})))}
              (if dirty?
                "Cancel"
                "Close"))))))))


(def ui-sv-form (comp/factory SiteVisitForm {:keyfn :db/id}))


(fm/defmutation sort-load->select-options
  "sorts the load results, and then creates a seq that can be used as the options for a select dropdown"
  [{:keys [target select-target text-fn sort-fn]}]
  (action [{:keys [state]}]
    ;(debug "sort-load->select-options" target select-target text-fn sort-fn)
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

(defn ref->gid
  "Sometimes references on the client are actual idents and sometimes they are
  nested maps, this function attempts to return an ident regardless."
  [x]
  ;(debug "ref->gid" x)
  (cond
    (eql/ident? x)
    (second x)
    (and (map? x) (:db/id x))
    (:db/id x)))

(fm/defmutation form-ready
  [{:keys [route-target form-ident]}]
  (action [{:keys [app state]}]
    ;(debug "MUTATION FORM READY" route-target form-ident)
    (let [st                 @state
          current-project-id (ref->gid (:ui.riverdb/current-project st))
          sv                 (get-in st form-ident)
          sv-project-id      (ref->gid (:sitevisit/ProjectID sv))
          same?              (= current-project-id sv-project-id)]
      ;(log/debug "CURRENT PROJECT" (pr-str current-project-id) "SV PROJECT" (pr-str sv-project-id) "same?" same?)
      (when-not same?
        ;(log/debug "NOT SAME, LOADING PROJECT" sv-project-id)
        (let [sv-proj (get-in st [:org.riverdb.db.projectslookup/gid sv-project-id])
              proj-k  (keyword (:projectslookup/ProjectID sv-proj))]
          ;(log/debug "SV PROJECT" proj-k)
          (comp/transact! app `[(rm/process-project-years {:proj-k ~proj-k})]))))
    (try
      (swap! state
        (fn [st]
          (-> st
            (rutil/convert-db-refs* form-ident)
            (fs/add-form-config* SiteVisitForm form-ident)
            (fs/entity->pristine* form-ident)
            (update-in form-ident assoc :ui/ready true))))
      (catch js/Object ex (debug "FORM LOAD FAILED" ex)))
    ;(debug "DONE SETTING UP SV FORM READY")
    (dr/target-ready! SPA route-target)))


(fm/defmutation merge-new-sv2
  [{:keys []}]
  (action [{:keys [state]}]
    (let [current-project (get @state :ui.riverdb/current-project)
          current-agency  (get @state :ui.riverdb/current-agency)
          sv              (comp/get-initial-state SiteVisitForm {:project current-project :agency current-agency})
          ;_               (debug "Merge SV!" sv current-project current-agency)
          sv              (walk-ident-refs @state sv)
          sv              (fs/add-form-config SiteVisitForm sv)]
      ;(debug "Merge new SV! do we have DB globals yet?" (get-in @state [:component/id :globals]))
      (swap! state
        (fn [st]
          (-> st
            ;; NOTE load the minimum so we can get globals after load
            (merge/merge-component SiteVisitForm sv
              :replace [:ui.riverdb/current-sv]
              :replace [:component/id :sitevisit-editor :sitevisit])))))))


(defsc ProjectInfo [this props]
  {:ident [:org.riverdb.db.projectslookup/gid :db/id]
   :query [:db/id
           :projectslookup/ProjectID
           :projectslookup/Name
           {:projectslookup/Parameters (comp/get-query Parameter)}]})


(defsc SiteVisitEditor [this {:keys                 [sitevisit session]
                              :ui.riverdb/keys [current-project current-agency]
                              :ui/keys              [ready] :as props}]
  {:ident             (fn [] [:component/id :sitevisit-editor])
   :initial-state     {:ui/ready false}
   :query             [:ui/ready
                       {:sitevisit (comp/get-query SiteVisitForm)}
                       {[:ui.riverdb/current-agency '_] (comp/get-query looks/agencylookup-sum)}
                       {[:ui.riverdb/current-project '_] (comp/get-query ProjectInfo)}
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
                          ;(debug "CHECK SESSION SiteVisitEditor" "valid?" valid? "admin?" admin? "result" result)
                          result))

   :will-enter        (fn [app {:keys [sv-id] :as params}]
                        (let [is-new?      (= sv-id "new")
                              ident-key    :org.riverdb.db.sitevisit/gid
                              editor-ident [:component/id :sitevisit-editor]]
                          ;(log/debug "WILL ENTER SiteVisitEditor" "NEW?" is-new? "PARAMS" params)
                          (if is-new?
                            (dr/route-deferred editor-ident
                              #(let [] ;(riverdb.ui.util/shortid)]]
                                 ;(log/debug "CREATING A NEW SITEVISIT")
                                 (comp/transact! app `[(merge-new-sv2)])
                                 (dr/target-ready! app editor-ident)))
                            (dr/route-deferred editor-ident
                              #(let [sv-ident [ident-key sv-id]]
                                 ;(log/debug "LOADING AN EXISTING SITEVISIT" sv-ident)
                                 (let [app-state       (app/current-state app)
                                       current-project (:ui.riverdb/current-project app-state)])
                                 ;(log/debug "SV PROJECT" current-project))

                                 (f/load! app sv-ident SiteVisitForm
                                   {:target               (targeting/multiple-targets
                                                            [:ui.riverdb/current-sv]
                                                            [:component/id :sitevisit-editor :sitevisit])
                                    :marker               ::sv
                                    :post-mutation        `form-ready
                                    :post-mutation-params {:route-target editor-ident
                                                           :form-ident   sv-ident}
                                    :without              #{[:riverdb.theta.options/ns '_]}}))))))
   :will-leave        (fn [this props]
                        ;(debug "WILL LEAVE EDITOR")
                        (dr/route-immediate [:component/id :sitevisit-editor]))

   :componentDidMount (fn [this]
                        (let [props  (comp/props this)
                              {:keys [sitevisit]} props
                              {:sitevisit/keys [AgencyCode ProjectID]} sitevisit
                              projID (if (eql/ident? ProjectID) (second ProjectID) (:db/id ProjectID))
                              agID   (if (eql/ident? AgencyCode) (second AgencyCode) (:db/id AgencyCode))]
                          ;_      (log/debug "DID MOUNT SiteVisitEditor" "AGENCY" AgencyCode "PROJECT" ProjectID "projID" projID "agID" agID)]

                          (preload-agency agID)

                          (preload-options :entity.ns/person {:query-params {:filter {:person/Agency agID}}})
                          (preload-options :entity.ns/stationlookup
                            {:query-params {:filter {:projectslookup/Stations {:reverse projID}}}
                             :sort-key     :stationlookup/StationName
                             :text-fn      {:keys #{:stationlookup/StationID :stationlookup/StationName}
                                            :fn   (fn [{:stationlookup/keys [StationID StationName]}]
                                                    (str StationID ": " StationName))}})
                          (preload-options :entity.ns/sitevisittype)
                          (preload-options :entity.ns/stationfaillookup)
                          (preload-options :entity.ns/samplingdevice {:filter-key :samplingdevice/DeviceType})
                          (preload-options :entity.ns/samplingdevicelookup)
                          (preload-options :entity.ns/resquallookup
                            {:text-fn      {:keys #{:resquallookup/ResQualCode :resquallookup/ResQualifier}
                                            :fn   (fn [{:resquallookup/keys [ResQualCode ResQualifier]}]
                                                    (str ResQualCode ": " ResQualifier))}})
                          (preload-options :entity.ns/fieldobsvarlookup
                            {:filter-key :fieldobsvarlookup/Analyte
                             :sort-key   :fieldobsvarlookup/IntCode})))

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
    ;(debug "RENDER SiteVisitsEditor")
    (div {}
      #_(button
          {:onClick
           (fn []
             (debug "CLICK")
             (uism/trigger! this ::uism-sv :event/begin-edit))} "EDIT")
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
      (td {:key 1} (str (t/date (t/instant SiteVisitDate))))
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
  (let [sites  (get (comp/props this) :ui.riverdb/current-project-sites)
        target [:ui.riverdb/current-project-sites]]
    (debug "RESET SITES" sites)
    (fm/set-value! this :ui/site nil)
    (comp/transact! this `[(rm/sort-ident-list-by {:idents-path ~target
                                                   :ident-key   :org.riverdb.db.stationlookup/gid
                                                   :sort-fn     :stationlookup/StationID})])))


(defn make-filter
  ([props]
   (let [{:ui.riverdb/keys [current-agency current-project current-year]
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
         :ui.riverdb/keys [current-agency current-project current-year current-project-sites]
         :ui/keys              [site sortField sortOrder limit offset list-meta show-upload] :as props}]
  {:ident              (fn [] [:component/id :sitevisit-list])
   :query              [{:sitevisits (comp/get-query SiteVisitSummary)}
                        {:project-years (comp/get-query ProjectYears)}
                        ;{:sites (comp/get-query looks/stationlookup-sum)}
                        {[:ui.riverdb/current-agency '_] (comp/get-query looks/agencylookup-sum)}
                        {[:ui.riverdb/current-project '_] (comp/get-query looks/projectslookup-sum)}
                        {[:ui.riverdb/current-project-sites '_] (comp/get-query looks/stationlookup-sum)}
                        [:ui.riverdb/current-year '_]
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
                                :ui.riverdb/keys [current-project current-year]} (comp/props this)
                               diff         (clojure.data/diff prev-props props)
                               changed-keys (clojure.set/union (keys (first diff)) (keys (second diff)))
                               runQuery?    (some #{:ui.riverdb/current-project
                                                    :ui.riverdb/current-year
                                                    :ui/offset
                                                    :ui/limit
                                                    :ui/sortField
                                                    :ui/sortOrder
                                                    :ui/site}
                                              changed-keys)
                               sortSites?   (some #{:ui.riverdb/current-project} changed-keys)]
                           (debug "DID UPDATE SiteVisitList" "runQuery?" runQuery? "sortSites?" sortSites?)
                           (when runQuery?
                             (let [filter (make-filter props)]
                               (load-sitevisits this filter limit offset sortField sortOrder)))
                           (when sortSites?
                             (reset-sites this))))
   :componentDidMount  (fn [this]
                         (let [{:ui/keys              [limit offset sortField sortOrder] :as props
                                :ui.riverdb/keys [current-agency]} (comp/props this)
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
                                 (debug "onEdit routing to sitevisit editor" (or (:db/id sv) "new"))
                                 (rroute/route-to! this SiteVisitEditor {:sv-id (or (:db/id sv) "new")}))
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
              (dom/select {:style    {:width "150px"}
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
   :shouldComponentUpdate (fn [_ _ _] true)
   :componentDidMount     (fn [this]
                            (debug "SV Page mounted")
                            (uism/begin! this rmsv/sv-sm ::uism-sv {:actor/sv-form (comp/get-ident this)})
                            #_(uism/trigger! this rmsv/sv-sm :event/edit))}

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