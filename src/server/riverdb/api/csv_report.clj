(ns riverdb.api.csv-report
  (:require
    [theta.log :as log]
    [riverdb.api.qc-report :as qc]
    [clojure-csv.core :as csv :refer [write-csv parse-csv]]
    [datomic.api :as d]
    [java-time :as jt]
    [riverdb.util :as util]
    [riverdb.state :refer [db cx]]
    [riverdb.db :as rdb :refer [rpull pull-entities]])
  (:import [java.io Writer]
           [java.util Date]
           [java.time Instant ZonedDateTime ZoneId]
           [java.text SimpleDateFormat]))

(defn time-from-instant [^Date ins]
  (-> ins
    (.getTime)
    (/ 1000)
    Instant/ofEpochSecond
    (ZonedDateTime/ofInstant
      (ZoneId/of "America/Los_Angeles"))))

(defn year-from-instant [^Date ins]
  (.getYear ^ZonedDateTime (time-from-instant ins)))

(def formatter  (SimpleDateFormat. "yyyy-MM-dd"))

(defn csv-headers [params-list params-map]
  ;(log/debug "CSV HEADERS" "params" params-list)
  (try
     (let [heads ["Date" "Site" "Time" "Fail?" "Publish"]
           ps    (vec
                   (flatten
                     (for [k params-list]
                       (let [{:parameter/keys [Replicates NameShort DeviceType]} (get params-map k)
                             reps   (or Replicates 1)
                             names  (if (= reps 1)
                                      [NameShort]
                                      [])
                             fields (if (> reps 1)
                                      (vec
                                        (concat
                                          (map str (range 1 (inc reps)))
                                          ["Mean" "Count"])))
                             fields (if DeviceType
                                      (conj fields "Device")
                                      fields)
                             names  (into names
                                      (for [field fields]
                                        (str NameShort "_" field)))]
                         (for [nm names]
                           nm)))))

           ends  [#_(th {:key 96 :className "info"} "Invalid_Total")
                  #_(th {:key 97 :className "danger"} "Exceed_Total")
                  "ID"
                  "Edit"
                  #_(th {:key 95} "SVID")
                  "Notes"]]

       (concat heads ps ends))
     (catch Exception ex (log/debug ex))))

(defn csv-cells [params-list params-map sv]
  (try
     (let [{:keys [db/id site date time publish fork trib samples svid failcode
                   notes count-params-invalid count-params-exceedance
                   incomplete? exceedance? imprecise?]} sv
           ;_      (log/debug "CSV SITEVISIT" site date "incomplete?" incomplete? "exceedance?" exceedance? "imprecise?" imprecise?)

           heads  [(when date (.format formatter date))
                   (str site)
                   (str time)
                   (when (not= failcode "None") failcode)
                   (str publish)
                   #_fork
                   #_trib]

           ps     (vec
                    (flatten
                      (for [k params-list]
                        (let [{pid             :db/id
                               :parameter/keys [Replicates NameShort DeviceType]} (get params-map k)]
                          (let [reps (or Replicates 1)
                                sa   (get samples k)
                                {val-range :range val-count :count
                                 :keys     [mean stddev prec vals device
                                            exceedance? incomplete? imprecise?
                                            too-low? too-high?]} sa

                                ;; with a blank sample or fewer vals, we still need to make blank cells
                                vals (reduce
                                       (fn [out i]
                                         (conj out (str (get vals i))))
                                       [] (range reps))

                                cols (if (<= reps 1)
                                       [mean]
                                       (into vals [(str mean) (str val-count)]))
                                cols (if DeviceType
                                       (into cols [(str device)])
                                       cols)]

                            (for [col cols] (or col "")))))))
           notes  [#_count-params-invalid
                   #_count-params-exceedance
                   (str id)
                   (when id (str "https://admin.riverdb.org/sitevisit/edit/" id))
                   (str notes)]
           result (concat heads ps notes)]
       result)
     (catch Exception ex (log/debug "CELL ERROR", ex))))


(comment
  ;;; output from get-datatable-report
  {:results     sitevisits
   ;:params            params
   :params-list params-list
   :params-map  params-map
   ;:precReqs qapp-requirements
   :reportYear  year
   :agency      agency
   :projectName Name})

(defn csv-report
  ([db {:keys [projectID agencyCode] :as args}]
   (csv-report *out* db args))
  ([^Writer writer db {:keys [projectID agencyCode year] :as args}]
   (let [_                (log/info "CSV REPORT" agencyCode projectID)
         datatable-report (qc/get-datatable-report db {:agency agencyCode :project projectID :year year})
         {:keys [results params-list params-map reportYear agency projectName]} datatable-report
         _                (log/debug "DETAILS" reportYear agency projectName)
         heads            (csv-headers params-list params-map)
         cells            (for [sv results]
                            (csv-cells params-list params-map sv))
         csv              (write-csv (cons heads cells) :force-quote true)]
     (.write writer ^String csv)
     (.flush writer))))



