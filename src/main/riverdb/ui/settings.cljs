(ns riverdb.ui.settings
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h3 button label]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [riverdb.application :refer [SPA]]
    [theta.log :as log :refer [debug]]))

(defsc Settings [this {:keys [ui/hello]  :as props}]
  {:ident              (fn [] [:component/id :settings])
   :query              [:ui/hello]
   :initial-state      {:ui/hello "Hello"}
   :route-segment      ["settings"]
   :componentDidUpdate (fn [this prev-props prev-state])
   :componentDidMount  (fn [this])}
  (let [_ (debug "SETTINGS" (keys props))]
    (div :.ui.container
      (div :.ui.segment
        (h3 "Settings")))))

(def ui-settings (comp/factory Settings))
