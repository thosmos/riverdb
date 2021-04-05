(ns riverdb.rad.ui.person
  (:require
    [clojure.string :as str]
    [edn-query-language.core :as eql]
    [riverdb.rad.model :as model]
    [riverdb.rad.model.agency :as agency]
    [riverdb.rad.model.person :as person]
    [riverdb.rad.model.global :as global]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    #?(:clj  [com.fulcrologic.fulcro.dom-server :as dom :refer [div label input]]
       :cljs [com.fulcrologic.fulcro.dom :as dom :refer [div label input]])
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.control :as control]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.picker-options :as po]
    [com.fulcrologic.rad.report-options :as ro]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.rendering.semantic-ui.components :refer [ui-wrapped-dropdown]]
    [com.fulcrologic.rad.report :as report]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.fulcro.ui-state-machines :as uism]
    [com.fulcrologic.rad.type-support.decimal :as math]
    [com.fulcrologic.rad.type-support.date-time :as datetime]
    [com.fulcrologic.semantic-ui.elements.icon.ui-icon :refer [ui-icon]]
    [theta.log :as log]))

(defsc AgencyQuery [this props]
  {:query [:agencylookup/uuid :agencylookup/AgencyDescr :agencylookup/AgencyCode]
   :ident :agencylookup/uuid})

(defsc AgencyQueryGID [this props]
  {:query [:db/id :agencylookup/uuid]
   :ident [:org.riverdb.db.agencylookup/gid :db/id]})


(form/defsc-form AgencyForm [this props]
  {fo/id         agency/uid
   fo/attributes [agency/AgencyCode global/EntityNS]
   fo/read-only-fields #{:agency/AgencyCode :riverdb.entity/ns}
   fo/layout     [[agency/AgencyCode]]})

(form/defsc-form PersonForm [this props]
  {fo/id             person/uid
   fo/attributes     [person/Name person/IsStaff person/Agency global/EntityNS]
   fo/default-values {:person/IsStaff    false
                      :riverdb.entity/ns :entity.ns/person}
   ;::form/enumeration-order :person/Name
   ;::form/cancel-route      ["people"]
   fo/route-prefix   "person"
   ;:route-segment     ["person"]
   fo/title          "Edit Person"
   fo/layout         [[:person/Name]
                      [:person/Agency]
                      [:person/IsStaff]]
   fo/field-labels   {:person/IsStaff "Staff"}
   fo/field-styles   {:person/Agency :pick-one}
   fo/read-only-fields #{:person/Agency}
   fo/field-options  {:person/Agency {po/cache-key :picker/agency
                                      po/query-key :all-agencies
                                      po/cache-time-ms 3600000
                                      po/query-component AgencyQuery
                                      po/options-xform (fn [_ options]
                                                         (let [opts
                                                               (mapv
                                                                 (fn [{:agencylookup/keys [uuid AgencyCode]}]
                                                                   {:text AgencyCode :value [:agencylookup/uuid uuid]})
                                                                 (sort-by :agencylookup/AgencyCode options))]
                                                           (log/debug "AGENCY PICKER OPTS" opts)
                                                           opts))}}
   ;fo/subforms       {:person/Agency {fo/ui AgencyForm
   ;                                   form/default-to-many}}



   ;fo/triggers       {:derive-fields
   ;                   (fn [props]
   ;                     (let [props (assoc props :person/Agency "17592186158635")]
   ;                       (log/debug "Form Derive Fields" props)
   ;                       props))
   ;                   :on-change
   ;                   (fn [{::uism/keys [state-map] :as uism-env} form-ident k old-value new-value]
   ;                     (log/debug "Form Trigger" form-ident k old-value new-value)
   ;                     uism-env)}
   #_::form/subforms          #_{:person/Agency {::form/ui            form/ToOneEntityPicker
                                                 ::form/pick-one      {:options/query-key :org.riverdb.db.agencylookup
                                                                       :options/params    {:limit -1 :filter {:agencylookup/Active true}}
                                                                       :options/subquery  [:agencylookup/uuid :agencylookup/AgencyCode]
                                                                       :options/transform (fn [{:agencylookup/keys [uuid AgencyCode]}]
                                                                                            {:text AgencyCode :value [:agencylookup/uuid uuid]})}
                                                 ::form/label         "Agency"
                                                 ;; Use computed props to inform subform of its role.
                                                 ::form/subform-style :inline}}})

;(def account-validator (fs/make-validator (fn [form field]
;                                            (case field
;                                              :account/email (let [prefix (or
;                                                                            (some-> form
;                                                                              (get :account/name)
;                                                                              (str/split #"\s")
;                                                                              (first)
;                                                                              (str/lower-case))
;                                                                            "")]
;                                                               (str/starts-with? (get form field) prefix))
;                                              (= :valid (model/all-attribute-validator form field))))))

(defsc PersonListItem [this {:person/keys [uuid Name Agency IsStaff] :as props}]
  {:query [:person/uuid :person/Name :person/IsStaff :person/Agency]
   :ident :person/uuid}
  (dom/tr
    (dom/td
      (dom/a {:onClick (fn [] (form/edit! this PersonForm uuid))} Name))
    (dom/td
      (dom/div (if IsStaff
                 (ui-icon {:name "check"}) "")))
    #_(dom/td
        (dom/div (:agencylookup/AgencyCode Agency)))))

(def ui-person-list-item (comp/factory PersonListItem {:keyfn :person/uuid}))


(report/defsc-report PersonList [this props]
  {ro/columns             [person/Name person/IsStaff]
   ro/column-headings     {:person/Name    "Name"
                           :person/IsStaff "Staff"}

   ro/BodyItem            PersonListItem
   ro/row-pk              person/uid
   ro/route               "people"
   ro/source-attribute    :all-people
   ro/title               "People"
   ro/paginate?           true
   ro/page-size           20
   ro/run-on-mount?       true
   ro/controls            {:person/Agency {:label               "Agency"
                                           :type                :picker
                                           :disabled?           true
                                           po/cache-key :picker/agency
                                           po/query-key :all-agencies
                                           po/cache-time-ms 3600000
                                           po/query-component AgencyQuery
                                           po/options-xform (fn [_ options]
                                                              (let [opts
                                                                    (mapv
                                                                      (fn [{:agencylookup/keys [uuid AgencyCode]}]
                                                                        {:text AgencyCode :value [:agencylookup/uuid uuid]})
                                                                      (sort-by :agencylookup/AgencyCode options))]
                                                                (log/debug "AGENCY PICKER OPTS" opts)
                                                                opts))}
                           :person/Name   {:label    "Name"
                                           :type     :string
                                           :style    :search
                                           :onChange (fn [this a]
                                                       (log/debug "search change" a)
                                                       (com.fulcrologic.rad.control/run! this))}
                           ::add-person   {:label  "Add Person"
                                           :type   :button
                                           :action (fn [this]
                                                     (let [props    (comp/props this)
                                                           agency   (:ui.riverdb/current-agency props)
                                                           ag-ident [:agencylookup/uuid (:agencylookup/uuid agency)]]
                                                       (log/debug "Add person agency" ag-ident)
                                                       (form/create! this PersonForm {:initial-state {:person/Agency ag-ident}})))}}
   ro/control-layout      {:action-buttons [::add-person]
                           :inputs         [[:person/Name :person/Agency]]}
   ro/links               {:person/Name (fn [report-instance {:person/keys [uuid] :as props}]
                                          (form/edit! report-instance PersonForm uuid))}
   ro/initial-sort-params {:sort-by :person/Name}
   ro/query-inclusions    [{[:ui.riverdb/current-agency '_] (comp/get-query AgencyQueryGID)} [::po/options-cache '_]]
   :componentDidMount     (fn [this]
                            (let [props    (comp/props this)
                                  agency   (:ui.riverdb/current-agency props)
                                  ag-ident [:agencylookup/uuid (:agencylookup/uuid agency)]]

                              (log/debug "PEOPLE PROPS" props)))})
                              ;(control/set-parameter! this :person/Agency ag-ident)))})


