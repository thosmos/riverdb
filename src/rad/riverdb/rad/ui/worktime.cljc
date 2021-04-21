(ns riverdb.rad.ui.worktime
  (:require
    [clojure.string :as str]
    [edn-query-language.core :as eql]
    [cognitect.transit :as transit]
    [com.cognitect.transit.types :as ty]
    [riverdb.rad.model :as model]
    [riverdb.rad.model.agency :as agency]
    [riverdb.rad.model.person :as person]
    [riverdb.rad.model.global :as global]
    [riverdb.rad.model.worktime :as worktime]
    [riverdb.rad.ui.agency :refer [agency-picker AgencyQuery]]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    #?(:clj  [com.fulcrologic.fulcro.dom-server :as dom :refer [div label input]]
       :cljs [com.fulcrologic.fulcro.dom :as dom :refer [div label input]])
    [com.fulcrologic.fulcro.mutations :as fm]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.fulcro.ui-state-machines :as uism]
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.control :as control]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.picker-options :as po]
    [com.fulcrologic.rad.report-options :as ro]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.rendering.semantic-ui.components :refer [ui-wrapped-dropdown]]
    [com.fulcrologic.rad.report :as report]
    [com.fulcrologic.rad.type-support.decimal :as math]
    [com.fulcrologic.rad.type-support.date-time :as datetime]
    #?(:cljs [com.fulcrologic.semantic-ui.elements.icon.ui-icon :refer [ui-icon]])
    [riverdb.rad.model.person :as person]
    [theta.log :as log]
    ["file-saver" :refer [saveAs]]
    ["papaparse" :refer [unparse]]))

(defsc PersonQuery [this props]
  {:query [:person/uuid :person/Name]
   :ident :person/uuid})

(defsc StationQuery [this props]
  {:query [:db/id :stationlookup/StationName :stationlookup/StationID]
   :ident [:org.riverdb.db.stationlookup/gid :db/id]})

(defsc SVQuery [this props]
  {:query [:db/id
           :riverdb.entity/ns
           :sitevisit/uuid
           :sitevisit/SiteVisitDate
           {:sitevisit/StationID (comp/get-query StationQuery)}]
   :ident [:org.riverdb.db.sitevisit/gid :db/id]})

(form/defsc-form WorkTimeForm [this props]
  {fo/id               worktime/uid
   fo/attributes       [worktime/Person worktime/Hours worktime/Task worktime/Date worktime/Agency global/EntityNS]
   fo/default-values   {:riverdb.entity/ns :entity.ns/worktime
                        :worktime/hours    (transit/bigdec "1")}
   fo/route-prefix     "hours"
   fo/title            "Edit Hours"
   fo/layout           [[:worktime/person :worktime/date :worktime/hours :worktime/task]]
   fo/field-styles     {:worktime/person :pick-one
                        :worktime/date   :date-at-noon
                        :worktime/agency :pick-one}
   fo/read-only-fields #{:worktime/agency :pick-one}
   fo/field-options    {:worktime/person person/person-picker
                        :worktime/agency agency-picker}})


(defsc WorkTimeListItem [this {:worktime/keys [uuid person hours task date] :as props}]
  {:query [:db/id
           :riverdb.entity/ns
           :worktime/uuid
           :worktime/hours
           :worktime/task
           :worktime/date
           {:worktime/person (comp/get-query PersonQuery)}
           {:worktime/agency (comp/get-query AgencyQuery)}]
   :ident :worktime/uuid}
  ;(log/debug "RENDER WorkTimeListItem props" props)
  (let [person-nm (:person/Name person)]
    (dom/tr
      (dom/td
        (dom/a {:onClick (fn []
                           (log/debug "CLICK" person-nm uuid)
                           (form/edit! this WorkTimeForm uuid))} person-nm))
      (dom/td
        (dom/div (.-rep hours)))
      (dom/td
        (dom/div task))
      (dom/td
        (dom/div (str (datetime/inst->local-date date)))))))

(def ui-user-list-item (comp/factory WorkTimeListItem {:keyfn :db/id}))

(defsc AgencyQueryGID [this props]
  {:query [:db/id :agencylookup/uuid]
   :ident [:org.riverdb.db.agencylookup/gid :db/id]})

(fm/defmutation dl-file [{:keys [ag-ident]}]
  (action [{:keys [state] :as env}]
    (let [st @state
          wts (:worktime/uuid st)
          fields ["Person" "Hours" "Task" "Date"]
          data (reduce
                 (fn [data [_ wt]]
                   (if (= ag-ident (:worktime/agency wt))
                     (let [hrs1 ^ty.TaggedValue (:worktime/hours wt)
                           hrs2 (.-rep hrs1)]
                       (conj data [(:person/Name (get-in st (:worktime/person wt)))
                                   hrs2
                                   (:worktime/task wt)
                                   (str (datetime/inst->local-date (:worktime/date wt)))]))
                     data))
                 [] wts)
          csv    (unparse (clj->js {:fields fields
                                    :data   data}))
          blob (js/Blob. [csv] #js {:type "text/csv;charset=utf-8"})]
      (saveAs blob "hours.csv"))))

(report/defsc-report WorkTimeList [this props]
  {ro/columns          [worktime/Person worktime/Hours worktime/Task worktime/Date]
   ro/row-pk           worktime/uid
   ro/route            "hours"
   ro/source-attribute :worktime/all
   ro/BodyItem         WorkTimeListItem
   ro/title            "Hours"
   ro/paginate?        true
   ro/page-size        10
   ro/run-on-mount?    true
   ro/controls         {::add-worktime   {:label  "Add Time"
                                          :type   :button
                                          :icon   "add"
                                          :action (fn [this]
                                                    (let [props    (comp/props this)
                                                          agency   (:ui.riverdb/current-agency props)
                                                          ag-ident [:agencylookup/uuid (:agencylookup/uuid agency)]]
                                                      (log/debug "Add worktime agency" ag-ident)
                                                      (form/create! this WorkTimeForm {:initial-state {:worktime/agency ag-ident}})))}
                        ::all-worktimes  {:label  "Export CSV"
                                          :type   :button
                                          :icon   "download"
                                          :action (fn [this]
                                                    (let [props    (comp/props this)
                                                          agency   (:ui.riverdb/current-agency props)
                                                          ag-ident [:agencylookup/uuid (:agencylookup/uuid agency)]]
                                                      (comp/transact! this `[(dl-file {:ag-ident ~ag-ident})])))}
                        :worktime/agency (merge
                                           {:label     "Agency"
                                            :type      :picker
                                            :disabled? true}
                                           agency-picker)}
   ro/control-layout   {:inputs []}
   ;csv    (unparse (clj->js data))
   ;blob   (js/Blob. [csv] (clj->js {:type "text/csv;charset=utf-8"}))]
   ;(log/debug "worktimes" csv blob)))}}
   ;(saveAs blob, "worktimes.csv")))}}
   ro/query-inclusions [{[:ui.riverdb/current-agency '_] (comp/get-query AgencyQueryGID)}
                        [::po/options-cache '_]]})

