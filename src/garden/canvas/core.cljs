(ns garden.canvas.core
  "Canvas rendering orchestration.

   This namespace coordinates all canvas rendering:
   - Dirty tracking for efficient updates
   - Layer ordering (background, topo, water, grid, areas, plants)
   - Performance monitoring
   - Render scheduling via requestAnimationFrame"
  (:require [garden.state :as state]
            [garden.canvas.viewport :as viewport]
            [garden.canvas.grid :as grid]
            [garden.canvas.render :as render]
            [garden.canvas.reference :as reference]
            [garden.canvas.topo :as topo]
            [garden.canvas.water :as water]
            [garden.simulation.water :as water-sim]))

;; =============================================================================
;; Dirty Tracking

(defonce ^:private last-render-state (atom nil))
(defonce ^:private render-scheduled? (atom false))

(defn- render-keys
  "Extract the state keys that affect rendering."
  [state]
  (select-keys state [:areas :plants :viewport :tool :selection :ui :topo :topo-points]))

(defn- needs-render?
  "Check if state has changed since last render."
  [state]
  (not= (render-keys state) @last-render-state))

;; =============================================================================
;; Performance Tracking

(defonce ^:private last-render-time (atom 0))

;; Ring buffer for render times - avoids allocation on every frame
(def ^:private render-time-size 30)
(defonce ^:private render-time-buffer (js/Float64Array. render-time-size))
(defonce ^:private render-time-index (atom 0))
(defonce ^:private render-time-count (atom 0))

(defn- record-render-time!
  "Record a render time to the ring buffer without allocation."
  [elapsed]
  (let [idx @render-time-index]
    (aset render-time-buffer idx elapsed)
    (reset! render-time-index (mod (inc idx) render-time-size))
    (when (< @render-time-count render-time-size)
      (swap! render-time-count inc))))

(defn get-avg-render-time
  "Get average render time in ms (last 30 frames)."
  []
  (let [cnt @render-time-count]
    (when (pos? cnt)
      (loop [i 0 sum 0.0]
        (if (< i cnt)
          (recur (inc i) (+ sum (aget render-time-buffer i)))
          (/ sum cnt))))))

;; =============================================================================
;; Core Render Loop

(defn render!
  "Render the current state to the canvas."
  []
  (when-let [ctx (state/canvas-ctx)]
    (let [start-time (.now js/performance)
          state @state/app-state
          {:keys [width height]} (get-in state [:viewport :size])]

      ;; Clear canvas
      (.clearRect ctx 0 0 width height)

      ;; Save context state
      (.save ctx)

      (try
        ;; Apply viewport transform (pan + zoom)
        (viewport/apply-transform! ctx)

        ;; Render background texture
        (when (get-in state [:ui :background :visible?])
          (render/render-background! ctx state))

        ;; Render reference image overlay (behind areas/plants)
        (reference/render! ctx state)

        ;; Render topographical data overlay
        (topo/render! ctx state)

        ;; Render water simulation overlay
        (water/render-water! ctx state)

        ;; Render grid
        (when (get-in state [:ui :grid :visible?])
          (grid/render! ctx state))

        ;; Compute visible bounds once for culling
        (let [bounds (viewport/visible-bounds)]
          ;; Render areas and plants
          (render/render-areas! ctx state bounds)
          (render/render-plants! ctx state bounds))

        ;; Render selection highlights
        (render/render-selection! ctx state)

        (catch :default e
          (js/console.error "Render error:" e))

        (finally
          ;; Always restore context to prevent state leakage
          (.restore ctx)))

      ;; Render tool overlay (in screen coordinates)
      (render/render-tool-overlay! ctx state)

      ;; Render tooltip for hovered plant
      (render/render-tooltip! ctx state)

      ;; Render reference image scale bar (in screen coords)
      (reference/render-scale-bar! ctx state)

      ;; Render topo elevation legend (in screen coords)
      (topo/render-elevation-legend! ctx state)

      ;; Render water legend (in screen coords)
      (water/render-water-legend! ctx state)

      ;; Update last render state
      (reset! last-render-state (render-keys state))

      ;; Track render time
      (let [elapsed (- (.now js/performance) start-time)]
        (reset! last-render-time elapsed)
        (record-render-time! elapsed)))))

(defn schedule-render!
  "Schedule a render on the next animation frame if needed."
  []
  (when (and (needs-render? @state/app-state)
             (not @render-scheduled?))
    (reset! render-scheduled? true)
    (js/requestAnimationFrame
     (fn [_]
       (render!)
       (reset! render-scheduled? false)))))

(defn force-render!
  "Force an immediate render."
  []
  (reset! last-render-state nil)
  (render!))

;; =============================================================================
;; Reactive Rendering

(defonce ^:private render-watcher
  (add-watch state/app-state :render
             (fn [_ _ _ _]
               (schedule-render!))))

;; =============================================================================
;; Water Controls (delegated to simulation)

(defn start-rain!
  "Start rain (and simulation if not running)."
  []
  (water-sim/start-rain!))

(defn stop-rain!
  "Stop rain but keep simulation running so water flows away."
  []
  (water-sim/stop-rain!))

(defn reset-water!
  "Clear all water."
  []
  (water-sim/reset-water!)
  (force-render!))

;; =============================================================================
;; Canvas Initialization

(defn init-canvas!
  "Initialize canvas with the given DOM element."
  [canvas-el]
  (let [ctx (.getContext canvas-el "2d")]
    (state/set-viewport-ctx! ctx)
    (force-render!)))

(defn resize-canvas!
  "Handle canvas resize."
  [width height]
  (state/set-viewport-size! width height)
  (force-render!))
