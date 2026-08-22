(ns riverdb.html.fieldmeasure
  "Field measurement grid for the SiteVisit form.

  Rows are the project's active field-measure parameters, in :parameter/Order.
  Columns are that parameter's :parameter/ReplicatesEntry replicate inputs plus
  derived statistics, which the server recomputes on every keystroke — the
  quality maths stays in Clojure next to the data rather than being duplicated
  in the browser.

  Signals keep their Datomic namespace and add the row and replicate beneath:

    {:sample      {:Time   {\"<param-eid>\" \"11:17\"}}
     :fieldresult {:Result {\"<param-eid>\" {\"1\" \"7.8\" \"2\" \"7.9\"}}}}

  so a cell binds to fieldresult.Result.<param-eid>.<replicate>.

  Row and replicate keys are keywords server-side. JSON encodes them as
  strings on the way out and raw-signals keywordizes them on the way back, so
  keywords are what both ends actually see — using strings here silently
  misses every lookup."
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [datomic.api :as d]
    [riverdb.html.schema :as sc]
    [thosmos.util :as tu]))

(def field-measure-type :sampletypelookup.SampleTypeCode/FieldMeasure)

(def result-attr :fieldresult/Result)
(def time-attr   :sample/Time)

;; ---------------------------------------------------------------------------
;; reads
;; ---------------------------------------------------------------------------

(def ^:private param-pull
  '[:db/id
    :parameter/Name
    :parameter/NameShort
    :parameter/Order
    :parameter/Active
    :parameter/Replicates
    :parameter/ReplicatesEntry
    :parameter/PrecisionCode
    :parameter/High
    :parameter/Low
    {:parameter/SampleType [:db/ident]}
    {:parameter/DeviceType [:db/id :samplingdevicelookup/DeviceType]}
    {:parameter/Constituent
     [:db/id
      {:constituentlookup/UnitCode [:unitlookup/Unit]}
      {:constituentlookup/AnalyteCode [:analytelookup/AnalyteShort]}]}])

(defn params
  "Active field-measure parameters for a project, in display order."
  [db project-eid]
  (when project-eid
    (->> (d/q '[:find [(pull ?p pull-spec) ...]
                :in $ ?proj pull-spec
                :where [?proj :projectslookup/Parameters ?p]]
           db project-eid param-pull)
      (filter :parameter/Active)
      (filter #(= field-measure-type (get-in % [:parameter/SampleType :db/ident])))
      (sort-by (juxt #(or (:parameter/Order %) 999) :parameter/Name))
      vec)))

(defn unit [param]
  (get-in param [:parameter/Constituent :constituentlookup/UnitCode :unitlookup/Unit]))

(defn replicates
  "How many replicate inputs to show. Entry count wins; fall back to the
  required count, then to one."
  [param]
  (or (:parameter/ReplicatesEntry param) (:parameter/Replicates param) 1))

(defn samples-by-param
  "This visit's field-measure samples, keyed by parameter eid."
  [sv]
  (into {}
    (for [s (:sitevisit/Samples sv)
          :when (= field-measure-type (get-in s [:sample/SampleTypeCode :db/ident]))
          :let [p (get-in s [:sample/Parameter :db/id])]
          :when p]
      [p s])))

(defn fmt-result
  "Wire form of a stored reading. :fieldresult/Result is a double, so a typed
  \"11\" comes back as 11.0; render integral values without the tail so the
  grid shows what was actually typed."
  [v]
  (cond
    (nil? v) ""
    (and (number? v) (== v (Math/rint (double v)))) (str (long v))
    :else (str v)))

(defn- results-by-replicate [sample]
  (into {}
    (for [fr (:sample/FieldResults sample)
          :let [rep (:fieldresult/FieldReplicate fr)]
          :when rep]
      [(str rep) fr])))

(def devtype-attr :sample/DeviceType)
(def devid-attr   :sample/DeviceID)

(defn device-types
  "Active sampling device types, for the Device column."
  [db]
  (->> (d/q '[:find [(pull ?d [:db/id :samplingdevicelookup/SampleDevice
                               :samplingdevicelookup/Active]) ...]
              :where [?d :samplingdevicelookup/SampleDevice]]
         db)
    (filter #(not= false (:samplingdevicelookup/Active %)))
    (map (fn [d] {:value (str (:db/id d))
                  :label (:samplingdevicelookup/SampleDevice d)}))
    (sort-by :label)
    vec))

(defn devices-by-type
  "Instruments grouped by device type eid, scoped to the visit's agency when
  the device records one. The ID column offers only instruments of whichever
  Device the row has selected."
  [db agency-eid]
  (->> (d/q '[:find [(pull ?d [:db/id :samplingdevice/CommonID :samplingdevice/Active
                               {:samplingdevice/DeviceType [:db/id]}
                               {:samplingdevice/Agency [:db/id]}]) ...]
              :where [?d :samplingdevice/CommonID]]
         db)
    (filter #(not= false (:samplingdevice/Active %)))
    (filter (fn [d]
              (let [a (get-in d [:samplingdevice/Agency :db/id])]
                (or (nil? a) (nil? agency-eid) (= a agency-eid)))))
    (group-by #(get-in % [:samplingdevice/DeviceType :db/id]))
    (reduce-kv (fn [m t ds]
                 (assoc m t (vec (sort-by :label
                                   (map (fn [d] {:value (str (:db/id d))
                                                 :label (:samplingdevice/CommonID d)})
                                     ds)))))
      {})))

;; ---------------------------------------------------------------------------
;; entity -> signals
;; ---------------------------------------------------------------------------

(defn row-key
  "Signals key for a parameter row."
  [param-eid]
  (keyword (str param-eid)))

(defn rep-key
  "Signals key for a replicate within a row."
  [replicate]
  (keyword (str replicate)))

(defn- ref-str [v] (if-let [id (:db/id v)] (str id) ""))

(defn ->signals
  "Grid signals for this visit: one entry per parameter, keyed by parameter eid.
  A row with no sample yet inherits :parameter/DeviceType as its default, which
  is what that attribute is for."
  [params samples]
  (let [devtypes (into {} (for [p params
                                :let [s (get samples (:db/id p))]]
                            [(row-key (:db/id p))
                             (if s
                               (ref-str (devtype-attr s))
                               (ref-str (:parameter/DeviceType p)))]))
        devids   (into {} (for [p params
                                :let [s (get samples (:db/id p))]]
                            [(row-key (:db/id p)) (ref-str (devid-attr s))]))
        times   (into {} (for [p params
                               :let [s (get samples (:db/id p))]]
                           [(row-key (:db/id p)) (or (time-attr s) "")]))
        results (into {}
                  (for [p params
                        :let [by-rep (results-by-replicate (get samples (:db/id p)))]]
                    [(row-key (:db/id p))
                     (into {}
                       (for [r (range 1 (inc (replicates p)))
                             :let [v (get-in by-rep [(str r) result-attr])]]
                         [(rep-key r) (fmt-result v)]))]))]
    (sc/signals-for {time-attr    times
                     devtype-attr devtypes
                     devid-attr   devids
                     result-attr  results})))

(defn cell-value
  "Current value of one replicate cell, from the signals map."
  [signals param-eid replicate]
  (or (get-in signals (into (vec (sc/signal-path result-attr))
                        [(row-key param-eid) (rep-key replicate)]))
      ""))

(defn time-value [signals param-eid]
  (or (get-in signals (conj (vec (sc/signal-path time-attr)) (row-key param-eid))) ""))

(defn attr-value
  "Value of a per-row sample attribute from the signals map."
  [signals attr param-eid]
  (or (get-in signals (conj (vec (sc/signal-path attr)) (row-key param-eid))) ""))

(defn row-signal
  "Dotted signal path for a per-row sample attribute."
  [attr param-eid]
  (str (sc/signal-name attr) "." param-eid))

(defn cell-signal
  "Dotted signal path for one replicate input."
  [param-eid replicate]
  (str (sc/signal-name result-attr) "." param-eid "." replicate))

(defn time-signal [param-eid]
  (str (sc/signal-name time-attr) "." param-eid))

;; ---------------------------------------------------------------------------
;; statistics
;;
;; Ported from riverdb.ui.edit.fieldmeasure so the numbers and the exceedance
;; rules match what the Fulcro form has always shown.
;; ---------------------------------------------------------------------------

(defn- ->double [v]
  (cond
    (number? v) (double v)
    (string? v) (let [t (str/trim v)]
                  (when-not (str/blank? t)
                    (try (Double/parseDouble t) (catch Exception _ nil))))
    :else nil))

(defn- precision [param]
  (when-let [code (:parameter/PrecisionCode param)]
    (try (edn/read-string code) (catch Exception _ nil))))

(defn stats
  "Derived columns for one row. `values` are the raw replicate signals.
  Returns nil-ish entries when there is nothing to compute, so the grid can
  render blanks rather than NaN — which is what the Fulcro form shows today."
  [param values]
  (let [rs (keep ->double values)
        n  (count rs)]
    (if (zero? n)
      {:n 0}
      (let [mean   (/ (reduce + rs) n)
            stddev (when (> n 1) (tu/std-dev rs))
            rsd    (when stddev (tu/round2 2 (tu/percent-prec mean stddev)))
            rnge   (tu/round2 2 (- (reduce max rs) (reduce min rs)))
            mean'  (tu/round2 2 mean)
            {prec-rsd :rsd prec-range :range threshold :threshold} (precision param)
            high   (some-> (:parameter/High param) double)
            low    (some-> (:parameter/Low param) double)]
        {:n         n
         :range     rnge
         :mean      mean'
         :stddev    (when stddev (tu/round2 2 stddev))
         :rsd       rsd
         :range-exc (when prec-range
                      (if threshold
                        (and (> rnge prec-range) (< mean threshold))
                        (> rnge prec-range)))
         :rsd-exc   (when (and prec-rsd rsd)
                      (if threshold
                        (and (> mean threshold) (> rsd prec-rsd))
                        (> rsd prec-rsd)))
         :qual-exc  (boolean (or (and high (> mean high))
                                 (and low  (< mean low))))}))))

(defn row-values
  "The replicate signals for one parameter, in replicate order."
  [signals param]
  (let [by-rep (get-in signals (conj (vec (sc/signal-path result-attr))
                                 (row-key (:db/id param))))]
    (for [r (range 1 (inc (replicates param)))]
      (get by-rep (rep-key r)))))

;; ---------------------------------------------------------------------------
;; render
;;
;; Only the derived cells are ever patched, and each carries its own id, so the
;; replicate inputs are never replaced and keep focus while you type.
;; ---------------------------------------------------------------------------

(defn- fmt [v] (if (nil? v) "" (str v)))

(defn- exc-class [flag] (when flag {:class "exc"}))

(defn stat-cells
  "The derived columns for one row. Rendered on load and re-rendered on input."
  [param st]
  (let [p (:db/id param)]
    ;; A seq, not a vector: hiccup splices seqs as siblings but treats a vector
    ;; as a single element.
    (list
     [:td {:id (str "fm-n-" p)} (fmt (when (pos? (:n st 0)) (:n st)))]
     [:td (merge {:id (str "fm-range-" p)} (exc-class (:range-exc st))) (fmt (:range st))]
     [:td (merge {:id (str "fm-mean-" p)}  (exc-class (:qual-exc st)))  (fmt (:mean st))]
     [:td {:id (str "fm-sd-" p)} (fmt (:stddev st))]
     [:td (merge {:id (str "fm-rsd-" p)}   (exc-class (:rsd-exc st)))   (fmt (:rsd st))])))

(defn devid-cell
  "The ID cell for one row. Its own element, because changing Device has to
  re-offer the instruments of the new type."
  [param signals devices]
  (let [p   (:db/id param)
        cur (str (attr-value signals devid-attr p))]
    [:td {:id (str "fm-devid-" p)}
     [:select.fm-devid {:data-bind (row-signal devid-attr p)}
      [:option {:value "" :selected (str/blank? cur)} "\u2014"]
      (for [d devices]
        [:option {:value (:value d) :selected (= (:value d) cur)} (:label d)])]]))

(defn grid
  "The Field Measurements table. `base` is the site visit URL prefix.
  `device-types` and `devices` supply the Device and ID columns; both are
  looked up once by the caller rather than per row."
  [base params signals {:keys [device-types devices]}]
  (let [max-reps (apply max 1 (map replicates params))]
    [:section.fieldmeasure
     [:h2 "Field Measurements"]
     (if (empty? params)
       [:p [:small "This project has no active field measurement parameters."]]
       [:div.overflow-auto
        [:table
         [:thead
          [:tr
           [:th {:scope "col"} "Param"]
           [:th {:scope "col"} "Device"]
           [:th {:scope "col" :title "Instrument identifier"} "ID"]
           [:th {:scope "col"} "Units"]
           (for [r (range 1 (inc max-reps))]
             [:th {:scope "col"} (str "Test " r)])
           [:th {:scope "col"} "Time"]
           [:th {:scope "col" :title "Number of readings entered"} "#"]
           [:th {:scope "col"} "Range"]
           [:th {:scope "col"} "Mean"]
           [:th {:scope "col"} "StdDev"]
           [:th {:scope "col" :title "Relative standard deviation, %"} "Prec"]]]
         [:tbody
          (for [param params
                :let [p    (:db/id param)
                      reps (replicates param)
                      st   (stats param (row-values signals param))]]
            [:tr
             [:th {:scope "row"} (:parameter/Name param)]
             [:td
              (let [cur (str (attr-value signals devtype-attr p))]
                [:select.fm-devtype
                 {:data-bind (row-signal devtype-attr p)
                  ;; Changing the type re-offers the instruments of that type.
                  :data-on:change (str "@post('" base "/fieldmeasure/" p "/device')")}
                 [:option {:value "" :selected (str/blank? cur)} "\u2014"]
                 (for [d device-types]
                   [:option {:value (:value d) :selected (= (:value d) cur)} (:label d)])])]
             (devid-cell param signals
               (get devices (some-> (not-empty (str (attr-value signals devtype-attr p))) parse-long)))
             [:td [:small (or (unit param) "")]]
             (for [r (range 1 (inc max-reps))]
               [:td
                (when (<= r reps)
                  [:input.fm-cell
                   {:type "text"
                    :inputmode "decimal"
                    :aria-label (str (:parameter/Name param) " test " r)
                    :value (cell-value signals p r)
                    :data-bind (cell-signal p r)
                    :data-on:input__debounce.300ms
                    (str "@post('" base "/fieldmeasure/" p "/stats')")}])])
             [:td [:input.fm-time
                   {:type "text"
                    :aria-label (str (:parameter/Name param) " time")
                    :value (time-value signals p)
                    :data-bind (time-signal p)}]]
             (stat-cells param st)])]]])]))

;; ---------------------------------------------------------------------------
;; save
;;
;; One sample per parameter that has readings, one fieldresult per non-blank
;; replicate. Both are Datomic components, so clearing a reading retracts its
;; fieldresult entity rather than leaving an orphan with a nil Result.
;; ---------------------------------------------------------------------------

(defn- sample-tempid [param-eid] (str "fm-sample-" param-eid))
(defn- result-tempid [param-eid r] (str "fm-result-" param-eid "-" r))

(defn row-present?
  "Was this row included in the payload for `attr` at all? Absent means \"not
  sent\", which must not be read as \"cleared\" — the same distinction
  schema/signal-has? draws for the scalar fields."
  [signals attr param-eid]
  (contains? (get-in signals (vec (sc/signal-path attr))) (row-key param-eid)))

(defn- row-tx
  "Datoms for one parameter's row. Emits nothing when the row is untouched, and
  ignores anything the payload did not mention."
  [sv-eid param sample signals]
  (let [p        (:db/id param)
        const    (get-in param [:parameter/Constituent :db/id])
        by-rep   (results-by-replicate sample)
        new?     (nil? sample)
        sid      (if new? (sample-tempid p) (:db/id sample))
        res-row  (get-in signals (conj (vec (sc/signal-path result-attr)) (row-key p)))
        entered  (when res-row
                   (for [r (range 1 (inc (replicates param)))
                         :when (contains? res-row (rep-key r))]
                     [r (->double (get res-row (rep-key r)))]))
        any?     (or (some (comp some? second) entered)
                     ;; a row that already has readings stays a row even if this
                     ;; payload only changed its device
                     (seq by-rep))
        result-tx
        (mapcat
          (fn [[r v]]
            (let [fr  (get by-rep (str r))
                  old (result-attr fr)]
              (cond
                (and fr (nil? v))    [[:db/retractEntity (:db/id fr)]]
                (nil? v)             []
                (and fr (= old v))   []
                fr                   [[:db/add (:db/id fr) result-attr v]]
                :else
                (let [frid (result-tempid p r)]
                  (cond-> [{:db/id frid
                            :fieldresult/FieldReplicate r
                            result-attr v}
                           [:db/add sid :sample/FieldResults frid]]
                    const (conj [:db/add frid :fieldresult/ConstituentRowID const]))))))
          entered)
        ;; Per-row sample attributes: diff only what the payload actually carried.
        attr-tx
        (mapcat
          (fn [attr]
            (when (row-present? signals attr p)
              (let [raw   (not-empty (str/trim (str (attr-value signals attr p))))
                    new-v (if (= attr time-attr) raw (some-> raw parse-long))
                    old-v (if (= attr time-attr) (get sample attr) (:db/id (get sample attr)))]
                (cond
                  (= new-v old-v) []
                  (nil? new-v)    (when old-v [[:db/retract sid attr old-v]])
                  :else           [[:db/add sid attr new-v]]))))
          [time-attr devtype-attr devid-attr])]
    (when (and any? (or (seq result-tx) (seq attr-tx)))
      (concat
        (when new?
          (cond-> [{:db/id sid
                    :sample/Parameter p
                    :sample/SampleTypeCode field-measure-type}
                   [:db/add sv-eid :sitevisit/Samples sid]]
            const (conj [:db/add sid :sample/Constituent const])))
        attr-tx
        result-tx))))

(defn grid-tx
  "Datoms for the whole grid. Only rows that actually changed contribute."
  [sv-eid params samples signals]
  (vec (mapcat #(row-tx sv-eid % (get samples (:db/id %)) signals) params)))
