(ns riverdb.ui.theta
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.application :as fapp]
    [com.fulcrologic.fulcro-css.css :as css]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h1 h2 h3 button label span select option
                                                table thead tbody th tr td]]
    [com.fulcrologic.fulcro-css.localized-dom :as ldom]
    [com.fulcrologic.fulcro.dom.html-entities :as htmle]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as fm :refer [defmutation]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as uism :refer [defstatemachine]]
    [com.fulcrologic.semantic-ui.elements.loader.ui-loader :refer [ui-loader]]
    [com.fulcrologic.semantic-ui.modules.dimmer.ui-dimmer :refer [ui-dimmer]]
    [com.fulcrologic.semantic-ui.elements.input.ui-input :refer [ui-input]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form :refer [ui-form]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-checkbox :refer [ui-form-checkbox]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-field :refer [ui-form-field]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-input :refer [ui-form-input]]
    [com.fulcrologic.semantic-ui.modules.checkbox.ui-checkbox :refer [ui-checkbox]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-dropdown :refer [ui-form-dropdown]]
    [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :refer [ui-dropdown]]
    [com.fulcrologic.semantic-ui.modules.dimmer.ui-dimmer :refer [ui-dimmer]]
    [com.fulcrologic.semantic-ui.addons.pagination.ui-pagination :refer [ui-pagination]]
    [com.fulcrologic.semantic-ui.modules.checkbox.ui-checkbox :refer [ui-checkbox]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table :refer [ui-table]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table-body :refer [ui-table-body]]
    [riverdb.application :refer [SPA]]
    [riverdb.roles :as roles]
    [riverdb.api.mutations :as rm]
    [riverdb.ui.lookup :as look]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.lookup-options :refer [ThetaOptions ui-theta-options]]
    [riverdb.ui.dataviz-page :refer [DataVizPage]]
    [riverdb.ui.components :refer [ui-treeview]]
    [riverdb.ui.routes]
    [riverdb.ui.sitevisits-page :refer [SiteVisitsPage]]
    [riverdb.ui.session :refer [ui-session Session]]
    [riverdb.ui.tac-report-page :refer [TacReportPage]]
    [riverdb.ui.user]
    [theta.log :as log :refer [debug]]
    [theta.ui.dnd :refer [ui-droppable]]
    [goog.object :as gob]
    [cljsjs.react-grid-layout]))



(def WidthProvider js/ReactGridLayout.WidthProvider)
(def Responsive js/ReactGridLayout.Responsive)
(def GridWithWidth (WidthProvider Responsive))








(defn cancelEvs [e]
  (-> e (.stopPropagation))
  (-> e (.preventDefault)))

;; {:type :header :text "AgencyCode" :key :agencylookup/AgencyCode :ref HTMLElement}
(def dragging (atom nil))


(defn useDroppable
  ([this state-k] (useDroppable this state-k nil))
  ([this state-k type-k]
   ;(debug "USE DROPPABLE")
   (let [set-state!  (fn [new-state] (comp/update-state! this update :drop-state merge new-state))
         isType      (fn [type] (if type-k (= type type-k) true))

         ;; updates every render / state change
         {:keys [text type] :as data} (comp/get-state this :drag-state)
         instate     (state-k (comp/get-state this :drop-state))
         isover      (and (isType type) instate)
         ;_           (debug "UPDATING DROPPABLE STATE" state-k "TYPE" type "INSTATE" instate "ISOVER" isover "ISTYPE" (isType type))

         onDragEnter (fn [e fn]
                       (let [] ;{:keys [text type] :as data} @dragging]
                         ;(debug "DRAG ENTER ATOM" state-k type text "ISOVER" isover "ISTYPE" (isType type))
                         (when (isType type)
                           (set-state! {state-k true})
                           (when fn (fn data)))))
         onDragOver  (fn [e fn]
                       (let []
                         ;(debug "DRAG OVER ATOM" state-k type text "ISOVER" isover "ISTYPE" (isType type))
                         (cancelEvs e)
                         (when (isType type)
                           (when (not instate)
                             (set-state! {state-k true}))
                           (when fn (fn data)))))
         onDragLeave (fn [e fn]
                       (let []
                         ;(debug "DRAG LEAVE ATOM" state-k type text "ISOVER" isover "ISTYPE" (isType type))
                         (cancelEvs e)
                         (when isover
                           (set-state! {state-k nil})
                           (when fn (fn data)))))
         onDrop      (fn [e fn]
                       (let []
                         ;(debug "DROP ATOM" state-k type text "ISOVER" isover "ISTYPE" (isType type))
                         (cancelEvs e)
                         ;(-> e (.stopPropagation))
                         (when isover
                           (set-state! {state-k nil})
                           (when fn (fn data)))))]
     {:isOver isover :onDragEnter onDragEnter :onDragOver onDragOver :onDragLeave onDragLeave :onDrop onDrop})))

(defn useDraggable [this]
  ;(debug "USE DRAGGABLE")
  (let [set-state! (fn [new-state] (comp/update-state! this update :drag-state merge new-state))
        dragStart  (fn [ev data]
                     ;(debug "DRAG START" data)
                     (-> ev (.stopPropagation))
                     (-> ev .-dataTransfer (.setData "text/plain" (:text data)))
                     (set-state! data))
        dragEnd    (fn [ev]
                     ;(cancelEvs ev)
                     (let [data (comp/get-state this :drag-state)]
                       ;(debug "DRAG END" data)
                       (set-state! nil)))]
    {:onDragStart dragStart :onDragEnd dragEnd}))

(defsc ChooseColumns [this {:ui/keys [show attrs prKeys]} {:keys [onChange]}]
  {:query [:ui/show
           :ui/attrs
           :ui/prKeys]}
  (let []
    (div :.ui.segment.raised
      {:key   "popup"
       :style {:position        "absolute"
               :right           0
               :margin          0
               :padding         20
               :white-space     "nowrap"
               :display         (if show "block" "none")
               :backgroundColor "white"}}
      (div {:style {:marginBottom 5}}
        (dom/b {} "Select Columns"))

      (for [[k {:attr/keys [key] :as attr}] attrs]
        (let [checked (if (some #{key} prKeys) true false)
              nm      (:attr/name attr)]
          (div {:key   key
                :style {:padding 1}}
            (ui-checkbox {:label   nm
                          :checked checked
                          :onClick (fn [_ data]
                                     (let [data      (js->clj data)
                                           checked   (get data "checked")
                                           new-value (vec (if checked
                                                            (conj prKeys key)
                                                            (remove #{key} prKeys)))]
                                       (when onChange
                                         (onChange new-value))))})))))))

(def ui-choose-cols (comp/factory ChooseColumns))


(defsc Attribute [this props]
  {:ident :attr/key
   :query [:attr/key
           :attr/name
           :attr/type
           :attr/cardinality
           :attr/ref
           :attr/refkey]})

(defsc Entity [this props]
  {:ident :entity/ns
   :query [:entity/ns
           :entity/name
           :entity/lookup
           :entity/prKeys
           {:entity/attrs (comp/get-query Attribute)}]})


(defn ref-input [refKey value onChange]
  (ui-theta-options (comp/computed {:riverdb.entity/ns refKey :ui/value value} {:onChange onChange})))

(defn default-filter-value [type]
  (case type
    :boolean false
    ""))

(defsc Filter [this {:keys [key text value type refKey] :as filter} {:keys [onChange]}]
  {:query [:key :text :value :type :refKey]}
  (let []
    ;(debug "RENDER FILTER" key type value)
    (case type
      :boolean
      (ui-checkbox {:checked  (or value false)
                    :onChange #(onChange (not value))})
      :ref
      (ref-input refKey value onChange)
      (ui-input {:style    {:width 100}
                 :value    (or value "")
                 :onChange #(onChange (-> % .-target .-value))}))))

(def ui-filter (comp/factory Filter))

(defn add-filter [this props attr]
  (let [{:attr/keys [key name type refKey]} attr
        filters (:ui/filters (comp/props this))
        filter  {:key key :text name :type type :refKey refKey :value (default-filter-value type)}]
    (fm/set-value! this :ui/offset 0)
    (fm/set-value! this :ui/filters
      (assoc (or filters {}) key filter))))

(defn parse-filters [ui-filters]
  ;(debug "PARSE FILTERS" ui-filters)
  (into {}
    (for [[k {:keys [key value]}] ui-filters]
      [key value])))

(def filters-ex [{:text "Site Visit" :key :sitevisit/SiteVisitDate :value {:< #inst "2007-01-01"
                                                                           :> #inst "2001-01-01"}}
                 {:text "Name" :key :constituentlookup/Name :type :string :value "hsdgf"}
                 {:text "Active" :key :constituentlookup/Active :type :boolean :value true}
                 {:text "Analyte" :key :constituentlookup/Analyte :type :ref :value "12312423524" :refKey :entity.ns/analytelookup}])

(defsc ThetaRow [this {:keys [] :as props} {:keys [prKeys root-spec]}]
  {:ident (fn []
            (let [ent-nm  (name (get props :riverdb.entity/ns))
                  ident-k (keyword (str "org.riverdb.db." ent-nm) "gid")
                  ident-v (:db/id props)
                  ident   [ident-k ident-v]]
              ident))
   :query [:db/id :riverdb.entity/ns]}
  (tr {}
    (doall
      (for [prKey prKeys]
        (let [entity-k (get props :riverdb.entity/ns)
              prAttr   (get-in root-spec [entity-k :entity/attrs prKey])
              isRef?   (= :ref (:attr/type prAttr))
              value    (if isRef?
                         (let [refNS    (:attr/refKey prAttr)
                               valueKey (get-in root-spec [refNS :entity/nameKey])]
                           (get-in props [prKey valueKey]))
                         (get props prKey))]
          (td {:key prKey} (str value)))))))

(def ui-theta-row (comp/factory ThetaRow {:keyfn :db/id}))

(defmutation theta-post-load [{:keys [target-ident] :as params}]
  (action [{:keys [state]}]
    (debug "THETA POST LOAD MUTATION")
    (swap! state assoc-in (into target-ident [:ui/ready]) true)))

(defn load-thetas
  ([app target-ident query-keys {:keys [filter] :as params}]
   (let [theta-k         (second target-ident)
         theta-nm        (name theta-k)
         theta-db-k      (keyword (str "org.riverdb.db." theta-nm))
         theta-db-k-meta (keyword (str "org.riverdb.db." theta-nm "-meta"))]
     (debug "LOAD THETA META" target-ident theta-db-k-meta params)
     (f/load! app theta-db-k-meta nil
       {;:parallel true
        :params {:filter filter :meta-count true :limit 1}
        :target (into target-ident [:ui/list-meta])})
     (debug "SET QUERY ThetaRow" (apply conj [:db/id :riverdb.entity/ns] query-keys))
     (comp/set-query! app ThetaRow {:query (apply conj [:db/id :riverdb.entity/ns] query-keys)})
     (debug "LOAD THETA" params)
     (try
       (f/load! app theta-db-k ThetaRow
         {;:parallel             true
          :target               (into target-ident [:thetas])
          :params               params
          :post-mutation        `theta-post-load
          :post-mutation-params {:target-ident target-ident}})
       (catch js/Object ex (debug "LOAD ERROR" ex))))))


(defn make-row-query [specs-map ent-k queryKeys]
  (let [ent-spec (get specs-map ent-k)
        attrs    (get ent-spec :entity/attrs)]
    (reduce
      (fn [query qKey]
        (let [type (get-in attrs [qKey :attr/type])]
          (conj query
            (if (= type :ref)
              (let [attr    (qKey attrs)
                    refKey  (:attr/refKey attr)
                    nameKey (-> specs-map refKey :entity/nameKey)]
                {qKey (if nameKey [:db/id nameKey] [:db/id])})
              qKey))))
      [] queryKeys)))

(defsc ThetaList [this {:keys [thetas] :ui/keys [ready filters showChooseCols prKeys limit offset list-meta] :as props}]
  {:ident              [:riverdb.theta.list/ns :riverdb.entity/ns]
   :query              [:riverdb.entity/ns
                        :ui/ready
                        :ui/showChooseCols
                        :ui/prKeys
                        :ui/filters
                        :ui/limit
                        :ui/offset
                        :ui/list-meta
                        [:riverdb.theta.options/ns '_] ;; needed to trigger render after a ref lookup loads itself the first time
                        {:thetas (comp/get-query ThetaRow)}]
   :componentDidUpdate (fn [this prev-props prev-state]
                         (let [props (comp/props this)
                               pdiff (clojure.data/diff prev-props props)
                               load? (some
                                       #{:ui/prKeys :ui/filters :ui/offset :ui/limit}
                                       (concat (keys (first pdiff)) (keys (second pdiff))))]
                           (debug "DID UPDATE - LOAD?" load?)
                           (when load?
                             (let [ident      (comp/ident this props)
                                   ent-ns     (:riverdb.entity/ns props)
                                   ent-spec   (get look/specs-map ent-ns)
                                   ent-prKeys (get ent-spec :entity/prKeys)
                                   queryKeys  (or (not-empty (:ui/prKeys props)) ent-prKeys)
                                   query      (make-row-query look/specs-map ent-ns queryKeys)
                                   filters    (parse-filters (:ui/filters props))
                                   limit      (:ui/limit props)
                                   offset     (:ui/offset props)]
                               (load-thetas this ident query {:filter filters :limit limit :offset offset})))))
   :componentDidMount  (fn [this]
                         (let [props (comp/props this)
                               ident (comp/ident this props)]
                           (debug "MOUNTED ThetaList" ident)
                           (if (empty? (:thetas props))
                             (let [ent-ns     (:riverdb.entity/ns props)
                                   ent-spec   (get look/specs-map ent-ns)
                                   ent-prKeys (get ent-spec :entity/prKeys)
                                   ;; if we have user prefs for display cols, otherwise use the spec prKeys
                                   queryKeys  (or (not-empty (:ui/prKeys props)) ent-prKeys)
                                   query      (make-row-query look/specs-map ent-ns queryKeys)
                                   limit      (:ui/limit props)
                                   offset     (:ui/offset props)]
                               (load-thetas this ident query {:limit limit :offset offset}))
                             (fm/set-value! this :ui/ready true))))
   :route-segment      ["list" :theta-ns]
   :will-enter         (fn [app {:keys [theta-ns] :as params}]
                         (debug "WILL ENTER ThetaList" params)
                         (let [ident-val   (keyword "entity.ns" theta-ns)
                               route-ident [:riverdb.theta.list/ns ident-val]]
                           (dr/route-deferred route-ident
                             #(let [theta-k      (keyword "entity.ns" theta-ns)
                                    ;;FIXME load spec over the wire at session start ???
                                    ;; it'd be nice to be able to change the spec without rebuilding the UI
                                    theta-spec   (get look/specs-map theta-k)
                                    theta-prKeys (:entity/prKeys theta-spec)
                                    app-state    (fapp/current-state SPA)
                                    userPrefs    (get-in app-state (into [:user/prefs] route-ident))
                                    ;; if we have user prefs for display keys, otherwise use the spec prKeys
                                    userPrKeys   (get userPrefs :ui/prKeys theta-prKeys)
                                    userFilters  (get userPrefs :ui/filters {})]
                                ;; save state where this component is about to load: [:riverdb.theta.list/ns ident-val]
                                (rm/merge-ident! route-ident {:riverdb.entity/ns ident-val
                                                              :ui/ready          false
                                                              :ui/limit          10
                                                              :ui/offset         0
                                                              :ui/filters        userFilters
                                                              :ui/prKeys         userPrKeys})
                                (dr/target-ready! app route-ident)))))
   :css                [[:.red {:backgroundColor "red"}
                         :.blue {:backgroundColor "blue"}]]}
  (let [entity-k    (:riverdb.entity/ns props)
        entity-nm   (name entity-k)
        entity-spec (get look/specs-map entity-k)
        attrs       (:entity/attrs entity-spec)
        ;; select visible attrs
        prAttrs     (select-keys attrs prKeys)
        {:keys [red blue]} (css/get-classnames ThetaList)]

    (if ready
      (let [activePage             (inc (/ offset limit))
            query-count            (get list-meta :org.riverdb.meta/query-count 0)
            totalPages             (int (Math/ceil (/ query-count limit)))
            handlePaginationChange (fn [e t]
                                     (let [page       (-> t .-activePage)
                                           new-offset (* (dec page) limit)]
                                       (log/debug "PAGINATION" "page" page "new-offset" new-offset)
                                       (when (> new-offset -1)
                                         (fm/set-integer! this :ui/offset :value new-offset))))
            {:keys [onDragEnter onDragOver onDragLeave onDrop isOver]} (useDroppable this :root :filter)]

        ;; NOTE droppable ROOT
        (div {:onDragOver onDragOver
              :onDrop     (fn [e]
                            (onDrop e
                              (fn [{:keys [text key]}]
                                (debug "DROP HANDLER" :root key text)
                                (fm/set-value! this :ui/filters (dissoc filters key)))))}

          (div :#sv-list-menu.ui.menu {:key "list-controls"}
            (div :.item {:style {}} entity-nm)

            ;; NOTE draggable FILTER
            (let [{:keys [onDragEnter onDragOver onDragLeave onDrop isOver]} (useDroppable this :filters :header)]
              (dom/div :.item.droppable
                {:style       {:backgroundColor (if isOver "lightblue" "white")}
                 :onDragOver  onDragOver
                 :onDragLeave onDragLeave
                 :onDragEnter onDragEnter
                 :onDrop      (fn [e]
                                (onDrop e
                                  (fn [{:keys [text] :as data}]
                                    (debug "FILTER DROP" data)
                                    (let [attr (get attrs (:key data))]
                                      (add-filter this props attr)))))}

                "Filters"
                (doall
                  (for [[k {:keys [key text] :as filt-m}] filters]
                    (let [{:keys [onDragStart onDragEnd]} (useDraggable this)]
                      (div :.item {:key         key
                                   :draggable   true
                                   :onDragStart #(onDragStart % {:type :filter :text text :key key})
                                   :onDragEnd   onDragEnd}
                        text (ui-filter (comp/computed filt-m
                                          {:onChange
                                           (fn [value]
                                             (debug "ON-CHANGE FILTER" key value)
                                             (let [new-filt    (assoc filt-m :value value)
                                                   new-filters (assoc filters k new-filt)]
                                               (fm/set-value! this :ui/filters new-filters)))}))))))))


            (div :.item.right
              (button {:disabled true :key "create" :onClick #(debug "CREATE")} (str "New " entity-nm))))


          (div :.ui.segment
            (div {:style {:padding   0
                          :margin    0
                          :marginTop 1
                          :float     "right"
                          :position  "absolute"
                          :width     20
                          :height    20
                          :right     15}}
              (button :.ui.small.secondary.basic.icon.button
                {:key     "button"
                 :style   {:padding     0
                           :margin      0
                           :paddingTop  3
                           :paddingLeft 1
                           :width       20
                           :height      20}
                 :onClick #(fm/toggle! this :ui/showChooseCols)}
                (dom/i :.pointer.small.plus.icon {:style {}}))

              (ui-choose-cols
                (comp/computed {:ui/attrs  (:entity/attrs entity-spec)
                                :ui/show   showChooseCols
                                :ui/prKeys prKeys}
                  {:onChange #(fm/set-value! this :ui/prKeys %)})))


            (table :.ui.very.compact.mini.table {:key "wqtab" :style {:marginTop 0}}
              (thead {:key 1}
                (tr {:key "head"}
                  (doall
                    (for [prKey prKeys]
                      (let [{:attr/keys [name]} (prKey prAttrs)]

                        ;; NOTE draggable header
                        (th {:key prKey}
                          (let [{:keys [onDragStart onDragEnd]} (useDraggable this)]
                            (dom/span :.draggable
                              {:draggable   true
                               :onDragStart #(onDragStart % {:type :header :text name :key prKey})
                               :onDragEnd   onDragEnd}
                              name))))))))

              ;; NOTE  ROWS !!!!
              (tbody {:key 2}
                (doall
                  (for [theta thetas]
                    (ui-theta-row (comp/computed theta {:root-spec look/specs-map :prKeys prKeys}))))))
            (div :.ui.menu
              (div :.item
                (ui-input {:type     "text" :label "Results Per Page" :defaultValue limit
                           :onChange (fn [e]
                                       (fm/set-integer! this :ui/limit :event e))}))
              (div :.item.right
                (ui-pagination
                  {:id            "paginator"
                   :activePage    activePage
                   :boundaryRange 1
                   :onPageChange  handlePaginationChange
                   :size          "mini"
                   :siblingRange  1
                   :totalPages    totalPages}))))))

      (ui-loader {:active true}))))

(defsc Thetas [this {:keys [ui/ready] :as props}]
  {:ident         (fn [] [:component/id :thetas])
   :query         [:ui/ready]
   :initial-state {:ui/ready true}
   :route-segment ["index"]}
  (div
    (h3 "Thetas")
    (doall
      (for [[k v] look/specs-map]
        (let [k-nm (name k)]
          (div {:key k-nm} (dom/a {:href (str "/theta/list/" k-nm)} k-nm)))))))

(dr/defrouter ThetaRouter [this props]
  {:router-targets [Thetas ThetaList]})

(def ui-theta-router (comp/factory ThetaRouter))


(defsc ThetaRoot [this {:keys [ui/ready theta/router] :as props}]
  {:ident              (fn [] [:component/id :theta-root])
   :query              [:ui/ready
                        {:theta/router (comp/get-query ThetaRouter)}]
   :initial-state      {:ui/ready     false
                        :theta/router {}}
   :route-segment      ["theta"]
   :componentDidUpdate (fn [this prev-props prev-state])
   :componentDidMount  (fn [this]
                         (let [props (comp/props this)]
                           ;(dr/change-route-relative this Theta "index")
                           (fm/set-value! this :ui/ready true)))}
  (let [_ (debug "Theta!" ready)]
    (div :.ui.container {}
      (if ready
        (ui-theta-router router)

        (ui-loader {:active true})))))
