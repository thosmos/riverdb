(ns riverdb.ui.components
  (:require
    [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h3 button table tr td]]
    [com.fulcrologic.fulcro.components :as om :refer [defsc transact!]]
    [riverdb.util :refer [sort-maps-by with-index]]
    [riverdb.ui.session :refer [Session]]
    [com.fulcrologic.fulcro.components :as comp]
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop]
    [com.fulcrologic.fulcro.mutations :as fm]
    ;[goog.object :as gobj]
    ;[tick.alpha.api :as t]
    ;[tick.timezone]
    [theta.log :refer [debug]]
    ["react-datepicker/dist" :default DatePicker]
    ["js-joda/dist/js-joda" :as js-joda :refer [DateTimeFormatter, LocalDateTime, LocalDate]]
    ;["react-treeview-semantic" :default SourceTree]))
    ["react-treeview/lib/react-treeview" :as TreeView]
    ["react-virtualized-auto-sizer/dist/index.cjs.js" :as AutoSizer]
    ["react-beautiful-dnd" :refer [DragDropContext Droppable Draggable]]))

(defn f
  "Wraps a React component into a Fulcro factory and then calls it with the same args"
  [react-class & args]
  (apply (interop/react-factory react-class) args))

(defn ui-drag-drop-context [& args]
  (apply f DragDropContext args))

(defn ui-droppable [& args]
  (apply f Droppable args))

(defn ui-draggable [& args]
  (apply f Draggable args))


;(debug "HEY from js-joda" (.. LocalDate (parse "2012-12-24") (atStartOfDay) (plusMonths 2) (format (.ofPattern DateTimeFormatter "M/d/yyyy"))))
;(debug "HEY from TICK" (t/time) (t/inst (t/offset-date-time "1918-11-11T11:00:00+01:00")) (-> (t/time "11:00") (t/on "1918-11-11") (t/in "Europe/Paris")))

;d.format(DateTimeFormatter.ofPattern('M/d/yyyy')) // 4/28/2018
;d.format(DateTimeFormatter.ofPattern('HH:mm')) // 12:34

;var LocalDate = require("@js-joda/core").LocalDate;
;var d = LocalDate.parse("2012-12-24")
;.atStartOfDay()
;.plusMonths(2); // 2013-02-24T00:00:00

(defsc HelloWorld [this {:keys [person/id] :as props}]
  {:query [:person/id]
   :ident :person/id})

(def ui-hello-world (comp/factory HelloWorld {:keyfn :person/id}))

(defn hoc-factory [Thing hoc]
  (interop/hoc-factory Thing hoc))

(defn input-factory [Thing]
  (interop/react-input-factory Thing))

(defn factory [Thing]
  (interop/react-factory Thing))

(def ui-datepicker (factory DatePicker))
(def ui-treeview (factory TreeView))
(def ui-autosizer (factory AutoSizer))

(defsc PlaceholderImage
  "Generates an SVG image placeholder of the given size and with the given label
  (defaults to showing 'w x h'.

  ```
  (ui-placeholder {:w 50 :h 50 :label \"avatar\"})
  ```
  "
  [this {:keys [w h label]}]
  (let [label (or label (str w "x" h))]
    (dom/svg #js {:width w :height h}
      (dom/rect #js {:width w :height h :style #js {:fill        "rgb(200,200,200)"
                                                    :strokeWidth 2
                                                    :stroke      "black"}})
      (dom/text #js {:textAnchor "middle" :x (/ w 2) :y (/ h 2)} label))))

(def ui-placeholder (om/factory PlaceholderImage))

