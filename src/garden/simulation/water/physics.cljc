(ns garden.simulation.water.physics
  "Physics constants and formulas for water flow simulation.

   Flow model based on simplified Manning's equation where velocity
   is proportional to the square root of slope.")

;; -----------------------------------------------------------------------------
;; Simulation Constants

(def ^:const simulation-interval-ms
  "Time between simulation steps in milliseconds."
  50)

(def ^:const steps-per-second
  "Number of simulation steps per second."
  (/ 1000 simulation-interval-ms))

(def ^:const base-flow-rate
  "Base fraction of water that flows per step on flat terrain."
  0.25)

(def ^:const max-flow-rate
  "Maximum flow rate to maintain simulation stability."
  0.95)

(def ^:const min-flow-threshold
  "Minimum water height (meters) to consider for flow."
  0.0001)

;; -----------------------------------------------------------------------------
;; Unit Conversions

(defn mm-per-hour->m-per-step
  "Convert mm/hour to meters per simulation step."
  [mm-hr]
  (* mm-hr
     (/ 1 1000)                    ; mm -> m
     (/ 1 3600000)                 ; per hour -> per ms
     simulation-interval-ms))      ; per step

(defn m-per-step->mm-per-hour
  "Convert meters per simulation step to mm/hour."
  [m-per-step]
  (* m-per-step
     1000                          ; m -> mm
     3600000                       ; per ms -> per hour
     (/ 1 simulation-interval-ms))) ; per step -> per ms

;; -----------------------------------------------------------------------------
;; Flow Physics

(defn calc-slope
  "Calculate slope (dimensionless) from height difference and cell size.

   Arguments:
     height-diff - Height difference in meters
     cell-size   - Cell size in meters

   Returns slope as rise/run ratio.
   A slope of 1.0 = 45 degrees, 0.1 = ~6 degrees."
  [height-diff cell-size]
  {:pre [(pos? cell-size)]}
  (/ height-diff cell-size))

(defn slope->flow-rate
  "Calculate flow rate from slope using sqrt relationship.

   Based on Manning's equation where velocity ~ sqrt(slope).
   This provides realistic behavior where steeper slopes flow faster
   but with diminishing returns.

   Arguments:
     slope     - Dimensionless slope (rise/run)
     base-rate - Base flow rate for flat terrain (default: base-flow-rate)

   Returns flow rate clamped to [0, max-flow-rate]."
  ([slope]
   (slope->flow-rate slope base-flow-rate))
  ([slope base-rate]
   (let [slope-multiplier (+ 1.0 (#?(:clj Math/sqrt :cljs js/Math.sqrt) slope))]
     (min max-flow-rate (* base-rate slope-multiplier)))))

(defn flow-rate-for-height-diff
  "Calculate flow rate for a given height difference and cell size.

   Convenience function combining slope calculation and flow rate.

   Arguments:
     height-diff - Height difference in meters
     cell-size   - Cell size in meters

   Returns flow rate as fraction per step."
  [height-diff cell-size]
  (-> (calc-slope height-diff cell-size)
      slope->flow-rate))
