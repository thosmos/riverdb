(ns riverdb.ui.comps.grid-layout
  (:require [com.fulcrologic.fulcro.components :as fp]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [garden.selectors :as gs]
            [goog.object :as gobj]
            [cljsjs.react-grid-layout]))

(def column-size 120)
(def max-columns 20)
(def columns-step 2)

(def breakpoints
  (vec
    (for [i (range 0 max-columns columns-step)
          :let [c (+ i columns-step)]]
      {:id         (str "c" c)
       :cols       c
       :breakpoint (if (zero? i) 0 (* c column-size))})))

(defn grid-item-css [props]
  [:$react-grid-item
   [(gs/& (gs/not :.react-grid-placeholder))
    props]])

(def WidthProvider js/ReactGridLayout.WidthProvider)
(def Responsive js/ReactGridLayout.Responsive)

(def GridWithWidth (WidthProvider Responsive))

(fp/defsc GridLayout
  [this props]
  {:css               [[:$react-grid-layout
                        {:position   "relative"
                         :transition "height 200ms ease"}]

                       [:$react-grid-item
                        {:transition          "all 200ms ease"
                         :transition-property "left, top"}

                        [:&$cssTransforms
                         {:transition-property "transform"}]

                        [:&$resizing
                         {:z-index     "1"
                          :will-change "width, height"}]

                        [:&$react-draggable-dragging
                         {:transition  "none"
                          :z-index     "3"
                          :will-change "transform"}]

                        [:&$react-grid-placeholder
                         {:background          "red"
                          :opacity             "0.2"
                          :transition-duration "100ms"
                          :z-index             "2"
                          :-webkit-user-select "none"
                          :-moz-user-select    "none"
                          :-ms-user-select     "none"
                          :-o-user-select      "none"
                          :user-select         "none"}]

                        [:> [:$react-resizable-handle
                             {:position "absolute"
                              :width    "20px"
                              :height   "20px"
                              :bottom   "0"
                              :right    "0"
                              :cursor   "se-resize"}

                             [:&:after
                              {:content       "\"\""
                               :position      "absolute"
                               :right         "5px"
                               :bottom        "5px"
                               :width         "5px"
                               :height        "5px"
                               :border-right  "2px solid rgba(0, 0, 0, 0.4)"
                               :border-bottom "2px solid rgba(0, 0, 0, 0.4)"}]]]]

                       [:$react-resizable
                        {:position "relative"}]

                       [:$react-resizable-handle
                        {:position            "absolute"
                         :width               "20px"
                         :height              "20px"
                         :bottom              "0"
                         :right               "0"
                         :background-position "bottom right"
                         :padding             "0 3px 3px 0"
                         :background-repeat   "no-repeat"
                         :background-origin   "content-box"
                         :box-sizing          "border-box"
                         :cursor              "se-resize"}]

                       (grid-item-css {:background    "#fff"
                                       :border-radius "6px"
                                       :display       "flex"})]
   :componentDidMount (fn [this]
                        (let [{:keys [onBreakpointChange]} (fp/props this)
                              width (-> (gobj/getValueByKeys this "grid")
                                      (dom/node)
                                      (gobj/get "offsetWidth"))
                              bp    (->> (rseq breakpoints)
                                      (filter #(>= width (:breakpoint %)))
                                      first
                                      :id)]
                          (onBreakpointChange bp)))}
  (dom/create-element GridWithWidth (clj->js (assoc props :ref #(gobj/set this "grid" %)))
    (fp/children this)))

(def grid-layout (fp/factory GridLayout))





(defonce components-with-error (atom #{}))

(defn block [w h x y] {"w" w "h" h "x" x "y" y})

(defn build-grid [items]
  (reduce
    (fn [grid {:strs [w h x y] :as item}]
      (into grid
        (for [x' (range w)
              y' (range h)]
          [[(+ x' x) (+ y' y)] item])))
    {}
    items))

(defn fits-in? [{:strs [w h x y]} grid]
  (let [coords (for [x' (range w)
                     y' (range h)]
                 [(+ x' x) (+ y' y)])]
    (every? #(not (contains? grid %)) coords)))

(defn smart-item-position [columns {:strs [w h] :as new-item} items]
  (let [grid (build-grid items)
        w    (min w columns)]
    (loop [x 0
           y 0]
      (if (> (+ x w) columns)
        (recur 0 (inc y))
        (if-let [block (get grid [x y])]
          (recur (+ (get block "x") (get block "w")) y)
          (if (fits-in? (block w h x y) grid)
            (assoc new-item "x" x "y" y "w" w)
            (recur (inc x) y)))))))

;(defsc GridZone [this {::keys [layouts cards] :as props}]
;  {:query             [::layouts ::breakpoint ::cards]
;   :css               [[:.container {:display        "flex"
;                                     :flex           "1"
;                                     :flex-direction "column"}]
;                       [:.grid {:flex       "1"
;                                :overflow-y "scroll"
;                                :overflow-x "hidden"}]
;                       [:.tools {:background  style/color-white
;                                 :color       style/color-limed-spruce
;                                 :padding     "5px 9px"
;                                 :display     "flex"
;                                 :align-items "center"}
;                        [:button {:margin-left "5px"}]]
;                       [:.breakpoint {:flex "1"}]]
;   :css-include       [grid/GridLayout]
;   :componentDidCatch (fn [this error info]
;                        (swap! components-with-error conj this)
;                        (comp/set-state! this {::error-catch? true}))}
;  (ldom/div :.grid {:style {:flex       "1"
;                            :overflow-y "scroll"
;                            :overflow-x "hidden"}}
;    (grid/grid-layout
;      {:className          "layout"
;       :rowHeight          30
;       :breakpoints        (into {} (map (juxt :id :breakpoint)) grid/breakpoints)
;       :cols               (into {} (map (juxt :id :cols)) grid/breakpoints)
;       ;:layouts            layouts
;       :draggableHandle    ".workspaces-cljs-card-drag-handle"
;       :onBreakpointChange (fn [bp _]
;                             (fm/set-value! this ::breakpoint bp))
;       #_:onLayoutChange     #_(fn [_ layouts]
;                                 (let [layouts' (->> (js->clj layouts)
;                                                  (into {} (map (fn [[k v]] [k (normalize-layout v)]))))]
;                                   (comp/transact! this [`(update-workspace ~{::workspace-id workspace-id
;                                                                              ::layouts      layouts'})])))}
;      (ldom/div {} "HMM"))))
;(def ui-gridzone (comp/factory GridZone))