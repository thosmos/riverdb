;; Read-only shape report for the :fieldobsvarlookup/Order migration.
;;
;;   clojure -M:server -i tools/obs_shape.clj
;;
;; Run it from the repo root. Reads DATOMIC_URI (the same env var the app uses),
;; transacts NOTHING, and prints a report meant to be diffed between
;; environments:
;;
;;   DATOMIC_URI=<dev>  clojure -M:server -i tools/obs_shape.clj > dev-shape.txt
;;   DATOMIC_URI=<prod> clojure -M:server -i tools/obs_shape.clj > prod-shape.txt
;;   diff dev-shape.txt prod-shape.txt
(require '[datomic.api :as d] '[clojure.string :as str])

;; The app reads its URI with dotenv, which also consults a .env file that
;; System/getenv knows nothing about. Prefer dotenv when it is available (it is
;; in the running server's REPL) so this cannot quietly report on the default
;; database while the app is pointed at another one.
(def uri
  (or (try (require 'dotenv)
           ((resolve 'dotenv/env) :DATOMIC_URI)
           (catch Throwable _ nil))
      (System/getenv "DATOMIC_URI")
      "datomic:dev://localhost:4334/riverdb"))
(def conn (d/connect uri))
(def db (d/db conn))

(defn installed? [a] (boolean (some-> (d/entity db a) :db.install/_attribute)))
(defn cnt [a] (or (ffirst (d/q '[:find (count ?e) :in $ ?a :where [?e ?a]] db a)) 0))
(defn line [& xs] (println (str/join " " (map str xs))))

(line "=== OBS SHAPE REPORT ===")
(line "uri:" (str/replace uri #"password=[^&]*" "password=***"))
(line "basis-t:" (d/basis-t db))

(line "\n-- attributes (installed? / datom count) --")
(doseq [a [:fieldobsvarlookup/IntCode :fieldobsvarlookup/Order
           :fieldobsvarlookup/Analyte :fieldobsvarlookup/AnalyteName
           :fieldobsvarlookup/ValueCode :fieldobsvarlookup/Active
           :parameter/ObsOptions :parameter/FieldObsType
           :fieldobsresult/RefResult :fieldobsresult/RefResults
           :fieldobsresult/BigDecResult :fieldobsresult/TextResult]]
  (line " " a (if (installed? a) (str "yes  n=" (cnt a)) "NOT INSTALLED")))

(when-not (installed? :fieldobsvarlookup/ValueCode)
  (line "\n!! :fieldobsvarlookup/ValueCode is not installed — wrong database?"))

(def vars (d/q '[:find [(pull ?v [:db/id :fieldobsvarlookup/ValueCode
                                  :fieldobsvarlookup/IntCode :fieldobsvarlookup/Order
                                  :fieldobsvarlookup/Active :fieldobsvarlookup/AnalyteName
                                  {:fieldobsvarlookup/Analyte
                                   [:db/id :analytelookup/AnalyteName]}]) ...]
                 :where [?v :fieldobsvarlookup/ValueCode]] db))

(line "\n-- obsvar vocabulary --")
(line "  total:                " (count vars))
(line "  Active=false:         " (count (filter #(false? (:fieldobsvarlookup/Active %)) vars)))
(line "  IntCode > 0:          " (count (filter #(some-> (:fieldobsvarlookup/IntCode %) pos?) vars)))
(line "  IntCode = 0:          " (count (filter #(some-> (:fieldobsvarlookup/IntCode %) zero?) vars)))
(line "  IntCode absent:       " (count (remove :fieldobsvarlookup/IntCode vars)))
(line "  Order already set:    " (count (filter :fieldobsvarlookup/Order vars)))
(line "  Analyte ref set:      " (count (filter :fieldobsvarlookup/Analyte vars)))
(line "  Analyte ref MISSING:  " (count (remove :fieldobsvarlookup/Analyte vars))
      "   <- these would show no options after the change")
(line "  => migration would write Order for:"
      (count (filter #(and (some-> (:fieldobsvarlookup/IntCode %) pos?)
                        (not (:fieldobsvarlookup/Order %))) vars)))

(line "\n-- per-analyte, offered options in migrated order --")
(line "   (analyte / count / duplicate IntCodes / ordered ValueCodes)")
(doseq [[an vs] (sort-by (fn [[an _]] (str an))
                  (group-by #(get-in % [:fieldobsvarlookup/Analyte :analytelookup/AnalyteName]) vars))]
  (let [offered (->> vs (filter #(some-> (:fieldobsvarlookup/IntCode %) pos?))
                  (remove #(false? (:fieldobsvarlookup/Active %)))
                  (sort-by :fieldobsvarlookup/IntCode))
        codes   (map :fieldobsvarlookup/IntCode offered)
        dups    (->> codes frequencies (filter #(> (val %) 1)) (map key) sort vec)]
    (line " " (or an "<NO ANALYTE REF>")
      (str "n=" (count offered) "/" (count vs))
      (if (seq dups) (str "DUP-INTCODES=" dups) "")
      (pr-str (mapv :fieldobsvarlookup/ValueCode offered)))))

(line "\n-- parameters that drive the observations UI --")
(if-not (installed? :parameter/FieldObsType)
  (line "  :parameter/FieldObsType is not installed here — skipping")
  (let [ps (d/q '[:find [(pull ?p [:db/id :parameter/Name :parameter/FieldObsType
                                 {:parameter/ObsOptions [:db/id]}
                                 {:parameter/Constituent
                                  [{:constituentlookup/AnalyteCode
                                    [:db/id :analytelookup/AnalyteName]}]}]) ...]
                :where [?p :parameter/FieldObsType]] db)]
  (line "  with :parameter/FieldObsType:" (count ps))
  (line "  with :parameter/ObsOptions:  " (count (filter :parameter/ObsOptions ps)))
  (doseq [[t n] (sort (frequencies (map :parameter/FieldObsType ps)))]
    (line "   " t n))
  (let [orphan (remove #(get-in % [:parameter/Constituent :constituentlookup/AnalyteCode :db/id]) ps)]
    (line "  parameters with no Constituent->AnalyteCode:" (count orphan)
      (pr-str (mapv :parameter/Name orphan))))))

(line "\n-- stored results that reference an option --")
(when (installed? :fieldobsresult/RefResult)
  (let [refd (set (d/q '[:find [?v ...] :where [_ :fieldobsresult/RefResult ?v]] db))
        many (if (installed? :fieldobsresult/RefResults)
               (set (d/q '[:find [?v ...] :where [_ :fieldobsresult/RefResults ?v]] db)) #{})
        used (into refd many)
        by-id (into {} (map (juxt :db/id identity) vars))
        hidden (remove #(some-> (:fieldobsvarlookup/IntCode (by-id %)) pos?) used)]
    (line "  distinct options referenced:" (count used))
    (line "  referenced but NOT offered (IntCode<=0/absent):" (count hidden))
    (doseq [h hidden]
      (line "    " h (pr-str (select-keys (by-id h)
                               [:fieldobsvarlookup/ValueCode :fieldobsvarlookup/IntCode]))))))

(line "\n-- conformity: migrations already applied here --")
(let [attr (or (some #(when (some-> (d/entity db %) :db.install/_attribute) %)
                 [:conformity/conformed-norms :confirmity/conformed-norms]))]
  (if attr
    (line "  " attr (pr-str (vec (sort (d/q '[:find [?v ...] :in $ ?a :where [_ ?a ?v]] db attr)))))
    (line "   none — conformity has never run on this database")))

(line "\n=== END ===")

;; Datomic's peer threads keep the JVM alive after the script body finishes, so
;; a script run has to exit explicitly. Guarded, because the other way to run
;; this is to load-file it into a running server's REPL — where exiting would
;; take the server down with it.
;;
;; The test is whether mount has states RUNNING, not whether app namespaces are
;; loaded: any alias set including :dev auto-loads user.clj (Clojure loads
;; user.clj from a classpath root), which drags in half the app without
;; starting any of it.
(defn- server-running? []
  (boolean (when-let [rs (resolve 'mount.core/running-states)]
             (seq (rs)))))

(if (server-running?)
  (line "\n(server is running here — leaving this JVM alone)")
  (do (d/release conn) (shutdown-agents) (System/exit 0)))
