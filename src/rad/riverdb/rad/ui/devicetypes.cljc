(ns riverdb.rad.ui.devicetypes
  (:require
    [riverdb.rad.model.devicetype :as devicetype]
    [riverdb.rad.model.global :as global]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    #?(:clj  [com.fulcrologic.fulcro.dom-server :as dom :refer [div label input]]
       :cljs [com.fulcrologic.fulcro.dom :as dom :refer [div label input]])
    [com.fulcrologic.rad.control :as control]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.picker-options :as po]
    [com.fulcrologic.rad.report-options :as ro]
    [com.fulcrologic.rad.report :as report]
    [com.fulcrologic.semantic-ui.elements.icon.ui-icon :refer [ui-icon]]
    [theta.log :as log]))


(form/defsc-form DeviceTypeForm [this props]
  {fo/id             devicetype/uid
   fo/attributes     [devicetype/Active devicetype/SampleDevice devicetype/SamplingMatrix global/EntityNS]
   fo/default-values {:samplingdevicelookup/Active true
                      :riverdb.entity/ns           :entity.ns/samplingdevicelookup}
   fo/route-prefix   "devicetype"
   fo/title          "Edit Device Type"
   fo/layout         [[:samplingdevicelookup/SampleDevice]
                      [:samplingdevicelookup/SamplingMatrix]
                      [:samplingdevicelookup/Active]]
   fo/field-labels   {:samplingdevicelookup/SampleDevice  "Name"
                      :samplingdevicelookup/SamplingMatrix "Matrix"}})


(defsc DeviceTypeListItem [this {:samplingdevicelookup/keys [uuid SampleDevice SamplingMatrix Active] :as props}]
  {:query [:samplingdevicelookup/uuid
           :samplingdevicelookup/SampleDevice
           :samplingdevicelookup/SamplingMatrix
           :samplingdevicelookup/Active]
   :ident :samplingdevicelookup/uuid}
  (dom/tr {:onClick (fn [] (form/edit! this DeviceTypeForm uuid))}
    (dom/td (dom/div SampleDevice))
    (dom/td (dom/div SamplingMatrix))
    (dom/td
      (dom/div (if Active
                 (ui-icon {:name "check square outline"})
                 (ui-icon {:name "square outline"}))))))

(def ui-device-type-list-item (comp/factory DeviceTypeListItem {:keyfn :samplingdevicelookup/uuid}))


(report/defsc-report DeviceTypeList [this props]
  {ro/columns          [devicetype/SampleDevice devicetype/SamplingMatrix devicetype/Active]
   ro/column-headings  {:samplingdevicelookup/SampleDevice   "Name"
                        :samplingdevicelookup/SamplingMatrix "Matrix"}
   ro/BodyItem         DeviceTypeListItem
   ro/row-pk           devicetype/uid
   ro/route            "devicetypes"
   ro/source-attribute :samplingdevicelookups/all
   ro/title            "Device Types"
   ro/run-on-mount?    true
   ro/controls         {:samplingdevicelookup/SampleDevice {:label    "Name"
                                                            :type     :string
                                                            :style    :search
                                                            :onChange (fn [this a]
                                                                        (log/debug "device type search change" a)
                                                                        (control/run! this))}
                        ::add-device-type                  {:label  "Add Device Type"
                                                            :type   :button
                                                            :action (fn [this]
                                                                      (form/create! this DeviceTypeForm {}))}}
   ro/control-layout   {:action-buttons [::add-device-type]
                        :inputs         [[:samplingdevicelookup/SampleDevice]]}
   ro/initial-sort-params {:sort-by :samplingdevicelookup/SampleDevice}
   ro/query-inclusions [[::po/options-cache '_]]})
