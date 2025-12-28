(ns garden.ui.app
  "Root application component.

   This namespace assembles the complete garden planning application:
   - Canvas for 2D/3D garden visualization
   - Toolbar with tools and controls
   - Side panels for plants and properties
   - Status bar with coordinates and state
   - Modal dialogs for data import"
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [garden.state :as state]
            [garden.canvas.core :as canvas]
            [garden.canvas.viewport :as viewport]
            [garden.canvas.render :as render]
            [garden.canvas.terrain3d :as terrain3d]
            [garden.topo.core :as topo]
            [garden.tools.protocol :as tools]
            [garden.constants :as const]
            [garden.data.plants :as plants]
            [garden.ui.toolbar :as toolbar]
            [garden.ui.panels.library :as library]
            [garden.ui.panels.properties :as properties]
            [garden.ui.panels.chat :as chat]
            [garden.ui.panels.reference :as reference]
            [garden.ui.panels.topo :as topo-panel]
            [garden.ui.panels.ground :as ground]
            [garden.ui.panels.water :as water-panel]
            ;; Load tools to register them
            [garden.tools.pan]
            [garden.tools.select]
            [garden.tools.area]
            [garden.tools.plant]
            [garden.tools.scatter]
            [garden.tools.trace]
            [garden.tools.fill]
            [garden.tools.contour-trace]
            [garden.tools.elevation-point]))

;; =============================================================================
;; Canvas Event Handling

(defn- get-canvas-point
  "Get the point relative to the canvas from a mouse event."
  [event canvas-el]
  (let [rect (.getBoundingClientRect canvas-el)
        x (- (.-clientX event) (.-left rect))
        y (- (.-clientY event) (.-top rect))]
    [x y]))

(defn- handle-wheel
  "Handle wheel events for zooming. Defined outside component for stable reference."
  [canvas-ref e]
  (.preventDefault e)
  (when-let [el @canvas-ref]
    (let [point (get-canvas-point e el)
          delta (.-deltaY e)
          factor (if (neg? delta) 1.1 0.9)]
      (state/zoom-at! point factor))))

;; =============================================================================
;; Canvas Component

(defn canvas-component
  "The main canvas component."
  []
  (let [canvas-ref (atom nil)
        wheel-handler (atom nil)]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (when-let [el @canvas-ref]
          (canvas/init-canvas! el)
          ;; Set initial size (leave room for status bar)
          (let [parent (.-parentElement el)
                width (.-clientWidth parent)
                height (- (.-clientHeight parent) 25)]
            (set! (.-width el) width)
            (set! (.-height el) height)
            (canvas/resize-canvas! width height))
          ;; Add wheel listener with passive: false to allow preventDefault
          (let [handler #(handle-wheel canvas-ref %)]
            (reset! wheel-handler handler)
            (.addEventListener el "wheel" handler #js {:passive false}))))

      :component-will-unmount
      (fn [_]
        (when-let [el @canvas-ref]
          (when-let [handler @wheel-handler]
            (.removeEventListener el "wheel" handler))))

      :component-did-update
      (fn [_]
        (canvas/force-render!))

      :reagent-render
      (fn []
        (let [cursor (state/get-state :tool :cursor)]
          [:canvas.garden-canvas
           {:ref #(reset! canvas-ref %)
            :style {:cursor (or cursor "default")}
            :tab-index 0  ; Make canvas focusable for keyboard events

            :on-mouse-down
            (fn [e]
              (.preventDefault e)
              (when-let [el @canvas-ref]
                (.focus el)
                (tools/dispatch-mouse-down! (get-canvas-point e el) e)))

            :on-mouse-move
            (fn [e]
              (when-let [el @canvas-ref]
                (let [screen-point (get-canvas-point e el)
                      canvas-point (viewport/screen->canvas screen-point)]
                  ;; Store mouse position for status bar
                  (state/set-state! [:ui :mouse :canvas-pos] canvas-point)
                  ;; Dispatch to tool
                  (tools/dispatch-mouse-move! screen-point e)
                  ;; Update hover state for tooltips
                  (let [hovered-plant (state/find-plant-at canvas-point render/get-plant-radius)]
                    (state/set-state! [:ui :hover :plant-id] (:id hovered-plant))))))

            :on-mouse-up
            (fn [e]
              (when-let [el @canvas-ref]
                (tools/dispatch-mouse-up! (get-canvas-point e el) e)))

            :on-mouse-leave
            (fn [_]
              ;; Batch both state updates into one swap
              (swap! state/app-state
                     #(-> %
                          (assoc-in [:ui :hover :plant-id] nil)
                          (assoc-in [:ui :mouse :canvas-pos] nil))))

            ;; Note: wheel handler added manually in component-did-mount
            ;; with {passive: false} to allow preventDefault

            :on-key-down
            (fn [e]
              (tools/dispatch-key-down! e))

            :on-drag-over
            (fn [e]
              (.preventDefault e)
              (set! (.-dropEffect (.-dataTransfer e)) "copy"))

            :on-drop
            (fn [e]
              (.preventDefault e)
              (when-let [el @canvas-ref]
                (let [species-id (.getData (.-dataTransfer e) const/species-drag-mime-type)
                      screen-point (get-canvas-point e el)
                      canvas-point (viewport/screen->canvas screen-point)]
                  ;; Validate species exists in library before creating plant
                  (when (and (seq species-id) (plants/get-plant species-id))
                    (let [plant-id (state/add-plant! {:species-id species-id
                                                      :position canvas-point
                                                      :planted-date (js/Date.)
                                                      :source :seedling})]
                      (state/select! :plant #{plant-id}))))))}]))})))

;; =============================================================================
;; Side Panels

(defn left-panel
  "Left panel with plant library."
  []
  (let [panel-state (state/get-state :ui :panels :left)]
    (when (:open? panel-state)
      [:div.panel.left-panel
       {:style {:width (:width panel-state)}}
       [:div.panel-header
        [:span "Plants"]
        [:button.panel-close
         {:on-click #(state/set-state! [:ui :panels :left :open?] false)}
         "×"]]
       [library/plant-library]])))

(defn right-panel
  "Right panel with properties."
  []
  (let [panel-state (state/get-state :ui :panels :right)]
    (when (:open? panel-state)
      [:div.panel.right-panel
       {:style {:width (:width panel-state)}}
       [:div.panel-header
        [:span "Properties"]
        [:button.panel-close
         {:on-click #(state/set-state! [:ui :panels :right :open?] false)}
         "×"]]
       [properties/properties-panel]])))

;; =============================================================================
;; Status Bar

(defn status-bar
  "Status bar showing mouse coordinates and other info."
  []
  (let [canvas-pos (state/get-state :ui :mouse :canvas-pos)
        zoom (state/zoom)
        active-tool (state/active-tool)
        has-topo? (some? (state/topo-elevation-data))
        has-geo? (topo/has-geo-info?)
        elevation (when (and canvas-pos has-topo?)
                    (topo/get-elevation-with-fallback canvas-pos))
        geo-coords (when (and canvas-pos has-geo?)
                     (topo/garden->latlon canvas-pos))]
    [:div.status-bar
     [:span.status-item
      (if canvas-pos
        (let [[x y] canvas-pos]
          (str "X: " (Math/round x) "  Y: " (Math/round y)))
        "X: --  Y: --")]
     ;; Elevation display (only when topo data loaded)
     (when has-topo?
       [:<>
        [:span.status-separator "|"]
        [:span.status-item
         (if elevation
           (str "Elev: " (.toFixed elevation 1) "m")
           "Elev: --")]])
     ;; Lat/Lon coordinates (when available from GeoTIFF)
     (when has-geo?
       [:<>
        [:span.status-separator "|"]
        [:span.status-item
         (if geo-coords
           (let [[lat lon] geo-coords]
             (str (.toFixed lat 5) "°N, " (.toFixed lon 5) "°E"))
           "-- °N, -- °E")]])
     [:span.status-separator "|"]
     [:span.status-item (str "Zoom: " (Math/round (* zoom 100)) "%")]
     [:span.status-separator "|"]
     [:span.status-item (str "Tool: " (if active-tool (name active-tool) "--"))]]))

;; =============================================================================
;; Loading Overlay

(defn loading-overlay
  "Overlay shown during long-running operations."
  []
  (let [loading? (state/get-state :ui :loading?)
        message (state/get-state :ui :loading-message)]
    (when loading?
      [:div.loading-overlay
       [:div.loading-spinner]
       [:div.loading-message (or message "Processing...")]])))

(defn status-bar-3d
  "Status bar for 3D view showing camera position and direction."
  []
  (let [camera-3d (state/get-state :camera-3d)
        cam-pos (:position camera-3d)
        cam-dir (:direction camera-3d)
        has-geo? (topo/has-geo-info?)
        ;; Convert camera XZ position to lat/lon (Y is elevation)
        geo-coords (when (and cam-pos has-geo?)
                     (let [[x _y z] cam-pos]
                       (topo/garden->latlon [x z])))]
    [:div.status-bar
     [:span.status-item "3D View"]
     [:span.status-separator "|"]
     [:span.status-item
      (if cam-pos
        (let [[x y z] cam-pos]
          ;; Convert from cm to meters
          (str "X: " (.toFixed (/ x 100) 1) "m Y: " (.toFixed (/ y 100) 1) "m Z: " (.toFixed (/ z 100) 1) "m"))
        "Cam: --")]
     (when has-geo?
       [:<>
        [:span.status-separator "|"]
        [:span.status-item
         (if geo-coords
           (let [[lat lon] geo-coords]
             (str (.toFixed lat 5) "°N, " (.toFixed lon 5) "°E"))
           "-- °N, -- °E")]])
     [:span.status-separator "|"]
     [:span.status-item
      (if cam-dir
        (let [[degrees cardinal] cam-dir]
          (str "Facing: " cardinal " (" degrees "°)"))
        "Facing: --")]]))

;; =============================================================================
;; Root Application

(defn app
  "Root application component."
  []
  (let [chat-open? (state/get-state :chat :open?)
        ground-modal-open? (state/get-state :ui :ground-modal-open?)
        water-modal-open? (state/get-state :ui :water-modal-open?)
        view-mode (state/get-state :view-mode)
        ;; Keep legacy modals for backwards compatibility
        ref-modal-open? (state/get-state :ui :reference-modal-open?)
        topo-modal-open? (state/get-state :ui :topo-modal-open?)]
    [:div.app
     [toolbar/toolbar]
     [:div.main-content
      [left-panel]
      [:div.canvas-container
       (if (= view-mode :3d)
         ;; 3D terrain view
         [:<>
          [terrain3d/terrain-3d-component]
          [status-bar-3d]]
         ;; 2D canvas view (default)
         [:<>
          [canvas-component]
          [status-bar]])]
      [right-panel]
      ;; Chat panel
      (when chat-open?
        [chat/chat-panel])
      ;; Chat toggle button (always visible)
      [chat/chat-toggle-button]
      ;; Ground data modal (unified)
      (when ground-modal-open?
        [ground/ground-modal])
      ;; Water simulation settings modal
      (when water-modal-open?
        [water-panel/water-settings-modal])
      ;; Legacy modals (kept for backwards compatibility)
      (when ref-modal-open?
        [reference/reference-modal])
      (when topo-modal-open?
        [topo-panel/topo-modal])
      ;; Loading overlay
      [loading-overlay]]]))

;; =============================================================================
;; Mount

(defn mount-app
  "Mount the app to the DOM."
  []
  (rdom/render [app] (.getElementById js/document "app")))
