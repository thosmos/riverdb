(ns riverdb.rad.ui.person
  (:require
    [clojure.string :as str]
    [edn-query-language.core :as eql]
    [riverdb.rad.model :as model]
    [riverdb.rad.model.person :as person]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    #?(:clj  [com.fulcrologic.fulcro.dom-server :as dom :refer [div label input]]
       :cljs [com.fulcrologic.fulcro.dom :as dom :refer [div label input]])
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.control :as control]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.rendering.semantic-ui.components :refer [ui-wrapped-dropdown]]
    [com.fulcrologic.rad.report :as report]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.fulcro.ui-state-machines :as uism]
    [com.fulcrologic.rad.type-support.decimal :as math]
    [com.fulcrologic.rad.type-support.date-time :as datetime]
    [com.fulcrologic.rad.report-options :as ro]
    [com.fulcrologic.semantic-ui.elements.icon.ui-icon :refer [ui-icon]]
    [theta.log :as log]))

(form/defsc-form PersonForm [this props]
  {fo/id             person/uid
   fo/attributes     [person/Name person/IsStaff #_person/Agency]
   fo/default-values {:person/IsStaff false}
   ;::form/enumeration-order :person/Name
   ;::form/cancel-route      ["people"]
   fo/route-prefix   "person"
   ;:route-segment     ["person"]
   fo/title          "Edit Person"
   fo/layout         [[:person/Name #_:person/Agency]
                      [:person/IsStaff]]
   fo/field-labels   {:person/IsStaff "Staff"}
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

(defsc PersonListItem [this {:person/keys [uuid Name #_Agency IsStaff] :as props}]
  {:query                   [:person/uuid :person/Name :person/IsStaff #_:person/Agency]
   :ident                   :person/uuid}
  (dom/tr
    (dom/td
      (dom/a {:onClick (fn [] (form/edit! this PersonForm uuid))} Name))
    (dom/td
      (dom/div (if IsStaff
                 (ui-icon {:name "check"}) "")))))

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
   ro/controls            {:person/Agency {:type          :string
                                           :visible?      false}
                           :person/Name   {:label    "Search Name"
                                           :type     :string
                                           :style    :search
                                           :onChange (fn [this a]
                                                       (log/debug "search change" a)
                                                       (com.fulcrologic.rad.control/run! this))}
                           ::add-person   {:label  "Add Person"
                                           :type   :button
                                           :action (fn [this] (form/create! this PersonForm))}}
   ro/control-layout      {:action-buttons [::add-person]
                           :inputs         [[:person/Name]]}
   ro/links               {:person/Name (fn [report-instance {:person/keys [uuid Name #_Agency IsStaff] :as props}]
                                          (form/edit! report-instance PersonForm uuid))}
   ro/initial-sort-params {:sort-by :person/Name}
   ro/query-inclusions    [[:riverdb.ui.root/current-agency '_]]
   :componentDidMount     (fn [this]
                            (let [props (comp/props this)]
                              (log/debug "PROPS" props)
                              #_(control/set-parameter! this :person/Agency "17592186158635")))})

