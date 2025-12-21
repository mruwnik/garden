(ns garden.ui.app
  (:require [reagent.core :as r]
            [garden.state :as state]
            [garden.canvas.core :as canvas]
            [garden.canvas.viewport :as viewport]
            [garden.canvas.render :as render]
            [garden.tools.protocol :as tools]
            [garden.ui.toolbar :as toolbar]
            [garden.ui.panels.library :as library]
            [garden.ui.panels.properties :as properties]
            [garden.ui.panels.chat :as chat]
            [garden.ui.panels.reference :as reference]
            ;; Load tools to register them
            [garden.tools.pan]
            [garden.tools.select]
            [garden.tools.area]
            [garden.tools.plant]
            [garden.tools.scatter]
            [garden.tools.trace]
            [garden.tools.fill]))

(defn- get-canvas-point
  "Get the point relative to the canvas from a mouse event."
  [event canvas-el]
  (let [rect (.getBoundingClientRect canvas-el)
        x (- (.-clientX event) (.-left rect))
        y (- (.-clientY event) (.-top rect))]
    [x y]))

(defn canvas-component
  "The main canvas component."
  []
  (let [canvas-ref (atom nil)]
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
            (canvas/resize-canvas! width height))))

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
              (state/set-state! [:ui :hover :plant-id] nil)
              (state/set-state! [:ui :mouse :canvas-pos] nil))

            :on-wheel
            (fn [e]
              (.preventDefault e)
              (when-let [el @canvas-ref]
                (let [point (get-canvas-point e el)
                      delta (.-deltaY e)
                      factor (if (neg? delta) 1.1 0.9)]
                  (state/zoom-at! point factor))))

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
                (let [species-id (.getData (.-dataTransfer e) "text/plain")
                      screen-point (get-canvas-point e el)
                      canvas-point (viewport/screen->canvas screen-point)]
                  (when (seq species-id)
                    (let [plant-id (state/add-plant! {:species-id species-id
                                                      :position canvas-point
                                                      :planted-date (js/Date.)
                                                      :source :seedling})]
                      (state/select! :plant #{plant-id}))))))}]))})))

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

(defn status-bar
  "Status bar showing mouse coordinates and other info."
  []
  (let [canvas-pos (state/get-state :ui :mouse :canvas-pos)
        zoom (state/zoom)
        active-tool (state/active-tool)]
    [:div.status-bar
     [:span.status-item
      (if canvas-pos
        (let [[x y] canvas-pos]
          (str "X: " (Math/round x) "  Y: " (Math/round y)))
        "X: --  Y: --")]
     [:span.status-separator "|"]
     [:span.status-item (str "Zoom: " (Math/round (* zoom 100)) "%")]
     [:span.status-separator "|"]
     [:span.status-item (str "Tool: " (name active-tool))]]))

(defn app
  "Root application component."
  []
  (let [chat-open? (state/get-state :chat :open?)
        ref-modal-open? (state/get-state :ui :reference-modal-open?)]
    [:div.app
     [toolbar/toolbar]
     [:div.main-content
      [left-panel]
      [:div.canvas-container
       [canvas-component]
       [status-bar]]
      [right-panel]
      ;; Chat panel
      (when chat-open?
        [chat/chat-panel])
      ;; Chat toggle button (always visible)
      [chat/chat-toggle-button]
      ;; Reference image modal
      (when ref-modal-open?
        [reference/reference-modal])]]))

(defn mount-app
  "Mount the app to the DOM."
  []
  (r/render [app] (.getElementById js/document "app")))
