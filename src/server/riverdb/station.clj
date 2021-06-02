(ns riverdb.station
  (:require
    [clojure.tools.logging :as log :refer [debug info warn error]]
    [datomic.api :as d]
    [riverdb.db :refer [remap-query limit-fn]]
    [riverdb.state :refer [db cx]]))

(defn get-stations [db query args]
  (try
    (let [_      (debug "GET-STATIONS" args) ;query)

          {:keys [agency project active constituent projectType params]} args

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
                   (and agency (not project))
                   (->
                     (update-in [:query :where]
                       #(apply conj % '[[?a :agencylookup/AgencyCode ?agency]
                                        [?sp :projectslookup/AgencyRef ?a]
                                        [?sp :projectslookup/Stations ?e]]))
                     (update-in [:query :in] conj '?agency)
                     (update :args conj agency))

                   ;;; if there's a `project` arg, add the conditions
                   project
                   (->
                     (update-in [:query :where]
                       #(apply conj % '[[?pj :projectslookup/ProjectID ?project]
                                        [?pj :projectslookup/Stations ?e]]))
                     (update-in [:query :in] conj '?project)
                     (update :args conj project))


                   (and project (= projectType "logger"))
                   (->
                     (update-in [:query :where]
                       #(apply conj % '[[?l :logger/stationRef ?e]
                                        [_ :logsample/logger ?l]])))

                   ;; return stations that actually have samples of the project's params
                   (and project (= projectType "sitevisit") (not constituent) (not params))
                   (->
                     (update-in [:query :where]
                       #(apply conj % '[[?sv :sitevisit/StationID ?e]
                                        [?sv :sitevisit/Samples ?sa]
                                        [?sa :sample/Constituent ?co]
                                        [?pj :projectslookup/Parameters ?pa]
                                        [?pa :parameter/Constituent ?co]])))

                   ;; return stations that have samples of the selected params
                   (and project (= projectType "sitevisit") params (not constituent))
                   (->
                     (update-in [:query :in] conj '[?params ...])
                     (update :args conj params)
                     (update-in [:query :where]
                       #(apply conj % '[[?pa :parameter/NameShort ?params]
                                        [?pa :parameter/Constituent ?co]
                                        [?sv :sitevisit/StationID ?e]
                                        [?sv :sitevisit/Samples ?sa]
                                        [?sa :sample/Constituent ?co]])))

                   constituent
                   (->
                     (update-in [:query :where]
                       #(apply conj % '[[?sv :sitevisit/StationID ?e]
                                        [?sv :sitevisit/Samples ?sa]
                                        [?sa :sample/Constituent ?cnst]
                                        [?cnst :constituentlookup/ConstituentCode ?cnstcode]]))

                     (update-in [:query :in] conj '?cnstcode)
                     (update :args conj constituent)))

          ;; if there are no conditions, get all stations
          q       (if #(empty? (get-in q [:query :where]))
                    (update-in q [:query :where] conj '[?e :stationlookup/StationID])
                    q)

          _      (debug "Stations QUERY" (pr-str q))

          rez    (d/query q)

          _      (debug (count rez) "results")
          ;_      (debug "FIRST RESULT" (first rez))

          limit  (get args :limit)
          offset (get args :offset)

          rez    (if (or limit offset)
                   (limit-fn limit offset rez)
                   rez)]

          ;_      (debug "FIRST RESULT AFTER LIMIT APPLIED" (first rez))


      rez)
    (catch Exception ex (debug "ERROR: " (.getMessage ex)))))

