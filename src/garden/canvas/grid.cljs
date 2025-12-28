(ns garden.canvas.grid
  "Grid overlay rendering with LOD optimization.

   Draws a measurement grid that adapts its density based on zoom level,
   with optional labels showing distances in meters."
  (:require [garden.canvas.viewport :as viewport]
            [garden.constants :as const]))

;; =============================================================================
;; LOD Calculations

(defn calculate-lod-spacing
  "Calculate grid spacing based on zoom level for LOD optimization.
   Returns a multiplier of base-spacing to use."
  [zoom]
  (cond
    (< zoom 0.015) 20
    (< zoom 0.03)  10
    (< zoom 0.06)  5
    (< zoom 0.12)  2
    :else          1))

(defn calculate-label-spacing
  "Calculate label spacing multiplier based on zoom level."
  [zoom]
  (cond
    (< zoom 0.01) 10
    (< zoom 0.02) 5
    (< zoom 0.05) 4
    (< zoom 0.1)  2
    :else         1))

;; =============================================================================
;; Grid Rendering

(defn render!
  "Render the grid overlay with LOD optimization."
  [ctx state]
  (let [zoom (get-in state [:viewport :zoom])
        base-spacing (get-in state [:ui :grid :spacing])
        ;; Adjust spacing based on zoom to avoid too many lines
        spacing (* base-spacing (calculate-lod-spacing zoom))
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
    (when (and (< num-v-lines const/max-grid-lines) (< num-h-lines const/max-grid-lines))
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

      ;; Draw measurement labels (optional)
      (when (get-in state [:ui :grid :labels?])
        (let [label-spacing (* spacing (calculate-label-spacing zoom))
              font-size (/ 12 zoom)]
          (set! (.-fillStyle ctx) "#666")
          (set! (.-font ctx) (str font-size "px sans-serif"))
          (set! (.-textAlign ctx) "center")
          (set! (.-textBaseline ctx) "top")

          ;; X-axis labels (along bottom of visible area, or at y=0)
          (let [label-y (js/Math.min (- max-y (/ 20 zoom)) 0)]
            (loop [x (* (Math/ceil (/ start-x label-spacing)) label-spacing)]
              (when (<= x end-x)
                (let [meters (/ x 100)
                      label (if (zero? (mod meters 1))
                              (str (int meters) "m")
                              (str (.toFixed meters 1) "m"))]
                  (.fillText ctx label x (+ label-y (/ 5 zoom))))
                (recur (+ x label-spacing)))))

          ;; Y-axis labels (along left of visible area, or at x=0)
          (set! (.-textAlign ctx) "left")
          (set! (.-textBaseline ctx) "middle")
          (let [label-x (js/Math.max (+ min-x (/ 5 zoom)) 0)]
            (loop [y (* (Math/ceil (/ start-y label-spacing)) label-spacing)]
              (when (<= y end-y)
                (let [meters (/ y 100)
                      label (if (zero? (mod meters 1))
                              (str (int meters) "m")
                              (str (.toFixed meters 1) "m"))]
                  (.fillText ctx label (+ label-x (/ 5 zoom)) y))
                (recur (+ y label-spacing)))))))

      (.restore ctx))))
