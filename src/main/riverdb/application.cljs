(ns riverdb.application
  (:require [edn-query-language.core :as eql]
            [clojure.walk :as walk]
            [com.fulcrologic.fulcro.algorithms.form-state :as fs]
            [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.data-fetch :as df]
            [com.fulcrologic.fulcro.networking.file-upload :as file-upload]
            [com.fulcrologic.fulcro.networking.http-remote :as net]
            [com.fulcrologic.fulcro.rendering.keyframe-render2 :as kf2]
            [com.fulcrologic.fulcro.rendering.multiple-roots-renderer :as mroot]
            [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
            [com.fulcrologic.fulcro.ui-state-machines :as uism]
            [com.wsscode.pathom.core :as p]
            [theta.log :as log :refer [debug]]))

(def default-network-blacklist
  "A set of the keywords that should not appear on network requests."
  #{::uism/asm-id
    ::app/active-remotes
    :com.fulcrologic.rad.blob/blobs
    :com.fulcrologic.rad.picker-options/options-cache
    df/marker-table
    ::fs/config})

(defn elision-predicate
  "Returns an elision predicate that will return true if the keyword k is in the blacklist or has the namespace
  `ui`."
  [blacklist]
  (fn [k]
    (let [kw-namespace (fn [k] (and (keyword? k) (namespace k)))
          k            (if (vector? k) (first k) k)
          ns           (some-> k kw-namespace)]
      (or
        (contains? blacklist k)
        (and (string? ns) (= "ui" ns))))))

(defn elide-params
  "Given a params map, elides any k-v pairs where `(pred k)` is false."
  [params pred]
  (walk/postwalk (fn [x]
                   (if (and (vector? x) (= 2 (count x)) (pred (first x)))
                     nil
                     x))
    params))

(defn elide-ast-nodes
  "Like df/elide-ast-nodes but also applies elision-predicate logic to mutation params."
  [{:keys [key union-key children] :as ast} elision-predicate]
  (let [union-elision? (and union-key (elision-predicate union-key))]
    (when-not (or union-elision? (elision-predicate key))
      (when (and union-elision? (<= (count children) 2))
        (log/warn "Unions are not designed to be used with fewer than two children. Check your calls to Fulcro
        load functions where the :without set contains " (pr-str union-key)))
      (let [new-ast (-> ast
                      (update :children (fn [c] (vec (keep #(elide-ast-nodes % elision-predicate) c))))
                      (update :params elide-params elision-predicate))]
        (if (seq (:children new-ast))
          new-ast
          (dissoc new-ast :children))))))

(defn global-eql-transform
  "Returns an EQL transform that removes `(pred k)` keywords from network requests."
  [pred]
  (fn [ast]
    (let [mutation? (symbol? (:dispatch-key ast))]
      (cond-> (-> ast
                (elide-ast-nodes pred)
                (update :children conj (eql/expr->ast :com.wsscode.pathom.core/errors)))
        mutation? (update :children conj (eql/expr->ast :tempids))))))

#_(def global-eql-transform-old
    (fn [ast]
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
          mutation? (update :children conj (eql/expr->ast :tempids))))))

(defn secured-request-middleware [{:keys [csrf-token]}]
  (->
    (net/wrap-fulcro-request)
    (file-upload/wrap-file-upload)
    (cond->
      csrf-token (net/wrap-csrf-token csrf-token))))

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
               {:remotes              {:remote (net/fulcro-http-remote
                                                 {:url                "/api"
                                                  :request-middleware (secured-request-middleware
                                                                        {:csrf-token
                                                                         (when-not
                                                                           (undefined? js/fulcro_network_csrf_token)
                                                                           js/fulcro_network_csrf_token)})})}
                :remote-error?        (fn [{:keys [body] :as result}]
                                        (or
                                          (app/default-remote-error? result)
                                          (contains-error? body)))
                ;:optimized-render!    kf2/render!
                :optimized-render!    mroot/render!
                ;:global-eql-transform global-eql-transform-old
                :global-eql-transform (global-eql-transform (elision-predicate default-network-blacklist))}))
                ;:client-did-mount     (fn [app]
                ;                        (auth/start! app [LoginForm]))}))


(comment
  (-> SPA (::app/runtime-atom) deref ::app/indexes))
