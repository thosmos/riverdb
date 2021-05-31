(ns
 riverdb.ui.lookups
 (:require
  [com.fulcrologic.fulcro.components :refer [defsc get-query factory]]
  [com.fulcrologic.fulcro.algorithms.tempid :refer [tempid]]
  [com.fulcrologic.fulcro.dom :refer [div]]
  [theta.log :as log :refer [debug]]))

(defsc
 agencylookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.agencylookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/agencylookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :agencylookup/Active
   :agencylookup/AgencyCode
   :agencylookup/AgencyDescr
   :agencylookup/City
   :agencylookup/Email
   :agencylookup/Fax
   :agencylookup/Logo
   :agencylookup/Mission
   :agencylookup/Name
   :agencylookup/NameShort
   :agencylookup/PrimaryContact
   :agencylookup/River
   :agencylookup/State
   :agencylookup/StreetAddress
   :agencylookup/TagLine
   :agencylookup/Telephone
   :agencylookup/WebAddress
   :agencylookup/Zip
   :agencylookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 agencylookup-sum
 [_ _]
 {:ident [:org.riverdb.db.agencylookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/agencylookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :agencylookup/uuid
   :agencylookup/Active
   :agencylookup/AgencyCode
   :agencylookup/Name
   :agencylookup/NameShort
   :agencylookup/WebAddress]})


(defsc
 analytelookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.analytelookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/analytelookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :analytelookup/Active
   :analytelookup/AnalyteCode
   :analytelookup/AnalyteDescr
   :analytelookup/AnalyteName
   :analytelookup/AnalyteShort
   :analytelookup/CASNumber
   :analytelookup/Group1
   :analytelookup/Group2
   :analytelookup/Group3
   :analytelookup/Group4
   :analytelookup/Group5
   :analytelookup/Group6
   :analytelookup/Utility1
   :analytelookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 analytelookup-sum
 [_ _]
 {:ident [:org.riverdb.db.analytelookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/analytelookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :analytelookup/AnalyteCode
   :analytelookup/AnalyteName
   :analytelookup/AnalyteShort]})


(defsc
 batchquallookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.batchquallookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/batchquallookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :batchquallookup/Active
   :batchquallookup/BatchQualCode
   :batchquallookup/BatchQualRowID
   :batchquallookup/BatchQualifier
   :batchquallookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 batchquallookup-sum
 [_ _]
 {:ident [:org.riverdb.db.batchquallookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/batchquallookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :batchquallookup/Active
   :batchquallookup/BatchQualCode
   :batchquallookup/BatchQualifier]})


(defsc
 batchvallookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.batchvallookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/batchvallookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :batchvallookup/Active
   :batchvallookup/BatchValCode
   :batchvallookup/BatchVerRowID
   :batchvallookup/BatchVerification
   :batchvallookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 batchvallookup-sum
 [_ _]
 {:ident [:org.riverdb.db.batchvallookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/batchvallookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :batchvallookup/Active
   :batchvallookup/BatchValCode
   :batchvallookup/BatchVerification]})


(defsc
 compliancelevellookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.compliancelevellookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/compliancelevellookup,
   :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :compliancelevellookup/Active
   :compliancelevellookup/ComplianceCode
   :compliancelevellookup/ComplianceDescr
   :compliancelevellookup/ComplianceLevelRowID
   :compliancelevellookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 compliancelevellookup-sum
 [_ _]
 {:ident [:org.riverdb.db.compliancelevellookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/compliancelevellookup,
   :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :compliancelevellookup/Active
   :compliancelevellookup/ComplianceCode
   :compliancelevellookup/ComplianceDescr]})


(defsc
 constituentlookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.constituentlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/constituentlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :constituentlookup/Active
   :constituentlookup/AnalyteCode
   :constituentlookup/ConstituentCode
   :constituentlookup/ConstituentRowID
   :constituentlookup/DerivedName
   :constituentlookup/DeviceType
   :constituentlookup/FractionCode
   :constituentlookup/HighValue
   :constituentlookup/LowValue
   :constituentlookup/MatrixCode
   :constituentlookup/MaxValue
   :constituentlookup/MethodCode
   :constituentlookup/MinValue
   :constituentlookup/Name
   :constituentlookup/UnitCode
   :constituentlookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 constituentlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.constituentlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/constituentlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :constituentlookup/Name
   :constituentlookup/Active
   :constituentlookup/AnalyteCode
   :constituentlookup/ConstituentCode
   :constituentlookup/FractionCode
   :constituentlookup/MatrixCode
   :constituentlookup/MethodCode
   :constituentlookup/UnitCode]})


(defsc
 corrections
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.corrections/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/corrections, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :corrections/AssociatedAgency
   :corrections/ChangedData
   :corrections/CorrectionsComments
   :corrections/CorrectionsRowID
   :corrections/Date
   :corrections/FieldName
   :corrections/Global
   :corrections/OriginalData
   :corrections/Person
   :corrections/TableName
   :corrections/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 corrections-sum
 [_ _]
 {:ident [:org.riverdb.db.corrections/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/corrections, :db/id (tempid)},
  :query [:db/id :riverdb.entity/ns :corrections/CorrectionsRowID]})


(defsc
 samplingdevicelookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.samplingdevicelookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/samplingdevicelookup,
   :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :samplingdevicelookup/Active
   :samplingdevicelookup/Constituent
   :samplingdevicelookup/DeviceMax
   :samplingdevicelookup/DeviceMin
   :samplingdevicelookup/QAmax
   :samplingdevicelookup/QAmin
   :samplingdevicelookup/SampleDevice
   :samplingdevicelookup/SampleDeviceDescr
   :samplingdevicelookup/SamplingDeviceCode
   :samplingdevicelookup/SamplingMatrix
   :samplingdevicelookup/SerialNumber
   :samplingdevicelookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 samplingdevicelookup-sum
 [_ _]
 {:ident [:org.riverdb.db.samplingdevicelookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/samplingdevicelookup,
   :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :samplingdevicelookup/Active
   :samplingdevicelookup/SampleDevice
   :samplingdevicelookup/DeviceMax
   :samplingdevicelookup/DeviceMin]})


(defsc
 digestextractlookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.digestextractlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/digestextractlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :digestextractlookup/Active
   :digestextractlookup/DigestExtractCode
   :digestextractlookup/DigestExtractDescr
   :digestextractlookup/DigestExtractInstrument
   :digestextractlookup/DigestExtractMethod
   :digestextractlookup/DigestExtractRowID
   :digestextractlookup/DigestExtractType
   :digestextractlookup/MethodOnFile
   :digestextractlookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 digestextractlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.digestextractlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/digestextractlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :digestextractlookup/Active
   :digestextractlookup/DigestExtractCode
   :digestextractlookup/DigestExtractInstrument
   :digestextractlookup/DigestExtractType
   :digestextractlookup/DigestExtractDescr
   :digestextractlookup/DigestExtractMethod]})


(defsc
 eventtypelookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.eventtypelookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/eventtypelookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :eventtypelookup/Active
   :eventtypelookup/EventDescr
   :eventtypelookup/EventType
   :eventtypelookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 eventtypelookup-sum
 [_ _]
 {:ident [:org.riverdb.db.eventtypelookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/eventtypelookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :eventtypelookup/Active
   :eventtypelookup/EventType
   :eventtypelookup/EventDescr]})


(defsc
 fieldobsresult
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.fieldobsresult/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/fieldobsresult, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :fieldobsresult/ConstituentRowID
   :fieldobsresult/IntResult
   :fieldobsresult/FieldObsResultComments
   :fieldobsresult/FieldObsResultRowID
   :fieldobsresult/SampleRowID
   :fieldobsresult/TextResult
   :fieldobsresult/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 fieldobsresult-sum
 [_ _]
 {:ident [:org.riverdb.db.fieldobsresult/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/fieldobsresult, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :fieldobsresult/TextResult
   :fieldobsresult/IntResult
   :fieldobsresult/ConstituentRowID
   :fieldobsresult/FieldObsResultComments
   :fieldobsresult/SampleRowID]})


(defsc
 fieldobsvarlookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.fieldobsvarlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/fieldobsvarlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :fieldobsvarlookup/Active
   :fieldobsvarlookup/AnalyteName
   :fieldobsvarlookup/FieldObsVarRowId
   :fieldobsvarlookup/ValueCode
   :fieldobsvarlookup/ValueCodeDescr
   :fieldobsvarlookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 fieldobsvarlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.fieldobsvarlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/fieldobsvarlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :fieldobsvarlookup/Active
   :fieldobsvarlookup/AnalyteName
   :fieldobsvarlookup/ValueCode
   :fieldobsvarlookup/ValueCodeDescr]})


(defsc
 fieldresequiplookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.fieldresequiplookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/fieldresequiplookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :fieldresequiplookup/Active
   :fieldresequiplookup/AnalyteName
   :fieldresequiplookup/FieldResEquipRowID
   :fieldresequiplookup/SampleDevice
   :fieldresequiplookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 fieldresequiplookup-sum
 [_ _]
 {:ident [:org.riverdb.db.fieldresequiplookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/fieldresequiplookup, :db/id (tempid)},
  :query
  [:db/id :riverdb.entity/ns :fieldresequiplookup/FieldResEquipRowID]})


(defsc
 fieldresult
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.fieldresult/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/fieldresult, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :fieldresult/CalibrationDate
   :fieldresult/FieldResultComments
   :fieldresult/ComplianceCode
   :fieldresult/ConstituentRowID
   :fieldresult/SamplingDeviceID
   :fieldresult/SamplingDeviceCode
   :fieldresult/QACode
   :fieldresult/QAFlag
   :fieldresult/FieldReplicate
   :fieldresult/ResQualCode
   :fieldresult/Result
   :fieldresult/ResultTime
   :fieldresult/FieldResultRowID
   :fieldresult/SampleRowID
   :fieldresult/SigFig
   :fieldresult/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 fieldresult-sum
 [_ _]
 {:ident [:org.riverdb.db.fieldresult/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/fieldresult, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :fieldresult/ResultTime
   :fieldresult/Result
   :fieldresult/FieldReplicate
   :fieldresult/SigFig
   :fieldresult/QACode
   :fieldresult/QAFlag]})


(defsc
 fractionlookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.fractionlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/fractionlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :fractionlookup/Active
   :fractionlookup/FractionCode
   :fractionlookup/FractionDescr
   :fractionlookup/FractionName
   :fractionlookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 fractionlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.fractionlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/fractionlookup, :db/id (tempid)},
  :query [:db/id :riverdb.entity/ns :fractionlookup/FractionCode]})


(defsc
 gpsdevicelookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.gpsdevicelookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/gpsdevicelookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :gpsdevicelookup/Active
   :gpsdevicelookup/GPSDescr
   :gpsdevicelookup/GPSDeviceCode
   :gpsdevicelookup/GPSDeviceID
   :gpsdevicelookup/GPSDeviceRowID
   :gpsdevicelookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 gpsdevicelookup-sum
 [_ _]
 {:ident [:org.riverdb.db.gpsdevicelookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/gpsdevicelookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :gpsdevicelookup/Active
   :gpsdevicelookup/GPSDeviceID
   :gpsdevicelookup/GPSDeviceCode]})


(defsc
 labbatch
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.labbatch/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/labbatch, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :labbatch/AgencyCode
   :labbatch/BatchQualCode
   :labbatch/BatchValCode
   :labbatch/LabBatch
   :labbatch/LabBatchComments
   :labbatch/LabBatchRowID
   :labbatch/SubmittingAgency
   :labbatch/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 labbatch-sum
 [_ _]
 {:ident [:org.riverdb.db.labbatch/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/labbatch, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :labbatch/LabBatch
   :labbatch/BatchQualCode
   :labbatch/AgencyCode
   :labbatch/SubmittingAgency
   :labbatch/LabBatchComments]})


(defsc
 labresult
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.labresult/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/labresult, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :labresult/AnalysisDate
   :labresult/Basis
   :labresult/ComplianceCode
   :labresult/ConstituentRowID
   :labresult/DigestExtractCode
   :labresult/DigestExtractDate
   :labresult/ExpectedValue
   :labresult/LabBatch
   :labresult/LabReplicate
   :labresult/LabResultComments
   :labresult/LabResultRowID
   :labresult/LabSampleID
   :labresult/MDL
   :labresult/PrepCode
   :labresult/PrepDate
   :labresult/QACode
   :labresult/QAFlag
   :labresult/RL
   :labresult/ResQualCode
   :labresult/Result
   :labresult/SampleID
   :labresult/SampleRowID
   :labresult/SigFig
   :labresult/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 labresult-sum
 [_ _]
 {:ident [:org.riverdb.db.labresult/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/labresult, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :labresult/AnalysisDate
   :labresult/Result
   :labresult/QAFlag
   :labresult/QACode]})


(defsc
 matrixlookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.matrixlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/matrixlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :matrixlookup/Active
   :matrixlookup/MatrixCode
   :matrixlookup/MatrixDescr
   :matrixlookup/MatrixName
   :matrixlookup/MatrixShort
   :matrixlookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 matrixlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.matrixlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/matrixlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :matrixlookup/Active
   :matrixlookup/MatrixCode
   :matrixlookup/MatrixName
   :matrixlookup/MatrixShort]})


(defsc
 methodlookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.methodlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/methodlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :methodlookup/Active
   :methodlookup/Instrument
   :methodlookup/MethodCode
   :methodlookup/MethodDescr
   :methodlookup/MethodName
   :methodlookup/MethodOnFile
   :methodlookup/Type1
   :methodlookup/Type2
   :methodlookup/Type3
   :methodlookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 methodlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.methodlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/methodlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :methodlookup/Active
   :methodlookup/MethodCode
   :methodlookup/MethodName]})


(defsc
 missingvaluelookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.missingvaluelookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/missingvaluelookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :missingvaluelookup/Active
   :missingvaluelookup/DataType
   :missingvaluelookup/MissingValueCode
   :missingvaluelookup/MissingValueComments
   :missingvaluelookup/MissingValueDescr
   :missingvaluelookup/MissingValueRowID
   :missingvaluelookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 missingvaluelookup-sum
 [_ _]
 {:ident [:org.riverdb.db.missingvaluelookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/missingvaluelookup, :db/id (tempid)},
  :query
  [:db/id :riverdb.entity/ns :missingvaluelookup/MissingValueRowID]})


(defsc
 paramline
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.paramline/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/paramline, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :paramline/css
   :paramline/color
   :paramline/label
   :paramline/value
   :paramline/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 paramline-sum
 [_ _]
 {:ident [:org.riverdb.db.paramline/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/paramline, :db/id (tempid)},
  :query [:db/id :riverdb.entity/ns :paramline/label]})


(defsc
 parameter
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.parameter/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/parameter, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :parameter/Active
   :parameter/Color
   :parameter/Constituent
   :parameter/DeviceType
   :parameter/High
   :parameter/Lines
   :parameter/Low
   :parameter/Name
   :parameter/NameShort
   :parameter/PrecisionCode
   :parameter/Replicates
   :parameter/ReplicatesEntry
   :parameter/ReplicatesElide
   :parameter/SampleType
   :parameter/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 parameter-sum
 [_ _]
 {:ident [:org.riverdb.db.parameter/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/parameter, :db/id (tempid)},
  :query [:db/id :riverdb.entity/ns :parameter/Active :parameter/Name]})


(defsc
 parentprojectlookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.parentprojectlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/parentprojectlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :parentprojectlookup/Address1
   :parentprojectlookup/Address2
   :parentprojectlookup/CityStat
   :parentprojectlookup/ParentProjectComments
   :parentprojectlookup/DQOlevel
   :parentprojectlookup/Duration
   :parentprojectlookup/ProjEmail
   :parentprojectlookup/ExternalFileName
   :parentprojectlookup/ParentProjectID
   :parentprojectlookup/ProjLead
   :parentprojectlookup/ParentProjectName
   :parentprojectlookup/Notes
   :parentprojectlookup/OrgNameorID
   :parentprojectlookup/ProjPhon
   :parentprojectlookup/Purpose
   :parentprojectlookup/ParentProjectRowID
   :parentprojectlookup/StartDate
   :parentprojectlookup/ProjType
   :parentprojectlookup/URL
   :parentprojectlookup/ZIP
   :parentprojectlookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 parentprojectlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.parentprojectlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/parentprojectlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :parentprojectlookup/ParentProjectID
   :parentprojectlookup/ParentProjectName
   :parentprojectlookup/OrgNameorID
   :parentprojectlookup/StartDate]})


(defsc
 person
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.person/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/person, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :person/Agency
   :person/FName
   :person/IsStaff
   :person/LName
   :person/Name
   :person/PersonID
   :person/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 person-sum
 [_ _]
 {:ident [:org.riverdb.db.person/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/person, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :person/Name
   :person/IsStaff
   :person/Agency]})


(defsc
 preplookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.preplookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/preplookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :preplookup/Active
   :preplookup/Filtered
   :preplookup/PrepCode
   :preplookup/PrepRowID
   :preplookup/Preparation
   :preplookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 preplookup-sum
 [_ _]
 {:ident [:org.riverdb.db.preplookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/preplookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :preplookup/Active
   :preplookup/PrepCode
   :preplookup/Preparation]})


(defsc
 projectslookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.projectslookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/projectslookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :projectslookup/Active
   :projectslookup/AgencyCode
   :projectslookup/AgencyRef
   :projectslookup/FieldVerCode
   :projectslookup/FieldVerComm
   :projectslookup/Name
   :projectslookup/Parameters
   :projectslookup/ParentProjectID
   :projectslookup/ProjectID
   :projectslookup/ProjectsComments
   :projectslookup/Public
   :projectslookup/QAPPVersion
   :projectslookup/qappURL
   :projectslookup/Stations
   :projectslookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 projectslookup-sum
 [_ _]
 {:ident [:org.riverdb.db.projectslookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/projectslookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :projectslookup/Active
   :projectslookup/ProjectID
   :projectslookup/Name
   :projectslookup/AgencyCode
   :projectslookup/AgencyRef]})


(defsc
 qalookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.qalookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/qalookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :qalookup/Active
   :qalookup/QACode
   :qalookup/QACodeDescr
   :qalookup/QARowID
   :qalookup/Type1
   :qalookup/Type2
   :qalookup/Type3
   :qalookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 qalookup-sum
 [_ _]
 {:ident [:org.riverdb.db.qalookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/qalookup, :db/id (tempid)},
  :query [:db/id :riverdb.entity/ns :qalookup/QARowID]})


(defsc
 resquallookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.resquallookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/resquallookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :resquallookup/Active
   :resquallookup/ResQualCode
   :resquallookup/ResQualifier
   :resquallookup/Type1
   :resquallookup/Type2
   :resquallookup/Type3
   :resquallookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 resquallookup-sum
 [_ _]
 {:ident [:org.riverdb.db.resquallookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/resquallookup, :db/id (tempid)},
  :query [:db/id :riverdb.entity/ns :resquallookup/ResQualCode]})


(defsc
 sample
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.sample/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/sample, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :sample/DepthSampleCollection
   :sample/EventType
   :sample/FieldObsResults
   :sample/FieldResults
   :sample/LabResults
   :sample/QCCheck
   :sample/SampleComments
   :sample/SampleComplete
   :sample/SampleDate
   :sample/SampleReplicate
   :sample/SampleRowID
   :sample/SampleTime
   :sample/SampleTypeCode
   :sample/SiteVisitID
   :sample/Unit
   :sample/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 sample-sum
 [_ _]
 {:ident [:org.riverdb.db.sample/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/sample, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :sample/SampleDate
   :sample/SampleReplicate
   :sample/EventType
   :sample/QCCheck
   :sample/SampleComplete
   :sample/SampleTime
   :sample/SampleTypeCode]})


(defsc
 sampledetail
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.sampledetail/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/sampledetail, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :sampledetail/ActualLatitude
   :sampledetail/ActualLongitude
   :sampledetail/Datum
   :sampledetail/DistanceFromBank
   :sampledetail/GPSAccuracy
   :sampledetail/GPSDeviceCode
   :sampledetail/Hydromod
   :sampledetail/HydromodLoc
   :sampledetail/OccupationMethod
   :sampledetail/PositionWaterColumn
   :sampledetail/SampleDetailComments
   :sampledetail/SampleDetailRowID
   :sampledetail/SampleLocation
   :sampledetail/SampleRowID
   :sampledetail/SamplingCrew
   :sampledetail/SamplingDeviceCode
   :sampledetail/StartingBank
   :sampledetail/StationWaterDepth
   :sampledetail/StreamWidth
   :sampledetail/UnitAccuracy
   :sampledetail/UnitDistanceFromBank
   :sampledetail/UnitStationWaterDepth
   :sampledetail/UnitStreamWidth
   :sampledetail/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 sampledetail-sum
 [_ _]
 {:ident [:org.riverdb.db.sampledetail/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/sampledetail, :db/id (tempid)},
  :query [:db/id :riverdb.entity/ns :sampledetail/SampleDetailRowID]})


(defsc
 sampletypelookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.sampletypelookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/sampletypelookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :sampletypelookup/Active
   :sampletypelookup/CollectionType
   :sampletypelookup/EventType1
   :sampletypelookup/EventType2
   :sampletypelookup/EventType3
   :sampletypelookup/EventType4
   :sampletypelookup/SampleTypeCode
   :sampletypelookup/SampleTypeDescr
   :sampletypelookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 sampletypelookup-sum
 [_ _]
 {:ident [:org.riverdb.db.sampletypelookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/sampletypelookup, :db/id (tempid)},
  :query
  [:db/id
   :db/ident
   :riverdb.entity/ns
   :sampletypelookup/Active
   :sampletypelookup/Name
   :sampletypelookup/SampleTypeCode]})



(defsc
 samplingdevice
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.samplingdevice/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/samplingdevice, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :samplingdevice/CommonID
   :samplingdevice/DeviceType
   :samplingdevice/SamplingDeviceID
   :samplingdevice/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 samplingdevice-sum
 [_ _]
 {:ident [:org.riverdb.db.samplingdevice/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/samplingdevice, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :samplingdevice/CommonID
   :samplingdevice/DeviceType]})


(defsc
 seasonlookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.seasonlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/seasonlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :seasonlookup/Active
   :seasonlookup/Season
   :seasonlookup/SeasonCode
   :seasonlookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 seasonlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.seasonlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/seasonlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :seasonlookup/Active
   :seasonlookup/SeasonCode
   :seasonlookup/Season]})


(defsc
 seasondetaillookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.seasondetaillookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/seasondetaillookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :seasondetaillookup/Active
   :seasondetaillookup/ProjectID
   :seasondetaillookup/SeasonCode
   :seasondetaillookup/SeasonDescr
   :seasondetaillookup/SeasonRowID
   :seasondetaillookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 seasondetaillookup-sum
 [_ _]
 {:ident [:org.riverdb.db.seasondetaillookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/seasondetaillookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :seasondetaillookup/Active
   :seasondetaillookup/SeasonCode
   :seasondetaillookup/SeasonDescr]})


(defsc
 sitevisit
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.sitevisit/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/sitevisit, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :sitevisit/AgencyCode
   :sitevisit/BacteriaCollected
   :sitevisit/BacteriaTime
   :sitevisit/CheckPerson
   :sitevisit/CheckPersonRef
   :sitevisit/CreationTimestamp
   :sitevisit/DataEntryDate
   :sitevisit/DataEntryNotes
   :sitevisit/DataEntryPerson
   :sitevisit/DataEntryPersonRef
   :sitevisit/SiteVisitDate
   :sitevisit/Datum
   :sitevisit/DepthMeasured
   :sitevisit/StationFailCode
   :sitevisit/GPSDeviceCode
   :sitevisit/HydroMod
   :sitevisit/HydroModLoc
   :sitevisit/StationID
   :sitevisit/Lat
   :sitevisit/Lon
   :sitevisit/MetalCollected
   :sitevisit/MetalTime
   :sitevisit/Notes
   :sitevisit/PointID
   :sitevisit/ProjectID
   :sitevisit/QACheck
   :sitevisit/QADate
   :sitevisit/QAPerson
   :sitevisit/QAPersonRef
   :sitevisit/SiteVisitID
   :sitevisit/Samples
   :sitevisit/Visitors
   :sitevisit/SeasonCode
   :sitevisit/StreamWidth
   :sitevisit/Time
   :sitevisit/TssCollected
   :sitevisit/TssTime
   :sitevisit/TurbidityCollected
   :sitevisit/TurbidityTime
   :sitevisit/UnitStreamWidth
   :sitevisit/UnitWaterDepth
   :sitevisit/VisitType
   :sitevisit/WaterDepth
   :sitevisit/WidthMeasured
   :sitevisit/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 sitevisit-sum
 [_ _]
 {:ident [:org.riverdb.db.sitevisit/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/sitevisit, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :sitevisit/SiteVisitDate
   :sitevisit/StationID
   :sitevisit/AgencyCode
   :sitevisit/ProjectID
   :sitevisit/QACheck
   :sitevisit/StationFailCode
   :sitevisit/VisitType]})


(defsc
 sitevisitgroup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.sitevisitgroup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/sitevisitgroup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :sitevisitgroup/GroupID
   :sitevisitgroup/Name
   :sitevisitgroup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 sitevisitgroup-sum
 [_ _]
 {:ident [:org.riverdb.db.sitevisitgroup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/sitevisitgroup, :db/id (tempid)},
  :query [:db/id :riverdb.entity/ns :sitevisitgroup/GroupID]})


(defsc
 statmethlookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.statmethlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/statmethlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :statmethlookup/Active
   :statmethlookup/StatMethCode
   :statmethlookup/StatMethDescr
   :statmethlookup/StatMethodRowID
   :statmethlookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 statmethlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.statmethlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/statmethlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :statmethlookup/Active
   :statmethlookup/StatMethCode
   :statmethlookup/StatMethDescr]})


(defsc
 stationlookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.stationlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/stationlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :stationlookup/Active
   :stationlookup/AddDate
   :stationlookup/Agency
   :stationlookup/AlternativeStationCode1
   :stationlookup/AlternativeStationCode2
   :stationlookup/AlternativeStationCode3
   :stationlookup/CalWNum221
   :stationlookup/County
   :stationlookup/DOcorrection
   :stationlookup/Datum
   :stationlookup/Description
   :stationlookup/Elevation
   :stationlookup/ForkTribGroup
   :stationlookup/GIS_latlon
   :stationlookup/GageStationID
   :stationlookup/GeoID
   :stationlookup/HBASA2
   :stationlookup/HydrologicUnit
   :stationlookup/LocalWaterbody
   :stationlookup/LocalWatershed
   :stationlookup/NHDRiverMile
   :stationlookup/NHDWaterbody
   :stationlookup/Name
   :stationlookup/NationalHydrographyDataReach
   :stationlookup/RiverFork
   :stationlookup/SWRCBNum221
   :stationlookup/SWRCBRegion
   :stationlookup/StationCode
   :stationlookup/StationCodeVer
   :stationlookup/StationCodeVerComm
   :stationlookup/StationComments
   :stationlookup/StationID
   :stationlookup/StationName
   :stationlookup/StationsRowID
   :stationlookup/StreamSubsystem
   :stationlookup/TargetLat
   :stationlookup/TargetLong
   :stationlookup/Unit
   :stationlookup/WBType
   :stationlookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 stationlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.stationlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/stationlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :stationlookup/Active
   :stationlookup/StationID
   :stationlookup/StationName
   :stationlookup/Agency
   :stationlookup/TargetLong
   :stationlookup/TargetLat
   :stationlookup/RiverFork]})


(defsc
 stationfaillookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.stationfaillookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/stationfaillookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :stationfaillookup/Active
   :stationfaillookup/FailureReason
   :stationfaillookup/StationFailCode
   :stationfaillookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 stationfaillookup-sum
 [_ _]
 {:ident [:org.riverdb.db.stationfaillookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/stationfaillookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :stationfaillookup/Active
   :stationfaillookup/StationFailCode
   :stationfaillookup/FailureReason]})


(defsc
 unitlookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.unitlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/unitlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :unitlookup/Active
   :unitlookup/Unit
   :unitlookup/UnitCode
   :unitlookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 unitlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.unitlookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/unitlookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :unitlookup/Active
   :unitlookup/UnitCode
   :unitlookup/Unit]})


(defsc
 unitslookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.unitslookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/unitslookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :unitslookup/CodeListName
   :unitslookup/FromDate
   :unitslookup/SignificantDecimal
   :unitslookup/ToDate
   :unitslookup/Units
   :unitslookup/UnitsLookupRowID
   :unitslookup/VariableName
   :unitslookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 unitslookup-sum
 [_ _]
 {:ident [:org.riverdb.db.unitslookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/unitslookup, :db/id (tempid)},
  :query [:db/id :riverdb.entity/ns :unitslookup/UnitsLookupRowID]})


(defsc
 variablecodeslookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.variablecodeslookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/variablecodeslookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :variablecodeslookup/Active
   :variablecodeslookup/CodeDescription
   :variablecodeslookup/CodeListName
   :variablecodeslookup/ValueCode
   :variablecodeslookup/VariableCodesLookUpRowID
   :variablecodeslookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 variablecodeslookup-sum
 [_ _]
 {:ident [:org.riverdb.db.variablecodeslookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/variablecodeslookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :variablecodeslookup/Active
   :variablecodeslookup/CodeListName
   :variablecodeslookup/ValueCode
   :variablecodeslookup/CodeDescription]})


(defsc
 variableslookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.variableslookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/variableslookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :variableslookup/Active
   :variableslookup/Description
   :variableslookup/VariableName
   :variableslookup/VariableType
   :variableslookup/VariablesLookUpRowID
   :variableslookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 variableslookup-sum
 [_ _]
 {:ident [:org.riverdb.db.variableslookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/variableslookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :variableslookup/Active
   :variableslookup/VariableName
   :variableslookup/VariableType
   :variableslookup/Description]})


(defsc
 sitevisittype
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.sitevisittype/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/sitevisittype, :db/id (tempid)},
  :query
  [:db/id :riverdb.entity/ns :sitevisittype/name :sitevisittype/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 sitevisittype-sum
 [_ _]
 {:ident [:org.riverdb.db.sitevisittype/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/sitevisittype, :db/id (tempid)},
  :query [:db/id :riverdb.entity/ns :sitevisittype/name]})


(defsc
 wbtypelookup
 [_ {:keys [riverdb.entity/ns]}]
 {:ident [:org.riverdb.db.wbtypelookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/wbtypelookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :wbtypelookup/WBType
   :wbtypelookup/WBTypeDescr
   :wbtypelookup/WBTypeRowID
   :wbtypelookup/uuid]}
 (debug (str "RENDER " ns)))

(defsc
 wbtypelookup-sum
 [_ _]
 {:ident [:org.riverdb.db.wbtypelookup/gid :db/id],
  :initial-state
  {:riverdb.entity/ns :entity.ns/wbtypelookup, :db/id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :wbtypelookup/WBType
   :wbtypelookup/WBTypeDescr]})


