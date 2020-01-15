(ns riverdb.service-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [io.pedestal.http :as bootstrap]
            [riverdb.server :as server]
            [mount.core :as mount]))

(declare service)

(defn my-test-fixture [f]
  (mount/start-with-args {:config "config/dev.edn"} (mount/except #{#'server/server}))
  (def service
    (::bootstrap/service-fn (bootstrap/create-servlet (server/create-service-map))))
  (f)
  (mount/stop))

; Here we register my-test-fixture to be called once, wrapping ALL tests
; in the namespace
(use-fixtures :once my-test-fixture)


(deftest hello-page-test
  (is (=
        (:body (response-for service :get "/hello"))
        "Hello World!"))
  (is (=
        (:headers (response-for service :get "/hello"))
        {"Strict-Transport-Security"         "max-age=31536000; includeSubdomains",
         "X-Frame-Options"                   "DENY",
         "X-Content-Type-Options"            "nosniff",
         "X-XSS-Protection"                  "1; mode=block",
         "X-Download-Options"                "noopen",
         "X-Permitted-Cross-Domain-Policies" "none",
         "Content-Security-Policy"           "script-src 'unsafe-inline' 'unsafe-eval' *; object-src *",
         "Content-Type"                      "text/html;charset=UTF-8"})))


