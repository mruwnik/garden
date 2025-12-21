(ns garden.ui.toolbar
  (:require [garden.state :as state]
            [garden.tools.protocol :as tools]))

(defn tool-button
  "A single tool button."
  [tool]
  (let [id (tools/tool-id tool)
        label (tools/tool-label tool)
        active? (= id (state/active-tool))]
    [:button.tool-btn
     {:class (when active? "active")
      :title label
      :on-click #(tools/activate-tool! id)}
     label]))

(defn- plant-mode-toggle
  "Toggle button for plant mode (single/row)."
  []
  (let [tool-state (state/tool-state)
        current-mode (or (:mode tool-state) :single)
        is-row? (= current-mode :row)]
    [:button.tool-btn.mode-toggle
     {:class (when is-row? "active")
      :title (if is-row? "Row mode (press R)" "Single mode (press R)")
      :on-click #(state/update-tool-state! assoc :mode (if is-row? :single :row))}
     (if is-row? "Row" "Single")]))

(defn toolbar
  "The main toolbar component."
  []
  [:div.toolbar
   (for [tool (tools/all-tools)]
     ^{:key (tools/tool-id tool)}
     [tool-button tool])

   ;; Show plant mode toggle when Plant tool is active
   (when (= :plant (state/active-tool))
     [plant-mode-toggle])

   [:div.toolbar-separator]

   ;; Undo/Redo controls
   [:button.tool-btn
    {:title "Undo (Ctrl+Z)"
     :disabled (not (state/can-undo?))
     :on-click #(state/undo!)}
    "Undo"]
   [:button.tool-btn
    {:title "Redo (Ctrl+Shift+Z)"
     :disabled (not (state/can-redo?))
     :on-click #(state/redo!)}
    "Redo"]

   [:div.toolbar-separator]

   ;; Zoom controls
   [:button.tool-btn
    {:title "Zoom In"
     :on-click #(state/zoom-at! [400 300] 1.2)}
    "+"]
   [:button.tool-btn
    {:title "Zoom Out"
     :on-click #(state/zoom-at! [400 300] 0.8)}
    "-"]
   [:button.tool-btn
    {:title "Reset View"
     :on-click #(do (state/set-state! [:viewport :zoom] 1.0)
                    (state/set-state! [:viewport :offset] [0 0]))}
    "Reset"]

   [:div.toolbar-separator]

   ;; Background toggle
   [:button.tool-btn
    {:title "Toggle Background"
     :class (when (state/get-state :ui :background :visible?) "active")
     :on-click #(state/update-state! [:ui :background :visible?] not)}
    "Grass"]

   ;; Grid toggle
   [:button.tool-btn
    {:title "Toggle Grid"
     :class (when (state/get-state :ui :grid :visible?) "active")
     :on-click #(state/update-state! [:ui :grid :visible?] not)}
    "Grid"]

   ;; Snap to grid toggle
   [:button.tool-btn
    {:title "Snap to Grid"
     :class (when (state/get-state :ui :grid :snap?) "active")
     :on-click #(state/update-state! [:ui :grid :snap?] not)}
    "Snap"]

   ;; Spacing circles toggle
   [:button.tool-btn
    {:title "Show plant spacing/footprint"
     :class (when (state/get-state :ui :spacing-circles :visible?) "active")
     :on-click #(state/update-state! [:ui :spacing-circles :visible?] not)}
    "Spacing"]

   [:div.toolbar-separator]

   ;; Clear All button
   [:button.tool-btn.danger
    {:title "Clear all areas and plants"
     :on-click #(when (js/confirm "Clear all areas and plants from the garden?")
                  (doseq [area (state/areas)]
                    (state/remove-area! (:id area)))
                  (doseq [plant (state/plants)]
                    (state/remove-plant! (:id plant)))
                  (state/clear-selection!))}
    "Clear All"]])
