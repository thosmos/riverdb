(ns riverdb.ui.reports.datatable-page
  (:require
    [com.fulcrologic.fulcro.dom :as dom :refer [div span select option ul li p h2 h3 button table tr td th thead tbody]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [com.fulcrologic.semantic-ui.elements.loader.ui-loader :refer [ui-loader]]
    [com.fulcrologic.fulcro.data-fetch :as df]
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
    [thosmos.util :as tu]))


(defn get-params [z-params]
  (vec (for [[p-nm p] z-params]
         p-nm)))


(defn dataviz-headers [params]
  (let [heads [(th {:key 1} "Date")
               (th {:key 2} "Site")
               (th {:key 3} "Time")
               #_(th {:key 4} "Fork")
               #_(th {:key 5} "Trib")]
        ps    (flatten
                (for [{:parameter/keys [Replicates NameShort DeviceType]} params]
                  (let [sample-count (or Replicates 1)
                        fields       (if (> sample-count 1)
                                       (into
                                         (mapv str (range 1 (inc sample-count)))
                                         ["Mean" "Count"])
                                       [""])
                        fields (if DeviceType
                                 (conj fields "Device")
                                 fields)]
                    (for [field fields]
                      (let [name (if
                                   (> sample-count 1)
                                   (str NameShort "_" field)
                                   NameShort)]
                        (cond
                          (= field (first fields))
                          (th {:key name :style {:borderLeftStyle "solid"
                                                 :borderLeftWidth "1px"}} name)
                          (= field (last fields))
                          (th {:key name :style {:borderRightStyle "solid"
                                                 :borderRightWidth "1px"}} name)
                          :else
                          (th {:key name} name)))))))
        ends  [#_(th {:key 96 :className "info"} "Invalid_Total")
               #_(th {:key 97 :className "danger"} "Exceed_Total")
               (th {:key 98} "ID")
               #_(th {:key 95} "SVID")
               (th {:key 99} "Notes")]]

    (concat heads ps ends)))

(defn cell [key val class]
  (td {:key key :className (when class class)} val))

(defn dataviz-cells [params sv]
  (let [{:keys [db/id site date time fork trib samples svid
                notes count-params-invalid count-params-exceedance]} sv
        heads [(td {:key 1 :style {:whiteSpace "nowrap"}} (.toLocaleDateString date "en-US"))
               (td {:key 3 :style {:whiteSpace "nowrap"}} site)
               (td {:key 2 :style {:whiteSpace "nowrap"}} time)
               #_(td {:key 4 :style {:whiteSpace "nowrap"}} fork)
               #_(td {:key 5 :style {:whiteSpace "nowrap"}} trib)]

        ps    (flatten
                (map-indexed
                  (fn [i {pid :db/id :parameter/keys [Replicates NameShort DeviceType]}]
                    (let [sample-count Replicates
                          ;fields       (if (= sample-count 1)
                          ;               ["1"]
                          ;               (into
                          ;                 (mapv str (range 1 (inc sample-count)))
                          ;                 ["Mean" "Count"]))
                          ;fields       (if DeviceType
                          ;               (conj fields "Device")
                          ;               fields)

                          rs           (get samples i)
                          vals         (:vals rs)

                          {:keys [mean stddev prec range count device exceedance? invalid? incomplete? too-low? too-high? imprecise?]} rs]
                      (if (= sample-count 1)
                        [(td {:key   (str pid 4)
                              :style {:borderLeftStyle "solid"
                                      :borderLeftWidth "1px"
                                      :backgroundColor (when exceedance? "#f2dede")}}
                           mean)
                         (when DeviceType
                           (td {:key (str pid 9) :style {:borderRightStyle "solid"
                                                         :borderRightWidth "1px"}}
                             device))]
                        (into
                          (mapv (fn []) (range sample-count))
                          [(td {:key   (str pid 1)
                                :style {:borderLeftStyle "solid"
                                        :borderLeftWidth "1px"
                                        :backgroundColor (cond exceedance? "#f2dede" invalid? "#d9edf7")}}
                             (get vals 0))
                           (td {:key   (str pid 2)
                                :style {:backgroundColor (cond exceedance? "#f2dede" invalid? "#d9edf7")}}
                             (get vals 1))
                           (td {:key   (str pid 3)
                                :style {:backgroundColor (cond exceedance? "#f2dede" invalid? "#d9edf7")}}
                             (get vals 2))
                           (td {:key   (str pid 4)
                                :style {:backgroundColor (when exceedance? "#f2dede")}}
                             mean)
                           (td {:key (str pid 5)} stddev)
                           (td {:key   (str pid 6)
                                :style {:backgroundColor (when imprecise? "#d9edf7")}} prec)
                           (td {:key (str pid 7) :style {:backgroundColor (when imprecise? "#d9edf7")}}
                             range)
                           (td {:key (str pid 8) :style {:backgroundColor (when incomplete? "#d9edf7")}}
                             count)
                           (td {:key (str pid 9) :style {}}
                             device)
                           (td {:key (str pid 10) :style {:backgroundColor (when invalid? "#d9edf7")}}
                             (when invalid? "1"))
                           (td {:key (str pid 11) :style {:backgroundColor  (when exceedance? "#f2dede")
                                                          :borderRightStyle "solid"
                                                          :borderRightWidth "1px"}}
                             (when exceedance? "1"))]))))
                  params))
        notes [#_(td {:key 96 :style {:backgroundColor (when count-params-invalid "#d9edf7")}} count-params-invalid)
               #_(td {:key 97 :style {:backgroundColor (when count-params-exceedance "#f2dede")}} count-params-exceedance)
               (td {:key 98} (or id ""))
               #_(td {:key 95} (or svid ""))
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

;{:results        sitevisits
; :params            params
; :reportYear       year
; :agency            agency
; :projectName           Name}


(defsc DataTableComp [this {:keys [datatable-report] :as props}]
  (let [{:keys [results params reportYear agency projectName]} datatable-report
        _ (debug "PARAMS" params)
        ]


    (div {:key "report" :style {:display "flex" :flexDirection "column" :height "90%"}}
      (h2 {:style {:flex "0 0 0"}} (str agency " " projectName " " (when reportYear
                                                                     reportYear)))
      (div {:style {:flex "1 0 100%"}}
        ;(log/debug "PRE AUTOSIZER Child FN")
        (ui-autosizer {}
          (fn [jsprops]
            (let [height (comp/isoget jsprops "height")
                  width  (comp/isoget jsprops "width")]
                  ;_      (log/debug "AUTOSIZER Child FN" width height)]
              (div {:style {:overflow "auto" :height height :width width}}
                (table :#dataviz.ui.small.single.line.very.compact.table {:style {}}
                  (thead {}
                    (tr {}
                      (dataviz-headers params)))
                  (tbody {}
                    (doall
                      (for [{:keys [db/id incomplete? exceedance? imprecise?] :as sv} results]
                        (tr {:key id :className (when (or incomplete? exceedance? imprecise?) "warning")}
                          #_(dataviz-cells params sv))))))))))))))


(fm/defmutation process-datatable
  [p]
  (action [{:keys [state]}]
    (do
      (debug "PROCESS DATA TABLE"))))
      ;(swap! state update-in [:datatable-report :results] sort-maps-by [:date :site]))))

(defsc DataTablePage [this {:ui.riverdb/keys [current-agency current-project current-year]
                            :keys                 [datatable-report project-years] :as props}]
  {:ident         (fn [] [:component/id :datatable])
   :query         [[:datatable-report '_]
                   {:project-years (comp/get-query ProjectYears)}
                   {[:ui.riverdb/current-agency '_] (comp/get-query looks/agencylookup-sum)}
                   {[:ui.riverdb/current-project '_] (comp/get-query looks/projectslookup-sum)}
                   [:ui.riverdb/current-year '_]
                   [df/marker-table ::table]]
   :initial-state (fn [params]
                    {:datatable-report  nil
                     :project-years (comp/initial-state ProjectYears params)})
   :route-segment ["datatable"]}
  (let [{:projectslookup/keys [ProjectID Name]} current-project
        AgencyCode   (:agencylookup/AgencyCode current-agency)
        marker       (get props [df/marker-table ::table])]
    (log/debug "RENDER DataTablePage" ProjectID current-year)
    (div :.ui.container {:className "dataviz-report" :style {:height "100%"}}
      (div :.ui.menu {:key "report-selectors" :style {}}
        (div :.item {:key "report-year-selector"}
          (ui-project-years project-years)

          (when current-year
            (button {:onClick #(do
                                 (df/load this :datatable-report nil
                                   {:params               {:project (name ProjectID)
                                                           :agency  AgencyCode
                                                           :year    current-year}
                                    :post-mutation-params {:order :asc}
                                    :post-mutation        `process-datatable
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
        (when datatable-report
          ((comp/factory DataTableComp) {:datatable-report datatable-report}))))))



