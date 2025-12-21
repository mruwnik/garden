(ns garden.canvas.viewport-test
  (:require [cljs.test :refer [deftest testing is use-fixtures]]
            [garden.state :as state]
            [garden.canvas.viewport :as viewport]))

;; Reset state before each test
(use-fixtures :each
  {:before (fn [] (reset! state/app-state state/initial-state))
   :after  (fn [] (reset! state/app-state state/initial-state))})

;; =============================================================================
;; Coordinate Transformations
;; =============================================================================

(deftest screen->canvas-test
  (testing "with default viewport (zoom=1, offset=[0,0])"
    (is (= [100.0 200.0] (viewport/screen->canvas [100 200]))))

  (testing "with zoom=2"
    (state/set-zoom! 2.0)
    ;; screen [100, 200] -> canvas [100/2, 200/2] = [50, 100]
    (is (= [50.0 100.0] (viewport/screen->canvas [100 200]))))

  (testing "with offset [50, 75]"
    (reset! state/app-state state/initial-state)
    (state/pan! 50 75)
    ;; screen [100, 200] with offset [50, 75] -> canvas [(100-50)/1, (200-75)/1] = [50, 125]
    (is (= [50.0 125.0] (viewport/screen->canvas [100 200]))))

  (testing "with zoom and offset"
    (reset! state/app-state state/initial-state)
    (state/set-zoom! 2.0)
    (state/pan! 100 100)
    ;; screen [300, 400] with zoom=2, offset=[100, 100]
    ;; canvas = [(300-100)/2, (400-100)/2] = [100, 150]
    (is (= [100.0 150.0] (viewport/screen->canvas [300 400])))))

(deftest canvas->screen-test
  (testing "with default viewport"
    (is (= [100.0 200.0] (viewport/canvas->screen [100 200]))))

  (testing "with zoom=2"
    (state/set-zoom! 2.0)
    ;; canvas [100, 200] -> screen [100*2 + 0, 200*2 + 0] = [200, 400]
    (is (= [200.0 400.0] (viewport/canvas->screen [100 200]))))

  (testing "with offset [50, 75]"
    (reset! state/app-state state/initial-state)
    (state/pan! 50 75)
    ;; canvas [100, 200] with offset [50, 75] -> screen [100*1 + 50, 200*1 + 75] = [150, 275]
    (is (= [150.0 275.0] (viewport/canvas->screen [100 200]))))

  (testing "with zoom and offset"
    (reset! state/app-state state/initial-state)
    (state/set-zoom! 2.0)
    (state/pan! 100 100)
    ;; canvas [50, 75] with zoom=2, offset=[100, 100]
    ;; screen = [50*2 + 100, 75*2 + 100] = [200, 250]
    (is (= [200.0 250.0] (viewport/canvas->screen [50 75])))))

(deftest roundtrip-test
  (testing "screen->canvas->screen preserves coordinates"
    (state/set-zoom! 1.5)
    (state/pan! 75 -25)
    (let [screen-pt [400 300]
          canvas-pt (viewport/screen->canvas screen-pt)
          back-to-screen (viewport/canvas->screen canvas-pt)]
      (is (< (js/Math.abs (- (first screen-pt) (first back-to-screen))) 0.001))
      (is (< (js/Math.abs (- (second screen-pt) (second back-to-screen))) 0.001))))

  (testing "canvas->screen->canvas preserves coordinates"
    (reset! state/app-state state/initial-state)
    (state/set-zoom! 0.5)
    (state/pan! -100 200)
    (let [canvas-pt [500 600]
          screen-pt (viewport/canvas->screen canvas-pt)
          back-to-canvas (viewport/screen->canvas screen-pt)]
      (is (< (js/Math.abs (- (first canvas-pt) (first back-to-canvas))) 0.001))
      (is (< (js/Math.abs (- (second canvas-pt) (second back-to-canvas))) 0.001)))))

(deftest visible-bounds-test
  (testing "visible bounds with default viewport"
    (state/set-viewport-size! 800 600)
    (let [bounds (viewport/visible-bounds)]
      (is (= [0.0 0.0] (:min bounds)))
      (is (= [800.0 600.0] (:max bounds)))))

  (testing "visible bounds with zoom=2"
    (reset! state/app-state state/initial-state)
    (state/set-viewport-size! 800 600)
    (state/set-zoom! 2.0)
    (let [bounds (viewport/visible-bounds)]
      ;; With zoom=2, visible canvas area is halved
      (is (= [0.0 0.0] (:min bounds)))
      (is (= [400.0 300.0] (:max bounds)))))

  (testing "visible bounds with offset"
    (reset! state/app-state state/initial-state)
    (state/set-viewport-size! 800 600)
    (state/pan! 200 100)
    (let [bounds (viewport/visible-bounds)]
      ;; With offset [200, 100], visible area shifts
      (is (= [-200.0 -100.0] (:min bounds)))
      (is (= [600.0 500.0] (:max bounds))))))
