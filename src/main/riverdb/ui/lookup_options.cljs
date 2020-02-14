(ns riverdb.ui.lookup-options
  (:require
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.application :as fapp]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li a p h2 h3 button table thead
                                                tbody th tr td form label input select
                                                option span]]
    [com.fulcrologic.fulcro.mutations :as fm :refer [defmutation]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as uism :refer [defstatemachine]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-dropdown :refer [ui-form-dropdown]]
    [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :refer [ui-dropdown]]
    [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown-menu :refer [ui-dropdown-menu]]
    [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown-item :refer [ui-dropdown-item]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form :refer [ui-form]]
    [com.fulcrologic.semantic-ui.elements.loader.ui-loader :refer [ui-loader]]
    [com.fulcrologic.semantic-ui.modules.dimmer.ui-dimmer :refer [ui-dimmer]]
    [com.fulcrologic.semantic-ui.addons.pagination.ui-pagination :refer [ui-pagination]]
    [com.fulcrologic.semantic-ui.modules.checkbox.ui-checkbox :refer [ui-checkbox]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table :refer [ui-table]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table-body :refer [ui-table-body]]
    [riverdb.application :refer [SPA]]
    [riverdb.model.sitevisit :as sv]
    [riverdb.model.user :as user]
    [riverdb.api.mutations :as rm]
    [riverdb.util :refer [sort-maps-by with-index]]
    [riverdb.ui.components :as c :refer [ui-datepicker]]
    [riverdb.ui.lookup :as look]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.person :refer [AddPersonModal ui-add-person-modal]]
    [riverdb.ui.routes :as routes]
    [riverdb.ui.session :refer [Session]]
    [riverdb.ui.project-years :refer [ProjectYears ui-project-years]]
    [riverdb.ui.util :refer [make-tempid]]
    [riverdb.util :refer [paginate]]
    [theta.log :as log :refer [debug info]]
    [thosmos.util :refer [merge-tree]]
    [tick.alpha.api :as t]
    [com.fulcrologic.fulcro.application :as app]
    [goog.object :as gobj]))



(defmutation theta-post-load [{:keys [target-ident text-key filter-key] :as params}]
  (action [{:keys [state]}]
    (debug "THETA OPTIONS POST LOAD MUTATION filter-key" filter-key)
    (let [st    @state
          edges (get-in st (into target-ident [:ui/thetas]))
          opts  (vec
                  (for [edge edges]
                    (let [m      (get-in st edge)
                          k      (:db/id m)
                          result {:value k :text (text-key m)}
                          result (if (and filter-key (get m filter-key))
                                   (assoc result :filt (get m filter-key))
                                   result)]
                      result)))]

      (swap! state update-in target-ident (fn [st]
                                            (-> st
                                              (assoc :ui/loading false)
                                              (assoc :ui/options opts)
                                              (dissoc :ui/thetas)))))))

(defsc ThetaOption [this props]
  {:ident (fn []
            (let [ent-nm  (name (get props :riverdb.entity/ns))
                  ident-k (keyword (str "org.riverdb.db." ent-nm) "gid")
                  ident-v (:db/id props)
                  ident   [ident-k ident-v]]
              ident))
   :query [:riverdb.entity/ns :db/id]})

(defn load-theta-options
  ([app target-ident text-key filter-key {:keys [filter] :as params}]
   (let [theta-k         (second target-ident)
         theta-nm        (name theta-k)
         theta-db-k      (keyword (str "org.riverdb.db." theta-nm))
         theta-db-k-meta (keyword (str "org.riverdb.db." theta-nm "-meta"))]
     (debug "LOAD THETA OPTIONS" "filter-key" filter-key "params" params)
     (rm/merge-ident! target-ident {:ui/loading true})
     (f/load! app theta-db-k ThetaOption
       {;:parallel             true
        :target               (into target-ident [:ui/thetas])
        :update-query         (fn [q] (if filter-key (conj q filter-key text-key) (conj q text-key)))
        :params               params
        :post-mutation        `theta-post-load
        :post-mutation-params {:target-ident target-ident :text-key text-key :filter-key filter-key}}))))



(defsc ThetaOptions [this {:keys [value filter filter-key filter-val ui/options] :as props}
                     {:keys [onChange changeMutation changeParams onAddItem addParams opts]}]
  {:ident              [:riverdb.theta.options/ns :riverdb.entity/ns]
   :query              [:riverdb.entity/ns
                        :value
                        :ui/loading
                        :ui/options
                        :filter
                        :filter-key
                        :filter-val
                        {:ui/add-person-modal (comp/get-query AddPersonModal)}]
   ;:initLocalState    (fn [this props]
   ;                     (debug "INIT LOCAL STATE ThetaOptions" props))
   ;:pre-merge         (fn [{:keys [data-tree current-normalized state-map query] :as env}]
   ;                     (debug "PRE-MERGE ThetaOptions" current-normalized query))
   ;:shouldComponentUpdate (fn [this next-props next-state]
   ;                         (let [props     (comp/props this)
   ;                               ;ident     (comp/ident this props)
   ;                               ;app-state (fapp/current-state SPA)
   ;                               ;ref-props (get-in app-state ident)
   ;                               update?   (cond
   ;                                           (not= (:value props) (:value next-props))
   ;                                           true
   ;                                           (not= (:riverdb.entity/ns props) (:riverdb.entity/ns next-props))
   ;                                           true
   ;                                           :else
   ;                                           false)]
   ;                           (debug "SHOULD UPDATE? ThetaOptions" update? (comp/get-state this) next-state)
   ;                           update?))
   :load-fn            (fn [ident options filter-key]
                         (debug "LOAD FN" ident)
                         (let [theta-k     (second ident)
                               theta-nm    (name theta-k)
                               isLookup?   (clojure.string/ends-with? theta-nm "lookup")
                               theta-spec  (get look/specs-map theta-k)
                               theta-nmKey (get theta-spec :entity/nameKey)]
                           (debug "LOADING ThetaOptions" theta-k)
                           (load-theta-options SPA ident theta-nmKey filter-key
                             (cond-> (merge
                                       {:limit -1 :sortField theta-nmKey}
                                       (or options {}))))))
                               ;isLookup?
                               ;(assoc-in [:filter (keyword theta-nm "Active")] true)))))

   :componentDidUpdate (fn [this prev-props prev-state]
                         (let [props      (comp/props this)
                               diff       (clojure.data/diff prev-props props)
                               changed?   (some #{:filter :filter-key} (concat (keys (first diff)) (keys (second diff))))
                               _          (debug "THETA OPTIONS CHANGED?" changed?)
                               filter     (:filter props)
                               filter-key (:filter-key props)
                               ident-val  (:riverdb.entity/ns props)
                               ident      [:riverdb.theta.options/ns ident-val]]
                           (when changed?
                             ((comp/component-options this :load-fn) ident {:filter filter} filter-key))))


   :componentDidMount  (fn [this]
                         (let [props      (comp/props this)
                               ident-val  (:riverdb.entity/ns props)
                               filter     (:filter props)
                               filter-key (:filter-key props)
                               ident      [:riverdb.theta.options/ns ident-val] ;(comp/ident this props)]
                               app-state  (fapp/current-state SPA)
                               ref-props  (get-in app-state ident)]
                           (debug "MOUNTED ThetaOptions" ident-val)
                           (if (and
                                 (empty? (:ui/options ref-props))
                                 (not (:ui/loading ref-props))
                                 (get-in props [:opts :load]))
                             ((comp/component-options this :load-fn) ident {:filter filter} filter-key)
                             #_(let [theta-k     (second ident)
                                     theta-nm    (name theta-k)
                                     isLookup?   (clojure.string/ends-with? theta-nm "lookup")
                                     theta-spec  (get look/specs-map theta-k)
                                     theta-nmKey (get theta-spec :entity/nameKey)]
                                 (debug "LOADING ThetaOptions" ident-val)
                                 (load-theta-options this ident
                                   theta-nmKey
                                   (cond-> (merge
                                             {:limit -1 :sortField theta-nmKey}
                                             filter)
                                     isLookup?
                                     (assoc-in [:filter (keyword theta-nm "Active")] true))))
                             (debug "SKIPPING LOAD ThetaOptions" ident-val))))}
  (let [this-ident  (comp/get-ident this)
        theta-k     (:riverdb.entity/ns props)
        theta-spec  (get look/specs-map theta-k)
        theta-nmKey (get theta-spec :entity/nameKey)
        app-state   (fapp/current-state SPA)
        ref-props   (get-in app-state this-ident)
        {:ui/keys [options loading]} ref-props
        {:keys [multiple clearable allowAdditions additionPosition style]} opts
        show?       (some? (and (not loading) (not-empty options)))
        options (if (and filter-key filter-val)
                  (filterv #(= (:filt %) filter-val) options)
                  options)]
    (debug "RENDER ThetaOptions" this-ident "k:" theta-k "nmKey:" theta-nmKey "value:" value "loading:" loading "filter:" filter "filter-key:" filter-key "filter-val:" filter-val "style" style)
    ;(when add-person-modal
    ;  (ui-add-person-modal
    ;    (comp/computed add-person-modal
    ;      {:post-save-mutation `post-save-mutation
    ;       :post-save-params   {:form-ident  this-ident}})))
    (ui-dropdown {:loading          (not show?)
                  :search           true
                  :selection        true
                  :multiple         (or multiple false)
                  :clearable        (or clearable false)
                  :allowAdditions   (or allowAdditions false)
                  :additionPosition (or additionPosition "bottom")
                  :options          options
                  :value            (or value "")
                  :autoComplete     "off"
                  :style            (or style {:width "auto" :minWidth "10em"})
                  :onChange         (fn [_ d]
                                      (when-let [value (-> d .-value)]
                                        (let [value  (if (= value "") nil value)]
                                          (log/debug "RefInput change" value)
                                          (when changeMutation
                                            (debug "calling mutation")
                                            (let [txd   `[(~changeMutation ~(assoc changeParams :v value))]]
                                              (comp/transact! SPA txd)))
                                          (when onChange
                                            (debug "calling onChange")
                                            (onChange value)))))
                  :onAddItem        (fn [_ d]
                                      (let [value (-> d .-value)]
                                        (log/debug "onAddItem" value)
                                        (when onAddItem
                                          (let [tx-params (merge addParams
                                                            {:v              value
                                                             :options-target (conj this-ident :ui/options)})
                                                txd       `[(~onAddItem ~tx-params)]]
                                            (debug "TXD" txd)
                                            (comp/transact! SPA txd)))))})))

(def ui-theta-options (comp/factory ThetaOptions {:keyfn :riverdb.entity/ns}))

(defn preload-options
  ([ent-ns]
   (preload-options ent-ns nil))
  ([ent-ns params]
   (preload-options ent-ns params nil))
  ([ent-ns params filter-key]
   (let [ident     (comp/get-ident ThetaOptions {:riverdb.entity/ns ent-ns})
         theta-nm  (name ent-ns)
         isLookup? (clojure.string/ends-with? theta-nm "lookup")
         nameKey   (get-in look/specs-map [ent-ns :entity/nameKey])
         app-state (fapp/current-state SPA)
         ref-props (get-in app-state ident)
         params    (cond-> {:limit -1 :sortField nameKey}
                     isLookup?
                     (assoc-in [:filter (keyword theta-nm "Active")] true)
                     params
                     (merge-tree params))
         {:ui/keys [options loading]} ref-props]
     (if (or (some? options) loading)
       (debug "ALREADY PRELOADING ..." ident)
       (do (debug "PRELOADING" ident (first options) loading)
           (load-theta-options SPA ident nameKey filter-key params))))))
