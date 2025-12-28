(ns garden.ui.panels.library
  "Left sidebar panel with drawing tools and plant library.

   Provides:
   - Drawing tool buttons (area, trace, fill, contour-trace, elevation-point)
   - Plant placement tools (single, row, scatter)
   - Tool-specific options panels
   - Searchable plant catalog with drag-and-drop support
   - 100+ plants with growth-habit-based visual representation"
  (:require [clojure.string :as str]
            [garden.state :as state]
            [garden.tools.protocol :as tools]
            [garden.data.area-types :as area-types]
            [garden.data.plants :as plants]
            [garden.constants :as const]))

;; =============================================================================
;; Plant Catalog Data
;;
;; Plants are now loaded from garden.data.plants which contains 100+ plants
;; with rich visual attributes (growth habit, foliage type, bloom colors, etc.)

;; =============================================================================
;; Plant Card Component

(defn- habit-icon
  "Get a small visual indicator for growth habit."
  [habit]
  (case habit
    :columnar "▮"
    :spreading "◎"
    :vase "⋁"
    :weeping "☂"
    :mounding "⌒"
    :upright "↑"
    :prostrate "━"
    :rosette "✿"
    :clumping "|||"
    :vining "~"
    :spiky "✶"
    :bushy "●"
    :fountain "⋔"
    :fan "◗"
    "○"))

(defn plant-card
  "A single plant in the library.
   Displays the plant with its primary color, bloom color, and growth habit."
  [plant]
  (let [selected-species (state/get-state :tool :state :species-id)
        has-bloom? (some? (:bloom-color plant))]
    [:div.plant-card
     {:class (when (= (:id plant) selected-species) "selected")
      :draggable true
      :on-drag-start (fn [e]
                       (.setData (.-dataTransfer e) const/species-drag-mime-type (:id plant))
                       (set! (.-effectAllowed (.-dataTransfer e)) "copy")
                       (tools/activate-tool! :plant)
                       (state/update-tool-state! assoc :species-id (:id plant)))
      :on-click (fn []
                  (let [current-tool (state/active-tool)]
                    (when (not= current-tool :scatter)
                      (tools/activate-tool! :plant)))
                  (state/update-tool-state! assoc :species-id (:id plant)))}
     ;; Color indicator with optional bloom accent
     [:div.plant-color
      {:style {:background-color (:color plant)
               :position "relative"}}
      (when has-bloom?
        [:div {:style {:position "absolute"
                       :bottom "2px"
                       :right "2px"
                       :width "8px"
                       :height "8px"
                       :border-radius "50%"
                       :background-color (:bloom-color plant)
                       :border "1px solid rgba(255,255,255,0.5)"}}])]
     [:div.plant-info
      [:div.plant-name (:common-name plant)]
      [:div.plant-meta
       {:style {:display "flex" :justify-content "space-between" :align-items "center"}}
       [:span.plant-type (name (:category plant))]
       [:span.plant-habit {:style {:opacity 0.6 :font-size "10px"}}
        (habit-icon (:habit plant))]]]]))

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
;; Area Type Selector

(defn- area-type-selector
  "Dropdown to select area type for drawing tools."
  [current-type on-change]
  [:div.form-field
   [:label "Area Type"]
   [:select.select-input
    {:value (name (or current-type :water))
     :on-change #(on-change (keyword (-> % .-target .-value)))}
    (for [[type-key {:keys [label]}] (area-types/sorted-by-label)]
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

(def category-groups
  "Grouped categories for the filter UI."
  [[:tree :shrub]
   [:perennial :annual]
   [:vegetable :herb]
   [:grass :groundcover]
   [:vine :succulent]
   [:aquatic :fern]])

(defn plant-library
  "The plant library panel content.
   Shows 100+ plants organized by category with search filtering."
  []
  (let [filter-state (state/get-state :library :filter)
        search-term (or (:search filter-state) "")
        category-filter (:category filter-state)
        search-lower (str/lower-case search-term)
        matches-search? (fn [plant]
                          (or (str/includes? (str/lower-case (:common-name plant)) search-lower)
                              (str/includes? (str/lower-case (:scientific-name plant)) search-lower)))
        filtered-plants (cond->> plants/plant-library
                          (seq search-term)
                          (filter matches-search?)
                          category-filter
                          (filter #(= (:category %) category-filter)))
        plant-count (count filtered-plants)]
    [:div.plant-library
     ;; Drawing tools section
     [drawing-tools-section]

     ;; Tool options (shown below buttons when a drawing tool is active)
     [tool-options-panel]

     [:div.section-header
      {:style {:display "flex" :justify-content "space-between" :align-items "center"}}
      [:span "Plants"]
      [:span {:style {:font-size "11px" :opacity 0.6}}
       (str plant-count " plants")]]

     ;; Search input
     [:input.search-input
      {:type "text"
       :placeholder "Search plants..."
       :value search-term
       :on-change #(state/set-state! [:library :filter :search]
                                     (-> % .-target .-value))}]

     ;; Category filters - two per row for compact display
     [:div.type-filters
      {:style {:display "flex" :flex-wrap "wrap" :gap "4px"}}
      [:button.filter-btn
       {:class (when (nil? category-filter) "active")
        :style {:flex "1 1 auto" :min-width "40px"}
        :on-click #(state/set-state! [:library :filter :category] nil)}
       "All"]
      (for [[cat1 cat2] category-groups]
        ^{:key cat1}
        [:<>
         [:button.filter-btn
          {:class (when (= category-filter cat1) "active")
           :style {:flex "1 1 auto" :min-width "40px" :font-size "11px"}
           :on-click #(state/set-state! [:library :filter :category] cat1)}
          (name cat1)]
         [:button.filter-btn
          {:class (when (= category-filter cat2) "active")
           :style {:flex "1 1 auto" :min-width "40px" :font-size "11px"}
           :on-click #(state/set-state! [:library :filter :category] cat2)}
          (name cat2)]])]

     ;; Plant list
     [:div.plant-list
      (for [plant filtered-plants]
        ^{:key (:id plant)}
        [plant-card plant])]]))
