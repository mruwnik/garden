(ns garden.tools.contour-trace
  "Contour tracing tool for topography-aware area creation.

   Click on a contour line to start tracing along that elevation.
   The tool automatically snaps to the contour as you draw.
   Areas include the contour elevation in their properties.

   Keyboard shortcuts:
   - 0-9: Select area type (1=water, 2=bed, 3=path, etc.)
   - w/b/p/l/h: Quick type selection
   - Escape: Cancel current trace"
  (:require [garden.tools.protocol :as p]
            [garden.state :as state]
            [garden.topo.core :as topo]
            [garden.data.area-types :as area-types]
            [garden.util.simplify :as simplify]))

(def ^:private snap-tolerance
  "How close (in meters of elevation) to snap to a contour."
  0.5)

(defn- round-to-contour
  "Round elevation to nearest contour line based on interval."
  [elevation interval]
  (when (and elevation interval (pos? interval))
    (* interval (Math/round (/ elevation interval)))))

(defn- get-contour-at
  "Get the contour elevation at the given point.
   Returns the nearest contour line elevation."
  [point]
  (when-let [elevation (topo/get-elevation-at point)]
    (let [interval (or (state/get-state :topo :contours :interval) 1)]
      (round-to-contour elevation interval))))

(defn- snap-to-contour
  "Snap point to the nearest position on the target contour elevation.
   Uses a simple search in the vicinity of the point."
  [point target-elevation]
  (when (and point target-elevation)
    (let [[x y] point
          resolution (or (state/topo-resolution) 100) ; cm
          search-radius (* 5 resolution) ; Search within 5 cells
          best (atom {:point point :dist js/Infinity})]
      ;; Search in a grid around the point
      (doseq [dx (range (- search-radius) search-radius (/ resolution 2))
              dy (range (- search-radius) search-radius (/ resolution 2))]
        (let [test-pt [(+ x dx) (+ y dy)]
              elev (topo/get-elevation-at test-pt)]
          (when elev
            (let [elev-diff (Math/abs (- elev target-elevation))
                  spatial-dist (Math/sqrt (+ (* dx dx) (* dy dy)))]
              ;; Prefer points closer to target elevation, then closer spatially
              (when (and (< elev-diff snap-tolerance)
                         (< spatial-dist (:dist @best)))
                (reset! best {:point test-pt :dist spatial-dist}))))))
      (:point @best))))

(defn- create-area-from-points!
  "Create an area from the traced points."
  [points area-type contour-elevation]
  (let [simplified (simplify/simplify-by-distance points 50)
        final-points (if (< (count simplified) 3) points simplified)]
    (when (>= (count final-points) 3)
      (let [label (or (area-types/get-label area-type) "Area")
            color (or (area-types/get-color area-type) "#888888")
            area-name (str label
                           (when contour-elevation
                             (str " @" (.toFixed contour-elevation 0) "m"))
                           " " (inc (count (state/areas))))
            area-id (state/add-area! {:name area-name
                                      :type area-type
                                      :points final-points
                                      :color color
                                      :properties {:contour-elevation contour-elevation}})]
        (state/select! :area #{area-id})))))

(defrecord ContourTraceTool []
  p/ITool
  (tool-id [_] :contour-trace)
  (tool-label [_] "Contour Trace")
  (tool-icon [_] "contour-trace")
  (cursor [_] "crosshair")

  (on-activate [_]
    (state/set-tool-state! {:points []
                            :drawing? false
                            :area-type :bed
                            :target-elevation nil
                            :preview-point nil}))

  (on-deactivate [_]
    (state/set-tool-state! nil))

  (on-mouse-down [_ point _event]
    (let [current-state (state/tool-state)
          contour-elev (get-contour-at point)]
      (if contour-elev
        ;; Start tracing at this contour elevation
        (state/set-tool-state!
         (merge current-state
                {:points [point]
                 :drawing? true
                 :target-elevation contour-elev}))
        ;; No topo data at this point
        (js/console.warn "No elevation data at this point"))))

  (on-mouse-move [_ point _event]
    (let [{:keys [drawing? target-elevation]} (state/tool-state)]
      (if drawing?
        ;; Try to snap to the contour while tracing
        (let [snapped (snap-to-contour point target-elevation)]
          (state/update-tool-state!
           #(-> %
                (update :points conj (or snapped point))
                (assoc :preview-point snapped))))
        ;; Just preview the contour elevation
        (let [contour-elev (get-contour-at point)]
          (state/update-tool-state!
           assoc :preview-point point :hover-elevation contour-elev)))))

  (on-mouse-up [_ _point _event]
    (let [{:keys [points drawing? area-type target-elevation]} (state/tool-state)]
      (when (and drawing? (>= (count points) 3))
        (create-area-from-points! points area-type target-elevation))
      (state/update-tool-state!
       assoc :points [] :drawing? false :target-elevation nil)))

  (on-key-down [_ event]
    (let [key (.-key event)]
      ;; Number keys for area types (matching trace tool):
      ;; 1=Water, 2=Bed, 3=Path, 4=Structure, 5=Lawn
      ;; 6=Rocks, 7=Hedge, 8=Mulch, 9=Patio, 0=Sand
      (case key
        "1" (state/update-tool-state! assoc :area-type :water)
        "2" (state/update-tool-state! assoc :area-type :bed)
        "3" (state/update-tool-state! assoc :area-type :path)
        "4" (state/update-tool-state! assoc :area-type :structure)
        "5" (state/update-tool-state! assoc :area-type :lawn)
        "6" (state/update-tool-state! assoc :area-type :rocks)
        "7" (state/update-tool-state! assoc :area-type :hedge)
        "8" (state/update-tool-state! assoc :area-type :mulch)
        "9" (state/update-tool-state! assoc :area-type :patio)
        "0" (state/update-tool-state! assoc :area-type :sand)
        ;; Letter shortcuts for common types
        "w" (state/update-tool-state! assoc :area-type :water)
        "b" (state/update-tool-state! assoc :area-type :bed)
        "p" (state/update-tool-state! assoc :area-type :path)
        "l" (state/update-tool-state! assoc :area-type :lawn)
        "h" (state/update-tool-state! assoc :area-type :hedge)
        "Escape" (state/update-tool-state!
                  assoc :points [] :drawing? false :target-elevation nil)
        nil))))

;; Register the tool
(p/register-tool! (->ContourTraceTool))
