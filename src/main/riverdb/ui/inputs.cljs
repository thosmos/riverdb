(ns riverdb.ui.inputs
  (:require
    [clojure.string :as str]
    [com.fulcrologic.fulcro.dom.inputs :as fi :refer [StringBufferedInput]]
    [com.fulcrologic.fulcro.algorithms.form-state :as fs]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc transact!]]
    [com.fulcrologic.fulcro.data-fetch :as f]
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li a p h2 h3 button table thead
                                                tbody th tr td form label input select
                                                option span]]
    [com.fulcrologic.fulcro.mutations :as fm]
    [riverdb.ui.util :as ru]))


(let [digits+ (into #{} (map str) (concat (range 10) ["." "-"]))]
  (defn just-floaties
    "Returns `s` with all non-digits other than - and . stripped."
    [s]
    (str/join
      (filter digits+ (seq s)))))


(def ui-float-input
  (comp/factory (StringBufferedInput ::FloatInput {:model->string str
                                                   :string->model ru/parse-float
                                                   :string-filter just-floaties})))