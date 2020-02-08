(ns riverdb.application
  (:require [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.networking.http-remote :as net]
            [com.fulcrologic.fulcro.rendering.keyframe-render2 :as kf2]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [com.wsscode.pathom.core :as p]
            [edn-query-language.core :as eql]
            [theta.log :as log :refer [debug]]))

(def secured-request-middleware
  ;; The CSRF token is embedded via server_components/html.clj
  (->
    (net/wrap-csrf-token (or js/fulcro_network_csrf_token "TOKEN-NOT-IN-HTML!"))
    (net/wrap-fulcro-request)))

(defn contains-error?
  "Check to see if the response contains Pathom error indicators."
  [body]
  (when (map? body)
    (let [values     (vals body)
          has-error? (reduce
                       (fn [error? v]
                         (if (or
                               (and (map? v) (contains? (set (keys v)) ::p/reader-error))
                               (= v ::p/reader-error))
                           (reduced true)
                           error?))
                       false
                       values)]
      ;(debug "contains-error?" has-error? body)
      has-error?)))


(defonce SPA (app/fulcro-app
               {;; This ensures your client can talk to a CSRF-protected server.
                ;; See middleware.clj to see how the token is embedded into the HTML
                :remotes {:remote (net/fulcro-http-remote
                                    {:url                "/api"
                                     :request-middleware secured-request-middleware})}
                :remote-error? (fn [{:keys [body] :as result}]
                                 (or
                                   (app/default-remote-error? result)
                                   (contains-error? body)))
                :optimized-render! kf2/render!
                :global-eql-transform (fn [ast]
                                        (let [kw-namespace (fn [k] (and (keyword? k) (namespace k)))
                                              mutation?    (symbol? (:dispatch-key ast))]
                                          (cond-> (df/elide-ast-nodes ast
                                                    (fn [k]
                                                      (let [ns (some-> k kw-namespace)]
                                                        (or
                                                          (= k '[:com.fulcrologic.fulcro.ui-state-machines/asm-id _])
                                                          (= k df/marker-table)
                                                          (= k ::fs/config)
                                                          (and
                                                            (string? ns)
                                                            (= "ui" ns))))))
                                            mutation? (update :children conj (eql/expr->ast :tempids)))))}))
                ;:client-did-mount     (fn [app]
                ;                        (auth/start! app [LoginForm]))}))

(comment
  (-> SPA (::app/runtime-atom) deref ::app/indexes))
