(ns riverdb.ui.lookup-options
  (:require
    [clojure.data]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.application :as fapp]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li a p h2 h3 button table thead
                                                tbody th tr td form label input select
                                                option span]]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-dropdown :refer [ui-form-dropdown]]
    [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :refer [ui-dropdown]]
    [com.fulcrologic.semantic-ui.elements.loader.ui-loader :refer [ui-loader]]
    [com.fulcrologic.semantic-ui.modules.dimmer.ui-dimmer :refer [ui-dimmer]]
    [goog.object :as gobj]
    [riverdb.application :refer [SPA]]
    [riverdb.api.mutations :as rm]
    [riverdb.util :refer [sort-maps-by with-index]]
    [riverdb.lookup :as look]
    [riverdb.ui.util :refer [get-ref-val get-ref-set-val]]
    [theta.log :as log :refer [debug info]]
    [thosmos.util :refer [merge-tree]]))


(defmutation theta-post-load [{target-ident                          :target-ident
                               {:keys [filter-key text-key text-fn sort-key]} :params}]
  (action [{:keys [state]}]
    (let [st      @state
          edges   (get-in st (into target-ident [:ui/thetas]))
          _       (debug "Edges" target-ident edges)
          text-fn (or (:fn text-fn) text-key)
          opts    (vec
                    (for [edge edges]
                      (let [m      (get-in st edge)
                            k      (:db/id m)
                            result {:value k :text (text-fn m)}
                            result (if (and filter-key (get m filter-key))
                                     (assoc result :filt (get m filter-key))
                                     result)
                            result (if (and sort-key (get m sort-key))
                                     (assoc result :sort (get m sort-key))
                                     result)]
                        result)))]

      (debug "POST LOAD MUTATION ThetaOptions" (second target-ident) opts)

      (swap! state update-in target-ident (fn [st]
                                            (-> st
                                              (assoc :ui/loading false)
                                              (assoc :ui/options opts)
                                              (dissoc :ui/thetas)))))))

(defsc ThetaOption [_ props]
  {:ident (fn []
            (let [ent-nm  (when-let [ent-k (:riverdb.entity/ns props)]
                            (name ent-k))
                  ident-k (keyword (str "org.riverdb.db." ent-nm) "gid")
                  ident-v (:db/id props)
                  ident   [ident-k ident-v]]
              ident))
   :query [:riverdb.entity/ns :db/id]})

(defn ent-ns->ident-k [ent-ns]
  (keyword (str "org.riverdb.db." (name ent-ns)) "gid"))

(defn load-theta-options
  ([app target-ident {:keys [text-key text-fn sort-key filter-key query-params] :as props}]
   (let [theta-k      (second target-ident)
         theta-nm     (name theta-k)
         theta-db-k   (keyword (str "org.riverdb.db." theta-nm))
         text-key     (or text-key (get-in look/specs-map [theta-k :entity/nameKey]))
         sort-key     (or sort-key text-key)
         props        (merge (or props {}) {:text-key text-key})
         isLookup?    (clojure.string/ends-with? theta-nm "lookup")
         query-params (cond-> {:limit -1 :sortField sort-key}
                        isLookup?
                        (assoc-in [:filter (keyword theta-nm "Active")] true)
                        query-params
                        (merge-tree query-params))
         query-keys   (cond-> #{}
                        text-key
                        (conj text-key)
                        sort-key
                        (conj sort-key)
                        filter-key
                        (conj filter-key)
                        (:keys text-fn)
                        (#(apply conj % (:keys text-fn))))]
     (debug "LOAD ThetaOptions" "query-params" query-params "props" props)
     (rm/merge-ident! target-ident {:ui/loading true})
     (f/load! app theta-db-k ThetaOption
       {:target               (into target-ident [:ui/thetas])
        :update-query         (fn [q]
                                (let [new-q (apply conj q query-keys)]
                                  (debug "load-theta Q" new-q)
                                  new-q))
        :params               query-params
        :post-mutation        `theta-post-load
        :post-mutation-params {:target-ident target-ident :params props}}))))



(defsc ThetaOptions [this {:keys [value query-params filter-key filter-val opts] :as props}
                     {:keys [onChange changeMutation changeParams onAddItem addParams]}]
  {:ident              [:riverdb.theta.options/ns :riverdb.entity/ns]
   :query              [:riverdb.entity/ns
                        :value
                        :ui/loading
                        :ui/options
                        :query-params
                        :filter-key
                        :filter-val
                        :text-key
                        :text-fn
                        :sort-key
                        :opts]

   :componentDidUpdate (fn [this prev-props _]
                         (let [props     (comp/props this)
                               diff      (clojure.data/diff prev-props props)
                               changed?  (some #{:query-params :filter-key} (concat (keys (first diff)) (keys (second diff))))
                               ;_         (debug "THETA OPTIONS CHANGED?" changed?)
                               ident-val (:riverdb.entity/ns props)
                               ident     [:riverdb.theta.options/ns ident-val]]
                           (when changed?
                             (load-theta-options SPA ident props))))

   :componentDidMount  (fn [this]
                         (let [props     (comp/props this)
                               ident-val (:riverdb.entity/ns props)
                               ident     [:riverdb.theta.options/ns ident-val]]
                           ;ref-props (get-in (fapp/current-state SPA) ident)]
                           (debug "MOUNTED ThetaOptions" ident-val)
                           (if (and
                                 (empty? (:ui/options props))
                                 (not (:ui/loading props))
                                 (get-in props [:opts :load]))
                             (load-theta-options SPA ident props)
                             (debug "SKIPPING LOAD ThetaOptions" ident-val))))}
  (let [this-ident (comp/get-ident this)
        theta-k    (:riverdb.entity/ns props)
        ident-k    (ent-ns->ident-k theta-k)
        theta-spec (get look/specs-map theta-k)
        text-key   (get theta-spec :entity/nameKey)
        ref-id     (get-ref-val value)
        ;ref-props  (get-in (fapp/current-state SPA) this-ident)
        {:ui/keys [options loading]} props
        {:keys [multiple clearable allowAdditions additionPosition style disabled]} opts
        show?      (not loading)
        options    (into [{:text "" :value ""}] (if (and filter-key filter-val)
                                                  (filterv #(= (:filt %) filter-val) options)
                                                  options))]
    ;(debug "RENDER ThetaOptions" theta-k "text-key:" text-key "value:" ref-id "filter-key:" filter-key "filter-val:" filter-val) ;"loading:" loading "query-params:" query-params "filter-key:" filter-key "filter-val:" filter-val)
    (ui-dropdown {:loading          (not show?)
                  :disabled         (or disabled false)
                  :search           true
                  :selection        true
                  :multiple         (or multiple false)
                  :clearable        (or clearable false)
                  :allowAdditions   (or allowAdditions false)
                  :additionPosition (or additionPosition "bottom")
                  :options          options
                  :value            ref-id
                  :autoComplete     "off"
                  :style            (merge {:width "auto" :minWidth "10em"} (or style {}))
                  :onChange         (fn [_ d]
                                      (when-let [val (-> d .-value)]
                                        (let [val     (if (= val "") nil val)
                                              ref-val (get-ref-set-val value (keyword (str "org.riverdb.db." (name theta-k)) "gid") val)]
                                          (log/debug "RefInput change" ref-val)
                                          (when changeMutation
                                            (debug "calling mutation")
                                            (let [txd `[(~changeMutation ~(assoc changeParams :v ref-val))]]
                                              (comp/transact! SPA txd)))
                                          (when onChange
                                            (debug "calling onChange" ref-val)
                                            (onChange ref-val)))))
                  :onAddItem        (fn [_ d]
                                      (let [val     (-> d .-value)
                                            ref-val (get-ref-set-val value (keyword (str "org.riverdb.db." (name theta-k)) "gid") val)]
                                        (log/debug "onAddItem" ref-val)
                                        (when onAddItem
                                          (let [tx-params (merge addParams
                                                            {:v              ref-val
                                                             :options-target (conj this-ident :ui/options)})
                                                txd       `[(~onAddItem ~tx-params)]]
                                            (debug "TXD" txd)
                                            (comp/transact! SPA txd)))))})))

(def ui-theta-options (comp/factory ThetaOptions {:keyfn :riverdb.entity/ns}))

(defn preload-options
  ([ent-ns]
   (preload-options ent-ns nil))
  ([ent-ns
    {{:keys [limit filter sortField]} :query-params
     :keys                            [text-key text-fn sort-key filter-key query-keys] :as props}]
   (let [ident     [:riverdb.theta.options/ns ent-ns]
         ref-props (get-in (fapp/current-state SPA) ident)
         {:ui/keys [options loading]} ref-props]
     (if (or (some? options) loading)
       (debug "SKIPPING PRELOAD ThetaOptions ALREADY LOADING ..." ident)
       (do (debug "PRELOAD ThetaOptions" ent-ns props)
           (load-theta-options SPA ident props))))))
