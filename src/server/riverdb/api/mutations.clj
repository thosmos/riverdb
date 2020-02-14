(ns riverdb.api.mutations
  (:require
    [clojure.string :as str]
    [clojure.set :as set]
    [clojure.walk :as walk]
    [clojure.pprint :refer [pprint]]
    [cognitect.transit :as transit]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.rpl.specter :as sp]
    [com.wsscode.pathom.core :as p]
    [datomic.api :as d]
    [edn-query-language.core :as eql]
    [riverdb.graphql.schema :refer [table-specs-ds specs-sorted specs-map]]
    [riverdb.state :refer [db cx]]
    [taoensso.timbre :as log :refer [debug]]
    [thosmos.util :as tu :refer [walk-modify-k-vals limit-fn]]))

;;;;  SERVER

;;[(riverdb.api.mutations/save-entity
;  {:ident [:org.riverdb.db.parameter/gid "17592186244690"],
;   :diff
;   {[:org.riverdb.db.parameter/gid "17592186244690"]
;    {:parameter/low "0",
;     :parameter/active true,
;     :parameter/samplingdevicelookupRef {:db/id "17592186046962"},
;     :parameter/high "10",
;     :parameter/precisionCode "{:sdf \"sdf\"}"}}})]

;;[(riverdb.api.mutations/save-entity
;  {:ident [:org.riverdb.db.projectslookup/gid "17592186178014"],
;   :diff
;   {[:org.riverdb.db.projectslookup/gid "17592186178014"]
;    {:projectslookup/Name "Monthly Water Qualit"},
;    [:org.riverdb.db.parameter/gid "17592186244690"]
;    {:parameter/name "SpecificConductivity"}}})]

;;[(riverdb.api.mutations/save-entity
;  {:ident [:org.riverdb.db.projectslookup/gid "17592186178014"],
;   :diff
;   {[:org.riverdb.db.projectslookup/gid "17592186178014"]
;    {:projectslookup/Name "Monthly Wa"},
;    [:org.riverdb.db.parameter/gid "17592186244692"]
;    {:parameter/name "pH ",
;     :parameter/samplingdevicelookupRef {:db/id "17592186046953"}}}})]

(defn parse-long [str]
  (try (Long/parseLong str)
       (catch Exception _ nil)))

(defn parse-double [str]
  (try (Double/parseDouble str)
       (catch Exception _ nil)))

(defn parse-bigdec [str]
  (try (BigDecimal. str)
       (catch Exception _ nil)))

(defn gid-ident->db-id [ident]
  (let [ident-val (second ident)]
    (if (tempid/tempid? ident-val)
      (-> ident-val :id)
      (parse-long ident-val))))

(defn convert-ids [body]
  (->>
    (sp/transform (sp/walker tempid/tempid?) #(-> % :id str (subs 0 20)) body)
    (sp/transform [first sp/ALL sp/LAST map? #(number? (parse-long (:db/id %)))] #(identity {:db/id (parse-long (:db/id %))}))
    (sp/transform [first sp/ALL sp/LAST vector? sp/FIRST #(number? (parse-long (:db/id %)))] #(identity {:db/id (parse-long (:db/id %))}))
    (sp/transform [sp/ALL sp/LAST vector? #(clojure.string/starts-with? (str (first %)) ":org.riverdb")]
      #(identity (or (parse-long (second %)) (second %))))
    (sp/transform [sp/ALL sp/LAST vector? sp/ALL vector? #(clojure.string/starts-with? (str (first %)) ":org.riverdb")]
      #(identity (or (parse-long (second %)) (second %))))
    (sp/select [sp/ALL #(some? (second %))])
    (into {})))

;(sp/transform [first sp/ALL sp/LAST map? #(number? (parse-long (:db/id %)))] #(identity {:db/id (parse-long (:db/id %))}))
;(sp/transform [first sp/ALL sp/LAST vector? sp/FIRST #(number? (parse-long (:db/id %)))] #(identity {:db/id (parse-long (:db/id %))}))

;(defn gid-ident->db-id [ident]
;  (let [ident-val (second ident)]
;    (if (clojure.string/starts-with? ident-val "t")
;      ident-val
;      (parse-long ident-val))))

(defn gid-ident->ent-ns [ident]
  (let [ns     (namespace (first ident))
        strs   (clojure.string/split ns #"\.")
        table  (last strs)
        ent-ns (keyword "entity.ns" table)]
    ent-ns))



;(defn parse-diff [ent-ns diff]
;  (let [ent-spec (get specs-map ent-ns)
;        attrs    (get ent-spec :entity/attrs)]
;    (into {} (for [[k v] diff]
;               (let [attr-spec    (get attrs k)
;                     attr-type    (:attr/type attr-spec)
;                     spec-ref?    (= :ref attr-type)
;                     spec-bigdec? (= :bigdec attr-type)
;                     ;; get the diff's :after value
;                     val          (:after v)
;                     _            (debug "DIFF" k "VAL" val)
;                     val-map?     (map? val)
;                     val-ref?     (and
;                                    val-map?
;                                    spec-ref?
;                                    (= (count val) 1)
;                                    (some? (:db/id val)))]
;                 (case attr-type
;                   :ref
;                   [k (parse-long (:db/id val))]
;                   [k val]))))))

(defn replace-ref-types
  "dbc   the database to query
   refs  a set of keywords that ref datomic entities, which you want to access directly
          (rather than retrieving the entity id)
   m     map returned from datomic pull containing the entity IDs you want to deref"
  [db refs arg]
  (walk/postwalk
    (fn [arg]
      (cond
        (and (map? arg) (some #(contains? refs %) (keys arg)))
        (reduce
          (fn [acc ref-k]
            (cond
              (and (get acc ref-k) (not (vector? (get acc ref-k))))
              (update acc ref-k (comp :db/ident (partial d/entity db) :db/id))
              (and (get acc ref-k) (vector? (get acc ref-k)))
              (update acc ref-k #(mapv (comp :db/ident (partial d/entity db) :db/id) %))
              :else acc))
          arg
          refs)
        :else arg))
    arg))

(defn pull-*
  "Will either call d/pull or d/pull-many depending on if the input is
  sequential or not.

  Optionally takes in a transform-fn, applies to individual result(s)."
  ([db pattern ident-keywords eid-or-eids]
   (->> (if (and (not (eql/ident? eid-or-eids)) (sequential? eid-or-eids))
          (d/pull-many db pattern eid-or-eids)
          (d/pull db pattern eid-or-eids))
     (replace-ref-types db ident-keywords)))
  ([db pattern ident-keywords eid-or-eids transform-fn]
   (let [result (pull-* db pattern ident-keywords eid-or-eids)]
     (if (sequential? result)
       (mapv transform-fn result)
       (transform-fn result)))))

(defn get-by-ids [db pk ids ident-keywords desired-output]
  ;; TODO: Should use consistent DB for atomicity
  (let [eids (mapv (fn [id] [pk id]) ids)]
    (pull-* db desired-output ident-keywords eids)))

(defn ref->ident
  "Sometimes references on the client are actual idents and sometimes they are
  nested maps, this function attempts to return an ident regardless."
  [x]
  (if (eql/ident? x)
    (if-let [long-str (parse-long (second x))]
      long-str
      (second x))
    (parse-long (:db/id x))))

(defn key->attribute [attr-k]
  (get-in specs-map [(keyword "entity.ns" (namespace attr-k)) :entity/attrs attr-k]))

(defn add-ident [db eid rel ident] (let [ref-val (or (:db/id (d/entity db ident)) (str (second ident)))] [:db/add eid rel ref-val]))

(defn delta->datomic-txn
  "Takes in a normalized form delta, usually from client, and turns in
  into a Datomic transaction for the given schema (returns empty txn if there is nothing on the delta for that schema)."
  [delta]
  ;; TASK: test mapcat on nil (nothing on schema)
  (vec
    (mapcat (fn [[[id-k id] entity-diff]]
              (mapcat (fn [[k diff]]
                        (let [id (if-let [long-id (parse-long id)] long-id (str id))
                              {:keys [before after]} diff
                              [before after] (if (= k :fieldresult/Result)
                                               [(double before) (double after)]
                                               [before after])]
                          (cond
                            (= k :db/id)
                            []

                            (ref->ident after)
                            [[:db/add id k (ref->ident after)]]

                            (and (sequential? after) (every? ref->ident after))
                            (let [before   (into #{}
                                             (comp (map ref->ident) (remove nil?))
                                             before)
                                  after    (into #{}
                                             (comp (map ref->ident) (remove nil?))
                                             after)
                                  retracts (set/difference before after)
                                  adds     (set/difference after before)
                                  eid      id]
                              (vec
                                (concat
                                  (for [r retracts] [:db/retract eid k r])
                                  (for [a adds] [:db/add eid k a]))))

                            (and (sequential? after) (every? keyword? after))
                            (let [before   (into #{}
                                             (comp (remove nil?))
                                             before)
                                  after    (into #{}
                                             (comp (remove nil?))
                                             after)
                                  retracts (set/difference before after)
                                  adds     (set/difference after before)
                                  eid      id]
                              (vec
                                (concat
                                  (for [r retracts] [:db/retract eid k r])
                                  (for [a adds] [:db/add eid k a]))))

                            ;; Assume field is optional and omit
                            (and (nil? before) (nil? after)) []

                            :else (if (nil? after)
                                    (if (ref->ident before)
                                      [[:db/retract id k (ref->ident before)]]
                                      [[:db/retract id k before]])
                                    [[:db/add id k after]]))))
                entity-diff))
      delta)))

(defn save-form!
  "Do all of the possible Datomic operations for the given form delta "
  [form-delta]
  (let [tempids    (sp/select (sp/walker tempid/tempid?) form-delta)
        fulcro-tempid->real-id
                   (into {} (map (fn [t] [t (-> t :id str (subs 20))]) tempids))
        form-delta (sp/transform (sp/walker tempid/tempid?) fulcro-tempid->real-id form-delta)
        txn        (delta->datomic-txn form-delta)]
    ;(log/debug "Saving form delta" form-delta)
    (log/debug "tempids\n" (with-out-str (pprint fulcro-tempid->real-id)))
    txn))
    ;fulcro-tempid->real-id))


(defn diff->txd [ent-ns db-id diff create]
  (debug "DIFF->TXD" ent-ns db-id diff create)
  (let [ent-spec (get specs-map ent-ns)
        attrs    (get ent-spec :entity/attrs)]
    (vec
      (remove nil?
        (for [[k v] diff]
          (let [{:keys [before after]} v
                attr-spec (get attrs k)
                attr-type (:attr/type attr-spec)
                ;_ (debug "DIFF->TXD" k attr-type v)
                ref?      (= :ref attr-type)
                ;; get the diff's :after value
                val       (if ref?
                            (parse-long (:db/id after))
                            after)
                retract?  (nil? val)
                op-key    (if retract? :db/retract :db/add)
                val       (if (and retract? (not (nil? before)))
                            (if ref?
                              (parse-long (:db/id before))
                              before)
                            val)]

            (cond
              ;; Assume field is optional and omit
              (and (nil? before) (nil? after))
              nil
              :else
              [op-key db-id k val])))))))



;(defn new-diff->txd [ent-ns db-id diff]
;  (let [ent-spec (get specs-map ent-ns)
;        attrs    (get ent-spec :entity/attrs)]
;    (debug "NEW DIFF->TXD" diff)))

(defn diff->txds [diff]
  (vec
    (for [[idt dif] diff]
      (let [ent-ns (gid-ident->ent-ns idt)
            db-id  (gid-ident->db-id idt)
            dif    (convert-ids dif)]
        ;txd    (diff->txd ent-ns db-id dif create)
        ;txd    (remove nil? txd)]
        (debug "SAVE-ENTITY TXD" db-id ent-ns dif)
        dif))))

(defn save-entity* [env ident diff create]
  (debug "MUTATION!!! save-entity" "IDENT" ident "DIFF" diff (comment "ENV" (keys env) "SESSION" (:session (:ring/request env))))
  (let [session-valid? (get-in env [:ring/request :session :session/valid?])
        user-email     (when session-valid?
                         (get-in env [:ring/request :session :account/auth :user :user/email]))
        karl?          (= user-email "karl@yubariver.org")
        tempids        (sp/select (sp/walker tempid/tempid?) diff)
        tmp-map        (into {} (map (fn [t] [t (-> t :id str (subs 0 20))]) tempids))
        _              (debug "PRE TEMPIDS" tempids tmp-map)]
    (if karl?
      (let [result (try
                     (let [form-delta (sp/transform (sp/walker tempid/tempid?) tmp-map diff)
                           txds        (delta->datomic-txn form-delta)
                           _    (debug "SAVE-ENTITY TXDS" txds)
                           tx   (d/transact (cx) txds)]
                       (debug "TX" @tx)
                       @tx)
                     (catch Exception ex (do
                                           (println "ERROR: " ex)
                                           {:error (.toString ex)})))]
        (if (:error result)
          result
          (let [db-tmps (:tempids result)
                tempids (into {}
                          (for [[t s] tmp-map]
                            [t (str (get db-tmps s))]))
                _       (debug "POST TEMPIDS" tempids)]
            (debug "SAVE-ENTITY RESULT" result)
            {:tempids tempids})))

      {:error "Unauthorized"})))

(pc/defmutation save-entity [env {:keys [ident diff create]}]
  {::pc/sym    `save-entity
   ::pc/params [:ident :diff :create]
   ::pc/output [:error :tempids]}
  (save-entity* env ident diff create))

(def mutations [save-entity])

;(defn commit-new [user-db [table id] entity]
;  (log/info "Committing new " table entity)
;  (case table
;    :user/by-id (users/add-user user-db entity)
;    {}))

;(defmethod core/server-mutate 'fulcro.ui.forms/commit-to-entity [{:keys [user-db]} k {:keys [form/new-entities] :as p}]
;  {:action (fn []
;             (log/info "Commit entity: " k p)
;             (when (seq new-entities)
;               {:tempids (reduce (fn [remaps [k v]]
;                                   (log/info "Create new " k v)
;                                   (merge remaps (commit-new user-db k v))) {} new-entities)}))})
;
;(defmutation logout
;  "Server mutation: Log the given UI out. This mutation just removes the session, so that the server won't recognize
;  the user anymore."
;  [ignored-params]
;  ; if you wanted to directly access the session store, you can
;  (action [{:keys [request session-store user-db]}]
;    (let [uid  (-> request :session :uid)
;          user (users/get-user user-db uid)]
;      (timbre/info "Logout for user: " uid)
;      (server/augment-response {}
;        (fn [resp] (assoc resp :session nil))))))
;
;;(defmutation import-data-entry
;;  [params]
;;  (action [{:keys [request datomic user-db]}]
;;    (let [cx     (:cx datomic)
;;          uid    (-> request :session :uid)
;;          user   (users/get-user user-db uid)
;;          agency (:agency user)
;;          name   (:name user)]
;;      (timbre/debug "IMPORTING RIMDB DATA for " agency " by " name)
;;      (rimdb-ui.api.rimdb/migrate-rimdb cx)
;;      )))

;(defmutation begin-tac-report
;  "begin the TAC Report"
;  [env]
;  (action [{:keys [request datomic]}]
;    (let [cx (:cx datomic)]
;      (tac/begin-query cx))))
