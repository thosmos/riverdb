(ns riverdb.html.handlers
  "Ring handlers for the server-rendered HTML app.

  Plain functions of a ring request. SSE responses come from the first-party
  http-kit adapter, which returns an ordinary ring response map, so nothing
  here needs to know how the server is wired."
  (:require
    [cheshire.core :as json]
    [clojure.tools.logging :refer [debug]]
    [hiccup.core :refer [html]]
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

(def ^:private signals-in-query?
  "Datastar sends signals as the `datastar` query param for methods that don't
  carry a body, and as a JSON body for the rest. Its own source is explicit:

      ot = e => ![\"GET\",\"DELETE\"].includes(e)
      ot(method) ? body = payload : params.set(\"datastar\", payload)

  Note DELETE is in that list. The SDK's get-signals only special-cases GET,
  which is why this is spelled out here: reading the body on a DELETE yields
  nothing, and a handler that diffs against the current selection would treat
  it as empty and wipe everything."
  #{:get :delete})

(defn raw-signals
  "The signals Datastar sent with this request, parsed with keyword keys."
  [{:keys [request-method query-params body] :as _request}]
  (try
    (if (contains? signals-in-query? request-method)
      (some-> (get query-params "datastar") (json/parse-string true))
      (some-> body slurp not-empty (json/parse-string true)))
    (catch Exception e
      (debug "SIGNALS PARSE FAILED" (.getMessage e))
      nil)))

(defn- eid
  "The malli-coerced :id, already an int by the time a handler runs."
  [request]
  (get-in request [:parameters :path :id]))

(defn- sse
  "Run `f` against an open SSE generator and close it."
  [request f]
  (->sse-response request
    {on-open (fn [gen] (d*/with-open-sse gen (f gen)))
     on-exception (fn [_gen e _opts] (debug "SSE EXCEPTION" (ex-message e)))}))

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

;; ---------------------------------------------------------------------------
;; Cardinality-many ref fields
;;
;; One registry per page; riverdb.html.ref-list supplies the mechanism. Adding
;; another many-ref here is a config entry, not new handlers.
;; ---------------------------------------------------------------------------

(def ref-fields
  {:monitors
   {:field    :monitors
    :signal   "Visitors"
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
                   [:sitevisit/AgencyCode :db/id]))}))

(def search-ref  (:search  ref-handlers))
(def add-ref     (:add     ref-handlers))
(def add-top-ref (:add-top ref-handlers))
(def remove-ref  (:remove  ref-handlers))

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
        (let [{:keys [stations people visit-types fail-codes]} (sv/form-options (state/db) sv-)
              signals (assoc (sv/sitevisit->signals sv-)
                                :VisitorsQuery "")]
          (ok (layout/base-layout
                {:title (str "Site Visit " (:sitevisit/SiteVisitID sv-))}
                (layout/nav {:brand "RiverDB" :items (layout/nav-items :sitevisits)})
                (layout/container
                  [:div {:data-signals (json/encode signals)}
                   [:hgroup
                    [:h1 "Site Visit " (:sitevisit/SiteVisitID sv-)]
                    [:p [:a {:href "/sitevisits"} "\u2190 Back to list"]]]

                   ;; Layout mirrors the existing Fulcro form: a dense
                   ;; three-across field grid on the left, monitors on the
                   ;; right, notes full width beneath.
                   [:div.form-layout
                    [:div.field-grid
                     (layout/select-field
                       {:label "Station" :signal "StationID" :options stations
                        :value (:StationID signals)})
                     (layout/date-field {:label "Site Visit Date" :signal "SiteVisitDate"
                                         :value (:SiteVisitDate signals)})
                     (layout/text-field {:label "Start Time" :signal "Time" :placeholder "e.g. 09:30"
                                         :value (:Time signals)})
                     (layout/select-field
                       {:label "Visit Type" :signal "VisitType" :options visit-types
                        :value (:VisitType signals)})
                     (layout/select-field
                       {:label "Failure?" :signal "StationFailCode" :options fail-codes
                        :value (:StationFailCode signals)})
                     [:div]
                     (layout/select-field
                       {:label "Entered By" :signal "DataEntryPersonRef" :options people
                        :value (:DataEntryPersonRef signals)})
                     (layout/date-field {:label "Data Entry Date" :signal "DataEntryDate"
                                         :value (:DataEntryDate signals)})
                     (layout/select-field
                       {:label "Checked By" :signal "CheckPersonRef" :options people
                        :value (:CheckPersonRef signals)})
                     (layout/select-field
                       {:label "QA'd By" :signal "QAPersonRef" :options people
                        :value (:QAPersonRef signals)})
                     (layout/date-field {:label "QA Date" :signal "QADate"
                                         :value (:QADate signals)})
                     (layout/checkbox-field {:label "Publish?" :signal "QACheck"
                                             :checked (:QACheck signals)})]

                    [:div.rail
                     (let [cfg (:monitors ref-fields)]
                       (rl/render (base-url id) cfg
                         {:selected (rl/options-for (state/db) cfg
                                      ((:read cfg) sv-))
                          :matches  nil
                          :query    ""}))
                     [:small (count (:sitevisit/Samples sv-)) " samples on this visit "
                      "(not editable in this proof of concept)"]]]

                   (layout/textarea-field {:label "Notes" :signal "Notes" :rows 3
                                           :value (:Notes signals)})

                   [:div.actions
                    [:button {:data-on:click (str "@post('/sitevisit/" id "/save')")}
                     "Save"]
                    [:button.secondary
                     {:data-on:click (str "@get('/sitevisit/" id "/reload')")}
                     "Revert"]]

                   [:div {:id "sv-status"}]

                   ;; Debug panel: the form's whole Datastar signals object, live.
                   ;; A bare $ is the signals root inside a Datastar expression;
                   ;; $Foo is sugar for $['Foo']. Keep this last in the container:
                   ;; a bad expression aborts Datastar's init for every element
                   ;; after it in document order.
                   [:details
                    [:summary "Live signals"]
                    [:pre [:code {:data-text "JSON.stringify($, null, 2)"}]]]]))))))))


(defn save-sitevisit
  "Validate the incoming signals, transact the diff, then push a status element
  and the freshly-read signals back so the form shows what Datomic stored."
  [request]
  (let [id (eid request)
        {:keys [value error]} (sc/decode sc/SiteVisitSignals (or (raw-signals request) {}))
        result (cond
                 error {:status :invalid :error error}
                 :else (sv/save-sitevisit! id value))]
    (debug "SAVE SITEVISIT" id (:status result))
    (sse request
      (fn [gen]
        (d*/patch-elements! gen (str (html (status-el result))))
        (when-let [sv- (sv/pull-sitevisit id)]
          (d*/patch-signals! gen (json/encode (sv/sitevisit->signals sv-)))
          (rl/patch-stored-chips! gen (base-url id) (state/db) ref-fields sv-))))))

(defn reload-sitevisit
  "Push the stored values back into the signals, discarding local edits."
  [request]
  (let [id  (eid request)
        sv- (sv/pull-sitevisit id)]
    (sse request
      (fn [gen]
        (if sv-
          (do
            (d*/patch-signals! gen (json/encode (assoc (sv/sitevisit->signals sv-)
                                                  :VisitorsQuery "")))
            (rl/patch-stored-chips! gen (base-url id) (state/db) ref-fields sv-)
            (d*/patch-elements! gen (str (html [:div {:id "sv-status"}
                                                [:article "Reverted to stored values."]]))))
          (d*/patch-elements! gen (str (html (status-el {:status :missing})))))))))
