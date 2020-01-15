(ns riverdb.workspaces
  (:require
    [nubank.workspaces.core :as ws]
    [riverdb.demo-ws]))

(defonce init (ws/mount))
