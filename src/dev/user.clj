(ns user
  (:require
    [clojure.edn :as edn]
;    [clojure.tools.namespace.repl :as tools-ns :refer [set-refresh-dirs]]
    [datascript.core :as ds]
    [datomic.api :as d]
    [domain-spec.core :as dspec]
    [mount.core :as mount]
    [riverdb.server]
    [riverdb.state :refer [db cx]]
    [riverdb.db :as rdb :refer [rpull pull-entities]]
    [com.rpl.specter :as sp]
    [thosmos.util :as tu]
    [thosmos.datomic :as tdb]
    [java-time :as jt]
    [tick.alpha.api :as t]
    [theta.log :as log]))

(set! *data-readers* (assoc *data-readers* 'fulcro/tempid #'riverdb.util/readTempid))

;; ==================== SERVER ====================
;(set-refresh-dirs "src/main" "src/server" "src/rad")

(defn start
  "Start the web server"
  []
  (mount/start-with-args {:config "config/dev.edn"}))

;(defn start-db []
;  (start-dbs riverdb.state/state))

(defn stop
  "Stop the web server"
  [] (mount/stop))

(defn restart
  "Stop, reload code, and restart the server. If there is a compile error, use:

  ```
;  (tools-ns/refresh)
  ```

  to recompile, and then use `start` once things are good."
  []
  (stop)
 ; (tools-ns/refresh :after 'user/start))
  (start))

(defn specs []
  (edn/read-string
    (slurp "resources/specs.edn")))

(defn specs-map []
  (dspec/specs->map (specs)))

(defn format-specs []
  (tu/spitpp "resources/specs-save.edn"
    (dspec/sort-specs
      (edn/read-string
        (slurp "resources/specs.edn")))))

;; Run (start-server-tests) in a REPL to start a runner that can render results in a browser
;; See fulcro-spec documentation for more information. NOTE: `specification` is really just
;; a `deftest` underneath, so you can use "Run all tests in this namespace" with your
;; editor/IDE and it should work that way too.  You can also use the fulcro-spec functions
;; like `assertions` and `when-mocking` in a regular `deftest` if you'd rather do that (which
;; gives a slight better REPL integration, while still leveraging some of the helpers).
;(suite/def-test-suite start-server-tests
;  {:config       {:port 8888}
;   :test-paths   ["src/test"]
;   :source-paths ["src/main"]}
;  {:available #{:focused :unit :integration}
;   :default   #{::sel/none :focused :unit}})

;(comment
;  ;; generate components
;  (require '[riverdb.graphql.schema :refer [specs-edn]])
;  (first specs-edn))

;(defmacro functionize [macro]
;  `(fn [& args#] (eval (cons '~macro args#))))

;(defn cljs?
; "A CLJ macro helper. `env` is the macro's `&env` value. Returns true when expanding a macro while compiling CLJS."
;  [env]
;  (boolean (:ns env)))
;
;(defmacro functionize [macro]
;  `(fn [& args#] (eval (cons '~macro args#))))
;
;(defmacro deflogmacro [f]
;  `(defn ~f [& msgs#]
;     (eval (apply list (symbol (str "log/" '~f)) msgs#))))
;
;
;(defn call [this & that]
;  (let [this-sym (symbol this)
;        this-resolved (ns-resolve 'resolver-clj.core this-sym)]
;    (.println System/out (str "current-ns: " *ns*))
;    (.println System/out (str "this-sym: " this-sym))
;    (.println System/out (str "this-resolved: " this-resolved))
;    (apply this-resolved that)))

;(clojure.tools.logging/debug ~msg#))))
    ;`(defn ~f
    ;   [& ~msg]
    ;   ;`(. js/console ~~f (js/Date.) (apply str (interpose " " (list ~~prefix ~@~msg)))))
    ;   ;`(. js/console ~~f (apply str (interpose " " (list ~@~msg)))))
    ;   `(~~f ~@~msg))
    ;#_`(defmacro ~f [& msg]
    ;     (eval (list (symbol (str "clojure.tools.logging/" '~f)) ~@msg)))))


;(defmacro logit [f & msgs]
;  `(eval (list (symbol (str "clojure.tools.logging/" '~f)) ~@msgs)))

;(timbre/merge-config! {:level :debug})

;; Control log filtering by namespaces/patterns. Useful for turning off
;; logging in noisy libraries, etc.:
;:ns-whitelist  [] #_["my-app.foo-ns"]
;:ns-blacklist  [] ["taoensso.*"]})



;(comment
;  (require '[vlaaad.reveal.ui])
;  (def rev (vlaaad.reveal.ui/make)))
;(require '[clojure.tools.deps.alpha :as deps])
;
;(->
;  '{:deps {org.clojure/clojure {:mvn/version "1.10.0"}
;           org.clojure/core.async {:mvn/version "0.4.474"}}
;    :mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}
;                "clojars" {:url "https://repo.clojars.org/"}}}
;  (deps/resolve-deps nil)
;  (deps/make-classpath nil nil)))


