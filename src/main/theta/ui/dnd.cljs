(ns theta.ui.dnd
  (:require
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.algorithms.react-interop :as interop]))
    ;["react-beautiful-dnd" :refer [Droppable Draggable DragDropContext]]))

;import * as React from 'react';
;import { Droppable } from 'react-beautiful-dnd';
;
;export default (props: any) =>
;<Droppable droppableId={props.droppableId}>
;{(provided: any) => (
;                      <div className={props.className}
;                      ref={provided.innerRef}
;                      {...provided.droppableProps}
;                      {...provided.droppablePlaceholder}>
;                      {props.children}
;                      </div>)}
;
;</Droppable>

;(def factory (interop/react-factory Droppable))
;(def ui-droppable (interop/react-factory Droppable))

;(defn ui-droppable [{:keys [droppableId className children] :as props}]
;  (factory {:droppableId droppableId}
;    (fn [jsprops]
;      (dom/div {:className            className
;                :ref                  (js->clj (comp/isoget jsprops "innerRef"))
;                :droppableProps       (js->clj (comp/isoget jsprops "droppableProps"))
;                :droppablePlaceholder (js->clj (comp/isoget jsprops "droppablePlaceholder"))}
;        children))))


