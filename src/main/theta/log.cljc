(ns theta.log
  #?(:cljs (:require-macros [theta.log])))
  ;#?(:clj (:require)))
            ;[environ.core :refer [env]]
            ;[dotenv]
            ;[clojure.tools.logging :as log])))

;#?(:clj
;   (defn set-level! [level]))

;(defonce log-level (atom 0))
;
;#?(:clj
;   (defn set-level! [level]
;     (reset! log-level (if (number? level)
;                         level
;                         (try (Integer/parseInt level)
;                              (catch Exception _ 0))))))

#?(:clj
   (defn cljs?
     "A CLJ macro helper. `env` is the macro's `&env` value. Returns true when expanding a macro while compiling CLJS."
     [env]
     (boolean (:ns env))))

;(defmacro functionize [macro]
;  `(fn [& args#] (eval (cons '~macro args#))))

;#?(:clj
;   (defmacro deflogmacro
;     [fn-name level f]
;     (let [cljs?     (cljs? &env)
;           prefix    (.toUpperCase (name fn-name))
;           msg       (gensym)
;           env-level 0]
;           ;env-level (or (dotenv/env :LOG_LEVEL) "0")
;           ;env-level (Integer/parseInt env-level)]
;
;       ;(if cljs?
;         (if (<= env-level level)
;           `(defmacro ~fn-name
;              [& ~msg]
;              (println "CREATING CLJS log macro" ~f)
;              ;`(. js/console ~~f (js/Date.) (apply str (interpose " " (list ~~prefix ~@~msg)))))
;              ;`(. js/console ~~f (apply str (interpose " " (list ~@~msg)))))
;              `(. js/console ~~f ~@~msg))
;           `(defmacro ~fn-name [& _#])))))
;         ;`(defn ~fn-name [& msgs#]
;         ;   ;(println "CREATING CLJ log macro" (str ~fn-name))
;         ;   (eval (apply list (symbol (str "log/" ~f)) msgs#)))))))

;#?(:clj (println "LOADING theta.log at log-level: " (or (dotenv/env :LOG_LEVEL) 0)))

;#?(:clj (deflogmacro debug 1 'debug))
;#?(:clj (deflogmacro info 2 'info))
;#?(:clj (deflogmacro warn 3 'warn))
;#?(:clj (deflogmacro error 4 'error))

#?(:clj (defmacro debug [& args]
          (if (cljs? &env)
            `(do (~'js/console.debug ~@args) nil)
            `(.println System/out  ~@args))))
#?(:clj (defmacro info [& args]
          (if (cljs? &env)
            `(do (~'js/console.log ~@args) nil)
            `(.println System/out  ~@args))))
#?(:clj (defmacro warn [& args]
          (if (cljs? &env)
            `(do (~'js/console.warn ~@args) nil)
            `(.println System/out  ~@args))))
#?(:clj (defmacro error [& args]
          (if (cljs? &env)
            `(do (~'js/console.error ~@args) nil)
            `(.println System/out  ~@args))))

(defn test-logs []
  (theta.log/debug "testing log debug")
  (theta.log/info "testing log info")
  (theta.log/warn "testing log warn")
  (theta.log/error "testing log error"))
