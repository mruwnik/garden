(ns garden.canvas.grid
  (:require [garden.canvas.viewport :as viewport]))

(defn render!
  "Render the grid overlay with LOD optimization."
  [ctx state]
  (let [zoom (get-in state [:viewport :zoom])
        base-spacing (get-in state [:ui :grid :spacing])
        ;; Adjust spacing based on zoom to avoid too many lines
        ;; At low zoom, use larger spacing (multiples of base)
        spacing (cond
                  (< zoom 0.015) (* base-spacing 20)
                  (< zoom 0.03)  (* base-spacing 10)
                  (< zoom 0.06)  (* base-spacing 5)
                  (< zoom 0.12)  (* base-spacing 2)
                  :else base-spacing)
        {:keys [min max]} (viewport/visible-bounds)
        [min-x min-y] min
        [max-x max-y] max
        ;; Snap to grid lines
        start-x (* (Math/floor (/ min-x spacing)) spacing)
        start-y (* (Math/floor (/ min-y spacing)) spacing)
        end-x (* (Math/ceil (/ max-x spacing)) spacing)
        end-y (* (Math/ceil (/ max-y spacing)) spacing)
        ;; Limit total number of lines to prevent slowdown
        num-v-lines (/ (- end-x start-x) spacing)
        num-h-lines (/ (- end-y start-y) spacing)]
    ;; Skip if way too many lines would be drawn
    (when (and (< num-v-lines 500) (< num-h-lines 500))
      (.save ctx)
      (set! (.-strokeStyle ctx) "#ddd")
      (set! (.-lineWidth ctx) (/ 0.5 zoom))

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
      (set! (.-lineWidth ctx) (/ 1 zoom))
      (.beginPath ctx)
      (.moveTo ctx 0 start-y)
      (.lineTo ctx 0 end-y)
      (.moveTo ctx start-x 0)
      (.lineTo ctx end-x 0)
      (.stroke ctx)

      (.restore ctx))))
