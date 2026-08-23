(ns riverdb.schema
  "Keeps the Datomic schema and resources/specs.edn in agreement.

  specs.edn is the source of truth: it already drives GraphQL generation and
  RAD forms, so the database should follow from it rather than the other way
  round. Datomic attribute installs are idempotent — transacting an identical
  definition again is a no-op — so schema needs no migration log. `sync!`
  transacts whatever specs.edn declares that the database lacks, and running it
  twice changes nothing.

  The reverse direction cannot be fixed automatically: an attribute that exists
  in the database but not in specs.edn is either something transacted by hand
  and never written back, or a typo that got installed. `drift` reports those so
  they are visible rather than discovered years later. That is not hypothetical
  — this namespace was written after finding thirteen of them, including a
  casing duplicate (:person/isStaff beside :person/IsStaff) and an
  :entity/nameKey pointing at an attribute that was never created.

  Data migrations are a different problem and belong in riverdb.migrations,
  which uses conformity to track what has been applied."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.tools.logging :as log]
    [datomic.api :as d]
    [domain-spec.core :as dspec]
    [domain-spec.literals :as lit]
    [riverdb.state :as state]))

(def specs-resource "specs.edn")

(def unmanaged
  "Attributes deliberately left out of specs.edn, so the drift check does not
  keep reporting them.

  :logsample/ident is a Datomic tuple. domain-spec's type vocabulary has no
  tuple, so specs.edn cannot express it — this is a real gap in the
  source-of-truth story rather than an oversight, and the attribute carries
  434k datoms, so it is not going anywhere. Extending domain-spec would let it
  be declared properly and this set could go away."
  #{:logsample/ident})

(def valid-types
  "Types domain-spec can turn into a Datomic valueType."
  #{:keyword :string :boolean :long :bigint :float :double :bigdec
    :ref :instant :uuid :uri :bytes})

;; ---------------------------------------------------------------------------
;; specs.edn
;; ---------------------------------------------------------------------------

(defn specs
  "The entity specs, read fresh so a REPL session picks up edits."
  []
  (edn/read-string (slurp (io/resource specs-resource))))

(defn spec-attrs
  "Every attribute specs.edn declares, as {attr-key spec}."
  [specs]
  (into {} (for [e specs a (:entity/attrs e)] [(:attr/key a) a])))

(defn problems
  "Anything in specs.edn that would make schema generation wrong or fail.
  Checked explicitly rather than left to blow up inside domain-spec, because
  the errors it raises don't say which attribute caused them."
  [specs]
  (let [pairs (for [e specs a (:entity/attrs e)] [(:entity/ns e) a])]
    (cond-> {}
      :always
      (assoc :invalid-types
        (vec (for [[ns a] pairs
                   :when (not (contains? valid-types (:attr/type a)))]
               {:entity ns :attr (:attr/key a) :type (:attr/type a)})))

      :always
      (assoc :duplicates
        (vec (for [[k grp] (group-by #(:attr/key (second %)) pairs)
                   :when (> (count grp) 1)]
               {:attr k :declared-in (mapv first grp)}))))))

(defn tx-data
  "specs.edn as Datomic schema transaction data."
  [specs]
  (-> specs dspec/specs->db-schema-terse lit/schema-tx))

;; ---------------------------------------------------------------------------
;; database
;; ---------------------------------------------------------------------------

(defn db-attrs
  "Installed attributes, as {ident {:valueType .. :cardinality ..}}. Only real
  attributes: entities carrying just :db/ident (enum values such as
  :sampletypelookup.SampleTypeCode/FieldMeasure) have no valueType and are not
  schema in this sense."
  [db]
  (into {}
    (for [[i vt card] (d/q '[:find ?i ?vt ?card
                             :where
                             [?e :db/ident ?i]
                             [?e :db/valueType ?v]  [?v :db/ident ?vt]
                             [?e :db/cardinality ?c] [?c :db/ident ?card]]
                        db)]
      [i {:valueType vt :cardinality card}])))

;; ---------------------------------------------------------------------------
;; drift
;; ---------------------------------------------------------------------------

(defn drift
  "How specs.edn and the database disagree.

    :missing-in-db     declared but never transacted here — sync! installs these
    :missing-in-specs  installed but undeclared — needs a human; a fresh
                       deployment built from specs.edn would not have them
    :mismatched        installed with a different type or cardinality than
                       declared; Datomic will not change these in place

  Only namespaces specs.edn claims are considered, so Datomic's own attributes
  and anything from another system are ignored."
  ([db] (drift db (specs)))
  ([db specs]
   (let [declared (spec-attrs specs)
         wanted   (into {} (for [t (tx-data specs)]
                             [(:db/ident t) {:valueType   (:db/valueType t)
                                             :cardinality (:db/cardinality t)}]))
         owned    (set (keep namespace (keys declared)))
         installed (db-attrs db)
         in-scope  (into {} (filter #(contains? owned (namespace (key %))) installed))]
     {:missing-in-db    (vec (sort (set/difference (set (keys wanted)) (set (keys installed)))))
      :missing-in-specs (vec (sort (set/difference (set (keys in-scope))
                                    (set (keys declared))
                                    unmanaged)))
      :mismatched       (vec (sort-by :attr
                               (for [[k want] wanted
                                     :let [have (get installed k)]
                                     :when (and have (not= want have))]
                                 {:attr k :declared want :installed have})))})))

(defn clean?
  "True when specs.edn and the database agree and specs.edn is well formed."
  [d probs]
  (and (empty? (:missing-in-db d))
       (empty? (:missing-in-specs d))
       (empty? (:mismatched d))
       (empty? (:invalid-types probs))
       (empty? (:duplicates probs))))

;; ---------------------------------------------------------------------------
;; sync
;; ---------------------------------------------------------------------------

(defn sync!
  "Install every attribute specs.edn declares that the database lacks.

  Idempotent: attributes already installed are simply not sent. Refuses to run
  while specs.edn has problems, because transacting a broken definition is
  harder to undo than fixing the file. Never retracts or alters anything —
  drift in the other direction is reported, not resolved."
  ([] (sync! (state/cx)))
  ([conn]
   (let [ss    (specs)
         probs (problems ss)]
     (if (or (seq (:invalid-types probs)) (seq (:duplicates probs)))
       {:status :blocked :problems probs}
       (let [d       (drift (d/db conn) ss)
             missing (set (:missing-in-db d))
             tx      (vec (filter #(contains? missing (:db/ident %)) (tx-data ss)))]
         (if (empty? tx)
           {:status :up-to-date :drift d}
           (do
             (log/info "Installing" (count tx) "attributes from specs.edn")
             @(d/transact conn tx)
             {:status :installed :installed (mapv :db/ident tx) :drift d})))))))

;; ---------------------------------------------------------------------------
;; reporting
;; ---------------------------------------------------------------------------

(defn report
  "Human-readable drift summary. Returns true when everything agrees."
  ([] (report (state/db)))
  ([db]
   (let [ss    (specs)
         probs (problems ss)
         d     (drift db ss)
         line  (fn [label xs f]
                 (when (seq xs)
                   (log/warn (str "  " label " (" (count xs) ")"))
                   (doseq [x xs] (log/warn (str "    " (f x))))))]
     (if (clean? d probs)
       (do (log/info "Schema matches specs.edn") true)
       (do
         (log/warn "Schema does not match specs.edn:")
         (line "invalid :attr/type in specs.edn" (:invalid-types probs)
           #(str (:attr %) " -> " (:type %)))
         (line "declared more than once in specs.edn" (:duplicates probs)
           #(str (:attr %) " in " (:declared-in %)))
         (line "declared but not installed — run (riverdb.schema/sync!)"
           (:missing-in-db d) identity)
         (line "installed but NOT in specs.edn — a fresh deploy would omit these"
           (:missing-in-specs d) identity)
         (line "type or cardinality differs — Datomic will not change these in place"
           (:mismatched d)
           #(str (:attr %) " declared " (:declared %) " installed " (:installed %)))
         false)))))
