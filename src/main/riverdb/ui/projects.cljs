(ns riverdb.ui.projects
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
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    ;[com.fulcrologic.rad.rendering.semantic-ui.decimal-field :refer [ui-decimal-input]]
    ;[com.fulcrologic.rad.type-support.decimal :as dec :refer [numeric]]
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
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
    [riverdb.application :refer [SPA]]
    [riverdb.roles :as roles]
    [riverdb.api.mutations :as rm :refer [TxResult ui-tx-result]]
    [riverdb.ui.agency :refer [Agency]]
    [riverdb.ui.components :refer [ui-drag-drop-context ui-droppable ui-draggable ui-autosizer]]
    [riverdb.ui.dataviz-page :refer [DataVizPage]]
    [riverdb.ui.forms :refer [SampleTypeCodeForm]]
    [riverdb.ui.lookup :refer [specs-map]]
    [riverdb.ui.lookup-options :refer [ui-theta-options preload-options]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.routes]
    [riverdb.ui.sitevisits-page :refer [SiteVisitsPage]]
    [riverdb.ui.session :refer [ui-session Session]]
    [riverdb.ui.tac-report-page :refer [TacReportPage]]
    [riverdb.ui.util :as ui-util :refer [make-validator parse-float rui-checkbox rui-int rui-bigdec rui-input ui-cancel-save
                                         set-editing set-value set-value! set-refs! set-ref! set-ref set-refs get-ref-val get-ref-set-val lookup-db-ident db-ident->db-ref]]
    [theta.log :as log :refer [debug]]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
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
(s/def :parameter/precisionCode (s/nilable (s/keys :opt-un [::range ::rsd ::threshold])))

(defn parameter-valid [form field]
  (let [v (get form field)]
    (case field
      :parameter/precisionCode
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
          (ui-popup {:trigger (label {} "% RSD" info-icon)}
            "% Relative Standard Deviation = (StdDev / Mean) * 100")

          (ui-input {:className "ui small input"
                     :value     rsd
                     :style     {:width 80}
                     :onChange  (save-fn :rsd)}))

        (div :.field
          (ui-popup {:trigger (label {} "Threshold" info-icon)}
            "If set, Range is used when sample mean is below threshold; % RSD is used when sample mean is above Threshold")

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

(defsc ParamRow [this {:parameter/keys [name active] :as props} {:keys [provided snapshot onEdit] :as computed}]
  {}
  ;(debug "RENDER ParamRow" name)
  (let [{:keys [dragHandleProps draggableProps innerRef]} (js->clj provided :keywordize-keys true)
        isDragging  (. snapshot -isDragging)]

    (tr (tu/merge-tree
          {:ref   innerRef
           :style {:backgroundColor "white"}}
          draggableProps)
      (ui-param-cell
        (tu/merge-tree
          {:style           {:width "20px"}
           :cellId          "handle"
           :isDragging      isDragging}
          dragHandleProps)
        (ui-icon {:name "bars" :color "grey"}))
      (ui-param-cell
        {:cellId          "name"
         :isDragging      isDragging}
        name)
      (ui-param-cell
        {:cellId          "active"
         :isDragging      isDragging}
        (str active))
      (ui-param-cell
        {:cellId          "edit"
         :isDragging      isDragging}
        (button :.ui.button.primary
          {:onClick onEdit
           :style   {:padding 5}}
          "Edit")))))
(def ui-param-row (comp/factory ParamRow))



(defsc ParameterForm [this {:keys           [root/tx-result db/id]
                            :ui/keys        [editing saving]
                            :parameter/keys [active name nameShort high low
                                             precisionCode replicates
                                             constituentlookupRef
                                             samplingdevicelookupRef
                                             sampleTypeRef]
                            :as             props}
                      {:keys [cancel-new on-save i]}]
  {:ident          [:org.riverdb.db.parameter/gid :db/id]
   :query          [fs/form-config-join
                    :db/id
                    :riverdb.entity/ns
                    :parameter/active
                    :parameter/color
                    :parameter/high
                    :parameter/lines
                    :parameter/low
                    :parameter/name
                    :parameter/nameShort
                    :parameter/order
                    :parameter/precisionCode
                    :parameter/replicates
                    :parameter/replicatesEntry
                    :parameter/replicatesElide
                    ;; passed to lookup dropdowns
                    :parameter/uuid
                    :parameter/constituentlookupRef
                    :parameter/samplingdevicelookupRef
                    :parameter/sampleTypeRef
                    :ui/editing
                    :ui/saving
                    [:riverdb.theta.options/ns '_]
                    {[:root/tx-result '_] (comp/get-query TxResult)}]
   ;[:riverdb.theta.options/ns :entity.ns/constituentlookup]
   ;[:riverdb.theta.options/ns :entity.ns/samplingdevicelookup]]
   :initLocalState (fn [this props]
                     {:onEdit #(fm/toggle! this :ui/editing)})
   :initial-state  {:db/id                   (tempid/tempid)
                    :parameter/uuid          (tempid/uuid)
                    :riverdb.entity/ns       :entity.ns/parameter
                    :parameter/name          ""
                    :ui/editing              true
                    :ui/saving               false
                    :parameter/active        false
                    :parameter/order         999
                    :parameter/sampleTypeRef :param/type}
   :form-fields    #{:db/id
                     :riverdb.entity/ns
                     :parameter/uuid
                     :parameter/name
                     :parameter/active
                     :parameter/constituentlookupRef
                     :parameter/samplingdevicelookupRef
                     :parameter/sampleTypeRef
                     :parameter/color
                     :parameter/high
                     :parameter/lines
                     :parameter/low
                     :parameter/nameShort
                     :parameter/order
                     :parameter/replicates
                     :parameter/replicatesEntry
                     :parameter/replicatesElide
                     :parameter/precisionCode}}
  ;:pre-merge     (fn [{:keys [current-normalized data-tree] :as env}]
  ;                 ;(debug "PREMERGE ParameterForm" (:parameter/active data-tree) current-normalized data-tree)
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
    ;(debug "RENDER ParameterForm" name) ;props)
    (if editing
      (ui-modal {:open editing}
        (ui-modal-header {:content (str "Edit Parameter: " name)})
        (ui-modal-content {}
          (when (not (empty? tx-result))
            (ui-tx-result tx-result))
          (div :.ui.form {}
            (div :.ui.segment {}
              (ui-header {} name)
              (div :.fields {}
                (div :.field {} (rui-input this :parameter/name {:required true :style {:width 200}}))
                (div :.field {}
                  (ui-popup
                    {:trigger (rui-input this :parameter/nameShort
                                {:required true :style {:width 100}
                                 :label    (label {} "Short Name" info-icon)})}
                    (get-in param-specs [:parameter/nameShort :attr/doc])))

                (div :.field {} (rui-checkbox this :parameter/active {})))
              (div :.field {}
                (dom/label {} "Sample Type")
                (ui-theta-options
                  (comp/computed
                    (merge
                      sampletypelookup-options
                      {:riverdb.entity/ns :entity.ns/sampletypelookup
                       :value             sampleTypeRef
                       :opts              {:load true}})
                    {:changeMutation `set-value
                     :changeParams   {:ident this-ident
                                      :k     :parameter/sampleTypeRef}})))
              (div :.field {}
                (dom/label {} "Constituent")
                (ui-theta-options
                  (comp/computed
                    (merge
                      constituentlookup-options
                      {:riverdb.entity/ns :entity.ns/constituentlookup
                       :value             constituentlookupRef
                       :opts              {:load  true
                                           :style {:width "100%"}}})
                    {:changeMutation `set-value
                     :changeParams   {:ident this-ident
                                      :k     :parameter/constituentlookupRef}})))
              (div :.field {}
                (dom/label {} "Default Device")
                (ui-theta-options
                  (comp/computed
                    (merge
                      samplingdevicelookup-options
                      {:riverdb.entity/ns :entity.ns/samplingdevicelookup
                       :value             samplingdevicelookupRef
                       :opts              {:clearable true :load true}})
                    {:changeMutation `set-value
                     :changeParams   {:ident this-ident
                                      :k     :parameter/samplingdevicelookupRef}})))
              (div :.field
                (label {} "Data Quality")
                (div :.fields {}
                  (div :.field {} (rui-bigdec this :parameter/low {:style {:width 80}}))
                  (div :.field {} (rui-bigdec this :parameter/high {:style {:width 80}}))
                  (div :.field {}
                    (ui-popup
                      {:trigger (rui-int this :parameter/replicates {:style {:width 80} :label (label {} "Required Replicates" info-icon)})}
                      (get-in param-specs [:parameter/replicates :attr/doc])))
                  (div :.field {}
                    (ui-popup
                      {:trigger (rui-int this :parameter/replicatesEntry {:label (label {} "Entry Replicates" info-icon) :style {:width 80}})}
                      (get-in param-specs [:parameter/replicatesEntry :attr/doc])))
                  (div :.field {}
                    (ui-popup
                      {:trigger (rui-checkbox this :parameter/replicatesElide {:label (label {} "Elide Outliers?" info-icon)})}
                      (get-in param-specs [:parameter/replicatesElide :attr/doc])))))
              (ui-precision this :parameter/precisionCode)

              (div :.field {}
                (label {} "Chart Lines")
                (div {} "coming soon ..."))
              (when saving
                "SAVING")
              (ui-cancel-save this props dirty?
                {:success-msg (str "Parameter " name " Saved")
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
          p'       (comp/get-initial-state ParameterForm {:type type-ref})
          p-form   (fs/add-form-config ParameterForm p')]
      (debug "MUTATION new-param" p')
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
      (recur (inc i) (assoc-in st (conj ident :parameter/order) i))
      st)))

(defmutation reorder-params [{:keys [proj-ident param-ids]}]
  (action [{:keys [state]}]
    (swap! state update-param-order* param-ids)))


(defsc ProjectForm [this {:keys [db/id ui/ready ui/editing root/tx-result] :projectslookup/keys [ProjectID Name Parameters Active Public] :as props}]
  {:ident             [:org.riverdb.db.projectslookup/gid :db/id]

   :query             [:riverdb.entity/ns
                       :db/id
                       :ui/ready
                       :ui/editing
                       fs/form-config-join
                       :projectslookup/ProjectID
                       :projectslookup/Name
                       :projectslookup/Active
                       :projectslookup/Public
                       :projectslookup/QAPPVersion
                       :projectslookup/qappURL
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
                        :projectslookup/Parameters}

   :initial-state     (fn [params]
                        (fs/add-form-config
                          ProjectForm
                          {:riverdb.entity/ns         :entity.ns/projectslookup
                           :projectslookup/Parameters []
                           :ui/ready                  true
                           :ui/editing                false}))

   :initLocalState    (fn [this props]
                        (debug "INIT LOCAL STATE ProjectForm" props)
                        {:onDragEnd         (fn [{:keys [reason destination] :as result}]
                                              (debug ":onDragEnd" result))
                         :onDragStart       #(debug ":onDragStart" %)
                         :onBeforeDragStart (fn []
                                              (debug ":onBeforeDragStart" "isDragOccurring" true)
                                              (reset! isDragOccurring true))})
   :componentDidMount (fn [this]
                        (let [props (comp/props this)]
                          #_(fm/set-value! this :ui/ready true)))}
  (let [this-ident   (comp/get-ident this)
        Parameters   (vec (riverdb.util/sort-maps-by Parameters [:parameter/order]))
        dirty-fields (fs/dirty-fields props true)
        dirty?       (some? (seq dirty-fields))
        _            (debug "DIRTY?" dirty? dirty-fields)
        {:keys [onDragEnd onDragStart onBeforeDragStart] :as state} (comp/get-state this)

        onDragEnd    (fn [e]
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
                           (let [pids  (mapv #(comp/get-ident ParameterForm %) Parameters)
                                 moved (get pids fromIndex)
                                 pids' (into [] (tu/vec-add toIndex moved (tu/vec-remove fromIndex pids)))]
                             ;(debug "BEFORE" pids "AFTER" pids')
                             (comp/transact! this `[(reorder-params ~{:proj-ident this-ident :param-ids pids'})])))))

        on-save      #(do
                        (debug "SAVE!" dirty? dirty-fields)
                        (comp/transact! this
                          `[(rm/save-entity ~{:ident       this-ident
                                              :diff        dirty-fields
                                              :success-msg "Project saved"})]))]

    (debug "RENDER ProjectForm" id ready "state" state "tx-result" tx-result)
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

            (ui-tab
              {:panes
               [{:menuItem "Field Measurements"
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
                                                                  (let [param (get Parameters (.. rubric -source -index))]
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
                                                 (fn [i p]
                                                   (let [p-data (fs/add-form-config ParameterForm p)]
                                                     ;(debug "param" i)
                                                     (ui-parameter-form
                                                       (comp/computed
                                                         p-data
                                                         {:proj-ident this-ident
                                                          :cancel-new #(comp/transact! this `[(cancel-temp-param {:proj-ident ~this-ident :tempid ~%})])
                                                          :on-save    on-save
                                                          :i          i}))))
                                                 Parameters)
                                               (. provided -placeholder)))))))
                                   (button :.ui.button.primary
                                     {:onClick #(comp/transact! this
                                                  `[(new-param
                                                      {:proj-ident ~this-ident
                                                       :type       :sampletypelookup.SampleTypeCode/FieldMeasure})])}
                                     "Add")))))}



                {:menuItem "Field Observations"
                 :render   (fn [] (ui-tab-pane {}
                                    (div :.ui.segment {:style {:padding 10}}
                                      (div :.field {}
                                        (label {} "")
                                        (div {} "...")))))}
                {:menuItem "Labs"
                 :render   (fn [] (ui-tab-pane {}
                                    (div :.ui.segment {:style {:padding 10}}
                                      (div :.field {}
                                        (label {} "")
                                        (div {} "...")))))}]})

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
    (swap! state
      (fn [s]
        (let [s (assoc-in s [:component/id :projects :projects] [])
              s (assoc-in s [:component/id :projects :ui/ready] true)]
          (-> (reduce
                (fn [s id]
                  (let [proj-ident [:org.riverdb.db.projectslookup/gid id]]
                    (-> s
                      (rm/sort-ident-list-by* (conj proj-ident :projectslookup/Parameters) :parameter/order)
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

                          (preload-options :entity.ns/constituentlookup)
                          (preload-options :entity.ns/samplingdevicelookup)
                          (preload-options :entity.ns/sampletypelookup)

                          (debug "DID MOUNT Projects") ; "Agency?" agency "PROJS?" projs)
                          (when agency
                            (load-projs this projs))))}

  (let [marker (get props [df/marker-table ::projs])]
    (if ready
      (div :.ui.segment
        (ui-header {:key "title"} "Projects")
        (if marker
          (ui-loader {:active true})
          [(doall
             (for [pj projects]
               (let [{:keys [db/id ui/hidden] :projectslookup/keys [Name ProjectID Active Public]} pj]
                 (ui-project-form pj))))]))
      #_(ui-loader {:active true}))))

(def ui-projects (comp/factory Projects))
