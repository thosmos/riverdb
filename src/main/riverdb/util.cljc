(ns riverdb.util
  #?(:cljs (:refer-clojure :exclude [uuid]))
  (:require
    #?(:cljs [goog.object :as gobj])
    [com.fulcrologic.guardrails.core :refer [>defn]]
    [clojure.spec.alpha :as s])
  #?(:clj (:import [java.util UUID])))

#?(:cljs
    (defn gget [& args]
          (apply gobj/get args)))

(>defn uuid
  "Generate a UUID the same way via clj/cljs.  Without args gives random UUID. With args, builds UUID based on input (which
  is useful in tests)."
  #?(:clj ([] [=> uuid?] (UUID/randomUUID)))
  #?(:clj ([int-or-str]
           [(s/or :i int? :s string?) => uuid?]
           (if (int? int-or-str)
             (UUID/fromString
               (format "ffffffff-ffff-ffff-ffff-%012d" int-or-str))
             (UUID/fromString int-or-str))))
  #?(:cljs ([] [=> uuid?] (random-uuid)))
  #?(:cljs ([& args]
            [(s/* any?) => uuid?]
            (cljs.core/uuid (apply str args)))))

(defn sort-maps-by
  "Sort a sequence of maps (ms) on multiple keys (ks)"
  [ms ks]
  (sort-by #(vec (map % ks)) ms))

(defn sort-maps-by*
  "Sort a vector of maps by one field.  Returns new app state."
  [state path keys]
  (let [items (get-in state path)
        sorted-items (sort-maps-by items keys)]
    (assoc-in state path sorted-items)))


(defn with-index
  "return the sequence with an index for every element.
  For example: (with-index [:a :b :c]) returns ([0 :a] [1 :b] [2 :c]).
  The use case for this method arises when you need access to the index of element of a sequence
  For example:
  (doseq [[index element] (with-index [:a :b :c :d])]
    (println index element)
   FROM: https://github.com/sonwh98/tily/blob/master/src/com/kaicode/tily.cljc"
  [a-seq]
  (map-indexed (fn [i element] [i element])
    a-seq))

#?(:cljs
   (defn to-seq
     "convert a js collection into a ISeq. Credit to http://www.dotkam.com/2012/11/23/convert-html5-filelist-to-clojure-vector/"
     [js-col]
     (-> (clj->js [])
       (.-slice)
       (.call js-col)
       (js->clj))))

(defn paginate
  "Returns data requred to render paginator. From https://github.com/manawardhana/paginator-clj (Eclipse License)"
  [{:keys [records per-page max-pages current biased] :or {per-page 25 max-pages 5 current 1 biased :left}}]

  (let [total-pages (int (Math/ceil (/ records per-page)))
        half (Math/floor (/ max-pages 2))
        left-half  (int (if (= biased :left) (- half (if (odd? max-pages) 0 1)) half))
        right-half (int (if (= biased :right) (- half (if (odd? max-pages) 0 1)) half))
        virtual-start (- current left-half);can be a minus
        virtual-end (+ current right-half); can be exceeding than available page limit
        start (max 1 (- virtual-start (if (> virtual-end total-pages) (- virtual-end total-pages) 0)))
        end (inc (min total-pages (+ current (+ right-half (if (< virtual-start 1) (Math/abs (dec virtual-start)) 0)))))]
    {:current current :pages (range start end)}))


;(defn history-view
;  "Return a function that will display transaction history with
;  pagination."
;  [page get-count-fn get-transactions-fn]
;  #(let [max-items        10            ; max no of items displayed
;         prange           5             ; pagination range
;         get-count-result (future (get-count-fn))
;         transactions     (future (get-transactions-fn
;                                    {:max_items max-items
;                                     :offset (* (dec page) max-items)}))
;         first-page       (-> page
;                            dec
;                            (quot prange)
;                            (* prange)
;                            inc)
;         pages            (take prange (iterate inc first-page))
;         {tcount :tcount} @get-count-result
;         total-pages      (if (zero? (mod tcount max-items))
;                            (quot tcount max-items)
;                            (inc (quot tcount max-items)))
;         has-page?        (fn [page] (<= page total-pages))
;         available-pages  (take-while has-page? pages)]
;     {:transactions  (map describe-transaction @transactions)
;      :current_page  page
;      :prev_page     (dec first-page)
;      :pages         available-pages
;      :next_page     (inc (last pages))
;      :no_next_page  (or (< (count available-pages) prange)
;                       (= (last pages) total-pages))}))