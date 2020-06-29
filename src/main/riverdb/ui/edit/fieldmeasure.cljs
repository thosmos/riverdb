(ns riverdb.ui.edit.fieldmeasure
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]
    [clojure.data]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.application :as fapp :refer [current-state]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li a p h2 h3 button table thead
                                                tbody th tr td form label input select
                                                option span]]
    [com.fulcrologic.fulcro.mutations :as fm]
    [com.fulcrologic.fulcro.networking.file-upload :as file-upload]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.ui-state-machines :as uism :refer [defstatemachine]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-dropdown :refer [ui-form-dropdown]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-input :refer [ui-form-input]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form-text-area :refer [ui-form-text-area]]
    [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :refer [ui-dropdown]]
    [com.fulcrologic.semantic-ui.collections.form.ui-form :refer [ui-form]]
    [com.fulcrologic.semantic-ui.elements.input.ui-input :refer [ui-input]]
    [com.fulcrologic.semantic-ui.elements.header.ui-header :refer [ui-header]]
    [com.fulcrologic.semantic-ui.elements.loader.ui-loader :refer [ui-loader]]
    [com.fulcrologic.semantic-ui.modules.dimmer.ui-dimmer :refer [ui-dimmer]]
    [com.fulcrologic.semantic-ui.addons.pagination.ui-pagination :refer [ui-pagination]]
    [com.fulcrologic.semantic-ui.modules.checkbox.ui-checkbox :refer [ui-checkbox]]
    [com.fulcrologic.semantic-ui.addons.radio.ui-radio :refer [ui-radio]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table :refer [ui-table]]
    [com.fulcrologic.semantic-ui.collections.table.ui-table-body :refer [ui-table-body]]
    [com.fulcrologic.semantic-ui.modules.popup.ui-popup :refer [ui-popup]]
    [com.fulcrologic.semantic-ui.addons.confirm.ui-confirm :refer [ui-confirm]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal :refer [ui-modal]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-header :refer [ui-modal-header]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-content :refer [ui-modal-content]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-actions :refer [ui-modal-actions]]
    [com.fulcrologic.semantic-ui.modules.modal.ui-modal-description :refer [ui-modal-description]]

    [riverdb.application :refer [SPA]]
    [riverdb.api.mutations :as rm]
    [riverdb.util :refer [sort-maps-by with-index]]
    [riverdb.ui.agency :refer [preload-agency Agency]]
    [riverdb.ui.components :refer [ui-datepicker]]
    [riverdb.ui.forms :refer [SampleTypeCodeForm]]
    [riverdb.ui.forms.FieldResult :refer [FieldResultForm]]
    [riverdb.ui.globals :refer [Globals]]
    [riverdb.ui.lookup-options :refer [preload-options ui-theta-options ThetaOptions]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.parameter :refer [Parameter]]
    [riverdb.ui.project-years :refer [ProjectYears ui-project-years]]
    [riverdb.ui.routes :as routes]
    [riverdb.ui.session :refer [Session]]
    [riverdb.ui.inputs :refer [ui-float-input]]
    [riverdb.ui.upload :refer [ui-upload-modal]]
    [riverdb.ui.util :as rutil :refer [walk-ident-refs* walk-ident-refs make-tempid make-validator parse-float rui-checkbox rui-int rui-bigdec rui-input ui-cancel-save set-editing set-value set-value! set-refs! set-ref! set-ref set-refs get-ref-set-val lookup-db-ident filter-param-typecode]]
    [riverdb.util :refer [paginate nest-by filter-sample-typecode]]
    [com.rpl.specter :as sp :refer [ALL LAST]]
    ;[tick.alpha.api :as t]
    [theta.log :as log :refer [debug info]]
    [thosmos.util :as tu]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [riverdb.roles :as roles]
    [edn-query-language.core :as eql]
    [tick.core :as t]
    [testdouble.cljs.csv :as csv]))



(fm/defmutation set-all [{:keys [k v dbids onChange]}]
  (action [{:keys [state]}]
    (let [val v]
      (debug "MUTATION set-all" k val dbids onChange)
      (when onChange
        (onChange v))
      (swap! state
        (fn [s]
          (reduce
            (fn [s id]
              (-> s
                (assoc-in [:org.riverdb.db.fieldresult/gid id k] val)
                (fs/mark-complete* [:org.riverdb.db.fieldresult/gid id] k)))
            s dbids))))))


(defn set-field-result* [state db-id val]
  (-> state
    (assoc-in [:org.riverdb.db.fieldresult/gid db-id :fieldresult/Result] val)
    (fs/mark-complete* [:org.riverdb.db.fieldresult/gid db-id] :fieldresult/Result)))

(defn remove-field-result* [state samp-ident db-id]
  (debug "MUTATION remove field result" samp-ident db-id)
  (let [path     (conj samp-ident :sample/FieldResults)
        frs      (get-in state path)
        str-dbid (str db-id)
        new-frs  (vec (remove nil?
                        (for [fr frs]
                          (let [id    (second fr)
                                strid (str id)]
                            (debug "FR" strid)
                            (if (= strid str-dbid)
                              nil
                              fr)))))]
    (-> state
      (assoc-in path new-frs)
      (#(if (tempid/tempid? db-id)
          (update % :org.riverdb.db.fieldresult/gid dissoc db-id)
          %)))))

(defn fr-map->fieldresults [fr-map]
  (sp/select [ALL LAST ALL LAST ALL LAST] fr-map))

(fm/defmutation set-field-result [{:keys [val i samp-ident p-const devType devID fr-map]}]
  (action [{:keys [state]}]
    (let [const    (rutil/map->ident p-const :org.riverdb.db.constituentlookup/gid)
          const-id (rutil/get-ref-val const)
          dev-id   (rutil/get-ref-val devType)
          current  (get-in fr-map [const-id dev-id i])
          db-id    (:db/id current)]
      (debug "MUTATION set-field-result" "samp-ident" samp-ident i val "const" const "devType" devType "current" current "fr-m" fr-map)

      (cond

        ;; NOTE updated existing fieldresult
        (some? db-id)
        (do
          (debug "UPDATE EXISTING" db-id)
          (if (some? val)
            (swap! state set-field-result* db-id val)
            (swap! state remove-field-result* samp-ident db-id)))

        ;; NOTE add a new fieldresult
        (and (nil? db-id) (some? val))
        (let [new-id   (tempid/tempid)
              new-fr   (comp/get-initial-state FieldResultForm
                         {:id new-id :uuid (tempid/uuid) :const p-const :rep i :result val :devID devID :devType devType})
              new-fr'  (rutil/convert-db-refs new-fr)
              new-form (fs/add-form-config FieldResultForm new-fr')
              fr-map'  (assoc-in fr-map [const-id dev-id i] new-form)
              frs      (mapv rutil/thing->ident (fr-map->fieldresults fr-map'))]
          (debug "CREATE NEW" "fr-map'" fr-map' "frs" frs)
          (swap! state
            (fn [st]
              (-> st
                (merge/merge-component FieldResultForm new-form)
                (assoc-in (conj samp-ident :sample/FieldResults) frs)))))))))

(defn rui-fieldres [samp-ident i p-const rs-map fr-map devType devID]
  (let [fr  (get rs-map i)
        val (:fieldresult/Result fr)]
    ;(debug "RENDER rui-fieldres" i (pr-str val))
    (ui-float-input {:type     "text"
                     :value    val
                     :style    {:width "60px" :paddingLeft 7 :paddingRight 7}
                     :onChange #(let [db-id (:db/id fr)
                                      v     %]
                                  (debug "CHANGE FIELD RESULT" db-id v)
                                  (comp/transact! SPA `[(set-field-result
                                                          ~{:samp-ident samp-ident
                                                            :p-const    p-const
                                                            :devType    devType
                                                            :fr-map     fr-map
                                                            :devID      devID
                                                            :val        v
                                                            :i          i})]))})))

(defsc FieldMeasureSampleForm
  [this
   {:keys [param sample] :as props}
   {:keys [sv-comp reps deviceTypeLookup deviceLookup onChangeSample]}]
  {:query          [:param :sample]
   :initLocalState (fn [this props]
                     ;(debug "INIT LOCAL STATE FieldMeasureParamForm" props)
                     (let [;comps  (:fulcro.client.primitives/computed props)
                           sample (:sample props)
                           stuff {:time          (:sample/Time sample)
                                  :setDeviceType #(do
                                                    (debug "SET STATE :devType" %)
                                                    (comp/set-state! this {:devType %}))
                                  :setDeviceID   #(do
                                                    (debug "SET STATE :devID" %)
                                                    (comp/set-state! this {:devID %}))}]
                       (debug "INIT LOCAL STATE FieldMeasureSampleForm" stuff)
                       stuff))}

  (let [p-id          (:db/id param)
        p-name        (:parameter/Name param)
        p-device      (:parameter/DeviceType param)
        p-const       (:parameter/Constituent param)
        {:parameter/keys [PrecisionCode Low High Replicates]} param
        samp-ident    (when sample (comp/get-ident sample))
        results       (:sample/FieldResults sample)
        rs-map        (into {} (map #(identity [(:fieldresult/FieldReplicate %) %]) results))
        rslts         (->> (mapv :fieldresult/Result results)
                        (mapv parse-float)
                        (remove nil?))
        {:keys [time setDeviceType setDeviceID devType devID] :as state} (comp/get-state this)
        ;_             (debug "READ STATE" state)
        fst           (first results)
        devFst        (:fieldresult/SamplingDeviceCode fst)
        devType       (if fst
                        devFst
                        (or devType p-device))
        devType       (rutil/map->ident devType :org.riverdb.db.samplingdevicelookup/gid)
        devIDfst      (:fieldresult/SamplingDeviceID fst)
        devID         (if fst
                        devIDfst
                        devID)
        devID         (rutil/map->ident devID :org.riverdb.db.samplingdevice/gid)
        mean          (/ (reduce + rslts) (count rslts))
        stddev        (tu/std-dev rslts)
        rsd           (tu/round2 2 (tu/percent-prec mean stddev))
        rnge          (tu/round2 2 (- (reduce max rslts) (reduce min rslts)))
        mean          (tu/round2 2 mean)

        stddev        (tu/round2 2 stddev)
        prec          (clojure.edn/read-string PrecisionCode)
        precRSD       (:rsd prec)
        precRange     (:range prec)
        precThreshold (:threshold prec)
        rangeExc      (when precRange
                        (if precThreshold
                          (and
                            (> rnge precRange)
                            (< mean precThreshold))
                          (> rnge precRange)))
        low           (when Low (.-rep Low))
        high          (when High (.-rep High))

        qualExc       (or
                        (and high (> mean high))
                        (and low (< mean low)))

        ;_             (debug "prec" prec "rnge" rnge "precRange" precRange "typeRange" (type precRange)
        ;                "typeRSD" (type precRSD) "typeThresh" (type precThreshold) "range exceedance?" (> rnge precRange))



        rsdExc        (when precRSD
                        (if precThreshold
                          (and
                            (> mean precThreshold)
                            (> rsd precRSD))
                          (> rsd precRSD)))
        time          (or time (:fieldresult/ResultTime (first results)))
        unit          (get-in p-const [:constituentlookup/UnitCode :unitlookup/Unit])]
    (debug "RENDER FieldMeasureSampleForm" p-name "rs-map" rs-map "props" props) ; "devType" devType "devID" devID "p-const" p-const "fieldresults" fieldresults)

    (tr {:key p-id}
      (td {:key "name"} p-name)
      (td {:key "inst"}
        (ui-theta-options (comp/computed
                            (merge deviceTypeLookup
                              {:riverdb.entity/ns :entity.ns/samplingdevicelookup
                               :value             devType
                               :opts              {:load  false
                                                   :style {:paddingLeft 5 :paddingRight 5}}})
                            {:changeMutation `set-all
                             :changeParams   {:dbids    (mapv :db/id results)
                                              :k        :fieldresult/SamplingDeviceCode
                                              :onChange setDeviceType}})))

      (td {:key "instID"}
        (ui-theta-options (comp/computed
                            (merge deviceLookup
                              {:riverdb.entity/ns :entity.ns/samplingdevice
                               :value             devID
                               :filter-key        :samplingdevice/DeviceType
                               :filter-val        {:db/id (second devType)}
                               :opts              {:load false :style {:width 70 :minWidth 50 :paddingLeft 5 :paddingRight 5}}})
                            {:changeMutation `set-all
                             :changeParams   {:dbids    (mapv :db/id results)
                                              :k        :fieldresult/SamplingDeviceID
                                              :onChange setDeviceID}})))

      (td {:key "unit"} (or unit ""))

      ;; NOTE this is the (difficult) shizzle here
      (mapv
        (fn [i]
          (td {:key i}
            #_(rui-fieldres samp-ident i (select-keys p-const [:db/id]) rs-map fr-map devType devID)))
        (range 1 (inc reps)))


      (td {:key "time"} (dom/input {:type     "text"
                                    :style    {:width "80px" :paddingLeft 7 :paddingRight 7}
                                    :value    (or (str time) "")
                                    :onChange #(let [value (-> % .-target .-value)]
                                                 (comp/transact! this `[(set-all {:dbids ~(mapv :db/id results)
                                                                                  :k     :fieldresult/ResultTime
                                                                                  :v     ~value})]))}))
      (td {:key "reps" :style {:color (if (< (count rslts) Replicates) "red" "black")}} (or (str (count rslts)) ""))
      (ui-popup
        {:open    false
         :trigger (td {:key "range" :style {:color (if rangeExc "red" "black")}} (or (str rnge) ""))}
        "Range exceedance")
      (td {:key "mean" :style {:color (if qualExc "red" "black")}} (or (str mean) ""))
      (td {:key "stddev"} (or (str stddev) ""))
      (ui-popup
        {:open    false
         :trigger (td {:key "prec" :style {:color (if rsdExc "red" "black")}} (or (str rsd) ""))}
        "Prec exceedance"))))

(def ui-fieldmeasure-sample-form (comp/factory FieldMeasureSampleForm {:keyfn :db/id}))


(defn calc-param-samples [params samples]
  (reduce
    (fn [out sa]
      (let [sa-param   (get-in sa [:sample/Parameter :db/id])
            sa-const   (get-in sa [:sample/Constituent :db/id])
            sa-devType (get-in sa [:sample/DeviceType :db/id])]))
    {} samples))

(defsc FieldMeasureList [this {:ui/keys [params samples] :as props} {:keys [sv-comp onChangeSample] :as comps}]
  {:query [:ui/params
           :ui/samples
           [:riverdb.theta.options/ns '_]]}
  (let [deviceTypeLookup (get-in props [:riverdb.theta.options/ns :entity.ns/samplingdevicelookup])
        deviceLookup     (get-in props [:riverdb.theta.options/ns :entity.ns/samplingdevice])
        max-reps         (reduce
                           (fn [mx p]
                             (if-let [reps (:parameter/ReplicatesEntry p)]
                               (max reps mx)
                               mx))
                           1 params)
        param-samps      (calc-param-samples params samples)]
    (debug "RENDER FieldMeasureList")
    (div :.ui.segment {}
      (ui-header {} "Field Measurements")
      (table :.ui.very.compact.mini.table {:key "wqtab"}
        (thead {:key 1}
          (tr {}
            (th {:key "nm"} "Param")
            (th {:key "inst"} "Device")
            (th {:key "instID"} "ID")
            (th {:key "units"} "Units")
            (mapv
              #(identity
                 (th {:key (str %)} (str "Test " %)))
              (range 1 (inc max-reps)))
            (th {:key "time"} "Time")
            (th {:key "reps"} "# Tests")
            (th {:key "range"} "Range")
            (th {:key "mean"} "Mean")
            (th {:key "stddev"} "StdDev")
            (th {:key "prec"} "Prec")))
        (tbody {:key 2}
          (vec
            (for [param params]
              (let [sample (param-samps param)]
                (ui-fieldmeasure-sample-form
                  (comp/computed {:param param :sample sample}
                    {:onChangeSample   onChangeSample
                     :deviceTypeLookup deviceTypeLookup
                     :deviceLookup     deviceLookup
                     :sv-comp          sv-comp
                     :reps             max-reps}))))))))))

(def ui-fm-list (comp/factory FieldMeasureList))
