(ns garden.ui.panels.topo
  "Topographical data settings modal."
  (:require [garden.state :as state]
            [garden.topo.geotiff :as geotiff]))

(defn topo-modal
  "Modal for configuring topographical data overlay."
  []
  (let [topo (state/topo-data)
        {:keys [elevation-data visible? min-elevation max-elevation
                bounds resolution contours band-count selected-band]} topo
        {:keys [visible? interval color] :or {visible? false interval 1 color "#8B4513"}} contours
        has-data? (some? elevation-data)
        has-multiple-bands? (and band-count (> band-count 1))
        topo-points (state/topo-points)
        has-points? (seq topo-points)]
    [:div.settings-overlay
     {:on-click #(state/set-state! [:ui :topo-modal-open?] false)}
     [:div.settings-modal
      {:on-click #(.stopPropagation %)
       :style {:width "420px"}}
      [:h3 "Topographical Data"]

      ;; File picker for GeoTIFF
      [:div.form-field
       [:label "Elevation Data (GeoTIFF)"]
       [:input
        {:type "file"
         :accept ".tif,.tiff,.geotiff"
         :style {:width "100%" :padding "8px 0"}
         :on-change (fn [e]
                      (when-let [file (first (array-seq (.. e -target -files)))]
                        ;; Store the file for potential re-loading with different band
                        (state/set-state! [:topo :source-file] file)
                        ;; Auto-detects resolution from file, defaults to 0.5m/pixel
                        (geotiff/load-geotiff! file)))}]]

      ;; Band/channel selection (only show when multiple bands available)
      (when has-multiple-bands?
        [:div.form-field
         [:label "Elevation Channel"]
         [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
          [:select.text-input
           {:value (or selected-band 0)
            :style {:width "auto" :min-width "150px"}
            :on-change (fn [e]
                         (let [new-band (js/parseInt (.. e -target -value) 10)
                               source-file (state/get-state :topo :source-file)]
                           (state/set-state! [:topo :selected-band] new-band)
                           (when source-file
                             (geotiff/load-geotiff! source-file new-band))))}
           (for [i (range band-count)]
             ^{:key i}
             [:option {:value i} (str "Band " (inc i))])]
          [:span {:style {:color "#666" :font-size "12px"}}
           (str band-count " bands available")]]])

      ;; Scale input (meters per pixel)
      [:div.form-field
       [:label "Scale (meters per pixel)"]
       [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
        [:input.text-input
         {:type "number"
          :min "0.1"
          :max "100"
          :step "0.1"
          :value (or (some-> (state/get-state :topo :georef :scale) (/ 100)) 1.0)
          :style {:width "80px"}
          :on-change #(let [v (js/parseFloat (.. % -target -value))]
                        (when (and (pos? v) (<= v 100))
                          (state/set-state! [:topo :georef :scale] (* v 100))))}]
        [:span "m/pixel"]
        ;; Quick presets
        [:button.btn-secondary
         {:on-click #(state/set-state! [:topo :georef :scale] 50)}  ; 0.5m
         "0.5m"]
        [:button.btn-secondary
         {:on-click #(state/set-state! [:topo :georef :scale] 100)} ; 1m
         "1m"]
        [:button.btn-secondary
         {:on-click #(state/set-state! [:topo :georef :scale] 500)} ; 5m
         "5m"]]]

      (when has-data?
        [:div
         ;; Data info
         [:div.form-field
          {:style {:background "#f5f5f5" :padding "10px" :border-radius "4px" :margin-bottom "12px"}}
          [:div {:style {:font-size "12px" :color "#666"}}
           (let [{:keys [min-x min-y max-x max-y]} bounds
                 width-m (/ (- max-x min-x) 100)
                 height-m (/ (- max-y min-y) 100)]
             [:div
              [:div (str "Elevation range: " (.toFixed (or min-elevation 0) 1) "m - "
                         (.toFixed (or max-elevation 0) 1) "m")]
              [:div (str "Coverage: " (int width-m) "m × " (int height-m) "m")]
              [:div (str "Resolution: " (.toFixed (/ resolution 100) 2) "m/cell")]
              (when band-count
                [:div (str "Using band " (inc (or selected-band 0)) " of " band-count)])])]]

         ;; Visibility toggle
         [:div.form-field
          [:label
           [:input
            {:type "checkbox"
             :checked (boolean (state/get-state :topo :visible?))
             :on-change #(state/set-state! [:topo :visible?]
                                           (.. % -target -checked))
             :style {:margin-right "8px"}}]
           "Show elevation overlay"]]

         ;; Contour lines section
         [:div.form-field
          [:label {:style {:font-weight "bold" :margin-bottom "8px" :display "block"}}
           "Contour Lines"]
          [:div {:style {:margin-left "8px"}}
           ;; Contour visibility toggle
           [:div {:style {:margin-bottom "8px"}}
            [:label
             [:input
              {:type "checkbox"
               :checked (boolean (state/get-state :topo :contours :visible?))
               :on-change #(state/set-state! [:topo :contours :visible?]
                                             (.. % -target -checked))
               :style {:margin-right "8px"}}]
             "Show contour lines"]]

           ;; Contour interval
           [:div {:style {:display "flex" :gap "8px" :align-items "center" :margin-bottom "8px"}}
            [:span "Interval:"]
            [:input.text-input
             {:type "number"
              :min "0.5"
              :max "50"
              :step "0.5"
              :value (or (state/get-state :topo :contours :interval) 1)
              :style {:width "60px"}
              :on-change #(let [v (js/parseFloat (.. % -target -value))]
                            (when (and (>= v 0.5) (<= v 50))
                              (state/set-state! [:topo :contours :interval] v)))}]
            [:span "meters"]
            ;; Quick presets
            [:button.btn-secondary
             {:on-click #(state/set-state! [:topo :contours :interval] 1)}
             "1m"]
            [:button.btn-secondary
             {:on-click #(state/set-state! [:topo :contours :interval] 5)}
             "5m"]
            [:button.btn-secondary
             {:on-click #(state/set-state! [:topo :contours :interval] 10)}
             "10m"]]

           ;; Contour color
           [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
            [:span "Color:"]
            [:input
             {:type "color"
              :value (or (state/get-state :topo :contours :color) "#8B4513")
              :style {:width "50px" :height "30px" :cursor "pointer"}
              :on-change #(state/set-state! [:topo :contours :color]
                                            (.. % -target -value))}]]]]

         ;; Clear data button
         [:div.form-field
          [:button.btn-secondary
           {:style {:color "#dc3545" :border-color "#dc3545"}
            :on-click #(do (state/clear-topo-data!)
                           (state/set-state! [:ui :topo-modal-open?] false))}
           "Remove Elevation Data"]]])

      ;; Manual points section
      (when (or has-points? (not has-data?))
        [:div
         [:hr {:style {:margin "16px 0" :border "none" :border-top "1px solid #ddd"}}]
         [:div.form-field
          [:label {:style {:font-weight "bold"}} "Manual Elevation Points"]
          (if has-points?
            [:div {:style {:max-height "150px" :overflow-y "auto" :margin-top "8px"}}
             (for [{:keys [id position elevation]} topo-points]
               ^{:key id}
               [:div {:style {:display "flex" :gap "8px" :align-items "center"
                              :padding "4px 0" :border-bottom "1px solid #eee"}}
                [:span {:style {:flex "1" :font-size "12px"}}
                 (let [[x y] position]
                   (str "(" (int (/ x 100)) "m, " (int (/ y 100)) "m) = " (.toFixed elevation 1) "m"))]
                [:button.btn-secondary
                 {:style {:padding "2px 8px" :font-size "11px"}
                  :on-click #(state/remove-topo-point! id)}
                 "×"]])]
            [:div {:style {:color "#666" :font-size "12px" :margin-top "8px"}}
             "Use the Elevation Point tool to place manual elevation markers."])]])

      ;; Close button
      [:div.settings-buttons
       [:button.btn-primary
        {:on-click #(state/set-state! [:ui :topo-modal-open?] false)}
        "Done"]]]]))
