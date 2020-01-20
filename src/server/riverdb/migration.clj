(ns riverdb.migration
  (:require [clojure.tools.logging :as log :refer [debug info warn error]]
            [datomic.api :as d]
            [riverdb.state :as st :refer [db cx]]
            [buddy.sign.jwt :as jwt]
            [buddy.core.hash :as hash]
            [buddy.hashers :as hashers]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [io.rkn.conformity :as c]
            [domain-spec.literals :as ds]))


(comment
  (c/ensure-conforms (cx) (c/read-resource "migrations/2018-09-05-users.edn")))

;(def user-specs (read-string
;                  (pr {:add-schema/users-2018050900
;                       {:txes [
;                               #domain-spec/schema-tx
;                                   [[:user/name :one :string "user's name"]
;                                    [:user/uuid :one :uuid :identity "unique UUID for this user"]
;                                    [:user/email :one :string :identity "an email that uniquely identifies this user"]
;                                    [:user/password :one :string :index "user's hashed password"]
;                                    [:user/verify-token :one :string :index "verify token sent via email for password reset"]
;                                    [:user/verified? :one :boolean :index "has this email been verified?"]
;                                    [:user/roles :many :ref :component "various roles this account holds"]
;                                    [:user/gravatar :one :string "user's gravatar link"]
;                                    [:role/name :one :string "role name"]
;                                    [:role/doc :one :string "info about this role"]
;                                    [:role/uuid :one :uuid :identity "role uuid"]
;                                    [:role/agency :one :ref "ref to the agency this role is associated with"]]
;                               ]}})))