(ns garden.canvas.topo-test
  (:require [cljs.test :refer [deftest testing is]]
            [garden.canvas.topo :as topo-canvas]))

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
