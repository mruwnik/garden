(ns garden.util.geometry)

;; Vector operations

(defn scale
  "Scale a point by factor s."
  [s [x y]]
  [(* s x) (* s y)])

(defn translate
  "Translate a point by offset."
  [[x-off y-off] [x y]]
  [(+ x-off x) (+ y-off y)])

(defn dot-product
  "Calculate the dot product of two vectors."
  [[x1 y1] [x2 y2]]
  (+ (* x1 x2) (* y1 y2)))

(defn points-distance
  "Return the Euclidean distance between two points."
  [[x1 y1] [x2 y2]]
  (Math/sqrt (+ (Math/pow (- x1 x2) 2) (Math/pow (- y1 y2) 2))))

;; Line operations

(defn linear-equation
  "Return a linear equation function going through two points."
  [[x1 y1] [x2 y2]]
  (when (not= x1 x2)
    (let [a (/ (- y2 y1) (- x2 x1))
          b (- y1 (* a x1))]
      (fn [x] (+ (* a x) b)))))

(defn height-on-line
  "Return the y-coordinate on the line at x-position xp."
  [[x1 y1] [x2 y2] xp]
  (+ y1 (/ (* (- xp x1) (- y2 y1)) (- x2 x1))))

(defn distance-from-segment
  "Return the distance from point p to line segment p1-p2."
  [[x1 y1] [x2 y2] [xp yp]]
  (let [v [(- x2 x1) (- y2 y1)]
        w [(- xp x1) (- yp y1)]
        c1 (dot-product w v)
        c2 (dot-product v v)
        [vx vy] v
        b (when c2 (/ c1 c2))]
    (cond
      (< c1 0) (points-distance [xp yp] [x1 y1])
      (<= c2 c1) (points-distance [xp yp] [x2 y2])
      :else (points-distance [xp yp] [(+ x1 (* b vx)) (+ y1 (* b vy))]))))

;; Polygon operations

(defn- winding-score
  "Return the winding number contribution for a line segment."
  [[x1 y1] [x2 y2] [xp yp]]
  (cond
    (and (< yp y1) (< yp y2)) 0
    (and (< xp x1) (< xp x2)) 0
    (and (> xp x1) (> xp x2)) 0
    (and (= x1 xp) (= x2 xp)) 0
    (or (= x1 xp) (= x2 xp)) (if (< x1 x2) 0.5 -0.5)
    (> x1 x2) (- (winding-score [x2 y2] [x1 y1] [xp yp]))
    (> y1 y2) (winding-score [(- (* 2 xp) x2) y2] [(- (* 2 xp) x1) y1] [xp yp])
    :else (if (>= yp (height-on-line [x1 y1] [x2 y2] xp)) 1 0)))

(defn point-in-polygon?
  "Return true if point is inside the polygon (using non-zero winding rule)."
  [polygon point]
  (not= 0
        (loop [checked (first polygon)
               points (conj (vec (rest polygon)) (first polygon))
               sum 0]
          (if-not (seq points)
            sum
            (recur (first points)
                   (rest points)
                   (+ sum (winding-score checked (first points) point)))))))

(defn point-in-polygon-with-holes?
  "Return true if point is inside the polygon but not inside any of the holes."
  [polygon holes point]
  (and (point-in-polygon? polygon point)
       (not-any? #(point-in-polygon? % point) holes)))

(defn on-contour
  "Return the first segment within max-dist of point, or nil."
  [polygon point max-dist]
  (loop [checked (first polygon)
         points (conj (vec (rest polygon)) (first polygon))]
    (cond
      (not (seq points)) nil
      (< (distance-from-segment checked (first points) point) max-dist)
      [checked (first points)]
      :else (recur (first points) (rest points)))))

(defn polygon-centroid
  "Calculate the centroid of a polygon."
  [points]
  (let [n (count points)]
    (if (zero? n)
      [0 0]
      [(/ (reduce + (map first points)) n)
       (/ (reduce + (map second points)) n)])))

(defn bounding-box
  "Return the bounding box of points as {:min [x y] :max [x y]}."
  [points]
  (when (seq points)
    (let [xs (map first points)
          ys (map second points)]
      {:min [(apply min xs) (apply min ys)]
       :max [(apply max xs) (apply max ys)]})))

;; Grid snapping

(defn snap-to-grid
  "Snap a point to the nearest grid intersection."
  [[x y] grid-spacing]
  [(* grid-spacing (Math/round (/ x grid-spacing)))
   (* grid-spacing (Math/round (/ y grid-spacing)))])
