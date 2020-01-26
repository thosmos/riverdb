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
    [riverdb.ui.routes :as routes]
    [riverdb.ui.session :refer [Session]]
    [riverdb.ui.project-years :refer [ProjectYears ui-project-years]]
    [riverdb.ui.util :refer [make-tempid]]
    [riverdb.util :refer [paginate]]
    [theta.log :as log :refer [debug info]]
    [tick.alpha.api :as t]
    [com.fulcrologic.fulcro.application :as app]
    [goog.object :as gobj]))



(defmutation theta-post-load [{:keys [target-ident text-key] :as params}]
  (action [{:keys [state]}]
    (debug "THETA POST LOAD MUTATION")
    ;; FIXME generate options and assoc-in to :ui/options
    (let [st    @state
          edges (get-in st (into target-ident [:ui/thetas]))
          opts  (vec
                  (for [edge edges]
                    (let [m (get-in st edge)
                          k (:db/id m)]
                      {:key k :value k :text (text-key m)})))]
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
  ([app target-ident text-key {:keys [filter] :as params}]
   (let [theta-k         (second target-ident)
         theta-nm        (name theta-k)
         theta-db-k      (keyword (str "org.riverdb.db." theta-nm))
         theta-db-k-meta (keyword (str "org.riverdb.db." theta-nm "-meta"))]
     (debug "LOAD THETA OPTIONS" params)
     (rm/merge-ident! target-ident {:ui/loading true})
     (f/load! app theta-db-k ThetaOption
       {;:parallel             true
        :target               (into target-ident [:ui/thetas])
        :update-query         (fn [query] (conj query text-key))
        :params               params
        :post-mutation        `theta-post-load
        :post-mutation-params {:target-ident target-ident :text-key text-key}}))))

(defsc ThetaOptions [this {:ui/keys [value] :as props} {:keys [onChange]}]
  {:ident             [:riverdb.theta.options/ns :riverdb.entity/ns]
   :query             [:riverdb.entity/ns
                       :ui/value]
   :pre-merge         (fn [{:keys [data-tree current-normalized state-map query] :as env}]
                        (debug "PRE-MERGE ThetaOptions" current-normalized query))
   :componentDidMount (fn [this]
                        (let [props     (comp/props this)
                              ident     (comp/ident this props)
                              app-state (fapp/current-state SPA)
                              ref-props (get-in app-state ident)]
                          (debug "MOUNTED ThetaOptions" ident)
                          (if (and
                                (empty? (:ui/options ref-props))
                                (not (:ui/loading ref-props)))
                            (let [theta-k     (second ident)
                                  theta-nm    (name theta-k)
                                  isLookup?   (clojure.string/ends-with? theta-nm "lookup")
                                  theta-spec  (get look/specs-map theta-k)
                                  theta-nmKey (get theta-spec :entity/nameKey)]
                              (debug "LOADING OPTIONS")
                              (load-theta-options this ident
                                theta-nmKey
                                (cond-> {:limit -1 :sortField theta-nmKey}
                                  isLookup?
                                  (assoc-in [:filter (keyword theta-nm "Active")] true))))
                            (do
                              (debug "OPTIONS ALREADY EXIST OR ARE LOADING")))))}
  (let [this-ident  (comp/get-ident this)
        theta-k     (:riverdb.entity/ns props)
        theta-spec  (get look/specs-map theta-k)
        theta-nmKey (get theta-spec :entity/nameKey)
        app-state   (fapp/current-state SPA)
        ref-props   (get-in app-state this-ident)
        {:ui/keys [options loading]} ref-props]
    (debug "RENDER ThetaOptions" this-ident "theta-k:" theta-k "nmKey:" theta-nmKey "value:" value "loading:" loading)
    (ui-dropdown {:loading      loading
                  :search       true
                  :selection    true
                  :tabIndex     -1
                  :options      options
                  :value        (or value "")

                  ;:fluid        true
                  :autoComplete "off"
                  :style        {:width "auto" :minWidth "10em"}
                  :onChange     (fn [_ d]
                                  (when-let [value (-> d .-value)]
                                    (log/debug "RefInput change" value)
                                    (when onChange
                                      (onChange value))))})))

(def ui-theta-options (comp/factory ThetaOptions {:keyfn :riverdb.entity/ns}))

(defn preload-options [ent-ns]
  (let [ident     (comp/get-ident ThetaOptions {:riverdb.entity/ns ent-ns})
        theta-nm    (name ent-ns)
        isLookup?   (clojure.string/ends-with? theta-nm "lookup")
        nameKey   (get-in look/specs-map [ent-ns :entity/nameKey])
        app-state (fapp/current-state SPA)
        ref-props (get-in app-state ident)
        {:ui/keys [options loading]} ref-props]
    (if (or (some? options) loading)
      (debug "NOT PRELOADING ...")
      (do (debug "PRELOADING" ident (first options) loading)
          (load-theta-options SPA ident nameKey
            (cond-> {:limit -1 :sortField nameKey}
              isLookup?
              (assoc-in [:filter (keyword theta-nm "Active")] true)))))))
