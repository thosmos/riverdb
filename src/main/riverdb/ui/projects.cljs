(ns riverdb.ui.projects
  (:require
    [clojure.string :as str]
    [cljs.spec.alpha :as s]
    [cljs.tools.reader.edn :as edn]
    [cognitect.transit :as transit]

    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h3 button label span table tr th td thead tbody]]
    [com.fulcrologic.fulcro.dom.html-entities :as ent]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as fm :refer [defmutation]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as uism :refer [defstatemachine]]
    [com.fulcrologic.fulcro-css.css :as css]

    [com.fulcrologic.semantic-ui.elements.loader.ui-loader :refer [ui-loader]]
    [com.fulcrologic.semantic-ui.modules.dimmer.ui-dimmer :refer [ui-dimmer]]
    [com.fulcrologic.semantic-ui.elements.input.ui-input :refer [ui-input]]
    [com.fulcrologic.semantic-ui.elements.header.ui-header :refer [ui-header]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form :refer [ui-form]]
    [com.fulcrologic.semantic-ui.elements.label.ui-label :refer [ui-label]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal :refer [ui-modal]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-header :refer [ui-modal-header]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-content :refer [ui-modal-content]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-actions :refer [ui-modal-actions]]

    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-description :refer [ui-modal-description]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-checkbox :refer [ui-form-checkbox]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-radio :refer [ui-form-radio]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-field :refer [ui-form-field]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-input :refer [ui-form-input]]

    [com.fulcrologic.semantic-ui.modules.checkbox.ui-checkbox :refer [ui-checkbox]]
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
    [riverdb.api.mutations :as rm :refer [TxResult ui-tx-result]]
    [riverdb.application :refer [SPA]]
    [riverdb.lookup :refer [specs-map]]
    [riverdb.roles :as roles]
    [riverdb.ui.agency :refer [Agency]]
    [riverdb.ui.components :refer [ui-drag-drop-context ui-droppable ui-draggable ui-autosizer]]
    [riverdb.ui.dataviz-page :refer [DataVizPage]]
    [riverdb.ui.forms :refer [SampleTypeCodeForm]]
    [riverdb.ui.lookup-options :refer [ui-theta-options preload-options]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.routes]
    [riverdb.ui.sitevisits-page :refer [SiteVisitsPage]]
    [riverdb.ui.session :refer [ui-session Session]]
    [riverdb.ui.tac-report-page :refer [TacReportPage]]
    [riverdb.ui.util :refer
     [make-validator parse-float rui-checkbox rui-int rui-bigdec rui-input ui-cancel-save
      set-editing set-value set-value! set-refs! set-ref! set-ref set-refs get-ref-val
      get-ref-set-val lookup-db-ident db-ident->db-ref filter-param-typecode]]
    [riverdb.util :refer [nest-by]]
    [theta.log :as log :refer [debug]]
    [thosmos.util :as tu]))


;; NOTE portal table for DnD params
(def table-portal (js/document.createElement "table"))
(js/Object.assign
  (. table-portal -style) #js
    {:margin  0
     :padding 0
     :border  0
     :height  0
     :width   0})
(def tbody-portal (js/document.createElement "tbody"))
(. table-portal (appendChild tbody-portal))
(js/document.body.appendChild table-portal)


(s/def ::range number?)
(s/def ::rsd number?)
(s/def ::threshold number?)
(s/def :parameter/PrecisionCode (s/nilable (s/keys :opt-un [::range ::rsd ::threshold])))

(defn parameter-valid [form field]
  (let [v (get form field)]
    (case field
      :parameter/PrecisionCode
      (let [edn-val (try (cljs.reader/read-string v) (catch js/Object ex nil))
            {:keys [threshold range rsd]} edn-val]
        (cond
          (and (some? threshold) (or (not range) (not rsd)))
          {:msg "If Threshold is set, then both Range and RSD must also be set"}
          (and (not threshold) (some? range) (some? rsd))
          {:msg "If Threshold is not set, then either Range or RSD can be set, but not both"}
          (not (s/valid? field edn-val))
          {:msg (str "All values must be numbers")}
          :else
          true)))))

(def validator (make-validator parameter-valid))

(defn invalid-msg [props attr-k]
  (let [valid? (validator props attr-k true)]
    (when (map? valid?)
      (div {:style {:color "red" :margin 5}} (:msg valid?)))))

(def info-icon
  (dom/sup (ui-icon {:name "info" :size "small" :circular true :style {:marginLeft 5}})))

(defn ui-precision [this attr-k]
  (let [props      (comp/props this)
        this-ident (comp/get-ident this)
        str-val    (attr-k props)
        _          (debug "PRECISION STR" str-val)
        edn-val    (try (cljs.reader/read-string str-val) (catch js/Object ex nil))
        _          (debug "PRECISION EDN" edn-val)
        {:keys [threshold range rsd]} edn-val
        range      (str range)
        rsd        (str rsd)
        threshold  (str threshold)
        save-edn   (fn [edn]
                     (set-value! this attr-k (pr-str edn)))
        save-fn    (fn [k]
                     (fn [e]
                       (let [val     (parse-float (-> e .-target .-value))
                             edn-val (if val
                                       (assoc edn-val k val)
                                       (dissoc edn-val k))]
                         (save-edn edn-val))))
        _          (debug "VALID? " (validator props attr-k true))]
    (div :.field
      (dom/label {} "Precision")
      (invalid-msg props attr-k)
      (div :.ui.fields {}
        (div :.field
          (ui-popup {:trigger (label {} "Range" info-icon)}
            "Range = Max - Min of sample replicates")

          (ui-input {:className "ui small input"
                     :value     range
                     :onChange  (save-fn :range)
                     :style     {:width 80}}))

        (div :.field
          (ui-popup {:trigger (label {} "± Mean" info-icon)}
            "")

          (ui-input {:className "ui small input"
                     :value     range
                     :onChange  (save-fn :range)
                     :style     {:width 80}}))

        (div :.field
          (ui-popup {:trigger (label {} "± % Mean" info-icon)}
            "mean ± __ %")

          (ui-input {:className "ui small input"
                     :value     rsd
                     :style     {:width 80}
                     :onChange  (save-fn :rsd)}))

        (div :.field
          (ui-popup {:trigger (label {} "Threshold" info-icon)}
            "If set, '± Mean' is used when sample mean is below threshold; '± % Mean' is used when sample mean is above Threshold")

          (ui-input {:className "ui small input"
                     :value     threshold
                     :style     {:width 80}
                     :onChange  (save-fn :threshold)}))))))

(def snapshot-map (atom {}))
(def isDragOccurring (atom false))

(defsc ParamCell [this props]
  {:componentDidMount    (fn [this]
                           (let [{:keys [cellId isDragging]} (comp/props this)
                                 applySnapshot (comp/get-state this :applySnapshot)
                                 snap?         (get @snapshot-map cellId)]

                             ;(when isDragging
                             ;  (debug "DID MOUNT ParamCell" cellId "isDragging" isDragging "snap?" snap?))

                             (when snap?
                               (if isDragging
                                 (applySnapshot snap?)
                                 (swap! snapshot-map dissoc cellId)))))

   :componentWillUnmount (fn [this]
                           (let [{:keys [cellId]} (comp/props this)]
                             ;(debug "WILL UNMOUNT" cellId "@isDragOccurring" @isDragOccurring)
                             (when @isDragOccurring
                               (let [getSnapshot (comp/get-state this :getSnapshot)
                                     snap        (getSnapshot)]
                                 (swap! snapshot-map assoc cellId snap)))))

   :initLocalState       (fn [this _]
                           {:setRef        (fn [r] (gobj/set this "cell-ref" r))
                            :getSnapshot   (fn []
                                             (let [ref (gobj/get this "cell-ref")]
                                               (when ref
                                                 (let [bounds (. ref getBoundingClientRect)
                                                       snap   {:width  (. bounds -width)
                                                               :height (. bounds -height)}]
                                                   ;(debug "GET SNAPSHOT" ref snap)
                                                   snap))))

                            :applySnapshot (fn [snap]
                                             (let [ref   (gobj/get this "cell-ref")
                                                   style (when ref
                                                           (gobj/get ref "style"))]
                                               (when (and ref
                                                       (not=
                                                         (gobj/get style "width")
                                                         (:width snap)))
                                                 ;(debug "APPLY SNAPSHOT PRE" "ref" ref "style" style "snap" snap)
                                                 (gobj/set style "width" (str (:width snap) "px"))
                                                 (gobj/set style "height" (str (:height snap) "px")))))})}


  (let [{:keys [setRef]} (comp/get-state this)
        props (-> props
                (dissoc :isDragging)
                (dissoc :cellId))]
    ;(debug "RENDER ParamCell" props)
    (apply td (merge props {:ref setRef}) (comp/children this))))

(def ui-param-cell (comp/factory ParamCell))

(defsc ParamRow [this {:parameter/keys [Name Active] :as props} {:keys [provided snapshot onEdit] :as computed}]
  {}
  ;(debug "RENDER ParamRow" name)
  (let [{:keys [dragHandleProps draggableProps innerRef]} (js->clj provided :keywordize-keys true)
        isDragging (. snapshot -isDragging)]

    (tr (tu/merge-tree
          {:ref   innerRef
           :style {:backgroundColor "white"}}
          draggableProps)
      (ui-param-cell
        (tu/merge-tree
          {:style      {:width "20px"}
           :cellId     "handle"
           :isDragging isDragging}
          dragHandleProps)
        (ui-icon {:name "bars" :color "grey"}))
      (ui-param-cell
        {:cellId     "name"
         :isDragging isDragging}
        Name)
      (ui-param-cell
        {:cellId     "active"
         :isDragging isDragging}
        (str Active))
      (ui-param-cell
        {:cellId     "edit"
         :isDragging isDragging}
        (button :.ui.button.primary
          {:onClick onEdit
           :style   {:padding 5}}
          "Edit")))))
(def ui-param-row (comp/factory ParamRow))

(defsc SampleTypeForm [_ _]
  {:ident       [:org.riverdb.db.sampletypelookup/gid :db/id]
   :query       [fs/form-config-join
                 :db/id
                 :db/ident
                 :riverdb.entity/ns
                 :sampletypelookup/SampleTypeCode]
   :form-fields #{:db/id :db/ident}})

(defsc ParameterForm [this {:keys           [root/tx-result db/id]
                            :ui/keys        [editing saving]
                            :parameter/keys [Name
                                             Constituent
                                             DeviceType
                                             SampleType]
                            :as             props}
                      {:keys [cancel-new on-save i]}]
  {:ident          [:org.riverdb.db.parameter/gid :db/id]
   :query          (fn []
                     [fs/form-config-join
                      :db/id
                      :riverdb.entity/ns
                      :parameter/Active
                      :parameter/Color
                      :parameter/High
                      :parameter/Lines
                      :parameter/Low
                      :parameter/Name
                      :parameter/NameShort
                      :parameter/Order
                      :parameter/PrecisionCode
                      :parameter/Replicates
                      :parameter/ReplicatesEntry
                      :parameter/ReplicatesElide
                      ;; passed to lookup dropdowns
                      :parameter/uuid
                      :parameter/Constituent
                      :parameter/DeviceType
                      {:parameter/SampleType (comp/get-query SampleTypeForm)}
                      :ui/editing
                      :ui/saving
                      [:riverdb.theta.options/ns '_]
                      {[:root/tx-result '_] (comp/get-query TxResult)}])
   ;[:riverdb.theta.options/ns :entity.ns/constituentlookup]
   ;[:riverdb.theta.options/ns :entity.ns/samplingdevicelookup]]
   :initLocalState (fn [this props]
                     {:onEdit #(fm/toggle! this :ui/editing)})
   :initial-state  (fn [{:keys [type]}]
                     (debug "INITIAL STATE ParameterForm" type)
                     {:db/id                   (tempid/tempid)
                      :parameter/uuid          (tempid/uuid)
                      :riverdb.entity/ns       :entity.ns/parameter
                      :parameter/Name          ""
                      :ui/editing              true
                      :ui/saving               false
                      :parameter/Active        false
                      :parameter/Order         999
                      :parameter/SampleType    type})
   :form-fields    #{:db/id
                     :riverdb.entity/ns
                     :parameter/uuid
                     :parameter/Name
                     :parameter/Active
                     :parameter/Constituent
                     :parameter/DeviceType
                     :parameter/SampleType
                     :parameter/Color
                     :parameter/High
                     :parameter/Lines
                     :parameter/Low
                     :parameter/NameShort
                     :parameter/Order
                     :parameter/Replicates
                     :parameter/ReplicatesEntry
                     :parameter/ReplicatesElide
                     :parameter/PrecisionCode}}
  ;:pre-merge     (fn [{:keys [current-normalized data-tree] :as env}]
  ;                 ;(debug "PREMERGE ParameterForm" (:parameter/Active data-tree) current-normalized data-tree)
  ;                 (->> data-tree
  ;                   (clojure.walk/postwalk #(if (= % :com.fulcrologic.fulcro.algorithms.merge/not-found) nil %))
  ;                   (merge current-normalized)))}
  (let [this-ident   (comp/get-ident this)
        dirty-fields (fs/dirty-fields props true)
        dirty?       (some? (seq dirty-fields))
        param-specs  (get-in specs-map [:entity.ns/parameter :entity/attrs])
        {:keys [onEdit]} (comp/get-state this)
        {sampletypelookup-options     :entity.ns/sampletypelookup
         constituentlookup-options    :entity.ns/constituentlookup
         samplingdevicelookup-options :entity.ns/samplingdevicelookup} (:riverdb.theta.options/ns props)]
    (debug "RENDER ParameterForm" Name props)
    (if editing
      (ui-modal {:open editing}
        (ui-modal-header {:content (str "Edit Parameter: " Name)})
        (ui-modal-content {}
          (when (not (empty? tx-result))
            (ui-tx-result tx-result))
          (div :.ui.form {}
            (div :.ui.segment {}
              (ui-header {} Name)
              (div :.fields {}
                (div :.field {} (rui-input this :parameter/Name {:required true :style {:width 200}}))
                (div :.field {}
                  (ui-popup
                    {:trigger (rui-input this :parameter/NameShort
                                {:required true :style {:width 100}
                                 :label    (label {} "Short Name" info-icon)})}
                    (get-in param-specs [:parameter/NameShort :attr/doc])))

                (div :.field {} (rui-checkbox this :parameter/Active {})))
              (div :.field {}
                (dom/label {} "Sample Type")
                (ui-theta-options
                  (comp/computed
                    (merge
                      sampletypelookup-options
                      {:riverdb.entity/ns :entity.ns/sampletypelookup
                       :value             SampleType
                       :opts              {:load true}})
                    {:changeMutation `set-value
                     :changeParams   {:ident this-ident
                                      :k     :parameter/SampleType}})))
              (div :.field {}
                (dom/label {} "Constituent (Analyte | Matrix | Unit | Method | Fraction)")
                (ui-theta-options
                  (comp/computed
                    (merge
                      constituentlookup-options
                      {:riverdb.entity/ns :entity.ns/constituentlookup
                       :value             Constituent
                       :opts              {:load  true
                                           :style {:width "100%"}}})
                    {:changeMutation `set-value
                     :changeParams   {:ident this-ident
                                      :k     :parameter/Constituent}})))
              (div :.field {}
                (dom/label {} "Default Device")
                (ui-theta-options
                  (comp/computed
                    (merge
                      samplingdevicelookup-options
                      {:riverdb.entity/ns :entity.ns/samplingdevicelookup
                       :value             DeviceType
                       :opts              {:clearable true :load true}})
                    {:changeMutation `set-value
                     :changeParams   {:ident this-ident
                                      :k     :parameter/DeviceType}})))
              (div :.field
                (label {} "Data Quality")
                (div :.fields {}
                  (div :.field {} (rui-bigdec this :parameter/Low {:style {:width 80}}))
                  (div :.field {} (rui-bigdec this :parameter/High {:style {:width 80}}))
                  (div :.field {}
                    (ui-popup
                      {:trigger (rui-int this :parameter/Replicates {:style {:width 80} :label (label {} "Required Replicates" info-icon)})}
                      (get-in param-specs [:parameter/Replicates :attr/doc])))
                  (div :.field {}
                    (ui-popup
                      {:trigger (rui-int this :parameter/ReplicatesEntry {:label (label {} "Entry Replicates" info-icon) :style {:width 80}})}
                      (get-in param-specs [:parameter/ReplicatesEntry :attr/doc])))
                  (div :.field {}
                    (ui-popup
                      {:trigger (rui-checkbox this :parameter/ReplicatesElide {:label (label {} "Elide Outliers?" info-icon)})}
                      (get-in param-specs [:parameter/ReplicatesElide :attr/doc])))))
              (ui-precision this :parameter/PrecisionCode)

              #_(div :.field {}
                  (label {} "Chart Lines")
                  (div {} "coming soon ..."))
              (when saving
                "SAVING")
              (ui-cancel-save this props dirty?
                {:success-msg (str "Parameter " Name " Saved")
                 :onCancel    #(when (tempid/tempid? id)
                                 (cancel-new id))
                 :onSave      on-save})))))

      (ui-draggable {:draggableId (str id)
                     :index       i
                     :key         (str id)}
        (fn [provided snapshot]
          (comp/with-parent-context this
            (ui-param-row (comp/computed props {:provided provided :snapshot snapshot :onEdit onEdit}))))))))




(def ui-parameter-form (comp/factory ParameterForm {:keyfn :db/id}))

(fm/defmutation new-param [{:keys [proj-ident type] :as p}]
  (action [{:keys [state]}]
    (let [type-ref (db-ident->db-ref type)
          typ      (get-in @state type-ref)
          p'       (comp/get-initial-state ParameterForm {:type typ})
          p-form   (fs/add-form-config ParameterForm p')]
      (debug "MUTATION new-param" p' "type" type "type-ref" type-ref "typ" typ)
      (swap! state merge/merge-component ParameterForm p-form
        :append (conj proj-ident :projectslookup/Parameters)))))

(fm/defmutation cancel-temp-param [{:keys [proj-ident tempid]}]
  (action [{:keys [state]}]
    (let [ps  (get-in @state (conj proj-ident :projectslookup/Parameters))
          ps' (vec (remove #(= tempid (second %)) ps))]
      (debug "MUTATION cancel-new-param" tempid)
      (if (seq ps')
        (swap! state assoc-in (conj proj-ident :projectslookup/Parameters) ps')
        (swap! state update-in proj-ident dissoc :projectslookup/Parameters)))))

;(defsc ModalForm [this {:keys [open header content tx-result]}]
;  {:query [:open
;           :header
;           :content
;           {:tx-result (comp/get-query TxResult)}]}
;  (ui-modal {:open open}
;    (ui-modal-header {:content header})
;    (ui-modal-content {}
;      (when (not (empty? tx-result))
;        (ui-tx-result tx-result))
;      content)))
;(def ui-modal-form (comp/factory ModalForm))

(defn update-param-order* [state param-idents]
  (loop [i 0 st state]
    (if-let [ident (get param-idents i)]
      (recur (inc i) (assoc-in st (conj ident :parameter/Order) i))
      st)))

(defmutation reorder-params [{:keys [param-ids]}]
  (action [{:keys [state]}]
    (swap! state update-param-order* param-ids)))


(defn ui-param-list-fn [this {:keys [type params proj-ident on-save label]}]
  (let [onBeforeDragStart (fn []
                            (debug ":onBeforeDragStart" "isDragOccurring" true)
                            (reset! isDragOccurring true))
        onDragStart       #(debug ":onDragStart" %)
        onDragEnd         (fn [e]
                            (let [{:keys [source reason destination] :as result} (js->clj e :keywordize-keys true)
                                  fromIndex (:index source)
                                  toIndex   (:index destination)]
                              (debug ":onDragEnd" "FROM" fromIndex "TO" toIndex)
                              (reset! isDragOccurring false)
                              (cond
                                (or
                                  (not destination)
                                  (= reason "CANCEL"))
                                nil
                                (and
                                  (= (:droppableId destination) (:droppableId source))
                                  (= toIndex fromIndex))
                                nil
                                :else
                                (let [pids  (mapv #(comp/get-ident ParameterForm %) params)
                                      moved (get pids fromIndex)
                                      pids' (into [] (tu/vec-add toIndex moved (tu/vec-remove fromIndex pids)))]
                                  (comp/transact! this `[(reorder-params ~{:param-ids pids'})])))))]
    (debug "RENDER ParamList" label type "params" params)
    {:menuItem label
     :render   (fn []
                 (ui-tab-pane {}
                   (comp/with-parent-context this
                     (div nil
                       (ui-drag-drop-context {:onDragEnd         onDragEnd
                                              :onDragStart       onDragStart
                                              :onBeforeDragStart onBeforeDragStart}
                         (ui-table
                           {:compact "very"
                            :size    "small"
                            :style   {:minHeight "10px"}}
                           (ui-table-header nil
                             (ui-table-row nil
                               (ui-table-header-cell nil "")
                               (ui-table-header-cell nil "Name")
                               (ui-table-header-cell nil "Active")
                               (ui-table-header-cell nil "Edit")))
                           (ui-droppable
                             {:droppableId          "tableBody"
                              :getContainerForClone (fn [] tbody-portal)
                              :renderClone          (fn [provided snapshot rubric]
                                                      (let [param (get params (.. rubric -source -index))]
                                                        ;(debug "RENDER CLONE" "param" param)
                                                        (comp/with-parent-context this
                                                          (ui-param-row
                                                            (comp/computed
                                                              param
                                                              {:provided provided
                                                               :snapshot snapshot})))))}
                             (fn [provided snapshot]
                               ;(debug "PROVIDED" (gobj/getKeys provided))
                               (comp/with-parent-context this
                                 (tbody
                                   (tu/merge-tree
                                     {:ref   (. provided -innerRef)
                                      :style {}}
                                     (js->clj (. provided -droppableProps)))
                                   (map-indexed
                                     (fn [i p-data]
                                       (let [p-data (fs/add-form-config ParameterForm p-data)]
                                         (debug "param" i p-data)
                                         (ui-parameter-form
                                           (comp/computed
                                             p-data
                                             {:proj-ident proj-ident
                                              :cancel-new #(comp/transact! this `[(cancel-temp-param {:proj-ident ~proj-ident :tempid ~%})])
                                              :on-save    on-save
                                              :i          i}))))
                                     params)
                                   (. provided -placeholder)))))))
                       (button :.ui.button.primary
                         {:onClick #(comp/transact! this
                                      `[(new-param
                                          {:proj-ident ~proj-ident
                                           :type       ~type})])}
                         "Add")))))}))

(defsc ProjectForm [this {:keys [db/id ui/ready ui/editing root/tx-result] :projectslookup/keys [ProjectID Name Parameters Active Public] :as props}]
  {:ident             [:org.riverdb.db.projectslookup/gid :db/id]

   :query             [:riverdb.entity/ns
                       :db/id
                       :ui/ready
                       :ui/editing
                       fs/form-config-join
                       :projectslookup/uuid
                       :projectslookup/ProjectID
                       :projectslookup/Name
                       :projectslookup/Active
                       :projectslookup/Public
                       :projectslookup/QAPPVersion
                       :projectslookup/qappURL
                       ;{:projectslookup/AgencyRef (comp/get-query Agency)}
                       :projectslookup/AgencyRef
                       :projectslookup/Description
                       {:stationlookup/_Project (comp/get-query looks/stationlookup-sum)}
                       {:projectslookup/Parameters (comp/get-query ParameterForm)}
                       {[:root/tx-result '_] (comp/get-query TxResult)}]

   :form-fields       #{:projectslookup/ProjectID
                        :projectslookup/Name
                        :projectslookup/Description
                        :projectslookup/Active
                        :projectslookup/Public
                        :projectslookup/QAPPVersion
                        :projectslookup/qappURL
                        :projectslookup/Parameters
                        :projectslookup/AgencyRef
                        :projectslookup/uuid
                        :riverdb.entity/ns}

   :initial-state     (fn [{:keys [agencyRef]}]
                        (fs/add-form-config
                          ProjectForm
                          {:db/id                     (tempid/tempid)
                           :riverdb.entity/ns         :entity.ns/projectslookup
                           :projectslookup/uuid       (tempid/uuid)
                           :projectslookup/Parameters []
                           :projectslookup/AgencyRef  agencyRef
                           :ui/ready                  true
                           :ui/editing                false}))


   :componentDidMount (fn [this]
                        (let [props (comp/props this)]
                          #_(fm/set-value! this :ui/ready true)))}
  (let [this-ident   (comp/get-ident this)
        Parameters   (vec (riverdb.util/sort-maps-by Parameters [:parameter/Order]))
        fm-params    (filter-param-typecode :sampletypelookup.SampleTypeCode/FieldMeasure Parameters)
        obs-params   (filter-param-typecode :sampletypelookup.SampleTypeCode/FieldObs Parameters)
        lab-params   (filter-param-typecode :sampletypelookup.SampleTypeCode/Grab Parameters)
        logger-params (filter-param-typecode :sampletypelookup.SampleTypeCode/Logger Parameters)
        dirty-fields (fs/dirty-fields props true)
        dirty?       (some? (seq dirty-fields))
        _            (debug "DIRTY?" dirty? dirty-fields)
        on-save      #(do
                        (debug "SAVE!" dirty? dirty-fields)
                        (comp/transact! this
                          `[(rm/save-entity ~{:ident       this-ident
                                              :diff        dirty-fields
                                              :success-msg "Project saved"})]))]

    (debug "RENDER ProjectForm" id Parameters props)
    (if editing
      (ui-modal {:open editing :dimmer "inverted"}
        (ui-modal-header {:content (str "Edit Project: " Name)})
        (ui-modal-content {}
          (when (not (empty? tx-result))
            (ui-tx-result tx-result))
          (div :.ui.form {}
            (if editing
              (div :.fields {}
                (ui-popup
                  {:trigger (rui-input this :projectslookup/ProjectID {:label (label {} "ID" info-icon) :disabled true :required true})}
                  "This must be unique across all organizations and must remained unchanged after the project has data.  To change it contact RiverDB Support")
                (rui-input this :projectslookup/Name {:required true})
                (rui-checkbox this :projectslookup/Active {})
                (rui-checkbox this :projectslookup/Public {}))
              (div :.fields {}
                (span "ID " ProjectID)))

            (rui-input this :projectslookup/Description {:label "Description"})
            (rui-input this :projectslookup/QAPPVersion {:label "QAPP Version"})
            (rui-input this :projectslookup/qappURL {:label "QAPP Link"})

            (debug "RENDER PARAM LISTS")
            (ui-tab
              {:panes
               [
                (ui-param-list-fn this {:type       :sampletypelookup.SampleTypeCode/FieldMeasure
                                        :label      "Field Measurements"
                                        :params     fm-params
                                        :proj-ident this-ident
                                        :on-save    on-save})

                (ui-param-list-fn this {:type       :sampletypelookup.SampleTypeCode/FieldObs
                                        :label      "Field Observations"
                                        :params     obs-params
                                        :proj-ident this-ident
                                        :on-save    on-save})

                (ui-param-list-fn this {:type       :sampletypelookup.SampleTypeCode/Grab
                                        :label      "Lab Results"
                                        :params     lab-params
                                        :proj-ident this-ident
                                        :on-save    on-save})

                (ui-param-list-fn this {:type       :sampletypelookup.SampleTypeCode/Logger
                                        :label      "Logger"
                                        :params     logger-params
                                        :proj-ident this-ident
                                        :on-save    on-save})]})

            (div :.ui.segment {:style {:padding 10}}
              (div :.field {}
                (label {} "Sites")
                (div {} "...")))

            (ui-cancel-save this props dirty?
              {:onSave on-save}))))

      (div :.ui.segment {}
        (ui-header {} Name)
        (button :.ui.button.primary {:onClick #(fm/set-value! this :ui/editing true)} "Edit")))))

(def ui-project-form (comp/factory ProjectForm {:keyfn :db/id}))

(defmutation init-projects-forms [{:keys [ids]}]
  (action [{:keys [state]}]
    (debug "MUTATION" init-projects-forms)
    (swap! state
      (fn [s]
        (let [s (assoc-in s [:component/id :projects :projects] [])
              s (assoc-in s [:component/id :projects :ui/ready] true)]
          (-> (reduce
                (fn [s id]
                  (let [proj-ident [:org.riverdb.db.projectslookup/gid id]]
                    (-> s
                      (rm/sort-ident-list-by* (conj proj-ident :projectslookup/Parameters) :parameter/Order)
                      (fs/add-form-config* ProjectForm proj-ident)
                      (update-in [:component/id :projects :projects] (fnil conj []) proj-ident))))
                s ids)
            ;; cleanup the load key
            (dissoc :org.riverdb.db.projectslookup)))))))

(defn load-projs [this projs]
  (let [proj-ids (mapv :db/id projs)]
    (debug "LOAD PROJECTS" proj-ids)
    (when proj-ids
      (df/load! this :org.riverdb.db.projectslookup ProjectForm
        {:params               {:ids proj-ids}
         :post-mutation        `init-projects-forms
         :post-mutation-params {:ids proj-ids}
         :marker               ::projs
         :without              #{[:riverdb.theta.options/ns '_] [:root/tx-result '_]}}))))

(defsc Projects [this {:keys                 [ui/ready projects]
                       :riverdb.ui.root/keys [current-agency] :as props}]
  {:ident             (fn [] [:component/id :projects])
   :query             [{:projects (comp/get-query ProjectForm)}
                       {[:riverdb.ui.root/current-agency '_] (comp/get-query Agency)}
                       :ui/ready
                       [df/marker-table ::projs]]
   :initial-state     {:ui/ready true}
   :route-segment     ["projects"]
   :check-session     (fn [app session]
                        (let [valid? (:session/valid? session)
                              admin? (->
                                       (:account/auth session)
                                       :user
                                       roles/user->roles
                                       roles/admin?)
                              result (and valid? admin?)]
                          (debug "CHECK SESSION Projects" "valid?" valid? "admin?" admin? "result" result)
                          result))
   :componentDidMount (fn [this]
                        (let [props  (comp/props this)
                              agency (:riverdb.ui.root/current-agency props)
                              projs  (:agencylookup/Projects agency)]

                          (preload-options :entity.ns/constituentlookup {:filter-key :constituentlookup/MethodCode})
                          (preload-options :entity.ns/samplingdevicelookup)
                          (preload-options :entity.ns/sampletypelookup {:filter-key :db/ident})

                          (debug "DID MOUNT Projects") ; "Agency?" agency "PROJS?" projs)
                          (when agency
                            (load-projs this projs))))}

  (let [marker    (get props [df/marker-table ::projs])
        ;agencyRef (riverdb.ui.util/thing->ident (:riverdb.ui.root/current-agency props))
        agencyRef (select-keys (:riverdb.ui.root/current-agency props) [:db/id])
        onNew     (fn []
                    (let [pj (comp/get-initial-state ProjectForm {:agencyRef agencyRef})]
                      (debug "NEW PROJECT" pj)))]
    (if ready
      (div :.ui.segment
        (ui-header {:key "title"} "Projects")
        (if marker
          (ui-loader {:active true})
          [(doall
             (for [pj projects]
               (let [{:keys [db/id ui/hidden] :projectslookup/keys [Name ProjectID Active Public]} pj]
                 (ui-project-form pj))))])
        (dom/button :.ui.button {:onClick onNew} "New"))
      #_(ui-loader {:active true}))))

(def ui-projects (comp/factory Projects))
