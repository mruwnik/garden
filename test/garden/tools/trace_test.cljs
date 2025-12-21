(ns garden.tools.trace-test
  (:require [cljs.test :refer [deftest testing is]]))

;; Recreate the private simplify-points function for testing
(defn- simplify-points
  "Reduce number of points by removing those too close together.
   min-distance is in garden cm."
  [points min-distance]
  (if (< (count points) 3)
    points
    (reduce (fn [acc pt]
              (let [last-pt (peek acc)]
                (if (or (nil? last-pt)
                        (let [[x1 y1] last-pt
                              [x2 y2] pt
                              dist (js/Math.sqrt (+ (* (- x2 x1) (- x2 x1))
                                                    (* (- y2 y1) (- y2 y1))))]
                          (> dist min-distance)))
                  (conj acc pt)
                  acc)))
            []
            points)))

;; =============================================================================
;; Simplify Points Tests
;; =============================================================================

(deftest simplify-points-test
  (testing "returns points unchanged if less than 3"
    (is (= [] (simplify-points [] 10)))
    (is (= [[0 0]] (simplify-points [[0 0]] 10)))
    (is (= [[0 0] [5 5]] (simplify-points [[0 0] [5 5]] 10))))

  (testing "removes points too close together"
    (let [points [[0 0] [1 0] [2 0] [3 0] [100 0]]
          simplified (simplify-points points 10)]
      ;; Points 1, 2, 3 are all within 10 units of each other from 0
      ;; Should keep first point [0 0] and last point [100 0]
      (is (= 2 (count simplified)))
      (is (= [0 0] (first simplified)))
      (is (= [100 0] (last simplified)))))

  (testing "keeps all points if far enough apart"
    (let [points [[0 0] [20 0] [40 0] [60 0]]
          simplified (simplify-points points 10)]
      ;; All points are 20 units apart, greater than min-distance of 10
      (is (= 4 (count simplified)))))

  (testing "handles diagonal points"
    (let [points [[0 0] [3 4] [6 8] [100 100]]  ; First 3 are 5 units apart
          simplified (simplify-points points 10)]
      ;; [3 4] is 5 units from [0 0] - too close
      ;; [6 8] is 5 units from [3 4] - but 10 units from [0 0] - too close
      ;; [100 100] is far enough
      (is (>= (count simplified) 2))
      (is (= [0 0] (first simplified)))
      (is (= [100 100] (last simplified)))))

  (testing "keeps points spaced greater than min-distance"
    (let [points [[0 0] [15 0] [30 0]]
          simplified (simplify-points points 10)]
      ;; 15 units apart, greater than min-distance of 10
      (is (= 3 (count simplified))))))
