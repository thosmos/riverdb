(ns watch
  "Dev-only hot reload for the Datastar HTML app on port 9595.

  Two things make this work without bouncing Jetty:

    1. riverdb.html.server asks Pedestal for a *routing function*, so the route
       table and every handler in it are resolved fresh on each request.
    2. tools.namespace is scoped to src/server/riverdb/html, and the server
       namespace itself is pinned via ns metadata, so a reload never touches
       the mount state holding the running server.

  Usage from the REPL:

    (watch/on)     ;; reload on save
    (watch/off)
    (watch/reload) ;; one-shot, no watcher needed"
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.namespace.repl :as tns]))

(def html-dir "src/server/riverdb/html")

(defn scope!
  "Point tools.namespace at just the HTML app, so a reload can't unload
  riverdb.server or riverdb.graphql.schema (expensive, and they hold state)."
  []
  (tns/set-refresh-dirs html-dir))

(defn scope-all!
  "Undo scope! if you want project-wide refresh semantics back."
  []
  (tns/set-refresh-dirs "src/main" "src/server" "src/dev"))

(defonce ^:private reload-lock (Object.))

(defn reload
  "Reload whatever changed under html-dir, in dependency order. Returns :ok or
  the Throwable, and never throws, so a typo can't kill the watcher thread."
  []
  (locking reload-lock
    ;; tools.namespace restores *ns* with set! when it finishes, which needs a
    ;; thread-local binding. The watcher thread has none, so establish one here
    ;; or every reload reports a bogus "Can't change/establish root binding".
    (let [result (binding [*ns* *ns*]
                   (try (tns/refresh) (catch Throwable t t)))]
      (if (instance? Throwable result)
        (do
          (println "  reload FAILED:" (ex-message result))
          (when-let [c (ex-cause result)]
            (println "  cause:" (ex-message c)))
          result)
        (do (println "  reloaded:" result) result)))))

(defn- snapshot []
  (into {}
    (for [^java.io.File f (file-seq (io/file html-dir))
          :when (and (.isFile f) (str/ends-with? (.getName f) ".clj"))]
      [(.getPath f) (.lastModified f)])))

(defonce ^:private watcher (atom nil))

(defn on
  "Start watching html-dir; reload on any .clj change. Idempotent."
  ([] (on 400))
  ([poll-ms]
   (scope!)
   (if @watcher
     (do (println "already watching" html-dir) :already-watching)
     (let [stop (atom false)
           t    (Thread.
                  ^Runnable
                  (fn []
                    (loop [prev (snapshot)]
                      (when-not @stop
                        (Thread/sleep (long poll-ms))
                        (let [now (snapshot)]
                          (if (= now prev)
                            (recur prev)
                            (do
                              (println "\n[watch] change detected")
                              (reload)
                              ;; re-snapshot after reloading: a failed reload
                              ;; must not re-fire until the file changes again
                              (recur (snapshot)))))))
                    (println "[watch] stopped"))
                  "riverdb-html-watch")]
       (.setDaemon t true)
       (.start t)
       (reset! watcher {:thread t :stop stop})
       (println "[watch] watching" html-dir "every" poll-ms "ms")
       :watching))))

(defn off []
  (if-let [{:keys [stop]} @watcher]
    (do (reset! stop true) (reset! watcher nil) :stopped)
    :not-watching))
