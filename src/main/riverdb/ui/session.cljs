(ns riverdb.ui.session
  (:require
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [riverdb.ui.user :as user]))

(defsc Auth [this props]
  {:query [:token
           {:user (comp/get-query user/User)}]})

(defsc Session
  "Session representation. Used primarily for server queries. On-screen representation happens in Login component."
  [this {:keys [:session/valid? :account/name :account/auth] :as props}]
  {:query         [:session/valid?
                   :account/name
                   {:account/auth (comp/get-query Auth)}]
   :ident         (fn [] [:component/id :session])
   :pre-merge     (fn [{:keys [data-tree]}]
                    (merge {:session/valid? false :account/name "" :account/auth {}}
                      data-tree))
   :initial-state {:session/valid? false
                   :account/name ""
                   :account/auth {}}})

(def ui-session (comp/factory Session))