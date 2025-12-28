(ns garden.tools.fill
  "Magic wand fill tool for automatic area detection.

   Click on a region in the reference image to automatically detect
   its boundaries using flood fill. Supports hole detection for
   areas with enclosed unfilled regions (e.g., islands in ponds).

   Uses Moore-Neighbor tracing and Ramer-Douglas-Peucker simplification.

   Keyboard shortcuts:
   - 0-9: Select area type (1=water, 2=bed, 3=path, etc.)
   - +/-: Adjust color tolerance"
  (:require [garden.tools.protocol :as p]
            [garden.state :as state]))

;; =============================================================================
;; Constants

(def ^:private default-tolerance 32)

;; Area types that respect existing areas by default
(def ^:private respects-existing-default #{:water :path})

;; =============================================================================
;; Coordinate Conversion

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
  "Convert a canvas point to image coordinates.
   Position is the image CENTER in canvas coordinates."
  [[cx cy] ref-img]
  (let [{:keys [image position bar-meters]} ref-img
        [center-x center-y] (or position [0 0])
        bar-px 150
        scale (/ (* (or bar-meters 50) 100) bar-px)
        ;; Calculate top-left from center
        img-w (.-width image)
        img-h (.-height image)
        top-left-x (- center-x (/ (* img-w scale) 2))
        top-left-y (- center-y (/ (* img-h scale) 2))]
    [(/ (- cx top-left-x) scale)
     (/ (- cy top-left-y) scale)]))

;; =============================================================================
;; Exclusion Mask

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

;; =============================================================================
;; Flood Fill

(defn- flood-fill-mask
  "Perform flood fill and return a js/Set of filled pixel keys.
   Uses JS arrays for stack to avoid Clojure overhead."
  [img start-x start-y tolerance max-pixels exclusion-mask]
  (let [w (.-width img)
        h (.-height img)
        canvas (js/document.createElement "canvas")
        ctx (.getContext canvas "2d")]
    (set! (.-width canvas) w)
    (set! (.-height canvas) h)
    (.drawImage ctx img 0 0)
    (let [image-data (.getImageData ctx 0 0 w h)
          data (.-data image-data)
          idx (* 4 (+ start-x (* start-y w)))
          tr (aget data idx)
          tg (aget data (+ idx 1))
          tb (aget data (+ idx 2))
          ;; Use JS array as stack for performance
          stack #js [start-x start-y]
          visited (js/Set.)
          filled (js/Set.)]
      (loop [cnt 0]
        (if (or (zero? (.-length stack)) (>= cnt max-pixels))
          filled
          (let [y (.pop stack)
                x (.pop stack)
                k (+ x (* y w))]
            (if (or (neg? x) (>= x w) (neg? y) (>= y h) (.has visited k))
              (recur cnt)
              (do
                (.add visited k)
                (let [i (* 4 k)
                      r (aget data i)
                      g (aget data (+ i 1))
                      b (aget data (+ i 2))]
                  (if (and (<= (js/Math.abs (- r tr)) tolerance)
                           (<= (js/Math.abs (- g tg)) tolerance)
                           (<= (js/Math.abs (- b tb)) tolerance)
                           (not (and exclusion-mask (pos? (aget exclusion-mask i)))))
                    (do
                      (.add filled k)
                      (.push stack (dec x) y)
                      (.push stack (inc x) y)
                      (.push stack x (dec y))
                      (.push stack x (inc y))
                      (recur (inc cnt)))
                    (recur cnt)))))))))))

;; =============================================================================
;; Contour Tracing

(defn- find-start-pixel
  "Find the topmost-leftmost boundary pixel by scanning filled pixels."
  [filled-set w _h]
  (let [result (atom nil)]
    (.forEach filled-set
              (fn [k]
                (let [x (mod k w)
                      y (quot k w)
                      ;; Check if this is a top boundary (pixel above not filled)
                      above-k (- k w)]
                  (when (and (or (neg? above-k) (not (.has filled-set above-k)))
                             ;; Is this better than current best?
                             (or (nil? @result)
                                 (< y (second @result))
                                 (and (= y (second @result)) (< x (first @result)))))
                    (reset! result [x y])))))
    @result))

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

(defn- sample-contour
  "Pre-sample a contour to at most max-points by taking every nth point."
  [points max-points]
  (let [n (count points)]
    (if (<= n max-points)
      points
      (let [step (/ n max-points)]
        (vec (for [i (range max-points)]
               (nth points (int (* i step)))))))))

(defn- simplify-contour
  "Reduce points using Ramer-Douglas-Peucker algorithm.
   Pre-samples very long contours to avoid O(n²) worst case."
  [points epsilon]
  ;; Pre-sample if contour is very long (> 2000 points)
  (let [points (if (> (count points) 2000)
                 (sample-contour points 2000)
                 points)]
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
          [start end])))))

;; =============================================================================
;; Hole Detection

(defn- collect-hole-candidates
  "Find all unfilled pixels that are adjacent to filled pixels.
   Returns a js/Set of pixel keys. O(filled pixels)."
  [filled-set w h]
  (let [candidates (js/Set.)]
    (.forEach filled-set
              (fn [k]
                (let [x (mod k w)
                      y (quot k w)]
                  ;; Check 4 neighbors
                  (when (and (> x 0) (not (.has filled-set (dec k))))
                    (.add candidates (dec k)))
                  (when (and (< x (dec w)) (not (.has filled-set (inc k))))
                    (.add candidates (inc k)))
                  (when (and (> y 0) (not (.has filled-set (- k w))))
                    (.add candidates (- k w)))
                  (when (and (< y (dec h)) (not (.has filled-set (+ k w))))
                    (.add candidates (+ k w))))))
    candidates))

(defn- point-in-contour?
  "Check if point [x y] is inside a contour using ray casting."
  [[px py] contour]
  (let [n (count contour)]
    (loop [i 0
           j (dec n)
           inside? false]
      (if (>= i n)
        inside?
        (let [[xi yi] (nth contour i)
              [xj yj] (nth contour j)
              intersects? (and (not= (> yi py) (> yj py))
                               (< px (+ (/ (* (- xj xi) (- py yi))
                                           (- yj yi))
                                        xi)))]
          (recur (inc i) i (if intersects? (not inside?) inside?)))))))

(defn- flood-fill-component
  "Flood fill from a boundary candidate into ALL unfilled pixels.
   Expands into entire hole region, not just boundary.
   Returns a js/Set of pixel keys, or nil if region is too large (exterior)."
  [candidates filled-set start-k w h max-size]
  (let [component (js/Set.)
        stack #js [start-k]]
    (loop []
      (cond
        ;; Too large - probably the exterior, abort
        (> (.-size component) max-size)
        nil

        ;; Stack empty - done
        (zero? (.-length stack))
        component

        :else
        (let [k (.pop stack)]
          (if (or (.has component k)
                  (.has filled-set k)  ; Stop at filled region boundary
                  (neg? k)
                  (>= k (* w h)))
            (recur)
            (let [x (mod k w)
                  y (quot k w)]
              (if (or (neg? x) (>= x w) (neg? y) (>= y h))
                (recur)
                (do
                  (.add component k)
                  (.delete candidates k)  ; Remove from candidates if present
                  (when (> x 0) (.push stack (dec k)))
                  (when (< x (dec w)) (.push stack (inc k)))
                  (when (> y 0) (.push stack (- k w)))
                  (when (< y (dec h)) (.push stack (+ k w)))
                  (recur))))))))))

(defn- find-hole-start
  "Find the topmost-leftmost pixel for tracing a hole boundary."
  [component w]
  (let [result (atom nil)]
    (.forEach component
              (fn [k]
                (let [x (mod k w)
                      y (quot k w)]
                  (when (or (nil? @result)
                            (< y (second @result))
                            (and (= y (second @result)) (< x (first @result))))
                    (reset! result [x y])))))
    @result))

(defn- group-candidates-into-components
  "Group boundary candidates into connected components.
   Returns a vector of js/Sets, each containing pixel keys for one component."
  [candidates w h]
  (let [remaining (js/Set.)
        _ (.forEach candidates (fn [k] (.add remaining k)))
        components (atom [])]
    (loop []
      (if (zero? (.-size remaining))
        @components
        (let [iter (.values remaining)
              start-k (.-value (.next iter))
              component (js/Set.)
              stack #js [start-k]]
          ;; Flood fill within candidates only
          (loop []
            (when (pos? (.-length stack))
              (let [k (.pop stack)]
                (when (and (.has remaining k) (not (.has component k)))
                  (.add component k)
                  (.delete remaining k)
                  (let [x (mod k w)
                        y (quot k w)]
                    (when (> x 0) (.push stack (dec k)))
                    (when (< x (dec w)) (.push stack (inc k)))
                    (when (> y 0) (.push stack (- k w)))
                    (when (< y (dec h)) (.push stack (+ k w)))))
                (recur))))
          (swap! components conj component)
          (recur))))))

(defn- mark-exterior-from-corners
  "Flood fill from image corners to mark exterior candidates.
   Returns a js/Set of candidate keys that are reachable from corners."
  [candidates filled-set w h]
  (let [num-candidates (.-size candidates)
        exterior (js/Set.)
        visited (js/Set.)
        stack #js [0 (dec w) (* (dec h) w) (+ (dec w) (* (dec h) w))]
        max-pixels (min 500000 (quot (* w h) 2))]
    (loop [n 0]
      ;; Early exit if we've marked all candidates as exterior
      (if (or (zero? (.-length stack))
              (>= n max-pixels)
              (>= (.-size exterior) num-candidates))
        exterior
        (let [k (.pop stack)]
          (if (or (.has visited k) (.has filled-set k))
            (recur n)
            (let [x (mod k w)
                  y (quot k w)]
              (if (or (neg? x) (>= x w) (neg? y) (>= y h))
                (recur n)
                (do
                  (.add visited k)
                  (when (.has candidates k)
                    (.add exterior k))
                  (.push stack (dec k))
                  (.push stack (inc k))
                  (.push stack (- k w))
                  (.push stack (+ k w))
                  (recur (inc n)))))))))))

(defn- point-in-polygon?
  "Check if a point is inside a polygon using ray casting."
  [[px py] polygon]
  (let [n (count polygon)]
    (loop [i 0
           j (dec n)
           inside? false]
      (if (>= i n)
        inside?
        (let [[xi yi] (nth polygon i)
              [xj yj] (nth polygon j)
              intersect? (and (not= (> yi py) (> yj py))
                              (< px (+ (/ (* (- xj xi) (- py yi)) (- yj yi)) xi)))]
          (recur (inc i) i (if intersect? (not inside?) inside?)))))))

(defn- hole-contains-hole?
  "Check if hole-a contains hole-b (by checking if hole-b's first point is inside hole-a)."
  [hole-a hole-b]
  (point-in-polygon? (first hole-b) hole-a))

(defn- filter-nested-holes
  "Remove holes that are nested inside other holes.
   With evenodd fill, nested holes get filled back in, so we only keep top-level holes."
  [holes]
  (if (<= (count holes) 1)
    holes
    (filterv (fn [hole]
               ;; Keep this hole only if it's not inside any other hole
               (not-any? (fn [other-hole]
                           (and (not= hole other-hole)
                                (hole-contains-hole? other-hole hole)))
                         holes))
             holes)))

(defn- detect-holes
  "Detect holes in the filled region by finding enclosed unfilled areas.
   Returns a vector of hole contours in image coordinates."
  [filled-set _outer-contour w h]
  (let [candidates (collect-hole-candidates filled-set w h)
        ;; Mark candidates reachable from corners (exterior)
        exterior-candidates (mark-exterior-from-corners candidates filled-set w h)
        ;; Filter to interior candidates only
        interior-candidates (js/Set.)
        _ (.forEach candidates
                    (fn [k]
                      (when-not (.has exterior-candidates k)
                        (.add interior-candidates k))))
        ;; Group interior candidates into components
        components (group-candidates-into-components interior-candidates w h)]
    ;; Expand and trace each interior component
    ;; Only keep holes with significant area (at least 500 pixels = ~22x22 px)
    ;; Deduplicate by first point to avoid duplicate holes
    (->> components
         (keep (fn [component]
                 (when (> (.-size component) 20)  ; Skip tiny boundary components
                   (let [first-k (.-value (.next (.values component)))
                         ;; Expand hole region
                         expanded (flood-fill-component (js/Set.) filled-set first-k w h 200000)]
                     (when (and expanded (> (.-size expanded) 500))  ; Minimum 500 pixels
                       (when-let [start (find-hole-start expanded w)]
                         (let [contour (trace-contour expanded w h start)
                               simplified (simplify-contour (vec contour) 2.0)]
                           (when (>= (count simplified) 3)
                             simplified))))))))
         ;; Deduplicate holes by their first point (identical holes have same first point)
         (reduce (fn [acc hole]
                   (let [key (first hole)]
                     (if (contains? (set (map first acc)) key)
                       acc  ; Skip duplicate
                       (conj acc hole))))
                 [])
         ;; Filter out nested holes (holes inside other holes cause evenodd to fill them back)
         filter-nested-holes
         vec)))

(defn- mask-to-outline
  "Convert a filled mask to an outline polygon using Moore-Neighbor tracing.
   Detects holes (enclosed unfilled regions) automatically.
   Returns {:outer [[x y]...] :holes [[[x y]...]...]} in image coordinates."
  [filled-set w h]
  (when-let [start (find-start-pixel filled-set w h)]
    (let [contour (trace-contour filled-set w h start)
          simplified (simplify-contour (vec contour) 2.0)]
      (when (>= (count simplified) 3)
        (let [holes (detect-holes filled-set simplified w h)]
          {:outer simplified :holes holes})))))

(defn- image->canvas-coords
  "Convert image coordinates to canvas coordinates.
   Position is the image CENTER in canvas coordinates."
  [img-points ref-img]
  (let [{:keys [image position bar-meters]} ref-img
        [center-x center-y] (or position [0 0])
        bar-px 150
        scale (/ (* (or bar-meters 50) 100) bar-px)
        ;; Calculate top-left from center
        img-w (.-width image)
        img-h (.-height image)
        top-left-x (- center-x (/ (* img-w scale) 2))
        top-left-y (- center-y (/ (* img-h scale) 2))]
    (mapv (fn [[ix iy]]
            [(+ top-left-x (* ix scale))
             (+ top-left-y (* iy scale))])
          img-points)))

;; =============================================================================
;; Fill Execution

(defn- do-fill-work!
  "Perform the actual fill work. Called via setTimeout to allow UI updates."
  [canvas-point area-type ref-img]
  (try
    (let [img (:image ref-img)
          [img-x img-y] (canvas->image-coords canvas-point ref-img)
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
              areas (state/areas)
              has-areas? (and respect-existing? (seq areas))
              exclusion-mask (when has-areas?
                               (create-exclusion-mask w h ref-img areas))
              filled (flood-fill-mask img img-x img-y tolerance 200000 exclusion-mask)
              contours (mask-to-outline filled w h)]
          (when (and contours (:outer contours) (>= (count (:outer contours)) 3))
            (let [;; Convert outer contour to canvas coordinates
                  canvas-points (image->canvas-coords (:outer contours) ref-img)
                  ;; Convert hole contours to canvas coordinates, reversed for proper winding
                  canvas-holes (when (seq (:holes contours))
                                 (mapv #(vec (reverse (image->canvas-coords % ref-img)))
                                       (:holes contours)))
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
                  area-data (cond-> {:name (str (get type-names area-type "Area") " " (inc (count (state/areas))))
                                     :type area-type
                                     :points canvas-points
                                     :color (get type-colors area-type "#888888")}
                              ;; Only include holes if there are any
                              (seq canvas-holes) (assoc :holes canvas-holes))
                  area-id (state/add-area! area-data)]
              (state/select! :area #{area-id}))))))
    (finally
      ;; Hide loading indicator
      (state/set-state! [:ui :loading?] false)
      (state/set-state! [:ui :loading-message] nil))))

(defn- do-fill!
  "Perform the fill operation at the given canvas point."
  [canvas-point area-type]
  (let [ref-img (state/get-state :ui :reference-image)
        img (:image ref-img)]
    (when (and img (:visible? ref-img))
      ;; Show loading indicator
      (state/set-state! [:ui :loading?] true)
      (state/set-state! [:ui :loading-message] "Detecting areas...")
      ;; Use setTimeout to allow UI to update before heavy work
      (js/setTimeout
       #(do-fill-work! canvas-point area-type ref-img)
       10))))

;; =============================================================================
;; Tool Implementation

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
