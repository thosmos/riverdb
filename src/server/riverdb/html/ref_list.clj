(ns riverdb.html.ref-list
  "Reusable editor for a cardinality-many ref: chips plus a type-ahead.

  What this abstracts is the *mechanism* — the endpoint set, the
  patch-on-every-change discipline, and the Datastar gotchas that made every
  bug in the first implementation. The *semantics* stay in each field's config:
  where candidates come from, how they're ranked and labelled, what scopes the
  search. Those differ per field and are where the product thinking lives.

  A field config is a map:

    :field       keyword, unique per page. Names the route segment and the
                 element ids, so several of these can coexist.
    :signal      signal holding the selection, e.g. \"Visitors\"
    :label       display label
    :placeholder input placeholder
    :search      (fn [db scope query exclude limit] -> [{:value :label}])
                 `exclude` is a set of already-chosen eids. Ranking is the
                 field's business.
    :label-of    (fn [db eid] -> string) for rendering a chip
    :read        (fn [entity] -> [eid ...]) pulling the stored value, used to
                 re-render chips on revert/save

  Selection lives in the Datastar signal, so handlers are stateless: read the
  list the browser sent, compute the new one, patch it back."
  (:require
    [clojure.string :as str]
    [hiccup.core :refer [html]]
    [riverdb.html.datastar :as ds]
    [starfederation.datastar.clojure.api :as d*]))

;; ---------------------------------------------------------------------------
;; naming
;; ---------------------------------------------------------------------------

(defn chips-id [{:keys [field]}] (str "chips-" (name field)))
(defn menu-id  [{:keys [field]}] (str "menu-"  (name field)))

(defn query-signal
  "Signal holding this field's type-ahead text. Must be declared in whatever
  malli schema validates the save payload: Datastar sends every signal on
  every request, and a closed schema rejects undeclared keys."
  [{:keys [signal]}]
  (str signal "Query"))

(defn- member-url [base {:keys [field]} eid]
  (str base "/ref/" (name field) (when eid (str "/" eid))))

;; ---------------------------------------------------------------------------
;; markup
;; ---------------------------------------------------------------------------

(defn- chip [base cfg {:keys [value label]}]
  [:span.chip
   label
   [:button.chip-x
    {:type "button"
     :aria-label (str "Remove " label)
     :data-on:click (str "@delete('" (member-url base cfg value) "')")}
    "×"]])

(defn chips
  "The selected values. Server-rendered, so it does NOT self-heal when the
  signal is patched — every handler that changes the selection must re-render
  this. See patch-chips!."
  [base cfg options]
  [:div {:id (chips-id cfg) :class "chips"}
   (for [o options] (chip base cfg o))])

(defn menu
  "Type-ahead results. Hidden by data-show when the query is empty, so an
  outside click can dismiss it without a round trip."
  [base cfg matches]
  [:div {:id (menu-id cfg) :class "menu"
         :data-show (str "$" (query-signal cfg) ".length > 0")}
   (if (seq matches)
     (for [m matches]
       [:button.menu-item
        {:type "button"
         :data-on:click (str "@post('" (member-url base cfg (:value m)) "')")}
        (:label m)])
     [:div.menu-empty "No matches"])])

(defn render
  "The whole field. Only the chips and menu are ever patched, so the input
  keeps focus while you type."
  [base cfg {:keys [selected matches query]}]
  (let [q (query-signal cfg)]
    [:div.ref-list
     {:data-on:click__outside (str "$" q " = ''")}
     [:span.field-label (:label cfg)]
     [:div.ref-list-box
      (chips base cfg selected)
      [:input.ref-list-input
       {:type "text"
        :placeholder (or (:placeholder cfg) "type to add…")
        :autocomplete "off"
        :value (or query "")
        :data-bind q
        :data-on:input__debounce.250ms (str "@get('" (member-url base cfg nil) "')")
        ;; Enter posts to the collection, so the server decides the top match
        ;; and the client never has to track ids.
        :data-on:keydown (str "evt.key === 'Enter' && (evt.preventDefault(), "
                              "@post('" (member-url base cfg nil) "'))")}]
      (menu base cfg matches)]]))

;; ---------------------------------------------------------------------------
;; patching
;; ---------------------------------------------------------------------------

(defn options-for [db cfg eids]
  (vec (for [e eids]
         {:value (str e) :label (or ((:label-of cfg) db e) (str e))})))

(defn patch-chips!
  "Re-render one field's chips. Call from ANY handler that changes or restores
  the selection — add, remove, revert, save. A data-bind input updates itself
  when its signal is patched; this fragment does not."
  [gen base db cfg eids]
  (d*/patch-elements! gen (str (html (chips base cfg (options-for db cfg eids))))))

(defn patch-stored-chips!
  "Re-render every registered field from the stored entity. For revert/save."
  [gen base db registry entity]
  (doseq [cfg (vals registry)]
    (patch-chips! gen base db cfg ((:read cfg) entity))))

(defn- patch-selection! [gen base db cfg eids]
  (patch-chips! gen base db cfg eids)
  (d*/patch-elements! gen (str (html (menu base cfg nil))))
  (ds/patch-signals! gen {(keyword (:signal cfg))      (mapv str eids)
                          (keyword (query-signal cfg)) ""})
  ;; Return focus so the next value can be typed without reaching for the mouse.
  (d*/execute-script! gen
    (str "document.querySelector('#" (chips-id cfg)
         "')?.parentElement?.querySelector('.ref-list-input')?.focus()")))

;; ---------------------------------------------------------------------------
;; handlers
;; ---------------------------------------------------------------------------

(defn- ->eid [v] (some-> v str str/trim not-empty parse-long))

(defn- read-state
  "Everything a handler needs, from ONE read of the request body."
  [request registry]
  (let [field   (keyword (get-in request [:parameters :path :field]))
        cfg     (get registry field)
        signals (or (ds/raw-signals request) {})]
    (when cfg
      {:cfg    cfg
       :chosen (vec (keep ->eid (get signals (keyword (:signal cfg)))))
       :query  (str (get signals (keyword (query-signal cfg)) ""))})))

(defn make-handlers
  "Ring handlers for a registry of ref-list fields.

  `base` and `scope` are functions of the request, so the same widget serves
  /sitevisit/:id today and a generic /entity/:ns/:id later:

    :base   (fn [request] -> \"/sitevisit/42\")
    :scope  (fn [db request] -> value passed to each field's :search)
    :db     (fn [] -> a Datomic db value)"
  [{:keys [registry base scope db max-matches] :or {max-matches 8}}]
  (letfn [(add [request state person]
            (let [{:keys [cfg chosen]} state
                  eids (if (and person (not (some #{person} chosen)))
                         (conj (vec chosen) person)
                         (vec chosen))]
              (ds/sse request
                (fn [gen] (patch-selection! gen (base request) (db) cfg eids)))))]
    {:search
     (fn [request]
       (let [{:keys [cfg chosen query] :as state} (read-state request registry)
             d (db)]
         (if-not state
           {:status 404 :body "unknown ref field"}
           (let [matches ((:search cfg) d (scope d request) query (set chosen) max-matches)]
             (ds/sse request
               (fn [gen]
                 (d*/patch-elements! gen
                   (str (html (menu (base request) cfg matches))))))))))

     :add
     (fn [request]
       (if-let [state (read-state request registry)]
         (add request state (get-in request [:parameters :path :member]))
         {:status 404 :body "unknown ref field"}))

     :add-top
     (fn [request]
       (if-let [{:keys [cfg chosen query] :as state} (read-state request registry)]
         (let [d   (db)
               top (some-> ((:search cfg) d (scope d request) query (set chosen) 1)
                     first :value ->eid)]
           (add request state top))
         {:status 404 :body "unknown ref field"}))

     :remove
     (fn [request]
       (if-let [{:keys [cfg chosen]} (read-state request registry)]
         (let [person (get-in request [:parameters :path :member])
               eids   (vec (remove #{person} chosen))]
           (ds/sse request
             (fn [gen] (patch-selection! gen (base request) (db) cfg eids))))
         {:status 404 :body "unknown ref field"}))}))
