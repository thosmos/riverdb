(ns riverdb.email
  (:require [clojure.tools.logging :as log :refer [debug info warn error]]
            [thosmos.util :as tu]
            [datomic.api :as d]
            dotenv
            [riverdb.state :as st :refer [db state cx uri]]))


(defn create-msg [to subject body]
  {:from "RiverDB <info@riverdb.org>"
   :to to
   :subject subject
   :text body})

(defn create-verify-msg [to token]
  (let [body    (str
                  "Please click this link or paste it into your browser "
                  "to confirm your email address.\n\n"
                  "https://graphql.riverdb.org/verify/" token)
        subject "Please verify you are who you say you are"]
    (create-msg to subject body)))

(def api-host "api.mailgun.net")
(def api-base-url (str "https://" api-host "/v3"))