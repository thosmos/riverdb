(ns riverdb.import
  (:require [clj-time.coerce :as c]
            [clj-time.core :as t]
            [clj-time.local :as l]
            [clojure.core.rrb-vector :as fv]
            [clojure-csv.core :as csv :refer [write-csv parse-csv]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log :refer [debug info warn error]]
            [clojure.string :as str]
    ;[com.walmartlabs.lacinia.schema :as schema]
            [datomic.api :as d]
    ;[domain-spec.core :as ds]
            [java-time :as jt]
    ;[riverdb.api.geo :as geo]
            [riverdb.db :as rdb :refer [rpull pull-entities]]
            [riverdb.state :as state :refer [db cx]]
            [theta.util :refer [parse-bool parse-long parse-double parse-date parse-bigdec]]
            [thosmos.util :as tu])
  (:import (java.util Date)))

;(def uri-dbf (or (dotenv/env :DATOMIC_URI_DBF) "datomic:free://localhost:4334/test-dbf"))
;




;(def qapp-requirements
;  {:include-elided? false
;   :min-samples     3
;   :elide-extra?    true
;   :params          {:H2O_Temp {:precision  {:unit 0.5}
;                                :exceedance {:high 20.0}}
;                     :H2O_Cond {:precision {:unit 5.0}}
;                     :H2O_DO   {:precision  {:percent 5.0}
;                                :exceedance {:low 7.0}}
;                     :H2O_pH   {:precision  {:unit 0.2}
;                                :exceedance {:low  6.5
;                                             :high 8.5}}
;                     :H2O_Turb {:precision {:percent   5.0
;                                            :unit      0.3
;                                            :threshold 10.0}}
;                     :H2O_PO4  {:precision {:percent   10.0
;                                            :unit      0.03
;                                            :threshold 0.1}}
;                     :H2O_NO3  {:precision {:percent   10.0
;                                            :unit      0.03
;                                            :threshold 0.1}}}})
;
;
;(def qapp-requirements-wcca
;  {:include-elided? false
;   :min-samples     3
;   :elide-extra?    true
;   :params          {:H2O_Temp {:precision  {:unit 0.5}
;                                :exceedance {:high 20.0}}
;                     :H2O_Cond {:precision {:unit 5.0}}
;                     :H2O_DO   {:precision  {:percent 5.0}
;                                :exceedance {:low 7.0}}
;                     :H2O_pH   {:precision  {:unit 0.2}
;                                :exceedance {:low  6.5
;                                             :high 8.5}}
;                     :H2O_Turb {:precision {:percent   5.0
;                                            :unit      0.3
;                                            :threshold 10.0}}}})


;(def params
;  [:Air_Temp :H2O_Temp :H2O_Cond :H2O_DO :H2O_pH :H2O_Turbidity])

;; FIXME per group

;(def param-config
;  {:Air_Temp     {:order 0 :count 1 :name "Air_Temp"}
;   :H2O_Temp     {:order 1 :count 6 :name "H2O_Temp"}
;   :H2O_Cond     {:order 2 :count 3 :name "Cond"}
;   :H2O_DO       {:order 3 :count 3 :name "DO"}
;   :H2O_pH       {:order 4 :count 3 :name "pH"}
;   :H2O_Turb     {:order 5 :count 3 :name "Turb"}
;   :H2O_NO3      {:order 6 :count 3 :name "NO3" :optional true}
;   :H2O_PO4      {:order 7 :count 3 :name "PO4" :optional true}
;   :H2O_Velocity {:elide? true}})

;(def param-config-wcca
;  {:Air_Temp   {:order 0 :count 1 :name "Air_Temp_C"}
;   :Cond       {:order 2 :count 3 :name "Cond_uS"}
;   :DO_mgL     {:order 3 :count 3 :name "DOxy_mgL"}
;   :DO_Percent {:order 3 :count 3 :name "DOxy_Percent"}
;   :H2O_Temp   {:order 1 :count 3 :name "H2OTemp_C"}
;   :H2O_TempDO {:order 1 :count 3 :name "H2OTempDO_C"}
;   :pH         {:order 4 :count 3 :name "pH"}
;   :Turb       {:order 5 :count 3 :name "Turb_NTUs"}})

;(def wcca-param->constituent
;  {:Air_Temp   [:constituentlookup/ConstituentCode "10-42-100-0-31"]
;   :Cond       [:constituentlookup/ConstituentCode "5-42-24-0-25"]
;   :DO_mgL     [:constituentlookup/ConstituentCode "WCCA-DO-H2O-mg/L-PROBE-NONE-5-42-38-0-6"]
;   :DO_Percent [:constituentlookup/ConstituentCode "WCCA-DO-H2O-%-PROBE-NONE-5-42-38-0-13"]
;   :H2O_Temp   [:constituentlookup/ConstituentCode "WCCA 5-42-100-0-31"]
;   :H2O_TempDO [:constituentlookup/ConstituentCode "WCCA-TempDO-5-42-100-0-31"]
;   :pH         [:constituentlookup/ConstituentCode "5-42-78-0-0"]
;   :Turb       [:constituentlookup/ConstituentCode "5-42-108-0-9"]})

;(def param-config-ssi
;  {:Air_Temp {:order 0 :count 1 :name "Air"}
;   :H2O_Temp {:order 1 :count 6 :name "H2Otemp"}
;   :H2O_Cond {:order 2 :count 3 :name "Cond"}
;   :H2O_DO   {:order 3 :count 3 :name "O2"}
;   :H2O_pH   {:order 4 :count 3 :name "pH"}
;   :H2O_Turb {:order 5 :count 3 :name "Tur"}
;   :H2O_PO4  {:order 6 :count 3 :name "PO4"}
;   :H2O_NO3  {:order 6 :count 3 :name "NO3"}
;   :TotalColiform {:order 7 :count 1 :name "TotalColiform"}
;   :EColi {:order 7 :count 1 :name "EColi"}})

;(def ssi-param->constituent
;  {:H2O_pH   [:constituentlookup/ConstituentCode "5-42-78-0-0"]
;   :H2O_Temp [:constituentlookup/ConstituentCode "5-42-100-0-31"]
;   :H2O_Turb [:constituentlookup/ConstituentCode "5-42-108-0-9"]
;   :H2O_Cond [:constituentlookup/ConstituentCode "5-42-24-0-25"]
;   :H2O_DO   [:constituentlookup/ConstituentCode "5-42-38-0-6"]
;   :H2O_PO4  [:constituentlookup/ConstituentCode "5-22-399-2-6"]
;   :H2O_NO3  [:constituentlookup/ConstituentCode "5-20-69-0-6"]
;   :Air_Temp [:constituentlookup/ConstituentCode "10-42-100-0-31"]
;   :TotalColiform [:constituentlookup/ConstituentCode "5-57-23-2-7"]
;   :EColi    [:constituentlookup/ConstituentCode "5-57-464-0-7"]})


;(def cols-wcca
;  {
;   "SiteName"            :site-name
;   "SiteID"              :site-id
;   "SiteSamplingEventID" :svid
;   "Date"                :SiteVisitDate
;   "StartTime"           :time
;   "Lat"                 :lat
;   "Long"                :lon
;
;   "WaterDepth_In"       :WaterDepth
;   "DepthUnit"           :UnitWaterDepth
;   "StreamWidth_Ft"      :StreamWidth
;   "WidthUnit"           :UnitStreamWidth
;
;   "Notes"               :Notes
;   "DataEntryNotes"      :DataEntryNotes
;   "DataEntryDateTime"   :DataEntryDate
;   "DataEntryPersonID"   :DataEntryPerson
;   "QC"                  :QACheck
;   "QCDate"              :QADate
;   "QCPersonID"          :QAPerson})


;(def cols-SSI
;  {
;   "Site"              :site-name
;   "SiteID"            :site-id
;   "SiteVisitID"       :svid
;   "Date"              :SiteVisitDate
;   "Time"              :time
;
;   "Water Depth"       :WaterDepth
;   "Depth Unit"        :UnitWaterDepth
;   "Stream Width"      :StreamWidth
;   "Width Unit"        :UnitStreamWidth})

;"Field Notes"       :Notes
;"Data Entry Notes"  :DataEntryNotes
;"DataEntryDateTime" :DataEntryDate
;;   "DataEntryPersonID"   :DataEntryPerson
;"QC"                :QACheck
;"QCDate"            :QADate
;"QCPerson"          :QAPerson})

;;
(def param-configs
  ":type is one of :field, :lab, :obs, :cont"
  {"SSI"   {:Air_Temp      {:order 0 :count 1 :name "Air"}
            :H2O_Temp      {:order 1 :count 6 :name "H2Otemp"}
            :H2O_Cond      {:order 2 :count 3 :name "Cond"}
            :H2O_DO        {:order 3 :count 3 :name "O2"}
            :H2O_pH        {:order 4 :count 3 :name "pH"}
            :H2O_Turb      {:order 5 :count 3 :name "Tur"}
            :H2O_PO4       {:order 6 :count 3 :name "PO4"}
            :H2O_NO3       {:order 6 :count 3 :name "NO3"}
            :TotalColiform {:order 7 :count 1 :type :lab :name "TotalColiform"}
            :EColi         {:order 7 :count 1 :type :lab :name "EColi"}}
   "WCCA"  {:Air_Temp   {:order 0 :count 1 :name "AirTemp_C"}
            :Cond       {:order 2 :count 3 :name "Cond_uS" :device "Cond_EquipID"}
            :DO_mgL     {:order 3 :count 3 :name "DOxy_mgL" :device "DOxy_EquipID"}
            :DO_Percent {:order 3 :count 3 :name "DOxy_Percent" :device "DOxy_EquipID"}
            :H2O_Temp   {:order 1 :count 3 :name "H2OTemp_C" :device "H2OTemp_EquipID"}
            :H2O_TempDO {:order 1 :count 3 :name "H2OTempDO_C" :device "DOxy_EquipID"}
            :pH         {:order 4 :count 3 :name "pH" :device "pH_EquipID"}
            :Turb       {:order 5 :count 3 :name "Turb_NTUs" :device "Turb_EquipID"}}
   "SYRCL" {:TotalColiform {:count 1 :type :lab :name "TotalColiform"}
            :EColi         {:count 1 :type :lab :name "EColi"}
            :Enterococcus  {:count 1 :type :lab :name "Enterococcus"}}})

(def param->const
  {"SSI"   {:H2O_pH        [:constituentlookup/ConstituentCode "5-42-78-0-0"]
            :H2O_Temp      [:constituentlookup/ConstituentCode "5-42-100-0-31"]
            :H2O_Turb      [:constituentlookup/ConstituentCode "5-42-108-0-9"]
            :H2O_Cond      [:constituentlookup/ConstituentCode "5-42-24-0-25"]
            :H2O_DO        [:constituentlookup/ConstituentCode "5-42-38-0-6"]
            :H2O_PO4       [:constituentlookup/ConstituentCode "5-22-399-2-6"]
            :H2O_NO3       [:constituentlookup/ConstituentCode "5-20-69-0-6"]
            :Air_Temp      [:constituentlookup/ConstituentCode "10-42-100-0-31"]
            :TotalColiform [:constituentlookup/ConstituentCode "5-56-23-20-7"] ;; "5-56-23-20-7" "5-57-23-2-7"
            :EColi         [:constituentlookup/ConstituentCode "5-57-464-0-7"]}
   "WCCA"  {:Air_Temp   [:constituentlookup/ConstituentCode "10-42-100-0-31"]
            :Cond       [:constituentlookup/ConstituentCode "5-42-24-0-25"]
            :DO_mgL     [:constituentlookup/ConstituentCode "5-42-38-0-6"]
            :DO_Percent [:constituentlookup/ConstituentCode "5-42-38-0-13"]
            :H2O_Temp   [:constituentlookup/ConstituentCode "5-42-100-0-31"]
            :H2O_TempDO [:constituentlookup/ConstituentCode "5-42-100-0-31"]
            :pH         [:constituentlookup/ConstituentCode "5-42-78-0-0"]
            :Turb       [:constituentlookup/ConstituentCode "5-42-108-0-9"]}
   "SYRCL" {:TotalColiform [:constituentlookup/ConstituentCode "5-56-23-20-7"]
            :EColi         [:constituentlookup/ConstituentCode "5-57-464-0-7"]
            :Enterococcus  [:constituentlookup/ConstituentCode "5-9000-9002-0-7"]}})

(def param->devType
  {"WCCA" {:Air_Temp   [:samplingdevicelookup/SampleDevice "SupcoTemp"]
           :Cond       [:samplingdevicelookup/SampleDevice "ECTestr 11"]
           :DO_mgL     [:samplingdevicelookup/SampleDevice "WCCA DO"]
           :DO_Percent [:samplingdevicelookup/SampleDevice "WCCA DO"]
           :H2O_Temp   [:samplingdevicelookup/SampleDevice "WCCA Temp"]
           :H2O_TempDO [:samplingdevicelookup/SampleDevice "WCCA DO"]
           :pH         [:samplingdevicelookup/SampleDevice "HannaPH"]
           :Turb       [:samplingdevicelookup/SampleDevice "LaMotteTurb"]}
   "SSI"  {:Air_Temp [:samplingdevicelookup/SampleDevice "ExtechTemp"]
           :H2O_Cond [:samplingdevicelookup/SampleDevice "ECTestr 11"]
           :H2O_DO   [:samplingdevicelookup/SampleDevice "LaMotteDO"]
           :H2O_Temp [:samplingdevicelookup/SampleDevice "ExtechTemp"]
           :H2O_pH   [:samplingdevicelookup/SampleDevice "OaktonPH2"]
           :H2O_Turb [:samplingdevicelookup/SampleDevice "LaMotteTurb"]
           :H2O_PO4  [:samplingdevicelookup/SampleDevice "Not Recorded"]
           :H2O_NO3  [:samplingdevicelookup/SampleDevice "Not Recorded"]}})



(def col-configs
  {"SSI"   {"Site"         :site-name
            "SiteID"       :site-id
            "SiteVisitID"  :svid
            "Date"         :SiteVisitDate
            "Time"         :time

            "Water Depth"  :WaterDepth
            "Depth Unit"   :UnitWaterDepth
            "Stream Width" :StreamWidth
            "Width Unit"   :UnitStreamWidth}
   "WCCA"  {"SiteName"            :site-name
            "SiteID"              :site-id
            "SiteSamplingEventID" :svid
            "Date"                :SiteVisitDate
            "StartTime"           :time
            "Lat"                 :lat
            "Long"                :lon

            "WaterDepth_In"       :WaterDepth
            "DepthUnit"           :UnitWaterDepth
            "StreamWidth_Ft"      :StreamWidth
            "WidthUnit"           :UnitStreamWidth

            "Notes"               :Notes
            "DataEntryNotes"      :DataEntryNotes
            "DataEntryDateTime"   :DataEntryDate
            "DataEntryPersonID"   :DataEntryPerson
            "QC"                  :QACheck
            "QCDate"              :QADate
            "QCPersonID"          :QAPerson}
   "SYRCL" {"Site" :site-id
            "Date" :SiteVisitDate}})





(def cols-SSI_BR
  {
   "Site"         :site-name
   "SiteID"       :site-id
   "SiteVisitID"  :svid
   "Date"         :SiteVisitDate

   "Water Depth"  :WaterDepth
   "Stream Width" :StreamWidth})

;"Field Notes"       :Notes
;"Data Entry Notes"  :DataEntryNotes
;"DataEntryDateTime" :DataEntryDate
;   "DataEntryPersonID"   :DataEntryPerson
;"QC"                :QACheck
;"QCDate"            :QADate
;"QCPerson"          :QAPerson})




(def site-cols-wcca
  {"SiteID"              :site-id
   "SiteName"            :site-name
   "LocationDescription" :descript
   "Lat"                 :lat
   "Long"                :lon
   "MonitoringSite"      :is-rm-site?})


;; "SiteID","Name","Description","Lat","Long","PointREF","MonitorSite"

(def site-cols-ssi
  {"SiteID"      :site-id
   "Name"        :site-name
   "Description" :descript
   "Lat"         :lat
   "Long"        :lon
   "MonitorSite" :is-rm-site?})

(def equip-id-suffix "_EquipID")


;(defn convert-coords [sites]
;  (for [{:keys [lat lon] :as site} sites]
;    (if (and lat lon)
;      (if (and
;            (str/ends-with? lat "N")
;            (str/ends-with? lon "E"))
;        (let [lat    (parse-double (subs lat 0 (str/index-of lat "N")))
;              lon    (parse-double (subs lon 0 (str/index-of lon "E")))
;              result (geo/convert {:lat lat :lon lon} geo/utm10n geo/wgs84)]
;          (merge site result))
;        (let [lat (parse-double lat)
;              lon (parse-double lon)]
;          (merge site {:lat lat :lon lon})))
;      site)))

(defn convert-site-ids [sites]
  (for [{:keys [site-id] :as site} sites]
    (if
      site-id
      (assoc site :site-id (parse-long site-id))
      site)))

(defn convert-bools [keys coll]
  (for [item coll]
    (reduce
      (fn [item k]
        (if-let [kb (get item k)]
          (assoc item k (parse-bool kb))
          item))
      item keys)))

(defn convert-bool [map-key coll]
  (for [c coll]
    (if-let [field (get c map-key)]
      (let [val (parse-bool field)]
        (assoc c map-key val))
      c)))

;(defn read-sites-csv [site-cols filename]
;  (with-open [^java.io.Reader r (clojure.java.io/reader filename)]
;    (let [csv    (parse-csv r)
;          header (first csv)
;          ncols  (count header)
;          hidx   (into {} (for [i (range ncols)]
;                            [(get header i) i]))
;          csv    (vec (rest csv))
;          sites  (for [row csv]
;                   (into {}
;                     (for [i (range ncols)]
;                       (when-let [col-key (get site-cols (get header i))]
;                         (let [col-data (get row i)]
;                           (when (and col-data (not= col-data ""))
;                             [col-key col-data]))))))]
;      (->> sites
;        convert-coords
;        convert-site-ids
;        (convert-bool :is-rm-site?)))))


(defn read-csv [filename col-config param-config]
  (with-open
    [^java.io.Reader r (clojure.java.io/reader filename)]
    (let [csv    (parse-csv r)
          header (first csv)
          hidx   (into {} (for [i (range (count header))]
                            [(get header i) i]))
          csv    (vec (rest csv))
          ncols  (count header)
          rows   (for [row csv]
                   (let [sv  (into {}
                               (for [i (range ncols)]
                                 (when-let [col-key (get col-config (get header i))]
                                   (let [col-data (get row i)]
                                     ;(debug "col-key" col-key "col-data" col-data)
                                     (when (and col-data (not= col-data ""))
                                       [col-key col-data])))))
                         frs (into {}
                               (for [[k v] param-config]
                                 (let [sample-count (get-in param-config [k :count])
                                       param-name   (get-in param-config [k :name])
                                       param-type   (get-in param-config [k :type] :field) ;; :field, :lab, :obs, :cont
                                       device-col   (get-in param-config [k :device])
                                       result       (cond
                                                      (= param-type :field)
                                                      (let [fld-vals   (vec
                                                                         (remove nil?
                                                                           (for [i (range 1 (+ sample-count 1))]
                                                                             (let [csv-field (if (> sample-count 1)
                                                                                               (str param-name "_" i)
                                                                                               param-name)
                                                                                   csv-idx   (get hidx csv-field)]
                                                                               (when csv-idx
                                                                                 (let [field-val (get row csv-idx)
                                                                                       bd-val    (when (and field-val (not= field-val ""))
                                                                                                   (try
                                                                                                     (bigdec field-val)
                                                                                                     (catch Exception _ field-val)))]
                                                                                   ;(debug "BD VAL" (type bd-val) bd-val)
                                                                                   bd-val))))))
                                                            device-val (let [csv-idx (get hidx device-col)]
                                                                         (when csv-idx
                                                                           (let [dev-val (get row csv-idx)]
                                                                             (when (not= dev-val "")
                                                                               dev-val))))]
                                                        (when (seq fld-vals)
                                                          {:vals fld-vals
                                                           :type param-type
                                                           :device device-val}))

                                                      (= param-type :lab)
                                                      (let [csv-idx    (get hidx param-name)
                                                            lab-val    (get row csv-idx)
                                                            over?      (str/includes? lab-val ">")
                                                            under?     (str/includes? lab-val "<")
                                                            dry?       (str/includes? lab-val "DRY")
                                                            lab-val    (-> lab-val
                                                                         (str/replace "<" "")
                                                                         (str/replace ">" "")
                                                                         (str/replace "DRY" ""))

                                                            lab-val    (when (and lab-val (not= lab-val ""))
                                                                         (try
                                                                           (bigdec lab-val)
                                                                           (catch Exception _ lab-val)))
                                                            lab-result (if lab-val
                                                                         (cond-> {:value lab-val}
                                                                           over?
                                                                           (assoc :over true)
                                                                           under?
                                                                           (assoc :under true))
                                                                         (cond
                                                                           dry?
                                                                           {:dry true}))]
                                                        (assoc lab-result :type param-type)))]
                                   (when result
                                     [k result]))))
                         sv  (assoc sv :results frs)]
                     sv))]
      (->> rows
        (convert-bool :QACheck)))))

(comment
  ;(read-csv "import-resources/SSI-2019.csv" cols-SSI param-config-ssi)
  (read-csv "import-resources/SYRCL_Bacteria.csv" (get col-configs "SYRCL") (get param-configs "SYRCL")))

(defn gen-site-with-name
  ([cx project site-name]
   (let [tx    @(d/transact cx [{:db/id                     site-name
                                 :stationlookup/StationName site-name
                                 :stationlookup/Project     [:projectslookup/ProjectID project]}])
         db-id (get-in tx [:tempids site-name])]
     db-id)))

(defn site->txd [project {:keys [site-id site-name descript lat lon is-rm-site?] :as site-map}]
  (let [txd {:db/id                     (str (or site-id site-name))
             :stationlookup/Project     [:projectslookup/ProjectID project]
             :stationlookup/StationCode (str project "_" site-id)
             :org.riverdb/import-key    (str project "-site-" (or site-id site-name))}
        txd (cond-> txd
              site-id
              (assoc :stationlookup/StationID site-id)
              (or site-name descript)
              (assoc :stationlookup/StationName (or site-name descript))
              (and site-name descript)
              (assoc :stationlookup/StationName (str site-name " - " descript))
              lat
              (assoc :stationlookup/TargetLat (BigDecimal/valueOf ^double lat))
              lon
              (assoc :stationlookup/TargetLong (BigDecimal/valueOf ^double lon))
              descript
              (assoc :stationlookup/Description descript)
              is-rm-site?
              (assoc :stationlookup/Active true))]
    txd))

;; 153019  (> % 153018)

(defn proj-site->station-id [db project site-id]
  (let [site-id (if (string? site-id)
                  (parse-long site-id)
                  site-id)
        result  (d/q '[:find ?e .
                       :in $ ?proj ?site-id
                       :where
                       [?pj :projectslookup/ProjectID ?proj]
                       [?e :stationlookup/StationID ?site-id]
                       [?e :stationlookup/Project ?pj]]
                  db project site-id)]
    result))

(defn proj-site-name->station-id [db project site-name]
  (d/q '[:find ?e .
         :in $ ?proj ?site-name
         :where
         [?pj :projectslookup/ProjectID ?proj]
         [?e :stationlookup/StationName ?site-name]
         [?e :stationlookup/Project ?pj]]
    db project site-name))

(defn gen-site-with-map
  ([cx project site-map]
   (let [txd     (site->txd project site-map)
         temp-id (:db/id txd)
         tx      @(d/transact cx [txd])
         ;tx      (d/with (d/db cx) [txd])
         db-id   (get-in tx [:tempids temp-id])]
     db-id)))

;(defn gen-sites [cx project site-names]
;  (let [existing-sites (into {}
;                         (d/q '[:find ?name ?e
;                                :in $ ?proj
;                                :where
;                                [?pj :projectslookup/ProjectID ?proj]
;                                [?e :stationlookup/Project ?pj]
;                                [?e :stationlookup/StationName ?name]] (d/db cx) project))]
;    (into {} (for [site site-names]
;               (let [db-id (if-let [db-id (get existing-sites site)]
;                             db-id
;                             (gen-site-with-name cx project site))]
;                 [site db-id])))))

(defn gen-sites [cx project sites]
  (let [existing-sites {}
        ;;; generating all sites every time, but they use import-key so it should just update existing entities
        #_(into {}
            (d/q '[:find ?id ?e
                   :in $ ?proj
                   :where
                   [?pj :projectslookup/ProjectID ?proj]
                   [?e :stationlookup/Project ?pj]
                   [?e :stationlookup/StationID ?id]] (d/db cx) project))]
    (into {} (for [site sites]
               (let [site-id (:site-id site)
                     db-id   (if-let [db-id (get existing-sites site-id)]
                               db-id
                               (gen-site-with-map cx project site))]
                 [site-id db-id])))))



(defn import-field-results [constituent devType import-token vals]
  ;(debug "import-field-results" vals)
  (vec
    (for [i (range (count vals))]
      (let [import-key (str import-token "-" (inc i))]
        ;(debug import-key (double (get vals i)))
        {:org.riverdb/import-key         import-key
         :fieldresult/uuid               (d/squuid)
         :fieldresult/Result             (double (get vals i))
         :fieldresult/FieldReplicate     (inc i)}))))
         ;:fieldresult/ConstituentRowID   constituent}))))
         ;:fieldresult/SamplingDeviceCode devType}))))

;(defn import-field-results [const-table devType-table import-token-sv results]
;  (vec
;    (flatten
;      (for [[k {:keys [vals]}] results]
;        (let [constituent  (get const-table k)
;              devType      (get devType-table k)
;              import-token (str import-token-sv "-field-" (name k))]
;          (import-field-result-vals constituent devType import-token vals))))))

(defn import-lab-result [constituent import-token-sa {:keys [value over under dry]}]
  (let [res {:org.riverdb/import-key     (str import-token-sa "-1")
             :labresult/uuid               (d/squuid)
             :labresult/LabReplicate     1
             :labresult/ConstituentRowID constituent}
        res (cond-> res
              value
              (->
                (assoc :labresult/Result (double value))
                (assoc :labresult/SigFig (.precision value)))
              over
              (assoc :labresult/ResQualCode [:resquallookup/ResQualCode ">"])
              under
              (assoc :labresult/ResQualCode [:resquallookup/ResQualCode "<"])
              dry
              (->
                (assoc :labresult/ResQualCode [:resquallookup/ResQualCode "NA"])
                (assoc :labresult/LabResultComments "DRY")))]
    res))

;(defn import-lab-results [const-table import-token-sv results]
;  (vec
;    (flatten
;      (for [[k result] results]
;        (let [constituent (get const-table k)
;              import-key  (str import-token-sv "-lab-" (name k))]
;          (import-lab-result constituent import-key result))))))

(defn get-device-id [agency devID devType]
  ;(debug "get-device-id" agency devID devType)
  (let [eid (d/q '[:find e? .
                   :where
                   [?e :samplingdevice/CommonID devID]
                   [?e :samplingdevice/DeviceType devType]]
              (db))]
    (when-not eid
      (let [entity {:samplingdevice/CommonID devID
                    :samplingdevice/DeviceType devType
                    :samplingdevice/Active true
                    :samplingdevice/uuid (d/squuid)
                    :riverdb.entity/ns :entity.ns/samplingdevice}]
        (try
          (let [tx @(d/transact (cx) [entity])]
            (-> (:tempids tx) first second))
          (catch Exception _))))))

(defn import-samples [agency const-table devtype-table import-token-sv results]
  (vec
    (for [[k {:keys [type device] :as result}] results]
      (let [event-type    (cond
                            (= type :obs)
                            "FieldDescription"
                            :else
                            "WaterChem")
            sample-type   (cond
                            (= type :obs)
                            "FieldObs"
                            (= type :lab)
                            "Grab"
                            :else
                            "FieldMeasure")
            constituent   (get const-table k)
            import-key-sa (str import-token-sv "-" (name type) "-" (name k))
            devType       (get devtype-table k)
            sa-result     {:org.riverdb/import-key import-key-sa
                           :sample/uuid            (d/squuid)
                           :sample/EventType       [:eventtypelookup/EventType event-type]
                           :sample/SampleTypeCode  [:sampletypelookup/SampleTypeCode sample-type]
                           :sample/QCCheck         true
                           :sample/Constituent     constituent
                           :sample/SampleReplicate 0}]

        (cond-> sa-result
          (= type :field)
          (->
            (assoc :sample/FieldResults (import-field-results constituent devType import-key-sa (:vals result)))
            (assoc :sample/DeviceType devType)
            (cond->
              device
              (assoc :sample/DeviceID (get-device-id agency device devType))))
          (= type :lab)
          (assoc :sample/LabResults [(import-lab-result constituent import-key-sa result)]))))))



(defn import-csv-txds [db agency project filename]
  (debug "import-csv-txds")
  (let [data (read-csv filename (get col-configs agency) (get param-configs agency))]
    (vec
      ;; remove any that have no station
      (filter #(:sitevisit/StationID (first %))
        (for [dat data]
          (let [{:keys [svid site-id site-name SiteVisitDate Notes time results
                        StreamWidth UnitStreamWidth WaterDepth UnitWaterDepth
                        DataEntryDate DataEntryNotes DataEntryPerson QADate QACheck QAPerson]} dat
                import-token-sv (str project "-" (or svid (str site-id "-" SiteVisitDate)))
                SiteVisitDate   (parse-date SiteVisitDate)

                DataEntryDate   (parse-date DataEntryDate)
                DataEntryPerson (parse-long DataEntryPerson)
                QACheck         (parse-bool QACheck)
                QADate          (parse-date QADate)
                QAPerson        (parse-long QAPerson)

                WaterDepth      (parse-bigdec WaterDepth)
                StreamWidth     (parse-bigdec StreamWidth)
                time            time
                site-id-id      (when site-id (proj-site->station-id db project site-id))
                site-id-name    (when site-name (proj-site-name->station-id db project site-name))
                site-id         (or site-id-id site-id-name)
                _               (when-not site-id
                                  (warn "WARNING: missing :sitevisit/StationID for site-name " site-name))
                import-str      (str "RiverDB.org import from file: " filename)
                Notes           (if Notes
                                  (str Notes " \n " import-str)
                                  import-str)
                sv              {:org.riverdb/import-key      import-token-sv
                                 :sitevisit/uuid              (d/squuid)
                                 :sitevisit/ProjectID         [:projectslookup/ProjectID project]
                                 :sitevisit/AgencyCode        [:agencylookup/AgencyCode agency]
                                 :sitevisit/StationID         site-id
                                 :sitevisit/QACheck           true
                                 :sitevisit/CreationTimestamp (Date.)
                                 :sitevisit/StationFailCode   [:stationfaillookup/StationFailCode 0]
                                 :sitevisit/VisitType         [:sitevisittype/id 1]}
                sv              (cond-> sv
                                  time
                                  (assoc :sitevisit/Time time)
                                  SiteVisitDate
                                  (assoc :sitevisit/SiteVisitDate SiteVisitDate)

                                  Notes
                                  (assoc :sitevisit/Notes Notes)

                                  DataEntryNotes
                                  (assoc :sitevisit/DataEntryNotes DataEntryNotes)
                                  DataEntryDate
                                  (assoc :sitevisit/DataEntryDate DataEntryDate)
                                  DataEntryPerson
                                  (assoc :sitevisit/DataEntryPerson DataEntryPerson)

                                  QADate
                                  (assoc :sitevisit/QADate QADate)
                                  QACheck
                                  (assoc :sitevisit/QACheck QACheck)
                                  QAPerson
                                  (assoc :sitevisit/QAPerson QAPerson)

                                  StreamWidth
                                  (assoc :sitevisit/StreamWidth StreamWidth)
                                  UnitStreamWidth
                                  (assoc :sitevisit/UnitStreamWidth UnitStreamWidth)
                                  WaterDepth
                                  (assoc :sitevisit/WaterDepth WaterDepth)
                                  UnitWaterDepth
                                  (assoc :sitevisit/UnitWaterDepth UnitWaterDepth)

                                  (and StreamWidth UnitStreamWidth)
                                  (assoc :sitevisit/WidthMeasured true)

                                  (and WaterDepth UnitWaterDepth)
                                  (assoc :sitevisit/DepthMeasured true))
                ;import-token-fm (str import-token-sv "-field")
                ;f-results       (import-field-results (get param->const agency) (get param->devType agency) import-token-fm results)
                ;l-results       (import-lab-results (get param->const agency) (str import-token-sv "-lab") results)
                ;sample          [{:org.riverdb/import-key import-token-fm
                ;                  :sample/EventType       [:eventtypelookup/EventType "WaterChem"]
                ;                  :sample/SampleTypeCode  [:sampletypelookup/SampleTypeCode "FieldMeasure"]
                ;                  :sample/QCCheck         true
                ;                  :sample/SampleReplicate 0
                ;                  :sample/FieldResults    f-results}]
                samples         (import-samples agency (get param->const agency) (get param->devType agency) import-token-sv results)
                result          [(assoc sv :sitevisit/Samples samples)]]
            result))))))


(comment
  (first (import-csv-txds (db) "SSI" "SSI_1" "import-resources/SSI-2019.csv"))
  (first (import-csv-txds (db) "SSI" "SSI_BR" "import-resources/SSI_BR-2019.csv"))
  (first (import-csv-txds (db) "WCCA" "WCCA_1" "import-resources/WCCA-2019.csv"))
  (first (import-csv-txds (db) "SYRCL" "SYRCL_1" "import-resources/SYRCL_Bacteria.csv"))
  (first (import-csv-txds (db) "WCCA" "WCCA_1" "import-resources/WCCA_2019_Corrected.csv"))
  ;; server:
  (first (import-csv-txds (db) "WCCA" "WCCA_1" "resources/WCCA-2019.csv")))




;(defn import-csv-wcca [cx project filename]
;  (println "importing " project filename)
;  (doseq [txd (import-csv-wcca-txds (d/db cx) project filename)]
;    (let [tx @(d/transact cx txd)]
;      (print ".")
;      (flush)))
;  (println "\ndone"))



;(defn import-csv-ssi [cx project filename]
;  (println "importing " project filename)
;  (doseq [txd (import-csv-ssi-txds (d/db cx) project filename)]
;    (let [tx @(d/transact cx txd)]
;      (print ".")
;      (flush)))
;  (println "\ndone"))

(defn import-csv [cx agency project filename]
  (println "importing " agency project filename)
  (doseq [txd (import-csv-txds (d/db cx) agency project filename)]
    (let [tx @(d/transact cx txd)]
      (print ".")
      (flush)))
  (println "\ndone"))

(comment
  (import-csv-txds (db) "WCCA" "WCCA_1" "import-resources/WCCA-2019.csv")
  (import-csv (cx) "WCCA" "WCCA_1" "import-resources/WCCA-2019.csv")
  ;; server
  (import-csv (cx) "WCCA" "WCCA_1" "resources/WCCA-2019.csv"))

;(comment
;  (import-csv-ssi (cx) "SSI_1" "import-resources/SSI-2019.csv")
;  (import-csv-ssi (cx) "SSI_BR" "import-resources/SSI_BR-2019.csv"))

;(comment
;  (def data (import-csv (cx) "SSI" "SSI_1" "resources/migrations/SSI-2016.csv"))
;  (def wcca (import-csv (cx) "WCCA" "WCCA_1" "resources/migrations/WCCA-2016.csv"))
;  (import-sites-wcca "WCCA" "WCCA_1" "resources/import/WCCA-Sites-V13.csv")
;  (read-csv "resources/import/WCCA-2017.csv")
;  (import-csv-wcca "WCCA_1" "resources/import/WCCA-2016.csv")
;  (d/with (db) (nth (import-csv-wcca "WCCA_1" "resources/import/WCCA-2017.csv") 4))
;
;  (import-csv-ssi "SSI_1" "resources/import/SSI-2017.csv")
;
;  ;; count sitevisits since a date
;  (count (d/q '[:find [(pull ?e [*]) ...]
;                :where
;                [(> ?dt (java.util.Date. (java.util.Date/parse "2018/01/01")))]
;                [?e :sitevisit/ProjectID [:projectslookup/ProjectID "SSI_1"]]
;                [?e :sitevisit/SiteVisitDate ?dt]] (db))))



