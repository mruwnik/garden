(ns garden.topo.geotiff
  "GeoTIFF parsing for elevation data - optimized for large files."
  (:require [garden.state :as state]
            ["geotiff" :as geotiff]))

;; Maximum grid dimension to keep in memory (will downsample larger files)
;; Increased to 2048 to preserve more detail for zoomed-in viewing
(def ^:private max-grid-size 2048)

;; Default meters per pixel when GeoTIFF doesn't contain resolution metadata
(def ^:private default-m-per-pixel 0.5)

(defn- extract-resolution
  "Extract meters-per-pixel from GeoTIFF image metadata.
   Returns nil if not available."
  [^js image]
  (try
    (when-let [res (.getResolution image)]
      ;; getResolution returns [x-res, y-res, z-res] in the file's CRS units
      ;; For most elevation DEMs this is in meters or degrees
      ;; We take the absolute value of x resolution (y is often negative)
      (let [x-res (js/Math.abs (aget res 0))]
        (when (and (pos? x-res) (< x-res 1000)) ; sanity check
          x-res)))
    (catch :default _
      nil)))

(defn- extract-geo-info
  "Extract geographic info from GeoTIFF image.
   Returns {:origin [x y], :bbox [minX minY maxX maxY]} or nil."
  [^js image]
  (try
    (let [origin (.getOrigin image)
          bbox (.getBoundingBox image)]
      (when (and origin bbox)
        {:origin [(aget origin 0) (aget origin 1)]
         :bbox [(aget bbox 0) (aget bbox 1) (aget bbox 2) (aget bbox 3)]}))
    (catch :default _
      nil)))

(defn- array-buffer->tiff
  "Parse ArrayBuffer as GeoTIFF. Returns a promise."
  [array-buffer]
  (geotiff/fromArrayBuffer array-buffer))

(defn- get-image
  "Get the first image from GeoTIFF. Returns a promise."
  [tiff]
  (.getImage tiff 0))

(defn- downsample-typed-array
  "Downsample a typed array from (src-w x src-h) to (dst-w x dst-h).
   Uses simple sampling for speed."
  [src-array src-w src-h dst-w dst-h]
  (let [dst-array (js/Float32Array. (* dst-w dst-h))
        x-ratio (/ src-w dst-w)
        y-ratio (/ src-h dst-h)]
    (dotimes [dy dst-h]
      (dotimes [dx dst-w]
        (let [sx (int (* dx x-ratio))
              sy (int (* dy y-ratio))
              src-idx (+ sx (* sy src-w))
              dst-idx (+ dx (* dy dst-w))
              val (aget src-array src-idx)]
          (aset dst-array dst-idx val))))
    dst-array))

(defn- find-min-max
  "Find min/max of a typed array efficiently, ignoring NaN."
  [typed-array]
  (let [len (.-length typed-array)]
    (loop [i 0
           min-val js/Infinity
           max-val js/-Infinity]
      (if (>= i len)
        [min-val max-val]
        (let [v (aget typed-array i)]
          (if (js/isNaN v)
            (recur (inc i) min-val max-val)
            (recur (inc i)
                   (min min-val v)
                   (max max-val v))))))))

(defn- process-rasters
  "Process raster data and create topo-data map.
   band-index: which band to use for elevation (0-indexed, default 0)"
  [rasters orig-width orig-height target-w target-h scale-m-per-pixel needs-downsample? band-index]
  (let [band-count (.-length rasters)
        band-idx (min band-index (dec band-count))  ; Clamp to valid range
        orig-array (aget rasters band-idx)
        elev-array (if needs-downsample?
                     (downsample-typed-array orig-array orig-width orig-height target-w target-h)
                     orig-array)
        [min-elev max-elev] (find-min-max elev-array)
        effective-scale (if needs-downsample?
                          (* scale-m-per-pixel (/ orig-width target-w))
                          scale-m-per-pixel)
        resolution-cm (* effective-scale 100)
        total-w (* target-w resolution-cm)
        total-h (* target-h resolution-cm)]
    {:elevation-data elev-array
     :width target-w
     :height target-h
     :bounds {:min-x (- (/ total-w 2))
              :min-y (- (/ total-h 2))
              :max-x (/ total-w 2)
              :max-y (/ total-h 2)}
     :resolution resolution-cm
     :min-elevation min-elev
     :max-elevation max-elev
     :band-count band-count
     :selected-band band-idx
     :source :geotiff}))

(defn parse-geotiff
  "Parse a GeoTIFF file and extract elevation data.
   Automatically downsamples large files for performance.
   band-index: which band to use for elevation (0-indexed, default 0).

   Returns a promise resolving to topo-data map."
  ([file scale-m-per-pixel] (parse-geotiff file scale-m-per-pixel 0))
  ([file scale-m-per-pixel band-index]
   (js/Promise.
    (fn [resolve reject]
      (let [reader (js/FileReader.)]
        (set! (.-onload reader)
              (fn [e]
                (let [array-buffer (.. e -target -result)]
                  (-> (array-buffer->tiff array-buffer)
                      (.then get-image)
                      (.then
                       (fn [^js image]
                         (let [orig-w (.getWidth image)
                               orig-h (.getHeight image)
                               needs-downsample? (or (> orig-w max-grid-size)
                                                     (> orig-h max-grid-size))
                               ;; Calculate target dimensions
                               [target-w target-h]
                               (if needs-downsample?
                                 (let [aspect (/ orig-w orig-h)]
                                   (if (> aspect 1)
                                     [max-grid-size (max 1 (int (/ max-grid-size aspect)))]
                                     [(max 1 (int (* max-grid-size aspect))) max-grid-size]))
                                 [orig-w orig-h])]
                           (js/console.log "GeoTIFF:" orig-w "x" orig-h
                                           (if needs-downsample?
                                             (str "-> " target-w "x" target-h)
                                             "(no downsampling)"))
                           (-> (.readRasters image)
                               (.then
                                (fn [rasters]
                                  (resolve (process-rasters rasters orig-w orig-h
                                                            target-w target-h
                                                            scale-m-per-pixel
                                                            needs-downsample?
                                                            band-index))))))))
                      (.catch reject)))))
        (set! (.-onerror reader) reject)
        (.readAsArrayBuffer reader file))))))

(defn load-geotiff!
  "Load a GeoTIFF file and set topo state.
   Automatically extracts resolution from GeoTIFF metadata if available.
   Falls back to default-m-per-pixel (0.5m) if not found.
   Uses the selected band from state, or band 0 if not set."
  ([file] (load-geotiff! file (or (state/get-state :topo :selected-band) 0)))
  ([file band-index]
   (state/set-state! [:ui :loading?] true)
   (state/set-state! [:ui :loading-message] "Loading elevation data...")
   (let [reader (js/FileReader.)]
     (set! (.-onload reader)
           (fn [e]
             (let [array-buffer (.. e -target -result)]
               (-> (array-buffer->tiff array-buffer)
                   (.then get-image)
                   (.then
                    (fn [^js image]
                      (let [orig-w (.getWidth image)
                            orig-h (.getHeight image)
                            ;; Try to get resolution and geo info from metadata
                            detected-res (extract-resolution image)
                            geo-info (extract-geo-info image)
                            scale-m-per-pixel (or detected-res default-m-per-pixel)
                            needs-downsample? (or (> orig-w max-grid-size)
                                                  (> orig-h max-grid-size))
                            [target-w target-h]
                            (if needs-downsample?
                              (let [aspect (/ orig-w orig-h)]
                                (if (> aspect 1)
                                  [max-grid-size (max 1 (int (/ max-grid-size aspect)))]
                                  [(max 1 (int (* max-grid-size aspect))) max-grid-size]))
                              [orig-w orig-h])]
                        (js/console.log "GeoTIFF:" orig-w "x" orig-h
                                        "resolution:" (if detected-res
                                                        (str detected-res "m (from file)")
                                                        (str default-m-per-pixel "m (default)")))
                        (when geo-info
                          (js/console.log "GeoTIFF geo:" (clj->js geo-info)))
                        (-> (.readRasters image)
                            (.then
                             (fn [rasters]
                               (let [band-count (.-length rasters)
                                     _ (js/console.log "GeoTIFF bands:" band-count "using band:" band-index)
                                     topo-data (process-rasters rasters orig-w orig-h
                                                                target-w target-h
                                                                scale-m-per-pixel
                                                                needs-downsample?
                                                                band-index)
                                     topo-data (if geo-info
                                                 (assoc topo-data :geo-info geo-info)
                                                 topo-data)]
                                 (state/set-topo-data! topo-data)
                                 (state/set-state! [:topo :visible?] true)
                                 (state/set-state! [:ui :loading?] false)
                                 (js/console.log "Loaded GeoTIFF:"
                                                 (clj->js {:width (:width topo-data)
                                                           :height (:height topo-data)
                                                           :min-elev (:min-elevation topo-data)
                                                           :max-elev (:max-elevation topo-data)
                                                           :band-count (:band-count topo-data)
                                                           :selected-band (:selected-band topo-data)
                                                           :resolution-m scale-m-per-pixel})))))))))
                   (.catch (fn [err]
                             (state/set-state! [:ui :loading?] false)
                             (js/console.error "Failed to load GeoTIFF:" err)))))))
     (set! (.-onerror reader)
           (fn [err]
             (state/set-state! [:ui :loading?] false)
             (js/console.error "Failed to read file:" err)))
     (.readAsArrayBuffer reader file))))

(defn load-geotiff-url!
  "Load a GeoTIFF from a URL.
   Automatically extracts resolution from GeoTIFF metadata if available.
   Falls back to default-m-per-pixel (0.5m) if not found.
   Uses the selected band from state, or band 0 if not set."
  ([url] (load-geotiff-url! url (or (state/get-state :topo :selected-band) 0)))
  ([url band-index]
   (state/set-state! [:ui :loading?] true)
   (state/set-state! [:ui :loading-message] "Fetching elevation data...")
   (-> (js/fetch url)
       (.then #(.arrayBuffer %))
       (.then
        (fn [array-buffer]
          (state/set-state! [:ui :loading-message] "Processing elevation data...")
          (-> (array-buffer->tiff array-buffer)
              (.then get-image)
              (.then
               (fn [^js image]
                 (let [orig-w (.getWidth image)
                       orig-h (.getHeight image)
                       ;; Try to get resolution and geo info from metadata
                       detected-res (extract-resolution image)
                       geo-info (extract-geo-info image)
                       scale-m-per-pixel (or detected-res default-m-per-pixel)
                       needs-downsample? (or (> orig-w max-grid-size)
                                             (> orig-h max-grid-size))
                       [target-w target-h]
                       (if needs-downsample?
                         (let [aspect (/ orig-w orig-h)]
                           (if (> aspect 1)
                             [max-grid-size (max 1 (int (/ max-grid-size aspect)))]
                             [(max 1 (int (* max-grid-size aspect))) max-grid-size]))
                         [orig-w orig-h])]
                   (js/console.log "GeoTIFF:" orig-w "x" orig-h
                                   "resolution:" (if detected-res
                                                   (str detected-res "m (from file)")
                                                   (str default-m-per-pixel "m (default)")))
                   (when geo-info
                     (js/console.log "GeoTIFF geo:" (clj->js geo-info)))
                   (-> (.readRasters image)
                       (.then
                        (fn [rasters]
                          (let [_ (js/console.log "GeoTIFF bands:" (.-length rasters) "using band:" band-index)
                                topo-data (process-rasters rasters orig-w orig-h
                                                           target-w target-h
                                                           scale-m-per-pixel
                                                           needs-downsample?
                                                           band-index)
                                topo-data (if geo-info
                                            (assoc topo-data :geo-info geo-info)
                                            topo-data)]
                            (state/set-topo-data! topo-data)
                            (state/set-state! [:topo :visible?] true)
                            (state/set-state! [:ui :loading?] false)
                            (js/console.log "Loaded GeoTIFF:"
                                            (clj->js {:width (:width topo-data)
                                                      :height (:height topo-data)
                                                      :min-elev (:min-elevation topo-data)
                                                      :max-elev (:max-elevation topo-data)
                                                      :band-count (:band-count topo-data)
                                                      :selected-band (:selected-band topo-data)
                                                      :resolution-m scale-m-per-pixel})))))))))))))
   (.catch (fn [err]
             (state/set-state! [:ui :loading?] false)
             (js/console.error "Failed to load GeoTIFF from URL:" err)))))
