(ns riverdb.model.logger
  (:require
    [theta.log :as log]
    [datomic.api :as d]
    [riverdb.db :refer [remap-query limit-fn]]
    [riverdb.state :refer [db cx]]))

(defn get-loggers [db query {:keys [agencyRef projectRef stationRef parameterRef] :as args}]
  (log/debug "GET-LOGGERS" args)
  (try
    (let [q   {:query {:find  ['[(pull ?e qu) ...]]
                       :in    '[$ qu]
                       :where []}
               :args  [db query]}

          q   (cond-> q

                ;; if there's an `active` arg, add the conditions
                ;(some? active)
                ;(->
                ;  (update-in [:query :where] conj '[?e :logger.meta/active ?active])
                ;  (update-in [:query :in] conj '?active)
                ;  (update :args conj active))

                agencyRef
                (->
                  (update-in [:query :where]
                    #(apply conj % '[[?e :logger/agencyRef ?ag]]))
                  (update-in [:query :in] conj '?ag)
                  (update :args conj agencyRef))

                projectRef
                (->
                  (update-in [:query :where]
                    #(apply conj % '[[?e :logger/projectRef ?pj]]))
                  (update-in [:query :in] conj '?pj)
                  (update :args conj projectRef))

                stationRef
                (->
                  (update-in [:query :where]
                    #(apply conj % '[[?e :logger/stationRef ?st]]))
                  (update-in [:query :in] conj '?st)
                  (update :args conj stationRef))

                parameterRef
                (->
                  (update-in [:query :where]
                    #(apply conj % '[[?e :logger/parameterRef ?pr]]))
                  (update-in [:query :in] conj '?pr)
                  (update :args conj parameterRef))

                ;; last one, in case there are no conditions, get all stations
                #(empty? (get-in q [:query :where]))
                (->
                  (update-in [:query :where] conj '[?e :logger/name])))

          rez (d/query q)]

      ;limit  (get args :limit)
      ;offset (get args :offset)

      ;rez    (if (or limit offset)
      ;         (limit-fn limit offset rez)
      ;         rez)]

      (log/debug "FIRST LOGGERs" (first rez))


      rez)
    (catch Exception ex (log/debug "ERROR: " (.getMessage ex)))))

(defn get-logger-samples [db query {:keys [loggerRef after before] :as args}]
  (log/debug "GET-LOGSAMPLES" query args)
  (try
    (let [q   {:query {:find  ['[(pull ?e q) ...]]
                       :in    '[$ q]
                       :where []}
               :args  [db query]}

          q   (cond-> q

                ;; if there's an `active` arg, add the conditions
                ;(some? active)
                ;(->
                ;  (update-in [:query :where] conj '[?e :logger.meta/active ?active])
                ;  (update-in [:query :in] conj '?active)
                ;  (update :args conj active))

                loggerRef
                (->
                  (update-in [:query :where]
                    #(apply conj % '[[?e :logsample/logger ?lgr]]))
                  (update-in [:query :in] conj '?lgr)
                  (update :args conj loggerRef))

                after
                (->
                  (update-in [:query :where]
                    #(apply conj % '[[(> ?after ?inst)]]))
                  (update-in [:query :in] conj '?after)
                  (update :args conj after))

                before
                (->
                  (update-in [:query :where]
                    #(apply conj % '[[(< ?before ?inst)]]))
                  (update-in [:query :in] conj '?before)
                  (update :args conj before))

                (or after before)
                (-> (update-in [:query :where]
                      #(apply conj % '[[?e :logsample/inst ?inst]])))

                ;; last one, in case there are no conditions, get all samples
                #(empty? (get-in q [:query :where]))
                (->
                  (update-in [:query :where] conj '[?e :logsample/inst])))

          rez (if (empty? (get-in q [:query :where]))
                []
                (d/query q))]
      (log/debug "FIRST LOGSAMPLES" (first rez))
      rez)
    (catch Exception ex (log/debug "ERROR: " (.getMessage ex)))))