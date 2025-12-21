(ns garden.canvas.viewport
  (:require [garden.state :as state]))

(defn screen->canvas
  "Convert screen (pixel) coordinates to canvas (world) coordinates."
  [[sx sy]]
  (let [{:keys [offset zoom]} (state/viewport)
        [ox oy] offset]
    [(/ (- sx ox) zoom)
     (/ (- sy oy) zoom)]))

(defn canvas->screen
  "Convert canvas (world) coordinates to screen (pixel) coordinates."
  [[cx cy]]
  (let [{:keys [offset zoom]} (state/viewport)
        [ox oy] offset]
    [(+ (* cx zoom) ox)
     (+ (* cy zoom) oy)]))

(defn apply-transform!
  "Apply the current viewport transform to the canvas context."
  [ctx]
  (let [{:keys [offset zoom]} (state/viewport)
        [ox oy] offset]
    (.translate ctx ox oy)
    (.scale ctx zoom zoom)))

(defn visible-bounds
  "Return the visible canvas bounds as {:min [x y] :max [x y]}."
  []
  (let [{:keys [size]} (state/viewport)
        {:keys [width height]} size]
    {:min (screen->canvas [0 0])
     :max (screen->canvas [width height])}))
