(ns garden.simulation.water-worker
  "Web Worker for water flow simulation.

   Runs the simulation loop in a background thread for performance.
   Uses pure JavaScript for the hot loop to minimize overhead."
  (:require [garden.simulation.water.physics :as physics]))

;; =============================================================================
;; State

(defonce ^:private state
  (atom {:water-grid nil        ; Float32Array - water heights (meters)
         :flow-grid nil         ; Float32Array - flow accumulation buffer
         :elevation nil         ; Float32Array - terrain elevation (meters)
         :width 0
         :height 0
         :cell-size 0.5         ; meters per cell

         ;; Simulation control
         :running? false
         :raining? false

         ;; Parameters (meters per step)
         :rain-rate 0.0
         :evaporation 0.0
         :infiltration 0.0
         :flow-rate 0.25
         :min-flow 0.0001}))

(defonce ^:private loop-id (atom nil))

;; =============================================================================
;; Simulation Steps

(defn- add-rain!
  "Add uniform rainfall across the terrain."
  []
  (let [{:keys [water-grid width height rain-rate]} @state]
    (when (and water-grid (pos? rain-rate))
      (let [n (* width height)]
        (dotimes [i n]
          (aset water-grid i (+ (aget water-grid i) rain-rate)))))))

(defn- simulate-flow!
  "Simulate water flow between cells.

   Flow physics:
   - Water flows downhill based on height differences
   - Flow rate scales with sqrt(slope) per Manning's equation
   - Flow is distributed proportionally to height differences
   - Edges drain water out of the system"
  []
  (let [{:keys [water-grid flow-grid elevation width height
                cell-size flow-rate min-flow]} @state]
    (when (and water-grid elevation (pos? width) (pos? height))
      ;; Pure JS for performance - this is the hot loop
      (js* "
        var wg = ~{water-grid};
        var fg = ~{flow-grid};
        var eg = ~{elevation};
        var W = ~{width};
        var H = ~{height};
        var cellSize = ~{cell-size};
        var baseFlow = ~{flow-rate};
        var minFlow = ~{min-flow};

        // Clear flow accumulator
        fg.fill(0);

        // Process each cell
        for (var y = 0; y < H; y++) {
          var row = y * W;
          var atTop = y === 0;
          var atBottom = y === H - 1;

          for (var x = 0; x < W; x++) {
            var i = row + x;
            var water = wg[i];
            if (water <= minFlow) continue;

            var atLeft = x === 0;
            var atRight = x === W - 1;
            var isEdge = atTop || atBottom || atLeft || atRight;

            var myHeight = eg[i] + water;
            var totalDiff = 0;
            var maxDiff = 0;

            // Neighbor indices and height differences
            var ni = [-1, -1, -1, -1];
            var nd = [0, 0, 0, 0];
            var nc = 0;

            // Check 4 neighbors
            var neighbors = [
              [!atLeft, i - 1],
              [!atRight, i + 1],
              [!atTop, i - W],
              [!atBottom, i + W]
            ];

            for (var n = 0; n < 4; n++) {
              if (neighbors[n][0]) {
                var j = neighbors[n][1];
                var diff = myHeight - (eg[j] + wg[j]);
                if (diff > minFlow) {
                  ni[nc] = j;
                  nd[nc] = diff;
                  totalDiff += diff;
                  if (diff > maxDiff) maxDiff = diff;
                  nc++;
                }
              }
            }

            // Edge cells drain out
            if (isEdge) {
              totalDiff += myHeight;
              if (myHeight > maxDiff) maxDiff = myHeight;
            }

            // Calculate and distribute flow
            if (totalDiff > 0) {
              // Flow rate scales with sqrt(slope) - Manning's equation
              var slope = maxDiff / cellSize;
              var flowMult = 1.0 + Math.sqrt(slope);
              var rate = Math.min(0.95, baseFlow * flowMult);
              var outflow = water * rate;

              // Distribute to neighbors
              for (var n = 0; n < nc; n++) {
                fg[ni[n]] += outflow * nd[n] / totalDiff;
              }

              // Remove outflow from source
              wg[i] = water - outflow;
            }
          }
        }

        // Apply accumulated inflow
        for (var i = 0; i < W * H; i++) {
          wg[i] += fg[i];
        }
      "))))

(defn- apply-evaporation!
  "Remove water through evaporation."
  []
  (let [{:keys [water-grid width height evaporation]} @state]
    (when (and water-grid (pos? evaporation))
      (let [n (* width height)]
        (dotimes [i n]
          (let [w (aget water-grid i)]
            (when (pos? w)
              (aset water-grid i (max 0 (- w evaporation))))))))))

(defn- apply-infiltration!
  "Remove water through ground infiltration."
  []
  (let [{:keys [water-grid width height infiltration]} @state]
    (when (and water-grid (pos? infiltration))
      (let [n (* width height)]
        (dotimes [i n]
          (let [w (aget water-grid i)]
            (when (pos? w)
              (aset water-grid i (max 0 (- w infiltration))))))))))

(defn- simulation-step!
  "Run one complete simulation step."
  []
  (when (:raining? @state)
    (add-rain!))
  (simulate-flow!)
  (apply-evaporation!)
  (apply-infiltration!))

;; =============================================================================
;; Communication

(defn- send-water-grid!
  "Send current water grid to main thread."
  []
  (when-let [grid (:water-grid @state)]
    (let [copy (js/Float32Array. grid)]
      (js/postMessage #js {:type "water-update" :grid copy}
                      #js [(.-buffer copy)]))))

;; =============================================================================
;; Simulation Loop

(defn- run-loop!
  "Main simulation loop - runs at ~20 steps/second."
  []
  (when (:running? @state)
    (simulation-step!)
    (send-water-grid!)
    (reset! loop-id (js/setTimeout run-loop! physics/simulation-interval-ms))))

(defn- stop-loop!
  "Stop the simulation loop."
  []
  (when-let [id @loop-id]
    (js/clearTimeout id)
    (reset! loop-id nil)))

;; =============================================================================
;; Message Handlers

(defmulti handle-message
  "Handle messages from main thread."
  (fn [data] (.-type data)))

(defmethod handle-message "init" [data]
  (let [elevation (.-elevationData data)
        width (.-width data)
        height (.-height data)
        cell-size (or (.-cellSize data) 0.5)
        n (* width height)]
    (swap! state assoc
           :elevation elevation
           :water-grid (js/Float32Array. n)
           :flow-grid (js/Float32Array. n)
           :width width
           :height height
           :cell-size cell-size)
    (js/postMessage #js {:type "ready"})))

(defmethod handle-message "start" [_]
  (swap! state assoc :running? true)
  (run-loop!))

(defmethod handle-message "stop" [_]
  (swap! state assoc :running? false)
  (stop-loop!))

(defmethod handle-message "start-rain" [_]
  (swap! state assoc :raining? true))

(defmethod handle-message "stop-rain" [_]
  (swap! state assoc :raining? false))

(defmethod handle-message "reset" [_]
  (when-let [grid (:water-grid @state)]
    (.fill grid 0)
    (send-water-grid!)))

(defmethod handle-message "set-params" [data]
  (swap! state merge
         {:rain-rate (or (.-rainRate data) 0)
          :evaporation (or (.-evaporation data) 0)
          :infiltration (or (.-infiltration data) 0)
          :flow-rate (or (.-flowRate data) 0.25)}))

(defmethod handle-message :default [data]
  (js/console.warn "Unknown message type:" (.-type data)))

;; =============================================================================
;; Worker Entry Point

(defn init
  "Initialize the worker. Called by shadow-cljs on load."
  []
  (js/self.addEventListener "message" #(handle-message (.-data %)))
  (js/postMessage #js {:type "loaded"}))
