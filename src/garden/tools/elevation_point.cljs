(ns garden.tools.elevation-point
  "Manual elevation point placement tool.

   Click to place elevation points that provide reference data for terrain
   interpolation when no GeoTIFF is available. Points can be dragged to
   reposition and deleted with Backspace/Delete.

   Workflow:
   1. Click on empty space to start placing a point
   2. Type elevation value (in meters)
   3. Press Enter to confirm
   4. Click existing points to select/drag them

   Keyboard shortcuts:
   - 0-9, ., -: Enter elevation value
   - Enter: Confirm elevation and place point
   - Delete/Backspace: Remove selected point
   - Escape: Cancel current placement"
  (:require [garden.tools.protocol :as p]
            [garden.state :as state]
            [garden.util.geometry :as geom]))

;; =============================================================================
;; Constants

(def ^:private point-hit-radius
  "Click radius for hitting existing points (in canvas units)."
  15)

;; =============================================================================
;; Hit Detection

(defn- find-topo-point-at
  "Find a topo point near the given canvas position."
  [point]
  (let [zoom (state/zoom)
        hit-radius (/ point-hit-radius zoom)
        points (state/topo-points)]
    (first (filter (fn [tp]
                     (< (geom/points-distance (:position tp) point) hit-radius))
                   points))))

;; =============================================================================
;; Tool Implementation

(defrecord ElevationPointTool []
  p/ITool
  (tool-id [_] :elevation-point)
  (tool-label [_] "Elevation Point")
  (tool-icon [_] "elevation-point")
  (cursor [_] "crosshair")

  (on-activate [_]
    (state/set-tool-state! {:pending-position nil  ; Position waiting for elevation input
                            :elevation-input ""    ; Current input value
                            :selected-point-id nil ; Selected existing point
                            :dragging? false
                            :drag-point-id nil}))

  (on-deactivate [_]
    (state/set-tool-state! nil))

  (on-mouse-down [_ point _event]
    (let [existing (find-topo-point-at point)
          current-state (state/tool-state)]
      (cond
        ;; If there's a pending position awaiting elevation, clicking elsewhere cancels
        (:pending-position current-state)
        (state/update-tool-state! assoc
                                  :pending-position nil
                                  :elevation-input "")

        ;; Clicked on existing point - select it for editing/moving
        existing
        (state/update-tool-state! assoc
                                  :selected-point-id (:id existing)
                                  :dragging? true
                                  :drag-point-id (:id existing)
                                  :elevation-input (str (:elevation existing)))

        ;; Clicked on empty space - start placing a new point
        :else
        (state/update-tool-state! assoc
                                  :pending-position point
                                  :elevation-input ""
                                  :selected-point-id nil))))

  (on-mouse-move [_ point _event]
    (let [{:keys [dragging? drag-point-id]} (state/tool-state)]
      (when (and dragging? drag-point-id)
        ;; Dragging an existing point
        (state/update-topo-point! drag-point-id {:position point}))))

  (on-mouse-up [_ _point _event]
    (state/update-tool-state! assoc :dragging? false :drag-point-id nil))

  (on-key-down [_ event]
    (let [key (.-key event)
          {:keys [pending-position elevation-input selected-point-id]} (state/tool-state)]
      (cond
        ;; Number keys and decimal point for elevation input
        (and pending-position (re-matches #"[0-9\.\-]" key))
        (state/update-tool-state! update :elevation-input str key)

        ;; Backspace to delete input character
        (and pending-position (= key "Backspace") (seq elevation-input))
        (state/update-tool-state! update :elevation-input #(subs % 0 (dec (count %))))

        ;; Enter to confirm elevation
        (and pending-position (= key "Enter") (seq elevation-input))
        (when-let [elev (js/parseFloat elevation-input)]
          (when-not (js/isNaN elev)
            (state/add-topo-point! {:position pending-position
                                    :elevation elev})
            ;; Update topo source to manual if not already set
            (when-not (state/topo-elevation-data)
              (state/update-state! [:topo :source] (constantly :manual)))
            (state/update-tool-state! assoc
                                      :pending-position nil
                                      :elevation-input "")))

        ;; Delete selected point
        (and selected-point-id (contains? #{"Backspace" "Delete"} key))
        (do
          (state/remove-topo-point! selected-point-id)
          (state/update-tool-state! assoc :selected-point-id nil))

        ;; Escape to cancel
        (= key "Escape")
        (state/update-tool-state! assoc
                                  :pending-position nil
                                  :elevation-input ""
                                  :selected-point-id nil)

        :else nil))))

;; Register the tool
(p/register-tool! (->ElevationPointTool))
