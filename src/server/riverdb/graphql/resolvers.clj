(ns riverdb.graphql.resolvers
  (:require
    [riverdb.graphql.mutations :as m]
    [riverdb.graphql.queries :as q]))

(defn resolvers []
  {:resolve-hello            q/resolve-hello
   :resolve-sitevisit        q/resolve-sitevisits
   :resolve-rimdb            q/resolve-rimdb2
   :resolve-rimdb-fk         q/resolve-rimdb-fk
   ;:resolve-rimdb-rk         q/resolve-rimdb-rk
   :resolve-spec-query       q/resolve-spec
   :resolve-spec-fk          q/resolve-rimdb-fk
   ;:resolve-spec-rk          q/resolve-rimdb-rk
   :resolve-stationdetail    q/resolve-stations
   :resolve-safetoswim       q/resolve-safetoswim
   :resolve-loggers          q/resolve-loggers
   :resolve-logsamples       q/resolve-logsamples
   :resolve-agency-ref       q/resolve-agency-ref
   :resolve-specs            q/resolve-specs
   :resolve-db-specs         q/resolve-db-specs

   :resolve-auth             m/resolve-auth
   :resolve-unauth           m/resolve-unauth
   :resolve-changeit         m/resolve-changeit
   :resolve-change-user-name m/resolve-change-user-name
   :resolve-set-password     m/resolve-set-password

   :resolve-entity-update    m/resolve-entity-update
   :resolve-entity-create    m/resolve-entity-create
   :resolve-entity-delete    m/resolve-entity-delete
   :resolve-list-query       q/resolve-list-query
   :resolve-list-meta        q/resolve-list-meta

   :resolve-current-user     q/resolve-current-user})