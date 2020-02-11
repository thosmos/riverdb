(ns riverdb.ui.bench
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom :as dom :refer [div]]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.normalize :as fnorm]))

(defsc Person [this props]
  {:query       [:db/id ::person-name ::person-age
                 fs/form-config-join]
   :ident       [:person/id :db/id]
   :form-fields #{::person-name ::person-age}})
(def person {:db/id 1 ::person-name "Bo"})
(def person-form (fs/add-form-config Person person))
(def state-map (fnorm/tree->db [{:the-person (comp/get-query Person)}] {:the-person person-form} true))
(def modified-state-map (-> state-map
                          (assoc-in [:person/id 1 ::person-name] "Bobby")
                          (assoc-in [:person/id 1 ::person-age] 42)))
(defn merge-elide-keys
  "replace a subset of m1's keys ks with m2's, eliding any missing"
  ([m1 m2 ks]
   (persistent!
     (reduce-kv
       (fn [out k v]
         (if (not (contains? ks k))
           (assoc! out k v)
           (if (contains? m2 k)
             (assoc! out k (k m2))
             out)))
       (transient {}) m1))))

(defn pristine->entity*
  [state-map entity-ident]
  (fs/update-forms state-map
    (fn reset-form-step [e {:keys [::fs/pristine-state] :as config}]
      [(merge e pristine-state) config]) entity-ident))

(defn pristine->entity-1
  [state-map entity-ident]
  (fs/update-forms state-map
    (fn reset-form-step [e {:keys [::fs/pristine-state ::fs/fields] :as config}]
      [(merge-elide-keys e pristine-state fields) config]) entity-ident))

(defn pristine->entity-2
  [state-map entity-ident]
  (fs/update-forms state-map
    (fn reset-form-step [e {:keys [::fs/pristine-state] :as config}]
      [(as-> e e (apply dissoc e (::fs/fields config)) (merge e pristine-state)) config]) entity-ident))

(defn pristine->entity-3
  [state-map entity-ident]
  (fs/update-forms state-map
    (fn reset-form-step [e {:keys [::fs/pristine-state ::fs/fields] :as config}]
      (let [new-e      (merge e pristine-state)
            elide-keys (clojure.set/difference fields (keys pristine-state))
            new-e      (apply dissoc new-e elide-keys)]
        [new-e config])) entity-ident))

(def reset-state-map-*    (pristine->entity* modified-state-map [:person/id 1]))
(def reset-state-map-1    (pristine->entity-1 modified-state-map [:person/id 1]))
(def reset-state-map-2    (pristine->entity-2 modified-state-map [:person/id 1]))
(def reset-state-map-3    (pristine->entity-3 modified-state-map [:person/id 1]))


(defsc Bench [this props]
  {}
  (div :.ui.segment {}
    (dom/h3 "Benchmarks")
    (div {} (simple-benchmark [m {:a 1}] (:a m) 10000000))
    (div {} (simple-benchmark [] (pristine->entity-1 modified-state-map [:person/id 1]) 9000))
    (div {} (simple-benchmark [] (pristine->entity-2 modified-state-map [:person/id 1]) 9000))
    (div {} (simple-benchmark [] (pristine->entity-3 modified-state-map [:person/id 1]) 9000))))

(def ui-bench (comp/factory Bench))

(comment
  ;; :dev REPL
  [m {:a 1}], (:a m), 10000000 runs, 363 msecs
  [], (pristine->entity-1 modified-state-map [:person/id 1]), 9000 runs, 43 msecs
  [], (pristine->entity-2 modified-state-map [:person/id 1]), 9000 runs, 51 msecs
  [], (pristine->entity-3 modified-state-map [:person/id 1]), 9000 runs, 59 msecs

  ;; :advanced compile
  [], (pristine->entity-1 modified-state-map [:person/id 1]), 9000 runs, 39 msecs
  [], (pristine->entity-2 modified-state-map [:person/id 1]), 9000 runs, 41 msecs
  [], (pristine->entity-3 modified-state-map [:person/id 1]), 9000 runs, 47 msecs)

