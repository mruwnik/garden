(ns garden.tools.select
  "Selection and editing tool for areas and plants.

   Features:
   - Click to select areas or plants
   - Shift+click to multi-select
   - Drag to move selected items
   - Drag vertices to reshape areas
   - Click on edges to add new vertices
   - Delete/Backspace to remove selected items or vertices"
  (:require [garden.tools.protocol :as proto]
            [garden.state :as state]
            [garden.util.geometry :as geom]
            [garden.canvas.render :as render]))

;; =============================================================================
;; Constants

(def ^:private vertex-hit-radius 12)

;; Edge hit distance for inserting new points
(def ^:private edge-hit-distance 8)

;; =============================================================================
;; Hit Detection

(defn- find-vertex-at
  "Find if click is near a vertex of a selected area.
   Returns {:area-id :vertex-index :point} or nil."
  [point]
  (let [selection (state/selection)
        zoom (state/zoom)
        ;; Adjust hit radius for zoom level
        hit-radius (/ vertex-hit-radius zoom)]
    (when (= :area (:type selection))
      ;; Check vertices of all selected areas
      (first
       (for [area-id (:ids selection)
             :let [area (state/find-area area-id)]
             :when area
             [idx vertex] (map-indexed vector (:points area))
             :when (< (geom/points-distance vertex point) hit-radius)]
         {:area-id area-id
          :vertex-index idx
          :point vertex})))))

(defn- find-edge-at
  "Find if click is near an edge of a selected area.
   Returns {:area-id :edge-index :point} or nil.
   edge-index is the index of the first point of the edge."
  [point]
  (let [selection (state/selection)
        zoom (state/zoom)
        hit-distance (/ edge-hit-distance zoom)]
    (when (= :area (:type selection))
      (first
       (for [area-id (:ids selection)
             :let [area (state/find-area area-id)
                   points (:points area)]
             :when (and area (>= (count points) 2))
             edge-idx (range (count points))
             :let [p1 (nth points edge-idx)
                   p2 (nth points (mod (inc edge-idx) (count points)))
                   dist (geom/distance-from-segment p1 p2 point)]
             :when (< dist hit-distance)]
         {:area-id area-id
          :edge-index edge-idx
          :point point})))))

(defn- find-area-at
  "Find the topmost area containing the point.
   Accounts for holes - clicking in a hole won't select the area."
  [point]
  (let [areas (reverse (state/areas))] ; Check top-to-bottom
    (first (filter #(if (:holes %)
                      (geom/point-in-polygon-with-holes? (:points %) (:holes %) point)
                      (geom/point-in-polygon? (:points %) point))
                   areas))))

(defn- find-plant-at
  "Find a plant near the point using actual plant radius."
  [point]
  (let [plants (state/plants)]
    ;; Use the actual plant radius for click detection (+ some extra padding)
    (first (filter (fn [plant]
                     (let [radius (+ (render/get-plant-radius plant) 10)]
                       (< (geom/points-distance (:position plant) point) radius)))
                   plants))))

(defn- maybe-snap
  "Snap point to grid if snap is enabled."
  [point]
  (let [snap? (state/get-state :ui :grid :snap?)
        spacing (state/get-state :ui :grid :spacing)]
    (if snap?
      (geom/snap-to-grid point spacing)
      point)))

;; =============================================================================
;; Tool Implementation

(defrecord SelectTool []
  proto/ITool
  (tool-id [_] :select)
  (tool-label [_] "Select")
  (tool-icon [_] "pointer")
  (cursor [_] "default")

  (on-activate [_]
    (state/set-tool-state! {:dragging? false
                            :drag-start nil
                            :drag-offset nil}))

  (on-deactivate [_]
    (state/set-tool-state! nil))

  (on-mouse-down [_ point event]
    (let [shift? (.-shiftKey event)
          ;; Check for vertex/edge of selected area first
          vertex-hit (find-vertex-at point)
          edge-hit (when-not vertex-hit (find-edge-at point))
          ;; Then try to find something at this point
          plant (find-plant-at point)
          area (find-area-at point)]
      (cond
        ;; Clicked on a vertex handle - select it and start dragging
        vertex-hit
        (state/set-tool-state! {:dragging? true
                                :drag-start point
                                :drag-type :vertex
                                :vertex-area-id (:area-id vertex-hit)
                                :vertex-index (:vertex-index vertex-hit)
                                ;; Keep track of selected vertex for deletion
                                :selected-vertex vertex-hit})

        ;; Double-click on edge to insert a new point
        edge-hit
        (let [area (state/find-area (:edge-index edge-hit))
              snapped-point (maybe-snap point)]
          ;; Insert point on the edge
          (when-let [area (state/find-area (:area-id edge-hit))]
            (let [edge-idx (:edge-index edge-hit)
                  old-points (:points area)
                  ;; Insert new point after edge-idx
                  new-points (vec (concat
                                   (take (inc edge-idx) old-points)
                                   [snapped-point]
                                   (drop (inc edge-idx) old-points)))]
              (state/update-area! (:area-id edge-hit) {:points new-points}))))

        ;; Clicked on a plant
        plant
        (do
          (if shift?
            (state/toggle-selection! :plant (:id plant))
            (state/select! :plant #{(:id plant)}))
          (state/set-tool-state! {:dragging? true
                                  :drag-start point
                                  :drag-type :plant
                                  :drag-ids #{(:id plant)}
                                  :selected-vertex nil}))

        ;; Clicked on an area
        area
        (do
          (if shift?
            (state/toggle-selection! :area (:id area))
            (state/select! :area #{(:id area)}))
          (state/set-tool-state! {:dragging? true
                                  :drag-start point
                                  :drag-type :area
                                  :drag-ids #{(:id area)}
                                  :selected-vertex nil}))

        ;; Clicked on nothing
        :else
        (do
          (state/clear-selection!)
          (state/update-tool-state! dissoc :selected-vertex)))))

  (on-mouse-move [_ point _event]
    (let [tool-state (state/tool-state)]
      (if (and tool-state (:dragging? tool-state))
        ;; Dragging mode
        (let [[sx sy] (:drag-start tool-state)
              [cx cy] point
              dx (- cx sx)
              dy (- cy sy)]
          (case (:drag-type tool-state)
            ;; Dragging a single vertex
            :vertex
            (let [area-id (:vertex-area-id tool-state)
                  vertex-idx (:vertex-index tool-state)
                  snapped-point (maybe-snap point)]
              (when-let [area (state/find-area area-id)]
                (let [new-points (assoc (:points area) vertex-idx snapped-point)]
                  (state/update-area! area-id {:points new-points}))))

            ;; Dragging whole area
            :area
            (doseq [id (:drag-ids tool-state)]
              (when-let [area (state/find-area id)]
                (let [new-points (mapv (fn [[x y]] [(+ x dx) (+ y dy)])
                                       (:points area))]
                  (state/update-area! id {:points new-points}))))

            ;; Dragging plants
            :plant
            (doseq [id (:drag-ids tool-state)]
              (when-let [plant (state/find-plant id)]
                (let [[px py] (:position plant)]
                  (state/update-plant! id {:position [(+ px dx) (+ py dy)]}))))

            nil)
          ;; Update drag start for continuous movement (not needed for vertex mode)
          (when (not= :vertex (:drag-type tool-state))
            (state/update-tool-state! assoc :drag-start point)))
        ;; Hover mode - check for vertex/edge hits and update cursor
        (let [vertex-hit (find-vertex-at point)
              edge-hit (when-not vertex-hit (find-edge-at point))]
          (cond
            vertex-hit
            (do
              (state/set-cursor! "move")
              (state/update-tool-state! assoc
                                        :hover-vertex vertex-hit
                                        :hover-edge nil))
            edge-hit
            (do
              (state/set-cursor! "crosshair")
              (state/update-tool-state! assoc
                                        :hover-vertex nil
                                        :hover-edge edge-hit))
            :else
            (do
              (state/set-cursor! "default")
              (state/update-tool-state! assoc
                                        :hover-vertex nil
                                        :hover-edge nil)))))))

  (on-mouse-up [_ _point _event]
    (state/update-tool-state! assoc :dragging? false))

  (on-key-down [_ event]
    (case (.-key event)
      ("Backspace" "Delete")
      (let [tool-state (state/tool-state)
            ;; Check for selected vertex first, then hovered vertex
            target-vertex (or (:selected-vertex tool-state)
                              (:hover-vertex tool-state))]
        (if target-vertex
          ;; Delete the vertex (if area has more than 3 points)
          (when-let [area (state/find-area (:area-id target-vertex))]
            (when (> (count (:points area)) 3)
              (let [vertex-idx (:vertex-index target-vertex)
                    new-points (vec (concat
                                     (take vertex-idx (:points area))
                                     (drop (inc vertex-idx) (:points area))))]
                (state/update-area! (:area-id target-vertex) {:points new-points})
                ;; Clear the selected vertex
                (state/update-tool-state! dissoc :selected-vertex))))
          ;; Delete selected items
          (let [selection (state/selection)]
            (case (:type selection)
              :area (doseq [id (:ids selection)]
                      (state/remove-area! id))
              :plant (doseq [id (:ids selection)]
                       (state/remove-plant! id))
              nil)
            (state/clear-selection!))))

      "Escape"
      (state/clear-selection!)

      nil)))

(proto/register-tool! (->SelectTool))
