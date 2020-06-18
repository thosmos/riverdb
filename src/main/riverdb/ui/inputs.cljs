(ns riverdb.ui.inputs
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro.dom.inputs :as fi :refer [StringBufferedInput]]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li a p h2 h3 button table thead
                                                tbody th tr td form label input select
                                                option span]]
    [com.fulcrologic.fulcro.dom.events :as evt]
    [com.fulcrologic.fulcro.mutations :as fm]
    [goog.object :as gobj]
    [riverdb.ui.util :as ru]
    [theta.log :refer [debug]]))


(let [digits+ (into #{} (map str) (concat (range 10) ["." "-"]))]
  (defn just-floaties
    "Returns `s` with all non-digits other than - and . stripped."
    [s]
    ;(debug "just-floaties" (pr-str s))
    (str/join
      (filter digits+ (seq s)))))

(defn model->str [model]
  ;(debug "model->str" (pr-str model))
  (str model))

;
;(defn StringBufferedInput
;  "Create a new type of input that can be derived from a string. `kw` is a fully-qualified keyword name for the new
;  class (which will be used to register it in the component registry), and `model->string` and `string->model` are
;  functions that can do the conversions (and MUST tolerate nil as input).
;  `model->string` MUST return a string (empty if invalid), and `string->model` should return nil if the string doesn't
;  yet convert to a valid model value.
;
;  `string-filter` is an optional `(fn [string?] string?)` that can be used to rewrite incoming strings (i.e. filter
;  things).
;  "
;  [kw {:keys [model->string
;              string->model
;              string-filter]}]
;  (let [cls (fn [props]
;              (cljs.core/this-as this
;                (let [props         (gobj/get props "fulcro$value")
;                      {:keys [value]} props
;                      initial-state {:oldPropValue value
;                                     :on-change    (fn [evt]
;                                                     (let [{:keys [value onChange]} (comp/props this)
;                                                           nsv (evt/target-value evt)
;                                                           nv  (string->model nsv)
;                                                           sv {:stringValue  nsv
;                                                               :oldPropValue value
;                                                               :value        nv}]
;                                                       (debug "StringBufferedInput SET STATE" sv)
;                                                       (comp/set-state! this sv)
;                                                       (when (and onChange (not= value nv))
;                                                         (onChange nv))))
;                                     :stringValue  (model->string value)}]
;                  (debug "StringBufferedInput initial-state" initial-state)
;                  (set! (.-state this) (cljs.core/js-obj "fulcro$state" initial-state)))
;                nil))]
;    (comp/configure-component! cls kw
;      {:getDerivedStateFromProps
;       (fn [latest-props state]
;         (let [{:keys [value]} latest-props
;               {:keys [oldPropValue stringValue]} state
;               ignorePropValue?  (or (= oldPropValue value) (= value (:value state)))
;               stringValue       (cond-> (if ignorePropValue?
;                                           stringValue
;                                           (model->string value))
;                                   string-filter string-filter)
;               new-derived-state (merge state {:stringValue stringValue :oldPropValue value})]
;               ;_                 (debug "StringBufferedInput getDerivedStateFromProps" "ignorePropValue?" ignorePropValue? "oldPropValue" (pr-str oldPropValue) "stringValue" (pr-str stringValue) "value" (pr-str value) "(:value state)" (pr-str (:value state)) "new-derived-state" new-derived-state)]
;           #js {"fulcro$state" new-derived-state}))
;       :render
;       (fn [this]
;         (let [{:keys [value onBlur] :as props} (comp/props this)
;               {:keys [stringValue on-change]} (comp/get-state this)]
;           (debug "RENDER StringBufferedInput" "value" value "stringValue" stringValue)
;           (dom/create-element "input"
;             (clj->js
;               (merge props
;                 (cond->
;                   {:value    stringValue
;                    :onChange on-change}
;                   onBlur (assoc :onBlur (fn [evt]
;                                           (onBlur (-> evt evt/target-value string->model))))))))))})
;    (comp/register-component! kw cls)
;    cls))

(def ui-float-input
  (comp/factory (StringBufferedInput ::FloatInput {:model->string model->str
                                                   :string->model ru/parse-float
                                                   :string-filter just-floaties})))