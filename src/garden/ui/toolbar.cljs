(ns garden.ui.toolbar
  "Main toolbar component.

   Provides quick access to:
   - Navigation tools (Select, Pan)
   - Undo/Redo controls
   - Zoom controls
   - Display toggles (grid, background, spacing)
   - Ground data and 3D view
   - Water simulation controls
   - Panel toggles"
  (:require [garden.state :as state]
            [garden.tools.protocol :as tools]
            [garden.canvas.core :as canvas]
            [garden.simulation.water :as water-sim]))

;; =============================================================================
;; Tool Buttons

(defn tool-button
  "A single tool button."
  [tool-id]
  (when-let [tool (tools/get-tool tool-id)]
    (let [label (tools/tool-label tool)
          active? (= tool-id (state/active-tool))]
      [:button.tool-btn
       {:class (when active? "active")
        :title label
        :on-click #(tools/activate-tool! tool-id)}
       label])))

;; =============================================================================
;; Main Toolbar

(defn toolbar
  "The main toolbar component."
  []
  [:div.toolbar
   ;; Only show navigation tools (Select, Pan) in top toolbar
   [tool-button :select]
   [tool-button :pan]

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
     :on-click #(let [{:keys [width height]} (:size (state/viewport))]
                  (state/zoom-at! [(/ width 2) (/ height 2)] 1.2))}
    "+"]
   [:button.tool-btn
    {:title "Zoom Out"
     :on-click #(let [{:keys [width height]} (:size (state/viewport))]
                  (state/zoom-at! [(/ width 2) (/ height 2)] 0.8))}
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

   ;; Grid labels toggle
   [:button.tool-btn
    {:title "Toggle Grid Labels"
     :class (when (state/get-state :ui :grid :labels?) "active")
     :on-click #(state/update-state! [:ui :grid :labels?] not)}
    "Labels"]

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

   ;; Ground data button - unified imagery + topo modal
   (let [has-ref? (some? (state/get-state :ui :reference-image :image))
         has-topo? (some? (state/topo-elevation-data))
         ref-visible? (state/get-state :ui :reference-image :visible?)
         topo-visible? (state/get-state :topo :visible?)
         has-data? (or has-ref? has-topo?)
         any-visible? (or ref-visible? topo-visible?)]
     [:button.tool-btn
      {:title "Ground data (imagery + topography)"
       :class (when (and has-data? any-visible?) "active")
       :on-click #(state/set-state! [:ui :ground-modal-open?] true)}
      "Ground"])

   ;; 2D/3D view toggle (only shown when topo data available)
   (let [has-topo? (some? (state/topo-elevation-data))
         view-mode (state/get-state :view-mode)]
     (when has-topo?
       [:button.tool-btn
        {:title (if (= view-mode :3d) "Switch to 2D view" "Switch to 3D view")
         :class (when (= view-mode :3d) "active")
         :on-click #(state/set-state! [:view-mode] (if (= view-mode :3d) :2d :3d))}
        (if (= view-mode :3d) "2D" "3D")]))

   ;; Water simulation controls (only shown when topo data available)
   (let [has-topo? (some? (state/topo-elevation-data))
         running? (water-sim/running?)
         raining? (water-sim/raining?)]
     (when has-topo?
       [:button.tool-btn
        {:title "Water simulation settings"
         :class (when raining? "active")
         :on-click #(state/set-state! [:ui :water-modal-open?] true)}
        "Rain"]))

   [:div.toolbar-separator]

   ;; Clear All button
   [:button.tool-btn.danger
    {:title "Clear all areas and plants"
     :on-click #(when (js/confirm "Clear all areas and plants from the garden?")
                  (state/clear-all!)
                  (state/clear-selection!))}
    "Clear All"]

   [:div.toolbar-spacer]

   ;; Panel toggles (right-aligned)
   [:button.tool-btn
    {:title "Toggle Plants panel"
     :class (when (state/get-state :ui :panels :left :open?) "active")
     :on-click #(state/update-state! [:ui :panels :left :open?] not)}
    "Plants"]
   [:button.tool-btn
    {:title "Toggle Properties panel"
     :class (when (state/get-state :ui :panels :right :open?) "active")
     :on-click #(state/update-state! [:ui :panels :right :open?] not)}
    "Props"]])
