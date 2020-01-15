(ns riverdb.ui.semantic
  (:refer-clojure :exclude [list List])
  (:require
    ["semantic-ui-react/dist/commonjs/collections/Breadcrumb" :default Breadcrumb]
    ["semantic-ui-react/dist/commonjs/collections/Form" :default Form]
    ["semantic-ui-react/dist/commonjs/collections/Grid" :default Grid]
    ["semantic-ui-react/dist/commonjs/collections/Menu" :default Menu]
    ["semantic-ui-react/dist/commonjs/collections/Message" :default Message]
    ["semantic-ui-react/dist/commonjs/elements/Button" :default Button]
    ["semantic-ui-react/dist/commonjs/elements/Container" :default Container]
    ["semantic-ui-react/dist/commonjs/elements/Divider" :default Divider]
    ["semantic-ui-react/dist/commonjs/elements/Header" :default Header]
    ["semantic-ui-react/dist/commonjs/elements/Icon" :default Icon]
    ["semantic-ui-react/dist/commonjs/elements/Input" :default Input]
    ["semantic-ui-react/dist/commonjs/elements/Image" :default Image]
    ["semantic-ui-react/dist/commonjs/elements/Label" :default Label]
    ["semantic-ui-react/dist/commonjs/elements/List" :default List]
    ["semantic-ui-react/dist/commonjs/elements/Segment" :default Segment]
    ["semantic-ui-react/dist/commonjs/modules/Dropdown" :default Dropdown]
    ["semantic-ui-react/dist/commonjs/modules/Checkbox" :default Checkbox]
    ["semantic-ui-react/dist/commonjs/modules/Popup" :default Popup]
    ["semantic-ui-react/dist/commonjs/modules/Sidebar" :default Sidebar]
    ["semantic-ui-react/dist/commonjs/addons/Responsive" :default Responsive]

    ["react-datepicker/dist" :default DatePicker]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop]))

(defn factory [Thing]
  (interop/react-factory Thing))

(def ui-datepicker (factory DatePicker))

;(:require ["semantic-ui-react/dist/commonjs/modules/Dropdown" :default Dropdown])
;(def dropdown (interop/react-factory Dropdown))


;(defn factory
;  [component]
;  (fn [& args]
;    (if (keyword? (first args))
;      (dom/macro-create-element component (next args) (first args))
;      (dom/macro-create-element component args))))
;
;(defn factory-apply
;  [class]
;  (fn [props & children]
;    (apply js/React.createElement
;      class
;      props
;      children)))

(def Menu Menu)
(def Segment Segment)
(def Responsive Responsive)

(def dropdown (factory Dropdown))

(def button (factory Button))
(def checkbox (factory Checkbox))
(def container (factory Container))
(def divider (factory Divider))
(def form (factory Form))
(def form-button (factory (.-Button Form)))
(def form-checkbox (factory (.-Checkbox Form)))
(def form-dropdown (factory (.-Dropdown Form)))
(def form-field (factory (.-Field Form)))
(def form-group (factory (.-Group Form)))
(def form-input (factory (.-Input Form)))
(def form-radio (factory (.-Radio Form)))
(def form-select (factory (.-Select Form)))
(def form-textarea (factory (.-TextArea Form)))
(def header (factory Header))
(def image (factory Image))
(def input (factory Input))
(def icon (factory Icon))
(def grid (factory Grid))
(def grid-col (factory (.-Column Grid)))
(def label (factory Label))
(def lst (factory List))
(def lst-item (factory (.-Item List)))
(def lst-icon (factory (.-Icon List)))
(def lst-content (factory (.-Content List)))
(def lst-header (factory (.-Header List)))
(def lst-description (factory (.-Description List)))
(def lst-list (factory (.-List List)))
(def menu (factory Menu))
(def menu-item (factory (.-Item Menu)))
(def menu-menu (factory (.-Menu Menu)))
(def message (factory Message))
(def message-content (factory (.-Content Message)))
(def message-header (factory (.-Header Message)))
(def popup (factory Popup))
(def responsive (factory Responsive))
(def segment (factory Segment))
(def sidebar (factory Sidebar))
(def sidebar-pushable (factory (.-Pushable Sidebar)))
(def sidebar-pusher (factory (.-Pusher Sidebar)))

(defn sem
  "Wraps a React component into a Fulcro factory and then calls it with the same args"
  [react-class & args]
  (apply (factory react-class) args))
