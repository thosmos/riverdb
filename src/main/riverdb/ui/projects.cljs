(ns riverdb.ui.projects
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h3 button label]]
    [com.fulcrologic.fulcro.dom.html-entities :as ent]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.application :as fapp]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as uism :refer [defstatemachine]]
    [com.fulcrologic.fulcro.mutations :as fm :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.semantic-ui.elements.loader.ui-loader :refer [ui-loader]]
    [com.fulcrologic.semantic-ui.modules.dimmer.ui-dimmer :refer [ui-dimmer]]
    [com.fulcrologic.semantic-ui.elements.input.ui-input :refer [ui-input]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form :refer [ui-form]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-checkbox :refer [ui-form-checkbox]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-field :refer [ui-form-field]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-input :refer [ui-form-input]]
    [com.fulcrologic.semantic-ui.modules.checkbox.ui-checkbox :refer [ui-checkbox]]
    [riverdb.application :refer [SPA]]
    [riverdb.roles :as roles]
    [riverdb.api.mutations :as rm]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.dataviz-page :refer [DataVizPage]]
    [riverdb.ui.components :refer [ui-treeview]]
    [riverdb.ui.routes]
    [riverdb.ui.sitevisits-page :refer [SiteVisitsPage]]
    [riverdb.ui.session :refer [ui-session Session]]
    [riverdb.ui.tac-report-page :refer [TacReportPage]]
    [riverdb.ui.user]
    [theta.log :as log :refer [debug]]
    [com.fulcrologic.fulcro.components :as om]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [goog.object :as gob]
    [com.fulcrologic.fulcro.data-fetch :as df]))


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

(defsc ParamForm [this {:constituentlookup/keys [Name]}]
  {:ident [:org.riverdb.db.constituentlookup/gid :db/id]
   :query [fs/form-config-join
           :db/id
           :riverdb.entity/ns
           :constituentlookup/Active
           :constituentlookup/AnalyteCode
           :constituentlookup/ConstituentCode
           :constituentlookup/ConstituentRowID
           {:constituentlookup/DeviceType (comp/get-query looks/samplingdevicelookup)}
           :constituentlookup/FractionCode
           :constituentlookup/HighValue
           :constituentlookup/LowValue
           :constituentlookup/MatrixCode
           :constituentlookup/MaxValue
           :constituentlookup/MethodCode
           :constituentlookup/MinValue
           :constituentlookup/Name
           :constituentlookup/UnitCode]}
  (div Name))
(def ui-param-form (comp/factory ParamForm {:keyfn :db/id}))

(defn update-value! [this k value]
  (fm/set-value! this k value)
  (comp/transact! this `[(fs/mark-complete! {:entity-ident nil :field ~k})]))

(defn rui-input [this k opts]
  (let [props   (comp/props this)
        value   (get props k)
        label   (get opts :label (name k))
        control (get opts :control "input")]
    (ui-form-field
      (merge opts
        {:label    label
         :control  control
         :value    value
         :onChange (fn [e]
                     (let [new-v (.. e -target -value)]
                       (debug "rui-input" k new-v)
                       (update-value! this k new-v)))}))))

(defn rui-checkbox [this k opts]
  (let [props (comp/props this)
        value (get props k)
        label (get opts :label (name k))]
    (div :.field {:key k}
      (dom/label {} label)
      (ui-checkbox (merge opts
                     {:checked  (or value false)
                      :onChange #(let [value (not value)]
                                   (log/debug "change" k value)
                                   (update-value! this k value))})))))

(defsc ProjectForm [this {:keys [db/id ui/show] :projectslookup/keys [Name Parameters] :as props}]
  {:ident         [:org.riverdb.db.projectslookup/gid :db/id]
   :query         [fs/form-config-join
                   :db/id
                   :ui/show
                   :projectslookup/ProjectID
                   :projectslookup/Name
                   :projectslookup/Active
                   :projectslookup/Public
                   :projectslookup/QAPPVersion
                   :projectslookup/AgencyRef
                   :riverdb.entity/ns
                   {:stationlookup/_Project (comp/get-query looks/stationlookup)}
                   {:projectslookup/Parameters (comp/get-query ParamForm)}]
   :form-fields   #{:projectslookup/ProjectID
                    :projectslookup/Name
                    :projectslookup/Active
                    :projectslookup/Public
                    :projectslookup/QAPPVersion}
   :initial-state (fn [params]
                    (fs/add-form-config
                      ProjectForm
                      {:ui/show true}))}
  (let [dirty? (some? (seq (fs/dirty-fields props false)))]
    (ui-treeview {:collapsed (not show)
                  :nodeLabel (dom/span {:style {} :onClick #(println "CLICK LABEL")} Name)
                  :onClick   #(do (fm/set-value! this :ui/show (not show)))}
      (div :.ui.segment
        (div :.fields {}
          (rui-input this :projectslookup/ProjectID {:label "ID" :required true})
          (rui-input this :projectslookup/Name {:required true})
          (rui-checkbox this :projectslookup/Active {})
          (rui-checkbox this :projectslookup/Public {}))
        (rui-input this :projectslookup/QAPPVersion {:label "QAPP"})
        (dom/h5 "Parameters"
          (doall
            (for [p Parameters]
              (ui-param-form p))))

        (dom/button :.ui.button.secondary
          {:disabled (not dirty?)
           :onClick  #(do
                        (debug "CANCEL!" dirty?)
                        (comp/transact! this
                          `[(rm/reset-form ~{:ident (comp/get-ident this)})]))} "Cancel")

        (dom/button :.ui.button.primary
          {:disabled (not dirty?)
           :onClick  #(do
                        (debug "SAVE!" dirty?)
                        (comp/transact! this
                          `[(rm/save-entity ~{:ident (comp/get-ident this)
                                              :diff  (fs/dirty-fields props false)})]))} "Save")))))




(def ui-project-form (comp/factory ProjectForm {:keyfn :db/id}))

(defsc ProjectRow [this {:keys [db/id] :projectslookup/keys [Name Parameters] :as props}]
  {:ident [:org.riverdb.db.projectslookup/gid :db/id]
   :query [:db/id
           :projectslookup/Active
           :projectslookup/ProjectID
           :projectslookup/Name
           :projectslookup/Public
           {:projectslookup/Parameters (comp/get-query ParamForm)}]}
  (let []
    (div Name)))


(defsc ProjectList [this {:keys [db/id ui/show] :projectslookup/keys [Name Parameters] :as props}]
  {:ident         (fn [] [:component/id :projects-list])
   :query         [:db/id
                   :ui/show
                   :projectslookup/ProjectID
                   :projectslookup/Name
                   :projectslookup/Active
                   :projectslookup/Public
                   :projectslookup/QAPPVersion
                   :projectslookup/AgencyRef
                   :riverdb.entity/ns
                   {:stationlookup/_Project (comp/get-query looks/stationlookup)}
                   {:projectslookup/Parameters (comp/get-query ParamForm)}]
   :form-fields   #{:projectslookup/ProjectID
                    :projectslookup/Name
                    :projectslookup/Active
                    :projectslookup/Public
                    :projectslookup/QAPPVersion}
   :initial-state (fn [params]
                    (fs/add-form-config
                      ProjectForm
                      {:ui/show true}))}
  (let [dirty? (some? (seq (fs/dirty-fields props false)))]
    (ui-treeview {:collapsed (not show)
                  :nodeLabel (dom/span {:style {} :onClick #(println "CLICK LABEL")} Name)
                  :onClick   #(do (fm/set-value! this :ui/show (not show)))}
      (div :.ui.segment
        (div :.fields {}
          (rui-input this :projectslookup/ProjectID {:label "ID" :required true})
          (rui-input this :projectslookup/Name {:required true})
          (rui-checkbox this :projectslookup/Active {})
          (rui-checkbox this :projectslookup/Public {}))
        (rui-input this :projectslookup/QAPPVersion {:label "QAPP"})
        (dom/h5 "Parameters"
          (doall
            (for [p Parameters]
              (ui-param-form p))))

        (dom/button :.ui.button.secondary
          {:disabled (not dirty?)
           :onClick  #(do
                        (debug "CANCEL!" dirty?)
                        (comp/transact! this
                          `[(rm/reset-form ~{:ident (comp/get-ident this)})]))} "Cancel")

        (dom/button :.ui.button.primary
          {:disabled (not dirty?)
           :onClick  #(do
                        (debug "SAVE!" dirty?)
                        (comp/transact! this
                          `[(rm/save-entity ~{:ident (comp/get-ident this)
                                              :diff  (fs/dirty-fields props false)})]))} "Save")))))

(defmutation init-projects-forms [{:keys []}]
  (action [{:keys [state]}]
    (swap! state
      (fn [s]
        (let [ids (keys (get s :org.riverdb.db.projectslookup/gid))
              s   (assoc-in s [:component/id :projects :projects] [])]
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
  (let [props (comp/props this)
        projs (:org.riverdb.db.projectslookup/gid props)]
    (when projs
      (let [ids (mapv first projs)]
        (df/load! this :org.riverdb.db.projectslookup ProjectForm
          {:params        {:ids ids}
           :post-mutation `init-projects-forms})))))


(defsc Projects [this {:keys [projects] :riverdb.ui.root/keys [current-project] :as props}]
  {:ident              (fn [] [:component/id :projects])
   :query              [{:projects (comp/get-query ProjectForm)}
                        {[:riverdb.ui.root/current-project '_] (comp/get-query ProjectForm)}
                        [:org.riverdb.db.projectslookup/gid '_]
                        [:riverdb.ui.root.current-agency '_]]
   :initial-state      {:projects []}
   :route-segment      ["projects"]
   :componentDidUpdate (fn [this prev-props prev-state]
                         (let [props   (comp/props this)
                               diff    (clojure.data/diff prev-props props)
                               changed (second diff)
                               _       (debug "SETTINGS DIFF" diff)]
                           (when (and
                                   (:org.riverdb.db.projectslookup/gid props)
                                   (not (:org.riverdb.db.projectslookup/gid prev-props)))
                             (load-projs this))))
   :componentDidMount  (fn [this]
                         (debug "Settings :componentDidMount")
                         (load-projs this))}

  ;(comp/transact! this `[(init-projects-forms)])))}
  (let [_ (debug "PROJECTS" projects "KEYS" (keys props))]
    (div :.ui.container
      (div :.ui.segment
        (h3 "Projects")
        (ui-form {:key "form" :size "tiny"}
          [(doall
             (for [pj projects]
               (let [{:keys [db/id ui/hidden] :projectslookup/keys [Name ProjectID Active Public]} pj]
                 (ui-project-form pj))))])))))

(def ui-projects (comp/factory Projects))
