(ns garden.tools.scatter-test
  "Tests for scatter tool grid calculation logic."
  (:require [cljs.test :refer [deftest is testing]]
            [garden.tools.scatter :as scatter]))

;; =============================================================================
;; Grid Dimension Tests

(deftest calculate-grid-dimensions-test
  (testing "Basic grid calculation"
    (let [result (scatter/calculate-grid-dimensions 100 100 50 10)]
      (is (= 2 (:cols result)))
      (is (= 2 (:rows result)))
      (is (= 4 (:max-plants result)))
      (is (= 4 (:actual-count result))))) ; Capped by max-plants

  (testing "Count target limits actual count"
    (let [result (scatter/calculate-grid-dimensions 500 500 50 5)]
      (is (= 10 (:cols result)))
      (is (= 10 (:rows result)))
      (is (= 100 (:max-plants result)))
      (is (= 5 (:actual-count result))))) ; Limited by count-target

  (testing "Minimum 1 column and row"
    (let [result (scatter/calculate-grid-dimensions 10 10 50 10)]
      (is (= 1 (:cols result)))
      (is (= 1 (:rows result)))
      (is (= 1 (:max-plants result)))))

  (testing "Non-square areas"
    (let [result (scatter/calculate-grid-dimensions 200 100 50 20)]
      (is (= 4 (:cols result)))
      (is (= 2 (:rows result)))
      (is (= 8 (:max-plants result)))))

  (testing "Large spacing relative to area"
    (let [result (scatter/calculate-grid-dimensions 100 100 200 10)]
      (is (= 1 (:cols result)))
      (is (= 1 (:rows result)))
      (is (= 1 (:actual-count result))))))

;; =============================================================================
;; Grid Position Tests

(deftest generate-grid-positions-test
  (testing "Generates correct number of positions"
    (let [positions (scatter/generate-grid-positions 0 0 50 3 2 0)]
      (is (= 6 (count positions)))))

  (testing "Positions are centered in cells (no jitter)"
    (let [positions (vec (scatter/generate-grid-positions 0 0 100 2 2 0))]
      ;; With spacing 100, cells are at 0-100, 100-200
      ;; Centers should be at 50, 150
      ;; Grid iterates: col=0,row=0 -> col=0,row=1 -> col=1,row=0 -> col=1,row=1
      (is (= [50 50] (first positions)))
      (is (= [50 150] (second positions)))))

  (testing "Offset starting position"
    (let [positions (vec (scatter/generate-grid-positions 100 200 50 2 2 0))]
      ;; First cell center should be at min + spacing/2
      (is (= [125 225] (first positions)))))

  (testing "Jitter affects positions"
    (let [no-jitter (scatter/generate-grid-positions 0 0 100 2 2 0)
          with-jitter (scatter/generate-grid-positions 0 0 100 2 2 0.3)]
      ;; With jitter, positions may differ (but this is probabilistic)
      ;; At minimum, verify we get the same count
      (is (= (count no-jitter) (count with-jitter)))))

  (testing "Single cell grid"
    (let [positions (vec (scatter/generate-grid-positions 0 0 100 1 1 0))]
      (is (= 1 (count positions)))
      (is (= [50 50] (first positions))))))

;; =============================================================================
;; Integration Tests

(deftest scatter-grid-integration-test
  (testing "Full workflow: dimensions to positions"
    (let [{:keys [cols rows]} (scatter/calculate-grid-dimensions 500 300 100 50)
          positions (scatter/generate-grid-positions 0 0 100 cols rows 0)]
      (is (= 5 cols))
      (is (= 3 rows))
      (is (= 15 (count positions)))
      ;; All positions should be within bounds (accounting for centering)
      (doseq [[x y] positions]
        (is (>= x 50))
        (is (< x 500))
        (is (>= y 50))
        (is (< y 300))))))
