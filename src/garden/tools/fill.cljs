(ns garden.tools.fill
  "Fill tool - click to flood fill from reference image and create area."
  (:require [garden.tools.protocol :as p]
            [garden.state :as state]))

;; Color tolerance for flood fill (0-255 range)
(def ^:private default-tolerance 32)

;; Area types that respect existing areas by default
(def ^:private respects-existing-default #{:water :path})

(defn- colors-similar?
  "Check if two colors are within tolerance."
  [[r1 g1 b1 _a1] [r2 g2 b2 _a2] tolerance]
  (and (<= (js/Math.abs (- r1 r2)) tolerance)
       (<= (js/Math.abs (- g1 g2)) tolerance)
       (<= (js/Math.abs (- b1 b2)) tolerance)))

(defn- canvas->image-coords
  "Convert canvas coordinates to reference image pixel coordinates.
   Position is the image CENTER in canvas coordinates."
  [canvas-point ref-img]
  (let [{:keys [image position bar-meters]} ref-img
        [center-x center-y] (or position [0 0])
        [cx cy] canvas-point
        ;; Scale: 150 image pixels = bar-meters * 100 cm
        bar-px 150
        scale (/ (* (or bar-meters 50) 100) bar-px)
        ;; Calculate top-left from center
        img-w (.-width image)
        img-h (.-height image)
        top-left-x (- center-x (/ (* img-w scale) 2))
        top-left-y (- center-y (/ (* img-h scale) 2))
        ;; Convert canvas coords to image coords
        img-x (/ (- cx top-left-x) scale)
        img-y (/ (- cy top-left-y) scale)]
    [(int img-x) (int img-y)]))

(defn- canvas->image-point
  "Convert a canvas point to image coordinates (inverse of image->canvas-coord)."
  [[cx cy] ref-img]
  (let [{:keys [position bar-meters]} ref-img
        [px py] position
        bar-px 150
        scale (/ (* (or bar-meters 50) 100) bar-px)]
    [(/ (- cx px) scale)
     (/ (- cy py) scale)]))

(defn- create-exclusion-mask
  "Pre-render existing areas to a canvas for fast O(1) exclusion checks.
   Returns a Uint8Array where non-zero means the pixel is in an existing area."
  [w h ref-img areas]
  (let [canvas (js/document.createElement "canvas")
        ctx (.getContext canvas "2d")]
    (set! (.-width canvas) w)
    (set! (.-height canvas) h)
    ;; Draw all areas as filled polygons
    (doseq [area areas]
      (let [points (:points area)]
        (when (>= (count points) 3)
          (.beginPath ctx)
          (let [[fx fy] (canvas->image-point (first points) ref-img)]
            (.moveTo ctx fx fy))
          (doseq [pt (rest points)]
            (let [[x y] (canvas->image-point pt ref-img)]
              (.lineTo ctx x y)))
          (.closePath ctx)
          (set! (.-fillStyle ctx) "white")
          (.fill ctx))))
    ;; Get pixel data - just check alpha or red channel
    (let [image-data (.getImageData ctx 0 0 w h)]
      (.-data image-data))))

(defn- flood-fill-mask
  "Perform flood fill and return a 2D boolean mask of filled pixels.
   Uses a scanline algorithm for efficiency.
   exclusion-mask is an optional Uint8Array (from canvas getImageData) where non-zero R channel means exclude."
  [img start-x start-y tolerance max-pixels exclusion-mask]
  (let [w (.-width img)
        h (.-height img)
        ;; Create a canvas to read pixels
        canvas (js/document.createElement "canvas")
        ctx (.getContext canvas "2d")]
    (set! (.-width canvas) w)
    (set! (.-height canvas) h)
    (.drawImage ctx img 0 0)
    (let [image-data (.getImageData ctx 0 0 w h)
          data (.-data image-data)
          ;; Get target color
          idx (* 4 (+ start-x (* start-y w)))
          target-color [(aget data idx)
                        (aget data (+ idx 1))
                        (aget data (+ idx 2))
                        (aget data (+ idx 3))]
          ;; Track visited pixels
          visited (js/Set.)
          filled (js/Set.)
          ;; Pixel helper
          get-color (fn [x y]
                      (let [i (* 4 (+ x (* y w)))]
                        [(aget data i)
                         (aget data (+ i 1))
                         (aget data (+ i 2))
                         (aget data (+ i 3))]))
          key-fn (fn [x y] (+ x (* y w)))]
      ;; Scanline flood fill
      (loop [stack [[start-x start-y]]
             count 0]
        (if (or (empty? stack) (>= count max-pixels))
          filled
          (let [[x y] (peek stack)
                stack (pop stack)
                k (key-fn x y)]
            (if (or (neg? x) (>= x w) (neg? y) (>= y h)
                    (.has visited k))
              (recur stack count)
              (do
                (.add visited k)
                (if (and (colors-similar? (get-color x y) target-color tolerance)
                         (not (and exclusion-mask
                                   (let [i (* 4 (+ x (* y w)))]
                                     (pos? (aget exclusion-mask i))))))
                  (do
                    (.add filled k)
                    (recur (-> stack
                               (conj [(dec x) y])
                               (conj [(inc x) y])
                               (conj [x (dec y)])
                               (conj [x (inc y)]))
                           (inc count)))
                  (recur stack count))))))))))

(defn- find-start-pixel
  "Find the topmost-leftmost boundary pixel."
  [filled-set w h]
  (first (for [y (range h)
               x (range w)
               :let [k (+ x (* y w))]
               :when (and (.has filled-set k)
                          (not (.has filled-set (+ x (* (dec y) w)))))]
           [x y])))

(defn- moore-neighborhood
  "Get the 8 neighbors in Moore neighborhood order (clockwise from given direction)."
  [[x y] start-dir]
  (let [dirs [[1 0] [1 1] [0 1] [-1 1] [-1 0] [-1 -1] [0 -1] [1 -1]]
        start-idx (mod start-dir 8)]
    (for [i (range 8)]
      (let [[dx dy] (nth dirs (mod (+ start-idx i) 8))]
        [(+ x dx) (+ y dy)]))))

(defn- trace-contour
  "Trace the contour using Moore-Neighbor tracing algorithm."
  [filled-set w h start]
  (let [key-fn (fn [[x y]] (+ x (* y w)))
        in-bounds? (fn [[x y]] (and (>= x 0) (< x w) (>= y 0) (< y h)))
        is-filled? (fn [p] (and (in-bounds? p) (.has filled-set (key-fn p))))]
    (loop [contour [start]
           current start
           dir 0  ; Start looking right
           steps 0]
      (if (> steps (* 4 (.-size filled-set)))
        ;; Safety limit reached
        contour
        (let [neighbors (moore-neighborhood current dir)
              ;; Find first filled neighbor
              next-info (first (keep-indexed
                                (fn [i p]
                                  (when (is-filled? p)
                                    {:point p :back-dir (mod (+ dir i 5) 8)}))
                                neighbors))]
          (if (nil? next-info)
            ;; No filled neighbor - isolated pixel
            contour
            (let [{:keys [point back-dir]} next-info]
              (if (and (= point start) (> (count contour) 2))
                ;; Back to start - contour complete
                contour
                (recur (conj contour point)
                       point
                       back-dir
                       (inc steps))))))))))

(defn- simplify-contour
  "Reduce points using Ramer-Douglas-Peucker algorithm."
  [points epsilon]
  (if (<= (count points) 2)
    points
    (let [start (first points)
          end (last points)
          ;; Find point with max distance from line
          line-dist (fn [[px py]]
                      (let [[x1 y1] start
                            [x2 y2] end
                            num (js/Math.abs (+ (* (- y2 y1) px)
                                                (- (* (- x2 x1) py))
                                                (* x2 y1)
                                                (- (* y2 x1))))
                            den (js/Math.sqrt (+ (* (- y2 y1) (- y2 y1))
                                                 (* (- x2 x1) (- x2 x1))))]
                        (if (zero? den) 0 (/ num den))))
          indexed (map-indexed vector (rest (butlast points)))
          [max-idx max-dist] (reduce (fn [[mi md] [i p]]
                                       (let [d (line-dist p)]
                                         (if (> d md) [i d] [mi md])))
                                     [0 0]
                                     indexed)]
      (if (> max-dist epsilon)
        ;; Recursively simplify
        (let [left (simplify-contour (vec (take (+ max-idx 2) points)) epsilon)
              right (simplify-contour (vec (drop (inc max-idx) points)) epsilon)]
          (vec (concat (butlast left) right)))
        ;; All points within tolerance
        [start end]))))

(defn- mask-to-outline
  "Convert a filled mask to an outline polygon using Moore-Neighbor tracing.
   Returns points in image coordinates."
  [filled-set w h]
  (when-let [start (find-start-pixel filled-set w h)]
    (let [contour (trace-contour filled-set w h start)
          ;; Simplify to reduce points (epsilon = 2 pixels)
          simplified (simplify-contour (vec contour) 2.0)]
      (when (>= (count simplified) 3)
        simplified))))

(defn- image->canvas-coords
  "Convert image coordinates to canvas coordinates."
  [img-points ref-img]
  (let [{:keys [position bar-meters]} ref-img
        [px py] position
        bar-px 150
        scale (/ (* (or bar-meters 50) 100) bar-px)]
    (mapv (fn [[ix iy]]
            [(+ px (* ix scale))
             (+ py (* iy scale))])
          img-points)))

(defn- do-fill!
  "Perform the fill operation at the given canvas point."
  [canvas-point area-type]
  (let [ref-img (state/get-state :ui :reference-image)
        img (:image ref-img)]
    (when (and img (:visible? ref-img))
      (let [[img-x img-y] (canvas->image-coords canvas-point ref-img)
            w (.-width img)
            h (.-height img)]
        (when (and (>= img-x 0) (< img-x w)
                   (>= img-y 0) (< img-y h))
          (let [tool-state (state/tool-state)
                tolerance (or (:tolerance tool-state) default-tolerance)
                ;; Check if we should respect existing areas
                respect-existing? (if (contains? tool-state :respect-existing?)
                                    (:respect-existing? tool-state)
                                    (contains? respects-existing-default area-type))
                ;; Create exclusion mask for fast O(1) checks
                exclusion-mask (when respect-existing?
                                 (let [areas (state/areas)]
                                   (when (seq areas)
                                     (create-exclusion-mask w h ref-img areas))))
                filled (flood-fill-mask img img-x img-y tolerance 200000 exclusion-mask)
                outline (mask-to-outline filled w h)]
            (when (and outline (>= (count outline) 3))
              (let [canvas-points (image->canvas-coords outline ref-img)
                    type-colors {:water "#4a90d9"
                                 :bed "#8B6914"
                                 :path "#d4a574"
                                 :structure "#607D8B"
                                 :lawn "#7CB342"
                                 :rocks "#9E9E9E"
                                 :hedge "#2E7D32"
                                 :mulch "#5D4037"
                                 :patio "#8D6E63"
                                 :sand "#E8D5B7"}
                    type-names {:water "Water"
                                :bed "Garden Bed"
                                :path "Path"
                                :structure "Structure"
                                :lawn "Lawn"
                                :rocks "Rocks"
                                :hedge "Hedge"
                                :mulch "Mulch"
                                :patio "Patio"
                                :sand "Sand"}
                    area-id (state/add-area!
                             {:name (str (get type-names area-type "Area") " " (inc (count (state/areas))))
                              :type area-type
                              :points canvas-points
                              :color (get type-colors area-type "#888888")})]
                (state/select! :area #{area-id})))))))))

(defrecord FillTool []
  p/ITool
  (tool-id [_] :fill)
  (tool-label [_] "Fill")
  (tool-icon [_] "fill")
  (cursor [_] "crosshair")

  (on-activate [_]
    (state/set-tool-state! {:area-type :water
                            :tolerance default-tolerance
                            :respect-existing? true}))  ; Default on for water

  (on-deactivate [_]
    (state/set-tool-state! nil))

  (on-mouse-down [_ point _event]
    (do-fill! point (or (:area-type (state/tool-state)) :water)))

  (on-mouse-move [_ _point _event]
    nil)

  (on-mouse-up [_ _point _event]
    nil)

  (on-key-down [_ event]
    (let [key (.-key event)]
      ;; Same shortcuts as trace tool
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
        ;; Tolerance adjustment with +/-
        "=" (state/update-tool-state! update :tolerance #(min 128 (+ (or % default-tolerance) 8)))
        "-" (state/update-tool-state! update :tolerance #(max 8 (- (or % default-tolerance) 8)))
        ;; Letter shortcuts
        "w" (state/update-tool-state! assoc :area-type :water)
        "b" (state/update-tool-state! assoc :area-type :bed)
        "p" (state/update-tool-state! assoc :area-type :path)
        "l" (state/update-tool-state! assoc :area-type :lawn)
        "h" (state/update-tool-state! assoc :area-type :hedge)
        nil))))

;; Register the tool
(p/register-tool! (->FillTool))
