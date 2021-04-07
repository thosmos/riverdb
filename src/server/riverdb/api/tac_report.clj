(ns riverdb.api.tac-report
  (:require
    [clojure.core.rrb-vector :as fv]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure-csv.core :as csv :refer [write-csv parse-csv]]
    [datomic.api :as d]
    [java-time :as jt]
    [riverdb.util :as util]
    [taoensso.timbre :as log :refer [debug info]]
    [thosmos.util :as tu]
    [clojure.pprint :as pprint])
  (:import (java.io Writer)
           (java.time ZonedDateTime)
           [java.math RoundingMode]
           [java.math BigDecimal]))


(defn time-from-instant [^java.util.Date ins]
  (-> ins
    (.getTime)
    (/ 1000)
    java.time.Instant/ofEpochSecond
    (java.time.ZonedDateTime/ofInstant
      (java.time.ZoneId/of "America/Los_Angeles"))))

(defn year-from-instant [^java.util.Date ins]
  (.getYear ^ZonedDateTime (time-from-instant ins)))

(defn get-agency-projects
  ([db agencies]
   ;(log/debug "get-agency-projects")
   (let [all? (some #{"ALL"} agencies)
         pjs  (if all?
                (d/q '[:find [(pull ?pj [*]) ...]
                       :where [?pj :projectslookup/AgencyCode]]
                  db)
                (d/q '[:find [(pull ?pj [*]) ...]
                       :in $ [?agency ...]
                       :where
                       [?pj :projectslookup/AgencyCode ?agency]]
                  db agencies))]
     ;(log/debug "get-agency-projects: " pjs)
     pjs)))

;; FIXME use sitevisit's timezone
(defn get-project-years [db project-id]
  ;(log/debug "get-project-years" project-id)
  (let [result (d/q '[:find [?year ...]
                      :in $ ?proj
                      :where
                      [?pj :projectslookup/ProjectID ?proj]
                      [?e :sitevisit/ProjectID ?pj]
                      [?e :sitevisit/SiteVisitDate ?date]
                      [(riverdb.api.tac-report/year-from-instant ?date) ?year]]
                 db project-id)]
    ;  (log/debug "get-project-years result" result)
    result))


(defn get-agency-project-years
  ([db agencies]
   ;(log/info "get-agency-project-years")
   (let [pjs  (get-agency-projects db agencies)
         pjis (into (sorted-map) (for [pj pjs]
                                   (let [{:projectslookup/keys [ProjectID AgencyCode Name]
                                          :db/keys             [id]} pj
                                         years (vec (map str (sort > (get-project-years db ProjectID))))
                                         ;_ (log/debug "project" ProjectID "years" years)
                                         sites (d/q '[:find [(pull ?st [:db/id :stationlookup/StationName :stationlookup/StationID]) ...]
                                                      :in $ ?pj
                                                      :where [?st :stationlookup/Project ?pj]]
                                                 db id)]
                                     ;_ (log/debug "project" ProjectID "sites" sites)]

                                     (when (seq years)
                                       [(keyword ProjectID) {:id      ProjectID
                                                             :agency  AgencyCode
                                                             :project (tu/walk-modify-k-vals pj :db/id str)
                                                             :name    Name
                                                             :years   years
                                                             :sites   (tu/walk-modify-k-vals sites :db/id str)}]))))]
     ;(log/info "get-agency-project-years results: " pjis)
     pjis)))




(defn get-sitevisit-years
  ([db project]
   (d/q '[:find [?year ...]
          :in $ ?proj
          :where
          [?pj :projectslookup/ProjectID ?proj]
          [?e :sitevisit/ProjectID ?pj]
          [?e :sitevisit/SiteVisitDate ?date]
          [(riverdb.api.tac-report/year-from-instant ?date) ?year]]
     db project)))

;(def default-query
;  [:db/id
;   [:sitevisit/SiteVisitID :as :svid]
;   [:sitevisit/SiteVisitDate :as :date]
;   [:sitevisit/Time :as :time]
;   [:sitevisit/Notes :as :notes]
;   {[:sitevisit/StationID :as :site]
;    [[:stationlookup/StationID :as :id]
;     [:stationlookup/RiverFork :as :fork]
;     [:stationlookup/ForkTribGroup :as :trib]]}
;   {[:sitevisit/StationFailCode :as :failcode]
;    [[:stationfaillookup/FailureReason :as :reason]]}
;   {:sitevisit/Samples
;    [{:sample/FieldResults
;      [:db/id :fieldresult/Result :fieldresult/FieldReplicate
;       {:fieldresult/ConstituentRowID
;        [:db/id ;:constituentlookup/HighValue :constituentlookup/LowValue
;         {:constituentlookup/AnalyteCode [:analytelookup/AnalyteShort]}
;         {:constituentlookup/MatrixCode [:matrixlookup/MatrixShort]}
;         {:constituentlookup/UnitCode [:unitlookup/Unit]}]}
;       {[:fieldresult/SamplingDeviceID :as :device]
;        [[:samplingdevice/CommonID :as :id]
;         {[:samplingdevice/DeviceType :as :type] [[:samplingdevicelookup/SampleDevice :as :name]]}]}]}]}])

(def default-query2
  [:db/id
   [:sitevisit/SiteVisitID :as :svid]
   [:sitevisit/SiteVisitDate :as :date]
   [:sitevisit/Time :as :time]
   [:sitevisit/Notes :as :notes]
   {[:sitevisit/StationID :as :station]
    [:db/id
     [:stationlookup/StationID :as :station_id]
     [:stationlookup/StationCode :as :station_code]
     [:stationlookup/RiverFork :as :river_fork]
     [:stationlookup/ForkTribGroup :as :trib_group]]}
   {[:sitevisit/StationFailCode :as :failcode]
    [[:stationfaillookup/FailureReason :as :reason]]}
   {[:sitevisit/Samples :as :samples]
    [{[:sample/SampleTypeCode :as :type]
      [[:sampletypelookup/SampleTypeCode :as :code]
       [:db/ident :as :ident]]}
     {[:sample/Constituent :as :constituent]
      [{[:constituentlookup/AnalyteCode :as :analyte]
        [[:analytelookup/AnalyteName :as :name]
         [:analytelookup/AnalyteShort :as :short]]}
       {[:constituentlookup/MatrixCode :as :matrix]
        [[:matrixlookup/MatrixName :as :name]
         [:matrixlookup/MatrixShort :as :short]]}
       {[:constituentlookup/UnitCode :as :unit]
        [[:unitlookup/Unit :as :name]]}]}
     {[:sample/DeviceID :as :device]
      [[:samplingdevice/CommonID :as :id]
       {[:samplingdevice/DeviceType :as :type]
        [[:samplingdevicelookup/SampleDevice :as :name]]}]}
     {[:sample/DeviceType :as :type]
      [[:samplingdevicelookup/SampleDevice :as :name]]}
     {[:sample/FieldResults :as :results]
      [[:fieldresult/Result :as :result]
       [:fieldresult/FieldReplicate :as :replicate]
       {[:fieldresult/ResQualCode :as :qual]
        [[:resquallookup/ResQualCode :as :code]]}]}
     {[:sample/LabResults :as :results]
      [[:labresult/Result :as :result]
       [:labresult/LabReplicate :as :replicate]
       {[:labresult/ResQualCode :as :qual]
        [[:resquallookup/ResQualCode :as :code]]}]}
     {[:sample/FieldObsResults :as :results]
      [[:fieldobsresult/IntResult :as :iresult]
       [:fieldobsresult/TextResult :as :tresult]]}]}])

(defn remap-query
  [{args :args :as m}]
  {:query (dissoc m :args)
   :args  args})

;; one graph query to pull all the data we need at once!
(defn get-sitevisits [db {:keys [agency fromDate toDate year
                                 project station stationCode query qaCheck] :as opts}]
  (log/debug "GET SITEVISITS" agency fromDate toDate project station)
  (let [fromDate (if (some? fromDate)
                   fromDate
                   (when year
                     (jt/zoned-date-time year)))
        toDate   (if (some? toDate)
                   toDate
                   (when year
                     (jt/plus fromDate (jt/years 1))))

        query    (or query default-query2)

        q      {:find  ['[(pull ?sv qu) ...]]
                :in    '[$ qu]
                :where '[]
                :args  [db query]}


        q      (cond-> q

                 ;; if station
                 station
                 (->
                   (update :where #(-> %
                                     (conj '[?st :stationlookup/StationID ?station])
                                     (conj '[?sv :sitevisit/StationID ?st])))
                   (update :in conj '?station)
                   (update :args conj station))

                 ;; if stationCode
                 stationCode
                 (->
                   (update :where #(-> %
                                     (conj '[?st :stationlookup/StationCode ?stationCode])
                                     (conj '[?sv :sitevisit/StationID ?st])))
                   (update :in conj '?stationCode)
                   (update :args conj stationCode))

                 agency
                 (->
                   (update :where #(-> %
                                     (conj '[?pj :projectslookup/AgencyCode ?agency])))
                   (update :in conj '?agency)
                   (update :args conj agency))

                 project
                 (->
                   (update :where #(-> %
                                     (conj '[?pj :projectslookup/ProjectID ?proj])))
                   (update :in conj '?proj)
                   (update :args conj project))


                 (or agency project)
                 (->
                   (update :where #(-> %
                                     (conj '[?sv :sitevisit/ProjectID ?pj]))))


                 ;; If the `fromDate` filter was passed, do the following:
                 ;; 1. add a parameter placeholder into the query;
                 ;; 2. add an actual value to the arguments;
                 ;; 3. add a proper condition against `?date` variable
                 ;; (remember, it was bound above).
                 fromDate
                 (->
                   (update :in conj '?fromDate)
                   (update :args conj (jt/java-date fromDate))
                   (update :where conj
                     '[(>= ?date ?fromDate)]))

                 ;; similar to ?fromDate
                 toDate
                 (->
                   (update :in conj '?toDate)
                   (update :args conj (jt/java-date toDate))
                   (update :where conj
                     '[(< ?date ?toDate)]))

                 ;; If either from- or to- date were passed, join the `sitevisit` entity
                 ;; and bind its `SiteVisitDate` attribute to the `?date` variable.
                 (or fromDate toDate)
                 (update :where conj
                   '[?sv :sitevisit/SiteVisitDate ?date])

                 qaCheck
                 (update q :where conj '[?sv :sitevisit/QACheck true])

                 ;; last one, in case there are no conditions, get all sitevisits
                 (empty? (:where q))
                 (->
                   (update :where conj '[?sv :sitevisit/StationID])))
        q      (remap-query q)
        ;_ (log/debug "SITEVISITS QUERY" q)
        result (d/qseq q)
        _      (log/debug "SITEVISIT RESULTS" (count result))]

    ;; this query only returns field results due to missing :db/id on sample
    ;; we can modiy this to handle labresults too, or do it somewhere else

    result))


;
;(defn get-sitevisits3
;  ([db {:keys [year agency fromDate toDate station stationCode projectID query qaCheck] :as opts}]
;   ;(debug "get-sitevisits" db opts)
;   (let [fromDate (if (some? fromDate)
;                    fromDate
;                    (when year
;                      (jt/zoned-date-time year)))
;         toDate   (if (some? toDate)
;                    toDate
;                    (when year
;                      (jt/plus fromDate (jt/years 1))))
;
;         query    (or query default-query2)
;
;         q        {:find  ['[(pull ?sv qu) ...]]
;                   :in    '[$ qu]
;                   :where '[]
;                   :args  [db query]}
;
;         q        (cond-> q
;
;                    ;; if station
;                    station
;                    (->
;                      (update :where #(-> %
;                                        (conj '[?st :stationlookup/StationID ?station])
;                                        (conj '[?sv :sitevisit/StationID ?st])))
;                      (update :in conj '?station)
;                      (update :args conj station))
;
;                    ;; if stationCode
;                    stationCode
;                    (->
;                      (update :where #(-> %
;                                        (conj '[?st :stationlookup/StationCode ?stationCode])
;                                        (conj '[?sv :sitevisit/StationID ?st])))
;                      (update :in conj '?stationCode)
;                      (update :args conj stationCode))
;
;                    agency
;                    (->
;                      (update :where #(-> %
;                                        (conj '[?pj :projectslookup/AgencyCode ?agency])
;                                        (conj '[?sv :sitevisit/ProjectID ?pj])))
;                      (update :in conj '?agency)
;                      (update :args conj agency))
;
;                    projectID
;                    (->
;                      (update :where #(-> %
;                                        (conj '[?pj :projectslookup/ProjectID ?projectID])
;                                        (conj '[?sv :sitevisit/ProjectID ?pj])))
;                      (update :in conj '?projectID)
;                      (update :args conj projectID))
;
;                    ;; If either from- or to- date were passed, join the `sitevisit` entity
;                    ;; and bind its `SiteVisitDate` attribute to the `?date` variable.
;                    (or fromDate toDate)
;                    (update :where conj
;                      '[?sv :sitevisit/SiteVisitDate ?date])
;
;                    ;; If the `fromDate` filter was passed, do the following:
;                    ;; 1. add a parameter placeholder into the query;
;                    ;; 2. add an actual value to the arguments;
;                    ;; 3. add a proper condition against `?date` variable
;                    ;; (remember, it was bound above).
;                    fromDate
;                    (->
;                      (update :in conj '?fromDate)
;                      (update :args conj (jt/java-date fromDate))
;                      (update :where conj
;                        '[(> ?date ?fromDate)]))
;
;                    ;; similar to ?fromDate
;                    toDate
;                    (->
;                      (update :in conj '?toDate)
;                      (update :args conj (jt/java-date toDate))
;                      (update :where conj
;                        '[(< ?date ?toDate)]))
;
;                    qaCheck
;                    (update q :where conj '[?sv :sitevisit/QACheck true]))
;
;
;         ;;; last one, in case there are no conditions, get all sitevisits
;         ;(empty? (:where q))
;         ;(->
;         ;  (update :where conj '[?sv :sitevisit/StationID])))
;
;         q        (update q :where conj '[?sv :sitevisit/QACheck true])
;
;         q        (remap-query q)]
;     ;; this query only returns field results due to missing :db/id on sample
;     ;; we can modiy this to handle labresults too, or do it somewhere else
;     (debug "QUERY" q)
;
;     (d/qseq q))))



;(defn filter-sitevisits [svs]
;  (vec (filter #(< (get-in % [:site :id]) 500) svs)))



;(defn pull-no-results [db eids]
;  (d/q '[:find [(pull ?eid
;                  [:db/id :sitevisit/SiteVisitID :sitevisit/SiteVisitDate :sitevisit/QACheck :sitevisit/Notes
;                   {:sitevisit/StationID [:stationlookup/StationID :stationlookup/RiverFork]}
;                   {:sitevisit/StationFailCode [:stationfaillookup/FailureReason]}
;                   {:samplingcrew/_SiteVisitID [{:samplingcrew/PersonID [:person/FName :person/LName]}]}]) ...]
;         :in $ [?eid ...]
;         :where [?eid]] db eids))

(defn pull-no-result [db eid]
  (let [x (d/pull db
            [:db/id [:sitevisit/SiteVisitDate :as :date] [:sitevisit/Notes :as :notes]
             {[:sitevisit/StationID :as :site] [[:stationlookup/StationID :as :id]]}
             {[:sitevisit/StationFailCode :as :reason] [[:stationfaillookup/FailureReason :as :reason]]}
             {[:sitevisit/Visitors :as :crew] [[:person/FName :as :fname] [:person/LName :as :lname]]}]
            eid)]
    (-> x
      (assoc :site (get-in x [:site :id]))
      (assoc :reason (get-in x [:reason :reason]))
      (assoc :crew (clojure.string/join ", "
                     (map #(let [peep %]
                             (str (:fname peep) " " (:lname peep)))
                       (:crew x)))))))


;;;;;; utilities

(defn vec-remove [pos coll]
  (fv/catvec (fv/subvec coll 0 pos) (fv/subvec coll (inc pos) (count coll))))

(defn round2
  "Round a double to the given precision (number of significant digits)"
  [precision d]
  (if (< d 0.01)
    d
    (let [factor (Math/pow 10 precision)]
      (/ (Math/round (* d factor)) factor))))

(defn square [n] (* n n))

(defn mean [a] (/ (reduce + a) (count a)))

(defn std-dev
  [a]
  (let [mn (mean a)]
    (Math/sqrt
      (/ (reduce #(+ %1 (square (- %2 mn))) 0 a)
        (dec (count a))))))

(defn percent-rds [avg stddev]
  (* (/ stddev avg) 100))

(defn percent-dev [val avg]
  (/ (Math/abs (- val avg)) avg))

;; if it's nil, make it 0
(defn inc! [val]
  (inc (or val 0)))

;; if it's nil, make it a []
(defn conjv [coll val]
  ((fnil conj []) coll val))

;;;;; data manip

;; run through all values and calc max relative diffs from the others
(defn calc-diffs [vals]
  (into []
    (for [v vals]
      (apply + (for [w vals]
                 (round2 10 (Math/abs (- v w))))))))

(defn reduce-outlier-idx [diffs]
  (:outlier-idx
    (reduce (fn [{:keys [i outlier outlier-idx] :as init} diff]
              (let [max         (- diff outlier)
                    outlier?    (pos? max)
                    outlier     (if outlier? diff outlier)
                    outlier-idx (if outlier? i outlier-idx)]
                {:outlier outlier :outlier-idx outlier-idx :i (inc i)}))
      {:outlier 0.0 :outlier-idx 0 :i 0} diffs)))

(defn find-outlier-idx [vals]
  (let [diffs   (calc-diffs vals)
        ;_       (println "diffs" diffs)
        outlier (reduce-outlier-idx diffs)]
    outlier))

(defn remove-outliers
  "remove values that are the biggest outliers, one at a time"
  [target vals]
  (loop [vals vals]
    (if (> (count vals) target)
      (recur (vec-remove (find-outlier-idx vals) vals))
      (vec vals))))



(defn reduce-fieldresults-to-map
  "reduce a set of fieldresults into a map of parameter maps indexed by parameter name"
  [fieldresults]
  (reduce (fn [init {:keys [fieldresult/Result fieldresult/ConstituentRowID device] :as fieldresult}]
            (let [c         ConstituentRowID
                  cid       (get c :db/id)
                  analyte   (get-in c [:constituentlookup/AnalyteCode :analytelookup/AnalyteShort])
                  matrix    (get-in c [:constituentlookup/MatrixCode :matrixlookup/MatrixShort])
                  unit      (get-in c [:constituentlookup/UnitCode :unitlookup/Unit])
                  device    (str (get-in device [:type :name]) " - " (:id device))
                  ;device (get-in device [:type :name])
                  analyte-k (keyword (str matrix "_" analyte))
                  ;analyte-k (keyword analyte)
                  init      (if (= nil (get init analyte-k))
                              (assoc init analyte-k {})
                              init)
                  init      (update init analyte-k
                              (fn [k] (-> k
                                        (update :vals conjv Result)
                                        (assoc :device device)
                                        (assoc :unit unit)
                                        (assoc :matrix matrix)
                                        (assoc :analyte analyte))))]


              init))
    {} fieldresults))

(defn reduce-samples-to-map
  "reduce a set of samples into a map of parameter maps indexed by parameter name"
  [samples]
  (reduce (fn [init {:keys [constituent results device type]}]
            (let [c         constituent
                  analyte   (or (get-in c [:analyte :short]) (get-in c [:analyte :name]))
                  matrix    (or (get-in c [:matrix :short]) (get-in c [:matrix :name]))
                  unit      (get-in c [:unit :name])
                  device    (when device (str (get-in device [:type :name]) " - " (:id device)))
                  type      (let [t (:code type)]
                              (if (= "Grab" t)
                                "Lab"
                                t))
                  matrix    (if (= type "FieldObs")
                              "FieldObs"
                              matrix)
                  analyte-k (keyword (str matrix "_" analyte))
                  summary   (case type
                              "Lab"
                              (let [vals (when (seq results)
                                           (->> results
                                             (sort-by :replicate)
                                             (mapv :result)))
                                    vals (vec (remove nil? vals))
                                    qual (get-in (first results) [:qual :code])]
                                (cond->
                                  {:type    type
                                   :count   (count vals)
                                   :vals    vals
                                   :unit    unit
                                   :matrix  matrix
                                   :analyte analyte}
                                  qual
                                  (assoc :qual qual)))
                              "FieldObs"
                              (let [{:keys [iresult tresult]} (first results)]
                                {:type    type
                                 :analyte analyte
                                 :intresult iresult
                                 :textresult tresult})
                              "FieldMeasure"
                              (let [vals (when (seq results)
                                           (->> results
                                             (sort-by :replicate)
                                             (mapv :result)))
                                    vals (vec (remove nil? vals))
                                    qual (get-in (first results) [:qual :code])]
                                (cond->
                                  {:type    type
                                   :vals    vals
                                   :unit    unit
                                   :matrix  matrix
                                   :analyte analyte}
                                  device
                                  (assoc :device device)
                                  qual
                                  (assoc :qual qual))))]

              (assoc init analyte-k summary)))
    {} samples))


;; FIXME per group

(def qapp-requirements
  {:include-elided? false
   :min-samples     3
   :elide-extra?    true
   :params          {:H2O_Temp {:precision  {:unit 0.5}
                                :exceedance {:high 20.0}}
                     :H2O_Cond {:precision {:range 10.0}}
                     :H2O_DO   {:precision  {:percent 5.0}
                                :exceedance {:low 7.0}}
                     :H2O_pH   {:precision  {:unit 0.2}
                                :exceedance {:low  6.5
                                             :high 8.5}}
                     :H2O_Turb {:precision {:percent   5.0
                                            :unit      0.3
                                            :threshold 10.0}}
                     :H2O_PO4  {:precision {:percent   10.0
                                            :unit      0.03
                                            :threshold 0.1}}
                     :H2O_NO3  {:precision {:percent   10.0
                                            :unit      0.03
                                            :threshold 0.1}}}})


;(def params
;  [:Air_Temp :H2O_Temp :H2O_Cond :H2O_DO :H2O_pH :H2O_Turb])

;; FIXME per group

(def param-config
  {:Air_Temp     {:order 0 :count 1 :name "Air_Temp"}
   :H2O_Temp     {:order 1 :count 3 :name "H2O_Temp"}
   :H2O_Cond     {:order 2 :count 3 :name "Cond"}
   :H2O_DO       {:order 3 :count 3 :name "DO"}
   :H2O_pH       {:order 4 :count 3 :name "pH"}
   :H2O_Turb     {:order 5 :count 3 :name "Turb"}
   :H2O_NO3      {:order 6 :count 3 :name "NO3" :optional true}
   :H2O_PO4      {:order 7 :count 3 :name "PO4" :optional true}
   :H2O_Velocity {:elide? true}})

(def param-config-ssi
  {:Air_Temp     {:order 0 :count 1 :name "Air_Temp"}
   :H2O_Temp     {:order 1 :count 6 :name "H2O_Temp"}
   :H2O_Cond     {:order 2 :count 3 :name "Cond"}
   :H2O_DO       {:order 3 :count 3 :name "DO"}
   :H2O_pH       {:order 4 :count 3 :name "pH"}
   :H2O_Turb     {:order 5 :count 3 :name "Turb"}
   :H2O_NO3      {:order 6 :count 3 :name "NO3" :optional true}
   :H2O_PO4      {:order 7 :count 3 :name "PO4" :optional true}
   :H2O_Velocity {:elide? true}})

;(def param-config-ssi
;  {:Air_Temp {:order 0
;              :count 1
;              :name  "Air Temp"}
;   :H2O_Temp {:order      1
;              :count      6
;              :name       "Water Temp"
;              :precision  {:unit 1.0}
;              :exceedance {:high 20.0}}
;   :H2O_Cond {:order     2
;              :count     3
;              :name      "µS Conductivity"
;              :precision {:unit 10.0}}
;   :H2O_DO   {:order      3
;              :count      3
;              :name       "Dissolved Oxygen"
;              :precision  {:percent 10.0}
;              :exceedance {:low 7.0}}
;   :H2O_pH   {:order      4 :count 3 :name "pH"
;              :precision  {:unit 0.4}
;              :exceedance {:low  6.5
;                           :high 8.5}}
;   :H2O_Turb {:order     5 :count 3 :name "Turbidity"
;              :precision {:percent   10.0
;                          :unit      0.6
;                          :threshold 10.0}}
;   :H2O_NO3  {:order     6 :count 3 :name "NO3"
;              :precision {:percent   20.0
;                          :unit      0.06
;                          :threshold 0.1}}
;   :H2O_PO4  {:order     7 :count 3 :name "PO4"
;              :precision {:percent   20.0
;                          :unit      0.06
;                          :threshold 0.1}}})

(defn calc [vals]
  (let [mn     (mean vals)
        _min   (apply min vals)
        _max   (apply max vals)
        stddev (std-dev vals)
        rng    (- _max _min)
        rsd    (percent-rds mn stddev)]

    (println (format "Values: %.1f, %.1f, %.1f \nMean: %.1f \nMin: %.1f \nMax: %.1f \nRange: %.1f \nStdDev: %.2f \nRSD: %.2f "
               (get vals 0) (get vals 1) (get vals 2) mn _min _max rng stddev rsd))
    {:vals   vals
     :range  rng
     :mean   mn
     :stddev stddev
     :max    _max
     :min    _min
     :rsd    rsd}))

(defn calc2 [vals]
  (let [mn     (mean vals)
        _min   (apply min vals)
        _max   (apply max vals)
        stddev (std-dev vals)
        rng    (- _max _min)
        rsd    (percent-rds mn stddev)]
    (println (format "Values: %.2f, %.2f, %.2f \nMean: %.2f \nMin: %.2f \nMax: %.2f \nRange: %.2f \nStdDev: %.3f \nRSD: %.3f "
               (get vals 0) (get vals 1) (get vals 2) mn _min _max rng stddev rsd))
    {:vals   vals
     :mean   mn
     :stddev stddev
     :range  rng
     :max    _max
     :min    _min
     :rsd    rsd}))

(defn get-scale [vals]
  (apply
    max (for [val vals]
          (.scale (bigdec val)))))

(defn max-precision [bigvals]
  (->> bigvals
    (map #(.precision %))
    (apply max)))

(defn bigmean [prec bigvals]
  (let [cnt (count bigvals)]
    (with-precision prec :rounding RoundingMode/HALF_EVEN
      (->> bigvals
        (apply +)
        (#(/ % cnt))))))

(defn bigstddev
  [prec vals]
 (let [mn (bigmean prec vals)]
   (with-precision prec :rounding RoundingMode/HALF_EVEN
                        (/ (reduce #(+ %1 (square (- %2 mn))) 0 vals)
                          (dec (count vals))))))

(defn calc3 [scale vals]
  (let [bigvals  (vec (map #(.setScale (bigdec %) scale) vals))
        prec     (max-precision bigvals)
        mn       (.setScale (bigmean prec bigvals) scale)
        _min     (apply min bigvals)
        _max     (apply max bigvals)
        plus     (- _max mn)
        minus    (- mn _min)
        pm       (max plus minus)
        plus%    (with-precision prec :rounding RoundingMode/HALF_EVEN (* 100 (/ plus mn)))
        minus%   (with-precision prec :rounding RoundingMode/HALF_EVEN (* 100 (/ minus mn)))
        pm%      (max plus% minus%)
        stddev   (with-precision prec (* (bigdec (std-dev vals)) 1))
        rng      (- _max _min)
        ;rsd    (percent-rds mn stddev)
        frmt-str (str "Values: %s, %s, %s \nMean: %s \nMin: %s \nMax: %s \nRange: %s \nStdDev: %s \n±: %s \n±%% %s \nResult: %4$s ± %9$s ± %10$s %%")]
    (println (format frmt-str
               (get bigvals 0) (get bigvals 1) (get bigvals 2) mn _min _max rng stddev pm pm%))
    {:vals   bigvals
     :mean   mn
     :stddev stddev
     :range  rng
     :max    _max
     :min    _min
     :±      pm
     :±%     pm%}))



(defn calc-param-summaries
  "calculate summaries for each parameter map entry"
  [fieldresult]
  (into {}
    (for [[k {:keys [type vals] :as v}] fieldresult]
      (do
        ;(println "CALC " fieldresult)
        (try
          (let [
                ;; calculate sample quantity exceedance
                qapp            qapp-requirements
                qapp?           (some? (get-in qapp [:params k]))
                qapp-samples    (get qapp :min-samples 0)
                val-count       (count vals)
                too-many?       (and qapp? (> val-count qapp-samples))
                incomplete?     (and qapp? (< val-count qapp-samples))

                ;; if too many, do we throw out the outliers?
                elide?          (and too-many? (:elide-extra? qapp))
                include-elided? (:include-elided? qapp)
                ;; save the original set of values before removal
                orig-vals       vals
                vals            (if elide?
                                  (remove-outliers qapp-samples vals)
                                  vals)
                val-count       (count vals)
                vals?           (seq vals)]

            (cond
              (= type "FieldObs")
              [k v]

              (= type "Lab")
              [k v]

              (not vals?)
              (let [v (merge v {:incomplete? true
                                :invalid?    true
                                :count       0
                                :vals        vals})]
                [k v])


              :else
              (let [
                    ;; calculate
                    mx           (apply max vals)
                    mn           (apply min vals)
                    ;; FIXME clamp range to 4 decimals to eliminate float stragglers - use sig figs?
                    range        (round2 4 (- mx mn))
                    stddev       (if (> range 0.0) (std-dev vals) 0.0)
                    mean         (if (> val-count 1)
                                   (mean vals)
                                   (first vals))

                    ;prec-percent (if (> range 0.0) (percent-rds mean stddev) 0.0)
                    ;prec-unit    range
                    prec-percent (if (> range 0.0)
                                   (max (percent-dev mx mean) (percent-dev mn mean))
                                   0.0)

                    prec-unit    (if (> range 0.0)
                                   (max (- mean mn) (- mx mean))
                                   0.0)

                    ;; calculate precision thresholds
                    req-percent  (get-in qapp [:params k :precision :percent])
                    req-unit     (get-in qapp [:params k :precision :unit])
                    req-range    (get-in qapp [:params k :precision :range])

                    threshold    (when (and req-percent req-unit)
                                   (or
                                     (get-in qapp [:params k :precision :threshold])
                                     (* (/ 100.0 req-percent) req-unit)))
                    imprecision? (when (and qapp? (> val-count 1))
                                   (if threshold
                                     (if (> mean threshold)
                                       (if
                                         (> prec-percent req-percent)
                                         (do
                                           ;(println "PRECISION % " k threshold prec-percent req-percent)
                                           true)
                                         false)
                                       (if
                                         (> prec-unit req-unit)
                                         (do
                                           ;(println "PRECISION unit " k threshold prec-unit req-unit)
                                           true)
                                         false))
                                     (cond
                                       req-range
                                       (> range req-range)
                                       req-percent
                                       (> prec-percent req-percent)
                                       req-unit
                                       (> prec-unit req-unit))))

                    ;;; calculate quality exceedance
                    high         (get-in qapp [:params k :exceedance :high])
                    low          (get-in qapp [:params k :exceedance :low])
                    too-high?    (and qapp? (some? high) (> mean high))
                    too-low?     (and qapp? (some? low) (< mean low))
                    invalid?     (and qapp? (or incomplete? imprecision?))
                    exceedance?  (and qapp? (not invalid?) (or too-low? too-high?))

                    v            (merge v {:invalid?      invalid?
                                           :exceedance?   exceedance?
                                           :is_valid      (not invalid?)
                                           :is_exceedance exceedance?
                                           :count         val-count
                                           :max           mx
                                           :min           mn
                                           :range         (round2 2 range)
                                           :stddev        (round2 2 stddev)
                                           :mean          (round2 2 mean)
                                           :prec          (if (some? prec-percent) (round2 2 prec-percent) nil)
                                           :vals          vals})

                    v            (cond-> v
                                   exceedance?
                                   (merge {:too-low?    too-low?
                                           :too-high?   too-high?
                                           :is_too_low  too-low?
                                           :is_too_high too-high?})
                                   invalid?
                                   (merge {:incomplete?   incomplete?
                                           :imprecise?    imprecision?
                                           :is_incomplete incomplete?
                                           :is_imprecise  imprecision?})
                                   (and elide? include-elided?)
                                   (merge {:orig-vals orig-vals
                                           :orig_vals orig-vals}))]

                [k v])))

          (catch Exception ex (println "ERROR in calc-param-summaries" ex fieldresult)))))))

(defn calc-sitevisit-stats [sv]
  (let [frs (:fieldresults sv)
        sv  (reduce-kv
              (fn [sv fr-k fr-m]
                (cond-> sv
                  (:invalid? fr-m)
                  (update :count-params-invalid inc!)
                  (:exceedance? fr-m)
                  (update :count-params-exceedance inc!)))
              sv frs)
        sv  (assoc sv :invalid? (some? (:count-params-invalid sv)))
        sv  (assoc sv :exceedance? (some? (:count-params-exceedance sv)))]
    sv))

(defn create-sitevisit-summaries3
  "create summary info per sitevisit"
  [sitevisits]
  (for [sv sitevisits]
    (try
      (let [
            ;_        (debug "processing SV " (get-in sv [:sitevisit/StationID
            ;                                    :stationlookup/StationID]))

            ;; remap nested values to root
            sv   (-> sv
                   (assoc :trib (get-in sv [:station :trib]))
                   (assoc :fork (get-in sv [:station :river_fork]))
                   (assoc :failcode (get-in sv [:failcode :reason]))
                   (assoc :site (get-in sv [:station :station_id])))

            ;; pull out samples list
            sas  (get-in sv [:samples])

            _    (debug "reduce Samples to param sums")
            sums (reduce-samples-to-map sas)
            _    (debug "calc param sums")
            sums (calc-param-summaries sums)
            ;; remove the list of samples and add the new param map
            sv   (-> sv
                   ;(dissoc :samples)
                   (assoc :fieldresults sums))
            ;_   (debug "count valid params")
            ;; count valid params

            sv   (calc-sitevisit-stats sv)]
        ;_    (debug "SV" (pprint/pprint sv))]


        sv)
      (catch Exception ex (debug "Error in create-param-summaries" ex)))))


(defn read-csv [filename]
  (with-open
    [^java.io.Reader r (clojure.java.io/reader filename)]
    (let [csv    (parse-csv r)
          header (first csv)
          hidx   (into {} (for [i (range (count header))]
                            [(get header i) i]))
          csv    (rest csv)]
      (doall
        (for [row csv]
          (let [sv  {:site (get row (get hidx "Site"))
                     :svid (get row (get hidx "SiteVisitID"))
                     :date (get row (get hidx "Date"))
                     :time (get row (get hidx "Time"))}
                ;_ (println "DAtE" (type (get row (get hidx "Date"))))
                frs (into {}
                      (for [[k v] (:params qapp-requirements)]
                        (let [sample-count (get-in param-config [k :count])]
                          [k {:vals (vec (remove nil?
                                           (for [i (range 1 (+ sample-count 1))]
                                             (let [csv_field (str (name k) "_" i)
                                                   csv_idx   (get hidx csv_field)]
                                               (when csv_idx
                                                 (let [field_val (get row csv_idx)]
                                                   (edn/read-string field_val)))))))}])))
                ;frs (calc-param-summaries frs)
                sv  (assoc sv :results frs)]
            ;sv (calc-sitevisit-stats sv)


            sv))))))


(defn csv-sitevisit-summaries
  "import csv dat into the same format as the reduce-fieldresults-to-map function"
  [filename]
  (with-open
    [^java.io.Reader r (clojure.java.io/reader filename)]
    (let [csv    (parse-csv r)
          header (first csv)
          hidx   (into {} (for [i (range (count header))]
                            [(get header i) i]))
          csv    (rest csv)]
      (doall
        (for [row csv]
          (let [sv  {:site (get row (get hidx "Site"))
                     :svid (get row (get hidx "SiteVisitID"))
                     :date (get row (get hidx "Date"))
                     :time (get row (get hidx "Time"))}
                ;_ (println "DAtE" (type (get row (get hidx "Date"))))
                frs (into {}
                      (for [[k v] (:params qapp-requirements)]
                        (let [sample-count (get-in param-config [k :count])]
                          [k {:vals (vec (remove nil?
                                           (for [i (range 1 (+ sample-count 1))]
                                             (let [csv_field (str (name k) "_" i)
                                                   csv_idx   (get hidx csv_field)]
                                               (when csv_idx
                                                 (let [field_val (get row csv_idx)]
                                                   (edn/read-string field_val)))))))}])))
                frs (calc-param-summaries frs)
                sv  (assoc sv :fieldresults frs)
                sv  (calc-sitevisit-stats sv)]
            sv))))))


(defn simplify-sv [sv param]
  (assoc sv :fieldresults (get-in sv [:fieldresults param])))

(defn report-reducer
  "generate a map with summary info over whole time period, including details for each parameter in :z-params"
  [db sitevisits]
  (reduce
    (fn [r {:keys [fieldresults] :as sv}]
      (try (let [param-keys (keys (:params qapp-requirements))

                 ;_        (println "SV " (get-in sv [:sitevisit/StationID
                 ;                                    :stationlookup/StationID]))

                 ;; count all sitevisits
                 r          (update r :count-sitevisits inc!)

                 r          (cond-> r
                              ;; if the sitevisit has no fieldresults, add it to a :no-results list
                              (empty? fieldresults)
                              (->
                                ;(update :no-results-rs conjv sv-ident)
                                ;#(do
                                ;   (println "EMPTY" sv)
                                ;   %)
                                (update :count-no-results inc!)
                                (update :no-results-rs conjv (when db (pull-no-result db (:db/id sv)))))
                              ;; otherwise add it to :results list
                              (seq fieldresults)
                              (->
                                (update :count-results inc!)))



                 ;;_ (println "reduce FRs")
                 ;; reduce the fieldresults to summarize

                 r          (reduce-kv
                              (fn [r fr-k fr-m]
                                (if (some #(= fr-k %) param-keys)
                                  (-> r
                                    ;; total count of results
                                    (update :count-params inc!)
                                    ;; count each param
                                    (update-in [:z-params fr-k :count] inc!)

                                    ;; count all exceedances
                                    (cond->

                                      ;; too few
                                      (:incomplete? fr-m)
                                      (->
                                        (update :invalid-incomplete inc!)
                                        ;(update :exceeds-total inc!)
                                        ;(update-in [:z-params fr-k :too-few-rs] conjv sv-ident)
                                        (update-in [:z-params fr-k :invalid-incomplete] inc!))

                                      ;; precision exceedance
                                      (:imprecise? fr-m)
                                      (->
                                        ;(update :qapp-rs conjv sv-ident)
                                        ;(update :exceeds-total inc!)
                                        (update :invalid-imprecise inc!)
                                        ;(update-in [:z-params fr-k :prec-exceeds-rs] conjv sv-ident)
                                        (update-in [:z-params fr-k :invalid-imprecise] inc!))

                                      ;; water quality
                                      (:too-high? fr-m)
                                      (->
                                        ;(update :qual-rs conjv sv-ident)
                                        ;(update :exceeds-total inc!)
                                        (update :exceed-high inc!)
                                        (update-in [:z-params fr-k :exceed-high] inc!))

                                      ;; water quality
                                      (:too-low? fr-m)
                                      (->
                                        ;(update :qual-rs conjv sv-ident)
                                        ;(update :exceeds-total inc!)
                                        (update :exceed-low inc!)
                                        (update-in [:z-params fr-k :exceed-low] inc!))

                                      ;; valid
                                      (:invalid? fr-m)
                                      (->
                                        (update :count-params-invalid inc!)
                                        (update-in [:z-params fr-k :invalid] inc!)
                                        (update-in [:z-params fr-k :invalid-rs] conjv (simplify-sv sv fr-k)))

                                      ;; valid
                                      (not (:invalid? fr-m))
                                      (->
                                        (update :count-params-valid inc!)
                                        (update-in [:z-params fr-k :valid] inc!))

                                      ;; exceedance
                                      (and (not (:invalid? fr-m)) (:exceedance? fr-m))
                                      (->
                                        (update :count-params-exceedance inc!)
                                        (update-in [:z-params fr-k :exceedance] inc!)
                                        (update-in [:z-params fr-k :exceedance-rs] conjv (simplify-sv sv fr-k)))))
                                  r))
                              r fieldresults)

                 r          (reduce-kv
                              (fn [r k m]
                                (let [total          (:count m)
                                      valid          (get m :valid 0)
                                      valid-percent  (when (> valid 0)
                                                       (round2 2 (* 100 (/ valid total))))
                                      exceedance     (get m :exceedance 0)
                                      non-exceed     (- valid exceedance)
                                      exceed-percent (when (> valid 0)
                                                       (round2 2 (* 100 (/ non-exceed valid))))]

                                  (-> r
                                    (assoc-in [:z-params k :percent-valid] valid-percent)
                                    (assoc-in [:z-params k :percent-exceedance] exceed-percent))))

                              r (:z-params r))]


             r)
           (catch Exception ex (println ex sv))))
    {} sitevisits))



(defn report-stats [{:keys [param-config] :as report}]
  (let [count-svs                 (get report :count-sitevisits 0)
        noresults                 (get report :count-no-results 0)
        results                   (get report :count-results 0)
        count-z-params            (count (:z-params report))
        count-params-planned      (* count-z-params count-svs)
        count-params-possible     (* count-z-params results)
        count-params              (get report :count-params 0)
        count-params-invalid      (get report :count-params-invalid 0)
        count-params-valid        (get report :count-params-valid 0)
        percent-params-valid      (if (> count-params 0)
                                    (round2 2 (* (/ count-params-valid count-params) 100))
                                    0)
        count-params-exceedance   (get report :count-params-exceedance 0)
        ;count-params-exceedable   ()
        percent-params-exceedance (if (> count-params-valid 0)
                                    (round2 2 (* (/ count-params-exceedance count-params-valid) 100))
                                    0)
        results                   (get report :count-results 0)
        percent                   (if (> count-svs 0)
                                    (round2 2 (* (/ results count-svs) 100))
                                    0)
        report                    (reduce-kv
                                    (fn [r k m]
                                      (let [order        (get-in param-config [k :order] 0)
                                            sample-count (get-in param-config [k :count] 3)]
                                        (-> r
                                          (assoc-in [:z-params k :display-order] order)
                                          (assoc-in [:z-params k :sample-count] sample-count))))
                                    report (:z-params report))]

    (-> report
      (assoc :percent-complete percent)
      ;(assoc :percent-valid percent-sv-valid)
      ;(assoc :count-params-valid count-params-valid)
      (assoc :count-params-planned count-params-planned)
      (assoc :count-params-possible count-params-possible)
      (assoc :percent-params-valid percent-params-valid)
      (assoc :count-params-exceedance count-params-exceedance)
      (assoc :percent-params-exceedance percent-params-exceedance))))

(defn print-report [report]
  (println (-> (pr-str report)
             (.replace ":" "\n") (.replace "{" "\n") (.replace "}" "\n")
             (.replace "," "") (.replace "z-params" ""))))


(s/def ::report-year (s/or :year integer? :all nil?))

(defn check [type data]
  (if (s/valid? type data)
    true
    (throw (AssertionError. (s/explain type data)))))

(defn get-project-name [db projCode]
  (d/q '[:find ?pjnm .
         :in $ ?pj
         :where
         [?e :projectslookup/ProjectID ?pj]
         [?e :projectslookup/Name ?pjnm]]
    db projCode))

(defn get-annual-report [db agency project year]
  ;{:pre [true (check ::report-year year)]}
  (let [year       (if (= year "") nil (Long/parseLong year))
        _          (check ::report-year year)
        ;agency     (or agency "SYRCL")
        done?      false
        _          (log/info "BEGINNING TAC REPORT" agency project year)
        ;start-time (jt/zoned-date-time year)
        ;end-time   (jt/plus start-time (jt/years 1))
        sitevisits (if year
                     (get-sitevisits db {:agency agency :project project :year year})
                     (get-sitevisits db {:agency agency}))
        _          (debug "svs" (first sitevisits))
        ;sitevisits (filter-sitevisits sitevisits)
        sitevisits (create-sitevisit-summaries3 sitevisits)
        _          (println "report reducer")
        ;param-keys (reduce #(apply conj %1 (map first (get-in %2 [:fieldresults]))) #{} sitevisits)
        report     (report-reducer db sitevisits)
        report     (-> report
                     (assoc :agency agency)
                     (assoc :report-year year)
                     (assoc :qapp-requirements qapp-requirements)
                     (assoc :param-config (if (= agency "SSI")
                                            param-config-ssi
                                            param-config))
                     (assoc :project (get-project-name db project)))
        ;_          (println "REPORT: "
        ;             (select-keys report
        ;               [:count-sitevisits :count-params-valid :count-params-exceedance]))
        report     (report-stats report)
        _          (log/info "TAC REPORT COMPLETE")]
    report))

(defn filter-empty-fieldresults [svs]
  (remove #(and
             (empty? (:fieldresults %))
             (= (:failcode %) "None")) svs))


(defn get-dataviz-data
  [db agency project year]
  (try
    (let [;_            (log/info "GET-DATAVIZ!" year project)
          year         (if (= year "") nil (Long/parseLong year))
          ;_            (check ::report-year year)
          param-config (into {} (remove #(:elide? (val %)) param-config))
          svs          (get-sitevisits db {:project project :year year})
          ;_            (debug "sitevisits" (count svs))
          svs          (create-sitevisit-summaries3 svs)
          ;_            (debug "sitevisits filtered" (count svs))
          svs          (filter-empty-fieldresults svs)
          ;_            (debug "sitevisits summaries" (count svs))
          svs          (sort-by :date svs)
          ;_            (debug "sitevisits first" (first svs))
          ;_            (debug "sitevisits last" (last svs))
          result       {:results-rs        svs
                        :param-config      param-config
                        :qapp-requirements qapp-requirements
                        :report-year       year
                        :agency            agency
                        :project           (get-project-name db project)}]
      result)
    (catch Exception ex (log/error "DATAVIZ ERROR" (.getMessage ex)))))

(defn get-annual-report-csv [filename]
  ;{:pre [true (check ::report-year year)]}
  (let [_      (log/info "BEGINNING CSV TAC REPORT")
        svs    (csv-sitevisit-summaries filename)
        report (report-reducer nil svs)
        report (report-stats report)
        report (-> report
                 (assoc :report-year nil)
                 (assoc :qapp-requirements qapp-requirements)
                 (assoc :param-config param-config))
        _      (log/info "CSV TAC REPORT COMPLETE")]
    report))



(defn sitevisit->csv [sv param-config]
  (let [date-val   [(jt/format "YYYY-MM-dd" (time-from-instant (:date sv)))]
        head-vals  (for [p [:site :time :fork :trib]]
                     (str (get sv p)))
        tail-vals  (for [p [:count-params-invalid :count-params-exceedance :db/id :svid :failcode :notes]]
                     (str (get sv p)))
        param-vals (map str
                     (flatten
                       (doall (for [[p-name p] param-config]
                                (let [rs           (get-in sv [:fieldresults p-name])
                                      vals         (:vals rs)
                                      no-vals?     (not vals)
                                      sample-count (:count p)
                                      {:keys [mean stddev prec range count device invalid? exceedance? incomplete? too-low? too-high? imprecise?]} rs]
                                  (if (= sample-count 1)
                                    [mean device]
                                    [(get vals 0) (get vals 1) (get vals 2)
                                     mean stddev prec
                                     range count device
                                     (when invalid? "1")
                                     (when exceedance? "1")]))))))]

    (write-csv [(concat date-val head-vals param-vals tail-vals)] :force-quote true)))

(defn sitevisits->csv
  ([svs p-cfg]
   (sitevisits->csv *out* svs p-cfg))
  ([^Writer writer sitevisits param-config]
   ;(println "sitevisits->csv" (map #(clojure.string/capitalize (name %)) (keys (first sitevisits))))
   (let [heads      ["Date" "Site" "Time" "Fork" "Trib"]
         body-1     ["1" "Device"]
         body-3     ["1" "2" "3" "Mean" "StDev" "Prec" "Range" "Count" "Device" "Invalid" "Exceed"]
         tails      ["Invalid_Total" "Exceed_Total" "ID" "SVID" "Failcode" "Notes"]
         ps         (flatten
                      (for [[p-nm p] param-config]
                        (let [sample-count (:count p)
                              fields       (if (= sample-count 1)
                                             body-1
                                             body-3)]
                          (for [field fields]
                            (let [name (str (:name p) "_" field)]
                              name)))))
         head-coll  [(concat heads ps tails)]
         csv-header (write-csv head-coll)]

     (.write writer csv-header)
     (doseq [sv sitevisits]
       (.write writer (sitevisit->csv sv param-config)))
     (.flush writer))))


(defn csv-all-years
  ([db agency]
   (csv-all-years *out* db agency))
  ([^Writer writer db agency]
   (let [_            (log/info "CSV-ALL-YEARS" agency)
         param-config (remove #(:elide? (val %)) param-config)
         sitevisits   (get-sitevisits db {:agency agency})
         sitevisits   (create-sitevisit-summaries3 sitevisits)
         sitevisits   (filter-empty-fieldresults sitevisits)
         sitevisits   (util/sort-maps-by sitevisits [:date :site])]
     (sitevisits->csv writer sitevisits param-config))))


;(defn get-sitevisit-summaries3 [db opts]
;  ;(debug "get-sitevisit-summaries opts: " opts)
;  (let [sitevisits (get-sitevisits3 db opts)
;        sitevisits (create-sitevisit-summaries3 sitevisits)
;        sitevisits (filter-empty-fieldresults sitevisits)
;        sitevisits (util/sort-maps-by sitevisits [:date :site])]
;    sitevisits))

