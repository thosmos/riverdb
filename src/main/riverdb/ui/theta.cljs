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
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h1 h2 h3 button label span select option]]
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
    [goog.object :as gob]))






(defn gen-attr-initial-value [attr])

(defsc ThetaRow [this {:keys [ui/ready db/id] :as props} {:keys [spec]}]
  {:ident         (fn []
                    (let [ent-nm  (name (get props :riverdb.entity/ns))
                          ident-k (keyword (str "org.riverdb.db." ent-nm) "gid")
                          ident-v (:db/id props)
                          ident   [ident-k ident-v]]
                      (debug "GET IDENT ThetaRow" ident)
                      ident))
   :query         (fn [a b c]
                    (debug "GET QUERY ThetaRow" a b c)
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
    (dom/li
      ;(str prKeys)
      (doall
        (for [prKey prKeys]
          (dom/div (str (get-in attrs [prKey :attr/name])) ": " (str (get props prKey))))))))
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

(defsc ThetaList [this {:keys [ui/ready thetas] :as props}]
  {:ident              [:riverdb.theta.list/ns :riverdb.entity/ns]
   :query              [:spec/entity :spec/global :riverdb.entity/ns :ui/ready
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
                               theta-prKeys    (get theta-spec :entity/prKeys)]
                           (debug "MOUNTED ThetaList" ident props)
                           (comp/set-query! this ThetaRow {:query (into [:ui/ready :db/id :riverdb.entity/ns] theta-prKeys)})
                           (load-thetas this ident theta-db-k theta-db-k-meta ThetaRow {})))
   ;(fm/set-value! this :ui/ready true)))
   :route-segment      ["list" :theta-ns]
   :will-enter         (fn [app {:keys [theta-ns] :as params}]
                         (debug "WILL ENTER ThetaList" params)
                         (let [ident-val   (keyword "entity.ns" theta-ns)
                               route-ident [:riverdb.theta.list/ns ident-val]]
                           (dr/route-deferred route-ident
                             #(do
                                (rm/merge-ident! route-ident {:riverdb.entity/ns ident-val
                                                              :ui/ready          false
                                                              :thetas            []})
                                (dr/target-ready! app route-ident)))))}
  (let [theta-k    (:riverdb.entity/ns props)
        theta-nm   (name theta-k)
        theta-spec (get look/specs-map theta-k)]
    (if ready
      (div {}
        (div :#sv-list-menu.ui.menu {:key "list-controls"}
          (div :.item {:style {}} theta-nm)
          (div :.item "Filters")
          (div :.item "Active:" com.fulcrologic.fulcro.dom.html-entities/nbsp
            (ui-checkbox {}))
          (div :.item
            (ui-input {:placeholder "AgencyCode"}))
          (div :.item
            (ui-input {:placeholder "Name"}))
          #_(div :.item
              (span {:key "site" :style {}}
                "Site: "
                (select {:style    {:width "150px"}
                         :value    ""
                         :onChange #(let [st (.. % -target -value)
                                          st (if (= st "")
                                               nil
                                               st)]
                                      (debug "set site" st))}
                  ;(fm/set-value! this :ui/site st))}
                  (into
                    [(option {:value "" :key "none"} "")] []
                    #_(doall
                        (for [{:keys [db/id stationlookup/StationName stationlookup/StationID]} sites]
                          (option {:value id :key id} (str StationID ": " StationName))))))))
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
        (dom/ul
          (doall
            (for [theta thetas]
              (ui-theta-row (comp/computed theta {:spec theta-spec}))))))
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
    (div :.ui.container
      (if ready
        (div :.ui.segment
          (ui-theta-router router))

        (ui-loader {:active true})))))
