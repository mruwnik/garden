(ns garden.state
  (:require [reagent.core :as r]))

(def initial-state
  {:garden {:name ""
            :location {:lat nil :lon nil}}

   ;; Domain data
   :areas []    ; [{:id :type :name :points :color :properties}]
   :plants []   ; [{:id :species-id :position :planted-date :source}]

   ;; Plant library
   :library {:plants {}
             :filter {:search "" :type nil}}

   ;; Viewport
   :viewport {:offset [0 0]
              :zoom 1.0
              :size {:width 800 :height 600}
              :ctx nil}

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
        :background {:visible? true}
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
   :topo-points []})

(defonce app-state (r/atom initial-state))

;; State accessors

(defn get-state
  "Get the current app state or a path within it."
  ([] @app-state)
  ([& path] (get-in @app-state (vec path))))

;; Viewport helpers

(defn viewport [] (:viewport @app-state))
(defn zoom [] (get-in @app-state [:viewport :zoom]))
(defn offset [] (get-in @app-state [:viewport :offset]))
(defn canvas-ctx [] (get-in @app-state [:viewport :ctx]))

;; Tool helpers

(defn active-tool [] (get-in @app-state [:tool :active]))
(defn tool-state [] (get-in @app-state [:tool :state]))

;; Selection helpers

(defn selection [] (:selection @app-state))
(defn selected-ids [] (get-in @app-state [:selection :ids]))
(defn selected? [id] (contains? (selected-ids) id))

;; Domain data helpers

(defn areas [] (:areas @app-state))
(defn plants [] (:plants @app-state))

(defn find-area [id]
  (first (filter #(= (:id %) id) (areas))))

(defn find-plant [id]
  (first (filter #(= (:id %) id) (plants))))

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

;; State mutations

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

;; Selection mutations

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

;; History/Undo helpers

(def max-history-size 50)

(defn- save-history!
  "Save current areas/plants state to history before a mutation."
  []
  (let [current {:areas (:areas @app-state)
                 :plants (:plants @app-state)}
        past (get-in @app-state [:history :past])]
    (swap! app-state
           #(-> %
                (assoc-in [:history :past]
                          (vec (take-last max-history-size (conj past current))))
                (assoc-in [:history :future] [])))))

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

;; Area mutations

(defn gen-id []
  (str (random-uuid)))

(defn add-area! [area]
  (save-history!)
  (let [area-with-id (if (:id area) area (assoc area :id (gen-id)))]
    (update-state! [:areas] conj area-with-id)
    (:id area-with-id)))

(defn update-area! [id updates]
  (save-history!)
  (swap! app-state update :areas
         (fn [areas]
           (mapv #(if (= (:id %) id) (merge % updates) %) areas))))

(defn remove-area! [id]
  (save-history!)
  (swap! app-state update :areas
         (fn [areas] (filterv #(not= (:id %) id) areas))))

;; Plant mutations

(defn add-plant! [plant]
  (save-history!)
  (let [plant-with-id (if (:id plant) plant (assoc plant :id (gen-id)))]
    (update-state! [:plants] conj plant-with-id)
    (:id plant-with-id)))

(defn add-plants-batch!
  "Add multiple plants in a single undo-able operation."
  [plants]
  (save-history!)
  (let [plants-with-ids (mapv #(if (:id %) % (assoc % :id (gen-id))) plants)]
    (swap! app-state update :plants into plants-with-ids)
    (mapv :id plants-with-ids)))

(defn update-plant! [id updates]
  (save-history!)
  (swap! app-state update :plants
         (fn [plants]
           (mapv #(if (= (:id %) id) (merge % updates) %) plants))))

(defn remove-plant! [id]
  (save-history!)
  (swap! app-state update :plants
         (fn [plants] (filterv #(not= (:id %) id) plants))))

;; Viewport mutations

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

;; Reference image helpers

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
                      ;; Default: 150 image pixels = 50 meters = 5000 cm
                      (let [bar-meters (get-state :ui :reference-image :bar-meters)
                            bar-px 150
                            scale (/ (* (or bar-meters 50) 100) bar-px)
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
                  bar-px 150
                  scale (/ (* (or bar-meters 50) 100) bar-px)]
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

;; Topo helpers

(defn topo-data [] (:topo @app-state))
(defn topo-points [] (:topo-points @app-state))
(defn topo-elevation-data [] (get-in @app-state [:topo :elevation-data]))
(defn topo-bounds [] (get-in @app-state [:topo :bounds]))
(defn topo-resolution [] (get-in @app-state [:topo :resolution]))
(defn topo-visible? [] (get-in @app-state [:topo :visible?]))

(defn set-topo-data!
  "Set the full topo data from a parsed source (GeoTIFF, etc.)"
  [{:keys [elevation-data bounds resolution min-elevation max-elevation source georef width height geo-info band-count selected-band]}]
  (swap! app-state update :topo merge
         {:elevation-data elevation-data
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

;; Manual elevation points

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
