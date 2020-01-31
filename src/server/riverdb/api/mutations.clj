(ns riverdb.api.mutations
  (:require
    [clojure.string :as st]
    [clojure.walk :as walk]
    [cognitect.transit :as transit]
    [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
    [com.wsscode.pathom.core :as p]
    [datomic.api :as d]
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

(defn parse-bigdec [str]
  (try (BigDecimal. str)
       (catch Exception _ nil)))

(defn gid-ident->db-id [ident]
  (let [db-id (parse-long (second ident))]
    db-id))

(defn gid-ident->ent-ns [ident]
  (let [ns     (namespace (first ident))
        strs   (clojure.string/split ns #"\.")
        table  (last strs)
        ent-ns (keyword "entity.ns" table)]
    ent-ns))



(defn parse-diff [ent-ns diff]
  (let [ent-spec (get specs-map ent-ns)
        attrs    (get ent-spec :entity/attrs)]
    (into {} (for [[k v] diff]
               (let [attr-spec    (get attrs k)
                     attr-type    (:attr/type attr-spec)
                     spec-ref?    (= :ref attr-type)
                     spec-bigdec? (= :bigdec attr-type)
                     ;; get the diff's :after value
                     val          (:after v)
                     _            (debug "DIFF" k "VAL" val)
                     val-map?     (map? val)
                     val-ref?     (and
                                    val-map?
                                    spec-ref?
                                    (= (count val) 1)
                                    (some? (:db/id val)))]
                 (case attr-type
                   :ref
                   [k (parse-long (:db/id val))]
                   [k val]))))))

(defn diff->txd [ent-ns db-id diff]
  ;(debug "DIFF->TXD" ent-ns db-id diff)
  (let [ent-spec (get specs-map ent-ns)
        attrs    (get ent-spec :entity/attrs)]
    (vec
      (for [[k v] diff]
        (let [attr-spec    (get attrs k)
              attr-type    (:attr/type attr-spec)
              ;_ (debug "DIFF->TXD" k attr-type v)
              spec-ref?    (= :ref attr-type)
              spec-bigdec? (= :bigdec attr-type)
              ;; get the diff's :after value
              val          (:after v)
              retract?     (nil? val)]
          ;_            (debug "DIFF" k "VAL" val)
          ;val-map?     (map? val)]
          ;val-ref?     (and
          ;               val-map?
          ;               spec-ref?
          ;               (= (count val) 1)
          ;               (some? (:db/id val)))]
          (if retract?
            [:db/retract db-id k (:before v)]
            (case attr-type
              :ref
              [:db/add db-id k (parse-long (:db/id val))]
              [:db/add db-id k val])))))))



(defn save-entity* [env ident diff]
  (debug "MUTATION!!! save-entity" "IDENT" ident "DIFF" diff (comment "ENV" (keys env)) "SESSION" (:session (:ring/request env)))
  (let [session-valid? (get-in env [:ring/request :session :session/valid?])
        user-email     (when session-valid?
                         (get-in env [:ring/request :session :account/auth :user :user/email]))
        karl?          (= user-email "karl@yubariver.org")]
    (if karl?
      (let [result (try
                     (let [txds (vec
                                  (apply concat
                                    (for [[idt dif] diff]
                                      (let [db-id  (gid-ident->db-id idt)
                                            ent-ns (gid-ident->ent-ns idt)
                                            txd    (diff->txd ent-ns db-id dif)]
                                        (debug "SAVE-ENTITY TXD" txd)
                                        txd))))
                           _    (debug "SAVE-ENTITY TXDS" txds)
                           tx   (d/transact (cx) txds)]
                       (debug "TXDS" txds "TX" @tx)
                       (:status @tx))
                     (catch Exception ex ex))]
        (debug "SAVE-ENTITY RESULT" result)
        result)

      {:error "Unauthorized"})))

(pc/defmutation save-entity [env {:keys [ident diff]}]
  {::pc/sym    `save-entity
   ::pc/params [:ident :diff]
   ::pc/output [:ident :diff :error]}
  (save-entity* env ident diff))

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
