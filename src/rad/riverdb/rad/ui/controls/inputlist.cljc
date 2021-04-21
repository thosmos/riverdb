(ns riverdb.rad.ui.controls.inputlist
  (:require
    #?@(:cljs
        [[com.fulcrologic.fulcro.dom :as dom :refer [div label ui-input datalist option]]
         [goog.object :as gobj]
         [cljs.reader :refer [read-string]]
         [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :refer [ui-dropdown]]]
         ;[com.fulcrologic.semantic-ui.elements.input.ui-input :refer [ui-input]]]
        :clj
        [[com.fulcrologic.fulcro.dom-server :as dom :refer [div label input datalist option]]])
    [com.fulcrologic.rad.ids :as ids]
    [com.fulcrologic.rad.ui-validation :as validation]
    [com.fulcrologic.fulcro.rendering.multiple-roots-renderer :as mroot]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.dom.html-entities :as ent]
    [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.options-util :as opts :refer [?!]]
    [com.fulcrologic.rad.rendering.semantic-ui.components :refer [ui-wrapped-dropdown]]
    [com.fulcrologic.rad.rendering.semantic-ui.field]
    [com.fulcrologic.rad.attributes :as attr]
    [com.fulcrologic.rad.picker-options :as po]
    [clojure.string :as str]
    [theta.log :as log]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.fulcro.algorithms.normalized-state :as fns]))

(defsc InputList [_ {:keys [env attribute] :as props}]
  {:componentDidMount (fn [this]
                        (let [{:keys [env attribute]} (comp/props this)
                              form-instance (::form/form-instance env)
                              form-props    (comp/props form-instance)
                              form-class    (comp/react-type form-instance)]
                          (po/load-options! form-instance form-class form-props attribute)))}
  (let [{::form/keys [form-instance]} env
        {::attr/keys [qualified-key]} attribute
        list-id            (str qualified-key)
        form-props         (comp/props form-instance)
        value              (get form-props qualified-key)
        field-label        (form/field-label env attribute)
        visible?           (form/field-visible? form-instance attribute)
        read-only?         (form/read-only? form-instance attribute)
        input-props        (?! (form/field-style-config env attribute :input/props) env)
        invalid?           (validation/invalid-attribute-value? env attribute)
        validation-message (when invalid? (validation/validation-error-message env attribute))
        options            (po/current-form-options form-instance attribute)]
    ;(log/debug "RENDER InputList" qualified-key "props" props "form-props" form-props "value" value)
    (when visible?
      (div :.ui.field {:key     (str qualified-key)
                       :classes [(when invalid? "error")]}
        (label
          (or field-label (some-> qualified-key name str/capitalize)))
        (if read-only?
          (get-in options [0 :text])
          (div
            (div :.ui.input
              (ui-input (merge input-props
                          {:value    (or value "")
                           :type     "text"
                           :list     list-id
                           :onBlur   (fn [v] (form/input-blur! env qualified-key v))
                           :onChange (fn [v] (form/input-changed! env qualified-key v))})))
            (datalist {:id list-id}
              (for [opt options]
                (option {:key (:value opt) :value (:value opt)} (:text opt))))
            (when validation-message
              (div :.ui.error.message
                (str validation-message)))))))))

(def ui-inputlist-field (comp/factory InputList
                          {:keyfn (fn [props]
                                    (-> props :attribute ::attr/qualified-key))}))

(defn render-inputlist-field [env {::attr/keys [type] :as attribute}]
  (if (not= :string type)
    (log/error "Can only handle string attributes with " `ui-inputlist-field)
    (ui-inputlist-field {:env env :attribute attribute})))


