(ns riverdb.html.handlers
  "Ring handlers for the server-rendered HTML app.

  Plain functions of a ring request. SSE responses come from the first-party
  http-kit adapter, which returns an ordinary ring response map, so nothing
  here needs to know how the server is wired."
  (:require
    [cheshire.core :as json]
    [clojure.tools.logging :refer [debug]]
    [hiccup.core :refer [html]]
    [riverdb.html.datastar :as ds]
    [riverdb.html.fieldmeasure :as fm]
    [riverdb.html.fieldobs :as fo]
    [riverdb.html.layout :as layout]
    [riverdb.html.ref-list :as rl]
    [riverdb.html.schema :as sc]
    [riverdb.html.sitevisit :as sv]
    [riverdb.state :as state]
    [starfederation.datastar.clojure.api :as d*]
    [starfederation.datastar.clojure.adapter.http-kit :as hk-sse :refer [->sse-response on-open on-exception]]))

;; ---------------------------------------------------------------------------
;; plumbing
;; ---------------------------------------------------------------------------

(defn- page [status body]
  {:status status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body body})

(defn ok [body] (page 200 body))

(defn- eid
  "The malli-coerced :id, already an int by the time a handler runs."
  [request]
  (get-in request [:parameters :path :id]))

(defn- base-url [id] (str "/sitevisit/" id))

(defn- status-el [{:keys [status tx error]}]
  [:div {:id "sv-status"}
   (case status
     :saved     [:article
                 [:strong "Saved. "] (count tx) " datom(s) transacted."
                 [:pre [:code (pr-str tx)]]]
     :unchanged [:article "No changes to save."]
     :missing   [:article {:aria-invalid "true"} "That site visit no longer exists."]
     :invalid   [:article {:aria-invalid "true"}
                 [:strong "Rejected at the edge: "]
                 [:pre [:code (pr-str error)]]]
     :error     [:article {:aria-invalid "true"}
                 [:strong "Save failed: "] (str error)]
     nil)])

(def dirty-signal
  "Local-only signal driving the Save and Revert buttons. The leading
  underscore keeps Datastar from sending it: it is browser state, and a closed
  save schema would reject it.

  It is set by any input or change inside the form, and by chip add/remove
  which are round trips rather than input events. It is NOT a true diff
  against pristine values -- typing a value and typing it back leaves the form
  marked dirty. That is deliberate: the button is an affordance, and the server
  still diffs on save, so a no-op save reports \"No changes to save\"."
  :_dirty)

;; ---------------------------------------------------------------------------
;; Cardinality-many ref fields
;;
;; One registry per page; riverdb.html.ref-list supplies the mechanism. Adding
;; another many-ref here is a config entry, not new handlers.
;; ---------------------------------------------------------------------------

(def ref-fields
  {:monitors
   {:field    :monitors
    :attr     :sitevisit/Visitors
    :label    "Monitors"
    :placeholder "type to add\u2026"
    :read     #(mapv :db/id (:sitevisit/Visitors %))
    :label-of sv/person-name
    :search   (fn [db agency query exclude limit]
                (sv/search-people db agency query exclude limit))}})

(def ^:private ref-handlers
  (rl/make-handlers
    {:registry ref-fields
     :db       #(state/db)
     :base     (fn [request] (base-url (eid request)))
     ;; People are scoped to the visit's agency.
     :scope    (fn [db request]
                 (get-in (sv/pull-sitevisit db (eid request))
                   [:sitevisit/AgencyCode :db/id]))
     ;; Adding or removing a chip is an edit, but it arrives as a round trip
     ;; rather than an input event, so it has to say so explicitly.
     :touch    {dirty-signal true}}))

(def search-ref  (:search  ref-handlers))
(def add-ref     (:add     ref-handlers))
(def add-top-ref (:add-top ref-handlers))
(def remove-ref  (:remove  ref-handlers))

;; ---------------------------------------------------------------------------
;; Field measurements
;; ---------------------------------------------------------------------------

(defn- fm-params [db sv-]
  (fm/params db (get-in sv- [:sitevisit/ProjectID :db/id])))

(defn- fo-params [db sv-]
  (fo/params db (get-in sv- [:sitevisit/ProjectID :db/id])))

(defn- clean-signals
  "Signals that mark the form as saved/reverted."
  []
  {dirty-signal false})

(defn page-signals
  "Every signal the site visit page holds: the entity's own attributes, the
  field measurement grid, and the UI-only type-ahead queries.

  Used by the initial render, by save and by revert, so the three cannot
  disagree about what the page should contain — forgetting the grid here is
  how revert silently left edited readings on screen."
  [db sv-]
  (merge-with merge
    (sv/sitevisit->signals sv-)
    (fm/->signals (fm-params db sv-) (fm/samples-by-param sv-))
    (fo/->signals (fo-params db sv-) (fo/samples-by-param sv-) (fo/options-by-analyte db))
    (sc/signals-for {(rl/query-key (:monitors ref-fields)) ""})
    {dirty-signal false}))

(defn- device-lookups [db sv-]
  {:device-types (fm/device-types db)
   :devices      (fm/devices-by-type db (get-in sv- [:sitevisit/AgencyCode :db/id]))})

(defn change-device
  "Device type changed on one row: re-offer that type's instruments, and drop
  the current instrument if it doesn't belong to the new type."
  [request]
  (let [id      (eid request)
        param-e (get-in request [:parameters :path :param])
        signals (or (ds/raw-signals request) {})
        db      (state/db)
        sv-     (sv/pull-sitevisit db id)
        param   (first (filter #(= param-e (:db/id %)) (fm-params db sv-)))
        {:keys [devices]} (device-lookups db sv-)
        chosen  (some-> (not-empty (str (fm/attr-value signals fm/devtype-attr param-e)))
                  parse-long)
        opts    (get devices chosen)
        cur-id  (str (fm/attr-value signals fm/devid-attr param-e))
        valid?  (some #(= cur-id (:value %)) opts)
        signals (cond-> signals
                  (not valid?) (assoc-in (conj (vec (sc/signal-path fm/devid-attr))
                                           (fm/row-key param-e)) ""))]
    (ds/sse request
      (fn [gen]
        (when param
          (d*/patch-elements! gen (str (html (fm/devid-cell param signals opts))))
          (when-not valid?
            (ds/patch-signals! gen
              (sc/signals-for {fm/devid-attr {(fm/row-key param-e) ""}}))))))))

(defn recompute-stats
  "Recompute one row's derived columns from the signals the browser sent and
  patch just those cells. The replicate inputs are never touched, so focus
  stays where the user is typing."
  [request]
  (let [id      (eid request)
        param-e (get-in request [:parameters :path :param])
        signals (or (ds/raw-signals request) {})
        db      (state/db)
        sv-     (sv/pull-sitevisit db id)
        param   (first (filter #(= param-e (:db/id %)) (fm-params db sv-)))]
    (ds/sse request
      (fn [gen]
        (when param
          (d*/patch-elements-seq! gen
            (map (fn [el] (str (html el)))
              (fm/stat-cells param (fm/stats param (fm/row-values signals param))))))))))

;; ---------------------------------------------------------------------------
;; static pages
;; ---------------------------------------------------------------------------

(defn home-page [_]
  (ok (layout/base-layout
        {:title "RiverDB HTML App"}
        (layout/nav {:brand "RiverDB" :items (layout/nav-items :home)})
        (layout/container
          [:div
           (layout/card
             {:title "Welcome to RiverDB HTML App"
              :subtitle "Server-side rendered with Datastar interactivity"}
             [:p "A purely server-side HTML application using:"]
             [:ul
              [:li "Hiccup for HTML generation"]
              [:li "Pico CSS for classless, semantic styling"]
              [:li "Datastar for hypermedia-driven interactivity"]
              [:li "http-kit + reitit, with malli validating the edge"]]
             [:a {:href "/sitevisits" :role "button"} "Site Visits"])]))))

(defn about-page [_]
  (ok (layout/base-layout
        {:title "About - RiverDB HTML App"}
        (layout/nav {:brand "RiverDB" :items (layout/nav-items :about)})
        (layout/container
          [:div
           (layout/card
             {:title "About This App"}
             [:p "Server-side HTML rendering with hypermedia interactivity."]
             [:p "Clojure, http-kit, reitit, malli, Hiccup, Pico CSS, Datastar."])]))))

(defn not-found [_]
  (page 404
    (layout/base-layout
      {:title "Not Found"}
      (layout/nav {:brand "RiverDB" :items (layout/nav-items nil)})
      (layout/container
        [:div
         (layout/alert {:variant "warning"}
           [:h4 "Page Not Found"]
           [:p "The page you're looking for doesn't exist."])
         [:a {:href "/" :role "button"} "Go Home"]]))))

(defn- db-down-page [title]
  (page 503
    (layout/base-layout
      {:title title}
      (layout/nav {:brand "RiverDB" :items (layout/nav-items :sitevisits)})
      (layout/container
        [:div
         (layout/alert {:variant "warning"}
           [:h4 "No database connection"]
           [:p "The Datomic connection hasn't been started. Run "
            [:code "(riverdb.state/start-dbs)"]
            " or start the main server on 8989, then reload."])]))))

;; ---------------------------------------------------------------------------
;; site visits
;; ---------------------------------------------------------------------------

(defn sitevisits-page
  "List the most recent site visits; the whole row links to the form."
  [_request]
  (if-not (sv/db-ready?)
    (db-down-page "Site Visits")
    (let [visits (sv/recent-sitevisits 50)]
      (ok (layout/base-layout
            {:title "Site Visits"}
            (layout/nav {:brand "RiverDB" :items (layout/nav-items :sitevisits)})
            (layout/container
              [:div
               [:h1 "Site Visits"]
               [:div.overflow-auto
                [:table.striped
                 [:thead
                  [:tr [:th {:scope "col"} "Date"] [:th {:scope "col"} "Station"]
                   [:th {:scope "col"} "Project"] [:th {:scope "col"} "SVID"]
                   [:th {:scope "col"} "Published"]]]
                 [:tbody
                  (for [{:keys [db/id] :as v} visits]
                    ;; An <a> can't wrap a <tr>, so every cell carries the same
                    ;; link. The whole row is the affordance; no second target.
                    (let [href (str "/sitevisit/" id)
                          cell (fn [content] [:td [:a {:href href} content]])]
                      [:tr
                       (cell (sc/date->wire (:sitevisit/SiteVisitDate v)))
                       (cell (sv/station-label (:sitevisit/StationID v)))
                       (cell (get-in v [:sitevisit/ProjectID :projectslookup/ProjectID]))
                       (cell (:sitevisit/SiteVisitID v))
                       (cell (if (:sitevisit/QACheck v) "yes" "no"))]))]]]
               (when (empty? visits)
                 [:p "No site visits found."])]))))))

(defn sitevisit-page
  "The form. All field state lives in one Datastar signals object."
  [request]
  (if-not (sv/db-ready?)
    (db-down-page "Site Visit")
    (let [id  (eid request)
          sv- (sv/pull-sitevisit id)]
      (if-not sv-
        (page 404
          (layout/base-layout
            {:title "Site Visit Not Found"}
            (layout/nav {:brand "RiverDB" :items (layout/nav-items :sitevisits)})
            (layout/container
              [:div
               (layout/alert {:variant "warning"} [:p "No such site visit."])
               [:a {:href "/sitevisits" :role "button"} "Back to list"]])))
        (let [db      (state/db)
              {:keys [stations people visit-types fail-codes]} (sv/form-options db sv-)
              fm-ps   (fm-params db sv-)
              signals (page-signals db sv-)]
          (ok (layout/base-layout
                {:title (str "Site Visit " (:sitevisit/SiteVisitID sv-))}
                (layout/nav {:brand "RiverDB" :items (layout/nav-items :sitevisits)})
                (layout/container
                  [:div {:data-signals (json/encode signals)
                         ;; Events bubble, so one handler covers every control.
                         ;; The type-ahead's own box is a search field, not a
                         ;; form edit, so it is excluded.
                         :data-on:input  (str "!evt.target.classList.contains('ref-list-input') && ($"
                                              (name dirty-signal) " = true)")
                         :data-on:change (str "$" (name dirty-signal) " = true")}
                   [:hgroup
                    [:h1 "Site Visit " (:sitevisit/SiteVisitID sv-)]
                    [:p [:a {:href "/sitevisits"} "\u2190 Back to list"]]]

                   ;; Layout mirrors the existing Fulcro form: a dense
                   ;; three-across field grid on the left, monitors on the
                   ;; right, notes full width beneath.
                   [:div.form-layout
                    [:div.field-grid
                     (layout/select-field
                       {:signals signals :label "Station" :attr :sitevisit/StationID :options stations})
                     (layout/date-field {:signals signals :label "Site Visit Date" :attr :sitevisit/SiteVisitDate})
                     (layout/text-field {:signals signals :label "Start Time" :attr :sitevisit/Time :placeholder "e.g. 09:30"})
                     (layout/select-field
                       {:signals signals :label "Visit Type" :attr :sitevisit/VisitType :options visit-types})
                     (layout/select-field
                       {:signals signals :label "Failure?" :attr :sitevisit/StationFailCode :options fail-codes})
                     [:div]
                     (layout/select-field
                       {:signals signals :label "Entered By" :attr :sitevisit/DataEntryPersonRef :options people})
                     (layout/date-field {:signals signals :label "Data Entry Date" :attr :sitevisit/DataEntryDate})
                     (layout/select-field
                       {:signals signals :label "Checked By" :attr :sitevisit/CheckPersonRef :options people})
                     (layout/select-field
                       {:signals signals :label "QA'd By" :attr :sitevisit/QAPersonRef :options people})
                     (layout/date-field {:signals signals :label "QA Date" :attr :sitevisit/QADate})
                     (layout/checkbox-field {:signals signals :label "Publish?" :attr :sitevisit/QACheck})]

                    [:div.rail
                     (let [cfg (:monitors ref-fields)]
                       (rl/render (base-url id) cfg
                         {:selected (rl/options-for (state/db) cfg ((:read cfg) sv-))
                          :matches  nil
                          :query    ""}))
                     [:small (count (:sitevisit/Samples sv-)) " samples on this visit"]]]

                   (layout/textarea-field {:signals signals :label "Notes" :attr :sitevisit/Notes :rows 3})

                   (fm/grid (base-url id) fm-ps signals (device-lookups db sv-))

                   (fo/section (fo-params db sv-) signals (fo/options-by-analyte db))

                   ;; Both disabled until something is edited; the server
                   ;; clears the flag again once a save or revert lands.
                   (let [clean (str "!$" (name dirty-signal))]
                     [:div.actions
                      [:button {:data-attr:disabled clean
                                :data-on:click (str "@post('/sitevisit/" id "/save')")}
                       "Save"]
                      [:button.secondary
                       {:data-attr:disabled clean
                        :data-on:click (str "@get('/sitevisit/" id "/reload')")}
                       "Revert"]])

                   [:div {:id "sv-status"}]

                   ;; Debug panel: the form's whole Datastar signals object, live.
                   ;; A bare $ is the signals root inside a Datastar expression;
                   ;; $Foo is sugar for $['Foo']. Keep this last in the container:
                   ;; a bad expression aborts Datastar's init for every element
                   ;; after it in document order.
                   [:details
                    [:summary "Live signals"]
                    [:pre [:code {:data-text "JSON.stringify($, null, 2)"}]]]]))))))))


(defn- save-with-grid!
  "Site visit diff plus the field measurement grid, in one transaction."
  [db id signals]
  (let [sv-  (sv/pull-sitevisit db id)
        grid (fm/grid-tx id (fm-params db sv-) (fm/samples-by-param sv-) signals)
        obs  (fo/tx id (fo-params db sv-) (fo/samples-by-param sv-) signals)]
    (sv/save-sitevisit! id signals (concat grid obs))))

(defn save-sitevisit
  "Validate the incoming signals, transact the diff, then push a status element
  and the freshly-read signals back so the form shows what Datomic stored."
  [request]
  (let [id (eid request)
        {:keys [value error]} (sc/decode sc/SiteVisitSignals (or (ds/raw-signals request) {}))
        result (cond
                 error {:status :invalid :error error}
                 :else (save-with-grid! (state/db) id value))]
    (debug "SAVE SITEVISIT" id (:status result))
    (ds/sse request
      (fn [gen]
        (d*/patch-elements! gen (str (html (status-el result))))
        (let [db (state/db)]
          (when-let [sv- (sv/pull-sitevisit db id)]
            (d*/patch-signals! gen (json/encode (page-signals db sv-)))
            (rl/patch-stored-chips! gen (base-url id) db ref-fields sv-)))))))

(defn reload-sitevisit
  "Push the stored values back into the signals, discarding local edits."
  [request]
  (let [id  (eid request)
        db  (state/db)
        sv- (sv/pull-sitevisit db id)]
    (ds/sse request
      (fn [gen]
        (if sv-
          (do
            (d*/patch-signals! gen (json/encode (page-signals db sv-)))
            (rl/patch-stored-chips! gen (base-url id) db ref-fields sv-)
            (d*/patch-elements! gen (str (html [:div {:id "sv-status"}
                                                [:article "Reverted to stored values."]]))))
          (d*/patch-elements! gen (str (html (status-el {:status :missing})))))))))
