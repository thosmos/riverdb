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
    [com.fulcrologic.semantic-ui.collections.form.ui-form-checkbox :refer [ui-form-checkbox]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-radio :refer [ui-form-radio]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-field :refer [ui-form-field]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-input :refer [ui-form-input]]
    [com.fulcrologic.semantic-ui.modules.checkbox.ui-checkbox :refer [ui-checkbox]]
    [com.fulcrologic.semantic-ui.modules.tab.ui-tab :refer [ui-tab]]
    [com.fulcrologic.semantic-ui.modules.tab.ui-tab-pane :refer [ui-tab-pane]]
    [com.fulcrologic.semantic-ui.elements.icon.ui-icon :refer [ui-icon]]
    [com.fulcrologic.semantic-ui.modules.popup.ui-popup :refer [ui-popup]]
    [goog.object :as gob]
    [riverdb.application :refer [SPA]]
    [riverdb.roles :as roles]
    [riverdb.api.mutations :as rm]
    [riverdb.ui.agency :refer [Agency]]
    [riverdb.ui.dataviz-page :refer [DataVizPage]]
    [riverdb.ui.lookup :refer [specs-map]]
    [riverdb.ui.lookup-options :refer [ui-theta-options preload-options]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.routes]
    [riverdb.ui.sitevisits-page :refer [SiteVisitsPage]]
    [riverdb.ui.session :refer [ui-session Session]]
    [riverdb.ui.tac-report-page :refer [TacReportPage]]
    [riverdb.ui.util :as uiutil :refer [make-validator parse-float rui-checkbox rui-int rui-bigdec rui-input ui-cancel-save
                                        set-editing set-value! set-refs! set-ref! get-set-val]]
    [theta.log :as log :refer [debug]]))

;(cljs.reader/register-tag-parser! 'bigdec numeric)

;; TASK per group

(def qapp-requirements
  {:H2O_Temp {:precision  {:unit 1.0}
              :exceedance {:high 20.0}}
   :H2O_Cond {:precision {:unit 10.0}}
   :H2O_DO   {:precision  {:percent 10.0}
              :exceedance {:low 7.0}}
   :H2O_pH   {:precision  {:unit 0.4}
              :exceedance {:low  6.5
                           :high 8.5}}
   :H2O_Turb {:precision {:percent   10.0
                          :unit      0.6
                          :threshold 10.0}}
   :H2O_PO4  {:precision {:percent   20.0
                          :unit      0.06
                          :threshold 0.1}}
   :H2O_NO3  {:precision {:percent   20.0
                          :unit      0.06
                          :threshold 0.1}}})

;; FIXME per group

(def param-config
  {:Air_Temp     {:order 0 :count 1 :name "Air_Temp"}
   :H2O_Temp     {:order 1 :count 3 :name "H2O_Temp"}
   :H2O_Cond     {:order 2 :count 3 :name "Cond"}
   :H2O_DO       {:order 3 :count 3 :name "DO"}
   :H2O_pH       {:order 4 :count 3 :name "pH"}
   :H2O_Turb     {:order 5 :count 3 :name "Turb"}
   :H2O_NO3      {:order 6 :count 3 :name "NO3" :optional true}
   :H2O_PO4      {:order 7 :count 3 :name "PO4" :optional true}
   :H2O_Velocity {:elide? true}})


(defn field-attrs
  "A helper function for getting aspects of a particular field."
  [component field]
  (let [form         (comp/props component)
        entity-ident (comp/get-ident component form)
        id           (str (first entity-ident) "-" (second entity-ident))
        is-dirty?    (fs/dirty? form field)
        clean?       (not is-dirty?)
        validity     (fs/get-spec-validity form field)
        is-invalid?  (= :invalid validity)
        value        (get form field "")]
    {:dirty?   is-dirty?
     :ident    entity-ident
     :id       id
     :clean?   clean?
     :validity validity
     :invalid? is-invalid?
     :value    value}))


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
                     ;:disabled  (not (or range? thresh?))
                     :onChange  (save-fn :range)
                     :style     {:width 80}}))

        (div :.field
          (ui-popup {:trigger (label {} "% RSD" info-icon)}
            "% Relative Standard Deviation = (StdDev / Mean) * 100")

          (ui-input {:className "ui small input"
                     :value     rsd
                     ;:disabled  (not (or rds? thresh?))
                     :style     {:width 80}
                     :onChange  (save-fn :rsd)}))

        (div :.field
          (ui-popup {:trigger (label {} "Threshold" info-icon)}
            "If set, Range is used when sample mean is below threshold; % RSD is used when sample mean is above Threshold")

          (ui-input {:className "ui small input"
                     :value     threshold
                     ;:disabled  (not thresh?)
                     :style     {:width 80}
                     :onChange  (save-fn :threshold)}))))))


(defsc ParameterForm [this {:ui/keys        [editing saving]
                            :parameter/keys [active name nameShort high low
                                             precisionCode replicates
                                             constituentlookupRef
                                             samplingdevicelookupRef
                                             sampleTypeRef]
                            :as             props}]
  {:ident         [:org.riverdb.db.parameter/gid :db/id]
   :query         [fs/form-config-join
                   :db/id
                   :riverdb.entity/ns
                   :parameter/active
                   :parameter/color
                   :parameter/high
                   :parameter/lines
                   :parameter/low
                   :parameter/name
                   :parameter/nameShort
                   :parameter/precisionCode
                   :parameter/replicates
                   :parameter/replicatesEntry
                   :parameter/replicatesElide
                   :parameter/uuid
                   ;; passed to lookup dropdowns
                   :parameter/constituentlookupRef
                   :parameter/samplingdevicelookupRef
                   :parameter/sampleTypeRef
                   :ui/editing
                   :ui/saving]
   ;[:riverdb.theta.options/ns :entity.ns/constituentlookup]
   ;[:riverdb.theta.options/ns :entity.ns/samplingdevicelookup]]
   :initial-state {:riverdb.entity/ns :entity.ns/parameter
                   :parameter/name    ""
                   :ui/editing        false
                   :ui/saving         false
                   :parameter/active  false}
   :form-fields   #{:parameter/constituentlookupRef
                    :parameter/samplingdevicelookupRef
                    :parameter/sampleTypeRef
                    :parameter/active
                    :parameter/color
                    :parameter/high
                    :parameter/lines
                    :parameter/low
                    :parameter/name
                    :parameter/nameShort
                    :parameter/replicates
                    :parameter/replicatesEntry
                    :parameter/replicatesElide
                    :parameter/precisionCode}
   :pre-merge     (fn [{:keys [current-normalized data-tree] :as env}]
                    (debug "PREMERGE ParameterForm" (:parameter/active data-tree) current-normalized data-tree)
                    (->> data-tree
                      (clojure.walk/postwalk #(if (= % :com.fulcrologic.fulcro.algorithms.merge/not-found) nil %))
                      (merge current-normalized)))}
  (let [dirty?         (some? (seq (fs/dirty-fields props false)))
        param-specs    (get-in specs-map [:entity.ns/parameter :entity/attrs])
        get-lookup-val (fn [value]
                         (cond
                           (map? value)
                           (:db/id value)
                           (vector? value)
                           (second value)
                           :else ""))]
    (if editing
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
              {:riverdb.entity/ns :entity.ns/sampletypelookup
               :value             (get-lookup-val sampleTypeRef)
               :opts              {:load true}}
              {:onChange #(do
                            (debug "OPTION CHANGE Parameter sampleTypeRef" %)
                            (set-ref! this :parameter/sampleTypeRef (get-set-val sampleTypeRef %)))})))
        (div :.field {}
          (dom/label {} "Constituent")
          (ui-theta-options
            (comp/computed
              {:riverdb.entity/ns :entity.ns/constituentlookup
               :value             (get-lookup-val constituentlookupRef)
               :opts              {:load true}}
              {:onChange #(do
                            (debug "OPTION CHANGE Parameter Constituent" %)
                            (set-ref! this :parameter/constituentlookupRef (get-set-val constituentlookupRef %)))})))
        (div :.field {}
          (dom/label {} "Default Device")
          (ui-theta-options
            (comp/computed
              {:riverdb.entity/ns :entity.ns/samplingdevicelookup
               :value             (get-lookup-val samplingdevicelookupRef)
               :opts              {:clearable true :load true}}
              {:onChange #(do
                            (debug "OPTION CHANGE Default Device" %)
                            (set-ref! this :parameter/samplingdevicelookupRef (get-set-val samplingdevicelookupRef %)))})))
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
          {:cancelAlwaysOn true}))
      (div {:style {:padding 0}}
        (div :.ui.horizontal.list {}
          (div :.ui.item {}
            (dom/a {:onClick #(fm/toggle! this :ui/editing)} name))
          #_(div :.ui.item {}
              (button :.ui.mini.primary.button {:onClick #(fm/toggle! this :ui/editing)} "Edit")))))))
;(:constituentlookup/Name constituentlookupRef)))))))



(def ui-parameter-form (comp/factory ParameterForm {:keyfn :db/id}))


(defsc ProjectForm [this {:keys [db/id ui/ready ui/editing] :projectslookup/keys [ProjectID Name Parameters Active Public] :as props}] ;Parameters
  {:ident             [:org.riverdb.db.projectslookup/gid :db/id]
   :query             [fs/form-config-join
                       :db/id
                       :ui/ready
                       :ui/editing
                       :projectslookup/ProjectID
                       :projectslookup/Name
                       :projectslookup/Active
                       :projectslookup/Public
                       :projectslookup/QAPPVersion
                       :projectslookup/qappURL
                       :projectslookup/AgencyRef
                       :riverdb.entity/ns
                       {:stationlookup/_Project (comp/get-query looks/stationlookup-sum)}
                       {:projectslookup/Parameters (comp/get-query ParameterForm)}]
   :form-fields       #{:projectslookup/ProjectID
                        :projectslookup/Name
                        :projectslookup/Active
                        :projectslookup/Public
                        :projectslookup/QAPPVersion
                        :projectslookup/qappURL
                        :projectslookup/Parameters}
   ;:pre-merge         (fn [{:keys [data-tree] :as env}]
   ;                     (debug "PREMERGE ProjectForm" (keys data-tree) (:projectslookup/Active data-tree))
   ;                     #_(postwalk prim/nillify-not-found data-tree)
   ;                     env)
   :initial-state     (fn [params]
                        (fs/add-form-config
                          ProjectForm
                          {:riverdb.entity/ns         :entity.ns/projectslookup
                           :projectslookup/Parameters []
                           :ui/ready                  true
                           :ui/editing                false}))
   :componentDidMount (fn [this]
                        (let [props (comp/props this)]
                          #_(fm/set-value! this :ui/ready true)))}
  (let [dirty? (some? (seq (fs/dirty-fields props false)))
        _      (debug "DIRTY?" dirty? (fs/dirty-fields props false))]
    (debug "RENDER ProjectForm" id ready)
    (if editing
      (div :.ui.segment
        (ui-header {} Name)
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

        (rui-input this :projectslookup/QAPPVersion {:label "QAPP Version"})
        (rui-input this :projectslookup/qappURL {:label "QAPP Link"})

        (ui-tab
          {:panes
           [{:menuItem "Field Measurements"
             :render   (fn [] (ui-tab-pane {}
                                (comp/with-parent-context this
                                  (doall
                                    (vec (for [p Parameters]
                                           (let [p-data (fs/add-form-config ParameterForm p)]
                                             (ui-parameter-form p-data))))))))}
            {:menuItem "Field Observations"
             :render   (fn [] (ui-tab-pane {}
                                (div :.ui.segment {:style {:padding 10}}
                                  (div :.field {}
                                    (label {} "")
                                    (div {} "coming soon ...")))))}
            {:menuItem "Grabs"
             :render   (fn [] (ui-tab-pane {}
                                (div :.ui.segment {:style {:padding 10}}
                                  (div :.field {}
                                    (label {} "")
                                    (div {} "coming soon ...")))))}
            {:menuItem "Stations"
             :render   (fn [] (ui-tab-pane {}
                                (div :.ui.segment {:style {:padding 10}}
                                  (div :.field {}
                                    (label {} "")
                                    (div {} "coming soon ...")))))}]})




        (ui-cancel-save this props dirty? {:cancelAlwaysOn true}))

      (div :.ui.segment {}
        (ui-header {} Name)
        (button :.ui.button.primary {:onClick #(fm/set-value! this :ui/editing true)} "Edit")))))

(def ui-project-form (comp/factory ProjectForm {:keyfn :db/id}))


(defmutation init-projects-forms [{:keys []}]
  (action [{:keys [state]}]
    (swap! state
      (fn [s]
        (let [ids (keys (get s :org.riverdb.db.projectslookup/gid))
              s   (assoc-in s [:component/id :projects :projects] [])
              s   (assoc-in s [:component/id :projects :ui/ready] true)]
          (-> (reduce
                (fn [s id]
                  (let [proj-ident [:org.riverdb.db.projectslookup/gid id]]
                    (-> s
                      (fs/add-form-config* ProjectForm proj-ident)
                      (update-in [:component/id :projects :projects] (fnil conj []) proj-ident))))
                s ids)
            ;; cleanup the load key
            (dissoc :org.riverdb.db.projectslookup)))))))

(defmutation nop [{:keys []}]
  (action [{:keys [state]}]
    (debug "NOP")
    (swap! state (fn [s]
                   s))))

(defn load-projs [this]
  (let [props    (comp/props this)
        ;app-state    (fapp/current-state SPA)
        ;agency       (:riverdb.ui.root/current-agency props)
        ;agency-ident (comp/get-ident agency)
        ;proj-idents  (get-in app-state (conj agency-ident :projectslookup/_AgencyRef))
        agency   (:riverdb.ui.root/current-agency props)
        projs    (:projectslookup/_AgencyRef agency)
        proj-ids (mapv :db/id projs)]
    (debug "LOAD PROJECTS" proj-ids)
    (when proj-ids
      (df/load! this :org.riverdb.db.projectslookup ProjectForm
        {:params        {:ids proj-ids}
         :post-mutation `init-projects-forms}))))


(defsc Projects [this {:keys [ui/ready projects] :riverdb.ui.root/keys [current-agency] :as props}]
  {:ident              (fn [] [:component/id :projects])
   :query              [{:projects (comp/get-query ProjectForm)}
                        ;{[:riverdb.ui.root/current-project '_] (comp/get-query ProjectForm)}
                        ;[:org.riverdb.db.projectslookup/gid '_]
                        {[:riverdb.ui.root/current-agency '_] (comp/get-query Agency)}
                        :ui/ready]
   :initial-state      {:ui/ready true}
   :route-segment      ["projects"]
   :componentDidUpdate (fn [this prev-props prev-state]
                         (let [props   (comp/props this)
                               diff    (clojure.data/diff prev-props props)
                               changed (second diff)
                               agency? (:riverdb.ui.root/current-agency changed)]))
   ;(debug "DID UPDATE Projects agency?" agency?)
   ;(when agency?
   ;  (load-projs this))))
   :componentDidMount  (fn [this]
                         (let [props  (comp/props this)
                               agency (:riverdb.ui.root/current-agency props)
                               projs  (:projectslookup/_AgencyRef agency)]
                           ;agency-ident (comp/get-ident agency)
                           ;app-state    (fapp/current-state SPA)
                           ;projs        (get-in app-state (conj agency-ident :projectslookup/_AgencyRef))] ;props (comp/props this)]

                           (preload-options :entity.ns/constituentlookup)
                           (preload-options :entity.ns/samplingdevicelookup)
                           (preload-options :entity.ns/sampletypelookup)

                           (debug "DID MOUNT Projects.  Agency?" agency "PROJS?" projs)
                           (when agency
                             (load-projs this))))}
  ;:pre-merge         (fn [{:keys [data-tree] :as env}]
  ;                     (debug "PREMERGE Projects" (keys data-tree))
  ;                     #_(postwalk prim/nillify-not-found data-tree)
  ;                     env)}

  ;(comp/transact! this `[(init-projects-forms)])))}
  (let [;projects (get-in current-agency [:projectslookup/_AgencyRef])
        _ (debug "PROJECTS" projects "READY" ready "KEYS" (keys props))]
    (if ready
      (div :.ui.segment
        (ui-header {} "Projects")
        (ui-form {:key "form" :size "tiny"}
          [(doall
             (for [pj projects]
               (let [{:keys [db/id ui/hidden] :projectslookup/keys [Name ProjectID Active Public]} pj]
                 (ui-project-form pj))))]))
      #_(ui-loader {:active true}))))

(def ui-projects (comp/factory Projects))
