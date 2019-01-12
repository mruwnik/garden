(ns garden.handlers
  (:require [garden.state :as state]
            [garden.canvas :refer [render add-line round-pos draw-layer]]))


(defn select-point
  "Handle a point being selected on the canvas."
  [e]
  (state/update
   (state/current-accessor :points)
   (conj (state/current-line) (round-pos e (state/get :canvas :pixels-per-meter))))
  (render @state/app-state))


(defn line-to-point
  "Draw a line from the last clicked point to the current position of the mouse,"
  [e]
  (when (seq (state/current-line))
    (let [canvas (state/get :canvas)
          ctx (:ctx canvas)]
      (render @state/app-state)
      (add-line ctx (last (state/current-line)) (round-pos e (:pixels-per-meter canvas)))

      (set! (.-lineWidth ctx) 1)
      (.stroke ctx))))


(defn mouse-out
  "Handle the mouse leaving the canvas."
  [e]
  (let [canvas (state/get :canvas)]
    (render @state/app-state)
    (draw-layer (:ctx canvas) (state/current-layer))))



(defn event-field
  "Extract the given `field` from the target of the given `event`."
  [event field] (-> event (aget "target") (aget field)))

(defn select-layer
  "Handle a layer being selected."
  [event]
  (state/select-layer (-> event (event-field "id") js/parseInt))
  (render @state/app-state))


(defn update-value [cast accessor event]
  (state/update accessor (cast (event-field event "value"))))
