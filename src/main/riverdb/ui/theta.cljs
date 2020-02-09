(ns riverdb.ui.theta
  (:require
    [clojure.string :as str]
    [clojure.data]
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
    [riverdb.ui.lookup :as look :refer [get-refNameKey get-refNameKeys]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.lookup-options :refer [ThetaOptions ui-theta-options]]
    [riverdb.ui.dataviz-page :refer [DataVizPage]]
    [riverdb.ui.components :refer [ui-treeview]]
    [riverdb.ui.components.drag :refer [useDroppable useDraggable]]
    [riverdb.ui.components.grid-layout :as grid]
    [riverdb.ui.routes]
    [riverdb.ui.sitevisits-page :refer [SiteVisitsPage]]
    [riverdb.ui.session :refer [ui-session Session]]
    [riverdb.ui.style :as style]
    [riverdb.ui.tac-report-page :refer [TacReportPage]]
    [riverdb.ui.theta-edit :refer [ThetaEditor]]
    [riverdb.ui.user]
    [theta.log :as log :refer [debug]]
    [goog.object :as gob]
    [cljsjs.react-grid-layout]
    [nubank.workspaces.ui.core :as uc]))







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
               :whiteSpace      "nowrap"
               :display         (if show "block" "none")
               :backgroundColor "white"
               :zIndex 1000}}
      (div {:style {:marginBottom 5}}
        (dom/b {} "Select Columns"))

      (for [[_ {:attr/keys [key] :as attr}] attrs]
        (let [checked (if (some #{key} prKeys) true false)
              nm      (:attr/name attr)]
          (div {:key   key
                :style {:padding 1}}
            (ui-checkbox {:label   nm
                          :checked checked
                          :onClick (fn [_ data]
                                     (let [checked   (gob/get data "checked")
                                           new-value (vec (if checked
                                                            (conj prKeys key)
                                                            (remove #{key} prKeys)))]
                                       (when onChange
                                         (onChange new-value))))})))))))

(def ui-choose-cols (comp/factory ChooseColumns))


(defn ref-input [refKey value onChange]
  (ui-theta-options (comp/computed {:riverdb.entity/ns refKey :value value} {:onChange onChange})))

(defn default-filter-value [type]
  (case type
    :boolean false
    ""))


(defsc Filter [this {:keys [key text value type refKey] :as filter} {:keys [onChange]}]
  {:query [:key :text :value :type :refKey]}
  (let []
    (case type
      :boolean
      (ui-checkbox {:checked  (or value false)
                    :onChange #(onChange (not value))})
      :ref
      (ref-input refKey value onChange)

      ;; default
      (ui-input {:style    {:width 100}
                 :value    (or value "")
                 :onChange #(onChange (-> % .-target .-value))}))))

(def ui-filter (comp/factory Filter))

(defn add-filter [this props attr]
  (let [{:attr/keys [key name type refKey]} attr
        filters (:ui/filters (comp/props this))
        filter  {:key key :text name :type type :refKey refKey :value (default-filter-value type)}]
    (fm/set-value! this :ui/filters
      (assoc (or filters {}) key filter))))

(defn parse-filters [ui-filters]
  (debug "PARSE FILTERS" ui-filters)
  (into {}
    (for [[k {:keys [key value]}] ui-filters]
      [key value])))

(comment
  (def filters-ex [{:text "Site Visit" :key :sitevisit/SiteVisitDate :value {:< #inst "2007-01-01"
                                                                             :> #inst "2001-01-01"}}
                   {:text "Name" :key :constituentlookup/Name :type :string :value "hsdgf"}
                   {:text "Active" :key :constituentlookup/Active :type :boolean :value true}
                   {:text "Analyte" :key :constituentlookup/Analyte :type :ref :value "12312423524" :refKey :entity.ns/analytelookup}]))





(defsc ThetaRow [this {:keys [] :as props} {:keys [prKeys refNameKeys]}]
  {:ident (fn []
            (let [ent-nm  (name (get props :riverdb.entity/ns))
                  ident-k (keyword (str "org.riverdb.db." ent-nm) "gid")
                  ident-v (:db/id props)
                  ident   [ident-k ident-v]]
              ident))
   :query [:db/id :riverdb.entity/ns]}
  (tr {}
    [(doall
       (for [prKey prKeys]
         (let [refKey? (get refNameKeys prKey)
               value   (if refKey?
                         (get-in props [prKey refKey?])
                         (get props prKey))]
           (td {:key prKey} (str value)))))
     (td {:key "cols"} "")]))


(def ui-theta-row (comp/factory ThetaRow {:keyfn :db/id}))



(defmutation theta-post-load [{:keys [target-ident] :as params}]
  (action [{:keys [state]}]
    (debug "THETA POST LOAD MUTATION")
    (swap! state update-in target-ident #(-> %
                                           (assoc :ui/ready true)
                                           (assoc :ui/loading false)))))

(defn make-rowQuery [queryKeys refNameKeys]
  (let []
    (mapv #(if-let [refNameKey? (get refNameKeys %)]
             {% [:db/id refNameKey?]}
             %)
      queryKeys)))

(defn load-thetas
  ([this target-ident]

   (let [;; FIXME set in :initLocalState ???  would that be faster than a global map lookup? ...
         ;; globals
         theta-k         (second target-ident)
         theta-nm        (name theta-k)
         theta-db-k      (keyword (str "org.riverdb.db." theta-nm))
         theta-db-k-meta (keyword (str "org.riverdb.db." theta-nm "-meta"))
         theta-spec      (get look/specs-map theta-k)
         theta-prKeys    (get theta-spec :entity/prKeys)
         theta-attrs     (get theta-spec :entity/attrs)
         refNameKeys     (get-refNameKeys theta-attrs)

         ;; locals
         props           (comp/props this)
         {:ui/keys [prKeys filters limit offset sortField sortOrder]} props

         ;; if we have user prefs for display cols, otherwise use the spec prKeys
         queryKeys       (or (not-empty prKeys) theta-prKeys)

         query           (make-rowQuery queryKeys refNameKeys)
         filters         (parse-filters filters)
         refSortField?   (get refNameKeys sortField)
         _               (debug "REF SORT FIELD" refSortField?)
         params          {:filter    filters
                          :limit     limit
                          :offset    offset
                          :sortField (if refSortField? {sortField refSortField?} sortField)
                          :sortOrder sortOrder}]

     (debug "LOAD THETA META" target-ident theta-db-k-meta params)
     (f/load! this theta-db-k-meta nil
       {;:parallel true
        :params {:filter filters :meta-count true :limit 1}
        :target (into target-ident [:ui/list-meta])})
     (debug "SET QUERY ThetaRow" (apply conj [:db/id :riverdb.entity/ns] query))
     (comp/set-query! this ThetaRow {:query (apply conj [:db/id :riverdb.entity/ns] query)})
     (debug "LOAD THETA" params)
     (try
       (f/load! this theta-db-k ThetaRow
         {;:parallel             true
          :target               (into target-ident [:thetas])
          :params               params
          :post-mutation        `theta-post-load
          :post-mutation-params {:target-ident target-ident}})
       (catch js/Object ex (debug "LOAD ERROR" ex))))))


(defsc ThetaList [this {:keys [thetas] :ui/keys [ready filters showChooseCols prKeys limit offset loading list-meta sortField sortOrder] :as props}]
  {:ident              [:riverdb.theta.list/ns :riverdb.entity/ns]
   :query              [:riverdb.entity/ns
                        :ui/ready
                        :ui/showChooseCols
                        :ui/prKeys
                        :ui/filters
                        :ui/limit
                        :ui/offset
                        :ui/loading
                        :ui/list-meta
                        :ui/sortField
                        :ui/sortOrder
                        [:riverdb.theta.options/ns '_] ;; needed to trigger render after a ref lookup loads itself the first time
                        {:thetas (comp/get-query ThetaRow)}]
   :componentDidUpdate (fn [this prev-props prev-state]
                         (let [props         (comp/props this)
                               ident         (comp/ident this props)
                               pdiff         (clojure.data/diff prev-props props)
                               load?         (some
                                               #{:ui/prKeys :ui/filters :ui/offset :ui/limit :ui/sortOrder :ui/sortField}
                                               (concat (keys (first pdiff)) (keys (second pdiff))))
                               reset-offset? (and load?
                                               (some
                                                 #{:ui/filters :ui/limit :ui/sortOrder :ui/sortField}
                                                 (concat (keys (first pdiff)) (keys (second pdiff)))))]
                           (debug "DID UPDATE ThetaList" ident "LOAD?" load?)
                           (when reset-offset?
                             (fm/set-value! this :ui/offset 0))
                           (when load?
                             (do
                               (fm/set-value! this :ui/loading true)
                               (load-thetas this ident)))))
   :componentDidMount  (fn [this]
                         (let [props (comp/props this)
                               ident (comp/ident this props)]
                           (debug "DID MOUNT ThetaList" ident)
                           (if (empty? (:thetas props))
                             (load-thetas this ident)
                             (fm/set-value! this :ui/ready true))))
   :route-segment      ["list" :theta-ns]
   :will-enter         (fn [app {:keys [theta-ns] :as params}]
                         (debug "WILL ENTER ThetaList" params)
                         (let [ident-val   (keyword "entity.ns" theta-ns)
                               route-ident [:riverdb.theta.list/ns ident-val]]
                           (dr/route-deferred route-ident
                             #(let [theta-k      (keyword "entity.ns" theta-ns)
                                    theta-spec   (get look/specs-map theta-k)
                                    theta-prKeys (:entity/prKeys theta-spec)
                                    app-state    (fapp/current-state SPA)
                                    userPrefs    (get-in app-state (into [:user/prefs] route-ident))
                                    props        (get-in app-state route-ident)
                                    {:ui/keys [filters prKeys limit offset sortField sortOrder]} props
                                    ;; if we have user prefs for display keys, otherwise use the spec prKeys
                                    userPrKeys   (or (get userPrefs :ui/prKeys) prKeys theta-prKeys)
                                    userFilters  (or (get userPrefs :ui/filters) filters {})]
                                ;; save state where this component is about to load: [:riverdb.theta.list/ns ident-val]
                                (rm/merge-ident! route-ident {:riverdb.entity/ns ident-val
                                                              :ui/ready          false
                                                              :ui/loading        false
                                                              :ui/filters        userFilters
                                                              :ui/prKeys         userPrKeys
                                                              :ui/limit          (or limit 15)
                                                              :ui/offset         (or offset 0)})
                                (dr/target-ready! app route-ident)))))
   :css                [[:.red {:backgroundColor "red"}
                         :.blue {:backgroundColor "blue"}]]}
  (let [entity-k    (:riverdb.entity/ns props)
        entity-nm   (name entity-k)
        entity-spec (get look/specs-map entity-k)
        attrs       (:entity/attrs entity-spec)
        ;; select visible attrs
        prAttrs     (select-keys attrs prKeys)
        ;; find only the :ref type attrs
        refNameKeys (get-refNameKeys prAttrs)
        handleSort  (fn [field]
                      (if (not= field sortField)
                        (do
                          (fm/set-value! this :ui/sortField field)
                          (fm/set-value! this :ui/sortOrder :asc))
                        (if (= sortOrder :desc)
                          (do
                            (fm/set-value! this :ui/sortField nil)
                            (fm/set-value! this :ui/sortOrder nil))
                          (do
                            (fm/set-value! this :ui/sortOrder :desc)))))
        limit       limit
        offset      offset

        {:keys [red blue]} (css/get-classnames ThetaList)]

    (if ready
      (let [offset                 (or offset 0)
            limit                  (or limit 15)
            activePage             (if (> limit 0)
                                     (inc (/ offset limit))
                                     1)
            query-count            (get list-meta :org.riverdb.meta/query-count 0)
            totalPages             (if (> limit 0)
                                     (int (Math/ceil (/ query-count limit)))
                                     1)
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


          (div :.ui.segment {}
            (div :.ui.dimmable {:classes [(when loading "dimmed")]}
              (div :.ui.inverted.dimmer {:key "dimmer" :classes [(when loading "active")]}
                (div :.ui.text.loader "Loading"))
              (table :.ui.sortable.very.compact.mini.table {:key "wqtab" :style {:marginTop 0}}
                (thead {:key 1}
                  (tr {:key "head"}
                    [(doall
                       (for [prKey prKeys]
                         (let [{:attr/keys [name]} (prKey prAttrs)]

                           ;; NOTE draggable header
                           (th {:key     prKey
                                :classes [(when (= sortField prKey)
                                            (str "sorted " (if (= sortOrder :desc) "descending" "ascending")))]
                                :onClick #(handleSort prKey)}
                             (let [{:keys [onDragStart onDragEnd]} (useDraggable this)]
                               (dom/span :.draggable
                                 {:draggable   true
                                  :onDragStart #(onDragStart % {:type :header :text name :key prKey})
                                  :onDragEnd   onDragEnd}
                                 name))))))
                     (th {:key   "cols"
                          :style {:width 20}}
                       (button :.ui.small.secondary.basic.icon.button
                         {:key     "button"
                          :style   {:padding     0
                                    :margin      0
                                    :paddingTop  3
                                    :paddingLeft 0
                                    :width       20
                                    :height      20}
                          :onClick #(fm/toggle! this :ui/showChooseCols)}
                         (dom/i :.pointer.small.plus.icon {:style {}}))

                       (ui-choose-cols
                         (comp/computed {:ui/attrs  (:entity/attrs entity-spec)
                                         :ui/show   showChooseCols
                                         :ui/prKeys prKeys}
                           {:onChange #(fm/set-value! this :ui/prKeys %)})))]))


                ;; NOTE  ROWS !!!!
                (tbody {:key 2}
                  (doall
                    (for [theta thetas]
                      (do
                        ;(debug "THETA ROW" (pr-str theta))
                        (ui-theta-row
                          (comp/computed theta {:prKeys prKeys :refNameKeys refNameKeys}))))))))



            (div :.ui.menu
              (div :.item {}
                (dom/span {:style {}} "Results Per Page")
                (ui-input {:style        {:width 70}
                           :value        (str (if (= limit -1) "" limit))
                           :onChange     (fn [e]
                                           (let [value (-> e .-target .-value)
                                                 value (if (= value "")
                                                         -1 value)]
                                             (fm/set-integer! this :ui/limit :value value)))}))
              (let [limit (if (< limit 0) 0 limit)
                    from  (-> activePage dec (* limit) inc)
                    to    (* activePage limit)
                    to    (if (= to 0) 1 to)]

                (debug "LIMIT" limit "activePage" activePage)
                (div :.item (str "Showing " from " to " to " of " query-count " results")))

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
  (div :.ui.segment
    (h3 "Tables")
    (doall
      (for [[k v] look/specs-map]
        (let [k-nm (name k)]
          (div {:key k-nm} (dom/a {:href (str "/theta/list/" k-nm)} k-nm)))))))

(dr/defrouter ThetaRouter [this props]
  {:router-targets [Thetas ThetaList ThetaEditor]})

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
