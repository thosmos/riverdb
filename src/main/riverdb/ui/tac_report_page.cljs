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

(defn param-sort [m]
  #_(conj (sorted-map-by #(compare (get-in cfg [%1 :order]) (get-in cfg [%2 :order])))
      m)
  (into (sorted-map-by #(do
                          ;(debug "PARAM SORT" %1 (get-in m [%1 :display-order]) %2 (get-in m [%2 :display-order]))
                          (compare (get-in m [%1 :display-order]) (get-in m [%2 :display-order]))))
    m))

(defn bool->Y [bool]
  (if bool
    "Y"
    ""))

(defn prec->str [param-prec]
  (let [{perc :percent unit :unit range :range thresh :threshold} param-prec]
    (if thresh
      (str "±" perc "% (above " thresh "), ±" unit " (below " thresh ")")
      (cond
        range
        (str "R <= " range)
        perc
        (str "± " perc "%")
        unit
        (str "± " unit)))))

(defsc TacReport [this {:keys [tac-report-data] :as props}]
  {:query         [:tac-report-data]
   :initial-state {}}

  (let [{:keys [count-sitevisits count-no-results no-results-rs param-config qapp-requirements
                count-results percent-complete z-params count-params x-params count-dupes
                percent-params-imprecise count-params-planned count-params-possible
                count-params-complete percent-params-complete count-params-imprecise
                count-params-exceedance percent-params-exceedance report-year project agency]} tac-report-data
        _ (debug "x-params" x-params)]
        ;_ (debug "Z PARAMS PRE" z-params)
        ;z-params (param-sort z-params)]
    ;(debug "Z PARAMS POST" z-params)
    ;{:keys [agency project year]} (om/get-computed this)]
    (dom/div {:className "tac-report"}
      (when tac-report-data
        (println "RENDER TAC_REPORT" agency)
        (dom/div {:key "tac-report"}
          (dom/div :.ui.segment
            (dom/h2 (str agency " " project " QC Report " (if report-year
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
                (dom/tr (td "Sampled Parameters: ") (td (str count-params)))
                nil
                (when (> count-dupes 0)
                  (dom/tr {:style {:color "red"}} (td "Duplicate Parameters: ") (td (str (or count-dupes 0)))))
                (dom/tr (td "Complete Parameters: ") (td (str count-params-complete)))
                (dom/tr (td "% Complete Parameters: ") (td (str percent-params-complete)))
                (dom/tr (td "Imprecise Parameters: ") (td (str count-params-imprecise)))
                (dom/tr (td "% Imprecise Parameters: ") (td (str percent-params-imprecise)))
                (dom/tr (td "Exceedance Parameters: ") (td (str count-params-exceedance)))
                (dom/tr (td "% Exceedance Parameters: ") (td (str percent-params-exceedance)))))

            (dom/h3 nil "Precision Standards")

            (dom/table :.ui.compact.collapsing.table
              (dom/tbody
                (doall
                  (for [[qk qm] (:params qapp-requirements)]
                    (let [qk-nm  (name qk)
                          prec   (:precision qm)
                          {perc :percent unit :unit range :range thresh :threshold} prec]
                      (dom/tr {:key qk-nm}
                        (dom/td
                          (str qk-nm))
                        (dom/td
                          (prec->str prec)
                          #_(if thresh
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
                  (dom/th "Complete")
                  (dom/th "% Complete")
                  (dom/th "Incomplete")
                  (dom/th "Imprecise")
                  (dom/th "Exceedance")
                  (dom/th "High")
                  (dom/th "Low")))
              (dom/tbody
                (doall
                  (for [p x-params]
                    (let [p-nm (:name p)]
                      (dom/tr {:key p-nm}
                        (dom/td p-nm)
                        (dom/td count-sitevisits)
                        (dom/td count-results)
                        (dom/td (:count p))
                        (dom/td (:complete p))
                        (dom/td (:percent-complete p))
                        (dom/td (:incomplete p))
                        (dom/td (:imprecise p))
                        (dom/td (:exceedance p))
                        (dom/td (:exceed-high p))
                        (dom/td (:exceed-low p)))))))))

          (dom/h2 nil "Parameter Details")

          (dom/div
            (doall
              (for [{:keys [type count complete incomplete percent-complete incomplete-rs qapp-precision qapp-exceedance
                            exceedance exceed-high exceed-low exceedance-rs imprecise imprecise-rs] :as p} x-params]
                (let [p-nm (:name p)
                      {req-perc :percent req-unit :unit req-range :range req-thresh :threshold} qapp-precision
                      {req-high :high req-low :low} qapp-exceedance]
                  (dom/div :.ui.segment {:key p-nm}
                    (dom/h3 nil (name p-nm))
                    (table :.ui.compact.collapsing.table
                      (dom/tbody
                        (dom/tr (td "Count: ") (td (str count)))
                        (when incomplete
                          (dom/tr (td "Incomplete: ") (td (str incomplete))))
                        (when imprecise
                          (dom/tr (td "Imprecise: ") (td (str imprecise))))
                        (when exceedance
                          (dom/tr (td "Exceedance: ") (td (str exceedance))))))

                    (when incomplete
                      (dom/div
                        (dom/h3 "Incomplete Records:")
                        (table :.ui.striped.very.compact.table
                          (dom/thead
                            (dom/tr
                              (dom/th "Site")
                              (dom/th "Date")
                              (dom/th "Samples")
                              (dom/th "Device")
                              (dom/th "Notes")))
                          (dom/tbody
                            (doall
                              (for [{:keys [db/id svid sample date site notes] :as sv} incomplete-rs]
                                (let [{:keys [min mean stddev vals prec max device range imprecise? incomplete?]} sample]
                                  (dom/tr #js {:key (or id svid)}
                                    (dom/td site)
                                    (dom/td (if (= (type date) js/String)
                                              date
                                              (.toLocaleDateString date "en-US")))
                                    (dom/td {:style {:whiteSpace "nowrap"}} (str-values vals))
                                    (dom/td {:style {:whiteSpace "nowrap"}} device)
                                    (dom/td notes)))))))))

                    (dom/br)

                    (when imprecise
                      (dom/div
                        (dom/h3 "Imprecise Records " (dom/small "(" (prec->str qapp-precision) ")") ":")

                        (table :.ui.striped.very.compact.table
                          (dom/thead
                            (dom/tr
                              (dom/th "Site")
                              (dom/th "Date")
                              (dom/th "Samples")
                              (dom/th "Max")
                              (dom/th "Min")
                              (dom/th "Mean")
                              (when req-range
                                (dom/th "Range"))
                              (when req-unit
                                (dom/th "±"))
                              (when req-perc
                                (dom/th "±%"))
                              (dom/th "Device")
                              (dom/th "Notes")))
                          (dom/tbody
                            (doall
                              (for [{:keys [db/id svid sample date site notes] :as sv} imprecise-rs]
                                (let [{:keys [min mean vals max device range prec-unit prec-percent]} sample]
                                  (dom/tr #js {:key (or id svid)}
                                    (dom/td site)
                                    (dom/td (if (= (type date) js/String)
                                              date
                                              (.toLocaleDateString date "en-US")))
                                    (dom/td {:style {:whiteSpace "nowrap"}} (str-values vals))
                                    (dom/td max)
                                    (dom/td min)
                                    (dom/td mean)
                                    (when req-range
                                      (dom/td range))
                                    (when req-unit
                                      (dom/td prec-unit))
                                    (when req-perc
                                      (dom/td prec-percent))
                                    (dom/td {:style {:whiteSpace "nowrap"}} device)
                                    (dom/td notes)))))))))

                    (dom/br)

                    (when exceedance
                      (dom/div
                        (dom/h3 "Exceedance Records "
                          (dom/small "("
                            (when req-high (str "high: " req-high))
                            (when req-low
                              (str (when req-high ", ") "low: " req-low)) ")"))
                        (table :.ui.striped.very.compact.table
                          (dom/thead
                            (dom/tr
                              (dom/th "Site")
                              (dom/th "Date")
                              (dom/th "Samples")
                              (dom/th "Max")
                              (dom/th "Min")
                              (dom/th "Mean")
                              (dom/th "High?")
                              (dom/th "Low?")
                              (dom/th "Device")
                              (dom/th "Notes")))
                          (dom/tbody
                            (doall
                              (for [{:keys [db/id svid sample date site notes] :as sv} exceedance-rs]
                                (let [{:keys [min mean stddev vals prec max device range too-low? too-high?]} sample]
                                  (dom/tr {:key (or id svid)}
                                    (dom/td site)
                                    (dom/td (if (= (type date) js/String)
                                              date
                                              (.toLocaleDateString date "en-US")))
                                    (dom/td {:style {:whiteSpace "nowrap"}} (str-values vals))
                                    (dom/td max)
                                    (dom/td min)
                                    (dom/td mean)
                                    (dom/td (bool->Y too-high?))
                                    (dom/td (bool->Y too-low?))
                                    (dom/td {:style {:whiteSpace "nowrap"}} device)
                                    (dom/td notes)))))))))))))))))))


(defn parse-year [yr]
  (if-not (= yr "") (js/parseInt yr) yr))

(defonce tac-page-state (atom {}))

(defsc TacReportPage [this {:ui.riverdb/keys [current-agency current-project current-year]
                            :keys                 [tac-report-data project-years] :as props}]
  {:ident         (fn [] [:component/id :tac-report])
   :query         [[:tac-report-data '_]
                   {:project-years (om/get-query ProjectYears)}
                   {[:ui.riverdb/current-agency '_] (comp/get-query looks/agencylookup-sum)}
                   {[:ui.riverdb/current-project '_] (comp/get-query looks/projectslookup-sum)}
                   [:ui.riverdb/current-year '_]
                   [df/marker-table ::tac]]
   :initial-state {:tac-report-data nil
                   :project-years   {}}
   :route-segment ["qc-report"]}

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
                                     (println "LOADING QC REPORT" AgencyCode ProjectID current-year)
                                     (f/load this :tac-report-data nil {:params               {:agency  AgencyCode
                                                                                               :project ProjectID
                                                                                               :year    current-year}
                                                                        :post-mutation-params {:order :asc}
                                                                        :post-mutation        `rm/process-tac-report
                                                                        :marker               ::tac}))}
              (str "Generate QC Report for " AgencyCode " " current-year)))))

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