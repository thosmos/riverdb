(ns riverdb.html.layout
  "HTML layout helpers using Hiccup and Pico CSS.

  Pico is classless: it styles bare semantic elements, so the helpers here emit
  <label>, <input>, <select>, <table> and friends with almost no classes. Its
  entire vocabulary is 15 classes plus a few ARIA hooks — `container`, `grid`,
  `secondary`, `outline`, `contrast`, `striped`, `overflow-auto`,
  role=\"group\", role=\"switch\", aria-current, aria-invalid, data-theme."
  (:require [clojure.string :as str]
            [dotenv]
            [hiccup.page :refer [html5 include-css include-js]]
            [starfederation.datastar.clojure.api :as d*]))

;; ---------------------------------------------------------------------------
;; Front-end assets
;;
;; Every slot is overridable by env var so a different CSS layer or a licensed
;; Datastar build can be swapped in WITHOUT committing it. Datastar Pro and
;; Stellar CSS may not be redistributed — "making the software available in a
;; public repo ... is strictly prohibited" — and this repo is public and
;; AGPL-3.0, so those files must never be added to it. Drop them in
;; resources/public/vendor/ (gitignored) and point the env vars at them.
;;
;; Set a slot to "" to omit its tag entirely.
;; ---------------------------------------------------------------------------

(defn- env-or [k default]
  (let [v (dotenv/env k)]
    (if (some? v) v default)))

;; Default to the SDK's own CDN-url constant rather than a hardcoded version.
;; The Clojure SDK and the JS bundle version independently (SDK 1.0.0-RC11
;; pairs with bundle v1.0.2), so hardcoding a URL drifts silently; taking it
;; from the SDK means a deps.edn bump moves the client too.
(def datastar-js (env-or :DATASTAR_JS d*/CDN-url))

(def ui-css
  ;; Pico CSS, MIT — AGPL-compatible, no build step, no JS.
  (env-or :UI_CSS "https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css"))

(def ui-js
  ;; Pico needs no JavaScript. Kept as a slot for CSS systems that do.
  (env-or :UI_JS ""))

(def utility-css
  ;; A token system or compiled utility build, if one is ever wanted alongside
  ;; Pico. Empty by default: Pico plus semantic markup covers this app, which
  ;; is what lets us drop Tailwind's Play CDN — a dev-only tool that compiled
  ;; in the browser and was never safe to ship.
  (env-or :UTILITY_CSS ""))

(def utility-js (env-or :UTILITY_JS ""))

(defn base-layout
  [{:keys [title head-extra]} & body]
  (html5
    {:lang "en"}
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title (or title "RiverDB")]
     (when (seq ui-css) (include-css ui-css))
     ;; App styles: committed, ours, and deliberately short. Loaded after the
     ;; UI framework so it can override.
     (include-css "/css/app.css")
     (when (seq utility-css) (include-css utility-css))
     (when (seq ui-js) (include-js ui-js))
     (when (seq utility-js) (include-js utility-js))
     head-extra]
    [:body
     body
     (when (seq datastar-js)
       [:script {:src datastar-js :type "module" :defer true}])]))

;; ---------------------------------------------------------------------------
;; Structure
;; ---------------------------------------------------------------------------

(defn container
  "Wide container: this is a data app, and .container caps at 950px which is
  too narrow for the site visit table."
  [& content]
  [:main.container-fluid content])

(defn nav-items
  "The app's nav, with `active` marking the current page."
  [active]
  (for [[k href label] [[:home "/" "Home"]
                        [:sitevisits "/sitevisits" "Site Visits"]
                        [:about "/about" "About"]]]
    {:href href :label label :active (= k active)}))

(defn nav
  "Pico styles <nav> containing <ul> lists, spacing them apart."
  [{:keys [brand items]}]
  [:nav.container-fluid
   [:ul [:li [:a {:href "/"} [:strong (or brand "RiverDB")]]]]
   [:ul
    (for [{:keys [href label active]} items]
      [:li [:a (cond-> {:href href}
                 active (assoc :aria-current "page"))
            label]])]])

(defn card
  "Pico styles <article> as a card, with optional <header>."
  [{:keys [title subtitle]} & content]
  [:article
   (when (or title subtitle)
     [:header
      (when title [:strong title])
      (when subtitle [:p subtitle])])
   content])

(defn alert
  "Pico has no alert component; an <article> reads as a panel. `variant`
  :warning marks it with aria-invalid, which Pico colours as an error."
  [{:keys [variant]} & content]
  [:article (when (= "warning" (name (or variant ""))) {:aria-invalid "true"})
   content])

(defn table
  [{:keys [headers rows striped]}]
  [:div.overflow-auto
   [:table (when striped {:class "striped"})
    [:thead [:tr (for [h headers] [:th {:scope "col"} h])]]
    [:tbody (for [row rows] [:tr (for [cell row] [:td cell])])]]])

;; ---------------------------------------------------------------------------
;; Datastar form controls
;;
;; Each control binds to a signal by name via data-bind, so the whole form's
;; state lives in one signals object that gets POSTed back on save. Labels wrap
;; their input, which is Pico's idiom and means no id bookkeeping.
;; ---------------------------------------------------------------------------

(defn text-field
  [{:keys [label signal type placeholder value] :or {type "text"}}]
  [:label label
   [:input (cond-> {:type type :data-bind signal}
             placeholder   (assoc :placeholder placeholder)
             (some? value) (assoc :value (str value)))]])

(defn date-field
  [{:keys [label signal value]}]
  (text-field {:label label :signal signal :type "date" :value value}))

(defn select-field
  "Single select. `value` is the stored value, rendered as `selected` so the
  page is correct even before Datastar hydrates the signal."
  [{:keys [label signal options value placeholder] :or {placeholder "-- none --"}}]
  (let [cur (str value)]
    [:label label
     [:select {:data-bind signal}
      [:option {:value "" :selected (str/blank? cur)} placeholder]
      (for [opt options]
        [:option {:value (:value opt) :selected (= (:value opt) cur)}
         (:label opt)])]]))

(defn multi-select-field
  [{:keys [label signal options values size] :or {size 8}}]
  (let [selected (set (map str values))]
    [:label label
     [:select {:data-bind signal :multiple true :size size}
      (for [opt options]
        [:option {:value (:value opt) :selected (contains? selected (:value opt))}
         (:label opt)])]]))

(defn textarea-field
  [{:keys [label signal rows value] :or {rows 4}}]
  [:label label
   [:textarea {:data-bind signal :rows rows} (str value)]])

(defn checkbox-field
  "role=\"switch\" makes Pico render this as a toggle rather than a checkbox,
  which matches what the Fulcro form used for Publish."
  [{:keys [label signal checked]}]
  [:label
   [:input (cond-> {:type "checkbox" :role "switch" :data-bind signal}
             checked (assoc :checked true))]
   label])
