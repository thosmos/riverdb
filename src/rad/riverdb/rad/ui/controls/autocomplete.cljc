(ns riverdb.rad.ui.controls.autocomplete
  (:require
    #?@(:cljs
        [[com.fulcrologic.fulcro.dom :as dom :refer [div label input]]
         [goog.object :as gobj]
         [cljs.reader :refer [read-string]]
         [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :refer [ui-dropdown]]
         [com.fulcrologic.semantic-ui.elements.input.ui-input :refer [ui-input]]]
        :clj
        [[com.fulcrologic.fulcro.dom-server :as dom :refer [div label input]]])
    [com.fulcrologic.rad.ids :as ids]
    [com.fulcrologic.rad.ui-validation :as validation]
    [com.fulcrologic.fulcro.rendering.multiple-roots-renderer :as mroot]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.rad.options-util :as opts]
    [com.fulcrologic.rad.rendering.semantic-ui.components :refer [ui-wrapped-dropdown]]
    [com.fulcrologic.rad.attributes :as attr]
    [clojure.string :as str]
    [theta.log :as log]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as fns]))

(defsc AutocompleteQuery [_ _] {:query [:text :value]})

(defn to-js [v]
  #?(:clj  v
     :cljs (clj->js v)))

(defmutation normalize-options [{:keys [source target]}]
  (action [{:keys [state]}]
    #?(:clj true
       :cljs
            (let [options            (get @state source)
                  normalized-options (apply array
                                       (map (fn [{:keys [text value]}]
                                              #js {:text text :value (pr-str value)}) options))]
              (fns/swap!-> state
                (dissoc source)
                (assoc-in target normalized-options))))))

(defsc AutocompleteField [this {:ui/keys [search-string options] :as props} {:keys [value label onChange
                                                                                    invalid? validation-message
                                                                                    read-only?
                                                                                    clearable
                                                                                    allowAdditions
                                                                                    additionPosition
                                                                                    onAddItem]}]
  {:initLocalState    (fn [this]
                        (let [{:autocomplete/keys [debounce-ms]} (comp/props this)]
                          {:load! (opts/debounce
                                    (fn [s]
                                      (let [{id                 ::autocomplete-id
                                             :autocomplete/keys [search-key]} (comp/props this)]
                                        ;(log/debug "Autocomplete load!" "id" id "search-key" search-key "props" (comp/props this))
                                        (df/load! this search-key AutocompleteQuery
                                          {:params               {:search-string s}
                                           :post-mutation        `normalize-options
                                           :post-mutation-params {:source search-key
                                                                  :target [::autocomplete-id id :ui/options]}})))
                                    (or debounce-ms 200))}))
   :componentDidMount (fn [this]
                        (let [{id                 ::autocomplete-id
                               :autocomplete/keys [search-key]} (comp/props this)
                              value (comp/get-computed this :value)]
                          ;(log/debug "Autocomplete componentDidMount" "id" id "search-key" search-key "props" (comp/props this))
                          (when (and search-key value)
                            (df/load! this search-key AutocompleteQuery
                              {:params               {:only value}
                               :post-mutation        `normalize-options
                               :post-mutation-params {:source search-key
                                                      :target [::autocomplete-id id :ui/options]}}))))
   :query             [::autocomplete-id :ui/search-string :ui/options :autocomplete/search-key
                       :autocomplete/debounce-ms :autocomplete/minimum-input]
   :ident             ::autocomplete-id}
  (let [load! (comp/get-state this :load!)]
    (log/debug "RENDER AutocompleteField" "options" options "props" props)
    #?(:clj
       (dom/div "")
       :cljs
       (dom/div :.field {:classes [(when invalid? "error")]}
         (dom/label label (when invalid? (str " " validation-message)))
         (if read-only?
           (gobj/getValueByKeys options 0 "text")
           #_(ui-dropdown #js {:search             true
                               :options            (if options options #js [])
                               :value              (pr-str value)
                               :selection          true
                               :closeOnBlur        true
                               :openOnFocus        true
                               :selectOnBlur       false
                               :selectOnNavigation false
                               :clearable          (or clearable false)
                               :allowAdditions     (or allowAdditions false)
                               :additionPosition   (or additionPosition "bottom")
                               :onSearchChange     (fn [_ v]
                                                     (let [query (comp/isoget v "searchQuery")]
                                                       (log/debug "autocomplete.onSearchChange" query)
                                                       (load! query)
                                                       (when onChange
                                                         (onChange query))))
                               :onChange           (fn [_ v]
                                                     (when onChange
                                                       (let [val (some-> (comp/isoget v "value") read-string)]
                                                         (log/debug "autocomplete.onChange" val)
                                                         (onChange val))))
                               :onAddItem          (fn [_ v]
                                                     (when onAddItem
                                                       (let [val (comp/isoget v "value")]
                                                         (log/debug "autocomplete.onAddItem" val)
                                                         (onAddItem val))))})
           (dom/div {}
             (dom/div :.ui.input {}
               (dom/input {:type        "text"
                           :list        "tasks"
                           :placeholder "Choose a task ..."
                           :onChange    (fn [v]
                                          (log/debug "input.onChange" v)
                                          (when onChange
                                            (let [val (comp/isoget-in v ["target" "value"])]
                                              (log/debug "input.onChange" val)
                                              (onChange val))))}))
             (dom/datalist {:id "tasks"}
               (dom/option {:value "monitoring"} "monitoring")
               (dom/option {:value "lab"} "lab")
               (dom/option {:value "office"} "office")
               (dom/option {:value "field"} "field"))))))))

(def ui-autocomplete-field (comp/computed-factory AutocompleteField {:keyfn ::autocomplete-id}))

(defmutation gc-autocomplete [{:keys [id]}]
  (action [{:keys [state]}]
    (when id
      (swap! state fns/remove-entity [::autocomplete-id id]))))

(defsc AutocompleteFieldRoot [this props {:keys [env attribute]}]
  {:initLocalState        (fn [this] {:field-id (ids/new-uuid)})
   :componentDidMount     (fn [this]
                            (let [id (comp/get-state this :field-id)
                                  {:keys [attribute]} (comp/get-computed this)
                                  {:autocomplete/keys [search-key debounce-ms minimum-input]} (::form/field-options attribute)]
                              (log/debug "MOUNTED AutocompleteFieldRoot" "id" id "attribute" attribute)
                              (merge/merge-component! this AutocompleteField {::autocomplete-id           id
                                                                              :autocomplete/search-key    search-key
                                                                              :autocomplete/debounce-ms   debounce-ms
                                                                              :autocomplete/minimum-input minimum-input
                                                                              :ui/search-string           ""
                                                                              :ui/options                 #js []}))
                            (mroot/register-root! this {:initialize? true}))
   :shouldComponentUpdate (fn [_ _] true)
   :initial-state         {::autocomplete-id {}}
   :componentWillUnmount  (fn [this]
                            (comp/transact! this [(gc-autocomplete {:id (comp/get-state this :field-id)})])
                            (mroot/deregister-root! this))
   :query                 [::autocomplete-id]}
  (let [{:autocomplete/keys [debounce-ms search-key]
         :dropdown/keys [allowAdditions onAddItem
                         additionPosition clearable]} (::form/field-options attribute)
        k                  (::attr/qualified-key attribute)
        {::form/keys [form-instance]} env
        value              (-> (comp/props form-instance) (get k))
        id                 (comp/get-state this :field-id)
        label              (form/field-label env attribute)
        read-only?         (form/read-only? form-instance attribute)
        invalid?           (validation/invalid-attribute-value? env attribute)
        validation-message (when invalid? (validation/validation-error-message env attribute))
        field              (get-in props [::autocomplete-id id])]
    (log/debug "RENDER AutocompleteFieldRoot" "field" field "id" id "props" props  "env" env "attribute" attribute)
    ;; Have to pass the id and debounce early since the merge in mount won't happen until after, which is too late for initial
    ;; state
    (ui-autocomplete-field (assoc field
                             ::autocomplete-id id
                             :autocomplete/search-key search-key
                             :autocomplete/debounce-ms debounce-ms)
      {:value              value
       :invalid?           invalid?
       :validation-message validation-message
       :label              label
       :read-only?         read-only?
       :onChange           (fn [normalized-value]
                             ;(log/debug "autocomplete changed" normalized-value)
                             #?(:cljs
                                (form/input-changed! env k normalized-value)))
       :clearable          clearable
       :allowAdditions     allowAdditions
       :onAddItem          (fn [normalized-value]
                             (log/debug "onAddItem changed" normalized-value)
                             #?(:cljs
                                (form/input-changed! env k normalized-value)))
       :additionPosition   additionPosition})))

(def ui-autocomplete-field-root (mroot/floating-root-factory AutocompleteFieldRoot
                                  {:keyfn (fn [props]
                                            ;(log/debug "floating-root-factory" props)
                                            (-> props :attribute ::attr/qualified-key))}))

(defn render-autocomplete-field [env {::attr/keys [cardinality] :or {cardinality :one} :as attribute}]
  (if (= :many cardinality)
    (log/error "Cannot autocomplete to-many attributes with renderer" `render-autocomplete-field)
    (ui-autocomplete-field-root {:env env :attribute attribute})))

