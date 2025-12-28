(ns garden.topo.core-test
  (:require [cljs.test :refer [deftest testing is use-fixtures]]
            [garden.topo.core :as topo]
            [garden.state :as state]))

;; Reset state before each test
(use-fixtures :each
  {:before (fn [] (reset! state/app-state state/initial-state))
   :after  (fn [] (reset! state/app-state state/initial-state))})

;; =============================================================================
;; Grid Cell Access
;; =============================================================================

(deftest get-cell-test
  (let [;; Create a 3x3 elevation grid
        ;; Values: row 0: [10, 20, 30], row 1: [40, 50, 60], row 2: [70, 80, 90]
        data (js/Float32Array. #js [10 20 30 40 50 60 70 80 90])
        width 3
        height 3]

    (testing "returns correct value for valid cell"
      (is (= 10 (topo/get-cell data width height 0 0)))
      (is (= 20 (topo/get-cell data width height 0 1)))
      (is (= 50 (topo/get-cell data width height 1 1)))
      (is (= 90 (topo/get-cell data width height 2 2))))

    (testing "returns nil for out of bounds"
      (is (nil? (topo/get-cell data width height -1 0)))
      (is (nil? (topo/get-cell data width height 0 -1)))
      (is (nil? (topo/get-cell data width height 3 0)))
      (is (nil? (topo/get-cell data width height 0 3))))

    (testing "returns nil for NaN values"
      (let [data-with-nan (js/Float32Array. #js [10 js/NaN 30])]
        (is (= 10 (topo/get-cell data-with-nan 3 1 0 0)))
        (is (nil? (topo/get-cell data-with-nan 3 1 0 1)))
        (is (= 30 (topo/get-cell data-with-nan 3 1 0 2)))))

    (testing "returns nil when no data"
      (is (nil? (topo/get-cell nil width height 0 0))))))

;; =============================================================================
;; Coordinate Conversion
;; =============================================================================

(deftest garden->grid-test
  (let [bounds {:min-x -100 :min-y -100 :max-x 100 :max-y 100}
        resolution 50]  ; 50 cm per grid cell

    (testing "converts garden coords to grid coords"
      (is (= [0.0 0.0] (topo/garden->grid -100 -100 bounds resolution)))
      (is (= [2.0 2.0] (topo/garden->grid 0 0 bounds resolution)))
      (is (= [4.0 4.0] (topo/garden->grid 100 100 bounds resolution))))

    (testing "handles fractional positions"
      (is (= [1.0 1.5] (topo/garden->grid -25 -50 bounds resolution))))

    (testing "returns nil for invalid inputs"
      (is (nil? (topo/garden->grid 0 0 nil resolution)))
      (is (nil? (topo/garden->grid 0 0 bounds nil)))
      (is (nil? (topo/garden->grid 0 0 bounds 0))))))

(deftest grid->garden-test
  (let [bounds {:min-x -100 :min-y -100 :max-x 100 :max-y 100}
        resolution 50]

    (testing "converts grid coords to garden coords"
      (is (= [-100 -100] (topo/grid->garden 0 0 bounds resolution)))
      (is (= [0 0] (topo/grid->garden 2 2 bounds resolution)))
      (is (= [100 100] (topo/grid->garden 4 4 bounds resolution))))

    (testing "handles fractional grid positions"
      (is (= [-75.0 -50.0] (topo/grid->garden 1 0.5 bounds resolution))))

    (testing "returns nil for invalid inputs"
      (is (nil? (topo/grid->garden 0 0 nil resolution)))
      (is (nil? (topo/grid->garden 0 0 bounds nil))))))

;; =============================================================================
;; Interpolation
;; =============================================================================

(deftest bilinear-interpolate-test
  (let [;; 3x3 grid with known values
        ;; [10 20 30]
        ;; [40 50 60]
        ;; [70 80 90]
        data (js/Float32Array. #js [10 20 30 40 50 60 70 80 90])
        width 3
        height 3]

    (testing "returns exact value at integer positions"
      ;; At integer positions with all 4 neighbors available
      (is (= 10.0 (topo/bilinear-interpolate data width height 0 0)))
      (is (= 50.0 (topo/bilinear-interpolate data width height 1 1)))
      ;; Note: position (2,2) returns nil because it needs neighbors at (3,2) and (2,3)
      ;; which are out of bounds. Bilinear needs all 4 corners.
      (is (nil? (topo/bilinear-interpolate data width height 2 2))))

    (testing "interpolates at midpoints"
      ;; Midpoint between 10,20,40,50 should be (10+20+40+50)/4 = 30
      (is (= 30.0 (topo/bilinear-interpolate data width height 0.5 0.5)))
      ;; Midpoint between 50,60,80,90 should be (50+60+80+90)/4 = 70
      (is (= 70.0 (topo/bilinear-interpolate data width height 1.5 1.5))))

    (testing "returns nil at boundary (missing neighbors)"
      ;; Position 1.5, 2.5 needs cells at row 2, col 3 which is out of bounds
      (is (nil? (topo/bilinear-interpolate data width height 1.5 2.5))))

    (testing "returns nil for nil data"
      (is (nil? (topo/bilinear-interpolate nil width height 0 0))))))

(deftest nearest-neighbor-test
  (let [data (js/Float32Array. #js [10 20 30 40 50 60 70 80 90])
        width 3
        height 3]

    (testing "returns nearest cell value"
      (is (= 10 (topo/nearest-neighbor data width height 0.3 0.3)))
      (is (= 50 (topo/nearest-neighbor data width height 1.4 1.4)))
      (is (= 60 (topo/nearest-neighbor data width height 1.4 1.6))))))

;; =============================================================================
;; Elevation Range Helpers
;; =============================================================================

(deftest normalize-elevation-test
  (testing "normalizes elevation to 0-1 range"
    (state/set-topo-data! {:min-elevation 0
                           :max-elevation 100
                           :elevation-data (js/Float32Array. 1)
                           :width 1 :height 1
                           :bounds {:min-x 0 :min-y 0 :max-x 1 :max-y 1}
                           :resolution 1})
    (is (= 0.0 (topo/normalize-elevation 0)))
    (is (= 0.5 (topo/normalize-elevation 50)))
    (is (= 1.0 (topo/normalize-elevation 100))))

  (testing "handles zero range"
    (state/set-topo-data! {:min-elevation 50
                           :max-elevation 50
                           :elevation-data (js/Float32Array. 1)
                           :width 1 :height 1
                           :bounds {:min-x 0 :min-y 0 :max-x 1 :max-y 1}
                           :resolution 1})
    (is (= 0.5 (topo/normalize-elevation 50)))))

;; =============================================================================
;; State-integrated functions
;; =============================================================================

(deftest get-elevation-at-test
  (testing "returns interpolated elevation from state"
    (let [;; 2x2 grid: [100, 200], [300, 400]
          data (js/Float32Array. #js [100 200 300 400])]
      (state/set-topo-data! {:elevation-data data
                             :width 2
                             :height 2
                             :bounds {:min-x 0 :min-y 0 :max-x 100 :max-y 100}
                             :resolution 100
                             :min-elevation 100
                             :max-elevation 400})
      ;; At (0, 0) should get cell (0, 0) = 100
      (is (= 100.0 (topo/get-elevation-at [0 0])))
      ;; At (50, 50) should interpolate middle = (100+200+300+400)/4 = 250
      (is (= 250.0 (topo/get-elevation-at [50 50]))))))

(deftest in-bounds?-test
  (testing "checks if point is within topo bounds"
    (state/set-topo-data! {:bounds {:min-x -100 :min-y -100 :max-x 100 :max-y 100}
                           :elevation-data (js/Float32Array. 1)
                           :width 1 :height 1
                           :resolution 1})
    (is (true? (topo/in-bounds? [0 0])))
    (is (true? (topo/in-bounds? [-100 -100])))
    (is (true? (topo/in-bounds? [100 100])))
    (is (false? (topo/in-bounds? [-101 0])))
    (is (false? (topo/in-bounds? [0 101])))))

(deftest grid-dimensions-test
  (testing "returns grid dimensions from state"
    (state/set-topo-data! {:width 100
                           :height 200
                           :elevation-data (js/Float32Array. 1)
                           :bounds {:min-x 0 :min-y 0 :max-x 1 :max-y 1}
                           :resolution 1})
    (is (= [200 100] (topo/grid-dimensions)))))

;; =============================================================================
;; Manual Point Interpolation (IDW)
;; =============================================================================

(deftest interpolate-from-points-test
  (testing "returns exact elevation at point location"
    (state/add-topo-point! {:position [100 100] :elevation 50})
    (is (= 50 (topo/interpolate-from-points [100 100]))))

  (testing "interpolates using inverse distance weighting"
    (reset! state/app-state state/initial-state)
    ;; Two points at equal distance should average
    (state/add-topo-point! {:position [0 0] :elevation 100})
    (state/add-topo-point! {:position [200 0] :elevation 200})
    ;; At midpoint (100, 0), both are equidistant -> average = 150
    (is (= 150.0 (topo/interpolate-from-points [100 0]))))

  (testing "returns nil when no points"
    (reset! state/app-state state/initial-state)
    (is (nil? (topo/interpolate-from-points [0 0])))))

;; =============================================================================
;; Geographic Coordinate Conversion
;; =============================================================================

(deftest has-geo-info?-test
  (testing "returns false when no geo info"
    (reset! state/app-state state/initial-state)
    (is (false? (topo/has-geo-info?))))

  (testing "returns true when geo info present"
    (state/set-topo-data! {:geo-info {:bbox [0 0 100 100]}
                           :bounds {:min-x 0 :min-y 0 :max-x 100 :max-y 100}
                           :elevation-data (js/Float32Array. 1)
                           :width 1 :height 1
                           :resolution 1})
    (is (true? (topo/has-geo-info?)))))

(deftest garden->geo-test
  (testing "converts garden coords to geo coords"
    (state/set-topo-data! {:geo-info {:bbox [500000 5550000 501000 5551000]}
                           :bounds {:min-x 0 :min-y 0 :max-x 100000 :max-y 100000}
                           :elevation-data (js/Float32Array. 1)
                           :width 1 :height 1
                           :resolution 1})
    ;; At garden origin (0,0), should map to geo origin
    (let [[geo-x geo-y] (topo/garden->geo [0 0])]
      (is (= 500000.0 geo-x))
      ;; Y is flipped in GeoTIFF
      (is (= 5551000.0 geo-y)))
    ;; At garden max (100000, 100000), should map to geo max
    (let [[geo-x geo-y] (topo/garden->geo [100000 100000])]
      (is (= 501000.0 geo-x))
      (is (= 5550000.0 geo-y)))))
