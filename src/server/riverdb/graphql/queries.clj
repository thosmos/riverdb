(ns riverdb.graphql.queries
  (:require [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.tools.logging :as log :refer [debug info warn error]]
            [clojure.walk]
            [com.rpl.specter :refer [select transform ALL FIRST LAST MAP-KEYS MAP-VALS]]
            [com.walmartlabs.lacinia.executor :as executor]
            [datascript.core :as ds]
            [datomic.api :as d]
            [java-time :as jt]
            [riverdb.db :refer [limit-fn remap-query]]
            [riverdb.graphql.schema :as schema :refer [table-specs-ds spec-spec-keys spec-specs-ds]]
            [riverdb.state :as st :refer [db cx]]
            [riverdb.station]
            [riverdb.model.logger :refer [get-loggers get-logger-samples]]
            [riverdb.model.user :as user]
            [riverdb.api.tac-report :as tac-report]
            [thosmos.util :as tu]
            [tick.core :as t])
  (:import [java.util Date]
           [java.text SimpleDateFormat]))


(defn resolve-hello
  [context args value]
  (str "Hello, Clojurians! \nArgs: " args))


(defn resolve-current-user [ctx args value]
  (debug "RESOLVE CURRENT USER")
  (let [user? (:user ctx)]
    (when user?
      (tu/walk-remove-ns
        (user/return-user user?)))))


(def key-change
 {:db/id        :id,
  :fieldresults :results})

(defn add-resultsv [sv fields]
  (let [field? (contains? (set fields) :resultsv)
        sv     (if field?
                 (assoc sv :resultsv (vec (for [[_ rslt] (:results sv)] rslt)))
                 sv)]
    sv))

(defn add-results [sv fields]
  (let [resultsv? (contains? (set fields) :resultsv)
        fieldmeas? (contains? (set fields) :fieldmeas)
        fieldobs? (contains? (set fields) :fieldobs)
        labresults? (contains? (set fields) :labresults)]
    (for [[_ rslt] (:results sv)]
      (let [typ (:type rslt)]
       (cond-> sv
         resultsv?
         (update :resultsv (fnil conj []) rslt)
         (and fieldmeas? (= typ "FieldMeasure"))
         (update :fieldmeas (fnil conj []) rslt)
         (and fieldobs? (= typ "FieldObs"))
         (update :fieldobs (fnil conj []) rslt)
         (and labresults? (= typ "Grab"))
         (update :labresults (fnil conj []) rslt))))))


(defn parse-sels [sels]
  (vec
    (for [sel sels]
      (let [leaf? (:leaf? sel)
            nskw  (get-in sel [:field-definition :qualified-name])]
        (if leaf?
          nskw
          {nskw (parse-sels (:selections sel))})))))

(defn walk-ids [form]
  (clojure.walk/prewalk
    (fn [form]
      (if (and (keyword? form) (= (name form) "id"))
        :db/id
        form))
    form))

(defn walk-rename-keys [form key-map]
  (clojure.walk/prewalk
    (fn [form]
      (if (map? form)
        (clojure.set/rename-keys form key-map)
        form))
    form))

(defn walk-rename-ns [form ns-map]
  (clojure.walk/prewalk
    (fn [form]
      (if (keyword? form)
        (if-let [new-ns (get ns-map (namespace form))]
          (keyword new-ns (name form))
          form)
        form))
    form))

(defn walk-response [map]
  (clojure.walk/postwalk
    (fn [form]
      (if (map? form)
        (reduce-kv
          (fn [acc k v]
            (assoc acc (keyword (name k)) (if (= k :db/id) (str v) v)))
          {} form)
        form))
    map))

(defn resolve-sitevisits [context args value]
  (let [_          (debug "RESOLVE DATOMIC SITEVISITS" args)
        selection  (:com.walmartlabs.lacinia/selection context)
        ;_ (pprint selection)
        selections (vec (:selections selection))
        ;_ (pprint selections)
        fields     (vec (map :field selections))
        ;_          (debug "FIELDS" fields)
        tk         (get selection :field)
        fromDate   (when (:fromYear args)
                     (jt/java-date (jt/zoned-date-time (:fromYear args))))
        toDate     (when (:toYear args)
                     (jt/java-date (jt/zoned-date-time (:toYear args))))
        ;_          (debug "ARGS" args)
        opts       (cond-> args
                     fromDate
                     (merge
                       {:fromDate fromDate})
                     toDate
                     (merge
                       {:toDate toDate}))

        svs (tac-report/get-sitevisits (db) opts)

        limit      (get args :limit)
        offset     (get args :offset)
        svs        (if (or limit offset)
                     (limit-fn limit offset svs)
                     svs)

        svs (tac-report/create-sitevisit-summaries3 svs)
        svs (tac-report/filter-empty-fieldresults svs)
        svs (riverdb.util/sort-maps-by svs [:date :site])



        formatter  (SimpleDateFormat. "yyyy-MM-dd")

        svs        (for [sv svs]
                     (-> sv
                       (update :station thosmos.util/walk-remove-ns)
                       (clojure.set/rename-keys key-change)
                       (tu/walk-modify-k-vals :id str)
                       (update :date #(.format formatter %))
                       ;(add-results fields)
                       (add-resultsv fields)))]
    ;(debug "SITEVISITS first:" (first svs))
    svs))



(defn resolve-stations [context args value]
  (debug "RESOLVE stations" args)
  (let [;result (get-d-sites)
        selection  (:com.walmartlabs.lacinia/selection context)
        ;_ (pprint selection)
        selections (vec (:selections selection))
        ;_ (pprint selections)
        ;fields     (vec (remove
        ;                  #(clojure.string/starts-with? (name %) "__")
        ;                  (map :field selections)))
        fields     (map :field-name selections)
        ;tk         (get selection :field)

        id-field?  (some #{:id} fields)


        ;_          (debug "FIELDS" fields args)
        query      (vec (for [field fields]
                          (if
                            (= :id field)
                            :db/id
                            [(keyword "stationlookup" (name field)) :as field])))
        results    (riverdb.station/get-stations (db) query args)
        results    (if id-field?
                     (for [r results]
                       (-> r
                         (assoc :id (str (:db/id r)))
                         (dissoc :db/id)))
                     results)]

    results))

(defn calc-geom-avg [values]
  ;; NOTE example: ({:date 2003-05-03, :value 38.8, :isHigh false} {:date 2003-06-07, :value 2.0, :isHigh false})
  ;(debug "calc-geom-avg values" values)
  (loop [vals values result []]
    (let [v         (first vals)
          ;_ (debug "v" v)
          v-rest    (rest vals)
          v-date    (t/date (t/instant (:date v)))
          dt-6weeks (t/- v-date (t/new-period 6 :weeks))
          vs        (reduce
                      (fn [out next]
                        (if (t/>= (t/date (t/instant (:date next))) dt-6weeks)
                          (conj out next)
                          out))
                      [] v-rest)
          avg       (when (not-empty vs)
                      (->> (conj vs v)
                        (map :value)
                        (apply *)
                        Math/sqrt
                        (tu/round2 1)))
          ;_         (debug "date:" v-date "dt-6wks:" dt-6weeks "value:" (:value v) (type (:value v)) "vs:" vs "avg:" avg)
          res       (cond-> v
                      avg
                      (assoc :avg avg)
                      (and avg (or
                                 (> avg 100)
                                 (> (:value v) 320)))
                      (assoc :isHigh true))
          result    (conj result res)]
      (if (seq v-rest)
        (recur v-rest result)
        result))))



(defn resolve-safetoswim [context args value]
  (debug "RESOLVE! safetoswim stations" args)
  (try
    (let [selection   (:com.walmartlabs.lacinia/selection context)
          selections  (vec (:selections selection))
          fields      (vec (map :field selections))

          ;fields     (vec (remove
          ;                  #(clojure.string/starts-with? (name %) "__")
          ;                  (map :field selections)))

          id-field?   (some #{:id} fields)
          get-latest? (some #{:latest} fields)
          get-data?   (some #{:values} fields)

          fields      (remove #(#{:id :latest :values} %) fields)

          query       (into [:db/id]
                        (for [field fields]
                          [(keyword "stationlookup" (name field)) :as field]))
          ;_           (debug "FIELDS" fields args)
          const-ecoli "5-57-464-0-7"
          args        (if (:constituent args)
                        args
                        (assoc args :constituent const-ecoli))
          stations    (riverdb.station/get-stations (db) query args)
          ;_           (debug "RESULT COUNT" (count stations))

          stations    (if id-field?
                        (for [r stations]
                          (-> r
                            (assoc :id (str (:db/id r)))))
                        stations)

          eids        (map :db/id stations)]
      (debug "EIDS" eids "ARGS" args)

      ;; TODO for each station, we want latest lab result for the given constituent
      (if-not (or get-data? get-latest?)
        ;; if we're not getting the data, then just return the stations
        stations

        (let [;; get the dates and results for the sites
              const-coliform "5-56-23-20-7"
              const-ecoli    "5-57-464-0-7"
              const-entero   "5-9000-9002-0-7"
              consts         [const-coliform const-ecoli const-entero]
              data           (d/q '[:find ?stationid ?svdt ?rslt
                                    :in $ ?const-code [?stationid ...]
                                    :where
                                    ;(or
                                    ;  ;; coliform
                                    ;  [?cnst :constituentlookup/ConstituentCode "5-56-23-20-7"]
                                    ;  ;; ecoli
                                    ;  [?cnst :constituentlookup/ConstituentCode "5-57-464-0-7"]
                                    ;  ;; enterococcus
                                    ;  [?cnst :constituentlookup/ConstituentCode "5-9000-9002-0-7"])
                                    [?cnst :constituentlookup/ConstituentCode ?const-code]
                                    [?frs :labresult/ConstituentRowID ?cnst]
                                    [?frs :labresult/Result ?rslt]
                                    ;[?frs :labresult/ResQualCode ?rq]
                                    ;[?rq :resquallookup/ResQualCode ?rqual]
                                    [?sa :sample/LabResults ?frs]
                                    [?sv :sitevisit/Samples ?sa]
                                    [?sv :sitevisit/StationID ?stationid]
                                    [?sv :sitevisit/SiteVisitDate ?svdt]]
                               (db) const-ecoli eids)

              ;; make a map on station-id for easy lookup
              ;_           (debug "LATEST" data)
              ;formatter      (SimpleDateFormat. "yyyy-MM-dd")

              data-m         (reduce
                               (fn [out [stationid ^Date svdate rslt]]
                                 (let [result {:date  (.getTime svdate) ; (.format formatter svdate)
                                               :value rslt}]
                                   ;result (if (not= rqual "")
                                   ;         (assoc result :qual rqual)
                                   ;         result)]
                                   (update-in out [stationid] (fnil conj [])
                                     result)))
                               {} data)

              results ;; for each station, return either all the data or just the latest
                             (reduce
                               (fn [out station]
                                 (let [values  (->>
                                                 (get station :db/id)
                                                 (get data-m)
                                                 (sort-by :date #(compare %2 %1)))
                                       values  (when (seq values)
                                                 (calc-geom-avg values))
                                       latest  (when (seq values)
                                                 (first values))
                                       station (cond-> station
                                                 get-latest?
                                                 (assoc :latest latest)
                                                 get-data?
                                                 (assoc :values values))]
                                   (if (seq values)
                                     (conj out station)
                                     out)))
                               [] stations)]
          ;(debug "first result" (first results))
          results)))
    (catch Exception ex
      (debug "SAFE To SWIM ERROR" ex)
      {:error (.toString ex)})))



(defn resolve-loggers [context args value]
  (log/debug "RESOLVE LOGGERS" args)

  (comment
    ;; query
    :loggers
    {:type        '(list :logger)
     :resolve     :resolve-loggers
     :description "loggers"
     :args        {:projectRef {:type 'ID}
                   :stationRef {:type 'ID}
                   :parameterRef {:type 'ID}}}

    ;; result object
    :logger {:fields {:id            {:type 'ID}
                      :name          {:type 'String}
                      :projectRef    {:type :projectslookup}
                      :parameterRef  {:type :parameter}
                      :deviceTypeRef {:type :samplingdevicelookup}
                      :stationRef    {:type :stationlookup}}})
  ;:samples       {:type '(list :logger_sample)
  ;                :args {:before {:type 'Int}
  ;                       :after {:type 'Int}}}}})

  (try
    (let [selection  (:com.walmartlabs.lacinia/selection context)
          selections (vec (:selections selection))
          pull-q     (parse-sels selections)
          pull-q     (walk-ids pull-q)

          _ (debug "LOGGERS PULL-Q" pull-q)

          args       (into {} (mapv (fn [[k v]] identity [k (Long/parseLong v)]) args))

          rez        (get-loggers (db) pull-q args)

          rez (walk-response rez)]

      rez)

    (catch Exception ex
      (debug "LOGGERS ERROR" ex)
      {:error (.toString ex)})))

(defn resolve-logsamples [context {:keys [loggerRef before after] :as args} value]
  (log/debug "RESOLVE LOGSAMPLES" args)

  (comment
    ;; query
    :logsamples
    {:type    '(list :logsample)
     :resolve :resolve-logsamples
     :args    {:loggerRef {:type 'ID}
               :before    {:type 'Int}
               :after     {:type 'Int}}}

    ;; result object
    :logsample {:fields {:id     {:type 'ID}
                         :result {:type 'Float}
                         :inst   {:type 'Int}}})

  (try
    (let [selection   (:com.walmartlabs.lacinia/selection context)
          selections  (vec (:selections selection))
          pull-q      (parse-sels selections)
          pull-q      (walk-ids pull-q)
          fields      (vec (map :field selections))
          id-field?   (some #{:id} fields)
          inst-field? (some #{:inst} fields)
          args        (into {} (mapv (fn [[k v]] identity [k (Long/parseLong v)]) args))
          args        (cond-> args
                        loggerRef
                        (assoc :loggerRef (Long/parseLong loggerRef))
                        before
                        (assoc :before (Date. ^Long (* 1000 before)))
                        after
                        (assoc :after (Date. ^Long (* 1000 after))))

          rez         (get-logger-samples (db) pull-q args)
          rez         (walk-response rez)
          ;; if we have results with :inst, then convert to secs
          rez         (if (and inst-field? (seq rez))
                        (map #(assoc % :inst (int (/ (.getTime (:inst %)) 1000))) rez)
                        rez)]

      (log/debug "FIRST LOGSAMPLES" (first rez))
      rez)
    (catch Exception ex
      (debug "LOGSAMPLES ERROR" ex)
      {:error (.toString ex)})))



(defn resolve-agency-ref [context args value]
  (let [selection    (:com.walmartlabs.lacinia/selection context)
        fk           (:field selection)
        fk-field-val (get value fk)
        fields       (vec (for [field (-> selection :selections)]
                            [(keyword "agencylookup" (name (:field field))) :as (:field field)]))]
    ;(debug "AGENCY-REF" fk-field-val fields)
    (d/q '[:find (pull ?e q) .
           :in $ ?e q]
      (db) (:db/id fk-field-val) fields)))


(defn nskw [nsk k]
  (keyword (name nsk) (name k)))

(defn resolve-attr-spec [context args value])

(defn get-spec-attrs-gql [spec-attrs]
  (into {}
    (for [sattr spec-attrs]
      (when-let [gql (:attr/gql sattr)]
        [gql (:attr/key sattr)]))))

(defn get-spec
  ([spec-ds spec-ns]
   (ds/pull @spec-ds '[*] [:entity/ns spec-ns])))

(defn get-identity-key
  ([spec-ds spec-ns]
   (first (:entity/pks (get-spec spec-ds spec-ns)))))

(defn get-spec-attr-by-name [name attrs]
  (first (select [ALL #(= (:attr/name %) name)] attrs)))

(defn get-spec-attr-by-key [key attrs]
  (first (select [ALL #(= (:attr/key %) key)] attrs)))

(defn parse-selections [level sortData sels]
  (vec
    (for [sel sels]
      (let [field-nm           (name (get-in sel [:field-definition :field-name]))
            table-nm           (name (get-in sel [:field-definition :type-name]))
            q-field            (get-in sel [:field-definition :qualified-field-name])
            q-field            (if (= (name q-field) "id")
                                 :db/id
                                 q-field)
            query              (if (:leaf? sel)
                                 q-field
                                 {q-field (parse-selections (inc level) sortData (:selections sel))})
            is-sort-parent?    (when sortData
                                 (= (:parent sortData) q-field))
            missing-child-key? (when is-sort-parent?
                                 (empty?
                                   (select [ALL :field-definition :qualified-field-name #(= % (:child sortData))] (:selections sel))))
            query              (if (and is-sort-parent? missing-child-key?)
                                 (update query q-field conj (:child sortData))
                                 query)]
        query))))


;(let [query (vec
;              (concat
;                [:db/id]
;                (for [field fields]
;                  (let [field-as [(nskw table field) :as (keyword field)]]
;                    (if (and nestedSort? (= field parentSortField))
;                      {field-as [:db/id childSortNSKW]}
;                      field-as)))))]))






;(defn resolve-rimdb
;  ([spec-ns]
;   ;(debug "RESOLVE RIMDB DEFAULT" spec-ns)
;   (resolve-rimdb spec-ns :query-single))
;  ([spec-ns query-type]
;   (fn [context args value]
;     (if (and (= query-type :query-single) (not (:id args)))
;       (do
;         (debug "requested a single result with no ID arg?  Returning NIL")
;         nil)
;       (try
;         (let [selection       (:com.walmartlabs.lacinia/selection context)
;               ;_               (pprint selection)
;               selections      (vec (:selections selection))
;               ;_ (pprint selections)
;               pull-q          (parse-selections selections)
;               _               (pprint pull-q)
;
;               fields          (map :field selections)
;               tk              (get selection :field)
;
;               table           (name tk)
;               table           (if (str/starts-with? table "all")
;                                 (subs table 3 (dec (count table)))
;                                 table)
;
;               meta?           (str/starts-with? table "_all")
;
;               table           (if meta?
;                                 (subs table 4 (- (count table) 5))
;                                 table)
;
;               _               (debug "RESOLVE" (if meta? "META" "TABLE") (or query-type "NIL") spec-ns "table:" table "args:" args "fields:" fields)
;
;               fields          (vec (->> fields
;                                      (map name)
;                                      (remove #(or (= % "id") (str/starts-with? % "rk_") (str/starts-with? % "__") (= % "count")))))
;
;               is-spec-spec?   (some? (spec-spec-keys spec-ns))
;
;               _               (when is-spec-spec?
;                                 (debug "It's a SPEC!"))
;
;
;               ;_           (debug "RIVERDB RESOLVER tk: " tk ", ARGS: " args ", FIELDS: " fields)
;
;
;
;               spec-entity     (if is-spec-spec?
;                                 (do
;                                   ;(debug "Retrieving spec from the spec-specs DB")
;                                   (ds/pull @spec-specs-ds '[*] [:entity/ns spec-ns]))
;                                 (do
;                                   ;(debug "Retrieving spec from the table-specs DB")
;                                   (ds/pull @table-specs-ds '[*] [:entity/ns spec-ns])))
;
;               ;_               (debug "spec-entity: " spec-entity)
;
;               spec-attrs      (:entity/attrs spec-entity)
;               ;_               (debug "spec-attrs" spec-attrs)
;
;               ;spec-attrs-gql  (get-spec-attrs-gql spec-attrs)
;               ;_               (when (seq spec-attrs-gql)
;               ;                  (debug "spec-attrs-gql" spec-attrs-gql))
;
;               sortField       (:sortField args)
;               sortOrder       (:sortOrder args)
;
;               doSort?         (and sortField sortOrder)
;               splitSort       (when sortField
;                                 (str/split sortField #"\."))
;               nestedSort?     (when splitSort
;                                 (> (count splitSort) 1))
;               parentSortField (when nestedSort?
;                                 (first splitSort))
;               sortFieldNSKW   (when doSort?
;                                 (if nestedSort?
;                                   (nskw table parentSortField)
;                                   (nskw table sortField)))
;               childSortField  (when nestedSort?
;                                 (second splitSort))
;               nestedSortAttr  (when nestedSort?
;                                 (get-spec-attr-by-name parentSortField spec-attrs))
;
;               nestedSortTable (when (and nestedSortAttr (= (:attr/type nestedSortAttr) :ref))
;                                 (ds/q '[:find ?name .
;                                         :in $ ?e
;                                         :where [?e :entity/name ?name]]
;                                   @table-specs-ds (:db/id (:attr/ref nestedSortAttr))))
;
;               childSortNSKW   (when nestedSort?
;                                 (nskw nestedSortTable childSortField))
;
;               recursive-q     (fn [table fields spec-attrs]
;                                 (vec
;                                   (concat
;                                     [:db/id]
;                                     (for [field fields]
;                                       (let [field-as   (nskw table field)
;                                             ;; get the field's attr spec
;                                             field-attr (get-spec-attr-by-key field-as spec-attrs)
;                                             ;; is it a :ref type?
;                                             is-ref?    (= :ref (:attr/type field-attr))
;                                             ;; if it's a :ref, then we need to recurse
;                                             hmm        (ds/pull @table-specs-ds '[*] [:entity/ns spec-ns])]
;                                         (cond
;                                           (and nestedSort? (= field-as sortFieldNSKW))
;                                           {field-as [:db/id childSortNSKW]}
;                                           :else
;                                           field-as))))))
;               query           (vec
;                                 (concat
;                                   [:db/id]
;                                   (for [field fields]
;                                     (let [field-as (nskw table field)]
;                                       ;(let [field-as [(nskw table field) :as (keyword field)]]
;                                       (if (and nestedSort? (= field parentSortField))
;                                         {field-as [:db/id childSortNSKW]}
;                                         field-as)))))
;
;               ;sortData        (when nestedSort?
;               ;                  {:parent sortFieldNSKW
;               ;                   :child  childSortNSKW})
;               ;sels            (parse-selections 0 sortData selections)
;               ;_               (debug "SELS: " sels)
;
;
;               ;rename-map  (into {}
;               ;              (for [field fields]
;               ;                [(nskw table field) (keyword field)]))
;
;               q               {:find  ['[(pull ?e qu) ...]]
;                                :in    '[$ qu]
;                                :where []
;                                :args  [(db) query]}
;
;               id-arg          (get args :id)
;
;               limit           (or
;                                 (get args :limit)
;                                 (get args :perPage))
;
;               page            (get args :page)
;               offset          (or
;                                 (get args :offset)
;                                 (when page
;                                   (* page limit)))
;
;
;
;               filter          (:filter args)
;
;               ids             (when filter
;                                 (:ids filter))
;
;               ;_              (debug "IDS 1" ids)
;               ids             (when (seq ids)
;                                 (vec (for [id ids]
;                                        (read-string id))))
;               ;_              (debug "IDS 2" ids)
;
;
;               ;;; add filters
;               fn-add-filters  (fn [q table filter]
;                                 (if (seq filter)
;                                   (reduce-kv
;                                     (fn [q filt-k filt-v]
;                                       (let [filt-s (symbol (str "?" (name filt-k)))]
;                                         (-> q
;                                           (update :in conj filt-s)
;                                           (update :where conj ['?e (nskw table filt-k) filt-s])
;                                           (update :args conj filt-v))))
;                                     q filter)
;                                   q))
;
;               ;;; if there are more filters, add 'em
;               filter          (apply dissoc filter [:ids :id :q])
;               _               (debug "FILTERS" filter)
;               q               (fn-add-filters q table filter)
;
;               ;;; if there are more args, add 'em
;               args            (apply dissoc args
;                                 [:id :offset :limit :filter :page
;                                  :perPage :sortField :sortOrder])
;               _               (debug "ARGS" args)
;               q               (fn-add-filters q table args)
;
;               first-nsk       (if is-spec-spec?
;                                 (get-identity-key spec-specs-ds spec-ns)
;                                 (get-identity-key table-specs-ds spec-ns))
;
;               q               (cond-> q
;
;                                 (seq ids)
;                                 (->
;                                   (update :in conj '[?e ...])
;                                   (update :args conj ids))
;
;                                 ;;; if this is a single, only return the entity
;                                 id-arg
;                                 (->
;                                   (update :in conj '?e)
;                                   (update :args conj (Long/parseLong id-arg)))
;
;                                 ;; last one, in case there are no conditions, get all records
;                                 (empty? (:where q))
;                                 (->
;                                   (update :where conj ['?e first-nsk]))
;
;                                 ;;; FIXME optimize the main query by running without the pull first, saving metadata and then running pull on the list of EIDs
;                                 meta?
;                                 (->
;                                   (assoc :find ['[?e ...]])))
;
;               q               (remap-query q)
;               _               (debug "Datomic query: " q)
;
;
;
;
;               query-ds        (when is-spec-spec?
;                                 (vec
;                                   (concat
;                                     [:db/id]
;                                     (for [field fields]
;                                       (nskw table field)))))
;               _               (when query-ds
;                                 (debug "DS query" query-ds))
;
;               q-map           (:query q)
;               ds-find         (when is-spec-spec?
;                                 (let [ds-find [:find (first (:find q-map))]
;                                       ds-in   (:in q-map)
;                                       ds-find (apply conj ds-find :in ds-in)
;                                       ds-find (apply conj ds-find :where (:where q-map))]
;                                   ds-find))
;
;               ds-args         (when is-spec-spec?
;                                 (->> (drop 2 (:args q))
;                                   (cons query-ds)
;                                   (cons @table-specs-ds)))
;
;               ;_ (debug "DS" ds-find ds-args)
;               ds-results      (when is-spec-spec?
;                                 (try
;                                   (apply (partial ds/q ds-find) ds-args)
;                                   (catch Exception ex (debug "DS failed" (.getMessage ex)))))
;               ;_             (debug "DS RESULTS" ds-results)
;
;
;               d-results       (if (and is-spec-spec? (seq ds-results))
;                                 ds-results
;                                 (try
;                                   (d/query q)
;                                   (catch Exception ex
;                                     (do
;                                       (warn "RiverDB Resolver query failed" (.getMessage ex))
;                                       (debug "Failed Query" q)))))
;
;
;
;               _               (debug "QUERY FINISHED")
;
;               ;d-results   (try
;               ;              (d/query q)
;               ;              (catch Exception ex (warn "RiverDB Resolver query failed" (.getMessage ex))))
;
;               _               (debug (count d-results) "RESULTS\n\n" (take 3 d-results) "\n\n")
;
;               compare-fn      (cond
;                                 (= sortOrder "ASC")
;                                 #(compare %1 %2)
;                                 (= sortOrder "DESC")
;                                 #(compare %2 %1))
;
;
;
;               ;; for example :entity.ns/samplingdevice has a DeviceType ref to :entity.ns/samplingdevicelookup and we want
;               ;; to know about that for sorting on a referenced entity's name field, etc
;               ;ref-keys        ()
;
;               ;nestedKey-fn    (fn [m]
;               ;                  (get-in m [(keyword parentSortField) (keyword childSortField)]))
;
;               d-results       (if doSort?
;                                 (let [sort-fn (if nestedSort?
;                                                 #(get-in % [sortFieldNSKW childSortNSKW])
;                                                 sortFieldNSKW)]
;                                   (sort-by sort-fn compare-fn d-results))
;                                 d-results)
;
;               _               (when doSort?
;                                 (debug "SORTED RESULTS" (take 5 d-results)))
;
;               d-results       (cond
;                                 meta?
;                                 {:count (count (or ids d-results))}
;
;                                 (or limit offset)
;                                 (do
;                                   (debug "STARTING LIMIT")
;                                   (let [res (limit-fn limit offset d-results)]
;                                     (debug "DONE LIMITING")
;                                     res))
;
;                                 ;; return exact list that was requested
;                                 (seq ids)
;                                 (do
;                                   (debug "Returning Exact Results for IDS" ids)
;                                   (let [id-map (into {} (for [res d-results]
;                                                           [(:db/id res) res]))
;                                         _      (debug "ID-MAP keys" (keys id-map))]
;                                     (vec
;                                       (for [id ids]
;                                         (get id-map id)))))
;
;                                 id-arg
;                                 (first d-results)
;
;                                 :else
;                                 d-results)
;
;               _               (debug "Pre-walk D-Results" (if (map? d-results) d-results (take 1 d-results)))
;               ;;; remove namespaces
;               d-results       (walk-response d-results)]
;
;
;           (debug "FINAL RESULTS" (if (map? d-results) d-results (take 5 d-results)))
;
;           ;d-results   (if id-field?
;           ;              (for [r d-results]
;           ;                (assoc r :id (:db/id r)))
;           ;              d-results)
;
;           ;_             (debug "Datomic first result: " (first d-results))]
;
;           ;[{:id "17592186045425",
;           ;        :CommonID "\u000bA",
;           ;        :SamplingDeviceID 128,
;           ;        :DeviceType {:db/id 17592186046954}}])
;           d-results)
;
;         (catch Exception ex (do (debug "FAILED QUERY" (.getStackTrace ex)) [])))))))


(defn resolve-rimdb2
  ([spec-ns]
   ;(debug "RESOLVE RIMDB DEFAULT" spec-ns)
   (resolve-rimdb2 spec-ns :query-single))
  ([spec-ns query-type]
   (fn [context args value]
     (if (and (= query-type :query-single) (not (:id args)))
       (do
         (debug "requested a single result with no ID arg?  Returning NIL")
         nil)
       (try
         (let [selection       (:com.walmartlabs.lacinia/selection context)
               ;_               (pprint selection)
               selections      (vec (:selections selection))
               ;_ (pprint selections)

               pull-q          (parse-sels selections)
               pull-q          (walk-ids pull-q)

               _               (pprint pull-q)

               fields          (map :field selections)
               tk              (get selection :field)

               table           (name tk)
               table           (if (str/starts-with? table "all")
                                 (subs table 3 (dec (count table)))
                                 table)

               meta?           (str/starts-with? table "_all")

               table           (if meta?
                                 (subs table 4 (- (count table) 5))
                                 table)

               _               (debug "RESOLVE v2!" (if meta? "META" "TABLE") (or query-type "NIL") spec-ns "table:" table "args:" args "fields:" fields)

               fields          (vec (->> fields
                                      (map name)
                                      (remove #(or (= % "id") (str/starts-with? % "rk_") (str/starts-with? % "__") (= % "count")))))


               spec-entity     (do
                                 (ds/pull @table-specs-ds '[*] [:entity/ns spec-ns]))

               ;_               (debug "spec-entity: " spec-entity)

               spec-attrs      (:entity/attrs spec-entity)
               ;_               (debug "spec-attrs" spec-attrs)


               sortField       (:sortField args)
               sortOrder       (:sortOrder args)

               doSort?         (and sortField sortOrder)
               splitSort       (when sortField
                                 (str/split sortField #"\."))
               nestedSort?     (when splitSort
                                 (> (count splitSort) 1))
               parentSortField (when nestedSort?
                                 (first splitSort))
               sortFieldNSKW   (when doSort?
                                 (if nestedSort?
                                   (nskw table parentSortField)
                                   (nskw table sortField)))
               childSortField  (when nestedSort?
                                 (second splitSort))
               nestedSortAttr  (when nestedSort?
                                 (get-spec-attr-by-name parentSortField spec-attrs))

               nestedSortTable (when (and nestedSortAttr (= (:attr/type nestedSortAttr) :ref))
                                 (ds/q '[:find ?name .
                                         :in $ ?e
                                         :where [?e :entity/name ?name]]
                                   @table-specs-ds (:db/id (:attr/ref nestedSortAttr))))

               childSortNSKW   (when nestedSort?
                                 (nskw nestedSortTable childSortField))

               recursive-q     (fn [table fields spec-attrs]
                                 (vec
                                   (concat
                                     [:db/id]
                                     (for [field fields]
                                       (let [field-as   (nskw table field)
                                             ;; get the field's attr spec
                                             field-attr (get-spec-attr-by-key field-as spec-attrs)
                                             ;; is it a :ref type?
                                             is-ref?    (= :ref (:attr/type field-attr))
                                             ;; if it's a :ref, then we need to recurse
                                             hmm        (ds/pull @table-specs-ds '[*] [:entity/ns spec-ns])]
                                         (cond
                                           (and nestedSort? (= field-as sortFieldNSKW))
                                           {field-as [:db/id childSortNSKW]}
                                           :else
                                           field-as))))))
               query           (vec
                                 (concat
                                   [:db/id]
                                   (for [field fields]
                                     (let [field-as (nskw table field)]
                                       ;(let [field-as [(nskw table field) :as (keyword field)]]
                                       (if (and nestedSort? (= field parentSortField))
                                         {field-as [:db/id childSortNSKW]}
                                         field-as)))))

               ;sortData        (when nestedSort?
               ;                  {:parent sortFieldNSKW
               ;                   :child  childSortNSKW})
               ;sels            (parse-selections 0 sortData selections)
               ;_               (debug "SELS: " sels)


               ;rename-map  (into {}
               ;              (for [field fields]
               ;                [(nskw table field) (keyword field)]))

               q               {:find  ['[(pull ?e qu) ...]]
                                :in    '[$ qu]
                                :where []
                                :args  [(db) pull-q]}

               id-arg          (get args :id)

               limit           (or
                                 (get args :limit)
                                 (get args :perPage))

               page            (get args :page)
               offset          (or
                                 (get args :offset)
                                 (when page
                                   (* page limit)))



               filter          (:filter args)

               ids             (when filter
                                 (:ids filter))

               ;_              (debug "IDS 1" ids)
               ids             (when (seq ids)
                                 (vec (for [id ids]
                                        (read-string id))))
               ;_              (debug "IDS 2" ids)


               ;;; add filters
               fn-add-filters  (fn [q table filter]
                                 (if (seq filter)
                                   (reduce-kv
                                     (fn [q filt-k filt-v]
                                       (let [filt-s (symbol (str "?" (name filt-k)))]
                                         (-> q
                                           (update :in conj filt-s)
                                           (update :where conj ['?e (nskw table filt-k) filt-s])
                                           (update :args conj filt-v))))
                                     q filter)
                                   q))

               ;;; if there are more filters, add 'em
               filter          (apply dissoc filter [:ids :id :q])
               _               (debug "FILTERS" filter)
               q               (fn-add-filters q table filter)

               ;;; if there are more args, add 'em
               args            (apply dissoc args
                                 [:id :offset :limit :filter :page
                                  :perPage :sortField :sortOrder])
               _               (debug "ARGS" args)
               q               (fn-add-filters q table args)

               first-nsk       (get-identity-key table-specs-ds spec-ns)

               q               (cond-> q

                                 (seq ids)
                                 (->
                                   (update :in conj '[?e ...])
                                   (update :args conj ids))

                                 ;;; if this is a single, only return the entity
                                 id-arg
                                 (->
                                   (update :in conj '?e)
                                   (update :args conj (Long/parseLong id-arg)))

                                 ;; last one, in case there are no conditions, get all records
                                 (empty? (:where q))
                                 (->
                                   (update :where conj ['?e first-nsk]))

                                 ;;; FIXME optimize the main query by running without the pull first, saving metadata and then running pull on the list of EIDs
                                 meta?
                                 (->
                                   (assoc :find ['[?e ...]])))

               q               (remap-query q)
               _               (debug "Datomic query: " q)

               d-results       (try
                                 (d/query q)
                                 (catch Exception ex
                                   (do
                                     (warn "RiverDB Resolver query failed" (.getMessage ex))
                                     (debug "Failed Query" q))))


               _               (debug "QUERY FINISHED")

               ;d-results   (try
               ;              (d/query q)
               ;              (catch Exception ex (warn "RiverDB Resolver query failed" (.getMessage ex))))

               _               (debug (count d-results) "RESULTS\n\n" (take 3 d-results) "\n\n")

               compare-fn      (cond
                                 (= sortOrder "ASC")
                                 #(compare %1 %2)
                                 (= sortOrder "DESC")
                                 #(compare %2 %1))



               ;; for example :entity.ns/samplingdevice has a DeviceType ref to :entity.ns/samplingdevicelookup and we want
               ;; to know about that for sorting on a referenced entity's name field, etc
               ;ref-keys        ()

               ;nestedKey-fn    (fn [m]
               ;                  (get-in m [(keyword parentSortField) (keyword childSortField)]))

               d-results       (if doSort?
                                 (let [sort-fn (if nestedSort?
                                                 #(get-in % [sortFieldNSKW childSortNSKW])
                                                 sortFieldNSKW)]
                                   (sort-by sort-fn compare-fn d-results))
                                 d-results)

               _               (when doSort?
                                 (debug "SORTED RESULTS" (take 5 d-results)))

               d-results       (cond
                                 meta?
                                 {:count (count (or ids d-results))}

                                 (or limit offset)
                                 (do
                                   (debug "STARTING LIMIT")
                                   (let [res (limit-fn limit offset d-results)]
                                     (debug "DONE LIMITING")
                                     res))

                                 ;; return exact list that was requested
                                 (seq ids)
                                 (do
                                   (debug "Returning Exact Results for IDS" ids)
                                   (let [id-map (into {} (for [res d-results]
                                                           [(:db/id res) res]))
                                         _      (debug "ID-MAP keys" (keys id-map))]
                                     (vec
                                       (for [id ids]
                                         (get id-map id)))))

                                 id-arg
                                 (first d-results)

                                 :else
                                 d-results)

               _               (debug "Pre-walk D-Results" (if (map? d-results) d-results (take 1 d-results)))
               ;;; remove namespaces
               d-results       (walk-response d-results)]


           (debug "FINAL RESULTS" (if (map? d-results) d-results (take 5 d-results)))

           ;d-results   (if id-field?
           ;              (for [r d-results]
           ;                (assoc r :id (:db/id r)))
           ;              d-results)

           ;_             (debug "Datomic first result: " (first d-results))]

           ;[{:id "17592186045425",
           ;        :CommonID "\u000bA",
           ;        :SamplingDeviceID 128,
           ;        :DeviceType {:db/id 17592186046954}}])
           d-results)

         (catch Exception ex (do (debug "FAILED QUERY" (.getStackTrace ex)) [])))))))


(defn resolve-rimdb-fk [spec-ns]
  (fn [context args value]
    ;(debug "FK Resolver" spec-ns)
    (debug "RESOLVE FK" spec-ns args value)
    (let [selection (:com.walmartlabs.lacinia/selection context)
          fk        (:field selection)
          fk-val    (get value fk)]
      (if (nil? fk-val)
        (do
          (debug "FK val for" fk "is NIL"))
        (let [fk-ns          (get-in selection [:field-definition :type-name])
              pk-ns          (-> selection :selections first :field-definition :type-name)
              this-attr      (nskw fk-ns fk)

              _              (debug "fk:" fk " fk-ns:" fk-ns " pk-ns:" pk-ns " fk-val:" fk-val)

              is-spec-spec?  (some? (spec-spec-keys spec-ns))
              ;_              (debug "is-spec-spec?" is-spec-spec?)

              ;; if we're accessing table schemas, use specs-ds, if we're wanting the spec's specs themselve, use spec-specs-ds

              spec-entity    (if is-spec-spec?
                               (ds/pull @spec-specs-ds '[*] [:entity/ns spec-ns])
                               (ds/pull @table-specs-ds '[*] [:entity/ns spec-ns]))

              ;_              (debug "spec-entity: " spec-entity)

              spec-attrs     (:entity/attrs spec-entity)
              ;spec-attrs-gql (into {}
              ;                 (for [sattr spec-attrs]
              ;                   (when-let [gql (:attr/gql sattr)]
              ;                     [(:attr/key sattr) gql])))
              ;_ (debug "spec-attrs" spec-attrs)
              ;_ (debug "spec-attrs-gql" spec-attrs-gql)

              this-attr-spec (first (filter #(= this-attr (:attr/key %)) spec-attrs))
              many?          (= :many (:attr/cardinality this-attr-spec))

              _              (debug "this-attr:" this-attr "isMany?" many? " this-attr-spec:" this-attr-spec)

              ;_ (debug "pk-ns: " pk-ns ", pk-field: " pk-field)

              tree           (executor/selections-tree context)
              keys           (keys tree)

              ;attr-spec    (ds/pull @specs-ds '[*] [:entity/ns spec-ns])
              ;_              (debug "spec-entity: " spec-entity)

              ;_              (debug "tree keys" keys)

              id?            (some #{"id"} (map name keys))
              keys           (if id?
                               (remove #(= (name %) "id") keys)
                               keys)

              ;keys           (map
              ;                 (fn [key]
              ;                   (if (= (name key) (nskw pk-ns key) )))
              ;                 keys)

              ;db-keys   (vec
              ;            (concat
              ;              [:db/id]
              ;              (for [k keys]
              ;                [k :as (keyword (name k))])))

              db-keys        (vec
                               (concat
                                 [:db/id]
                                 keys))

              _              (debug "ID? " id? "KEYS: " keys ", DB-KEYS" db-keys)

              result         (if (seq keys)
                               (if is-spec-spec?
                                 (get value fk)
                                 (if many?
                                   []
                                   (walk-response (d/pull (db) db-keys (Long/parseLong (:id fk-val))))))
                               fk-val)]
          ;result         (assoc result :id (str (:id result)))]

          ;_              (debug "FK result" result)]

          ;result         (if (and id? (not many?))
          ;                 (assoc result :id (:db/id result))
          ;                 result)]


          (debug "FK RESULT" result)
          (debug "id type" (type (:id result)))

          result)))))

;
;
;(defn resolve-rimdb-rk [spec-ns]
;  (fn [context args value]
;    (debug "RESOLVE RK" spec-ns args value)
;    (let [selection     (:com.walmartlabs.lacinia/selection context)
;          ;_             (debug "RK SELECTION:")
;          ;_ (pprint selection)
;
;          pk-table      (get-in selection [:field-definition :type-name])
;          ;pk-table-name (name pk-table)
;          fk-table      (-> selection :selections first :field-definition :type-name)
;          fk-table-name (name fk-table)
;          fk-info       (get-in @state [:tables pk-table :rev-keys fk-table])
;          pk-field      (:pkcolumn_name fk-info)
;          ;pk-field-val  (get value (keyword pk-field))
;          pk-field-val  (:db/id value)
;          fk-field      (:fkcolumn_name fk-info)
;          fk            (nskw fk-table fk-field)
;
;          ;_             (debug "fk-table: " fk-table ", fk-field: " fk-field ", value: " value)
;          ;_             (debug "pk-table: " pk-table ", pk-field: " pk-field ", pk-field-val: " pk-field-val)
;
;          tree          (executor/selections-tree context)
;          keys          (keys tree)
;
;          id?           (some #{"id"} (map name keys))
;          keys          (if id?
;                          (remove #(= (name %) "id") keys)
;                          keys)
;
;          db-keys       (vec
;                          (concat
;                            [:db/id]
;                            (for [k keys]
;                              [k :as (keyword (name k))])))
;
;          ;_             (debug "KEYS" keys ", DB KEYS" db-keys)
;
;          rez           (d/q '[:find [(pull ?e qu) ...]
;                               :in $ ?id ?fk qu
;                               :where [?e ?fk ?id]] (db) pk-field-val fk db-keys)
;
;          rez           (if id?
;                          (for [r rez]
;                            (assoc r :id (:db/id r)))
;                          rez)]
;
;      ;_             (debug "DB RESULTS" rez)
;
;
;
;      rez)))


(defn resolve-spec [spec-ns]
  (debug "RESOLVE SPEC" spec-ns)
  (resolve-rimdb2 spec-ns :query-single))


(defn resolve-connection [context args value]
  (let [selection-keys (keys (executor/selections-tree context))]
    (pprint (str "SELECTION KEYS: " selection-keys))))

(defn resolve-specs [ctx args value]
  (debug "RESOLVE SPECS" args))

(defn resolve-db-specs [ctx args value]
  (debug "RESOLVE SPECS" args))

;(defn resolve-list-meta [ent]
;  (fn [ctx args value]
;    (debug "LIST META" ent args)
;    (let [q          (d/q '[:find (count ?e) .
;                            :where [?e :samplingdevice/CommonID]] (db))
;          selection  (:com.walmartlabs.lacinia/selection ctx)
;          selections (vec (:selections selection))
;          fields     (map :field selections)
;          tk         (get selection :field)
;          id-field?  (some #{:id} fields)
;          fields     (if id-field?
;                       (remove #(= % :id) fields)
;                       fields)
;
;          table      (name tk)
;          table      (if (str/starts-with? table "_all")
;                       (subs table 4 (- (count table) 5))
;                       table)
;
;          fields     (vec (->> fields
;                            (map name)
;                            (remove #(or (str/starts-with? % "rk_") (str/starts-with? % "__")))))
;
;          _          (debug "RESOLVE LIST META: " ent ", table: " table ", args: " args ", fields: " fields)]
;
;      {:count 1})))

(defn resolve-list-query [ent]
  (fn [ctx args value]
    ;(debug "LIST QUERY" ent args)
    ((resolve-rimdb2 ent :query-list) ctx args value)))

(defn resolve-list-meta [ent]
  (fn [ctx args value]
    ;(debug "LIST QUERY" ent args)
    ((resolve-rimdb2 ent :query-meta) ctx args value)))