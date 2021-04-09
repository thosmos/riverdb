(ns riverdb.rad.model.db-queries
  (:require
    [com.fulcrologic.rad.database-adapters.datomic :as datomic]
    [datomic.api :as d]
    [theta.log :as log]))

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
          ids    (if person
                   (d/q '[:find [?e ...]
                          :in $ ?person
                          :where
                          [?e :worktime/person ?person]] db person)
                   (d/q '[:find [?e ...]
                          :where
                          [?e :riverdb.entity/ns :entity.ns/worktime]] db))]
      (mapv (fn [id] {:org.riverdb.db.worktime/gid id}) ids))
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