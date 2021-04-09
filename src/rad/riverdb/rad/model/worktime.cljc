(ns riverdb.rad.model.worktime
  (:require
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.authorization :as auth]
    #?(:clj [riverdb.rad.model.db-queries :as queries])
    [com.wsscode.pathom.connect :as pc]
    [theta.log :as log]
    [com.fulcrologic.rad.form-options :as fo]))

(defattr uid :org.riverdb.db.worktime/gid :long
  {ao/identity? true
   ao/schema    :production})

(defattr Person :worktime/person :ref
  {ao/identities #{:org.riverdb.db.worktime/gid}
   ao/schema     :production
   ao/target     :person/uuid
   ao/required?  true})

(defattr Hours :worktime/hours :bigdec
  {ao/identities #{:org.riverdb.db.worktime/gid}
   ao/schema :production
   ao/required? true})

(defattr Task :worktime/task :string
  {ao/identities #{:org.riverdb.db.worktime/gid}
   ao/schema :production})

(defattr Sitevisit :worktime/sitevisit :ref
  {ao/identities #{:org.riverdb.db.worktime/gid}
   ao/schema :production
   ao/target :org.riverdb.db.sitevisit/gid})

(defattr Date :worktime/date :instant
  {ao/identities #{:org.riverdb.db.worktime/gid}
   ao/schema :production})

;(defattr all-worktimes :worktime/all :ref
;  {ao/target :org.riverdb.db.worktime/gid
;   ::pc/output [{:worktime/all [:org.riverdb.db.worktime/gid]}]
;   ::pc/resolve (fn [{:keys [query-params] :as env} _]
;                  (log/debug "RESOLVE :worktime/all" query-params)
;                  #?(:clj
;                     {:worktime/all (queries/get-all-worktimes env query-params)}))})

(def attributes [uid Person Hours Task Sitevisit Date #_all-worktimes])