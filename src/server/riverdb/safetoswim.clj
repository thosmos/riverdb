(ns riverdb.safetoswim
  (:require
    [clojure.tools.logging :as log :refer [debug info warn error]]
    [datomic.api :as d]
    [riverdb.db :refer [remap-query limit-fn]]
    [riverdb.state :refer [db cx]]
    [java-time :as jt]))


(defn get-latest-results []
  (let [latest (d/q '[:find ?si ?siid ?sinm ?ag (max ?svdt)
                      :where
                      [?cnst :constituentlookup/ConstituentCode "5-57-23-2-7"]
                      [?frs :labresult/ConstituentRowID ?cnst]
                      ;[?frs :labresult/Result ?rslt]
                      [?sa :sample/LabResults ?frs]
                      [?sv :sitevisit/Samples ?sa]
                      [?sv :sitevisit/StationID ?si]
                      [?sv :sitevisit/SiteVisitDate ?svdt]
                      ;[(> ?svdt #inst "2019")]
                      [?si :stationlookup/StationName ?sinm]
                      [?si :stationlookup/StationCode ?siid]
                      [?pj :projectslookup/Stations ?si]
                      ;[?pj :projectslookup/ProjectID ?agcd]
                      [?pj :projectslookup/AgencyCode ?ag]]
                 (db))
        eids   (mapv first latest)]
    eids))


(defn get-sitevisits2
  ([db {:keys [year agency fromDate toDate station stations stationCode projectID labConst] :as opts}]
   ;agency fromDate toDate station
   ;(debug "get-sitevisits" db opts)
   (let [fromDate (if (some? fromDate)
                    fromDate
                    (when year
                      (jt/zoned-date-time year)))
         toDate   (if (some? toDate)
                    toDate
                    (when year
                      (jt/plus fromDate (jt/years 1))))

         q        {:find  ['[(pull ?sv [:db/id
                                        [:sitevisit/SiteVisitID :as :svid]
                                        [:sitevisit/SiteVisitDate :as :date]
                                        [:sitevisit/Time :as :time]
                                        [:sitevisit/Notes :as :notes]
                                        {[:sitevisit/StationID :as :station] [:db/id
                                                                              [:stationlookup/StationID :as :station_id]
                                                                              [:stationlookup/StationCode :as :station_code]
                                                                              [:stationlookup/RiverFork :as :river_fork]
                                                                              [:stationlookup/ForkTribGroup :as :trib_group]]}
                                        {[:sitevisit/StationFailCode :as :failcode] [[:stationfaillookup/FailureReason :as :reason]]}
                                        {:sitevisit/Samples
                                         [{:sample/FieldResults
                                           [:db/id :fieldresult/Result :fieldresult/FieldReplicate
                                            {:fieldresult/ConstituentRowID
                                             [:db/id
                                              {:constituentlookup/AnalyteCode [:analytelookup/AnalyteShort]}
                                              {:constituentlookup/MatrixCode [:matrixlookup/MatrixShort]}
                                              {:constituentlookup/UnitCode [:unitlookup/Unit]}]}
                                            {[:fieldresult/SamplingDeviceID :as :device]
                                             [[:samplingdevice/CommonID :as :id]
                                              {[:samplingdevice/DeviceType :as :type] [[:samplingdevicelookup/SampleDevice :as :name]]}]}]}]}]) ...]]
                   :in    '[$]
                   :where '[[?sv :sitevisit/QACheck true]]
                   :args  [db]}


         q        (cond-> q
                    
                    labConst
                    (->
                      (update :where
                        #(-> %
                           (conj '[?const :constituentlookup/ConstituentCode labConst])
                           (conj '[?lrs :labresult/ConstituentRowID ?const])
                           (conj '[?sa :sample/LabResults ?lrs])
                           (conj '[?sv :sitevisit/Samples ?sa]))))


                    ;; if station
                    station
                    (->
                      (update :where #(-> %
                                        (conj '[?st :stationlookup/StationID ?station])
                                        (conj '[?sv :sitevisit/StationID ?st])))
                      (update :in conj '?station)
                      (update :args conj station))

                    ;; if stations
                    stations
                    (->
                      (update :where #(-> %
                                        (conj '[?st :stationlookup/StationID ?station])
                                        (conj '[?sv :sitevisit/StationID ?st])))
                      (update :in conj '[?station ...])
                      (update :args conj stations))

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
                                        (conj '[?pj :projectslookup/AgencyCode ?agency])
                                        (conj '[?sv :sitevisit/ProjectID ?pj])))
                      (update :in conj '?agency)
                      (update :args conj agency))

                    projectID
                    (->
                      (update :where #(-> %
                                        (conj '[?pj :projectslookup/ProjectID ?projectID])
                                        (conj '[?sv :sitevisit/ProjectID ?pj])))
                      (update :in conj '?projectID)
                      (update :args conj projectID))

                    ;; If either from- or to- date were passed, join the `sitevisit` entity
                    ;; and bind its `SiteVisitDate` attribute to the `?date` variable.
                    (or fromDate toDate)
                    (update :where conj
                      '[?sv :sitevisit/SiteVisitDate ?date])

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
                        '[(> ?date ?fromDate)]))

                    ;; similar to ?fromDate
                    toDate
                    (->
                      (update :in conj '?toDate)
                      (update :args conj (jt/java-date toDate))
                      (update :where conj
                        '[(< ?date ?toDate)]))

                    ;; last one, in case there are no conditions, get all sitevisits
                    (empty? (:where q))
                    (->
                      (update :where conj '[?sv :sitevisit/StationID])))
         q        (remap-query q)]
     ;; this query only returns field results due to missing :db/id on sample
     ;; we can modiy this to handle labresults too, or do it somewhere else
     (debug "QUERY" q)

     (d/query q))))


