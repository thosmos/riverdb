(ns riverdb.server-components.pathom
  (:require
    [com.fulcrologic.rad.database-adapters.datomic :as datomic]
    [com.fulcrologic.rad.pathom :as radpm]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.attributes :as attr]
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]
    [com.wsscode.common.async-clj :refer [let-chan]]
    [clojure.core.async :as async]
    [mount.core :refer [defstate]]
    [riverdb.api.mutations :as mutations]
    [riverdb.api.resolvers :as resolvers]
    [riverdb.model.session :as session]
    [riverdb.model.user :as user]
    [riverdb.rad.model :refer [all-attributes]]
    [riverdb.rad.middleware :as middleware]
    [riverdb.server-components.config :refer [config]]
    [riverdb.server-components.auto-resolvers :refer [automatic-resolvers]]
    [riverdb.server-components.datomic :refer [datomic-connections]]
    [riverdb.state :refer [db cx]]))

(pc/defresolver index-explorer [{::pc/keys [indexes]} _]
  {::pc/input  #{:com.wsscode.pathom.viz.index-explorer/id}
   ::pc/output [:com.wsscode.pathom.viz.index-explorer/index]}
  {:com.wsscode.pathom.viz.index-explorer/index
   (p/transduce-maps
     (remove (comp #{::pc/resolve ::pc/mutate} key))
     indexes)})

(def all-resolvers [user/resolvers session/resolvers resolvers/resolvers
                    resolvers/lookup-resolvers resolvers/id-resolvers index-explorer
                    mutations/mutations
                    #_automatic-resolvers
                    form/save-form
                    #_form/delete-entity])

(defn preprocess-parser-plugin
  "Helper to create a plugin that can view/modify the env/tx of a top-level request.

  f - (fn [{:keys [env tx]}] {:env new-env :tx new-tx})

  If the function returns no env or tx, then the parser will not be called (aborts the parse)"
  [f]
  {::p/wrap-parser
   (fn transform-parser-out-plugin-external [parser]
     (fn transform-parser-out-plugin-internal [env tx]
       (let [{:keys [env tx] :as req} (f {:env env :tx tx})]
         (if (and (map? env) (seq tx))
           (parser env tx)
           {}))))})

(defn log-requests [{:keys [env tx] :as req}]
  ;(log/debug "Pathom transaction:" (pr-str tx))
  req)

(defn build-parser []
  (let [real-parser (p/parallel-parser
                      {::p/mutate  pc/mutate-async
                       ::p/env     {::p/reader               [p/map-reader
                                                              pc/parallel-reader
                                                              pc/open-ident-reader
                                                              p/env-placeholder-reader]
                                    ::pc/mutation-join-globals [:tempids]
                                    ::p/placeholder-prefixes #{">"}}
                       ::p/plugins [(pc/connect-plugin {::pc/register all-resolvers})
                                    (p/env-wrap-plugin (fn [env]
                                                         ;; Here is where you can dynamically add things to the resolver/mutation
                                                         ;; environment, like the server config, database connections, etc.
                                                         (-> env
                                                           (assoc
                                                             :db (db) ; real datomic would use (d/db db-connection)
                                                             :connection (cx)
                                                             :config config))))
                                                           ;(datomic/add-datomic-env {:production (:main datomic-connections)}))))
                                    (preprocess-parser-plugin log-requests)

                                    radpm/query-params-to-env-plugin

                                    (attr/pathom-plugin all-attributes) ; required to populate standard things in the parsing env
                                    (form/pathom-plugin middleware/save middleware/delete) ; installs form save/delete middleware
                                    (datomic/pathom-plugin (fn [env] {:production (:main datomic-connections)})) ; db-specific adapter

                                    p/error-handler-plugin
                                    p/request-cache-plugin
                                    (p/post-process-parser-plugin p/elide-not-found)
                                    p/trace-plugin]})
        ;; NOTE: Add -Dtrace to the server JVM to enable Fulcro Inspect query performance traces to the network tab.
        ;; Understand that this makes the network responses much larger and should not be used in production.
        trace?      (not (nil? (System/getProperty "trace")))]
    (fn wrapped-parser [env tx]
      (async/<!! (real-parser env (if trace?
                                    (conj tx :com.wsscode.pathom/trace)
                                    tx))))))

(defstate parser
  :start (build-parser))

;(defstate parser
;  :start
;  (pathom/new-parser config
;    (fn [env]
;      (-> env
;        (datomic/add-datomic-env {:production (:main datomic-connections)})))
;    all-resolvers))

