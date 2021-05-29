(ns riverdb.api.mutations
  (:require
    [clojure.string :as str]
    [clojure.set :as set]
    [clojure.walk :as walk]
    [clojure.pprint :refer [pprint]]
    [cognitect.transit :as transit]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.networking.file-upload :as fup]
    [com.rpl.specter :as sp]
    [com.wsscode.pathom.core :as p]
    [datomic.api :as d]
    [edn-query-language.core :as eql]
    [riverdb.graphql.schema :refer [table-specs-ds specs-sorted specs-map]]
    [riverdb.state :refer [db cx]]
    [taoensso.timbre :as log :refer [debug]]
    [thosmos.util :as tu :refer [walk-modify-k-vals limit-fn]]
    [riverdb.roles :as roles]
    [riverdb.db :as rdb]
    [riverdb.api.import :as import])
  (:import (java.math BigDecimal)))

;;;;  SERVER

(defn parse-long [val]
  (let [t (type val)]
    (cond
      (or (= t java.lang.Long) (= t java.lang.Integer))
      val
      (= t java.lang.String)
      (try (Long/parseLong val)
           (catch Exception _ nil))
      :else
      (throw (Exception. (str "Unknown Type: '" (type val) "' for value: " val))))))


(defn parse-double [str]
  (try (Double/parseDouble str)
       (catch Exception _ nil)))

(defn parse-bigdec [str]
  (try (BigDecimal. str)
       (catch Exception _ nil)))


;(defn convert-ids [body]
;  (->>
;    (sp/transform (sp/walker tempid/tempid?) #(-> % :id str (subs 0 20)) body)
;    (sp/transform [first sp/ALL sp/LAST map? #(number? (parse-long (:db/id %)))] #(identity {:db/id (parse-long (:db/id %))}))
;    (sp/transform [first sp/ALL sp/LAST vector? sp/FIRST #(number? (parse-long (:db/id %)))] #(identity {:db/id (parse-long (:db/id %))}))
;    (sp/transform [sp/ALL sp/LAST vector? #(clojure.string/starts-with? (str (first %)) ":org.riverdb")]
;      #(identity (or (parse-long (second %)) (second %))))
;    (sp/transform [sp/ALL sp/LAST vector? sp/ALL vector? #(clojure.string/starts-with? (str (first %)) ":org.riverdb")]
;      #(identity (or (parse-long (second %)) (second %))))
;    (sp/select [sp/ALL #(some? (second %))])
;    (into {})))

;(defn gid-ident->db-id [ident]
;  (let [ident-val (second ident)]
;    (if (tempid/tempid? ident-val)
;      (-> ident-val :id)
;      (parse-long ident-val))))

;(defn gid-ident->ent-ns [ident]
;  (let [ns     (namespace (first ident))
;        strs   (clojure.string/split ns #"\.")
;        table  (last strs)
;        ent-ns (keyword "entity.ns" table)]
;    ent-ns))

(defn ref->gid
  "Sometimes references on the client are actual idents and sometimes they are
  nested maps, this function attempts to return an ident regardless."
  [x]
  (debug "ref->gid" x)
  (cond
    (eql/ident? x)
    (if
      (tempid/tempid? (second x))
      (second x)
      (if-let [long-str (parse-long (second x))]
        long-str
        (second x)))
    (and (map? x) (:db/id x))
    (parse-long (:db/id x))))

(defn delta->datomic-txn
  "Takes in a normalized form delta, usually from client, and turns in
  into a Datomic transaction for the given schema (returns empty txn if there is nothing on the delta for that schema).
  This fn was derived and modified from https://github.com/fulcrologic/fulcro-rad-datomic/"
  [delta]
  ;; TASK: test mapcat on nil (nothing on schema)
  (vec
    (mapcat (fn [[[id-k id] entity-diff]]
              (debug "DELTA FN" id-k id)
              (mapcat (fn [[k diff]]
                        (debug "DIFF FN" k diff)
                        (let [id (if-let [long-id (parse-long id)] long-id (str id))
                              {:keys [before after]} diff
                              [before after] (if (= k :fieldresult/Result)
                                               [(double before) (double after)]
                                               [before after])]
                          (cond
                            (= k :db/id)
                            []

                            (ref->gid after)
                            [[:db/add id k (ref->gid after)]]

                            (and (sequential? after) (every? ref->gid after))
                            (let [before   (into #{}
                                             (comp (map ref->gid) (remove nil?))
                                             before)
                                  after    (into #{}
                                             (comp (map ref->gid) (remove nil?))
                                             after)
                                  retracts (set/difference before after)
                                  adds     after
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
                                  adds     after
                                  eid      id]
                              (vec
                                (concat
                                  (for [r retracts] [:db/retract eid k r])
                                  (for [a adds] [:db/add eid k a]))))

                            ;; Assume field is optional and omit
                            (and (nil? before) (nil? after)) []

                            :else (if (nil? after)
                                    (if (ref->gid before)
                                      [[:db/retract id k (ref->gid before)]]
                                      [[:db/retract id k before]])
                                    [[:db/add id k after]]))))
                entity-diff))
      delta)))


(defn save-entity* [env {:keys [ident diff delete agency] :as params}]
  (debug "MUTATION!!!" "save-entity" (str (when delete "DELETE ")) "IDENT" ident "DIFF" diff)
  (let [session-valid? (get-in env [:ring/request :session :session/valid?])
        user           (when session-valid?
                         (get-in env [:ring/request :session :account/auth :user]))
        role?          (when user
                         (get-in user [:user/role :db/ident]))
        agency?        (when user
                         (get-in user [:user/agency :db/id]))
        match?         (= agency? agency)
        _              (debug "match?" match?)
        gid            (ref->gid ident)
        _              (debug "gid" gid)
        tempid?        (tempid/tempid? gid)
        _              (debug "tempid?" tempid?)
        exists?        (when (and gid (not tempid?)) (rdb/pull-tx gid))
        _              (debug "exists?" exists?)
        tx-user?       (when exists?
                         (get-in exists? [:riverdb/tx-user :db/id]))
        _              (debug "tx-user?" tx-user?)
        creator?       (= tx-user? (:db/id user))
        _              (debug "creator?" creator?)
        ;_              (debug "SAVE USER" user "CREATOR?" creator? "ROLE" role? "AGENCY" agency? "MATCH?" match?)
        tempids        (sp/select (sp/walker tempid/tempid?) diff)
        tmp-map        (into {} (map (fn [t] [t (-> t :id str (subs 0 20))]) tempids))
        _              (debug "PRE TEMPIDS" tempids tmp-map)]
    (cond
      (not role?)
      (do
        (debug "ERROR save-entity*" "Unauthorized")
        {:error "Unauthorized"})
      (and
        exists?
        (= role? :role.type/data-entry)
        (not creator?))
      (do
        (debug "ERROR: :role.type/data-entry attempted to change, but is not creator ")
        {:error "Unauthorized: role is data entry, but is not creator"})
      :else
      (let [result (try
                     (let [txds (if delete
                                  [[:db.fn/retractEntity (ref->gid ident)]]
                                  (delta->datomic-txn (sp/transform (sp/walker tempid/tempid?) tmp-map diff)))
                           txds (conj txds {:db/id           "datomic.tx"
                                            :riverdb/tx-user (parse-long (:db/id user))
                                            :riverdb/tx-info "save-entity"})
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
            {:tempids tempids}))))))

(pc/defmutation save-entity [env {:keys [ident diff delete agency] :as params}]
  {::pc/sym    `save-entity
   ::pc/params [:ident :diff :delete]
   ::pc/output [:error :tempids]}
  (let [_      (debug "SAVE ENTITY" "params" params "ident" ident "diff" diff)
        result (save-entity* env params)]
    (debug "RESULT save-entity" result)
    result))

(pc/defmutation upload-files [env {:keys [config] ::fup/keys [files] :as params}]
  {::pc/sym `upload-files
   ::pc/params [:config]
   ::pc/output [:errors :tempids :msgs]}
  (let [{:keys [agency project]} config
        session-valid? (get-in env [:ring/request :session :session/valid?])
        user           (when session-valid?
                         (get-in env [:ring/request :session :account/auth :user]))
        role?          (when user
                         (get-in user [:user/role :db/ident]))
        agency?        (when user
                         (get-in user [:user/agency :agencylookup/AgencyCode]))
        match?         (= agency? agency)]
    (debug "UPLOAD" "role?" role? "agency?" agency? "match?" match?)
    (cond
      (or (not agency) (not role?) (not match?))
      (do
        (debug "ERROR upload-files" "Unauthorized")
        {:errors ["Unauthorized"]})
      (= role? :role.type/data-entry)
      (do
        (debug "ERROR: Data Entry role is not authorized")
        {:errors ["Unauthorized: Data Entry role is not authorized"]})
      :else
      (import/process-uploads {:config config :files files}))))

(def mutations [save-entity upload-files])

