(ns garden.core
  (:require [garden.ui.app :as app]
            [garden.state :as state]))

(enable-console-print!)

(defn ^:export init
  "Initialize the application."
  []
  (app/mount-app)
  ;; Load default reference image
  (state/load-reference-image-url! "/kenroku-en.png"))

(defn on-js-reload
  "Called on figwheel hot reload."
  []
  (app/mount-app))

;; Note: shadow-cljs calls init automatically via :init-fn
