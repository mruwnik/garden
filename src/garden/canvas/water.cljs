(ns garden.canvas.water
  "Render water overlay from simulation.

   Renders the water grid as a blue overlay with transparency
   proportional to water depth (logarithmic scale for visibility)."
  (:require [garden.state :as state]
            [garden.simulation.water :as water-sim]))

;; =============================================================================
;; Constants

(def ^:private water-color [30 100 200])

;; =============================================================================
;; Cache

(defonce ^:private water-cache
  (atom {:canvas nil
         :bounds nil
         :frame-count 0}))

;; =============================================================================
;; Cache Rendering

(defn- get-or-create-canvas!
  "Get cached canvas or create new one if dimensions changed."
  [width height]
  (let [cached (:canvas @water-cache)]
    (if (and cached
             (= (.-width cached) width)
             (= (.-height cached) height))
      cached
      (let [canvas (js/document.createElement "canvas")]
        (set! (.-width canvas) width)
        (set! (.-height canvas) height)
        canvas))))

(defn- render-water-cache!
  "Render water to an offscreen canvas."
  []
  (let [topo (state/topo-data)
        water-grid (water-sim/water-grid)
        [grid-w grid-h] (water-sim/grid-dimensions)]
    (when (and topo water-grid (pos? grid-w) (pos? grid-h))
      (let [{:keys [bounds]} topo
            {:keys [min-x min-y max-x max-y]} bounds
            ;; Reuse cached canvas if dimensions match
            canvas (get-or-create-canvas! grid-w grid-h)
            ctx (.getContext canvas "2d")
            image-data (.createImageData ctx grid-w grid-h)
            pixels (.-data image-data)
            ;; Find max water for normalization
            [_ max-water] (water-sim/water-bounds)
            max-water (max 0.01 (or max-water 0.01))]
        ;; Fill pixels based on water height
        (dotimes [y grid-h]
          (dotimes [x grid-w]
            (let [idx (+ x (* y grid-w))
                  w (aget water-grid idx)
                  pixel-idx (* idx 4)]
              (if (> w 0.001)
                (let [;; Normalize water depth (log scale for better visibility)
                      normalized (min 1 (/ (js/Math.log (+ 1 (* w 100)))
                                           (js/Math.log (+ 1 (* max-water 100)))))
                      alpha (int (* normalized 200))  ; Max alpha 200/255
                      [r g b] water-color]
                  (aset pixels pixel-idx r)
                  (aset pixels (+ pixel-idx 1) g)
                  (aset pixels (+ pixel-idx 2) b)
                  (aset pixels (+ pixel-idx 3) alpha))
                ;; No water - transparent
                (do
                  (aset pixels pixel-idx 0)
                  (aset pixels (+ pixel-idx 1) 0)
                  (aset pixels (+ pixel-idx 2) 0)
                  (aset pixels (+ pixel-idx 3) 0))))))
        (.putImageData ctx image-data 0 0)
        ;; Store in cache
        (swap! water-cache assoc
               :canvas canvas
               :bounds {:left min-x :top min-y :right max-x :bottom max-y}
               :frame-count (inc (:frame-count @water-cache)))
        canvas))))

;; =============================================================================
;; Public API

(defn render-water!
  "Render water overlay to the main canvas."
  [ctx state]
  (when (water-sim/running?)
    ;; Update cache (every frame since water is animated)
    (render-water-cache!)
    (let [{:keys [canvas bounds]} @water-cache
          viewport (:viewport state)]
      (when (and canvas bounds)
        (let [{:keys [left top right bottom]} bounds
              {:keys [offset zoom]} viewport
              [ox oy] offset
              ;; Convert to screen coordinates
              screen-left (+ ox (* left zoom))
              screen-top (+ oy (* top zoom))
              screen-width (* (- right left) zoom)
              screen-height (* (- bottom top) zoom)]
          (.save ctx)
          ;; Flip Y because water grid is top-down but canvas is bottom-up
          (.drawImage ctx canvas
                      screen-left screen-top
                      screen-width screen-height)
          (.restore ctx))))))

(defn render-water-legend!
  "Render a legend showing water depth info."
  [ctx state]
  (when (water-sim/running?)
    (let [{:keys [width height]} (get-in state [:viewport :size])
          [_ max-water] (water-sim/water-bounds)
          margin 20
          legend-x margin
          legend-y (- height margin 40)]
      (.save ctx)
      (set! (.-fillStyle ctx) "rgba(30, 100, 200, 0.8)")
      (.fillRect ctx legend-x legend-y 15 15)
      (set! (.-font ctx) "12px sans-serif")
      (set! (.-fillStyle ctx) "#333")
      (set! (.-textAlign ctx) "left")
      (set! (.-textBaseline ctx) "middle")
      (.fillText ctx
                 (str "Water (max: " (.toFixed (or max-water 0) 2) ")")
                 (+ legend-x 20) (+ legend-y 7))
      (.restore ctx))))
