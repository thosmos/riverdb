(ns riverdb.rad.model.db-queries
  (:require
    [com.fulcrologic.rad.database-adapters.datomic :as datomic]
    [datomic.api :as d]
    [theta.log :as log]
    [riverdb.api.resolvers :refer [add-conditions]]))

;(defn get-all-roles
;  [env query-params]
;  (if-let [db (some-> (get-in env [::datomic/databases :production]) deref)]
;    (let [agency (:role/agency query-params)
;          ids (if agency
;                (d/q '[:find [?uuid ...]
;                       :in $ ?ag
;                       :where
;                       [?e :role/agency ?ag]
;                       [?e :role/uuid ?uuid]] db agency)
;                (d/q '[:find [?uuid ...]
;                       :where
;                       [?e :role/uuid ?uuid]] db))]
;      (mapv (fn [id] {:role/uuid id}) ids))
;    (log/error "No database atom for production schema!")))

(defn get-all-role-types
  [env]
  (if-let [db (some-> (get-in env [::datomic/databases :production]) deref)]
    (d/q '[:find [(pull ?e [*]) ...]
           :where
           [?e :role.type/uuid]] db)
    (log/error "No database atom for production schema!")))

(defn get-all-projects
  [env query-params]
  (if-let [db (some-> (get-in env [::datomic/databases :production]) deref)]
    (let [agency (or
                   (:projectslookup/AgencyRef query-params)
                   (:user/agency query-params))
          ids (if agency
                (d/q '[:find [?uuid ...]
                       :in $ ?ag
                       :where
                       [?e :projectslookup/AgencyRef ?ag]
                       [?e :projectslookup/uuid ?uuid]] db agency)
                (d/q '[:find [?uuid ...]
                       :where
                       [?e :projectslookup/uuid ?uuid]] db))]
      (mapv (fn [id] {:projectslookup/uuid id}) ids))
    (log/error "No database atom for production schema!")))

(defn get-all-worktimes
  [env query-params]
  (if-let [db (some-> (get-in env [::datomic/databases :production]) deref)]
    (let [person (:worktime/person query-params)
          agency (:worktime/agency query-params)
          ids    (cond
                   person
                   (d/q '[:find [?u ...]
                          :in $ ?person
                          :where
                          [?e :worktime/person ?person]
                          [?e :worktime/uuid ?u]] db person)
                   agency
                   (d/q '[:find [?u ...]
                          :in $ ?agency
                          :where
                          [?e :worktime/agency ?agency]
                          [?e :worktime/uuid ?u]] db agency)
                   :else
                   (d/q '[:find [?u ...]
                          :where
                          [_ :worktime/uuid ?u]] db))]
      (mapv (fn [id] {:worktime/uuid id}) ids))
    (log/error "No database atom for production schema!")))

(defn get-all-worktime-tasks
  [env query-params]
  (if-let [db (some-> (get-in env [::datomic/databases :production]) deref)]
    (let [{:keys [only search-string]} query-params
          values (if (or only search-string)
                   (let [find-v '[:find [?t ...] :where]
                         find-v (add-conditions find-v '?t {:contains (or only search-string)})
                         find-v (conj find-v '[_ :worktime/task ?t])]
                     ;(log/debug "get-all-worktime-tasks" find-v)
                     (d/q find-v db))
                   (d/q '[:find [?t ...]
                          :where
                          [_ :worktime/task ?t]] db))
          results (mapv
                    (fn [t]
                      {:text t :value t})
                    values)]
      ;(log/debug "ALL TASKS" results)
      results)
    (log/error "No database atom for production schema!")))

(defn get-all-users
  [env query-params]
  (if-let [db (some-> (get-in env [::datomic/databases :production]) deref)]
    (let [agency (or
                   (:projectslookup/AgencyRef query-params)
                   (:user/agency query-params))
          ids (if agency
                (d/q '[:find [?uuid ...]
                       :in $ ?ag
                       :where
                       [?e :projectslookup/AgencyRef ?ag]
                       [?e :projectslookup/uuid ?uuid]] db agency)
                (d/q '[:find [?uuid ...]
                       :where
                       [?e :projectslookup/uuid ?uuid]] db))]
      (mapv (fn [id] {:projectslookup/uuid id}) ids))
    (log/error "No database atom for production schema!")))