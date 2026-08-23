(ns riverdb.html.datastar
  "Datastar plumbing shared by every handler.

  Kept separate from any one feature because the two things in here are both
  easy to get subtly wrong, and getting them wrong fails silently."
  (:require
    [cheshire.core :as json]
    [clojure.tools.logging :refer [debug]]
    [starfederation.datastar.clojure.api :as d*]
    [starfederation.datastar.clojure.adapter.http-kit :refer [->sse-response on-open on-exception]]))

(def signals-in-query?
  "Methods for which Datastar puts signals in the `datastar` query param rather
  than the request body. From its source:

      ot = e => ![\"GET\",\"DELETE\"].includes(e)
      ot(method) ? body = payload : params.set(\"datastar\", payload)

  DELETE is in that list. The SDK's own `get-signals` only special-cases GET,
  so reading the body on a DELETE yields nothing — and a handler that diffs
  against the current selection then treats it as empty and wipes everything.
  Test DELETE endpoints with the payload in the query, as the browser sends it."
  #{:get :delete})

(defn raw-signals
  "The signals Datastar sent with this request, parsed with keyword keys.

  NOTE: this consumes the request body. Call it once per request and thread the
  result; a second call reads a closed stream and silently yields nil."
  [{:keys [request-method query-params body] :as _request}]
  (try
    (if (contains? signals-in-query? request-method)
      (some-> (get query-params "datastar") (json/parse-string true))
      (some-> body slurp not-empty (json/parse-string true)))
    (catch Exception e
      (debug "SIGNALS PARSE FAILED" (.getMessage e))
      nil)))

(defn sse
  "Run `f` against an open SSE generator and close it."
  [request f]
  (->sse-response request
    {on-open      (fn [gen] (d*/with-open-sse gen (f gen)))
     on-exception (fn [_gen e _opts] (debug "SSE EXCEPTION" (ex-message e)))}))

(defn patch-signals!
  [gen m]
  (d*/patch-signals! gen (json/encode m)))
