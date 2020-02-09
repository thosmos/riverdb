(ns riverdb.api.riverdb
  (:require [clojure.tools.logging :as log :refer [debug info warn error]]
            [datomic.api :as d]
            [java-time :as jt]
            [clojure.core.rrb-vector :as fv]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [thosmos.util :as util])
  (:import (java.io Writer)
           (java.time ZonedDateTime)))

;; FIXME pull this from logged in user
;(def report-config
;  {:agency-code "SYRCL"})
;
;(defn time-from-instant [^java.util.Date ins]
;  (-> ins
;    (.getTime)
;    (/ 1000)
;    java.time.Instant/ofEpochSecond
;    (java.time.ZonedDateTime/ofInstant
;      (java.time.ZoneId/of "America/Los_Angeles"))))
;
;(defn year-from-instant [^java.util.Date ins]
;  (.getYear ^ZonedDateTime (time-from-instant ins)))

;;; FIXME use sitevisit's timezone
;(defn get-sitevisit-years
;  ([db agency]
;   (d/q '[:find [?year ...]
;          :in $ ?agency
;          :where
;          [?e :sitevisit/SiteVisitDate ?date]
;          [?pj :projectslookup/AgencyCode ?agency]
;          [?e :sitevisit/ProjectID ?pj]
;          [(rimdb-ui.api.tac-report/year-from-instant ?date) ?year]]
;     db agency)))

;(defn remap-query
;  [{args :args :as m}]
;  {:query (dissoc m :args)
;   :args args})

;; one graph query to pull all the data we need at once!
;(defn get-sitevisits
;  ;([db]
;  ; (get-sitevisits db))
;  ;([db agency]
;  ; (get-sitevisits db :agency agency))
;  ;([db agency year]
;  ; (let [start-time (jt/zoned-date-time year)
;  ;       end-time   (jt/plus start-time (jt/years 1))]
;  ;   (get-sitevisits db agency start-time end-time nil)))
;  ([db {:keys [year agency fromDate toDate station stationCode projectID] :as opts}]
;    ;agency fromDate toDate station
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
;         q        {:find  ['[(pull ?sv [:db/id
;                                        [:sitevisit/SiteVisitID :as :svid]
;                                        [:sitevisit/SiteVisitDate :as :date]
;                                        [:sitevisit/Time :as :time]
;                                        [:sitevisit/Notes :as :notes]
;                                        {[:sitevisit/StationID :as :station] [:db/id
;                                                                              [:stationlookup/StationID :as :station_id]
;                                                                              [:stationlookup/StationCode :as :station_code]
;                                                                              [:stationlookup/RiverFork :as :river_fork]
;                                                                              [:stationlookup/ForkTribGroup :as :trib_group]]}
;                                        {[:sitevisit/StationFailCode :as :failcode] [[:stationfaillookup/FailureReason :as :reason]]}
;                                        {:sample/_SiteVisitID
;                                         [{:fieldresult/_SampleRowID
;                                           [:db/id :fieldresult/Result :fieldresult/FieldReplicate
;                                            {:fieldresult/ConstituentRowID
;                                             [:db/id ;:constituentlookup/HighValue :constituentlookup/LowValue
;                                              {:constituentlookup/AnalyteCode [:analytelookup/AnalyteShort]}
;                                              {:constituentlookup/MatrixCode [:matrixlookup/MatrixShort]}
;                                              {:constituentlookup/UnitCode [:unitlookup/Unit]}]}
;                                            {[:fieldresult/SamplingDeviceID :as :device]
;                                             [[:samplingdevice/CommonID :as :id]
;                                              {[:samplingdevice/DeviceType :as :type] [[:samplingdevicelookup/SampleDevice :as :name]]}]}]}]}]) ...]]
;                   :in    '[$]
;                   :where '[]
;                   :args  [db]}
;
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
;                    ;; last one, in case there are no conditions, get all sitevisits
;                    (empty? (:where q))
;                    (->
;                      (update :where conj '[?sv :sitevisit/StationID])))
;         q        (remap-query q)]
;
;
;     ;; this query only returns field results due to missing :db/id on sample
;     ;; we can modiy this to handle labresults too, or do it somewhere else
;
;     (d/query q))))


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

;(defn pull-no-result [db eid]
;  (let [x (d/pull db
;            [:db/id [:sitevisit/SiteVisitDate :as :date] [:sitevisit/Notes :as :notes]
;             {[:sitevisit/StationID :as :station] [[:db/id :stationlookup/StationName :as :name]]}
;             {[:sitevisit/StationFailCode :as :reason] [[:stationfaillookup/FailureReason :as :reason]]}
;             {[:samplingcrew/_SiteVisitID :as :crew] [{[:samplingcrew/PersonID :as :person] [[:person/FName :as :fname] [:person/LName :as :lname]]}]}]
;            eid)]
;    (-> x
;      (assoc :site (get-in x [:station :name]))
;      (assoc :reason (get-in x [:reason :reason]))
;      (assoc :crew (clojure.string/join ", "
;                     (map #(let [peep (:person %)]
;                             (str (:fname peep) " " (:lname peep)))
;                       (:crew x)))))))


;;;;;; utilities

;(defn vec-remove [pos coll]
;  (fv/catvec (fv/subvec coll 0 pos) (fv/subvec coll (inc pos) (count coll))))
;
;(defn round2
;  "Round a double to the given precision (number of significant digits)"
;  [precision d]
;  (let [factor (Math/pow 10 precision)]
;    (/ (Math/round (* d factor)) factor)))

;(defn square [n] (* n n))
;
;(defn mean [a] (/ (reduce + a) (count a)))
;
;(defn std-dev
;  [a]
;  (let [mn (mean a)]
;    (Math/sqrt
;      (/ (reduce #(+ %1 (square (- %2 mn))) 0 a)
;        (dec (count a))))))

;(defn percent-prec [avg stddev]
;  (* (/ stddev avg) 100))

;; if it's nil, make it 0
;(defn inc! [val]
;  (inc (or val 0)))

;; if it's nil, make it a []
;(defn conjv [coll val]
;  (conj (or coll []) val))

;;;;; data manip

;; run through all values and calc max relative diffs from the others
;(defn calc-diffs [vals]
;  (into []
;    (for [v vals]
;      (apply + (for [w vals]
;                 (round2 10 (Math/abs (- v w))))))))

;(defn reduce-outlier-idx [diffs]
;  (:outlier-idx
;    (reduce (fn [{:keys [i outlier outlier-idx] :as init} diff]
;              (let [max         (- diff outlier)
;                    outlier?    (pos? max)
;                    outlier     (if outlier? diff outlier)
;                    outlier-idx (if outlier? i outlier-idx)]
;                {:outlier outlier :outlier-idx outlier-idx :i (inc i)}))
;      {:outlier 0.0 :outlier-idx 0 :i 0} diffs)))

;(defn find-outlier-idx [vals]
;  (let [diffs   (calc-diffs vals)
;        ;_       (debug "diffs" diffs)
;        outlier (reduce-outlier-idx diffs)]
;    outlier))

;(defn remove-outliers
;  "remove values that are the biggest outliers, one at a time"
;  [target vals]
;  (loop [vals vals]
;    (if (> (count vals) target)
;      (recur (vec-remove (find-outlier-idx vals) vals))
;      (vec vals))))



;(defn reduce-fieldresults-to-map
;  "reduce a set of fieldresults into a map of parameter maps indexed by parameter name"
;  [fieldresults]
;  (reduce (fn [init {:keys [fieldresult/Result fieldresult/ConstituentRowID device] :as fieldresult}]
;            (let [c         ConstituentRowID
;                  cid       (get c :db/id)
;                  analyte   (get-in c [:constituentlookup/AnalyteCode :analytelookup/AnalyteShort])
;                  matrix    (get-in c [:constituentlookup/MatrixCode :matrixlookup/MatrixShort])
;                  unit      (get-in c [:constituentlookup/UnitCode :unitlookup/Unit])
;                  device    (str (get-in device [:type :name]) " - " (:id device))
;                  ;device (get-in device [:type :name])
;                  analyte-k (keyword (str matrix "_" analyte))
;                  ;analyte-k (keyword analyte)
;                  init      (if (= nil (get init analyte-k))
;                              (assoc init analyte-k {})
;                              init)
;                  init      (update init analyte-k
;                              (fn [k] (-> k
;                                        (update :vals conjv Result)
;                                        (assoc :device device)
;                                        (assoc :unit unit)
;                                        (assoc :matrix matrix)
;                                        (assoc :analyte analyte))))]
;
;
;              init))
;    {} fieldresults))


;; FIXME per group

;(def qapp-requirements
;  {:include-elided? false
;   :min-samples 3
;   :elide-extra? false
;   :params {:H2O_Temp {:precision {:unit 1.0}
;                       :exceedance {:high 20.0}}
;            :H2O_Cond {:precision {:unit 10.0}}
;            :H2O_DO {:precision {:percent 10.0}
;                     :exceedance {:low 7.0}}
;            :H2O_pH {:precision {:unit 0.4}
;                     :exceedance {:low 6.5
;                                  :high 8.5}}
;            :H2O_Turb {:precision {:percent 10.0
;                                        :unit 0.6
;                                        :threshold 10.0}}
;            :H2O_PO4 {:precision {:percent 20.0}}
;            :H2O_NO3 {:precision {:percent 20.0}}}})


;(def params
;  [:Air_Temp :H2O_Temp :H2O_Cond :H2O_DO :H2O_pH :H2O_Turbidity])

;; FIXME per group

;(def param-config
;  {:Air_Temp {:order 0 :count 1 :name "Air_Temp"}
;   :H2O_Temp {:order 1 :count 6 :name "H2O_Temp"}
;   :H2O_Cond {:order 2 :count 3 :name "Cond"}
;   :H2O_DO {:order 3 :count 3 :name "DO"}
;   :H2O_pH {:order 4 :count 3 :name "pH"}
;   :H2O_Turb {:order 5 :count 3 :name "Turb"}
;   :H2O_NO3 {:order 6 :count 3 :name "NO3" :optional true}
;   :H2O_PO4 {:order 7 :count 3 :name "PO4" :optional true}
;   :H2O_Velocity {:elide? true}})




;(defn calc-param-summaries
;  "calculate summaries for each parameter map entry"
;  [fieldresult]
;  (into {}
;    (for [[k {:keys [vals] :as v}] fieldresult]
;      (try
;        (let [;_ (debug "CALC " k)
;              ;; calculate sample quantity exceedance
;              qapp            qapp-requirements
;              qapp?           (some? (get-in qapp [:params k]))
;              qapp-samples    (get qapp :min-samples 0)
;              val-count       (count vals)
;              too-many?       (and qapp? (> val-count qapp-samples))
;              incomplete?     (and qapp? (< val-count qapp-samples))
;
;              ;; if too many, do we throw out the outliers?
;              elide?          (and too-many? (:elide-extra? qapp))
;              include-elided? (:include-elided? qapp)
;              ;; save the original set of values before removal
;              orig-vals       vals
;              vals            (if elide?
;                                (remove-outliers qapp-samples vals)
;                                vals)
;              val-count       (count vals)
;              vals?           (seq vals)]
;
;          (if-not vals?
;            (let [v (merge v {:incomplete? true
;                              :invalid? true
;                              :count 0
;                              :vals vals})]
;              [k v])
;
;            (let [
;                  ;; calculate
;                  mx           (apply max vals)
;                  mn           (apply min vals)
;                  ;; FIXME clamp range to 4 decimals to eliminate float stragglers - use sig figs?
;                  range        (round2 4 (- mx mn))
;                  stddev       (if (> range 0.0) (std-dev vals) 0.0)
;                  mean         (mean vals)
;                  prec-percent (if (> range 0.0) (percent-prec mean stddev) 0.0)
;                  prec-unit    range
;
;                  ;; calculate precision thresholds
;                  req-percent  (get-in qapp [:params k :precision :percent])
;                  req-unit     (get-in qapp [:params k :precision :unit])
;
;                  threshold    (if (and req-percent req-unit)
;                                 (or
;                                   (get-in qapp [:params k :precision :threshold])
;                                   (* (/ 100.0 req-percent) req-unit)))
;                  imprecision? (when (and qapp? (> val-count 1))
;                                 (if threshold
;                                   (if (> mean threshold)
;                                     (if
;                                       (> prec-percent req-percent)
;                                       (do
;                                         ;(debug "PRECISION % " k threshold prec-percent req-percent)
;                                         true)
;                                       false)
;                                     (if
;                                       (> prec-unit req-unit)
;                                       (do
;                                         ;(debug "PRECISION unit " k threshold prec-unit req-unit)
;                                         true)
;                                       false))
;                                   (if req-percent
;                                     (> prec-percent req-percent)
;                                     (> prec-unit req-unit))))
;
;                  ;;; calculate quality exceedance
;                  high         (get-in qapp [:params k :exceedance :high])
;                  low          (get-in qapp [:params k :exceedance :low])
;                  too-high?    (and qapp? (some? high) (> mean high))
;                  too-low?     (and qapp? (some? low) (< mean low))
;                  invalid?     (and qapp? (or incomplete? imprecision?))
;                  exceedance?  (and qapp? (not invalid?) (or too-low? too-high?))
;
;                  v            (merge v {:is_valid (not invalid?)
;                                         :is_exceedance exceedance?
;                                         :count val-count
;                                         :max mx
;                                         :min mn
;                                         :range (round2 2 range)
;                                         :stddev (round2 2 stddev)
;                                         :mean (round2 2 mean)
;                                         :prec (if (some? prec-percent) (round2 2 prec-percent) nil)
;                                         :vals vals})
;
;                  v            (cond-> v
;                                 exceedance?
;                                 (merge {:is_too_low too-low?
;                                         :is_too_high too-high?})
;                                 invalid?
;                                 (merge {:is_incomplete incomplete?
;                                         :is_imprecise imprecision?})
;                                 (and elide? include-elided?)
;                                 (assoc :orig_vals orig-vals))]
;
;
;              [k v])))
;
;        (catch Exception ex (debug ex fieldresult))))))

;(defn calc-sitevisit-stats [sv]
;  (let [frs (:fieldresults sv)
;        sv  (reduce-kv
;              (fn [sv fr-k fr-m]
;                (cond-> sv
;                  (:invalid? fr-m)
;                  (update :count-params-invalid inc!)
;                  (:exceedance? fr-m)
;                  (update :count-params-exceedance inc!)))
;              sv frs)
;        sv  (assoc sv :invalid? (some? (:count-params-invalid sv)))
;        sv  (assoc sv :exceedance? (some? (:count-params-exceedance sv)))]
;    sv))

;(defn create-sitevisit-summaries
;  "create summary info per sitevisit"
;  [sitevisits]
;  (for [sv sitevisits]
;    (try
;      (let [
;            ;_        (debug "processing SV " (get-in sv [:sitevisit/StationID
;            ;                                    :stationlookup/StationID]))
;
;            ;; remap nested values to root
;            sv  (-> sv
;                  (assoc :trib (get-in sv [:station :trib]))
;                  (assoc :fork (get-in sv [:station :river_fork]))
;                  ;(assoc :station (get-in sv [:station :db/id]))
;                  (assoc :failcode (get-in sv [:failcode :reason])))
;
;            ;; pull out fieldresult list
;            frs (get-in sv [:sample/_SiteVisitID 0 :fieldresult/_SampleRowID])
;            ;; restrict to water samples (no air)
;            ;frs (filter #(= "H2O" (get-in % [:fieldresult/ConstituentRowID
;            ;                                 :constituentlookup/MatrixCode
;            ;                                 :matrixlookup/MatrixShort])) frs)
;
;            ;; toss velocity
;            ;frs (remove #(= "Velocity" (get-in % [:fieldresult/ConstituentRowID
;            ;                                      :constituentlookup/AnalyteCode
;            ;                                      :analytelookup/AnalyteShort])) frs)
;
;            ;_   (debug "reduce FRs to param sums")
;            frs (reduce-fieldresults-to-map frs)
;            ;_   (debug "calc param sums")
;            frs (calc-param-summaries frs)
;            ;; remove the list of samples and add the new param map
;            ;_   (debug "move FRS")
;            sv  (-> sv
;                  (dissoc :sample/_SiteVisitID)
;                  (assoc :fieldresults frs))
;            ;_   (debug "count valid params")
;            ;; count valid params
;
;            sv  (calc-sitevisit-stats sv)]
;
;        sv)
;      (catch Exception ex (debug "Error in create-param-summaries" ex)))))



;(defn simplify-sv [sv param]
;  (assoc sv :fieldresults (get-in sv [:fieldresults param])))

;(defn report-reducer
;  "generate a map with summary info over whole time period, including details for each parameter in :z-params"
;  [db sitevisits]
;  (reduce
;    (fn [r {:keys [fieldresults] :as sv}]
;      (try (let [param-keys (keys (:params qapp-requirements))
;
;                 ;_        (debug "SV " (get-in sv [:sitevisit/StationID
;                 ;                                    :stationlookup/StationID]))
;
;                 ;; count all sitevisits
;                 r          (update r :count-sitevisits inc!)
;
;                 r          (cond-> r
;                              ;; if the sitevisit has no fieldresults, add it to a :no-results list
;                              (empty? fieldresults)
;                              (->
;                                ;(update :no-results-rs conjv sv-ident)
;                                ;#(do
;                                ;   (debug "EMPTY" sv)
;                                ;   %)
;                                (update :count-no-results inc!)
;                                (update :no-results-rs conjv (when db (pull-no-result db (:db/id sv)))))
;                              ;; otherwise add it to :results list
;                              (seq fieldresults)
;                              (->
;                                (update :count-results inc!)))
;
;
;
;                 ;;_ (debug "reduce FRs")
;                 ;; reduce the fieldresults to summarize
;
;                 r          (reduce-kv
;                              (fn [r fr-k fr-m]
;                                (if (some #(= fr-k %) param-keys)
;                                  (-> r
;                                    ;; total count of results
;                                    (update :count-params inc!)
;                                    ;; count each param
;                                    (update-in [:z-params fr-k :count] inc!)
;
;                                    ;; count all exceedances
;                                    (cond->
;
;                                      ;; too few
;                                      (:incomplete? fr-m)
;                                      (->
;                                        (update :invalid-incomplete inc!)
;                                        ;(update :exceeds-total inc!)
;                                        ;(update-in [:z-params fr-k :too-few-rs] conjv sv-ident)
;                                        (update-in [:z-params fr-k :invalid-incomplete] inc!))
;
;                                      ;; precision exceedance
;                                      (:imprecise? fr-m)
;                                      (->
;                                        ;(update :qapp-rs conjv sv-ident)
;                                        ;(update :exceeds-total inc!)
;                                        (update :invalid-imprecise inc!)
;                                        ;(update-in [:z-params fr-k :prec-exceeds-rs] conjv sv-ident)
;                                        (update-in [:z-params fr-k :invalid-imprecise] inc!))
;
;                                      ;; water quality
;                                      (:too-high? fr-m)
;                                      (->
;                                        ;(update :qual-rs conjv sv-ident)
;                                        ;(update :exceeds-total inc!)
;                                        (update :exceed-high inc!)
;                                        (update-in [:z-params fr-k :exceed-high] inc!))
;
;                                      ;; water quality
;                                      (:too-low? fr-m)
;                                      (->
;                                        ;(update :qual-rs conjv sv-ident)
;                                        ;(update :exceeds-total inc!)
;                                        (update :exceed-low inc!)
;                                        (update-in [:z-params fr-k :exceed-low] inc!))
;
;                                      ;; valid
;                                      (:invalid? fr-m)
;                                      (->
;                                        (update :count-params-invalid inc!)
;                                        (update-in [:z-params fr-k :invalid] inc!)
;                                        (update-in [:z-params fr-k :invalid-rs] conjv (simplify-sv sv fr-k)))
;
;                                      ;; valid
;                                      (not (:invalid? fr-m))
;                                      (->
;                                        (update :count-params-valid inc!)
;                                        (update-in [:z-params fr-k :valid] inc!))
;
;                                      ;; exceedance
;                                      (and (not (:invalid? fr-m)) (:exceedance? fr-m))
;                                      (->
;                                        (update :count-params-exceedance inc!)
;                                        (update-in [:z-params fr-k :exceedance] inc!)
;                                        (update-in [:z-params fr-k :exceedance-rs] conjv (simplify-sv sv fr-k)))))
;                                  r))
;                              r fieldresults)
;
;                 r          (reduce-kv
;                              (fn [r k m]
;                                (let [total          (:count m)
;                                      valid          (get m :valid 0)
;                                      valid-percent  (when (> valid 0)
;                                                       (round2 2 (* 100 (/ valid total))))
;                                      exceedance     (get m :exceedance 0)
;                                      non-exceed     (- valid exceedance)
;                                      exceed-percent (when (> valid 0)
;                                                       (round2 2 (* 100 (/ non-exceed valid))))]
;
;                                  (-> r
;                                    (assoc-in [:z-params k :percent-valid] valid-percent)
;                                    (assoc-in [:z-params k :percent-exceedance] exceed-percent))))
;
;                              r (:z-params r))]
;
;
;             r)
;           (catch Exception ex (debug ex sv))))
;    {} sitevisits))

;(defn report-stats [report]
;  (let [count-svs                 (get report :count-sitevisits 0)
;        noresults                 (get report :count-no-results 0)
;        results                   (get report :count-results 0)
;        count-z-params            (count (:z-params report))
;        count-params-planned      (* count-z-params count-svs)
;        count-params-possible     (* count-z-params results)
;        count-params              (get report :count-params 0)
;        count-params-invalid      (get report :count-params-invalid 0)
;        count-params-valid        (get report :count-params-valid 0)
;        percent-params-valid      (round2 2 (* (/ count-params-valid count-params) 100))
;        count-params-exceedance   (get report :count-params-exceedance 0)
;        percent-params-exceedance (round2 2 (* (/ count-params-exceedance count-params-valid) 100))
;        results                   (get report :count-results 0)
;        percent                   (round2 2 (* (/ results count-svs) 100))
;
;        report                    (reduce-kv
;                                    (fn [r k m]
;                                      (let [order        (get-in param-config [k :order] 0)
;                                            sample-count (get-in param-config [k :count] 3)]
;                                        (-> r
;                                          (assoc-in [:z-params k :display-order] order)
;                                          (assoc-in [:z-params k :sample-count] sample-count))))
;                                    report (:z-params report))]
;
;    ;order          (get-in param-config [k :order] 0)
;    ;sample-count   (get-in param-config [k :count] 3)
;    ;(assoc-in [:z-params k :display-order] order)
;    ;(assoc-in [:z-params k :sample-count] sample-count)
;
;
;    ;count-sv-valid       (or (:count-sitevisits-valid report) 0)
;    ;percent-sv-valid     (round2 2 (* 100 (/ count-sv-valid count-svs)))
;
;    (-> report
;      (assoc :percent-complete percent)
;      ;(assoc :percent-valid percent-sv-valid)
;      ;(assoc :count-params-valid count-params-valid)
;      (assoc :count-params-planned count-params-planned)
;      (assoc :count-params-possible count-params-possible)
;      (assoc :percent-params-valid percent-params-valid)
;      (assoc :count-params-exceedance count-params-exceedance)
;      (assoc :percent-params-exceedance percent-params-exceedance))))

;(defn print-report [report]
;  (debug (-> (pr-str report)
;             (.replace ":" "\n") (.replace "{" "\n") (.replace "}" "\n")
;             (.replace "," "") (.replace "z-params" ""))))

;
;(s/def ::report-year (s/or :year integer? :all nil?))

;(defn check [type data]
;  (if (s/valid? type data)
;    true
;    (throw (AssertionError. (s/explain type data)))))

;(defn get-annual-report [db agency year]
;  ;{:pre [true (check ::report-year year)]}
;  (let [year       (if (= year "") nil year)
;        _          (check ::report-year year)
;        agency     (or agency "SYRCL")
;        done?      false
;        _          (info "BEGINNING TAC REPORT")
;        ;start-time (jt/zoned-date-time year)
;        ;end-time   (jt/plus start-time (jt/years 1))
;        sitevisits (if year
;                     (get-sitevisits db {:agency agency :year year})
;                     (get-sitevisits db {:agency agency}))
;        sitevisits (filter-sitevisits sitevisits)
;        sitevisits (create-sitevisit-summaries sitevisits)
;        ;_ (debug "report reducer")
;        ;param-keys (reduce #(apply conj %1 (map first (get-in %2 [:fieldresults]))) #{} sitevisits)
;        report     (report-reducer db sitevisits)
;        ;_          (debug "REPORT: "
;        ;             (select-keys report
;        ;               [:count-sitevisits :count-params-valid :count-params-exceedance]))
;        report     (report-stats report)
;        report     (-> report
;                     (assoc :report-year year)
;                     (assoc :qapp-requirements qapp-requirements)
;                     (assoc :param-config param-config))
;        _          (info "TAC REPORT COMPLETE")]
;    report))

;(defn filter-empty-fieldresults [svs]
;  (remove #(and
;             (empty? (:fieldresults %))
;             (= (:failcode %) "None")) svs))

;(defn get-dataviz-data
;  [db agency year]
;  (let [_            (info "GET-DATAVIZ" year agency)
;        year         (if (= year "") nil year)
;        _            (check ::report-year year)
;        param-config (into {} (remove #(:elide? (val %)) param-config))
;        svs          (get-sitevisits db {:agency agency :year year})
;        svs          (filter-empty-fieldresults svs)
;        svs          (create-sitevisit-summaries svs)
;        result       {:results-rs svs
;                      :param-config param-config
;                      :qapp-requirements qapp-requirements
;                      :report-year year}]
;
;    result))



;(defn get-sitevisit-summaries [db opts]
;  ;(debug "get-sitevisit-summaries opts: " opts)
;  (let [sitevisits (get-sitevisits db opts)
;        sitevisits (filter-empty-fieldresults sitevisits)
;        sitevisits (create-sitevisit-summaries sitevisits)
;        sitevisits (util/sort-maps-by sitevisits [:date :site])]
;    sitevisits))




;(comment
;  (require
;    '[datomic.api :as d]
;    '[java-time :as jt]
;    '[clojure.core.rrb-vector :as fv])
;
;
;  (in-ns 'rimdb-ui.api.tac-report)
;  (def cx (:cx (:datomic @user/system)))
;  ;(print-report (get-annual-report (d/db cx) 2016))
;
;  (d/q '[:find [(pull ?e [*]) ...]
;         :where [?e :stationlookup/StationID]] (d/db cx))
;
;  (d/q '[:find [(pull ?e [* {:constituentlookup/DeviceType [*]}])]
;         :where [?e :constituentlookup/Active true]
;         [?e :constituentlookup/DeviceType]] (d/db cx))
;
;  (d/q '[:find [(pull ?e [:db/id :samplingdevicelookup/SampleDevice :samplingdevicelookup/DeviceMax :samplingdevicelookup/DeviceMin
;                          :samplingdevicelookup/QAmax :samplingdevicelookup/QAmin]) ...]
;         :where [?e :samplingdevicelookup/Active true]] (d/db cx))
;
;
;  (let [t (jt/local-date-time #inst "2004-03-06T08:00:00.000-00:00")]))
