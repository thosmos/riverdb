(ns riverdb.ui.routes
  (:import [goog History])
  (:require
    [goog.events :as gevents]
    [clojure.string :as str]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [pushy.core :as pushy]
    [theta.log :refer [debug]]
    [clojure.string :as str]
    [taoensso.timbre :as timbre]
    [riverdb.application :refer [SPA]]))

(defonce page-history
  (doto (History.)
    (.setEnabled true)))

(defn listen-nav-change [f]
  (gevents/listen page-history "navigate" #(f % (.-token %))))

(defn change-route [path]
  (.setToken page-history (str/join "/" path)))

(defn path->route [path]
  (let [r-segments (vec (rest (str/split path "/")))]
    (if (seq r-segments)
      r-segments
      ["main"])))

(defn change-route-from-nav-event [app]
  (fn [_ path]
    (dr/change-route app (path->route path))))

(comment (change-route (dr/path-to SearchPage)))

(defn start-routing! []
  (listen-nav-change #(change-route-from-nav-event SPA)))


(defonce history
  (pushy/pushy
    (fn [p]
      (let [r-segments (vec (rest (str/split p "/")))]
        (timbre/spy :info r-segments)
        (if (seq r-segments)
          (dr/change-route SPA r-segments)
          (dr/change-route SPA ["main"])))) identity))

(defn start! []
  (pushy/start! history)
  #_(listen-nav-change #(change-route-from-nav-event SPA)))

(defn route-to! [route-str]
  (pushy/set-token! history route-str)
  #_(.setToken page-history route-str))




