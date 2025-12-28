(ns garden.state-test
  (:require [cljs.test :refer [deftest testing is use-fixtures]]
            [garden.state :as state]))

;; Reset state before each test
(use-fixtures :each
  {:before (fn [] (reset! state/app-state state/initial-state))
   :after  (fn [] (reset! state/app-state state/initial-state))})

;; =============================================================================
;; Selection Operations
;; =============================================================================

(deftest selection-test
  (testing "initial selection is empty"
    (is (= {:type nil :ids #{}} (state/selection))))

  (testing "select! sets selection"
    (state/select! :plant #{"p1" "p2"})
    (is (= {:type :plant :ids #{"p1" "p2"}} (state/selection))))

  (testing "clear-selection! empties selection"
    (state/select! :area #{"a1"})
    (state/clear-selection!)
    (is (= {:type nil :ids #{}} (state/selection))))

  (testing "toggle-selection! adds and removes"
    (state/select! :plant #{"p1"})
    (state/toggle-selection! :plant "p2")
    (is (= #{"p1" "p2"} (state/selected-ids)))
    (state/toggle-selection! :plant "p1")
    (is (= #{"p2"} (state/selected-ids)))))

(deftest selected?-test
  (testing "selected? returns true for selected ids"
    (state/select! :plant #{"p1" "p2"})
    (is (true? (state/selected? "p1")))
    (is (true? (state/selected? "p2")))
    (is (false? (state/selected? "p3")))))

;; =============================================================================
;; Undo/Redo Operations
;; =============================================================================

(deftest undo-redo-test
  (testing "initially cannot undo or redo"
    (is (not (state/can-undo?)))
    (is (not (state/can-redo?))))

  (testing "adding plant enables undo"
    (reset! state/app-state state/initial-state)
    (state/add-plant! {:species-id "tomato" :position [100 100]})
    (is (state/can-undo?))
    (is (= 1 (count (state/plants)))))

  (testing "undo restores previous state"
    (reset! state/app-state state/initial-state)
    (state/add-plant! {:species-id "tomato" :position [100 100]})
    (state/undo!)
    (is (= 0 (count (state/plants))))
    (is (state/can-redo?)))

  (testing "redo restores undone state"
    (reset! state/app-state state/initial-state)
    (state/add-plant! {:species-id "tomato" :position [100 100]})
    (state/undo!)
    (state/redo!)
    (is (= 1 (count (state/plants))))))

;; =============================================================================
;; Area Operations
;; =============================================================================

(deftest area-operations-test
  (testing "add-area! creates area with id"
    (reset! state/app-state state/initial-state)
    (let [id (state/add-area! {:name "Bed 1" :points [[0 0] [100 0] [100 100] [0 100]]})]
      (is (some? id))
      (is (= 1 (count (state/areas))))
      (is (= "Bed 1" (:name (state/find-area id))))))

  (testing "update-area! modifies area"
    (reset! state/app-state state/initial-state)
    (let [id (state/add-area! {:name "Bed 1" :points [[0 0] [100 0] [100 100]]})]
      (state/update-area! id {:name "Updated Bed"})
      (is (= "Updated Bed" (:name (state/find-area id))))))

  (testing "remove-area! deletes area"
    (reset! state/app-state state/initial-state)
    (let [id (state/add-area! {:name "Bed 1" :points [[0 0] [100 0] [100 100]]})]
      (state/remove-area! id)
      (is (= 0 (count (state/areas))))))

  (testing "add-area! rejects invalid areas"
    (reset! state/app-state state/initial-state)
    (is (nil? (state/add-area! {:name "Bad" :points []})))
    (is (nil? (state/add-area! {:name "Bad" :points [[0 0]]})))
    (is (nil? (state/add-area! {:name "Bad" :points [[0 0] [10 10]]})))
    (is (= 0 (count (state/areas))))))

;; =============================================================================
;; Plant Operations
;; =============================================================================

(deftest plant-operations-test
  (testing "add-plant! creates plant with id"
    (reset! state/app-state state/initial-state)
    (let [id (state/add-plant! {:species-id "tomato" :position [50 50]})]
      (is (some? id))
      (is (= 1 (count (state/plants))))))

  (testing "add-plants-batch! adds multiple plants"
    (reset! state/app-state state/initial-state)
    (let [ids (state/add-plants-batch! [{:species-id "a" :position [0 0]}
                                        {:species-id "b" :position [10 10]}
                                        {:species-id "c" :position [20 20]}])]
      (is (= 3 (count ids)))
      (is (= 3 (count (state/plants)))))))

;; =============================================================================
;; Viewport Operations
;; =============================================================================

(deftest viewport-test
  (testing "initial viewport"
    (is (= 1.0 (state/zoom)))
    (is (= [0 0] (state/offset))))

  (testing "set-zoom! clamps to valid range"
    (state/set-zoom! 0.5)
    (is (= 0.5 (state/zoom)))
    (state/set-zoom! 0.0001)
    (is (= 0.001 (state/zoom)))  ; min zoom is 0.001 (0.1%)
    (state/set-zoom! 100)
    (is (= 10.0 (state/zoom))))

  (testing "pan! adjusts offset"
    (reset! state/app-state state/initial-state)
    (state/pan! 50 -30)
    (is (= [50 -30] (state/offset)))))

;; =============================================================================
;; Accessor Functions
;; =============================================================================

(deftest accessor-test
  (testing "areas returns empty list initially"
    (reset! state/app-state state/initial-state)
    (is (= [] (state/areas))))

  (testing "plants returns empty list initially"
    (reset! state/app-state state/initial-state)
    (is (= [] (state/plants))))

  (testing "find-area finds by id"
    (reset! state/app-state state/initial-state)
    (let [id (state/add-area! {:name "Test Bed" :type :bed :points [[0 0] [10 0] [10 10]]})]
      (is (= "Test Bed" (:name (state/find-area id))))
      (is (nil? (state/find-area "nonexistent")))))

  (testing "find-plant finds by id"
    (reset! state/app-state state/initial-state)
    (let [id (state/add-plant! {:species-id "tomato" :position [50 50]})]
      (is (= "tomato" (:species-id (state/find-plant id))))
      (is (nil? (state/find-plant "nonexistent")))))

  (testing "active-tool returns current tool"
    (reset! state/app-state state/initial-state)
    (is (= :select (state/active-tool)))
    (state/set-tool! :plant)
    (is (= :plant (state/active-tool))))

  (testing "tool-state returns tool state"
    (reset! state/app-state state/initial-state)
    (is (nil? (state/tool-state)))
    (state/set-tool-state! {:mode :test})
    (is (= {:mode :test} (state/tool-state)))))

(deftest find-plant-at-test
  (testing "finds plant within radius"
    (reset! state/app-state state/initial-state)
    (state/add-plant! {:species-id "tomato" :position [100 100]})
    (let [radius-fn (constantly 20)
          found (state/find-plant-at [105 105] radius-fn)]
      (is (some? found))
      (is (= "tomato" (:species-id found)))))

  (testing "returns nil when no plant at position"
    (reset! state/app-state state/initial-state)
    (state/add-plant! {:species-id "tomato" :position [100 100]})
    (let [radius-fn (constantly 20)
          found (state/find-plant-at [200 200] radius-fn)]
      (is (nil? found)))))
