(ns garden.ui.panels.library
  (:require [garden.state :as state]
            [garden.tools.protocol :as tools]))

;; Sample plant library data
;; spacing-cm is the recommended spacing between plants (diameter of mature footprint)
(def sample-plants
  [{:id "tomato-cherry"
    :common-name "Cherry Tomato"
    :scientific-name "Solanum lycopersicum"
    :type :vegetable
    :color "#ff6347"
    :spacing-cm 60}
   {:id "basil"
    :common-name "Basil"
    :scientific-name "Ocimum basilicum"
    :type :herb
    :color "#228b22"
    :spacing-cm 25}
   {:id "sunflower"
    :common-name "Sunflower"
    :scientific-name "Helianthus annuus"
    :type :flower
    :color "#ffd700"
    :spacing-cm 45}
   {:id "rose"
    :common-name "Rose"
    :scientific-name "Rosa"
    :type :flower
    :color "#ff1493"
    :spacing-cm 60}
   {:id "tulip"
    :common-name "Tulip"
    :scientific-name "Tulipa"
    :type :flower
    :color "#ff69b4"
    :spacing-cm 15}
   {:id "lavender"
    :common-name "Lavender"
    :scientific-name "Lavandula"
    :type :flower
    :color "#9370db"
    :spacing-cm 45}
   {:id "marigold"
    :common-name "Marigold"
    :scientific-name "Tagetes"
    :type :flower
    :color "#ffa500"
    :spacing-cm 25}
   {:id "carrot"
    :common-name "Carrot"
    :scientific-name "Daucus carota"
    :type :vegetable
    :color "#ff8c00"
    :spacing-cm 8}
   {:id "lettuce"
    :common-name "Lettuce"
    :scientific-name "Lactuca sativa"
    :type :vegetable
    :color "#90ee90"
    :spacing-cm 30}
   {:id "rosemary"
    :common-name "Rosemary"
    :scientific-name "Salvia rosmarinus"
    :type :herb
    :color "#2e8b57"
    :spacing-cm 60}
   {:id "apple-tree"
    :common-name "Apple Tree"
    :scientific-name "Malus domestica"
    :type :tree
    :color "#8b4513"
    :spacing-cm 400}
   ;; Japanese Garden Plants
   {:id "cherry-blossom"
    :common-name "Cherry Blossom"
    :scientific-name "Prunus serrulata"
    :type :tree
    :color "#ffb7c5"
    :spacing-cm 500}
   {:id "japanese-maple"
    :common-name "Japanese Maple"
    :scientific-name "Acer palmatum"
    :type :tree
    :color "#c41e3a"
    :spacing-cm 400}
   {:id "japanese-pine"
    :common-name "Japanese Black Pine"
    :scientific-name "Pinus thunbergii"
    :type :tree
    :color "#355e3b"
    :spacing-cm 600}
   {:id "plum-tree"
    :common-name "Plum Tree"
    :scientific-name "Prunus mume"
    :type :tree
    :color "#dda0dd"
    :spacing-cm 400}
   {:id "bamboo"
    :common-name "Bamboo"
    :scientific-name "Phyllostachys"
    :type :tree
    :color "#7cfc00"
    :spacing-cm 100}
   {:id "azalea"
    :common-name "Azalea"
    :scientific-name "Rhododendron"
    :type :flower
    :color "#ff6ec7"
    :spacing-cm 80}
   {:id "iris"
    :common-name "Japanese Iris"
    :scientific-name "Iris ensata"
    :type :flower
    :color "#5d3fd3"
    :spacing-cm 30}
   {:id "moss"
    :common-name "Moss"
    :scientific-name "Bryophyta"
    :type :herb
    :color "#4a5d23"
    :spacing-cm 10}
   {:id "wisteria"
    :common-name "Wisteria"
    :scientific-name "Wisteria floribunda"
    :type :flower
    :color "#c9a0dc"
    :spacing-cm 200}
   {:id "camellia"
    :common-name "Camellia"
    :scientific-name "Camellia japonica"
    :type :flower
    :color "#e34234"
    :spacing-cm 150}])

(defn plant-card
  "A single plant in the library."
  [plant]
  (let [selected-species (state/get-state :tool :state :species-id)]
    [:div.plant-card
     {:class (when (= (:id plant) selected-species) "selected")
      :draggable true
      :on-drag-start (fn [e]
                       ;; Store plant ID in drag data
                       (.setData (.-dataTransfer e) "text/plain" (:id plant))
                       (set! (.-effectAllowed (.-dataTransfer e)) "copy")
                       ;; Also activate plant tool
                       (tools/activate-tool! :plant)
                       (state/update-tool-state! assoc :species-id (:id plant)))
      :on-click (fn []
                  ;; Select this plant for placement
                  (tools/activate-tool! :plant)
                  (state/update-tool-state! assoc :species-id (:id plant)))}
     [:div.plant-color
      {:style {:background-color (:color plant)}}]
     [:div.plant-info
      [:div.plant-name (:common-name plant)]
      [:div.plant-type (name (:type plant))]]]))

(defn plant-library
  "The plant library panel content."
  []
  (let [filter-state (state/get-state :library :filter)
        search-term (or (:search filter-state) "")
        type-filter (:type filter-state)
        filtered-plants (cond->> sample-plants
                          (seq search-term)
                          (filter #(or (re-find (re-pattern (str "(?i)" search-term))
                                                (:common-name %))
                                       (re-find (re-pattern (str "(?i)" search-term))
                                                (:scientific-name %))))
                          type-filter
                          (filter #(= (:type %) type-filter)))]
    [:div.plant-library
     ;; Search input
     [:input.search-input
      {:type "text"
       :placeholder "Search plants..."
       :value search-term
       :on-change #(state/set-state! [:library :filter :search]
                                     (-> % .-target .-value))}]

     ;; Type filter
     [:div.type-filters
      [:button.filter-btn
       {:class (when (nil? type-filter) "active")
        :on-click #(state/set-state! [:library :filter :type] nil)}
       "All"]
      (for [t [:vegetable :herb :flower :tree]]
        ^{:key t}
        [:button.filter-btn
         {:class (when (= type-filter t) "active")
          :on-click #(state/set-state! [:library :filter :type] t)}
         (name t)])]

     ;; Plant list
     [:div.plant-list
      (for [plant filtered-plants]
        ^{:key (:id plant)}
        [plant-card plant])]]))
