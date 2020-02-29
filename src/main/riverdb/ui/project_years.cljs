(ns riverdb.ui.project-years
  (:require
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h3 button table tr td]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [com.fulcrologic.fulcro.mutations :as fm]
    [riverdb.api.mutations :as rm]
    [riverdb.util :refer [sort-maps-by with-index]]
    [riverdb.ui.session :refer [Session]]
    [riverdb.ui.lookups :as looks :refer [stationlookup-sum]]
    [theta.log :as log :refer [debug info]]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]))


(defsc ProjectYears [this {:keys                 [agency-project-years]
                           :riverdb.ui.root/keys [current-agency current-project current-year]}]
  {:ident             (fn [] [:component/id :proj-years])
   :query             [:agency-project-years
                       {[:riverdb.ui.root/current-agency '_] (comp/get-query looks/agencylookup-sum)}
                       {[:riverdb.ui.root/current-project '_] (comp/get-query looks/projectslookup-sum)}
                       [:riverdb.ui.root/current-year '_]]
   :initial-state     {:agency-project-years nil}
   ;; FIXME update :agency-project-years when [:riverdb.ui.root/current-agency '_] changes
   :componentDidMount (fn [this]
                        (debug "PROJECT YEARS MOUNTED")
                        (let [{:riverdb.ui.root/keys [current-agency]
                               :keys                 [agency-project-years]} (comp/props this)
                              agencyCode (:agencylookup/AgencyCode current-agency)]
                          (when (and agencyCode (not agency-project-years))
                            (f/load this :agency-project-years nil
                              {:params {:agencies [agencyCode]}
                               :target [:component/id :proj-years :agency-project-years]}))))}

  (let [get-year-fn (fn [ui-year years]
                      (if (some #{ui-year} years)
                        ui-year
                        (when-let [year (first years)]
                          year)))

        update-em   (fn [proj-k]
                      {:pre [(keyword? proj-k)]}
                      (debug "UPDATE SELECTED PROJECT" proj-k)
                      (let [project (when proj-k
                                      (get-in agency-project-years [proj-k :project]))
                            sites   (when proj-k
                                      (get-in agency-project-years [proj-k :sites]))
                            years   (when proj-k
                                      (get-in agency-project-years [proj-k :years]))
                            year    (when years
                                      (get-year-fn current-year years))]
                        (when project
                          (merge/merge-component! this looks/projectslookup-sum project :replace [:riverdb.ui.root/current-project]))
                          ;(rm/merge-ident! (comp/get-ident looks/projectslookup-sum project) project
                          ;  :replace [:riverdb.ui.root/current-project]))
                        (when sites
                          (rm/set-root-key! :riverdb.ui.root/current-project-sites [])
                          (rm/merge-idents! :org.riverdb.db.stationlookup/gid :db/id sites
                            :append [:riverdb.ui.root/current-project-sites]))
                        (when year
                          (rm/set-root-key! :riverdb.ui.root/current-year year)))
                      proj-k)

        proj-k      (if current-project
                      (keyword (:projectslookup/ProjectID current-project))
                      (let [proj-k (ffirst agency-project-years)]
                        (when proj-k
                          (update-em proj-k))))

        proj-nm     (when proj-k
                      (name proj-k))
        proj-name   (when proj-k
                      (get-in agency-project-years [proj-k :name]))
        agencyCode  (when proj-k
                      (get-in agency-project-years [proj-k :agency]))
        years       (when proj-k
                      (get-in agency-project-years [proj-k :years]))
        proj-year   (get-year-fn current-year years)]

    (debug "RENDER ProjectYears" proj-nm proj-year)
    (when current-agency
      (debug ":agency-project-years" agencyCode proj-nm proj-name proj-year)
      (dom/span {:style {}}
        (dom/span {:key "project" :style {:margin 10}}
          "Project: "
          (dom/select {:value    (or proj-nm "")
                       :onChange #(let [proj-nm (.. % -target -value)
                                        proj-k  (keyword proj-nm)]
                                    (when proj-k
                                      (update-em proj-k)))}
            (doall
              (for [[prj-k prj] agency-project-years]
                (let [prj-nm   (name prj-k)
                      prj-name (:name prj)]
                  (dom/option {:value prj-nm :key prj-nm} prj-name))))))
        (when (and proj-k years)
          (dom/span {:key "year" :style {:margin 10}}
            " Year: "
            (dom/select {:value    (or proj-year "")
                         :onChange #(let [yr (.. % -target -value)]
                                      (rm/set-root-key! :riverdb.ui.root/current-year yr))}
              (doall
                (for [yr years]
                  (dom/option {:value (str yr) :key yr} (str yr)))))))))))


(def ui-project-years (comp/factory ProjectYears))
