(ns riverdb.api.resolvers
  (:require
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.server.api-middleware :as fmw]
    [com.fulcrologic.guardrails.core :refer [>defn => | ?]]
    [com.fulcrologic.rad.type-support.date-time :as datetime]
    [cljc.java-time.zone-id]
    [com.rpl.specter :as sp]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [clojure.edn :as edn]
    [clojure.spec.alpha :as s]
    [clojure.string :as st]
    [clojure.walk :as walk]
    [datomic.api :as d]
    [datascript.core :as ds]
    [riverdb.state :refer [db cx]]
    [riverdb.api.tac-report :as tac]
    [riverdb.api.qc-report :as qc]
    [riverdb.graphql.schema :refer [table-specs-ds specs-sorted specs-map]]
    [taoensso.timbre :as log :refer [debug]]
    [thosmos.util :as tu :refer [walk-modify-k-vals limit-fn]]))


(defn add-conditions
  "add :where conditions like:
  [(> ?SiteVisitDate #inst \"2019-11-03T00:00:00.000-00:00\")]
  [(> ?SiteVisitDate #inst \"2020-11-03T00:00:00.000-00:00\")]
  from a map like:
  {:> #inst \"2019-11-03T00:00:00.000-00:00\"
   :< #inst \"2020-11-03T00:00:00.000-00:00\"}"
  [find arg conditions]
  (reduce-kv
    (fn [find k v]
      (cond
        (= k :>)
        (conj find [(list '> arg v)])
        (= k :<)
        (conj find [(list '< arg v)])
        (= k :contains)
        (let [argl (symbol (str arg "LC"))
              v    (clojure.string/lower-case v)]
          (-> find
            (conj [(list 'clojure.string/lower-case arg) argl])
            (conj [`(clojure.string/includes? ~argl ~v)])))))
    find conditions))

(defn add-filters [arg find-v filter-m]
  (try
    (reduce-kv
      (fn [find k v]
        (let [ent-k        (keyword "entity.ns" (namespace k))
              ent-nm       (name k)
              attr-spec    (get-in specs-map [ent-k :entity/attrs k])
              attr-type    (:attr/type attr-spec)
              type-ref?    (= attr-type :ref)
              type-inst?   (= attr-type :instant)
              type-string? (= attr-type :string)

              val-string?  (string? v)
              val-map?     (map? v)
              val-nil?     (nil? v)
              val-vec?     (vector? v)

              arg?         (cond
                             (and type-ref? val-map?)
                             (symbol (str "?" (str (namespace (ffirst v)) "_" (name (ffirst v)))))
                             (and type-inst? val-map?)
                             (symbol (str "?" ent-nm))
                             type-string?
                             (symbol (str "?" ent-nm)))
              find         (cond
                             (and type-ref? val-map?)
                             (add-filters arg? find v) ;; if it's a nested field, then recurse
                             (and type-inst? val-map?)
                             (add-conditions find arg? v) ;; if it's got where conditions, add the bindings
                             type-string?
                             (add-conditions find arg? {:contains v})
                             :else
                             find)
              v            (cond
                             type-ref?
                             (cond
                               val-string?
                               (Long/parseLong v)
                               val-map?
                               arg?
                               :else
                               v)
                             type-inst?
                             (cond
                               val-map?
                               arg? ;; if it's a map of conditions, return the arg that they bind to
                               :else
                               v)
                             type-string?
                             arg?
                             :else
                             v)]
          (cond
            val-nil?
            find
            :else
            (conj find [arg k v]))))
      find-v filter-m)
    (catch Exception ex (log/error "ADD-FILTERS ERROR" ex))))

(defn compare-fn [sortOrder]
  (cond
    (= sortOrder :desc)
    #(compare %2 %1)
    :else
    #(compare %1 %2)))

(defn swap-derived-keys [query]
  (debug "PREWALK QUERY")
  (clojure.walk/prewalk-demo query))

(defn lookup-resolve [env input]
  (debug "LOOKUP RESOLVER" input)
  (try
    (let [params        (-> env :ast :params)
          query         (:query env)

          ids?          (:ids params)
          ids           (when ids?
                          (mapv #(Long/parseLong %) ids?))

          ent-key?      (-> env :ast :key)
          ent-name?     (name ent-key?)
          meta?         (st/ends-with? ent-name? "-meta")
          meta-key      (when meta?
                          ent-key?)

          ent-name      (if meta?
                          (subs ent-name? 0 (st/index-of ent-name? "-meta"))
                          ent-name?)
          ent-key       (if meta?
                          (keyword ent-name)
                          ent-key?)

          ent-type      (tu/ns-kw "entity.ns" (last (st/split ent-name #"\.")))
          spec          (get specs-map ent-type)
          ;; if we have a spec for a different lookup key, use it
          lookup?       (get spec :entity/lookup)
          lookup-key    (cond
                          lookup?
                          lookup?
                          ids?
                          ids
                          :else
                          ent-type)

          _             (log/debug "List Resolver -> params" params "ent-key" ent-key "ent-type" ent-type "lookup-key" lookup-key)
          ;meta?         (some #{:org.riverdb.meta/query-count} query)

          ;; if it's a meta query, we don't need any query fields
          query         (if meta?
                          [:db/id]
                          query)

          ;query         (swap-derived-keys query)

          find          (if ids?
                          '[:find [(pull ?e qu) ...]
                            :in $ qu [?e ...]
                            :where]
                          '[:find [(pull ?e qu) ...]
                            :in $ qu ?typ
                            :where])


          ;;; If either from- or to- date were passed, join the `sitevisit` entity
          ;;; and bind its `SiteVisitDate` attribute to the `?date` variable.
          ;(or fromDate toDate)
          ;(update :where conj
          ;  '[?sv :sitevisit/SiteVisitDate ?date])
          ;
          ;;; If the `fromDate` filter was passed, do the following:
          ;;; 1. add a parameter placeholder into the query;
          ;;; 2. add an actual value to the arguments;
          ;;; 3. add a proper condition against `?date` variable
          ;;; (remember, it was bound above).
          ;fromDate
          ;(->
          ;  (update :in conj '?fromDate)
          ;  (update :args conj (jt/java-date fromDate))
          ;  (update :where conj
          ;    '[(> ?date ?fromDate)]))
          ;
          ;;; similar to ?fromDate
          ;toDate
          ;(->
          ;  (update :in conj '?toDate)
          ;  (update :args conj (jt/java-date toDate))
          ;  (update :where conj
          ;    '[(< ?date ?toDate)]))

          _             (debug "ADDING FILTERS ...")
          ;; add the filter conditions first.
          ;; TODO we can probably skip this if we have IDs, but leaving for now
          filter        (:filter params)
          find          (if filter
                          (add-filters '?e find filter)
                          find)

          _             (log/debug "POST FILTERS" find)

          ;; add the type condition because filters are *probably* more restrictive than the type?
          find          (cond
                          ids? ; if we have IDs, we don't need any more conditions
                          find

                          lookup?
                          (conj find `[~'?e ~lookup-key])

                          :else
                          (conj find '[?e :riverdb.entity/ns ?typ]))

          _             (log/debug "FINAL FIND" find)

          results       (d/q find
                          (db) query lookup-key)
          results-count (count results)
          _             (log/debug "\nLIST RESULTS for" lookup-key "\nCOUNT" results-count "\nFIND" find "\nQUERY" query "\nFIRST RESULTS" (first results))]

      ;; if it's a metadata query, branch before doing all the limits and sorts
      (if meta?
        ;; we return one record with the metadata fields
        (let [result {:org.riverdb.meta/query-count results-count}
              final  {meta-key result}]
          (debug "FINAL META RESULT" final)
          final)


        ;; if it's a regular query, then let's do the whole thing
        (let [limit           (get params :limit 15)
              offset          (get params :offset 0)
              sortField       (:sortField params)
              sortOrder       (:sortOrder params)

              nestedSort?     (map? sortField)
              childSortField  (when nestedSort?
                                (-> sortField first val))
              parentSortField (when nestedSort?
                                (ffirst sortField))

              _               (when nestedSort?
                                (debug "childSortField" childSortField "parentSortField" parentSortField))

              ids             nil

              results         (if sortField
                                (let [sort-fn (if nestedSort?
                                                #(get-in % [parentSortField childSortField])
                                                sortField)]
                                  (sort-by sort-fn (compare-fn sortOrder) results))
                                results)

              results         (cond->> results

                                ; return exact list that was requested
                                (seq ids)
                                (fn [results]
                                  (log/debug "Returning Exact Results for IDS" ids)
                                  (let [id-map (into {} (for [res results]
                                                          [(:db/id res) res]))
                                        _      (log/debug "ID-MAP keys" (keys id-map))]
                                    (vec
                                      (for [id ids]
                                        (get id-map id)))))

                                (and limit (> limit 0))
                                (limit-fn limit offset))


              results         (walk-modify-k-vals results :db/id str)]
          (log/debug "FINAL RESULTS" (first results))
          {ent-key results})))

    (catch Exception ex (log/error "RESOLVER ERROR" ex))))



(def lookup-resolvers
  (mapv
    (fn [spec]
      (let [{:entity/keys [ns attrs]} spec
            nm  (name ns)
            aks (mapv :attr/key attrs)]
        {::pc/sym     (symbol nm)
         ::pc/output  [{(keyword (str "org.riverdb.db." nm))
                        (into [:db/id :riverdb.entity/ns :org.riverdb.meta/query-count] aks)}]
         ::pc/resolve lookup-resolve}))
    specs-sorted))

(def meta-resolvers
  (mapv
    (fn [spec]
      (let [{:entity/keys [ns attrs]} spec
            nm  (name ns)
            aks (mapv :attr/key attrs)]
        {::pc/sym     (symbol (str nm "-meta"))
         ::pc/output  [{(keyword (str "org.riverdb.db." nm "-meta"))
                        (into [:db/id :riverdb.entity/ns :org.riverdb.meta/query-count] aks)}]
         ::pc/resolve lookup-resolve}))
    specs-sorted))




(defn id-resolve-factory [gid-key]
  (fn [env input]
    (log/debug "ID PULL RESOLVER" gid-key input)
    (try
      (let [query   (-> env ::p/parent-query)
            id-val  (get input gid-key)
            _       (log/debug "Lookup Resolver Key" gid-key "Input" input "QUERY" query)

            tp      (type id-val)
            id-val? (cond
                      (= tp java.lang.String)
                      (try
                        (Long/parseLong id-val)
                        (catch NumberFormatException _ nil))
                      (= tp java.lang.Long)
                      id-val)

            result  (when id-val?
                      (d/pull (db) query id-val?))
            result  (when result
                      (walk-modify-k-vals result :db/id str))]
        (log/debug "RESULT" result)
        result)
      (catch Exception ex (log/error ex)))))

(defn get-reverse-keys [ent-k specs-sorted]
  (->> (sp/select [sp/ALL :entity/attrs sp/ALL #(= (:attr/refKey %) ent-k) :attr/key] specs-sorted)
    (mapv #(keyword (namespace %) (str "_" (name %))))))

(def id-resolvers
  (vec
    (for [spec specs-sorted]
      (let [{:entity/keys [ns attrs]} spec
            aks   (mapv :attr/key attrs)
            rks   (get-reverse-keys ns specs-sorted)
            nm    (name ns)
            gid-k (keyword (str "org.riverdb.db." nm) "gid")]
        {::pc/sym       (symbol (str nm "GID"))
         ::pc/input     #{gid-k}
         ::pc/output    (-> [:db/id :riverdb.entity/ns] (into aks) (into rks))
         ::pc/resolve   (id-resolve-factory gid-k)
         ::pc/transform pc/transform-batch-resolver}))))


(defn uuid-resolve-factory [uuid-key]
  (fn [env input]
    (log/debug "UUID PULL RESOLVER" uuid-key input)
    (try
      (let [query   (-> env ::p/parent-query)
            _       (log/debug "Lookup Resolver Key" uuid-key "Input" input "QUERY" query)
            id-val? (try
                      (get input uuid-key)
                      (catch IllegalArgumentException _ nil))
            result  (when id-val?
                      (d/pull (db) query [uuid-key id-val?]))]
        (log/debug "RESULT" result)
        result)
      (catch Exception ex (log/error ex)))))


(def uuid-resolvers
  (vec
    (for [spec specs-sorted]
      (let [{:entity/keys [ns attrs]} spec
            aks    (mapv :attr/key attrs)
            nm     (name ns)
            uuid-k (keyword nm "uuid")]
        {::pc/sym       (symbol (str nm "UUID"))
         ::pc/input     #{uuid-k}
         ::pc/output    (into [:db/id :riverdb.entity/ns] aks)
         ::pc/resolve   (uuid-resolve-factory uuid-k)
         ::pc/transform pc/transform-batch-resolver}))))




(defresolver agency-project-years [env _]
  {::pc/output [:agency-project-years]}
  (let [params (-> env :ast :params)]
    (log/info "QUERY :agency-project-years" params)
    {:agency-project-years (tac/get-agency-project-years (db) (:agencies params))}))


(defresolver db-idents [env _]
  {::pc/output [{:db-idents [:db/ident :db/id]}]}
  {:db-idents
   (mapv
     (fn [[dbid dbident]]
       {:db/id (str dbid) :db/ident dbident})
     (d/q '[:find ?e ?id
            :where
            [(> ?e 1000)]
            [?e :db/ident ?id]]
       (db)))})

(defresolver db-ident [env {:db/keys [ident]}]
  {::pc/output [:db/id :db/ident :riverdb.entity/ns]
   ::pc/input  #{:db/ident}}
  (let [result (d/q '[:find (pull ?e [:db/id :db/ident :riverdb.entity/ns]) .
                      :in $ ?id
                      :where
                      [(> ?e 1000)]
                      [?e :db/ident ?id]]
                 (db) ident)]
    ;(debug "DB-IDENT QUERY" ident result)
    result))

(defresolver meta-entities [env _]
  {::pc/output [{:meta-entities (mapv :attr/key (get-in (domain-spec.core/spec-specs) [0 :entity/attrs]))}]}
  {:meta-entities specs-sorted})

(defn get-global-attrs []
  (edn/read-string (slurp "resources/global-attrs.edn")))

(defresolver meta-global-attrs [env _]
  {::pc/output [{:meta-global-attrs (mapv :attr/key (get-in (domain-spec.core/spec-specs) [1 :entity/attrs]))}]}
  {:meta-global-attrs (get-global-attrs)})

(defresolver globals [env _]
  {::pc/output [{:globals [:db-idents :meta-entities :meta-global-attrs]}]})

;(defresolver all-sitevisit-years [env _]
;  {::pc/output [:all-sitevisit-years]}
;  (let [params (-> env :ast :params)]
;    (do
;      (log/info "QUERY :all-sitevisit-years" params)
;      {:all-sitevisit-years (tac/get-sitevisit-years (db) (:project params))})))

(defresolver dataviz-data [env _]
  {::pc/output [:dataviz-data]}
  (let [params (-> env :ast :params)
        result (tac/get-dataviz-data (db) (:agency params) (:project params) (:year params))]
    (log/info "QUERY :dataviz-data" params (count result))
    {:dataviz-data result}))

(defresolver tac-report-data [env _]
  {::pc/output [:tac-report-data]}
  (let [params (-> env :ast :params)]
    (log/info "QUERY :tac-report-data" params)
    {:tac-report-data (binding [datetime/*current-timezone* (cljc.java-time.zone-id/of "America/Los_Angeles")]
                               (if (:csv params)
                                 (tac/get-annual-report-csv (:csv params))
                                 (qc/get-qc-report (db) (:agency params) (:project params) (:year params))))}))

(def agency-query [:db/id :agencylookup/uuid :agencylookup/AgencyCode :agencylookup/AgencyDescr])

(>defn all-agencies
  "Returns a sequence of ..."
  [db]
  [any? => (s/coll-of map? :kind vector?)]
  (d/q '[:find [(pull ?e qu) ...]
         :in $ qu
         :where [?e :agencylookup/Active true]]
    db agency-query))

(defresolver agencylookup-resolver [{:keys [db] :as env} input]
  {;;GIVEN nothing key, gets all agency records
   ::pc/output [{:all-agencies agency-query}]}
  (let [params (-> env :ast :params)]
    (log/debug "Agency Lookup Input" input "Params?" params)
    {:all-agencies (all-agencies db)}))

(def people-query [:person/uuid :person/Name :person/IsStaff {:person/Agency [:db/id :agencylookup/uuid :agencylookup/AgencyCode]}])

(defn all-people-ids
  "Returns a sequence of entities for all of the active persons in the system"
  [db query-params]
  (let [find '[:find [(pull ?e qu) ...]
               :in $ qu
               :where]
        find (add-filters '?e find query-params)
        find (conj find '[?e :person/uuid])]
    (log/debug "ALL PEOPLE PARAMS" query-params find)
    (d/q find db people-query)))

(defresolver all-people-resolver [{:keys [db query-params] :as env} input]
  {;;GIVEN nothing (e.g. this is usable as a root query)
   ::pc/output [{:all-people people-query}]}
  {:all-people (all-people-ids db query-params)})


(defresolver index-explorer [env _]
  {::pc/input  #{:com.wsscode.pathom.viz.index-explorer/id}
   ::pc/output [:com.wsscode.pathom.viz.index-explorer/index]}
  {:com.wsscode.pathom.viz.index-explorer/index
   (get env ::pc/indexes)})


(defresolver test-meta [_ _]
  {::pc/output [:test-meta]}
  {:test-meta (with-meta {:some :data} {:some :meta})})

(def resolvers [globals db-ident db-idents meta-entities meta-global-attrs agencylookup-resolver agency-project-years tac-report-data dataviz-data meta-resolvers index-explorer test-meta all-people-resolver uuid-resolvers])