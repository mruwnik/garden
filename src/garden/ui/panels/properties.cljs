(ns garden.ui.panels.properties
  (:require [garden.state :as state]))

(def area-types
  "Available area types with their default colors and labels."
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

(defn- color-input
  "A color picker input."
  [value on-change]
  [:input.color-input
   {:type "color"
    :value (or value "#8B4513")
    :on-change #(on-change (-> % .-target .-value))}])

(defn- text-input
  "A text input field."
  [label value on-change]
  [:div.form-field
   [:label label]
   [:input.text-input
    {:type "text"
     :value (or value "")
     :on-change #(on-change (-> % .-target .-value))}]])

(defn- textarea-input
  "A multiline textarea field."
  [label value on-change]
  [:div.form-field
   [:label label]
   [:textarea.textarea-input
    {:value (or value "")
     :placeholder "Add notes..."
     :on-change #(on-change (-> % .-target .-value))}]])

(defn area-properties
  "Properties editor for a selected area."
  [area-id]
  ;; Re-read area from state to ensure we have current values
  (let [area (state/find-area area-id)]
    (when area
      [:div.area-properties
       [text-input "Name" (:name area)
        #(state/update-area! area-id {:name %})]

       [:div.form-field
        [:label "Color"]
        [color-input (:color area)
         #(state/update-area! area-id {:color %})]]

       [:div.form-field
        [:label "Type"]
        [:select.select-input
         {:value (name (or (:type area) :bed))
          :on-change (fn [e]
                       (let [new-type (keyword (-> e .-target .-value))
                             new-color (get-in area-types [new-type :color])]
                         (state/update-area! area-id {:type new-type
                                                      :color new-color})))}
         (for [[type-key {:keys [label]}] (sort-by (comp :label val) area-types)]
           ^{:key type-key}
           [:option {:value (name type-key)} label])]]

       [textarea-input "Notes" (:notes area)
        #(state/update-area! area-id {:notes %})]

       [:div.form-info
        [:span (str (count (:points area)) " points")]]])))

(defn plant-properties
  "Properties editor for a selected plant."
  [plant]
  [:div.plant-properties
   [:div.form-field
    [:label "Species"]
    [:span.form-value (:species-id plant)]]

   [:div.form-field
    [:label "Position"]
    [:span.form-value
     (let [[x y] (:position plant)]
       (str (Math/round x) ", " (Math/round y) " cm"))]]

   [:div.form-field
    [:label "Life Stage"]
    [:select.select-input
     {:value (name (or (:stage plant) :mature))
      :on-change #(state/update-plant! (:id plant)
                                       {:stage (keyword (-> % .-target .-value))})}
     [:option {:value "seed"} "Seed"]
     [:option {:value "seedling"} "Seedling"]
     [:option {:value "mature"} "Mature"]]]

   [:div.form-field
    [:label "Source"]
    [:select.select-input
     {:value (name (or (:source plant) :seedling))
      :on-change #(state/update-plant! (:id plant)
                                       {:source (keyword (-> % .-target .-value))})}
     [:option {:value "seed"} "Direct sow"]
     [:option {:value "seedling"} "Seedling"]
     [:option {:value "transplant"} "Transplant"]]]])

(defn multi-selection-properties
  "Properties for multiple selected items."
  [selection]
  [:div.multi-selection
   [:p (str (count (:ids selection)) " items selected")]
   [:button.delete-btn
    {:on-click (fn []
                 (case (:type selection)
                   :area (doseq [id (:ids selection)]
                           (state/remove-area! id))
                   :plant (doseq [id (:ids selection)]
                            (state/remove-plant! id))
                   nil)
                 (state/clear-selection!))}
    "Delete All"]])

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
    nil))

(defn no-selection
  "Shown when nothing is selected."
  []
  ;; Explicitly deref active-tool to ensure Reagent tracks this dependency
  (let [active-tool (state/active-tool)]
    (if-let [tool-panel (tool-options-panel)]
      tool-panel
      [:div.no-selection
       [:p "Select an item to edit its properties"]
       [:p.hint "Click on an area or plant to select it"]])))

(defn properties-panel
  "The properties panel content."
  []
  (let [selection (state/selection)
        selected-ids (:ids selection)
        selection-type (:type selection)
        ;; Read active-tool and tool-state to ensure re-render when tool changes
        _active-tool (state/active-tool)
        _tool-state (state/tool-state)]
    [:div.properties-panel
     (cond
       ;; Nothing selected
       (empty? selected-ids)
       [no-selection]

       ;; Multiple items selected
       (> (count selected-ids) 1)
       [multi-selection-properties selection]

       ;; Single area selected
       (= selection-type :area)
       [area-properties (first selected-ids)]

       ;; Single plant selected
       (= selection-type :plant)
       (when-let [plant (state/find-plant (first selected-ids))]
         [plant-properties plant])

       :else
       [no-selection])]))
