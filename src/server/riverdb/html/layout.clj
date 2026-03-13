(ns riverdb.html.layout
  "HTML layout helpers using Hiccup and Basecoat UI components"
  (:require [hiccup.page :refer [html5 include-css include-js]]
            [hiccup.core :refer [html]]))

(def basecoat-cdn "https://cdn.jsdelivr.net/npm/basecoat@0.7.5/dist/basecoat.min.css")
(def datastar-cdn "https://cdn.jsdelivr.net/npm/@sudodevnull/datastar@0.20.3/dist/datastar.js")

(defn base-layout
  "Base HTML layout with Basecoat CSS and Datastar JS"
  [{:keys [title head-extra]} & body]
  (html5
    {:lang "en"}
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title (or title "RiverDB HTML App")]
     (include-css basecoat-cdn)
     head-extra]
    [:body
     body
     [:script {:src datastar-cdn :type "module" :defer true}]]))

(defn container
  "Basecoat container component"
  [& content]
  [:div.container content])

(defn card
  "Basecoat card component"
  [{:keys [title subtitle]} & content]
  [:div.card
   (when (or title subtitle)
     [:div.card-header
      (when title [:h3.card-title title])
      (when subtitle [:p.card-subtitle subtitle])])
   [:div.card-body content]])

(defn button
  "Basecoat button component"
  [{:keys [variant size class] :or {variant "primary"}} label]
  [:button
   {:class (str "btn btn-" variant
                (when size (str " btn-" size))
                (when class (str " " class)))}
   label])

(defn input
  "Basecoat input component"
  [{:keys [type id name placeholder value label class]
    :or {type "text"}}]
  [:div.form-group
   (when label
     [:label {:for id} label])
   [:input.form-control
    (merge
      {:type type}
      (when id {:id id})
      (when name {:name name})
      (when placeholder {:placeholder placeholder})
      (when value {:value value})
      (when class {:class class}))]])

(defn table
  "Basecoat table component"
  [{:keys [headers rows striped hover]}]
  [:table
   {:class (str "table"
                (when striped " table-striped")
                (when hover " table-hover"))}
   [:thead
    [:tr
     (for [header headers]
       [:th header])]]
   [:tbody
    (for [row rows]
      [:tr
       (for [cell row]
         [:td cell])])]])

(defn alert
  "Basecoat alert component"
  [{:keys [variant dismissible]} & content]
  [:div
   {:class (str "alert alert-" (or variant "info")
                (when dismissible " alert-dismissible"))}
   content
   (when dismissible
     [:button.btn-close {:type "button" :data-dismiss "alert"}])])

(defn badge
  "Basecoat badge component"
  [{:keys [variant]} label]
  [:span {:class (str "badge badge-" (or variant "primary"))} label])

(defn nav
  "Basecoat navigation component"
  [{:keys [brand items]}]
  [:nav.navbar.navbar-expand-lg
   [:div.container-fluid
    (when brand
      [:a.navbar-brand {:href "/"} brand])
    [:div.navbar-nav
     (for [{:keys [href label active]} items]
       [:a.nav-link
        {:href href
         :class (when active "active")}
        label])]]])
