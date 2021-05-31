(ns riverdb.ui.forms.sample
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
    ;[riverdb.ui.forms :refer [SampleTypeCodeForm]]
    ;[riverdb.ui.forms.FieldResult :refer [FieldResultForm]]
    [riverdb.ui.globals :refer [Globals]]
    [riverdb.ui.lookup-options :refer [preload-options ui-theta-options ThetaOptions]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.parameter :refer [Parameter]]
    [riverdb.ui.project-years :refer [ProjectYears ui-project-years]]
    [riverdb.ui.routes :as routes]
    [riverdb.ui.session :refer [Session]]
    [riverdb.ui.inputs :refer [ui-float-input]]
    [riverdb.ui.upload :refer [ui-upload-modal]]
    [riverdb.ui.util :as rutil :refer [walk-ident-refs* walk-ident-refs make-tempid make-validator parse-float rui-checkbox rui-int rui-bigdec rui-input ui-cancel-save set-editing set-value set-value!! set-refs! set-ref! set-ref set-refs get-ref-set-val lookup-db-ident filter-param-typecode]]
    [riverdb.util :refer [paginate nest-by filter-sample-typecode]]
    [com.rpl.specter :as sp :refer [ALL LAST]]
    ;[tick.alpha.api :as t]
    [theta.log :as log :refer [debug info]]
    [thosmos.util :as tu]
    [com.fulcrologic.fulcro.data-fetch :as df]
    [riverdb.roles :as roles]
    [edn-query-language.core :as eql]
    [tick.alpha.api :as t]
    [testdouble.cljs.csv :as csv]))

(defsc AnalyteRefForm [this props]
  {:ident [:org.riverdb.db.analytelookup/gid :db/id]
   :query [:db/id
           :riverdb.entity/ns
           :analytelookup/AnalyteCode
           :analytelookup/AnalyteDescr
           :analytelookup/AnalyteName
           :analytelookup/AnalyteShort
           fs/form-config-join]
   :form-fields #{:db/id}})

(defsc Constituent [this props]
  {:ident [:org.riverdb.db.constituentlookup/gid :db/id]
   :query [:db/id
           :riverdb.entity/ns
           :constituentlookup/Name
           :constituentlookup/Analyte
           fs/form-config-join]})

(defsc ConstituentRefForm [this props]
  {:ident       [:org.riverdb.db.constituentlookup/gid :db/id]
   :query       [:db/id
                 :riverdb.entity/ns
                 :constituentlookup/Name
                 {:constituentlookup/AnalyteCode (comp/get-query AnalyteRefForm)}
                 fs/form-config-join]
   :form-fields #{:db/id}})

(defsc DeviceTypeForm [_ _]
  {:ident         [:org.riverdb.db.samplingdevicelookup/gid :db/id],
   :initial-state (fn [{:keys [id]}]
                    {:db/id             (or id (tempid/tempid)),
                     :riverdb.entity/ns :entity.ns/samplingdevicelookup})
   :query         [:db/id
                   :riverdb.entity/ns
                   fs/form-config-join
                   :samplingdevicelookup/Active
                   :samplingdevicelookup/SampleDevice]
   :form-fields  #{:db/id}})

(defsc DeviceIDForm [_ _]
  {:ident         [:org.riverdb.db.samplingdevice/gid :db/id],
   :initial-state (fn [{:keys [id]}]
                    {:db/id             (or id (tempid/tempid)),
                     :riverdb.entity/ns :entity.ns/samplingdevice})
   :query         [:db/id
                   :riverdb.entity/ns
                   fs/form-config-join
                   :samplingdevice/CommonID
                   :samplingdevice/DeviceType]
   :form-fields   #{:db/id}})

(defsc SampleTypeForm [this {:keys [] :as props}]
  {:ident       [:org.riverdb.db.sampletypelookup/gid :db/id]
   :initial-state (fn [{:keys [id]}]
                    {:db/id             (or id (tempid/tempid)),
                     :riverdb.entity/ns :entity.ns/sampletypelookup})
   :query       [:db/id
                 :db/ident
                 fs/form-config-join
                 :riverdb.entity/ns
                 :sampletypelookup/uuid
                 :sampletypelookup/SampleTypeCode
                 :sampletypelookup/CollectionType]
   :form-fields #{:db/id :db/ident :riverdb.entity/ns}})

(defsc ParameterForm [_ _]
  {:ident         [:org.riverdb.db.parameter/gid :db/id]
   :query         (fn []
                    [fs/form-config-join
                     :db/id
                     :riverdb.entity/ns
                     :parameter/Active
                     :parameter/Color
                     :parameter/High
                     :parameter/Lines
                     :parameter/Low
                     :parameter/Name
                     :parameter/NameShort
                     :parameter/Order
                     :parameter/PrecisionCode
                     :parameter/Replicates
                     :parameter/ReplicatesEntry
                     :parameter/ReplicatesElide
                     ;; passed to lookup dropdowns
                     :parameter/uuid
                     :parameter/FieldObsType
                     {:parameter/DeviceType (comp/get-query DeviceTypeForm)}
                     {:parameter/Constituent (comp/get-query ConstituentRefForm)}
                     {:parameter/SampleType (comp/get-query SampleTypeForm)}])

   :initial-state (fn [{:keys [type]}]
                    {:db/id                (tempid/tempid)
                     :parameter/uuid       (tempid/uuid)
                     :riverdb.entity/ns    :entity.ns/parameter
                     :parameter/Name       ""
                     :ui/editing           true
                     :ui/saving            false
                     :parameter/Active     false
                     :parameter/Order      999
                     :parameter/SampleType type
                     :parameter/FieldObsType :text})

   :form-fields   #{:db/id
                    :riverdb.entity/ns
                    :parameter/uuid
                    :parameter/Name
                    :parameter/Active
                    :parameter/Constituent
                    :parameter/DeviceType
                    :parameter/SampleType
                    :parameter/Color
                    :parameter/High
                    :parameter/Lines
                    :parameter/Low
                    :parameter/NameShort
                    :parameter/Order
                    :parameter/Replicates
                    :parameter/ReplicatesEntry
                    :parameter/ReplicatesElide
                    :parameter/PrecisionCode
                    :parameter/FieldObsType}})

(defsc FieldObsVarForm [_ _]
  {:ident       [:org.riverdb.db.fieldobsvarlookup/gid :db/id]
   :query       [fs/form-config-join
                 :db/id
                 :riverdb.entity/ns
                 :fieldobsvarlookup/uuid
                 :fieldobsvarlookup/Active
                 :fieldobsvarlookup/AnalyteName
                 :fieldobsvarlookup/IntCode
                 :fieldobsvarlookup/ValueCode
                 :fieldobsvarlookup/ValueCodeDescr]
   :form-fields #{:db/id
                  :riverdb.entity/ns
                  :fieldobsvarlookup/uuid
                  :fieldobsvarlookup/Active
                  :fieldobsvarlookup/AnalyteName
                  :fieldobsvarlookup/IntCode
                  :fieldobsvarlookup/ValueCode
                  :fieldobsvarlookup/ValueCodeDescr}})

(defsc FieldResultForm [this {:keys [] :as props}]
  {:ident         [:org.riverdb.db.fieldresult/gid :db/id]
   :query         [:db/id
                   :riverdb.entity/ns
                   fs/form-config-join
                   :fieldresult/uuid
                   :fieldresult/Result
                   :fieldresult/ResultTime
                   :fieldresult/FieldReplicate
                   :fieldresult/ConstituentRowID
                   :fieldresult/SamplingDeviceID
                   :fieldresult/SamplingDeviceCode
                   :fieldresult/FieldResultComments]
   :form-fields   #{:db/id
                    :riverdb.entity/ns
                    :fieldresult/uuid
                    :fieldresult/Result
                    :fieldresult/ResultTime
                    :fieldresult/FieldReplicate
                    :fieldresult/ConstituentRowID
                    :fieldresult/SamplingDeviceID
                    :fieldresult/SamplingDeviceCode
                    :fieldresult/FieldResultComments}
   :initial-state {:db/id                          :param/id
                   :riverdb.entity/ns              :entity.ns/fieldresult
                   :fieldresult/uuid               :param/uuid
                   :fieldresult/Result             :param/result
                   :fieldresult/ResultTime         :param/time
                   :fieldresult/FieldReplicate     :param/rep
                   :fieldresult/ConstituentRowID   :param/const
                   :fieldresult/SamplingDeviceID   :param/devID
                   :fieldresult/SamplingDeviceCode :param/devType}})

(defsc FieldObsResultForm [this {:keys [] :as props}]
  {:ident         [:org.riverdb.db.fieldobsresult/gid :db/id]
   :query         [:db/id
                   :riverdb.entity/ns
                   fs/form-config-join
                   :fieldobsresult/uuid
                   :fieldobsresult/IntResult
                   :fieldobsresult/TextResult
                   :fieldobsresult/RefResult
                   :fieldobsresult/RefResults
                   :fieldobsresult/BigDecResult
                   :fieldobsresult/FieldObsResultComments]
   :form-fields   #{:riverdb.entity/ns
                    :fieldobsresult/uuid
                    :fieldobsresult/IntResult
                    :fieldobsresult/TextResult
                    :fieldobsresult/RefResult
                    :fieldobsresult/RefResults
                    :fieldobsresult/BigDecResult
                    :fieldobsresult/FieldObsResultComments}
   :initial-state {:riverdb.entity/ns               :entity.ns/fieldobsresult
                   :db/id                           :param/id
                   :fieldobsresult/uuid             :param/uuid
                   :fieldobsresult/IntResult        :param/int
                   :fieldobsresult/TextResult       :param/text
                   :fieldobsresult/RefResult        :param/ref
                   :fieldobsresult/RefResults       :param/refs
                   :fieldobsresult/BigDecResult     :param/bigdec}})

(comment
  {:sample/Constituent           #:db{:id 17592186156795},
   :sample/uuid                  #uuid"5ef96476-b262-43b5-af9d-4d7a956ed059",
   :sample/SampleTypeCode        #:db{:id 17592186178106},
   :sample/EventType             #:db{:id 17592186170823},
   :sample/SampleDate            #inst"2001-01-13T08:00:00.000-00:00",
   :sample/QCCheck               false,
   :sample/Unit                  "m",
   :sample/DepthSampleCollection 0.10000M,
   :riverdb.entity/ns            :entity.ns/sample,
   :db/id                        17592186311523,
   :sample/SampleReplicate       1,
   :sample/LabResults            [{:db/id                       17592186311524,
                                   :labresult/ResQualCode       {:resquallookup/ResQualCode "ND",
                                                                 :resquallookup/uuid #uuid"5e3e50f0-fa6e-4b30-a701-6f02d0351665",
                                                                 :resquallookup/Type3 "Field",
                                                                 :riverdb.entity/ns :entity.ns/resquallookup,
                                                                 :resquallookup/Type1 "Lab",
                                                                 :resquallookup/Active true,
                                                                 :db/id 17592186158558,
                                                                 :resquallookup/ResQualifier "Not Detected",
                                                                 :resquallookup/Type2 "Tox"},
                                   :labresult/DigestExtractCode {:db/id 17592186152306,
                                                                 :digestextractlookup/DigestExtractRowID 6,
                                                                 :digestextractlookup/DigestExtractCode 1,
                                                                 :digestextractlookup/DigestExtractMethod "Not Recorded",
                                                                 :digestextractlookup/DigestExtractDescr "Not Recorded",
                                                                 :digestextractlookup/Active true,
                                                                 :riverdb.entity/ns :entity.ns/digestextractlookup,
                                                                 :digestextractlookup/uuid #uuid"5e3e50eb-30c9-4fe4-98f3-87c0c9bb601f"},
                                   :labresult/LabResultComments "Orig value: ND",
                                   :labresult/LabReplicate      1,
                                   :labresult/PrepCode          {:db/id 17592186046970,
                                                                 :preplookup/PrepRowID 6,
                                                                 :preplookup/PrepCode 1,
                                                                 :preplookup/Preparation "Not Recorded",
                                                                 :preplookup/Filtered false,
                                                                 :preplookup/Active true,
                                                                 :riverdb.entity/ns :entity.ns/preplookup,
                                                                 :preplookup/uuid #uuid"5e3e50f0-f8de-4e01-ae8e-6d1762a76176"}
                                   :riverdb.entity/ns           :entity.ns/labresult,
                                   :labresult/uuid              #uuid"5ef96476-148c-4c44-9796-b8aa5624dd20"}]})

(defsc ResQualCodeForm [this props]
  {:ident [:org.riverdb.db.resquallookup/gid :db/id]
   :query [:db/id
           :riverdb.entity/ns
           fs/form-config-join
           :resquallookup/ResQualCode
           :resquallookup/ResQualifier]
   :form-fields   #{:db/id}})

(defsc LabResultForm [this {:keys [] :as props}]
  {:ident         [:org.riverdb.db.labresult/gid :db/id]
   :query         [:db/id
                   :riverdb.entity/ns
                   fs/form-config-join
                   :labresult/uuid
                   :labresult/AnalysisDate
                   :labresult/LabReplicate
                   :labresult/LabResultComments
                   :labresult/Result
                   :labresult/SigFig
                   {:labresult/ResQualCode (comp/get-query ResQualCodeForm)}]
                   ;{:labresult/ConstituentRowID (comp/get-query Constituent)}]
   :form-fields   #{:db/id
                    :riverdb.entity/ns
                    :labresult/uuid
                    :labresult/AnalysisDate
                    :labresult/LabReplicate
                    :labresult/LabResultComments
                    :labresult/Result
                    :labresult/SigFig
                    :labresult/ResQualCode}
   :initial-state (fn [params]
                    {:db/id                  (tempid/tempid)
                     :riverdb.entity/ns      :entity.ns/labresult
                     :labresult/uuid         (tempid/uuid)
                     :labresult/LabReplicate 1
                     :labresult/LabResultComments nil
                     :labresult/ResQualCode nil})})

(defsc SampleForm [this
                   {:ui/keys [ready]
                    :sample/keys [SampleTypeCode
                                  FieldResults
                                  FieldObsResults
                                  LabResults
                                  uuid] :as props}
                   {:keys [samplingdevicelookup-options
                           fieldobsvarlookup-options
                           active-params
                           sv-comp
                           onChange]}]
  {:ident         [:org.riverdb.db.sample/gid :db/id]
   :query         [:db/id
                   :riverdb.entity/ns
                   fs/form-config-join
                   :ui/ready
                   :sample/uuid
                   :sample/SampleDate
                   :sample/SampleTime
                   :sample/Time
                   {:sample/DeviceID (comp/get-query DeviceIDForm)}
                   {:sample/DeviceType (comp/get-query DeviceTypeForm)}
                   {:sample/Constituent (comp/get-query ConstituentRefForm)}
                   {:sample/Parameter (comp/get-query ParameterForm)}
                   {:sample/SampleTypeCode (comp/get-query SampleTypeForm)}
                   {:sample/FieldResults (comp/get-query FieldResultForm)}
                   {:sample/FieldObsResults (comp/get-query FieldObsResultForm)}
                   {:sample/LabResults (comp/get-query LabResultForm)}]
                   ;[:riverdb.theta.options/ns '_]]


   :initial-state (fn [{:keys [sampType devType devID const param]}]
                    (let [{:parameter/keys [Constituent DeviceType DeviceID SampleType]} param
                          const (or const Constituent)
                          devType (or devType DeviceType)
                          devID (or devID DeviceID)
                          sampType (or sampType SampleType)]
                      (cond->
                        {:db/id                 (tempid/tempid)
                         :sample/uuid           (tempid/uuid)
                         :riverdb.entity/ns     :entity.ns/sample
                         :sample/Constituent    const
                         :sample/SampleTypeCode sampType}
                        devType
                        (assoc :sample/DeviceType devType)
                        devID
                        (assoc :sample/DeviceID devID)
                        param
                        (assoc :sample/Parameter param))))
   :form-fields   #{:db/id
                    :sample/uuid
                    :riverdb.entity/ns
                    :sample/SampleTime
                    :sample/FieldResults
                    :sample/FieldObsResults
                    :sample/LabResults
                    :sample/DeviceType
                    :sample/DeviceID
                    :sample/Constituent
                    :sample/SampleTypeCode
                    :sample/Parameter
                    :sample/Time}})

(def ui-sample-form (comp/factory SampleForm {:keyfn :db/id}))
