(ns riverdb.ui.routes
  (:require
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [pushy.core :as pushy]
    [theta.log :refer [debug]]
    [clojure.string :as str]
    [taoensso.timbre :as timbre]
    [riverdb.application :refer [SPA]]))

(defonce history
  (pushy/pushy
    (fn [p]
      (let [r-segments (vec (rest (str/split p "/")))]
        (timbre/spy :info r-segments)
        (if (seq r-segments)
          (dr/change-route SPA r-segments)
          (dr/change-route SPA ["main"])))) identity))

(defn start! []
  (pushy/start! history))

(defn route-to! [route-str]
  (pushy/set-token! history route-str))

