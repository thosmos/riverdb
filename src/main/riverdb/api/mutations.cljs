(ns riverdb.api.mutations
  (:require
    [com.fulcrologic.fulcro.algorithms.data-targeting :as dt :refer [process-target]]
    [com.fulcrologic.fulcro.algorithms.normalize :as norm]
    [com.fulcrologic.fulcro.algorithms.normalized-state :refer [remove-ident]]
    [com.fulcrologic.fulcro.application :as fapp]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.semantic-ui.collections.message.ui-message :refer [ui-message]]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.networking.http-remote :as http-remote]
    [theta.log :as log :refer [debug info]]
    [riverdb.application :refer [SPA]]
    [riverdb.ui.lookups :as looks]
    [riverdb.util :as util :refer [sort-maps-by]]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.mutations :as fm]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.dom :as dom :refer [div]]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]
    [com.fulcrologic.rad.routing :as rroute]
    [com.fulcrologic.rad.routing.history :as hist]
    [com.fulcrologic.rad.routing.html5-history :as hist5 :refer [url->route apply-route!]]
    [riverdb.ui.routes :as routes]
    [com.rpl.specter :as sp]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
    [edn-query-language.core :as eql]))

;;;  CLIENT

(defn set-root-key* [state key value]
  (assoc state key value))

(defmutation set-root-key
  "generic mutation to set a root key"
  [{:keys [key value]}]
  (action [{:keys [state]}]
    (do
      (debug "SET ROOT KEY" key value)
      (swap! state set-root-key* key value))))

(defn set-root-key! [k v]
  (comp/transact! SPA `[(set-root-key {:key ~k :value ~v})]))

(defmutation merge-ident [{:keys [ident props]}]
  (action [{:keys [state]}]
    (swap! state merge/merge-ident ident props)))

(defn merge-ident! [ident props]
  (comp/transact! SPA `[(merge-ident {:ident ~ident :props ~props})]))

(defn merge-idents* [state ident-k ident-v coll & targets]
  (debug "MERGE IDENT" ident-k ident-v targets)
  (reduce
    (fn [st m]
      (let [ident [ident-k (get m ident-v)]
            st    (merge/merge-ident st ident m)
            st'   (if targets
                    (apply dt/integrate-ident* st ident targets)
                    st)]
        st'))
    state coll))

(defmutation merge-idents
  "merge a collection of maps that conform to the ident of the comp c"
  [{:keys [ident-k ident-v coll targets]}]
  (action [{:keys [state]}]
    (debug "MERGE IDENTS" ident-k ident-v targets)
    (swap! state (fn [st]
                   (reduce
                     (fn [st m]
                       (let [ident [ident-k (get m ident-v)]
                             st    (merge/merge-ident st ident m)
                             st'   (if targets
                                     (apply dt/integrate-ident* st ident targets)
                                     st)]
                         st'))
                     st coll)))))

(defn merge-idents! [ident-k ident-v coll & targets]
  (comp/transact! SPA `[(merge-idents {:ident-k ~ident-k :ident-v ~ident-v :coll ~coll :targets ~targets})]))


;(defmutation attempt-login
;  "Fulcro mutation: Attempt to log in the user. Triggers a server interaction to see if there is already a cookie."
;  [{:keys [uid]}]
;  (action [{:keys [state]}]
;    (swap! state assoc
;      :current-user {:id uid :name ""}
;      :server-down false))
;  (remote [env] true))

;(defmutation server-down
;  "Fulcro mutation: Called if the server does not respond so we can show an error."
;  [p]
;  (action [{:keys [state]}] (swap! state assoc :server-down true)))

(defmutation hide-server-error
  ""
  [p]
  (action [{:keys [state]}]
    (swap! state dissoc :fulcro/server-error)))

(defmutation clear-new-user
  "Fulcro mutation: Used for returning to the sign-in page from the login link. Clears the form."
  [ignored]
  (action [{:keys [state]}]
    (let [uid        (util/uuid)
          new-user   {:uid uid :name "" :password "" :password2 ""}
          user-ident [:user/by-id uid]]
      (swap! state (fn [s]
                     (-> s
                       (assoc :user/by-id {}) ; clear all users
                       (assoc-in user-ident new-user)
                       (assoc-in [:new-user :page :form] user-ident)))))))

(def get-year-fn (fn [ui-year years]
                   (if (some #{ui-year} years)
                     ui-year
                     (when-let [year (first years)]
                       year))))

(defsc Agency [_ _]
  {:ident [:org.riverdb.db.agencylookup/gid :db/id]
   :query [:db/id]})
(defsc Project [_ _]
  {:ident [:org.riverdb.db.projectslookup/gid :db/id],
   :query [:db/id
           :riverdb.entity/ns
           :projectslookup/Active
           :projectslookup/ProjectID
           :projectslookup/Name
           {:projectslookup/AgencyRef (comp/get-query Agency)}]})

(defmutation process-project-years [{:keys [desired-route proj-k]}]
  (action [{:keys [state]}]
    (log/info (clojure.string/upper-case "process-project-years"))
    (let [agency-project-years (get-in @state [:component/id :proj-years :agency-project-years])
          _                    (log/info "agency-project-years" agency-project-years)
          current-project      (get @state :ui.riverdb/current-project)
          current-year         (get @state :ui.riverdb/current-year)
          proj-k               (or proj-k
                                 (if current-project
                                   (keyword (:projectslookup/ProjectID current-project))
                                   (ffirst agency-project-years)))
          project              (when proj-k
                                 (get-in agency-project-years [proj-k :project]))
          sites                (when proj-k
                                 (get-in agency-project-years [proj-k :sites]))
          years                (when proj-k
                                 (get-in agency-project-years [proj-k :years]))
          year                 (when years
                                 (get-year-fn current-year years))]
      (swap! state
        (fn [st]
          (cond-> st
            project
            (merge/merge-component Project project :replace [:ui.riverdb/current-project])
            sites
            (->
              (set-root-key* :ui.riverdb/current-project-sites [])
              (merge-idents* :org.riverdb.db.stationlookup/gid :db/id sites
                :append [:ui.riverdb/current-project-sites]))
            year
            (set-root-key* :ui.riverdb/current-year year))))
      (when desired-route
        (info "ROUTE TO desired-route" desired-route)
        (let [params (:params desired-route)
              rad?   (:_rp_ params)]
          (if rad?
            (do
              (log/info "RAD ROUTE" desired-route)
              (hist5/apply-route! SPA desired-route))
            (do
              (log/info "NON-RAD ROUTE" desired-route)
              (dr/change-route! SPA (:route desired-route))
              (hist/replace-route! SPA (:route desired-route) (:params desired-route)))))))))

(defmutation process-tac-report
  "TAC Report post process"
  [p]
  (action [{:keys [state]}]
    (do
      (debug "TAC POST PROCESS" p)
      (swap! state update-in [:tac-report-data :no-results-rs] sort-maps-by [:site :date]))))
;(swap! state update-in [:tac-report-data :results-rs] sort-maps-by [:date :site])
;(swap! state update-in [:tac-report-page :page :field-count] inc)

(defmutation clear-tac-report
  "clear :tac-report-data from local state"
  [p]
  (action [{:keys [state]}]
    (do
      (debug "CLEAR TAC DATA")
      (swap! state dissoc :tac-report-data))))

(defmutation set-current-agency
  ""
  [{:keys [agency key]}]
  (action [{:keys [state]}]
    (do
      (debug "SET CURRENT AGENCY" key agency)
      (swap! state assoc key agency))))


(defmutation clear-agency-project-years
  ""
  [{:keys [ident]}]
  (action [{:keys [state]}]
    (do
      (debug "CLEAR AGENCY PROJECT YEARS")
      (swap! state update-in ident (fn [st] (-> st
                                              (dissoc :agency-project-years)
                                              (dissoc :ui/project-code)
                                              (dissoc :ui/proj-year)))))))

(defmutation process-all-years
  "All Years post process"
  [p]
  (action [{:keys [state]}]
    (do
      (debug "POST PROCESS ALL YEARS" p)
      (swap! state update :all-years-data sort-maps-by [:date]))))

(defmutation sort-years
  [p]
  (action [{:keys [state]}]
    (do
      (debug "SORT YEARS")
      (swap! state update :all-sitevisit-years (partial sort >)))))

(defmutation set-project
  [p]
  (action [{:keys [state]}]
    (let [prj (:ui/project-code p)]
      (debug "MUTATION set-project" prj)
      (swap! state assoc-in [:myvals :project] prj))))

(defmutation process-agency-project-years
  [p]
  (action [{:keys [state]}]
    (debug "process-agency-project-years")
    (swap! state update :agency-project-years
      (fn [prjs]
        (reduce-kv
          (fn [prjs k v]
            (assoc-in prjs [k :years] (vec (sort > (:years v)))))
          prjs
          prjs)))))

(defmutation refresh
  [p]
  (action [_]
    (debug "NOP just to refresh the current component")))

(defmutation update-report-year [p]
  (action [{:keys [state]}]
    (let [year (:year p)
          year (if-not (= year "") (js/parseInt year) year)]
      (debug "MUTATE REPORT YEAR" year)
      (swap! state assoc-in [:tac-report-page :page :ui/year] year))))

(defn sort-ident-list-by*
  "Sort the idents in the list path by the indicated field. Returns the new app-state."
  ([state path sort-fn]
   (sort-ident-list-by* state path sort-fn nil))
  ([state path sort-fn ident-key]
   (let [idents       (get-in state path [])
         ident-key    (or ident-key (ffirst idents))
         items        (map (fn [ident] (get-in state ident)) idents)
         sorted-items (sort-by sort-fn items)
         new-idents   (mapv (fn [item] [ident-key (:db/id item)]) sorted-items)]
     ;(debug "SORTED" new-idents)
     (assoc-in state path new-idents))))

(defmutation sort-ident-list-by
  "sorts a seq of maps at target location by running sort-fn on the maps"
  [{:keys [idents-path sort-fn ident-key]}]
  (action [{:keys [state]}]
    (debug "SORTING SITES")
    (swap! state sort-ident-list-by* idents-path sort-fn ident-key)))



(defmutation save-project [{:keys [id diff]}]
  (action [{:keys [state]}]
    (swap! state (fn [s]
                   (-> s
                     ; update the pristine state
                     (fs/entity->pristine* [:org.riverdb.db.projectslookup/gid id])))))
  (remote [env] true))

(defmutation reset-form [{:keys [ident]}]
  (action [{:keys [state]}]
    (debug "RESET FORM" ident "DIRTY" (get-in @state ident) "PRISTINE" (-> @state
                                                                         (fs/pristine->entity* ident)
                                                                         (get-in ident)))
    (swap! state fs/pristine->entity* ident)))

(defmutation cancel-new-form! [{:keys [ident]}]
  (action [{:keys [state]}]
    (debug "MUTATION cancel-new-form!" ident)))


(defmutation set-enum [{:keys [key value]}]
  (action [{:keys [state component]}]
    (let [comp-ident (comp/get-ident component)]
      (debug "set-enum" comp-ident key value)
      (if (eql/ident? value)
        (swap! state
          #(-> %
             (assoc-in (conj comp-ident key) value)
             (fs/mark-complete* comp-ident key)))
        (log/error "set-enum requires an ident as a value")))))

(defn set-enum! [this key value]
  (comp/transact! this [(set-enum {:key key :value value})]))

(defn clear-tx-result* [state]
  ;(debug "CLEAR TX RESULT")
  (assoc state :root/tx-result {}))
(defmutation clear-tx-result [{:keys [] :as params}]
  (action [{:keys [state]}]
    (swap! state clear-tx-result*)))
(defn clear-tx-result! []
  (comp/transact! SPA `[(clear-tx-result)]))

(defn show-tx-result* [state result]
  ;(debug "SHOW TX RESULT")
  (when-not (:error result)
    (js/setTimeout clear-tx-result! 2000))
  (assoc state :root/tx-result result))
(defmutation show-tx-result [result]
  (action [{:keys [state]}]
    (swap! state (fn [s] (assoc-in s [:root/tx-result] result)))))
(defn show-tx-result! [result]
  (comp/transact! SPA `[(show-tx-result ~result)]))

(defn set-create* [state ident creating]
  (assoc-in state (conj ident :ui/create) creating))

(defn set-saving* [state ident busy]
  (assoc-in state (conj ident :ui/saving) busy))
(defmutation set-saving [{:keys [ident busy]}]
  (action [{:keys [state]}]
    (swap! state set-saving* ident busy)))
(defn set-saving! [ident busy]
  (comp/transact! SPA `[(set-saving {:ident ~ident :busy ~busy})]))

(comp/defsc TxResult [this {:keys [result msgs error com.wsscode.pathom.core/reader-error]}]
  {:query             [:result
                       :msgs
                       :error
                       :com.wsscode.pathom.core/reader-error
                       :sticky]
   :initial-state     {:result                               nil
                       :msgs                                 nil
                       :error                                nil
                       :com.wsscode.pathom.core/reader-error nil
                       :sticky                               false}
   :componentDidMount (fn [this]
                        (let [props (comp/props this)]
                          ;(debug "DID MOUNT TxResult" "props" props)
                          (when (and
                                  (not (:error props))
                                  (not (:sticky props)))
                            (js/setTimeout clear-tx-result! 2000))))}
  (let [error (or error reader-error)]
    ;(debug "TxResult" "error" error "result" result)
    (div {:style {:position "fixed" :top "50px" :left 0 :right 0 :maxWidth 500 :margin "auto" :zIndex 1000}}
      (if error
        (ui-message {:error true}
          (div :.content {}
            (div :.ui.right.floated.negative.button
              {:onClick clear-tx-result!}
              "OK")
            (div :.header {} "Error")
            (div :.horizontal.items
              (div :.item error))
            (when msgs
              (map-indexed
                (fn [i msg]
                  (dom/p {:key i} msg))
                msgs))))
        (ui-message {:success true :onDismiss clear-tx-result!}
          (div :.content {}
            (div :.header {} "Success")
            (dom/p {} result)
            (when msgs
              (map-indexed
                (fn [i msg]
                  (dom/p {:key i} msg))
                msgs))))))))

(def ui-tx-result (comp/factory TxResult))

(defn remove-ident*
  "Removes an ident, if it exists, from a list of idents in app state. This
  function is safe to use within mutations."
  [state-map ident path-to-idents]
  {:pre [(map? state-map)]}
  (let [new-list (fn [old-list]
                   (vec (filter #(not= ident %) old-list)))]
    (update-in state-map path-to-idents new-list)))

(fm/defmutation delete-sample [{:keys [sv-ident sa-ident]}]
  (action [{:keys [state]}]
    (debug "MUTATION delete sample" sv-ident sa-ident)
    (swap! state
      (fn [s]
        (let [sas-path (conj sv-ident :sitevisit/Samples)
              _        (debug "samples BEFORE" (get-in s sas-path))

              res      (-> s
                         (remove-ident* sa-ident sas-path)
                         (fs/mark-complete* sv-ident :sitevisit/Samples))
              _ (debug "samples AFTER" (get-in res sas-path))]
          res)))))

(defn del-sample [sv-ident sa-ident]
  (comp/transact! SPA `[(delete-sample ~{:sv-ident sv-ident :sa-ident sa-ident})]))

(defmutation set-pristine
  "changes a form's current state to pristine"
  [{:keys [ident]}]
  (action [{:keys [state]}]
    (swap! state fs/entity->pristine* ident)))
(defn set-pristine! [ident]
  (comp/transact! SPA `[(set-pristine {:ident ~ident})]))

(defn get-new-ident [env ident]
  (if-let [new-ident-v (get-in env [:tempid->realid (second ident)])]
    [(first ident) new-ident-v]
    ident))

(defmutation save-entity
  "saves an entity diff to the backend Datomic DB"
  [{:keys [ident diff delete] :as params}]
  (action [{:keys [state]}]
    (if delete
      (debug "DELETE ENTITY" ident)
      (debug "SAVE ENTITY" ident))
    (swap! state set-saving* ident true))
  (remote [{:keys [state] :as env}]
    (debug "SAVE ENTITY REMOTE" (:ui.riverdb/current-agency @state))
    (-> env
      (update-in [:ast :params] #(-> %
                                   (dissoc :post-mutation)
                                   (dissoc :post-params)
                                   (dissoc :success-msg)
                                   (assoc :agency (get-in @state [:ui.riverdb/current-agency 1]))))
      ;; remove reverse keys
      (update-in [:ast :params :diff]
        (fn [diff]
          (let [new-diff (sp/transform
                           [sp/ALL sp/LAST sp/ALL]
                           #(when (not (and (keyword? (first %)) (= "_" (first (name (first %)))))) %)
                           diff)]
            (debug "DIFF" new-diff)
            new-diff)))
      (fm/returning TxResult)
      (fm/with-target [:root/tx-result])))
  (ok-action [{:keys [state result] :as env}]
    (debug "OK ACTION" "IDENT" ident "REF" (:ref env) "result" result "(comp/get-ident (:component env))" (comp/get-ident (:component env)))
    (debug ":tempid->realid" (:tempid->realid env))
    (debug "TRANSACTED AST" (:transacted-ast env))
    (if-let [ok-err (get-in result [:body `save-entity :error])]
      (do
        (debug "OK ERROR" ok-err)
        (swap! state
          (fn [s]
            (-> s
              (set-saving* ident false)))))
      (try
        (let [{:keys [post-mutation post-params success-msg delete]} (get-in env [:transacted-ast :params])
              ident (get-new-ident env ident)]
          (if delete
            (swap! state (fn [s] (-> s
                                   (show-tx-result* {:result (or success-msg "Delete Succeeded")})
                                   (set-saving* ident false)
                                   (dissoc ident))))
            (swap! state
              (fn [s]
                (-> s
                  (fs/entity->pristine* ident)
                  (show-tx-result* {:result (or success-msg "Save Succeeded")})
                  (set-saving* ident false)
                  (set-create* ident false)))))
          (when post-mutation
            (let [tempid (get-in env [:transacted-ast :params :ident 1])
                  new-id (get-in env [:tempid->realid tempid])
                  params (if new-id
                           (assoc post-params :new-id new-id)
                           params)
                  txd    `[(~post-mutation ~params)]]
              (comp/transact! SPA txd))))
        (catch js/Object ex (let [ident (get-new-ident env ident)]
                              (log/error "OK Action handler failed: " ex)
                              (swap! state
                                (fn [st]
                                  (-> st
                                    (set-saving* ident false)
                                    (assoc-in [:root/tx-result :error] (str "TX Result Handler failed: " ex))))))))))

  (error-action [{:keys [app ref result state] :as env}]
    (log/info "Mutation Error Result " ident diff result)
    (swap! state
      (fn [s]
        (-> s
          (set-saving* ident false)
          (#(if-let [err (get-in result [:error-text])]
              (assoc-in % [:root/tx-result :error] err)
              %)))))))

(defsc UploadResult [this props]
  {:query [:errors :msgs]}
  (debug "UploadResult" props))

(defmutation upload-files [{:keys [config progress-target] :as params}]
  (action [{:keys [state]}]
    (debug "UPLOAD FILES" config))
  (remote [env]
    (-> env
      (fm/returning UploadResult)))
  (progress-action [{:keys [progress state] :as env}]
    ;(debug "PROGRESS" progress)
    (swap! state assoc-in progress-target (http-remote/overall-progress env)))
  (ok-action [{:keys [state result] :as env}]
    (let [ident (comp/get-ident (:component env))
          {:keys [tempids errors msgs] :as res} (get-in result [:body `upload-files])]
      (debug "UPLOAD OK" ident res result)
      (if (seq errors)
        (do
          (log/error "OK ERROR" (first errors))
          (swap! state
            (fn [s]
              (-> s
                (assoc-in [:root/tx-result :error] (str
                                                     (if (= (count errors) 1)
                                                       (first errors)
                                                       errors)))
                (assoc-in (conj ident :ui/import-error?) true)
                (cond->
                  (seq msgs)
                  (assoc-in [:root/tx-result :msgs] msgs))))))
        (swap! state
          (fn [s]
            (-> s
              (assoc-in [:root/tx-result :result] "Import completed")
              (cond->
                (seq msgs)
                (->
                  (assoc-in [:root/tx-result :msgs] msgs)
                  (assoc-in [:root/tx-result :sticky] true)))))))))
  (error-action [{:keys [app ref result state] :as env}]
    (let [ident        (comp/get-ident (:component env))
          reader-error (get-in result [:body `upload-files :com.wsscode.pathom.core/reader-error])
          error-text   (get-in result [:error-text])]

      (log/error "UPLOAD FILES ERROR" ident result)
      (when (or reader-error error-text)
        (swap! state
          (fn [s]
            (-> s
              (assoc-in [:root/tx-result :error] (or reader-error error-text))
              (assoc-in (conj ident :ui/import-error?) true)
              (cond->))))))))


