(ns garden.display
  (:require [garden.state :as state]
            [garden.handlers :as handlers]
            [clojure.string :refer (join split)]))

(defn canvas []
  (let []
    (fn [{:keys [id width height]}]
      [:div {:class "canvas left"}
       [:canvas
        {:ref id :width width :height height
         :on-mouse-move handlers/line-to-point
         :on-mouse-out handlers/mouse-out
         :on-click handlers/select-point}
        "Please upgrade your browser"]])))

(def grid-canvas
  (with-meta canvas
    {:component-did-mount #(-> % (aget "refs") (aget "garden-canvas") (.getContext "2d") state/set-context)}))


(defn description-item [item & {:keys [on-click classes]}]
  [:p {:id (:id item)
       :class (join " " classes)
       :on-click on-click}
    [:dt (:name item)] [:dd (:desc item)]])

(defn layer-classes [layer]
  (concat
   ["item-handler"]
   (if (= (state/get :current :id) (:id layer)) ["selected"] [])))

(defn description-list [items & {:keys [on-click]}]
  (->> items
       (map #(description-item %1 :on-click on-click :classes (layer-classes %1)))
       (concat [:dl])
       (into [])))


(defn side-bar [summary contents & {:keys [start-open] :or {start-open true}}]
  [:div {:class "options-container"}
   [:details {:open start-open}
    [:summary summary] contents]])


(defn text-input [label name accessor]
  [:p
   [:label {:for name} label]
   [:input {:type "text" :name name
            :value (or  (state/get-in accessor) "")
            :on-change (partial handlers/update-value accessor)}]])

(defn garden-layers []
  (side-bar "Patches" (description-list (state/get :layers) :on-click handlers/select-layer)))

(defn edit-layer []
  (when (state/current-layer)
    [:div {:class "options-container"}
     [:h4 "Edit patch"]
     [:form {:id "edit-layer"}
      [text-input "Name" "layer-name" (state/current-accessor :name)]
      [text-input "Description" "layer-desc" (state/current-accessor :desc)]
      ]])
                                        )

(defn garden-app []
  [:div
   [:div {:class "side-bar left"}
    [edit-layer]
    [garden-layers]]
   [grid-canvas {:id "garden-canvas" :width (state/get :canvas :width) :height (state/get :canvas :height)}]
   ])
