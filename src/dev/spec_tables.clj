(ns spec-tables
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :refer [writer]]
    [thosmos.util :refer [ppstr]]))


(def header '(ns riverdb.ui.lookups
               (:require
                 [com.fulcrologic.fulcro.components :refer [defsc get-query factory]]
                 [com.fulcrologic.fulcro.algorithms.tempid :refer [tempid]]
                 [com.fulcrologic.fulcro.dom :refer [div]]
                 [theta.log :as log :refer [debug]])))

(defn templ [sym ent-ns k query]
  `(~'defsc ~sym ~'[_ {:keys [riverdb.entity/ns]}]
     {:ident         [~k :db/id]
      :query         ~query
      :initial-state {:db/id (~'tempid) :riverdb.entity/ns ~ent-ns}}
     ~'(debug (str "RENDER " ns))))

(defn templ-sum [sym ent-ns k query]
  `(~'defsc ~sym ~'[_ _]
     {:ident         [~k :db/id]
      :query         ~query
      :initial-state {:db/id (~'tempid) :riverdb.entity/ns ~ent-ns}}))

(defn ui-templ [sym ui-sym]
  `(~'def ~ui-sym (~'factory ~sym {:keyfn :db/id})))

(defn load-specs []
  (println "DEFSCing SPECs")
  (let [specs (edn/read-string (slurp "resources/specs.edn"))]
    (with-open [w (writer "src/main/riverdb/ui/lookups.cljs")]
      (.write w (str (ppstr header) "\n"))
      (doseq [{:entity/keys [ns attrs prKeys]} specs]
        (let [aks       (mapv :attr/key attrs)
              nm        (name ns)
              k         (keyword (str "org.riverdb.db." nm) "gid")
              sym       (symbol nm)
              sym-sum   (symbol (str nm "-sum"))
              ;ui-sym (symbol (str "ui-" name))
              query     (into [:db/id :riverdb.entity/ns] aks)
              query-sum (into [:db/id :riverdb.entity/ns] prKeys)]
          (.write w (str (ppstr (templ sym ns k query)) "\n"))
          (.write w (str (ppstr (templ-sum sym-sum ns k query-sum)) "\n\n")))))))

(comment
  (load-specs)
  (println "SPECS LOADED"))

