(ns garden.state
  (:refer-clojure :exclude [get get-in update set!])
  (:require [reagent.core :as r]))

(defonce app-state
  (r/atom {:canvas {:width 1200 :height 800 :pixels-per-unit 10 :ctx nil :x-offset 0 :y-offset 0 :zoom 1}
           :garden {:name "Bla bla bla" :lat 12.21 :lon 43.2}
           :layers []
           :current nil}))

;; Accessors
(defn get [& path] (cljs.core/get-in @app-state path))
(defn get-in [path] (cljs.core/get-in @app-state path))

(defn current-accessor [& path] (concat [:layers (get :current :index)] path))
(defn current-layer [] (get-in (current-accessor)))
(defn current-line [] (get-in (current-accessor :points)))

;; Setters
(defn set-val [accessor value]
  (swap! app-state assoc-in accessor value))
(defn update [accessor func & params]
  (set-val accessor (apply func (get-in accessor) params)))


(defn set-context [ctx] (set-val [:canvas :ctx] ctx))


;; Edit mode handlers
(defn get-mode [] (get :current :mode))
(defn set-mode [mode] (set-val [:current :mode] mode))

(defn set-pointer [pointer] (set-val [:canvas :pointer] pointer))

(defn move
  "Return the offset from the previous movement ([0 0] if there was no movement)."
  [point]
  (if-not (get :current :last-point)
    (set-val [:current :last-point] point)
    (let [[prev-x prev-y] (get :current :last-point)
          [current-x current-y] point]
      (set-val [:current :last-point] point)
      [(- current-x prev-x) (- current-y prev-y)])))
(defn end-move [] (set-val [:current :last-point] nil))

(defn- offset-point [[x-offset y-offset] [x y]] [(+ x x-offset) (+ y y-offset)])
(defn move-current-layer
  "Move the current layer by the given offset."
  [offset]
  (->>
   (current-line)
   (map (partial offset-point offset))
   (set-val (current-accessor :points))))

(defn move-current-point
  "Move the last selected point in the current layer to the new position"
  [pos]
  (->> (current-line)
       (replace {(get :current :last-point) pos})
       (set-val (current-accessor :points)))
  (set-val [:current :last-point] pos))


(defn select-layer
  "Select the layer with the given id as active, or deactivate it if previously chosen."
  [layer-id]
  (set-val [:current]
          (when (not= layer-id (get :current :id))
            (->> (:layers @app-state)
                 (map-indexed #(assoc %2 :index %1))
                 (filter #(= (:id %) layer-id))
                 first)))
  (when (current-layer)
    (set-mode :edit)))

(defn set-canvas-size []
  (let [window-width (or (aget  js/window "innerWidth") (-> js/document (aget "body") (aget "clientWidth")))
        window-height (or (aget  js/window "innerHeight") (-> js/document (aget "body") (aget "clientHeight")))]
    (set-val [:canvas :width] (- window-width 380))
    (set-val [:canvas :height] (- window-height 20))))
