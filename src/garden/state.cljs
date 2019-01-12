(ns garden.state
  (:refer-clojure :exclude [get get-in update])
  (:require [reagent.core :as r]))

(defonce app-state
  (r/atom {:canvas {:width 1200 :height 300 :pixels-per-meter 10 :ctx nil}
           :layers [{:id 1 :name "layer 1" :desc "asdadas asd asd asdasd asd asd asd asd " :points []}
                    {:id 2 :name "layer 2" :desc "ggrere reg er gre  " :points []}
                    {:id 3 :name "layer 3" :desc "ghtrrth sdf werrew" :points []}]}))

(defonce current-layer (r/atom nil))


;; Accessors
(defn get [& path] (cljs.core/get-in @app-state path))
(defn get-in [path] (cljs.core/get-in @app-state path))

(defn current-line [] (get-in [:layers @current-layer :points]))

;; Setters
(defn update [accessor value]
  (swap! app-state assoc-in accessor value))

(defn set-context [ctx] (update [:canvas :ctx] ctx))
(defn select-layer [layer-id]
  (->> (:layers @app-state) (map-indexed vector)
       (filter (fn [[i layer]] (= (:id layer) layer-id)))
       first first
       (reset! current-layer)))