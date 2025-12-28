(ns garden.tools.trace
  "Freehand tracing tool for creating areas.

   Click and drag to draw, release to create an area. Points are
   automatically simplified to reduce polygon complexity.

   Keyboard shortcuts:
   - 0-9: Select area type (1=bed, 2=path, 3=water, etc.)
   - w/b/p/l/h: Quick type selection"
  (:require [garden.tools.protocol :as p]
            [garden.state :as state]
            [garden.data.area-types :as area-types]
            [garden.util.simplify :as simplify]
            [garden.constants :as const]))

(defn- create-area-from-points!
  "Create an area from the traced points."
  [points area-type]
  (let [simplified (simplify/simplify-by-distance points const/trace-simplify-tolerance-cm)
        ;; Ensure we have enough points
        final-points (if (< (count simplified) 3)
                       points
                       simplified)]
    (when (>= (count final-points) 3)
      (let [label (or (area-types/get-label area-type) "Area")
            color (or (area-types/get-color area-type) "#888888")
            area-id (state/add-area! {:name (str label " " (inc (count (state/areas))))
                                      :type area-type
                                      :points final-points
                                      :color color})]
        (state/select! :area #{area-id})))))

(defrecord TraceTool []
  p/ITool
  (tool-id [_] :trace)
  (tool-label [_] "Trace")
  (tool-icon [_] "trace")
  (cursor [_] "crosshair")

  (on-activate [_]
    (state/set-tool-state! {:points []
                            :drawing? false
                            :area-type :water}))  ; Default to water for tracing ponds

  (on-deactivate [_]
    (state/set-tool-state! nil))

  (on-mouse-down [_ point _event]
    ;; point is already in canvas coordinates (protocol converts it)
    (state/set-tool-state! {:points [point]
                            :drawing? true
                            :area-type (or (:area-type (state/tool-state)) :water)}))

  (on-mouse-move [_ point _event]
    ;; point is already in canvas coordinates
    (when (:drawing? (state/tool-state))
      (state/update-tool-state! update :points conj point)))

  (on-mouse-up [_ _screen-point _event]
    (let [{:keys [points drawing? area-type]} (state/tool-state)]
      (when (and drawing? (seq points))
        (create-area-from-points! points area-type))
      (state/update-tool-state! assoc :points [] :drawing? false)))

  (on-key-down [_ event]
    (let [key (.-key event)]
      ;; Number keys for area types:
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
        ;; Also keep letter shortcuts for common types
        "w" (state/update-tool-state! assoc :area-type :water)
        "b" (state/update-tool-state! assoc :area-type :bed)
        "p" (state/update-tool-state! assoc :area-type :path)
        "l" (state/update-tool-state! assoc :area-type :lawn)
        "h" (state/update-tool-state! assoc :area-type :hedge)
        nil))))

;; Register the tool
(p/register-tool! (->TraceTool))
