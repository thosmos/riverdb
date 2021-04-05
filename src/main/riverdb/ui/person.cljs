(ns riverdb.ui.person
  (:require
    [clojure.string :as str]
    [clojure.data]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h1 h2 h3 button label span select option
                                                table thead tbody th tr td]]
    [com.fulcrologic.fulcro.mutations :as fm :refer [defmutation]]
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
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal :refer [ui-modal]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-header :refer [ui-modal-header]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-content :refer [ui-modal-content]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-actions :refer [ui-modal-actions]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-description :refer [ui-modal-description]]
    [goog.object :as gob]
    [riverdb.application :refer [SPA]]
    [riverdb.api.mutations :as rm :refer [TxResult ui-tx-result]]
    [riverdb.ui.routes]
    [riverdb.ui.session :refer [ui-session Session]]
    [riverdb.ui.util :refer [make-tempid]]
    [theta.log :as log :refer [debug]]))

(fm/defmutation cancel-add [{:keys [form-ident lookup-key orig]}]
  (action [{:keys [state]}]
    (debug "CANCEL ADD" form-ident)
    (let [[form-k temp-id] form-ident]
     (swap! state update form-k dissoc temp-id))))

(defsc AddPersonModal [this {:person/keys [FName LName Name Agency uuid]
                             :ui/keys [show orig field-k options-target]
                             :keys [root/tx-result] :as props}
                       {:keys [post-save-mutation
                               post-save-params
                               onCancel] :as computed}]
  {:ident             [:org.riverdb.db.person/gid :db/id]
   :query             [fs/form-config-join
                       :db/id
                       :riverdb.entity/ns
                       :person/uuid
                       :person/FName
                       :person/LName
                       :person/Name
                       :person/Agency
                       :ui/field-k
                       :ui/show
                       :ui/orig
                       :ui/options-target
                       {[:root/tx-result '_] (comp/get-query TxResult)}]
   :initial-state     {:db/id             (make-tempid)
                       :riverdb.entity/ns :entity.ns/person
                       :person/uuid       (tempid/tempid)
                       :person/Name       :param/name
                       :person/LName      :param/lname
                       :person/FName      :param/fname
                       :person/Agency     :param/agency
                       :ui/orig           :param/orig
                       :ui/field-k        :param/field-k
                       :ui/options-target :param/options-target
                       :ui/show           true}
   :form-fields       #{:person/FName :person/LName :person/Name :person/Agency
                        :person/uuid :riverdb.entity/ns}
   :componentDidMount (fn [this]
                        (let [{:person/keys [Name]} (comp/props this)]
                          (debug "DID MOUNT ADD PERSON" Name)))}
  (let [dirty-fields (fs/dirty-fields props false)
        dirty?       (some? (seq dirty-fields))
        this-ident   (comp/get-ident this)
        update-name  (fn [fname lname]
                       (let [fname (clojure.string/trim (or fname ""))
                             lname (clojure.string/trim (or lname ""))
                             space (and (not= fname "") (not= lname ""))
                             nm    (str fname (when space " ") lname)]
                         (fm/set-string! this :person/Name :value nm)
                         (fm/set-string! this :person/FName :value fname)
                         (fm/set-string! this :person/LName :value lname)))]

    (ui-modal {:open show} ;:dimmer false}
      (ui-modal-header {:content (str "Add Person \"" Name "\"")})
      (ui-modal-content {}
        (when (not (empty? tx-result))
          (ui-tx-result tx-result))
        (ui-form {}
          (div :.ui.fields {}
            (ui-form-input {:label "First Name" :value (or FName "") :onChange #(update-name (-> % .-target .-value) LName)})
            (ui-form-input {:label "Last Name" :value (or LName "") :onChange #(update-name FName (-> % .-target .-value))})
            (div :.field
              (dom/label {} "Agency")
              (ui-input {:disabled true :value (or (:agencylookup/AgencyCode Agency) "")}))
            (div :.field
              (dom/label {} "UUID")
              (ui-input {:disabled true :value (str (or uuid ""))})))

             ;(div :.field
             ;  (dom/label {} "Staff?")
             ;  (ui-checkbox {:checked (or IsStaff false) :onChange #(fm/toggle! this :person/IsStaff)})))
          (div :.ui.fields {}
            (dom/button :.ui.button.secondary
              {:onClick #(do
                           (debug "CANCEL!" dirty? (fs/dirty-fields props false))
                           (let [tempid (:db/id props)]
                             (debug "CLOSE")
                             ;(fn [{:keys [orig field-k]}]
                             ;  (let [field-val (get props field-k)
                             ;        new-val   (if (vector? field-val)
                             ;                    (vec (remove #(= (:db/id %) orig) field-val))
                             ;                    nil)]
                             ;    (debug "CANCEL! NEW VAL" new-val)
                             ;    (fm/set-value! this field-k new-val)))
                             (comp/transact! this
                               `[(rm/reset-form {:ident ~this-ident})])
                             (fm/set-value! this :ui/show false)
                             (comp/transact! this `[(cancel-add {:form-ident ~this-ident})])
                             (when onCancel
                               (onCancel {:orig orig :field-k field-k}))))}

              "Cancel")

            (dom/button :.ui.button.primary
              {:disabled (not dirty?)
               :onClick  #(let [dirty-fields (fs/dirty-fields props true)]
                            (debug "SAVE!" dirty? dirty-fields)
                            (comp/transact! this
                              `[(rm/save-entity {:ident         ~this-ident
                                                 :diff          ~dirty-fields
                                                 :post-mutation ~post-save-mutation
                                                 :post-params   ~(merge
                                                                   post-save-params
                                                                   {:ent-ns         :entity.ns/person
                                                                    :field-k        field-k
                                                                    :orig           orig
                                                                    :new-name       Name
                                                                    :modal-ident    this-ident
                                                                    :options-target options-target})})]))}

              "Save")))))))
(def ui-add-person-modal (comp/factory AddPersonModal {:keyfn :db/id}))

;(defmutation add-person [{:keys [ident props]}]
;  (action [{:keys [state]}]
;    (swap! state
;      (fn [st]
;        (-> st
;          (rm/merge-ident* ident props))))))

(defn add-person* [state-map new-name target field-k options-target]
  (let [orig new-name
        new-name   (str/trim new-name)
        names      (str/split new-name #"\ ")
        fname      (first names)
        lname      (str/join " " (rest names))
        agency     (select-keys (get-in state-map (:ui.riverdb/current-agency state-map)) [:db/id :agencylookup/AgencyCode])
        new-person (->>
                     (comp/get-initial-state AddPersonModal {:name new-name
                                                             :fname fname
                                                             :lname lname
                                                             :orig orig
                                                             :agency agency
                                                             :field-k field-k
                                                             :options-target options-target
                                                             :uuid (tempid/tempid)})
                     (fs/add-form-config AddPersonModal))]
    (debug "add-person*" new-person)
    (merge/merge-component state-map AddPersonModal new-person :replace target)))

