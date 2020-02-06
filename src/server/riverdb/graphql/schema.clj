(ns riverdb.graphql.schema
  (:require [clojure.tools.logging :as log :refer [debug info warn error]]
            [clojure.pprint :refer [pprint]]
            [riverdb.state :as st :refer [db cx]]
            [riverdb.db :refer [limit-fn remap-query]]
            [riverdb.station]
            [clojure.edn :as edn]
            [thosmos.util :as tu]
            [datascript.core :as ds]
            [domain-spec.core :as dspec]))

;; specs for the specs themselves
(def spec-specs (dspec/spec-specs))

;; the datascript DB to hold our spec-specs
(def spec-specs-ds (dspec/new-specs-ds))

(def spec-specs-ds-tx (ds/transact! spec-specs-ds spec-specs))


;; specs for our RiverDB entity types
(def specs-edn (edn/read-string (slurp "resources/specs.edn")))

;; the datascript DB to hold our specs
(def table-specs-ds (dspec/new-specs-ds))

;; concat and load our specs into the DS
;(def specs-ds-tx (ds/transact! specs-ds (vec (concat specs-edn spec-specs))))
;(def specs-ds-tx (ds/transact! specs-ds spec-specs))
(def specs-ds-tx (ds/transact! table-specs-ds specs-edn))

(def specs-sorted (dspec/get-specs table-specs-ds))

(def specs-map (dspec/get-spec-map table-specs-ds))

;; load the spec-specs themselves (so we can provide a means to query them via GQL)


;; spec-ds fns

(def spec-spec-keys #{:entity.ns/entity :entity.ns/attr})

(defn get-ref-key [specs-ds attr-spec]
  (ds/q '[:find ?ns .
          :in $ ?e
          :where [?e :entity/ns ?ns]]
    @specs-ds
    (get-in attr-spec [:attr/ref :db/id])))

(defn get-spec [specs-ds entity-ns]
  (ds/q '[:find (pull ?e [*]) .
          :in $ ?e-ns
          :where [?e :entity/ns ?e-ns]]
    @specs-ds entity-ns))

(defn gql-name [attr]
  (if-let [gql-nm (:attr/gql attr)]
    gql-nm
    (name (:attr/key attr))))




;; input object fns

(defn strip-resolvers [{:keys [fields] :as object}]
  (let [fields (into {}
                 (->> (for [[k v] fields]
                        (let [v (into {}
                                  (remove (fn [[k v]] (= k :resolve)) v))]
                          [k v]))
                   (remove (fn [[k v]] (clojure.string/starts-with? (name k) "rk_")))))]
    (assoc object :fields fields)))
;
;{:db/id         98,
; :entity/attrs  [{:db/id            251,
;                  :attr/cardinality :one,
;                  :attr/key         :sample/EventType,
;                  :attr/name        "EventType",
;                  :attr/position    4,
;                  :attr/ref         #:db{:id 88},
;                  :attr/strlen      20,
;                  :attr/type        :ref}
;                 {:db/id            252,
;                  :attr/cardinality :many,
;                  :attr/component   true,
;                  :attr/doc         "Results aquired in the field",
;                  :attr/key         :sample/FieldResults,
;                  :attr/name        "FieldResults",
;                  :attr/ref         #:db{:id 111},
;                  :attr/type        :ref}
;                 {:db/id            253,
;                  :attr/cardinality :many,
;                  :attr/component   true,
;                  :attr/doc         "Field Observations",
;                  :attr/key         :sample/FieldObsResults,
;                  :attr/name        "FieldObsResults",
;                  :attr/ref         #:db{:id 92},
;                  :attr/type        :ref}
;                 {:db/id            254,
;                  :attr/cardinality :many,
;                  :attr/component   true,
;                  :attr/doc         "Results acquired from a lab",
;                  :attr/key         :sample/LabResults,
;                  :attr/name        "LabResults",
;                  :attr/ref         #:db{:id 148},
;                  :attr/type        :ref}
;                 {:db/id            255,
;                  :attr/cardinality :one,
;                  :attr/key         :sample/QCCheck,
;                  :attr/name        "QCCheck",
;                  :attr/position    12,
;                  :attr/type        :boolean},,,],
; :entity/name   "sample",
; :entity/ns     :entity.ns/sample,
; :entity/pks    [:sample/SampleRowID],
; :entity/prKeys [:sample/SampleRowID]}

;; table generation

(def spec-resolvers
  {:query :resolve-spec-query
   :fk    :resolve-spec-fk
   :rk    :resolve-spec-rk})

(def table-resolvers
  {:query :resolve-rimdb
   :fk    :resolve-rimdb-fk
   :rk    :resolve-rimdb-rk})


(defn generate-schemas
  [specs-ds resolvers]
  (debug "Generating schemas ...")
  (let [specs             (dspec/get-specs specs-ds)
        objects           (into {}
                            (for [spec specs]
                              (let [
                                    ;fks (:foreign-keys table)
                                    ;rks (:rev-keys table)
                                    spec-ns (:entity/ns spec)
                                    nm      (name spec-ns)
                                    _       (debug "OBJECT" nm)
                                    k       (keyword nm)
                                    pks     (:entity/pks spec)
                                    attrs   (:entity/attrs spec)

                                    rks     (ds/q '[:find [(pull ?e [*]) ...]
                                                    :in $ ?spec-ns
                                                    :where
                                                    [?rf :entity/ns ?spec-ns]
                                                    [?e :attr/ref ?rf]]
                                              @specs-ds spec-ns)

                                    v       {:fields
                                             (into {}
                                               (concat
                                                 {:id {:type 'ID}}
                                                 (for [{:keys [attr/key attr/type attr/ref
                                                               attr/component attr/cardinality
                                                               attr/gql attr/doc]
                                                        :as   attr} attrs]
                                                   (let [col-k-nm  (gql-name attr)
                                                         col-k     (keyword col-k-nm)

                                                         ;; is this a ref?
                                                         ref-type? (= type :ref)
                                                         col-value (cond

                                                                     ;; ref?
                                                                     ref-type?
                                                                     (let [col-type (if
                                                                                      ref
                                                                                      (keyword (name (get-ref-key specs-ds attr)))
                                                                                      'ID)
                                                                           col-val  {:type    col-type}]
                                                                                     ;:resolve [(:fk resolvers) spec-ns]}]
                                                                       col-val)

                                                                     ;; all others
                                                                     :else
                                                                     (case type
                                                                       :string
                                                                       {:type 'String}
                                                                       :boolean
                                                                       {:type 'Boolean}
                                                                       :long
                                                                       {:type 'Int}
                                                                       :double
                                                                       {:type 'Float}
                                                                       {:type 'String}))

                                                         col-type  (:type col-value)

                                                         col-value (cond-> col-value

                                                                     (= cardinality :many)
                                                                     (assoc :type `(~'list ~col-type))

                                                                     doc
                                                                     (assoc :description doc))]

                                                     [col-k col-value]))))}]




                                ;(vec (for [[fk fkey] (:foreign-keys table)]
                                ;       (let [k (keyword (str "fk_" (:fkcolumn_name fkey)))
                                ;             v {:type (keyword (:pktable_name fkey))
                                ;                :resolve :resolve-rimdb-fk
                                ;                ;:args (into {} [[(:pkcolumn_name fk) {:type 'String}]])
                                ;                }]
                                ;         [k v])))

                                ;;; reverse lookups!
                                ;(for [attr rks]
                                ;  (let [attr-key (:attr/key attr)
                                ;        nm       (namespace attr-key)
                                ;        k        (keyword (str "rk_" nm))
                                ;        tk       (keyword nm)
                                ;        args     {:limit {:type 'Int}}
                                ;        v        {:type    `(~'list ~tk)
                                ;                  :resolve [(:rk resolvers) spec-ns]}]
                                ;    [k v]))))}]




                                [k v])))


        convert-ref-to-ID (fn [object ref-key specs-map]
                            (let [ref-spec (ref-key specs-map)]
                              (reduce-kv
                                (fn [object field-k {:keys [type] :as field-map}]
                                  (let [attr-key (tu/ns-kw (name ref-key) field-k)
                                        spec     (get-in ref-spec [:entity/attrs attr-key])
                                        ref?     (:attr/ref spec)
                                        comp?    (:attr/component spec)
                                        many?    (= :many (:attr/cardinality spec))
                                        ref-type (if comp?
                                                   (keyword (str "input_"
                                                              (if many?
                                                                (name (second type))
                                                                (name type))))
                                                   'ID)
                                        ref-type (if many?
                                                   `(~'list ~ref-type)
                                                   ref-type)]

                                    (if ref?
                                      (assoc-in object [:fields field-k :type] ref-type)
                                      object)))
                                object (:fields object))))


        input-objects     (let [specs-map (dspec/get-spec-map specs-ds)]
                            (reduce-kv
                              (fn [inputs ent-ns ent]
                                (reduce-kv
                                  (fn [inputs attr-key {:keys [attr/ref attr/component] :as attr}]
                                    (if (and ref component)
                                      (let [ref-key  (get-ref-key specs-ds attr)
                                            ref-nm   (name ref-key)
                                            input-kw (keyword (str "input_" ref-nm))
                                            ref-type (keyword ref-nm)]
                                        ;; use it if we already have it, otherwise, make it
                                        (if-let [i-object (get inputs input-kw)]
                                          i-object
                                          (assoc inputs input-kw (->
                                                                   (get objects ref-type)
                                                                   strip-resolvers
                                                                   (convert-ref-to-ID ref-key specs-map)))))
                                      inputs))
                                  inputs (get ent :entity/attrs)))
                              {} specs-map))

        queries           (reduce
                            (fn [queries spec]
                              (let [spec-ns   (:entity/ns spec)
                                    nm        (name spec-ns)
                                    ;_         (debug "QUERY" nm)

                                    nm-cap    (clojure.string/capitalize nm)
                                    k         (keyword nm)

                                    lst       `(~'list ~k)

                                    v         {:type    k
                                               :resolve [(:query resolvers) spec-ns]
                                               :args    {:id     {:type 'ID}
                                                         :limit  {:type 'Int :default 1}
                                                         :offset {:type 'Int :default 0}}}


                                    attrs     (:entity/attrs spec)
                                    bools?    (filterv #(= (:attr/type %) :boolean) attrs)

                                    ;;; add boolean args
                                    v         (if (seq bools?)
                                                (reduce
                                                  (fn [v boo]
                                                    (let [boo-k (keyword (gql-name boo))
                                                          doc   (:attr/doc boo)]
                                                      (assoc-in v [:args boo-k]
                                                        (if doc
                                                          {:type        'Boolean
                                                           :description doc}
                                                          {:type 'Boolean}))))
                                                  v bools?)
                                                v)

                                    list-args {:page      {:type 'Int}
                                               :perPage   {:type 'Int}
                                               :sortField {:type 'String}
                                               :sortOrder {:type 'String}
                                               :filter    {:type :post_filter}}]


                                (-> queries
                                  (assoc k v)
                                  (assoc (keyword (str "all" nm "s"))
                                         {:type lst :resolve [:resolve-list-query spec-ns]
                                          :args list-args})
                                  (assoc (keyword (str "_all" nm "sMeta"))
                                         {:type :list_meta :resolve [:resolve-list-meta spec-ns]
                                          :args list-args}))))


                            {} specs)



        mutations         (reduce
                            (fn [mutations spec]
                              (let [nm        (name (:entity/ns spec))
                                    ;_         (debug "GENERATING MUTATION" nm)

                                    nm-cap    (clojure.string/capitalize nm)
                                    nm-create (str "create" nm)
                                    nm-update (str "update" nm)
                                    nm-delete (str "delete" nm)

                                    v         {:type (keyword nm)
                                               :args {:id {:type 'ID}}}

                                    attrs     (:entity/attrs spec)

                                    argz      (into {}
                                                (for [{:keys [attr/key attr/type attr/ref attr/doc
                                                              attr/component attr/cardinality]
                                                       :as   attr} attrs]
                                                  (let [attr-nm  (gql-name attr)
                                                        k        (keyword attr-nm)
                                                        ref-type (if
                                                                   (and ref component)
                                                                   (keyword (str "input_" (name (get-ref-key specs-ds attr))))
                                                                   :db_ref)
                                                        ;ref-type 'ID
                                                        many?    (= :many cardinality)
                                                        val-type (case type
                                                                   :boolean
                                                                   'Boolean
                                                                   :string
                                                                   'String
                                                                   :double
                                                                   'Float
                                                                   :bigdec
                                                                   'Float
                                                                   :long
                                                                   'Int
                                                                   :instant
                                                                   'String
                                                                   :ref
                                                                   (if many?
                                                                     `(~'list ~ref-type)
                                                                     ref-type)
                                                                   'String)
                                                        val      (if doc
                                                                   {:type        val-type
                                                                    :description doc}
                                                                   {:type val-type})]
                                                    [k val])))]

                                (-> mutations
                                  (assoc (keyword nm-create) (-> v
                                                               (assoc :resolve [:resolve-entity-create nm])
                                                               (assoc :args argz)))
                                  (assoc (keyword nm-update) (-> v
                                                               (assoc :resolve [:resolve-entity-update nm])
                                                               (update :args merge argz)))
                                  (assoc (keyword nm-delete) (assoc v :resolve [:resolve-entity-delete nm])))))
                            {} specs)]


    {:input-objects input-objects
     :objects       objects
     :queries       queries
     :mutations     mutations}))



;(defn schema-spec->gql-schema-entity [specs-ds]
;  )
;
;(defn schema-spec->gql-schema-attr [specs-ds]
;  )

(defn static-schema []
  (debug "Loading static schemas")
  {
   ;;; FIXME generate filter objects for each table
   :input-objects {:post_filter {:fields {:id     {:type 'ID}
                                          :ids    {:type '(list ID)}
                                          :q      {:type 'String}
                                          :Active {:type 'Boolean}}}
                   :db_ref      {:fields {:id {:type 'ID}}}
                   :auth_creds  {:fields {:email    {:type 'String}
                                          :password {:type 'String}}}}

   :objects       {

                   ;:entity_spec (schema-spec->gql-schema-entity specs-ds)
                   ;:attr_spec (schema-spec->gql-schema-attr specs-ds)

                   ;:entity_spec {:fields {:entity_ns {:type 'String}
                   ;                       :entity_name {:type 'String}
                   ;                       :entity_doc {:type 'String}
                   ;                       :entity_prn_dash_fn {:type 'String}
                   ;                       :entity_prn_dash_keys {:type 'String}}}

                   :list_meta          {:fields {:count {:type 'Int}}}
                   :fldrslt            {:fields {:count         {:type 'Int}
                                                 :matrix        {:type 'String}
                                                 :analyte       {:type 'String}
                                                 :unit          {:type 'String}
                                                 :vals          {:type '(list Float)}
                                                 :is_valid      {:type 'Boolean}
                                                 :max           {:type 'Float}
                                                 :min           {:type 'Float}
                                                 :range         {:type 'Float}
                                                 :stddev        {:type 'Float}
                                                 :mean          {:type 'Float}
                                                 :prec          {:type 'Float}
                                                 :is_too_low    {:type 'Boolean}
                                                 :is_too_high   {:type 'Boolean}
                                                 :is_too_few    {:type 'Boolean}
                                                 :is_exceedance {:type 'Boolean}}}
                   :station            {:fields {:id           {:type 'ID}
                                                 :station_id   {:type 'Int}
                                                 :station_code {:type 'String}
                                                 :river_fork   {:type 'String}
                                                 :trib_group   {:type 'String}}}
                   :site_visit         {:description "summary info about a sitevisit"
                                        :fields      {:id       {:type 'ID}
                                                      :date     {:type 'String}
                                                      :notes    {:type 'String}
                                                      :valid    {:type 'Boolean}
                                                      :station  {:type :station}
                                                      :resultsv {:type '(list :fldrslt)
                                                                 :description "summary info about a field result"}}}


                   :agencydetail       {:fields {:AgencyCode     {:type 'String}
                                                 :AgencyDescr    {:type 'String}
                                                 :Email          {:type 'String}
                                                 :WebAddress     {:type 'String}
                                                 :Telephone      {:type 'String}
                                                 :PrimaryContact {:type 'String}
                                                 :Active         {:type 'Boolean}}}


                   :stationdetail      {:fields {:id              {:type 'ID}
                                                 :StationID       {:type 'Int}
                                                 :Agency          {:type    :agencydetail
                                                                   :resolve :resolve-agency-ref}
                                                 :Description     {:type 'String}
                                                 :Active          {:type 'Boolean}
                                                 :StationName     {:type 'String}
                                                 :LocalWaterbody  {:type 'String}
                                                 :ForkTribGroup   {:type 'String}
                                                 :RiverFork       {:type 'String}
                                                 :NHDWaterbody    {:type 'String}
                                                 :HydrologicUnit  {:type 'String}
                                                 :StreamSubsystem {:type 'String}
                                                 :TargetLat       {:type 'Float}
                                                 :TargetLong      {:type 'Float}
                                                 :County          {:type 'String}
                                                 :LocalWatershed  {:type 'String}
                                                 :StationCode     {:type 'String}}}
                   :role               {:fields {:name {:type 'String}
                                                 :uuid {:type 'ID}
                                                 :type {:type 'String}
                                                 :doc  {:type 'String}}}
                   ;:agency {:type 'String}


                   :user               {:fields {:name  {:type 'String}
                                                 :email {:type 'String}
                                                 :uuid  {:type 'ID}
                                                 :roles {:type '(list :role)}}}

                   :auth_result        {:fields {:success {:type 'Boolean}
                                                 :msg     {:type 'String}
                                                 :token   {:type 'String}
                                                 :user    {:type :user}}}

                   :unauth_result      {:fields {:success {:type 'Boolean}}}

                   :user_result        {:fields {:success {:type 'Boolean}
                                                 :msg     {:type 'String}
                                                 :user    {:type :user}}}

                   :basic_result       {:fields {:success {:type 'Boolean}
                                                 :msg     {:type 'String}}}

                   :edit_entity_result {:fields {:success      {:type 'Boolean}
                                                 :msg          {:type 'String}
                                                 :updated_keys {:type '(list String)}}}}

   :queries       {

                   :hello
                   ; String is quoted here; in EDN the quotation is not required
                   {:type        'String
                    :resolve     :resolve-hello
                    :description "This is the hello world of fields"
                    :args        {:foo {:type 'String}}}

                   :sitevisits
                   {:type        '(list :site_visit)
                    :resolve     :resolve-sitevisit
                    :description "List of sitevisit summaries"
                    :args        {:fromYear    {:type 'Int}
                                  :toYear      {:type 'Int}
                                  :station     {:type 'Int}
                                  :stationCode {:type 'String}
                                  :agency      {:type 'String}
                                  :projectID   {:type 'String}
                                  :limit       {:type    'Int
                                                :default 10}
                                  :offset      {:type    'Int
                                                :default 0}}}

                   :stations
                   {:type        '(list :stationdetail)
                    :resolve     :resolve-stationdetail
                    :description "Station details"
                    :args        {:agency {:type 'String}
                                  :active {:type 'Boolean}
                                  :limit  {:type    'Int
                                           :default 10}
                                  :offset {:type    'Int
                                           :default 0}}}

                   ;:line_snap
                   ;{:type        :line_snap
                   ; :resolve     :resolve-line-snap
                   ; :description "calc the nearest stream and point on its line"
                   ; :args        {:lat {:type 'Float}
                   ;               :lon {:type 'Float}}}

                   :current_user
                   {:type        :user
                    :resolve     :resolve-current-user
                    :description "the current user record, if any"}}


   :mutations     {

                   :auth
                   {:type        :auth_result
                    :resolve     :resolve-auth
                    :description "authenticate!"
                    :args        {:email    {:type 'String}
                                  :password {:type 'String}}}

                   :unauth
                   {:type        :unauth_result
                    :resolve     :resolve-unauth
                    :description "unauthenticate and clear the cookie"}


                   :change_current_name
                   {:type        :user_result
                    :resolve     :resolve-change-user-name
                    :description "Change the name of the current user"
                    :args        {:name {:type 'String}}}

                   :set_password
                   {:type        :user_result
                    :resolve     :resolve-set-password
                    :description "Change the password of the current user"
                    :args        {:password {:type 'String}}}}})





(defn gen-table-schemas []
  (debug "Generating table schemas from specs")
  (generate-schemas table-specs-ds table-resolvers))

(defn gen-spec-schemas []
  (debug "Generating spec schemas from specs")
  (generate-schemas spec-specs-ds spec-resolvers))

(defn merge-tree [a b]
  (if (and (map? a) (map? b))
    (merge-with #(merge-tree %1 %2) a b)
    b))

(defn merge-schemas []
  (debug "MERGE SCHEMAS")
  (reduce merge-tree [(gen-table-schemas)
                      (gen-spec-schemas)
                      (static-schema)]))

(defn save-specs [filename specs]
  (let [specs (if (vector? specs)
                specs
                (dspec/get-specs specs))]
    (tu/spitpp "resources/specs-save.edn" (dspec/sort-specs specs))))

(defn save-schemas [schemas]
  (debug "Saving schemas to resources/schemas.edn")
  (tu/spitpp "resources/schemas.edn" schemas))

(defn save-all-schemas []
  (debug "Merging and saving all schemas ...")
  (let [schemas (merge-schemas)]
    (save-schemas schemas)))

;(defn load-all-schemas []
;  (debug "Loading resources/schemas.edn ...")
;  (try
;    (edn/read-string (slurp "resources/schemas.edn"))
;    (catch Exception ex (error "No resources/schemas.edn!" ex))))

;(defn load-all-schemas [])
;(merge-schemas))


(comment
  (spit "resources/specs.edn" (with-out-str (pprint (dspec/sort-specs specs-edn))))

  ;; specs for specs

  (def spec-specs (dspec/spec-specs))
  (def spec-spec-ds (specs-ds/new-specs-ds))
  (ds/transact! spec-spec-ds spec-specs)
  (specs-ds/get-specs spec-spec-ds)

  ;;update cached schemas
  (save-all-schemas))