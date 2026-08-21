(ns riverdb.html.schema
  "Malli schemas for data crossing the HTTP edge.

  Datastar signals arrive as JSON: every ref is a string entity id (\"\" when
  cleared), dates are \"YYYY-MM-DD\", booleans are booleans. Decoding happens
  here so that riverdb.html.sitevisit and everything below it sees real longs,
  real java.util.Dates and real nils, and never parses a string."
  (:require
    [clojure.string :as str]
    [malli.core :as m]
    [malli.error :as me]
    [malli.transform :as mt])
  (:import
    (java.time LocalDate ZoneId)
    (java.util Date)))

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

(def SiteVisitSignals
  "Every key optional: a save applies only the signals it was sent, so a
  partial patch leaves the rest of the entity alone. Closed, so a typo in a
  signal name is a 400 rather than a silently ignored field."
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
   [:Visitors           {:optional true} Refs]

   ;; UI-only signals. Datastar sends every signal on every request, so each
   ;; ref-list's type-ahead query arrives here too and must be declared or
   ;; :closed true rejects the save. Named <Signal>Query by
   ;; riverdb.html.ref-list/query-signal. Absent from `fields`, so they never
   ;; reach a transaction.
   [:VisitorsQuery      {:optional true} Text]])

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
