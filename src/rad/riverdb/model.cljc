(ns riverdb.model
  (:require
    [riverdb.model.person :as person]
    [com.fulcrologic.rad.attributes :as attr]))

(def all-attributes (vec (concat
                           person/attributes)))

(def all-attribute-validator (attr/make-attribute-validator all-attributes))