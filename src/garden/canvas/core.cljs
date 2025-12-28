(ns garden.canvas.core
  (:require [garden.state :as state]
            [garden.canvas.viewport :as viewport]
            [garden.canvas.grid :as grid]
            [garden.canvas.render :as render]
            [garden.canvas.reference :as reference]
            [garden.canvas.topo :as topo]))

;; Dirty tracking for efficient rendering
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

;; Performance tracking
(defonce ^:private last-render-time (atom 0))
(defonce ^:private render-times (atom []))

(defn get-avg-render-time
  "Get average render time in ms (last 30 frames)."
  []
  (let [times @render-times]
    (when (seq times)
      (/ (reduce + times) (count times)))))

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

      ;; Apply viewport transform (pan + zoom)
      (viewport/apply-transform! ctx)

      ;; Render background texture
      (when (get-in state [:ui :background :visible?])
        (render/render-background! ctx state))

      ;; Render reference image overlay (behind areas/plants)
      (reference/render! ctx state)

      ;; Render topographical data overlay
      (topo/render! ctx state)

      ;; Render grid
      (when (get-in state [:ui :grid :visible?])
        (grid/render! ctx state))

      ;; Render areas and plants
      (render/render-areas! ctx state)
      (render/render-plants! ctx state)

      ;; Render selection highlights
      (render/render-selection! ctx state)

      ;; Restore context (remove transform)
      (.restore ctx)

      ;; Render tool overlay (in screen coordinates)
      (render/render-tool-overlay! ctx state)

      ;; Render tooltip for hovered plant
      (render/render-tooltip! ctx state)

      ;; Render reference image scale bar (in screen coords)
      (reference/render-scale-bar! ctx state)

      ;; Render topo elevation legend (in screen coords)
      (topo/render-elevation-legend! ctx state)

      ;; Update last render state
      (reset! last-render-state (render-keys state))

      ;; Track render time
      (let [elapsed (- (.now js/performance) start-time)]
        (reset! last-render-time elapsed)
        (swap! render-times (fn [times]
                              (let [new-times (conj times elapsed)]
                                (if (> (count new-times) 30)
                                  (vec (rest new-times))
                                  new-times))))))))

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

;; Watch app-state for changes and trigger renders
(defonce ^:private render-watcher
  (add-watch state/app-state :render
             (fn [_ _ _ _]
               (schedule-render!))))

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
