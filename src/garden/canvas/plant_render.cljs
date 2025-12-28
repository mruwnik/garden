(ns garden.canvas.plant-render
  "Symbolic plant rendering for 2D canvas.

   Plants are rendered based on their growth habit, creating intuitive
   visual representations that work both as top-down views and as
   recognizable plant forms.

   Growth habits determine the overall shape:
   - columnar:   Tall narrow ellipse with vertical texture
   - spreading:  Wide irregular crown shape
   - vase:       Narrow bottom, wide top
   - weeping:    Drooping curved lines from center
   - mounding:   Soft dome shape
   - upright:    Vertical oval with slight spread
   - prostrate:  Flat spreading patches
   - rosette:    Circular leaf arrangement
   - clumping:   Dense cluster of vertical elements
   - vining:     Curving tendrils from center
   - spiky:      Radiating pointed leaves
   - bushy:      Dense irregular mass
   - fountain:   Arching lines from center
   - fan:        Fan-shaped spread"
  (:require [garden.data.plants :as plants]
            [garden.constants :as constants]))

;; =============================================================================
;; Color Utilities

(defn- hex->rgb
  "Convert hex color to [r g b]."
  [hex]
  (let [hex (if (= (first hex) \#) (subs hex 1) hex)]
    [(js/parseInt (subs hex 0 2) 16)
     (js/parseInt (subs hex 2 4) 16)
     (js/parseInt (subs hex 4 6) 16)]))

(defn- rgb->hex
  "Convert [r g b] to hex color."
  [[r g b]]
  (str "#"
       (.toString (bit-or (bit-shift-left 1 24)
                          (bit-shift-left (int r) 16)
                          (bit-shift-left (int g) 8)
                          (int b))
                  16)))

(defn- lighten
  "Lighten a hex color by amount (0-1)."
  [hex amount]
  (let [[r g b] (hex->rgb hex)
        lighten-ch (fn [c] (min 255 (+ c (* (- 255 c) amount))))]
    (rgb->hex [(lighten-ch r) (lighten-ch g) (lighten-ch b)])))

(defn- darken
  "Darken a hex color by amount (0-1)."
  [hex amount]
  (let [[r g b] (hex->rgb hex)
        darken-ch (fn [c] (* c (- 1 amount)))]
    (rgb->hex [(darken-ch r) (darken-ch g) (darken-ch b)])))

(defn- with-alpha
  "Add alpha to hex color, return rgba string."
  [hex alpha]
  (let [[r g b] (hex->rgb hex)]
    (str "rgba(" r "," g "," b "," alpha ")")))

;; =============================================================================
;; Drawing Primitives

(defn- draw-ellipse!
  "Draw a filled ellipse."
  [ctx x y rx ry color & [rotation]]
  (.save ctx)
  (.translate ctx x y)
  (when rotation (.rotate ctx rotation))
  (.beginPath ctx)
  (.ellipse ctx 0 0 rx ry 0 0 constants/TWO-PI)
  (set! (.-fillStyle ctx) color)
  (.fill ctx)
  (.restore ctx))

(defn- draw-ellipse-outline!
  "Draw an ellipse outline."
  [ctx x y rx ry color line-width & [rotation]]
  (.save ctx)
  (.translate ctx x y)
  (when rotation (.rotate ctx rotation))
  (.beginPath ctx)
  (.ellipse ctx 0 0 rx ry 0 0 constants/TWO-PI)
  (set! (.-strokeStyle ctx) color)
  (set! (.-lineWidth ctx) line-width)
  (.stroke ctx)
  (.restore ctx))

(defn- draw-arc!
  "Draw a curved line (arc segment)."
  [ctx x y radius start-angle end-angle color line-width]
  (.beginPath ctx)
  (.arc ctx x y radius start-angle end-angle)
  (set! (.-strokeStyle ctx) color)
  (set! (.-lineWidth ctx) line-width)
  (.stroke ctx))

(defn- draw-line!
  "Draw a line."
  [ctx x1 y1 x2 y2 color line-width]
  (.beginPath ctx)
  (.moveTo ctx x1 y1)
  (.lineTo ctx x2 y2)
  (set! (.-strokeStyle ctx) color)
  (set! (.-lineWidth ctx) line-width)
  (.stroke ctx))

(defn- draw-bezier!
  "Draw a quadratic bezier curve."
  [ctx x1 y1 cx cy x2 y2 color line-width]
  (.beginPath ctx)
  (.moveTo ctx x1 y1)
  (.quadraticCurveTo ctx cx cy x2 y2)
  (set! (.-strokeStyle ctx) color)
  (set! (.-lineWidth ctx) line-width)
  (.stroke ctx))

(defn- draw-leaf-shape!
  "Draw a leaf-like shape."
  [ctx x y length width angle color]
  (.save ctx)
  (.translate ctx x y)
  (.rotate ctx angle)
  (.beginPath ctx)
  (.moveTo ctx 0 0)
  (.quadraticCurveTo ctx (* width 0.5) (* length -0.3) 0 (- length))
  (.quadraticCurveTo ctx (* width -0.5) (* length -0.3) 0 0)
  (set! (.-fillStyle ctx) color)
  (.fill ctx)
  (.restore ctx))

(defn- draw-pointed-leaf!
  "Draw a pointed spiky leaf."
  [ctx x y length width angle color]
  (.save ctx)
  (.translate ctx x y)
  (.rotate ctx angle)
  (.beginPath ctx)
  (.moveTo ctx 0 0)
  (.lineTo ctx (/ width 2) (* length -0.2))
  (.lineTo ctx 0 (- length))
  (.lineTo ctx (/ width -2) (* length -0.2))
  (.closePath ctx)
  (set! (.-fillStyle ctx) color)
  (.fill ctx)
  (.restore ctx))

;; =============================================================================
;; Growth Habit Renderers

(defn- render-columnar!
  "Render a columnar plant (narrow vertical form)."
  [ctx x y radius color bloom-color]
  (let [height (* radius 1.8)
        width (* radius 0.4)]
    ;; Main vertical form
    (draw-ellipse! ctx x y width height color)
    ;; Highlight
    (draw-ellipse! ctx (- x (* width 0.3)) (- y (* height 0.2))
                   (* width 0.3) (* height 0.4) (lighten color 0.3))
    ;; Texture lines
    (let [dark (darken color 0.2)]
      (dotimes [i 3]
        (let [ly (+ (- y (* height 0.5)) (* i (* height 0.3)))]
          (draw-ellipse! ctx x ly (* width 0.9) (* height 0.08) dark))))
    ;; Bloom if present
    (when bloom-color
      (draw-ellipse! ctx x (- y (* height 0.8)) (* width 0.5) (* width 0.5) bloom-color))))

(defn- render-spreading!
  "Render a spreading plant (wide horizontal crown)."
  [ctx x y radius color bloom-color]
  (let [r radius
        layers 4]
    ;; Multiple overlapping ellipses for organic shape
    (dotimes [i layers]
      (let [angle (* i (/ constants/TWO-PI layers))
            offset-x (* r 0.25 (Math/cos angle))
            offset-y (* r 0.25 (Math/sin angle))
            layer-r (* r (- 0.9 (* i 0.1)))
            layer-color (if (even? i) color (darken color 0.1))]
        (draw-ellipse! ctx (+ x offset-x) (+ y offset-y) layer-r (* layer-r 0.85) layer-color)))
    ;; Center highlight
    (draw-ellipse! ctx (- x (* r 0.2)) (- y (* r 0.2)) (* r 0.25) (* r 0.2) (lighten color 0.25))
    ;; Bloom clusters if present
    (when bloom-color
      (dotimes [i 5]
        (let [angle (* i (/ constants/TWO-PI 5))
              bx (+ x (* r 0.5 (Math/cos angle)))
              by (+ y (* r 0.5 (Math/sin angle)))]
          (draw-ellipse! ctx bx by (* r 0.15) (* r 0.15) bloom-color))))))

(defn- render-vase!
  "Render a vase-shaped plant (narrow bottom, spreading top)."
  [ctx x y radius color bloom-color]
  (let [r radius]
    ;; Base (narrower)
    (draw-ellipse! ctx x (+ y (* r 0.3)) (* r 0.4) (* r 0.3) (darken color 0.15))
    ;; Top crown (wider)
    (draw-ellipse! ctx x (- y (* r 0.2)) r (* r 0.7) color)
    ;; Side lobes
    (draw-ellipse! ctx (- x (* r 0.5)) y (* r 0.4) (* r 0.5) color 0.3)
    (draw-ellipse! ctx (+ x (* r 0.5)) y (* r 0.4) (* r 0.5) color -0.3)
    ;; Highlight
    (draw-ellipse! ctx (- x (* r 0.2)) (- y (* r 0.3)) (* r 0.2) (* r 0.15) (lighten color 0.25))
    ;; Blooms
    (when bloom-color
      (dotimes [i 7]
        (let [angle (+ (* i (/ Math/PI 6)) (/ Math/PI 6))
              bx (+ x (* r 0.6 (Math/cos angle)))
              by (+ y (* r -0.4 (Math/sin angle)))]
          (draw-ellipse! ctx bx by (* r 0.12) (* r 0.12) bloom-color))))))

(defn- render-weeping!
  "Render a weeping plant (drooping branches)."
  [ctx x y radius color bloom-color]
  (let [r radius
        branches 12]
    ;; Central trunk area
    (draw-ellipse! ctx x y (* r 0.25) (* r 0.25) (darken color 0.3))
    ;; Drooping branches
    (dotimes [i branches]
      (let [angle (* i (/ constants/TWO-PI branches))
            start-x (+ x (* r 0.2 (Math/cos angle)))
            start-y (+ y (* r 0.2 (Math/sin angle)))
            end-x (+ x (* r 0.95 (Math/cos angle)))
            end-y (+ y (* r 0.95 (Math/sin angle)))
            ctrl-x (+ x (* r 0.6 (Math/cos angle)))
            ctrl-y (+ y (* r 0.6 (Math/sin angle)) (* r 0.3))
            branch-color (if (even? i) color (lighten color 0.1))]
        (draw-bezier! ctx start-x start-y ctrl-x ctrl-y end-x end-y branch-color (* r 0.08))))
    ;; Blooms scattered on branches
    (when bloom-color
      (dotimes [i 8]
        (let [angle (* i (/ constants/TWO-PI 8))
              bx (+ x (* r 0.7 (Math/cos angle)))
              by (+ y (* r 0.7 (Math/sin angle)) (* r 0.15))]
          (draw-ellipse! ctx bx by (* r 0.1) (* r 0.1) bloom-color))))))

(defn- render-mounding!
  "Render a mounding plant (soft dome shape)."
  [ctx x y radius color bloom-color]
  (let [r radius]
    ;; Main dome
    (draw-ellipse! ctx x y r (* r 0.75) color)
    ;; Inner shading for depth
    (draw-ellipse! ctx x (+ y (* r 0.1)) (* r 0.85) (* r 0.6) (darken color 0.08))
    ;; Highlight
    (draw-ellipse! ctx (- x (* r 0.25)) (- y (* r 0.2)) (* r 0.3) (* r 0.2) (lighten color 0.2))
    ;; Small texture bumps
    (dotimes [i 5]
      (let [angle (* i (/ constants/TWO-PI 5))
            bx (+ x (* r 0.5 (Math/cos angle)))
            by (+ y (* r 0.4 (Math/sin angle)))]
        (draw-ellipse! ctx bx by (* r 0.2) (* r 0.15) (darken color 0.05))))
    ;; Blooms
    (when bloom-color
      (dotimes [i 6]
        (let [angle (* i (/ constants/TWO-PI 6))
              bx (+ x (* r 0.45 (Math/cos angle)))
              by (+ y (* r 0.35 (Math/sin angle)))]
          (draw-ellipse! ctx bx by (* r 0.15) (* r 0.15) bloom-color))))))

(defn- render-upright!
  "Render an upright plant (vertical oval)."
  [ctx x y radius color bloom-color]
  (let [r radius
        height (* r 1.3)
        width (* r 0.8)]
    ;; Main form
    (draw-ellipse! ctx x y width height color)
    ;; Inner layers for depth
    (draw-ellipse! ctx x (+ y (* height 0.1)) (* width 0.8) (* height 0.7) (darken color 0.08))
    ;; Highlight
    (draw-ellipse! ctx (- x (* width 0.2)) (- y (* height 0.2)) (* width 0.25) (* height 0.2) (lighten color 0.2))
    ;; Blooms at top
    (when bloom-color
      (dotimes [i 5]
        (let [angle (+ (* i (/ Math/PI 4)) (/ Math/PI 8))
              bx (+ x (* width 0.4 (Math/cos angle)))
              by (- y (* height 0.5) (* height -0.2 (Math/sin angle)))]
          (draw-ellipse! ctx bx by (* r 0.12) (* r 0.12) bloom-color))))))

(defn- render-prostrate!
  "Render a prostrate/groundcover plant (flat spreading)."
  [ctx x y radius color bloom-color]
  (let [r radius
        patches 7]
    ;; Multiple flat overlapping patches
    (dotimes [i patches]
      (let [angle (* i (/ constants/TWO-PI patches))
            dist (* r (+ 0.3 (* 0.4 (Math/abs (Math/sin (* i 2.5))))))
            px (+ x (* dist (Math/cos angle)))
            py (+ y (* dist (Math/sin angle)))
            patch-r (* r (+ 0.25 (* 0.15 (Math/abs (Math/cos (* i 3))))))
            patch-color (if (even? i) color (lighten color 0.1))]
        (draw-ellipse! ctx px py patch-r (* patch-r 0.7) patch-color (* i 0.5))))
    ;; Center patch
    (draw-ellipse! ctx x y (* r 0.4) (* r 0.35) color)
    ;; Tiny blooms
    (when bloom-color
      (dotimes [i 8]
        (let [angle (* i (/ constants/TWO-PI 8))
              bx (+ x (* r 0.5 (Math/cos angle)))
              by (+ y (* r 0.4 (Math/sin angle)))]
          (draw-ellipse! ctx bx by (* r 0.08) (* r 0.08) bloom-color))))))

(defn- render-rosette!
  "Render a rosette plant (circular leaf arrangement)."
  [ctx x y radius color bloom-color]
  (let [r radius
        leaves 8]
    ;; Leaves radiating from center
    (dotimes [i leaves]
      (let [angle (* i (/ constants/TWO-PI leaves))
            leaf-length (* r 0.9)
            leaf-width (* r 0.35)
            leaf-color (if (even? i) color (lighten color 0.08))]
        (draw-leaf-shape! ctx x y leaf-length leaf-width angle leaf-color)))
    ;; Center
    (draw-ellipse! ctx x y (* r 0.2) (* r 0.2) (lighten color 0.15))
    ;; Bloom in center
    (when bloom-color
      (draw-ellipse! ctx x y (* r 0.25) (* r 0.25) bloom-color))))

(defn- render-clumping!
  "Render a clumping plant (dense vertical clusters like grasses)."
  [ctx x y radius color bloom-color]
  (let [r radius
        blades 16]
    ;; Multiple vertical blades
    (dotimes [i blades]
      (let [angle (* i (/ constants/TWO-PI blades))
            base-x (+ x (* r 0.15 (Math/cos angle)))
            base-y (+ y (* r 0.15 (Math/sin angle)))
            tip-x (+ x (* r (+ 0.6 (* 0.3 (Math/abs (Math/sin (* i 2))))) (Math/cos angle)))
            tip-y (+ y (* r (+ 0.6 (* 0.3 (Math/abs (Math/sin (* i 2))))) (Math/sin angle)))
            blade-color (if (even? i) color (lighten color 0.1))]
        (draw-line! ctx base-x base-y tip-x tip-y blade-color (* r 0.06))))
    ;; Center clump
    (draw-ellipse! ctx x y (* r 0.2) (* r 0.2) (darken color 0.15))
    ;; Bloom tips
    (when bloom-color
      (dotimes [i 6]
        (let [angle (* i (/ constants/TWO-PI 6))
              bx (+ x (* r 0.75 (Math/cos angle)))
              by (+ y (* r 0.75 (Math/sin angle)))]
          (draw-ellipse! ctx bx by (* r 0.08) (* r 0.12) bloom-color))))))

(defn- render-vining!
  "Render a vining plant (curving tendrils)."
  [ctx x y radius color bloom-color]
  (let [r radius
        vines 6]
    ;; Central base
    (draw-ellipse! ctx x y (* r 0.25) (* r 0.25) (darken color 0.15))
    ;; Curving vines
    (dotimes [i vines]
      (let [angle (* i (/ constants/TWO-PI vines))
            start-x x
            start-y y
            end-x (+ x (* r 0.9 (Math/cos angle)))
            end-y (+ y (* r 0.9 (Math/sin angle)))
            ;; Control point curves the vine
            curve-dir (if (even? i) 1 -1)
            ctrl-x (+ x (* r 0.5 (Math/cos (+ angle (* curve-dir 0.5)))))
            ctrl-y (+ y (* r 0.5 (Math/sin (+ angle (* curve-dir 0.5)))))
            vine-color (if (even? i) color (lighten color 0.08))]
        (draw-bezier! ctx start-x start-y ctrl-x ctrl-y end-x end-y vine-color (* r 0.05))
        ;; Small leaves along vine
        (dotimes [j 3]
          (let [t (/ (inc j) 4)
                lx (+ (* (- 1 t) (- 1 t) start-x)
                      (* 2 (- 1 t) t ctrl-x)
                      (* t t end-x))
                ly (+ (* (- 1 t) (- 1 t) start-y)
                      (* 2 (- 1 t) t ctrl-y)
                      (* t t end-y))]
            (draw-ellipse! ctx lx ly (* r 0.08) (* r 0.06) vine-color)))))
    ;; Blooms
    (when bloom-color
      (dotimes [i 4]
        (let [angle (* i (/ constants/TWO-PI 4))
              bx (+ x (* r 0.6 (Math/cos angle)))
              by (+ y (* r 0.6 (Math/sin angle)))]
          (draw-ellipse! ctx bx by (* r 0.15) (* r 0.15) bloom-color))))))

(defn- render-spiky!
  "Render a spiky plant (radiating pointed leaves like agave/yucca)."
  [ctx x y radius color bloom-color]
  (let [r radius
        spikes 12]
    ;; Pointed leaves radiating from center
    (dotimes [i spikes]
      (let [angle (* i (/ constants/TWO-PI spikes))
            length (* r (+ 0.7 (* 0.25 (Math/abs (Math/sin (* i 2.3))))))
            width (* r 0.15)
            spike-color (if (even? i) color (lighten color 0.1))]
        (draw-pointed-leaf! ctx x y length width angle spike-color)))
    ;; Center rosette
    (draw-ellipse! ctx x y (* r 0.15) (* r 0.15) (darken color 0.2))
    ;; Bloom stalk
    (when bloom-color
      (draw-ellipse! ctx x (- y (* r 0.3)) (* r 0.1) (* r 0.25) bloom-color))))

(defn- render-bushy!
  "Render a bushy plant (dense irregular mass)."
  [ctx x y radius color bloom-color]
  (let [r radius
        blobs 8]
    ;; Multiple overlapping irregular shapes
    (dotimes [i blobs]
      (let [angle (* i (/ constants/TWO-PI blobs) 1.1)
            dist (* r (+ 0.3 (* 0.35 (Math/abs (Math/sin (* i 3.7))))))
            bx (+ x (* dist (Math/cos angle)))
            by (+ y (* dist (Math/sin angle)))
            blob-rx (* r (+ 0.25 (* 0.15 (Math/abs (Math/cos (* i 2.3))))))
            blob-ry (* blob-rx (+ 0.7 (* 0.2 (Math/sin (* i 4)))))
            blob-color (if (even? i) color (darken color 0.08))]
        (draw-ellipse! ctx bx by blob-rx blob-ry blob-color (* i 0.4))))
    ;; Central mass
    (draw-ellipse! ctx x y (* r 0.5) (* r 0.45) color)
    ;; Highlight
    (draw-ellipse! ctx (- x (* r 0.15)) (- y (* r 0.15)) (* r 0.15) (* r 0.12) (lighten color 0.2))
    ;; Blooms scattered
    (when bloom-color
      (dotimes [i 6]
        (let [angle (* i (/ constants/TWO-PI 6))
              bx (+ x (* r 0.4 (Math/cos angle)))
              by (+ y (* r 0.35 (Math/sin angle)))]
          (draw-ellipse! ctx bx by (* r 0.12) (* r 0.12) bloom-color))))))

(defn- render-fountain!
  "Render a fountain plant (arching from center like ornamental grasses)."
  [ctx x y radius color bloom-color]
  (let [r radius
        arches 14]
    ;; Arching blades/fronds
    (dotimes [i arches]
      (let [angle (* i (/ constants/TWO-PI arches))
            start-x x
            start-y y
            ;; End point arcs outward and down
            end-x (+ x (* r 0.95 (Math/cos angle)))
            end-y (+ y (* r 0.95 (Math/sin angle)))
            ;; Control point arcs up first then out
            ctrl-x (+ x (* r 0.5 (Math/cos angle)))
            ctrl-y (+ y (* r 0.5 (Math/sin angle)) (* r -0.3))
            blade-color (if (even? i) color (lighten color 0.1))]
        (draw-bezier! ctx start-x start-y ctrl-x ctrl-y end-x end-y blade-color (* r 0.04))))
    ;; Center clump
    (draw-ellipse! ctx x y (* r 0.15) (* r 0.15) (darken color 0.2))
    ;; Plume tips
    (when bloom-color
      (dotimes [i 6]
        (let [angle (* i (/ constants/TWO-PI 6))
              bx (+ x (* r 0.85 (Math/cos angle)))
              by (+ y (* r 0.85 (Math/sin angle)))]
          (draw-ellipse! ctx bx by (* r 0.1) (* r 0.18) bloom-color))))))

(defn- render-fan!
  "Render a fan-shaped plant (like palms or ginkgo)."
  [ctx x y radius color bloom-color]
  (let [r radius
        segments 9]
    ;; Fan segments radiating upward
    (dotimes [i segments]
      (let [;; Spread across top half only
            angle (+ (/ Math/PI 4) (* i (/ Math/PI segments 1.5)))
            length (* r (+ 0.7 (* 0.2 (Math/abs (Math/sin (* i 2))))))
            width (* r 0.2)
            seg-color (if (even? i) color (lighten color 0.08))]
        (draw-leaf-shape! ctx x y length width (- angle Math/PI) seg-color)))
    ;; Stem/trunk
    (draw-ellipse! ctx x (+ y (* r 0.3)) (* r 0.15) (* r 0.25) (darken color 0.3))
    ;; Blooms
    (when bloom-color
      (dotimes [i 3]
        (let [angle (+ (/ Math/PI 3) (* i (/ Math/PI 4)))
              bx (+ x (* r 0.5 (Math/cos (- angle (/ Math/PI 2)))))
              by (- y (* r 0.5 (Math/sin (- angle (/ Math/PI 2)))))]
          (draw-ellipse! ctx bx by (* r 0.1) (* r 0.1) bloom-color))))))

;; =============================================================================
;; Life Stage Renderers

(defn- render-seed!
  "Render a seed (small oval in soil mound)."
  [ctx x y radius color]
  (let [r radius]
    ;; Soil mound
    (draw-ellipse! ctx x (+ y (* r 0.3)) (* r 1.2) (* r 0.6) "#8B7355")
    ;; Seed
    (draw-ellipse! ctx x y (* r 0.5) (* r 0.8) color)
    ;; Highlight
    (draw-ellipse! ctx (- x (* r 0.1)) (- y (* r 0.2)) (* r 0.15) (* r 0.2) (lighten color 0.3))))

(defn- render-seedling!
  "Render a seedling (small sprout)."
  [ctx x y radius color]
  (let [r radius]
    ;; Soil mound
    (draw-ellipse! ctx x (+ y (* r 0.3)) (* r 0.8) (* r 0.4) "#8B7355")
    ;; Stem
    (draw-line! ctx x (+ y (* r 0.2)) x (- y (* r 0.4)) "#4CAF50" (* r 0.15))
    ;; Two cotyledons
    (draw-leaf-shape! ctx x (- y (* r 0.3)) (* r 0.5) (* r 0.25) -0.6 color)
    (draw-leaf-shape! ctx x (- y (* r 0.3)) (* r 0.5) (* r 0.25) 0.6 color)
    ;; Small true leaf
    (draw-leaf-shape! ctx x (- y (* r 0.5)) (* r 0.3) (* r 0.15) 0 "#2E7D32")))

;; =============================================================================
;; Main Render Function

(defn render-plant!
  "Render a plant based on its properties.

   Arguments:
   - ctx: Canvas 2D context
   - plant: Plant instance with :species-id, :position, :stage
   - zoom: Current viewport zoom level

   The plant is rendered using its growth habit as the primary visual form."
  [ctx plant zoom]
  (let [species-id (:species-id plant)
        plant-data (plants/get-plant species-id)
        [x y] (:position plant)
        stage (or (:stage plant) :mature)
        ;; Get visual properties
        habit (or (:habit plant-data) :mounding)
        color (or (:color plant-data) "#228B22")
        bloom-color (:bloom-color plant-data)
        spacing-cm (or (:spacing-cm plant-data) 50)
        ;; Calculate radius based on stage
        base-radius (/ spacing-cm 2)
        radius (case stage
                 :seed (* base-radius 0.15)
                 :seedling (* base-radius 0.4)
                 :mature base-radius
                 base-radius)]

    ;; Render based on stage
    (case stage
      :seed (render-seed! ctx x y radius color)
      :seedling (render-seedling! ctx x y radius color)
      ;; Mature: render based on growth habit
      (case habit
        :columnar (render-columnar! ctx x y radius color bloom-color)
        :spreading (render-spreading! ctx x y radius color bloom-color)
        :vase (render-vase! ctx x y radius color bloom-color)
        :weeping (render-weeping! ctx x y radius color bloom-color)
        :mounding (render-mounding! ctx x y radius color bloom-color)
        :upright (render-upright! ctx x y radius color bloom-color)
        :prostrate (render-prostrate! ctx x y radius color bloom-color)
        :rosette (render-rosette! ctx x y radius color bloom-color)
        :clumping (render-clumping! ctx x y radius color bloom-color)
        :vining (render-vining! ctx x y radius color bloom-color)
        :spiky (render-spiky! ctx x y radius color bloom-color)
        :bushy (render-bushy! ctx x y radius color bloom-color)
        :fountain (render-fountain! ctx x y radius color bloom-color)
        :fan (render-fan! ctx x y radius color bloom-color)
        ;; Default fallback
        (render-mounding! ctx x y radius color bloom-color)))))

(defn render-plant-simple!
  "Render a simplified plant (for low zoom levels)."
  [ctx plant]
  (let [species-id (:species-id plant)
        plant-data (plants/get-plant species-id)
        [x y] (:position plant)
        stage (or (:stage plant) :mature)
        color (or (:color plant-data) "#228B22")
        spacing-cm (or (:spacing-cm plant-data) 50)
        base-radius (/ spacing-cm 2)
        radius (case stage
                 :seed (* base-radius 0.15)
                 :seedling (* base-radius 0.4)
                 :mature base-radius
                 base-radius)]
    (draw-ellipse! ctx x y radius radius color)))

(defn get-plant-radius
  "Get the display radius for a plant (for hit detection)."
  [plant]
  (let [species-id (:species-id plant)
        plant-data (plants/get-plant species-id)
        stage (or (:stage plant) :mature)
        spacing-cm (or (:spacing-cm plant-data) 50)
        base-radius (/ spacing-cm 2)]
    (case stage
      :seed (* base-radius 0.15)
      :seedling (* base-radius 0.4)
      :mature base-radius
      base-radius)))
