(ns riverdb.import.csv
  (:require [clojure.tools.logging :as log :refer [debug info warn error]]
            [clojure-csv.core :as csv :refer [write-csv parse-csv]]
            [clojure.string :as str :refer [join]]
            [io.pedestal.http.route :as route]
            [clojure.pprint :refer [pprint]]
            [com.rpl.specter :refer :all]
            [io.pedestal.http :as http]
            [clojure.java.io :as io]
            [thosmos.util :as tu]
            [datomic.api :as d]
            clojure.edn
            dotenv
            [domain-spec.core :as ds]
            [riverdb.state :as st :refer [db state cx uri uri-dbf]]
            [riverdb.auth :as auth]))


(defn load-csv
  "import csv data into the same format as the reduce-fieldresults-to-map function"
  [filename]
  (with-open
    [^java.io.Reader r (clojure.java.io/reader filename)]
    (let [csv    (parse-csv r)
          header (first csv)
          hidx   (into {} (for [i (range (count header))]
                            [(get header i) i]))]
          ;csv    (rest csv)

      csv
      #_(doall
          (for [row csv]
            (let [sv  {:uuid (get row (get hidx "UUID"))
                       :site (get row (get hidx "Site"))
                       :svid (get row (get hidx "SiteVisitID"))
                       :date (get row (get hidx "Date"))
                       :time (get row (get hidx "Time"))}]
                  ;_ (println "DAtE" (type (get row (get hidx "Date"))))
                  ;frs (into {}
                  ;      (for [[k v] (:params qapp-requirements)]
                  ;        (let [sample-count (get-in param-config [k :count])]
                  ;          [k {:vals (vec (remove nil?
                  ;                           (for [i (range 1 (+ sample-count 1))]
                  ;                             (let [csv_field (str (name k) "_" i)
                  ;                                   csv_idx   (get hidx csv_field)]
                  ;                               (when csv_idx
                  ;                                 (let [field_val (get row csv_idx)]
                  ;                                   (edn/read-string field_val)))))))}])))
                  ;frs (calc-param-summaries frs)
                  ;sv  (assoc sv :fieldresults frs)
                  ;sv  (calc-sitevisit-stats sv)

              sv))))))

(defn import-csv [ctx]
  (let [response {:status  200
                  :headers {}
                  :body    {}}]
    (assoc ctx :response response)))

(defn csv-interceptor []
  (io.pedestal.interceptor/interceptor
    {:name  ::import-csv
     :enter (fn [ctx]
              (import-csv ctx))}))

(defn create-squuid-route []
  ["/import-csv"
   :get [(csv-interceptor)]
   :route-name ::import-csv])
