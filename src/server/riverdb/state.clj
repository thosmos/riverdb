(ns riverdb.state
  (:require
    [datomic.api :as d]
    dotenv))

;(def uri (or (dotenv/env :DATOMIC_URI) "datomic:free://localhost:4334/test-db"))

(def default-uri "datomic:free://localhost:4334/test-db")

;(defn get-mysql-uri [db-name sql-user sql-password]
;  (str "datomic:sql://" db-name "?jdbc:mysql://localhost:3306/datomic?user=" sql-user "&password=" sql-password "&useSSL=false"))

(def uris {:base (or (dotenv/env :DATOMIC_URI) default-uri)
           :model (or (dotenv/env :DATOMIC_MODEL_URI) (dotenv/env :DATOMIC_URI) default-uri)
           :users (or (dotenv/env :DATOMIC_USERS_URI) (dotenv/env :DATOMIC_URI) default-uri)})

(defonce state (atom {}))

(defn db
  ([] (db :base))
  ([k] (d/db (k @state))))

(defn cx
  ([] (cx :base))
  ([k] (k @state)))

(defn start-dbs
  ([] (start-dbs state))
  ([st]
   (doseq [[k uri] uris]
     (d/create-database uri)
     (swap! st assoc k (d/connect uri)))))
