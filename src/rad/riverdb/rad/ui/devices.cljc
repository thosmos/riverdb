(ns riverdb.rad.ui.devices
  (:require
    [clojure.string :as str]
    [edn-query-language.core :as eql]
    [riverdb.rad.model :as model]
    [riverdb.rad.model.agency :as agency]
    [riverdb.rad.model.device :as device]
    [riverdb.rad.model.devicetype :as devicetype]
    [riverdb.rad.model.global :as global]
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

(defsc SamplingDeviceLookupQuery [this props]
  {:query [:samplingdevicelookup/uuid :samplingdevicelookup/SampleDevice]
   :ident :samplingdevicelookup/uuid})

(def devicetype-picker
  {po/cache-key       :picker/devicetype
   po/query-key       :samplingdevicelookups/all
   po/cache-time-ms   3600000
   po/query-component SamplingDeviceLookupQuery
   po/options-xform   (fn [_ options]
                        (let [opts
                              (into [{:text "" :value nil}]
                                (mapv
                                  (fn [{:samplingdevicelookup/keys [uuid SampleDevice]}]
                                    {:text SampleDevice :value [:samplingdevicelookup/uuid uuid]})
                                  (sort-by :samplingdevicelookup/SampleDevice options)))]
                          (log/debug "samplingdevicelookup PICKER OPTS" opts)
                          opts))})

(form/defsc-form DeviceTypeForm [this props]
  {fo/id               devicetype/uid
   fo/attributes       [devicetype/Active devicetype/SampleDevice global/EntityNS]
   fo/default-values   {:samplingdevicelookup/Active    true
                        :riverdb.entity/ns :entity.ns/samplingdevicelookup}
   ;::form/enumeration-order :person/Name
   ;::form/cancel-route      ["people"]
   fo/route-prefix     "device"
   ;:route-segment     ["person"]
   fo/title            "Edit Device"
   fo/layout           [[:samplingdevicelookup/SampleDevice]
                        [:samplingdevicelookup/Active]]
   ;[:samplingdevice/DeviceType]
   ;[:samplingdevice/Active]]
   ;[:samplingdevice/Agency]]
   fo/field-labels     {:samplingdevicelookup/SampleDevice "Name"}})

(form/defsc-form DeviceForm [this props]
  {fo/id               device/uid
   fo/attributes       [device/Active device/CommonID device/DeviceType device/Agency global/EntityNS]
   fo/default-values   {:samplingdevice/Active    true
                        :riverdb.entity/ns :entity.ns/samplingdevice}
   ;::form/enumeration-order :person/Name
   ;::form/cancel-route      ["people"]
   fo/route-prefix     "device"
   ;:route-segment     ["person"]
   fo/title            "Edit Device"
   fo/layout           [[:samplingdevice/CommonID]
                        [:samplingdevice/DeviceType]
                        [:samplingdevice/Active]
                        [:samplingdevice/Agency]]

   fo/field-labels     {:samplingdevice/CommonID "ID"}
   fo/field-styles     {:samplingdevice/Agency :pick-one
                        :samplingdevice/DeviceType :pick-one
                        :samplingdevice/Active :default}
   fo/read-only-fields #{:samplingdevice/Agency
                         #_:samplingdevice/DeviceType}
   fo/field-options    {:samplingdevice/Agency agency-picker
                        :samplingdevice/DeviceType devicetype-picker}})




;(def account-validator (fs/make-validator (fn [form field]
;                                            (case field
;                                              :account/email (let [prefix (or
;                                                                            (some-> form
;                                                                              (get :account/name)
;                                                                              (str/split #"\s")
;                                                                              (first)
;                                                                              (str/lower-case))
;                                                                            "")]
;                                                               (str/starts-with? (get form field) prefix))
;                                              (= :valid (model/all-attribute-validator form field))))))

(defsc DeviceListItem [this {:samplingdevice/keys [uuid CommonID Agency Active DeviceType] :as props}]
  {:query [:samplingdevice/uuid :samplingdevice/CommonID
           :samplingdevice/Active
           {:samplingdevice/DeviceType [:samplingdevicelookup/uuid :samplingdevicelookup/SampleDevice]}
           {:samplingdevice/Agency [:agencylookup/uuid :agencylookup/AgencyCode]}]
   :ident :samplingdevice/uuid}
  (dom/tr {:onClick (fn [] (form/edit! this DeviceForm uuid))}
    (dom/td
      (dom/div CommonID))
    (dom/td
      (dom/div (:samplingdevicelookup/SampleDevice DeviceType)))
    (dom/td
      (dom/div (if Active
                 (ui-icon {:name "check square outline"})
                 (ui-icon {:name "square outline"}))))
    (dom/td
      (dom/div (:agencylookup/AgencyCode Agency)))))

(def ui-device-list-item (comp/factory DeviceListItem {:keyfn :samplingdevice/uuid}))


(report/defsc-report DeviceList [this props]
  {ro/columns             [device/CommonID device/DeviceType device/Active device/Agency]
   ro/column-headings     {:samplingdevice/CommonID "ID"}

   ro/BodyItem            DeviceListItem
   ro/row-pk              device/uid
   ro/route               "devices"
   ro/source-attribute    :devices/all
   ro/title               "Sampling Devices"
   ;ro/paginate?           true
   ;ro/page-size           20
   ro/run-on-mount?       true
   ro/controls            {:samplingdevice/Agency     (merge
                                                        {:label     "Agency"
                                                         :type      :picker
                                                         :disabled? true}
                                                        agency-picker)
                           :samplingdevice/DeviceType (merge
                                                        {:label    "Device Type"
                                                         :type     :picker
                                                         :onChange (fn [this a]
                                                                     (log/debug "device type change" a)
                                                                     (com.fulcrologic.rad.control/run! this))}
                                                        devicetype-picker)

                           :samplingdevice/CommonID   {:label    "ID"
                                                       :type     :string
                                                       :style    :search
                                                       :onChange (fn [this a]
                                                                   (log/debug "search change" a)
                                                                   (com.fulcrologic.rad.control/run! this))}

                           ::add-device               {:label  "Add Device"
                                                       :type   :button
                                                       :action (fn [this]
                                                                 (let [props    (comp/props this)
                                                                       agency   (:ui.riverdb/current-agency props)
                                                                       ag-ident [:agencylookup/uuid (:agencylookup/uuid agency)]]
                                                                   (log/debug "Add device agency" ag-ident)
                                                                   (form/create! this DeviceForm {:initial-state {:samplingdevice/Agency ag-ident}})))}}
   ro/control-layout      {:action-buttons [::add-device]
                           :inputs         [[:samplingdevice/CommonID :samplingdevice/DeviceType :samplingdevice/Agency]]}
   ro/links               {:samplingdevice/CommonID
                           (fn [report-instance {:samplingdevice/keys [uuid] :as props}]
                             (form/edit! report-instance DeviceForm uuid))}
   ro/initial-sort-params {:sort-by :samplingdevice/CommonID}
   ro/query-inclusions    [{[:ui.riverdb/current-agency '_] (comp/get-query AgencyQueryGID)}
                           [::po/options-cache '_]]})



