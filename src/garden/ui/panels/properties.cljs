(ns garden.ui.panels.properties
  "Right sidebar panel for editing selected items.

   Features:
   - Area editing: name, type, color, notes
   - Plant editing: species, position, life stage, source
   - Topography display: elevation, slope, aspect (when topo data available)
   - Multi-selection support with batch delete"
  (:require [reagent.core :as r]
            [garden.state :as state]
            [garden.topo.slope :as slope]
            [garden.data.area-types :as area-types]))

;; =============================================================================
;; Debounce Utilities

(def ^:private debounce-delay 500) ; ms before committing text changes

;; =============================================================================
;; Form Components

(defn- color-input
  "A color picker input."
  [value on-change]
  [:input.color-input
   {:type "color"
    :value (or value "#8B4513")
    :on-change #(on-change (-> % .-target .-value))}])

(defn- debounced-text-input
  "A text input with debounced commits to prevent history spam.
   Uses local state for instant feedback, commits on blur or after delay."
  [label initial-value on-commit]
  (let [local-value (r/atom (or initial-value ""))
        timer-id (r/atom nil)
        commit! (fn []
                  (when @timer-id
                    (js/clearTimeout @timer-id)
                    (reset! timer-id nil))
                  (when (not= @local-value initial-value)
                    (on-commit @local-value)))]
    (fn [label initial-value _on-commit]
      ;; Sync with external value when it changes (e.g., undo)
      (when (and (not= initial-value @local-value)
                 (nil? @timer-id))
        (reset! local-value (or initial-value "")))
      [:div.form-field
       [:label label]
       [:input.text-input
        {:type "text"
         :value @local-value
         :on-change (fn [e]
                      (let [v (-> e .-target .-value)]
                        (reset! local-value v)
                        (when @timer-id (js/clearTimeout @timer-id))
                        (reset! timer-id (js/setTimeout commit! debounce-delay))))
         :on-blur commit!}]])))

(defn- debounced-textarea-input
  "A multiline textarea with debounced commits."
  [label initial-value on-commit]
  (let [local-value (r/atom (or initial-value ""))
        timer-id (r/atom nil)
        commit! (fn []
                  (when @timer-id
                    (js/clearTimeout @timer-id)
                    (reset! timer-id nil))
                  (when (not= @local-value initial-value)
                    (on-commit @local-value)))]
    (fn [label initial-value _on-commit]
      (when (and (not= initial-value @local-value)
                 (nil? @timer-id))
        (reset! local-value (or initial-value "")))
      [:div.form-field
       [:label label]
       [:textarea.textarea-input
        {:value @local-value
         :placeholder "Add notes..."
         :on-change (fn [e]
                      (let [v (-> e .-target .-value)]
                        (reset! local-value v)
                        (when @timer-id (js/clearTimeout @timer-id))
                        (reset! timer-id (js/setTimeout commit! debounce-delay))))
         :on-blur commit!}]])))

;; =============================================================================
;; Property Editors

(defn area-properties
  "Properties editor for a selected area."
  [area-id]
  ;; Re-read area from state to ensure we have current values
  (let [area (state/find-area area-id)]
    (if area
      [:div.area-properties
       ^{:key (str area-id "-name")}
       [debounced-text-input "Name" (:name area)
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
                             new-color (area-types/get-color new-type)]
                         (state/update-area! area-id {:type new-type
                                                      :color new-color})))}
         (for [[type-key {:keys [label]}] (area-types/sorted-by-label)]
           ^{:key type-key}
           [:option {:value (name type-key)} label])]]

       ^{:key (str area-id "-notes")}
       [debounced-textarea-input "Notes" (:notes area)
        #(state/update-area! area-id {:notes %})]

       ;; Topography section (only shown if topo data available)
       (when (state/topo-elevation-data)
         (let [topo-analysis (slope/analyze-area-topography (:points area))]
           (when topo-analysis
             [:div.topo-section
              {:style {:margin-top "12px" :padding-top "12px" :border-top "1px solid #ddd"}}
              [:label {:style {:font-weight "bold" :display "block" :margin-bottom "8px"}}
               "Topography"]
              ;; Elevation
              (when-let [{:keys [min max avg]} (:elevation topo-analysis)]
                [:div.form-field
                 [:label "Elevation"]
                 [:span.form-value
                  (str (.toFixed min 1) " - " (.toFixed max 1) "m")
                  [:span {:style {:color "#666" :font-size "11px" :margin-left "8px"}}
                   (str "(avg: " (.toFixed avg 1) "m)")]]])
              ;; Slope
              (when-let [{:keys [min max avg category]} (:slope topo-analysis)]
                [:div.form-field
                 [:label "Slope"]
                 [:span.form-value
                  (str (.toFixed avg 1) "°")
                  [:span {:style {:color "#666" :font-size "11px" :margin-left "8px"}}
                   (str "(" (slope/slope-category-label category) ")")]]])
              ;; Aspect
              (when-let [{:keys [dominant label full-label]} (:aspect topo-analysis)]
                [:div.form-field
                 [:label "Aspect"]
                 [:span.form-value
                  label
                  [:span {:style {:color "#666" :font-size "11px" :margin-left "8px"}}
                   (str "(" full-label "-facing)")]]])])))

       [:div.form-info
        [:span (str (count (:points area)) " points")]]]
      ;; Area not found - show message instead of returning nil
      [:div.area-properties
       [:p "Area not found"]
       [:p.hint (str "ID: " area-id)]])))

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
                   :area (state/remove-areas-batch! (vec (:ids selection)))
                   :plant (state/remove-plants-batch! (vec (:ids selection)))
                   nil)
                 (state/clear-selection!))}
    "Delete All"]])

(defn no-selection
  "Shown when nothing is selected."
  []
  [:div.no-selection
   [:p "Select an item to edit its properties"]
   [:p.hint "Click on an area or plant to select it"]])

;; =============================================================================
;; Main Panel

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
       (if-let [plant (state/find-plant (first selected-ids))]
         [plant-properties plant]
         [:div.plant-properties
          [:p "Plant not found"]
          [:p.hint (str "ID: " (first selected-ids))]])

       :else
       [no-selection])]))
