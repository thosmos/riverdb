(ns riverdb.ui.util
  (:require
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    ["shortid" :as shorty :refer [generate]]))

(defn shortid []
  (shorty))

(defn make-tempid []
  (tempid/tempid (str "t" (generate))))


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
           complete?  (or complete? #{})]
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

