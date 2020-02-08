(ns
 riverdb.ui.lookups
 (:require
  [com.fulcrologic.fulcro.components :refer [defsc transact! factory]]
  [com.fulcrologic.fulcro.algorithms.tempid :refer [tempid]]
  [com.fulcrologic.fulcro.dom :as dom :refer [div]]
  [com.fulcrologic.fulcro.algorithms.form-state :as fs]
  [taoensso.timbre :as log]))

(defsc
 agencylookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.agencylookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "agencylookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
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
   :agencylookup/Zip]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 agencylookup-sum
 [_ _]
 {:ident [:org.riverdb.db.agencylookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :agencylookup/Active
   :agencylookup/AgencyCode
   :agencylookup/Name]})


(defsc
 analytelookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.analytelookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "analytelookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
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
   :analytelookup/Utility1]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 analytelookup-sum
 [_ _]
 {:ident [:org.riverdb.db.analytelookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :analytelookup/AnalyteCode]})


(defsc
 batchquallookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.batchquallookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "batchquallookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :batchquallookup/Active
   :batchquallookup/BatchQualCode
   :batchquallookup/BatchQualRowID
   :batchquallookup/BatchQualifier]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 batchquallookup-sum
 [_ _]
 {:ident [:org.riverdb.db.batchquallookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :batchquallookup/BatchQualRowID]})


(defsc
 batchvallookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.batchvallookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "batchvallookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :batchvallookup/Active
   :batchvallookup/BatchValCode
   :batchvallookup/BatchVerRowID
   :batchvallookup/BatchVerification]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 batchvallookup-sum
 [_ _]
 {:ident [:org.riverdb.db.batchvallookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :batchvallookup/BatchVerRowID]})


(defsc
 compliancelevellookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.compliancelevellookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "compliancelevellookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :compliancelevellookup/Active
   :compliancelevellookup/ComplianceCode
   :compliancelevellookup/ComplianceDescr
   :compliancelevellookup/ComplianceLevelRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 compliancelevellookup-sum
 [_ _]
 {:ident [:org.riverdb.db.compliancelevellookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :compliancelevellookup/ComplianceLevelRowID]})


(defsc
 constituentlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.constituentlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "constituentlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :constituentlookup/Active
   :constituentlookup/AnalyteCode
   :constituentlookup/ConstituentCode
   :constituentlookup/ConstituentRowID
   :constituentlookup/DeviceType
   :constituentlookup/FractionCode
   :constituentlookup/HighValue
   :constituentlookup/LowValue
   :constituentlookup/MatrixCode
   :constituentlookup/MaxValue
   :constituentlookup/MethodCode
   :constituentlookup/MinValue
   :constituentlookup/Name
   :constituentlookup/UnitCode]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 constituentlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.constituentlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id :riverdb.entity/ns :constituentlookup/Name fs/form-config-join]})


(defsc
 corrections
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.corrections/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "corrections"},
  :query
  [:ui/msg
   :ui/name
   :db/id
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
   :corrections/TableName]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 corrections-sum
 [_ _]
 {:ident [:org.riverdb.db.corrections/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :corrections/CorrectionsRowID]})


(defsc
 digestextractlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.digestextractlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "digestextractlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :digestextractlookup/Active
   :digestextractlookup/DigestExtractCode
   :digestextractlookup/DigestExtractDescr
   :digestextractlookup/DigestExtractInstrument
   :digestextractlookup/DigestExtractMethod
   :digestextractlookup/DigestExtractRowID
   :digestextractlookup/DigestExtractType
   :digestextractlookup/MethodOnFile]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 digestextractlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.digestextractlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id :riverdb.entity/ns :digestextractlookup/DigestExtractRowID]})


(defsc
 eventtypelookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.eventtypelookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "eventtypelookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :eventtypelookup/Active
   :eventtypelookup/EventDescr
   :eventtypelookup/EventType]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 eventtypelookup-sum
 [_ _]
 {:ident [:org.riverdb.db.eventtypelookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :eventtypelookup/EventType]})


(defsc
 fieldobsresult
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.fieldobsresult/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "fieldobsresult"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :fieldobsresult/ConstituentRowID
   :fieldobsresult/FieldObsResultComments
   :fieldobsresult/FieldObsResultRowID
   :fieldobsresult/IntResult
   :fieldobsresult/SampleRowID
   :fieldobsresult/TextResult]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 fieldobsresult-sum
 [_ _]
 {:ident [:org.riverdb.db.fieldobsresult/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id :riverdb.entity/ns :fieldobsresult/FieldObsResultRowID]})


(defsc
 fieldobsvarlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.fieldobsvarlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "fieldobsvarlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :fieldobsvarlookup/Active
   :fieldobsvarlookup/AnalyteName
   :fieldobsvarlookup/FieldObsVarRowId
   :fieldobsvarlookup/ValueCode
   :fieldobsvarlookup/ValueCodeDescr]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 fieldobsvarlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.fieldobsvarlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id :riverdb.entity/ns :fieldobsvarlookup/FieldObsVarRowId]})


(defsc
 fieldresequiplookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.fieldresequiplookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "fieldresequiplookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :fieldresequiplookup/Active
   :fieldresequiplookup/AnalyteName
   :fieldresequiplookup/FieldResEquipRowID
   :fieldresequiplookup/SampleDevice]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 fieldresequiplookup-sum
 [_ _]
 {:ident [:org.riverdb.db.fieldresequiplookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id :riverdb.entity/ns :fieldresequiplookup/FieldResEquipRowID]})


(defsc
 fieldresult
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.fieldresult/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "fieldresult"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :fieldresult/CalibrationDate
   :fieldresult/ComplianceCode
   :fieldresult/ConstituentRowID
   :fieldresult/FieldReplicate
   :fieldresult/FieldResultComments
   :fieldresult/FieldResultRowID
   :fieldresult/QACode
   :fieldresult/QAFlag
   :fieldresult/ResQualCode
   :fieldresult/Result
   :fieldresult/ResultTime
   :fieldresult/SampleRowID
   :fieldresult/SamplingDeviceCode
   :fieldresult/SamplingDeviceID
   :fieldresult/SigFig]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 fieldresult-sum
 [_ _]
 {:ident [:org.riverdb.db.fieldresult/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :fieldresult/FieldResultRowID]})


(defsc
 fractionlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.fractionlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "fractionlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :fractionlookup/Active
   :fractionlookup/FractionCode
   :fractionlookup/FractionDescr
   :fractionlookup/FractionName]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 fractionlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.fractionlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :fractionlookup/FractionCode]})


(defsc
 gpsdevicelookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.gpsdevicelookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "gpsdevicelookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :gpsdevicelookup/Active
   :gpsdevicelookup/GPSDescr
   :gpsdevicelookup/GPSDeviceCode
   :gpsdevicelookup/GPSDeviceID
   :gpsdevicelookup/GPSDeviceRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 gpsdevicelookup-sum
 [_ _]
 {:ident [:org.riverdb.db.gpsdevicelookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :gpsdevicelookup/GPSDeviceRowID]})


(defsc
 labbatch
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.labbatch/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "labbatch"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :labbatch/AgencyCode
   :labbatch/BatchQualCode
   :labbatch/BatchValCode
   :labbatch/LabBatch
   :labbatch/LabBatchComments
   :labbatch/LabBatchRowID
   :labbatch/SubmittingAgency]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 labbatch-sum
 [_ _]
 {:ident [:org.riverdb.db.labbatch/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :labbatch/LabBatchRowID]})


(defsc
 labresult
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.labresult/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "labresult"},
  :query
  [:ui/msg
   :ui/name
   :db/id
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
   :labresult/SigFig]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 labresult-sum
 [_ _]
 {:ident [:org.riverdb.db.labresult/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :labresult/LabResultRowID]})


(defsc
 matrixlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.matrixlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "matrixlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :matrixlookup/Active
   :matrixlookup/MatrixCode
   :matrixlookup/MatrixDescr
   :matrixlookup/MatrixName
   :matrixlookup/MatrixShort]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 matrixlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.matrixlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :matrixlookup/MatrixCode]})


(defsc
 methodlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.methodlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "methodlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :methodlookup/Active
   :methodlookup/Instrument
   :methodlookup/MethodCode
   :methodlookup/MethodDescr
   :methodlookup/MethodName
   :methodlookup/MethodOnFile
   :methodlookup/Type1
   :methodlookup/Type2
   :methodlookup/Type3]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 methodlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.methodlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :methodlookup/MethodCode]})


(defsc
 missingvaluelookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.missingvaluelookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "missingvaluelookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :missingvaluelookup/Active
   :missingvaluelookup/DataType
   :missingvaluelookup/MissingValueCode
   :missingvaluelookup/MissingValueComments
   :missingvaluelookup/MissingValueDescr
   :missingvaluelookup/MissingValueRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 missingvaluelookup-sum
 [_ _]
 {:ident [:org.riverdb.db.missingvaluelookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id :riverdb.entity/ns :missingvaluelookup/MissingValueRowID]})


(defsc
 parentprojectlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.parentprojectlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "parentprojectlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :parentprojectlookup/Address1
   :parentprojectlookup/Address2
   :parentprojectlookup/CityStat
   :parentprojectlookup/DQOlevel
   :parentprojectlookup/Duration
   :parentprojectlookup/ExternalFileName
   :parentprojectlookup/Notes
   :parentprojectlookup/OrgNameorID
   :parentprojectlookup/ParentProjectComments
   :parentprojectlookup/ParentProjectID
   :parentprojectlookup/ParentProjectName
   :parentprojectlookup/ParentProjectRowID
   :parentprojectlookup/ProjEmail
   :parentprojectlookup/ProjLead
   :parentprojectlookup/ProjPhon
   :parentprojectlookup/ProjType
   :parentprojectlookup/Purpose
   :parentprojectlookup/StartDate
   :parentprojectlookup/URL
   :parentprojectlookup/ZIP]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 parentprojectlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.parentprojectlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id :riverdb.entity/ns :parentprojectlookup/ParentProjectRowID]})


(defsc
 person
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.person/gid :db/id],
  :initial-state {:ui/msg "hello", :db/id (tempid), :ui/name "person"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :person/Agency
   :person/Name
   :person/PersonID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 person-sum
 [_ _]
 {:ident [:org.riverdb.db.person/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :person/PersonID]})


(defsc
 preplookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.preplookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "preplookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :preplookup/Active
   :preplookup/Filtered
   :preplookup/PrepCode
   :preplookup/PrepRowID
   :preplookup/Preparation]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 preplookup-sum
 [_ _]
 {:ident [:org.riverdb.db.preplookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :preplookup/PrepRowID]})


(defsc
 projectslookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.projectslookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "projectslookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :projectslookup/Active
   :projectslookup/AgencyCode
   :projectslookup/AgencyRef
   :projectslookup/FieldVerCode
   :projectslookup/FieldVerComm
   :projectslookup/Name
   :projectslookup/ParentProjectID
   :projectslookup/ProjectID
   :projectslookup/ProjectsComments
   :projectslookup/QAPPVersion]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 projectslookup-sum
 [_ _]
 {:ident [:org.riverdb.db.projectslookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
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
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.qalookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "qalookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :qalookup/Active
   :qalookup/QACode
   :qalookup/QACodeDescr
   :qalookup/QARowID
   :qalookup/Type1
   :qalookup/Type2
   :qalookup/Type3]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 qalookup-sum
 [_ _]
 {:ident [:org.riverdb.db.qalookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :qalookup/QARowID]})


(defsc
 resquallookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.resquallookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "resquallookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :resquallookup/Active
   :resquallookup/ResQualCode
   :resquallookup/ResQualifier
   :resquallookup/Type1
   :resquallookup/Type2
   :resquallookup/Type3]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 resquallookup-sum
 [_ _]
 {:ident [:org.riverdb.db.resquallookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :resquallookup/ResQualCode]})


(defsc
 sample
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.sample/gid :db/id],
  :initial-state {:ui/msg "hello", :db/id (tempid), :ui/name "sample"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :sample/DepthSampleCollection
   :sample/EventType
   :sample/FieldResults
   :sample/FieldObsResults
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
   :sample/Unit]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 sample-sum
 [_ _]
 {:ident [:org.riverdb.db.sample/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :sample/SampleRowID]})


(defsc
 sampledetail
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.sampledetail/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "sampledetail"},
  :query
  [:ui/msg
   :ui/name
   :db/id
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
   :sampledetail/UnitStreamWidth]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 sampledetail-sum
 [_ _]
 {:ident [:org.riverdb.db.sampledetail/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :sampledetail/SampleDetailRowID]})


(defsc
 sampletypelookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.sampletypelookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "sampletypelookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :sampletypelookup/Active
   :sampletypelookup/CollectionType
   :sampletypelookup/EventType1
   :sampletypelookup/EventType2
   :sampletypelookup/EventType3
   :sampletypelookup/EventType4
   :sampletypelookup/SampleTypeCode
   :sampletypelookup/SampleTypeDescr]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 sampletypelookup-sum
 [_ _]
 {:ident [:org.riverdb.db.sampletypelookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :sampletypelookup/SampleTypeCode]})


(defsc
 samplingdevice
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.samplingdevice/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "samplingdevice"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :samplingdevice/CommonID
   :samplingdevice/DeviceType
   :samplingdevice/SamplingDeviceID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 samplingdevice-sum
 [_ _]
 {:ident [:org.riverdb.db.samplingdevice/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :samplingdevice/SamplingDeviceID]})


(defsc
 samplingdevicelookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.samplingdevicelookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "samplingdevicelookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
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
   :samplingdevicelookup/SerialNumber]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 samplingdevicelookup-sum
 [_ _]
 {:ident [:org.riverdb.db.samplingdevicelookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id :riverdb.entity/ns :samplingdevicelookup/SamplingDeviceCode fs/form-config-join]})


(defsc
 seasondetaillookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.seasondetaillookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "seasondetaillookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :seasondetaillookup/Active
   :seasondetaillookup/ProjectID
   :seasondetaillookup/SeasonCode
   :seasondetaillookup/SeasonDescr
   :seasondetaillookup/SeasonRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 seasondetaillookup-sum
 [_ _]
 {:ident [:org.riverdb.db.seasondetaillookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :seasondetaillookup/SeasonRowID
   :seasondetaillookup/SeasonDescr]})


(defsc
 seasonlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.seasonlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "seasonlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :seasonlookup/Active
   :seasonlookup/Season
   :seasonlookup/SeasonCode]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 seasonlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.seasonlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :seasonlookup/Active
   :seasonlookup/SeasonCode
   :seasonlookup/Season]})


(defsc
 sitevisit
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.sitevisit/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "sitevisit"},
  :query
  [:ui/msg
   :ui/name
   :db/id
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
   :sitevisit/Datum
   :sitevisit/DepthMeasured
   :sitevisit/GPSDeviceCode
   :sitevisit/HydroMod
   :sitevisit/HydroModLoc
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
   :sitevisit/Samples
   :sitevisit/SeasonCode
   :sitevisit/SiteVisitDate
   :sitevisit/SiteVisitID
   :sitevisit/StationFailCode
   :sitevisit/StationID
   :sitevisit/StreamWidth
   :sitevisit/Time
   :sitevisit/TssCollected
   :sitevisit/TssTime
   :sitevisit/TurbidityCollected
   :sitevisit/TurbidityTime
   :sitevisit/UnitStreamWidth
   :sitevisit/UnitWaterDepth
   :sitevisit/Visitors
   :sitevisit/VisitType
   :sitevisit/WaterDepth
   :sitevisit/WidthMeasured
   :sitevisit/uuid]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 sitevisit-sum
 [_ _]
 {:ident [:org.riverdb.db.sitevisit/gid :db/id],
  :initial-state #:db{:id (tempid)},
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
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.sitevisitgroup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "sitevisitgroup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :sitevisitgroup/GroupID
   :sitevisitgroup/Name]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 sitevisitgroup-sum
 [_ _]
 {:ident [:org.riverdb.db.sitevisitgroup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :sitevisitgroup/GroupID]})


(defsc
 sitevisittype
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.sitevisittype/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "sitevisittype"},
  :query
  [:ui/msg :ui/name :db/id :riverdb.entity/ns :sitevisittype/name]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 sitevisittype-sum
 [_ _]
 {:ident [:org.riverdb.db.sitevisittype/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :sitevisittype/name]})


(defsc
 stationfaillookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.stationfaillookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "stationfaillookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :stationfaillookup/Active
   :stationfaillookup/FailureReason
   :stationfaillookup/StationFailCode]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 stationfaillookup-sum
 [_ _]
 {:ident [:org.riverdb.db.stationfaillookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :stationfaillookup/Active
   :stationfaillookup/StationFailCode
   :stationfaillookup/FailureReason]})


(defsc
 stationlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.stationlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "stationlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
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
   :stationlookup/NationalHydrographyDataReach
   :stationlookup/Project
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
   :stationlookup/WBType]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 stationlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.stationlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :stationlookup/Active
   :stationlookup/StationID
   :stationlookup/StationName
   :stationlookup/Agency
   :stationlookup/Project
   :stationlookup/TargetLong
   :stationlookup/TargetLat
   :stationlookup/RiverFork]})


(defsc
 statmethlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.statmethlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "statmethlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :statmethlookup/Active
   :statmethlookup/StatMethCode
   :statmethlookup/StatMethDescr
   :statmethlookup/StatMethodRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 statmethlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.statmethlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :statmethlookup/StatMethodRowID]})


(defsc
 timepointlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.timepointlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "timepointlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :timepointlookup/Active
   :timepointlookup/TimePoint
   :timepointlookup/TimePointDescr
   :timepointlookup/TimePointRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 timepointlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.timepointlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :timepointlookup/TimePointRowID]})


(defsc
 toxbatch
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.toxbatch/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "toxbatch"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :toxbatch/AgencyCode
   :toxbatch/BatchQualCode
   :toxbatch/BatchValCode
   :toxbatch/OrganismAgeAtTestStart
   :toxbatch/OrganismSupplier
   :toxbatch/RefToxBatch
   :toxbatch/SubmittingAgency
   :toxbatch/ToxBatch
   :toxbatch/ToxBatchComments
   :toxbatch/ToxBatchRowID
   :toxbatch/ToxBatchStartDate
   :toxbatch/UnitsAgeAtStart]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 toxbatch-sum
 [_ _]
 {:ident [:org.riverdb.db.toxbatch/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :toxbatch/ToxBatchRowID]})


(defsc
 toxconclookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.toxconclookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "toxconclookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :toxconclookup/Active
   :toxconclookup/ToxConc
   :toxconclookup/ToxConcDescr
   :toxconclookup/ToxConcRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 toxconclookup-sum
 [_ _]
 {:ident [:org.riverdb.db.toxconclookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :toxconclookup/ToxConcRowID]})


(defsc
 toxconstituentlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.toxconstituentlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "toxconstituentlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :toxconstituentlookup/Active
   :toxconstituentlookup/MatrixCode
   :toxconstituentlookup/MethodCode
   :toxconstituentlookup/ToxConstituentCode
   :toxconstituentlookup/ToxConstituentRowID
   :toxconstituentlookup/ToxSpeciesCode
   :toxconstituentlookup/ToxTestDur
   :toxconstituentlookup/UnitCode]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 toxconstituentlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.toxconstituentlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :toxconstituentlookup/ToxConstituentRowID]})


(defsc
 toxeffort
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.toxeffort/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "toxeffort"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :toxeffort/Dilution
   :toxeffort/ToxConc
   :toxeffort/ToxEffortComments
   :toxeffort/ToxEffortRowID
   :toxeffort/ToxResConstituentRowID
   :toxeffort/ToxTestRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 toxeffort-sum
 [_ _]
 {:ident [:org.riverdb.db.toxeffort/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :toxeffort/ToxEffortRowID]})


(defsc
 toxefforttypelookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.toxefforttypelookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "toxefforttypelookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :toxefforttypelookup/Active
   :toxefforttypelookup/ToxEffortType
   :toxefforttypelookup/ToxEffortTypeDescr
   :toxefforttypelookup/ToxEffortTypeRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 toxefforttypelookup-sum
 [_ _]
 {:ident [:org.riverdb.db.toxefforttypelookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id :riverdb.entity/ns :toxefforttypelookup/ToxEffortTypeRowID]})


(defsc
 toxendpointlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.toxendpointlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "toxendpointlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :toxendpointlookup/Active
   :toxendpointlookup/ToxEffortType
   :toxendpointlookup/ToxEndPoint
   :toxendpointlookup/ToxEndPointCode
   :toxendpointlookup/ToxEndPointRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 toxendpointlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.toxendpointlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id :riverdb.entity/ns :toxendpointlookup/ToxEndPointRowID]})


(defsc
 toxresconstituentlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.toxresconstituentlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello",
   :db/id (tempid),
   :ui/name "toxresconstituentlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :toxresconstituentlookup/Active
   :toxresconstituentlookup/TimePoint
   :toxresconstituentlookup/TimePointUnit
   :toxresconstituentlookup/ToxEndPointCode
   :toxresconstituentlookup/ToxResConstituentCode
   :toxresconstituentlookup/ToxResConstituentRowID
   :toxresconstituentlookup/UnitCode
   :toxresconstituentlookup/WQSource]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 toxresconstituentlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.toxresconstituentlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :toxresconstituentlookup/ToxResConstituentRowID]})


(defsc
 toxresult
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.toxresult/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "toxresult"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :toxresult/ResQualCode
   :toxresult/Result
   :toxresult/SigFig
   :toxresult/ToxEffortRowID
   :toxresult/ToxReplicate
   :toxresult/ToxResultsComments
   :toxresult/ToxResultsRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 toxresult-sum
 [_ _]
 {:ident [:org.riverdb.db.toxresult/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :toxresult/ToxResultsRowID]})


(defsc
 toxsigeffectlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.toxsigeffectlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "toxsigeffectlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :toxsigeffectlookup/Active
   :toxsigeffectlookup/ToxSigEffectCode
   :toxsigeffectlookup/ToxSigEffectDescr
   :toxsigeffectlookup/ToxSigEffectRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 toxsigeffectlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.toxsigeffectlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id :riverdb.entity/ns :toxsigeffectlookup/ToxSigEffectRowID]})


(defsc
 toxspecieslookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.toxspecieslookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "toxspecieslookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :toxspecieslookup/Active
   :toxspecieslookup/ToxSpeciesCode
   :toxspecieslookup/ToxSpeciesComments
   :toxspecieslookup/ToxSpeciesName
   :toxspecieslookup/ToxSpeciesRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 toxspecieslookup-sum
 [_ _]
 {:ident [:org.riverdb.db.toxspecieslookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :toxspecieslookup/ToxSpeciesRowID]})


(defsc
 toxsum
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.toxsum/gid :db/id],
  :initial-state {:ui/msg "hello", :db/id (tempid), :ui/name "toxsum"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :toxsum/EvalThreshold
   :toxsum/Mean
   :toxsum/PctControl
   :toxsum/Probability
   :toxsum/RepCount
   :toxsum/StatMethCode
   :toxsum/StdDev
   :toxsum/ToxEffortRowId
   :toxsum/ToxSigEffectCode
   :toxsum/ToxSumComments
   :toxsum/ToxSumRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 toxsum-sum
 [_ _]
 {:ident [:org.riverdb.db.toxsum/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :toxsum/ToxSumRowID]})


(defsc
 toxtest
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.toxtest/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "toxtest"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :toxtest/ComplianceCode
   :toxtest/LabSampleID
   :toxtest/QACode
   :toxtest/SampleID
   :toxtest/SampleRowID
   :toxtest/ToxBatch
   :toxtest/ToxConstituentRowID
   :toxtest/ToxTestComments
   :toxtest/ToxTestRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 toxtest-sum
 [_ _]
 {:ident [:org.riverdb.db.toxtest/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :toxtest/ToxTestRowID]})


(defsc
 toxtestdurlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.toxtestdurlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "toxtestdurlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :toxtestdurlookup/Active
   :toxtestdurlookup/ToxTestDur
   :toxtestdurlookup/ToxTestDurRowID
   :toxtestdurlookup/Unit]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 toxtestdurlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.toxtestdurlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :toxtestdurlookup/ToxTestDurRowID]})


(defsc
 unitlookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.unitlookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "unitlookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :unitlookup/Active
   :unitlookup/Unit
   :unitlookup/UnitCode]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 unitlookup-sum
 [_ _]
 {:ident [:org.riverdb.db.unitlookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :unitlookup/UnitCode]})


(defsc
 unitslookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.unitslookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "unitslookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :unitslookup/CodeListName
   :unitslookup/FromDate
   :unitslookup/SignificantDecimal
   :unitslookup/ToDate
   :unitslookup/Units
   :unitslookup/UnitsLookupRowID
   :unitslookup/VariableName]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 unitslookup-sum
 [_ _]
 {:ident [:org.riverdb.db.unitslookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :unitslookup/UnitsLookupRowID]})


(defsc
 userpreferences
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.userpreferences/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "userpreferences"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :userpreferences/cboAgencyCode
   :userpreferences/cboBasis
   :userpreferences/cboDatum
   :userpreferences/cboDepthUnits
   :userpreferences/cboDigestExtractCode
   :userpreferences/cboGPSDeviceCode
   :userpreferences/cboHydromod
   :userpreferences/cboHydromodLoc
   :userpreferences/cboLabBatch
   :userpreferences/cboOccupMethod
   :userpreferences/cboPositionWaterColumn
   :userpreferences/cboPrepCode
   :userpreferences/cboProjectID
   :userpreferences/cboQACodeFieldResult
   :userpreferences/cboQACodeLabResult
   :userpreferences/cboQACodeToxTest
   :userpreferences/cboResQualCodeFieldResult
   :userpreferences/cboResQualCodeLabResult
   :userpreferences/cboResQualCodeToxResult
   :userpreferences/cboSampleLocation
   :userpreferences/cboSamplingCrew
   :userpreferences/cboSamplingDeviceCode
   :userpreferences/cboSamplingDeviceCodeFieldResult
   :userpreferences/cboSeason
   :userpreferences/cboStartingBank
   :userpreferences/cboStatMethCode
   :userpreferences/cboStationFail
   :userpreferences/cboToxBatch
   :userpreferences/cboToxConc
   :userpreferences/cboToxSigEffectCode
   :userpreferences/cboUnitAccuracy
   :userpreferences/cboUnitDistanceFromBank
   :userpreferences/cboUnitsStationWaterDepth
   :userpreferences/cboUnitsStreamWidth
   :userpreferences/txtDilution
   :userpreferences/txtEvalThreshold
   :userpreferences/txtLatitude
   :userpreferences/txtLongitude
   :userpreferences/txtMean
   :userpreferences/txtPctControl
   :userpreferences/txtProbability
   :userpreferences/txtStdDev]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 userpreferences-sum
 [_ _]
 {:ident [:org.riverdb.db.userpreferences/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns]})


(defsc
 variablecodeslookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.variablecodeslookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "variablecodeslookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :variablecodeslookup/Active
   :variablecodeslookup/CodeDescription
   :variablecodeslookup/CodeListName
   :variablecodeslookup/ValueCode
   :variablecodeslookup/VariableCodesLookUpRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 variablecodeslookup-sum
 [_ _]
 {:ident [:org.riverdb.db.variablecodeslookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id
   :riverdb.entity/ns
   :variablecodeslookup/VariableCodesLookUpRowID]})


(defsc
 variableslookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.variableslookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "variableslookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :variableslookup/Active
   :variableslookup/Description
   :variableslookup/VariableName
   :variableslookup/VariableType
   :variableslookup/VariablesLookUpRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 variableslookup-sum
 [_ _]
 {:ident [:org.riverdb.db.variableslookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query
  [:db/id :riverdb.entity/ns :variableslookup/VariablesLookUpRowID]})


(defsc
 wbtypelookup
 [_ {:keys [ui/msg ui/name]}]
 {:ident [:org.riverdb.db.wbtypelookup/gid :db/id],
  :initial-state
  {:ui/msg "hello", :db/id (tempid), :ui/name "wbtypelookup"},
  :query
  [:ui/msg
   :ui/name
   :db/id
   :riverdb.entity/ns
   :wbtypelookup/WBType
   :wbtypelookup/WBTypeDescr
   :wbtypelookup/WBTypeRowID]}
 (do
  (log/debug (str "RENDER " name))
  (div (str msg " from the " name " component"))))

(defsc
 wbtypelookup-sum
 [_ _]
 {:ident [:org.riverdb.db.wbtypelookup/gid :db/id],
  :initial-state #:db{:id (tempid)},
  :query [:db/id :riverdb.entity/ns :wbtypelookup/WBTypeRowID]})


