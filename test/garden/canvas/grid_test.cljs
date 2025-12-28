(ns garden.canvas.grid-test
  "Tests for grid LOD calculations."
  (:require [cljs.test :refer [deftest is testing]]
            [garden.canvas.grid :as grid]))

;; =============================================================================
;; LOD Spacing Tests

(deftest calculate-lod-spacing-test
  (testing "Very zoomed out (< 0.015) uses 20x spacing"
    (is (= 20 (grid/calculate-lod-spacing 0.01)))
    (is (= 20 (grid/calculate-lod-spacing 0.014))))

  (testing "Zoomed out (0.015-0.03) uses 10x spacing"
    (is (= 10 (grid/calculate-lod-spacing 0.015)))
    (is (= 10 (grid/calculate-lod-spacing 0.02)))
    (is (= 10 (grid/calculate-lod-spacing 0.029))))

  (testing "Medium zoom (0.03-0.06) uses 5x spacing"
    (is (= 5 (grid/calculate-lod-spacing 0.03)))
    (is (= 5 (grid/calculate-lod-spacing 0.04)))
    (is (= 5 (grid/calculate-lod-spacing 0.059))))

  (testing "Zoomed in (0.06-0.12) uses 2x spacing"
    (is (= 2 (grid/calculate-lod-spacing 0.06)))
    (is (= 2 (grid/calculate-lod-spacing 0.1)))
    (is (= 2 (grid/calculate-lod-spacing 0.119))))

  (testing "Very zoomed in (>= 0.12) uses 1x spacing"
    (is (= 1 (grid/calculate-lod-spacing 0.12)))
    (is (= 1 (grid/calculate-lod-spacing 0.5)))
    (is (= 1 (grid/calculate-lod-spacing 1.0)))
    (is (= 1 (grid/calculate-lod-spacing 2.0)))))

;; =============================================================================
;; Label Spacing Tests

(deftest calculate-label-spacing-test
  (testing "Very zoomed out (< 0.01) uses 10x label spacing"
    (is (= 10 (grid/calculate-label-spacing 0.005)))
    (is (= 10 (grid/calculate-label-spacing 0.009))))

  (testing "Zoomed out (0.01-0.02) uses 5x label spacing"
    (is (= 5 (grid/calculate-label-spacing 0.01)))
    (is (= 5 (grid/calculate-label-spacing 0.015)))
    (is (= 5 (grid/calculate-label-spacing 0.019))))

  (testing "Medium zoom (0.02-0.05) uses 4x label spacing"
    (is (= 4 (grid/calculate-label-spacing 0.02)))
    (is (= 4 (grid/calculate-label-spacing 0.03)))
    (is (= 4 (grid/calculate-label-spacing 0.049))))

  (testing "Zoomed in (0.05-0.1) uses 2x label spacing"
    (is (= 2 (grid/calculate-label-spacing 0.05)))
    (is (= 2 (grid/calculate-label-spacing 0.07)))
    (is (= 2 (grid/calculate-label-spacing 0.099))))

  (testing "Very zoomed in (>= 0.1) uses 1x label spacing"
    (is (= 1 (grid/calculate-label-spacing 0.1)))
    (is (= 1 (grid/calculate-label-spacing 0.5)))
    (is (= 1 (grid/calculate-label-spacing 1.0)))))

;; =============================================================================
;; Integration Tests

(deftest lod-spacing-decreases-with-zoom
  (testing "Spacing multiplier decreases as zoom increases"
    (let [zooms [0.005 0.02 0.04 0.08 0.5]
          spacings (map grid/calculate-lod-spacing zooms)]
      ;; Each spacing should be >= the next as zoom increases
      (is (apply >= spacings)))))

(deftest label-spacing-decreases-with-zoom
  (testing "Label spacing multiplier decreases as zoom increases"
    (let [zooms [0.005 0.015 0.03 0.07 0.5]
          spacings (map grid/calculate-label-spacing zooms)]
      ;; Each spacing should be >= the next as zoom increases
      (is (apply >= spacings)))))
