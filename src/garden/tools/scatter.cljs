(ns garden.tools.scatter
  "Scatter tool - draw a rectangle and scatter plants in it."
  (:require [garden.tools.protocol :as p]
            [garden.state :as state]
            [garden.ui.panels.library :as library]))

(defn- scatter-plants!
  "Scatter plants in the given rectangle."
  [species-id min-x min-y max-x max-y count-target]
  (let [;; Get plant spacing from library
        plant-data (first (filter #(= (:id %) species-id) library/sample-plants))
        spacing (or (:spacing-cm plant-data) 50)
        ;; Calculate how many can actually fit with proper spacing
        width (- max-x min-x)
        height (- max-y min-y)
        cols (max 1 (int (/ width spacing)))
        rows (max 1 (int (/ height spacing)))
        max-plants (* cols rows)
        actual-count (min count-target max-plants)
        ;; Generate positions with spacing + jitter
        positions (take actual-count
                        (shuffle
                         (for [col (range cols)
                               row (range rows)]
                           (let [base-x (+ min-x (* col spacing) (/ spacing 2))
                                 base-y (+ min-y (* row spacing) (/ spacing 2))
                                 ;; Add random jitter (up to 30% of spacing)
                                 jitter (* spacing 0.3)
                                 x (+ base-x (- (rand jitter) (/ jitter 2)))
                                 y (+ base-y (- (rand jitter) (/ jitter 2)))]
                             [x y]))))]
    (doseq [[x y] positions]
      (state/add-plant! {:species-id species-id
                         :position [x y]
                         :stage :mature
                         :source :scatter}))
    (count positions)))

(defrecord ScatterTool []
  p/ITool
  (tool-id [_] :scatter)
  (tool-label [_] "Scatter")
  (tool-icon [_] "scatter")
  (cursor [_] "crosshair")

  (on-activate [_]
    ;; Preserve the species-id if coming from plant tool
    (let [current-species (get-in @state/app-state [:tool :state :species-id])]
      (state/set-tool-state! {:start nil
                              :preview nil
                              :count 20
                              :species-id current-species})))

  (on-deactivate [_]
    (state/set-tool-state! nil))

  (on-mouse-down [_ point _event]
    (state/update-tool-state! assoc :start point))

  (on-mouse-move [_ point _event]
    (when (state/tool-state)
      (state/update-tool-state! assoc :preview point)))

  (on-mouse-up [_ point _event]
    (let [{:keys [start count]} (state/tool-state)]
      (when start
        ;; Get selected plant species
        (let [species-id (or (get-in @state/app-state [:tool :state :species-id])
                             (first (map :id library/sample-plants)))]
          (when species-id
            (let [min-x (min (first start) (first point))
                  max-x (max (first start) (first point))
                  min-y (min (second start) (second point))
                  max-y (max (second start) (second point))]
              ;; Only scatter if area is large enough
              (when (and (> (- max-x min-x) 50)
                         (> (- max-y min-y) 50))
                (scatter-plants! species-id min-x min-y max-x max-y count)))))
        (state/update-tool-state! assoc :start nil :preview nil))))

  (on-key-down [_ event]
    (let [key (.-key event)]
      ;; Number keys to adjust count
      (when (re-matches #"[0-9]" key)
        (let [digit (js/parseInt key 10)
              ;; Map 0-9 to useful counts: 0=100, 1=10, 2=20, ..., 9=90
              new-count (if (zero? digit) 100 (* digit 10))]
          (state/update-tool-state! assoc :count new-count))))))

;; Register the tool
(p/register-tool! (->ScatterTool))
