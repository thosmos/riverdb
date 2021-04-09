(ns riverdb.rad.model
  (:require
    [riverdb.rad.model.person :as person]
    [riverdb.rad.model.agency :as agency]
    [riverdb.rad.model.global :as global]
    [riverdb.rad.model.user :as user]
    [riverdb.rad.model.worktime :as worktime]
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    #?(:clj
       [riverdb.rad.model.db-queries :as queries])
    [com.wsscode.pathom.connect :as pc]
    [theta.log :as log]))

(defattr all-projects :project/all-projects :ref
  {ao/target :project/uuid
   ::pc/output [{:project/all-projects [:project/uuid]}]
   ::pc/resolve (fn [{:keys [query-params] :as env} _]
                  (log/debug "RESOLVE ALL-PROJECTS" query-params)
                  #?(:clj
                     {:project/all-projects (queries/get-all-projects env query-params)}))})

(def all-attributes (vec (concat
                           person/attributes
                           agency/attributes
                           global/attributes
                           user/attributes
                           worktime/attributes
                           [all-projects])))

(def all-attribute-validator (attr/make-attribute-validator all-attributes))