(ns riverdb.safetoswim
  (:require
    [clojure.tools.logging :as log :refer [debug info warn error]]
    [datomic.api :as d]
    [riverdb.db :refer [remap-query limit-fn]]
    [riverdb.state :refer [db cx]]
    [java-time :as jt]))



