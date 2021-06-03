(ns riverdb.rad.model.user
  (:require
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.authorization :as auth]
    #?(:clj [riverdb.rad.model.db-queries :as queries])
    [com.wsscode.pathom.connect :as pc]
    [theta.log :as log]
    [com.fulcrologic.rad.form-options :as fo]))

(defattr uid :user/uuid :uuid
  {ao/identity? true
   ao/schema    :production
   ao/required? true})

(defattr Name :user/name :string
  {ao/identities #{:user/uuid}
   ao/schema     :production
   ao/required?  true})

(defattr Agency :user/agency :ref
  {ao/identities #{:user/uuid}
   ao/schema     :production
   ao/required?  true
   ao/target     :agencylookup/uuid})

(defattr Role :user/role :ref
  {ao/identities #{:user/uuid}
   ao/schema     :production
   ao/required?  true
   ao/target     :role.type/uuid})

(defattr RoleID :role.type/uuid :uuid
  {ao/identity? true
   ao/schema     :production})

(defattr RoleLabel :role.type/label :string
  {ao/identities #{:role.type/uuid}
   ao/schema     :production})


;(defattr role-types :role/role-types :ref
;  {ao/target :role.type/uuid
;   ::pc/output [{:role/role-types [:db/ident :role.type/uuid :role.type/label]}]
;   ::pc/resolve (fn [{:keys [query-params] :as env} _]
;                  (log/debug "RESOLVE :role/role-types" query-params)
;                  #?(:clj
;                     {:role/role-types (queries/get-all-role-types env)}))})


(defattr Type :user/type :keyword
  {ao/identities        #{:user/uuid}
   ao/schema            :production
   ao/required?         true
   ao/computed-options [{:text "Data Entry" :value :role.type/data-entry}
                        {:text "QC" :value :role.type/qc}
                        {:text "Admin" :value :role.type/admin}]})

(defattr Projects :user/projects :ref
  {ao/identities  #{:user/uuid}
   ao/schema      :production
   ao/cardinality :many
   ao/target      :projectslookup/uuid})

(defattr Email :user/email :string
  {ao/identities #{:user/uuid}
   ao/schema     :production
   ao/required?  true})

(defattr Password :user/password :string
  {ao/identities #{:user/uuid}
   ao/schema     :production})
   ;fo/field-style :password})
   ;ao/required?  true})

(defattr Verified? :user/verified? :boolean
  {ao/identities #{:user/uuid}
   ao/schema     :production})

;(defattr Roles :user/roles :ref
;  {ao/identities #{:user/uuid}
;   ao/cardinality :many
;   ao/target :role/uuid
;   ao/required? true
;   ao/schema     :production})

(def attributes [uid Name Agency Role Type Projects Email Password Verified? RoleID RoleLabel])

