(ns riverdb.api.import
  (:require
    [clojure.core.rrb-vector :as fv]
    [clojure-csv.core :as csv :refer [write-csv parse-csv]]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s]
    [clojure.tools.logging :as log :refer [debug info warn error]]
    [clojure.string :as str]
    [datomic.api :as d]
    [java-time :as jt]
    [riverdb.db :as rdb :refer [rpull pull-entities]]
    [riverdb.state :as state :refer [db cx]]
    [riverdb.util :refer [nest-by assoc-conj-by]]
    [theta.util :refer [parse-bool parse-long parse-double parse-date parse-bigdec]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [thosmos.util :as tu]
    [tick.core :as t])
  (:import (java.util Date)
           (java.io Reader File)
           (org.apache.commons.io FileUtils FileExistsException)
           (java.nio.file Files)))

;(def uri-dbf (or (dotenv/env :DATOMIC_URI_DBF) "datomic:free://localhost:4334/test-dbf"))


(def param-configs
  ":type is one of :field, :lab, :obs, :cont"
  {"SSI"   {:Air_Temp      {:order 0 :count 1 :name "Air"}
            :H2O_Temp      {:order 1 :count 6 :name "H2Otemp"}
            :H2O_Cond      {:order 2 :count 3 :name "Cond" :device "Cond_."}
            :H2O_DO        {:order 3 :count 3 :name "O2" :device "O2_."}
            :H2O_pH        {:order 4 :count 3 :name "pH" :device "pH_."}
            :H2O_Turb      {:order 5 :count 3 :name "Tur" :device "Tur_."}
            :H2O_PO4       {:order 6 :count 3 :name "PO4"}
            :H2O_NO3       {:order 6 :count 3 :name "NO3"}
            :TotalColiform {:order 7 :count 1 :type :lab :name "TotalColiform"}
            :EColi         {:order 7 :count 1 :type :lab :name "EColi"}}
   "WCCA"  {:Air_Temp   {:order 0 :count 1 :name "AirTemp_C"}
            :TDS        {:order 2 :count 3 :name "TDS_ppm" :device "TDS_EquipID"}
            :DO_mgL     {:order 3 :count 3 :name "DOxy_mgL" :device "DOxy_EquipID"}
            :DO_Percent {:order 3 :count 3 :name "DOxy_Percent" :device "DOxy_EquipID"}
            :H2O_Temp   {:order 1 :count 3 :name "H2OTemp_C" :device "pH_EquipID"}
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
            :TotalColiform [:constituentlookup/ConstituentCode "5-57-23-2-7"] ;; "5-56-23-20-7" "5-57-23-2-7"
            :EColi         [:constituentlookup/ConstituentCode "5-57-464-0-7"]}
   "WCCA"  {:Air_Temp   [:constituentlookup/ConstituentCode "10-42-100-0-31"]
            :TDS        [:constituentlookup/ConstituentCode "5-42-107-0-100"]
            :DO_mgL     [:constituentlookup/ConstituentCode "5-42-38-0-6"]
            :DO_Percent [:constituentlookup/ConstituentCode "5-42-38-0-13"]
            :H2O_Temp   [:constituentlookup/ConstituentCode "5-42-100-0-31"]
            :H2O_TempDO [:constituentlookup/ConstituentCode "5-42-100-0-31"]
            :pH         [:constituentlookup/ConstituentCode "5-42-78-0-0"]
            :Turb       [:constituentlookup/ConstituentCode "5-42-108-0-9"]}
   "SYRCL" {:TotalColiform [:constituentlookup/ConstituentCode "5-57-23-2-7"]
            :EColi         [:constituentlookup/ConstituentCode "5-57-464-0-7"]
            :Enterococcus  [:constituentlookup/ConstituentCode "5-9000-9002-0-7"]}})

(def param->devType
  {"WCCA" {:Air_Temp   [:samplingdevicelookup/SampleDevice "SupcoTemp"]
           :TDS        [:samplingdevicelookup/SampleDevice "TDSTestr 11"]
           :DO_mgL     [:samplingdevicelookup/SampleDevice "YSI"]
           :DO_Percent [:samplingdevicelookup/SampleDevice "YSI"]
           :H2O_Temp   [:samplingdevicelookup/SampleDevice "HannaPH"]
           :H2O_TempDO [:samplingdevicelookup/SampleDevice "YSI"]
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
   "MonitorSite" :active})

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
      (assoc c map-key (parse-bool field))
      c)))

(defn read-sites-csv [site-cols filename]
  (with-open [^java.io.Reader r (clojure.java.io/reader filename)]
    (let [csv    (parse-csv r)
          header (first csv)
          ncols  (count header)
          hidx   (into {} (for [i (range ncols)]
                            [(get header i) i]))
          csv    (vec (rest csv))
          sites  (for [row csv]
                   (into {}
                     (for [i (range ncols)]
                       (when-let [col-key (get site-cols (get header i))]
                         (let [col-data (get row i)]
                           (when (and col-data (not= col-data ""))
                             [col-key col-data]))))))]
      (->> sites))))
;convert-coords
;convert-site-ids
;(convert-bool :is-rm-site?)))))


(defn read-csv [filename col-config param-config]
  (debug "READ CSV" filename)
  (with-open
    [^Reader r (clojure.java.io/reader filename)]
    (let [csv    (parse-csv r)
          header (first csv)
          hidx   (into {} (for [i (range (count header))]
                            [(get header i) i]))
          csv    (vec (rest csv))
          ncols  (count header)
          quals  (->
                   (remove #(contains? #{"" "<=" ">="} %)
                     (d/q '[:find [?qual ...]
                            :where
                            [_ :resquallookup/ResQualCode ?qual]]
                       (db)))
                   vec
                   ;; put <= and >= at the end to match after < = >
                   (conj "<=" ">="))
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
                                                          {:param-name   param-name
                                                           :sample-count sample-count
                                                           :vals         fld-vals
                                                           :type         param-type
                                                           :device       device-val}))

                                                      (= param-type :lab)
                                                      (let [csv-idx    (get hidx param-name)
                                                            lab-val    (get row csv-idx)
                                                            lab-val    (when (not= lab-val "")
                                                                         lab-val)
                                                            val?       (some? lab-val)

                                                            qual?      (when val?
                                                                         (reduce
                                                                           (fn [res qual]
                                                                             (if (clojure.string/includes? lab-val qual)
                                                                               qual
                                                                               res))
                                                                           nil quals))

                                                            lab-val    (if qual?
                                                                         (str/replace lab-val qual? "")
                                                                         lab-val)

                                                            lab-val    (when (not= lab-val "")
                                                                         lab-val)

                                                            lab-val    (when val?
                                                                         (parse-bigdec lab-val))

                                                            lab-result (when val?
                                                                         (cond->
                                                                           {:param-name param-name
                                                                            :type       param-type}
                                                                           lab-val
                                                                           (assoc :value lab-val)
                                                                           qual?
                                                                           (assoc :qual [:resquallookup/ResQualCode qual?])))]

                                                        lab-result))]
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
                                 :stationlookup/StationName site-name}
                                {:projectslookup/ProjectID project
                                 :projectslookup/Stations  site-name}])
         db-id (get-in tx [:tempids site-name])]
     db-id)))

(def site-cols-syrcl-logger
  {"StationID"   :site-id
   "Subbasin"    :subbasin
   "StationName" :site-name
   "Years"       :years
   "Lat"         :lat
   "Lon"         :lon})

(defn parse-station-id [station-str]
  "parses a station-id string into a double, multiplies by 100, and returns a long"
  (long (* 100 (Double/parseDouble (re-find #"\d+\.\d+|\d+" station-str)))))

(defn site->txd [agency project {:keys [site-id site-name subbasin years descript lat lon active]} [& {active-override :active}]]
  (let [station_code (str project "_" site-id)
        txd          {:db/id                     station_code
                      :stationlookup/StationCode station_code
                      :stationlookup/Agency      [:agencylookup/AgencyCode agency]
                      :stationlookup/uuid        (d/squuid)
                      :riverdb.entity/ns         :entity.ns/stationlookup
                      :org.riverdb/import-key    (str project "-site-" (or site-id site-name))}
        active?      (not-empty (remove nil? [active-override active]))
        txd          (cond-> txd
                       site-id
                       (->
                         (assoc :stationlookup/StationID site-id)
                         (assoc :stationlookup/StationIDLong (parse-station-id site-id)))
                       site-name
                       (assoc :stationlookup/StationName site-name)
                       (and (not site-name) descript)
                       (assoc :stationlookup/StationName descript)
                       (and (not site-name) (not descript) site-id)
                       (assoc :stationlookup/StationName site-id)
                       lat
                       (assoc :stationlookup/TargetLat (bigdec lat))
                       lon
                       (assoc :stationlookup/TargetLong (bigdec lon))
                       descript
                       (assoc :stationlookup/Description descript)
                       (some? active?)
                       (assoc :stationlookup/Active (first active?))
                       subbasin
                       (assoc :stationlookup/ForkTribGroup subbasin)
                       years
                       (assoc :stationlookup/StationComments years))]
    txd))

(defn syrcl-logger [station-code station-name]
  {:org.riverdb/import-key (str "LOGGER_" station-code)
   :logger/name            (str "Logger: " station-name),
   :logger/projectRef      #:db{:id 17592186302687},
   :logger/parameterRef    #:db{:id 17592186302688},
   :logger/deviceTypeRef   #:db{:id 17592186302685},
   :logger/stationRef      [:stationlookup/StationCode station-code]
   :logger/uuid            (d/squuid)
   :riverdb.entity/ns      :entity.ns/logger})

(comment
  ;; import SYRCL thermalogger sites and loggers
  (let [sites       (read-sites-csv site-cols-syrcl-logger "import-resources/SYRCL Temp Logger Sites.csv")
        site-txds   (for [site-m sites]
                      (site->txd "SYRCL" "SYRCL_THERMAS" site-m))
        proj-txds   (for [{:stationlookup/keys [StationCode]} site-txds]
                      [:db/add
                       [:projectslookup/ProjectID "SYRCL_THERMAS"]
                       :projectslookup/Stations
                       [:stationlookup/StationCode StationCode]])
        logger-txds (for [site-txd site-txds]
                      (let [{:stationlookup/keys [StationCode StationName]} site-txd]
                        (syrcl-logger StationCode StationName)))]
    (vec
      (for [txds [site-txds proj-txds logger-txds]]
        (:tempids @(d/transact (cx) txds))))))


(defn proj-site->station-id [stations station-id]
  (debug "proj-site->station-id" station-id)
  (when (seq stations)
    (loop [sts stations]
      (let [{:stationlookup/keys [StationID]} (first sts)]
        (debug "station-id loop" station-id "=?" StationID)
        (if
          (= StationID station-id)
          station-id
          (recur (rest sts)))))))

(defn proj-site-name->station-id [stations site-name]
  (debug "proj-site-name->station-id" site-name)
  (when (seq stations)
    (loop [sts stations]
      (let [{:db/keys [id] :stationlookup/keys [StationID StationName uuid] :as st} (first sts)]
        (debug "site-name loop" site-name "=?" StationName StationID)
        (if
          (or
            (= site-name StationName)
            (= site-name StationID))
          uuid
          (recur (rest sts)))))))

;(defn gen-site-with-map
;  ([cx agency project site-map]
;   (let [txd     (site->txd agency project site-map)
;         temp-id (:db/id txd)
;         tx      @(d/transact cx [txd])
;         ;tx      (d/with (d/db cx) [txd])
;         db-id   (get-in tx [:tempids temp-id])]
;     db-id)))
;
;
;(defn gen-sites [cx agency project sites]
;  (let [existing-sites {}]
;        ;;; generating all sites every time, but they use import-key so it should just update existing entities
;    (into {} (for [site sites]
;               (let [site-id (:site-id site)
;                     db-id   (if-let [db-id (get existing-sites site-id)]
;                               db-id
;                               (gen-site-with-map cx agency project site))]
;                 [site-id db-id])))))

(defn find-resqualcode [code]
  (debug "find-resqualcode" code)
  (d/q '[:find ?e .
         :in $ ?id
         :where
         [?e :resquallookup/ResQualCode ?id]]
    (db) code))

(defn import-field-results [import-token {:keys [vals fm-values]}]
  ;(debug "import-field-results" vals)
  (vec
    (for [i (range (count vals))]
      (let [import-key   (str import-token "-" (inc i))
            val          (get vals i)
            fm-value     (get fm-values i)
            qualcode     (when (:qual fm-value) (find-resqualcode (:qual fm-value)))
            result-val   val
            result       (cond->
                           {:org.riverdb/import-key     import-key
                            :riverdb.entity/ns          :entity.ns/fieldresult
                            :fieldresult/uuid           (d/squuid)
                            :fieldresult/FieldReplicate (inc i)}
                           result-val
                           (assoc :fieldresult/Result (double result-val))
                           qualcode
                           (assoc :fieldresult/ResQualCode qualcode))]
        result))))
;:fieldresult/ConstituentRowID   constituent}))))
;:fieldresult/SamplingDeviceCode devType}))))

(defn import-lab-result [import-token-sa {:keys [value qual]}]
  (let [res {:org.riverdb/import-key (str import-token-sa "-1")
             :riverdb.entity/ns      :entity.ns/labresult
             :labresult/uuid         (d/squuid)
             :labresult/LabReplicate 1}
        res (cond-> res
              value
              (->
                (assoc :labresult/Result (double value))
                (assoc :labresult/SigFig (.precision value)))
              qual
              (assoc :labresult/ResQualCode qual))]
    res))

;(defn import-lab-results [const-table import-token-sv results]
;  (vec
;    (flatten
;      (for [[k result] results]
;        (let [constituent (get const-table k)
;              import-key  (str import-token-sv "-lab-" (name k))]
;          (import-lab-result constituent import-key result))))))

(defn get-or-create-device-id [agency devType devID create-devices?]
  ;(debug "get-device-id" agency devType devID)
  (let [eid (d/q '[:find ?e .
                   :in $ ?devID ?devType ?agency
                   :where
                   [?e :samplingdevice/CommonID ?devID]
                   [?e :samplingdevice/DeviceType ?devType]
                   [?e :samplingdevice/Agency ?ag]
                   [?ag :agencylookup/AgencyCode ?agency]]
              (db) devID devType agency)]
    (if eid
      eid
      (if create-devices?
        (let [entity {:samplingdevice/CommonID   devID
                      :samplingdevice/DeviceType devType
                      :samplingdevice/Active     true
                      :samplingdevice/uuid       (d/squuid)
                      :samplingdevice/Agency     [:agencylookup/AgencyCode agency]
                      :riverdb.entity/ns         :entity.ns/samplingdevice}]
          (try
            (let [_  (debug "Create device" agency devType devID)
                  tx @(d/transact (cx) [entity])]
              (-> (:tempids tx) first second))
            (catch Exception ex (error ex "CREATE DEVICE FAILED"))))
        (throw (Exception. (str "Failed to find device for devType: " devType ", devID: " devID)))))))

(defn get-or-create-parameter [{:keys [param-name sample-count project constituent devType sampleType create-params?]}]
  ;(debug "get-or-create-parameter" param-name constituent)
  (let [eid? (if devType
               (d/q '[:find ?e .
                      :in $ ?project ?const ?devType
                      :where
                      [?pj :projectslookup/ProjectID ?project]
                      [?pj :projectslookup/Parameters ?e]
                      [?e :parameter/Constituent ?const]
                      [?e :parameter/DeviceType ?devType]]
                 (db) project constituent devType)
               (d/q '[:find ?e .
                      :in $ ?project ?const
                      :where
                      [?pj :projectslookup/ProjectID ?project]
                      [?pj :projectslookup/Parameters ?e]
                      [?e :parameter/Constituent ?const]]
                 (db) project constituent))
        eid  (if eid?
               (do
                 ;(debug "FOUND PARAMETER" eid?)
                 eid?)
               (if create-params?
                 (try
                   (let [entity  (cond-> {:db/id                 "param"
                                          :riverdb.entity/ns     :entity.ns/parameter
                                          :parameter/uuid        (d/squuid)
                                          :parameter/Active      true
                                          :parameter/Constituent constituent
                                          :parameter/Name        param-name
                                          :parameter/NameShort   (clojure.string/replace param-name #" " "")
                                          :parameter/SampleType  [:sampletypelookup/SampleTypeCode sampleType]}
                                   devType
                                   (assoc :parameter/DeviceType devType)
                                   sample-count
                                   (assoc :parameter/Replicates sample-count))
                         proj-tx {:projectslookup/ProjectID  project
                                  :projectslookup/Parameters [{:db/id "param"}]}
                         ;_ (debug "CREATE PARAMETER TX" param-name [entity proj-tx])
                         ;eid [:parameter/uuid (:parameter/uuid entity)]

                         tx      @(d/transact (cx) [entity proj-tx])
                         eid     (get (:tempids tx) "param")]

                     (debug "CREATE PARAMETER" param-name eid entity)
                     eid)

                   (catch Exception ex (error ex "CREATE PARAMETER FAILED")))
                 (throw (Exception. (str "Failed to find parameter for " param-name)))))]
    eid))




(defn import-samples [{:keys [agency project import-token-sv results create-devices?]}]
  ;(debug "IMPORT SAMPLE")
  (vec
    (for [[k {:keys [type device parameter] :as result}] results]
      (let [param-name    (:parameter/NameShort parameter)
            event-type    (cond
                            (= type :obs)
                            "FieldDescription"
                            :else
                            "WaterChem")
            type          (or type :field)
            sample-type   (cond
                            (= type :obs)
                            "FieldObs"
                            (= type :lab)
                            "Grab"
                            :else
                            "FieldMeasure")
            constituent   (get-in parameter [:parameter/Constituent :db/id])
            import-key-sa (str import-token-sv "-" (name type) "-" (name k))
            devType       (get-in parameter [:parameter/DeviceType :db/id])
            sa-result     {:org.riverdb/import-key import-key-sa
                           :riverdb.entity/ns      :entity.ns/sample
                           :sample/uuid            (d/squuid)
                           :sample/EventType       [:eventtypelookup/EventType event-type]
                           :sample/SampleTypeCode  [:sampletypelookup/SampleTypeCode sample-type]
                           :sample/QCCheck         true
                           :sample/Constituent     constituent
                           :sample/SampleReplicate 0}
            device-id     (when device (get-or-create-device-id agency devType device create-devices?))
            sample        (cond-> sa-result
                            parameter
                            (assoc :sample/Parameter (:db/id parameter))
                            (= type :field)
                            (->
                              (assoc :sample/FieldResults (import-field-results import-key-sa result))
                              (cond->
                                devType
                                (assoc :sample/DeviceType devType)
                                device-id
                                (assoc :sample/DeviceID device-id)))
                            (= type :lab)
                            (assoc :sample/LabResults [(import-lab-result import-key-sa result)]))]
        ;(debug "SAMPLE DONE" param-name)
        sample))))



(defn import-csv-txds [db agency-code project-code filename [& {:keys [create-devices? create-params?] :as opts}]]
  (debug "import-csv-txds")
  (let [data      (read-csv filename (get col-configs agency-code) (get param-configs agency-code))
        project   (rpull [:projectslookup/ProjectID project-code] [:db/id :projectslookup/ParentProjectID])
        {:db/keys [id] :projectslookup/keys [ParentProjectID]} project
        ;; get stations from parent or this project
        station-q [:stationlookup/StationID
                   :stationlookup/StationIDLong
                   :stationlookup/StationCode
                   :stationlookup/StationName]
        proj-id   (or (:db/id ParentProjectID) id)
        stations  (d/q '[:find [(pull ?e qu) ...]
                         :in $ qu ?pj
                         :where
                         [?pj :projectslookup/Stations ?e]]
                    db station-q proj-id)]
    (vec
      ;; remove any that have no station
      (filter #(:sitevisit/StationID (first %))
        (for [dat data]
          (let [{:keys [svid site-id site-name SiteVisitDate Notes time results
                        StreamWidth UnitStreamWidth WaterDepth UnitWaterDepth
                        DataEntryDate DataEntryNotes DataEntryPerson QADate QACheck QAPerson]} dat
                import-token-sv (str project-code "-" (or svid (str site-id "-" SiteVisitDate)))
                SiteVisitDate   (parse-date SiteVisitDate)
                _               (when-not SiteVisitDate
                                  (throw (Exception. (str "Missing :sitevisit/SiteVisitDate for site-name " site-name))))

                DataEntryDate   (parse-date DataEntryDate)
                DataEntryPerson (parse-long DataEntryPerson)
                QACheck         (parse-bool QACheck)
                QADate          (parse-date QADate)
                QAPerson        (parse-long QAPerson)

                WaterDepth      (parse-bigdec WaterDepth)
                StreamWidth     (parse-bigdec StreamWidth)
                time            time
                site-id-id      (when site-id (proj-site->station-id stations site-id))
                site-id-name    (when site-name (proj-site-name->station-id stations site-name))
                site-id         (or site-id-id site-id-name)
                _               (when-not site-id
                                  (throw (Exception. (str "Failed to find StationID for site-name " site-name))))
                import-str      (str "RiverDB.org import from file: " filename)
                Notes           (if Notes
                                  (str Notes " \n " import-str)
                                  import-str)
                sv              {:org.riverdb/import-key      import-token-sv
                                 :sitevisit/uuid              (d/squuid)
                                 :sitevisit/ProjectID         [:projectslookup/ProjectID project-code]
                                 :sitevisit/AgencyCode        [:agencylookup/AgencyCode agency-code]
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
                samples         (import-samples
                                  {:agency          agency-code
                                   :project         project-code
                                   :devtype-table   (get param->devType agency-code)
                                   :const-table     (get param->const agency-code)
                                   :import-token-sv import-token-sv
                                   :results         results
                                   :create-devices? create-devices?
                                   :create-params?  create-params?})
                result          [(assoc sv :sitevisit/Samples samples)]]
            result))))))




(defn parse-sitevisit-cols [sv config row]
  sv)

(defn parse-parameter-cols [sv {:keys [params col-config] :as config} row]
  ;(debug "parse-parameter-cols v1")
  (let [quals (->
                (remove #(contains? #{"" "<=" ">="} %)
                  (d/q '[:find [?qual ...]
                         :where
                         [_ :resquallookup/ResQualCode ?qual]]
                    (db)))
                vec
                ;; put <= and >= at the end to match after < = >
                (conj "<=" ">="))]
    (reduce
      (fn [sv [col {:keys [type field] :as cfg}]]
        (let [p        (get params col)
              {:parameter/keys [NameShort SampleType]} p
              samptype (:db/ident SampleType)


              sv       (cond

                         (and (= type "field"))
                         (assoc sv field (get row col))

                         (and (= type "result") p)
                         (let [val (get row col)]
                           (if (or (not val) (= val ""))
                             sv
                             (case samptype
                               :sampletypelookup.SampleTypeCode/FieldMeasure
                               (let [fm-val val
                                     qual?  (reduce
                                              (fn [res qual]
                                                (if (clojure.string/includes? fm-val qual)
                                                  qual
                                                  res))
                                              nil quals)

                                     fm-val (if qual?
                                              (str/replace fm-val qual? "")
                                              fm-val)

                                     fm-val (when (not= fm-val "")
                                              fm-val)

                                     bd-val (parse-bigdec fm-val)]
                                 (-> sv
                                   (assoc-in [:results NameShort :parameter] p)
                                   (update-in [:results NameShort :type] :field)
                                   (update-in [:results NameShort :vals] (fnil conj []) bd-val)
                                   (update-in [:results NameShort :sample-count] (fnil inc 0))
                                   (update-in [:results NameShort :fm-values] (fnil conj [])
                                     {:qual   qual?
                                      :value  bd-val
                                      :fm-val fm-val
                                      :val    val})
                                   (cond->
                                     qual?
                                     (update-in [:results NameShort :quals] (fnil conj []) qual?))))
                               :sampletypelookup.SampleTypeCode/Grab
                               (let [lab-val    val
                                     lab-result {:parameter p
                                                 :type      :lab}
                                     qual?      (when lab-val
                                                  (reduce
                                                    (fn [res qual]
                                                      (if (clojure.string/includes? lab-val qual)
                                                        qual
                                                        res))
                                                    nil quals))

                                     lab-val    (if qual?
                                                  (str/replace lab-val qual? "")
                                                  lab-val)

                                     lab-val    (when (not= lab-val "")
                                                  lab-val)

                                     lab-val    (when lab-val
                                                  (parse-bigdec lab-val))

                                     lab-result (cond-> lab-result
                                                  lab-val
                                                  (assoc :value lab-val)
                                                  qual?
                                                  (assoc :qual [:resquallookup/ResQualCode qual?]))]
                                 (assoc-in sv [:results NameShort] lab-result))
                               (assoc-in sv [:results NameShort] (merge cfg {:error "unhandled param type"})))))

                         (and (= type "device") p)
                         (assoc-in sv [:results NameShort :device] val)

                         (or (= type "date") (= type "station"))
                         sv
                         :else
                         (assoc-in sv [:unhandled-cols col] cfg))]
          sv))
      sv (sort col-config))))



(defn check-sv-dupes [config]
  ;(debug "check-sv-dupes" (count (get-in config [:csv :data])) "rows")
  (let [{:keys [project date-column station-source station-column station-id csv]} config
        {:keys [data]} csv
        st-dates (reduce
                   (fn [st-dates row]
                     (let [date       (parse-date (get row date-column))
                           ;; get column-based station id
                           station-id (if (= station-source "column")
                                        (get row station-column)
                                        station-id)]
                       (conj st-dates [station-id date])))
                   [] data)
        dupes    (set (d/q '[:find ?stid ?date
                             :in $ ?proj [[?stid ?date]]
                             :where
                             [?pj :projectslookup/ProjectID ?proj]
                             [?pj :projectslookup/Stations ?st]
                             (or
                               [?st :stationlookup/StationID ?stid]
                               [?st :stationlookup/StationName ?stid])
                             [?e :sitevisit/SiteVisitDate ?date]
                             [?e :sitevisit/ProjectID ?pj]
                             [?e :sitevisit/StationID ?st]]
                        (db) project st-dates))]
    (if (seq dupes)
      (let []
        (debug (format "FOUND %d DUPES" (count dupes)))
        (assoc config :dupes dupes))
      config)))



(defn csv->data [config filename]
  (debug "csv->data")
  (let [{:keys [csv date-column station-source
                station-column station-id project dupes]} config
        {:keys [data]} csv

        svs (reduce
              (fn [svs row]
                (let [dateStr    (get row date-column)
                      date       (parse-date dateStr)
                      ;; set column-based station id
                      site-name  (if (= station-source "column")
                                   (get row station-column))
                      station-id (if (= station-source "column")
                                   site-name
                                   station-id)

                      sv         {:date       date
                                  :dateStr    dateStr
                                  :station-id station-id
                                  :site-name  site-name}

                      ;dupes      (check-sv-dupes project station-id date)
                      dupe?      (contains? dupes [station-id date])]
                  ;_ (debug "DUPE" (clojure.string/join "-" [project station-id dateStr]) [station-id date])]

                  (if dupe?
                    svs
                    (conj svs
                      (-> sv
                        (parse-sitevisit-cols config row)
                        (parse-parameter-cols config row))))))
              [] data)]
    ;(debug "FIRST" (first svs) "SECOND" (second svs) "LAST" (last svs))
    #_(->> svs
        (convert-bool :QACheck))
    svs))



(defn data->txds [config data]
  (log/debug "data->txds" (count data))
  (let [{project-code :project
         agency-code  :agency
         project      :project-m
         :keys        [create-devices?]} config

        ;; get stations from this project
        station-q [:stationlookup/uuid
                   :stationlookup/StationID
                   :stationlookup/StationName]
        proj-id   (:db/id project)
        stations  (d/q '[:find [(pull ?e qu) ...]
                         :in $ qu ?pj
                         :where
                         [?pj :projectslookup/Stations ?e]]
                    (db) station-q proj-id)]

    (vec
      ;; remove any that have no station
      ; filter #(:sitevisit/StationID (first %))
      (map-indexed
        (fn [idx dat]
          (debug idx (:svid dat))
          (try
            (let [{:keys [svid station-id site-name date dateStr Notes time results
                          StreamWidth UnitStreamWidth WaterDepth UnitWaterDepth
                          DataEntryDate DataEntryNotes DataEntryPerson QADate QACheck QAPerson]} dat
                  import-token-sv (str project-code "-" (or svid (str station-id "-" dateStr)))
                  _               (debug "importing sitevisit" idx import-token-sv)
                  SiteVisitDate   date
                  _               (when-not SiteVisitDate
                                    (throw (Exception. (str "Missing date field for station-id " station-id))))

                  _               (debug "BEGINNING SV generation for " "station-id" station-id "site-name" site-name)

                  DataEntryDate   (parse-date DataEntryDate)
                  DataEntryPerson (parse-long DataEntryPerson)
                  QACheck         (parse-bool QACheck)
                  QADate          (parse-date QADate)
                  QAPerson        (parse-long QAPerson)

                  WaterDepth      (parse-bigdec WaterDepth)
                  StreamWidth     (parse-bigdec StreamWidth)

                  _               (debug "PARSED SV fields")

                  time            time
                  site-uuid       (when site-name (proj-site-name->station-id stations site-name))
                  ;site-id-id      (when station-id (proj-site->station-id stations station-id))
                  ;site-id         (or site-id-name site-id-id)

                  _               (debug "BEGINNING TXD generation for " (pr-str site-name) (pr-str site-uuid))

                  _               (when-not site-uuid
                                    (throw (Exception. (str "Failed to find project station for station " site-name))))
                  sv              {:org.riverdb/import-key      import-token-sv
                                   :riverdb.entity/ns           :entity.ns/sitevisit,
                                   :sitevisit/uuid              (d/squuid)
                                   :sitevisit/ProjectID         [:projectslookup/ProjectID project-code]
                                   :sitevisit/AgencyCode        [:agencylookup/AgencyCode agency-code]
                                   :sitevisit/StationID         [:stationlookup/uuid site-uuid]
                                   :sitevisit/QACheck           true
                                   :sitevisit/CreationTimestamp (Date.)
                                   :sitevisit/StationFailCode   [:stationfaillookup/StationFailCode 0]
                                   :sitevisit/VisitType         [:sitevisittype/id 1]}
                  ;_               (debug "SV" sv)
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
                  _               (debug "SV pre-samples" sv)
                  samples         (import-samples
                                    {:agency          agency-code
                                     :project         project-code
                                     :import-token-sv import-token-sv
                                     :results         results
                                     :create-devices? create-devices?})
                  sv              (assoc sv :sitevisit/Samples samples)]
              sv)
            (catch Exception ex
              (log/error ex)
              {:error "something happened"})))
        data))))

(defn remove-repeaters [data]
  (let [[uniq data] (reduce
                      (fn [[uniq data] {:keys [station-id date] :as row}]
                        (let [token [station-id date]]
                          (if (contains? uniq token)
                            (do
                              (debug "REPEAT" token)
                              [uniq data])
                            [(conj uniq token) (conj data row)])))
                      [#{} []] data)]
    data))

(defn process-sitevisit-file [config file]
  (debug "process-sitevisit-file")
  (let [filename (:filename file)
        config   (check-sv-dupes config)
        ;_        (debug "FIRST DUPES" (take 5 (:dupes config)))
        data     (csv->data config filename)
        data     (remove-repeaters data)
        _        (debug "FIRST DATA" (first data))
        txds     (data->txds config data)
        _        (debug "FIRST TXD" (first txds))]
    ;_        (debug (count txds) "TXDS" txds)]
    (assoc config :txds txds)))




(defsc Analyte [_ _]
  {:query [:db/id
           :analytelookup/AnalyteCode
           :analytelookup/AnalyteName
           :analytelookup/AnalyteShort]})

(defn get-context [{agency-code :agency project-code :project
                    :keys       [station-id station-ids station-source]}]
  (let [agency      (rpull [:agencylookup/AgencyCode agency-code]
                      [:db/id
                       :agencylookup/AgencyCode])
        station-ids (if (and (= station-source "select") station-id)
                      [station-id]
                      station-ids)
        stations    (when (seq station-ids)
                      (debug "GETTING STATIONS" (pr-str station-ids) project-code)
                      (let [qu   [:db/id
                                  :stationlookup/StationID
                                  :stationlookup/StationIDLong
                                  :stationlookup/StationCode
                                  :stationlookup/StationName]
                            find '[:find (pull ?e qu) .
                                   :in $ ?st ?pjc qu
                                   :where
                                   [?pj :projectslookup/ProjectID ?pjc]
                                   [?pj :projectslookup/Stations ?e]
                                   [?e :stationlookup/StationID ?st]]]
                        (debug "FIND" find)
                        (for [st station-ids]
                          (d/q find (db) st project-code qu))))
        project     (rpull [:projectslookup/ProjectID project-code]
                      [:db/id
                       ;:projectslookup/AgencyRef
                       ;:projectslookup/Name
                       :projectslookup/ProjectID
                       {:projectslookup/ProjectType ['*]}
                       ;{:projectslookup/SampleTypes [:db/ident]}
                       :projectslookup/Stations
                       {:projectslookup/Parameters
                        [:db/id
                         :parameter/NameShort
                         ;:parameter/Replicates
                         {:parameter/Constituent
                          [:db/id
                           :constituentlookup/ConstituentCode
                           :constituentlookup/Name]}
                         ;{:constituentlookup/AnalyteCode
                         ; [:db/id
                         ;  :analytelookup/AnalyteCode
                         ;  :analytelookup/AnalyteName
                         ;  :analytelookup/AnalyteShort]}
                         ;{:constituentlookup/MatrixCode
                         ; [:db/id
                         ;  :matrixlookup/MatrixCode
                         ;  :matrixlookup/MatrixName
                         ;  :matrixlookup/MatrixShort]}
                         ;{:constituentlookup/UnitCode
                         ; [:db/id
                         ;  :unitlookup/UnitCode
                         ;  :unitlookup/Unit]}]}
                         {:parameter/DeviceType
                          [:db/id
                           :samplingdevicelookup/SampleDevice
                           :samplingdevicelookup/DeviceMax
                           :samplingdevicelookup/DeviceMin]}
                         {:parameter/SampleType [:db/ident]}]}])
        #_project     #_(rpull [:projectslookup/ProjectID project-code]
                          [:db/id
                           :projectslookup/AgencyCode
                           :projectslookup/AgencyRef
                           :projectslookup/Name
                           :projectslookup/ProjectID
                           {:projectslookup/ProjectType ['*]}
                           {:projectslookup/SampleTypes
                            [:db/id
                             :db/ident
                             :sampletypelookup/Name
                             :sampletypelookup/SampleTypeCode]}
                           :projectslookup/Stations
                           {:projectslookup/Parameters
                            [:db/id
                             :parameter/Active
                             :parameter/Name
                             :parameter/NameShort
                             :parameter/Replicates
                             :parameter/ReplicatesEntry
                             {:parameter/Constituent
                              [:constituentlookup/Active
                               {:constituentlookup/AnalyteCode
                                [:db/id
                                 :analytelookup/AnalyteCode
                                 :analytelookup/AnalyteName
                                 :analytelookup/AnalyteShort]}
                               {:constituentlookup/MatrixCode
                                [:db/id
                                 :matrixlookup/MatrixCode
                                 :matrixlookup/MatrixName
                                 :matrixlookup/MatrixShort]}
                               :constituentlookup/Name
                               {:constituentlookup/UnitCode
                                [:db/id
                                 :unitlookup/UnitCode
                                 :unitlookup/Unit]}]}
                             {:parameter/DeviceType
                              [:db/id
                               :samplingdevicelookup/SampleDevice
                               :samplingdevicelookup/DeviceMax
                               :samplingdevicelookup/DeviceMin]}
                             {:parameter/SampleType
                              [:db/id
                               :db/ident
                               :sampletypelookup/Name
                               :sampletypelookup/SampleTypeCode]}]}])]
    {:agency   agency
     :project  project
     :stations stations}))

(defn parse-upload [{:keys [skip-line]} filename]
  (with-open
    [^Reader r (clojure.java.io/reader filename)]
    (let [csv    (parse-csv r)
          header (if skip-line
                   (second csv)
                   (first csv))
          data   (vec
                   (if skip-line
                     (rest (rest csv))
                     (rest csv)))]
      {:header header :data data})))

(defn move-file [^File src ^File dest]
  (let [destDir (.getParentFile dest)] ;
    (when
      (not (.exists destDir))
      (.mkdirs destDir))
    (.renameTo
      src
      dest)))

(defn parse-station-ids [files]
  (let [parsedIDs (try
                    (vec
                      (for [file files]
                        (let [filename (:file/name file)]
                          (subs filename 0 (clojure.string/index-of filename "_")))))
                    (catch Exception _))]))

#_(d/transact (cx)
    [{:db/id                "1"
      :logger/name          "Temp Logger 101 Above Scotchman Creek"
      :logger/projectRef    [:projectslookup/ProjectID "SYRCL_THERMAS"]
      :logger/parameterRef  17592186302688
      :logger/stationRef    [:stationlookup/StationCode "SYRCL_THERMAS_101"]
      :logger/deviceTypeRef :samplingdevicelookup.ident/SYRCLThermaLogger}
     {:logsample/value  3.2M
      :logsample/logger "1"
      :logsample/inst   #inst"2020-10-10T10:10:10.000-00:00"}
     {:logsample/value  3.3M
      :logsample/logger "1"
      :logsample/inst   #inst"2020-10-10T10:11:10.000-00:00"}
     {:logsample/value  3.4M
      :logsample/logger "1"
      :logsample/inst   #inst"2020-10-10T10:12:10.000-00:00"}
     {:logsample/value  3.5M
      :logsample/logger "1"
      :logsample/inst   #inst"2020-10-10T10:13:10.000-00:00"}])

(defn process-csv-row [{:keys [date-column station-source station-id station-column col-config logger project-type] :as config} row]
  (let [date       (get row date-column)
        proj-type  (:projecttype/ident project-type)
        ;; set column-based station id
        station-id (if (= station-source "column")
                     (get row station-column)
                     station-id)
        _          (debug "PROCESS ROW" "STATION" station-id "DATE" date)]
    config))

(defn process-logger-row [{:keys [date-column col-config result-cols logger dupes] :as config} row]
  (let [date  (get row date-column)
        inst  (parse-date date)
        dupe? (contains? dupes inst)]
    (if dupe?
      config
      (let [logger     (:db/id logger)
            result-col (first result-cols)
            value      (bigdec (get row result-col))
            txd        {:logsample/logger logger
                        :logsample/inst   inst
                        :logsample/value  value}]
        ;_          (debug "PROCESS LOGGER ROW" txd)]
        (update config :txds conj txd)))))

(defn get-logger [{:keys [project stations station-id] :as config}]
  (let [station (first (filter #(= station-id (:stationlookup/StationID %)) stations))
        _       (when-not station
                  (throw (Exception. (str "Failed to find station for station-id: " station-id))))
        _       (debug "GET LOGGER for" station-id)
        logger  (d/q '[:find (pull ?e [*]) .
                       :in $ ?proj ?stat
                       :where
                       [?e :logger/stationRef ?stat]
                       [?e :logger/projectRef ?proj]]
                  (db)
                  [:projectslookup/ProjectID project]
                  (:db/id station))]
    (when-not logger
      (throw (Exception. (str "Failed to find logger for station-id: " station-id))))
    (debug "GOT LOGGER" (:logger/name logger))
    (assoc config :logger logger)))

(defn check-logger-dupes [{:keys [logger date-column] :as config} rows]
  (log/debug "CHECKING DUPES")
  (let [logger-id  (:db/id logger)
        insts      (sort (map #(parse-date (get % date-column)) rows))
        from       (first insts)
        to         (last insts)
        dupes      (not-empty
                     (d/q '[:find [?inst ...]
                            :in $ ?logger ?from ?to
                            :where
                            [(<= ?from ?inst)]
                            [(<= ?inst ?to)]
                            [?e :logsample/logger ?logger]
                            [?e :logsample/inst ?inst]]
                       (db) logger-id from to))
        dupes      (when dupes (sort dupes))
        _          (when dupes
                     (log/debug (format "FOUND %d DUPES" (count dupes))))
        dupe-first (first dupes)
        dupe-last  (last dupes)]
    (cond-> config
      dupes
      (->
        (merge
          {:dupes-first dupe-first
           :dupes-last  dupe-last
           :dupes       (set dupes)})))))


(defn process-logger-file [config rows]
  (log/debug "PROCESSING LOGGER FILE")
  (reduce
    (fn [config row]
      (process-logger-row config row))
    config rows))



(defn process-file [{:as   env
                     :keys [file-i file]}
                    {:keys [data] :as csv}]
  (debug "process-file")
  (try
    (let [filename  (:filename file)
          config    (:config env)
          {:keys [date-column
                  station-source
                  station-ids
                  project-type]} config
          proj-type (:projecttype/ident project-type)
          _         (debug "PROCESSING FILE" proj-type filename)
          ;_ (debug "FIRST DATE" (parse-date (get (first data) date-column)))

          config    (-> config
                      (assoc :txds [])
                      (assoc :csv csv)
                      (cond->
                        ;; set file-based station id
                        (= station-source "file")
                        (assoc :station-id (get station-ids file-i))
                        ;; get the logger
                        (= proj-type "logger")
                        (->
                          (get-logger)
                          (check-logger-dupes data)
                          (process-logger-file data))
                        (= proj-type "sitevisit")
                        (process-sitevisit-file file)))

          _         (log/debug "FINISHED PROCESSING FILE")

          dupes     (:dupes config)
          env       (if (seq dupes)
                      (update-in env [:res :msgs] conj (format "Skipped %d duplicates for %s" (count dupes) filename))
                      env)

          _         (log/debug "BEGINNING DB TRANSACTIONS")

          env       (update env :res
                      (fn [res]
                        (let [txds (:txds config)]
                          (if (seq txds)
                            (let [tx      @(d/transact (cx) txds)
                                  tempids (:tempids tx)
                                  ;res     (update res :tempids merge tempids)
                                  cnt     (count txds)]
                              (update res :msgs conj (format "Ran %d transactions for %s" cnt filename)))
                            (update res :msgs conj "No transactions for " filename)))))]

      _         (log/debug "FINISHED DB TRANSACTIONS")
      ;; return env
      env)
    (catch Exception ex
      (log/error "PROCESS CSV ERROR" ex)
      (update-in env [:res :errors] conj (str "PROCESS CSV ERROR: " ex " for " (:filename file))))))

(defn process-uploads [{:keys [config files] :as env}]
  (log/debug "process-uploads" config)
  (try
    (let [{:keys [agency project skip-line station-source station-column
                  station-id station-ids col-config]} config
          {agency-m  :agency
           project-m :project
           stations  :stations} (get-context config)

          ;_            (debug "STATIONS" stations)

          ptype        (:projectslookup/ProjectType project-m)
          Parameters   (:projectslookup/Parameters project-m)
          params-map   (nest-by [:parameter/NameShort] Parameters)
          param-ks     (set (keys params-map))

          ;_            (debug "param-ks" param-ks "params-map" params-map)

          config       (reduce
                         (fn [config [col {:keys [type param rep] :as col-cfg}]]
                           ;(debug "(contains? param)" param (contains? param-ks param))
                           (cond-> config

                             (= type "date")
                             (assoc :date-column col)

                             (and
                               (= station-source "column")
                               (= type "station")
                               (not (= station-column col)))
                             (assoc :station-column col)

                             (and
                               (= type "result")
                               (contains? param-ks param))
                             (assoc-in [:params col] (get params-map param))

                             (= type "result")
                             (update :result-cols (fnil conj []) col)))
                         config col-config)

          no-stn-col?  (or
                         (and
                           (= station-source "column")
                           (not (:station-column config)))
                         (and
                           (= station-source "file")
                           (empty? (:station-ids config)))
                         (and
                           (= station-source "select")
                           (not (:station-id config))))

          no-date-col? (not (:date-column config))

          errors       (cond-> []
                         no-stn-col?
                         (conj "Config: Invalid Station ID")
                         no-date-col?
                         (conj "Config: Missing Date"))

          ;_            (debug "CONFIG" config)


          config       (-> config
                         (assoc :project-type ptype)
                         (assoc :stations stations)
                         (assoc :project-m project-m))

          env          (merge env
                         {:file-i 0
                          :config config
                          :res    {:tempids {}
                                   :msgs    []
                                   :errors  errors}})]

      (debug (format "PROCESSING %d FILES" (count files)))
      (let [env (if (empty? errors)
                  (reduce
                    (fn [{:keys [file-i] :as env} {:keys [filename content-type ^File tempfile size]}]
                      (let [;newfile (io/file (str "resources/public/admin/files/" filename))
                            ;move?   (move-file tempfile newfile)
                            csv (parse-upload config tempfile)]
                        (-> env
                          (assoc :file (get files file-i))
                          ;(cond->
                          ;  (not move?)
                          ;  (update-in [:res :errors] conj (str "Failed to save " filename)))
                          (process-file csv)
                          (update :file-i inc))))
                    env files)
                  env)
            res (dissoc (:res env) :config :txds :txs)]
        (debug "RES" res)
        res))

    (catch Exception ex (error ex) (throw ex))))



