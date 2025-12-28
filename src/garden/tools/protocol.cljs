(ns garden.tools.protocol
  "Tool system infrastructure.

   Provides the ITool protocol that all tools implement, along with:
   - Tool registry for storing tool implementations
   - Event dispatching to the active tool
   - Tool switching with proper activate/deactivate lifecycle"
  (:require [garden.state :as state]
            [garden.canvas.viewport :as viewport]))

;; =============================================================================
;; Tool Protocol

(defprotocol ITool
  "Protocol for interactive canvas tools."
  (tool-id [this] "Return the tool's keyword identifier.")
  (tool-label [this] "Return the display name.")
  (tool-icon [this] "Return the icon identifier.")
  (cursor [this] "Return the CSS cursor for this tool.")

  ;; Event handlers - called with canvas coordinates
  (on-activate [this] "Called when tool becomes active.")
  (on-deactivate [this] "Called when switching away from tool.")
  (on-mouse-down [this point event] "Handle mouse down.")
  (on-mouse-move [this point event] "Handle mouse move.")
  (on-mouse-up [this point event] "Handle mouse up.")
  (on-key-down [this event] "Handle key down."))

;; =============================================================================
;; Tool Registry

(defonce tools (atom {}))

(defn register-tool!
  "Register a tool implementation."
  [tool]
  (swap! tools assoc (tool-id tool) tool))

(defn get-tool
  "Get a tool by its id."
  [tool-id]
  (get @tools tool-id))

(defn active-tool
  "Get the currently active tool."
  []
  (get-tool (state/active-tool)))

(defn all-tools
  "Get all registered tools."
  []
  (vals @tools))

;; =============================================================================
;; Event Dispatch

(defn dispatch-mouse-down!
  "Dispatch mouse down event to active tool."
  [screen-point event]
  (when-let [tool (active-tool)]
    (let [canvas-point (viewport/screen->canvas screen-point)]
      (on-mouse-down tool canvas-point event))))

(defn dispatch-mouse-move!
  "Dispatch mouse move event to active tool."
  [screen-point event]
  (when-let [tool (active-tool)]
    (let [canvas-point (viewport/screen->canvas screen-point)]
      (on-mouse-move tool canvas-point event))))

(defn dispatch-mouse-up!
  "Dispatch mouse up event to active tool."
  [screen-point event]
  (when-let [tool (active-tool)]
    (let [canvas-point (viewport/screen->canvas screen-point)]
      (on-mouse-up tool canvas-point event))))

(defn dispatch-key-down!
  "Dispatch key down event to active tool."
  [event]
  (when-let [tool (active-tool)]
    (on-key-down tool event)))

;; =============================================================================
;; Tool Switching

(defn activate-tool!
  "Switch to a different tool."
  [new-tool-id]
  (let [current-id (state/active-tool)]
    (when (not= current-id new-tool-id)
      ;; Deactivate current tool
      (when-let [current-tool (get-tool current-id)]
        (on-deactivate current-tool))
      ;; Activate new tool
      (state/set-tool! new-tool-id)
      (when-let [new-tool (get-tool new-tool-id)]
        (state/set-cursor! (cursor new-tool))
        (on-activate new-tool)))))
