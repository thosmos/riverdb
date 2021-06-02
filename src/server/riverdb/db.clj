(ns riverdb.db
  (:require
    [clojure.tools.logging :as log :refer [debug info warn error]]
    [datomic.api :as d]
    [riverdb.state :as state :refer [db cx]]
    [java-time :as jt]
    [domain-spec.core]))

(defn eids
  ([attr]
   (d/q '[:find [?e ...]
          :in $ ?attr
          :where
          [?e ?attr]]
     (db) attr))
  ([attr val]
   (d/q '[:find [?e ...]
          :in $ ?attr ?val
          :where
          [?e ?attr ?val]]
     (db) attr val)))

(defn attr->eid-val [attr]
  (d/q '[:find ?e ?v
         :in $ ?a
         :where
         [?e ?a ?v]]
    (db) attr))


(defn eids->retract-attr
  ([eids attr]
   (d/transact (cx)
     (vec
       (for [eid eids]
         [:db/retract eid attr]))))
  ([eids attr val]
   (d/transact (cx)
     (vec
       (for [eid eids]
         [:db/retract eid attr val])))))

(defn eid-attr->val [eid attr]
  (d/q '[:find ?v .
         :in $ ?e ?a
         :where
         [?e ?a ?v]]
    (db) eid attr))

(defn pull-attr
  ([attr]
   (pull-attr attr '[*]))
  ([attr query]
   (let [eid (d/q '[:find ?e .
                    :in $ ?attr
                    :where [?e ?attr]]
               (db) attr val)]
     (when eid
       (d/pull (db) query eid)))))

(defn pull-attr-val
  ([attr val]
   (pull-attr-val attr val '[*]))
  ([attr val query]
   (let [eid (d/q '[:find ?e .
                    :in $ ?attr ?val
                    :where [?e ?attr ?val]]
               (db) attr val)]
     (when eid
       (d/pull (db) query eid)))))

(defn remap-query
  [{args :args :as m}]
  {:query (dissoc m :args)
   :args  args})

(defn limit-fn
  "Pagination mimicking the MySql LIMIT"
  ([quantity coll]
   (limit-fn quantity 0 coll))
  ([quantity start-from coll]
   (let [quantity   (or quantity 10)
         start-from (or start-from 0)]
     (take quantity (drop start-from coll)))))

(defn schemas []
  (domain-spec.core/datomic->terse-schema (db)))

(defn schema-for-ns [ns]
  (filter #(= (namespace (first %)) ns) (schemas)))

(defn pull-entities
  ([attr]
   (pull-entities attr {}))
  ([attr {:keys [value query wheres] :as opts}]
   (let [find (if value
                '[:find [(pull ?e qu) ...]
                  :in $ ?attr qu ?value
                  :where
                  [?e ?attr ?value]]
                '[:find [(pull ?e qu) ...]
                  :in $ ?attr qu
                  :where
                  [?e ?attr]])
         find (if wheres
                (apply conj find wheres)
                find)
         qu (if query
              query
              '[*])]
     (if value
       (d/q find (db) attr qu value)
       (d/q find (db) attr qu)))))

(defn rpull
  ([eid]
   (d/pull (db) '[*] eid))
  ([eid query]
   (d/pull (db) query eid)))

(defn pull-tx [eid]
  (d/q '[:find (pull ?t [*]) .
         :in $ ?e
         :where
         [?e _ _ ?t]]
    (db) eid))



(defn find-lab-parameters []
  (d/q '[:find ?param ?param2 ?matrix ?method ?unit ?code
         :where
         [?sa :sample/SampleTypeCode :sampletypelookup.SampleTypeCode/Grab]
         [?sa :sample/Constituent ?e]
         [?e :constituentlookup/ConstituentCode ?code]
         [?e :constituentlookup/AnalyteCode ?ana]
         [?e :constituentlookup/MatrixCode ?mat]
         [?e :constituentlookup/MethodCode ?mthd]
         [?e :constituentlookup/UnitCode ?unt]
         [?ana :analytelookup/AnalyteShort ?param]
         [?ana :analytelookup/AnalyteName ?param2]
         [?mat :matrixlookup/MatrixShort ?matrix]
         [?mthd :methodlookup/MethodName ?method]
         [?unt :unitlookup/Unit ?unit]]
    (db)))

(defn find-field-parameters []
  (d/q '[:find ?param ?param2 ?matrix ?method ?unit ?code
         :where
         [?fr :fieldresult/ConstituentRowID ?e]
         [?e :constituentlookup/ConstituentCode ?code]
         [?e :constituentlookup/AnalyteCode ?ana]
         [?e :constituentlookup/MatrixCode ?mat]
         [?e :constituentlookup/MethodCode ?mthd]
         [?e :constituentlookup/UnitCode ?unt]

         [?ana :analytelookup/AnalyteShort ?param]
         [?ana :analytelookup/AnalyteName ?param2]
         [?mat :matrixlookup/MatrixShort ?matrix]
         [?mthd :methodlookup/MethodName ?method]
         [?unt :unitlookup/Unit ?unit]]
    (db)))

(defn find-project-stations [project]
  (d/q '[:find [(pull ?st [*]) ...]
         :in $ ?pj
         :where
         [?e :projectslookup/ProjectID ?pj]
         [?e :projectslookup/Stations ?st]]
    (db) project))

(defn get-constituent
  ([analyte]
   (map first
     (d/q '[:find [(pull ?e [:constituentlookup/ConstituentCode]) ...]
            :in $ ?analyte
            :where
            [?ana :analytelookup/AnalyteShort ?analyte]
            [?e :constituentlookup/AnalyteCode ?ana]]
       (db) analyte)))
  ([matrix analyte]
   (map first
     (d/q '[:find [(pull ?e [:constituentlookup/ConstituentCode]) ...]
            :in $ ?matrix ?analyte
            :where
            [?ana :analytelookup/AnalyteShort ?analyte]
            [?mat :matrixlookup/MatrixShort ?matrix]
            [?e :constituentlookup/MatrixCode ?mat]
            [?e :constituentlookup/AnalyteCode ?ana]]
       (db) matrix analyte)))
  ([matrix analyte method]
   (let [results (d/q '[:find [(pull ?e [:constituentlookup/ConstituentCode]) ...]
                        :in $ ?matrix ?param ?method
                        :where
                        [?ana :analytelookup/AnalyteShort ?param]
                        [?mat :matrixlookup/MatrixShort ?matrix]
                        [?mthd :methodlookup/MethodName ?method]
                        [?e :constituentlookup/AnalyteCode ?ana]
                        [?e :constituentlookup/MatrixCode ?mat]
                        [?e :constituentlookup/MethodCode ?mthd]]
                   (db) matrix analyte method)]
     (when (> (count results) 1)
       (warn "Got more than one Constituent at (get-constituent [matrix param])" (map first results)))
     (when (= (count results) 0)
       (warn "Got no Constituent at (get-constituent [matrix param])" matrix analyte method))
     (ffirst
       results))))

(defn get-units []
  (d/q '[:find [(pull ?e [*]) ...]
         :where [?e :unitlookup/UnitCode]] (db)))

(defn get-methods []
  (d/q '[:find [(pull ?e [*]) ...]
         :where [?e :methodlookup/MethodName]] (db)))


(defn get-const-info
  "given a constituent entity, pull the most important sub-codes that make up its code"
  [const]
  (d/pull (db)
    '[*
      {:constituentlookup/AnalyteCode [:analytelookup/AnalyteCode
                                       :analytelookup/AnalyteName
                                       :analytelookup/AnalyteShort]}
      {:constituentlookup/MatrixCode [:matrixlookup/MatrixCode
                                      :matrixlookup/MatrixName
                                      :matrixlookup/MatrixShort]}
      {:constituentlookup/UnitCode [:unitlookup/UnitCode
                                    :unitlookup/Unit]}
      {:constituentlookup/MethodCode [:methodlookup/MethodCode
                                      :methodlookup/MethodName]}
      {:constituentlookup/FractionCode [:fractionlookup/FractionCode
                                        :fractionlookup/FractionName]}]
    const))

(defn get-all-constituents
  "get a list of all constituents and their important field names"
  ([]
   (d/q '[:find
          [(pull ?e
             [:db/id [:constituentlookup/Name :as :name]
              {[:constituentlookup/AnalyteCode :as :analyte] [[:analytelookup/AnalyteName :as :name]]}
              {[:constituentlookup/MatrixCode :as :matrix] [[:matrixlookup/MatrixName :as :name]]}
              {[:constituentlookup/UnitCode :as :unit] [[:unitlookup/Unit :as :name]]}
              {[:constituentlookup/MethodCode :as :method] [[:methodlookup/MethodName :as :name]]}
              {[:constituentlookup/FractionCode :as :fraction] [[:fractionlookup/FractionName :as :name]]}])
           ...]
          :where [?e :constituentlookup/AnalyteCode]] (db))))

(defn generate-const-code [const-id]
  (let [c (get-const-info const-id)
        ma (get-in c [:constituentlookup/MatrixCode :matrixlookup/MatrixCode])
        me (get-in c [:constituentlookup/MethodCode :methodlookup/MethodCode])
        an (get-in c [:constituentlookup/AnalyteCode :analytelookup/AnalyteCode])
        fr (get-in c [:constituentlookup/FractionCode :fractionlookup/FractionCode])
        un (get-in c [:constituentlookup/UnitCode :unitlookup/UnitCode])]
    (apply str (interpose "-" [ma me an fr un]))))

(defn pull-sv
  "a full graph query of the most significant fields in a sitevisit"
  [db-id]
  (d/pull (db) '[*
                 {[:sample/_SiteVisitID :as :sitevisit/Samples]
                  [:db/id
                   {:sample/EventType
                    [:db/id :eventtypelookup/EventType]}
                   {:sample/SampleTypeCode
                    [:db/id :sampletypelookup/SampleTypeCode]}
                   :sample/QCCheck
                   :sample/SampleReplicate
                   {:sample/Constituent
                    [:constituentlookup/ConstituentCode
                     {:constituentlookup/AnalyteCode [:analytelookup/AnalyteCode
                                                      :analytelookup/AnalyteName]}]}
                   {[:fieldresult/_SampleRowID :as :sample/FieldResults]
                    [:db/id
                     :fieldresult/Result
                     :fieldresult/FieldReplicate
                     :fieldresult/QAFlag
                     :fieldresult/ResultTime
                     :fieldresult/SigFig
                     {:fieldresult/SamplingDeviceID
                      [:db/id
                       :samplingdevice/CommonID
                       {:samplingdevice/DeviceType [:samplingdevicelookup/SampleDevice]}]}]}

                   {[:fieldobsresult/_SampleRowID :as :sample/FieldObservations] [*]}
                   {[:labresult/_SampleRowID :as :sample/LabResults]
                    [* {:labresult/DigestExtractCode [*]} {:labresult/ResQualCode [*]}]}]}]
    db-id))


(defn rollback
  "Reassert retracted datoms and retract asserted datoms in a transaction,
  effectively \"undoing\" the transaction.

  WARNING: *very* naive function!"
  [conn tx]
  (let [tx-log (-> conn d/log (d/tx-range tx nil) first) ; find the transaction
        txid   (-> tx-log :t d/t->tx) ; get the transaction entity id
        newdata (->> (:data tx-log)   ; get the datoms from the transaction
                  (remove #(= (:e %) txid)) ; remove transaction-metadata datoms
                  ; invert the datoms add/retract state.
                  (map #(do [(if (:added %) :db/retract :db/add) (:e %) (:a %) (:v %)]))
                  reverse)] ; reverse order of inverted datoms.
    @(d/transact conn newdata)))  ; commit new datoms.


(defn add-person-ref [cx personID-key personRef-key]
  (d/transact cx
    (vec (for [[sv ps] (d/q '[:find ?sv ?p
                              :in $ ?personID-key
                              :where
                              [?sv ?personID-key ?pid]
                              [?p :person/PersonID ?pid]]
                         (d/db cx) personID-key)]
           [:db/add sv personRef-key ps]))))

(defn add-person-refs-to-sitevisits [cx]
  (let [des (pr-str (:tx-data @(add-person-ref cx :sitevisit/DataEntryPerson :sitevisit/DataEntryPersonRef)))
        cps (count (:tx-data @(add-person-ref cx :sitevisit/CheckPerson :sitevisit/CheckPersonRef)))
        qas (count (:tx-data @(add-person-ref cx :sitevisit/QAPerson :sitevisit/QAPersonRef)))]
    (log/debug "des" des "cps" cps "qas" qas)))


(defn retract-all-sitevisits-between-dates [cx from to]
  (let [svs (d/q '[:find [?e ...]
                   :in $ ?from ?to
                   :where
                   [(> ?dt ?from)]
                   [(< ?dt ?to)]
                   [?ag :agencylookup/AgencyCode "SYRCL"]
                   [?e :sitevisit/AgencyCode ?ag]
                   [?e :sitevisit/SiteVisitDate ?dt]]
              (db) from to)
        sps (d/q '[:find [?e ...]
                   :in $ [?sid ...]
                   :where [?e :sample/SiteVisitID ?sid]]
              (db) svs)
        frs  (d/q '[:find [?e ...]
                    :in $ [?sid ...]
                    :where [?e :fieldresult/SampleRowID ?sid]]
               (db) sps)
        fos  (d/q '[:find [?e ...]
                    :in $ [?sid ...]
                    :where [?e :fieldobsresult/SampleRowID ?sid]]
               (db) sps)
        lrs  (d/q '[:find [?e ...]
                    :in $ [?sid ...]
                    :where [?e :labresult/SampleRowID ?sid]]
               (db) sps)
        all  (concat svs sps frs fos lrs)
        txds (vec (for [a all]
                    [:db.fn/retractEntity a]))
        txds' (partition 100 txds)]
    (for [t txds']
      (d/transact cx (vec t)))))



(defn retract-all-samples-without-svs [cx]
  (let [sids (->>
               (d/q '[:find [(pull ?e [:db/id :sample/SiteVisitID]) ...]
                      :where
                      [?e :riverdb.entity/ns :entity.ns/sample]]
                 (db))
               (remove :sample/SiteVisitID)
               (map :db/id))
        frs  (d/q '[:find [?e ...]
                    :in $ [?sid ...]
                    :where [?e :fieldresult/SampleRowID ?sid]]
               (db) sids)
        fos  (d/q '[:find [?e ...]
                    :in $ [?sid ...]
                    :where [?e :fieldobsresult/SampleRowID ?sid]]
               (db) sids)
        lrs  (d/q '[:find [?e ...]
                    :in $ [?sid ...]
                    :where [?e :labresult/SampleRowID ?sid]]
               (db) sids)
        all  (concat sids frs fos lrs)
        txds (vec (for [a all]
                    [:db.fn/retractEntity a]))
        txds' (partition 100 txds)]
    (for [t txds']
      (d/transact cx (vec t)))))




;(defn retract-sitevisits-since [cx date]
;  (let [svs (d/q '[:find [(pull ?e [:db/id :sitevisit/SiteVisitDate {:sample/_SiteVisitID [:db/id :fieldresult/_SampleRowID :labresult/_SampleRowID :fieldobsresult/_SampleRowID]}]) ...]
;                   :where
;                   [(> ?d date)]
;                   [?e :sitevisit/SiteVisitDate ?d]]
;              (d/db cx))]
;    (reduce
;      (fn [eids sv]
;        (-> eids
;          (conj (:db/id sv))))
;      [] svs)))

