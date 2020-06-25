(ns riverdb.ui.util
  (:require
    [cognitect.transit :as transit]
    [edn-query-language.core :as eql]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as dt]
    [com.fulcrologic.fulcro.application :as fapp]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h3 button label span table tr th td thead tbody]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.mutations :as fm :refer [defmutation]]
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
    [riverdb.application :refer [SPA]]
    [riverdb.api.mutations :as rm]
    [theta.log :as log :refer [debug]]
    ["shortid" :as shorty :refer [generate]]
    [thosmos.util :as tu]
    [clojure.string :as str]
    [riverdb.lookup :as look]))

(defn shortid []
  (str "t" (shorty)))

(defn make-tempid []
  (tempid/tempid (str "t" (generate))))

(defn walk-ident-refs*
  "walks the props at ident searching for {:db/ident :db-ident-keyword} refs and adding :db/id resulting in: {:db/id \"123\" :db/ident :db-ident-keyword}"
  [state-map ident]
  (let [props     (get-in state-map ident)
        f         (fn [[k v]]
                    (if (and (map? v) (keyword? (:db/ident v)))
                      (if-let [db-id (get-in state-map [:db/ident (:db/ident v) :db/id])]
                        [k (assoc v :db/id db-id)]
                        [k v])
                      [k v]))
        new-props (clojure.walk/prewalk
                    (fn [x] (if (map? x) (into {} (map f x)) x))
                    props)]
    (assoc-in state-map ident new-props)))

(defn walk-ident-refs
  "walks the props map looking for {:db/ident :ident-keyword} and adding :db/id to maps.  returns an updated props map."
  [state-or-ident-map props]
  (let [f         (fn [[k v]]
                    (if (and (map? v) (keyword? (:db/ident v)))
                      (if-let [db-id (get-in state-or-ident-map [:db/ident (:db/ident v) :db/id])]
                        [k (assoc v :db/id db-id)]
                        [k v])
                      [k v]))
        new-props (clojure.walk/prewalk
                    (fn [x] (if (map? x) (into {} (map f x)) x))
                    props)]
    new-props))

(defn make-validator
  "Create a form/field validation function using a supplied field checker. The field checker will be given
  the entire form (denormalized) and a single field key that is to be checked. It must return
  a boolean indicating if that given field is valid or not, or a custom error map if invalid.

  During a recursive check for a form, the validation function will be in the correct context (e.g. the form supplied will contain
  the field. There is no need to search for it in subforms).

  make-validator returns a three arity function:

  - `(fn [form] ...)` - Calling this version will return :unchecked, :valid, or :invalid for the entire form.
  - `(fn [form field] ...)` - Calling this version will return :unchecked, :valid, or :invalid for the single field.
  - `(fn [form field return-map?] ...)` - Calling this version will return :unchecked, :valid, :invalid, or a custom error map for the single field.

  Typical usage would be to show messages around the form fields:

  ```
  (defn field-valid? [form field] true) ; just return true

  (def my-validator (make-validator field-valid?))
  (defn valid? [form field]
     (= :valid (my-validator form field)))
  (defn checked? [form field]
     (not= :unchecked (my-validator form field)))
  ```

  A more complex example with custom error messages:
  ```
  (defn field-valid? [form field]
    (case field
      :my-field
      (let [field-val (get form field)]
         (cond
            (= field-val :foo)
            {:msg \"must not be :foo\"}
            (= field-val :bar)
            {:msg \":bar is an invalid value\"}
            (= field-val :baz)
            false
            :else
            true))))

  (def map-validator (make-validator field-valid?))
  (defn custom-msg? [form field]
    (let [valid? (map-validator form field true)]
      (when (map? valid?)
        (:msg valid?)))
  ```
  "
  [field-valid?]
  (fn custom-get-validity*
    ([ui-entity-props field return-map?]
     (let [{{complete? ::fs/complete?} ::fs/config} ui-entity-props
           complete? (or complete? #{})]
       (if (not (complete? field))
         :unchecked
         (let [valid? (field-valid? ui-entity-props field)]
           (if (or (map? valid?) (not valid?))
             (if return-map?
               valid?
               :invalid)
             :valid)))))
    ([ui-entity-props field]
     (custom-get-validity* ui-entity-props field false))
    ([ui-entity-props]
     (let [{{:keys [::fs/fields ::fs/subforms]} ::fs/config} ui-entity-props
           immediate-subforms (fs/immediate-subforms ui-entity-props (-> subforms keys set))
           field-validity     (fn [current-validity k] (fs/merge-validity current-validity (custom-get-validity* ui-entity-props k)))
           subform-validities (map custom-get-validity* immediate-subforms)
           subform-validity   (reduce fs/merge-validity :valid subform-validities)
           this-validity      (reduce field-validity :valid fields)]
       (fs/merge-validity this-validity subform-validity)))))


(defn ent-ns->ident-k
  ([ent-ns]
   (when ent-ns
     (keyword (str "org.riverdb.db." (name ent-ns)) "gid"))))

(defn ent-ns->ident [ent-ns id]
  (debug "ent-ns->db-ref" ent-ns id)
  (when (and ent-ns id)
    [(ent-ns->ident-k ent-ns) id]))

(defn ident-k->ent-ns [ident-k]
  (when (and ident-k (keyword? ident-k))
    (tu/ns-kw "entity.ns" (last (str/split (namespace ident-k) #"\.")))))

(defn ident->ent-ns [ident]
  (when (eql/ident? ident)
    (ident-k->ent-ns (first ident))))

(defn ref-is-map? [db-ref]
  (and
    (map? db-ref)
    (= (keys db-ref) '(:db/id))))

(defn map-ref->ident [db-ref ident-k]
  (if (ref-is-map? db-ref)
    [ident-k (:db/id db-ref)]
    db-ref))

(defn map->ident [m ident-k]
  (if (and (map? m) (:db/id m))
    [ident-k (:db/id m)]
    m))

(defn thing->ident [thing]
  (cond

    (eql/ident? thing)
    thing

    (map? thing)
    (let [dbid   (:db/id thing)
          ent-ns (:riverdb.entity/ns thing)]
      (if (and dbid ent-ns)
        [(ent-ns->ident-k ent-ns) dbid]
        thing))

    :else
    thing))

(defn convert-db-refs [thing]
  (let [ent-ns (:riverdb.entity/ns thing)
        ent-spec (get look/specs-map ent-ns)
        out (reduce-kv
              (fn [out attr-k {:attr/keys [type ref cardinality]}]
                (let [attr-val (get out attr-k)
                      is-ref?  (= type :ref)
                      ident-k (when is-ref?
                                (ent-ns->ident-k (:entity/ns ref)))
                      attr-val' (if
                                  (= cardinality :one)
                                  (map-ref->ident attr-val ident-k)
                                  (mapv #(map-ref->ident % ident-k) attr-val))]
                  (if is-ref?
                    (assoc out attr-k attr-val')
                    out)))
              thing
              (:entity/attrs ent-spec))]
    ;(debug "convert-db-refs" "BEFORE" thing "AFTER" out)
    out))

(defn convert-db-refs* [state-map ident]
  (let [thing (get-in state-map ident)
        ent-ns (ident->ent-ns ident)
        ent-spec (get look/specs-map ent-ns)
        out (reduce-kv
              (fn [out attr-k {:attr/keys [type ref cardinality]}]
                (let [attr-val (get out attr-k)
                      is-ref?  (= type :ref)
                      ident-k (when is-ref?
                                (ent-ns->ident-k (:entity/ns ref)))
                      attr-val' (if
                                  (= cardinality :one)
                                  (map-ref->ident attr-val ident-k)
                                  (mapv #(map-ref->ident % ident-k) attr-val))]
                  (if is-ref?
                    (assoc out attr-k attr-val')
                    out)))
              thing
              (:entity/attrs ent-spec))]
    (debug "convert-db-refs*" "BEFORE" (get-in state-map ident) "AFTER" out)
    (assoc-in state-map ident out)))

(defn lookup-db-ident [ident]
  (let [app-state (fapp/current-state SPA)
        ident     (cond
                    (and (map? ident) (keyword? (:db/ident ident)))
                    (:db/ident ident)

                    (keyword? ident)
                    ident

                    (and
                      (vector? ident)
                      (= 2 (count ident))
                      (= (first ident) :db/ident)
                      (keyword? (second ident)))
                    (second ident))
        result    (when ident
                    (get-in app-state [:db/ident ident]))
        _         (debug "get-db-ident" ident "result:" result)]

    result))

(defn db-ident->db-ref [ident]
  (let [ident-m (lookup-db-ident ident)
        ent-ns (:riverdb.entity/ns ident-m)
        db-id (:db/id ident-m)
        db-ref (ent-ns->ident ent-ns db-id)]
    db-ref))

(defn get-ref-set-val [current id-key id]
  (cond
    (nil? id)
    nil
    (eql/ident? current)
    [id-key id]
    (vector? current)
    (mapv #(get-ref-set-val (first current) id-key %) id)
    :else
    [id-key id]))

(defn get-ref-val [value]
  (let [result (cond
                 (map? value)
                 (:db/id value)
                 (eql/ident? value)
                 (second value)
                 (vector? value)
                 (mapv get-ref-val value)
                 :else value)]
    ;(debug "get-ref-val" value result)
    result))

;(defn set-ref! [this k val]
;  (fm/set-value! this k val)
;  (comp/transact! this `[(fs/mark-complete! {:entity-ident nil :field ~k})]))

;(defn set-refs! [this k ids]
;  (fm/set-value! this k (mapv #(identity {:db/id %}) ids))
;  (comp/transact! this `[(fs/mark-complete! {:entity-ident nil :field ~k})]))

;(defn set-value! [this k value]
;  (fm/set-value! this k value)
;  (comp/transact! this `[(fs/mark-complete! {:entity-ident nil :field ~k})]))

(defn set-value* [state-map ident k v]
  (-> state-map
    (update-in ident assoc k v)
    (fs/mark-complete* ident k)))

(fm/defmutation set-value [{:keys [ident k v]}]
  (action [{:keys [state]}]
    (debug "MUTATION set-value" ident k v)
    (swap! state set-value* ident k v)))

(defn set-value! [this k value]
  (let [ident (comp/get-ident this)]
    (comp/transact! this `[(set-value {:ident ~ident :k ~k :v ~value})])))

(defn set-ident-value! [ident k value]
  (comp/transact! SPA `[(set-value {:ident ~ident :k ~k :v ~value})]))

(fm/defmutation set-ref [{:keys [ident k v]}]
  (action [{:keys [state]}]
    (let [v {:db/id v}]
      (debug "MUTATION set-ref" ident k v)
      (swap! state set-value* ident k v))))

(defn set-ref! [this k id]
  (comp/transact! this `[(set-ref {:ident ~(comp/get-ident this) :k ~k :v ~id})]))

(fm/defmutation set-refs [{:keys [ident k v]}]
  (action [{:keys [state]}]
    (let [v (mapv #(identity {:db/id %}) v)]
      (debug "MUTATION set-refs" ident k v)
      (swap! state set-value* ident k v))))

(defn set-refs! [this k ids]
  (comp/transact! this `[(set-refs {:ident ~(comp/get-ident this) :k ~k :v ~ids})]))



(defmutation set-editing [{:keys [editing]}]
  (action [{:keys [state ref]}]
    (swap! state (assoc-in (comp/get-ident ref) :ui/editing editing))))

(defn ui-cancel-save [this {:ui/keys [saving] :as props} dirty? {:keys [onCancel onSave success-msg]}]
  (div {}
    (dom/button :.ui.button.secondary
      {;:disabled (and (not dirty?) (not cancelAlwaysOn))
       :onClick #(do
                   (debug "CANCEL!" dirty? (when dirty? (fs/dirty-fields props false)))
                   (if dirty?
                     (do
                       (comp/transact! this
                         `[(rm/reset-form ~{:ident (comp/get-ident this)})]))
                     (fm/set-value! this :ui/editing false))
                   (when onCancel
                     (onCancel)))}
      (if dirty? "Cancel" "Close"))

    (dom/button :.ui.button.primary
      {:disabled (not dirty?)
       :onClick  #(do
                    ;let [dirty-fields (fs/dirty-fields props true)]
                    ;(debug "SAVE!" dirty? dirty-fields)
                    ;(comp/transact! this
                    ;  `[(rm/save-entity ~{:ident (comp/get-ident this)
                    ;                      :diff  dirty-fields
                    ;                      :success-msg success-msg})])
                    (when onSave
                      (onSave)))}
      (if saving
        (ui-loader {:active true})
        "Save"))))

(defn rui-input [this k opts]
  (let [props   (comp/props this)
        value   (get props k)
        label   (get opts :label (clojure.string/capitalize (name k)))
        opts    (dissoc opts :label)
        control (get opts :control "input")]
    (div :.field {}
      (dom/label {} label)
      (ui-input
        (merge opts
          {:control  control
           :value    (or value "")
           ;:fluid false
           :onChange (fn [e]
                       (let [new-v (.. e -target -value)]
                         (debug "rui-input" k new-v)
                         (set-value! this k new-v)))})))))

(defn rui-ident-input [ident k value opts]
  (let [label   (get opts :label (clojure.string/capitalize (name k)))
        opts    (dissoc opts :label)
        control (get opts :control "input")]
    (div :.field {}
      (dom/label {} label)
      (ui-input
        (merge opts
          {:control  control
           :value    (or value "")
           ;:fluid false
           :onChange (fn [e]
                       (let [new-v (.. e -target -value)]
                         (debug "rui-input" k new-v)
                         (set-ident-value! ident k new-v)))})))))

(defn rui-bigdec [this k opts]
  (let [props (comp/props this)
        value (get props k)
        label (get opts :label (clojure.string/capitalize (name k)))
        opts  (dissoc opts :label)]
    (div :.field {}
      (dom/label {} label)
      (ui-input
        (merge opts
          {:value    (if (transit/bigdec? value)
                       (.-rep value)
                       (or value ""))
           :onChange (fn [e]
                       (let [val   (transit/bigdec (.. e -target -value))
                             new-v (if (= (.-rep val) "")
                                     nil
                                     val)]
                         (debug "Set BigDecimal" k new-v
                           (set-value! this k new-v))))})))))

(defn rui-int [this k opts]
  (let [props (comp/props this)
        value (get props k)
        label (get opts :label (clojure.string/capitalize (name k)))
        popup (:popup opts)
        opts  (-> opts
                (dissoc :label)
                (dissoc :popup))]
    (div :.field {}
      (dom/label {} label)
      (ui-input
        (merge opts
          {:value    (or (str value) "")
           :onChange (fn [e]
                       (let [val   (js/parseInt (.. e -target -value))
                             new-v (if (js/Number.isNaN val) nil val)]
                         (debug "Set Integer" k new-v
                           (set-value! this k new-v))))})))))

(defn rui-checkbox [this k opts]
  (let [props (comp/props this)
        value (get props k)
        label (get opts :label (clojure.string/capitalize (name k)))
        opts  (dissoc opts :label)]
    (div :.field {:key k}
      (dom/label {} label)
      (ui-checkbox (merge opts
                     {;:fluid    "true"
                      :checked  (or value false)
                      :onChange #(let [value (not value)]
                                   (log/debug "change" k value)
                                   (set-value! this k value))})))))

(defn parse-float [str]
  (let [res (js/parseFloat str)]
    (if (js/Number.isNaN res)
      nil
      res)))

(defn filter-param-typecode [type params]
  (filterv #(= type (get-in % [:parameter/sampleTypeRef :db/ident])) params))


(comment
  (dt/integrate-ident*))