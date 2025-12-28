(ns garden.data.area-types
  "Shared area type definitions used throughout the application.

   This centralizes the area type data to avoid duplication across:
   - properties panel, library panel, trace tools, fill tools")

;; =============================================================================
;; Area Types

(def area-types
  "All available area types with their labels and default colors."
  {:bed       {:label "Garden Bed" :color "#8B6914"}
   :path      {:label "Path" :color "#d4a574"}
   :water     {:label "Water" :color "#4a90d9"}
   :structure {:label "Structure" :color "#607D8B"}
   :lawn      {:label "Lawn" :color "#7CB342"}
   :rocks     {:label "Rocks/Gravel" :color "#9E9E9E"}
   :hedge     {:label "Hedge" :color "#2E7D32"}
   :mulch     {:label "Mulch" :color "#5D4037"}
   :patio     {:label "Patio/Deck" :color "#8D6E63"}
   :sand      {:label "Sand" :color "#E8D5B7"}})

;; =============================================================================
;; Accessors

(defn get-label
  "Get the display label for an area type."
  [type-key]
  (get-in area-types [type-key :label]))

(defn get-color
  "Get the default color for an area type."
  [type-key]
  (get-in area-types [type-key :color]))

(defn all-types
  "Get all area type keys."
  []
  (keys area-types))

(defn sorted-by-label
  "Get area types sorted by their label."
  []
  (sort-by (comp :label val) area-types))
