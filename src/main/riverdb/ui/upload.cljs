(ns riverdb.ui.upload
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h3 button label span table tr th td thead tbody]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as fm :refer [defmutation]]
    [com.fulcrologic.fulcro.networking.file-upload :as fup]
    [com.fulcrologic.fulcro.ui-state-machines :as uism :refer [defstatemachine]]
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

    [com.fulcrologic.semantic-ui.modules.progress.ui-progress :refer [ui-progress]]

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
    [riverdb.ui.globals :as globals]
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
    [thosmos.util :as tu]
    [testdouble.cljs.csv :as csv]))

(defn coll->opts [text-k val-k coll]
  (if (seq coll)
    (vec
      (map-indexed
        (fn [i m]
          {:key   i
           :text  (str (text-k m))
           :value (str (val-k m))})
        coll))
    []))

(defsc ImportColumn [this {:keys [i column config] :as props} {:keys [set-config params-opts]}]
  {:query [:i :column :config]}
  (let [{:keys [type param field]} (get config i)]
    (dom/tr
      (dom/td (inc i))
      (dom/td (dom/b (str column)))
      (dom/td (ui-dropdown {:options  [{:key -1 :text "" :value 0}
                                       {:key 1 :text "Date" :value "date"}
                                       {:key 5 :text "Device ID" :value "device"}
                                       {:key 6 :text "Field" :value "field"}
                                       ;{:key 3 :text "Unit" :value "unit"}
                                       ;{:key 4 :text "Device Type" :value "devType"}
                                       {:key -2 :text "Result" :value "result"}
                                       {:key 2 :text "Station ID" :value "station"}]
                            :value    (or type 0)
                            :style    {:width "auto" :minWidth "10em"}
                            :onChange (fn [_ d]
                                        (when-let [v (-> d .-value)]
                                          (when set-config
                                            (set-config
                                              (if (= v 0)
                                                (dissoc config i)
                                                (assoc-in config [i :type] v))))))}))
      (cond
        (or (= type "result") (= type "device"))
        (dom/td
          (ui-dropdown {:options  (into [{:key -1 :text "" :value 0}] params-opts)
                        :value    (or param 0)
                        :style    {:width "auto" :minWidth "10em"}
                        :onChange (fn [_ d]
                                    (when-let [v (-> d .-value)]
                                      (when set-config
                                        (set-config
                                          (if (= v 0)
                                            (update config i dissoc :param)
                                            (assoc-in config [i :param] v))))))}))
        (= type "field")
        (dom/td
          (ui-dropdown {:options  (into [{:key -1 :text "" :value 0}] globals/sv-fields-opts)
                        :value    (or field 0)
                        :style    {:width "auto" :minWidth "10em"}
                        :onChange (fn [_ d]
                                    (when-let [v (-> d .-value)]
                                      (when set-config
                                        (set-config
                                          (if (= v 0)
                                            (update config i dissoc :field)
                                            (assoc-in config [i :field] v))))))}))
        :else
        (dom/td ""))
      #_(dom/td (ui-dropdown {:options [{:key -1 :text "" :value -1}
                                        {:key 1 :text "°C" :value 1}
                                        {:key 2 :text "MPN/100mL" :value 2}]
                              :value   -1
                              :style   {:width "auto" :minWidth "10em"}})))))

(def ui-import-column (comp/computed-factory ImportColumn {:keyfn :i}))

(defn set-csv-data [this file]
  (if file
    (let [{:ui/keys [skip-line]} (comp/props this)
          js-file (:js-value (meta (:file/content file)))]
      (.then (.text js-file)
        (fn [text]
          (let [text   (str/replace text "\r" "")
                parsed (csv/read-csv text)]
            (debug "CSV data" (take 3 parsed))
            (comp/set-state! this {:csv parsed})))))
    (comp/set-state! this {:csv nil})))

(defn update-config [config up-config params-map csv-cols]
  (let [param-keys (set (keys params-map))
        field-keys (set (map name (keys globals/sv-fields)))
        {:keys [station-source station-column]} up-config]
    (loop [i 0 cols csv-cols config config]
      (let [col      (first cols)
            cfg      (get config i)
            typ      (:type cfg)
            param    (:param cfg)
            field    (:field cfg)
            field?   (contains? field-keys col)
            devID?   (when (string? col) (clojure.string/ends-with? col "_DevID"))
            col_dev  (when devID?
                       (subs col 0 (clojure.string/last-index-of col "_DevID")))
            device?  (contains? param-keys col_dev)
            rep?     (when (string? col) (re-find #"_\d$" col))
            col_rep  (if rep?
                       (subs col 0 (clojure.string/last-index-of col rep?))
                       col)
            result?  (contains? param-keys col_rep)
            date?    (= (clojure.string/lower-case col) "date")
            station? (and
                       (= station-source "column")
                       (= station-column i))
            config   (cond-> config
                       station?
                       (assoc-in [i :type] "station")

                       (and
                         (not station?)
                         (= (get-in config [i :type]) "station"))
                       (update i dissoc :type)

                       (and (not typ) (or date? result? field? devID?))
                       (assoc-in [i :type] (cond
                                             date? "date"
                                             result? "result"
                                             field? "field"
                                             devID? "device"))

                       (and (not field) field?)
                       (assoc-in [i :field] col)

                       (and (not param) (or device? result?))
                       (assoc-in [i :param] (or col_dev col_rep)))]
        (if-let [cols (not-empty (rest cols))]
          (recur (inc i) cols config)
          config)))))

(defn reset-progress [this]
  (fm/set-value! this :ui/progress 0)
  (fm/set-value! this :ui/import-error? false)
  (fm/set-string! this :ui/phase :value "uploading ...")
  (fm/set-value! this :ui/active true))

(defsc UploadComp [this {:ui/keys [active import-error? progress phase project-code samp-type station-id stations station-source station-column skip-line config device-suffix] :as props}]
  {:ident              (fn [] [:component/id :upload-comp])
   :query              [{[:ui.riverdb/current-agency '_] (comp/get-query Agency)}
                        :ui/project-code
                        :ui/samp-type
                        :ui/station-id
                        :ui/station-source
                        :ui/station-column
                        :ui/config
                        :ui/skip-line
                        :ui/active
                        :ui/progress
                        :ui/phase
                        :ui/import-error?
                        :ui/device-suffix
                        {:ui/stations (comp/get-query looks/stationlookup-sum)}]
   :initLocalState     (fn [this _]
                         {:save-ref (fn [r] (gobj/set this "input-ref" r))})
   :initial-state      {:ui/skip-line      false
                        :ui/config         {}
                        :ui/stations       {}
                        :ui/station-source "file"
                        :ui/active         false
                        :ui/import-error?  false
                        :ui/progress       0
                        :ui/device-suffix  nil}
   :componentDidMount  (fn [this]
                         (let [props   (comp/props this)
                               agency  (:ui.riverdb/current-agency props)
                               {:agencylookup/keys [Projects]} agency
                               project (first Projects)
                               ident   (comp/get-ident this)
                               id      (when project (:db/id project))]
                           (when id
                             (df/load! this :org.riverdb.db.stationlookup looks/stationlookup-sum
                               {:params {:limit  -1
                                         :filter {:projectslookup/Stations {:reverse id}}}
                                :target (conj ident :ui/stations)}))))
   :componentDidUpdate (fn [this prev-props prev-state]
                         (let [props         (comp/props this)
                               proj-changed? (not= (:ui/project-code props) (:ui/project-code prev-props))]
                           (when proj-changed?
                             (let [agency    (:ui.riverdb/current-agency props)
                                   {:agencylookup/keys [Projects]} agency
                                   {:ui/keys [project-code]} props
                                   projs-map (nest-by [:projectslookup/ProjectID] Projects)
                                   project   (get projs-map project-code)
                                   ident     (comp/get-ident this)
                                   id        (:db/id project)]
                               (when id
                                 (df/load! this :org.riverdb.db.stationlookup looks/stationlookup-sum
                                   {:params {:limit  -1
                                             :filter {:projectslookup/Stations {:reverse id}}}
                                    :target (conj ident :ui/stations)}))))))}
  (let [save-ref       (comp/get-state this :save-ref)
        csv-data       (comp/get-state this :csv)
        csv-cols       (if skip-line (second csv-data) (first csv-data))
        ;csv-row      (if skip-line (nth csv-data 2) (second csv-data))

        files          (not-empty (comp/get-state this :files))
        parse?         (and
                         (= station-source "file")
                         files)
        parsedIDs      (when parse?
                         (try
                           (vec
                             (for [file files]
                               (let [filename (:file/name file)]
                                 (subs filename 0 (clojure.string/index-of filename "_")))))
                           (catch js/Object _)))

        station-opts   (map-indexed
                         (fn [i m]
                           {:key   i
                            :text  (str (:stationlookup/StationID m) " " (:stationlookup/StationName m))
                            :value (str (:stationlookup/StationID m))})
                         stations)

        missing?       (when (and (seq parsedIDs) (seq stations))
                         (let [station-set (set (map :value station-opts))
                               parsed-set  (set parsedIDs)]
                           ;(debug "MISSING?" station-set parsed-set)
                           (not-empty
                             (clojure.set/difference parsed-set station-set))))

        parse-err?     (when (and
                               parse?
                               (not parsedIDs)))

        agency         (:ui.riverdb/current-agency props)
        {:agencylookup/keys [AgencyCode Projects]} agency

        proj-opts      (coll->opts :projectslookup/Name :projectslookup/ProjectID Projects)
        projs-map      (nest-by [:projectslookup/ProjectID] Projects)
        project-code   (or project-code (:value (first proj-opts)))
        project        (get projs-map project-code)
        {:projectslookup/keys [Parameters]} project

        params-map     (nest-by [:parameter/NameShort] Parameters)
        params-opts    (coll->opts :parameter/Name :parameter/NameShort Parameters)

        ;samps-map    (nest-by [:sampletypelookup/SampleTypeCode] SampleTypes)
        ;samps-opts   (coll->opts :sampletypelookup/Name :sampletypelookup/SampleTypeCode SampleTypes)
        ;samp-type    (or samp-type (:value (first samps-opts)))

        up-config      {:agency         AgencyCode
                        :project        project-code
                        :skip-line      skip-line
                        :station-source station-source
                        :station-column (when (= station-source "column") station-column)
                        :station-id     (when (= station-source "select") station-id)
                        :station-ids    (when parse? parsedIDs)}

        config         (when csv-cols
                         (update-config config up-config params-map csv-cols))

        up-config      (assoc up-config :col-config config)

        enable         (if
                         (or
                           (not files)
                           (empty? config)
                           parse-err?
                           ;; TODO re-enable this
                           ;missing?
                           (and
                             (= station-source "select")
                             (not station-id))
                           (and
                             (= station-source "column")
                             (not station-column)))
                         false
                         true)
        phase (cond
                (= progress 100)
                "complete"
                (< progress 49)
                "uploading ..."
                (>= progress 49)
                "processing ...")]


    #_(debug "RENDER UploadComp" "code" AgencyCode "agency" agency "project-code" project-code "proj-opts" proj-opts "projs" projs-map "samps" samps-map "samps-opts" samps-opts "params" params-map "params-opts" params-opts "csv-data" (take 3 csv-data) "parsedIDs" parsedIDs "stations" stations "config" config)

    (dom/div :.ui.segment
      (dom/div :.ui.header "BULK IMPORT CSV DATA")
      (dom/div :.ui.form
        (dom/div :.field
          (dom/label "Project")
          (ui-dropdown {:loading          false
                        :search           true
                        :selection        true
                        :multiple         false
                        ;:error true
                        :clearable        false
                        :allowAdditions   false
                        :additionPosition "bottom"
                        :options          proj-opts
                        :value            project-code
                        :style            {:width "auto" :minWidth "10em"}
                        :onChange         (fn [_ d]
                                            (when-let [val (. d -value)]
                                              (fm/set-value! this :ui/config {})
                                              (fm/set-string! this :ui/project-code :value val)))}))

        (dom/div :.field
          (dom/label "Files")
          (dom/input
            {:type     "file"
             :multiple true
             :accept   "text/csv"
             :ref      save-ref
             :onChange (fn [evt]
                         (let [files (fup/evt->uploads evt)]
                           (debug "FILES" files)
                           (comp/set-state! this {:files files})
                           (set-csv-data this (first files))
                           #_(fm/set-value! this :ui/config {})))}))

        (div :.dimmable.fields {:key "import-source"}
          (dom/div :.field
            (dom/label "Station ID Source")
            (ui-dropdown {:loading          false
                          :search           true
                          :selection        true
                          :multiple         false
                          :clearable        false
                          :allowAdditions   false
                          :additionPosition "bottom"
                          :options          [{:text "File Name" :value "file"}
                                             {:text "Select Single" :value "select"}
                                             {:text "CSV Column" :value "column"}]
                          :value            (or station-source "file")
                          :style            {:width "auto" :minWidth "10em"}
                          :onChange         (fn [_ d]
                                              (when-let [val (. d -value)]
                                                (fm/set-string! this :ui/station-source :value val)))}))
          (when (= station-source "select")
            (dom/div :.field
              (dom/label "Station ID")
              (ui-dropdown {:loading          false
                            :search           true
                            :selection        true
                            :multiple         false
                            :clearable        false
                            :allowAdditions   false
                            :additionPosition "bottom"
                            :options          station-opts
                            :value            station-id
                            :style            {:width "auto" :minWidth "10em"}
                            :onChange         (fn [_ d]
                                                (when-let [val (. d -value)]
                                                  (fm/set-string! this :ui/station-id :value val)))})))
          (when (and (= station-source "column") csv-data)
            (let [col-opts (map-indexed
                             (fn [i col]
                               {:text (str (inc i) " - " col) :value i})
                             csv-cols)]
              (dom/div :.field
                (dom/label "Station Column")
                (ui-dropdown {:loading          false
                              :search           true
                              :selection        true
                              :multiple         false
                              :clearable        false
                              :allowAdditions   false
                              :additionPosition "bottom"
                              :options          col-opts
                              :value            station-column
                              :style            {:width "auto" :minWidth "10em"}
                              :onChange         (fn [_ d]
                                                  (when-let [val (. d -value)]
                                                    (fm/set-string! this :ui/station-column :value val)))}))))

          (when (and csv-data parsedIDs)
            (dom/div :.field {:key "stationIDS"}
              (dom/label "Station IDs")
              (dom/p (str/join ", " parsedIDs)))))

        (when parse-err?
          (dom/div :.ui.negative.message
            (dom/div :.header "Error")
            (dom/p "Failed to parse StationID from file name")
            (dom/p "Format: StationID_station_name.csv")
            (dom/p "Example: 101_Above_Scotchman_Creek.csv")))

        (when missing?
          (dom/div :.ui.negative.message
            (dom/div :.header "Missing Stations")
            (dom/p "There are some parsed station IDs without matching stations in the database")
            (dom/p (str/join ", " missing?))))

        (when csv-data
          (dom/div :.field {:key "columns"}
            ;(dom/label "Column Config")
            (ui-checkbox
              {:label    "Skip First Line"
               :checked  skip-line
               :onChange (fn []
                           (debug "CHECKBOX")
                           (fm/set-value! this :ui/skip-line (not skip-line)))})
            #_(ui-input
                {:placeholder "Device Column Suffix"})
            (dom/table {:className "ui selectable small table"}
              (dom/thead
                (dom/tr
                  (dom/th "Column")
                  (dom/th "Name")
                  (dom/th "Type")
                  (dom/th "Parameter")
                  #_(dom/th "Matches?")
                  #_(dom/th "Unit")))
              (dom/tbody
                (map-indexed
                  (fn [i col]
                    (ui-import-column
                      {:i      i
                       :column col
                       :config config}
                      {:params-opts params-opts
                       :set-config  (fn [config]
                                      (debug "SET CONFIG" config)
                                      (fm/set-value! this :ui/config config))}))
                  csv-cols)))))
        (when active
          (dom/div
            (ui-progress
              {:indicating true :active true :autoSuccess true :percent progress :error import-error?}
              (when phase phase))))
        (dom/div
          (dom/button :.ui.button
            {:disabled (not enable)
             :onClick  (fn []
                         (let [params (fup/attach-uploads {:config up-config} files)
                               params (assoc params :progress-target (conj (comp/get-ident this) :ui/progress))]
                           (reset-progress this)
                           (comp/transact! this `[(rm/upload-files ~params)])))}

            "Import"))))))
(def ui-upload-comp (comp/factory UploadComp))


(defsc UploadModal [this {:ui/keys [show-upload] :as props} {:keys [onClose]}]
  {}
  (ui-modal {:open show-upload :onClose onClose}
    (ui-modal-content {}
      (ui-upload-comp {}))))
(def ui-upload-modal (comp/factory UploadModal))


(defsc UploadPage [this {:ui/keys [uploadcomp] :as props}]
  {:query         [:ui/filename
                   {:ui/uploadcomp (comp/get-query UploadComp)}]
   :initial-state {:ui/filename   nil
                   :ui/uploadcomp {}}
   :ident         (fn [] [:component/id :upload-page])
   :route-segment ["upload"]}
  (debug "RENDER UploadPage" props)
  (ui-upload-comp uploadcomp))


(def ui-upload-page (comp/factory UploadPage))
