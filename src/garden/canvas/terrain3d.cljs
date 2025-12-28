(ns garden.canvas.terrain3d
  "3D terrain visualization using Three.js.

   Provides a first-person view of terrain with:
   - Minecraft-style WASD + mouse controls
   - Water simulation overlay
   - 3D plant representations based on growth habits
   - Dynamic geometry based on resolution settings"
  (:require [reagent.core :as r]
            [garden.state :as state]
            [garden.simulation.water :as water-sim]
            [garden.simulation.water.grid :as water-grid]
            [garden.data.plants :as plants]
            [garden.constants :as constants]
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
         :plant-group nil       ; Group containing all plant meshes
         :plant-meshes {}       ; Map of plant-id -> mesh
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
;; 3D Plant Creation
;;
;; Plants are represented as 3D meshes based on their growth habit.
;; Each habit type has a characteristic shape that's intuitive and pretty.

(defn- hex->color
  "Convert hex color string to Three.js Color."
  [hex]
  (let [hex-val (if (= (first hex) \#) (subs hex 1) hex)]
    (THREE/Color. (js/parseInt hex-val 16))))

(defn- create-columnar-plant!
  "Create a columnar plant (tall cylinder like cypress)."
  [height-3d radius-3d color]
  (let [group (THREE/Group.)
        ;; Trunk
        trunk-geom (THREE/CylinderGeometry. (* radius-3d 0.15) (* radius-3d 0.2) (* height-3d 0.3) 8)
        trunk-mat (THREE/MeshLambertMaterial. #js {:color 0x5D4037})
        trunk (THREE/Mesh. trunk-geom trunk-mat)
        ;; Foliage - tall cone
        foliage-geom (THREE/ConeGeometry. radius-3d (* height-3d 0.9) 8)
        foliage-mat (THREE/MeshLambertMaterial. #js {:color (hex->color color)})
        foliage (THREE/Mesh. foliage-geom foliage-mat)]
    (.set (.-position trunk) 0 (* height-3d 0.15) 0)
    (.set (.-position foliage) 0 (* height-3d 0.55) 0)
    (.add group trunk)
    (.add group foliage)
    group))

(defn- create-spreading-plant!
  "Create a spreading plant (wide crown like oak)."
  [height-3d radius-3d color]
  (let [group (THREE/Group.)
        ;; Trunk
        trunk-geom (THREE/CylinderGeometry. (* radius-3d 0.12) (* radius-3d 0.18) (* height-3d 0.4) 8)
        trunk-mat (THREE/MeshLambertMaterial. #js {:color 0x5D4037})
        trunk (THREE/Mesh. trunk-geom trunk-mat)
        ;; Foliage - multiple spheres for organic shape
        foliage-mat (THREE/MeshLambertMaterial. #js {:color (hex->color color)})
        main-geom (THREE/SphereGeometry. radius-3d 12 8)
        main (THREE/Mesh. main-geom foliage-mat)
        left-geom (THREE/SphereGeometry. (* radius-3d 0.7) 10 6)
        left (THREE/Mesh. left-geom foliage-mat)
        right (THREE/Mesh. left-geom foliage-mat)]
    (.set (.-position trunk) 0 (* height-3d 0.2) 0)
    (.set (.-position main) 0 (* height-3d 0.6) 0)
    (.set (.-position left) (* radius-3d -0.6) (* height-3d 0.5) (* radius-3d 0.3))
    (.set (.-position right) (* radius-3d 0.6) (* height-3d 0.5) (* radius-3d -0.3))
    (.add group trunk)
    (.add group main)
    (.add group left)
    (.add group right)
    group))

(defn- create-mounding-plant!
  "Create a mounding plant (dome shape like shrubs)."
  [height-3d radius-3d color bloom-color]
  (let [group (THREE/Group.)
        ;; Main dome
        dome-geom (THREE/SphereGeometry. radius-3d 12 8 0 constants/TWO-PI 0 (/ Math/PI 2))
        dome-mat (THREE/MeshLambertMaterial. #js {:color (hex->color color)})
        dome (THREE/Mesh. dome-geom dome-mat)]
    (.set (.-position dome) 0 0 0)
    (.add group dome)
    ;; Add blooms if present
    (when bloom-color
      (let [bloom-mat (THREE/MeshLambertMaterial. #js {:color (hex->color bloom-color)})
            bloom-geom (THREE/SphereGeometry. (* radius-3d 0.15) 6 4)]
        (dotimes [i 5]
          (let [angle (* i (/ constants/TWO-PI 5))
                bloom (THREE/Mesh. bloom-geom bloom-mat)]
            (.set (.-position bloom)
                  (* radius-3d 0.6 (Math/cos angle))
                  (* height-3d 0.7)
                  (* radius-3d 0.6 (Math/sin angle)))
            (.add group bloom)))))
    group))

(defn- create-upright-plant!
  "Create an upright plant (vertical oval)."
  [height-3d radius-3d color bloom-color]
  (let [group (THREE/Group.)
        ;; Trunk for trees, skip for smaller plants
        tall? (> height-3d 3)
        _ (when tall?
            (let [trunk-geom (THREE/CylinderGeometry. (* radius-3d 0.1) (* radius-3d 0.15) (* height-3d 0.3) 8)
                  trunk-mat (THREE/MeshLambertMaterial. #js {:color 0x5D4037})
                  trunk (THREE/Mesh. trunk-geom trunk-mat)]
              (.set (.-position trunk) 0 (* height-3d 0.15) 0)
              (.add group trunk)))
        ;; Vertical oval foliage
        foliage-geom (THREE/SphereGeometry. radius-3d 10 8)
        foliage-mat (THREE/MeshLambertMaterial. #js {:color (hex->color color)})
        foliage (THREE/Mesh. foliage-geom foliage-mat)]
    (set! (.-y (.-scale foliage)) 1.3)
    (.set (.-position foliage) 0 (if tall? (* height-3d 0.55) (* height-3d 0.5)) 0)
    (.add group foliage)
    ;; Blooms at top
    (when bloom-color
      (let [bloom-mat (THREE/MeshLambertMaterial. #js {:color (hex->color bloom-color)})
            bloom-geom (THREE/SphereGeometry. (* radius-3d 0.2) 6 4)
            bloom (THREE/Mesh. bloom-geom bloom-mat)]
        (.set (.-position bloom) 0 (* height-3d 0.85) 0)
        (.add group bloom)))
    group))

(defn- create-weeping-plant!
  "Create a weeping plant (drooping branches like willow)."
  [height-3d radius-3d color]
  (let [group (THREE/Group.)
        ;; Trunk
        trunk-geom (THREE/CylinderGeometry. (* radius-3d 0.15) (* radius-3d 0.25) (* height-3d 0.4) 8)
        trunk-mat (THREE/MeshLambertMaterial. #js {:color 0x5D4037})
        trunk (THREE/Mesh. trunk-geom trunk-mat)
        ;; Crown at top
        crown-geom (THREE/SphereGeometry. (* radius-3d 0.6) 10 8)
        crown-mat (THREE/MeshLambertMaterial. #js {:color (hex->color color)})
        crown (THREE/Mesh. crown-geom crown-mat)
        ;; Drooping "curtain" using elongated cones
        droop-mat (THREE/MeshLambertMaterial. #js {:color (hex->color color)})]
    (.set (.-position trunk) 0 (* height-3d 0.2) 0)
    (.set (.-position crown) 0 (* height-3d 0.55) 0)
    (.add group trunk)
    (.add group crown)
    ;; Add drooping branches
    (dotimes [i 8]
      (let [angle (* i (/ constants/TWO-PI 8))
            droop-geom (THREE/ConeGeometry. (* radius-3d 0.3) (* height-3d 0.5) 6)
            droop (THREE/Mesh. droop-geom droop-mat)]
        (.set (.-position droop)
              (* radius-3d 0.7 (Math/cos angle))
              (* height-3d 0.3)
              (* radius-3d 0.7 (Math/sin angle)))
        (.add group droop)))
    group))

(defn- create-rosette-plant!
  "Create a rosette plant (flat circular arrangement)."
  [height-3d radius-3d color bloom-color]
  (let [group (THREE/Group.)
        ;; Flat disc shape
        disc-geom (THREE/CylinderGeometry. radius-3d radius-3d (* height-3d 0.3) 16)
        disc-mat (THREE/MeshLambertMaterial. #js {:color (hex->color color)})
        disc (THREE/Mesh. disc-geom disc-mat)]
    (.set (.-position disc) 0 (* height-3d 0.15) 0)
    (.add group disc)
    ;; Add bloom center if present
    (when bloom-color
      (let [bloom-mat (THREE/MeshLambertMaterial. #js {:color (hex->color bloom-color)})
            bloom-geom (THREE/SphereGeometry. (* radius-3d 0.3) 8 6)
            bloom (THREE/Mesh. bloom-geom bloom-mat)]
        (.set (.-position bloom) 0 (* height-3d 0.4) 0)
        (.add group bloom)))
    group))

(defn- create-clumping-plant!
  "Create a clumping plant (grass-like vertical spikes)."
  [height-3d radius-3d color bloom-color]
  (let [group (THREE/Group.)
        spike-mat (THREE/MeshLambertMaterial. #js {:color (hex->color color)})]
    ;; Multiple vertical cones/cylinders
    (dotimes [i 7]
      (let [angle (* i (/ constants/TWO-PI 7))
            dist (* radius-3d 0.4 (if (zero? i) 0 1))
            spike-h (* height-3d (+ 0.7 (* 0.3 (Math/random))))
            spike-geom (THREE/ConeGeometry. (* radius-3d 0.15) spike-h 6)
            spike (THREE/Mesh. spike-geom spike-mat)]
        (.set (.-position spike)
              (* dist (Math/cos angle))
              (/ spike-h 2)
              (* dist (Math/sin angle)))
        (.add group spike)))
    ;; Plume tips if bloom color
    (when bloom-color
      (let [bloom-mat (THREE/MeshLambertMaterial. #js {:color (hex->color bloom-color)})
            bloom-geom (THREE/SphereGeometry. (* radius-3d 0.1) 6 4)]
        (dotimes [i 3]
          (let [angle (* i (/ constants/TWO-PI 3))
                bloom (THREE/Mesh. bloom-geom bloom-mat)]
            (.set (.-position bloom)
                  (* radius-3d 0.3 (Math/cos angle))
                  height-3d
                  (* radius-3d 0.3 (Math/sin angle)))
            (.add group bloom)))))
    group))

(defn- create-bushy-plant!
  "Create a bushy plant (irregular dense mass)."
  [height-3d radius-3d color bloom-color]
  (let [group (THREE/Group.)
        bush-mat (THREE/MeshLambertMaterial. #js {:color (hex->color color)})]
    ;; Multiple overlapping spheres
    (dotimes [i 5]
      (let [angle (* i (/ constants/TWO-PI 5))
            dist (if (zero? i) 0 (* radius-3d 0.4))
            size (* radius-3d (+ 0.5 (* 0.3 (if (zero? i) 1 (Math/random)))))
            sphere-geom (THREE/SphereGeometry. size 8 6)
            sphere (THREE/Mesh. sphere-geom bush-mat)]
        (.set (.-position sphere)
              (* dist (Math/cos angle))
              (* height-3d (+ 0.3 (* 0.2 (Math/random))))
              (* dist (Math/sin angle)))
        (.add group sphere)))
    ;; Blooms scattered on top
    (when bloom-color
      (let [bloom-mat (THREE/MeshLambertMaterial. #js {:color (hex->color bloom-color)})
            bloom-geom (THREE/SphereGeometry. (* radius-3d 0.12) 6 4)]
        (dotimes [i 6]
          (let [angle (* i (/ constants/TWO-PI 6))
                bloom (THREE/Mesh. bloom-geom bloom-mat)]
            (.set (.-position bloom)
                  (* radius-3d 0.5 (Math/cos angle))
                  (* height-3d 0.7)
                  (* radius-3d 0.5 (Math/sin angle)))
            (.add group bloom)))))
    group))

(defn- create-fountain-plant!
  "Create a fountain plant (arching from center like ornamental grass)."
  [height-3d radius-3d color bloom-color]
  (let [group (THREE/Group.)
        arch-mat (THREE/MeshLambertMaterial. #js {:color (hex->color color)})]
    ;; Arching blades represented as bent cones
    (dotimes [i 10]
      (let [angle (* i (/ constants/TWO-PI 10))
            arch-geom (THREE/ConeGeometry. (* radius-3d 0.08) (* height-3d 0.8) 4)
            arch (THREE/Mesh. arch-geom arch-mat)]
        ;; Tilt outward
        (.set (.-position arch)
              (* radius-3d 0.3 (Math/cos angle))
              (* height-3d 0.4)
              (* radius-3d 0.3 (Math/sin angle)))
        (set! (.-x (.-rotation arch)) (* 0.4 (Math/cos angle)))
        (set! (.-z (.-rotation arch)) (* -0.4 (Math/sin angle)))
        (.add group arch)))
    ;; Center base
    (let [base-geom (THREE/CylinderGeometry. (* radius-3d 0.2) (* radius-3d 0.25) (* height-3d 0.2) 8)
          base (THREE/Mesh. base-geom arch-mat)]
      (.set (.-position base) 0 (* height-3d 0.1) 0)
      (.add group base))
    ;; Plumes if bloom color
    (when bloom-color
      (let [bloom-mat (THREE/MeshLambertMaterial. #js {:color (hex->color bloom-color)})
            bloom-geom (THREE/SphereGeometry. (* radius-3d 0.15) 6 4)]
        (dotimes [i 5]
          (let [angle (* i (/ constants/TWO-PI 5))
                bloom (THREE/Mesh. bloom-geom bloom-mat)]
            (.set (.-position bloom)
                  (* radius-3d 0.8 (Math/cos angle))
                  (* height-3d 0.85)
                  (* radius-3d 0.8 (Math/sin angle)))
            (.add group bloom)))))
    group))

(defn- create-spiky-plant!
  "Create a spiky plant (radiating points like agave)."
  [height-3d radius-3d color]
  (let [group (THREE/Group.)
        spike-mat (THREE/MeshLambertMaterial. #js {:color (hex->color color)})]
    ;; Radiating spikes
    (dotimes [i 10]
      (let [angle (* i (/ constants/TWO-PI 10))
            spike-geom (THREE/ConeGeometry. (* radius-3d 0.12) (* height-3d 0.8) 4)
            spike (THREE/Mesh. spike-geom spike-mat)]
        (.set (.-position spike)
              (* radius-3d 0.3 (Math/cos angle))
              (* height-3d 0.35)
              (* radius-3d 0.3 (Math/sin angle)))
        ;; Tilt outward and up
        (set! (.-x (.-rotation spike)) (* 0.5 (Math/cos angle)))
        (set! (.-z (.-rotation spike)) (* -0.5 (Math/sin angle)))
        (.add group spike)))
    ;; Center rosette
    (let [center-geom (THREE/ConeGeometry. (* radius-3d 0.15) (* height-3d 0.5) 6)
          center (THREE/Mesh. center-geom spike-mat)]
      (.set (.-position center) 0 (* height-3d 0.25) 0)
      (.add group center))
    group))

(defn- create-vining-plant!
  "Create a vining plant (tendrils from center)."
  [height-3d radius-3d color bloom-color]
  (let [group (THREE/Group.)
        vine-mat (THREE/MeshLambertMaterial. #js {:color (hex->color color)})
        ;; Center mound
        center-geom (THREE/SphereGeometry. (* radius-3d 0.4) 8 6)
        center (THREE/Mesh. center-geom vine-mat)]
    (.set (.-position center) 0 (* height-3d 0.3) 0)
    (.add group center)
    ;; Trailing vines as thin cylinders
    (dotimes [i 6]
      (let [angle (* i (/ constants/TWO-PI 6))
            vine-geom (THREE/CylinderGeometry. (* radius-3d 0.05) (* radius-3d 0.03) radius-3d 4)
            vine (THREE/Mesh. vine-geom vine-mat)]
        (.set (.-position vine)
              (* radius-3d 0.5 (Math/cos angle))
              (* height-3d 0.15)
              (* radius-3d 0.5 (Math/sin angle)))
        (set! (.-z (.-rotation vine)) (/ Math/PI 2))
        (set! (.-y (.-rotation vine)) angle)
        (.add group vine)))
    ;; Blooms
    (when bloom-color
      (let [bloom-mat (THREE/MeshLambertMaterial. #js {:color (hex->color bloom-color)})
            bloom-geom (THREE/SphereGeometry. (* radius-3d 0.15) 6 4)]
        (dotimes [i 4]
          (let [angle (* i (/ constants/TWO-PI 4))
                bloom (THREE/Mesh. bloom-geom bloom-mat)]
            (.set (.-position bloom)
                  (* radius-3d 0.6 (Math/cos angle))
                  (* height-3d 0.5)
                  (* radius-3d 0.6 (Math/sin angle)))
            (.add group bloom)))))
    group))

(defn- create-prostrate-plant!
  "Create a prostrate/groundcover plant (flat spreading)."
  [_height-3d radius-3d color bloom-color]
  (let [group (THREE/Group.)
        mat (THREE/MeshLambertMaterial. #js {:color (hex->color color)})]
    ;; Multiple flat patches
    (dotimes [i 7]
      (let [angle (if (zero? i) 0 (* i (/ constants/TWO-PI 6)))
            dist (if (zero? i) 0 (* radius-3d 0.5))
            patch-r (* radius-3d (+ 0.3 (* 0.2 (Math/random))))
            patch-geom (THREE/CylinderGeometry. patch-r patch-r 0.2 8)
            patch (THREE/Mesh. patch-geom mat)]
        (.set (.-position patch)
              (* dist (Math/cos angle))
              0.1
              (* dist (Math/sin angle)))
        (.add group patch)))
    ;; Tiny blooms
    (when bloom-color
      (let [bloom-mat (THREE/MeshLambertMaterial. #js {:color (hex->color bloom-color)})
            bloom-geom (THREE/SphereGeometry. (* radius-3d 0.08) 4 3)]
        (dotimes [i 8]
          (let [angle (* i (/ constants/TWO-PI 8))
                bloom (THREE/Mesh. bloom-geom bloom-mat)]
            (.set (.-position bloom)
                  (* radius-3d 0.6 (Math/cos angle))
                  0.3
                  (* radius-3d 0.6 (Math/sin angle)))
            (.add group bloom)))))
    group))

(defn- create-plant-mesh!
  "Create a 3D mesh for a plant based on its species data."
  [plant-data stage]
  (let [habit (or (:habit plant-data) :mounding)
        color (or (:color plant-data) "#228B22")
        bloom-color (:bloom-color plant-data)
        spacing-cm (or (:spacing-cm plant-data) 50)
        height-cm (or (:height-cm plant-data) spacing-cm)
        ;; Convert to 3D units and apply stage scaling
        stage-scale (case stage :seed 0.15 :seedling 0.4 :mature 1.0 1.0)
        radius-3d (* (/ spacing-cm 2 cm-per-unit) stage-scale)
        height-3d (* (/ height-cm cm-per-unit) stage-scale)]
    ;; Create based on growth habit
    (case habit
      :columnar (create-columnar-plant! height-3d radius-3d color)
      :spreading (create-spreading-plant! height-3d radius-3d color)
      :vase (create-upright-plant! height-3d radius-3d color bloom-color)
      :weeping (create-weeping-plant! height-3d radius-3d color)
      :mounding (create-mounding-plant! height-3d radius-3d color bloom-color)
      :upright (create-upright-plant! height-3d radius-3d color bloom-color)
      :prostrate (create-prostrate-plant! height-3d radius-3d color bloom-color)
      :rosette (create-rosette-plant! height-3d radius-3d color bloom-color)
      :clumping (create-clumping-plant! height-3d radius-3d color bloom-color)
      :vining (create-vining-plant! height-3d radius-3d color bloom-color)
      :spiky (create-spiky-plant! height-3d radius-3d color)
      :bushy (create-bushy-plant! height-3d radius-3d color bloom-color)
      :fountain (create-fountain-plant! height-3d radius-3d color bloom-color)
      :fan (create-upright-plant! height-3d radius-3d color bloom-color)
      ;; Default to mounding
      (create-mounding-plant! height-3d radius-3d color bloom-color))))

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

(defn- add-plants-to-scene!
  "Add all plants from state to the 3D scene."
  [scene topo-state]
  (let [all-plants (state/plants)
        plant-group (THREE/Group.)
        plant-meshes (atom {})]
    ;; Create mesh for each plant
    (doseq [plant all-plants]
      (let [plant-data (plants/get-plant (:species-id plant))
            stage (or (:stage plant) :mature)
            mesh (create-plant-mesh! plant-data stage)
            [gx gy] (:position plant)
            ;; Convert garden coords to 3D coords
            x3d (/ gx cm-per-unit)
            z3d (- (/ gy cm-per-unit))
            ;; Get elevation at plant position
            y3d (get-elevation-at-3d-point topo-state x3d z3d)]
        (.set (.-position mesh) x3d y3d z3d)
        (.add plant-group mesh)
        (swap! plant-meshes assoc (:id plant) mesh)))
    (.add scene plant-group)
    {:group plant-group
     :meshes @plant-meshes}))

;; =============================================================================
;; Scene Management

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

    ;; Add plants to the scene
    (let [{:keys [group meshes]} (add-plants-to-scene! scene topo-state)]
      (reset! scene-state
              {:scene scene
               :camera camera
               :renderer renderer
               :terrain-mesh terrain-mesh
               :water-mesh water-mesh
               :water-geometry water-geom
               :plant-group group
               :plant-meshes meshes
               :animation-frame nil
               :pointer-locked? false}))

    ;; Animation loop
    (letfn [(animate []
              (let [{:keys [renderer scene camera]} @scene-state]
                (when renderer
                  (process-movement!)
                  (when (water-sim/running?)
                    (update-water-geometry!))
                  (update-camera-state! camera)
                  (.render renderer scene camera)
                  (swap! scene-state assoc :animation-frame
                         (js/requestAnimationFrame animate)))))]
      (animate))))

(defn- dispose-scene!
  "Clean up Three.js resources."
  []
  (let [{:keys [renderer terrain-mesh water-mesh plant-group animation-frame]} @scene-state]
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
    ;; Dispose plant meshes
    (when plant-group
      (.traverse plant-group
                 (fn [obj]
                   (when (.-geometry obj) (.dispose (.-geometry obj)))
                   (when (.-material obj) (.dispose (.-material obj))))))
    (when renderer
      (.dispose renderer)
      (when-let [canvas (.-domElement renderer)]
        (when-let [parent (.-parentElement canvas)]
          (.removeChild parent canvas)))))
  (reset! scene-state
          {:scene nil :camera nil :renderer nil
           :terrain-mesh nil :water-mesh nil :water-geometry nil
           :plant-group nil :plant-meshes {}
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
