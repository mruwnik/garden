(ns garden.simulation.water.physics-test
  (:require [cljs.test :refer [deftest testing is are]]
            [garden.simulation.water.physics :as physics]))

(deftest test-unit-conversions
  (testing "mm/hour to m/step conversion"
    ;; 1 mm/hour = 0.001 m/hour = 0.001/3600000 m/ms
    ;; At 50ms steps: 0.001 * 50 / 3600000 = 1.389e-8 m/step
    (is (< (js/Math.abs (- (physics/mm-per-hour->m-per-step 1.0)
                           1.3888888888888889e-8))
           1e-15))

    ;; Round trip should preserve value
    (let [original 10.0
          converted (physics/mm-per-hour->m-per-step original)
          back (physics/m-per-step->mm-per-hour converted)]
      (is (< (js/Math.abs (- original back)) 0.0001))))

  (testing "zero rate stays zero"
    (is (zero? (physics/mm-per-hour->m-per-step 0)))
    (is (zero? (physics/m-per-step->mm-per-hour 0)))))

(deftest test-slope-calculation
  (testing "basic slope = rise/run"
    (is (= 1.0 (physics/calc-slope 1.0 1.0)))
    (is (= 2.0 (physics/calc-slope 2.0 1.0)))
    (is (= 0.5 (physics/calc-slope 1.0 2.0))))

  (testing "45 degree slope = 1.0"
    (is (= 1.0 (physics/calc-slope 0.5 0.5))))

  (testing "steep slope with small cells"
    ;; 1m drop over 25cm = slope of 4
    (is (= 4.0 (physics/calc-slope 1.0 0.25)))))

(deftest test-flow-rate-calculation
  (testing "flat terrain uses base flow rate"
    (is (= physics/base-flow-rate (physics/slope->flow-rate 0))))

  (testing "steeper slopes flow faster"
    (let [flat-rate (physics/slope->flow-rate 0)
          gentle-rate (physics/slope->flow-rate 0.1)
          steep-rate (physics/slope->flow-rate 1.0)
          very-steep-rate (physics/slope->flow-rate 10.0)]
      (is (< flat-rate gentle-rate))
      (is (< gentle-rate steep-rate))
      (is (< steep-rate very-steep-rate))))

  (testing "flow rate is capped at max"
    (is (<= (physics/slope->flow-rate 100) physics/max-flow-rate))
    (is (<= (physics/slope->flow-rate 1000) physics/max-flow-rate)))

  (testing "flow rate for height diff convenience function"
    (let [rate (physics/flow-rate-for-height-diff 1.0 0.5)]
      ;; 1m height / 0.5m cell = slope of 2
      ;; 1 + sqrt(2) = 2.414
      ;; 0.25 * 2.414 = 0.604
      (is (< 0.5 rate 0.7)))))

(deftest test-constants
  (testing "simulation runs at expected rate"
    (is (= 20 physics/steps-per-second))
    (is (= 50 physics/simulation-interval-ms)))

  (testing "flow rate bounds are reasonable"
    (is (< 0 physics/base-flow-rate 1))
    (is (< physics/base-flow-rate physics/max-flow-rate 1))))
