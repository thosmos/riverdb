(ns riverdb.rad.model.person
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.picker-options :as po]
    [theta.log :as log]))

;; not using the alias to avoid needing to import for the UI build
;[com.fulcrologic.rad.database-adapters.datomic :as datomic]))

(defattr uid :person/uuid :uuid
  {ao/identity? true
   ao/schema    :production})

(defattr Name :person/Name :string
  {ao/identities #{:person/uuid}
   ao/schema        :production
   ao/required? true})

(defattr IsStaff :person/IsStaff :boolean
  {ao/identities #{:person/uuid}
   ao/schema        :production})

(defattr Agency :person/Agency :ref
  {ao/identities #{:person/uuid}
   ao/target :agencylookup/uuid
   ao/required? true
   ao/schema     :production})

(defsc PersonQuery [this props]
  {:query [:person/uuid :person/Name]
   :ident :person/uuid})

(def person-picker
  {po/cache-key       :picker/person
   po/query-key       :all-people
   po/cache-time-ms   3600000
   po/query-component PersonQuery
   po/options-xform   (fn [_ options]
                        (let [opts
                              (mapv
                                (fn [{:person/keys [uuid Name]}]
                                  {:text Name :value [:person/uuid uuid]})
                                (sort-by :person/Name options))]
                          (log/debug "PERSON PICKER XFORM" opts)
                          opts))})

(def attributes [uid Name IsStaff Agency])
