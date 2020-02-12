(ns riverdb.ui.lookup
  #?(:cljs (:require-macros [riverdb.ui.lookup]))
  (:require
    [com.fulcrologic.fulcro.components :as om :refer [defsc transact!]]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [com.fulcrologic.fulcro.dom :as dom]
    ;[domain-spec.core :refer [specs->map]]
    [theta.log :refer [debug]]
    #?@(:cljs
        [[shadow.resource :as rc]
         [cljs.tools.reader.edn :as edn]])
    #?@(:clj
        [[clojure.edn :as edn]
         [thosmos.util :refer [functionize]]])))

(defn coll->map
  ([key-fn coll]
   (coll->map key-fn identity coll))
  ([key-fn val-fn coll]
   (into (sorted-map)
     (for [m coll]
       [(key-fn m) (val-fn m)]))))

(defn specs->map [coll]
  (->> coll
    (coll->map :entity/ns
      (fn [ent]
        (update ent :entity/attrs #(coll->map :attr/key %))))))

;#?(:cljs (def specs (rc/inline "specs.edn")))
#?(:cljs (def specs-map (specs->map (edn/read-string (rc/inline "specs.edn")))))

#?(:cljs (defn get-refNameKey [attr]
           (when (= :ref (:attr/type attr))
             (let [refKey (:attr/refKey attr)]
               (-> specs-map refKey :entity/nameKey)))))

#?(:cljs (defn get-refNameKeys [attrs]
           (reduce-kv
             (fn [refs k v]
               (if (= :ref (:attr/type v))
                 (assoc refs k (get-refNameKey v))
                 refs))
             {} attrs)))


;#?(:clj (defmacro specs [] (specs->map (clojure.edn/read-string (clojure.core/slurp "resources/specs.edn")))))

;#?(:clj (defmacro specs [] (clojure.edn/read-string (clojure.core/slurp "resources/specs.edn"))))

;(defn get-specs []
;  #?(:clj (clojure.edn/read-string (specs))
;     :cljs (cljs.tools.reader.edn/read-string (specs))))


;#?(:clj (defmacro specs-global [] (clojure.edn/read-string (clojure.core/slurp "resources/specs-global.edn"))))

;#?(:clj
;   (defn cljs?
;     "A CLJ macro helper. `env` is the macro's `&env` value. Returns true when expanding a macro while compiling CLJS."
;     [env]
;     (boolean (:ns env))))

;#?(:clj (log/debug "LOOKUP FROM CLJ!"))
;
;#?(:clj (defn templ [sym name k query]
;          `(~'defsc ~sym ~'[this {:keys [ui/msg ui/name] :as props}]
;             {:ident         [~k :db/id]
;              :query         ~query
;              :initial-state {:db/id "" :ui/msg "hello" :ui/name ~name}}
;             ~'(do
;                 (log/debug (str "RENDER " name))
;                 (div (str msg " from the " name " component"))))))
;
;#?(:clj (defmacro load-defsc [sym]
;          (when (cljs? &env)
;            (let [nm   (name sym)
;                  nm-k (keyword ":entity.ns" nm)]
;              (if-let [spec (get specs nm-k)]
;                (do
;                  (log/debug "emitting defsc for" sym)
;                  (let [{:entity/keys [attrs]} spec
;                        aks   (mapv :attr/key attrs)
;                        ns-k  (keyword "riverdb.entity.ns" nm)
;                        query (into [:ui/msg :ui/name :db/id :riverdb.entity/ns] aks)]
;                    (templ sym nm ns-k query)))
;                (log/warn "Error: no spec with key" nm-k))))))

;#?(:cljs (load-defsc agencylookup))
;#?(:cljs (load-defsc person))
;#?(:cljs (load-defsc projectslookup))

;#?(:clj (defmacro oh-yeah []
;          (when (cljs? &env)
;            (println "emitting do in CLJS context")
;            (cons 'do
;              (for [spec (specs)]
;                (let [{:entity/keys [ns name attrs]} spec
;                      aks   (mapv :attr/key attrs)
;                      sym   (symbol name)
;                      query (into [:db/id :riverdb.entity/ns] aks)]
;                  (println sym)
;                  `(defsc ~sym [this# props#] {:ident ~[ns :db/id] :query ~query})))))))

;#?(:clj (defmacro oh-yeah [sym ns query]
;          (when (cljs? &env)
;            (println "emitting do in CLJS context")
;            `(defsc ~sym [this# props#] {:ident ~[ns :db/id] :query ~query}))))
;
;#?(:clj (defmacro declare-inspire
;          "defs the supplied var names with no bindings, useful for making forward declarations."
;          {:added "1.0"}
;          [& names] `(do ~@(map #(list 'def (vary-meta % assoc :declared true)) names))))

;#?(:clj (defmacro oy []
;          (when (cljs? &env)
;            (println "emitting do in CLJS context")
;            (vec
;              (doall
;                (cons "do"
;                  (for [{:entity/keys [name attrs]} (take 5 (specs))]
;                    name
;                    #_(let [aks   (mapv :attr/key attrs)
;                            sym   (symbol name)
;                            ns    (keyword "org.riverdb.entity.ns" name)
;                            query (into [:db/id :riverdb.entity/ns :msg] aks)]
;                        (println sym)
;                        (str sym)
;                        #_`(com.fulcrologic.fulcro.components/defsc ~sym [this# props#]
;                             {:ident         [~ns :db/id]
;                              :query         ~query
;                              :initial-state {:db/id 1 :msg "hello"}}
;                             (let [msg# (get (com.fulcrologic.fulcro.components/props this#) :msg)]
;                               (com.fulcrologic.fulcro.dom/div (str msg# " from the macro"))))))))))))


;(defonce i (atom 0))
;(defn nam []
;  (str "mu" (swap! i inc)))

;#?(:clj (defmacro doy []
;          (when (cljs? &env)
;            (let [nm (nam)]
;             (println "trying to autogen")
;             (oy nm :comp/id [:db/id :comp/id :msg])))))


;#?(:clj (defmacro doit
;          (doall
;            (for [spec specs]
;              (let [sym (:entity/name spec)]
;                (println (oh-yeah hmm :comp/id [:comp/id])))))))




;#?(:clj
;   (defmacro generate-lookups
;     "Loads entity definitions from disk and generates basic stateful components for all the lookups"
;     [sym query ident]
;     (let [specs]
;       `(defsc ~sym '[this props]
;          {:query ~query
;           :ident ~ident}))))
