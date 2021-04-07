(ns riverdb.rad.model.role
  (:require
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.authorization :as auth]
    #?(:clj
       [riverdb.rad.model.db-queries :as queries])
    [com.wsscode.pathom.connect :as pc]
    [theta.log :as log]))

(defattr uid :role/uuid :uuid
  {ao/identity? true
   ao/schema    :production})

(defattr Name :role/name :string
  {ao/identities #{:role/uuid}
   ao/schema        :production
   ao/required? true})

(defattr Type :role/type :keyword
  {ao/identities #{:role/uuid}
   ao/schema        :production
   ao/required? true
   ao/enumerated-values #{:role.type/data-entry :role.type/qc :role.type/admin}
   ao/computed-options
   [{:text "Data Entry" :value :role.type/data-entry}
    {:text "QC" :value :role.type/qc}
    {:text "Admin" :value :role.type/admin}]})

(defattr Agency :role/agency :ref
  {ao/identities #{:role/uuid}
   ao/schema        :production
   ao/target :agencylookup/uuid})

(defattr Projects :role/projects :ref
  {ao/identities #{:role/uuid}
   ao/schema :production
   ao/target :projectslookup/uuid
   ao/cardinality :many})

(defattr Doc :role/doc :string
  {ao/identities #{:role/uuid}
   ao/schema        :production
   ao/required? true})

;(defattr all-roles :role/all-roles :ref
;  {ao/target :role/uuid
;   ::pc/output [{:role/all-roles [:role/uuid]}]
;   ::pc/resolve (fn [{:keys [query-params] :as env} _]
;                  (log/debug "RESOLVE ALL-ROLES" query-params)
;                  #?(:clj
;                     {:role/all-roles (queries/get-all-roles env query-params)}))})

(def attributes [uid Name Type Agency Projects Doc #_all-roles])