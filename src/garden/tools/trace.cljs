(ns garden.tools.trace
  "Freehand tracing tool for creating areas.

   Click and drag to draw, release to create an area. Points are
   automatically simplified to reduce polygon complexity.

   Keyboard shortcuts:
   - 0-9: Select area type (1=water, 2=bed, 3=path, etc.)
   - w/b/p/l/h: Quick type selection"
  (:require [garden.tools.protocol :as p]
            [garden.state :as state]))

(defn- simplify-points
  "Reduce number of points by removing those too close together.
   min-distance is in garden cm."
  [points min-distance]
  (if (< (count points) 3)
    points
    (reduce (fn [acc pt]
              (let [last-pt (peek acc)]
                (if (or (nil? last-pt)
                        (let [[x1 y1] last-pt
                              [x2 y2] pt
                              dist (js/Math.sqrt (+ (* (- x2 x1) (- x2 x1))
                                                    (* (- y2 y1) (- y2 y1))))]
                          (> dist min-distance)))
                  (conj acc pt)
                  acc)))
            []
            points)))

(def area-type-info
  "Area type definitions with colors and names."
  {:water     {:name "Water" :color "#4a90d9"}
   :bed       {:name "Garden Bed" :color "#8B6914"}
   :path      {:name "Path" :color "#d4a574"}
   :structure {:name "Structure" :color "#607D8B"}
   :lawn      {:name "Lawn" :color "#7CB342"}
   :rocks     {:name "Rocks" :color "#9E9E9E"}
   :hedge     {:name "Hedge" :color "#2E7D32"}
   :mulch     {:name "Mulch" :color "#5D4037"}
   :patio     {:name "Patio" :color "#8D6E63"}
   :sand      {:name "Sand" :color "#E8D5B7"}})

(defn- create-area-from-points!
  "Create an area from the traced points."
  [points area-type]
  (let [simplified (simplify-points points 50)  ; Simplify to ~50cm resolution
        ;; Ensure we have enough points
        final-points (if (< (count simplified) 3)
                       points
                       simplified)]
    (when (>= (count final-points) 3)
      (let [type-info (get area-type-info area-type {:name "Area" :color "#888888"})
            area-id (state/add-area! {:name (str (:name type-info) " " (inc (count (state/areas))))
                                      :type area-type
                                      :points final-points
                                      :color (:color type-info)})]
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
