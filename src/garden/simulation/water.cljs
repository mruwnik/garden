(ns garden.simulation.water
  "Water flow simulation on terrain.

   This namespace provides backwards compatibility.
   Implementation is in garden.simulation.water.core."
  (:require [garden.simulation.water.core :as core]
            [garden.simulation.water.physics :as physics]))

;; Re-export public API for backwards compatibility

(def start-simulation! core/start!)
(def stop-simulation! core/stop!)
(def start-rain! core/start-rain!)
(def stop-rain! core/stop-rain!)
(def reset-water! core/reset!)

(def running? core/running?)
(def raining? core/raining?)

(def water-grid core/get-water-grid)
(def water-bounds core/get-water-bounds)
(def grid-dimensions core/get-grid-dimensions)

(def set-rain-rate! core/set-rain-rate!)
(def set-evaporation-rate! core/set-evaporation-rate!)
(def set-infiltration-rate! core/set-infiltration-rate!)
(def set-flow-rate! core/set-flow-rate!)

;; Legacy function - simulation interval is now in physics module
(defn get-sim-interval []
  physics/simulation-interval-ms)
