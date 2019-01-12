(ns garden.core
  (:require [reagent.core :as r]
            [garden.state :as state]
            [garden.canvas :as canvas]
            [garden.display :as display]))

(enable-console-print!)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )


(defn render-react []
  (r/render [display/garden-app]
            (.getElementById js/document "app")))


(render-react)
(canvas/render @state/app-state)
