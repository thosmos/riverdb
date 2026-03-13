(ns riverdb.html.handlers
  "HTTP handlers for server-side HTML app with Datastar"
  (:require [clojure.tools.logging :refer [debug]]
            [hiccup.core :refer [html]]
            [ring.util.response :as ring-resp]
            [riverdb.html.layout :as layout]))

(defn home-page
  "Home page handler"
  [request]
  (debug "HOME PAGE REQUEST")
  (ring-resp/response
    (layout/base-layout
      {:title "RiverDB HTML App"}
      (layout/nav {:brand "RiverDB"
                   :items [{:href "/" :label "Home" :active true}
                           {:href "/about" :label "About"}
                           {:href "/demo" :label "Demo"}]})
      (layout/container
        [:div.mt-4
         (layout/card
           {:title "Welcome to RiverDB HTML App"
            :subtitle "Server-side rendered with Datastar interactivity"}
           [:p "This is a purely server-side HTML application using:"]
           [:ul
            [:li "Hiccup for HTML generation"]
            [:li "Basecoat for UI components"]
            [:li "Datastar for hypermedia-driven interactivity"]]
           [:div.mt-3
            [:a.btn.btn-primary {:href "/demo"} "View Demo"]])]))))

(defn about-page
  "About page handler"
  [request]
  (ring-resp/response
    (layout/base-layout
      {:title "About - RiverDB HTML App"}
      (layout/nav {:brand "RiverDB"
                   :items [{:href "/" :label "Home"}
                           {:href "/about" :label "About" :active true}
                           {:href "/demo" :label "Demo"}]})
      (layout/container
        [:div.mt-4
         (layout/card
           {:title "About This App"}
           [:p "This application demonstrates server-side HTML rendering with modern interactivity."]
           [:p "Built with Clojure, Pedestal, Hiccup, Basecoat, and Datastar."])]))))

(defn demo-page
  "Demo page with Datastar interactivity"
  [request]
  (ring-resp/response
    (layout/base-layout
      {:title "Demo - RiverDB HTML App"}
      (layout/nav {:brand "RiverDB"
                   :items [{:href "/" :label "Home"}
                           {:href "/about" :label "About"}
                           {:href "/demo" :label "Demo" :active true}]})
      (layout/container
        [:div.mt-4
         (layout/card
           {:title "Counter Demo"
            :subtitle "Click the button to increment the counter"}
           [:div
            {:data-store "{count: 0}"}
            [:p.text-lg
             "Count: "
             [:span.badge.badge-primary
              {:data-text "$count"}
              "0"]]
            [:button.btn.btn-primary.mt-2
             {:data-on-click "$$get('/html/increment')"}
             "Increment"]])

         [:div.mt-4
          (layout/card
            {:title "Live Search Demo"
             :subtitle "Search updates as you type"}
            [:div
             {:data-store "{query: '', results: []}"}
             (layout/input
               {:id "search"
                :placeholder "Type to search..."
                :class "data-model-query data-on-input-$$get('/html/search')"})
             [:div.mt-3
              {:data-show "$results.length > 0"}
              [:h5 "Results:"]
              [:ul
               {:id "results"}
               [:template
                {:data-for "result in $results"}
                [:li {:data-text "$result"}]]]]])]]))))

(defn increment-handler
  "Datastar handler for incrementing counter"
  [request]
  (let [current-count (or (some-> (get-in request [:params :count]) (Integer/parseInt)) 0)
        new-count (inc current-count)]
    {:status 200
     :headers {"Content-Type" "text/vnd.datastar.fragment+html"}
     :body (html
             [:div
              {:data-merge-store (str "{count: " new-count "}")}])}))

(defn search-handler
  "Datastar handler for live search"
  [request]
  (let [query (get-in request [:params :query] "")
        results (if (empty? query)
                  []
                  ["Result 1" "Result 2" "Result 3"])]
    {:status 200
     :headers {"Content-Type" "text/vnd.datastar.fragment+html"}
     :body (html
             [:div
              {:data-merge-store (str "{results: " (pr-str results) "}")}])}))

(defn not-found
  "404 handler"
  [request]
  (-> (layout/base-layout
        {:title "Not Found"}
        (layout/container
          [:div.mt-4
           (layout/alert
             {:variant "warning"}
             [:h4 "Page Not Found"]
             [:p "The page you're looking for doesn't exist."])
           [:a.btn.btn-primary {:href "/"} "Go Home"]]))
      ring-resp/response
      (ring-resp/status 404)))
