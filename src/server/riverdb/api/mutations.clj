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
    [thosmos.util :as tu :refer [walk-modify-k-vals limit-fn]])
  (:import (java.math BigDecimal)))

;;;;  SERVER

(defn parse-long [str]
  (try (Long/parseLong str)
       (catch Exception _ nil)))

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


(defn ref->ident
  "Sometimes references on the client are actual idents and sometimes they are
  nested maps, this function attempts to return an ident regardless."
  [x]
  (if (eql/ident? x)
    (if-let [long-str (parse-long (second x))]
      long-str
      (second x))
    (parse-long (:db/id x))))


(defn delta->datomic-txn
  "Takes in a normalized form delta, usually from client, and turns in
  into a Datomic transaction for the given schema (returns empty txn if there is nothing on the delta for that schema).
  This fn was derived and modified from https://github.com/fulcrologic/fulcro-rad-datomic/"
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


(defn save-entity* [env ident diff create]
  (debug "MUTATION!!! save-entity" "IDENT" ident "DIFF" diff (comment "ENV" (keys env) "SESSION" (:session (:ring/request env))))
  (let [session-valid? (get-in env [:ring/request :session :session/valid?])
        user-email     (when session-valid?
                         (get-in env [:ring/request :session :account/auth :user :user/email]))
        karl?          (or
                         (= user-email "karl@yubariver.org")
                         (= user-email "mo@sierrastreamsinstitute.org"))
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
      (do
        (debug "ERROR save-entity*" "Unauthorized")
        {:error "Unauthorized"}))))

(pc/defmutation save-entity [env {:keys [ident diff create]}]
  {::pc/sym    `save-entity
   ::pc/params [:ident :diff :create]
   ::pc/output [:error :tempids]}
  (let [result (save-entity* env ident diff create)]
    (debug "RESULT save-entity" result)
    result))

(def mutations [save-entity])

