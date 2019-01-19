(ns garden.canvas
  (:require [garden.state :as state]))

(defn linear-equation
  "Return a linear equation function going through the given 2 points."
  [[x1 y1] [x2 y2]]
  (when (not= x1 x2)
    (let [a (/ (- y2 y1) (- x2 x1))
          b (- y1 (* a x1))]
      (fn [x] (+ (* a x) b)))))


(defn dot-product
  "Calculate the dot product of the given points."
  [[x1 y1] [x2 y2]]
  (+ (* x1 x2) (* y1 y2)))

(defn points-distance
  "Return the distance between the two given points."
  [[x1 y1] [x2 y2]]
  (Math/sqrt (+ (Math/pow (- x1 x2) 2) (Math/pow (- y1 y2) 2))))


(defn distance-from-segment
  "Return how far the given point is from the given line.

  Taken from `http://geomalgorithms.com/a02-_lines.html`"
  [[x1 y1] [x2 y2] [xp yp]]
  (let [v [(- x2 x1) (- y2 y1)]
        w [(- xp x1) (- yp y1)]
        c1 (dot-product w v)
        c2 (dot-product v v)
        [vx vy] v
        b (when c2 (/ c1 c2))]
    (cond
      (< c1 0) (points-distance [xp yp] [x1 y1])
      (<= c2 c1) (points-distance [xp yp] [x2 y2])
      true (points-distance [xp yp] [(+ x1 (* b vx)) (+ y1 (* b vy))]))))


(defn on-contour
  "Return the first segment of the given `shape` that is no less than `max-dist` from `point`."
  [shape point max-dist]
  (loop [checked (first shape)
         points (conj (rest shape) (first shape))]
    (cond
      (not (seq points)) nil
      (< (distance-from-segment checked (first points) point) max-dist) [checked (first points)]
      true (recur (first points) (rest points)))))


(defn height-on-line [[x1 y1] [x2 y2] xp]
  (+ y1 (/ (* (- xp x1) (- y2 y1)) (- x2 x1))))


(defn winding-score
  "Return the score of the given line according to the Non Zero Rule (https://en.wikipedia.org/wiki/Nonzero-rule)"
  [[x1 y1] [x2 y2] [xp yp]]
  (cond
    (and (< yp y1) (< yp y2)) 0  ; the line is under the point
    (and (< xp x1) (< xp x2)) 0  ; the line is to the right of the point
    (and (> xp x1) (> xp x2)) 0  ; the line is to the left of the point
    (and (= x1 xp) (= x2 xp)) 0  ; the line is a vertical line above the point - disregard it
    (or (= x1 xp) (= x2 xp)) (if (< x1 x2) 0.5 -0.5)  ; the line starts or ends at the point - the next line will also start or end here, so both should be treated as one
    (> x1 x2) (- (winding-score [x2 y2] [x1 y1] [xp yp]))  ; the line is right to left - turn it around and check again
    (> y1 y2) (winding-score [(- (* 2 xp) x2) y2] [(- (* 2 xp) x1) y1] [xp yp]) ; the line goes down - mirror image the line to make checking easier
    :else (if (>= yp (height-on-line [x1 y1] [x2 y2] xp)) 1 0)))  ; the line is a normal linear function - check if the point lies above or below it

(defn in-shape
  "Return any layers that the given point belongs to."
  [shape point]
  (not= 0
        (loop [checked (first shape)
               points (conj (rest shape) (first shape))
               sum 0]
          (if-not (seq points)
            sum
            (recur (first points) (rest points) (+ sum (winding-score checked (first points) point)))))))


(defn add-line
  "Add a line to the given canvas `ctx`."
  ([ctx start end] (add-line ctx start end [0 0]))
  ([ctx [start-x start-y] [end-x end-y] [x-off y-off]]
   (.moveTo ctx (+ x-off start-x) (+ y-off start-y))
   (.lineTo ctx (+ x-off end-x) (+ y-off end-y))))


(defn draw-circle
  "Draw a circle onto `ctx` at (`x`, `y`) with the given `radius` in pixels"
  [ctx x y radius]
  (.beginPath ctx)
  (.arc ctx x y radius 0 (* Math/PI 2))
  (.fill ctx))


(defn scale [s [x y]] [(* s x) (* s y)])
(defn translate [[x-off y-off] [x y]] [(+ x-off x) (+ y-off y)])

(defn scaled-offsetted
  "Return the given points scaled and offsetted by the given values,"
  [points s offset]
  (map #(translate offset (scale s %)) points))


(defn grid-path
  "Generate a grid for the given `width` and `height`, where a meter is `pixels-per-unit`."
  [width height pixels-per-unit]
  (let [grid (js/Path2D.)]
    (doseq [i (range 0  (+ width 1) pixels-per-unit)]
      (add-line grid [i 0] [i height]))
    (doseq [i (range 0 (+ height 1) pixels-per-unit)]
      (add-line grid [0 i] [width i]))
    grid))


(defn draw-dotted-line
  "Draw lines joining all the given `points`, as well as marking the `points` on the resulting line."
  [ctx points x-offset y-offset scale]
  ; Draw the line
  (when (seq points)
    (let [points (scaled-offsetted points scale [x-offset y-offset])
          [start-x start-y] (first points)]
      (.moveTo ctx start-x start-y)
      (.beginPath ctx)
      (doseq [[x y] points]
        (.lineTo ctx x y))

      (set! (.-lineWidth ctx) 1)
      (.stroke ctx)

    ; Draw points for each of the points on the line
      (doseq [[x y] points]
        (draw-circle ctx x y 2)))))


(defn draw-polygon
  "Draw a polygon from all the points in `layer`."
  [ctx points colour]
  (when (seq points)
    (let [[start-x start-y] (first points)]
      (.moveTo ctx start-x start-y)
      (.beginPath ctx)
      (doseq [[x y] points]
        (.lineTo ctx x y))

      (set! (.-fillStyle ctx) colour)
      (.fill ctx))))


(defn potential-polygon
  [ctx points colour]
  (let [prev (.-globalAlpha ctx)
        new-point (first points)]
    (set! (.-globalAlpha ctx) 0.4)
    (draw-polygon ctx points colour)
    (set! (.-globalAlpha ctx) prev)

    (add-line ctx (last points) new-point)
    (set! (.-lineWidth ctx) 1)
    (.stroke ctx)))


(defn draw-layers [app-state]
  (let [ctx (-> app-state :canvas :ctx)
        scale (-> app-state :canvas :zoom)
        offsets [(-> app-state :canvas :x-offset) (-> app-state :canvas :y-offset)]
        layers (reverse (:layers app-state))]
    (set! (.-globalAlpha ctx) (if (-> app-state :current)  0.4 1))
    (doseq [layer layers]
      (draw-polygon ctx (scaled-offsetted (:points layer) scale offsets) (:colour layer)))
    (set! (.-globalAlpha ctx) 1)))


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
    (.stroke ctx (grid-path width height (:pixels-per-unit canvas)))

    ; draw all garden layers
    (draw-layers app-state)

   ; draw any other things
    (draw-dotted-line ctx (state/current-line) (:x-offset canvas) (:y-offset canvas) (:zoom canvas))
  ))
