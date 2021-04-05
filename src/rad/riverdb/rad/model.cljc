(ns riverdb.rad.model
  (:require
    [riverdb.rad.model.person :as person]
    [riverdb.rad.model.agency :as agency]
    [riverdb.rad.model.global :as global]
    [com.fulcrologic.rad.attributes :as attr]))


(def all-attributes (vec (concat
                           person/attributes
                           agency/attributes
                           global/attributes)))

(def all-attribute-validator (attr/make-attribute-validator all-attributes))