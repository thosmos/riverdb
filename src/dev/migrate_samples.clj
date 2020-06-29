(ns migrate-samples
  (:require
    [datomic.api :as d]
    [theta.log :as log]
    [riverdb.state :refer [db cx]]
    [riverdb.db :as rdb]))

(defn get-sa-ik [sa-ik const]
  (let [sa-ik (when sa-ik
                (let [[mat ana] (d/q '[:find [?mat ?ana]
                                       :in $ ?e
                                       :where
                                       [?e :constituentlookup/AnalyteCode ?a]
                                       [?a :analytelookup/AnalyteShort ?ana]
                                       [?e :constituentlookup/MatrixCode ?m]
                                       [?m :matrixlookup/MatrixShort ?mat]]
                                  (db) (:db/id const))]
                  (str sa-ik "-" mat "_" ana)))]))


(defn migrate-fms [sv sa]
  ;(log/debug "MIGRATE FIELD MEASURES")
  (let [sv-id (:db/id sv)
        sa-id (:db/id sa)
        fr-m  (reduce
                (fn [fr-m fr]
                  (let [const   (:fieldresult/ConstituentRowID fr)
                        devType (:fieldresult/SamplingDeviceCode fr)]
                    (update fr-m [const devType] (fnil conj []) fr)))
                {} (:sample/FieldResults sa))
        sas   (reduce
                (fn [sas [[const devType] frs]]
                  (let [fr     (first frs)
                        devID  (:fieldresult/SamplingDeviceID fr)
                        time   (:fieldresult/ResultTime fr)
                        qaFlag (:fieldresult/QAFlag fr)

                        frs    (map (fn [fr]
                                      (-> fr
                                        (dissoc :db/id)
                                        (dissoc :fieldresult/SigFig)
                                        (dissoc :fieldresult/SampleRowID)
                                        (dissoc :fieldresult/SamplingDeviceCode)
                                        (dissoc :fieldresult/FieldResultRowID)
                                        (dissoc :fieldresult/ConstituentRowID)
                                        (dissoc :fieldresult/SamplingDeviceID)
                                        (dissoc :fieldresult/ResultTime)
                                        (assoc :fieldresult/uuid (d/squuid))
                                        (#(if (= 0 qaFlag)
                                            (dissoc % :fieldresult/QAFlag)
                                            %))))
                                 frs)
                        new-sa (-> sa
                                 (dissoc :db/id)
                                 (dissoc :sample/uuid)
                                 (dissoc :sample/SiteVisitID)
                                 (dissoc :sample/FieldResults)
                                 (dissoc :sample/SampleRowID)
                                 (dissoc :sample/SampleComplete)
                                 (dissoc :sample/SampleReplicate)
                                 (dissoc :sample/DepthSampleCollection)
                                 (dissoc :org.riverdb/import-key)
                                 (assoc :sample/uuid (d/squuid))
                                 (assoc :sample/FieldResults (vec frs))
                                 (assoc :sample/Constituent const)
                                 (#(if devType
                                     (assoc % :sample/DeviceType devType)
                                     %))
                                 (#(if devID
                                     (assoc % :sample/DeviceID devID)
                                     %))
                                 (#(if time
                                     (assoc % :sample/Time time)
                                     %))
                                 (assoc :sample/SampleTypeCode :sampletypelookup.SampleTypeCode/FieldMeasure))]
                    (conj sas new-sa)))
                [] fr-m)


        txds  [[[:db/retractEntity sa-id]]]
        txds  (if (seq sas)
                (conj txds [{:db/id             sv-id
                             :sitevisit/Samples sas}])
                txds)]
    txds))

(defn migrate-labs [sv sa]
  ;(log/debug "MIGRATE LABS")
  (let [sv-id (:db/id sv)
        sa-id (:db/id sa)
        lr-m  (reduce
                (fn [lr-m lr]
                  (let [const (:labresult/ConstituentRowID lr)]
                    (update lr-m const (fnil conj []) lr)))
                {} (:sample/LabResults sa))
        sas   (reduce
                (fn [sas [const lrs]]
                  (let [lr     (first lrs)
                        qaFlag (:labresult/QAFlag lr)
                        lrs    (map #(-> %
                                       (dissoc :db/id)
                                       (dissoc :labresult/LabResultRowID)
                                       (dissoc :labresult/SampleRowID)
                                       (dissoc :labresult/SigFig)
                                       (dissoc :labresult/ConstituentRowID)
                                       (assoc :labresult/uuid (d/squuid))
                                       ((fn [lr] (if (= 0 qaFlag)
                                                   (dissoc lr :labresult/QAFlag)
                                                   lr))))
                                 lrs)
                        new-sa (-> sa
                                 (dissoc :db/id)
                                 (dissoc :sample/SiteVisitID)
                                 (dissoc :sample/LabResults)
                                 (dissoc :sample/SampleRowID)
                                 (dissoc :sample/SampleComplete)
                                 (dissoc :org.riverdb/import-key)
                                 (assoc :sample/uuid (d/squuid))
                                 (assoc :sample/LabResults (vec lrs))
                                 (assoc :sample/Constituent const)
                                 (assoc :sample/SampleTypeCode :sampletypelookup.SampleTypeCode/Grab))]
                    (conj sas new-sa)))
                [] lr-m)
        txds  [[[:db/retractEntity sa-id]]]
        txds  (if (seq sas)
                (conj txds [{:db/id             sv-id
                             :sitevisit/Samples sas}])
                txds)]
    txds))

(defn migrate-obs [sv sa]
  ;(log/debug "MIGRATE OBSERVATIONS")
  (let [sv-id  (:db/id sv)
        sa-id  (:db/id sa)
        obsr-m (reduce
                 (fn [obsr-m obsr]
                   (let [const (:fieldobsresult/ConstituentRowID obsr)]
                     (update obsr-m const (fnil conj []) obsr)))
                 {} (:sample/FieldObsResults sa))
        sas    (reduce
                 (fn [sas [const obsrs]]
                   (let [obsrs  (map #(-> %
                                        (dissoc :db/id)
                                        (dissoc :fieldobsresult/FieldObsResultRowID)
                                        (dissoc :fieldobsresult/SampleRowID)
                                        (assoc :fieldobsresult/uuid (d/squuid)))
                                  obsrs)
                         new-sa (-> sa
                                  (dissoc :db/id)
                                  (dissoc :sample/SiteVisitID)
                                  (dissoc :sample/FieldObsResults)
                                  (dissoc :sample/SampleRowID)
                                  (dissoc :sample/SampleComplete)
                                  (dissoc :sample/SampleReplicate)
                                  (dissoc :sample/DepthSampleCollection)
                                  (dissoc :org.riverdb/import-key)
                                  (dissoc :sample/uuid)
                                  (assoc :sample/uuid (d/squuid))
                                  (assoc :sample/FieldObsResults (vec obsrs))
                                  (assoc :sample/Constituent const)
                                  (assoc :sample/SampleTypeCode :sampletypelookup.SampleTypeCode/FieldObs))]
                     (conj sas new-sa)))
                 [] obsr-m)
        txds   [[[:db/retractEntity sa-id]]]
        txds   (if (seq sas)
                 (conj txds [{:db/id             sv-id
                              :sitevisit/Samples sas}])
                 txds)]
    txds))

(defn migrate-sv [sv]
  (let [sas (:sitevisit/Samples sv)]
    (try
      (log/debug "MIGRATING SV" (:db/id sv) (.toInstant ^java.util.Date (:sitevisit/SiteVisitDate sv)))
      (reduce
        (fn [txds sa]
          (vec
            (concat txds
              (case (get-in sa [:sample/SampleTypeCode :sampletypelookup/SampleTypeCode])
                "FieldMeasure" (migrate-fms sv sa)
                "FieldObs" (migrate-obs sv sa)
                "Grab" (migrate-labs sv sa)
                []))))
        [] sas)
      (catch Exception ex (log/debug "FAILED TO MIGRATE SV" sv ex)))))

(defn migrate [db]
  (log/debug "MIGRATE")
  (let [svs (d/q '[:find
                   [(pull ?e
                      [* {:sitevisit/Samples
                          [* {:sample/SampleTypeCode [:sampletypelookup/SampleTypeCode]}]}]) ...]
                   :where [?e :riverdb.entity/ns :entity.ns/sitevisit]]
              db)]
    (for [sv svs]
      (migrate-sv sv))))

(comment
  (doseq [txds (migrate (db))]
    (mapv #(try
             @(d/transact (cx) %)
             (catch Exception ex
               (log/debug "ERROR:" (.getMessage ^Exception ex)))) txds))
  (let [sv (d/pull (db) '[* {:sitevisit/Samples [* {:sample/SampleTypeCode [:sampletypelookup/SampleTypeCode]}]}] 17592186284983)]
    (mapv #(d/transact (cx) %) (migrate-sv sv)))
  #_"")
