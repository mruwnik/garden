(ns garden.canvas.grid
  (:require [garden.canvas.viewport :as viewport]))

(defn render!
  "Render the grid overlay."
  [ctx state]
  (let [spacing (get-in state [:ui :grid :spacing])
        {:keys [min max]} (viewport/visible-bounds)
        [min-x min-y] min
        [max-x max-y] max
        ;; Snap to grid lines
        start-x (* (Math/floor (/ min-x spacing)) spacing)
        start-y (* (Math/floor (/ min-y spacing)) spacing)
        end-x (* (Math/ceil (/ max-x spacing)) spacing)
        end-y (* (Math/ceil (/ max-y spacing)) spacing)]

    (.save ctx)
    (set! (.-strokeStyle ctx) "#ddd")
    (set! (.-lineWidth ctx) (/ 0.5 (get-in state [:viewport :zoom])))

    (.beginPath ctx)

    ;; Vertical lines
    (loop [x start-x]
      (when (<= x end-x)
        (.moveTo ctx x start-y)
        (.lineTo ctx x end-y)
        (recur (+ x spacing))))

    ;; Horizontal lines
    (loop [y start-y]
      (when (<= y end-y)
        (.moveTo ctx start-x y)
        (.lineTo ctx end-x y)
        (recur (+ y spacing))))

    (.stroke ctx)

    ;; Draw origin axes
    (set! (.-strokeStyle ctx) "#aaa")
    (set! (.-lineWidth ctx) (/ 1 (get-in state [:viewport :zoom])))
    (.beginPath ctx)
    (.moveTo ctx 0 start-y)
    (.lineTo ctx 0 end-y)
    (.moveTo ctx start-x 0)
    (.lineTo ctx end-x 0)
    (.stroke ctx)

    (.restore ctx)))
