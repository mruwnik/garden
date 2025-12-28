(ns garden.state
  "Centralized application state management.

   This namespace provides:
   - The initial state structure
   - Accessors for reading state
   - Mutation functions for updating state
   - History management for undo/redo"
  (:require [reagent.core :as r]
            [garden.constants :as const]))

;; =============================================================================
;; Initial State

(def initial-state
  {:garden {:name ""
            :location {:lat nil :lon nil}}

   ;; Domain data
   :areas []    ; [{:id :type :name :points :color :properties}]
   :plants []   ; [{:id :species-id :position :planted-date :source}]

   ;; Plant library
   :library {:plants {}
             :filter {:search "" :type nil}}

   ;; Viewport (2D canvas)
   :viewport {:offset [0 0]
              :zoom 1.0
              :size {:width 800 :height 600}
              :ctx nil}

   ;; Viewport (3D view) - tracks camera for when switching back
   :viewport-3d {:target [0 0 0]      ; orbit center point
                 :camera-pos [0 -100 80]}  ; camera position

   ;; View mode - :2d or :3d
   :view-mode :2d

   ;; Tool state
   :tool {:active :select
          :state nil
          :cursor :default}

   ;; Selection
   :selection {:type nil
               :ids #{}}

   ;; History (undo/redo)
   :history {:past []
             :future []}

   ;; UI state
   :ui {:panels {:left {:open? true :width 260}
                 :right {:open? true :width 260}
                 :bottom {:open? false :height 120}}
        :grid {:visible? true :spacing 50 :snap? false :labels? true}
        :spacing-circles {:visible? false}
        :background {:visible? false}
        :reference-image {:visible? false
                          :url nil         ; data URL or file URL
                          :image nil       ; loaded Image object
                          :position [0 0]  ; top-left position in garden coords
                          :scale 1.0       ; scale factor (cm per image pixel)
                          :opacity 0.5
                          :bar-meters 50}  ; what the scale bar represents
        :reference-modal-open? false
        :topo-modal-open? false
        :ground-modal-open? false
        :mode :plan
        :hover {:position nil :plant-id nil}
        :mouse {:canvas-pos nil}
        :loading? false
        :loading-message nil}

   ;; Chat state
   :chat {:open? false
          :messages []  ; [{:role :user/:assistant :content "..."}]
          :input ""
          :loading? false}

   ;; Topographical data
   :topo {:elevation-data nil      ; 2D array of elevation values (meters)
          :source nil              ; :geotiff or :manual
          :bounds nil              ; {:min-x :min-y :max-x :max-y} in garden coords (cm)
          :resolution nil          ; Grid cell size in cm
          :min-elevation nil       ; Minimum elevation in meters
          :max-elevation nil       ; Maximum elevation in meters
          :visible? false          ; Show topo overlay
          :band-count nil          ; Number of bands in GeoTIFF
          :selected-band 0         ; Which band to use for elevation (0-indexed)
          :color-scale-mode :data  ; :data (file min/max), :absolute (-300 to 8000m), :visible (viewport min/max)
          :contours {:visible? false
                     :interval 5   ; Contour interval in meters
                     :color "#8B4513"}
          :georef {:position [0 0]  ; Center position in garden coords
                   :scale 0.25       ; cm per data cell
                   :rotation 0}}    ; degrees

   ;; Manual elevation points (alternative to GeoTIFF)
   :topo-points []

   ;; Water simulation state (for UI reactivity)
   :water-sim {:running? false
               :raining? false
               ;; SI units configuration
               :rain-rate-mm-hr 10.0      ; mm/hour - typical light rain is 2.5, moderate 7.5, heavy 50+
               :evaporation-mm-hr 5.0     ; mm/hour - typical is 2-6 mm/day, but faster for sim
               :infiltration-mm-hr 0.0}

   ;; Graphics settings
   :graphics {:resolution-cm 50           ; Resolution for 3D rendering and simulation (cm per cell)
              :terrain-detail :medium     ; :low, :medium, :high - affects max segments
              :water-detail :medium}})

(defonce app-state (r/atom initial-state))

;; =============================================================================
;; Generic State Accessors

(defn get-state
  "Get the current app state or a path within it."
  ([] @app-state)
  ([& path] (get-in @app-state (vec path))))

;; =============================================================================
;; Viewport Accessors

(defn viewport [] (:viewport @app-state))
(defn zoom [] (get-in @app-state [:viewport :zoom]))
(defn offset [] (get-in @app-state [:viewport :offset]))
(defn canvas-ctx [] (get-in @app-state [:viewport :ctx]))

;; =============================================================================
;; Tool Accessors

(defn active-tool [] (get-in @app-state [:tool :active]))
(defn tool-state [] (get-in @app-state [:tool :state]))

;; =============================================================================
;; Selection Accessors

(defn selection [] (:selection @app-state))
(defn selected-ids [] (get-in @app-state [:selection :ids]))
(defn selected? [id] (contains? (selected-ids) id))

;; =============================================================================
;; Domain Data Accessors (Areas & Plants)

(defn areas [] (:areas @app-state))
(defn plants [] (:plants @app-state))

;; Cached index maps for O(1) lookups
;; These are recomputed only when the underlying collections change
(defonce ^:private areas-index-cache (atom {:source nil :index {}}))
(defonce ^:private plants-index-cache (atom {:source nil :index {}}))

(defn areas-by-id
  "Get a map of area-id -> area for O(1) lookups.
   Cached and recomputed only when areas change."
  []
  (let [current-areas (areas)
        {:keys [source index]} @areas-index-cache]
    (if (identical? source current-areas)
      index
      (let [new-index (into {} (map (juxt :id identity) current-areas))]
        (reset! areas-index-cache {:source current-areas :index new-index})
        new-index))))

(defn plants-by-id
  "Get a map of plant-id -> plant for O(1) lookups.
   Cached and recomputed only when plants change."
  []
  (let [current-plants (plants)
        {:keys [source index]} @plants-index-cache]
    (if (identical? source current-plants)
      index
      (let [new-index (into {} (map (juxt :id identity) current-plants))]
        (reset! plants-index-cache {:source current-plants :index new-index})
        new-index))))

(defn find-area
  "Find an area by ID. O(1) using cached index."
  [id]
  (get (areas-by-id) id))

(defn find-plant
  "Find a plant by ID. O(1) using cached index."
  [id]
  (get (plants-by-id) id))

(defn find-plant-at
  "Find a plant at the given canvas position."
  [pos radius-fn]
  (let [[px py] pos]
    (first (filter (fn [plant]
                     (let [[x y] (:position plant)
                           radius (radius-fn plant)
                           dx (- px x)
                           dy (- py y)]
                       (<= (+ (* dx dx) (* dy dy)) (* radius radius))))
                   (plants)))))

;; =============================================================================
;; Generic State Mutations

(defn set-state! [path value]
  (swap! app-state assoc-in path value))

(defn update-state! [path f & args]
  (swap! app-state update-in path #(apply f % args)))

(defn set-viewport-ctx! [ctx]
  (set-state! [:viewport :ctx] ctx))

(defn set-viewport-size! [width height]
  (set-state! [:viewport :size] {:width width :height height}))

(defn set-tool! [tool-id]
  (swap! app-state #(-> %
                        (assoc-in [:tool :active] tool-id)
                        (assoc-in [:tool :state] nil))))

(defn set-tool-state! [state]
  (set-state! [:tool :state] state))

(defn update-tool-state! [f & args]
  (swap! app-state update-in [:tool :state] #(apply f % args)))

(defn set-cursor! [cursor]
  (set-state! [:tool :cursor] cursor))

;; =============================================================================
;; Selection Mutations

(defn select! [type ids]
  (swap! app-state assoc :selection {:type type :ids (set ids)}))

(defn clear-selection! []
  (swap! app-state assoc :selection {:type nil :ids #{}}))

(defn toggle-selection! [type id]
  (let [current-ids (selected-ids)]
    (if (contains? current-ids id)
      (let [new-ids (disj current-ids id)]
        (if (empty? new-ids)
          (clear-selection!)
          (select! type new-ids)))
      (select! type (conj current-ids id)))))

;; =============================================================================
;; History (Undo/Redo)

(def max-history-size 50)

;; Flag to suppress history during batch operations
(def ^:private suppress-history? (atom false))

(defn- push-history!
  "Push a state snapshot to history stack."
  [pre-state]
  (let [past (get-in @app-state [:history :past])]
    (swap! app-state
           #(-> %
                (assoc-in [:history :past]
                          (vec (take-last max-history-size (conj past pre-state))))
                (assoc-in [:history :future] [])))))

(defn- with-history*
  "Execute f, saving history only if successful.
   Captures pre-mutation state, runs f, then pushes history on success."
  [f]
  (if @suppress-history?
    (f) ; Skip history if suppressed (inside batch)
    (let [pre-state {:areas (:areas @app-state)
                     :plants (:plants @app-state)}
          result (f)]
      (push-history! pre-state)
      result)))

(defn with-batch-history
  "Execute f as a batch operation with a single undo entry.
   All mutations inside f will be grouped into one history entry."
  [f]
  (let [pre-state {:areas (:areas @app-state)
                   :plants (:plants @app-state)}]
    (reset! suppress-history? true)
    (try
      (let [result (f)]
        (push-history! pre-state)
        result)
      (finally
        (reset! suppress-history? false)))))

(defn can-undo? []
  (seq (get-in @app-state [:history :past])))

(defn can-redo? []
  (seq (get-in @app-state [:history :future])))

(defn undo!
  "Restore previous state from history."
  []
  (when (can-undo?)
    (let [current {:areas (:areas @app-state)
                   :plants (:plants @app-state)}
          past (get-in @app-state [:history :past])
          previous (peek past)]
      (swap! app-state
             #(-> %
                  (assoc :areas (:areas previous))
                  (assoc :plants (:plants previous))
                  (assoc-in [:history :past] (pop past))
                  (update-in [:history :future] conj current))))))

(defn redo!
  "Restore next state from future history."
  []
  (when (can-redo?)
    (let [current {:areas (:areas @app-state)
                   :plants (:plants @app-state)}
          future-states (get-in @app-state [:history :future])
          next-state (peek future-states)]
      (swap! app-state
             #(-> %
                  (assoc :areas (:areas next-state))
                  (assoc :plants (:plants next-state))
                  (assoc-in [:history :future] (pop future-states))
                  (update-in [:history :past] conj current))))))

;; =============================================================================
;; Area Mutations

(defn gen-id []
  (str (random-uuid)))

(defn add-area!
  "Add an area to the garden. Returns the area's id, or nil if invalid.
   Areas must have at least 3 points to form a valid polygon."
  [area]
  (let [points (:points area)]
    (if (and points (>= (count points) 3))
      (with-history*
        #(let [area-with-id (if (:id area) area (assoc area :id (gen-id)))]
           (update-state! [:areas] conj area-with-id)
           (:id area-with-id)))
      (do
        (js/console.warn "Cannot add area with fewer than 3 points:" (count points))
        nil))))

(defn update-area! [id updates]
  (with-history*
    #(swap! app-state update :areas
            (fn [areas]
              (mapv (fn [a] (if (= (:id a) id) (merge a updates) a)) areas)))))

(defn remove-area! [id]
  (with-history*
    #(swap! app-state update :areas
            (fn [areas] (filterv (fn [a] (not= (:id a) id)) areas)))))

;; =============================================================================
;; Plant Mutations

(defn add-plant! [plant]
  (with-history*
    #(let [plant-with-id (if (:id plant) plant (assoc plant :id (gen-id)))]
       (update-state! [:plants] conj plant-with-id)
       (:id plant-with-id))))

(defn add-plants-batch!
  "Add multiple plants in a single undo-able operation."
  [plants]
  (with-history*
    #(let [plants-with-ids (mapv (fn [p] (if (:id p) p (assoc p :id (gen-id)))) plants)]
       (swap! app-state update :plants into plants-with-ids)
       (mapv :id plants-with-ids))))

(defn update-plant! [id updates]
  (with-history*
    #(swap! app-state update :plants
            (fn [plants]
              (mapv (fn [p] (if (= (:id p) id) (merge p updates) p)) plants)))))

(defn remove-plant! [id]
  (with-history*
    #(swap! app-state update :plants
            (fn [plants] (filterv (fn [p] (not= (:id p) id)) plants)))))

(defn clear-all!
  "Clear all areas and plants in a single undo-able operation."
  []
  (with-history*
    #(swap! app-state assoc :areas [] :plants [])))

(defn remove-areas-batch!
  "Remove multiple areas in a single undo-able operation."
  [ids]
  (let [id-set (set ids)]
    (with-history*
      #(swap! app-state update :areas
              (fn [areas] (filterv (fn [a] (not (contains? id-set (:id a)))) areas))))))

(defn remove-plants-batch!
  "Remove multiple plants in a single undo-able operation."
  [ids]
  (let [id-set (set ids)]
    (with-history*
      #(swap! app-state update :plants
              (fn [plants] (filterv (fn [p] (not (contains? id-set (:id p)))) plants))))))

;; =============================================================================
;; Viewport Mutations

(defn pan! [dx dy]
  (update-state! [:viewport :offset]
                 (fn [[x y]] [(+ x dx) (+ y dy)])))

(defn set-zoom! [new-zoom]
  (set-state! [:viewport :zoom] (max 0.001 (min 10.0 new-zoom))))

(defn zoom-at!
  "Zoom centered on screen point."
  [[sx sy] factor]
  (let [{:keys [offset zoom]} (viewport)
        [ox oy] offset
        ;; Convert screen point to canvas coordinates before zoom
        cx (/ (- sx ox) zoom)
        cy (/ (- sy oy) zoom)
        ;; Calculate new zoom (min 0.001 = 0.1%, max 10 = 1000%)
        new-zoom (max 0.001 (min 10.0 (* zoom factor)))
        ;; Adjust offset to keep canvas point stationary
        new-ox (- sx (* cx new-zoom))
        new-oy (- sy (* cy new-zoom))]
    (swap! app-state #(-> %
                          (assoc-in [:viewport :zoom] new-zoom)
                          (assoc-in [:viewport :offset] [new-ox new-oy])))))

;; =============================================================================
;; Reference Image

(defn load-reference-image!
  "Load a reference image from a File object."
  [file]
  (let [reader (js/FileReader.)]
    (set! (.-onload reader)
          (fn [e]
            (let [data-url (.. e -target -result)
                  img (js/Image.)]
              (set! (.-onload img)
                    (fn []
                      ;; Calculate scale based on bar-meters setting
                      ;; Default: bar-image-pixels of image = default-bar-meters in real world
                      (let [bar-meters (get-state :ui :reference-image :bar-meters)
                            scale (/ (* (or bar-meters const/default-bar-meters) 100) const/bar-image-pixels)
                            img-w (.-width img)
                            img-h (.-height img)
                            ;; Position image centered at grid origin (0,0)
                            img-canvas-w (* img-w scale)
                            img-canvas-h (* img-h scale)
                            pos-x (- (/ img-canvas-w 2))
                            pos-y (- (/ img-canvas-h 2))]
                        (set-state! [:ui :reference-image :url] data-url)
                        (set-state! [:ui :reference-image :image] img)
                        (set-state! [:ui :reference-image :scale] scale)
                        (set-state! [:ui :reference-image :position] [pos-x pos-y])
                        (set-state! [:ui :reference-image :visible?] true))))
              (set! (.-src img) data-url))))
    (.readAsDataURL reader file)))

(defn load-reference-image-url!
  "Load a reference image from a URL."
  [url]
  (let [img (js/Image.)]
    (set! (.-onload img)
          (fn []
            (let [bar-meters (get-state :ui :reference-image :bar-meters)
                  scale (/ (* (or bar-meters const/default-bar-meters) 100) const/bar-image-pixels)]
              (set-state! [:ui :reference-image :url] url)
              (set-state! [:ui :reference-image :image] img)
              (set-state! [:ui :reference-image :scale] scale)
              ;; Position is the CENTER of the image in canvas coords
              ;; Center at origin [0, 0]
              (set-state! [:ui :reference-image :position] [0 0])
              (set-state! [:ui :reference-image :visible?] true))))
    (set! (.-src img) url)))

(defn clear-reference-image! []
  (swap! app-state update-in [:ui :reference-image]
         assoc :url nil :image nil :visible? false))

(defn set-reference-opacity! [opacity]
  (set-state! [:ui :reference-image :opacity] (max 0.1 (min 1.0 opacity))))

(defn set-reference-scale! [scale]
  (set-state! [:ui :reference-image :scale] (max 0.1 (min 10.0 scale))))

(defn toggle-reference-visible! []
  (update-state! [:ui :reference-image :visible?] not))

;; =============================================================================
;; Topographical Data

(defn topo-data [] (:topo @app-state))
(defn topo-points [] (:topo-points @app-state))
(defn topo-elevation-data [] (get-in @app-state [:topo :elevation-data]))
(defn topo-bounds [] (get-in @app-state [:topo :bounds]))
(defn topo-resolution [] (get-in @app-state [:topo :resolution]))
(defn topo-visible? [] (get-in @app-state [:topo :visible?]))

(defn set-topo-data!
  "Set the full topo data from a parsed source (GeoTIFF, etc.)"
  [{:keys [elevation-data rgb-data bounds resolution min-elevation max-elevation source georef width height geo-info band-count selected-band]}]
  (swap! app-state update :topo merge
         {:elevation-data elevation-data
          :rgb-data rgb-data  ; Uint8ClampedArray with RGBA, or nil
          :bounds bounds
          :resolution resolution
          :min-elevation min-elevation
          :max-elevation max-elevation
          :source source
          :width width
          :height height
          :geo-info geo-info
          :band-count band-count
          :selected-band selected-band
          :georef (or georef {:position [0 0] :scale 1.0 :rotation 0})}))

(defn clear-topo-data! []
  (swap! app-state assoc :topo
         {:elevation-data nil
          :source nil
          :source-file nil
          :bounds nil
          :resolution nil
          :min-elevation nil
          :max-elevation nil
          :visible? false
          :band-count nil
          :selected-band 0
          :contours {:visible? false :interval 1 :color "#8B4513"}
          :georef {:position [0 0] :scale 1.0 :rotation 0}}))

(defn toggle-topo-visible! []
  (update-state! [:topo :visible?] not))

(defn set-topo-opacity! [opacity]
  (set-state! [:topo :opacity] (max 0.1 (min 1.0 opacity))))

(defn toggle-contours-visible! []
  (update-state! [:topo :contours :visible?] not))

(defn set-contour-interval! [interval]
  (set-state! [:topo :contours :interval] (max 0.5 interval)))

;; =============================================================================
;; Manual Elevation Points

(defn add-topo-point!
  "Add a manual elevation point."
  [{:keys [position elevation]}]
  (let [point {:id (gen-id)
               :position position
               :elevation elevation}]
    (update-state! [:topo-points] conj point)
    (:id point)))

(defn remove-topo-point! [id]
  (swap! app-state update :topo-points
         (fn [points] (filterv #(not= (:id %) id) points))))

(defn update-topo-point! [id updates]
  (swap! app-state update :topo-points
         (fn [points]
           (mapv #(if (= (:id %) id) (merge % updates) %) points))))

;; =============================================================================
;; Graphics Settings

(defn graphics-resolution-cm
  "Get the graphics resolution in cm per cell."
  []
  (get-in @app-state [:graphics :resolution-cm]))

(defn set-graphics-resolution-cm!
  "Set the graphics resolution in cm per cell. Lower = more detail, more compute."
  [cm]
  (set-state! [:graphics :resolution-cm] (max 10 (min 500 cm))))
