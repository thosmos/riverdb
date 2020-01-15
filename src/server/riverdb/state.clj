(ns riverdb.state
  (:require
    [datomic.api :as d]
    dotenv))

(def uri (or (dotenv/env :DATOMIC_URI) "datomic:free://localhost:4334/test-db"))
(def uri-dbf (or (dotenv/env :DATOMIC_URI_DBF) "datomic:free://localhost:4334/test-dbf"))

(defonce state (atom {:tables  nil
                      :datomic nil
                      :pks     nil}))

(defn db []
  (d/db (:cx @state)))

(defn cx []
  (:cx @state))

(defn start-dbs [state]
  (do
    (d/create-database uri)
    (swap! state assoc :cx (d/connect uri))))

(defn start-db []
  (start-dbs state))
