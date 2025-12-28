(ns garden.ui.panels.library
  "Left sidebar panel with drawing tools and plant library.

   Provides:
   - Drawing tool buttons (area, trace, fill, contour-trace, elevation-point)
   - Plant placement tools (single, row, scatter)
   - Tool-specific options panels
   - Searchable plant catalog with drag-and-drop support"
  (:require [garden.state :as state]
            [garden.tools.protocol :as tools]))

;; =============================================================================
;; Plant Catalog Data

;; Sample plant library data
;; spacing-cm is the recommended spacing between plants (diameter of mature footprint)
(def sample-plants
  [{:id "tomato-cherry"
    :common-name "Cherry Tomato"
    :scientific-name "Solanum lycopersicum"
    :type :vegetable
    :color "#ff6347"
    :spacing-cm 60}
   {:id "basil"
    :common-name "Basil"
    :scientific-name "Ocimum basilicum"
    :type :herb
    :color "#228b22"
    :spacing-cm 25}
   {:id "sunflower"
    :common-name "Sunflower"
    :scientific-name "Helianthus annuus"
    :type :flower
    :color "#ffd700"
    :spacing-cm 45}
   {:id "rose"
    :common-name "Rose"
    :scientific-name "Rosa"
    :type :flower
    :color "#ff1493"
    :spacing-cm 60}
   {:id "tulip"
    :common-name "Tulip"
    :scientific-name "Tulipa"
    :type :flower
    :color "#ff69b4"
    :spacing-cm 15}
   {:id "lavender"
    :common-name "Lavender"
    :scientific-name "Lavandula"
    :type :flower
    :color "#9370db"
    :spacing-cm 45}
   {:id "marigold"
    :common-name "Marigold"
    :scientific-name "Tagetes"
    :type :flower
    :color "#ffa500"
    :spacing-cm 25}
   {:id "carrot"
    :common-name "Carrot"
    :scientific-name "Daucus carota"
    :type :vegetable
    :color "#ff8c00"
    :spacing-cm 8}
   {:id "lettuce"
    :common-name "Lettuce"
    :scientific-name "Lactuca sativa"
    :type :vegetable
    :color "#90ee90"
    :spacing-cm 30}
   {:id "rosemary"
    :common-name "Rosemary"
    :scientific-name "Salvia rosmarinus"
    :type :herb
    :color "#2e8b57"
    :spacing-cm 60}
   {:id "apple-tree"
    :common-name "Apple Tree"
    :scientific-name "Malus domestica"
    :type :tree
    :color "#8b4513"
    :spacing-cm 400}
   ;; Japanese Garden Plants
   {:id "cherry-blossom"
    :common-name "Cherry Blossom"
    :scientific-name "Prunus serrulata"
    :type :tree
    :color "#ffb7c5"
    :spacing-cm 500}
   {:id "japanese-maple"
    :common-name "Japanese Maple"
    :scientific-name "Acer palmatum"
    :type :tree
    :color "#c41e3a"
    :spacing-cm 400}
   {:id "japanese-pine"
    :common-name "Japanese Black Pine"
    :scientific-name "Pinus thunbergii"
    :type :tree
    :color "#355e3b"
    :spacing-cm 600}
   {:id "plum-tree"
    :common-name "Plum Tree"
    :scientific-name "Prunus mume"
    :type :tree
    :color "#dda0dd"
    :spacing-cm 400}
   {:id "bamboo"
    :common-name "Bamboo"
    :scientific-name "Phyllostachys"
    :type :tree
    :color "#7cfc00"
    :spacing-cm 100}
   {:id "azalea"
    :common-name "Azalea"
    :scientific-name "Rhododendron"
    :type :flower
    :color "#ff6ec7"
    :spacing-cm 80}
   {:id "iris"
    :common-name "Japanese Iris"
    :scientific-name "Iris ensata"
    :type :flower
    :color "#5d3fd3"
    :spacing-cm 30}
   {:id "moss"
    :common-name "Moss"
    :scientific-name "Bryophyta"
    :type :herb
    :color "#4a5d23"
    :spacing-cm 10}
   {:id "wisteria"
    :common-name "Wisteria"
    :scientific-name "Wisteria floribunda"
    :type :flower
    :color "#c9a0dc"
    :spacing-cm 200}
   {:id "camellia"
    :common-name "Camellia"
    :scientific-name "Camellia japonica"
    :type :flower
    :color "#e34234"
    :spacing-cm 150}])

;; =============================================================================
;; Plant Card Component

(defn plant-card
  "A single plant in the library."
  [plant]
  (let [selected-species (state/get-state :tool :state :species-id)]
    [:div.plant-card
     {:class (when (= (:id plant) selected-species) "selected")
      :draggable true
      :on-drag-start (fn [e]
                       ;; Store plant ID in drag data
                       (.setData (.-dataTransfer e) "text/plain" (:id plant))
                       (set! (.-effectAllowed (.-dataTransfer e)) "copy")
                       ;; Also activate plant tool
                       (tools/activate-tool! :plant)
                       (state/update-tool-state! assoc :species-id (:id plant)))
      :on-click (fn []
                  ;; Select this plant for placement
                  ;; If scatter tool is active, stay in scatter mode
                  (let [current-tool (state/active-tool)]
                    (when (not= current-tool :scatter)
                      (tools/activate-tool! :plant)))
                  (state/update-tool-state! assoc :species-id (:id plant)))}
     [:div.plant-color
      {:style {:background-color (:color plant)}}]
     [:div.plant-info
      [:div.plant-name (:common-name plant)]
      [:div.plant-type (name (:type plant))]]]))

;; =============================================================================
;; Drawing Tools Section

(def drawing-tools
  "Tools for drawing/placing items, shown in left panel."
  [:area :trace :fill :contour-trace :elevation-point])

(defn- drawing-tool-button
  "A button for a drawing tool."
  [tool-id]
  (when-let [tool (tools/get-tool tool-id)]
    (let [label (tools/tool-label tool)
          active? (= tool-id (state/active-tool))]
      [:button.tool-btn
       {:class (when active? "active")
        :on-click #(tools/activate-tool! tool-id)}
       label])))

(defn- plant-tool-button
  "Button for plant tool with specific mode."
  [mode label]
  (let [active? (and (= :plant (state/active-tool))
                     (= mode (or (:mode (state/tool-state)) :single)))]
    [:button.tool-btn
     {:class (when active? "active")
      :on-click #(do (tools/activate-tool! :plant)
                     (state/update-tool-state! assoc :mode mode))}
     label]))

(defn- scatter-tool-button
  "Button for scatter tool."
  []
  (let [active? (= :scatter (state/active-tool))]
    [:button.tool-btn
     {:class (when active? "active")
      :on-click #(tools/activate-tool! :scatter)}
     "Scatter"]))

;; =============================================================================
;; Area Types

(def area-types
  {:bed       {:label "Garden Bed" :color "#8B6914"}
   :path      {:label "Path" :color "#d4a574"}
   :water     {:label "Water" :color "#4a90d9"}
   :structure {:label "Structure" :color "#607D8B"}
   :lawn      {:label "Lawn" :color "#7CB342"}
   :rocks     {:label "Rocks/Gravel" :color "#9E9E9E"}
   :hedge     {:label "Hedge" :color "#2E7D32"}
   :mulch     {:label "Mulch" :color "#5D4037"}
   :patio     {:label "Patio/Deck" :color "#8D6E63"}
   :sand      {:label "Sand" :color "#E8D5B7"}})

(defn- area-type-selector
  "Dropdown to select area type for drawing tools."
  [current-type on-change]
  [:div.form-field
   [:label "Area Type"]
   [:select.select-input
    {:value (name (or current-type :water))
     :on-change #(on-change (keyword (-> % .-target .-value)))}
    (for [[type-key {:keys [label]}] (sort-by (comp :label val) area-types)]
      ^{:key type-key}
      [:option {:value (name type-key)} label])]])

;; =============================================================================
;; Tool Options Panels

(defn- trace-tool-options
  "Options panel for the trace tool."
  []
  (let [tool-state (state/tool-state)
        area-type (or (:area-type tool-state) :water)]
    [:div.tool-options
     [:h4 "Trace Tool"]
     [:p.hint "Draw freehand to create area outlines"]
     [area-type-selector area-type
      #(state/update-tool-state! assoc :area-type %)]
     [:div.form-info
      [:span "Keyboard: 1-0 or W/B/P/L/H for types"]]]))

(defn- fill-tool-options
  "Options panel for the fill tool."
  []
  (let [tool-state (state/tool-state)
        area-type (or (:area-type tool-state) :water)
        tolerance (or (:tolerance tool-state) 32)
        respect-existing? (if (contains? tool-state :respect-existing?)
                            (:respect-existing? tool-state)
                            (contains? #{:water :path} area-type))]
    [:div.tool-options
     [:h4 "Fill Tool"]
     [:p.hint "Click on reference image to flood fill"]
     [area-type-selector area-type
      #(do (state/update-tool-state! assoc :area-type %)
           ;; Update respect-existing default based on type
           (when-not (contains? (state/tool-state) :respect-existing?)
             (state/update-tool-state! assoc :respect-existing?
                                       (contains? #{:water :path} %))))]
     [:div.form-field
      [:label "Color Tolerance"]
      [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
       [:input
        {:type "range"
         :min "8"
         :max "128"
         :value tolerance
         :style {:flex "1"}
         :on-change #(state/update-tool-state! assoc :tolerance
                                               (js/parseInt (-> % .-target .-value)))}]
       [:span {:style {:min-width "30px"}} tolerance]]]
     [:div.form-field
      [:label
       [:input
        {:type "checkbox"
         :checked respect-existing?
         :style {:margin-right "8px"}
         :on-change #(state/update-tool-state! assoc :respect-existing?
                                               (-> % .-target .-checked))}]
       "Exclude existing areas"]]
     [:div.form-info
      [:span "+/- keys adjust tolerance"]]]))

(defn- area-tool-options
  "Options panel for the area drawing tool."
  []
  [:div.tool-options
   [:h4 "Draw Area"]
   [:p.hint "Click to place vertices, click first point or Enter to close"]
   [:div.form-info
    [:span "Escape to cancel"]]])

(defn- scatter-tool-options
  "Options panel for the scatter tool."
  []
  (let [tool-state (state/tool-state)
        count (or (:count tool-state) 20)
        species-id (:species-id tool-state)]
    [:div.tool-options
     [:h4 "Scatter Tool"]
     [:p.hint "Drag to define area, plants scatter inside"]
     [:div.form-field
      [:label "Plant Count"]
      [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
       [:input
        {:type "range"
         :min "10"
         :max "100"
         :step "10"
         :value count
         :style {:flex "1"}
         :on-change #(state/update-tool-state! assoc :count
                                               (js/parseInt (-> % .-target .-value)))}]
       [:span {:style {:min-width "30px"}} count]]]
     [:div.form-info
      [:span (str "Plant: " (or species-id "Select from library"))]]
     [:div.form-info
      [:span "Keys 1-9, 0 = 10-90, 100 plants"]]]))

(defn- contour-trace-tool-options
  "Options panel for the contour trace tool."
  []
  (let [tool-state (state/tool-state)
        area-type (or (:area-type tool-state) :bed)
        hover-elevation (:hover-elevation tool-state)
        target-elevation (:target-elevation tool-state)
        drawing? (:drawing? tool-state)]
    [:div.tool-options
     [:h4 "Contour Trace"]
     [:p.hint "Trace along elevation contour lines"]
     [area-type-selector area-type
      #(state/update-tool-state! assoc :area-type %)]
     (when (or hover-elevation target-elevation)
       [:div.form-field
        [:label "Contour Elevation"]
        [:span.form-value
         (if drawing?
           (str "Tracing at " (.toFixed (or target-elevation 0) 1) "m")
           (str "Hover: " (.toFixed (or hover-elevation 0) 1) "m"))]])
     [:div.form-info
      [:span "Keyboard: 1-8 for area types"]]
     [:div.form-info
      [:span "Requires loaded topo data"]]]))

(defn- elevation-point-tool-options
  "Options panel for the elevation point tool."
  []
  (let [tool-state (state/tool-state)
        pending-position (:pending-position tool-state)
        elevation-input (:elevation-input tool-state)
        selected-point-id (:selected-point-id tool-state)
        topo-points (state/topo-points)
        point-count (count topo-points)]
    [:div.tool-options
     [:h4 "Elevation Point"]
     [:p.hint "Place points with known elevations"]
     (cond
       pending-position
       [:div.form-field
        [:label "Enter Elevation"]
        [:span.form-value
         (if (seq elevation-input)
           (str elevation-input " m")
           "Type value, press Enter")]]

       selected-point-id
       [:div.form-field
        [:label "Selected Point"]
        [:span.form-value "Delete or drag to move"]]

       :else
       [:div.form-field
        [:label "Points Placed"]
        [:span.form-value (str point-count " point" (when (not= point-count 1) "s"))]])
     [:div.form-info
      [:span "Click to place, type elevation, Enter to confirm"]]
     [:div.form-info
      [:span "Delete/Backspace removes selected point"]]]))

(defn- plant-tool-options
  "Options panel for the plant tool."
  []
  (let [tool-state (state/tool-state)
        mode (or (:mode tool-state) :single)
        species-id (:species-id tool-state)]
    [:div.tool-options
     [:h4 (if (= mode :row) "Plant Row" "Plant")]
     [:p.hint (if (= mode :row)
                "Drag to place a row of plants"
                "Click to place individual plants")]
     [:div.form-info
      [:span (str "Plant: " (or species-id "Select from library"))]]
     [:div.form-info
      [:span "Press R to toggle row mode"]]]))

(defn- tool-options-panel
  "Show options for the active drawing tool."
  []
  (case (state/active-tool)
    :trace [trace-tool-options]
    :fill [fill-tool-options]
    :area [area-tool-options]
    :scatter [scatter-tool-options]
    :plant [plant-tool-options]
    :contour-trace [contour-trace-tool-options]
    :elevation-point [elevation-point-tool-options]
    nil))

;; =============================================================================
;; Public Components

(defn drawing-tools-section
  "Section with drawing/placement tools."
  []
  [:div.drawing-tools-section
   [:div.section-header "Drawing Tools"]
   [:div.drawing-tools-buttons
    (for [tool-id drawing-tools]
      ^{:key tool-id}
      [drawing-tool-button tool-id])
    [plant-tool-button :single "Plant"]
    [plant-tool-button :row "Plant Row"]
    [scatter-tool-button]]])

(defn plant-library
  "The plant library panel content."
  []
  (let [filter-state (state/get-state :library :filter)
        search-term (or (:search filter-state) "")
        type-filter (:type filter-state)
        filtered-plants (cond->> sample-plants
                          (seq search-term)
                          (filter #(or (re-find (re-pattern (str "(?i)" search-term))
                                                (:common-name %))
                                       (re-find (re-pattern (str "(?i)" search-term))
                                                (:scientific-name %))))
                          type-filter
                          (filter #(= (:type %) type-filter)))]
    [:div.plant-library
     ;; Drawing tools section
     [drawing-tools-section]

     ;; Tool options (shown below buttons when a drawing tool is active)
     [tool-options-panel]

     [:div.section-header "Plants"]

     ;; Search input
     [:input.search-input
      {:type "text"
       :placeholder "Search plants..."
       :value search-term
       :on-change #(state/set-state! [:library :filter :search]
                                     (-> % .-target .-value))}]

     ;; Type filter
     [:div.type-filters
      [:button.filter-btn
       {:class (when (nil? type-filter) "active")
        :on-click #(state/set-state! [:library :filter :type] nil)}
       "All"]
      (for [t [:vegetable :herb :flower :tree]]
        ^{:key t}
        [:button.filter-btn
         {:class (when (= type-filter t) "active")
          :on-click #(state/set-state! [:library :filter :type] t)}
         (name t)])]

     ;; Plant list
     [:div.plant-list
      (for [plant filtered-plants]
        ^{:key (:id plant)}
        [plant-card plant])]]))
