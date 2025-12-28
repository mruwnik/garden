(ns garden.topo.slope-test
  "Tests for slope and aspect calculations."
  (:require [cljs.test :refer [deftest is testing]]
            [garden.topo.slope :as slope]))

;; =============================================================================
;; Aspect Label Tests

(deftest aspect-to-label-test
  (testing "Cardinal directions"
    (is (= "N" (slope/aspect-to-label 0)))
    (is (= "N" (slope/aspect-to-label 360)))
    (is (= "E" (slope/aspect-to-label 90)))
    (is (= "S" (slope/aspect-to-label 180)))
    (is (= "W" (slope/aspect-to-label 270))))

  (testing "Intercardinal directions"
    (is (= "NE" (slope/aspect-to-label 45)))
    (is (= "SE" (slope/aspect-to-label 135)))
    (is (= "SW" (slope/aspect-to-label 225)))
    (is (= "NW" (slope/aspect-to-label 315))))

  (testing "Direction boundaries (22.5° windows)"
    ;; N is 337.5 - 22.5
    (is (= "N" (slope/aspect-to-label 10)))
    (is (= "N" (slope/aspect-to-label 350)))
    ;; NE is 22.5 - 67.5
    (is (= "NE" (slope/aspect-to-label 23)))
    (is (= "NE" (slope/aspect-to-label 67))))

  (testing "Nil input"
    (is (nil? (slope/aspect-to-label nil)))))

(deftest aspect-to-full-label-test
  (testing "Full direction names"
    (is (= "North" (slope/aspect-to-full-label 0)))
    (is (= "East" (slope/aspect-to-full-label 90)))
    (is (= "South" (slope/aspect-to-full-label 180)))
    (is (= "West" (slope/aspect-to-full-label 270)))
    (is (= "Northeast" (slope/aspect-to-full-label 45)))
    (is (= "Southeast" (slope/aspect-to-full-label 135)))
    (is (= "Southwest" (slope/aspect-to-full-label 225)))
    (is (= "Northwest" (slope/aspect-to-full-label 315))))

  (testing "Nil input"
    (is (nil? (slope/aspect-to-full-label nil)))))

;; =============================================================================
;; Slope Category Tests

(deftest slope-category-test
  (testing "Flat terrain (< 2°)"
    (is (= :flat (slope/slope-category 0)))
    (is (= :flat (slope/slope-category 1)))
    (is (= :flat (slope/slope-category 1.9))))

  (testing "Gentle slope (2-5°)"
    (is (= :gentle (slope/slope-category 2)))
    (is (= :gentle (slope/slope-category 3)))
    (is (= :gentle (slope/slope-category 4.9))))

  (testing "Moderate slope (5-10°)"
    (is (= :moderate (slope/slope-category 5)))
    (is (= :moderate (slope/slope-category 7)))
    (is (= :moderate (slope/slope-category 9.9))))

  (testing "Steep slope (10-15°)"
    (is (= :steep (slope/slope-category 10)))
    (is (= :steep (slope/slope-category 12)))
    (is (= :steep (slope/slope-category 14.9))))

  (testing "Very steep slope (15-30°)"
    (is (= :very-steep (slope/slope-category 15)))
    (is (= :very-steep (slope/slope-category 20)))
    (is (= :very-steep (slope/slope-category 29.9))))

  (testing "Extreme slope (> 30°)"
    (is (= :extreme (slope/slope-category 30)))
    (is (= :extreme (slope/slope-category 45)))
    (is (= :extreme (slope/slope-category 90))))

  (testing "Nil input"
    (is (nil? (slope/slope-category nil)))))

(deftest slope-category-label-test
  (testing "Category labels"
    (is (= "Flat (<2°)" (slope/slope-category-label :flat)))
    (is (= "Gentle (2-5°)" (slope/slope-category-label :gentle)))
    (is (= "Moderate (5-10°)" (slope/slope-category-label :moderate)))
    (is (= "Steep (10-15°)" (slope/slope-category-label :steep)))
    (is (= "Very Steep (15-30°)" (slope/slope-category-label :very-steep)))
    (is (= "Extreme (>30°)" (slope/slope-category-label :extreme))))

  (testing "Unknown category"
    (is (= "Unknown" (slope/slope-category-label nil)))
    (is (= "Unknown" (slope/slope-category-label :invalid)))))
