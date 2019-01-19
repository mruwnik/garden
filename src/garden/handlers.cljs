(ns garden.handlers
  (:require [garden.state :as state]
            [garden.canvas :refer [
                                   render add-line round-pos draw-circle
                                   draw-polygon potential-polygon in-shape on-contour]]))


(defn mouse-pos
  ([e] (mouse-pos e (state/get :canvas :x-offset) (state/get :canvas :y-offset)))
  ([e x-offset y-offset]
   (let [bounding-box (-> e (aget "target") (.getBoundingClientRect))
         x (- (aget e "clientX") (aget bounding-box "left") x-offset)
         y (- (aget e "clientY") (aget bounding-box "top") y-offset)]
     [x y])))


(defn closest-mouse-pos [e]
  (round-pos e (state/get :canvas :pixels-per-meter) (state/get :canvas :x-offset) (state/get :canvas :y-offset)))

(defn over-layer-point
  ([e] (over-layer-point e (state/current-line)))
  ([e line] (some #(= (closest-mouse-pos e) %) line)))

(defn over-layer-line
  "Return the segment which the mouse is currently over."
  [e]
  (let [line (state/current-line)]
    (on-contour (conj line (last line)) (mouse-pos e) (/ (state/get :canvas :pixels-per-meter) 2))))


(defn clicked-layer
  "Check whether the given layer was clicked.

  The points of the layer are assumed to be a closed polygon - if not, then the
  first point will be added to the end to make it so."
  [e layer]
  (let [points (:points layer)
        closed (if (= (last points) (first points)) points (conj points (last points)))]
    (in-shape closed (mouse-pos e))))


(defn selected-layer
  "Return the id of the selected layer."
  [e]
  (if (clicked-layer e (state/current-layer))
    (:id (state/current-layer))
    (->>  (state/get :layers)
          (filter (partial clicked-layer e))
          first
          :id)))


(defn select-layer
  "Select the layer with the given id."
  [id]
  (state/select-layer id)
  (render @state/app-state))


;;; Layer drawing handlers

(defn append-point
  "Append the selected point to the current layer."
  [e]
  (state/update
   (state/current-accessor :points)
   (conj (state/current-line) (closest-mouse-pos e))))

(defn insert-point
  "Insert the clicked point between the `start` and `end` points."
  [e [start end]]
  (let [pos (closest-mouse-pos e)
        [before after] (split-with #(not= end %) (state/current-line))]
    (state/update
     (state/current-accessor :points) (concat before [pos] after))))


(defn line-to-point
  "Draw a line from the last clicked point to the current position of the mouse,"
  [e]
  (when (seq (state/current-line))
    (let [canvas (state/get :canvas)
          ctx (:ctx canvas)
          current-layer (state/current-layer)
          point (round-pos e (:pixels-per-meter canvas) (:x-offset canvas) (:y-offset canvas))]
      (render @state/app-state)
      (potential-polygon ctx (:points current-layer) point (:colour current-layer) [(:x-offset canvas) (:y-offset canvas)]))))


;;; Layer movement handlers

(defn start-move
  "If the current mouse position is inside the current layer, then start moving.
  If a line was selected, then first add a new point.."
  [e]
  (let [over (over-layer-point e)
        on-line (over-layer-line e)
        in-layer (clicked-layer e (state/current-layer))]
    (when on-line
      (insert-point e on-line)
      )
    (when (or over in-layer on-line)
      (state/move (closest-mouse-pos e))
      (state/set-mode
       (cond
         over :move-point
         on-line :move-point
         in-layer :move)))))


(defn move-layer [e]
  (-> (closest-mouse-pos e) state/move state/move-current-layer)
  (render @state/app-state))


(defn move-point [e]
  (-> (closest-mouse-pos e) state/move-current-point)
  (render @state/app-state))


(defn end-move [e]
  (state/end-move)
  (state/set-mode :edit))


;;; Basic canvas mouse handlers (a state machine, more or less)

(defn set-pointer [e action]
  (state/set-pointer
   (cond
     (and (= action :edit) (over-layer-point e)) :crosshair
     (and (= action :edit) (over-layer-line e)) :pointer
     (and (= action :edit) (clicked-layer e (state/current-layer))) :grab
     (= action :move) :grabbing
     (= action :move-point) :grabbing
     true :default)))


(defn mouse-down
  "Call the appropriate handler for the current state,"
  [e]
  (println "down:" (state/get-mode))
  (condp = (state/get-mode)
    nil nil                                                            ; if no mode is active, then do nothing
    :edit (start-move e)                                        ; start moving the shape
    :draw nil                                                       ; when drawing, only mouse-move and mouse-down are used
    :move (throw (js/Error. "Shouldn't happen"))   ; this shouldn't happen, as :move is a substate of :edit
    )
  (set-pointer e (state/get-mode)))


(defn mouse-move
  "Handle a mouse movement by calling the appropriate handlers for this state."
  [e]
  (condp = (state/get-mode)
    nil nil                                       ; if no mode is active, do nothing
    :edit (set-pointer e :move)
    :move (move-layer e)                ; move the whole layer
    :move-point (move-point e)        ; just move the last selected point
    :draw (line-to-point e))               ; when drawing, display a line from the current position to the last point on the line
  (set-pointer e (state/get-mode)))


(defn mouse-up
  "Handle a mouse up action by calling the appropriate handlers for the current state."
  [e]
  (condp = (state/get-mode)
    nil (select-layer (selected-layer e))
    :edit (select-layer (selected-layer e))
    :draw (append-point e)
    :move (end-move e)
    :move-point (end-move e))
  (set-pointer e (state/get-mode)))


(defn mouse-out
  "Handle the mouse leaving the canvas."
  [e]
  (state/end-move)
  (when (= (state/get-mode) :draw)
    (state/set-mode :edit))
  (render @state/app-state))


;; Side bar handlers

(defn event-field
  "Extract the given `field` from the target of the given `event`."
  [event field] (-> event (aget "target") (aget field)))


(defn select-event-layer
  "Handle a layer being selected."
  [event]
  (select-layer (-> event (event-field "id") js/parseInt))
  (.log js/console (state/get :canvas :ctx)))


(defn random-colour [] (apply str "#" (repeatedly 6 #(rand-nth "01234567890ABCDEF"))))

(defn add-layer [event]
  (let [id (->> (state/get :layers) (map :id) (apply max) (+ 1))
        layer {:id id :name (str "New patch (" id ")") :colour (random-colour)}
        layers (conj (state/get :layers) layer)]
    (state/update [:layers] layers)
    (state/select-layer id)
    (state/set-mode :draw)))

(defn remove-layer [event]
  (let [id (-> event (event-field "id") js/parseInt)]
    (->> (state/get :layers)
         (remove #(= (:id %) id))
         (into [])
         (state/update [:layers]))
    (when (= (state/get :current :id) id) (state/update [:current] nil))
    (render @state/app-state)))


(defn update-value [cast accessor event]
  (state/update accessor (cast (event-field event "value"))))

(defn move [dir]
  (condp = dir
    :left (state/update [:canvas :x-offset] (- (state/get :canvas :x-offset) 10))
    :right (state/update [:canvas :x-offset] (+ (state/get :canvas :x-offset) 10))
    :up (state/update [:canvas :y-offset] (- (state/get :canvas :y-offset) 10))
    :down (state/update [:canvas :y-offset] (+ (state/get :canvas :y-offset) 10)))
  (render @state/app-state))
