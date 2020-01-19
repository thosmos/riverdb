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


(defn gen-attr-initial-value [attr])

(defsc ThetaRow [this {:keys [ui/ready db/id] :as props} {:keys [spec]}]
  {:ident         (fn []
                    (let [ent-nm  (name (get props :riverdb.entity/ns))
                          ident-k (keyword (str "org.riverdb.db." ent-nm) "gid")
                          ident-v (:db/id props)
                          ident   [ident-k ident-v]]
                      ;(debug "GET IDENT ThetaRow" ident)
                      ident))
   :query         (fn [a b c]
                    ;(debug "GET QUERY ThetaRow" a b c)
                    [:ui/ready])
   :initial-state (fn [{:keys [spec/entity spec/global]}]
                    #_(let [{:entity/keys [ns prKeys attrs]} entity
                            ent-nm   (:entity/name entity)
                            attr-kvs (for [attr attrs]
                                       (let [{:attr/keys [key cardinality type ref identity]} attr
                                             attr-nm (:attr/name attr)
                                             attr-k  (keyword ent-nm attr-nm)]
                                         [attr-k nil]))]
                        (into {} attr-kvs))
                    {:ui/ready false})}
  (let [attrs  (:entity/attrs spec)
        prKeys (:entity/prKeys spec)]
    (tr {}
      (doall
        (for [prKey prKeys]
          (td {:key prKey} (str (get props prKey))))))))
;:componentDidMount (fn [this]
;                     (let [load-k-str  (str "org.riverdb.db." theta-ns)
;                           load-k-meta (keyword (str load-k-str "-meta"))
;                           load-k      (keyword load-k-str)]
;                       (load-theta this load-k load-k-meta ThetaRow {})))})

(def ui-theta-row (comp/factory ThetaRow {:keyfn :db/id}))

(defmutation theta-post-load [{:keys [target-ident] :as params}]
  (action [{:keys [state]}]
    (debug "THETA POST LOAD MUTATION")
    (swap! state assoc-in (into target-ident [:ui/ready]) true)))

(defn load-thetas
  ([app target-ident k k-meta theta-class {:keys [filter limit offset sort] :as params}]
   (let []
     (debug "LOAD THETA" params)
     (f/load! app k-meta nil
       {;:parallel true
        :params {:filter filter :meta-count true :limit 1}
        :target (into target-ident [:ui/list-meta])})
     (f/load! app k theta-class
       {;:parallel             true
        :target               (into target-ident [:thetas])
        :params               params
        :post-mutation        `theta-post-load
        :post-mutation-params {:target-ident target-ident}}))))


(defn cancelEvs [e]
  (-> e (.stopPropagation))
  (-> e (.preventDefault)))

;; {:type :header :text "AgencyCode" :key :agencylookup/AgencyCode :ref HTMLElement}
(def dragging (atom nil))


(defn useDroppable
  ([this state-k] (useDroppable this state-k nil))
  ([this state-k type-k]
   (debug "USE DROPPABLE")
   (let [set-state!  (fn [new-state] (comp/update-state! this update :drop-state merge new-state))
         isType      (fn [type] (if type-k (= type type-k) true))

         ;; updates every render / state change
         {:keys [text type] :as data} (comp/get-state this :drag-state)
         instate     (state-k (comp/get-state this :drop-state))
         isover      (and (isType type) instate)

         onDragEnter (fn [e fn]
                       (let [] ;{:keys [text type] :as data} @dragging]
                         (debug "DRAG ENTER ATOM" state-k type text)
                         (when (isType type)
                           (set-state! {state-k true})
                           (when fn (fn data)))))
         onDragOver  (fn [e fn]
                       (let []
                         (debug "DRAG OVER ATOM" state-k type text)
                         (cancelEvs e)
                         (when (isType type)
                           (when (not instate)
                             (set-state! {state-k true}))
                           (when fn (fn data)))))
         onDragLeave (fn [e fn]
                       (let []
                         (debug "DRAG LEAVE ATOM" state-k type text)
                         (cancelEvs e)
                         (when isover
                           (set-state! {state-k nil})
                           (when fn (fn data)))))
         onDrop      (fn [e fn]
                       (let []
                         (debug "DROP ATOM" state-k type text)
                         (cancelEvs e)
                         (when isover
                           (set-state! {state-k nil})
                           (when fn (fn data)))))]
     {:isOver isover :onDragEnter onDragEnter :onDragOver onDragOver :onDragLeave onDragLeave :onDrop onDrop})))

(defn useDraggable [this]
  (debug "USE DRAGGABLE")
  (let [set-state! (fn [new-state] (comp/update-state! this update :drag-state merge new-state))
        dragStart  (fn [ev data]
                     (debug "DRAG START" data)
                     (-> ev (.stopPropagation))
                     (-> ev .-dataTransfer (.setData "text/plain" (:text data)))
                     (set-state! data))
        dragEnd    (fn [ev]
                     (cancelEvs ev)
                     (let [data (comp/get-state this :drag-state)]
                       (debug "DRAG END" (:text data))
                       (when data
                         (set-state! nil))))]
    {:onDragStart dragStart :onDragEnd dragEnd}))

(defsc ChooseColumns [this props {:ui/keys [show]}]
  {}
  (let [attrs (:entity/attrs props)]
    (div {:key   "popup"
          :style {:position        "absolute"
                  :right           0
                  :padding         5
                  :display         (if show "block" "none")
                  :backgroundColor "yellow"}}
      (for [[k {:attr/keys [key name type ref]}] attrs]
        ;; FIXME draggable columns
        (let [{:keys [onDragStart onDragEnd]} (useDraggable this)]
          (div {:key         name
                :draggable   true
                :onDragStart #(onDragStart % {:type :column :text name :key key})
                :onDragEnd   onDragEnd
                :style       {:padding 2}}
            name))))))

(def ui-choos-cols (comp/factory ChooseColumns))


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

(defsc Filter [this props]
  {:query         [:key
                   :text
                   :filt
                   {:attr (comp/get-query Attribute)}]
   :initial-state (fn [{:keys [key text filt attr]}]
                    {:key  key
                     :text text
                     :filt filt
                     :attr (comp/get-initial-state Attribute attr)})})
(def ui-filter (comp/factory Filter))



(defsc ThetaList [this {:keys [thetas] :ui/keys [ready filters showChooseCols prKeys] :as props}]
  {:ident              [:riverdb.theta.list/ns :riverdb.entity/ns]
   :query              [:riverdb.entity/ns
                        :ui/ready
                        :ui/showChooseCols
                        :ui/prKeys
                        {:spec/entity (comp/get-query Entity)}
                        {:ui/filters (comp/get-query Filter)}
                        {:thetas (comp/get-query ThetaRow)}]
   :componentDidUpdate (fn [this prev-props prev-state])
   :componentDidMount  (fn [this]
                         (let [props           (comp/props this)
                               ident           (comp/ident this props)
                               theta-k         (:riverdb.entity/ns props)
                               theta-nm        (name theta-k)
                               theta-db-k      (keyword (str "org.riverdb.db." theta-nm))
                               theta-db-k-meta (keyword (str "org.riverdb.db." theta-nm "-meta"))
                               theta-spec      (get look/specs-map theta-k)
                               theta-prKeys    (get theta-spec :entity/prKeys)
                               ;; if we have user prefs for display cols, otherwise use the spec prKeys
                               prKeys          (or (not-empty (:ui/prKeys props)) theta-prKeys)]
                           (debug "MOUNTED ThetaList" ident)
                           (comp/set-query! this ThetaRow {:query (into [:ui/ready :ui/prKeys :ui/showChooseCols :db/id :riverdb.entity/ns] prKeys)})
                           (if (empty? (:thetas props))
                             (load-thetas this ident theta-db-k theta-db-k-meta ThetaRow {})
                             (fm/set-value! this :ui/ready true))))
   :route-segment      ["list" :theta-ns]
   :will-enter         (fn [app {:keys [theta-ns] :as params}]
                         (debug "WILL ENTER ThetaList" params)
                         (let [ident-val   (keyword "entity.ns" theta-ns)
                               route-ident [:riverdb.theta.list/ns ident-val]]
                           (dr/route-deferred route-ident
                             #(let [theta-k      (keyword "entity.ns" theta-ns)
                                    ;; FIXME load spec over the wire at session start
                                    theta-spec   (get look/specs-map theta-k)
                                    theta-prKeys (:entity/prKeys theta-spec)
                                    state        (fapp/current-state SPA)
                                    userPrefs    (get-in state (into [:user/prefs] route-ident))
                                    ;; if we have user prefs for display keys, otherwise use the spec prKeys
                                    userPrKeys   (get userPrefs :ui/prKeys theta-prKeys)
                                    userFilters  (get userPrefs :ui/filters [])]
                                ;; save state where this component is about to load: [:riverdb.theta.list/ns ident-val]
                                (rm/merge-ident! route-ident {:riverdb.entity/ns ident-val
                                                              :spec/entity       theta-spec
                                                              :ui/ready          false
                                                              :ui/filters        userFilters
                                                              :ui/prKeys         userPrKeys})
                                (dr/target-ready! app route-ident)))))
   :css                [[:.red {:backgroundColor "red"}
                         :.blue {:backgroundColor "blue"}]]}
  (let [theta-k    (:riverdb.entity/ns props)
        theta-nm   (name theta-k)
        ;theta-spec (get look/specs-map theta-k)
        theta-spec (:spec/entity props)
        attrs      (:entity/attrs theta-spec)
        ;; select visible attrs
        prAttrs    (select-keys attrs prKeys)
        _          (debug "prAttrs" prAttrs)
        _          (debug "FILTERS" filters)
        {:keys [red blue]} (css/get-classnames ThetaList)]

    (if ready

      ;; FIXME droppable ROOT
      (let [{:keys [onDragEnter onDragOver onDragLeave onDrop isOver]} (useDroppable this :root :filter)]
        (div {:onDragOver onDragOver
              :onDrop     (fn [e]
                            (onDrop e
                              (fn [{:keys [text key]}]
                                (debug "DROP HANDLER" :root key text)
                                (fm/set-value! this :ui/filters (remove #(= (:key %) key) filters)))))}

          (div :#sv-list-menu.ui.menu {:key "list-controls"}
            (div :.item {:style {}} theta-nm)

            ;; FIXME droppable FILTERS
            (let [{:keys [onDragEnter onDragOver onDragLeave onDrop isOver]} (useDroppable this :filters :header)]
              (dom/div :.item.droppable
                {:style       {:backgroundColor (if isOver "lightblue" "default")}
                 :onDragOver  onDragOver
                 :onDragLeave onDragLeave
                 :onDragEnter onDragEnter
                 :onDrop      (fn [e]
                                (onDrop e
                                  (fn [{:keys [key text] :as data}]
                                    (debug "FILTER DROP" data)
                                    ;; if the key is in our list of visible fields
                                    (let [attr (key attrs)]
                                      (when (empty (filter #(= (:key %) key) filters))
                                        (fm/set-value! this :ui/filters
                                          (conj (or filters [])
                                            (comp/get-initial-state Filter {:key key :text text :attr attr}))))))))}

                ;{:onDragOver  (fn [ev]
                ;                (cancelEvs ev)
                ;                (let [{:keys [text type]} @dragging]
                ;                  (debug "DRAG OVER Filters" text type)
                ;                  (when-not isOverFilters
                ;                    (comp/set-state! this {:isOverFilters true}))))
                ; :onDrop      (fn [ev]
                ;                (cancelEvs ev)
                ;                (let [field (-> ev .-dataTransfer (.getData "text"))]
                ;                  (debug "DROP Filters" field)
                ;                  (when isOverFilters
                ;                    (comp/set-state! this {:isOverFilters false}))
                ;                  (when (strKeys field)
                ;                    (fm/set-value! this :ui/filters (conj (or filters #{}) field)))))
                ; :onDragLeave (fn [ev]
                ;                (when isOverFilters
                ;                  (comp/set-state! this {:isOverFilters false}))
                ;                (debug "DRAG LEAVE Filters"))

                "Filters"
                (doall
                  (for [[k {:keys [text] :as filt}] filters]
                    ;; FIXME draggable filter
                    (let [{:keys [onDragStart onDragEnd]} (useDraggable this)]
                      (div :.item {:key         k
                                   :draggable   true
                                   :onDragStart #(onDragStart % {:type :filter :text text :key k})
                                   :onDragEnd   onDragEnd}
                        text (ui-filter filt)))))))

            (div :.item
              (button {:key "create" :onClick #(debug "CREATE")} "New"))

            (div :.item.right
              (ui-pagination
                {:id            "paginator"
                 :activePage    1
                 :boundaryRange 1
                 :onPageChange  #(debug %)
                 :size          "mini"
                 :siblingRange  1
                 :totalPages    1})))

          (div :.ui.segment
            (table :.ui.very.compact.mini.table {:key "wqtab"}
              (thead {:key 1}

                (let [] ;{:keys [isOver onDragOver onDragLeave onDrop]} (useDroppable this :headers :column)]
                  ;; FIXME droppable header
                  (tr #_{:style       {:backgroundColor (if isOver "lightblue" "white")}
                         :onDragOver  onDragOver
                         :onDragLeave onDragLeave
                         :onDrop      #(onDrop %
                                         (fn [{:keys [text] :as data}]
                                           (debug "ATOM CALLBACK" data)
                                           (when (strKeys text)
                                             (debug "ADD TO PR-KEYS" text))))}
                    (doall
                      (for [prKey prKeys]
                        (let [{:attr/keys [name]} (prKey prAttrs)]

                          (debug "HEADER" prKey name)
                          ;; FIXME draggable header
                          (th {:key prKey}
                            (let [{:keys [onDragStart onDragEnd]} (useDraggable this)]
                              (dom/span :.draggable
                                {:draggable   true
                                 :onDragStart #(onDragStart % {:type :header :text name :key prKey})
                                 :onDragEnd   onDragEnd}
                                ;:onDragStart #(dragStart % {:text (name prKey) :type :header})
                                ;:onDragEnd   dragEnd}
                                name))))))

                    (div {:style {:padding   0
                                  :margin    0
                                  :marginTop 1
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
                      (ui-choos-cols (comp/computed theta-spec {:ui/show showChooseCols}))))))



              (tbody {:key 2}
                (doall
                  (for [theta thetas]
                    (ui-theta-row (comp/computed theta {:spec theta-spec})))))))))

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
