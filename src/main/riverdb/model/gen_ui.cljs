(ns riverdb.model.gen-ui)


(def pspec
  {:db/id
   "projectslookup",
   :entity/name
   "projectslookup",
   :entity/ns
   :entity.ns/projectslookup,
   :entity/pks
   [:projectslookup/ProjectID],
   :entity/prKeys
   [:projectslookup/Active :projectslookup/ProjectID
    :projectslookup/Name :projectslookup/AgencyCode
    :projectslookup/AgencyRef],
   :entity/attrs
   [#:attr{:name        "Active",
           :key         :projectslookup/Active,
           :position    7,
           :cardinality :one,
           :type        :boolean}
    #:attr{:name        "AgencyCode",
           :key         :projectslookup/AgencyCode,
           :position    2,
           :cardinality :one,
           :type        :string,
           :strlen      20}
    #:attr{:name        "AgencyRef",
           :key         :projectslookup/AgencyRef,
           :cardinality :one,
           :type        :ref,
           :ref         #:entity{:ns :entity.ns/agencylookup},
           :doc         "a ref to the Agency entity"}
    #:attr{:name        "FieldVerCode",
           :key         :projectslookup/FieldVerCode,
           :position    4,
           :cardinality :one,
           :type        :ref,
           :ref         #:entity{:ns :entity.ns/batchvallookup},
           :strlen      10}
    #:attr{:name        "FieldVerComm",
           :key         :projectslookup/FieldVerComm,
           :position    5,
           :cardinality :one,
           :type        :string,
           :strlen      50}
    #:attr{:name        "Name",
           :key         :projectslookup/Name,
           :position    3,
           :cardinality :one,
           :type        :string,
           :strlen      250}
    #:attr{:name        "ParentProjectID",
           :key         :projectslookup/ParentProjectID,
           :position    3,
           :cardinality :one,
           :type        :ref,
           :ref         #:entity{:ns :entity.ns/parentprojectlookup},
           :strlen      25}
    #:attr{:name        "ProjectID",
           :key         :projectslookup/ProjectID,
           :position    1,
           :primary     true,
           :identity    true,
           :cardinality :one,
           :type        :string,
           :strlen      8}
    #:attr{:name        "ProjectsComments",
           :key         :projectslookup/ProjectsComments,
           :position    8,
           :cardinality :one,
           :type        :string,
           :strlen      250}
    #:attr{:name        "QAPPVersion",
           :key         :projectslookup/QAPPVersion,
           :position    6,
           :cardinality :one,
           :type        :string,
           :strlen      100}
    #:attr{:name        "Stations",
           :key         :projectslookup/Stations,
           :cardinality :many,
           :type        :ref
           :doc         "Each project has a set of stations"}
    #:attr{:name        "Parameters",
           :key         :projectslookup/Parameters,
           :cardinality :many,
           :type        :ref
           :ref         {:entity/ns :entity.ns/parameter}
           :doc         "Each project has a set of its active parameters (Constituents in SWAMP naming)"}
    #:attr{:name        "Public",
           :key         :projectslookup/Public,
           :cardinality :one,
           :type        :boolean
           :doc         "Is this project visible to the public?"}]})

