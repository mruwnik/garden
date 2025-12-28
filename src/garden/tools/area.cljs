(ns garden.tools.area
  "Area drawing tool for creating garden regions.

   Click to add vertices, close near the first point or press Enter
   to complete the polygon. Supports grid snapping."
  (:require [garden.tools.protocol :as proto]
            [garden.state :as state]
            [garden.util.geometry :as geom]))

(defn- maybe-snap
  "Snap point to grid if snap is enabled."
  [point]
  (let [snap? (state/get-state :ui :grid :snap?)
        spacing (state/get-state :ui :grid :spacing)]
    (if snap?
      (geom/snap-to-grid point spacing)
      point)))

(def close-threshold 15) ; Pixels to close polygon

(defn close-to-first?
  "Check if point is close enough to the first point to close the polygon.
   Returns true if points has >= 3 vertices and point is within close-threshold
   of the first point."
  [points point]
  (when (>= (count points) 3)
    (let [first-point (first points)
          dist (geom/points-distance first-point point)]
      (< dist close-threshold))))

(defn- finish-area!
  "Complete the area and add it to state."
  [points]
  (when (>= (count points) 3)
    (let [area-id (state/add-area! {:type :bed
                                    :name "New Area"
                                    :points (vec points)
                                    :color "#8B4513"})]
      (state/select! :area #{area-id})))
  (state/set-tool-state! {:points [] :preview-point nil}))

(defrecord AreaTool []
  proto/ITool
  (tool-id [_] :area)
  (tool-label [_] "Draw Area")
  (tool-icon [_] "polygon")
  (cursor [_] "crosshair")

  (on-activate [_]
    (state/set-tool-state! {:points [] :preview-point nil}))

  (on-deactivate [_]
    ;; Save any in-progress area before switching tools
    (let [points (:points (state/tool-state))]
      (when (>= (count points) 3)
        (finish-area! points)))
    (state/set-tool-state! nil))

  (on-mouse-down [_ point _event]
    (let [snapped-point (maybe-snap point)
          tool-state (state/tool-state)
          points (or (:points tool-state) [])]
      (if (close-to-first? points snapped-point)
        ;; Close the polygon
        (finish-area! points)
        ;; Add point
        (state/update-tool-state! update :points conj snapped-point))))

  (on-mouse-move [_ point _event]
    (state/update-tool-state! assoc :preview-point (maybe-snap point)))

  (on-mouse-up [_ _point _event]
    nil)

  (on-key-down [_ event]
    (case (.-key event)
      "Escape"
      (state/set-tool-state! {:points [] :preview-point nil})

      "Enter"
      (let [points (:points (state/tool-state))]
        (finish-area! points))

      nil)))

(proto/register-tool! (->AreaTool))
