(ns riverdb.api.qc-report
  (:require
    [clojure.core.rrb-vector :as fv]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure-csv.core :as csv :refer [write-csv parse-csv]]
    [datomic.api :as d]
    [java-time :as jt]
    [riverdb.util :as util]
    [riverdb.state :refer [db cx]]
    [riverdb.db :as rdb :refer [rpull pull-entities]]
    [taoensso.timbre :as log :refer [debug info]]
    [thosmos.util :as tu]
    [clojure.pprint :refer [pprint]]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.rad.type-support.decimal :as math]
    [com.fulcrologic.rad.type-support.date-time :as datetime]
    [cljc.java-time.zone-id])
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
                                                      :where [?pj :projectslookup/Stations ?st]]
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


(def default-query
  [:db/id
   ;:org.riverdb/import-key
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
    [:db/id
     ;:org.riverdb/import-key
     {[:sample/SampleTypeCode :as :type]
      [[:sampletypelookup/SampleTypeCode :as :code]
       [:db/ident :as :ident]]}
     {[:sample/Constituent :as :constituent]
      [[:constituentlookup/ConstituentCode :as :code]
       {[:constituentlookup/AnalyteCode :as :analyte]
        [[:analytelookup/AnalyteName :as :name]
         [:analytelookup/AnalyteShort :as :short]]}
       {[:constituentlookup/MatrixCode :as :matrix]
        [[:matrixlookup/MatrixName :as :name]
         [:matrixlookup/MatrixShort :as :short]]}
       {[:constituentlookup/UnitCode :as :unit]
        [[:unitlookup/Unit :as :name]]}]}
     {[:sample/DeviceID :as :device]
      [[:samplingdevice/CommonID :as :id]]}
     {[:sample/Parameter :as :param]
      [[:parameter/Replicates :as :reps]
       [:parameter/NameShort :as :name]
       [:parameter/High :as :high]
       [:parameter/Low :as :low]
       [:parameter/PrecisionCode :as :prec]]}
     {[:sample/DeviceType :as :deviceType]
      [[:samplingdevicelookup/SampleDevice :as :name]
       [:samplingdevicelookup/Scale :as :scale]]}
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
     #_{[:sample/FieldObsResults :as :results]
        [[:fieldobsresult/IntResult :as :iresult]
         [:fieldobsresult/TextResult :as :tresult]]}]}])

(defn remap-query
  [{args :args :as m}]
  {:query (dissoc m :args)
   :args  args})

;; one graph query to pull all the data we need at once!
(defn get-sitevisits [db {:keys [agency fromDate toDate year sampleType
                                 project station stationCode query qaCheck] :as opts}]

  (let [fromDate (if (some? fromDate)
                   fromDate
                   (when year
                     (jt/zoned-date-time year)))
        toDate   (if (some? toDate)
                   toDate
                   (when year
                     (jt/plus fromDate (jt/years 1))))

        _        (log/debug "GET SITEVISITS" agency project station year fromDate toDate)

        query    (or query default-query)

        q        {:find  ['[(pull ?sv qu) ...]]
                  :in    '[$ qu]
                  :where '[]
                  :args  [db query]}

        q        (cond-> q

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

                   sampleType
                   (->
                     (update :where #(-> %
                                       (conj '[?sv :sitevisit/Samples ?sas])
                                       (conj '[?sas :sample/SampleTypeCode ?stype])
                                       (conj '[?stype :sampletypelookup/SampleTypeCode ?sampleType])))
                     (update :in conj '?sampleType)
                     (update :args conj sampleType))

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
                   ;; 3. add a condition against `?date` variable
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
        q        (remap-query q)
        ;_ (log/debug "SITEVISITS QUERY" q)
        result   (d/qseq q)
        _        (log/debug "SITEVISIT RESULTS" (count result))]

    ;; this query only returns field results due to missing :db/id on sample
    ;; we can modiy this to handle labresults too, or do it somewhere else

    result))



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
  (let [cnt (count a)
        mn  (mean a)]
    (when (> cnt 1)
      (Math/sqrt
        (/ (reduce #(+ %1 (square (- %2 mn))) 0 a)
          (dec (count a)))))))

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


;; FIXME per group

(def qapp-requirements
  {:include-elided? false
   :min-samples     3
   :elide-extra?    true
   :params          {:H2O_Temp {:precision  {:unit 0.5}
                                :exceedance {:high 20.0}}
                     :H2O_Cond {:precision {:range 10.0}}
                     :H2O_TDS  {:precision {:range 10.0}}
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

(def param->const
  {"SSI"   {:H2O_pH        [:constituentlookup/ConstituentCode "5-42-78-0-0"]
            :H2O_Temp      [:constituentlookup/ConstituentCode "5-42-100-0-31"]
            :H2O_Turb      [:constituentlookup/ConstituentCode "5-42-108-0-9"]
            :H2O_Cond      [:constituentlookup/ConstituentCode "5-42-24-0-25"]
            :H2O_DO        [:constituentlookup/ConstituentCode "5-42-38-0-6"]
            :H2O_PO4       [:constituentlookup/ConstituentCode "5-22-399-2-6"]
            :H2O_NO3       [:constituentlookup/ConstituentCode "5-20-69-0-6"]
            :Air_Temp      [:constituentlookup/ConstituentCode "10-42-100-0-31"]
            :TotalColiform [:constituentlookup/ConstituentCode "5-57-23-2-7"] ;; "5-56-23-20-7" "5-57-23-2-7"
            :EColi         [:constituentlookup/ConstituentCode "5-57-464-0-7"]}
   "WCCA"  {:Air_Temp   [:constituentlookup/ConstituentCode "10-42-100-0-31"]
            :Cond       [:constituentlookup/ConstituentCode "5-42-107-0-100"]
            :DO_mgL     [:constituentlookup/ConstituentCode "5-42-38-0-6"]
            :DO_Percent [:constituentlookup/ConstituentCode "5-42-38-0-13"]
            :H2O_Temp   [:constituentlookup/ConstituentCode "5-42-100-0-31"]
            :H2O_TempDO [:constituentlookup/ConstituentCode "5-42-100-0-31"]
            :pH         [:constituentlookup/ConstituentCode "5-42-78-0-0"]
            :Turb       [:constituentlookup/ConstituentCode "5-42-108-0-9"]}
   "SYRCL" {:TotalColiform [:constituentlookup/ConstituentCode "5-57-23-2-7"]
            :EColi         [:constituentlookup/ConstituentCode "5-57-464-0-7"]
            :Enterococcus  [:constituentlookup/ConstituentCode "5-9000-9002-0-7"]}})


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
  (let [bigvals  (try
                   (vec (map #(.setScale (bigdec %) scale RoundingMode/HALF_EVEN) vals))
                   (catch Exception ex (log/error ex "setScale failed" scale vals)))
        prec     (max-precision bigvals)
        ;mn       (.setScale (bigmean prec bigvals) scale RoundingMode/HALF_EVEN)
        mn       (.setScale
                   (with-precision prec :rounding RoundingMode/HALF_EVEN (* (bigdec (mean vals)) 1))
                   scale RoundingMode/HALF_EVEN)
        _min     (apply min bigvals)
        _max     (apply max bigvals)
        _        (when (and (> (count vals) 2) (> mn _max))
                   (debug "WEIRD MEAN" mn bigvals))
        plus     (- _max mn)
        minus    (- mn _min)
        pm       (max plus minus)
        plus%    (if (and (> plus 0) (> mn 0))
                   (with-precision prec :rounding RoundingMode/HALF_EVEN (* 100 (/ plus mn)))
                   0)
        minus%   (if (and (> minus 0) (> mn 0))
                   (with-precision prec :rounding RoundingMode/HALF_EVEN (* 100 (/ minus mn)))
                   0)
        pm%      (max plus% minus%)
        stddev1  (std-dev vals)
        stddev   (when stddev1
                   (with-precision prec (* (bigdec stddev1) 1)))
        rng      (- _max _min)
        ;rsd    (percent-rds mn stddev)
        frmt-str (str "Values: %s, %s, %s \nMean: %s \nMin: %s \nMax: %s \nRange: %s \nStdDev: %s \n±: %s \n±%% %s \nResult: %4$s ± %9$s ± %10$s %%")]
    #_(println (format frmt-str
                 (get bigvals 0) (get bigvals 1) (get bigvals 2) mn _min _max rng stddev pm pm%))
    {:vals   bigvals
     :mean   mn
     :stddev stddev
     :range  rng
     :max    _max
     :min    _min
     :±      pm
     :±%     pm%}))




(defn summarize-samples
  "summarize a vector of samples"
  [samples]
  (reduce (fn [init {:keys [constituent results device deviceType type param] :as sample}]
            (let [c       constituent
                  analyte (or (get-in c [:analyte :short]) (get-in c [:analyte :name]))
                  matrix  (or (get-in c [:matrix :short]) (get-in c [:matrix :name]))
                  unit    (get-in c [:unit :name])
                  device  (str (:name deviceType) " " (:id device))
                  type    (:code type)
                  matrix  (if (= type "FieldObs")
                            "FieldObs"
                            matrix)
                  param-k (keyword (str matrix "_" analyte))

                  ;param-name (or
                  ;             (:name param)
                  ;             (let [nm (str matrix " " analyte " " unit)]
                  ;               (if deviceType
                  ;                 (str nm " " (:name deviceType))
                  ;                 nm)))
                  vals    (when (seq results)
                            (->> results
                              (sort-by :replicate)
                              (mapv :result)
                              (remove nil?)
                              vec))
                  qual    (when (seq results)
                            (get-in (first results) [:qual :code]))
                  summary (cond-> {:param-key param-k
                                   :param-nm  (:name param)
                                   :type      type
                                   :unit      unit
                                   :matrix    matrix
                                   :analyte   analyte
                                   :id        (:db/id sample)}
                            qual
                            (assoc :qual qual)
                            param
                            (assoc :param param)
                            vals
                            (merge
                              {:count (count vals)
                               :vals  vals})
                            device
                            (assoc :device device)
                            deviceType
                            (assoc :deviceType deviceType)
                            (= type "FieldObs")
                            (#(let [{:keys [iresult tresult]} (first results)]
                                (merge %
                                  {:intresult  iresult
                                   :textresult tresult}))))]

              (conj init summary)))


    [] samples))

(defn sample-calcs
  "calculate stuff for each sample summary"
  [sums]
  (vec
    (for [{:keys [type vals unit matrix analyte qual deviceType device param param-key] :as sum} sums]
      (do
        ;(debug "CALC " v)
        (try
          (let [
                ;; calculate sample quantity exceedance
                param-reps      (:reps param)
                high            (:high param)
                low             (:low param)

                qapp            qapp-requirements
                qapp?           (some? (get-in qapp [:params param-key]))
                qapp-samples    (or param-reps (and qapp? (get qapp :min-samples 0)))
                val-count       (count vals)

                ;; if too many, do we throw out the outliers?
                too-many?       (and qapp-samples (> val-count qapp-samples))
                incomplete?     (and qapp-samples (< val-count qapp-samples))
                ;; save the original set of values before removal
                elide?          (and too-many? (:elide-extra? qapp))
                include-elided? (:include-elided? qapp)
                orig-vals       vals
                vals            (if elide?
                                  (remove-outliers qapp-samples vals)
                                  vals)

                val-count       (count vals)
                vals?           (seq vals)]

            (cond

              (not vals?)
              (let [sum (merge sum {:incomplete? true
                                    :invalid?    true
                                    :count       0
                                    :vals        vals})]
                sum)

              :else
              (let [scale          (or (:scale deviceType) (get-scale vals))
                    _              (when (nil? scale)
                                     (debug "SCALE IS NIL" sum))

                    ;; calculate
                    bigcalcs       (calc3 scale vals)
                    {:keys [mean max min range stddev]} bigcalcs

                    ;_            (debug "param-sums" param-name bigcalcs)

                    ;range        (round2 4 (- mx mn))
                    ;bigrange     (- bigmax bigmin)
                    ;stddev       (if (> range 0.0) (std-dev vals) 0.0)
                    ;mean         (if (> val-count 1)
                    ;               (mean vals)
                    ;               (first vals))

                    prec-percent   (:±% bigcalcs)
                    prec-unit      (:± bigcalcs)

                    ;prec-percent (if (> range 0.0) (percent-rds mean stddev) 0.0)
                    ;prec-unit    range
                    ;prec-percent (if (> range 0.0)
                    ;               (max (percent-dev mx mean) (percent-dev mn mean))
                    ;               0.0)
                    ;
                    ;prec-unit    (if (> range 0.0)
                    ;               (max (- mean mn) (- mx mean))
                    ;               0.0)

                    ;; calculate precision thresholds
                    qapp-precision (get-in qapp [:params param-key :precision])
                    {req-percent :percent req-unit :unit req-range :range threshold :threshold} qapp-precision

                    ;threshold      (when (and req-percent req-unit)
                    ;                 (or
                    ;                   (get-in qapp [:params param-key :precision :threshold])
                    ;                   (* (/ 100.0 req-percent) req-unit)))

                    imprecision?   (when (> val-count 1)
                                     (if threshold
                                       (if (> mean threshold)
                                         (if
                                           (> prec-percent req-percent)
                                           (do
                                             ;(println "PRECISION % " param-name "thresh:" threshold "prec:" prec-percent "req:" req-percent)
                                             true)
                                           false)
                                         (if
                                           (> prec-unit req-unit)
                                           (do
                                             ;(println "PRECISION unit " param-name "thresh:" threshold "prec:" prec-unit "req: " req-unit)
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
                    high           (or high (get-in qapp [:params param-key :exceedance :high]))
                    low            (or low (get-in qapp [:params param-key :exceedance :low]))
                    too-high?      (and (some? high) (> mean high))
                    too-low?       (and (some? low) (< mean low))
                    exceedance?    (and (not incomplete?) (or too-low? too-high?))

                    sum            (merge sum {:incomplete?   incomplete?
                                               :is_incomplete incomplete?
                                               :is_valid      (not incomplete?)
                                               :exceedance?   exceedance?
                                               :is_exceedance exceedance?
                                               :imprecise?    imprecision?
                                               :is_imprecise  imprecision?
                                               :count         val-count
                                               :mean          (str mean)
                                               :vals          vals
                                               :max           (str max)
                                               :min           (str min)})

                    sum            (cond-> sum
                                     exceedance?
                                     (merge {:too-low?    too-low?
                                             :too-high?   too-high?
                                             :is_too_low  too-low?
                                             :is_too_high too-high?
                                             :high        (str high)
                                             :low         (str low)})
                                     incomplete?
                                     (merge {:incomplete?   incomplete?
                                             :is_incomplete incomplete?})
                                     imprecision?
                                     (merge {:prec-percent (str prec-percent)
                                             :prec-unit    (str prec-unit)
                                             :range        (str range)})
                                     (and elide? include-elided?)
                                     (merge {:orig-vals orig-vals
                                             :orig_vals orig-vals}))]

                sum)))

          (catch Exception ex (println "ERROR in calc-param-summaries" ex sums)))))))

(defn sv-sample-stats [sv]
  (let [samples (:samples sv)
        sv      (reduce
                  (fn [sv sample]
                    (cond-> sv
                      (:incomplete? sample)
                      (update :count-params-incomplete inc!)
                      (:exceedance? sample)
                      (update :count-params-exceedance inc!)))
                  sv samples)
        sv      (assoc sv :incomplete? (some? (:count-params-incomplete sv)))
        sv      (assoc sv :exceedance? (some? (:count-params-exceedance sv)))]
    sv))

(def qapp-param-ks
  #{:H2O_Temp :H2O_pH :H2O_DO :H2O_Cond :H2O_Turb :H2O_NO3 :H2O_PO4})

(defn sample-stats [samples]
  (reduce
    (fn [stats sample]
      (cond-> stats
        ;; only increment :incomplete when it's a QAPP param
        (and
          (:incomplete? sample)
          (contains? qapp-param-ks (:param-key sample)))
        (update :incomplete? inc!)
        (:exceedance? sample)
        (update :exceedance? inc!)))
    {} samples))


(defn sitevisit-summaries
  "create summary info per sitevisit"
  [{:keys [sitevisits sampleType] :as opts}]
  (debug "CREATE SV SUMMARIES" (keys opts))
  (vec
    (for [sv sitevisits]
      (try
        (let [;dt   (str (datetime/inst->local-date (:date sv)))
              ;_    (debug "processing SV " (get-in sv [:station :station_id]) dt)

              ;; remap nested values to root
              sv   (-> sv
                     (assoc :trib (get-in sv [:station :trib]))
                     (assoc :fork (get-in sv [:station :river_fork]))
                     (assoc :failcode (get-in sv [:failcode :reason]))
                     (assoc :site (get-in sv [:station :station_id])))

              ;; pull out samples list
              sas  (get-in sv [:samples])

              sas  (if sampleType
                     (filter #(= (get-in % [:type :code]) sampleType) sas)
                     sas)

              ;_    (debug "reduce Samples to param sums")
              sums (summarize-samples sas)
              sums (sample-calcs sums)
              ;; remove the list of samples and add the new param map
              sv   (-> sv
                     (dissoc :samples)
                     ;(assoc :fieldresults sums)
                     (assoc :samples sums))

              ;_   (debug "count valid params")
              ;; count valid params

              sv   (sv-sample-stats sv)]
          ;(debug "SV" sv)

          sv)
        (catch Exception ex (log/error ex))))))

(defn samples->map [p-list samples]
  (reduce
    (fn [out sa]
      (let [sa-p   (get-in sa [:param :name])
            match? (contains? (set p-list) sa-p)]
        (if match?
          (assoc out sa-p sa)
          out)))
    {} samples))

(defn sitevisit-summaries2
  "create summary info per sitevisit"
  [{:keys [sitevisits sampleType project params-list params-map] :as opts}]
  ;(debug "CREATE SV SUMMARIES" (keys opts))
  (vec
    (for [sv sitevisits]
      (try
        (let [;dt   (str (datetime/inst->local-date (:date sv)))
              ;_    (debug "processing SV " (get-in sv [:station :station_id]) dt)

              ;; remap nested values to root
              sv      (-> sv
                        (assoc :trib (get-in sv [:station :trib]))
                        (assoc :fork (get-in sv [:station :river_fork]))
                        (assoc :failcode (get-in sv [:failcode :reason]))
                        (assoc :site (get-in sv [:station :station_id])))

              ;; pull out samples list
              sas     (get-in sv [:samples])

              ;_       (debug "FIRST SAMPLE OF " (count sas) (first sas))

              sas     (if sampleType
                        (filter #(= (get-in % [:type :code]) sampleType) sas)
                        sas)

              ;_    (debug "reduce Samples to param sums")
              sums    (summarize-samples sas)
              sums    (sample-calcs sums)
              stats   (sample-stats sums)

              sas-map (samples->map params-list sums)

              sv      (-> sv
                        (merge stats)
                        (dissoc :samples)
                        (assoc :samples sas-map))]

          sv)
        (catch Exception ex (log/error ex))))))


;(defn read-csv [filename]
;  (with-open
;    [^java.io.Reader r (clojure.java.io/reader filename)]
;    (let [csv    (parse-csv r)
;          header (first csv)
;          hidx   (into {} (for [i (range (count header))]
;                            [(get header i) i]))
;          csv    (rest csv)]
;      (doall
;        (for [row csv]
;          (let [sv  {:site (get row (get hidx "Site"))
;                     :svid (get row (get hidx "SiteVisitID"))
;                     :date (get row (get hidx "Date"))
;                     :time (get row (get hidx "Time"))}
;                ;_ (println "DAtE" (type (get row (get hidx "Date"))))
;                frs (into {}
;                      (for [[k v] (:params qapp-requirements)]
;                        (let [sample-count (get-in param-config [k :count])]
;                          [k {:vals (vec (remove nil?
;                                           (for [i (range 1 (+ sample-count 1))]
;                                             (let [csv_field (str (name k) "_" i)
;                                                   csv_idx   (get hidx csv_field)]
;                                               (when csv_idx
;                                                 (let [field_val (get row csv_idx)]
;                                                   (edn/read-string field_val)))))))}])))
;                ;frs (calc-param-summaries frs)
;                sv  (assoc sv :results frs)]
;            ;sv (calc-sitevisit-stats sv)
;
;
;            sv))))))

;
;(defn csv-sitevisit-summaries
;  "import csv dat into the same format as the reduce-fieldresults-to-map function"
;  [filename]
;  (with-open
;    [^java.io.Reader r (clojure.java.io/reader filename)]
;    (let [csv    (parse-csv r)
;          header (first csv)
;          hidx   (into {} (for [i (range (count header))]
;                            [(get header i) i]))
;          csv    (rest csv)]
;      (doall
;        (for [row csv]
;          (let [sv  {:site (get row (get hidx "Site"))
;                     :svid (get row (get hidx "SiteVisitID"))
;                     :date (get row (get hidx "Date"))
;                     :time (get row (get hidx "Time"))}
;                ;_ (println "DAtE" (type (get row (get hidx "Date"))))
;                frs (into {}
;                      (for [[k v] (:params qapp-requirements)]
;                        (let [sample-count (get-in param-config [k :count])]
;                          [k {:vals (vec (remove nil?
;                                           (for [i (range 1 (+ sample-count 1))]
;                                             (let [csv_field (str (name k) "_" i)
;                                                   csv_idx   (get hidx csv_field)]
;                                               (when csv_idx
;                                                 (let [field_val (get row csv_idx)]
;                                                   (edn/read-string field_val)))))))}])))
;                frs (calc-param-summaries frs)
;                sv  (assoc sv :fieldresults frs)
;                sv  (calc-sitevisit-stats sv)]
;            sv))))))


(defn simplify-sv
  ([sv]
   (dissoc sv :samples))
  ([sv sample]
   (-> sv
     (dissoc :samples)
     (assoc :sample sample))))

(defn report-reducer
  "generate a map with summary info over whole time period, including details for each parameter in :z-params"
  [db sitevisits]
  (reduce
    (fn [r {:keys [samples] :as sv}]
      (try (let [param-keys (keys (:params qapp-requirements))

                 ;_        (println "SV " (get-in sv [:sitevisit/StationID
                 ;                                    :stationlookup/StationID]))

                 ;; count all sitevisits
                 r          (update r :count-sitevisits inc!)

                 r          (cond-> r
                              ;; if the sitevisit has no samples, add it to a :no-results list
                              (empty? samples)
                              (->
                                ;(update :no-results-rs conjv sv-ident)
                                ;#(do
                                ;   (println "EMPTY" sv)
                                ;   %)
                                (update :count-no-results inc!)
                                (update :no-results-rs conjv (when db (pull-no-result db (:db/id sv)))))
                              ;; otherwise add it to :results list
                              (seq samples)
                              (->
                                (update :count-results inc!)))



                 ;;_ (println "reduce FRs")
                 ;; reduce the fieldresults to summarize

                 r          (reduce
                              (fn [r sample]
                                (let [param-k  (:param-key sample)
                                      param-nm (or (get-in sample [:param :name]) (name param-k))]
                                  (if (some #(= param-k %) param-keys)
                                    (-> r
                                      ;; total count of results
                                      (update :count-params inc!)
                                      ;; set param-k
                                      (assoc-in [:z-params param-nm :type] param-k)
                                      (assoc-in [:z-params param-nm :name] param-nm)
                                      (assoc-in [:z-params param-nm :display-order]
                                        (get-in param-config [param-k :order] 99))

                                      (assoc-in [:z-params param-nm :qapp-precision]
                                        (get-in qapp-requirements [:params param-k :precision]))

                                      (assoc-in [:z-params param-nm :qapp-exceedance]
                                        (get-in qapp-requirements [:params param-k :exceedance]))

                                      ;; count each param
                                      (update-in [:z-params param-nm :count] inc!)

                                      ;; count all exceedances
                                      (cond->

                                        ;; too few
                                        (:incomplete? sample)
                                        (->
                                          (update :count-params-incomplete inc!)
                                          (update-in [:z-params param-nm :incomplete] inc!)
                                          (update-in [:z-params param-nm :incomplete-rs] conjv (simplify-sv sv sample)))

                                        ;; complete
                                        (not (:incomplete? sample))
                                        (->
                                          (update :count-params-complete inc!)
                                          (update-in [:z-params param-nm :complete] inc!))

                                        ;; precision exceedance
                                        (and (not (:incomplete? sample)) (:imprecise? sample))
                                        (->
                                          (update :count-params-imprecise inc!)
                                          (update-in [:z-params param-nm :imprecise] inc!)
                                          (update-in [:z-params param-nm :imprecise-rs] conjv (simplify-sv sv sample)))

                                        ;; water quality
                                        (:too-high? sample)
                                        (->
                                          (update :exceed-high inc!)
                                          (update-in [:z-params param-nm :exceed-high] inc!))

                                        ;; water quality
                                        (:too-low? sample)
                                        (->
                                          (update :exceed-low inc!)
                                          (update-in [:z-params param-nm :exceed-low] inc!))

                                        ;; exceedance
                                        (and (not (:incomplete? sample)) (:exceedance? sample))
                                        (->
                                          (update :count-params-exceedance inc!)
                                          (update-in [:z-params param-nm :exceedance] inc!)
                                          (update-in [:z-params param-nm :exceedance-rs] conjv (simplify-sv sv sample)))))
                                    r)))
                              r samples)

                 r          (reduce-kv
                              (fn [r k m]
                                (let [total             (:count m)
                                      complete          (get m :complete 0)
                                      valid-percent     (when (> complete 0)
                                                          (round2 1 (* 100 (/ complete total))))
                                      exceedance        (get m :exceedance 0)
                                      ;non-exceed     (- complete exceedance)
                                      exceed-percent    (when (> complete 0)
                                                          (round2 1 (* 100 (/ exceedance complete))))
                                      imprecise         (get m :imprecise 0)
                                      imprecise-percent (when (> complete 0)
                                                          (round2 1 (* 100 (/ imprecise complete))))
                                      param-k           (:param-key m)]
                                  (-> r
                                    (assoc-in [:z-params k :percent-complete] valid-percent)
                                    (assoc-in [:z-params k :percent-exceedance] exceed-percent)
                                    (assoc-in [:z-params k :percent-imprecise] imprecise-percent))))

                              r (:z-params r))

                 r          (assoc r :x-params (vec (sort-by :display-order (vals (:z-params r)))))]
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
        count-params-complete     (get report :count-params-complete 0)
        count-params-incomplete   (get report :count-params-incomplete 0)
        count-params-imprecise    (get report :count-params-imprecise 0)
        percent-params-complete   (if (> count-params 0)
                                    (round2 1 (* (/ count-params-complete count-params) 100))
                                    0)
        count-params-exceedance   (get report :count-params-exceedance 0)
        percent-params-exceedance (if (> count-params-complete 0)
                                    (round2 1 (* (/ count-params-exceedance count-params-complete) 100))
                                    0)
        percent-params-imprecise  (if (> count-params-complete 0)
                                    (round2 1 (* (/ count-params-imprecise count-params-complete) 100))
                                    0)
        percent                   (if (> count-svs 0)
                                    (round2 1 (* (/ results count-svs) 100))
                                    0)]
    (-> report
      (assoc :percent-complete percent)
      (assoc :count-params-planned count-params-planned)
      (assoc :count-params-possible count-params-possible)
      (assoc :count-params-complete count-params-complete)
      (assoc :count-params-incomplete count-params-incomplete)
      (assoc :percent-params-complete percent-params-complete)
      (assoc :count-params-imprecise count-params-imprecise)
      (assoc :percent-params-imprecise percent-params-imprecise)
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

(defn get-qc-report [db agency-code project-id year]
  ;{:pre [true (check ::report-year year)]}
  (let [year       (if (= year "") nil (Long/parseLong year))
        _          (check ::report-year year)
        done?      false
        _          (log/info "BEGINNING QC REPORT" agency-code project-id year)
        ;start-time (jt/zoned-date-time year)
        ;end-time   (jt/plus start-time (jt/years 1))
        agency     (rpull [:agencylookup/AgencyCode agency-code])
        project    (rpull [:projectslookup/ProjectID project-id])
        sitevisits (if year
                     (get-sitevisits db {:agency agency-code :project project-id :year year :sampleType "FieldMeasure"})
                     (get-sitevisits db {:agency agency-code :sampleType "FieldMeasure"}))
        ;_          (debug "first SV" (first sitevisits))
        ;sitevisits (filter-sitevisits sitevisits)
        sitevisits (sitevisit-summaries {:sitevisits sitevisits :sampleType "FieldMeasure"})
        ;_          (println "report reducer" (first sitevisits))
        ;param-keys (reduce #(apply conj %1 (map first (get-in %2 [:fieldresults]))) #{} sitevisits)
        report     (report-reducer db sitevisits)
        report     (-> report
                     (assoc :agency agency-code)
                     (assoc :report-year year)
                     (assoc :qapp-requirements qapp-requirements)
                     (assoc :param-config (if (= agency-code "SSI")
                                            param-config-ssi
                                            param-config))
                     (assoc :project (get-project-name db project-id)))
        ;_          (println "REPORT: "
        ;             (select-keys report
        ;               [:count-sitevisits :count-params-valid :count-params-exceedance]))
        report     (report-stats report)
        _          (log/info "QC REPORT COMPLETE")]
    report))

(defn filter-empty-samples [svs]
  (remove #(and
             (empty? (:samples %))
             (= (:failcode %) "None")) svs))

(def type-order
  {:sampletypelookup.SampleTypeCode/FieldMeasure 1
   :sampletypelookup.SampleTypeCode/Grab         2
   :sampletypelookup.SampleTypeCode/FieldObs     3})

(defn compare-param [x y]
  (compare
    [(get type-order (get-in x [:parameter/SampleType :db/ident])) (:parameter/Order x)]
    [(get type-order (get-in y [:parameter/SampleType :db/ident])) (:parameter/Order y)]))

(defn get-datatable-report
  [db {project-code :project :keys [agency year]}]
  (try
    (let [_           (log/debug "GET DATATABLE" year project-code)
          year        (if (= year "") nil (Long/parseLong year))

          sitevisits  (get-sitevisits db {:project project-code :year year})
          ;_            (debug "sitevisits" (count svs))
          ;svs          (create-sitevisit-summaries3 svs)
          project     (rpull
                        [:projectslookup/ProjectID project-code]
                        '[* {:projectslookup/Parameters
                             [* {:parameter/SampleType [:db/ident :sampletypelookup/SampleTypeCode]}]}])
          {:projectslookup/keys [Name Parameters]} project
          params      (remove
                        #(or
                           (not= true (:parameter/Active %))
                           (= (get-in % [:parameter/SampleType :db/ident])
                             :sampletypelookup.SampleTypeCode/FieldObs))
                        Parameters)
          params      (sort compare-param params)
          params-list (mapv :parameter/NameShort params)
          params-map  (riverdb.util/nest-by [:parameter/NameShort] params)
          ;_           (debug "PARAMS" params-list)
          sitevisits  (sitevisit-summaries2 {:sitevisits sitevisits :params-list params-list :params-map params-map})
          ;_            (debug "sitevisits filtered" (count svs))
          ;svs          (filter-empty-fieldresults svs)
          ;_            (debug "sitevisits summaries" (count svs))
          sitevisits  (sort-by :date sitevisits)
          ;_           (debug "sitevisits first" (first sitevisits))
          ;_            (debug "sitevisits last" (last svs))
          result      {:results     sitevisits
                       ;:params            params
                       :params-list params-list
                       :params-map  params-map
                       ;:precReqs qapp-requirements
                       :reportYear  year
                       :agency      agency
                       :projectName Name}]
      result)
    (catch Exception ex (log/error "DATATABLE ERROR" (.getMessage ex)))))

