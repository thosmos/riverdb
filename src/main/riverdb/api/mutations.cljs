(ns riverdb.api.mutations
  (:require
    [com.fulcrologic.fulcro.algorithms.data-targeting :as dt :refer [process-target]]
    [com.fulcrologic.fulcro.algorithms.normalize :as norm]
    [com.fulcrologic.fulcro.application :as fa]
    [com.fulcrologic.fulcro.mutations :refer [defmutation]]
    [com.fulcrologic.semantic-ui.collections.message.ui-message :refer [ui-message]]
    [com.fulcrologic.fulcro.components :as om]
    [theta.log :as log :refer [debug]]
    [riverdb.application :refer [SPA]]
    [riverdb.ui.lookups :as looks]
    [riverdb.util :as util :refer [sort-maps-by]]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.mutations :as fm]
    [com.fulcrologic.fulcro.algorithms.data-targeting :as targeting]
    [com.fulcrologic.fulcro.dom :as dom :refer [div]]
    [com.fulcrologic.fulcro.algorithms.tempid :as tempid]))

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
  (om/transact! SPA `[(set-root-key {:key ~k :value ~v})]))

(defn merge-ident* [state ident props & targets]
  (debug "MERGE IDENT" ident props targets)
  (let [state (merge/merge-ident state ident props)
        st'   (if targets
                (apply dt/integrate-ident* state ident targets)
                state)]
    st'))

(defmutation merge-ident [{:keys [ident props targets]}]
  (action [{:keys [state]}]
    (if targets
      (swap! state merge-ident* ident props targets)
      (swap! state merge-ident* ident props))))

(defn merge-ident!
  "Merge some data at an ident"
  [ident props & targets]
  (debug "merge-ident!" ident "targets:" targets)
  (let [params {:ident ident :props props}
        params (if targets
                 (assoc params :targets targets)
                 params)]
    (om/transact! SPA `[(merge-ident ~params)])))

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
  (om/transact! SPA `[(merge-idents {:ident-k ~ident-k :ident-v ~ident-v :coll ~coll :targets ~targets})]))

(defmutation set-ident-value
  "generic mutation to set an ident value"
  [{:keys [ident value]}]
  (action [{:keys [state]}]
    (do
      (debug "SET IDENT VALUE" ident value)
      (swap! state assoc-in ident value))))

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

(defmutation process-project-years [_]
  (action [{:keys [state]}]
    (debug (clojure.string/upper-case "process-project-years"))
    (let [agency-project-years (get-in @state [:component/id :proj-years :agency-project-years])
          _                    (debug "agency-project-years" agency-project-years)
          current-project      (get @state :riverdb.ui.root/current-project)
          current-year         (get @state :riverdb.ui.root/current-year)
          proj-k               (if current-project
                                 (keyword (:projectslookup/ProjectID current-project))
                                 (ffirst agency-project-years))
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
            (merge-ident* (comp/get-ident looks/projectslookup-sum project) project
              :replace [:riverdb.ui.root/current-project])
            sites
            (->
              (set-root-key* :riverdb.ui.root/current-project-sites [])
              (merge-idents* :org.riverdb.db.stationlookup/gid :db/id sites
                :append [:riverdb.ui.root/current-project-sites]))
            year
            (set-root-key* :riverdb.ui.root/current-year year)))))))


;(defmutation login-complete
;  "Fulcro mutation: Attempted login post-mutation the update the UI with the result. Requires the app-root of the mounted application
;  so routing can be started."
;  [{:keys [app-root]}]
;  (action [{:keys [component state]}]
;    ; idempotent (start routing)
;    (when app-root
;      (r/start-routing app-root))
;    (let [current-user (get-in @state (get @state :current-user))
;          logged-in?   (and
;                         (-> current-user :login/error not)
;                         (-> current-user :uid pos-int?))]
;      (let [desired-page (get @state :loaded-uri (or (and @r/history (pushy/get-token @r/history)) r/MAIN-URI))
;            desired-page (if (= r/LOGIN-URI desired-page)
;                           r/MAIN-URI
;                           desired-page)]
;        (swap! state assoc :ui/ready? true) ; Make the UI show up. (flicker prevention)
;        (when logged-in?
;          (swap! state update-in [:login :page] assoc :ui/username "" :ui/password "")
;          (swap! state assoc :logged-in? true)
;          (if (and @r/history @r/use-html5-routing)
;            (pushy/set-token! @r/history desired-page)
;            (swap! state ur/update-routing-links {:handler :main})))))))
;
;(defmutation logout
;  "Fulcro mutation: Removes user identity from the local app and asks the server to forget the user as well."
;  [p]
;  (action [{:keys [state]}]
;    (swap! state assoc :current-user nil :logged-in? false :user/by-id nil
;      :tac-report-data nil :dataviz-data nil)
;    ;(swap! state update
;    ;  (fn [st]
;    ;    (-> st
;    ;      (assoc :logged-in? false)
;    ;      (dissoc :current-user :user/by-id :tac-report-data :dataviz-data))))
;    (when (and @r/use-html5-routing @r/history)
;      (pushy/set-token! @r/history r/LOGIN-URI)))
;  (remote [env] true))


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

(defmutation process-dataviz-data
  [p]
  (action [{:keys [state]}]
    (do
      (debug "PROCESS DATAVIZ DATA")
      (swap! state update-in [:dataviz-data :results-rs] sort-maps-by [:date :site]))))

(defmutation update-report-year [p]
  (action [{:keys [state]}]
    (let [year (:year p)
          year (if-not (= year "") (js/parseInt year) year)]
      (debug "MUTATE REPORT YEAR" year)
      (swap! state assoc-in [:tac-report-page :page :ui/year] year))))

(defn sort-ident-list-by*
  "Sort the idents in the list path by the indicated field. Returns the new app-state."
  [state path ident-key sort-fn]
  (let [idents       (get-in state path [])
        items        (map (fn [ident] (get-in state ident)) idents)
        sorted-items (sort-by sort-fn items)
        new-idents   (mapv (fn [item] [ident-key (:db/id item)]) sorted-items)]
    (debug "SORTED" new-idents)
    (assoc-in state path new-idents)))

(defmutation sort-ident-list-by
  "sorts a seq of maps at target location by running sort-fn on the maps"
  [{:keys [idents-path ident-key sort-fn]}]
  (action [{:keys [state]}]
    (debug "SORTING SITES")
    (swap! state sort-ident-list-by* idents-path ident-key sort-fn)))




(defmutation save-project [{:keys [id diff]}]
  (action [{:keys [state]}]
    (swap! state (fn [s]
                   (-> s
                     ; update the pristine state
                     (fs/entity->pristine* [:org.riverdb.db.projectslookup/gid id])))))
  (remote [env] true))

(defmutation reset-form [{:keys [ident]}]
  (action [{:keys [state]}]
    (swap! state fs/pristine->entity* ident)))


(defn clear-tx-result* [state]
  (debug "CLEAR TX RESULT")
  (assoc state :root/tx-result {}))
(defmutation clear-tx-result [{:keys [] :as params}]
  (action [{:keys [state]}]
    (swap! state clear-tx-result*)))
(defn clear-tx-result! []
  (comp/transact! SPA `[(clear-tx-result)]))

(defn show-tx-result* [state result]
  (debug "SHOW TX RESULT")
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

(comp/defsc TxResult [this {:keys [result error com.wsscode.pathom.core/reader-error]}]
  {:query         [:result
                   :error
                   :com.wsscode.pathom.core/reader-error]
   :initial-state {:result                               nil
                   :error                                nil
                   :com.wsscode.pathom.core/reader-error nil}}
  (let [error (or error reader-error)]
    (debug "TxResult" error)
    (div {:style {:position "fixed" :top "50px" :left 0 :right 0 :maxWidth 500 :margin "auto" :zIndex 1000}}
      (if error
        (ui-message {:error true}
          (div :.content {}
            (div :.ui.right.floated.negative.button
              {:onClick clear-tx-result!}
              "OK")
            (div :.header {} "Server Error")
            (div :.horizontal.items
              (div :.item error))))
        (ui-message {:success true :onDismiss clear-tx-result!}
          (div :.content {}
            (div :.header {} "Success")
            (dom/p {} result)))))))

(def ui-tx-result (comp/factory TxResult))


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
  [{:keys [ident diff] :as params}]
  (action [{:keys [state]}]
    (debug "SAVE ENTITY DIFF" ident diff)
    (swap! state set-saving* ident true))
  (remote [env]
    (update-in env [:ast :params] #(-> %
                                     (dissoc :post-mutation)
                                     (dissoc :post-params))))
  (ok-action [{:keys [state] :as env}]
    (debug "OK ACTION" "IDENT" ident "REF" (:ref env) "(comp/get-ident (:component env))" (comp/get-ident (:component env)))
    (debug ":tempid->realid" (:tempid->realid env))
    (debug "TRANSACTED AST"  (:transacted-ast env))
    (try
      (let [{:keys [post-mutation post-params]} (get-in env [:transacted-ast :params])
            ident (get-new-ident env ident)]
        (swap! state
          (fn [s]
            (-> s
              (fs/entity->pristine* ident)
              (set-saving* ident false)
              (set-create* ident false)
              (show-tx-result* {:result "Save Succeeded"}))))
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
                                  (show-tx-result* {:error (str "TX Result Handler failed: " ex)}))))))))



  (error-action [{:keys [app ref result state] :as env}]
    (log/info "Mutation Error Result " ident diff result)
    (swap! state
      (fn [s]
        (-> s
          (set-saving* ident false)
          (show-tx-result* (get-in result [:body `save-entity])))))))
