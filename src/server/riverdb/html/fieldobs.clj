(ns riverdb.html.fieldobs
  "Field observations for the SiteVisit form.

  Rows are the project's active parameters whose :parameter/SampleType is
  FieldObs. Unlike the measurement grid, each row's widget differs: the
  parameter's :parameter/FieldObsType selects it.

    :ref     single choice  -> radio group
    :refs    multi choice   -> checkbox group
    :bigdec  number         -> numeric input   (:long is legacy data, same shape)
    :text    free text      -> text input

  Choices come from fieldobsvarlookup rows sharing the parameter's analyte
  name, reached via :parameter/Constituent -> AnalyteCode -> AnalyteName.

  Signals keep their Datomic namespace and key by parameter:

    {:fieldobsresult {:RefResult    {\"<param>\" \"<var-eid>\"}
                      :RefResults   {\"<param>\" [\"<var-eid>\" ...]}
                      :BigDecResult {\"<param>\" \"60\"}
                      :TextResult   {\"<param>\" \"...\"}}}"
  (:require
    [clojure.string :as str]
    [datomic.api :as d]
    [riverdb.html.schema :as sc]))

(def field-obs-type :sampletypelookup.SampleTypeCode/FieldObs)

(def ref-attr  :fieldobsresult/RefResult)
(def refs-attr :fieldobsresult/RefResults)
(def num-attr  :fieldobsresult/BigDecResult)
(def text-attr :fieldobsresult/TextResult)

(defn widget
  "Which control this parameter wants. :long is legacy data that renders the
  same as :bigdec; anything unrecognised falls back to free text rather than
  rendering nothing."
  [param]
  (case (:parameter/FieldObsType param)
    :ref    :ref
    :refs   :refs
    :bigdec :number
    :long   :number
    :text   :text
    :text))

(defn value-attr [param]
  (case (widget param)
    :ref    ref-attr
    :refs   refs-attr
    :number num-attr
    text-attr))

;; ---------------------------------------------------------------------------
;; reads
;; ---------------------------------------------------------------------------

(def ^:private param-pull
  '[:db/id
    :parameter/Name
    :parameter/Order
    :parameter/Active
    :parameter/FieldObsType
    {:parameter/SampleType [:db/ident]}
    {:parameter/ObsOptions [:db/id]}
    {:parameter/Constituent
     [{:constituentlookup/AnalyteCode [:db/id :analytelookup/AnalyteName]}]}])

(defn params
  "Active field-observation parameters for a project, in display order."
  [db project-eid]
  (when project-eid
    (->> (d/q '[:find [(pull ?p pull-spec) ...]
                :in $ ?proj pull-spec
                :where [?proj :projectslookup/Parameters ?p]]
           db project-eid param-pull)
      (filter :parameter/Active)
      (filter #(= field-obs-type (get-in % [:parameter/SampleType :db/ident])))
      (sort-by (juxt #(or (:parameter/Order %) 999) :parameter/Name))
      vec)))

(defn analyte
  "The analyte entity id this parameter observes. Options join to it by ref
  (:fieldobsvarlookup/Analyte) rather than by matching the AnalyteName string,
  which a rename or a typo would silently break."
  [param]
  (get-in param [:parameter/Constituent
                 :constituentlookup/AnalyteCode
                 :db/id]))

(def not-recorded
  "The \"not recorded\" choice is synthesised, not stored: it means the absence
  of a value, so its wire value is the empty string. The lookup does contain
  rows spelled \"Not Recorded\" / \"not recorded\", but they are legacy and are
  filtered out by the IntCode rule below."
  {:value "" :label "NR"})

(defn options-by-analyte
  "Configured choices, grouped by analyte entity and ordered by
  :fieldobsvarlookup/Order.

  An option is offered when it has an Order and is not explicitly inactive.
  Order's presence is the switch: it replaces the old IntCode > 0 convention,
  which used one number for display order, for whether to offer the option at
  all, and as a sentinel hiding OtherPresence's \"none\" at zero. Giving a
  dormant option an Order is now a data edit rather than a code change."
  [db]
  (->> (d/q '[:find [(pull ?v [:db/id
                               :fieldobsvarlookup/ValueCode
                               :fieldobsvarlookup/ValueCodeDescr
                               :fieldobsvarlookup/Order
                               :fieldobsvarlookup/Active
                               {:fieldobsvarlookup/Analyte [:db/id]}]) ...]
              :where [?v :fieldobsvarlookup/Order]]
         db)
    (filter #(not= false (:fieldobsvarlookup/Active %)))
    (group-by #(get-in % [:fieldobsvarlookup/Analyte :db/id]))
    (reduce-kv
      (fn [m a vs]
        (assoc m a (vec (for [v (sort-by :fieldobsvarlookup/Order vs)]
                          {:value (str (:db/id v))
                           :label (or (:fieldobsvarlookup/ValueCode v)
                                      (:fieldobsvarlookup/ValueCodeDescr v))}))))
      {})))

(defn options-for
  "Choices to render for one parameter. A parameter may curate a subset via
  :parameter/ObsOptions; empty means every configured option for its analyte.
  Order still comes from the vocabulary, since these are mostly ordinal scales
  and the order is a property of the scale rather than of the parameter.

  Single-choice rows get the synthetic \"not recorded\" first, so there is a way
  to say nothing was observed. Multi-choice rows do not: unchecking everything
  already says that."
  [options param]
  (let [all     (get options (analyte param))
        curated (set (map (comp str :db/id) (:parameter/ObsOptions param)))
        opts    (if (seq curated)
                  (filterv #(contains? curated (:value %)) all)
                  all)]
    (if (and (seq opts) (= :ref (widget param)))
      (into [not-recorded] opts)
      opts)))

(defn samples-by-param
  "This visit's field-observation samples, keyed by parameter eid."
  [sv]
  (into {}
    (for [s (:sitevisit/Samples sv)
          :when (= field-obs-type (get-in s [:sample/SampleTypeCode :db/ident]))
          :let [p (get-in s [:sample/Parameter :db/id])]
          :when p]
      [p s])))

(defn- result-of [sample]
  (first (:sample/FieldObsResults sample)))

;; ---------------------------------------------------------------------------
;; entity <-> signals
;; ---------------------------------------------------------------------------

(defn row-key [param-eid] (keyword (str param-eid)))

(defn ->signals
  "Signals for the section. `options` is the by-analyte map, needed because a
  checkbox group's array must be POSITIONAL.

  Datastar writes a checkbox group by index — clicking the third box sets
  element 3 — while reading it by containment. Seeding a compact list like
  [\"foam\"] therefore means clicking any other box overwrites slot 0 and the
  original selection is silently lost. The array must be as long as the option
  list, with \"\" in every unchecked position."
  [params samples options]
  (let [pick (fn [attr f]
               (into {} (for [p params
                              :when (= attr (value-attr p))
                              :let [r (result-of (get samples (:db/id p)))]]
                          [(row-key (:db/id p)) (f p (get r attr))])))]
    (sc/signals-for
      {ref-attr  (pick ref-attr  (fn [_ v] (if-let [id (:db/id v)] (str id) "")))
       refs-attr (pick refs-attr
                   (fn [param v]
                     (let [chosen (set (map (comp str :db/id) v))]
                       (mapv #(if (contains? chosen (:value %)) (:value %) "")
                         (options-for options param)))))
       num-attr  (pick num-attr  (fn [_ v] (if (some? v) (str v) "")))
       text-attr (pick text-attr (fn [_ v] (or v "")))})))

(defn signal-for [param param-eid]
  (str (sc/signal-name (value-attr param)) "." param-eid))

(defn value-of [signals param]
  (get-in signals (conj (vec (sc/signal-path (value-attr param)))
                    (row-key (:db/id param)))))

(defn present?
  "Did the payload mention this row at all? Absent means not sent, which must
  not read as cleared."
  [signals param]
  (contains? (get-in signals (vec (sc/signal-path (value-attr param))))
    (row-key (:db/id param))))

(defn checked-values
  "Datastar writes a checkbox group positionally, padding unchecked boxes with
  empty strings — [\"x\" \"\" \"z\"], not [\"x\" \"z\"]. Reading is by
  containment, so blanks are safe to drop, but they must be dropped or they
  become bogus refs."
  [v]
  (vec (keep #(some-> % str not-empty) (if (sequential? v) v [v]))))

;; ---------------------------------------------------------------------------
;; render
;; ---------------------------------------------------------------------------

(defn- radio-group [param signal cur options]
  (let [nm (str "fo-" (:db/id param))]
    [:div.fo-choices
     (for [o options]
       [:label.fo-choice
        [:input {:type "radio" :name nm :value (:value o)
                 :data-bind signal
                 :checked (= (:value o) (str cur))}]
        (:label o)])]))

(defn- checkbox-group [param signal cur options]
  (let [chosen (set (map str (checked-values cur)))]
    [:div.fo-choices
     (for [o options]
       [:label.fo-choice
        [:input {:type "checkbox" :value (:value o)
                 :data-bind signal
                 :checked (contains? chosen (:value o))}]
        (:label o)])]))

(defn- control [param signal cur options]
  (case (widget param)
    :ref    (radio-group param signal cur options)
    :refs   (checkbox-group param signal cur options)
    :number [:input.fo-number {:type "text" :inputmode "decimal"
                               :aria-label (:parameter/Name param)
                               :value (str (or cur ""))
                               :data-bind signal}]
    [:input.fo-text {:type "text"
                     :aria-label (:parameter/Name param)
                     :value (str (or cur ""))
                     :data-bind signal}]))

(defn section
  "The Field Observations table."
  [params signals options]
  [:section.fieldobs
   [:h2 "Field Observations"]
   (if (empty? params)
     [:p [:small "This project has no active field observation parameters."]]
     [:div.overflow-auto
      [:table
       [:thead [:tr [:th {:scope "col"} "Parameter"] [:th {:scope "col"} "Value"]]]
       [:tbody
        (for [param params
              :let [p    (:db/id param)
                    opts (options-for options param)]]
          [:tr
           [:th {:scope "row"} (:parameter/Name param)]
           [:td (control param (signal-for param p) (value-of signals param) opts)]])]]])])

;; ---------------------------------------------------------------------------
;; save
;;
;; One sample per parameter, holding a single fieldobsresult. Both are Datomic
;; components, so a cleared observation retracts the result entity.
;; ---------------------------------------------------------------------------

(defn- ->bigdec [v]
  (let [t (some-> v str str/trim not-empty)]
    (when t (try (bigdec t) (catch Exception _ nil)))))

(defn- decoded
  "The value this row wants written, in its attribute's own type."
  [param raw]
  (case (widget param)
    :ref    (some-> (not-empty (str/trim (str raw))) parse-long)
    :refs   (vec (keep parse-long (checked-values raw)))
    :number (->bigdec raw)
    (some-> (str raw) str/trim not-empty)))

(defn- row-tx [sv-eid param sample signals]
  (when (present? signals param)
    (let [p     (:db/id param)
          attr  (value-attr param)
          res   (result-of sample)
          new-v (decoded param (value-of signals param))
          old-v (let [v (get res attr)]
                  (case (widget param)
                    :ref  (:db/id v)
                    :refs (vec (sort (map :db/id v)))
                    v))
          new-c (if (= :refs (widget param)) (vec (sort new-v)) new-v)
          empty? (if (= :refs (widget param)) (empty? new-c) (nil? new-c))]
      (cond
        (= new-c old-v) nil

        ;; cleared: drop the result entity, and the sample with it if that was
        ;; all it held
        (and empty? res) [[:db/retractEntity (:db/id res)]]
        empty?           nil

        :else
        (let [new-sample? (nil? sample)
              sid  (if new-sample? (str "fo-sample-" p) (:db/id sample))
              new-res?    (nil? res)
              rid  (if new-res? (str "fo-result-" p) (:db/id res))]
          (concat
            (when new-sample?
              [{:db/id sid
                :sample/Parameter p
                :sample/SampleTypeCode field-obs-type}
               [:db/add sv-eid :sitevisit/Samples sid]])
            (when new-res?
              [{:db/id rid}
               [:db/add sid :sample/FieldObsResults rid]])
            (if (= :refs (widget param))
              (concat
                (for [v (remove (set new-c) (or old-v []))] [:db/retract rid attr v])
                (for [v (remove (set (or old-v [])) new-c)] [:db/add rid attr v]))
              [[:db/add rid attr new-c]])))))))

(defn tx
  "Datoms for the whole section. Only rows the payload mentioned, and only
  those that actually changed, contribute."
  [sv-eid params samples signals]
  (vec (mapcat #(row-tx sv-eid % (get samples (:db/id %)) signals) params)))
