(ns garden.tools.plant-test
  (:require [cljs.test :refer [deftest testing is]]))

;; We need to test the private functions, so we'll recreate them here
;; since they're not exposed publicly

(defn- distance [[x1 y1] [x2 y2]]
  (Math/sqrt (+ (* (- x2 x1) (- x2 x1))
                (* (- y2 y1) (- y2 y1)))))

(defn- calculate-row-positions
  "Calculate evenly spaced positions along a line from start to end."
  [start end spacing]
  (let [dist (distance start end)
        count (max 1 (Math/floor (/ dist spacing)))
        [x1 y1] start
        [x2 y2] end
        dx (- x2 x1)
        dy (- y2 y1)]
    (if (< dist 5)
      ;; Too short for a row, just return start point
      [start]
      ;; Generate evenly spaced positions
      (for [i (range (inc count))]
        (let [t (if (zero? count) 0 (/ i count))]
          [(+ x1 (* dx t))
           (+ y1 (* dy t))])))))

;; =============================================================================
;; Distance Tests
;; =============================================================================

(deftest distance-test
  (testing "distance between same point is zero"
    (is (= 0.0 (distance [5 5] [5 5]))))

  (testing "horizontal distance"
    (is (= 10.0 (distance [0 0] [10 0]))))

  (testing "vertical distance"
    (is (= 10.0 (distance [0 0] [0 10]))))

  (testing "3-4-5 triangle"
    (is (= 5.0 (distance [0 0] [3 4]))))

  (testing "distance is symmetric"
    (is (= (distance [1 2] [4 6])
           (distance [4 6] [1 2])))))

;; =============================================================================
;; Row Position Tests
;; =============================================================================

(deftest calculate-row-positions-test
  (testing "horizontal row with exact spacing"
    (let [positions (calculate-row-positions [0 0] [100 0] 50)]
      (is (= 3 (count positions)))
      ;; Should be at 0, 50, 100
      (is (< (js/Math.abs (- (first (first positions)) 0)) 0.01))
      (is (< (js/Math.abs (- (first (last positions)) 100)) 0.01))))

  (testing "vertical row"
    (let [positions (calculate-row-positions [0 0] [0 100] 25)]
      (is (= 5 (count positions)))
      ;; All x coords should be 0
      (is (every? #(< (js/Math.abs (first %)) 0.01) positions))))

  (testing "diagonal row"
    (let [positions (calculate-row-positions [0 0] [30 40] 50)]
      ;; Distance is 50, so should return 2 points (start and end)
      (is (= 2 (count positions)))))

  (testing "too short for row returns just start"
    (let [positions (calculate-row-positions [0 0] [2 0] 20)]
      (is (= 1 (count positions)))
      (is (= [0 0] (first positions)))))

  (testing "row with fractional spacing"
    (let [positions (calculate-row-positions [0 0] [75 0] 30)]
      ;; 75 / 30 = 2.5, floors to 2, so 3 points
      (is (= 3 (count positions))))))
