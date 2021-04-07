(ns riverdb.rad.ui.users
  (:require
    [clojure.string :as str]
    [edn-query-language.core :as eql]
    [riverdb.rad.model :as model]
    [riverdb.rad.model.agency :as agency]
    [riverdb.rad.model.user :as user]
    [riverdb.rad.model.role :as role]
    [riverdb.rad.model.global :as global]
    [riverdb.rad.ui.agency :refer [agency-picker]]
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
    [theta.log :as log]
    [com.fulcrologic.rad.attributes-options :as ao]))

(defsc AgencyQuery [this props]
  {:query [:agencylookup/uuid :agencylookup/AgencyDescr :agencylookup/AgencyCode]
   :ident :agencylookup/uuid})

(defsc RoleQuery [this props]
  {:query [:role.type/uuid :db/ident :role.type/label]
   :ident :role.type/uuid})

(defsc AgencyQueryGID [this props]
  {:query [:db/id :agencylookup/uuid]
   :ident [:org.riverdb.db.agencylookup/gid :db/id]})

(defsc ProjectQuery [this props]
  {:query [:projectslookup/uuid :projectslookup/ProjectsComments]
   :ident :projectslookup/uuid})


(form/defsc-form UserForm [this props]
  {fo/id             user/uid
   fo/attributes     [user/Name user/Email user/Password user/Agency user/Role user/Projects]
   fo/default-values {:user/verified? false}
   fo/route-prefix   "user"
   fo/title          "Edit User"
   ;fo/layout         [[:user/name]
   ;                   [:person/Agency]
   ;                   [:person/IsStaff]]
   ;fo/field-labels   {:person/IsStaff "Staff"}
   fo/field-styles   {:user/agency   :pick-one
                      :user/projects :pick-many
                      :user/role     :pick-one}
   ;:user/password :password}
   fo/read-only-fields #{:user/agency}
   fo/field-options  {:user/agency   agency-picker
                      :user/role     {po/cache-key        :picker/roles
                                      po/query-key        :role/role-types
                                      po/query-component  RoleQuery
                                      po/options-xform    (fn [_ options]
                                                            (log/debug "ROLE PICKER options" options)
                                                            (let [opts
                                                                  (mapv
                                                                    (fn [{:role.type/keys [uuid label]}]
                                                                      {:text label :value [:role.type/uuid uuid]})
                                                                    (sort-by :role.type/label options))]
                                                              opts))
                                      po/cache-time-ms    3600000}

                      :user/projects {po/cache-key        :picker/projects
                                      po/query-key        :project/all-projects
                                      po/query-component  ProjectQuery
                                      ;po/query-parameters (fn [app cls props]
                                      ;                      (log/debug "PROJECTS PICKER QUERY PARAMS FN" "PROPS" props)
                                      ;                      (let [agency (:user/agency props)]
                                      ;                        (if agency
                                      ;                          {:projectslookup/AgencyRef agency}
                                      ;                          {})))
                                      po/options-xform    (fn [_ options]
                                                            (let [opts
                                                                  (mapv
                                                                    (fn [{:projectslookup/keys [uuid ProjectsComments]}]
                                                                      {:text ProjectsComments :value [:projectslookup/uuid uuid]})
                                                                    (sort-by :projectslookup/ProjectsComments options))]
                                                              (log/debug "PROJECTS PICKER OPTS" opts)
                                                              opts))
                                      po/cache-time-ms    3600000}}})


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




(defsc UserListItem [this {:user/keys [uuid name email agency role] :as props}]
  {:query [:user/uuid :user/name :user/email
           {:user/agency (comp/get-query AgencyQuery)}
           {:user/role (comp/get-query RoleQuery)}]
   :ident :user/uuid}
  (log/debug "RENDER UserListItem props" props)
  (dom/tr
    (dom/td
      (dom/a {:onClick (fn [] (form/edit! this UserForm uuid))} name))
    (dom/td
      (dom/div email))
    (dom/td
      (dom/div (:role.type/label role)))
    (dom/td
      (dom/div (:agencylookup/AgencyCode agency)))))

(def ui-user-list-item (comp/factory UserListItem {:keyfn :user/uuid}))

(report/defsc-report UserList [this props]
  {ro/columns             [user/Name user/Email user/Role user/Agency]
   ;ro/column-headings     {:person/Name    "Name"
   ;                        :person/IsStaff "Staff"}

   ro/BodyItem            UserListItem
   ro/row-pk              user/uid
   ro/route               "users"
   ro/source-attribute    :all-users
   ro/title               "Users"
   ro/paginate?           true
   ro/page-size           20
   ro/run-on-mount?       true
   ro/controls            {:user/agency (merge
                                          agency-picker
                                          {:label     "Agency"
                                           :type      :picker
                                           :disabled? true})

                           :user/name   {:label    "Name"
                                         :type     :string
                                         :style    :search
                                         :onChange (fn [this a]
                                                     (log/debug "search change" a)
                                                     (com.fulcrologic.rad.control/run! this))}

                           ::add-user   {:label  "Add User"
                                         :type   :button
                                         :action (fn [this]
                                                   (let [props    (comp/props this)
                                                         agency   (:ui.riverdb/current-agency props)
                                                         ag-ident [:agencylookup/uuid (:agencylookup/uuid agency)]]
                                                     (log/debug "Add user agency" ag-ident)
                                                     (form/create! this UserForm
                                                       {:initial-state {:user/agency ag-ident}})))}}

   ro/control-layout      {:action-buttons [::add-user]
                           :inputs         [[:user/name :user/agency]]}
   ro/links               {:user/name (fn [report-instance {:user/keys [uuid] :as props}]
                                        (form/edit! report-instance UserForm uuid))}
   ro/initial-sort-params {:sort-by :user/name}
   ro/query-inclusions    [{[:ui.riverdb/current-agency '_] (comp/get-query AgencyQueryGID)}
                           #_[::po/options-cache '_]]})


