(ns garden.canvas.render
  "Canvas rendering for areas, plants, selections and overlays.

   This namespace handles the 2D rendering of:
   - Areas (beds, paths, water features, structures)
   - Plants (with growth-habit-based symbolic rendering)
   - Selection highlights and handles
   - Tool overlays and tooltips"
  (:require [garden.state :as state]
            [garden.canvas.viewport :as viewport]
            [garden.canvas.plant-render :as plant-render]
            [garden.data.plants :as plants]
            [garden.data.area-types :as area-types]
            [garden.constants :as constants]))

;; =============================================================================
;; Background Rendering

(defn render-background!
  "Render a grass/soil background texture with aggressive LOD optimization."
  [ctx state]
  (let [{:keys [offset zoom]} (:viewport state)
        [ox oy] offset
        {:keys [width height]} (get-in state [:viewport :size])
        ;; Calculate visible area in canvas coordinates
        start-x (/ (- ox) zoom)
        start-y (/ (- oy) zoom)
        end-x (/ (- width ox) zoom)
        end-y (/ (- height oy) zoom)]
    ;; Base grass green color - always draw this
    (set! (.-fillStyle ctx) "#90A955")
    (.fillRect ctx start-x start-y (- end-x start-x) (- end-y start-y))
    ;; Only draw texture details when zoomed in enough (> 0.3 = fairly close)
    (when (> zoom 0.3)
      (let [base-spacing 25
            patch-spacing 50]
        ;; Add subtle grass texture with dots
        (set! (.-fillStyle ctx) "rgba(120, 150, 80, 0.3)")
        (doseq [x (range (- start-x base-spacing) end-x base-spacing)
                y (range (- start-y base-spacing) end-y base-spacing)]
          (let [offset-x (* 5 (Math/sin (+ x y)))
                offset-y (* 5 (Math/cos (* x 0.5)))]
            (.beginPath ctx)
            (.arc ctx (+ x offset-x) (+ y offset-y) 2 0 constants/TWO-PI)
            (.fill ctx)))
        ;; Add darker grass patches
        (set! (.-fillStyle ctx) "rgba(70, 100, 50, 0.15)")
        (doseq [x (range (- start-x patch-spacing) end-x patch-spacing)
                y (range (- start-y patch-spacing) end-y patch-spacing)]
          (let [size (+ 10 (* 10 (Math/abs (Math/sin (* x y 0.001)))))]
            (.beginPath ctx)
            (.arc ctx x y size 0 constants/TWO-PI)
            (.fill ctx)))))))

(defn- draw-polygon!
  "Draw a filled polygon, optionally with holes using evenodd fill rule."
  [ctx points color & [holes]]
  (when (seq points)
    (.beginPath ctx)
    ;; Draw outer ring
    (let [[x y] (first points)]
      (.moveTo ctx x y))
    (doseq [[x y] (rest points)]
      (.lineTo ctx x y))
    (.closePath ctx)
    ;; Draw holes as part of the same path (evenodd will cut them out)
    (doseq [hole holes]
      (when (seq hole)
        (let [[x y] (first hole)]
          (.moveTo ctx x y))
        (doseq [[x y] (rest hole)]
          (.lineTo ctx x y))
        (.closePath ctx)))
    ;; Fill using evenodd rule - holes will be cut out
    (set! (.-fillStyle ctx) color)
    (.fill ctx "evenodd")))

(defn- draw-polygon-outline!
  "Draw a polygon outline, optionally with holes."
  [ctx points color line-width & [holes]]
  (when (seq points)
    (.beginPath ctx)
    ;; Draw outer ring
    (let [[x y] (first points)]
      (.moveTo ctx x y))
    (doseq [[x y] (rest points)]
      (.lineTo ctx x y))
    (.closePath ctx)
    ;; Draw hole outlines
    (doseq [hole holes]
      (when (seq hole)
        (let [[x y] (first hole)]
          (.moveTo ctx x y))
        (doseq [[x y] (rest hole)]
          (.lineTo ctx x y))
        (.closePath ctx)))
    (set! (.-strokeStyle ctx) color)
    (set! (.-lineWidth ctx) line-width)
    (.stroke ctx)))

(defn- draw-circle!
  "Draw a filled circle."
  [ctx x y radius color]
  (.beginPath ctx)
  (.arc ctx x y radius 0 constants/TWO-PI)
  (set! (.-fillStyle ctx) color)
  (.fill ctx))

(defn- draw-circle-outline!
  "Draw a circle outline."
  [ctx x y radius color line-width]
  (.beginPath ctx)
  (.arc ctx x y radius 0 constants/TWO-PI)
  (set! (.-strokeStyle ctx) color)
  (set! (.-lineWidth ctx) line-width)
  (.stroke ctx))

;; =============================================================================
;; Area Rendering

(defn- clip-to-polygon!
  "Set up clipping region for a polygon with optional holes. Call .restore to remove clip."
  [ctx points & [holes]]
  (.save ctx)
  (.beginPath ctx)
  ;; Draw outer ring
  (let [[x y] (first points)]
    (.moveTo ctx x y))
  (doseq [[x y] (rest points)]
    (.lineTo ctx x y))
  (.closePath ctx)
  ;; Draw holes
  (doseq [hole holes]
    (when (seq hole)
      (let [[x y] (first hole)]
        (.moveTo ctx x y))
      (doseq [[x y] (rest hole)]
        (.lineTo ctx x y))
      (.closePath ctx)))
  ;; Use evenodd for clipping - holes are excluded from clip region
  (.clip ctx "evenodd"))

(defn- draw-soil-texture!
  "Draw organic soil texture with dirt clumps and variations."
  [ctx points & [holes]]
  (when (seq points)
    (let [xs (map first points)
          ys (map second points)
          min-x (apply min xs)
          max-x (apply max xs)
          min-y (apply min ys)
          max-y (apply max ys)]
      (clip-to-polygon! ctx points holes)
      ;; Dark soil base variations
      (set! (.-fillStyle ctx) "rgba(60, 40, 30, 0.15)")
      (doseq [x (range min-x max-x 25)
              y (range min-y max-y 25)]
        (let [offset-x (* 8 (Math/sin (* (+ x y) 0.1)))
              offset-y (* 8 (Math/cos (* x 0.15)))
              size (+ 8 (* 6 (Math/abs (Math/sin (* x y 0.002)))))]
          (.beginPath ctx)
          (.ellipse ctx (+ x offset-x) (+ y offset-y) size (* size 0.7) (* x 0.01) 0 constants/TWO-PI)
          (.fill ctx)))
      ;; Small pebbles and organic matter
      (set! (.-fillStyle ctx) "rgba(100, 80, 60, 0.2)")
      (doseq [x (range min-x max-x 18)
              y (range min-y max-y 18)]
        (let [px (+ x (* 7 (Math/sin (* y 0.3))))
              py (+ y (* 7 (Math/cos (* x 0.25))))]
          (.beginPath ctx)
          (.arc ctx px py (+ 2 (* 2 (Math/abs (Math/sin (* px py 0.01))))) 0 constants/TWO-PI)
          (.fill ctx)))
      ;; Light highlights for depth
      (set! (.-fillStyle ctx) "rgba(150, 120, 90, 0.1)")
      (doseq [x (range min-x max-x 40)
              y (range min-y max-y 40)]
        (.beginPath ctx)
        (.arc ctx (+ x 5) (+ y 3) 4 0 constants/TWO-PI)
        (.fill ctx))
      (.restore ctx))))

(defn- draw-stone-path-texture!
  "Draw cobblestone/gravel pattern for paths."
  [ctx points & [holes]]
  (when (seq points)
    (let [xs (map first points)
          ys (map second points)
          min-x (apply min xs)
          max-x (apply max xs)
          min-y (apply min ys)
          max-y (apply max ys)
          stone-size 22]
      (clip-to-polygon! ctx points holes)
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
          (.ellipse ctx x y (/ w 2) (/ h 2) (* (Math/sin x) 0.2) 0 constants/TWO-PI)
          (.fill ctx)
          ;; Stone highlight
          (set! (.-fillStyle ctx) "rgba(200, 200, 195, 0.3)")
          (.beginPath ctx)
          (.ellipse ctx (- x 2) (- y 2) (/ w 4) (/ h 4) 0 0 constants/TWO-PI)
          (.fill ctx)
          ;; Stone outline/shadow
          (set! (.-strokeStyle ctx) "rgba(80, 80, 75, 0.4)")
          (set! (.-lineWidth ctx) 1)
          (.beginPath ctx)
          (.ellipse ctx x y (/ w 2) (/ h 2) (* (Math/sin x) 0.2) 0 constants/TWO-PI)
          (.stroke ctx)))
      ;; Add some gravel between stones
      (set! (.-fillStyle ctx) "rgba(120, 115, 100, 0.5)")
      (doseq [x (range min-x max-x 8)
              y (range min-y max-y 8)]
        (let [px (+ x (* 3 (Math/sin (* y 0.5))))
              py (+ y (* 3 (Math/cos (* x 0.4))))]
          (.beginPath ctx)
          (.arc ctx px py 1.5 0 constants/TWO-PI)
          (.fill ctx)))
      (.restore ctx))))

(defn- draw-wood-texture!
  "Draw wood grain pattern for structures."
  [ctx points & [holes]]
  (when (seq points)
    (let [xs (map first points)
          ys (map second points)
          min-x (apply min xs)
          max-x (apply max xs)
          min-y (apply min ys)
          max-y (apply max ys)]
      (clip-to-polygon! ctx points holes)
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
          (.ellipse ctx px py 4 6 0.5 0 constants/TWO-PI)
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

(defn- path-is-thin?
  "Check if a path polygon is thin (linear) and needs stroke rendering."
  [points]
  (when (>= (count points) 2)
    (let [xs (map first points)
          ys (map second points)
          width (- (apply max xs) (apply min xs))
          height (- (apply max ys) (apply min ys))
          ;; Consider thin if one dimension is much smaller than the other
          min-dim (min width height)
          max-dim (max width height)]
      (or (< min-dim 50)  ; Less than 50cm width
          (and (pos? max-dim) (< (/ min-dim max-dim) 0.1))))))

(defn- draw-path-as-stroke!
  "Draw a thin path as a thick stroke along its centerline."
  [ctx points color width _zoom]
  (when (seq points)
    (.beginPath ctx)
    (let [[x y] (first points)]
      (.moveTo ctx x y))
    (doseq [[x y] (rest points)]
      (.lineTo ctx x y))
    (set! (.-strokeStyle ctx) color)
    (set! (.-lineWidth ctx) (max (/ 60 1) width))  ; Minimum 60cm wide path
    (set! (.-lineCap ctx) "round")
    (set! (.-lineJoin ctx) "round")
    (.stroke ctx)))

(defn- draw-water-texture!
  "Draw water ripple effects."
  [ctx points & [holes]]
  (when (seq points)
    (let [xs (map first points)
          ys (map second points)
          min-x (apply min xs)
          max-x (apply max xs)
          min-y (apply min ys)
          max-y (apply max ys)
          cx (/ (+ min-x max-x) 2)
          cy (/ (+ min-y max-y) 2)]
      (clip-to-polygon! ctx points holes)
      ;; Ripple circles
      (set! (.-strokeStyle ctx) "rgba(255,255,255,0.2)")
      (set! (.-lineWidth ctx) 2)
      (doseq [r (range 20 (max (- max-x min-x) (- max-y min-y)) 40)]
        (.beginPath ctx)
        (.arc ctx cx cy r 0 constants/TWO-PI)
        (.stroke ctx))
      ;; Light shimmer
      (set! (.-fillStyle ctx) "rgba(255,255,255,0.1)")
      (doseq [x (range min-x max-x 60)
              y (range min-y max-y 60)]
        (when (< (rand) 0.3)
          (.beginPath ctx)
          (.ellipse ctx x y 8 4 (* x 0.01) 0 constants/TWO-PI)
          (.fill ctx)))
      (.restore ctx))))

(defn- render-area!
  "Render a single area with type-specific styling and aggressive LOD."
  [ctx area zoom ref-visible?]
  (let [points (:points area)
        holes (:holes area)  ; Optional inner rings (islands become holes)
        area-type (or (:type area) :bed)
        color (or (:color area)
                  (area-types/get-color area-type)
                  "#8B4513")
        ;; Only show detailed textures when fairly zoomed in (> 0.25)
        show-textures? (> zoom 0.25)
        ;; Make areas semi-transparent when reference image is visible
        area-opacity (if ref-visible? 0.6 1.0)]

    ;; Set opacity for the whole area rendering
    (.save ctx)
    (set! (.-globalAlpha ctx) area-opacity)

    (case area-type
      :path
      ;; Special handling for paths - ensure they're visible
      (if (path-is-thin? points)
        ;; Thin path: render as thick stroke
        (draw-path-as-stroke! ctx points color 80 zoom)
        ;; Wide path polygon: render with holes
        (do
          (draw-polygon! ctx points color holes)
          (when show-textures?
            (draw-stone-path-texture! ctx points holes))
          (draw-polygon-outline! ctx points "rgba(0,0,0,0.3)" (/ 2 zoom) holes)))

      :water
      (do
        (draw-polygon! ctx points color holes)
        (when show-textures?
          (draw-water-texture! ctx points holes))
        (draw-polygon-outline! ctx points "rgba(0,50,100,0.5)" (/ 2 zoom) holes))

      :bed
      (do
        (draw-polygon! ctx points color holes)
        (when show-textures?
          (draw-soil-texture! ctx points holes))
        (draw-polygon-outline! ctx points "rgba(0,0,0,0.3)" (/ 1 zoom) holes))

      :structure
      (do
        (draw-polygon! ctx points color holes)
        (when show-textures?
          (draw-wood-texture! ctx points holes)
          (draw-polygon-outline! ctx points "rgba(0,0,0,0.5)" (/ 3 zoom) holes))
        (draw-polygon-outline! ctx points "rgba(0,0,0,0.3)" (/ 1 zoom) holes))

      ;; Default
      (do
        (draw-polygon! ctx points color holes)
        (draw-polygon-outline! ctx points "rgba(0,0,0,0.3)" (/ 1 zoom) holes)))

    ;; Restore opacity
    (.restore ctx)))

(defn- area-in-view?
  "Check if an area intersects the visible viewport bounds."
  [area bounds]
  (let [points (:points area)
        {:keys [min max]} bounds
        [min-x min-y] min
        [max-x max-y] max
        ;; Get area bounding box
        xs (map first points)
        ys (map second points)
        area-min-x (apply cljs.core/min xs)
        area-max-x (apply cljs.core/max xs)
        area-min-y (apply cljs.core/min ys)
        area-max-y (apply cljs.core/max ys)]
    ;; Check if bounding boxes overlap
    (and (<= area-min-x max-x)
         (>= area-max-x min-x)
         (<= area-min-y max-y)
         (>= area-max-y min-y))))

(defn- polygon-area
  "Calculate the area of a polygon using the shoelace formula."
  [points]
  (let [n (count points)]
    (if (< n 3)
      0
      (/ (Math/abs
          (reduce + (for [i (range n)]
                      (let [[x1 y1] (nth points i)
                            [x2 y2] (nth points (mod (inc i) n))]
                        (- (* x1 y2) (* x2 y1))))))
         2))))

(defn render-areas!
  "Render all areas (beds, paths, structures) with viewport culling.
   Smaller areas render on top of larger ones (for islands in ponds, etc).
   If bounds is provided, uses it instead of computing visible-bounds."
  ([ctx state] (render-areas! ctx state nil))
  ([ctx state bounds]
   (let [zoom (get-in state [:viewport :zoom])
         areas (:areas state)
         bounds (or bounds (viewport/visible-bounds))
         visible-areas (filter #(area-in-view? % bounds) areas)
         ;; Sort by size descending - larger areas first, smaller on top
         sorted-areas (sort-by #(- (polygon-area (:points %))) visible-areas)
         ;; Check if reference image is visible
         ref-visible? (and (get-in state [:ui :reference-image :visible?])
                           (get-in state [:ui :reference-image :image]))]
     (doseq [area sorted-areas]
       (render-area! ctx area zoom ref-visible?)))))

;; =============================================================================
;; Plant Rendering
;;
;; Plants are now rendered using the growth-habit-based symbolic system
;; from garden.canvas.plant-render. This provides intuitive visual
;; representations based on plant characteristics.

(defn- render-spacing-circle!
  "Render a spacing/footprint circle for a plant."
  [ctx plant zoom]
  (let [plant-data (plants/get-plant (:species-id plant))
        [x y] (:position plant)
        spacing-cm (or (:spacing-cm plant-data) 30)
        radius (/ spacing-cm 2)]
    (.beginPath ctx)
    (.arc ctx x y radius 0 constants/TWO-PI)
    (set! (.-strokeStyle ctx) "rgba(100, 100, 100, 0.4)")
    (set! (.-lineWidth ctx) (/ 1.5 zoom))
    (.setLineDash ctx #js [(/ 5 zoom) (/ 3 zoom)])
    (.stroke ctx)
    (.setLineDash ctx #js [])
    (set! (.-fillStyle ctx) "rgba(100, 100, 100, 0.05)")
    (.fill ctx)))

(defn- plant-in-view?
  "Check if a plant is within the visible viewport bounds."
  [plant bounds]
  (let [[px py] (:position plant)
        {:keys [min max]} bounds
        [min-x min-y] min
        [max-x max-y] max
        margin 300]
    (and (>= px (- min-x margin))
         (<= px (+ max-x margin))
         (>= py (- min-y margin))
         (<= py (+ max-y margin)))))

(defn render-plants!
  "Render all plants with viewport culling and LOD.
   Uses growth-habit-based symbolic rendering for intuitive plant visualization.
   If bounds is provided, uses it instead of computing visible-bounds."
  ([ctx state] (render-plants! ctx state nil))
  ([ctx state bounds]
   (let [all-plants (:plants state)
         zoom (get-in state [:viewport :zoom])
         show-spacing? (get-in state [:ui :spacing-circles :visible?])
         bounds (or bounds (viewport/visible-bounds))
         visible-plants (filter #(plant-in-view? % bounds) all-plants)
         use-simple-render? (< zoom 0.2)]
    ;; Draw spacing circles first (behind plants)
     (when (and show-spacing? (>= zoom 0.15))
       (doseq [plant visible-plants]
         (render-spacing-circle! ctx plant zoom)))
    ;; Draw plants using the new symbolic rendering system
     (if use-simple-render?
       (doseq [plant visible-plants]
         (plant-render/render-plant-simple! ctx plant))
       (doseq [plant visible-plants]
         (plant-render/render-plant! ctx plant zoom))))))

;; =============================================================================
;; Selection Rendering

(defn render-selection!
  "Render selection highlights."
  [ctx state]
  (let [selection (:selection state)
        selected-ids (:ids selection)
        zoom (get-in state [:viewport :zoom])
        tool-state (get-in state [:tool :state])
        hover-vertex (:hover-vertex tool-state)
        selected-vertex (:selected-vertex tool-state)
        hover-edge (:hover-edge tool-state)]
    (when (seq selected-ids)
      (case (:type selection)
        :area
        (doseq [id selected-ids]
          (when-let [area (state/find-area id)]
            ;; Highlight outer outline only (holes not shown in selection)
            (draw-polygon-outline! ctx (:points area) "#0066ff" (/ 3 zoom))
            ;; Draw vertex handles for outer ring
            (doseq [[idx [x y]] (map-indexed vector (:points area))]
              (let [is-hovered? (and hover-vertex
                                     (= id (:area-id hover-vertex))
                                     (= idx (:vertex-index hover-vertex)))
                    is-selected? (and selected-vertex
                                      (= id (:area-id selected-vertex))
                                      (= idx (:vertex-index selected-vertex)))
                    is-active? (or is-hovered? is-selected?)]
                  ;; Draw larger highlight if hovered or selected
                (when is-active?
                  (draw-circle! ctx x y (/ 12 zoom) (if is-selected?
                                                      "rgba(255, 0, 0, 0.3)"
                                                      "rgba(0, 102, 255, 0.3)")))
                (draw-circle! ctx x y (/ 6 zoom) (cond
                                                   is-selected? "#ff0000"
                                                   is-hovered? "#ff6600"
                                                   :else "#0066ff"))
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
                  radius (plant-render/get-plant-radius plant)]
              (draw-circle-outline! ctx x y (+ radius 4) "#0066ff" (/ 3 zoom)))))

        nil))))

;; =============================================================================
;; Tool Overlay Rendering

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
              (plant-render/render-plant! ctx {:species-id species-id :position pos} zoom))
            ;; Single mode: show single preview
            (when single-preview
              (plant-render/render-plant! ctx {:species-id species-id :position single-preview} zoom)))
          (set! (.-globalAlpha ctx) 1.0))
        (.restore ctx))

      :scatter
      (let [{:keys [start preview count]} tool-state]
        (when (and start preview)
          (.save ctx)
          (let [{:keys [offset zoom]} (:viewport state)
                [ox oy] offset
                [x1 y1] start
                [x2 y2] preview
                min-x (min x1 x2)
                max-x (max x1 x2)
                min-y (min y1 y2)
                max-y (max y1 y2)]
            (.translate ctx ox oy)
            (.scale ctx zoom zoom)
            ;; Draw scatter area rectangle
            (set! (.-fillStyle ctx) "rgba(0, 200, 0, 0.1)")
            (.fillRect ctx min-x min-y (- max-x min-x) (- max-y min-y))
            (set! (.-strokeStyle ctx) "rgba(0, 150, 0, 0.6)")
            (set! (.-lineWidth ctx) (/ 2 zoom))
            (.setLineDash ctx #js [(/ 8 zoom) (/ 4 zoom)])
            (.strokeRect ctx min-x min-y (- max-x min-x) (- max-y min-y))
            (.setLineDash ctx #js [])
            ;; Show count label
            (let [font-size (/ 16 zoom)
                  cx (/ (+ min-x max-x) 2)
                  cy (/ (+ min-y max-y) 2)]
              (set! (.-font ctx) (str "bold " font-size "px sans-serif"))
              (set! (.-fillStyle ctx) "rgba(0, 100, 0, 0.8)")
              (set! (.-textAlign ctx) "center")
              (set! (.-textBaseline ctx) "middle")
              (.fillText ctx (str "Scatter " (or count 20) " plants") cx cy)))
          (.restore ctx)))

      :trace
      (let [{:keys [points drawing? area-type]} tool-state]
        (when (and drawing? (seq points))
          (.save ctx)
          (let [{:keys [offset zoom]} (:viewport state)
                [ox oy] offset
                color (or (area-types/get-color area-type) "#888888")]
            (.translate ctx ox oy)
            (.scale ctx zoom zoom)
            ;; Draw the traced path
            (.beginPath ctx)
            (let [[fx fy] (first points)]
              (.moveTo ctx fx fy))
            (doseq [[x y] (rest points)]
              (.lineTo ctx x y))
            ;; Close the path back to start
            (.closePath ctx)
            ;; Fill with transparency
            (set! (.-fillStyle ctx) color)
            (set! (.-globalAlpha ctx) 0.3)
            (.fill ctx)
            ;; Stroke the outline
            (set! (.-globalAlpha ctx) 0.8)
            (set! (.-strokeStyle ctx) color)
            (set! (.-lineWidth ctx) (/ 3 zoom))
            (.stroke ctx)
            (set! (.-globalAlpha ctx) 1.0))
          (.restore ctx)))

      ;; Default: no overlay
      nil)))

;; =============================================================================
;; Tooltip Rendering

(defn render-tooltip!
  "Render a tooltip for hovered plant (in screen coordinates)."
  [ctx state]
  (when-let [hover-plant-id (get-in state [:ui :hover :plant-id])]
    (when-let [plant (state/find-plant hover-plant-id)]
      (let [{:keys [offset zoom]} (:viewport state)
            [ox oy] offset
            [px py] (:position plant)
            screen-x (+ (* px zoom) ox)
            screen-y (+ (* py zoom) oy)
            plant-data (plants/get-plant (:species-id plant))
            name (or (:common-name plant-data) (:species-id plant))
            padding 6
            font-size 12]
        (.save ctx)
        (set! (.-font ctx) (str font-size "px -apple-system, sans-serif"))
        (let [text-width (.-width (.measureText ctx name))
              tooltip-width (+ text-width (* padding 2))
              tooltip-height (+ font-size (* padding 2))
              tooltip-x (- screen-x (/ tooltip-width 2))
              tooltip-y (- screen-y (plant-render/get-plant-radius plant) 12 tooltip-height)]
          (set! (.-fillStyle ctx) "rgba(0,0,0,0.8)")
          (.beginPath ctx)
          (let [r 4]
            (.roundRect ctx tooltip-x tooltip-y tooltip-width tooltip-height r))
          (.fill ctx)
          (set! (.-fillStyle ctx) "#fff")
          (set! (.-textAlign ctx) "center")
          (set! (.-textBaseline ctx) "middle")
          (.fillText ctx name screen-x (+ tooltip-y (/ tooltip-height 2))))
        (.restore ctx)))))

;; =============================================================================
;; Public API

(defn get-plant-radius
  "Get the display radius for a plant (for hover detection)."
  [plant]
  (plant-render/get-plant-radius plant))
