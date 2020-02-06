(ns theta.log
  #?(:cljs (:require-macros [theta.log]))
  (:require
    [theta.util]
    #?@(:clj
        [[dotenv]
         [clojure.tools.logging :as log]])))

#?(:clj
   (defn cljs?
     "A CLJ macro helper. `env` is the macro's `&env` value. Returns true when expanding a macro while compiling CLJS."
     [env]
     (boolean (:ns env))))

#?(:clj (def log-level (Integer/parseInt (or (dotenv/env :LOG_LEVEL) "0"))))
;#?(:clj (println "LOADING theta.log at log-level: " log-level))

#?(:clj
   (defn set-level!
     "overrides the level at run time"
     [level]
     (def log-level level)))

#?(:clj (defmacro debug [& args]
          (when (<= log-level 1)
            (if (cljs? &env)
              `(do (~'js/console.debug ~@args) nil)
              `(log/debug ~@args)))))
#?(:clj (defmacro info [& args]
          (when (<= log-level 2)
            (if (cljs? &env)
              `(do (~'js/console.log ~@args) nil)
              `(log/info ~@args)))))
#?(:clj (defmacro warn [& args]
          (when (<= log-level 3)
            (if (cljs? &env)
              `(do (~'js/console.warn ~@args) nil)
              `(log/warn ~@args)))))
#?(:clj (defmacro error [& args]
          (when (<= log-level 4)
            (if (cljs? &env)
              `(do (~'js/console.error ~@args) nil)
              `(log/error ~@args)))))



(defn test-logs []
  (theta.log/debug "testing log debug")
  (theta.log/info "testing log info")
  (theta.log/warn "testing log warn")
  (theta.log/error "testing log error"))



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


;#?(:clj (deflogmacro debug 1 'debug))
;#?(:clj (deflogmacro info 2 'info))
;#?(:clj (deflogmacro warn 3 'warn))
;#?(:clj (deflogmacro error 4 'error))

