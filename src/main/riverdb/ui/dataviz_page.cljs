(ns riverdb.ui.dataviz-page
  (:require
    [com.fulcrologic.fulcro.dom :as dom :refer [div span select option ul li p h2 h3 button table tr td th thead tbody]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [com.fulcrologic.semantic-ui.elements.loader.ui-loader :refer [ui-loader]]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [com.fulcrologic.fulcro.mutations :as fm]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [riverdb.model.user :as user]
    [riverdb.api.mutations :as rm]
    [riverdb.util :refer [sort-maps-by with-index]]
    [riverdb.ui.components :refer [ui-autosizer]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.session :refer [Session]]
    [riverdb.ui.project-years :refer [ProjectYears ui-project-years]]
    [theta.log :as log :refer [debug]]
    [riverdb.util :as util :refer [sort-maps-by]]
    [riverdb.ui.user :refer [User]]
    [goog.object :as gobj]
    [com.fulcrologic.fulcro.data-fetch :as df]))


(defn get-params [z-params]
  (vec (for [[p-nm p] z-params]
         p-nm)))


(defn dataviz-headers [param-cfg]
  (let [heads [(th {:key 1} "Date")
               (th {:key 2} "Site")
               (th {:key 3} "Time")
               (th {:key 4} "Fork")
               (th {:key 5} "Trib")]
        ps    (flatten
                (for [[p-nm p] param-cfg]
                  (let [sample-count (:count p)
                        fields       (if (= sample-count 1)
                                       ["1" "Device"]
                                       ["1" "2" "3" "Mean" "StDev" "Prec" "Range" "Count" "Device" "Invalid" "Exceed"])]
                    (for [field fields]
                      (let [name (str (:name p) "_" field)]
                        (cond
                          (= field (first fields))
                          (th {:key name :style {:borderLeftStyle "solid"
                                                 :borderLeftWidth "1px"}} name)
                          (= field (last fields))
                          (th {:key name :style {:borderRightStyle "solid"
                                                 :borderRightWidth "1px"}} name)
                          :else
                          (th {:key name} name)))))))
        ends  [(th {:key 96 :className "info"} "Invalid_Total")
               (th {:key 97 :className "danger"} "Exceed_Total")
               (th {:key 98} "ID")
               (th {:key 95} "SVID")
               (th {:key 99} "Notes")]]

    (concat heads ps ends)))

(defn cell [key val class]
  (td {:key key :className (when class class)} val))

(defn dataviz-cells [param-cfg sv]
  (let [{:keys [db/id site date time fork trib fieldresults svid notes count-params-invalid count-params-exceedance]} sv
        heads [(td {:key 1 :style {:whiteSpace "nowrap"}} (.toLocaleDateString date "en-US"))
               (td {:key 3 :style {:whiteSpace "nowrap"}} site)
               (td {:key 2 :style {:whiteSpace "nowrap"}} time)
               (td {:key 4 :style {:whiteSpace "nowrap"}} fork)
               (td {:key 5 :style {:whiteSpace "nowrap"}} trib)]

        ps    (flatten
                (doall (for [[p-name p] param-cfg]
                         (let [rs           (get-in fieldresults [p-name])
                               vals         (:vals rs)
                               no-vals?     (not vals)
                               sample-count (:count p)
                               {:keys [mean stddev prec range count device invalid? exceedance? incomplete? too-low? too-high? imprecise?]} rs]
                           (if (= sample-count 1)
                             [(td {:key   (str p-name 4)
                                   :style {:borderLeftStyle "solid"
                                           :borderLeftWidth "1px"
                                           :backgroundColor (when exceedance? "#f2dede")}}
                                mean)
                              (td {:key (str p-name 9) :style {:borderRightStyle "solid"
                                                               :borderRightWidth "1px"}}
                                device)]
                             [(td {:key   (str p-name 1)
                                   :style {:borderLeftStyle "solid"
                                           :borderLeftWidth "1px"
                                           :backgroundColor (cond exceedance? "#f2dede" invalid? "#d9edf7")}}
                                (get vals 0))
                              (td {:key   (str p-name 2)
                                   :style {:backgroundColor (cond exceedance? "#f2dede" invalid? "#d9edf7")}}
                                (get vals 1))
                              (td {:key   (str p-name 3)
                                   :style {:backgroundColor (cond exceedance? "#f2dede" invalid? "#d9edf7")}}
                                (get vals 2))
                              (td {:key   (str p-name 4)
                                   :style {:backgroundColor (when exceedance? "#f2dede")}}
                                mean)
                              (td {:key (str p-name 5)} stddev)
                              (td {:key   (str p-name 6)
                                   :style {:backgroundColor (when imprecise? "#d9edf7")}} prec)
                              (td {:key (str p-name 7) :style {:backgroundColor (when imprecise? "#d9edf7")}}
                                range)
                              (td {:key (str p-name 8) :style {:backgroundColor (when incomplete? "#d9edf7")}}
                                count)
                              (td {:key (str p-name 9) :style {}}
                                device)
                              (td {:key (str p-name 10) :style {:backgroundColor (when invalid? "#d9edf7")}}
                                (when invalid? "1"))
                              (td {:key (str p-name 11) :style {:backgroundColor  (when exceedance? "#f2dede")
                                                                :borderRightStyle "solid"
                                                                :borderRightWidth "1px"}}
                                (when exceedance? "1"))])))))
        notes [(td {:key 96 :style {:backgroundColor (when count-params-invalid "#d9edf7")}} count-params-invalid)
               (td {:key 97 :style {:backgroundColor (when count-params-exceedance "#f2dede")}} count-params-exceedance)
               (td {:key 98} (or id ""))
               (td {:key 95} (or svid ""))
               (td {:key 99} (or notes ""))]]
    (concat heads ps notes)))

(defn str-values [vals]
  (str "(" (clojure.string/join ", " vals) ")"))

(defn param-sort [m]
  (conj
    (sorted-map-by #(compare (get-in m [%1 :order]) (get-in m [%2 :order])))
    m))

(defn param-sort2 [m]
  (into
    (sorted-map-by
      (fn [key1 key2]
        (compare [(get m key2) key2]
          [(get m key1) key1])))
    m))

(defsc Scrollable [this props]
  {:initLocalState (fn [this _]
                     {:save-ref (fn [r] (gobj/set this "input-ref" r))})})

(defsc DataVizComp [this {:keys [dataviz-data] :as props}]
  (let [{:keys [results-rs param-config report-year agency project]} dataviz-data
        ;{:keys [agency]} current-user
        ;_            (log/debug "PARAM-CONFIG BEFORE" param-config)
        ;param-config (into {} (remove #(:elide? (val %)) param-config))
        param-config (param-sort param-config)]
    (div {:key "report" :style {:display "flex" :flexDirection "column" :height "90%"}}
      (h2 {:style {:flex "0 0 0"}} (str agency " " project " " (when report-year
                                                                 report-year)))
      (div {:style {:flex "1 0 100%"}}
        (log/debug "PRE AUTOSIZER Child FN")
        (ui-autosizer {}
          (fn [jsprops]
            (let [height (comp/isoget jsprops "height")
                  width  (comp/isoget jsprops "width")
                  _      (log/debug "AUTOSIZER Child FN" width height)]
              (div {:style {:overflow "auto" :height height :width width}}
                (table :#dataviz.ui.small.single.line.very.compact.table {:style {}}
                  (thead {}
                    (tr {}
                      (dataviz-headers param-config)))
                  (tbody {}
                    (doall
                      (for [{:keys [db/id invalid? exceedance?] :as sv} results-rs]
                        (tr {:key id :className (when (or exceedance? invalid?) "warning")}
                          (dataviz-cells param-config sv))))))))))))))


(defsc DataVizPage [this {:ui.riverdb/keys [current-agency current-project current-year]
                          :keys                 [dataviz-data project-years] :as props}]
  {:ident         (fn [] [:component/id :dataviz])
   :query         [[:dataviz-data '_]
                   {:project-years (comp/get-query ProjectYears)}
                   {[:ui.riverdb/current-agency '_] (comp/get-query looks/agencylookup-sum)}
                   {[:ui.riverdb/current-project '_] (comp/get-query looks/projectslookup-sum)}
                   [:ui.riverdb/current-year '_]
                   [df/marker-table ::table]]
   :initial-state (fn [params]
                    {:dataviz-data  nil
                     :project-years (comp/initial-state ProjectYears params)})
   :route-segment ["dataviz"]}
  (let [{:projectslookup/keys [ProjectID Name]} current-project
        AgencyCode   (:agencylookup/AgencyCode current-agency)
        {:keys [results-rs param-config report-year]} dataviz-data
        param-config (param-sort param-config)
        marker       (get props [df/marker-table ::table])]
    (log/debug "RENDER DataVizPage" ProjectID current-year)
    (div :.ui.container {:className "dataviz-report" :style {:height "100%"}}
      (div :.ui.menu {:key "report-selectors" :style {}}
        (div :.item {:key "report-year-selector"}
          (ui-project-years project-years)

          (when current-year
            (button {:onClick #(do
                                 (f/load this :dataviz-data nil
                                   {:params               {:project (name ProjectID)
                                                           :agency  AgencyCode
                                                           :year    current-year}
                                    :post-mutation-params {:order :asc}
                                    :post-mutation        `rm/process-dataviz-data
                                    :marker               ::table}))}

              (str "Generate Table for " current-year))))



        (div :.item.right {:key "all-years-button" :style {}}
          (button :.pointer {:type    "submit"
                             :onClick #(do
                                         (log/debug "GET DATAVIZ ALL YEARS CSV" AgencyCode)
                                         (set! (.. js/window -location -href) (str "/sitevisits.csv?agency=" AgencyCode)))}

            "Download CSV for All Years")))


      (if marker
        (ui-loader {:active true})
        (when dataviz-data
          (let [cnt (count results-rs)
                _   (log/debug "RESULTS COUNT" cnt)]
            ((comp/factory DataVizComp) {:dataviz-data dataviz-data
                                         :param-config param-config
                                         :results-rs   results-rs})))))))



