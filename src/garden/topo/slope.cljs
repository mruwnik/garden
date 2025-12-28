(ns garden.topo.slope
  "Slope and aspect calculations from elevation data."
  (:require [garden.topo.core :as topo]
            [garden.state :as state]))

(defn calculate-slope
  "Calculate slope angle in degrees at garden coordinates [x y].
   Slope is the steepness of the terrain (0 = flat, 90 = vertical).
   Returns nil if elevation data not available."
  [[x y]]
  (when-let [{:keys [dzdx dzdy]} (topo/elevation-gradient-at [x y])]
    ;; slope = arctan(sqrt(dzdx^2 + dzdy^2))
    ;; Note: gradients are in m/m (dimensionless)
    (let [gradient-magnitude (Math/sqrt (+ (* dzdx dzdx) (* dzdy dzdy)))
          slope-rad (Math/atan gradient-magnitude)]
      (* slope-rad (/ 180 js/Math.PI)))))

(defn calculate-aspect
  "Calculate aspect (compass direction the slope faces) in degrees at [x y].
   0/360 = North, 90 = East, 180 = South, 270 = West.
   Returns nil if elevation data not available or terrain is flat."
  [[x y]]
  (when-let [{:keys [dzdx dzdy]} (topo/elevation-gradient-at [x y])]
    ;; aspect = atan2(dzdy, -dzdx) + 180
    ;; This gives the direction the slope faces (downhill direction)
    (when (or (not= dzdx 0) (not= dzdy 0))
      (let [aspect-rad (Math/atan2 dzdy (- dzdx))
            aspect-deg (+ (* aspect-rad (/ 180 js/Math.PI)) 180)]
        (mod aspect-deg 360)))))

(defn aspect-to-label
  "Convert aspect degrees to compass direction label."
  [aspect-deg]
  (when aspect-deg
    (let [directions ["N" "NE" "E" "SE" "S" "SW" "W" "NW"]
          ;; Each direction covers 45 degrees, centered
          ;; N is 337.5-22.5, NE is 22.5-67.5, etc.
          adjusted (mod (+ aspect-deg 22.5) 360)
          idx (int (/ adjusted 45))]
      (nth directions (mod idx 8)))))

(defn aspect-to-full-label
  "Convert aspect degrees to full direction name."
  [aspect-deg]
  (when aspect-deg
    (let [directions ["North" "Northeast" "East" "Southeast"
                      "South" "Southwest" "West" "Northwest"]
          adjusted (mod (+ aspect-deg 22.5) 360)
          idx (int (/ adjusted 45))]
      (nth directions (mod idx 8)))))

(defn slope-category
  "Categorize slope angle into descriptive category."
  [slope-deg]
  (cond
    (nil? slope-deg) nil
    (< slope-deg 2)  :flat
    (< slope-deg 5)  :gentle
    (< slope-deg 10) :moderate
    (< slope-deg 15) :steep
    (< slope-deg 30) :very-steep
    :else            :extreme))

(defn slope-category-label
  "Get human-readable label for slope category."
  [category]
  (case category
    :flat       "Flat (<2°)"
    :gentle     "Gentle (2-5°)"
    :moderate   "Moderate (5-10°)"
    :steep      "Steep (10-15°)"
    :very-steep "Very Steep (15-30°)"
    :extreme    "Extreme (>30°)"
    "Unknown"))

(defn calculate-slope-aspect
  "Calculate both slope and aspect at [x y].
   Returns {:slope degrees :aspect degrees :aspect-label \"N\"/\"NE\"/etc}
   or nil if no data."
  [[x y]]
  (let [slope (calculate-slope [x y])
        aspect (calculate-aspect [x y])]
    (when slope
      {:slope slope
       :aspect aspect
       :aspect-label (aspect-to-label aspect)
       :slope-category (slope-category slope)})))

;; Polygon analysis functions

(defn- point-in-polygon?
  "Check if point [px py] is inside polygon defined by points.
   Uses ray casting algorithm."
  [[px py] points]
  (let [n (count points)]
    (loop [i 0
           j (dec n)
           inside? false]
      (if (>= i n)
        inside?
        (let [[xi yi] (nth points i)
              [xj yj] (nth points j)
              intersects? (and (not= (> yi py) (> yj py))
                               (< px (+ xj (* (/ (- yj yi) 1)
                                              (- (/ (- py yi) (- yj yi))
                                                 (- xj xi))))))]
          (recur (inc i) i (if intersects? (not inside?) inside?)))))))

(defn sample-polygon-grid
  "Generate a grid of sample points within a polygon.
   Returns seq of [x y] points."
  [points spacing]
  (when (seq points)
    (let [xs (map first points)
          ys (map second points)
          min-x (apply min xs)
          max-x (apply max xs)
          min-y (apply min ys)
          max-y (apply max ys)]
      ;; Generate grid points and filter to those inside polygon
      (for [x (range min-x max-x spacing)
            y (range min-y max-y spacing)
            :when (point-in-polygon? [x y] points)]
        [x y]))))

(defn analyze-area-topography
  "Analyze topography for an area polygon.
   Returns {:elevation {:min :max :avg}
            :slope {:min :max :avg :category}
            :aspect {:dominant :label}}
   or nil if no topo data."
  [points]
  (when (and (seq points) (state/topo-elevation-data))
    (let [resolution (or (state/topo-resolution) 100) ; cm
          sample-spacing (* resolution 2) ; Sample every 2 grid cells
          sample-points (sample-polygon-grid points sample-spacing)]
      (when (seq sample-points)
        (let [;; Gather elevation samples
              elevations (keep #(topo/get-elevation-at %) sample-points)
              ;; Gather slope/aspect samples
              slope-aspects (keep #(calculate-slope-aspect %) sample-points)
              slopes (map :slope slope-aspects)
              aspects (keep :aspect slope-aspects)]
          (when (seq elevations)
            {:elevation {:min (apply min elevations)
                         :max (apply max elevations)
                         :avg (/ (reduce + elevations) (count elevations))}
             :slope (when (seq slopes)
                      (let [avg-slope (/ (reduce + slopes) (count slopes))]
                        {:min (apply min slopes)
                         :max (apply max slopes)
                         :avg avg-slope
                         :category (slope-category avg-slope)}))
             :aspect (when (seq aspects)
                       ;; Calculate circular mean for aspect
                       (let [sin-sum (reduce + (map #(Math/sin (* % (/ js/Math.PI 180))) aspects))
                             cos-sum (reduce + (map #(Math/cos (* % (/ js/Math.PI 180))) aspects))
                             avg-aspect (mod (* (Math/atan2 sin-sum cos-sum)
                                                (/ 180 js/Math.PI))
                                             360)]
                         {:dominant avg-aspect
                          :label (aspect-to-label avg-aspect)
                          :full-label (aspect-to-full-label avg-aspect)}))}))))))
