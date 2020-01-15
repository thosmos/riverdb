;(ns rimdb-ui.api.read
;  (:require
;    [rimdb-ui.api.mutations :as m]
;    [fulcro.server :refer [defquery-entity defquery-root]]
;    [taoensso.timbre :as timbre]
;    [rimdb-ui.api.user-db :as users]
;    [fulcro.server :as server]
;    [datomic.api :as d]
;    [rimdb-ui.api.rimdb :as r]
;    [rimdb-ui.api.tac-report :as tac]))
;
;;; SERVER READ IMPLEMENTATION. We're using `fulcro-parser`. You can either use defmulti on the multimethods
;;; (see fulcro.server defmulti declarations) or the defquery-* helper macros.
;(defn attempt-login
;  "The `request` will be available in the `env` of action. This is a normal Ring request (session is under :session)."
;  [user-db request username password]
;  (let [user        (users/get-user user-db username password)
;        real-uid    (:uid user)
;        secure-user (select-keys user [:name :uid :email :agency])]
;    (Thread/sleep 300) ; pretend it takes a while to auth a user
;    (if user
;      (do
;        (timbre/info "Logged in user " user)
;        (server/augment-response secure-user
;          (fn [resp] (assoc-in resp [:session :uid] real-uid))))
;      (do
;        (timbre/error "Invalid login using email: " username)
;        {:uid :no-such-user :name "Unkown" :email username :login/error "Invalid credentials"}))))
;
;(defquery-root :current-user
;  "Answer the :current-user query. This is also how you log in (passing optional username and password)"
;  (value [{:keys [request user-db]} {:keys [username password]}]
;    (if (and username password)
;      (attempt-login user-db request username password)
;      (let [resp (-> (users/get-user user-db (-> request :session :uid))
;                   (select-keys [:uid :name :email :agency]))]
;        (timbre/info "Current user: " resp)
;        resp))))
;
;(defquery-root :tac-report-data
;  "Generate a TAC Report"
;  (value [{:keys [request datomic] :as env} params]
;    (do
;      (timbre/info "QUERY :tac-report-data PARAMS: " params)
;      (if (:csv params)
;        (tac/get-annual-report-csv (:csv params))
;        (tac/get-annual-report (d/db (:cx datomic)) (:agency params) (:project params) (:year params))))))
;
;(defquery-root :all-sitevisit-years
;  "get a list of all years with sitevisits"
;  (value [{:keys [request datomic] :as env} params]
;    (do
;      (timbre/info "QUERY :all-sitevisit-years")
;      (tac/get-sitevisit-years (d/db (:cx datomic)) (:agency params)))))
;
;(defquery-root :agency-project-years
;  (value [{:keys [request datomic] :as env} params]
;    (do
;      (timbre/info "QUERY :agency-project-years")
;      (tac/get-agency-project-years (d/db (:cx datomic)) (:agency params)))))
;
;(defquery-root :agency-projects
;  "get a list of agency's projects"
;  (value [{:keys [request datomic] :as env} params]
;    (do
;      (timbre/info "QUERY :agency-projects")
;      (tac/get-agency-projects (d/db (:cx datomic)) (:agency params)))))
;
;
;(defquery-root :all-years-data
;  "get a CSV of all years"
;  (value [{:keys [request datomic] :as env} params]
;    (do
;      (timbre/info "QUERY :all-years-data")
;      (tac/csv-all-years (d/db (:cx datomic)) (:agency params) (:token params)))))
;
;(defquery-root :dataviz-data
;  "get data for the dataviz page"
;  (value [{:keys [request datomic] :as env} params]
;    (do
;      (timbre/info "QUERY :dataviz-data")
;      (tac/get-dataviz-data (d/db (:cx datomic)) (:agency params) (:year params)))))
;
;(defquery-root :stationlookup
;  "get all stations"
;  (value [{:keys [request datomic] :as env} {:keys [query agency] :as params}]
;    (let [db (d/db (:cx datomic))]
;      (timbre/info "QUERY :stationlookup" query)
;      (r/get-table db :stationlookup/StationID (or query '[*])))))
;
;(defn get-user [request user-db]
;  (users/get-user user-db (-> request :session :uid)))
;
;(defquery-root :tables-list
;  "get a list of tables"
;  (value [{:keys [request datomic user-db]} {:keys [uid agency]}]
;    (let [db (d/db (:cx datomic))
;          user (get-user request user-db)
;          _ (timbre/debug "TABLES USER?" user)
;          uid (when user
;                (:uid user))
;          agency (when user
;                   (:agency user))]
;      (timbre/info "QUERY :tables-list" uid agency)
;      [{:one 1 :two 0}])))
;
;
;;(defquery-root :rimdb-table
;;  "get all records from a param defined :table-key"
;;  (value [{:keys [request datomic query] :as env} params]
;;    (let [db (d/db (:cx datomic))
;;          kw (:table-key params)]
;;      (r/get-table db kw query))))
;
;;(defquery-entity :station/by-id
;;  "get all stations"
;;  (value [{:keys [request datomic] :as env} id params]
;;    (let [cx (:cx datomic)])))