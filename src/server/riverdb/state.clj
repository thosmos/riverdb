(ns riverdb.state
  (:require
    [datomic.api :as d]
    ;[datomock.core :as mock]
    [theta.log :as log]
    dotenv))

;(def uri (or (dotenv/env :DATOMIC_URI) "datomic:free://localhost:4334/test-db"))

(def default-uri "datomic:dev://localhost:4334/riverdb")

;(defn get-mysql-uri [db-name sql-user sql-password]
;  (str "datomic:sql://" db-name "?jdbc:mysql://localhost:3306/datomic?user=" sql-user "&password=" sql-password "&useSSL=false"))

(def uris {:base (or (dotenv/env :DATOMIC_URI) default-uri)
           :model (or (dotenv/env :DATOMIC_MODEL_URI) (dotenv/env :DATOMIC_URI) default-uri)
           :users (or (dotenv/env :DATOMIC_USERS_URI) (dotenv/env :DATOMIC_URI) default-uri)})

(defonce state (atom {:admin-disabled (or (dotenv/env :ADMIN_DISABLED) false)}))

(defn db
  ([] (db :base))
  ([k] (d/db (k @state))))

(defn cx
  ([] (cx :base))
  ([k] (k @state)))

(defn start-dbs
  ([] (start-dbs state))
  ([st]
   (let [mock-db? (if (some? (:mock-db @state))
                    (:mock-db @state)
                    (dotenv/env :MOCK_DB))]
     (doseq [[k uri] uris]
       (d/create-database uri)
       (let [cx (d/connect uri)
             cx (if mock-db?
                  (do
                    (log/debug "UNIMPLEMENTED MOCKING Datomic CX"))
                    ;(mock/fork-conn cx))
                  cx)]
        (swap! st assoc k cx))))))

(defn set-mock-db! [mock?]
  (swap! state assoc :mock-db mock?)
  (start-dbs))
