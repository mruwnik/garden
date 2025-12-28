(ns garden.tools.area-test
  "Tests for area tool polygon closing logic."
  (:require [cljs.test :refer [deftest is testing]]
            [garden.tools.area :as area]))

;; =============================================================================
;; Close Detection Tests

(deftest close-to-first-test
  (testing "Returns nil with fewer than 3 points"
    (is (nil? (area/close-to-first? [] [0 0])))
    (is (nil? (area/close-to-first? [[0 0]] [0 0])))
    (is (nil? (area/close-to-first? [[0 0] [10 0]] [0 0]))))

  (testing "Returns true when point is within threshold of first point"
    (let [triangle [[0 0] [100 0] [50 100]]]
      ;; Point at origin
      (is (true? (area/close-to-first? triangle [0 0])))
      ;; Point within threshold (15 pixels)
      (is (true? (area/close-to-first? triangle [10 0])))
      (is (true? (area/close-to-first? triangle [0 10])))
      (is (true? (area/close-to-first? triangle [10 10])))))

  (testing "Returns falsy when point is outside threshold"
    (let [triangle [[0 0] [100 0] [50 100]]]
      ;; Point clearly outside threshold
      (is (not (area/close-to-first? triangle [50 50])))
      (is (not (area/close-to-first? triangle [100 0])))
      ;; Point just outside threshold
      (is (not (area/close-to-first? triangle [20 0])))))

  (testing "Works with complex polygons"
    (let [pentagon [[100 100] [200 100] [250 180] [150 250] [50 180]]]
      ;; Near first point
      (is (true? (area/close-to-first? pentagon [105 105])))
      ;; Far from first point
      (is (not (area/close-to-first? pentagon [200 200]))))))

(deftest close-threshold-test
  (testing "Close threshold is a reasonable pixel value"
    (is (number? area/close-threshold-px))
    (is (> area/close-threshold-px 0))
    (is (<= area/close-threshold-px 50)))) ; Should be a small, usable value
