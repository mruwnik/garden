(ns garden.canvas.render
  (:require [garden.state :as state]
            [garden.ui.panels.library :as library]))

;; Background rendering

(defn render-background!
  "Render a grass/soil background texture."
  [ctx state]
  (let [{:keys [offset zoom]} (:viewport state)
        [ox oy] offset
        {:keys [width height]} (get-in state [:viewport :size])
        ;; Calculate visible area in canvas coordinates
        start-x (/ (- ox) zoom)
        start-y (/ (- oy) zoom)
        end-x (/ (- width ox) zoom)
        end-y (/ (- height oy) zoom)
        ;; Grass texture spacing
        spacing 25]
    ;; Base grass green color
    (set! (.-fillStyle ctx) "#90A955")
    (.fillRect ctx start-x start-y (- end-x start-x) (- end-y start-y))
    ;; Add subtle grass texture with dots and small strokes
    (set! (.-fillStyle ctx) "rgba(120, 150, 80, 0.3)")
    (doseq [x (range (- start-x spacing) end-x spacing)
            y (range (- start-y spacing) end-y spacing)]
      (let [offset-x (* 5 (Math/sin (+ x y)))
            offset-y (* 5 (Math/cos (* x 0.5)))]
        (.beginPath ctx)
        (.arc ctx (+ x offset-x) (+ y offset-y) 2 0 (* 2 Math/PI))
        (.fill ctx)))
    ;; Add darker grass patches
    (set! (.-fillStyle ctx) "rgba(70, 100, 50, 0.15)")
    (doseq [x (range (- start-x 50) end-x 50)
            y (range (- start-y 50) end-y 50)]
      (let [size (+ 10 (* 10 (Math/abs (Math/sin (* x y 0.001)))))]
        (.beginPath ctx)
        (.arc ctx x y size 0 (* 2 Math/PI))
        (.fill ctx)))))

(defn- draw-polygon!
  "Draw a filled polygon."
  [ctx points color]
  (when (seq points)
    (.beginPath ctx)
    (let [[x y] (first points)]
      (.moveTo ctx x y))
    (doseq [[x y] (rest points)]
      (.lineTo ctx x y))
    (.closePath ctx)
    (set! (.-fillStyle ctx) color)
    (.fill ctx)))

(defn- draw-polygon-outline!
  "Draw a polygon outline."
  [ctx points color line-width]
  (when (seq points)
    (.beginPath ctx)
    (let [[x y] (first points)]
      (.moveTo ctx x y))
    (doseq [[x y] (rest points)]
      (.lineTo ctx x y))
    (.closePath ctx)
    (set! (.-strokeStyle ctx) color)
    (set! (.-lineWidth ctx) line-width)
    (.stroke ctx)))

(defn- draw-circle!
  "Draw a filled circle."
  [ctx x y radius color]
  (.beginPath ctx)
  (.arc ctx x y radius 0 (* 2 Math/PI))
  (set! (.-fillStyle ctx) color)
  (.fill ctx))

(defn- draw-circle-outline!
  "Draw a circle outline."
  [ctx x y radius color line-width]
  (.beginPath ctx)
  (.arc ctx x y radius 0 (* 2 Math/PI))
  (set! (.-strokeStyle ctx) color)
  (set! (.-lineWidth ctx) line-width)
  (.stroke ctx))

;; Area rendering

(defn- clip-to-polygon!
  "Set up clipping region for a polygon. Call .restore to remove clip."
  [ctx points]
  (.save ctx)
  (.beginPath ctx)
  (let [[x y] (first points)]
    (.moveTo ctx x y))
  (doseq [[x y] (rest points)]
    (.lineTo ctx x y))
  (.closePath ctx)
  (.clip ctx))

(defn- draw-soil-texture!
  "Draw organic soil texture with dirt clumps and variations."
  [ctx points]
  (when (seq points)
    (let [xs (map first points)
          ys (map second points)
          min-x (apply min xs)
          max-x (apply max xs)
          min-y (apply min ys)
          max-y (apply max ys)]
      (clip-to-polygon! ctx points)
      ;; Dark soil base variations
      (set! (.-fillStyle ctx) "rgba(60, 40, 30, 0.15)")
      (doseq [x (range min-x max-x 25)
              y (range min-y max-y 25)]
        (let [offset-x (* 8 (Math/sin (* (+ x y) 0.1)))
              offset-y (* 8 (Math/cos (* x 0.15)))
              size (+ 8 (* 6 (Math/abs (Math/sin (* x y 0.002)))))]
          (.beginPath ctx)
          (.ellipse ctx (+ x offset-x) (+ y offset-y) size (* size 0.7) (* x 0.01) 0 (* 2 Math/PI))
          (.fill ctx)))
      ;; Small pebbles and organic matter
      (set! (.-fillStyle ctx) "rgba(100, 80, 60, 0.2)")
      (doseq [x (range min-x max-x 18)
              y (range min-y max-y 18)]
        (let [px (+ x (* 7 (Math/sin (* y 0.3))))
              py (+ y (* 7 (Math/cos (* x 0.25))))]
          (.beginPath ctx)
          (.arc ctx px py (+ 2 (* 2 (Math/abs (Math/sin (* px py 0.01))))) 0 (* 2 Math/PI))
          (.fill ctx)))
      ;; Light highlights for depth
      (set! (.-fillStyle ctx) "rgba(150, 120, 90, 0.1)")
      (doseq [x (range min-x max-x 40)
              y (range min-y max-y 40)]
        (.beginPath ctx)
        (.arc ctx (+ x 5) (+ y 3) 4 0 (* 2 Math/PI))
        (.fill ctx))
      (.restore ctx))))

(defn- draw-stone-path-texture!
  "Draw cobblestone/gravel pattern for paths."
  [ctx points]
  (when (seq points)
    (let [xs (map first points)
          ys (map second points)
          min-x (apply min xs)
          max-x (apply max xs)
          min-y (apply min ys)
          max-y (apply max ys)
          stone-size 22]
      (clip-to-polygon! ctx points)
      ;; Draw individual stones
      (doseq [row (range (int (/ (- min-y 10) stone-size)) (int (/ (+ max-y 10) stone-size)))
              col (range (int (/ (- min-x 10) stone-size)) (int (/ (+ max-x 10) stone-size)))]
        (let [;; Offset every other row for brick pattern
              offset-x (if (odd? row) (/ stone-size 2) 0)
              base-x (+ (* col stone-size) offset-x)
              base-y (* row stone-size)
              ;; Add randomness based on position
              rand-offset-x (* 3 (Math/sin (* base-x base-y 0.01)))
              rand-offset-y (* 3 (Math/cos (* base-x 0.02)))
              x (+ base-x rand-offset-x)
              y (+ base-y rand-offset-y)
              ;; Vary stone size
              w (+ (* stone-size 0.85) (* 3 (Math/sin (* x 0.1))))
              h (+ (* stone-size 0.75) (* 3 (Math/cos (* y 0.1))))
              ;; Vary stone color
              lightness (+ 0.9 (* 0.15 (Math/sin (* x y 0.005))))]
          ;; Stone body
          (set! (.-fillStyle ctx) (str "rgba(" (int (* 140 lightness)) "," (int (* 140 lightness)) "," (int (* 135 lightness)) ", 0.9)"))
          (.beginPath ctx)
          (.ellipse ctx x y (/ w 2) (/ h 2) (* (Math/sin x) 0.2) 0 (* 2 Math/PI))
          (.fill ctx)
          ;; Stone highlight
          (set! (.-fillStyle ctx) "rgba(200, 200, 195, 0.3)")
          (.beginPath ctx)
          (.ellipse ctx (- x 2) (- y 2) (/ w 4) (/ h 4) 0 0 (* 2 Math/PI))
          (.fill ctx)
          ;; Stone outline/shadow
          (set! (.-strokeStyle ctx) "rgba(80, 80, 75, 0.4)")
          (set! (.-lineWidth ctx) 1)
          (.beginPath ctx)
          (.ellipse ctx x y (/ w 2) (/ h 2) (* (Math/sin x) 0.2) 0 (* 2 Math/PI))
          (.stroke ctx)))
      ;; Add some gravel between stones
      (set! (.-fillStyle ctx) "rgba(120, 115, 100, 0.5)")
      (doseq [x (range min-x max-x 8)
              y (range min-y max-y 8)]
        (let [px (+ x (* 3 (Math/sin (* y 0.5))))
              py (+ y (* 3 (Math/cos (* x 0.4))))]
          (.beginPath ctx)
          (.arc ctx px py 1.5 0 (* 2 Math/PI))
          (.fill ctx)))
      (.restore ctx))))

(defn- draw-wood-texture!
  "Draw wood grain pattern for structures."
  [ctx points]
  (when (seq points)
    (let [xs (map first points)
          ys (map second points)
          min-x (apply min xs)
          max-x (apply max xs)
          min-y (apply min ys)
          max-y (apply max ys)]
      (clip-to-polygon! ctx points)
      ;; Wood grain lines
      (set! (.-strokeStyle ctx) "rgba(80, 55, 35, 0.25)")
      (set! (.-lineWidth ctx) 1.5)
      (doseq [y (range min-y max-y 6)]
        (let [wave-offset (* 2 (Math/sin (* y 0.05)))]
          (.beginPath ctx)
          (.moveTo ctx min-x (+ y wave-offset))
          (doseq [x (range min-x max-x 20)]
            (let [wave (* 1.5 (Math/sin (+ (* x 0.1) (* y 0.02))))]
              (.lineTo ctx x (+ y wave-offset wave))))
          (.stroke ctx)))
      ;; Knots
      (set! (.-fillStyle ctx) "rgba(60, 40, 25, 0.3)")
      (doseq [x (range (+ min-x 30) max-x 80)
              y (range (+ min-y 20) max-y 60)]
        (let [px (+ x (* 20 (Math/sin (* y 0.1))))
              py (+ y (* 15 (Math/cos (* x 0.08))))]
          (.beginPath ctx)
          (.ellipse ctx px py 4 6 0.5 0 (* 2 Math/PI))
          (.fill ctx)))
      ;; Subtle plank divisions
      (set! (.-strokeStyle ctx) "rgba(50, 35, 20, 0.2)")
      (set! (.-lineWidth ctx) 2)
      (doseq [x (range min-x max-x 45)]
        (.beginPath ctx)
        (.moveTo ctx x min-y)
        (.lineTo ctx x max-y)
        (.stroke ctx))
      (.restore ctx))))

(defn- render-area!
  "Render a single area with type-specific styling."
  [ctx area zoom]
  (let [points (:points area)
        area-type (or (:type area) :bed)
        color (or (:color area)
                  (case area-type
                    :bed "#8B6914"
                    :path "#9E9E9E"
                    :structure "#607D8B"
                    "#8B4513"))]
    ;; Base fill
    (draw-polygon! ctx points color)
    ;; Type-specific textures and decorations
    (case area-type
      :bed
      (draw-soil-texture! ctx points)

      :path
      (draw-stone-path-texture! ctx points)

      :structure
      (do
        (draw-wood-texture! ctx points)
        ;; Solid border for structures
        (draw-polygon-outline! ctx points "rgba(0,0,0,0.5)" (/ 3 zoom)))

      ;; Default: just the fill
      nil)
    ;; Common outline
    (draw-polygon-outline! ctx points "rgba(0,0,0,0.3)" (/ 1 zoom))))

(defn render-areas!
  "Render all areas (beds, paths, structures)."
  [ctx state]
  (let [zoom (get-in state [:viewport :zoom])
        areas (:areas state)]
    (doseq [area areas]
      (render-area! ctx area zoom))))

;; Plant rendering

(defn- get-plant-data
  "Look up plant species data from the library."
  [species-id]
  (first (filter #(= (:id %) species-id) library/sample-plants)))

(defn- plant-radius
  "Get the display radius for a plant based on its spacing-cm and life stage.
   Garden coordinates are in centimeters, so radius = spacing-cm / 2."
  [plant]
  (let [plant-data (get-plant-data (:species-id plant))
        stage (or (:stage plant) :mature)
        ;; Use spacing-cm from library (diameter of mature plant footprint)
        ;; Fall back to type-based defaults if not defined
        spacing-cm (or (:spacing-cm plant-data)
                       (case (:type plant-data)
                         :tree 400
                         :flower 30
                         :vegetable 40
                         :herb 25
                         30))
        base-radius (/ spacing-cm 2)]
    ;; Scale radius based on life stage
    (case stage
      :seed (* base-radius 0.15)
      :seedling (* base-radius 0.4)
      :mature base-radius
      base-radius)))

(defn- draw-leaf!
  "Draw a small leaf shape."
  [ctx x y size angle color]
  (.save ctx)
  (.translate ctx x y)
  (.rotate ctx angle)
  (.beginPath ctx)
  (.moveTo ctx 0 0)
  (.quadraticCurveTo ctx (* size 0.5) (* size -0.3) size 0)
  (.quadraticCurveTo ctx (* size 0.5) (* size 0.3) 0 0)
  (set! (.-fillStyle ctx) color)
  (.fill ctx)
  (.restore ctx))

(defn- draw-flower-petals!
  "Draw flower petals around a center point."
  [ctx x y radius color]
  ;; Draw stem first
  (let [stem-height (* radius 1.2)]
    (.beginPath ctx)
    (.moveTo ctx x y)
    (.lineTo ctx x (+ y stem-height))
    (set! (.-strokeStyle ctx) "#4CAF50")
    (set! (.-lineWidth ctx) (max 2 (* radius 0.15)))
    (.stroke ctx)
    ;; Stem leaves
    (draw-leaf! ctx (- x (* radius 0.3)) (+ y (* stem-height 0.5)) (* radius 0.5) -0.8 "#4CAF50")
    (draw-leaf! ctx (+ x (* radius 0.3)) (+ y (* stem-height 0.7)) (* radius 0.4) 0.8 "#4CAF50"))
  ;; Draw petals
  (let [petal-count 6
        petal-length (* radius 0.8)]
    (dotimes [i petal-count]
      (let [angle (* i (/ (* 2 Math/PI) petal-count))]
        (.save ctx)
        (.translate ctx x y)
        (.rotate ctx angle)
        (.beginPath ctx)
        (.ellipse ctx 0 (- petal-length) (* radius 0.4) petal-length 0 0 (* 2 Math/PI))
        (set! (.-fillStyle ctx) color)
        (.fill ctx)
        ;; Petal outline
        (set! (.-strokeStyle ctx) "rgba(0,0,0,0.1)")
        (set! (.-lineWidth ctx) 1)
        (.stroke ctx)
        (.restore ctx)))))

(defn- draw-tree-crown!
  "Draw a tree with trunk and crown."
  [ctx x y radius color]
  ;; Trunk
  (let [trunk-width (* radius 0.3)
        trunk-height (* radius 0.8)]
    (set! (.-fillStyle ctx) "#5D4037")
    (.fillRect ctx (- x (/ trunk-width 2)) y trunk-width trunk-height))
  ;; Crown - layered circles for a fuller look
  (draw-circle! ctx x (- y (* radius 0.1)) (* radius 1.1) color)
  (draw-circle! ctx (- x (* radius 0.3)) (+ y (* radius 0.1)) (* radius 0.7) color)
  (draw-circle! ctx (+ x (* radius 0.3)) (+ y (* radius 0.1)) (* radius 0.7) color)
  ;; Add highlight
  (draw-circle! ctx (- x (* radius 0.3)) (- y (* radius 0.3)) (* radius 0.25) "rgba(255,255,255,0.25)")
  ;; Add outline for definition
  (draw-circle-outline! ctx x (- y (* radius 0.1)) (* radius 1.1) "rgba(0,0,0,0.2)" 1))

(defn- draw-vegetable!
  "Draw a vegetable plant (bushy mound with prominent leaves)."
  [ctx x y radius color]
  ;; Soil/base mound
  (draw-circle! ctx x (+ y (* radius 0.2)) (* radius 0.8) "#8B7355")
  ;; Main plant body
  (draw-circle! ctx x y radius color)
  ;; Add multiple leaves sprouting up
  (let [leaf-size (* radius 0.8)
        leaf-color "#2E7D32"]
    (draw-leaf! ctx x (- y (* radius 0.7)) leaf-size 0 leaf-color)
    (draw-leaf! ctx (- x (* radius 0.4)) (- y (* radius 0.4)) leaf-size -0.6 leaf-color)
    (draw-leaf! ctx (+ x (* radius 0.4)) (- y (* radius 0.4)) leaf-size 0.6 leaf-color)
    (draw-leaf! ctx (- x (* radius 0.5)) (- y (* radius 0.1)) (* leaf-size 0.7) -0.9 leaf-color)
    (draw-leaf! ctx (+ x (* radius 0.5)) (- y (* radius 0.1)) (* leaf-size 0.7) 0.9 leaf-color))
  ;; Outline for definition
  (draw-circle-outline! ctx x y radius "rgba(0,0,0,0.15)" 1))

(defn- draw-herb!
  "Draw an herb (aromatic bushy plant with small leaves)."
  [ctx x y radius color]
  ;; Small pot/base
  (draw-circle! ctx x (+ y (* radius 0.3)) (* radius 0.5) "#A1887F")
  ;; Dense cluster of small leaves
  (let [leaf-size (* radius 0.7)]
    ;; Center leaves
    (draw-leaf! ctx x (- y (* radius 0.5)) leaf-size 0 color)
    (draw-leaf! ctx x (- y (* radius 0.2)) (* leaf-size 0.9) 0.1 color)
    ;; Side leaves
    (draw-leaf! ctx (- x (* radius 0.35)) (- y (* radius 0.3)) (* leaf-size 0.8) -0.5 color)
    (draw-leaf! ctx (+ x (* radius 0.35)) (- y (* radius 0.3)) (* leaf-size 0.8) 0.5 color)
    (draw-leaf! ctx (- x (* radius 0.45)) y (* leaf-size 0.6) -0.8 color)
    (draw-leaf! ctx (+ x (* radius 0.45)) y (* leaf-size 0.6) 0.8 color)
    ;; Bottom leaves
    (draw-leaf! ctx (- x (* radius 0.25)) (+ y (* radius 0.15)) (* leaf-size 0.5) -0.4 color)
    (draw-leaf! ctx (+ x (* radius 0.25)) (+ y (* radius 0.15)) (* leaf-size 0.5) 0.4 color)))

(defn- draw-seed!
  "Draw a seed (small oval with soil mound)."
  [ctx x y radius color]
  ;; Soil mound
  (draw-circle! ctx x (+ y (* radius 0.5)) (* radius 1.2) "#8B7355")
  ;; Seed shape - oval
  (.save ctx)
  (.translate ctx x y)
  (.beginPath ctx)
  (.ellipse ctx 0 0 (* radius 0.6) radius 0 0 (* 2 Math/PI))
  (set! (.-fillStyle ctx) color)
  (.fill ctx)
  ;; Seed highlight
  (.beginPath ctx)
  (.ellipse ctx (* radius -0.15) (* radius -0.3) (* radius 0.15) (* radius 0.25) -0.3 0 (* 2 Math/PI))
  (set! (.-fillStyle ctx) "rgba(255,255,255,0.4)")
  (.fill ctx)
  ;; Outline
  (.beginPath ctx)
  (.ellipse ctx 0 0 (* radius 0.6) radius 0 0 (* 2 Math/PI))
  (set! (.-strokeStyle ctx) "rgba(0,0,0,0.3)")
  (set! (.-lineWidth ctx) 1)
  (.stroke ctx)
  (.restore ctx))

(defn- draw-seedling!
  "Draw a seedling (small sprout with cotyledons)."
  [ctx x y radius color]
  ;; Soil mound
  (draw-circle! ctx x (+ y (* radius 0.4)) (* radius 0.8) "#8B7355")
  ;; Stem
  (.beginPath ctx)
  (.moveTo ctx x (+ y (* radius 0.3)))
  (.lineTo ctx x (- y (* radius 0.5)))
  (set! (.-strokeStyle ctx) "#4CAF50")
  (set! (.-lineWidth ctx) (max 2 (* radius 0.2)))
  (.stroke ctx)
  ;; Two cotyledon leaves (seed leaves)
  (draw-leaf! ctx (- x (* radius 0.3)) (- y (* radius 0.4)) (* radius 0.6) -0.7 color)
  (draw-leaf! ctx (+ x (* radius 0.3)) (- y (* radius 0.4)) (* radius 0.6) 0.7 color)
  ;; Small true leaf emerging
  (draw-leaf! ctx x (- y (* radius 0.6)) (* radius 0.4) 0 "#2E7D32"))

;; Multimethod for plant rendering - dispatches on [stage type]
(defn- plant-dispatch
  "Dispatch function for render-plant multimethod."
  [_ctx plant _zoom]
  (let [plant-data (get-plant-data (:species-id plant))
        stage (or (:stage plant) :mature)
        plant-type (or (:type plant-data) :vegetable)]
    (if (= stage :mature)
      [:mature plant-type]
      [stage nil])))

(defmulti render-plant!
  "Render a plant based on its stage and type."
  #'plant-dispatch)

;; Seed stage - all plant types look the same as seeds
(defmethod render-plant! [:seed nil]
  [ctx plant _zoom]
  (let [plant-data (get-plant-data (:species-id plant))
        [x y] (:position plant)
        radius (plant-radius plant)
        color (or (:color plant-data) (:color plant) "#228B22")]
    (draw-seed! ctx x y radius color)))

;; Seedling stage - small sprouts
(defmethod render-plant! [:seedling nil]
  [ctx plant _zoom]
  (let [plant-data (get-plant-data (:species-id plant))
        [x y] (:position plant)
        radius (plant-radius plant)
        color (or (:color plant-data) (:color plant) "#228B22")]
    (draw-seedling! ctx x y radius color)))

;; Mature tree
(defmethod render-plant! [:mature :tree]
  [ctx plant _zoom]
  (let [plant-data (get-plant-data (:species-id plant))
        [x y] (:position plant)
        radius (plant-radius plant)
        color (or (:color plant-data) (:color plant) "#228B22")]
    (draw-tree-crown! ctx x y radius color)))

;; Mature flower
(defmethod render-plant! [:mature :flower]
  [ctx plant _zoom]
  (let [plant-data (get-plant-data (:species-id plant))
        [x y] (:position plant)
        radius (plant-radius plant)
        color (or (:color plant-data) (:color plant) "#228B22")]
    (draw-flower-petals! ctx x y radius color)
    (draw-circle! ctx x y (* radius 0.35) "#FFD700")))

;; Mature herb
(defmethod render-plant! [:mature :herb]
  [ctx plant _zoom]
  (let [plant-data (get-plant-data (:species-id plant))
        [x y] (:position plant)
        radius (plant-radius plant)
        color (or (:color plant-data) (:color plant) "#228B22")]
    (draw-herb! ctx x y radius color)))

;; Mature vegetable
(defmethod render-plant! [:mature :vegetable]
  [ctx plant _zoom]
  (let [plant-data (get-plant-data (:species-id plant))
        [x y] (:position plant)
        radius (plant-radius plant)
        color (or (:color plant-data) (:color plant) "#228B22")]
    (draw-vegetable! ctx x y radius color)))

;; Default fallback
(defmethod render-plant! :default
  [ctx plant zoom]
  (let [plant-data (get-plant-data (:species-id plant))
        [x y] (:position plant)
        radius (plant-radius plant)
        color (or (:color plant-data) (:color plant) "#228B22")]
    (draw-circle! ctx x y radius color)
    (draw-circle-outline! ctx x y radius "rgba(0,0,0,0.3)" (/ 1 zoom))))

(defn- render-spacing-circle!
  "Render a spacing/footprint circle for a plant."
  [ctx plant zoom]
  (let [plant-data (get-plant-data (:species-id plant))
        [x y] (:position plant)
        spacing-cm (or (:spacing-cm plant-data) 30)
        ;; Convert cm to canvas units (1 unit = 1 cm for now)
        radius (/ spacing-cm 2)]
    ;; Draw dashed circle
    (.beginPath ctx)
    (.arc ctx x y radius 0 (* 2 Math/PI))
    (set! (.-strokeStyle ctx) "rgba(100, 100, 100, 0.4)")
    (set! (.-lineWidth ctx) (/ 1.5 zoom))
    (.setLineDash ctx #js [(/ 5 zoom) (/ 3 zoom)])
    (.stroke ctx)
    (.setLineDash ctx #js [])
    ;; Fill with very light color
    (set! (.-fillStyle ctx) "rgba(100, 100, 100, 0.05)")
    (.fill ctx)))

(defn render-plants!
  "Render all plants."
  [ctx state]
  (let [plants (:plants state)
        zoom (get-in state [:viewport :zoom])
        show-spacing? (get-in state [:ui :spacing-circles :visible?])]
    ;; Draw spacing circles first (behind plants)
    (when show-spacing?
      (doseq [plant plants]
        (render-spacing-circle! ctx plant zoom)))
    ;; Draw plants on top
    (doseq [plant plants]
      (render-plant! ctx plant zoom))))

;; Selection rendering

(defn render-selection!
  "Render selection highlights."
  [ctx state]
  (let [selection (:selection state)
        selected-ids (:ids selection)
        zoom (get-in state [:viewport :zoom])
        tool-state (get-in state [:tool :state])
        hover-vertex (:hover-vertex tool-state)
        hover-edge (:hover-edge tool-state)]
    (when (seq selected-ids)
      (case (:type selection)
        :area
        (doseq [id selected-ids]
          (when-let [area (state/find-area id)]
            ;; Highlight outline
            (draw-polygon-outline! ctx (:points area) "#0066ff" (/ 3 zoom))
            ;; Draw vertex handles
            (doseq [[idx [x y]] (map-indexed vector (:points area))]
              (let [is-hovered? (and hover-vertex
                                     (= id (:area-id hover-vertex))
                                     (= idx (:vertex-index hover-vertex)))]
                ;; Draw larger highlight if hovered
                (when is-hovered?
                  (draw-circle! ctx x y (/ 10 zoom) "rgba(0, 102, 255, 0.3)"))
                (draw-circle! ctx x y (/ 6 zoom) (if is-hovered? "#ff6600" "#0066ff"))
                (draw-circle! ctx x y (/ 4 zoom) "#fff")))
            ;; Draw edge insertion point if hovering
            (when (and hover-edge (= id (:area-id hover-edge)))
              (let [[px py] (:point hover-edge)]
                ;; Draw a "+" indicator at the insertion point
                (draw-circle! ctx px py (/ 8 zoom) "rgba(255, 102, 0, 0.5)")
                (draw-circle! ctx px py (/ 5 zoom) "#ff6600")
                ;; Small plus sign
                (set! (.-strokeStyle ctx) "#fff")
                (set! (.-lineWidth ctx) (/ 2 zoom))
                (.beginPath ctx)
                (.moveTo ctx (- px (/ 3 zoom)) py)
                (.lineTo ctx (+ px (/ 3 zoom)) py)
                (.moveTo ctx px (- py (/ 3 zoom)))
                (.lineTo ctx px (+ py (/ 3 zoom)))
                (.stroke ctx)))))

        :plant
        (doseq [id selected-ids]
          (when-let [plant (state/find-plant id)]
            (let [[x y] (:position plant)
                  radius (plant-radius plant)]
              (draw-circle-outline! ctx x y (+ radius 4) "#0066ff" (/ 3 zoom)))))

        nil))))

;; Tool overlay rendering

(defn render-tool-overlay!
  "Render tool-specific overlay (in screen coordinates)."
  [ctx state]
  (let [tool-state (get-in state [:tool :state])
        active-tool (get-in state [:tool :active])]
    (case active-tool
      :area
      (when-let [points (:points tool-state)]
        (when (seq points)
          (.save ctx)
          ;; Apply viewport transform for drawing
          (let [{:keys [offset zoom]} (:viewport state)
                [ox oy] offset]
            (.translate ctx ox oy)
            (.scale ctx zoom zoom)
            ;; Draw in-progress polygon
            (set! (.-globalAlpha ctx) 0.5)
            (draw-polygon! ctx points "#8B4513")
            (set! (.-globalAlpha ctx) 1.0)
            ;; Draw outline
            (draw-polygon-outline! ctx points "#333" (/ 2 zoom))
            ;; Draw vertices
            (doseq [[x y] points]
              (draw-circle! ctx x y (/ 6 zoom) "#0066ff")
              (draw-circle! ctx x y (/ 4 zoom) "#fff"))
            ;; Draw line to preview point
            (when-let [[px py] (:preview-point tool-state)]
              (let [[lx ly] (last points)]
                (.beginPath ctx)
                (.moveTo ctx lx ly)
                (.lineTo ctx px py)
                (set! (.-strokeStyle ctx) "#666")
                (set! (.-lineWidth ctx) (/ 1 zoom))
                (.setLineDash ctx #js [(/ 5 zoom) (/ 5 zoom)])
                (.stroke ctx)
                (.setLineDash ctx #js []))))
          (.restore ctx)))

      :plant
      (let [row-preview (:row-preview tool-state)
            single-preview (:preview-position tool-state)]
        (.save ctx)
        (let [{:keys [offset zoom]} (:viewport state)
              [ox oy] offset
              species-id (:species-id tool-state)]
          (.translate ctx ox oy)
          (.scale ctx zoom zoom)
          (set! (.-globalAlpha ctx) 0.5)
          (if (seq row-preview)
            ;; Row mode: show all preview plants
            (doseq [pos row-preview]
              (render-plant! ctx {:species-id species-id :position pos} zoom))
            ;; Single mode: show single preview
            (when single-preview
              (render-plant! ctx {:species-id species-id :position single-preview} zoom)))
          (set! (.-globalAlpha ctx) 1.0))
        (.restore ctx))

      ;; Default: no overlay
      nil)))

;; Tooltip rendering

(defn render-tooltip!
  "Render a tooltip for hovered plant (in screen coordinates)."
  [ctx state]
  (when-let [hover-plant-id (get-in state [:ui :hover :plant-id])]
    (when-let [plant (state/find-plant hover-plant-id)]
      (let [{:keys [offset zoom]} (:viewport state)
            [ox oy] offset
            [px py] (:position plant)
            ;; Convert to screen coordinates
            screen-x (+ (* px zoom) ox)
            screen-y (+ (* py zoom) oy)
            plant-data (get-plant-data (:species-id plant))
            name (or (:common-name plant-data) (:species-id plant))
            ;; Tooltip styling
            padding 6
            font-size 12]
        (.save ctx)
        (set! (.-font ctx) (str font-size "px -apple-system, sans-serif"))
        (let [text-width (.-width (.measureText ctx name))
              tooltip-width (+ text-width (* padding 2))
              tooltip-height (+ font-size (* padding 2))
              tooltip-x (- screen-x (/ tooltip-width 2))
              tooltip-y (- screen-y (plant-radius plant) 12 tooltip-height)]
          ;; Background
          (set! (.-fillStyle ctx) "rgba(0,0,0,0.8)")
          (.beginPath ctx)
          (let [r 4]  ; corner radius
            (.roundRect ctx tooltip-x tooltip-y tooltip-width tooltip-height r))
          (.fill ctx)
          ;; Text
          (set! (.-fillStyle ctx) "#fff")
          (set! (.-textAlign ctx) "center")
          (set! (.-textBaseline ctx) "middle")
          (.fillText ctx name screen-x (+ tooltip-y (/ tooltip-height 2))))
        (.restore ctx)))))

;; Public helper for hover detection
(defn get-plant-radius [plant]
  (plant-radius plant))
