(ns garden.ui.panels.water
  "Water simulation settings modal."
  (:require [garden.state :as state]
            [garden.simulation.water :as water-sim]
            [garden.canvas.core :as canvas]))

(defn water-settings-modal
  "Modal for configuring water simulation parameters."
  []
  (let [rain-rate (or (state/get-state :water-sim :rain-rate-mm-hr) 10.0)
        evap-rate (or (state/get-state :water-sim :evaporation-mm-hr) 5.0)
        infil-rate (or (state/get-state :water-sim :infiltration-mm-hr) 0.0)
        running? (water-sim/running?)
        raining? (water-sim/raining?)]
    [:div.settings-overlay
     {:on-click #(state/set-state! [:ui :water-modal-open?] false)}
     [:div.settings-modal
      {:on-click #(.stopPropagation %)
       :style {:width "400px"}}
      [:h3 "Water Simulation Settings"]

      ;; Status and control buttons
      [:div.form-field
       {:style {:background (if running? "#e8f5e9" "#f5f5f5")
                :padding "12px"
                :border-radius "4px"
                :margin-bottom "16px"}}
       [:div {:style {:display "flex" :align-items "center" :justify-content "space-between"}}
        [:div {:style {:display "flex" :align-items "center" :gap "8px"}}
         [:div {:style {:width "10px"
                        :height "10px"
                        :border-radius "50%"
                        :background (cond
                                      raining? "#4CAF50"
                                      running? "#FFC107"
                                      :else "#9E9E9E")}}]
         [:span {:style {:font-weight "500"}}
          (cond
            raining? "Rain active"
            running? "Simulation running (draining)"
            :else "Simulation stopped")]]
        [:div {:style {:display "flex" :gap "8px"}}
         (if raining?
           [:button.btn-secondary
            {:style {:background "#f44336" :color "white" :border-color "#f44336"}
             :on-click #(canvas/stop-rain!)}
            "Stop Rain"]
           [:button.btn-primary
            {:on-click #(canvas/start-rain!)}
            "Start Rain"])
         (when running?
           [:button.btn-secondary
            {:on-click #(canvas/reset-water!)}
            "Clear Water"])]]]

      ;; Rain rate
      [:div.form-field
       [:label "Rain Rate"]
       [:div {:style {:font-size "12px" :color "#666" :margin-bottom "4px"}}
        "Amount of rainfall per hour (typical: light 2.5, moderate 7.5, heavy 50+)"]
       [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
        [:input.text-input
         {:type "number"
          :min "0"
          :max "500"
          :step "1"
          :value (int rain-rate)
          :style {:width "80px"}
          :on-change #(let [v (js/parseFloat (.. % -target -value))]
                        (when (>= v 0)
                          (water-sim/set-rain-rate! v)))}]
        [:span "mm/hour"]
        [:button.btn-secondary {:on-click #(water-sim/set-rain-rate! 2.5)} "Light"]
        [:button.btn-secondary {:on-click #(water-sim/set-rain-rate! 10)} "Moderate"]
        [:button.btn-secondary {:on-click #(water-sim/set-rain-rate! 50)} "Heavy"]]]

      ;; Evaporation rate
      [:div.form-field
       [:label "Evaporation Rate"]
       [:div {:style {:font-size "12px" :color "#666" :margin-bottom "4px"}}
        "Water lost to evaporation (real-world: 2-6 mm/day, use higher for faster sim)"]
       [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
        [:input.text-input
         {:type "number"
          :min "0"
          :max "100"
          :step "0.5"
          :value (.toFixed evap-rate 1)
          :style {:width "80px"}
          :on-change #(let [v (js/parseFloat (.. % -target -value))]
                        (when (>= v 0)
                          (water-sim/set-evaporation-rate! v)))}]
        [:span "mm/hour"]
        [:button.btn-secondary {:on-click #(water-sim/set-evaporation-rate! 1)} "Slow"]
        [:button.btn-secondary {:on-click #(water-sim/set-evaporation-rate! 5)} "Normal"]
        [:button.btn-secondary {:on-click #(water-sim/set-evaporation-rate! 20)} "Fast"]]]

      ;; Infiltration rate
      [:div.form-field
       [:label "Infiltration Rate"]
       [:div {:style {:font-size "12px" :color "#666" :margin-bottom "4px"}}
        "Water absorbed by ground (depends on soil: sand 50+, loam 10-20, clay 1-5)"]
       [:div {:style {:display "flex" :gap "8px" :align-items "center"}}
        [:input.text-input
         {:type "number"
          :min "0"
          :max "200"
          :step "1"
          :value (.toFixed infil-rate 1)
          :style {:width "80px"}
          :on-change #(let [v (js/parseFloat (.. % -target -value))]
                        (when (>= v 0)
                          (water-sim/set-infiltration-rate! v)))}]
        [:span "mm/hour"]
        [:button.btn-secondary {:on-click #(water-sim/set-infiltration-rate! 0)} "None"]
        [:button.btn-secondary {:on-click #(water-sim/set-infiltration-rate! 5)} "Clay"]
        [:button.btn-secondary {:on-click #(water-sim/set-infiltration-rate! 25)} "Loam"]]]

      ;; Presets section
      [:div.form-field
       {:style {:border-top "1px solid #ddd" :margin-top "16px" :padding-top "16px"}}
       [:label "Quick Presets"]
       [:div {:style {:display "flex" :gap "8px" :flex-wrap "wrap"}}
        [:button.btn-secondary
         {:on-click #(do (water-sim/set-rain-rate! 50)
                         (water-sim/set-evaporation-rate! 2)
                         (water-sim/set-infiltration-rate! 5))}
         "Storm"]
        [:button.btn-secondary
         {:on-click #(do (water-sim/set-rain-rate! 10)
                         (water-sim/set-evaporation-rate! 5)
                         (water-sim/set-infiltration-rate! 10))}
         "Normal Rain"]
        [:button.btn-secondary
         {:on-click #(do (water-sim/set-rain-rate! 2)
                         (water-sim/set-evaporation-rate! 10)
                         (water-sim/set-infiltration-rate! 0))}
         "Drizzle"]
        [:button.btn-secondary
         {:on-click #(do (water-sim/set-rain-rate! 0)
                         (water-sim/set-evaporation-rate! 20)
                         (water-sim/set-infiltration-rate! 50))}
         "Dry Out"]]]

      ;; Close button
      [:div.settings-buttons
       [:button.btn-primary
        {:on-click #(state/set-state! [:ui :water-modal-open?] false)}
        "Done"]]]]))
