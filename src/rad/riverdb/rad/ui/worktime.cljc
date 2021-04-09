(ns riverdb.rad.ui.worktime
  (:require
    [clojure.string :as str]
    [edn-query-language.core :as eql]
    [riverdb.rad.model :as model]
    [riverdb.rad.model.agency :as agency]
    [riverdb.rad.model.person :as person]
    [riverdb.rad.model.global :as global]
    [riverdb.rad.model.worktime :as worktime]
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
    [theta.log :as log]))

(defsc PersonQuery [this props]
  {:query [:person/uuid :person/Name]
   :ident :person/uuid})

(defsc StationQuery [this props]
  {:query [:db/id :stationlookup/StationName :stationlookup/StationID]
   :ident [:org.riverdb.db.stationlookup/gid :db/id]})

(defsc SVQuery [this props]
  {:query [:db/id
           :riverdb.entity/ns
           :sitevisit/uuid
           :sitevisit/SiteVisitDate
           {:sitevisit/StationID (comp/get-query StationQuery)}]
   :ident [:org.riverdb.db.sitevisit/gid :db/id]})

(defsc WorkTimeListItem [this {:worktime/keys [uuid person hours task sitevisit date] :as props}]
  {:query [:db/id
           :riverdb.entity/ns
           :worktime/uuid
           :worktime/hours
           :worktime/task
           :worktime/date
           {:worktime/person (comp/get-query PersonQuery)}
           {:worktime/sitevisit (comp/get-query SVQuery)}]
   :ident [:org.riverdb.db.worktime/gid :db/id]}
  (log/debug "RENDER WorkTimeListItem props" props)
  (let [person-nm (:person/Name person)
        sv-date   (or (:sitevisit/SiteVisitDate sitevisit) date)
        sv-site (get-in sitevisit [:sitevisit/StationID :stationlookup/StationID])]
    (dom/tr
      (dom/td
        (dom/a {:onClick (fn []
                           #_(form/edit! this UserForm uuid)
                           (log/debug "CLICK" person-nm))} person-nm))
      (dom/td
        (dom/div (.-rep hours)))
      (dom/td
        (dom/div
          (if sitevisit
            (dom/a {:onClick (fn []
                               #_(form/edit! this UserForm uuid)
                               (log/debug "CLICK" task))} task)
            task)))
      #_(dom/td
          (dom/div (when sv-site (str "Site " sv-site))))
      (dom/td
        (dom/div (str (datetime/inst->local-date sv-date)))))))

(def ui-user-list-item (comp/factory WorkTimeListItem {:keyfn :db/id}))

(report/defsc-report WorkTimeList [this props]
  {ro/columns             [worktime/Person worktime/Hours worktime/Task worktime/Date]
   ro/row-pk              worktime/uid
   ro/route               "worktime"
   ro/source-attribute    :org.riverdb.db.worktime
   ro/BodyItem WorkTimeListItem
   ro/title               "Work Times"
   ro/paginate?           true
   ro/page-size           50
   ro/run-on-mount?       true})

