(ns riverdb.station
  (:require
    [clojure.tools.logging :as log :refer [debug info warn error]]
    [datomic.api :as d]
    [riverdb.db :refer [remap-query limit-fn]]
    [riverdb.state :refer [db cx]]))

(defn get-stations [db query args]
  (try
    (let [_      (debug "GET-STATIONS" args) ;query)

          {:keys [agency project active constituent]} args

          ;q      {:find  ['[(pull ?e q) ...]]
          ;        :in    '[$ q]
          ;        :where []
          ;        :args  [db query]}

          q     {:query {:find  ['[(pull ?e q) ...]]
                          :in    '[$ q]
                          :where []}
                  :args  [db query]}

          q      (cond-> q

                   ;; if there's an `active` arg, add the conditions
                   (some? active)
                   (->
                     (update-in [:query :where] conj '[?e :stationlookup/Active ?active])
                     (update-in [:query :in] conj '?active)
                     (update :args conj active))

                   ;; if there's an `agency` arg, add the conditions
                   agency
                   (->
                     (update-in [:query :where]
                       #(apply conj % '[[?pj :projectslookup/AgencyCode ?agency]
                                        [?e :stationlookup/Project ?pj]]))
                     (update-in [:query :in] conj '?agency)
                     (update :args conj agency))

                   ;; if there's a `project` arg, add the conditions
                   project
                   (->
                     (update-in [:query :where]
                       #(apply conj % '[[?pj :projectslookup/ProjectID ?project]
                                        [?e :stationlookup/Project ?pj]]))
                     (update-in [:query :in] conj '?project)
                     (update :args conj project))

                   constituent
                   (->
                     (update-in [:query :where]
                       #(apply conj % '[[?sv :sitevisit/StationID ?e]
                                        ;[?sv :sitevisit/SiteVisitDate ?svdt]
                                        ;[(> ?svdt #inst "2019")]
                                        [?sv :sitevisit/Samples ?sa]
                                        [?sa :sample/Constituent ?cnst]
                                        [?cnst :constituentlookup/ConstituentCode ?cnstcode]]))
                                        ;[?sa :sample/LabResults ?frs]
                                        ;[?frs :labresult/ConstituentRowID ?cnst]

                     (update-in [:query :in] conj '?cnstcode)
                     (update :args conj constituent))

                   ;; last one, in case there are no conditions, get all stations
                   #(empty? (get-in q [:query :where]))
                   (->
                     (update-in [:query :where] conj '[?e :stationlookup/StationName])))
          ;q      (remap-query q)
          ;_      (debug "Stations QUERY" q)
          rez    (d/query q)
          ;_      (debug "FIRST RESULT" (first rez))

          limit  (get args :limit)
          offset (get args :offset)

          rez    (if (or limit offset)
                   (limit-fn limit offset rez)
                   rez)]

          ;_      (debug "FIRST RESULT AFTER LIMIT APPLIED" (first rez))


      rez)
    (catch Exception ex (debug "ERROR: " (.getMessage ex)))))

