(ns riverdb.api.geo
  (:require [clojure.java.io :as io]
            [datomic.api :as d]
            [com.walmartlabs.lacinia.schema :as schema]
            [clojure.tools.logging :as log :refer [debug info warn error]])
  (:import
    (java.util Random)
    (org.geotools.data FeatureSource FileDataStore FileDataStoreFinder)
    ;(org.geotools.data.shapefile.dbf DbaseFileReader DbaseFileHeader)
    (org.geotools.feature FeatureCollection)
    (org.geotools.geometry.jts ReferencedEnvelope JTS JTSFactoryFinder)
    ;(org.geotools.util NullProgressListener)
    (org.geotools.referencing CRS)
    (org.geotools.referencing.crs DefaultProjectedCRS DefaultGeographicCRS)
    (org.opengis.referencing.crs CoordinateReferenceSystem CRSAuthorityFactory)
    (org.opengis.referencing.operation MathTransform)
    (org.opengis.feature Feature FeatureVisitor Property)
    (org.opengis.feature.simple SimpleFeature)
    (org.locationtech.jts.geom Coordinate GeometryFactory Point)
    (com.vividsolutions.jts.geom Envelope Geometry LineString MultiLineString)
    (com.vividsolutions.jts.index SpatialIndex)
    (com.vividsolutions.jts.linearref LinearLocation LocationIndexedLine)
    [java.io File FileInputStream]
    (java.nio.channels FileChannel)
    ;(net.iryndin.jdbf.core DbfRecord DbfMetadata DbfFileTypeEnum)
    ;(net.iryndin.jdbf.reader DbfReader)
    (com.vividsolutions.jts.index.strtree STRtree)
    (java.nio.charset Charset)
    [org.geotools.data.util NullProgressListener]))


;(defn load-dbf-schema []
;  (debug "Loading DBF schemas")
;  (edn/read-string (slurp "resources/NHDFlowline-gr.edn")))

;(def schema {:objects {:lat_lon   {:fields {:lat {:type 'Float}
;                                            :lon {:type 'Float}}}
;                       :line_snap {:fields {:closest_point {:type :lat_lon}
;                                            :Shape_Leng    {:type 'Float}
;                                            :OBJECTID      {:type 'Int}
;                                            :FCODE         {:type 'Float}
;                                            :NAME          {:type 'String}
;                                            :FCODE_DESC    {:type 'String}
;                                            :FTYPE         {:type 'String}
;                                            :AP_WITHIN     {:type 'String}
;                                            :STRM_LEVEL    {:type 'Float}
;                                            :METERS        {:type 'Float}
;                                            :FEET          {:type 'Float}}}}})

;(defn resolve-line-snap [context args value]
;  (debug "LINE SNAPE args" args)
;  (let [result (geo/closest-point (:lon args) (:lat args))]
;    (when result
;      (into {}
;        (for [[k v] result] [(keyword k) v])))))


;(defn get-dbf-id-field [table]
;  (let [db-schema-fn #(clojure.edn/read-string (slurp (str "resources/" % "-db.edn")))
;        db-fn-memo   (memoize db-schema-fn)
;
;        first-attr   (first (db-fn-memo table))
;        id-field     (:db/ident first-attr)]
;    id-field))
;
;
;
;(defn resolve-dbf [context args value]
;  (debug "RESOLVE DBF")
;  (let [cx         (d/connect uri-dbf)
;        db         #(d/db cx)
;        selection  (:com.walmartlabs.lacinia/selection context)
;        selections (vec (:selections selection))
;        fields     (map :field selections)
;        tk         (get selection :field)
;        table      (name tk)
;
;        ;first-field (first fields)
;        ;id-field   (nskw table first-field)
;
;        id-field   (get-dbf-id-field table)
;
;        query      (vec (for [field fields]
;                          [(nskw table field) :as (keyword field)]))
;        ids        (get args :ids [])
;        _          (debug "IDS" ids)
;
;        offset     (get args :offset)
;        limit      (or (get args :limit)
;                     (when (empty? ids) 10))
;
;        ;args       (dissoc args :ids)
;
;        q          {:find  ['[(pull ?e qu) ...]]
;                    :in    '[$ qu]
;                    :where []
;                    :args  [(db) query]}
;
;        ;;; the goal is something like:
;        ;;; (d/q '[:find [(pull ?e query) ...]
;        ;;; :in $ query [?ids ...]
;        ;;; :where [?e :NHDFlowline/COMID ?ids]]
;        ;;; (d/db cx) query [8203093 22592543 22226720])
;
;        q          (cond-> q
;
;                     ;;; if there are args, add the conditions
;                     ;(some? args)
;                     ;(->
;                     ;  (fn [qz] qz))
;
;                     (seq ids)
;                     (->
;                       (update :in conj '[?ids ...])
;                       (update :where conj ['?e id-field '?ids])
;                       (update :args conj ids))
;
;                     ;; last one, in case there are no conditions, get all stations
;                     (empty? (:where q))
;                     (->
;                       (update :where conj ['?e id-field])))
;
;        q          (remap-query q)
;
;        d-results  (d/query q)
;
;        rez        (if (or limit offset)
;                     (limit-fn limit offset d-results)
;                     d-results)
;
;        ;; sort into the same order that was requested
;        rez        (vec (sort-by (keyword (name id-field)) #(< (.indexOf ids %1) (.indexOf ids %2)) rez))]
;
;    ;_ (debug "RESULT" rez)
;    ;_          (debug "Datomic query: " q)
;    ;_          (debug "Datomic first result: " (first d-results))
;
;    rez))

;; FIRST: establish sanity in the war between x and y ordering!
(System/setProperty "org.geotools.referencing.forceXY" "true")

;; This code is a port from the Java at http://docs.geotools.org/latest/userguide/library/jts/snap.html

;(def p (Coordinate. -121.0373 39.2943))
;(def geomFactory ^GeometryFactory (JTSFactoryFinder/getGeometryFactory))
;(def pt ^Point (.createPoint geomFactory p))

;Hints hints = new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, Boolean.TRUE);
;CRSAuthorityFactory factory = ReferencingFactoryFinder.getCRSAuthorityFactory("EPSG", hints);
;CoordinateReferenceSystem crs = factory.createCoordinateReferenceSystem("EPSG:4326");

;CRSAuthorityFactory   factory = CRS.getAuthorityFactory(true);
;CoordinateReferenceSystem crs = factory.createCoordinateReferenceSystem("EPSG:4326");

(def crsFactory ^CRSAuthorityFactory (CRS/getAuthorityFactory true))
(defn createCRS [crs-code]
  (.createCoordinateReferenceSystem crsFactory crs-code))

(def wgs84 ^CoordinateReferenceSystem (createCRS "EPSG:4326"))
(def utm10n ^CoordinateReferenceSystem (createCRS "EPSG:32610"))
(def epsg3857 ^CoordinateReferenceSystem (createCRS "EPSG:3857"))

(defn convert [{:keys [lat lon] :as fromLatLon} ^CoordinateReferenceSystem fromCRS ^CoordinateReferenceSystem toCRS]
  (let [tx (CRS/findMathTransform fromCRS toCRS)
        from (Coordinate. lon lat)
        to ^Coordinate (JTS/transform from nil tx)]
    {:lon (.x to) :lat (.y to)}))


;(defonce state (atom {:yuba nil :wgs84 (CRS/decode "EPSG:4326")}))
;
;(defn open-shapefile
;  "Opens a shapefile from the classpath and tests for validity" [path]
;  (if-let [yuba (:yuba @state)]
;    yuba
;    (let [path          path
;          file          (io/resource path)
;          store         (FileDataStoreFinder/getDataStore file)
;          source        (.getFeatureSource store)
;          geomBinding   (.. source (getSchema) (getGeometryDescriptor) (getType) (getBinding))
;          lineStr?      (.isAssignableFrom LineString geomBinding)
;          multiLineStr? (.isAssignableFrom MultiLineString geomBinding)
;          isLine?       (and (some? geomBinding) (or lineStr? multiLineStr?))
;          yuba          (if isLine? source nil)]
;      (swap! state assoc :yuba yuba)
;      yuba)))
;
;(defn generate-datomic-schema [^String namespace ^DbaseFileHeader header]
;  (let [numFields (.getNumFields header)]
;    (vec (for [i (range numFields)]
;           (let [f-name  (.getFieldName header i)
;                 f-class (.getFieldClass header i)
;                 f-type  (.getFieldType header i)
;                 f-len   (.getFieldLength header i)
;                 f-dec   (.getFieldDecimalCount header i)
;                 doc-str (str f-name "," f-type "," f-len (when (= (str f-type) "N") (str "," f-dec)))
;                 type    (case (.getName f-class)
;
;                           ("java.lang.Integer" "java.lang.Long")
;                           :db.type/long
;
;                           "java.util.Date"
;                           :db.type/instant
;
;                           "java.lang.String"
;                           :db.type/string
;
;                           "java.lang.Double"
;                           :db.type/double
;
;                           :db.type/string)
;                 result  {:db/ident (keyword namespace f-name)
;                          :db/valueType type
;                          :db/cardinality :db.cardinality/one
;                          :db/index (if (= type :db.type/long) true false)
;                          :db/doc doc-str}
;                 result  (cond-> result
;                           (= i 0)
;                           (assoc :db/unique :db.unique/identity))]
;             result)))))
;
;(defn db-schema->graphql-schema [namespace db-txd]
;  (let [table-key  (keyword namespace)
;        fields-map (into {} (for [{:keys [db/valueType db/ident db/doc] :as txd} db-txd]
;                              (let [type  (case valueType
;                                            :db.type/long 'Int
;                                            :db.type/instant 'String
;                                            :db.type/string 'String
;                                            :db.type/double 'Float)
;                                    field (name ident)]
;                                [(keyword field) {:type type
;                                                  :description doc}])))]
;    {:objects {table-key {:fields fields-map
;                          :description (str "imported from " namespace ".dbf")}}
;     :queries {table-key {:type `(~'list ~table-key),
;                          :resolve :resolve-dbf,
;                          :args
;                          {:limit {:type 'Int, :default 10},
;                           :offset {:type 'Int, :default 0},
;                           :ids {:type '(list Int), :default nil}}}}}))
;     ;:scalars {
;     ;          :Long {
;     ;                 :parse '(schema/as-conformer #(Long/parseLong %))
;     ;                 :serialize '(schema/as-conformer #(do %))
;     ;                 }
;     ;          }
;
;
;
;
;(defn load-dbf [filename & {:keys [cx filter-fn limit debug? namespace]}]
;  (println "importing DBF:" filename)
;  (let [file       (io/file filename)
;        f-name     (.getName file)
;        namespace  (or namespace
;                     (subs f-name 0 (clojure.string/last-index-of f-name ".")))
;        in         (.getChannel (FileInputStream. ^File file))
;        r          (DbaseFileReader. in false (Charset/forName "ISO-8859-1"))
;        header     (.getHeader r)
;        numRecords (.getNumRecords header)
;        numRows    (if (and limit (< limit numRecords)) limit numRecords)
;        _          (println numRecords "records, importing" numRows "rows")
;        schema-txd (generate-datomic-schema namespace header)
;        parts      (partition 200 200 nil (range numRows))]
;    (doseq [part parts]
;      (print (first part) "")
;      (flush)
;      (let [part-txd (for [_ part]
;                       (let [row    (.readRow r)
;                             db-row (into {}
;                                      (for [i (range (count schema-txd))]
;                                        (let [attr  (get schema-txd i)
;                                              ident (:db/ident attr)
;                                              ;type  (:db/valueType attr)
;                                              value (.read row i)]
;                                          [ident value])))]
;
;                         db-row))
;            part-txd (if filter-fn
;                       (filter filter-fn part-txd)
;                       part-txd)]
;        (when debug?
;          (debug "records" part-txd))
;        (when cx
;          (d/transact cx part-txd))))))
;
;(comment
;  (load-dbf "resources/NHDFlowline_17.dbf"
;    :namespace "NHDFlowline"
;    :debug? true
;    :limit 20
;    :cx (d/connect (dotenv/env :DATOMIC_URI_DBF))
;    :filter-fn #(> (count (get % :NHDFlowline/GNIS_NAME)) 0))
;
;  (load-dbf "/home/rimdb/resources/Flowline_DBF/NHDFlowline_17.dbf"
;    :namespace "NHDFlowline" :debug? false
;    :filter-fn #(> (count (get % :NHDFlowline/GNIS_NAME)) 0)
;    :cx (d/connect (dotenv/env :DATOMIC_URI_DBF))))
;
;
;(defn import-dbf
;  "reads a DBF file into EDN schema files and optionally transacts schema and data into Datomic "
;  ([filename]
;   (import-dbf filename nil))
;  ([filename cx]
;   (let [file            (io/file filename)
;         f-name          (.getName file)
;         f-base          (subs f-name 0 (clojure.string/last-index-of f-name "."))
;         out-filename-db (str (.getParent file) "/" f-base "-db.edn")
;         out-filename-gr (str (.getParent file) "/" f-base "-gr.edn")
;         in              (.getChannel (FileInputStream. ^File file))
;         r               (DbaseFileReader. in false (Charset/forName "ISO-8859-1"))
;         header          (.getHeader r)
;         numRows         (.getNumRecords header)
;         ;numFields    (.getNumFields header)
;         ;fields       (vec (for [i (range numFields)]
;         ;                    (let [field-name  (.getFieldName header i)
;         ;                          field-class (.getFieldClass header i)]
;         ;                      [i field-name field-class])))
;         schema-txd      (generate-datomic-schema f-base header)
;         graph-schema    (db-schema->graphql-schema f-base schema-txd)]
;
;
;     (spit (io/file out-filename-db) schema-txd)
;     (spit (io/file out-filename-gr) graph-schema)
;
;     ;(let [uri (or (env :dbf-uri) "datomic:free://localhost:4334/test-dbf")
;     ;      _   (d/create-database uri)
;     ;      cx  (d/connect uri)])
;
;     (when cx
;       (d/transact cx schema-txd))
;
;     (load-dbf filename :cx cx))))
;
;(defn ^FeatureCollection get-features
;  [^FeatureSource source]
;  (.getFeatures source))
;
;(defn get-projection [^FeatureSource source]
;  (.getCoordinateReferenceSystem
;    (.getSchema source)))
;
;(defn ^ReferencedEnvelope get-bounds
;  [^FeatureCollection features]
;  (.getBounds features))
;
;(defn index-features [^FeatureCollection features]
;  (if (:index @state)
;    (:index @state)
;    (let [index   (STRtree.)
;          visitor (reify FeatureVisitor
;                    (visit [this feature]
;                      (let [feature ^SimpleFeature feature
;                            geom    ^Geometry (.getDefaultGeometry feature)]
;                        (if (some? geom)
;                          (let [env (.getEnvelopeInternal geom)]
;                            (if-not (.isNull env)
;                              (do (.insert index env feature)
;                                  #_(debug "num points: " (.getNumPoints geom) " length: " (.getLength geom)
;                                      (.getMinX env) (.getMaxX env)))))))))
;
;          _       (.accepts features visitor (NullProgressListener.))
;          _       (swap! state assoc :index index)]
;      index)))
;
;;CoordinateReferenceSystem dataCRS = schema.getCoordinateReferenceSystem();
;;CoordinateReferenceSystem worldCRS = map.getCoordinateReferenceSystem();
;;boolean lenient = true; // allow for some error due to different datums
;;MathTransform transform = CRS.findMathTransform(dataCRS, worldCRS, lenient);
;
;;(CRS/findMathTransform point-crs
;;  (.getCoordinateReferenceSystem
;;    (.getSchema
;;      (.getFeatureSource
;;        (FileDataStoreFinder/getDataStore
;;          (io/resource "yuba_hydrology.shp"))))) true)
;
;
;(defn generate-points [num-points ^ReferencedEnvelope bounds]
;  (let [random ^Random (Random. (.hashCode bounds))]
;    (into []
;      (for [i (range num-points)]
;        (Coordinate.
;          (+ (.getMinX bounds) (* (.nextDouble random) (.getWidth bounds)))
;          (+ (.getMinY bounds) (* (.nextDouble random) (.getHeight bounds))))))))
;
;
;
;(defn calc-snap [^Coordinate point ^SpatialIndex index ^ReferencedEnvelope bounds]
;  (let [search              (Envelope. point)
;        MAX_SEARCH_DISTANCE (/ (.getSpan bounds 0) 10.0)
;        _                   (.expandBy search MAX_SEARCH_DISTANCE)
;        lines               (.query index search)
;        result              (reduce (fn [result ^SimpleFeature feature]
;                                      (let [line    (.getDefaultGeometry feature)
;                                            attrs   (.getAttributes feature)
;                                            ;_ (debug "feat attrs: " attrs)
;                                            i-line  (LocationIndexedLine. line)
;                                            here    (.project i-line point)
;                                            point2  (.extractPoint i-line here)
;                                            dist    (.distance point2 point)
;                                            minDist (:minDist result)]
;                                        (if (< dist minDist)
;                                          (-> result
;                                            (assoc :minDist dist)
;                                            (assoc :minPoint point2)
;                                            (assoc :minLine feature))
;                                          result)))
;                              {:minDist Double/MAX_VALUE :minPoint nil :minLine nil} lines)]
;    result))
;
;
;(defn calc-snaps [points ^SpatialIndex index ^ReferencedEnvelope bounds]
;  (let [num-points          (count points)
;        MAX_SEARCH_DISTANCE (/ (.getSpan bounds 0) 100.0)]
;    (loop [points-processed 0
;           points-snapped   0]
;      (when (< points-processed num-points)
;        (let [^Coordinate point (aget points points-processed)
;              point2            (calc-snap point index bounds)
;              points-snapped    (if (some? point2) (inc points-snapped) points-snapped)]
;          (recur (inc points-processed) points-snapped))))))
;
;(defn closest-point [lon lat]
;  (let [lat       (or lat 39.2943)
;        lon       (or lon -121.0373)
;        point     (Coordinate. lon lat)
;        shapefile "yuba_hydrology.shp"
;        source    (open-shapefile shapefile)
;        ;_         (debug "source" source)
;        features  (get-features source)
;        ;_         (debug "features" features)
;        bounds    (get-bounds features)
;        ;_         (debug "bounds" bounds)
;        index     (index-features features)
;        ;_         (debug "index" index)
;        result    (calc-snap point index bounds)]
;        ;_         (debug "result" result)
;
;    (when (:minPoint result)
;      (let [line   ^SimpleFeature (:minLine result)
;            props  (into {}
;                     (for [^Property prop (.getProperties line)]
;                       [(.getLocalPart (.getName prop)) (.getValue prop)]))
;            point2 ^Coordinate (:minPoint result)
;            point2 {:lon (.getOrdinate point2 0) :lat (.getOrdinate point2 1)}]
;
;        (-> props
;          (dissoc "the_geom")
;          (assoc "closest_point" point2))))))



