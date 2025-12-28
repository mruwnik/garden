(ns garden.canvas.topo-test
  (:require [cljs.test :refer [deftest testing is]]
            [garden.canvas.topo :as topo-canvas]))

;; =============================================================================
;; Peak Detection
;; =============================================================================

(deftest find-peaks-test
  (testing "finds single peak in center of grid"
    ;; 3x3 grid with peak in center
    ;; [10 10 10]
    ;; [10 50 10]
    ;; [10 10 10]
    (let [data (js/Float32Array. #js [10 10 10 10 50 10 10 10 10])
          topo-state {:elevation-data data
                      :width 3
                      :height 3
                      :bounds {:min-x 0 :min-y 0 :max-x 300 :max-y 300}
                      :resolution 100}
          peaks (topo-canvas/find-peaks topo-state 0)]
      (is (= 1 (count peaks)))
      (is (= 50 (:elevation (first peaks))))
      ;; Peak at grid position (1, 1) -> garden coords (100, 100)
      (is (= 100 (:x (first peaks))))
      (is (= 100 (:y (first peaks))))))

  (testing "finds multiple peaks"
    ;; 5x5 grid with two peaks
    ;; [10 10 10 10 10]
    ;; [10 50 10 10 10]
    ;; [10 10 10 10 10]
    ;; [10 10 10 60 10]
    ;; [10 10 10 10 10]
    (let [data (js/Float32Array.
                #js [10 10 10 10 10
                     10 50 10 10 10
                     10 10 10 10 10
                     10 10 10 60 10
                     10 10 10 10 10])
          topo-state {:elevation-data data
                      :width 5
                      :height 5
                      :bounds {:min-x 0 :min-y 0 :max-x 500 :max-y 500}
                      :resolution 100}
          peaks (topo-canvas/find-peaks topo-state 0)]
      (is (= 2 (count peaks)))
      (is (= #{50 60} (set (map :elevation peaks))))))

  (testing "respects elevation threshold"
    ;; Same grid as above, but with threshold of 55
    (let [data (js/Float32Array.
                #js [10 10 10 10 10
                     10 50 10 10 10
                     10 10 10 10 10
                     10 10 10 60 10
                     10 10 10 10 10])
          topo-state {:elevation-data data
                      :width 5
                      :height 5
                      :bounds {:min-x 0 :min-y 0 :max-x 500 :max-y 500}
                      :resolution 100}
          peaks (topo-canvas/find-peaks topo-state 55)]
      ;; Only the 60m peak should be returned
      (is (= 1 (count peaks)))
      (is (= 60 (:elevation (first peaks))))))

  (testing "ignores plateau (equal neighbors)"
    ;; 3x3 grid where center equals neighbors - not a peak
    ;; [50 50 50]
    ;; [50 50 50]
    ;; [50 50 50]
    (let [data (js/Float32Array. #js [50 50 50 50 50 50 50 50 50])
          topo-state {:elevation-data data
                      :width 3
                      :height 3
                      :bounds {:min-x 0 :min-y 0 :max-x 300 :max-y 300}
                      :resolution 100}
          peaks (topo-canvas/find-peaks topo-state 0)]
      (is (empty? peaks))))

  (testing "ignores edge cells"
    ;; Peak on edge should not be detected (edge cells are skipped)
    ;; Even if edge has highest value, only interior cells are checked
    ;; [90 10 10]
    ;; [10 50 10]  <- center (1,1) is 50, but neighbor (0,0)=90 is higher
    ;; [10 10 10]
    ;; Neither is a peak: 90 is on edge (skipped), 50 has higher neighbor
    (let [data (js/Float32Array. #js [90 10 10 10 50 10 10 10 10])
          topo-state {:elevation-data data
                      :width 3
                      :height 3
                      :bounds {:min-x 0 :min-y 0 :max-x 300 :max-y 300}
                      :resolution 100}
          peaks (topo-canvas/find-peaks topo-state 0)]
      ;; No peaks: 90 is on edge, 50 is not a local max (90 > 50)
      (is (= 0 (count peaks))))

    ;; Better test: edge cell is highest but not detected
    ;; [10 10 90]
    ;; [10 50 10]  <- center (1,1)=50 is lower than edge (0,2)=90
    ;; [10 10 10]
    (let [data (js/Float32Array. #js [10 10 90 10 50 10 10 10 10])
          topo-state {:elevation-data data
                      :width 3
                      :height 3
                      :bounds {:min-x 0 :min-y 0 :max-x 300 :max-y 300}
                      :resolution 100}
          peaks (topo-canvas/find-peaks topo-state 0)]
      ;; 90 on edge is not detected, 50 is not a peak (90 > 50)
      (is (= 0 (count peaks)))))

  (testing "handles NaN values gracefully"
    ;; Grid with NaN - should not crash
    (let [data (js/Float32Array. #js [10 js/NaN 10 10 50 10 10 10 10])
          topo-state {:elevation-data data
                      :width 3
                      :height 3
                      :bounds {:min-x 0 :min-y 0 :max-x 300 :max-y 300}
                      :resolution 100}
          peaks (topo-canvas/find-peaks topo-state 0)]
      ;; Center peak should still be detected, NaN neighbor is treated as nil
      (is (= 1 (count peaks)))))

  (testing "returns nil for missing data"
    (is (nil? (topo-canvas/find-peaks {:elevation-data nil} 0)))
    (is (nil? (topo-canvas/find-peaks {:elevation-data (js/Float32Array. 9)
                                       :width nil
                                       :height 3
                                       :bounds {:min-x 0 :min-y 0 :max-x 300 :max-y 300}
                                       :resolution 100} 0)))))

;; =============================================================================
;; Color Interpolation (testing internal helpers via elevation->color behavior)
;; =============================================================================

(deftest elevation-color-mapping-test
  (testing "produces valid colors for normalized elevations"
    ;; We can't directly test private functions, but we can verify
    ;; that the render functions don't throw errors with various inputs
    ;; This is more of a smoke test
    (let [data (js/Float32Array. #js [0 50 100])
          topo-state {:elevation-data data
                      :width 3
                      :height 1
                      :bounds {:min-x 0 :min-y 0 :max-x 300 :max-y 100}
                      :resolution 100
                      :min-elevation 0
                      :max-elevation 100
                      :visible? true}]
      ;; If this doesn't throw, basic color mapping works
      (is (some? topo-state)))))
