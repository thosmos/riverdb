(ns riverdb.development-preload
  (:require
    [com.fulcrologic.fulcro.algorithms.timbre-support :as ts]
    [taoensso.timbre :as log]
    [clojure.string :as str]))

;(defn prefix-output-fn
;  "Mostly taken from timbre, but just formats message prefix as output (e.g. only location/line/level). Use with the
;   console appender from this namespace to get better logging output in cljs."
;  ([data] (prefix-output-fn nil data))
;  ([opts data]                                              ; For partials
;   (let [{:keys [level msg_ ?ns-str ?file ?line]} data]
;     (str (str/upper-case (name level)) " " msg_ " " "[" (or ?ns-str ?file "?") ":" (or ?line "?") "] - "))))

;(defn default-output-fn
;  "Default (fn [data]) -> string output fn.
;  Use`(partial default-output-fn <opts-map>)` to modify default opts."
;  ([     data] (default-output-fn nil data))
;  ([opts data] ; For partials
;   (let [{:keys [no-stacktrace? stacktrace-fonts]} opts
;         {:keys [level ?err #_vargs msg_ ?ns-str ?file hostname_
;                 timestamp_ ?line]} data]
;     (str
;       #+clj (force timestamp_) #+clj " "
;       #+clj (force hostname_)  #+clj " "
;       (str/upper-case (name level))  " "
;       "[" (or ?ns-str ?file "?") ":" (or ?line "?") "] - "
;       (force msg_)
;       (when-not no-stacktrace?
;         (when-let [err ?err]
;           (str "\n" (stacktrace err opts))))))))
;
;(defn console-appender
;  "Returns a js/console appender for ClojureScript. This appender uses the normal output-fn to generate the main
;  message, but it also does raw output of the original logging args so that devtools can format data structures.
;
;  Furthermore, if it detects an ExceptionInfo it will print the `ex-message` *after* so that you can see the real
;  message of the exception last in the console.  This is particularly handy when using specs and expound with
;  spec instrumentation.
;
;  For accurate line numbers in Chrome, add these Blackbox[1] patterns:
;    `/taoensso/timbre/appenders/core\\.js$`
;    `/taoensso/timbre\\.js$`
;    `/cljs/core\\.js$`
;
;  [1] Ref. https://goo.gl/ZejSvR"
;  [& [opts]]
;  {:enabled?   true
;   :async?     false
;   :min-level  nil
;   :rate-limit nil
;   :output-fn  :inherit
;   :fn         (if (exists? js/console)
;                 (let [;; Don't cache this; some libs dynamically replace js/console
;                       level->logger
;                       (fn [level]
;                         (or
;                           (case level
;                             :trace js/console.trace
;                             :debug js/console.debug
;                             :info js/console.info
;                             :warn js/console.warn
;                             :error js/console.error
;                             :fatal js/console.error
;                             :report js/console.info)
;                           js/console.log))]
;
;                   (fn [{:keys [level vargs ?err output-fn] :as data}]
;                     (when-let [logger (level->logger level)]
;                       (let [args   (if-let [err ?err]
;                                      (cons err vargs)
;                                      vargs)
;                             output (when output-fn (output-fn (assoc data :msg_ args :?err nil)))]
;                         (js/console output)
;                         (when (instance? ExceptionInfo ?err)
;                           (js/console.log (ex-message ?err)))))))
;                 (fn [data] nil))})

(js/console.log "Turning logging to :debug (in app.development-preload)")
(log/set-level! :debug)
(log/merge-config! {:output-fn ts/prefix-output-fn
                    :appenders {:console (ts/console-appender)}
                    :ns-blacklist  []});"com.fulcrologic.fulcro.algorithms.indexing"
                                    ;"com.fulcrologic.fulcro.algorithms.tx-processing"]})
