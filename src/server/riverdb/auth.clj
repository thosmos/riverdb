(ns riverdb.auth
  (:require [buddy.sign.jwt :as jwt]
            [buddy.core.hash :as hash]
            [buddy.hashers :as hashers]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [clojure.tools.logging :as log :refer [debug info warn error]]
            [clojure.walk :as walk]
            [datomic.api :as d]
            [dotenv]
            [riverdb.model.user :as user]
            [riverdb.state :as st :refer [db state cx uri]]
            [thosmos.util :as tu :refer [check]]))

(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")

(def jwt-secret (or (dotenv/env :JWT_SECRET) "5b90f771-1366-4091-a46a-dba088741fb5"))

(s/def ::check-token-result
  (s/or
    :user (s/keys :req [:user/email])
    :error string?
    :nil nil?))

(s/fdef check-token
  :args string?
  :ret ::check-token-result)

(defn check-token [token]
  (when token
    (try
      (jwt/unsign token jwt-secret)
      (catch Exception ex (do
                            (debug "check-token: " (.getMessage ex))
                            nil)))))

(defn create-token [email]
  (jwt/sign {:user/email email} jwt-secret))

(defn hash-password [password]
  (hashers/derive password))

(s/def ::email (s/and string? #(re-matches email-regex %)))
(s/def ::verify string?)
(s/def ::password string?)

(s/def ::auth-password-args
  (s/keys
    :req-un [::email ::password ::verify]
    :opt-un []))

(s/def ::success boolean?)
(s/def ::msg string?)
(s/def ::token string?)


(s/def ::auth-result (s/keys
                       :req-un [::success ::msg]
                       :opt-un [::token ::user]))

(s/fdef auth-password
  :ret ::auth-result
  :args ::auth-password-args)



;(defn walk-remove-ns [map]
;  (walk/postwalk
;    (fn [form]
;      (if (map? form)
;        (reduce-kv (fn [acc k v] (assoc acc (keyword (name k)) v)) {} form)
;        form))
;    map))

(defn auth-password [{:keys [email password verify] :as args}]
  ;{:pre  [(check ::auth-password-args args)]
  ; :post [(check ::auth-result %)]}
  (cond
    (try
      (not (check ::auth-password-args args))
      (catch AssertionError _ true))
    {:success false
     :msg     "invalid args"}

    (and email password verify)
    (do
      (debug "AUTH email + pass: " email)
      (let [ok? (try (hashers/check password verify) (catch Exception ex false))]
        (cond
          ok?
          {:success true
           :msg     "authenticated"
           :token   (create-token email)}
          :else
          {:success false
           :msg     "failed auth"})))

    :else
    {:success false
     :msg     "invalid args"}))

(defn unauth []
  {})
