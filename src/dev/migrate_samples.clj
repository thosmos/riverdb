(ns migrate-samples
  (:require
    [datomic.api :as d]
    [theta.log :as log]
    [riverdb.state :refer [db cx]]))

(defn migrate-fms [sv sa]
  (log/debug "MIGRATE FIELD MEASURES")
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
                        frs    (map #(-> %
                                       (dissoc :db/id)
                                       (dissoc :fieldresult/SigFig)
                                       (dissoc :fieldresult/SampleRowID)
                                       (dissoc :fieldresult/SamplingDeviceCode)
                                       (dissoc :fieldresult/FieldResultRowID)
                                       (dissoc :fieldresult/ConstituentRowID)
                                       (dissoc :fieldresult/SamplingDeviceID)
                                       (dissoc :fieldresult/ResultTime)
                                       (assoc :fieldresult/uuid (d/squuid))
                                       ((fn [fr] (if (= 0 qaFlag)
                                                   (dissoc fr :fieldresult/QAFlag)
                                                   fr))))
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
                                 (assoc :sample/uuid (d/squuid))
                                 (assoc :sample/FieldResults (vec frs))
                                 (assoc :sample/ConstituentRef const)
                                 (assoc :sample/DeviceTypeRef devType)
                                 (assoc :sample/DeviceIDRef devID)
                                 (assoc :sample/Time time)
                                 (assoc :sample/SampleTypeCode :sampletypelookup.SampleTypeCode/FieldMeasure))]
                    (conj sas new-sa)))
                [] fr-m)


        txds  [[[:db/retractEntity sa-id]]]
        txds  (conj txds [{:db/id             sv-id
                           :sitevisit/Samples sas}])]
    txds))

(defn migrate-labs [sv sa]
  (log/debug "MIGRATE LABS")
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
                                       ;(dissoc :labresult/ConstituentRowID)
                                       ;(assoc :labresult/uuid (d/squuid))
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
                                 (assoc :sample/uuid (d/squuid))
                                 (assoc :sample/LabResults (vec lrs))
                                 (assoc :sample/ConstituentRef const)
                                 (assoc :sample/SampleTypeCode :sampletypelookup.SampleTypeCode/Grab))]
                    (conj sas new-sa)))
                [] lr-m)
        txds  [[[:db/retractEntity sa-id]]]
        txds  (conj txds [{:db/id             sv-id
                           :sitevisit/Samples sas}])]
    txds))

(defn migrate-obs [sv sa]
  (log/debug "MIGRATE OBSERVATIONS")
  (let [sv-id  (:db/id sv)
        sa-id  (:db/id sa)
        obsr-m (reduce
                 (fn [obsr-m obsr]
                   (let [const (:fieldobsresult/ConstituentRowID obsr)]
                     (update obsr-m const (fnil conj []) obsr)))
                 {} (:sample/FieldObsResults sa))
        sas    (reduce
                 (fn [sas [const obsrs]]
                   (let [obsrs (map #(-> %
                                       (dissoc :db/id)
                                       (dissoc :fieldobsresult/FieldObsResultRowID)
                                       (dissoc :fieldobsresult/SampleRowID))
                                 obsrs)
                         new-sa (-> sa
                                  (dissoc :db/id)
                                  (dissoc :sample/SiteVisitID)
                                  (dissoc :sample/FieldObsResults)
                                  (dissoc :sample/SampleRowID)
                                  (dissoc :sample/SampleComplete)
                                  (dissoc :sample/SampleReplicate)
                                  (dissoc :sample/DepthSampleCollection)
                                  (assoc :sample/uuid (d/squuid))
                                  (assoc :sample/FieldObsResults (vec obsrs))
                                  (assoc :sample/ConstituentRef const)
                                  (assoc :sample/SampleTypeCode :sampletypelookup.SampleTypeCode/FieldObs))]
                     (conj sas new-sa)))
                 [] obsr-m)
        txds   [[[:db/retractEntity sa-id]]]
        txds   (conj txds [{:db/id             sv-id
                            :sitevisit/Samples sas}])]
    txds))


(defn migrate [db]
  (log/debug "MIGRATE")
  (let [sv  (d/pull db '[* {:sitevisit/Samples [* {:sample/SampleTypeCode [*]}]}] 17592186176861)
        sas (:sitevisit/Samples sv)]
    (reduce
      (fn [txds sa]
        (vec
          (concat txds
            (case (get-in sa [:sample/SampleTypeCode :sampletypelookup/SampleTypeCode])
              "FieldMeasure" (migrate-fms sv sa)
              "FieldObs" (migrate-obs sv sa)
              "Grab" (migrate-labs sv sa)
              []))))
      [] sas)))

(comment
  (mapv #(d/transact (cx) %) (migrate (db)))
  #_"")
