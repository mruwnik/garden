(ns garden.tools.fill-test
  (:require [cljs.test :refer [deftest testing is]]))

;; Recreate the pure private functions for testing

(defn- colors-similar?
  "Check if two colors are within tolerance."
  [[r1 g1 b1 _a1] [r2 g2 b2 _a2] tolerance]
  (and (<= (js/Math.abs (- r1 r2)) tolerance)
       (<= (js/Math.abs (- g1 g2)) tolerance)
       (<= (js/Math.abs (- b1 b2)) tolerance)))

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

;; =============================================================================
;; Colors Similar Tests
;; =============================================================================

(deftest colors-similar-test
  (testing "identical colors are similar"
    (is (true? (colors-similar? [100 150 200 255] [100 150 200 255] 0))))

  (testing "colors within tolerance are similar"
    (is (true? (colors-similar? [100 150 200 255] [105 145 195 255] 10))))

  (testing "colors outside tolerance are not similar"
    (is (false? (colors-similar? [100 150 200 255] [120 150 200 255] 10))))

  (testing "alpha is ignored"
    (is (true? (colors-similar? [100 150 200 0] [100 150 200 255] 0))))

  (testing "works with zero tolerance"
    (is (true? (colors-similar? [0 0 0 0] [0 0 0 0] 0)))
    (is (false? (colors-similar? [0 0 0 0] [1 0 0 0] 0))))

  (testing "boundary cases"
    (is (true? (colors-similar? [100 100 100 255] [110 100 100 255] 10)))
    (is (false? (colors-similar? [100 100 100 255] [111 100 100 255] 10)))))

;; =============================================================================
;; Simplify Contour (RDP) Tests
;; =============================================================================

(deftest simplify-contour-test
  (testing "returns empty/single/two points unchanged"
    (is (= [] (simplify-contour [] 5)))
    (is (= [[0 0]] (simplify-contour [[0 0]] 5)))
    (is (= [[0 0] [10 10]] (simplify-contour [[0 0] [10 10]] 5))))

  (testing "removes colinear points"
    (let [points [[0 0] [5 0] [10 0]]  ; All on horizontal line
          simplified (simplify-contour points 1)]
      ;; Middle point is on the line, distance = 0, should be removed
      (is (= 2 (count simplified)))
      (is (= [[0 0] [10 0]] simplified))))

  (testing "preserves points far from line"
    (let [points [[0 0] [5 10] [10 0]]  ; Triangle shape
          simplified (simplify-contour points 5)]
      ;; Middle point is 10 units from line, epsilon=5, should keep it
      (is (= 3 (count simplified)))))

  (testing "epsilon controls simplification level"
    (let [points [[0 0] [5 2] [10 0]]]  ; Slight curve
      ;; With small epsilon, keep the middle point
      (is (= 3 (count (simplify-contour points 1))))
      ;; With large epsilon, remove the middle point
      (is (= 2 (count (simplify-contour points 5))))))

  (testing "handles complex paths"
    (let [points [[0 0] [1 0] [2 0] [3 0] [4 10] [5 0] [6 0] [7 0]]
          simplified (simplify-contour points 2)]
      ;; Should simplify but keep the peak at [4 10]
      (is (<= (count simplified) (count points)))
      (is (some #(= (second %) 10) simplified)))))

;; =============================================================================
;; Coordinate Transform Tests (Pure Math)
;; =============================================================================

;; These test the mathematical correctness of coordinate transforms
;; Note: The actual fill.cljs functions depend on image dimensions,
;; so we test the math formulas directly

(deftest coordinate-transform-math-test
  (let [;; Simulated ref-img params
        center-x 0
        center-y 0
        bar-meters 50
        bar-px 150
        scale (/ (* bar-meters 100) bar-px)  ; = 33.33 cm per pixel
        img-w 300  ; 300 px image
        img-h 200
        ;; Calculate top-left from center
        top-left-x (- center-x (/ (* img-w scale) 2))
        top-left-y (- center-y (/ (* img-h scale) 2))]

    (testing "scale calculation"
      ;; 150 px = 50m = 5000 cm
      ;; scale = 5000 / 150 = 33.33 cm/px
      (is (< (js/Math.abs (- scale 33.333)) 0.01)))

    (testing "top-left from center calculation"
      ;; Image is 300 x 200 px
      ;; Width in cm = 300 * 33.33 = 10000 cm = 100m
      ;; Height in cm = 200 * 33.33 = 6666 cm = 66.66m
      ;; Top-left = center - (size/2) = [0,0] - [5000, 3333] = [-5000, -3333]
      (is (< (js/Math.abs (- top-left-x -5000)) 1))
      (is (< (js/Math.abs (- top-left-y -3333)) 1)))

    (testing "canvas to image coordinate conversion"
      ;; canvas [0, 0] (center) -> image center
      (let [cx 0 cy 0
            img-x (/ (- cx top-left-x) scale)
            img-y (/ (- cy top-left-y) scale)]
        ;; Should be at image center (150, 100)
        (is (< (js/Math.abs (- img-x 150)) 1))
        (is (< (js/Math.abs (- img-y 100)) 1))))

    (testing "image to canvas coordinate conversion"
      ;; image [0, 0] (top-left) -> canvas top-left
      (let [ix 0 iy 0
            cx (+ top-left-x (* ix scale))
            cy (+ top-left-y (* iy scale))]
        ;; Should be at canvas top-left
        (is (< (js/Math.abs (- cx top-left-x)) 0.01))
        (is (< (js/Math.abs (- cy top-left-y)) 0.01))))))
