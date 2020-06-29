(ns riverdb.ui.comps.drag
  (:require [com.fulcrologic.fulcro.components :as comp]))

(defn cancelEvs [e]
  (-> e (.stopPropagation))
  (-> e (.preventDefault)))

(def dragging (atom nil))

(defn useDroppable
  ([this state-k] (useDroppable this state-k nil))
  ([this state-k type-k]
   ;(debug "USE DROPPABLE")
   (let [set-state!  (fn [new-state] (comp/update-state! this update :drop-state merge new-state))
         isType      (fn [type] (if type-k (= type type-k) true))

         ;; updates every render / state change
         {:keys [text type] :as data} (comp/get-state this :drag-state)
         instate     (state-k (comp/get-state this :drop-state))
         isover      (and (isType type) instate)
         ;_           (debug "UPDATING DROPPABLE STATE" state-k "TYPE" type "INSTATE" instate "ISOVER" isover "ISTYPE" (isType type))

         onDragEnter (fn [e fn]
                       (let [] ;{:keys [text type] :as data} @dragging]
                         ;(debug "DRAG ENTER ATOM" state-k type text "ISOVER" isover "ISTYPE" (isType type))
                         (when (isType type)
                           (set-state! {state-k true})
                           (when fn (fn data)))))
         onDragOver  (fn [e fn]
                       (let []
                         ;(debug "DRAG OVER ATOM" state-k type text "ISOVER" isover "ISTYPE" (isType type))
                         (cancelEvs e)
                         (when (isType type)
                           (when (not instate)
                             (set-state! {state-k true}))
                           (when fn (fn data)))))
         onDragLeave (fn [e fn]
                       (let []
                         ;(debug "DRAG LEAVE ATOM" state-k type text "ISOVER" isover "ISTYPE" (isType type))
                         (cancelEvs e)
                         (when isover
                           (set-state! {state-k nil})
                           (when fn (fn data)))))
         onDrop      (fn [e fn]
                       (let []
                         ;(debug "DROP ATOM" state-k type text "ISOVER" isover "ISTYPE" (isType type))
                         (cancelEvs e)
                         ;(-> e (.stopPropagation))
                         (when isover
                           (set-state! {state-k nil})
                           (when fn (fn data)))))]
     {:isOver isover :onDragEnter onDragEnter :onDragOver onDragOver :onDragLeave onDragLeave :onDrop onDrop})))

(defn useDraggable [this]
  ;(debug "USE DRAGGABLE")
  (let [set-state! (fn [new-state] (comp/update-state! this update :drag-state merge new-state))
        dragStart  (fn [ev data]
                     ;(debug "DRAG START" data)
                     (-> ev (.stopPropagation))
                     (-> ev .-dataTransfer (.setData "text/plain" (:text data)))
                     (set-state! data))
        dragEnd    (fn [ev]
                     ;(cancelEvs ev)
                     (let [data (comp/get-state this :drag-state)]
                       ;(debug "DRAG END" data)
                       (set-state! nil)))]
    {:onDragStart dragStart :onDragEnd dragEnd}))