(ns riverdb.migrations
  "Data migrations, tracked with conformity.

  Schema is NOT here: it is declarative in resources/specs.edn and installed
  idempotently by riverdb.schema/sync!. Data changes are different — a backfill
  run twice is not the same as run once — so each is a named norm, applied at
  most once per database, recorded in the database itself. That means the same
  code can run against dev and production and do the right thing on each,
  replacing the practice of keeping a dev-changes-<year>.edn file and replaying
  the relevant bits by hand.

  Norms are append-only. Once one has run anywhere, edit it only to fix a bug
  that has not yet reached production; otherwise add a new norm."
  (:require
    [clojure.tools.logging :as log]
    [datomic.api :as d]
    [io.rkn.conformity :as c]
    [riverdb.state :as state]))

;; ---------------------------------------------------------------------------
;; 2026-08 :fieldobsvarlookup/Order
;;
;; IntCode was doing three jobs at once: display order, whether an option is
;; offered at all (> 0), and a sentinel that hid OtherPresence's "none" at 0.
;; Order takes over the first two — its presence means "configured and
;; offered" — and nothing needs a sentinel.
;;
;; Deliberately conservative: it copies IntCode where IntCode is positive and
;; touches nothing else. Options with no IntCode stay unoffered exactly as
;; before, so behaviour is identical the moment it runs. Giving one an Order
;; later is then a data edit rather than a code change, which is how the ten
;; dormant analytes (Odor, ObservedFlow, Color, ...) become usable.
;; ---------------------------------------------------------------------------

(defn obs-order-from-intcode
  "Copy a positive IntCode to Order, skipping any already set.

  conformity calls a :txes-fn with the CONNECTION, not a db value, and expects
  a collection of transactions rather than a single one."
  [conn]
  (let [datoms (vec
                 (for [[e int-code] (d/q '[:find ?v ?ic
                                           :where
                                           [?v :fieldobsvarlookup/IntCode ?ic]
                                           [(pos? ?ic)]
                                           (not [?v :fieldobsvarlookup/Order])]
                                      (d/db conn))]
                   [:db/add e :fieldobsvarlookup/Order int-code]))]
    (log/info "Backfilling :fieldobsvarlookup/Order for" (count datoms) "options")
    ;; Always one transaction, even when empty: conformity refuses a norm that
    ;; produced no transactions, and it still needs a tx to hang its marker on.
    [datoms]))

(def norms
  {:riverdb.obs/order-from-intcode-2026-08
   {:txes-fn 'riverdb.migrations/obs-order-from-intcode
    :txes    []}})

;; ---------------------------------------------------------------------------

(defn applied?
  "Has this norm run against this database?

  conformity's own conforms-to? wants the transaction count a norm produced,
  which a computed :txes-fn does not know in advance, so this reads the marker
  attribute conformity writes directly. Which attribute that is has to be asked
  of the db rather than assumed: the c/default-conformity-attribute var is
  deprecated and misspelled (:confirmity/), kept only so databases marked by
  older versions still read back."
  [db norm]
  (let [attr (c/default-conformity-attribute-for-db db)]
    ;; conformity installs its tracking attribute on first use, so on a database
    ;; that has never been migrated nothing can have been applied.
    (boolean
      (when (c/has-attribute? db attr)
        (seq (d/q '[:find [?e ...] :in $ ?attr ?norm :where [?e ?attr ?norm]]
               db attr norm))))))

(defn pending
  "Norms not yet applied to this database."
  ([] (pending (state/db)))
  ([db] (vec (remove #(applied? db %) (keys norms)))))

(defn status
  ([] (status (state/db)))
  ([db]
   (into {} (for [k (keys norms)] [k (if (applied? db k) :applied :pending)]))))

(defn migrate!
  "Apply every norm this database has not seen. Safe to run repeatedly and on
  any environment; conformity records what it applied."
  ([] (migrate! (state/cx)))
  ([conn]
   (let [before (pending (d/db conn))]
     (if (empty? before)
       (do (log/info "No pending data migrations") {:status :up-to-date})
       (do
         (log/info "Applying data migrations:" before)
         (c/ensure-conforms conn norms (vec (keys norms)))
         {:status :applied :applied before})))))
