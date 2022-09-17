(ns riverdb.rad.ui.stations
  (:require
    [clojure.string :as str]
    [edn-query-language.core :as eql]
    [riverdb.rad.model :as model]
    [riverdb.rad.model.agency :as agency]
    [riverdb.rad.model.station :as station]
    [riverdb.rad.model.devicetype :as devicetype]
    [riverdb.rad.model.global :as global]
    [riverdb.rad.ui.project :refer [proj-picker]]
    [riverdb.rad.ui.agency :refer [agency-picker]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    #?(:clj  [com.fulcrologic.fulcro.dom-server :as dom :refer [div label input]]
       :cljs [com.fulcrologic.fulcro.dom :as dom :refer [div label input]])
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.control :as control]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.picker-options :as po]
    [com.fulcrologic.rad.report-options :as ro]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.rendering.semantic-ui.components :refer [ui-wrapped-dropdown]]
    [com.fulcrologic.semantic-ui.elements.label.ui-label :refer [ui-label]]
    [com.fulcrologic.rad.report :as report]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.fulcro.ui-state-machines :as uism]
    [com.fulcrologic.rad.type-support.decimal :as math]
    [com.fulcrologic.rad.type-support.date-time :as datetime]
    [com.fulcrologic.semantic-ui.elements.icon.ui-icon :refer [ui-icon]]
    [theta.log :as log]))

(defsc AgencyQueryGID [this props]
  {:query [:db/id :agencylookup/uuid]
   :ident [:org.riverdb.db.agencylookup/gid :db/id]})


(form/defsc-form StationForm [this props]
  {fo/id               station/uid
   fo/attributes       [global/EntityNS station/StationID station/StationName
                        station/Projects
                        station/Active
                        station/Agency
                        station/Description station/StationComments
                        station/Lat
                        station/Lon
                        station/ForkTribGroup station/RiverFork station/LocalWatershed
                        station/NHDWaterbody station/Elevation station/DOcorrection]
   fo/default-values   {:stationlookup/Active true
                        :riverdb.entity/ns    :entity.ns/stationlookup}
   fo/route-prefix     "station"
   fo/title            "Edit Station"
   fo/layout           [[:stationlookup/Agency]
                        [:stationlookup/Active]
                        [:stationlookup/StationID :stationlookup/StationName]
                        [:stationlookup/TargetLat :stationlookup/TargetLong]
                        [:stationlookup/Description]
                        [:stationlookup/StationComments]
                        [:stationlookup/ForkTribGroup :stationlookup/RiverFork]
                        [:stationlookup/LocalWatershed :stationlookup/NHDWaterbody]
                        [:projectslookup/_Stations]]


   fo/field-labels     {:stationlookup/StationID       "ID"
                        :stationlookup/StationName     "Name"
                        :stationlookup/StationComments "Comments"
                        :stationlookup/ForkTribGroup   "ForkTrib Group"
                        :stationlookup/RiverFork       "River Fork"
                        :stationlookup/LocalWatershed  "Local Watershed"
                        :stationlookup/NHDWaterbody    "NHD Waterbody"
                        :stationlookup/TargetLat       "Lat"
                        :stationlookup/TargetLong      "Long"
                        :projectslookup/_Stations      "Projects"}
   fo/field-styles     {:stationlookup/Agency     :pick-one
                        :stationlookup/Active     :default
                        :projectslookup/_Stations :pick-many
                        :stationlookup/TargetLong :river-decimal}
   fo/read-only-fields #{:stationlookup/Agency}
   fo/field-options    {:stationlookup/Agency agency-picker
                        :projectslookup/_Stations proj-picker}
   fo/triggers         {:derive-fields            (fn [props]
                                                    (log/debug "TRIGGERS PROPS" props)
                                                    props)}})


(defsc StationListItem [this {:stationlookup/keys [uuid StationID StationIDLong StationName Agency Active] :as props}]
  {:query [:stationlookup/uuid :stationlookup/StationID :stationlookup/StationIDLong :stationlookup/StationName :stationlookup/Active
           {:projectslookup/_Stations [:projectslookup/uuid :projectslookup/ProjectID]}
           {:stationlookup/Projects [:projectslookup/uuid :projectslookup/ProjectID]}
           {:stationlookup/Agency [:agencylookup/uuid :agencylookup/AgencyCode]}]
   :ident :stationlookup/uuid}
  (let [proj-ids (not-empty (map :projectslookup/ProjectID (:projectslookup/_Stations props)))]
    (dom/tr {:onClick (fn [] (form/edit! this StationForm uuid))}
      (dom/td
        (dom/div StationID))
      (dom/td
        (dom/div StationName))
      (dom/td
        (dom/div (if Active
                   (ui-icon {:name "check square outline"})
                   (ui-icon {:name "square outline"}))))
      (dom/td
        (dom/div (:agencylookup/AgencyCode Agency)))
      (dom/td
        (when proj-ids
          (for [proj-id proj-ids]
            (when proj-id
              (ui-label {:key proj-id} proj-id))))))))

(def ui-device-list-item (comp/factory StationListItem {:keyfn :stationlookup/uuid}))


(report/defsc-report StationList [this props]
  {ro/columns             [station/StationID station/StationName station/Active station/Agency station/Projects]
   ro/column-headings     {:stationlookup/StationID   "ID"
                           :stationlookup/StationName "Name"
                           :projectslookup/_Stations  "Projects"}
   ro/BodyItem            StationListItem
   ro/row-pk              station/uid
   ro/route               "stations"
   ro/source-attribute    :stationlookup/all
   ro/title               "Stations"
   ro/paginate?           false
   ;ro/page-size           20
   ro/run-on-mount?       true
   ro/controls            {:stationlookup/Agency      (merge
                                                        {:label     "Agency"
                                                         :type      :picker
                                                         :disabled? true}
                                                        agency-picker)
                           :stationlookup/StationName {:label    "Name"
                                                       :type     :string
                                                       :style    :search
                                                       :onChange (fn [this a]
                                                                   (log/debug "search change" a)
                                                                   (com.fulcrologic.rad.control/run! this))}

                           ::add-station              {:label  "Add Station"
                                                       :type   :button
                                                       :action (fn [this]
                                                                 (let [props    (comp/props this)
                                                                       agency   (:ui.riverdb/current-agency props)
                                                                       ag-ident [:agencylookup/uuid (:agencylookup/uuid agency)]]
                                                                   (log/debug "Add station w/ agency" ag-ident)
                                                                   (form/create! this StationForm {:initial-state {:stationlookup/Agency ag-ident}})))}}
   ro/control-layout      {:action-buttons [::add-station]
                           :inputs         [[:stationlookup/StationName :stationlookup/Agency]]}
   ro/initial-sort-params {:sort-by :stationlookup/StationID}
   ro/query-inclusions    [{[:ui.riverdb/current-agency '_] (comp/get-query AgencyQueryGID)}
                           [::po/options-cache '_]]})



