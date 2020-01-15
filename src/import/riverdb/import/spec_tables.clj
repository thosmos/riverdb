(ns riverdb.import.spec-tables
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :refer [writer]]
    [thosmos.util :refer [ppstr]]))


(def header '(ns riverdb.ui.lookups
               (:require
                 [com.fulcrologic.fulcro.components :refer [defsc transact! factory]]
                 [com.fulcrologic.fulcro.algorithms.tempid :refer [tempid]]
                 [com.fulcrologic.fulcro.dom :as dom :refer [div]]
                 [taoensso.timbre :as log])))

(defn templ [sym name k query]
  `(~'defsc ~sym ~'[_ {:keys [ui/msg ui/name]}]
     {:ident         [~k :db/id]
      :query         ~query
      :initial-state {:db/id (~'tempid) :ui/msg "hello" :ui/name ~name}}
     ~'(do
         (log/debug (str "RENDER " name))
         (div (str msg " from the " name " component")))))

(defn templ-sum [sym name k query]
  `(~'defsc ~sym ~'[_ _]
     {:ident         [~k :db/id]
      :query         ~query
      :initial-state {:db/id (~'tempid)}}))

(defn ui-templ [sym ui-sym]
  `(~'def ~ui-sym (~'factory ~sym {:keyfn :db/id})))

(defn load-specs []
  (println "DEFSCing SPECs")
  (let [specs (edn/read-string (slurp "resources/specs.edn"))]
    (with-open [w (writer "src/main/riverdb/ui/lookups.cljs")]
      (.write w (str (ppstr header) "\n"))
      (doseq [{:entity/keys [name attrs prKeys]} specs]
        (let [aks       (mapv :attr/key attrs)
              k         (keyword (str "org.riverdb.db." name) "gid")
              sym       (symbol name)
              sym-sum   (symbol (str name "-sum"))
              ;ui-sym (symbol (str "ui-" name))
              query     (into [:ui/msg :ui/name :db/id :riverdb.entity/ns] aks)
              query-sum (into [:db/id :riverdb.entity/ns] prKeys)]
          (.write w (str (ppstr (templ sym name k query)) "\n"))
          (.write w (str (ppstr (templ-sum sym-sum name k query-sum)) "\n\n")))))))

(comment
  (load-specs)
  (println "SPECS LOADED"))

