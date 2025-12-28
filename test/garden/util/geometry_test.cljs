(ns garden.util.geometry-test
  (:require [cljs.test :refer [deftest testing is]]
            [garden.util.geometry :as geo]))

;; =============================================================================
;; Vector Operations
;; =============================================================================

(deftest scale-test
  (testing "scale multiplies coordinates by factor"
    (is (= [4 6] (geo/scale 2 [2 3])))
    (is (= [0 0] (geo/scale 0 [5 10])))
    (is (= [-3 -6] (geo/scale 3 [-1 -2]))))

  (testing "scale with decimal factor"
    (is (= [2.5 5.0] (geo/scale 0.5 [5 10])))))

(deftest translate-test
  (testing "translate adds offset to point"
    (is (= [5 7] (geo/translate [3 4] [2 3])))
    (is (= [0 0] (geo/translate [-5 -5] [5 5]))))

  (testing "translate with negative offset"
    (is (= [-2 -2] (geo/translate [-4 -4] [2 2])))))

(deftest dot-product-test
  (testing "dot product of perpendicular vectors is zero"
    (is (= 0 (geo/dot-product [1 0] [0 1])))
    (is (= 0 (geo/dot-product [3 4] [-4 3]))))

  (testing "dot product of parallel vectors"
    (is (= 25 (geo/dot-product [3 4] [3 4])))
    (is (= 14 (geo/dot-product [1 2] [4 5])))))

(deftest points-distance-test
  (testing "distance between same point is zero"
    (is (= 0.0 (geo/points-distance [5 5] [5 5]))))

  (testing "distance of 3-4-5 triangle"
    (is (= 5.0 (geo/points-distance [0 0] [3 4]))))

  (testing "distance is symmetric"
    (is (= (geo/points-distance [1 2] [4 6])
           (geo/points-distance [4 6] [1 2])))))

;; =============================================================================
;; Line Operations
;; =============================================================================

(deftest linear-equation-test
  (testing "returns nil for vertical line"
    (is (nil? (geo/linear-equation [5 0] [5 10]))))

  (testing "creates correct equation for horizontal line"
    (let [eq (geo/linear-equation [0 5] [10 5])]
      (is (= 5.0 (eq 0)))
      (is (= 5.0 (eq 5)))
      (is (= 5.0 (eq 100)))))

  (testing "creates correct equation for diagonal"
    (let [eq (geo/linear-equation [0 0] [10 10])]
      (is (= 5.0 (eq 5)))
      (is (= 10.0 (eq 10))))))

(deftest distance-from-segment-test
  (testing "distance to point on segment is zero"
    (is (< (geo/distance-from-segment [0 0] [10 0] [5 0]) 0.001)))

  (testing "perpendicular distance to segment"
    (is (= 5.0 (geo/distance-from-segment [0 0] [10 0] [5 5]))))

  (testing "distance to endpoint when projection is outside segment"
    (is (= 5.0 (geo/distance-from-segment [0 0] [10 0] [-3 4])))))

(deftest height-on-line-test
  (testing "y on horizontal line"
    (is (= 5.0 (geo/height-on-line [0 5] [10 5] 3))))

  (testing "y on diagonal line y=x"
    (is (= 5.0 (geo/height-on-line [0 0] [10 10] 5))))

  (testing "y on steep line"
    (is (= 10.0 (geo/height-on-line [0 0] [5 20] 2.5)))))

(deftest on-contour-test
  (let [square [[0 0] [10 0] [10 10] [0 10]]]
    (testing "point near edge returns segment"
      (let [result (geo/on-contour square [5 1] 3)]
        (is (some? result))
        (is (= [[0 0] [10 0]] result))))

    (testing "point far from contour returns nil"
      (is (nil? (geo/on-contour square [5 5] 1))))))

;; =============================================================================
;; Polygon Operations
;; =============================================================================

(deftest point-in-polygon-test
  (let [square [[0 0] [10 0] [10 10] [0 10]]]
    (testing "point inside polygon"
      (is (true? (geo/point-in-polygon? square [5 5])))
      (is (true? (geo/point-in-polygon? square [1 1]))))

    (testing "point outside polygon"
      (is (false? (geo/point-in-polygon? square [15 5])))
      (is (false? (geo/point-in-polygon? square [-1 -1])))))

  ;; L-shaped polygon to test concave shapes
  (let [l-shape [[0 0] [5 0] [5 5] [10 5] [10 10] [0 10]]]
    (testing "point in L-shape"
      (is (geo/point-in-polygon? l-shape [2 5])))

    (testing "point outside L-shape (in the notch)"
      (is (not (geo/point-in-polygon? l-shape [7 2]))))))

(deftest point-in-polygon-with-holes-test
  (let [outer [[0 0] [100 0] [100 100] [0 100]]
        hole [[25 25] [75 25] [75 75] [25 75]]]
    (testing "point inside outer but outside hole"
      (is (true? (geo/point-in-polygon-with-holes? outer [hole] [10 10])))
      (is (true? (geo/point-in-polygon-with-holes? outer [hole] [90 90]))))

    (testing "point inside hole returns false"
      (is (false? (geo/point-in-polygon-with-holes? outer [hole] [50 50])))
      (is (false? (geo/point-in-polygon-with-holes? outer [hole] [30 30]))))

    (testing "point outside outer returns false"
      (is (false? (geo/point-in-polygon-with-holes? outer [hole] [150 50])))
      (is (false? (geo/point-in-polygon-with-holes? outer [hole] [-10 -10])))))

  (testing "multiple holes"
    (let [outer [[0 0] [200 0] [200 100] [0 100]]
          hole1 [[10 10] [50 10] [50 50] [10 50]]
          hole2 [[60 10] [100 10] [100 50] [60 50]]]
      (is (true? (geo/point-in-polygon-with-holes? outer [hole1 hole2] [150 25])))
      (is (false? (geo/point-in-polygon-with-holes? outer [hole1 hole2] [30 30])))
      (is (false? (geo/point-in-polygon-with-holes? outer [hole1 hole2] [80 30])))))

  (testing "no holes"
    (let [square [[0 0] [10 0] [10 10] [0 10]]]
      (is (true? (geo/point-in-polygon-with-holes? square [] [5 5])))
      (is (false? (geo/point-in-polygon-with-holes? square [] [15 5]))))))

(deftest polygon-centroid-test
  (testing "centroid of square"
    (let [square [[0 0] [10 0] [10 10] [0 10]]]
      (is (= [5 5] (geo/polygon-centroid square)))))

  (testing "centroid of triangle"
    (let [triangle [[0 0] [6 0] [3 6]]]
      (is (= [3 2] (geo/polygon-centroid triangle)))))

  (testing "centroid of empty polygon"
    (is (= [0 0] (geo/polygon-centroid [])))))

(deftest bounding-box-test
  (testing "bounding box of polygon"
    (let [points [[1 2] [5 1] [3 8] [0 4]]]
      (is (= {:min [0 1] :max [5 8]} (geo/bounding-box points)))))

  (testing "bounding box of single point"
    (is (= {:min [5 5] :max [5 5]} (geo/bounding-box [[5 5]]))))

  (testing "bounding box of empty list"
    (is (nil? (geo/bounding-box [])))))

;; =============================================================================
;; Grid Snapping
;; =============================================================================

(deftest snap-to-grid-test
  (testing "snap to nearest grid point"
    (is (= [50 50] (geo/snap-to-grid [48 52] 50)))
    (is (= [100 100] (geo/snap-to-grid [76 124] 50))))

  (testing "exact grid point stays unchanged"
    (is (= [100 200] (geo/snap-to-grid [100 200] 50))))

  (testing "snap with different grid spacing"
    (is (= [20 20] (geo/snap-to-grid [18 22] 10)))))
