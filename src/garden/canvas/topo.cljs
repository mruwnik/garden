(ns garden.canvas.topo
  "Render topographical data overlays.

   Renders:
   - Elevation color overlay (cached ImageData)
   - Contour lines (marching squares algorithm)
   - Elevation legend

   Supports three color scale modes:
   - :data     - Use full data range (default)
   - :absolute - Fixed global range (-300m to 8000m)
   - :visible  - Dynamic range based on visible viewport"
  (:require [garden.topo.core :as topo]
            [garden.state :as state]))

;; =============================================================================
;; Cache State

;; Full-extent cache for :data and :absolute modes (rendered once)
(defonce ^:private full-cache
  (atom {:canvas nil           ; OffscreenCanvas covering entire topo bounds
         :bounds nil           ; {:left :top :right :bottom} in canvas coords
         :color-mode nil       ; :data or :absolute
         :data-hash nil}))     ; Hash of elevation data

;; Viewport-aware cache for :visible mode only (debounced updates)
(defonce ^:private viewport-cache
  (atom {:canvas nil           ; OffscreenCanvas with rendered elevation
         :bounds nil           ; {:left :top :right :bottom} in canvas coords
         :color-range nil      ; [min max] used for coloring
         :data-hash nil        ; Hash of elevation data
         :pending? false}))    ; Whether a render is pending

;; Debounce timer for :visible mode re-rendering
(defonce ^:private render-timer (atom nil))
(def ^:private render-debounce-ms 3000)  ; Wait 3s after last pan/zoom

;; Max pixels for full-extent cache (to avoid memory issues)
(def ^:private max-full-cache-pixels 4194304)  ; 2048x2048

;; Absolute elevation range for :absolute color scale mode
(def ^:private absolute-min-elevation -300)
(def ^:private absolute-max-elevation 8000)

;; =============================================================================
;; Color Ramps

(def ^:private elevation-colors
  "Default elevation color ramp: green (low) -> brown -> white (high)"
  [[0.0  [34 139 34]]    ; Forest green (low)
   [0.3  [107 142 35]]   ; Olive drab
   [0.5  [210 180 140]]  ; Tan
   [0.7  [139 90 43]]    ; Saddle brown
   [0.85 [160 160 160]]  ; Gray (rock)
   [1.0  [255 255 255]]])  ; White (snow/peak)

(defn- lerp
  "Linear interpolation between a and b by t (0-1)."
  [a b t]
  (+ a (* (- b a) t)))

(defn- lerp-color
  "Interpolate between two RGB colors."
  [[r1 g1 b1] [r2 g2 b2] t]
  [(int (lerp r1 r2 t))
   (int (lerp g1 g2 t))
   (int (lerp b1 b2 t))])

(defn- elevation->color
  "Convert normalized elevation (0-1) to RGB color using ramp."
  [normalized-elev]
  (let [ramp elevation-colors
        lower (last (filter #(<= (first %) normalized-elev) ramp))
        upper (first (filter #(> (first %) normalized-elev) ramp))]
    (cond
      (nil? lower) (second (first ramp))
      (nil? upper) (second (last ramp))
      :else
      (let [[t1 c1] lower
            [t2 c2] upper
            t (/ (- normalized-elev t1) (- t2 t1))]
        (lerp-color c1 c2 t)))))

(defn- rgba-str
  "Convert RGB array to CSS rgba string."
  [[r g b] alpha]
  (str "rgba(" r "," g "," b "," alpha ")"))

;; =============================================================================
;; Elevation Range Calculation

(defn- get-visible-elevation-range
  "Calculate min/max elevation for the currently visible viewport area."
  [topo-state viewport-state]
  (let [{:keys [elevation-data bounds resolution]} topo-state
        grid-width (:width topo-state)
        grid-height (:height topo-state)
        {:keys [offset zoom size]} viewport-state
        [ox oy] offset
        vp-width (or (:width size) 800)
        vp-height (or (:height size) 600)]
    (when (and elevation-data grid-width grid-height bounds resolution)
      (let [{:keys [min-x min-y max-x max-y]} bounds
            ;; Convert viewport corners to canvas coordinates
            ;; Screen to canvas: canvas = (screen - offset) / zoom
            canvas-left (/ (- 0 ox) zoom)
            canvas-top (/ (- 0 oy) zoom)
            canvas-right (/ (- vp-width ox) zoom)
            canvas-bottom (/ (- vp-height oy) zoom)
            ;; Clamp to topo bounds
            vis-min-x (max min-x canvas-left)
            vis-min-y (max min-y canvas-top)
            vis-max-x (min max-x canvas-right)
            vis-max-y (min max-y canvas-bottom)
            ;; Convert to grid coordinates
            grid-min-col (max 0 (int (/ (- vis-min-x min-x) resolution)))
            grid-min-row (max 0 (int (/ (- vis-min-y min-y) resolution)))
            grid-max-col (min (dec grid-width) (int (/ (- vis-max-x min-x) resolution)))
            grid-max-row (min (dec grid-height) (int (/ (- vis-max-y min-y) resolution)))]
        ;; Find min/max in visible region
        (when (and (< grid-min-col grid-max-col) (< grid-min-row grid-max-row))
          (loop [row grid-min-row
                 vis-min js/Infinity
                 vis-max js/-Infinity]
            (if (> row grid-max-row)
              (when (and (js/isFinite vis-min) (js/isFinite vis-max))
                [vis-min vis-max])
              (let [[row-min row-max]
                    (loop [col grid-min-col
                           rmin vis-min
                           rmax vis-max]
                      (if (> col grid-max-col)
                        [rmin rmax]
                        (let [idx (+ col (* row grid-width))
                              v (aget elevation-data idx)]
                          (if (js/isNaN v)
                            (recur (inc col) rmin rmax)
                            (recur (inc col) (min rmin v) (max rmax v))))))]
                (recur (inc row) row-min row-max)))))))))

(defn- get-color-scale-range
  "Get the min/max elevation to use for coloring based on scale mode."
  [topo-state viewport-state]
  (let [mode (or (:color-scale-mode topo-state) :data)]
    (case mode
      :absolute [absolute-min-elevation absolute-max-elevation]
      :visible (or (get-visible-elevation-range topo-state viewport-state)
                   [(:min-elevation topo-state) (:max-elevation topo-state)])
      ;; :data is default
      [(:min-elevation topo-state) (:max-elevation topo-state)])))

;; =============================================================================
;; Cache Rendering

(defn- sample-rgb
  "Sample RGB from the rgb-data array at grid position (nearest neighbor)."
  [rgb-data grid-width grid-height gx gy]
  (let [ix (max 0 (min (dec grid-width) (int gx)))
        iy (max 0 (min (dec grid-height) (int gy)))
        idx (* (+ ix (* iy grid-width)) 4)]
    [(aget rgb-data idx)
     (aget rgb-data (+ idx 1))
     (aget rgb-data (+ idx 2))]))

(defn- render-to-cache!
  "Render elevation/RGB to an offscreen canvas covering the specified bounds.
   Uses RGB data if available, otherwise falls back to elevation coloring.
   Returns the offscreen canvas."
  [topo-state cache-bounds color-min color-max target-resolution]
  (let [{:keys [elevation-data rgb-data bounds resolution]} topo-state
        grid-width (:width topo-state)
        grid-height (:height topo-state)
        {:keys [min-x min-y]} bounds
        {:keys [left top right bottom]} cache-bounds
        use-rgb? (some? rgb-data)
        elev-range (- color-max color-min)
        elev-range (if (pos? elev-range) elev-range 1)
        ;; Calculate render dimensions based on target resolution
        cache-width (- right left)
        cache-height (- bottom top)
        ;; Pixels per canvas unit - clamped to reasonable range
        pixels-per-unit (max 0.1 (min 2.0 target-resolution))
        render-width (max 1 (min 2048 (int (* cache-width pixels-per-unit))))
        render-height (max 1 (min 2048 (int (* cache-height pixels-per-unit))))
        ;; Create offscreen canvas
        offscreen (js/document.createElement "canvas")
        _ (set! (.-width offscreen) render-width)
        _ (set! (.-height offscreen) render-height)
        off-ctx (.getContext offscreen "2d")
        image-data (.createImageData off-ctx render-width render-height)
        pixels (.-data image-data)
        ;; Step size in canvas coords per render pixel
        step-x (/ cache-width render-width)
        step-y (/ cache-height render-height)]
    ;; Fill pixels by sampling from data
    (dotimes [py render-height]
      (dotimes [px render-width]
        (let [;; Canvas coordinate for this pixel
              cx (+ left (* (+ px 0.5) step-x))
              cy (+ top (* (+ py 0.5) step-y))
              ;; Grid coordinate (fractional)
              gx (/ (- cx min-x) resolution)
              gy (/ (- cy min-y) resolution)
              pixel-idx (* (+ px (* py render-width)) 4)]
          (if use-rgb?
            ;; Use RGB data directly
            (if (and (>= gx 0) (< gx grid-width) (>= gy 0) (< gy grid-height))
              (let [[r g b] (sample-rgb rgb-data grid-width grid-height gx gy)]
                (aset pixels pixel-idx r)
                (aset pixels (+ pixel-idx 1) g)
                (aset pixels (+ pixel-idx 2) b)
                (aset pixels (+ pixel-idx 3) 255))
              ;; Out of bounds - transparent
              (do (aset pixels pixel-idx 0)
                  (aset pixels (+ pixel-idx 1) 0)
                  (aset pixels (+ pixel-idx 2) 0)
                  (aset pixels (+ pixel-idx 3) 0)))
            ;; Fall back to elevation coloring
            (let [elev (topo/bilinear-interpolate elevation-data grid-width grid-height gy gx)]
              (if (or (nil? elev) (js/isNaN elev))
                ;; Transparent for no-data
                (do (aset pixels pixel-idx 0)
                    (aset pixels (+ pixel-idx 1) 0)
                    (aset pixels (+ pixel-idx 2) 0)
                    (aset pixels (+ pixel-idx 3) 0))
                ;; Color from elevation
                (let [normalized (max 0 (min 1 (/ (- elev color-min) elev-range)))
                      [r g b] (elevation->color normalized)]
                  (aset pixels pixel-idx r)
                  (aset pixels (+ pixel-idx 1) g)
                  (aset pixels (+ pixel-idx 2) b)
                  (aset pixels (+ pixel-idx 3) 255))))))))
    (.putImageData off-ctx image-data 0 0)
    offscreen))

(defn- render-full-cache!
  "Render the full-extent cache for :data or :absolute modes.
   Called once when data loads or mode changes."
  [topo-state mode]
  (let [{:keys [elevation-data bounds min-elevation max-elevation]} topo-state
        grid-width (:width topo-state)
        grid-height (:height topo-state)
        {:keys [min-x min-y max-x max-y]} bounds
        ;; Calculate render dimensions - aim for 1:1 with grid, capped
        topo-width (- max-x min-x)
        topo-height (- max-y min-y)
        aspect (/ topo-width topo-height)
        ;; Scale to fit within max pixels while preserving aspect
        max-dim (int (Math/sqrt max-full-cache-pixels))
        [render-width render-height]
        (if (> aspect 1)
          [(min max-dim grid-width) (min (int (/ max-dim aspect)) grid-height)]
          [(min (int (* max-dim aspect)) grid-width) (min max-dim grid-height)])
        ;; Get color range based on mode
        [color-min color-max] (case mode
                                :absolute [absolute-min-elevation absolute-max-elevation]
                                [min-elevation max-elevation])
        cache-bounds {:left min-x :top min-y :right max-x :bottom max-y}
        ;; Calculate target resolution (pixels per canvas unit)
        target-resolution (/ render-width topo-width)]
    (js/console.log "Rendering full topo cache:" render-width "x" render-height "for mode:" (name mode))
    (let [canvas (render-to-cache! topo-state cache-bounds color-min color-max target-resolution)]
      (reset! full-cache
              {:canvas canvas
               :bounds cache-bounds
               :color-mode mode
               :data-hash (hash elevation-data)})
      ;; Force a re-render
      (state/set-state! [:ui :_cache-updated] (js/Date.now)))))

(defn- ensure-full-cache!
  "Ensure full-extent cache is valid for current data and mode."
  [topo-state mode]
  (let [cache @full-cache
        current-hash (hash (:elevation-data topo-state))]
    (when (or (nil? (:canvas cache))
              (not= (:data-hash cache) current-hash)
              (not= (:color-mode cache) mode))
      (render-full-cache! topo-state mode))))

(defn- schedule-viewport-cache-update!
  "Schedule a viewport-aware cache update for :visible mode after debounce."
  [topo-state viewport-state]
  ;; Cancel any pending timer
  (when-let [timer @render-timer]
    (js/clearTimeout timer))
  ;; Schedule new render
  (reset! render-timer
          (js/setTimeout
           (fn []
             (let [{:keys [offset zoom size]} viewport-state
                   [ox oy] offset
                   vp-width (or (:width size) 800)
                   vp-height (or (:height size) 600)
                   topo-bounds (:bounds topo-state)
                   {:keys [min-x min-y max-x max-y]} topo-bounds
                   ;; Current visible bounds
                   vis-left (/ (- 0 ox) zoom)
                   vis-top (/ (- 0 oy) zoom)
                   vis-right (/ (- vp-width ox) zoom)
                   vis-bottom (/ (- vp-height oy) zoom)
                   ;; Extend by 100% on each side (3x total area)
                   extend-x (- vis-right vis-left)
                   extend-y (- vis-bottom vis-top)
                   cache-left (max min-x (- vis-left extend-x))
                   cache-top (max min-y (- vis-top extend-y))
                   cache-right (min max-x (+ vis-right extend-x))
                   cache-bottom (min max-y (+ vis-bottom extend-y))
                   cache-bounds {:left cache-left :top cache-top
                                 :right cache-right :bottom cache-bottom}
                   ;; Calculate optimal resolution (pixels per canvas unit)
                   ;; Aim for ~1 pixel per screen pixel at current zoom
                   target-resolution zoom
                   ;; Get color range for visible area
                   [color-min color-max] (get-color-scale-range topo-state viewport-state)
                   ;; Render to cache
                   canvas (render-to-cache! topo-state cache-bounds color-min color-max target-resolution)]
               ;; Store in cache
               (reset! viewport-cache
                       {:canvas canvas
                        :bounds cache-bounds
                        :color-range [color-min color-max]
                        :data-hash (hash (:elevation-data topo-state))
                        :pending? false})
               ;; Force a re-render
               (state/set-state! [:ui :_cache-updated] (js/Date.now))))
           render-debounce-ms)))

(defn- viewport-cache-valid?
  "Check if the viewport cache is valid for :visible mode."
  [topo-state viewport-state]
  (let [cache @viewport-cache
        {:keys [canvas bounds color-range data-hash]} cache]
    (when (and canvas bounds)
      (let [{:keys [offset zoom size]} viewport-state
            [ox oy] offset
            vp-width (or (:width size) 800)
            vp-height (or (:height size) 600)
            ;; Current visible bounds
            vis-left (/ (- 0 ox) zoom)
            vis-top (/ (- 0 oy) zoom)
            vis-right (/ (- vp-width ox) zoom)
            vis-bottom (/ (- vp-height oy) zoom)
            {:keys [left top right bottom]} bounds
            ;; Check if visible area is within cached bounds
            [cached-min cached-max] color-range
            [current-min current-max] (get-color-scale-range topo-state viewport-state)]
        (and (>= vis-left left)
             (>= vis-top top)
             (<= vis-right right)
             (<= vis-bottom bottom)
             (= data-hash (hash (:elevation-data topo-state)))
             ;; Color range changes with viewport in :visible mode
             (= cached-min current-min)
             (= cached-max current-max))))))

(defn- draw-cache-to-screen!
  "Draw a cache canvas to the screen context."
  [ctx cache-canvas cache-bounds viewport-state opacity]
  (let [{:keys [left top right bottom]} cache-bounds
        {:keys [offset zoom]} viewport-state
        [ox oy] offset
        ;; Convert cache bounds to screen coordinates
        screen-left (+ ox (* left zoom))
        screen-top (+ oy (* top zoom))
        screen-width (* (- right left) zoom)
        screen-height (* (- bottom top) zoom)]
    (.save ctx)
    (set! (.-globalAlpha ctx) opacity)
    (.drawImage ctx cache-canvas
                screen-left screen-top
                screen-width screen-height)
    (.restore ctx)))

;; =============================================================================
;; Overlay Rendering

(defn render-elevation-overlay!
  "Render elevation as colored overlay.
   For :data/:absolute modes: uses full-extent cache (rendered once).
   For :visible mode: uses viewport cache with debounced updates."
  [ctx state]
  (let [topo-state (:topo state)
        viewport-state (:viewport state)
        {:keys [visible? elevation-data]} topo-state
        opacity (or (:opacity topo-state) 0.3)
        mode (or (:color-scale-mode topo-state) :data)]
    (when (and visible? elevation-data)
      (if (= mode :visible)
        ;; :visible mode - use viewport cache with debounced updates
        (let [cache @viewport-cache
              {:keys [canvas bounds]} cache
              cache-ok? (viewport-cache-valid? topo-state viewport-state)]
          ;; Schedule cache update if needed
          (when (not cache-ok?)
            (schedule-viewport-cache-update! topo-state viewport-state))
          ;; Draw from cache if available (even if outdated - better than nothing)
          (when (and canvas bounds)
            (draw-cache-to-screen! ctx canvas bounds viewport-state opacity)))
        ;; :data or :absolute mode - use full-extent cache (no debounce)
        (do
          (ensure-full-cache! topo-state mode)
          (let [cache @full-cache
                {:keys [canvas bounds]} cache]
            (when (and canvas bounds)
              (draw-cache-to-screen! ctx canvas bounds viewport-state opacity))))))))

;; =============================================================================
;; Contour Lines

(defn render-contours!
  "Render contour lines at regular intervals using marching squares.
   Draws segments with round caps so adjacent segments connect visually."
  [ctx state]
  (let [topo-state (:topo state)
        {:keys [elevation-data bounds resolution width height contours]} topo-state
        {:keys [visible? interval color]} contours
        zoom (get-in state [:viewport :zoom])]
    (when (and visible? elevation-data bounds resolution (pos? interval))
      (let [effective-interval (if (< zoom 0.2)
                                 (* interval 5)
                                 interval)
            [min-elev max-elev] (topo/elevation-range)
            start-level (* (Math/ceil (/ min-elev effective-interval)) effective-interval)
            levels (take-while #(<= % max-elev)
                               (iterate #(+ % effective-interval) start-level))
            {:keys [min-x min-y]} bounds]
        (.save ctx)
        (set! (.-strokeStyle ctx) (or color "#8B4513"))
        (set! (.-lineWidth ctx) (/ 2 zoom))
        (set! (.-lineCap ctx) "round")
        ;; Draw all contour levels
        (doseq [level levels]
          (.beginPath ctx)
          (dotimes [row (dec height)]
            (dotimes [col (dec width)]
              (let [idx00 (+ col (* row width))
                    idx10 (+ (inc col) (* row width))
                    idx01 (+ col (* (inc row) width))
                    idx11 (+ (inc col) (* (inc row) width))
                    e00 (aget elevation-data idx00)
                    e10 (aget elevation-data idx10)
                    e01 (aget elevation-data idx01)
                    e11 (aget elevation-data idx11)]
                (when (and (not (js/isNaN e00))
                           (not (js/isNaN e10))
                           (not (js/isNaN e01))
                           (not (js/isNaN e11)))
                  (let [b00 (if (>= e00 level) 1 0)
                        b10 (if (>= e10 level) 1 0)
                        b01 (if (>= e01 level) 1 0)
                        b11 (if (>= e11 level) 1 0)
                        case-idx (+ (* b00 8) (* b10 4) (* b11 2) b01)
                        cx (+ min-x (* col resolution))
                        cy (+ min-y (* row resolution))
                        interp-x (fn [ea eb]
                                   (if (= ea eb) 0.5 (/ (- level ea) (- eb ea))))
                        top-x (+ cx (* (interp-x e00 e10) resolution))
                        top-y cy
                        bottom-x (+ cx (* (interp-x e01 e11) resolution))
                        bottom-y (+ cy resolution)
                        left-x cx
                        left-y (+ cy (* (interp-x e00 e01) resolution))
                        right-x (+ cx resolution)
                        right-y (+ cy (* (interp-x e10 e11) resolution))]
                    (case case-idx
                      (1 14) (do (.moveTo ctx left-x left-y) (.lineTo ctx bottom-x bottom-y))
                      (2 13) (do (.moveTo ctx bottom-x bottom-y) (.lineTo ctx right-x right-y))
                      (3 12) (do (.moveTo ctx left-x left-y) (.lineTo ctx right-x right-y))
                      (4 11) (do (.moveTo ctx top-x top-y) (.lineTo ctx right-x right-y))
                      (6 9)  (do (.moveTo ctx top-x top-y) (.lineTo ctx bottom-x bottom-y))
                      (7 8)  (do (.moveTo ctx left-x left-y) (.lineTo ctx top-x top-y))
                      5 (do (.moveTo ctx left-x left-y) (.lineTo ctx top-x top-y)
                            (.moveTo ctx bottom-x bottom-y) (.lineTo ctx right-x right-y))
                      10 (do (.moveTo ctx top-x top-y) (.lineTo ctx right-x right-y)
                             (.moveTo ctx left-x left-y) (.lineTo ctx bottom-x bottom-y))
                      nil))))))
          (.stroke ctx))
        (.restore ctx)))))

;; =============================================================================
;; Legend

(defn render-elevation-legend!
  "Render an elevation legend in fixed screen position."
  [ctx state]
  (let [topo-state (:topo state)
        viewport-state (:viewport state)
        {:keys [elevation-data visible?]} topo-state]
    (when (and visible? elevation-data)
      ;; Use the same color range as the overlay
      (let [[min-elev max-elev] (get-color-scale-range topo-state viewport-state)
            mode (or (:color-scale-mode topo-state) :data)
            {:keys [width height]} (get-in state [:viewport :size])
            legend-w 20
            legend-h 150
            padding 8
            margin 20
            legend-x (- width margin legend-w)
            legend-y margin
            mode-label (case mode :absolute "Abs" :visible "Vis" "Data")]
        (.save ctx)
        (set! (.-fillStyle ctx) "rgba(255,255,255,0.9)")
        (.fillRect ctx (- legend-x padding) (- legend-y padding)
                   (+ legend-w (* 2 padding)) (+ legend-h (* 2 padding) 30))
        (doseq [i (range legend-h)]
          (let [normalized (- 1 (/ i legend-h))
                color (elevation->color normalized)]
            (set! (.-fillStyle ctx) (rgba-str color 1.0))
            (.fillRect ctx legend-x (+ legend-y i) legend-w 1)))
        (set! (.-strokeStyle ctx) "#333")
        (set! (.-lineWidth ctx) 1)
        (.strokeRect ctx legend-x legend-y legend-w legend-h)
        (set! (.-font ctx) "11px sans-serif")
        (set! (.-fillStyle ctx) "#333")
        (set! (.-textAlign ctx) "left")
        (set! (.-textBaseline ctx) "top")
        (.fillText ctx (str (.toFixed max-elev 0) "m") (+ legend-x legend-w 4) legend-y)
        (set! (.-textBaseline ctx) "bottom")
        (.fillText ctx (str (.toFixed min-elev 0) "m") (+ legend-x legend-w 4) (+ legend-y legend-h))
        (set! (.-textBaseline ctx) "top")
        (set! (.-textAlign ctx) "center")
        (.fillText ctx mode-label (+ legend-x (/ legend-w 2)) (+ legend-y legend-h 8))
        (.restore ctx)))))

;; =============================================================================
;; Public API

(defn invalidate-cache!
  "Clear all cached bitmaps (call when topo data changes)."
  []
  (when-let [timer @render-timer]
    (js/clearTimeout timer))
  (reset! full-cache
          {:canvas nil
           :bounds nil
           :color-mode nil
           :data-hash nil})
  (reset! viewport-cache
          {:canvas nil
           :bounds nil
           :color-range nil
           :data-hash nil
           :pending? false}))

(defn render!
  "Main render function for topo overlay."
  [ctx state]
  (let [topo-state (:topo state)]
    (when (:visible? topo-state)
      (render-elevation-overlay! ctx state)
      (render-contours! ctx state))))
