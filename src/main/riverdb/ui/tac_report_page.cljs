(ns riverdb.ui.tac-report-page
  (:require
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h3 button table tr td]]
    [com.fulcrologic.fulcro.components :as om :refer [defsc transact!]]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [com.fulcrologic.fulcro.mutations :as fm]
    [com.fulcrologic.semantic-ui.elements.loader.ui-loader :refer [ui-loader]]
    [riverdb.model.user :as user]
    [riverdb.api.mutations :as rm]
    [riverdb.util :refer [sort-maps-by with-index]]
    [riverdb.ui.lookups :as looks]
    [riverdb.ui.session :refer [Session]]
    [riverdb.ui.project-years :refer [ProjectYears ui-project-years]]
    [theta.log :as log :refer [debug]]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.data-fetch :as df]))

(defn str-values [vals]
  (str "(" (clojure.string/join ", " vals) ")"))

(defn param-sort [m cfg]
  (conj (sorted-map-by #(compare (get-in cfg [%1 :order]) (get-in cfg [%2 :order])))
    m))

(defsc TacReport [this {:keys [tac-report-data] :as props}]
  {:query         [:tac-report-data]
   :initial-state {}}

  (let [{:keys [count-sitevisits count-no-results no-results-rs param-config qapp-requirements
                count-results percent-complete z-params count-params
                count-params-planned count-params-possible count-params-valid percent-params-valid
                count-params-exceedance percent-params-exceedance report-year project agency]} tac-report-data
        z-params (param-sort z-params param-config)]
    ;{:keys [agency project year]} (om/get-computed this)]
    (dom/div {:className "tac-report"}
      (when tac-report-data
        (println "RENDER TAC_REPORT" agency)
        (dom/div {:key "tac-report"}
          (dom/div :.ui.segment
            (dom/h2 (str agency " " project " TAC Report " (if report-year
                                                             (str "for " report-year)
                                                             "for All Years")))
            (dom/h3 "Site Visit Completeness")
            (dom/table :.ui.collapsing.very.compact.table
              (dom/tbody
                (dom/tr (td "Planned Site Visits: ") (td (str count-sitevisits)))
                (dom/tr (td "Failed Site Visits: ") (td (str count-no-results)))
                (dom/tr (td "Actual Site Visits: ") (td (str count-results)))
                (dom/tr (td "Site Visit Completeness: ") (td (str percent-complete "%")))
                (dom/tr (td " ") (td ""))
                (dom/tr (td "Planned Parameters: ") (td (str count-params-planned)))
                (dom/tr (td "Possible Parameters: ") (td (str count-params-possible)))
                (dom/tr (td "Actual Parameters: ") (td (str count-params)))
                (dom/tr (td "Valid Parameters: ") (td (str count-params-valid)))
                (dom/tr (td "% Valid Parameters: ") (td (str percent-params-valid)))
                (dom/tr (td "Exceedance Parameters: ") (td (str count-params-exceedance)))
                (dom/tr (td "% Exceedance Parameters: ") (td (str percent-params-exceedance)))))

            (dom/h3 nil "QAPP Precision Standards")

            (dom/table :.ui.compact.collapsing.table
              (dom/tbody
                (doall
                  (for [[qk qm] (:params qapp-requirements)]
                    (let [qk-nm  (name qk)
                          prec   (:precision qm)
                          thresh (:threshold prec)
                          unit   (:unit prec)
                          perc   (:percent prec)
                          range  (:range prec)]
                      (dom/tr {:key qk-nm}
                        (dom/td
                          (str qk-nm))
                        (dom/td
                          (if thresh
                            (str "± " perc " % avg above " thresh ", " " ± " unit " units avg below " thresh)
                            (cond
                              range
                              (str "R <= " range)
                              perc
                              (str "± " perc " % avg")
                              unit
                              (str "± " unit " avg")))))))))))


          (when (seq no-results-rs)
            (dom/div :.ui.segment
              (dom/div {:key "missing_results"}
                (dom/h3 "Missing Results:"))

              (dom/table :.ui.striped.very.compact.table
                (dom/thead
                  (dom/tr
                    (dom/th "Site")
                    (dom/th "Date")
                    (dom/th "Reason")
                    (dom/th "Crew")
                    (dom/th "Notes")))
                (dom/tbody
                  (doall
                    (for [{:keys [db/id date site reason crew notes]} no-results-rs]
                      (let [_ (println "DATE: " date)]
                        (dom/tr {:key id}
                          (dom/td site)
                          (dom/td (if (= (type date) js/String)
                                    date
                                    (.toLocaleDateString date "en-US")))

                          (dom/td reason)
                          (dom/td crew)
                          (dom/td notes)))))))))

          (dom/h2 "Parameter Validity Summary")

          (dom/div {:className "table-responsive"}
            (table :.ui.striped.very.compact.table
              (dom/thead
                (dom/tr
                  (dom/th "Param")
                  (dom/th "Planned")
                  (dom/th "Possible")
                  (dom/th "Actual")
                  (dom/th "Valid")
                  (dom/th "% Valid")
                  (dom/th "Invalid")
                  (dom/th "Incomplete")
                  (dom/th "Imprecise")
                  (dom/th "Exceedance")
                  (dom/th "High")
                  (dom/th "Low")))
              (dom/tbody
                (doall
                  (for [[p-nm p] z-params]
                    (dom/tr {:key p-nm}
                      (dom/td (name p-nm))
                      (dom/td count-sitevisits)
                      (dom/td count-results)
                      (dom/td (:count p))
                      (dom/td (:valid p))
                      (dom/td (:percent-valid p))
                      (dom/td (:invalid p))
                      (dom/td (:invalid-incomplete p))
                      (dom/td (:invalid-imprecise p))
                      (dom/td (:exceedance p))
                      (dom/td (:exceed-high p))
                      (dom/td (:exceed-low p))))))))

          (dom/h2 nil "Parameter Details")

          (dom/div
            (doall
              (for [[p-nm {:keys [valid exceedance exceedance-rs exceed-high exceed-low
                                  percent-valid invalid count invalid-imprecise invalid-rs] :as p}] z-params]
                (dom/div :.ui.segment {:key p-nm}
                  (dom/h3 nil (name p-nm))
                  (table :.ui.compact.collapsing.table
                    (dom/tbody
                      (dom/tr (td "Count: ") (td (str count)))
                      (dom/tr (td "Valid: ") (td (str valid)))
                      (when invalid
                        (dom/tr (td "Invalid: ") (td (str invalid))))
                      (when exceedance
                        (dom/tr (td "Exceedances: ") (td (str exceedance))))))

                  (when invalid
                    (dom/div
                      (dom/h3 "Invalid Records:")
                      (table :.ui.striped.very.compact.table
                        (dom/thead
                          (dom/tr
                            (dom/th "Site")
                            (dom/th "Date")
                            (dom/th "Max")
                            (dom/th "Min")
                            (dom/th "Mean")
                            (dom/th "StdDev")
                            (dom/th "RDS")
                            (dom/th "Range")
                            (dom/th "Samples")
                            (dom/th "Imprecise?")
                            (dom/th "Incomplete?")
                            (dom/th "Device")
                            (dom/th "Notes")))
                        (dom/tbody
                          (doall
                            (for [{:keys [db/id svid fieldresults date site notes] :as sv} invalid-rs]
                              (let [{:keys [min mean stddev vals prec max device range imprecise? incomplete?]} fieldresults]
                                (dom/tr #js {:key (or id svid)}
                                  (dom/td site)
                                  (dom/td (if (= (type date) js/String)
                                            date
                                            (.toLocaleDateString date "en-US")))
                                  (dom/td max)
                                  (dom/td min)
                                  (dom/td mean)
                                  (dom/td stddev)
                                  (dom/td prec)
                                  (dom/td range)
                                  (dom/td {:style {:whiteSpace "nowrap"}} (str-values vals))
                                  (dom/td (when imprecise? "Y"))
                                  (dom/td (when incomplete? "Y"))
                                  (dom/td {:style {:whiteSpace "nowrap"}} device)
                                  (dom/td notes)))))))))

                  (dom/br)

                  (when exceedance
                    (dom/div
                      (dom/h3 "Exceedance Records:")
                      (table :.ui.striped.very.compact.table
                        (dom/thead
                          (dom/tr
                            (dom/th "Site")
                            (dom/th "Date")
                            (dom/th "Max")
                            (dom/th "Min")
                            (dom/th "Mean")
                            (dom/th "StdDev")
                            (dom/th "RDS")
                            (dom/th "Range")
                            (dom/th "Samples")
                            (dom/th "Device")
                            (dom/th "Notes")))
                        (dom/tbody
                          (doall
                            (for [{:keys [db/id svid fieldresults date site notes] :as sv} exceedance-rs]
                              (let [{:keys [min mean stddev vals prec max device range]} fieldresults]
                                (dom/tr {:key (or id svid)}
                                  (dom/td site)
                                  (dom/td (if (= (type date) js/String)
                                            date
                                            (.toLocaleDateString date "en-US")))
                                  (dom/td max)
                                  (dom/td min)
                                  (dom/td mean)
                                  (dom/td stddev)
                                  (dom/td prec)
                                  (dom/td range)
                                  (dom/td {:style {:whiteSpace "nowrap"}} (str-values vals))
                                  (dom/td {:style {:whiteSpace "nowrap"}} device)
                                  (dom/td notes))))))))))))))))))


(defn parse-year [yr]
  (if-not (= yr "") (js/parseInt yr) yr))

(defonce tac-page-state (atom {}))

(defsc TacReportPage [this {:riverdb.ui.root/keys [current-agency current-project current-year]
                            :keys                 [tac-report-data project-years] :as props}]
  {:ident         (fn [] [:component/id :tac-report])
   :query         [[:tac-report-data '_]
                   {:project-years (om/get-query ProjectYears)}
                   {[:riverdb.ui.root/current-agency '_] (comp/get-query looks/agencylookup-sum)}
                   {[:riverdb.ui.root/current-project '_] (comp/get-query looks/projectslookup-sum)}
                   [:riverdb.ui.root/current-year '_]
                   [df/marker-table ::tac]]
   :initial-state {:tac-report-data nil
                   :project-years   {}}
   :route-segment ["tac-report"]}

  (let [{:projectslookup/keys [ProjectID Name]} current-project
        AgencyCode (:agencylookup/AgencyCode current-agency)
        marker (get props [df/marker-table ::tac])]
    (debug "RENDER TacReportPage" AgencyCode ProjectID)
    (dom/div {:className "tac-report-page"}
      (dom/div :.ui.menu {:style {}}
        (dom/div :.item {}
          (ui-project-years project-years)
          (when (and ProjectID current-year)
            (dom/button {:onClick #(let []
                                     (println "LOADING TAC REPORT" AgencyCode ProjectID current-year)
                                     (f/load this :tac-report-data nil {:params               {:agency  AgencyCode
                                                                                               :project ProjectID
                                                                                               :year    current-year}
                                                                        :post-mutation-params {:order :asc}
                                                                        :post-mutation        `rm/process-tac-report
                                                                        :marker               ::tac}))}
              (str "Generate TAC Report for " AgencyCode " " current-year)))))

      (if marker
        (ui-loader {:active true})
        (when tac-report-data
          (div {:style {}}
            ((om/factory TacReport) (om/computed {:tac-report-data tac-report-data} {:agency  AgencyCode
                                                                                     :project Name
                                                                                     :year    current-year}))))))))

;(doseq [[index file] (with-index files)]
;  (println index file)
;  (.. formData (append (str "file" index) file)))
;
;(aset http-request "onreadystatechange" (fn [evt]
;                                          (if (= 4 (. http-request -readyState))
;                                            (js/console.log (. http-request -statusText)))
;                                          ))
;(. http-request open "post" "/upload" true)
;(. http-request setRequestHeader "Content-Type" "multipart/form-data; boundary=foo")
;(. http-request send formData)
;; an input triggering upload(s)
;(let [onChange (fn [evt]
;                 (let [js-file-list (.. evt -target -files)]
;                   (om/transact! this
;                     (mapv (fn [file-idx]
;                             (let [fid     (om/tempid)
;                                   js-file (with-meta {} {:js-file (.item js-file-list file-idx)})]
;                               `(upload-file ~{:id fid :file js-file})))
;                       (range (.-length js-file-list))))))]
;  (dom/input #js {:multiple true :onChange onChange}))