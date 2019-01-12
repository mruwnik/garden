(ns garden.canvas
  (:require [garden.state :as state]))

(defn round-pos
  "Round off the coordinates of the given event.

  The idea is to limit the amount of points on the canvas, where clicking on a pixel
  will result in the closest point on the grid being selected.
  "
  [e grid-size]
  (let [bounding-box (-> e (aget "target") (.getBoundingClientRect))
        x (- (aget e "clientX") (aget bounding-box "left"))
        y (- (aget e "clientY") (aget bounding-box "top"))
        floor-x (- x (mod x grid-size))
        floor-y (- y (mod y grid-size))
        cutoff (/ grid-size 2)]
    [(if (<= (- x floor-x) cutoff) floor-x (+ floor-x grid-size))
     (if (<= (- y floor-y) cutoff) floor-y (+ floor-y grid-size))]))


(defn add-line
  "Add a line to the given canvas `ctx`."
  [ctx [start-x start-y] [end-x end-y]]
  (.moveTo ctx start-x start-y)
  (.lineTo ctx end-x end-y))


(defn draw-circle
  "Draw a circle onto `ctx` at (`x`, `y`) with the given `radius` in pixels"
  [ctx x y radius]
  (.beginPath ctx)
  (.arc ctx x y radius 0 (* Math/PI 2))
  (.fill ctx))


(defn grid-path
  "Generate a grid for the given `width` and `height`, where a meter is `pixels-per-meter`."
  [width height pixels-per-meter]
  (let [grid (js/Path2D.)]
    (doseq [i (range 0  (+ width 1) pixels-per-meter)]
      (add-line grid [i 0] [i height]))
    (doseq [i (range 0 (+ height 1) pixels-per-meter)]
      (add-line grid [0 i] [width i]))
    grid))


(defn draw-dotted-line
  "Draw lines joining all the given `points`, as well as marking the `points` on the resulting line."
  [ctx points]
  ; Draw the line
  (when (seq points)
    (let [[start-x start-y] (first points)]
      (.moveTo ctx start-x start-y)
      (.beginPath ctx)
      (doseq [[x y] points]
        (.lineTo ctx x y))

      (set! (.-lineWidth ctx) 1)
      (.stroke ctx))

    ; Draw points for each of the points on the line
    (doseq [[x y] points]
      (draw-circle ctx x y 2))))


(defn draw-layer
  "Draw a polygon from all the points in `layer`."
  [ctx layer]
  (when (seq layer)
    (let [[start-x start-y] (first layer)]
      (.moveTo ctx start-x start-y)
      (.beginPath ctx)
      (doseq [[x y] layer]
        (.lineTo ctx x y))
      (.fill ctx))))


(defn render
  "Render the current state of the canvas, on the basis of the app-state passed via `settings`"
  [app-state]
  (let [canvas (:canvas app-state)
        ctx (:ctx canvas)
        width (:width canvas)
        height (:height canvas)]
    ; clear the canvas
    (.clearRect ctx 0 0 width height)

    ; draw the grid
    (set! (.-lineWidth ctx) 0.3)
    (.stroke ctx (grid-path  width height (:pixels-per-meter canvas)))

   ; draw any other things

    (draw-dotted-line ctx (state/current-line))
  ))
