(ns garden.canvas.terrain3d
  "3D terrain visualization using Three.js.

   Provides a first-person view of terrain with:
   - Minecraft-style WASD + mouse controls
   - Water simulation overlay
   - Dynamic geometry based on resolution settings"
  (:require [reagent.core :as r]
            [garden.state :as state]
            [garden.simulation.water :as water-sim]
            [garden.simulation.water.grid :as water-grid]
            ["three" :as THREE]))

;; =============================================================================
;; Constants

(def ^:private cm-per-unit
  "Conversion factor: centimeters per 3D unit.
   10 means 10cm = 1 unit, making terrain appear 10x larger."
  10)

(def ^:private min-camera-height
  "Minimum camera height above terrain in 3D units."
  10)

(def ^:private movement-speed
  "Base movement speed in 3D units per frame."
  0.5)

(def ^:private sprint-multiplier
  "Speed multiplier when sprinting."
  5)

(def ^:private mouse-sensitivity
  "Mouse look sensitivity."
  0.003)

;; =============================================================================
;; State

(defonce ^:private scene-state
  (atom {:scene nil
         :camera nil
         :renderer nil
         :terrain-mesh nil
         :water-mesh nil
         :water-geometry nil
         :animation-frame nil
         :pointer-locked? false}))

(defonce ^:private terrain-cache
  (atom {:segments-x 0
         :segments-y 0
         :water-segments-x 0
         :water-segments-y 0
         :data-width 0
         :data-height 0
         :elev-scale 1
         :min-elevation 0
         :world-width 0
         :world-height 0}))

(defonce ^:private camera-yaw (atom 0))
(defonce ^:private camera-pitch (atom 0))
(defonce ^:private keys-pressed (atom #{}))

;; =============================================================================
;; Coordinate Conversion

(defn- garden->3d
  "Convert garden coordinates (cm) to 3D coordinates.
   Garden: X=east, Y=north
   3D: X=east, Z=south (-Z=north), Y=up"
  [[x y]]
  [(/ x cm-per-unit)
   0  ; Y (elevation) handled separately
   (- (/ y cm-per-unit))])

(defn- threejs->garden
  "Convert 3D position to garden coordinates (cm)."
  [pos]
  [(* (.-x pos) cm-per-unit)
   (* (.-y pos) cm-per-unit)
   (* (- (.-z pos)) cm-per-unit)])

;; =============================================================================
;; Geometry Creation

(defn- create-terrain-geometry
  "Create terrain mesh geometry from elevation data."
  [topo-state]
  (let [{:keys [elevation-data width height bounds min-elevation max-elevation]} topo-state
        {:keys [min-x min-y max-x max-y]} bounds
        ;; World dimensions in 3D units
        world-width (/ (- max-x min-x) cm-per-unit)
        world-height (/ (- max-y min-y) cm-per-unit)
        ;; Calculate segments from resolution setting
        resolution-cm (or (state/graphics-resolution-cm) 50)
        [segments-x segments-y] (water-grid/calc-grid-dimensions bounds resolution-cm)
        geometry (THREE/PlaneGeometry. world-width world-height segments-x segments-y)
        positions (.-array (.-position (.-attributes geometry)))
        ;; Elevation scaling
        elev-range (- max-elevation min-elevation)
        elev-scale (if (pos? elev-range) (/ 50 elev-range) 1)]

    ;; Cache for water updates
    (reset! terrain-cache
            {:segments-x segments-x
             :segments-y segments-y
             :data-width width
             :data-height height
             :elev-scale elev-scale
             :min-elevation min-elevation
             :world-width world-width
             :world-height world-height})

    ;; Displace vertices by elevation
    (dotimes [i (inc segments-y)]
      (dotimes [j (inc segments-x)]
        (let [data-x (int (* (/ j segments-x) (dec width)))
              data-y (int (* (/ i segments-y) (dec height)))
              data-idx (+ data-x (* data-y width))
              elev (aget elevation-data data-idx)
              vert-idx (+ j (* i (inc segments-x)))
              z-idx (+ (* vert-idx 3) 2)]
          (aset positions z-idx
                (if (js/isNaN elev)
                  0
                  (* (- elev min-elevation) elev-scale))))))

    (.computeVertexNormals geometry)
    geometry))

(defn- create-water-geometry
  "Create water surface geometry matching terrain dimensions."
  [topo-state]
  (let [{:keys [bounds]} topo-state
        {:keys [min-x min-y max-x max-y]} bounds
        world-width (/ (- max-x min-x) cm-per-unit)
        world-height (/ (- max-y min-y) cm-per-unit)
        resolution-cm (or (state/graphics-resolution-cm) 50)
        [seg-x seg-y] (water-grid/calc-grid-dimensions bounds resolution-cm)]

    (swap! terrain-cache assoc
           :water-segments-x seg-x
           :water-segments-y seg-y)

    (THREE/PlaneGeometry. world-width world-height seg-x seg-y)))

(defn- create-rgb-texture
  "Create a Three.js texture from RGBA data."
  [rgb-data width height]
  (doto (THREE/DataTexture. rgb-data width height THREE/RGBAFormat)
    (-> .-needsUpdate (set! true))
    (-> .-flipY (set! true))
    (-> .-minFilter (set! THREE/LinearFilter))
    (-> .-magFilter (set! THREE/LinearFilter))))

;; =============================================================================
;; Water Updates

(defn- update-water-geometry!
  "Update water mesh vertices from simulation data."
  []
  (let [{:keys [water-geometry]} @scene-state
        water-grid-data (water-sim/water-grid)
        [sim-w sim-h] (water-sim/grid-dimensions)
        topo (state/topo-data)
        elev-data (:elevation-data topo)]

    (when (and water-geometry water-grid-data elev-data (pos? sim-w) (pos? sim-h))
      (let [{:keys [water-segments-x water-segments-y elev-scale min-elevation
                    data-width data-height]} @terrain-cache
            seg-x (or water-segments-x 512)
            seg-y (or water-segments-y 512)
            positions (.-array (.-position (.-attributes water-geometry)))
            water-scale (* elev-scale 10)]  ; Exaggerate for visibility

        (dotimes [i (inc seg-y)]
          (dotimes [j (inc seg-x)]
            (let [sim-x (int (* (/ j seg-x) (dec sim-w)))
                  sim-y (int (* (/ i seg-y) (dec sim-h)))
                  sim-idx (+ sim-x (* sim-y sim-w))
                  data-x (int (* (/ j seg-x) (dec data-width)))
                  data-y (int (* (/ i seg-y) (dec data-height)))
                  data-idx (+ data-x (* data-y data-width))
                  elev (aget elev-data data-idx)
                  water-h (aget water-grid-data sim-idx)
                  vert-idx (+ j (* i (inc seg-x)))
                  z-idx (+ (* vert-idx 3) 2)
                  base-z (if (js/isNaN elev) 0 (* (- elev min-elevation) elev-scale))
                  water-z (+ base-z (* water-h water-scale))]
              (aset positions z-idx
                    (if (> water-h 0.001) water-z js/NaN)))))

        (set! (.-needsUpdate (.-position (.-attributes water-geometry))) true)))))

;; =============================================================================
;; Camera Controls

(defn- update-camera-look!
  "Update camera orientation from yaw/pitch angles."
  []
  (when-let [camera (:camera @scene-state)]
    (let [yaw @camera-yaw
          pitch @camera-pitch
          pos (.-position camera)
          look-dist 100
          look-x (+ (.-x pos) (* look-dist (js/Math.sin yaw) (js/Math.cos pitch)))
          look-y (+ (.-y pos) (* look-dist (js/Math.sin pitch)))
          look-z (+ (.-z pos) (* look-dist (- (js/Math.cos yaw)) (js/Math.cos pitch)))]
      (.lookAt camera look-x look-y look-z))))

(defn- move-camera-forward!
  "Move camera along look direction."
  [delta]
  (when-let [camera (:camera @scene-state)]
    (let [pos (.-position camera)
          yaw @camera-yaw
          pitch @camera-pitch
          dx (* delta (js/Math.sin yaw) (js/Math.cos pitch))
          dy (* delta (js/Math.sin pitch))
          dz (* delta (- (js/Math.cos yaw)) (js/Math.cos pitch))]
      (.set pos
            (+ (.-x pos) dx)
            (max min-camera-height (+ (.-y pos) dy))
            (+ (.-z pos) dz))
      (update-camera-look!))))

(defn- strafe-camera!
  "Move camera perpendicular to look direction."
  [delta]
  (when-let [camera (:camera @scene-state)]
    (let [pos (.-position camera)
          yaw @camera-yaw
          dx (* delta (js/Math.cos yaw))
          dz (* delta (js/Math.sin yaw))]
      (.set pos (+ (.-x pos) dx) (.-y pos) (+ (.-z pos) dz))
      (update-camera-look!))))

(defn- adjust-camera-elevation!
  "Adjust camera height."
  [delta]
  (when-let [camera (:camera @scene-state)]
    (let [pos (.-position camera)
          new-y (+ (.-y pos) delta)]
      (when (> new-y min-camera-height)
        (.set pos (.-x pos) new-y (.-z pos))))))

(defn- process-movement!
  "Process camera movement from pressed keys."
  []
  (let [keys @keys-pressed
        sprinting? (or (keys "ShiftLeft") (keys "ShiftRight"))
        delta (* movement-speed (if sprinting? sprint-multiplier 1))]
    (when (keys "KeyW") (move-camera-forward! delta))
    (when (keys "KeyS") (move-camera-forward! (- delta)))
    (when (keys "KeyA") (strafe-camera! (- delta)))
    (when (keys "KeyD") (strafe-camera! delta))
    (when (keys "KeyQ") (adjust-camera-elevation! (- delta)))
    (when (keys "KeyE") (adjust-camera-elevation! delta))))

(defn- update-camera-state!
  "Sync camera state to app-state for status bar."
  [camera]
  (when camera
    (let [pos (.-position camera)
          [cam-x cam-y cam-z] (threejs->garden pos)
          dir (THREE/Vector3.)
          _ (.getWorldDirection camera dir)
          angle (js/Math.atan2 (.-x dir) (- (.-z dir)))
          degrees (mod (+ (* angle (/ 180 js/Math.PI)) 360) 360)
          cardinal (cond
                     (or (>= degrees 337.5) (< degrees 22.5)) "N"
                     (< degrees 67.5) "NE"
                     (< degrees 112.5) "E"
                     (< degrees 157.5) "SE"
                     (< degrees 202.5) "S"
                     (< degrees 247.5) "SW"
                     (< degrees 292.5) "W"
                     :else "NW")]
      (state/set-state! [:camera-3d]
                        {:position [cam-x cam-y cam-z]
                         :direction [(Math/round degrees) cardinal]}))))

;; =============================================================================
;; Event Handlers

(defn- handle-mousemove! [e]
  (when (some-> @scene-state :renderer .-domElement (= (.-pointerLockElement js/document)))
    (swap! camera-yaw + (* (.-movementX e) mouse-sensitivity))
    (swap! camera-pitch #(-> (- % (* (.-movementY e) mouse-sensitivity))
                             (max -1.5) (min 1.5)))
    (update-camera-look!)))

(defn- handle-click! [_]
  (when-let [canvas (some-> @scene-state :renderer .-domElement)]
    (.requestPointerLock canvas)))

(defn- handle-pointer-lock-change! []
  (let [locked? (some-> @scene-state :renderer .-domElement
                        (= (.-pointerLockElement js/document)))]
    (swap! scene-state assoc :pointer-locked? locked?)))

(defn- handle-keydown! [e]
  (swap! keys-pressed conj (.-code e)))

(defn- handle-keyup! [e]
  (swap! keys-pressed disj (.-code e)))

;; =============================================================================
;; Scene Management

(defn- get-elevation-at-3d-point
  "Get terrain elevation at a 3D coordinate."
  [topo-state x z]
  (let [{:keys [elevation-data width height bounds min-elevation]} topo-state
        {:keys [min-x min-y max-x max-y]} bounds
        {:keys [elev-scale]} @terrain-cache
        garden-x (* x cm-per-unit)
        garden-y (* (- z) cm-per-unit)
        world-width (- max-x min-x)
        world-height (- max-y min-y)
        data-x (-> (/ (- garden-x min-x) world-width) (* (dec width)) int (max 0) (min (dec width)))
        data-y (-> (/ (- garden-y min-y) world-height) (* (dec height)) int (max 0) (min (dec height)))
        elev (aget elevation-data (+ data-x (* data-y width)))]
    (if (or (nil? elev) (js/isNaN elev))
      0
      (* (- elev min-elevation) elev-scale))))

(defn- create-scene!
  "Initialize the Three.js scene."
  [container topo-state]
  (let [width (.-clientWidth container)
        height (.-clientHeight container)
        ;; Core objects
        scene (doto (THREE/Scene.)
                (-> .-background (set! (THREE/Color. 0x87CEEB))))
        camera (THREE/PerspectiveCamera. 60 (/ width height) 0.1 10000)
        renderer (doto (THREE/WebGLRenderer. #js {:antialias true})
                   (.setSize width height)
                   (.setPixelRatio (.-devicePixelRatio js/window)))
        _ (.appendChild container (.-domElement renderer))

        ;; Lighting
        has-texture? (some? (:rgb-data topo-state))
        ambient (THREE/AmbientLight. 0xffffff (if has-texture? 0.8 0.4))
        sun (doto (THREE/DirectionalLight. 0xffffff (if has-texture? 0.4 0.8))
              (-> .-position (.set 50 50 100)))
        _ (.add scene ambient)
        _ (.add scene sun)

        ;; Terrain mesh
        terrain-geom (create-terrain-geometry topo-state)
        terrain-mat (if has-texture?
                      (THREE/MeshLambertMaterial.
                       #js {:map (create-rgb-texture (:rgb-data topo-state)
                                                     (:width topo-state)
                                                     (:height topo-state))
                            :side THREE/DoubleSide})
                      (THREE/MeshLambertMaterial.
                       #js {:color 0x8B7355
                            :side THREE/DoubleSide}))
        terrain-mesh (doto (THREE/Mesh. terrain-geom terrain-mat)
                       (.rotateX (- (/ js/Math.PI 2))))
        _ (.add scene terrain-mesh)

        ;; Water mesh
        water-geom (create-water-geometry topo-state)
        water-mat (THREE/MeshLambertMaterial.
                   #js {:color 0x1E90FF
                        :transparent true
                        :opacity 0.6
                        :side THREE/DoubleSide})
        water-mesh (doto (THREE/Mesh. water-geom water-mat)
                     (.rotateX (- (/ js/Math.PI 2))))
        _ (.add scene water-mesh)

        ;; Initial camera position from 2D viewport
        viewport (state/viewport)
        [ox oy] (:offset viewport)
        zoom (:zoom viewport)
        vp-size (:size viewport)
        center-x (/ (- (/ (:width vp-size) 2) ox) zoom)
        center-y (/ (- (/ (:height vp-size) 2) oy) zoom)
        cam-x (/ center-x cm-per-unit)
        cam-z (- (/ center-y cm-per-unit))
        cam-y (+ (get-elevation-at-3d-point topo-state cam-x cam-z) 20)]

    (.set (.-position camera) cam-x cam-y cam-z)
    (reset! camera-yaw 0)
    (reset! camera-pitch 0)
    (update-camera-look!)

    (reset! scene-state
            {:scene scene
             :camera camera
             :renderer renderer
             :terrain-mesh terrain-mesh
             :water-mesh water-mesh
             :water-geometry water-geom
             :animation-frame nil
             :pointer-locked? false})

    ;; Animation loop
    (letfn [(animate []
              (let [{:keys [renderer scene camera]} @scene-state]
                (when renderer
                  (process-movement!)
                  (when (water-sim/sim-running?)
                    (update-water-geometry!))
                  (update-camera-state! camera)
                  (.render renderer scene camera)
                  (swap! scene-state assoc :animation-frame
                         (js/requestAnimationFrame animate)))))]
      (animate))))

(defn- dispose-scene!
  "Clean up Three.js resources."
  []
  (let [{:keys [renderer terrain-mesh water-mesh animation-frame]} @scene-state]
    (when animation-frame
      (js/cancelAnimationFrame animation-frame))
    (when terrain-mesh
      (.dispose (.-geometry terrain-mesh))
      (when-let [mat (.-material terrain-mesh)]
        (when-let [tex (.-map mat)] (.dispose tex))
        (.dispose mat)))
    (when water-mesh
      (.dispose (.-geometry water-mesh))
      (when-let [mat (.-material water-mesh)] (.dispose mat)))
    (when renderer
      (.dispose renderer)
      (when-let [canvas (.-domElement renderer)]
        (when-let [parent (.-parentElement canvas)]
          (.removeChild parent canvas)))))
  (reset! scene-state
          {:scene nil :camera nil :renderer nil
           :terrain-mesh nil :water-mesh nil :water-geometry nil
           :animation-frame nil :pointer-locked? false}))

(defn- handle-resize!
  "Handle container resize."
  [container]
  (let [{:keys [camera renderer]} @scene-state
        width (.-clientWidth container)
        height (.-clientHeight container)]
    (when (and camera renderer (pos? width) (pos? height))
      (set! (.-aspect camera) (/ width height))
      (.updateProjectionMatrix camera)
      (.setSize renderer width height))))

;; =============================================================================
;; Component

(defn terrain-3d-component
  "3D terrain viewer with first-person controls.

   Controls:
   - Click to enable mouse look (Esc to exit)
   - WASD: Move forward/back/left/right
   - Q/E: Move down/up
   - Shift: Sprint"
  []
  (let [container-ref (atom nil)
        resize-observer (atom nil)]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (when-let [container @container-ref]
          (when-let [topo (state/topo-data)]
            (when (:elevation-data topo)
              (create-scene! container topo)
              ;; Resize handling
              (let [observer (js/ResizeObserver. #(handle-resize! container))]
                (.observe observer container)
                (reset! resize-observer observer))
              ;; Event listeners
              (.addEventListener js/window "keydown" handle-keydown!)
              (.addEventListener js/window "keyup" handle-keyup!)
              (.addEventListener js/document "mousemove" handle-mousemove!)
              (.addEventListener js/document "pointerlockchange" handle-pointer-lock-change!)
              (when-let [canvas (some-> @scene-state :renderer .-domElement)]
                (.addEventListener canvas "click" handle-click!))))))

      :component-will-unmount
      (fn [_]
        (when-let [observer @resize-observer]
          (.disconnect observer))
        (.removeEventListener js/window "keydown" handle-keydown!)
        (.removeEventListener js/window "keyup" handle-keyup!)
        (reset! keys-pressed #{})
        (.removeEventListener js/document "mousemove" handle-mousemove!)
        (.removeEventListener js/document "pointerlockchange" handle-pointer-lock-change!)
        (when-let [canvas (some-> @scene-state :renderer .-domElement)]
          (.removeEventListener canvas "click" handle-click!))
        (when (.-pointerLockElement js/document)
          (.exitPointerLock js/document))
        (dispose-scene!))

      :reagent-render
      (fn []
        [:div.terrain-3d-container
         {:ref #(reset! container-ref %)
          :tab-index 0
          :style {:width "100%" :height "100%" :background "#87CEEB"}}
         (when-not (:pointer-locked? @scene-state)
           [:div {:style {:position "absolute"
                          :top "50%" :left "50%"
                          :transform "translate(-50%, -50%)"
                          :color "white"
                          :background "rgba(0,0,0,0.6)"
                          :padding "12px 24px"
                          :border-radius "8px"
                          :pointer-events "none"
                          :font-size "14px"}}
            "Click to look around (Esc to exit)"])])})))

;; =============================================================================
;; Public API

(defn has-terrain-data?
  "Check if elevation data is available for 3D rendering."
  []
  (some? (state/topo-elevation-data)))

(defn get-camera-position
  "Get camera position as [x y z] in garden coordinates (cm)."
  []
  (when-let [camera (:camera @scene-state)]
    (threejs->garden (.-position camera))))

(defn get-camera-direction
  "Get camera direction as [degrees cardinal]."
  []
  (when-let [camera (:camera @scene-state)]
    (let [dir (THREE/Vector3.)
          _ (.getWorldDirection camera dir)
          angle (js/Math.atan2 (.-x dir) (.-y dir))
          degrees (mod (+ (* angle (/ 180 js/Math.PI)) 360) 360)
          cardinal (cond
                     (or (>= degrees 337.5) (< degrees 22.5)) "N"
                     (< degrees 67.5) "NE"
                     (< degrees 112.5) "E"
                     (< degrees 157.5) "SE"
                     (< degrees 202.5) "S"
                     (< degrees 247.5) "SW"
                     (< degrees 292.5) "W"
                     :else "NW")]
      [(Math/round degrees) cardinal])))
