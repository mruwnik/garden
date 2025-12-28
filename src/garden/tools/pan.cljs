(ns garden.tools.pan
  "Pan tool for navigating the canvas.

   Click and drag to move the viewport around the garden."
  (:require [garden.tools.protocol :as proto]
            [garden.state :as state]))

(defrecord PanTool []
  proto/ITool
  (tool-id [_] :pan)
  (tool-label [_] "Pan")
  (tool-icon [_] "move")
  (cursor [_]
    (if (get-in (state/tool-state) [:dragging?])
      "grabbing"
      "grab"))

  (on-activate [_]
    (state/set-tool-state! {:dragging? false :last-point nil}))

  (on-deactivate [_]
    (state/set-tool-state! nil))

  (on-mouse-down [_ point _event]
    (state/set-tool-state! {:dragging? true :last-point point})
    (state/set-cursor! "grabbing"))

  (on-mouse-move [_ point _event]
    (when-let [tool-state (state/tool-state)]
      (when (:dragging? tool-state)
        (let [[lx ly] (:last-point tool-state)
              [cx cy] point
              zoom (state/zoom)
              dx (* (- cx lx) zoom)
              dy (* (- cy ly) zoom)]
          (state/pan! dx dy)
          (state/update-tool-state! assoc :last-point point)))))

  (on-mouse-up [_ _point _event]
    (state/update-tool-state! assoc :dragging? false :last-point nil)
    (state/set-cursor! "grab"))

  (on-key-down [_ _event]
    nil))

(proto/register-tool! (->PanTool))
