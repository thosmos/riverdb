(ns riverdb.ui.upload
  (:require
    [clojure.string :as str]
    [cljs.spec.alpha :as s]
    [cljs.tools.reader.edn :as edn]
    [cognitect.transit :as transit]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h3 button label span table tr th td thead tbody]]
    [com.fulcrologic.fulcro.dom.html-entities :as ent]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.application :as fapp]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as uism :refer [defstatemachine]]
    [com.fulcrologic.fulcro.mutations :as fm :refer [defmutation]]
    [com.fulcrologic.fulcro.networking.file-upload :as file-upload]

    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.semantic-ui.elements.loader.ui-loader :refer [ui-loader]]
    [com.fulcrologic.semantic-ui.modules.dimmer.ui-dimmer :refer [ui-dimmer]]
    [com.fulcrologic.semantic-ui.elements.input.ui-input :refer [ui-input]]
    [com.fulcrologic.semantic-ui.elements.header.ui-header :refer [ui-header]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form :refer [ui-form]]
    [com.fulcrologic.semantic-ui.elements.label.ui-label :refer [ui-label]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal :refer [ui-modal]]
    [com.fulcrologic.semantic-ui.elements.button.ui-button :refer [ui-button]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-header :refer [ui-modal-header]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-content :refer [ui-modal-content]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-actions :refer [ui-modal-actions]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-description :refer [ui-modal-description]]

    [com.fulcrologic.semantic-ui.collections.form.ui-form-checkbox :refer [ui-form-checkbox]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-radio :refer [ui-form-radio]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-field :refer [ui-form-field]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-input :refer [ui-form-input]]
    [com.fulcrologic.semantic-ui.modules.checkbox.ui-checkbox :refer [ui-checkbox]]

    [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :refer [ui-dropdown]]

    [com.fulcrologic.semantic-ui.collections.table.ui-table :refer [ui-table]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table-header :refer [ui-table-header]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table-body :refer [ui-table-body]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table-header-cell :refer [ui-table-header-cell]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table-cell :refer [ui-table-cell]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table-row :refer [ui-table-row]]

    [com.fulcrologic.semantic-ui.modules.tab.ui-tab :refer [ui-tab]]
    [com.fulcrologic.semantic-ui.modules.tab.ui-tab-pane :refer [ui-tab-pane]]
    [com.fulcrologic.semantic-ui.elements.icon.ui-icon :refer [ui-icon]]
    [com.fulcrologic.semantic-ui.modules.popup.ui-popup :refer [ui-popup]]
    [goog.object :as gobj]
    [riverdb.application :refer [SPA]]
    [riverdb.api.mutations :as rm :refer [TxResult ui-tx-result]]
    [riverdb.ui.agency :refer [Agency]]
    [riverdb.ui.components :refer [ui-drag-drop-context ui-droppable ui-draggable ui-autosizer]]
    [riverdb.ui.forms :refer [SampleTypeCodeForm]]
    [riverdb.lookup :refer [specs-map]]
    [riverdb.ui.lookup-options :refer [ui-theta-options preload-options]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.routes]
    [riverdb.ui.session :refer [ui-session Session]]
    [riverdb.ui.util :as ui-util :refer
     [make-validator parse-float rui-checkbox rui-int rui-bigdec rui-input ui-cancel-save
      set-editing set-value set-value!! set-refs! set-ref! set-ref set-refs get-ref-val
      get-ref-set-val lookup-db-ident db-ident->db-ref filter-param-typecode]]
    [riverdb.util :refer [nest-by]]
    [theta.log :as log :refer [debug]]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [thosmos.util :as tu]
    [testdouble.cljs.csv :as csv]))


(defsc UploadComp [this {:ui/keys []}]
  {:query []
   :initLocalState     (fn [this _]
                         {:save-ref (fn [r] (gobj/set this "input-ref" r))})}
  (let [save-ref (comp/get-state this :save-ref)
        csv-params (comp/get-state this :csv-params)
        projects  []
        types    []
        stations []]
    (dom/div :.ui.segment
      (dom/div :.ui.header "BULK IMPORT CSV DATA")
      (dom/div :.ui.form
        (div :.dimmable.fields {:key "import-meta"}
          (dom/div :.field
            (dom/label "Project")
            (ui-dropdown {:loading          false
                          :search           true
                          :selection        true
                          :multiple         false
                          :clearable        false
                          :allowAdditions   false
                          :additionPosition "bottom"
                          :options          projects
                          :value            0
                          :style            {:width "auto" :minWidth "10em"}}))
          (dom/div :.field
            (dom/label "Sample Type")
            (ui-dropdown {:loading          false
                          :search           true
                          :selection        true
                          :multiple         false
                          :clearable        false
                          :allowAdditions   false
                          :additionPosition "bottom"
                          :options          types
                          :value            0
                          :style            {:width "auto" :minWidth "10em"}}))
          (dom/div :.field
            (dom/label "Station")
            (ui-dropdown {:loading          false
                          :search           true
                          :selection        true
                          :multiple         false
                          :clearable        false
                          :allowAdditions   false
                          :additionPosition "bottom"
                          :options          stations
                          :value            0
                          :style            {:width "auto" :minWidth "10em"}})))

        (dom/div :.field
          (dom/label "CSV File")
          (dom/input
            {:type     "file"
             :accept   "text/csv"
             :ref      save-ref
             :onChange (fn [evt]
                         (let [file    (first (file-upload/evt->uploads evt))
                               js-file (:js-value (meta (:file/content file)))]
                           (comp/set-state! this {:file file})
                           (.then (.text js-file)
                             (fn [text]
                               (let [text (str/replace text "\r" "")
                                     parsed (csv/read-csv text)]
                                 (debug "CSV" (first parsed))
                                 (comp/set-state! this {:csv-params parsed}))))
                           (debug "FILE" file)))}))
        (when csv-params
          (dom/div :.field
            (dom/label "Import Parameters")
            (dom/table {:className "ui selectable small table"}
              (dom/thead
                (dom/tr
                  (dom/th "CSV Column")
                  (dom/th "Type")
                  (dom/th "Parameter")
                  (dom/th "Unit")
                  (dom/th "")))
              (dom/tbody
                (map-indexed
                  (fn [i col]
                    (dom/tr {:key i}
                      (dom/td {} (dom/b (str col)))
                      (dom/td (ui-dropdown {:options          [{:text "" :value -1} {:text "Result" :value 0}
                                                               {:text "Date" :value 1} {:text "Station" :value 2}
                                                               {:text "Unit" :value 3} {:text "Device Type" :value 4}
                                                               {:text "Device ID" :value 5}]
                                            :value            -1
                                            :style            {:width "auto" :minWidth "10em"}}))
                      (dom/td (ui-dropdown {:options          [{:text "" :valuw -1} {:text "Temp" :value 1} {:text "E. coli" :value 2} {:text "Coliform" :value 3} {:text "Entero" :value 4}]
                                            :value            -1
                                            :style            {:width "auto" :minWidth "10em"}}))
                      (dom/td (ui-dropdown {:options          [{:text "" :value -1} {:text "°C" :value 1} {:text "MPN/100mL" :value 2}]
                                            :value            -1
                                            :style            {:width "auto" :minWidth "10em"}}))))
                  (first csv-params))))))
        (dom/div
          (dom/button :.ui.button {:onClick (fn []
                                              (let [file (comp/get-state this :file)]))}
            "Import"))))))
(def ui-upload-comp (comp/factory UploadComp))


(defsc UploadModal [this {:ui/keys [show-upload] :as props} {:keys [onClose]}]
  {}
  (ui-modal {:open show-upload :onClose onClose}
    (ui-modal-content {}
      (ui-upload-comp {}))))
(def ui-upload-modal (comp/factory UploadModal))

(defsc UploadPage [this {:ui/keys [] :as props}]
  {:query         [:ui/filename]
   :initial-state {:ui/filename nil}
   :ident         (fn [] [:component/id :upload-page])
   :route-segment ["upload"]}
  (debug "RENDER UploadPage")
  (ui-upload-comp {}))


(def ui-upload-page (comp/factory UploadPage))
