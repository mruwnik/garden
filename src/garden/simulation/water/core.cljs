(ns garden.simulation.water.core
  "Water flow simulation on terrain.

   Runs simulation in a Web Worker for performance.
   All rates use SI units (mm/hour) and are converted internally."
  (:require [garden.state :as state]
            [garden.simulation.water.worker :as worker]
            [garden.simulation.water.grid :as grid]
            [garden.simulation.water.physics :as physics]))

;; -----------------------------------------------------------------------------
;; Local State

(defonce ^:private water-grid (atom nil))
(defonce ^:private grid-dims (atom [0 0]))

(defonce ^:private sim-params
  (atom {:rain-rate-mm-hr 10.0
         :evaporation-mm-hr 5.0
         :infiltration-mm-hr 0.0
         :flow-rate physics/base-flow-rate}))

;; -----------------------------------------------------------------------------
;; Worker Communication

(defn- send-params!
  "Send current simulation parameters to the worker."
  []
  (let [{:keys [rain-rate-mm-hr evaporation-mm-hr infiltration-mm-hr flow-rate]} @sim-params]
    (worker/send! :set-params
                  :rainRate (physics/mm-per-hour->m-per-step rain-rate-mm-hr)
                  :evaporation (physics/mm-per-hour->m-per-step evaporation-mm-hr)
                  :infiltration (physics/mm-per-hour->m-per-step infiltration-mm-hr)
                  :flowRate flow-rate)))

(defn- send-elevation-data!
  "Send elevation data to worker, resampled to match graphics resolution."
  []
  (let [topo (state/topo-data)
        {:keys [elevation-data width height bounds]} topo
        resolution-cm (or (state/graphics-resolution-cm) 50)
        cell-size-m (grid/resolution->cell-size-m resolution-cm)]
    (when (and elevation-data width height bounds)
      (let [[sim-w sim-h] (grid/calc-grid-dimensions bounds resolution-cm)
            resampled (grid/resample-grid elevation-data width height sim-w sim-h)]
        (reset! grid-dims [sim-w sim-h])
        (js/console.log "Water simulation:" sim-w "x" sim-h
                        "cells at" cell-size-m "m/cell")
        (worker/send-with-transfer!
         :init
         [(.-buffer resampled)]
         :elevationData resampled
         :width sim-w
         :height sim-h
         :cellSize cell-size-m)))))

(defn- on-water-update
  "Handle water grid updates from the worker."
  [grid-data]
  (reset! water-grid grid-data)
  (state/set-state! [:water-sim :last-update] (js/Date.now)))

(defn- on-worker-ready
  "Handle worker ready signal."
  []
  (js/console.log "Water worker ready")
  (send-params!))

;; -----------------------------------------------------------------------------
;; Initialization

(defn init!
  "Initialize the water simulation. Must be called before use."
  []
  (worker/init!)
  (worker/on-message :ready on-worker-ready)
  (worker/on-message :water-update on-water-update))

;; -----------------------------------------------------------------------------
;; Public API - Simulation Control

(defn start!
  "Start the water simulation (flowing/draining only, no rain)."
  []
  (init!)
  ;; Send elevation data to initialize worker if it's not yet ready
  (when-not (worker/ready?)
    (send-elevation-data!))
  (worker/send! :start)
  (state/set-state! [:water-sim :running?] true))

(defn stop!
  "Stop the water simulation completely."
  []
  (worker/send! :stop)
  ;; Batch both state updates
  (state/update-state! [:water-sim] merge {:running? false :raining? false}))

(defn start-rain!
  "Start rain (also starts simulation if not running)."
  []
  (init!)
  ;; Send elevation data to initialize worker if it's not yet ready
  (when-not (worker/ready?)
    (send-elevation-data!))
  (send-params!)
  (worker/send! :start-rain)
  (worker/send! :start)
  ;; Batch both state updates
  (state/update-state! [:water-sim] merge {:running? true :raining? true}))

(defn stop-rain!
  "Stop rain but keep simulation running for drainage."
  []
  (worker/send! :stop-rain)
  (state/set-state! [:water-sim :raining?] false))

(defn reset!
  "Clear all water from the grid."
  []
  (worker/send! :reset)
  (when-let [grid @water-grid]
    (.fill grid 0)))

;; -----------------------------------------------------------------------------
;; Public API - State Queries

(defn running?
  "Check if simulation is running."
  []
  (boolean (state/get-state :water-sim :running?)))

(defn raining?
  "Check if rain is active."
  []
  (boolean (state/get-state :water-sim :raining?)))

(defn get-water-grid
  "Get the current water grid (Float32Array)."
  []
  @water-grid)

(defn get-grid-dimensions
  "Get water grid dimensions [width height]."
  []
  @grid-dims)

(defn get-water-bounds
  "Get min/max water heights for visualization.
   Returns [min max] or nil if no water data."
  []
  (when-let [grid @water-grid]
    (let [n (.-length grid)]
      (loop [i 0
             min-w js/Infinity
             max-w 0]
        (if (>= i n)
          (when (< min-w js/Infinity)
            [min-w max-w])
          (let [w (aget grid i)]
            (recur (inc i)
                   (if (pos? w) (min min-w w) min-w)
                   (max max-w w))))))))

;; -----------------------------------------------------------------------------
;; Public API - Parameter Configuration

(defn set-rain-rate!
  "Set rain rate in mm/hour."
  [mm-hr]
  (let [rate (max 0 mm-hr)]
    (swap! sim-params assoc :rain-rate-mm-hr rate)
    (state/set-state! [:water-sim :rain-rate-mm-hr] rate)
    (send-params!)))

(defn set-evaporation-rate!
  "Set evaporation rate in mm/hour."
  [mm-hr]
  (let [rate (max 0 mm-hr)]
    (swap! sim-params assoc :evaporation-mm-hr rate)
    (state/set-state! [:water-sim :evaporation-mm-hr] rate)
    (send-params!)))

(defn set-infiltration-rate!
  "Set infiltration rate in mm/hour."
  [mm-hr]
  (let [rate (max 0 mm-hr)]
    (swap! sim-params assoc :infiltration-mm-hr rate)
    (state/set-state! [:water-sim :infiltration-mm-hr] rate)
    (send-params!)))

(defn set-flow-rate!
  "Set flow rate (fraction that flows per step, 0.01-0.5)."
  [rate]
  (let [clamped (-> rate (max 0.01) (min 0.5))]
    (swap! sim-params assoc :flow-rate clamped)
    (send-params!)))
