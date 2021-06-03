(ns riverdb.rad.model.worktime
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.rad.attributes :as attr :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.form :as form]
    #?(:clj [riverdb.rad.model.db-queries :as queries])
    [com.wsscode.pathom.connect :as pc]
    [theta.log :as log]
    [com.fulcrologic.rad.form-options :as fo]
    [com.fulcrologic.rad.picker-options :as po]))

(defattr uid :worktime/uuid :uuid
  {ao/identity? true
   ao/schema    :production})

(defattr Person :worktime/person :ref
  {ao/identities #{:worktime/uuid}
   ao/schema     :production
   ao/target     :person/uuid
   ao/required?  true})

(defattr Hours :worktime/hours :decimal
  {ao/identities #{:worktime/uuid}
   ao/schema     :production
   ao/required?  true})

(defsc OptionsQuery [_ _] {:query [:text :value]})

(defattr Task :worktime/task :string
  {ao/identities      #{:worktime/uuid}
   ao/schema          :production
   ao/style           :inputlist
   po/cache-key       :picker/tasks
   po/query-key       :worktime/tasks
   po/query-component OptionsQuery})

(defattr Sitevisit :worktime/sitevisit :ref
  {ao/identities #{:worktime/uuid}
   ao/schema     :production
   ao/target     :org.riverdb.db.sitevisit/gid})

(defattr Date :worktime/date :instant
  {ao/identities #{:worktime/uuid}
   ao/schema     :production})

(defattr Agency :worktime/agency :ref
  {ao/identities #{:worktime/uuid}
   ao/schema     :production
   ao/target     :agencylookup/uuid})

(pc/defresolver worktimes-resolver [{:keys [query-params] :as env} input]
  {::pc/output [{:worktime/all [:worktime/uuid]}]}
  #?(:clj {:worktime/all (queries/get-all-worktimes env query-params)}))

(pc/defresolver work-tasks-resolver [{:keys [query-params] :as env} input]
  {::pc/output [{:worktime/tasks [:text :value]}]}
  #?(:clj {:worktime/tasks (queries/get-all-worktime-tasks env query-params)}))

(def resolvers [worktimes-resolver work-tasks-resolver])
(def attributes [uid Person Hours Task Sitevisit Date Agency #_all-worktimes])