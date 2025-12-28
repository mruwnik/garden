(ns garden.simulation.water.grid-test
  (:require [cljs.test :refer [deftest testing is]]
            [garden.simulation.water.grid :as grid]))

(deftest test-calc-grid-dimensions
  (testing "basic dimension calculation"
    (let [bounds {:min-x 0 :min-y 0 :max-x 10000 :max-y 10000}  ; 100m x 100m
          [w h] (grid/calc-grid-dimensions bounds 100)]          ; 1m resolution
      (is (= 100 w))
      (is (= 100 h))))

  (testing "smaller resolution = more cells"
    (let [bounds {:min-x 0 :min-y 0 :max-x 10000 :max-y 10000}
          [w1 _] (grid/calc-grid-dimensions bounds 100)
          [w2 _] (grid/calc-grid-dimensions bounds 50)]
      (is (= (* 2 w1) w2))))

  (testing "respects minimum grid size"
    (let [bounds {:min-x 0 :min-y 0 :max-x 100 :max-y 100}  ; 1m x 1m
          [w h] (grid/calc-grid-dimensions bounds 100)]      ; would be 1x1
      (is (>= w grid/min-grid-size))
      (is (>= h grid/min-grid-size))))

  (testing "respects maximum grid size"
    (let [bounds {:min-x 0 :min-y 0 :max-x 1000000 :max-y 1000000}  ; 10km x 10km
          [w h] (grid/calc-grid-dimensions bounds 10)]              ; would be 100000x100000
      (is (<= w grid/max-grid-size))
      (is (<= h grid/max-grid-size))))

  (testing "asymmetric bounds"
    (let [bounds {:min-x -5000 :min-y -2500 :max-x 5000 :max-y 2500}  ; 100m x 50m
          [w h] (grid/calc-grid-dimensions bounds 100)]
      (is (= 100 w))
      (is (= 50 h)))))

(deftest test-resolution-conversion
  (testing "cm to meters"
    (is (= 0.5 (grid/resolution->cell-size-m 50)))
    (is (= 1.0 (grid/resolution->cell-size-m 100)))
    (is (= 0.25 (grid/resolution->cell-size-m 25)))))

(deftest test-bilinear-sample
  (testing "exact corner sampling"
    (let [data #js [1 2 3 4]  ; 2x2 grid
          w 2
          h 2]
      (is (= 1 (grid/bilinear-sample data w h 0 0)))
      (is (= 2 (grid/bilinear-sample data w h 1 0)))
      (is (= 3 (grid/bilinear-sample data w h 0 1)))
      (is (= 4 (grid/bilinear-sample data w h 1 1)))))

  (testing "midpoint interpolation"
    (let [data #js [0 10 0 10]  ; 2x2 grid
          w 2
          h 2]
      ;; Center should be average of all corners
      (is (= 5.0 (grid/bilinear-sample data w h 0.5 0.5)))))

  (testing "edge interpolation"
    (let [data #js [0 10 0 10]
          w 2
          h 2]
      ;; Midpoint of top edge (between 0 and 10)
      (is (= 5.0 (grid/bilinear-sample data w h 0.5 0))))))

(deftest test-resample-grid
  (testing "same dimensions returns copy"
    (let [src (js/Float32Array. #js [1 2 3 4])
          dst (grid/resample-grid src 2 2 2 2)]
      (is (not (identical? src dst)))
      ;; Compare as vectors since JS arrays don't compare with =
      (is (= (vec (js/Array.from src)) (vec (js/Array.from dst))))))

  (testing "upsampling 2x2 to 3x3"
    (let [src (js/Float32Array. #js [0 10 0 10])
          dst (grid/resample-grid src 2 2 3 3)]
      ;; Corners should be preserved
      (is (= 0 (aget dst 0)))      ; top-left
      (is (= 10 (aget dst 2)))     ; top-right
      (is (= 0 (aget dst 6)))      ; bottom-left
      (is (= 10 (aget dst 8)))     ; bottom-right
      ;; Center should be average
      (is (= 5.0 (aget dst 4)))))

  (testing "downsampling preserves extremes approximately"
    (let [src (js/Float32Array. (clj->js (range 100)))  ; 10x10 with values 0-99
          dst (grid/resample-grid src 10 10 5 5)]
      ;; Check dimensions
      (is (= 25 (.-length dst)))
      ;; First value should be 0 (corner)
      (is (= 0 (aget dst 0))))))
