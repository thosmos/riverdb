(ns riverdb.ui.forms.FieldResult
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]))

(defsc FieldResultForm [this {:keys [] :as props}]
  {:ident         [:org.riverdb.db.fieldresult/gid :db/id]
   :query         [:db/id
                   :riverdb.entity/ns
                   fs/form-config-join
                   :fieldresult/uuid
                   :fieldresult/Result
                   :fieldresult/FieldReplicate]
   :form-fields   #{:db/id
                    :riverdb.entity/ns
                    :fieldresult/uuid
                    :fieldresult/Result
                    :fieldresult/FieldReplicate}
   :initial-state {:db/id                          :param/id
                   :riverdb.entity/ns              :entity.ns/fieldresult
                   :fieldresult/uuid               :param/uuid
                   :fieldresult/Result             :param/result
                   :fieldresult/FieldReplicate     :param/rep}})