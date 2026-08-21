(ns riverdb.html.sitevisit
  "Datomic reads and writes backing the Datastar SiteVisit form.

  Values arriving here have already been decoded by riverdb.html.schema, so
  this namespace deals in longs, java.util.Dates, booleans and nils; it never
  parses a string. Encoding back out to the wire is the one direction it does
  care about, since it owns what the form displays."
  (:require
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.logging :refer [debug]]
    [datomic.api :as d]
    [riverdb.html.schema :as sc]
    [riverdb.state :as state]))

(defn db-ready?
  "The html app's mount state can start before riverdb.server/start-service has
  connected the databases, so handlers check before touching Datomic."
  []
  (some? (:base @state/state)))

;; ---------------------------------------------------------------------------
;; pulls
;; ---------------------------------------------------------------------------

(def form-pull
  "The fields the Fulcro SiteVisitForm actually renders, minus the nested
  Samples/WorkTimes editors."
  '[:db/id
    :sitevisit/SiteVisitID
    :sitevisit/Time
    :sitevisit/Notes
    :sitevisit/QACheck
    :sitevisit/SiteVisitDate
    :sitevisit/DataEntryDate
    :sitevisit/QADate
    {:sitevisit/StationID [:db/id :stationlookup/StationID :stationlookup/StationName]}
    {:sitevisit/ProjectID [:db/id :projectslookup/ProjectID :projectslookup/Name]}
    {:sitevisit/AgencyCode [:db/id :agencylookup/AgencyCode]}
    {:sitevisit/VisitType [:db/id :sitevisittype/name]}
    {:sitevisit/StationFailCode [:db/id :stationfaillookup/FailureReason]}
    {:sitevisit/DataEntryPersonRef [:db/id :person/Name]}
    {:sitevisit/CheckPersonRef [:db/id :person/Name]}
    {:sitevisit/QAPersonRef [:db/id :person/Name]}
    {:sitevisit/Visitors [:db/id :person/Name]}
    {:sitevisit/Samples [:db/id]}])

(defn pull-sitevisit
  "Nil unless `eid` names an entity that actually carries site visit attributes.
  d/pull answers {:db/id eid} for any eid at all, including unallocated ones, so
  an emptiness check is what separates a real site visit from a bad URL. Saves
  go through here too, which keeps them from writing onto an arbitrary entity."
  ([eid] (pull-sitevisit (state/db) eid))
  ([db eid]
   (when eid
     (let [sv (d/pull db form-pull eid)]
       (when (seq (dissoc sv :db/id)) sv)))))

(def ^:private list-pull
  '[:db/id
    :sitevisit/SiteVisitID
    :sitevisit/SiteVisitDate
    :sitevisit/QACheck
    {:sitevisit/StationID [:stationlookup/StationID :stationlookup/StationName]}
    {:sitevisit/ProjectID [:projectslookup/ProjectID]}])

(defn recent-sitevisits
  "The `limit` most recent site visits by date, newest first."
  ([limit] (recent-sitevisits (state/db) limit))
  ([db limit]
   (->> (d/q '[:find ?e ?date :where [?e :sitevisit/SiteVisitDate ?date]] db)
     (sort-by second)
     reverse
     (take limit)
     (map #(d/pull db list-pull (first %))))))

;; ---------------------------------------------------------------------------
;; select options
;; ---------------------------------------------------------------------------

(defn- ->option [id label]
  {:value (str id) :label (str label)})

(defn station-label [{:stationlookup/keys [StationID StationName]}]
  (str StationID ": " StationName))

(defn station-options
  "Stations on the site visit's project, falling back to every station when the
  visit has no project."
  [db project-eid]
  (->> (if project-eid
         (d/q '[:find [(pull ?st [:db/id :stationlookup/StationID :stationlookup/StationName]) ...]
                :in $ ?pj
                :where [?pj :projectslookup/Stations ?st]]
           db project-eid)
         (d/q '[:find [(pull ?st [:db/id :stationlookup/StationID :stationlookup/StationName]) ...]
                :where [?st :stationlookup/StationID]]
           db))
    (map #(->option (:db/id %) (station-label %)))
    (sort-by :label)))

(defn person-options
  "People at the site visit's agency, falling back to everyone."
  [db agency-eid]
  (->> (if agency-eid
         (d/q '[:find [(pull ?p [:db/id :person/Name]) ...]
                :in $ ?ag
                :where
                [?p :person/Name]
                [?p :person/Agency ?ag]]
           db agency-eid)
         (d/q '[:find [(pull ?p [:db/id :person/Name]) ...]
                :where [?p :person/Name]]
           db))
    (map #(->option (:db/id %) (:person/Name %)))
    (sort-by :label)))

(defn search-people
  "People at `agency-eid` whose name matches `query`, excluding `exclude` (a set
  of eids already chosen). Case-insensitive substring, matches ranked so that
  names starting with the query come first — typing \"thom\" should surface
  Thomas before Bryan Thomas."
  [db agency-eid query exclude limit]
  (let [q (str/lower-case (str/trim (or query "")))]
    (when-not (str/blank? q)
      (->> (person-options db agency-eid)
        (remove #(contains? exclude (parse-long (:value %))))
        (keep (fn [{:keys [label] :as opt}]
                (let [l (str/lower-case label)]
                  (cond
                    (str/starts-with? l q) (assoc opt :rank 0)
                    (str/includes? l q)    (assoc opt :rank 1)
                    :else nil))))
        (sort-by (juxt :rank :label))
        (take limit)
        vec))))

(defn person-name
  "Display name for a person eid, or nil."
  [db eid]
  (:person/Name (d/pull db [:person/Name] eid)))

(defn visit-type-options [db]
  (->> (d/q '[:find [(pull ?e [:db/id :sitevisittype/name]) ...]
              :where [?e :sitevisittype/name]]
         db)
    (map #(->option (:db/id %) (:sitevisittype/name %)))
    (sort-by :label)))

(defn fail-code-options [db]
  (->> (d/q '[:find [(pull ?e [:db/id :stationfaillookup/StationFailCode
                               :stationfaillookup/FailureReason]) ...]
              :where [?e :stationfaillookup/FailureReason]]
         db)
    (sort-by :stationfaillookup/StationFailCode)
    (map #(->option (:db/id %) (:stationfaillookup/FailureReason %)))))

(defn form-options
  "Every dropdown the form needs, scoped by the visit's project and agency."
  [db sv]
  (let [project (get-in sv [:sitevisit/ProjectID :db/id])
        agency  (get-in sv [:sitevisit/AgencyCode :db/id])]
    {:stations    (station-options db project)
     :people      (person-options db agency)
     :visit-types (visit-type-options db)
     :fail-codes  (fail-code-options db)}))

;; ---------------------------------------------------------------------------
;; entity <-> signals
;; ---------------------------------------------------------------------------

(def fields
  "Drives both the outgoing signals and the save diff."
  [{:signal :StationID          :attr :sitevisit/StationID          :kind :ref}
   {:signal :SiteVisitDate      :attr :sitevisit/SiteVisitDate      :kind :date}
   {:signal :Time               :attr :sitevisit/Time               :kind :string}
   {:signal :VisitType          :attr :sitevisit/VisitType          :kind :ref}
   {:signal :StationFailCode    :attr :sitevisit/StationFailCode    :kind :ref}
   {:signal :DataEntryPersonRef :attr :sitevisit/DataEntryPersonRef :kind :ref}
   {:signal :DataEntryDate      :attr :sitevisit/DataEntryDate      :kind :date}
   {:signal :CheckPersonRef     :attr :sitevisit/CheckPersonRef     :kind :ref}
   {:signal :QAPersonRef        :attr :sitevisit/QAPersonRef        :kind :ref}
   {:signal :QADate             :attr :sitevisit/QADate             :kind :date}
   {:signal :QACheck            :attr :sitevisit/QACheck            :kind :boolean}
   {:signal :Notes              :attr :sitevisit/Notes              :kind :string}])

(defn- ->wire [kind v]
  (case kind
    :ref     (str (:db/id v))
    :date    (sc/date->wire v)
    :boolean (boolean v)
    :string  (or v "")))

(defn sitevisit->signals
  "Entity -> the JSON-safe signals object the form is seeded with."
  [sv]
  (into {:Visitors (mapv #(str (:db/id %)) (:sitevisit/Visitors sv))}
    (for [{:keys [signal attr kind]} fields]
      [signal (->wire kind (get sv attr))])))

;; ---------------------------------------------------------------------------
;; save
;; ---------------------------------------------------------------------------

(defn- current [sv {:keys [attr kind]}]
  (let [v (get sv attr)]
    (case kind
      :ref     (:db/id v)
      :boolean (boolean v)
      v)))

(defn- scalar-tx [eid sv signals]
  (for [{:keys [signal attr] :as f} fields
        :when (contains? signals signal)
        :let  [new-v (get signals signal)
               old-v (current sv f)]
        :when (not= new-v old-v)]
    (if (nil? new-v)
      [:db/retract eid attr old-v]
      [:db/add eid attr new-v])))

(defn- visitors-tx [eid sv signals]
  (when (contains? signals :Visitors)
    (let [new-ids (set (:Visitors signals))
          old-ids (set (map :db/id (:sitevisit/Visitors sv)))]
      (concat
        (for [id (set/difference new-ids old-ids)] [:db/add eid :sitevisit/Visitors id])
        (for [id (set/difference old-ids new-ids)] [:db/retract eid :sitevisit/Visitors id])))))

(defn save-sitevisit!
  "Diff decoded `signals` against Datomic and transact only what changed.
  Returns {:status :saved|:unchanged|:missing|:error, :tx [...], :error msg}."
  [eid signals]
  (try
    (if-let [sv (pull-sitevisit eid)]
      (let [tx (vec (concat (scalar-tx eid sv signals) (visitors-tx eid sv signals)))]
        (if (seq tx)
          (do
            (debug "SAVE SITEVISIT" eid tx)
            @(d/transact (state/cx) tx)
            {:status :saved :tx tx})
          {:status :unchanged :tx []}))
      {:status :missing})
    (catch Exception e
      (debug "SAVE SITEVISIT FAILED" eid e)
      {:status :error :error (.getMessage e)})))
