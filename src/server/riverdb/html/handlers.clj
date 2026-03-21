(ns riverdb.html.handlers
  "HTTP handlers for server-side HTML app with Datastar"
  (:require [clojure.tools.logging :refer [debug]]
            [charred.api :as charred]
            [cheshire.core :as json]
            [hiccup.core :refer [html]]
            [ring.util.response :as ring-resp]
            [riverdb.html.layout :as layout]
            [starfederation.datastar.clojure.api :as d*]
            [org.tcrawley.datastar-pedestal-adapter :as d*.pedestal :refer [->sse-response on-open]]))

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
                           {:href "/demo" :label "Demo"}
                           {:href "/hello" :label "Hello"}]})
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

(def ^:private bufSize 1024)
(def read-json (charred/parse-json-fn {:async? false :bufsize bufSize}))
(def parse-json json/decode)

(defn get-signals*
  "Returns the signals json string. You need to use some middleware
  that adds the :query-params key to the request for this function
  to work properly.

  (Bring your own json parsing)"
  [request]
  (if (= :get (:request-method request))
    (get-in request [:query-params :datastar])
    (:body request)))

(defn get-signals [req]
  (some-> req get-signals* json/decode))

(def message "Hello, world!")

(def msg-count  (count message))

(defn ->frag [i]
  (let [result (html
          [:div {:id "message"}
           (subs message 0 (inc i))])]
    ;(debug "FRAG" result)
    result))



(defn hello-world [request]
  (debug "HELLO HANDLER" (get-signals request) )
  (let [d (-> request get-signals (get "delay") int)]
    (->sse-response request
                    {on-open
                     (fn [sse]
                       (d*/with-open-sse sse
                                         (dotimes [i msg-count]
                                           (d*/patch-elements! sse (->frag i))
                                           (Thread/sleep d))))})))


(defn error-fn [arg1 arg2]
  (debug "ERROR" arg1 arg2))

(defn hello-handler [ctx sse]
  (let [params (get-in ctx [:request :query-params])
        _ (debug "params" params)
        d (-> (:request ctx) get-signals (get "delay"))
        _ (debug "delay" d (type d))
        d (if (string? d) (int d) d)
        d (if (not (number? d)) 100 d)]
    (debug "HELLO handler" params )
    (d*/with-open-sse sse
                      (d*/patch-elements! sse (html [:div {:id "hello"} "Hello World Indeed!"]))
                      (dotimes [i msg-count]
                        (d*/patch-elements! sse (->frag i))
                        (Thread/sleep d)))))

(defn handler-fn [sse]
  ;(d*/patch-elements! sse "<div>1</div>")
  ;(d*/patch-elements-seq! sse ["<div>2</div>" "<div>3</div>"])
  ;(d*/patch-signals! sse (json/encode {:$abc true}))
  ;(d*/close-sse! sse)
  (d*/with-open-sse sse
                    (dotimes [i msg-count]
                      (d*/patch-elements! sse (->frag i))
                      (Thread/sleep 100))))

(defn ->hello-interceptor
  []
  {:name ::hello-interceptor
   :enter (fn [ctx]
            (d*.pedestal/->sse-response
              ctx
              {d*.pedestal/on-open #(hello-handler ctx %)
               ;d*.pedestal/on-exception error-fn
               }))})

(defn hello-page
  "A datastar hello world page"
  [request]
  (ring-resp/response
    (layout/base-layout
      {:title "Hello World"}
      (layout/nav {:brand "RiverDB"
                   :items [{:href "/" :label "Home"}
                           {:href "/about" :label "About"}
                           {:href "/demo" :label "Demo"}
                           {:href "/hello" :label "Hello" :active true}]})
      (layout/container
        [:div.bg-white.dark:bg-gray-800.text-gray-500.dark:text-gray-400.rounded-lg.px-6.py-8.ring.shadow-xl.space-y-2
         {:data-signals "{delay: 400}" :class "ring-gray-900/5"}
         [:div.flex.justify-between.items-center
          [:h1.text-gray-900.dark:text-white.text-3xl.font-semibold "Datastar SDK Demo"]
          [:img {:src "https://data-star.dev/static/images/rocket-64x64.png" :alt "Rocket" :width "64" :height "64"}]]

         [:p.mt-2 "SSE events will be streamed from the backend to the frontend."]

         [:div.space-x-2
          [:label {:for "delay"} "Delay in milliseconds"]
          [:input#delay.w-36.rounded-md.border.border-gray-300.px-3.py-2.placeholder-gray-400.shadow-sm.focus:border-sky-500.focus:outline.focus:outline-sky-500.dark:disabled:border-gray-700
           {:data-bind "delay" :type "number" :step "100" :min "0" :class "dark:disabled:bg-gray-800/20"}
           ]]
         [:button.rounded-md.bg-sky-500.px-5.py-2.5.leading-5.font-semibold.text-white.hover:bg-sky-700.hover:text-gray-100.cursor-pointer
          {:data-on:click "@get('/hello-world')"}
          ;{:data-on:click "alert('hi there')"}
          "Hello"]
        [:div.my-16.text-8xl.font-bold.text-transparent
         {:style "background: linear-gradient(to right in oklch, red, orange, yellow, green, blue, blue, violet); background-clip: text"}
         [:div#message "Hello, world!"]]
         [:div#hello "hello?"]
       ])))
  )

(defn about-page
  "About page handler"
  [request]
  (ring-resp/response
    (layout/base-layout
      {:title "About - RiverDB HTML App"}
      (layout/nav {:brand "RiverDB"
                   :items [{:href "/" :label "Home"}
                           {:href "/about" :label "About" :active true}
                           {:href "/demo" :label "Demo"}
                           {:href "/hello" :label "Hello"}]})
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
                           {:href "/demo" :label "Demo" :active true}
                           {:href "/hello" :label "Hello"}]})
      (layout/container
        [:div.mt-4
         (layout/card
           {:title "Counter Demo"
            :subtitle "Click the button to increment the counter"}
           [:div
            {:data-signals "{count: 0}"}
            [:p.text-lg
             "Count?: "
             [:span.badge.badge-primary
              {:data-text "$count"}]]
            [:button.btn.btn-primary.mt-2
             {:data-on:click "@get('/html/increment')"}
             "Increment"]
             [:button.rounded-md.bg-sky-500.px-5.py-2.5.leading-5.font-semibold.text-white.hover:bg-sky-700.hover:text-gray-100.cursor-pointer
              {:data-on:click "@get('/hello-world')"}
              ;{:data-on:click "alert('hi there')"}
              "HI"]
            ;[:button.btn.btn-primary.mt-2 {:data-on:click "@get('/endpoint')"} "Open the pod bay doors, HAL."]
            [:div#message]
            [:div#hello]
            ])

         [:div.mt-4
          (layout/card
            {:title "Live Search Demo"
             :subtitle "Search updates as you type"}
            [:div
             {:data-signals "{query: '', results: []}"}
             [:div.form-group
              [:input.form-control
               {:id          "search"
                :data-bind   "query"
                :placeholder "Type to search..."
                :data-on:input "@get('/html/search')"}]]
             #_(layout/input
                 {:id          "search"
                  :data-bind   "query"
                  :placeholder "Type to search..."
                  :class       "data-on-input-get('/html/search')"})
             #_[:button.btn.btn-primary.mt-2
              {:data-on:click "@get('/html/search')"}
              "Search"]
             [:div#results.mt-3]])]
         ]))))



(defn increment-handler
  "Datastar handler for incrementing counter"
  [ctx sse]
  (debug "INCREMENT PARAMS" (-> ctx :request :query-params))
  (let [params (-> ctx :request :query-params)
        _ (debug "inc params 1" params)
        params (get-in ctx [:request :query-params])
        _ (debug "inc params 2" params)
        sigs (-> (:request ctx) get-signals)
        _ (debug "SIGNALS" sigs)
        count (get sigs "count")
        _ (debug "count" count (type count) (pr-str count))
        count (if (not (number? count)) 0 count)
        new-count (inc count)]
    (d*/with-open-sse
      sse
      (d*/patch-elements! sse (html [:div {:id "hello"} "Hello World " (str new-count) "!"]))
      (d*/patch-signals!  sse (json/encode {:count new-count}))
      )))

(defn ->inc-interceptor
  []
  {:name ::inc-interceptor
   :enter (fn [ctx]
            (d*.pedestal/->sse-response
              ctx
              {d*.pedestal/on-open      #(increment-handler ctx %)
               d*.pedestal/on-exception error-fn}))})

(defn search-handler
  "Datastar handler for live search"
  [ctx sse]
  (let [request (:request ctx)
        params (:query-params request)
        _ (debug "params" params)
        signals (get-signals request)
        _ (debug "signals" signals)
        query (get-in request [:params :query] "")
        _ (debug "query" query)
        query (get signals "query")
        _ (debug "signal query" query)
        results (if (empty? query)
                  []
                  ["Result 1" "Result 2" "Result 3"])
        _ (debug "results" results)]
    (d*/with-open-sse
      sse
      (d*/console-log! sse "hi from the backend")
      (d*/patch-elements!
        sse
        (html
          (if (empty? results)
            [:div#results.mt-3]
            [:div#results.mt-3
             [:h5 "Results:"]
             [:ul
              (for [result results]
               [:li result])]])))
      )
    #_{:status 200
     :headers {"Content-Type" "text/vnd.datastar.fragment+html"}
     :body (html
             [:div
              {:data-merge-store (str "{results: " (pr-str results) "}")}])}))

(defn ->search
  []
  {:name ::search-interceptor
   :enter (fn [ctx]
            (d*.pedestal/->sse-response
              ctx
              {d*.pedestal/on-open      #(search-handler ctx %)
               d*.pedestal/on-exception error-fn}))})

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
