(ns riverdb.html.schema
  "Malli schemas for data crossing the HTTP edge.

  Signals keep their Datomic namespace. Datastar's expression syntax accepts
  dots as a nesting separator but not slashes, so :sitevisit/StationID travels
  as the nested signal `sitevisit.StationID` and arrives as

      {:sitevisit {:StationID \"17592186045422\"}}

  Verified against v1.0.2: data-bind, data-text and deep indexed access all
  work on dotted paths, and the nested shape survives the round trip. Encoding
  the slash as an underscore was the alternative, but :stationlookup/GIS_latlon
  already contains one, so that mapping is ambiguous.

  Signals that are not entity attributes live under the `ui` namespace, which
  keeps the entity namespaces a faithful mirror of Datomic.

  Values arrive as JSON: refs are string entity ids (\"\" when cleared), dates
  are \"YYYY-MM-DD\". Decoding happens here so that riverdb.html.sitevisit and
  everything below it sees real longs, real java.util.Dates and real nils, and
  never parses a string."
  (:require
    [clojure.string :as str]
    [malli.core :as m]
    [malli.error :as me]
    [malli.transform :as mt])
  (:import
    (java.time LocalDate ZoneId)
    (java.util Date)))

;; ---------------------------------------------------------------------------
;; namespaced keys <-> nested signal paths
;; ---------------------------------------------------------------------------

(defn signal-path
  "Qualified keyword -> the path it occupies in the signals map. The namespace
  is split on its dots, so the shape mirrors the keyword exactly:

    :sitevisit/StationID        -> [:sitevisit :StationID]
    :org.riverdb.db.sitevisit/gid -> [:org :riverdb :db :sitevisit :gid]

  Reversing it is unambiguous because the LAST segment is always the name and
  everything before it is the namespace. No attribute name in specs.edn
  contains a dot, so nothing collides."
  [k]
  (mapv keyword (conj (vec (str/split (namespace k) (re-pattern "\\."))) (name k))))

(defn path->key
  "Inverse of signal-path: last segment is the name, the rest is the namespace."
  [path]
  (let [segs (mapv name path)]
    (keyword (str/join "." (butlast segs)) (last segs))))

(defn signal-name
  "Qualified keyword -> the string a data-bind / data-text expression uses.
  Datastar splits on dots, so this is just the keyword with / replaced by .

    :sitevisit/StationID -> \"sitevisit.StationID\""
  [k]
  (str (namespace k) "." (name k)))

(defn signal-get
  "Read a namespaced attribute out of a decoded signals map."
  [signals k]
  (get-in signals (signal-path k)))

(defn signal-has?
  "Was this attribute present in the payload at all? Distinguishes \"not sent\"
  from \"sent as nil\", which is what makes partial saves work."
  [signals k]
  (let [path (signal-path k)]
    (contains? (get-in signals (vec (butlast path))) (last path))))

(defn signals-for
  "Build a nested signals map from a flat map of namespaced key -> value."
  [m]
  (reduce-kv (fn [acc k v] (assoc-in acc (signal-path k) v)) {} m))

;; ---------------------------------------------------------------------------
;; wire -> domain
;; ---------------------------------------------------------------------------

(defn ->eid
  "\"17592186045422\" -> 17592186045422, \"\" -> nil. Anything unparseable is
  left alone so validation reports it rather than silently nil-ing it out."
  [v]
  (cond
    (int? v) v
    (string? v) (if (str/blank? v) nil (or (parse-long (str/trim v)) v))
    :else v))

(defn ->date
  "\"2024-05-14\" -> Date at local midnight, \"\" -> nil."
  [v]
  (cond
    (inst? v) v
    (string? v) (if (str/blank? v)
                  nil
                  (try
                    (Date/from (.toInstant (.atStartOfDay (LocalDate/parse (str/trim v))
                                                          (ZoneId/systemDefault))))
                    (catch Exception _ v)))
    :else v))

(defn date->wire
  "Date -> \"YYYY-MM-DD\" for <input type=date>, nil -> \"\"."
  [^Date d]
  (if d
    (str (.toLocalDate (.atZone (.toInstant d) (ZoneId/systemDefault))))
    ""))

(defn- ->text [v]
  (if (string? v) (not-empty (str/trim v)) v))

(defn- ->eids [v]
  (cond
    (nil? v) []
    (sequential? v) (mapv ->eid v)
    :else [(->eid v)]))

;; ---------------------------------------------------------------------------
;; schemas
;; ---------------------------------------------------------------------------

(def Ref
  "A Datomic entity id. Cleared selects arrive as \"\" and decode to nil."
  [:maybe {:decode/signals ->eid} :int])

(def Instant
  [:maybe {:decode/signals ->date} inst?])

(def Text
  [:maybe {:decode/signals ->text} :string])

(def Refs
  [:sequential {:decode/signals ->eids} :int])

(def SiteVisitAttrs
  "The `sitevisit` branch of the signals: real Datomic attributes, named
  exactly as they are in the database. Every key optional, so a save applies
  only what it was sent and a partial patch leaves the rest alone."
  [:map {:closed true}
   [:StationID          {:optional true} Ref]
   [:SiteVisitDate      {:optional true} Instant]
   [:Time               {:optional true} Text]
   [:VisitType          {:optional true} Ref]
   [:StationFailCode    {:optional true} Ref]
   [:DataEntryPersonRef {:optional true} Ref]
   [:DataEntryDate      {:optional true} Instant]
   [:CheckPersonRef     {:optional true} Ref]
   [:QAPersonRef        {:optional true} Ref]
   [:QADate             {:optional true} Instant]
   [:QACheck            {:optional true} :boolean]
   [:Notes              {:optional true} Text]
   [:Visitors           {:optional true} Refs]])

(def UiSignals
  "Signals that are not entity attributes. Keeping them out of the entity
  namespaces means `sitevisit` stays a faithful mirror of Datomic. Datastar
  sends every signal on every request, so each one must be declared here or a
  closed schema rejects the save."
  [:map {:closed true}
   [:MonitorsQuery {:optional true} Text]])

(def GridCells
  "Field measurement grid signals, keyed by parameter eid then replicate.
  Values stay strings here: the grid is transcription input, and blank means
  \"not recorded\" rather than zero. They are parsed when a row's statistics
  are computed and again when the grid is saved."
  [:map-of :keyword [:map-of :keyword [:maybe :string]]])

(def PerRow
  "A per-parameter value in the field measurement grid."
  [:map-of :keyword [:maybe :string]])

(def SampleAttrs
  [:map {:closed true}
   [:Time       {:optional true} PerRow]
   [:DeviceType {:optional true} PerRow]
   [:DeviceID   {:optional true} PerRow]])

(def FieldResultAttrs
  [:map {:closed true}
   [:Result {:optional true} GridCells]])

(def SiteVisitSignals
  [:map {:closed true}
   [:sitevisit   {:optional true} SiteVisitAttrs]
   [:sample      {:optional true} SampleAttrs]
   [:fieldresult {:optional true} FieldResultAttrs]
   [:ui          {:optional true} UiSignals]])

(def FieldMeasurePath
  "Path params for the field measurement stats endpoint."
  [:map [:id :int] [:param :int]])

(def RefFieldPath
  "Path params for a cardinality-many ref endpoint."
  [:map [:id :int] [:field :string]])

(def RefMemberPath
  "As RefFieldPath, plus the member being added or removed."
  [:map [:id :int] [:field :string] [:member :int]])

(def SiteVisitPath
  [:map [:id :int]])

;; ---------------------------------------------------------------------------
;; decoding
;; ---------------------------------------------------------------------------

(def ^:private signals-transformer
  (mt/transformer {:name :signals}))

(defn decode
  "Decode and validate `x` against `schema`.
  Returns {:value decoded} or {:error humanized}."
  [schema x]
  (let [decoded (m/decode schema x signals-transformer)]
    (if-let [problem (m/explain schema decoded)]
      {:error (me/humanize problem)}
      {:value decoded})))
