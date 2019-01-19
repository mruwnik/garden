(ns garden.state
  (:refer-clojure :exclude [get get-in update])
  (:require [reagent.core :as r]))

(defonce app-state
  (r/atom {:canvas {:width 1200 :height 800 :pixels-per-meter 10 :ctx nil :x-offset 0 :y-offset 0}
           :garden {:name "Bla bla bla" :lat 12.21 :lon 43.2}
           :layers [{:id 1 :name "layer 1" :desc "asdadas asd asd asdasd asd asd asd asd " :points [] :colour "red"}
                       {:id 2 :name "layer 2" :desc "ggrere reg er gre  " :points [] :colour "green"}
                    {:id 3 :name "layer 3" :desc "ghtrrth sdf werrew" :points [] :colour "blue"}]
           :current nil}))

;; Accessors
(defn get [& path] (cljs.core/get-in @app-state path))
(defn get-in [path] (cljs.core/get-in @app-state path))

(defn current-accessor [& path] (concat [:layers (get :current :index)] path))
(defn current-layer [] (get-in (current-accessor)))
(defn current-line [] (get-in (current-accessor :points)))

;; Setters
(defn update [accessor value]
  (swap! app-state assoc-in accessor value))

(defn set-context [ctx] (update [:canvas :ctx] ctx))


;; Edit mode handlers
(defn get-mode [] (get :current :mode))
(defn set-mode [mode] (update [:current :mode] mode))

(defn set-pointer [pointer] (update [:canvas :pointer] pointer))

(defn move
  "Return the offset from the previous movement ([0 0] if there was no movement)."
  [point]
  (if-not (get :current :last-point)
    (update [:current :last-point] point)
    (let [[prev-x prev-y] (get :current :last-point)
          [current-x current-y] point]
      (update [:current :last-point] point)
      [(- current-x prev-x) (- current-y prev-y)])))
(defn end-move [] (update [:current :last-point] nil))

(defn- offset-point [[x-offset y-offset] [x y]] [(+ x x-offset) (+ y y-offset)])
(defn move-current-layer [offset]
  (->>
   (current-line)
   (map (partial offset-point offset))
   (update (current-accessor :points))))


(defn select-layer
  "Select the layer with the given id as active, or deactivate it if previously chosen."
  [layer-id]
  (update [:current]
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
    (update [:canvas :width] (- window-width 380))
    (update [:canvas :height] (- window-height 20))))
