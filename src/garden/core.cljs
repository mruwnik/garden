(ns garden.core
  (:require [garden.ui.app :as app]))

(enable-console-print!)

(defn ^:export init
  "Initialize the application."
  []
  (app/mount-app))

(defn on-js-reload
  "Called on figwheel hot reload."
  []
  (app/mount-app))

;; Note: shadow-cljs calls init automatically via :init-fn
