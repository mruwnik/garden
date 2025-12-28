(ns garden.canvas.terrain3d
  "3D terrain visualization using Three.js."
  (:require [reagent.core :as r]
            [garden.state :as state]
            [garden.simulation.water :as water-sim]
            ["three" :as THREE]))

;; Three.js objects (kept outside React lifecycle)
(defonce ^:private scene-state
  (atom {:scene nil
         :camera nil
         :renderer nil
         :terrain-mesh nil
         :water-mesh nil
         :water-geometry nil
         :animation-frame nil}))

;; Cache terrain info for water updates
(defonce ^:private terrain-cache
  (atom {:segments-x 0
         :segments-y 0
         :data-width 0
         :data-height 0
         :elev-scale 1
         :min-elevation 0}))

(defn- create-terrain-geometry
  "Create a PlaneGeometry with vertices displaced by elevation data."
  [topo-state]
  (let [{:keys [elevation-data width height bounds min-elevation max-elevation]} topo-state
        {:keys [min-x min-y max-x max-y]} bounds
        ;; World dimensions (convert from cm to meters for reasonable scale)
        world-width (/ (- max-x min-x) 100)
        world-height (/ (- max-y min-y) 100)
        ;; Create plane geometry with grid resolution
        ;; Use fewer segments for performance (max 512x512)
        segments-x (min 512 (dec width))
        segments-y (min 512 (dec height))
        geometry (THREE/PlaneGeometry. world-width world-height segments-x segments-y)
        ;; Get position attribute
        positions (.-array (.-position (.-attributes geometry)))
        ;; Elevation scaling - make it visible but not too extreme
        elev-range (- max-elevation min-elevation)
        elev-scale (if (pos? elev-range) (/ 50 elev-range) 1)]  ; 50m range in 3D space
    ;; Cache terrain info for water updates
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
        (let [;; Map segment indices to data grid indices
              data-x (int (* (/ j segments-x) (dec width)))
              data-y (int (* (/ i segments-y) (dec height)))
              data-idx (+ data-x (* data-y width))
              elev (aget elevation-data data-idx)
              ;; Vertex index in position array (each vertex has x,y,z)
              vert-idx (+ j (* i (inc segments-x)))
              z-idx (+ (* vert-idx 3) 2)]
          ;; Set Z (elevation) - handle NaN
          (if (js/isNaN elev)
            (aset positions z-idx 0)
            (aset positions z-idx (* (- elev min-elevation) elev-scale))))))
    ;; Update normals for proper lighting
    (.computeVertexNormals geometry)
    geometry))

(defn- create-water-geometry
  "Create water surface geometry matching terrain dimensions."
  [topo-state]
  (let [{:keys [bounds]} topo-state
        {:keys [min-x min-y max-x max-y]} bounds
        world-width (/ (- max-x min-x) 100)
        world-height (/ (- max-y min-y) 100)
        {:keys [segments-x segments-y]} @terrain-cache]
    (THREE/PlaneGeometry. world-width world-height segments-x segments-y)))

(defn- update-water-geometry!
  "Update water mesh vertices from simulation water grid."
  []
  (let [{:keys [water-geometry]} @scene-state
        water-grid (water-sim/water-grid)
        topo (state/topo-data)
        elev-data (:elevation-data topo)]
    (when (and water-geometry water-grid elev-data)
      (let [{:keys [segments-x segments-y data-width data-height elev-scale min-elevation]} @terrain-cache
            positions (.-array (.-position (.-attributes water-geometry)))
            ;; Water scale: convert meters to 3D units
            water-scale (* elev-scale 10)]  ; Exaggerate water height for visibility
        ;; Update each vertex
        (dotimes [i (inc segments-y)]
          (dotimes [j (inc segments-x)]
            (let [data-x (int (* (/ j segments-x) (dec data-width)))
                  data-y (int (* (/ i segments-y) (dec data-height)))
                  data-idx (+ data-x (* data-y data-width))
                  elev (aget elev-data data-idx)
                  water-h (aget water-grid data-idx)
                  vert-idx (+ j (* i (inc segments-x)))
                  z-idx (+ (* vert-idx 3) 2)
                  ;; Water surface = terrain elevation + water height
                  base-z (if (js/isNaN elev) 0 (* (- elev min-elevation) elev-scale))
                  water-z (+ base-z (* water-h water-scale))]
              ;; Only show water where there's significant depth
              (if (> water-h 0.001)  ; 1mm minimum
                (aset positions z-idx water-z)
                (aset positions z-idx js/NaN)))))  ; NaN vertices won't render
        ;; Mark geometry as needing update
        (set! (.-needsUpdate (.-position (.-attributes water-geometry))) true)))))

(defn- create-rgb-texture
  "Create a Three.js DataTexture from RGBA data."
  [rgb-data tex-width tex-height]
  (let [texture (THREE/DataTexture. rgb-data tex-width tex-height THREE/RGBAFormat)]
    (set! (.-needsUpdate texture) true)
    (set! (.-flipY texture) true)
    (set! (.-minFilter texture) THREE/LinearFilter)
    (set! (.-magFilter texture) THREE/LinearFilter)
    texture))

(defn- update-camera-state!
  "Update camera position and direction in app-state for status bar."
  [camera]
  (when camera
    (let [pos (.-position camera)
          scale 100  ; Convert from 3D units (meters) to garden coords (cm)
          ;; In 3D: X=east, Z=south, Y=up
          ;; Display as: X=east, Y=elevation, Z=north
          cam-x (* (.-x pos) scale)
          cam-y (* (.-y pos) scale)           ; Y = elevation (height)
          cam-z (* (- (.-z pos)) scale)       ; -Z in 3D = north
          ;; Calculate direction
          dir (THREE/Vector3.)
          _ (.getWorldDirection camera dir)
          ;; In 3D coords: -Z is north, +X is east
          ;; atan2(x, -z) gives angle from north
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

(defn- get-elevation-at-origin
  "Get the elevation at (0,0) in 3D space units."
  [topo-state]
  (let [{:keys [elevation-data width height bounds min-elevation]} topo-state
        {:keys [min-x min-y max-x max-y]} bounds
        {:keys [elev-scale]} @terrain-cache
        ;; Convert (0,0) garden coords to data grid indices
        ;; Garden (0,0) maps to center of the terrain bounds
        world-width (- max-x min-x)
        world-height (- max-y min-y)
        ;; Origin (0,0) in garden coords - find corresponding data index
        ;; Garden coords start at 0, data bounds might be offset
        data-x (int (* (/ (- 0 min-x) world-width) (dec width)))
        data-y (int (* (/ (- 0 min-y) world-height) (dec height)))
        ;; Clamp to valid range
        data-x (max 0 (min (dec width) data-x))
        data-y (max 0 (min (dec height) data-y))
        data-idx (+ data-x (* data-y width))
        elev (aget elevation-data data-idx)]
    (if (or (nil? elev) (js/isNaN elev))
      0
      (* (- elev min-elevation) elev-scale))))

(defn- create-scene!
  "Create the Three.js scene with terrain and static camera."
  [container topo-state]
  (let [width (.-clientWidth container)
        height (.-clientHeight container)
        ;; Scene
        scene (THREE/Scene.)
        _ (set! (.-background scene) (THREE/Color. 0x87CEEB))  ; Sky blue
        ;; Camera
        camera (THREE/PerspectiveCamera. 60 (/ width height) 0.1 10000)
        ;; Renderer
        renderer (THREE/WebGLRenderer. #js {:antialias true})
        _ (.setSize renderer width height)
        _ (.setPixelRatio renderer (.-devicePixelRatio js/window))
        _ (.appendChild container (.-domElement renderer))
        ;; Lighting
        rgb-data (:rgb-data topo-state)
        use-texture? (some? rgb-data)
        ambient (THREE/AmbientLight. 0xffffff (if use-texture? 0.8 0.4))
        _ (.add scene ambient)
        sun (THREE/DirectionalLight. 0xffffff (if use-texture? 0.4 0.8))
        _ (.set (.-position sun) 50 50 100)
        _ (.add scene sun)
        ;; Terrain
        geometry (create-terrain-geometry topo-state)
        material (if use-texture?
                   (let [texture (create-rgb-texture rgb-data (:width topo-state) (:height topo-state))]
                     (THREE/MeshLambertMaterial.
                      #js {:map texture
                           :side THREE/DoubleSide}))
                   (THREE/MeshLambertMaterial.
                    #js {:color 0x8B7355
                         :side THREE/DoubleSide}))
        terrain-mesh (THREE/Mesh. geometry material)
        ;; Rotate to XZ plane: X=east, Z=north, Y=up (elevation)
        _ (.rotateX terrain-mesh (- (/ js/Math.PI 2)))
        _ (.add scene terrain-mesh)
        ;; Water surface
        water-geometry (create-water-geometry topo-state)
        water-material (THREE/MeshLambertMaterial.
                        #js {:color 0x1E90FF
                             :transparent true
                             :opacity 0.6
                             :side THREE/DoubleSide})
        water-mesh (THREE/Mesh. water-geometry water-material)
        _ (.rotateX water-mesh (- (/ js/Math.PI 2)))
        _ (.add scene water-mesh)
        ;; Camera setup - terrain in XZ plane, Y is up
        ;; X = east/west, Z = south/north (-Z = north), Y = elevation
        {:keys [world-width world-height]} @terrain-cache
        cam-distance (max world-width world-height 100)]
    ;; Camera positioned south of terrain (+Z), elevated (Y), looking at center
    (.set (.-position camera) 0 (* cam-distance 0.7) cam-distance)
    (.lookAt camera 0 0 0)
    ;; Store references
    (swap! scene-state merge
           {:scene scene
            :camera camera
            :renderer renderer
            :terrain-mesh terrain-mesh
            :water-mesh water-mesh
            :water-geometry water-geometry
            :animation-frame nil})
    ;; Start animation loop (just for water updates and rendering)
    (letfn [(animate []
              (let [{:keys [renderer scene camera]} @scene-state]
                (when renderer
                  ;; Update water surface from simulation
                  (when (water-sim/sim-running?)
                    (update-water-geometry!))
                  ;; Update camera info in app-state for status bar
                  (update-camera-state! camera)
                  (.render renderer scene camera)
                  (swap! scene-state assoc :animation-frame (js/requestAnimationFrame animate)))))]
      (animate))))

(defn adjust-camera-elevation!
  "Adjust camera Y position by delta (in 3D units/meters)."
  [delta]
  (when-let [camera (:camera @scene-state)]
    (let [pos (.-position camera)
          new-y (+ (.-y pos) delta)]
      ;; Keep camera above ground (minimum 1 meter)
      (when (> new-y 1)
        (.set pos (.-x pos) new-y (.-z pos))))))

(defn- handle-keydown!
  "Handle keyboard events for camera control."
  [e]
  (let [key (.-key e)]
    (case key
      "q" (adjust-camera-elevation! -5)   ; Move down 5 meters
      "Q" (adjust-camera-elevation! -5)
      "e" (adjust-camera-elevation! 5)    ; Move up 5 meters
      "E" (adjust-camera-elevation! 5)
      nil)))

(defn- dispose-scene!
  "Clean up Three.js resources."
  []
  (let [{:keys [renderer terrain-mesh water-mesh animation-frame]} @scene-state]
    (when animation-frame
      (js/cancelAnimationFrame animation-frame))
    (when terrain-mesh
      (.dispose (.-geometry terrain-mesh))
      (when-let [material (.-material terrain-mesh)]
        (when-let [texture (.-map material)]
          (.dispose texture))
        (.dispose material)))
    (when water-mesh
      (.dispose (.-geometry water-mesh))
      (when-let [material (.-material water-mesh)]
        (.dispose material)))
    (when renderer
      (.dispose renderer)
      (when-let [canvas (.-domElement renderer)]
        (when-let [parent (.-parentElement canvas)]
          (.removeChild parent canvas)))))
  (reset! scene-state
          {:scene nil :camera nil :renderer nil
           :terrain-mesh nil :water-mesh nil :water-geometry nil
           :animation-frame nil}))

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

(defn terrain-3d-component
  "3D terrain viewer component with static camera."
  []
  (let [container-ref (atom nil)
        resize-observer (atom nil)]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (when-let [container @container-ref]
          (let [topo-state (state/topo-data)]
            (when (:elevation-data topo-state)
              (create-scene! container topo-state)
              ;; Set up resize observer
              (let [observer (js/ResizeObserver.
                              (fn [_entries]
                                (handle-resize! container)))]
                (.observe observer container)
                (reset! resize-observer observer))
              ;; Add keyboard listener for camera controls
              (.addEventListener js/window "keydown" handle-keydown!)))))

      :component-will-unmount
      (fn [_]
        (when-let [observer @resize-observer]
          (.disconnect observer))
        ;; Remove keyboard listener
        (.removeEventListener js/window "keydown" handle-keydown!)
        (dispose-scene!))

      :reagent-render
      (fn []
        [:div.terrain-3d-container
         {:ref #(reset! container-ref %)
          :tab-index 0  ; Make focusable for keyboard events
          :style {:width "100%"
                  :height "100%"
                  :background "#87CEEB"}}])})))

(defn has-terrain-data?
  "Check if there's elevation data available for 3D rendering."
  []
  (some? (state/topo-elevation-data)))

(defn get-camera-position
  "Get camera position as [x y z] in garden coordinates (cm)."
  []
  (when-let [camera (:camera @scene-state)]
    (let [pos (.-position camera)
          {:keys [world-width world-height]} @terrain-cache
          ;; Convert from 3D units (meters) back to garden coords (cm)
          scale 100]
      [(* (.-x pos) scale)
       (* (.-y pos) scale)
       (* (.-z pos) scale)])))

(defn get-camera-direction
  "Get camera direction as [degrees cardinal] where degrees is bearing from north."
  []
  (when-let [camera (:camera @scene-state)]
    (let [dir (THREE/Vector3.)
          _ (.getWorldDirection camera dir)
          ;; In our coord system: +X is east, +Y is north
          ;; dir is where camera is looking
          angle (js/Math.atan2 (.-x dir) (.-y dir))  ; angle from north
          degrees (mod (+ (* angle (/ 180 js/Math.PI)) 360) 360)
          ;; Convert to cardinal direction
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
