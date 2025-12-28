(ns garden.tools.plant
  "Plant placement tool.

   Supports two modes:
   - Single: Click to place individual plants
   - Row: Click and drag to create a row of evenly spaced plants
   Press 'r' to toggle between modes."
  (:require [garden.tools.protocol :as proto]
            [garden.state :as state]
            [garden.util.geometry :as geom]
            [garden.data.plants :as plants]))

(def ^:private fallback-spacing 50) ; Fallback spacing if plant not found (cm)

(defn- get-default-species-id
  "Get a valid default species ID from the plant library."
  []
  (:id (first plants/plant-library)))

(defn- get-species-spacing
  "Get the spacing for a species from the plant library.
   Returns spacing-cm from plant data, or fallback if not found."
  [species-id]
  (if-let [plant-data (plants/get-plant species-id)]
    (or (:spacing-cm plant-data) fallback-spacing)
    fallback-spacing))

(defn- calculate-row-positions
  "Calculate evenly spaced positions along a line from start to end."
  [start end spacing]
  (let [dist (geom/points-distance start end)
        count (max 1 (Math/floor (/ dist spacing)))
        [x1 y1] start
        [x2 y2] end
        dx (- x2 x1)
        dy (- y2 y1)]
    (if (< dist 5)
      ;; Too short for a row, just return start point
      [start]
      ;; Generate evenly spaced positions
      (for [i (range (inc count))]
        (let [t (if (zero? count) 0 (/ i count))]
          [(+ x1 (* dx t))
           (+ y1 (* dy t))])))))

(defrecord PlantTool []
  proto/ITool
  (tool-id [_] :plant)
  (tool-label [_] "Plant")
  (tool-icon [_] "flower")
  (cursor [_] "crosshair")

  (on-activate [_]
    (state/set-tool-state! {:mode :single
                            :species-id nil
                            :preview-position nil
                            :drag-start nil
                            :row-preview nil}))

  (on-deactivate [_]
    (state/set-tool-state! nil))

  (on-mouse-down [_ point _event]
    (let [tool-state (state/tool-state)
          mode (:mode tool-state)]
      (if (= mode :row)
        ;; Start row drag
        (state/update-tool-state! assoc :drag-start point :row-preview [point])
        ;; Single mode: place immediately
        (let [species-id (or (:species-id tool-state) (get-default-species-id))
              plant-id (state/add-plant! {:species-id species-id
                                          :position point
                                          :planted-date (js/Date.)
                                          :source :seedling})]
          (state/select! :plant #{plant-id})))))

  (on-mouse-move [_ point _event]
    (let [tool-state (state/tool-state)
          mode (:mode tool-state)
          drag-start (:drag-start tool-state)]
      (if (and (= mode :row) drag-start)
        ;; Update row preview
        (let [species-id (or (:species-id tool-state) (get-default-species-id))
              spacing (get-species-spacing species-id)
              positions (calculate-row-positions drag-start point spacing)]
          (state/update-tool-state! assoc
                                    :preview-position point
                                    :row-preview positions))
        ;; Single mode: update preview position
        (state/update-tool-state! assoc :preview-position point))))

  (on-mouse-up [_ point _event]
    (let [tool-state (state/tool-state)
          mode (:mode tool-state)
          drag-start (:drag-start tool-state)
          species-id (or (:species-id tool-state) (get-default-species-id))]
      (when (and (= mode :row) drag-start)
        ;; Place all plants in the row as a batch (single undo operation)
        (let [spacing (get-species-spacing species-id)
              positions (calculate-row-positions drag-start point spacing)
              plants (mapv (fn [pos]
                             {:species-id species-id
                              :position pos
                              :planted-date (js/Date.)
                              :source :seedling})
                           positions)
              plant-ids (state/add-plants-batch! plants)]
          (state/select! :plant (set plant-ids))))
      ;; Clear drag state
      (state/update-tool-state! assoc :drag-start nil :row-preview nil)))

  (on-key-down [_ event]
    (case (.-key event)
      "Escape"
      (state/update-tool-state! assoc
                                :preview-position nil
                                :drag-start nil
                                :row-preview nil)

      ;; Toggle mode with 'r' key
      "r"
      (let [current-mode (:mode (state/tool-state))]
        (state/update-tool-state! assoc :mode (if (= current-mode :row) :single :row)))

      nil)))

(proto/register-tool! (->PlantTool))
