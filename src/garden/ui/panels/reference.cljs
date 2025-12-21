(ns garden.ui.panels.reference
  "Reference image settings modal."
  (:require [garden.state :as state]))

(defn- load-image-from-file! [file]
  (let [reader (js/FileReader.)]
    (set! (.-onload reader)
          (fn [e]
            (let [data-url (.. e -target -result)
                  img (js/Image.)]
              (set! (.-onload img)
                    (fn []
                      (state/set-state! [:ui :reference-image :url] data-url)
                      (state/set-state! [:ui :reference-image :image] img)
                      ;; Position [0,0] means image center at grid origin
                      (state/set-state! [:ui :reference-image :position] [0 0])
                      (state/set-state! [:ui :reference-image :visible?] true)))
              (set! (.-src img) data-url))))
    (.readAsDataURL reader file)))

(defn reference-modal
  "Modal for configuring reference image overlay."
  []
  (let [ref-img (state/get-state :ui :reference-image)
        {:keys [image visible? opacity scale position]} ref-img
        [pos-x pos-y] (or position [0 0])
        has-image? (some? image)]
    [:div.settings-overlay
     {:on-click #(state/set-state! [:ui :reference-modal-open?] false)}
     [:div.settings-modal
      {:on-click #(.stopPropagation %)
       :style {:width "400px"}}
      [:h3 "Reference Image"]

      ;; File picker
      [:div.form-field
       [:label "Image File"]
       [:input
        {:type "file"
         :accept "image/*"
         :style {:width "100%" :padding "8px 0"}
         :on-change (fn [e]
                      (when-let [file (first (array-seq (.. e -target -files)))]
                        (load-image-from-file! file)))}]]

      (when has-image?
        [:div
         ;; Visibility toggle
         [:div.form-field
          [:label
           [:input
            {:type "checkbox"
             :checked (boolean visible?)
             :on-change #(state/set-state! [:ui :reference-image :visible?]
                                           (.. % -target -checked))
             :style {:margin-right "8px"}}]
           "Show reference image"]]

         ;; Scale - how many meters per 150 image pixels
         [:div.form-field
          [:label "Image Scale"]
          (let [img-w (.-width image)
                img-h (.-height image)
                bar-m (or (:bar-meters ref-img) 50)
                ;; Scale derived from bar
                bar-px 150
                eff-scale (/ (* bar-m 100) bar-px)
                width-m (/ (* img-w eff-scale) 100)
                height-m (/ (* img-h eff-scale) 100)]
            [:div
             [:div {:style {:color "#666" :font-size "12px" :margin-bottom "6px"}}
              "150 image pixels ="]
             [:div {:style {:display "flex" :gap "8px" :align-items "center" :margin-bottom "8px"}}
              [:input.text-input
               {:type "number"
                :min "1"
                :max "500"
                :step "1"
                :value (int bar-m)
                :style {:width "80px"}
                :on-change #(let [v (js/parseFloat (.. % -target -value))]
                              (when (and (pos? v) (<= v 500))
                                (state/set-state! [:ui :reference-image :bar-meters] v)))}]
              [:span "meters"]
              ;; Quick presets - smaller values for typical gardens
              [:button.btn-secondary
               {:on-click #(state/set-state! [:ui :reference-image :bar-meters] 5)}
               "5m"]
              [:button.btn-secondary
               {:on-click #(state/set-state! [:ui :reference-image :bar-meters] 10)}
               "10m"]
              [:button.btn-secondary
               {:on-click #(state/set-state! [:ui :reference-image :bar-meters] 25)}
               "25m"]]
             ;; Show resulting image size
             [:div {:style {:color "#666" :font-size "12px"}}
              (str "Image covers " (int width-m) "m × " (int height-m) "m")]])]

         ;; Opacity
         [:div.form-field
          [:label "Opacity"]
          [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
           [:input
            {:type "range"
             :min "10"
             :max "100"
             :value (int (* 100 (or opacity 0.5)))
             :style {:flex "1"}
             :on-change #(state/set-state! [:ui :reference-image :opacity]
                                           (/ (js/parseInt (.. % -target -value)) 100))}]
           [:span {:style {:min-width "40px"}} (str (int (* 100 (or opacity 0.5))) "%")]]]

         ;; Position = image center offset from grid origin (display in meters, store in cm)
         [:div.form-field
          [:label "Center offset (meters from origin)"]
          [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
           [:span "X:"]
           [:input.text-input
            {:type "number"
             :step "1"
             :value (int (/ pos-x 100))
             :style {:width "80px"}
             :on-change #(let [m (js/parseFloat (.. % -target -value))]
                           (state/set-state! [:ui :reference-image :position]
                                             [(* m 100) pos-y]))}]
           [:span "m  Y:"]
           [:input.text-input
            {:type "number"
             :step "1"
             :value (int (/ pos-y 100))
             :style {:width "80px"}
             :on-change #(let [m (js/parseFloat (.. % -target -value))]
                           (state/set-state! [:ui :reference-image :position]
                                             [pos-x (* m 100)]))}]
           [:span "m"]
           [:button.btn-secondary
            {:on-click #(state/set-state! [:ui :reference-image :position] [0 0])}
            "Reset"]]]

         ;; Clear button
         [:div.form-field
          [:button.btn-secondary
           {:style {:color "#dc3545" :border-color "#dc3545"}
            :on-click #(do (state/clear-reference-image!)
                           (state/set-state! [:ui :reference-modal-open?] false))}
           "Remove Image"]]])

      ;; Close button
      [:div.settings-buttons
       [:button.btn-primary
        {:on-click #(state/set-state! [:ui :reference-modal-open?] false)}
        "Done"]]]]))
