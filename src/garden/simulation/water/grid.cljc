(ns garden.simulation.water.grid
  "Grid utilities for water simulation.
   Handles dimension calculations and resampling of elevation data.")

;; -----------------------------------------------------------------------------
;; Dimension Calculations

(def ^:const max-grid-size
  "Maximum grid dimension to prevent performance issues."
  2048)

(def ^:const min-grid-size
  "Minimum grid dimension for meaningful simulation."
  10)

(defn calc-grid-dimensions
  "Calculate simulation grid dimensions from terrain bounds and resolution.

   Arguments:
     bounds       - Map with :min-x :min-y :max-x :max-y in cm
     resolution-cm - Grid cell size in centimeters

   Returns [width height] clamped to [min-grid-size, max-grid-size]."
  [{:keys [min-x min-y max-x max-y]} resolution-cm]
  {:pre [(number? min-x) (number? max-x) (pos? resolution-cm)]}
  (let [terrain-width  (- max-x min-x)
        terrain-height (- max-y min-y)
        width  (-> (/ terrain-width resolution-cm)
                   int
                   (max min-grid-size)
                   (min max-grid-size))
        height (-> (/ terrain-height resolution-cm)
                   int
                   (max min-grid-size)
                   (min max-grid-size))]
    [width height]))

(defn resolution->cell-size-m
  "Convert resolution in cm to cell size in meters."
  [resolution-cm]
  (/ resolution-cm 100.0))

;; -----------------------------------------------------------------------------
;; Resampling

(defn bilinear-sample
  "Sample a value from a 2D grid using bilinear interpolation.

   Arguments:
     data   - Source data array
     width  - Source grid width
     height - Source grid height
     x, y   - Sample coordinates (can be fractional)"
  [data width height x y]
  (let [x0 (int x)
        y0 (int y)
        x1 (min (inc x0) (dec width))
        y1 (min (inc y0) (dec height))
        fx (- x x0)
        fy (- y y0)
        ;; Sample four corners
        v00 (#?(:clj nth :cljs aget) data (+ x0 (* y0 width)))
        v10 (#?(:clj nth :cljs aget) data (+ x1 (* y0 width)))
        v01 (#?(:clj nth :cljs aget) data (+ x0 (* y1 width)))
        v11 (#?(:clj nth :cljs aget) data (+ x1 (* y1 width)))]
    ;; Bilinear interpolation
    (+ (* v00 (- 1 fx) (- 1 fy))
       (* v10 fx (- 1 fy))
       (* v01 (- 1 fx) fy)
       (* v11 fx fy))))

#?(:cljs
   (defn resample-grid
     "Resample a Float32Array to new dimensions using bilinear interpolation.

      Arguments:
        src-data   - Source Float32Array
        src-width  - Source grid width
        src-height - Source grid height
        dst-width  - Target grid width
        dst-height - Target grid height

      Returns a new Float32Array with resampled data."
     [src-data src-width src-height dst-width dst-height]
     (if (and (= src-width dst-width) (= src-height dst-height))
       ;; No resampling needed, just copy
       (js/Float32Array. src-data)
       ;; Resample to new dimensions
       (let [dst-data (js/Float32Array. (* dst-width dst-height))
             x-ratio  (/ (dec src-width) (max 1 (dec dst-width)))
             y-ratio  (/ (dec src-height) (max 1 (dec dst-height)))]
         (dotimes [dy dst-height]
           (dotimes [dx dst-width]
             (let [sx (* dx x-ratio)
                   sy (* dy y-ratio)
                   v  (bilinear-sample src-data src-width src-height sx sy)]
               (aset dst-data (+ dx (* dy dst-width)) v))))
         dst-data))))
