(ns garden.state
  (:refer-clojure :exclude [get get-in update])
  (:require [reagent.core :as r]))

(defonce app-state
  (r/atom {:canvas {:width 1200 :height 300 :pixels-per-meter 10 :ctx nil}
           :current nil
           :layers [{:id 1 :name "layer 1" :desc "asdadas asd asd asdasd asd asd asd asd " :points [] :colour "red"}
                       {:id 2 :name "layer 2" :desc "ggrere reg er gre  " :points [] :colour "green"}
                        {:id 3 :name "layer 3" :desc "ghtrrth sdf werrew" :points [] :colour "blue"}]}))

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

(defn select-layer
  "Select the layer with the given id as active, or deactivate it if previously chosen."
  [layer-id]
  (update [:current]
          (when (not= layer-id (get :current :id))
            (->> (:layers @app-state)
                 (map-indexed #(assoc %2 :index %1))
                 (filter #(= (:id %) layer-id))
                 first))))
