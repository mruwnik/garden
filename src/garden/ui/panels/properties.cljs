(ns garden.ui.panels.properties
  (:require [garden.state :as state]))

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
                       (let [new-type (keyword (-> e .-target .-value))]
                         (state/update-area! area-id {:type new-type})))}
         [:option {:value "bed"} "Garden Bed"]
         [:option {:value "path"} "Path"]
         [:option {:value "water"} "Water"]
         [:option {:value "structure"} "Structure"]]]

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

(defn no-selection
  "Shown when nothing is selected."
  []
  [:div.no-selection
   [:p "Select an item to edit its properties"]
   [:p.hint "Click on an area or plant to select it"]])

(defn properties-panel
  "The properties panel content."
  []
  (let [selection (state/selection)
        selected-ids (:ids selection)
        selection-type (:type selection)]
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
