(ns garden.topo.core
  "Core topographical data handling: elevation grid, interpolation, lookups.

   Provides:
   - Grid cell access for typed Float32Array elevation data
   - Coordinate conversion between garden space (cm) and grid space
   - Bilinear and nearest-neighbor interpolation
   - Elevation gradient calculation for slope/aspect
   - Manual point interpolation using inverse distance weighting
   - Geographic coordinate conversion (PL-1992/EPSG:2180 to WGS84)

   Elevation Grid Data Structure:
   - elevation-data: Float32Array, 1D row-major (index = row * width + col)
   - width/height: Grid dimensions
   - bounds: {:min-x :min-y :max-x :max-y} in garden coords (cm)
   - resolution: Size of each grid cell in cm"
  (:require [garden.state :as state]))

;; =============================================================================
;; Grid Cell Access

(defn get-cell
  "Get elevation at grid cell [row col] from typed array.
   Returns nil if out of bounds or NaN."
  [elevation-data width height row col]
  (when (and elevation-data
             (>= row 0) (< row height)
             (>= col 0) (< col width))
    (let [idx (+ col (* row width))
          v (aget elevation-data idx)]
      (when-not (js/isNaN v) v))))

;; =============================================================================
;; Coordinate Conversion

(defn garden->grid
  "Convert garden coordinates (cm) to grid coordinates.
   Returns [row col] as floats for interpolation."
  [x y bounds resolution]
  (when (and bounds resolution (pos? resolution))
    (let [{:keys [min-x min-y]} bounds
          col (/ (- x min-x) resolution)
          row (/ (- y min-y) resolution)]
      [row col])))

(defn grid->garden
  "Convert grid coordinates [row col] to garden coordinates (cm).
   Returns [x y]."
  [row col bounds resolution]
  (when (and bounds resolution)
    (let [{:keys [min-x min-y]} bounds
          x (+ min-x (* col resolution))
          y (+ min-y (* row resolution))]
      [x y])))

;; =============================================================================
;; Interpolation

(defn bilinear-interpolate
  "Interpolate elevation at fractional grid position [row col].
   Uses bilinear interpolation from surrounding 4 cells."
  [elevation-data width height row col]
  (when elevation-data
    (let [;; Grid cell indices (floor)
          r0 (int (Math/floor row))
          c0 (int (Math/floor col))
          r1 (inc r0)
          c1 (inc c0)
          ;; Fractional parts
          fr (- row r0)
          fc (- col c0)
          ;; Get four corner elevations
          q00 (get-cell elevation-data width height r0 c0)
          q10 (get-cell elevation-data width height r0 c1)
          q01 (get-cell elevation-data width height r1 c0)
          q11 (get-cell elevation-data width height r1 c1)]
      ;; All four corners must exist for interpolation
      (when (and q00 q10 q01 q11)
        ;; Bilinear formula
        (+ (* q00 (- 1 fc) (- 1 fr))
           (* q10 fc (- 1 fr))
           (* q01 (- 1 fc) fr)
           (* q11 fc fr))))))

(defn nearest-neighbor
  "Get elevation using nearest neighbor (no interpolation).
   Faster but less smooth than bilinear."
  [elevation-data width height row col]
  (let [r (int (Math/round row))
        c (int (Math/round col))]
    (get-cell elevation-data width height r c)))

;; =============================================================================
;; Public Elevation API

(defn get-elevation-at
  "Get interpolated elevation at garden coordinates [x y] in cm.
   Returns elevation in meters, or nil if no data."
  [[x y]]
  (let [{:keys [elevation-data bounds resolution width height]} (state/topo-data)]
    (when (and elevation-data bounds resolution width height)
      (when-let [[row col] (garden->grid x y bounds resolution)]
        (bilinear-interpolate elevation-data width height row col)))))

(defn get-elevation-at-nearest
  "Get elevation at garden coordinates using nearest neighbor.
   Faster for real-time updates."
  [[x y]]
  (let [{:keys [elevation-data bounds resolution width height]} (state/topo-data)]
    (when (and elevation-data bounds resolution width height)
      (when-let [[row col] (garden->grid x y bounds resolution)]
        (nearest-neighbor elevation-data width height row col)))))

(defn in-bounds?
  "Check if garden coordinates [x y] are within the topo data bounds."
  [[x y]]
  (let [{:keys [bounds]} (state/topo-data)]
    (when bounds
      (let [{:keys [min-x min-y max-x max-y]} bounds]
        (and (>= x min-x) (<= x max-x)
             (>= y min-y) (<= y max-y))))))

(defn grid-dimensions
  "Get the dimensions of the elevation grid as [height width]."
  []
  (let [{:keys [width height]} (state/topo-data)]
    (when (and width height)
      [height width])))

;; =============================================================================
;; Elevation Range Helpers

(defn elevation-range
  "Get [min max] elevation from stored metadata."
  []
  (let [{:keys [min-elevation max-elevation]} (state/topo-data)]
    (when (and min-elevation max-elevation)
      [min-elevation max-elevation])))

(defn normalize-elevation
  "Normalize elevation value to 0-1 range based on data min/max."
  [elevation]
  (when-let [[min-e max-e] (elevation-range)]
    (let [range (- max-e min-e)]
      (if (pos? range)
        (/ (- elevation min-e) range)
        0.5))))

;; =============================================================================
;; Gradient Calculations (for slope/aspect)

(defn elevation-gradient-at
  "Calculate elevation gradient (dz/dx, dz/dy) at garden coordinates.
   Returns {:dzdx :dzdy} in meters per meter, or nil if not calculable."
  [[x y]]
  (let [{:keys [elevation-data bounds resolution width height]} (state/topo-data)]
    (when (and elevation-data bounds resolution width height)
      ;; Sample at neighboring points
      (let [e-left  (get-elevation-at [(- x resolution) y])
            e-right (get-elevation-at [(+ x resolution) y])
            e-up    (get-elevation-at [x (- y resolution)])
            e-down  (get-elevation-at [x (+ y resolution)])]
        (when (and e-left e-right e-up e-down)
          ;; Central difference approximation
          ;; Note: resolution is in cm, elevation in meters
          ;; Convert resolution to meters for consistent units
          (let [res-m (/ resolution 100)]
            {:dzdx (/ (- e-right e-left) (* 2 res-m))
             :dzdy (/ (- e-down e-up) (* 2 res-m))}))))))

;; =============================================================================
;; Manual Point Interpolation (IDW)

(defn interpolate-from-points
  "Generate elevation at [x y] by interpolating from manual topo-points.
   Uses inverse distance weighting (IDW)."
  [[x y]]
  (let [points (state/topo-points)]
    (when (seq points)
      (let [distances (map (fn [{:keys [position elevation]}]
                             (let [[px py] position
                                   dx (- x px)
                                   dy (- y py)
                                   dist (Math/sqrt (+ (* dx dx) (* dy dy)))]
                               {:distance dist :elevation elevation}))
                           points)
            ;; Filter out points at exact location (dist = 0)
            non-zero (filter #(pos? (:distance %)) distances)]
        (if (empty? non-zero)
          ;; Exact match - return that elevation
          (:elevation (first (filter #(zero? (:distance %)) distances)))
          ;; IDW: weight = 1/d^2
          (let [weights (map #(/ 1.0 (* (:distance %) (:distance %))) non-zero)
                total-weight (reduce + weights)
                weighted-sum (reduce + (map (fn [w {:keys [elevation]}]
                                              (* w elevation))
                                            weights non-zero))]
            (/ weighted-sum total-weight)))))))

(defn get-elevation-with-fallback
  "Get elevation, trying grid data first, then manual points."
  [[x y]]
  (or (get-elevation-at [x y])
      (interpolate-from-points [x y])))

;; =============================================================================
;; Geographic Coordinate Conversion
;;
;; PL-1992 (EPSG:2180) to WGS84 conversion
;; Transverse Mercator projection parameters for PL-1992:
;; - Ellipsoid: GRS 1980
;; - Central meridian: 19°E
;; - Scale factor: 0.9993
;; - False easting: 500,000m
;; - False northing: -5,300,000m

(def ^:private grs80-a 6378137.0)        ; Semi-major axis (meters)
(def ^:private grs80-f (/ 1 298.257222101)) ; Flattening
(def ^:private grs80-e2 (* grs80-f (- 2 grs80-f))) ; First eccentricity squared

(def ^:private pl1992-k0 0.9993)         ; Scale factor
(def ^:private pl1992-lon0 (/ (* 19 js/Math.PI) 180)) ; Central meridian (radians)
(def ^:private pl1992-E0 500000)         ; False easting
(def ^:private pl1992-N0 -5300000)       ; False northing

(defn- pl1992->latlon
  "Convert PL-1992 (EPSG:2180) coordinates to WGS84 lat/lon.
   Input: [easting northing] in meters
   Output: [latitude longitude] in degrees"
  [[easting northing]]
  (let [;; Remove false origin
        x (- easting pl1992-E0)
        y (- northing pl1992-N0)

        ;; Derived ellipsoid constants
        e2 grs80-e2
        e4 (* e2 e2)
        e6 (* e4 e2)

        ;; Meridian arc constants
        A (* grs80-a (- 1 (/ e2 4) (/ (* 3 e4) 64) (/ (* 5 e6) 256)))

        ;; Footpoint latitude (iterative)
        mu (/ y (* pl1992-k0 A))

        e1 (/ (- 1 (js/Math.sqrt (- 1 e2))) (+ 1 (js/Math.sqrt (- 1 e2))))
        e1-2 (* e1 e1)
        e1-3 (* e1-2 e1)
        e1-4 (* e1-3 e1)

        phi1 (+ mu
                (* (- (/ (* 3 e1) 2) (/ (* 27 e1-3) 32)) (js/Math.sin (* 2 mu)))
                (* (- (/ (* 21 e1-2) 16) (/ (* 55 e1-4) 32)) (js/Math.sin (* 4 mu)))
                (* (/ (* 151 e1-3) 96) (js/Math.sin (* 6 mu)))
                (* (/ (* 1097 e1-4) 512) (js/Math.sin (* 8 mu))))

        ;; Radius of curvature
        sin-phi1 (js/Math.sin phi1)
        cos-phi1 (js/Math.cos phi1)
        tan-phi1 (js/Math.tan phi1)

        N1 (/ grs80-a (js/Math.sqrt (- 1 (* e2 sin-phi1 sin-phi1))))
        T1 (* tan-phi1 tan-phi1)
        C1 (/ (* e2 cos-phi1 cos-phi1) (- 1 e2))
        R1 (/ (* grs80-a (- 1 e2))
              (js/Math.pow (- 1 (* e2 sin-phi1 sin-phi1)) 1.5))
        D (/ x (* N1 pl1992-k0))

        D2 (* D D)
        D3 (* D2 D)
        D4 (* D3 D)
        D5 (* D4 D)
        D6 (* D5 D)

        ;; Latitude
        lat (- phi1
               (* (/ (* N1 tan-phi1) R1)
                  (+ (/ D2 2)
                     (* (/ (+ -5 (* 3 T1) (* 10 C1) (* -4 C1 C1) (* -9 e2)) 24) D4)
                     (* (/ (+ 61 (* -90 T1) (* 298 C1) (* 45 T1 T1) (* -252 e2) (* -3 C1 C1)) 720) D6))))

        ;; Longitude
        lon (+ pl1992-lon0
               (/ (- D
                     (* (/ (+ 1 (* 2 T1) C1) 6) D3)
                     (* (/ (+ 5 (* -2 C1) (* 28 T1) (* -3 C1 C1) (* 8 e2) (* 24 T1 T1)) 120) D5))
                  cos-phi1))]

    ;; Convert to degrees
    [(* lat (/ 180 js/Math.PI))
     (* lon (/ 180 js/Math.PI))]))

(defn garden->geo
  "Convert garden coordinates [x y] to geographic coordinates.
   Returns [geo-x geo-y] in the CRS of the GeoTIFF (often UTM meters or lat/lon),
   or nil if no geo-info is available."
  [[x y]]
  (let [{:keys [bounds geo-info]} (state/topo-data)]
    (when (and bounds geo-info)
      (let [{:keys [min-x min-y max-x max-y]} bounds
            {:keys [bbox]} geo-info
            [geo-min-x geo-min-y geo-max-x geo-max-y] bbox
            ;; Normalize position within garden bounds (0-1)
            norm-x (/ (- x min-x) (- max-x min-x))
            norm-y (/ (- y min-y) (- max-y min-y))
            ;; Map to geo bbox (note: y may be flipped in some CRS)
            geo-x (+ geo-min-x (* norm-x (- geo-max-x geo-min-x)))
            ;; GeoTIFF y often goes from top to bottom, so flip
            geo-y (+ geo-max-y (* norm-y (- geo-min-y geo-max-y)))]
        [geo-x geo-y]))))

(defn garden->latlon
  "Convert garden coordinates [x y] to WGS84 lat/lon.
   Currently assumes PL-1992 (EPSG:2180) coordinate system.
   Returns [latitude longitude] in degrees, or nil if no geo-info."
  [[x y]]
  (when-let [[geo-x geo-y] (garden->geo [x y])]
    (pl1992->latlon [geo-x geo-y])))

(defn has-geo-info?
  "Check if geographic info is available."
  []
  (some? (get-in (state/topo-data) [:geo-info :bbox])))
