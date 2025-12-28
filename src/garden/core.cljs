(ns garden.core
  "Application entry point.

   Initializes the garden planning application and handles hot reload."
  (:require [garden.ui.app :as app]
            [garden.topo.geotiff :as geotiff]))

(enable-console-print!)

(defn ^:export init
  "Initialize the application."
  []
  (app/mount-app)
  ;; Load default topo data (resolution auto-detected from file, defaults to 0.5m)
  (geotiff/load-geotiff-url! "/topo.tif"))

(defn on-js-reload
  "Called on figwheel hot reload."
  []
  (app/mount-app))

;; Note: shadow-cljs calls init automatically via :init-fn
