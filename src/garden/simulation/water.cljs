(ns garden.simulation.water
  "Water flow simulation on terrain.
   Uses a grid-based approach where water flows to lower neighbors.
   All rates use SI units (mm/hour) and are converted based on simulation timestep."
  (:require [garden.state :as state]))

;; Simulation timestep in milliseconds
(def ^:private sim-interval-ms 100)  ; 10 steps per second

;; Convert mm/hour to meters per step
;; mm/hr * (1m/1000mm) * (1hr/3600000ms) * (step_ms)
(defn- mm-hr->m-per-step [mm-hr]
  (* mm-hr (/ 1 1000) (/ 1 3600000) sim-interval-ms))

;; Simulation state
(defonce ^:private sim-state
  (atom {:water-grid nil      ; Float32Array of water heights (meters)
         :flow-grid nil       ; Float32Array for flow accumulation
         :width 0
         :height 0
         :cell-area 1.0       ; Area of each cell in m² (for volume calculations)
         :running? false      ; Is the simulation loop active?
         :raining? false      ; Is rain being added?
         ;; SI unit configuration (synced from app-state)
         :rain-rate-mm-hr 10.0      ; mm/hour
         :evaporation-mm-hr 5.0     ; mm/hour
         :infiltration-mm-hr 0.0    ; mm/hour (water absorbed by ground)
         ;; Flow parameters
         :flow-rate 0.25      ; Fraction of excess water that flows per step
         :min-flow 0.0001}))  ; Minimum water height to trigger flow (meters)

(defn- create-water-grid!
  "Create water grid matching terrain dimensions."
  []
  (let [topo (state/topo-data)
        w (:width topo)
        h (:height topo)
        resolution (or (:resolution topo) 100)]  ; cm per cell
    (when (and w h (pos? w) (pos? h))
      ;; Cell area in m² = (cm/cell * 0.01)²
      (let [cell-size-m (* resolution 0.01)
            cell-area (* cell-size-m cell-size-m)]
        (swap! sim-state assoc
               :water-grid (js/Float32Array. (* w h))
               :flow-grid (js/Float32Array. (* w h))
               :width w
               :height h
               :cell-area cell-area)))))

(defn- idx
  "Convert x,y to array index."
  ^number [^number x ^number y ^number w]
  (+ x (* y w)))

(defn- add-rain!
  "Add rain to the terrain uniformly."
  [water-grid w h rain-m-per-step]
  (let [n (* w h)]
    (dotimes [i n]
      (aset water-grid i (+ (aget water-grid i) rain-m-per-step)))))

(defn- simulate-flow-step!
  "Simulate one step of water flow using optimized typed array operations.
   Water flows from higher cells to lower neighbors.
   Edge cells can drain water off the grid."
  []
  (let [{:keys [water-grid flow-grid width height flow-rate min-flow]} @sim-state
        topo (state/topo-data)
        elev-data (:elevation-data topo)
        w width
        h height]
    (when (and water-grid elev-data (pos? w) (pos? h))
      ;; Clear flow accumulation grid efficiently
      (.fill flow-grid 0)

      ;; Pre-calculate bounds for edge detection
      (let [max-x (dec w)
            max-y (dec h)]
        ;; Process all cells
        (dotimes [y h]
          (let [y-offset (* y w)
                is-y-edge (or (zero? y) (= y max-y))]
            (dotimes [x w]
              (let [i (+ x y-offset)
                    my-water (aget water-grid i)]
                ;; Only process cells with significant water
                (when (> my-water min-flow)
                  (let [my-elev (aget elev-data i)
                        my-total (+ my-elev my-water)
                        is-edge (or is-y-edge (zero? x) (= x max-x))
                        ;; Check neighbors and accumulate flow info
                        ;; Using mutable state for performance
                        total-diff (volatile! 0)
                        neighbors (volatile! (transient []))]

                    ;; Check left neighbor
                    (when (pos? x)
                      (let [ni (dec i)
                            n-total (+ (aget elev-data ni) (aget flow-grid ni) (aget water-grid ni))
                            diff (- my-total n-total)]
                        (when (> diff min-flow)
                          (vswap! total-diff + diff)
                          (vswap! neighbors conj! [ni diff]))))

                    ;; Check right neighbor
                    (when (< x max-x)
                      (let [ni (inc i)
                            n-total (+ (aget elev-data ni) (aget flow-grid ni) (aget water-grid ni))
                            diff (- my-total n-total)]
                        (when (> diff min-flow)
                          (vswap! total-diff + diff)
                          (vswap! neighbors conj! [ni diff]))))

                    ;; Check top neighbor
                    (when (pos? y)
                      (let [ni (- i w)
                            n-total (+ (aget elev-data ni) (aget flow-grid ni) (aget water-grid ni))
                            diff (- my-total n-total)]
                        (when (> diff min-flow)
                          (vswap! total-diff + diff)
                          (vswap! neighbors conj! [ni diff]))))

                    ;; Check bottom neighbor
                    (when (< y max-y)
                      (let [ni (+ i w)
                            n-total (+ (aget elev-data ni) (aget flow-grid ni) (aget water-grid ni))
                            diff (- my-total n-total)]
                        (when (> diff min-flow)
                          (vswap! total-diff + diff)
                          (vswap! neighbors conj! [ni diff]))))

                    ;; Edge drain (to "sea level" = 0)
                    (let [edge-diff (when is-edge my-total)
                          final-total-diff (+ @total-diff (or edge-diff 0))
                          neighbor-vec (persistent! @neighbors)]
                      (when (pos? final-total-diff)
                        (let [outflow (* my-water flow-rate)]
                          ;; Distribute to neighbors
                          (doseq [[ni diff] neighbor-vec]
                            (let [share (* outflow (/ diff final-total-diff))]
                              (aset flow-grid ni (+ (aget flow-grid ni) share))))
                          ;; Remove outflow from this cell
                          (aset water-grid i (- my-water outflow)))))))))))

        ;; Add accumulated inflow to water grid
        (let [n (* w h)]
          (dotimes [i n]
            (aset water-grid i (+ (aget water-grid i) (aget flow-grid i)))))))))

(defn- apply-evaporation!
  "Remove water through evaporation."
  [evap-m-per-step]
  (let [{:keys [water-grid width height]} @sim-state]
    (when (and water-grid (pos? evap-m-per-step))
      (let [n (* width height)]
        (dotimes [i n]
          (let [w (aget water-grid i)]
            (when (pos? w)
              (aset water-grid i (max 0 (- w evap-m-per-step))))))))))

(defn- apply-infiltration!
  "Remove water through ground infiltration."
  [infil-m-per-step]
  (let [{:keys [water-grid width height]} @sim-state]
    (when (and water-grid (pos? infil-m-per-step))
      (let [n (* width height)]
        (dotimes [i n]
          (let [w (aget water-grid i)]
            (when (pos? w)
              (aset water-grid i (max 0 (- w infil-m-per-step))))))))))

(defn- sync-rates-from-state!
  "Sync SI unit rates from app-state."
  []
  (let [rain-mm-hr (or (state/get-state :water-sim :rain-rate-mm-hr) 10.0)
        evap-mm-hr (or (state/get-state :water-sim :evaporation-mm-hr) 5.0)
        infil-mm-hr (or (state/get-state :water-sim :infiltration-mm-hr) 0.0)]
    (swap! sim-state assoc
           :rain-rate-mm-hr rain-mm-hr
           :evaporation-mm-hr evap-mm-hr
           :infiltration-mm-hr infil-mm-hr)))

(defn simulation-step!
  "Run one simulation step: rain (if active) + flow + evaporation + infiltration."
  []
  (let [{:keys [water-grid raining? width height
                rain-rate-mm-hr evaporation-mm-hr infiltration-mm-hr]} @sim-state]
    (when water-grid
      ;; Convert SI units to meters per step
      (let [rain-m (mm-hr->m-per-step rain-rate-mm-hr)
            evap-m (mm-hr->m-per-step evaporation-mm-hr)
            infil-m (mm-hr->m-per-step infiltration-mm-hr)]
        (when raining?
          (add-rain! water-grid width height rain-m))
        (simulate-flow-step!)
        (apply-evaporation! evap-m)
        (apply-infiltration! infil-m)))))

(defn start-simulation!
  "Start the water simulation (flow processing)."
  []
  (when-not (:water-grid @sim-state)
    (create-water-grid!))
  (sync-rates-from-state!)
  (swap! sim-state assoc :running? true)
  (state/set-state! [:water-sim :running?] true))

(defn stop-simulation!
  "Stop the water simulation completely."
  []
  (swap! sim-state assoc :running? false :raining? false)
  (state/set-state! [:water-sim :running?] false)
  (state/set-state! [:water-sim :raining?] false))

(defn start-rain!
  "Start adding rain (also starts simulation if not running)."
  []
  (when-not (:water-grid @sim-state)
    (create-water-grid!))
  (sync-rates-from-state!)
  (swap! sim-state assoc :running? true :raining? true)
  (state/set-state! [:water-sim :running?] true)
  (state/set-state! [:water-sim :raining?] true))

(defn stop-rain!
  "Stop adding rain but keep simulation running so water flows away."
  []
  (swap! sim-state assoc :raining? false)
  (state/set-state! [:water-sim :raining?] false))

(defn raining?
  "Check if rain is currently being added."
  []
  (state/get-state :water-sim :raining?))

(defn reset-water!
  "Clear all water from the grid."
  []
  (when-let [grid (:water-grid @sim-state)]
    (.fill grid 0)))

(defn running?
  "Check if simulation is running (for UI reactivity)."
  []
  (state/get-state :water-sim :running?))

(defn sim-running?
  "Check if simulation is running (internal, for loop)."
  []
  (:running? @sim-state))

(defn water-grid
  "Get the current water grid (Float32Array of water heights in meters)."
  []
  (:water-grid @sim-state))

(defn water-bounds
  "Get min/max water heights for visualization (in meters)."
  []
  (when-let [grid (:water-grid @sim-state)]
    (let [n (.-length grid)]
      (loop [i 0 min-w js/Infinity max-w 0]
        (if (>= i n)
          [min-w max-w]
          (let [w (aget grid i)]
            (recur (inc i)
                   (if (pos? w) (min min-w w) min-w)
                   (max max-w w))))))))

(defn grid-dimensions
  "Get water grid dimensions [width height]."
  []
  [(:width @sim-state) (:height @sim-state)])

(defn get-sim-interval
  "Get the simulation interval in milliseconds."
  []
  sim-interval-ms)

;; Configuration setters (update both local and app-state)

(defn set-rain-rate!
  "Set rain rate in mm/hour."
  [mm-hr]
  (let [rate (max 0 mm-hr)]
    (swap! sim-state assoc :rain-rate-mm-hr rate)
    (state/set-state! [:water-sim :rain-rate-mm-hr] rate)))

(defn set-evaporation-rate!
  "Set evaporation rate in mm/hour."
  [mm-hr]
  (let [rate (max 0 mm-hr)]
    (swap! sim-state assoc :evaporation-mm-hr rate)
    (state/set-state! [:water-sim :evaporation-mm-hr] rate)))

(defn set-infiltration-rate!
  "Set infiltration rate in mm/hour."
  [mm-hr]
  (let [rate (max 0 mm-hr)]
    (swap! sim-state assoc :infiltration-mm-hr rate)
    (state/set-state! [:water-sim :infiltration-mm-hr] rate)))

(defn set-flow-rate!
  "Set the flow rate (fraction that flows per step, 0.01-0.5)."
  [rate]
  (swap! sim-state assoc :flow-rate (max 0.01 (min 0.5 rate))))

;; Deprecated - use set-evaporation-rate! instead
(defn set-evaporation!
  "Deprecated: Set evaporation rate (old interface)."
  [rate]
  ;; Convert old rate to approximate mm/hr (assuming 20 steps/sec and rate was m/step)
  (set-evaporation-rate! (* rate 20 3600 1000)))
