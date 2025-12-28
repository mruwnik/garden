(ns garden.ui.panels.ground
  "Unified ground data modal - combines reference imagery and topographical data."
  (:require [garden.state :as state]
            [garden.topo.geotiff :as geotiff]
            [reagent.core :as r]))

;; Local state for UI
(defonce ^:private ui-state (r/atom {:advanced-open? false}))

(defn- is-geotiff? [file]
  (let [name (.-name file)
        lower-name (.toLowerCase name)]
    (or (.endsWith lower-name ".tif")
        (.endsWith lower-name ".tiff")
        (.endsWith lower-name ".geotiff"))))

(defn- load-image-file!
  "Load a regular image file (PNG, JPG, etc.) as reference image."
  [file]
  (let [reader (js/FileReader.)]
    (set! (.-onload reader)
          (fn [e]
            (let [data-url (.. e -target -result)
                  img (js/Image.)]
              (set! (.-onload img)
                    (fn []
                      (state/set-state! [:ui :reference-image :url] data-url)
                      (state/set-state! [:ui :reference-image :image] img)
                      (state/set-state! [:ui :reference-image :position] [0 0])
                      (state/set-state! [:ui :reference-image :visible?] true)))
              (set! (.-src img) data-url))))
    (.readAsDataURL reader file)))

(defn- extract-rgb-from-geotiff!
  "Extract RGB bands from GeoTIFF and create a reference image.
   rgb-bands is a vector of 3 band indices [r g b] (0-indexed)."
  [rasters width height rgb-bands]
  (let [[r-idx g-idx b-idx] rgb-bands
        band-count (.-length rasters)]
    (when (and (< r-idx band-count)
               (< g-idx band-count)
               (< b-idx band-count))
      (let [r-band (aget rasters r-idx)
            g-band (aget rasters g-idx)
            b-band (aget rasters b-idx)
            ;; Create canvas and ImageData
            canvas (js/document.createElement "canvas")
            _ (set! (.-width canvas) width)
            _ (set! (.-height canvas) height)
            ctx (.getContext canvas "2d")
            image-data (.createImageData ctx width height)
            pixels (.-data image-data)]
        ;; Fill pixels from RGB bands
        (dotimes [i (* width height)]
          (let [pixel-idx (* i 4)
                r (aget r-band i)
                g (aget g-band i)
                b (aget b-band i)]
            (aset pixels pixel-idx (int (min 255 (max 0 r))))
            (aset pixels (+ pixel-idx 1) (int (min 255 (max 0 g))))
            (aset pixels (+ pixel-idx 2) (int (min 255 (max 0 b))))
            (aset pixels (+ pixel-idx 3) 255)))
        (.putImageData ctx image-data 0 0)
        ;; Convert to data URL and create image
        (let [data-url (.toDataURL canvas "image/png")
              img (js/Image.)]
          (set! (.-onload img)
                (fn []
                  (state/set-state! [:ui :reference-image :url] data-url)
                  (state/set-state! [:ui :reference-image :image] img)
                  (state/set-state! [:ui :reference-image :position] [0 0])
                  (state/set-state! [:ui :reference-image :visible?] true)
                  (js/console.log "Extracted RGB image from GeoTIFF:" width "x" height)))
          (set! (.-src img) data-url))))))

(defn- load-geotiff-file!
  "Load a GeoTIFF file, extracting both RGB (if available) and elevation data."
  [file]
  (state/set-state! [:ui :loading?] true)
  (state/set-state! [:ui :loading-message] "Loading ground data...")
  (state/set-state! [:topo :source-file] file)
  (let [reader (js/FileReader.)
        rgb-bands (or (state/get-state :topo :rgb-bands) [0 1 2])
        elev-band (or (state/get-state :topo :selected-band) 3)]
    (set! (.-onload reader)
          (fn [e]
            (let [array-buffer (.. e -target -result)]
              (-> (js/Promise.resolve array-buffer)
                  (.then #(js/GeoTIFF.fromArrayBuffer %))
                  (.then #(.getImage % 0))
                  (.then
                   (fn [^js image]
                     (let [orig-w (.getWidth image)
                           orig-h (.getHeight image)
                           detected-res (try
                                          (when-let [res (.getResolution image)]
                                            (let [x-res (js/Math.abs (aget res 0))]
                                              (when (and (pos? x-res) (< x-res 1000))
                                                x-res)))
                                          (catch :default _ nil))
                           geo-info (try
                                      (let [origin (.getOrigin image)
                                            bbox (.getBoundingBox image)]
                                        (when (and origin bbox)
                                          {:origin [(aget origin 0) (aget origin 1)]
                                           :bbox [(aget bbox 0) (aget bbox 1) (aget bbox 2) (aget bbox 3)]}))
                                      (catch :default _ nil))
                           scale-m-per-pixel (or detected-res 0.5)]
                       (js/console.log "GeoTIFF:" orig-w "x" orig-h
                                       "resolution:" (if detected-res
                                                       (str detected-res "m (from file)")
                                                       "0.5m (default)"))
                       (-> (.readRasters image)
                           (.then
                            (fn [rasters]
                              (let [band-count (.-length rasters)]
                                (js/console.log "GeoTIFF bands:" band-count)
                                ;; Store band count
                                (state/set-state! [:topo :band-count] band-count)
                                ;; Extract RGB if we have at least 3 bands
                                (when (>= band-count 3)
                                  (extract-rgb-from-geotiff! rasters orig-w orig-h rgb-bands))
                                ;; Process elevation if we have the elevation band
                                (when (< elev-band band-count)
                                  (geotiff/load-geotiff! file elev-band))
                                (state/set-state! [:ui :loading?] false))))))))
                  (.catch (fn [err]
                            (state/set-state! [:ui :loading?] false)
                            (js/console.error "Failed to load GeoTIFF:" err)))))))
    (set! (.-onerror reader)
          (fn [err]
            (state/set-state! [:ui :loading?] false)
            (js/console.error "Failed to read file:" err)))
    (.readAsArrayBuffer reader file)))

(defn- handle-file-change [e]
  (when-let [file (first (array-seq (.. e -target -files)))]
    (if (is-geotiff? file)
      (load-geotiff-file! file)
      (load-image-file! file))))

(defn- simple-section
  "Simple controls shown by default."
  []
  (let [ref-img (state/get-state :ui :reference-image)
        topo (state/topo-data)
        has-image? (some? (:image ref-img))
        has-elevation? (some? (:elevation-data topo))
        has-data? (or has-image? has-elevation?)]
    [:div
     ;; File picker
     [:div.form-field
      [:label "Ground Data File"]
      [:div {:style {:font-size "12px" :color "#666" :margin-bottom "6px"}}
       "Accepts images (PNG, JPG) or GeoTIFF with RGB + elevation"]
      [:input
       {:type "file"
        :accept "image/*,.tif,.tiff,.geotiff"
        :style {:width "100%" :padding "8px 0"}
        :on-change handle-file-change}]]

     (when has-data?
       [:div
        ;; Data summary
        [:div.form-field
         {:style {:background "#f5f5f5" :padding "10px" :border-radius "4px"}}
         [:div {:style {:font-size "12px" :color "#666"}}
          (when has-image?
            (let [img (:image ref-img)
                  bar-m (or (:bar-meters ref-img) 50)
                  bar-px 150
                  eff-scale (/ (* bar-m 100) bar-px)
                  width-m (/ (* (.-width img) eff-scale) 100)
                  height-m (/ (* (.-height img) eff-scale) 100)]
              [:div (str "Image: " (.-width img) "×" (.-height img) "px → "
                         (int width-m) "×" (int height-m) "m")]))
          (when has-elevation?
            [:div
             [:div (str "Elevation: " (.toFixed (or (:min-elevation topo) 0) 1) "m - "
                        (.toFixed (or (:max-elevation topo) 0) 1) "m")]
             (when (:band-count topo)
               [:div (str "Using band " (inc (or (:selected-band topo) 0))
                          " of " (:band-count topo) " for elevation")])])]]

        ;; Quick visibility toggles
        (when has-image?
          [:div.form-field
           [:label
            [:input
             {:type "checkbox"
              :checked (boolean (:visible? ref-img))
              :on-change #(state/set-state! [:ui :reference-image :visible?]
                                            (.. % -target -checked))
              :style {:margin-right "8px"}}]
            "Show background image"]])

        (when has-elevation?
          [:div
           [:div.form-field
            [:label
             [:input
              {:type "checkbox"
               :checked (boolean (:visible? topo))
               :on-change #(state/set-state! [:topo :visible?]
                                             (.. % -target -checked))
               :style {:margin-right "8px"}}]
             "Show elevation overlay"]]
           [:div.form-field
            [:label
             [:input
              {:type "checkbox"
               :checked (boolean (get-in topo [:contours :visible?]))
               :on-change #(state/set-state! [:topo :contours :visible?]
                                             (.. % -target -checked))
               :style {:margin-right "8px"}}]
             "Show contour lines"]]])

        ;; Quick scale presets
        [:div.form-field
         [:label "Scale (150px = X meters)"]
         [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
          [:input.text-input
           {:type "number"
            :min "1"
            :max "500"
            :step "1"
            :value (int (or (:bar-meters ref-img) 50))
            :style {:width "70px"}
            :on-change #(let [v (js/parseFloat (.. % -target -value))]
                          (when (and (pos? v) (<= v 500))
                            (state/set-state! [:ui :reference-image :bar-meters] v)))}]
          [:span "m"]
          [:button.btn-secondary {:on-click #(state/set-state! [:ui :reference-image :bar-meters] 10)} "10m"]
          [:button.btn-secondary {:on-click #(state/set-state! [:ui :reference-image :bar-meters] 25)} "25m"]
          [:button.btn-secondary {:on-click #(state/set-state! [:ui :reference-image :bar-meters] 50)} "50m"]]]])]))

(defn- advanced-section
  "Advanced controls, shown when expanded."
  []
  (let [ref-img (state/get-state :ui :reference-image)
        topo (state/topo-data)
        has-image? (some? (:image ref-img))
        has-elevation? (some? (:elevation-data topo))
        band-count (or (:band-count topo) 1)
        has-multiple-bands? (> band-count 1)
        [pos-x pos-y] (or (:position ref-img) [0 0])]
    [:div {:style {:border-top "1px solid #ddd" :margin-top "12px" :padding-top "12px"}}
     ;; Band selection for multi-band GeoTIFF
     (when has-multiple-bands?
       [:div
        [:div.form-field
         [:label "RGB Bands"]
         [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
          (for [[label idx-key default] [["R" 0 0] ["G" 1 1] ["B" 2 2]]]
            ^{:key label}
            [:div {:style {:display "flex" :align-items "center" :gap "4px"}}
             [:span label ":"]
             [:select.text-input
              {:value (or (get (state/get-state :topo :rgb-bands) idx-key) default)
               :style {:width "70px"}
               :on-change #(let [v (js/parseInt (.. % -target -value) 10)
                                 current (or (state/get-state :topo :rgb-bands) [0 1 2])
                                 updated (assoc current idx-key v)]
                             (state/set-state! [:topo :rgb-bands] updated))}
              (for [i (range band-count)]
                ^{:key i}
                [:option {:value i} (str "Band " (inc i))])]])]]
        [:div.form-field
         [:label "Elevation Band"]
         [:select.text-input
          {:value (or (:selected-band topo) 3)
           :style {:width "100px"}
           :on-change #(let [v (js/parseInt (.. % -target -value) 10)]
                         (state/set-state! [:topo :selected-band] v)
                         (when-let [file (state/get-state :topo :source-file)]
                           (geotiff/load-geotiff! file v)))}
          (for [i (range band-count)]
            ^{:key i}
            [:option {:value i} (str "Band " (inc i))])]]])

     ;; Image opacity
     (when has-image?
       [:div.form-field
        [:label "Image Opacity"]
        [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
         [:input
          {:type "range"
           :min "10"
           :max "100"
           :value (int (* 100 (or (:opacity ref-img) 0.5)))
           :style {:flex "1"}
           :on-change #(state/set-state! [:ui :reference-image :opacity]
                                         (/ (js/parseInt (.. % -target -value)) 100))}]
         [:span {:style {:min-width "40px"}}
          (str (int (* 100 (or (:opacity ref-img) 0.5))) "%")]]])

     ;; Elevation opacity
     (when has-elevation?
       [:div.form-field
        [:label "Elevation Opacity"]
        [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
         [:input
          {:type "range"
           :min "10"
           :max "100"
           :value (int (* 100 (or (:opacity topo) 0.3)))
           :style {:flex "1"}
           :on-change #(state/set-state! [:topo :opacity]
                                         (/ (js/parseInt (.. % -target -value)) 100))}]
         [:span {:style {:min-width "40px"}}
          (str (int (* 100 (or (:opacity topo) 0.3))) "%")]]])

     ;; Color scale mode
     (when has-elevation?
       [:div.form-field
        [:label "Color Scale"]
        [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
         [:select.text-input
          {:value (name (or (:color-scale-mode topo) :data))
           :style {:width "auto"}
           :on-change #(state/set-state! [:topo :color-scale-mode]
                                         (keyword (.. % -target -value)))}
          [:option {:value "data"} "Data range (file min/max)"]
          [:option {:value "visible"} "Visible range (zoom-dependent)"]
          [:option {:value "absolute"} "Absolute (-300m to 8000m)"]]]])

     ;; Position offset
     (when has-image?
       [:div.form-field
        [:label "Position offset (meters)"]
        [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
         [:span "X:"]
         [:input.text-input
          {:type "number"
           :step "1"
           :value (int (/ pos-x 100))
           :style {:width "70px"}
           :on-change #(let [m (js/parseFloat (.. % -target -value))]
                         (state/set-state! [:ui :reference-image :position]
                                           [(* m 100) pos-y]))}]
         [:span "Y:"]
         [:input.text-input
          {:type "number"
           :step "1"
           :value (int (/ pos-y 100))
           :style {:width "70px"}
           :on-change #(let [m (js/parseFloat (.. % -target -value))]
                         (state/set-state! [:ui :reference-image :position]
                                           [pos-x (* m 100)]))}]
         [:button.btn-secondary
          {:on-click #(state/set-state! [:ui :reference-image :position] [0 0])}
          "Reset"]]])

     ;; Contour settings
     (when has-elevation?
       [:div.form-field
        [:label "Contour Interval"]
        [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
         [:input.text-input
          {:type "number"
           :min "0.5"
           :max "50"
           :step "0.5"
           :value (or (get-in topo [:contours :interval]) 5)
           :style {:width "60px"}
           :on-change #(let [v (js/parseFloat (.. % -target -value))]
                         (when (>= v 0.5)
                           (state/set-state! [:topo :contours :interval] v)))}]
         [:span "meters"]
         [:button.btn-secondary {:on-click #(state/set-state! [:topo :contours :interval] 1)} "1m"]
         [:button.btn-secondary {:on-click #(state/set-state! [:topo :contours :interval] 5)} "5m"]
         [:button.btn-secondary {:on-click #(state/set-state! [:topo :contours :interval] 10)} "10m"]]])

     ;; Clear buttons
     [:div.form-field {:style {:display "flex" :gap "8px"}}
      (when has-image?
        [:button.btn-secondary
         {:style {:color "#dc3545" :border-color "#dc3545"}
          :on-click state/clear-reference-image!}
         "Remove Image"])
      (when has-elevation?
        [:button.btn-secondary
         {:style {:color "#dc3545" :border-color "#dc3545"}
          :on-click state/clear-topo-data!}
         "Remove Elevation"])]]))

(defn ground-modal
  "Modal for configuring ground data (imagery + topography)."
  []
  (let [advanced-open? (:advanced-open? @ui-state)]
    [:div.settings-overlay
     {:on-click #(state/set-state! [:ui :ground-modal-open?] false)}
     [:div.settings-modal
      {:on-click #(.stopPropagation %)
       :style {:width "450px"}}
      [:h3 "Ground Data"]

      [simple-section]

      ;; Advanced toggle
      [:div.form-field
       [:button.btn-secondary
        {:style {:width "100%"}
         :on-click #(swap! ui-state update :advanced-open? not)}
        (if advanced-open? "▼ Hide Advanced" "▶ Show Advanced")]]

      (when advanced-open?
        [advanced-section])

      ;; Close button
      [:div.settings-buttons
       [:button.btn-primary
        {:on-click #(state/set-state! [:ui :ground-modal-open?] false)}
        "Done"]]]]))
