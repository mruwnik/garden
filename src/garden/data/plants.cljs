(ns garden.data.plants
  "Comprehensive plant library with visual representation attributes.

   Plants are represented using a symbolic system based on:
   - Growth habit: Determines the overall shape (2D silhouette and 3D form)
   - Foliage type: Affects texture and detail rendering
   - Colors: Primary (foliage) and secondary (bloom/fruit/bark)
   - Dimensions: Height and spread for scaling

   Growth Habits (primary shape):
   - :columnar    - Tall and narrow (cypress, poplar)
   - :spreading   - Wide horizontal branching (oak)
   - :vase        - Narrow base, spreading top (elm)
   - :weeping     - Drooping branches (willow)
   - :mounding    - Dome-shaped (boxwood, shrubs)
   - :upright     - Vertical, slightly spreading (most trees)
   - :prostrate   - Ground-hugging (groundcovers)
   - :rosette     - Circular leaf arrangement (lettuce, succulents)
   - :clumping    - Dense clusters (grasses, bamboo)
   - :vining      - Climbing/trailing (ivy, wisteria)
   - :spiky       - Pointed leaves radiating (yucca, agave)
   - :bushy       - Dense irregular (vegetables, herbs)
   - :fountain    - Arching from center (ornamental grasses)
   - :fan         - Fan-shaped (palms, ginkgo)

   Foliage Types (texture):
   - :broadleaf   - Large leaf shapes
   - :needle      - Coniferous needles
   - :grass       - Long thin blades
   - :succulent   - Thick fleshy
   - :palm        - Fan or feather fronds
   - :fern        - Delicate fronds
   - :scale       - Tiny overlapping (junipers)
   - :compound    - Multiple leaflets")

;; =============================================================================
;; Plant Categories for filtering

(def categories
  {:tree       {:label "Trees" :icon "🌳"}
   :shrub      {:label "Shrubs" :icon "🌿"}
   :perennial  {:label "Perennials" :icon "🌸"}
   :annual     {:label "Annuals" :icon "🌻"}
   :vegetable  {:label "Vegetables" :icon "🥬"}
   :herb       {:label "Herbs" :icon "🌱"}
   :grass      {:label "Grasses" :icon "🌾"}
   :groundcover {:label "Groundcovers" :icon "☘️"}
   :vine       {:label "Vines" :icon "🍇"}
   :succulent  {:label "Succulents" :icon "🌵"}
   :aquatic    {:label "Aquatic" :icon "💧"}
   :fern       {:label "Ferns" :icon "🌿"}})

;; =============================================================================
;; Color Palettes for visual variety

(def foliage-colors
  {:dark-green    "#2D5016"
   :forest-green  "#228B22"
   :lime-green    "#7CFC00"
   :olive         "#808000"
   :sage          "#87AE73"
   :blue-green    "#4A766E"
   :silver-green  "#8FBC8F"
   :chartreuse    "#ADFF2F"
   :bronze        "#8B4513"
   :burgundy      "#722F37"
   :red           "#C41E3A"
   :orange        "#FF8C00"
   :yellow-green  "#9ACD32"
   :purple        "#663399"
   :variegated    "#90EE90"})

(def bloom-colors
  {:white         "#FFFFFF"
   :cream         "#FFFDD0"
   :yellow        "#FFD700"
   :orange        "#FFA500"
   :coral         "#FF7F50"
   :red           "#DC143C"
   :pink          "#FFB6C1"
   :hot-pink      "#FF69B4"
   :magenta       "#FF00FF"
   :purple        "#9370DB"
   :lavender      "#E6E6FA"
   :blue          "#4169E1"
   :sky-blue      "#87CEEB"})

;; =============================================================================
;; Comprehensive Plant Library (~100 plants)

(def plant-library
  [;; =========================================================================
   ;; TREES - Large woody plants
   ;; =========================================================================

   ;; Deciduous Trees
   {:id "oak-english"
    :common-name "English Oak"
    :scientific-name "Quercus robur"
    :category :tree
    :habit :spreading
    :foliage :broadleaf
    :evergreen? false
    :color "#355E3B"
    :bloom-color nil
    :height-cm 2000
    :spread-cm 2000
    :spacing-cm 1500}

   {:id "maple-japanese"
    :common-name "Japanese Maple"
    :scientific-name "Acer palmatum"
    :category :tree
    :habit :mounding
    :foliage :broadleaf
    :evergreen? false
    :color "#C41E3A"
    :bloom-color nil
    :height-cm 600
    :spread-cm 600
    :spacing-cm 400}

   {:id "cherry-blossom"
    :common-name "Cherry Blossom"
    :scientific-name "Prunus serrulata"
    :category :tree
    :habit :vase
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFB7C5"
    :height-cm 800
    :spread-cm 800
    :spacing-cm 500}

   {:id "willow-weeping"
    :common-name "Weeping Willow"
    :scientific-name "Salix babylonica"
    :category :tree
    :habit :weeping
    :foliage :broadleaf
    :evergreen? false
    :color "#9ACD32"
    :bloom-color nil
    :height-cm 1200
    :spread-cm 1000
    :spacing-cm 800}

   {:id "birch-silver"
    :common-name "Silver Birch"
    :scientific-name "Betula pendula"
    :category :tree
    :habit :upright
    :foliage :broadleaf
    :evergreen? false
    :color "#90EE90"
    :bloom-color nil
    :height-cm 1500
    :spread-cm 600
    :spacing-cm 500}

   {:id "magnolia"
    :common-name "Magnolia"
    :scientific-name "Magnolia grandiflora"
    :category :tree
    :habit :upright
    :foliage :broadleaf
    :evergreen? true
    :color "#2D5016"
    :bloom-color "#FFFFFF"
    :height-cm 1000
    :spread-cm 600
    :spacing-cm 500}

   {:id "dogwood"
    :common-name "Flowering Dogwood"
    :scientific-name "Cornus florida"
    :category :tree
    :habit :spreading
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFB6C1"
    :height-cm 600
    :spread-cm 600
    :spacing-cm 400}

   {:id "ginkgo"
    :common-name "Ginkgo"
    :scientific-name "Ginkgo biloba"
    :category :tree
    :habit :fan
    :foliage :broadleaf
    :evergreen? false
    :color "#9ACD32"
    :bloom-color nil
    :height-cm 1500
    :spread-cm 800
    :spacing-cm 600}

   {:id "plum-tree"
    :common-name "Plum Tree"
    :scientific-name "Prunus domestica"
    :category :tree
    :habit :vase
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFFFFF"
    :height-cm 500
    :spread-cm 400
    :spacing-cm 400}

   {:id "apple-tree"
    :common-name "Apple Tree"
    :scientific-name "Malus domestica"
    :category :tree
    :habit :spreading
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFB6C1"
    :height-cm 400
    :spread-cm 400
    :spacing-cm 400}

   {:id "pear-tree"
    :common-name "Pear Tree"
    :scientific-name "Pyrus communis"
    :category :tree
    :habit :upright
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFFFFF"
    :height-cm 500
    :spread-cm 350
    :spacing-cm 350}

   {:id "fig-tree"
    :common-name "Fig Tree"
    :scientific-name "Ficus carica"
    :category :tree
    :habit :spreading
    :foliage :broadleaf
    :evergreen? false
    :color "#2D5016"
    :bloom-color nil
    :height-cm 400
    :spread-cm 400
    :spacing-cm 350}

   {:id "olive-tree"
    :common-name "Olive Tree"
    :scientific-name "Olea europaea"
    :category :tree
    :habit :spreading
    :foliage :broadleaf
    :evergreen? true
    :color "#808000"
    :bloom-color "#FFFDD0"
    :height-cm 600
    :spread-cm 500
    :spacing-cm 500}

   {:id "lemon-tree"
    :common-name "Lemon Tree"
    :scientific-name "Citrus limon"
    :category :tree
    :habit :mounding
    :foliage :broadleaf
    :evergreen? true
    :color "#228B22"
    :bloom-color "#FFFFFF"
    :height-cm 400
    :spread-cm 350
    :spacing-cm 350}

   ;; Coniferous Trees
   {:id "pine-japanese"
    :common-name "Japanese Black Pine"
    :scientific-name "Pinus thunbergii"
    :category :tree
    :habit :spreading
    :foliage :needle
    :evergreen? true
    :color "#355E3B"
    :bloom-color nil
    :height-cm 1500
    :spread-cm 800
    :spacing-cm 600}

   {:id "cypress-italian"
    :common-name "Italian Cypress"
    :scientific-name "Cupressus sempervirens"
    :category :tree
    :habit :columnar
    :foliage :scale
    :evergreen? true
    :color "#355E3B"
    :bloom-color nil
    :height-cm 2000
    :spread-cm 200
    :spacing-cm 200}

   {:id "cedar-blue-atlas"
    :common-name "Blue Atlas Cedar"
    :scientific-name "Cedrus atlantica"
    :category :tree
    :habit :spreading
    :foliage :needle
    :evergreen? true
    :color "#4A766E"
    :bloom-color nil
    :height-cm 1500
    :spread-cm 1000
    :spacing-cm 800}

   {:id "spruce-blue"
    :common-name "Blue Spruce"
    :scientific-name "Picea pungens"
    :category :tree
    :habit :columnar
    :foliage :needle
    :evergreen? true
    :color "#4A766E"
    :bloom-color nil
    :height-cm 1500
    :spread-cm 600
    :spacing-cm 500}

   {:id "juniper-tree"
    :common-name "Juniper"
    :scientific-name "Juniperus communis"
    :category :tree
    :habit :columnar
    :foliage :scale
    :evergreen? true
    :color "#355E3B"
    :bloom-color nil
    :height-cm 600
    :spread-cm 200
    :spacing-cm 200}

   {:id "redwood"
    :common-name "Dawn Redwood"
    :scientific-name "Metasequoia glyptostroboides"
    :category :tree
    :habit :columnar
    :foliage :needle
    :evergreen? false
    :color "#228B22"
    :bloom-color nil
    :height-cm 3000
    :spread-cm 600
    :spacing-cm 600}

   ;; =========================================================================
   ;; SHRUBS
   ;; =========================================================================

   {:id "azalea"
    :common-name "Azalea"
    :scientific-name "Rhododendron"
    :category :shrub
    :habit :mounding
    :foliage :broadleaf
    :evergreen? true
    :color "#2D5016"
    :bloom-color "#FF6EC7"
    :height-cm 150
    :spread-cm 150
    :spacing-cm 100}

   {:id "hydrangea"
    :common-name "Hydrangea"
    :scientific-name "Hydrangea macrophylla"
    :category :shrub
    :habit :mounding
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#87CEEB"
    :height-cm 150
    :spread-cm 150
    :spacing-cm 120}

   {:id "boxwood"
    :common-name "Boxwood"
    :scientific-name "Buxus sempervirens"
    :category :shrub
    :habit :mounding
    :foliage :broadleaf
    :evergreen? true
    :color "#2D5016"
    :bloom-color nil
    :height-cm 150
    :spread-cm 150
    :spacing-cm 80}

   {:id "camellia"
    :common-name "Camellia"
    :scientific-name "Camellia japonica"
    :category :shrub
    :habit :upright
    :foliage :broadleaf
    :evergreen? true
    :color "#2D5016"
    :bloom-color "#DC143C"
    :height-cm 300
    :spread-cm 200
    :spacing-cm 150}

   {:id "rhododendron"
    :common-name "Rhododendron"
    :scientific-name "Rhododendron"
    :category :shrub
    :habit :mounding
    :foliage :broadleaf
    :evergreen? true
    :color "#2D5016"
    :bloom-color "#9370DB"
    :height-cm 200
    :spread-cm 200
    :spacing-cm 150}

   {:id "rose-bush"
    :common-name "Rose Bush"
    :scientific-name "Rosa"
    :category :shrub
    :habit :bushy
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FF1493"
    :height-cm 120
    :spread-cm 100
    :spacing-cm 80}

   {:id "lilac"
    :common-name "Lilac"
    :scientific-name "Syringa vulgaris"
    :category :shrub
    :habit :upright
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#E6E6FA"
    :height-cm 300
    :spread-cm 250
    :spacing-cm 200}

   {:id "forsythia"
    :common-name "Forsythia"
    :scientific-name "Forsythia x intermedia"
    :category :shrub
    :habit :fountain
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFD700"
    :height-cm 250
    :spread-cm 250
    :spacing-cm 200}

   {:id "privet"
    :common-name "Privet"
    :scientific-name "Ligustrum"
    :category :shrub
    :habit :upright
    :foliage :broadleaf
    :evergreen? true
    :color "#228B22"
    :bloom-color "#FFFFFF"
    :height-cm 300
    :spread-cm 200
    :spacing-cm 100}

   {:id "butterfly-bush"
    :common-name "Butterfly Bush"
    :scientific-name "Buddleja davidii"
    :category :shrub
    :habit :fountain
    :foliage :broadleaf
    :evergreen? false
    :color "#87AE73"
    :bloom-color "#9370DB"
    :height-cm 250
    :spread-cm 200
    :spacing-cm 150}

   {:id "spirea"
    :common-name "Spirea"
    :scientific-name "Spiraea japonica"
    :category :shrub
    :habit :mounding
    :foliage :broadleaf
    :evergreen? false
    :color "#9ACD32"
    :bloom-color "#FF69B4"
    :height-cm 100
    :spread-cm 120
    :spacing-cm 80}

   {:id "viburnum"
    :common-name "Viburnum"
    :scientific-name "Viburnum opulus"
    :category :shrub
    :habit :mounding
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFFFFF"
    :height-cm 300
    :spread-cm 300
    :spacing-cm 200}

   ;; =========================================================================
   ;; PERENNIALS
   ;; =========================================================================

   {:id "lavender"
    :common-name "Lavender"
    :scientific-name "Lavandula"
    :category :perennial
    :habit :mounding
    :foliage :broadleaf
    :evergreen? true
    :color "#87AE73"
    :bloom-color "#9370DB"
    :height-cm 60
    :spread-cm 60
    :spacing-cm 45}

   {:id "iris"
    :common-name "Iris"
    :scientific-name "Iris germanica"
    :category :perennial
    :habit :clumping
    :foliage :grass
    :evergreen? false
    :color "#4A766E"
    :bloom-color "#4169E1"
    :height-cm 90
    :spread-cm 45
    :spacing-cm 30}

   {:id "peony"
    :common-name "Peony"
    :scientific-name "Paeonia"
    :category :perennial
    :habit :bushy
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFB6C1"
    :height-cm 90
    :spread-cm 90
    :spacing-cm 90}

   {:id "hosta"
    :common-name "Hosta"
    :scientific-name "Hosta"
    :category :perennial
    :habit :mounding
    :foliage :broadleaf
    :evergreen? false
    :color "#4A766E"
    :bloom-color "#E6E6FA"
    :height-cm 60
    :spread-cm 90
    :spacing-cm 60}

   {:id "daylily"
    :common-name "Daylily"
    :scientific-name "Hemerocallis"
    :category :perennial
    :habit :clumping
    :foliage :grass
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFA500"
    :height-cm 90
    :spread-cm 60
    :spacing-cm 45}

   {:id "coneflower"
    :common-name "Coneflower"
    :scientific-name "Echinacea purpurea"
    :category :perennial
    :habit :bushy
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FF69B4"
    :height-cm 90
    :spread-cm 45
    :spacing-cm 45}

   {:id "black-eyed-susan"
    :common-name "Black-Eyed Susan"
    :scientific-name "Rudbeckia hirta"
    :category :perennial
    :habit :bushy
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFD700"
    :height-cm 75
    :spread-cm 45
    :spacing-cm 40}

   {:id "sedum"
    :common-name "Sedum"
    :scientific-name "Sedum spectabile"
    :category :perennial
    :habit :mounding
    :foliage :succulent
    :evergreen? false
    :color "#4A766E"
    :bloom-color "#FF69B4"
    :height-cm 45
    :spread-cm 45
    :spacing-cm 40}

   {:id "salvia"
    :common-name "Salvia"
    :scientific-name "Salvia nemorosa"
    :category :perennial
    :habit :bushy
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#4169E1"
    :height-cm 60
    :spread-cm 45
    :spacing-cm 40}

   {:id "catmint"
    :common-name "Catmint"
    :scientific-name "Nepeta"
    :category :perennial
    :habit :mounding
    :foliage :broadleaf
    :evergreen? false
    :color "#87AE73"
    :bloom-color "#9370DB"
    :height-cm 45
    :spread-cm 60
    :spacing-cm 45}

   {:id "coral-bells"
    :common-name "Coral Bells"
    :scientific-name "Heuchera"
    :category :perennial
    :habit :mounding
    :foliage :broadleaf
    :evergreen? true
    :color "#722F37"
    :bloom-color "#FF7F50"
    :height-cm 45
    :spread-cm 45
    :spacing-cm 35}

   {:id "astilbe"
    :common-name "Astilbe"
    :scientific-name "Astilbe"
    :category :perennial
    :habit :clumping
    :foliage :compound
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FF69B4"
    :height-cm 60
    :spread-cm 45
    :spacing-cm 40}

   {:id "bleeding-heart"
    :common-name "Bleeding Heart"
    :scientific-name "Dicentra spectabilis"
    :category :perennial
    :habit :mounding
    :foliage :compound
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FF69B4"
    :height-cm 75
    :spread-cm 75
    :spacing-cm 60}

   {:id "delphinium"
    :common-name "Delphinium"
    :scientific-name "Delphinium"
    :category :perennial
    :habit :upright
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#4169E1"
    :height-cm 150
    :spread-cm 45
    :spacing-cm 45}

   {:id "phlox"
    :common-name "Garden Phlox"
    :scientific-name "Phlox paniculata"
    :category :perennial
    :habit :upright
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FF69B4"
    :height-cm 90
    :spread-cm 60
    :spacing-cm 50}

   ;; =========================================================================
   ;; ANNUALS
   ;; =========================================================================

   {:id "sunflower"
    :common-name "Sunflower"
    :scientific-name "Helianthus annuus"
    :category :annual
    :habit :upright
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFD700"
    :height-cm 200
    :spread-cm 60
    :spacing-cm 45}

   {:id "marigold"
    :common-name "Marigold"
    :scientific-name "Tagetes"
    :category :annual
    :habit :bushy
    :foliage :compound
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFA500"
    :height-cm 30
    :spread-cm 30
    :spacing-cm 25}

   {:id "petunia"
    :common-name "Petunia"
    :scientific-name "Petunia"
    :category :annual
    :habit :mounding
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FF00FF"
    :height-cm 25
    :spread-cm 45
    :spacing-cm 30}

   {:id "impatiens"
    :common-name "Impatiens"
    :scientific-name "Impatiens walleriana"
    :category :annual
    :habit :mounding
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FF69B4"
    :height-cm 30
    :spread-cm 30
    :spacing-cm 25}

   {:id "zinnia"
    :common-name "Zinnia"
    :scientific-name "Zinnia elegans"
    :category :annual
    :habit :bushy
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#DC143C"
    :height-cm 75
    :spread-cm 30
    :spacing-cm 30}

   {:id "cosmos"
    :common-name "Cosmos"
    :scientific-name "Cosmos bipinnatus"
    :category :annual
    :habit :upright
    :foliage :compound
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFB6C1"
    :height-cm 120
    :spread-cm 45
    :spacing-cm 35}

   {:id "dahlia"
    :common-name "Dahlia"
    :scientific-name "Dahlia"
    :category :annual
    :habit :bushy
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FF7F50"
    :height-cm 90
    :spread-cm 60
    :spacing-cm 60}

   {:id "snapdragon"
    :common-name "Snapdragon"
    :scientific-name "Antirrhinum majus"
    :category :annual
    :habit :upright
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#DC143C"
    :height-cm 90
    :spread-cm 30
    :spacing-cm 25}

   {:id "pansy"
    :common-name "Pansy"
    :scientific-name "Viola tricolor"
    :category :annual
    :habit :mounding
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#663399"
    :height-cm 20
    :spread-cm 25
    :spacing-cm 20}

   {:id "begonia"
    :common-name "Begonia"
    :scientific-name "Begonia"
    :category :annual
    :habit :mounding
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#DC143C"
    :height-cm 30
    :spread-cm 30
    :spacing-cm 25}

   ;; =========================================================================
   ;; VEGETABLES
   ;; =========================================================================

   {:id "tomato"
    :common-name "Tomato"
    :scientific-name "Solanum lycopersicum"
    :category :vegetable
    :habit :bushy
    :foliage :compound
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFD700"
    :height-cm 150
    :spread-cm 60
    :spacing-cm 60}

   {:id "pepper"
    :common-name "Bell Pepper"
    :scientific-name "Capsicum annuum"
    :category :vegetable
    :habit :bushy
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFFFFF"
    :height-cm 75
    :spread-cm 45
    :spacing-cm 45}

   {:id "lettuce"
    :common-name "Lettuce"
    :scientific-name "Lactuca sativa"
    :category :vegetable
    :habit :rosette
    :foliage :broadleaf
    :evergreen? false
    :color "#90EE90"
    :bloom-color nil
    :height-cm 30
    :spread-cm 30
    :spacing-cm 30}

   {:id "carrot"
    :common-name "Carrot"
    :scientific-name "Daucus carota"
    :category :vegetable
    :habit :rosette
    :foliage :compound
    :evergreen? false
    :color "#228B22"
    :bloom-color nil
    :height-cm 30
    :spread-cm 10
    :spacing-cm 8}

   {:id "cabbage"
    :common-name "Cabbage"
    :scientific-name "Brassica oleracea"
    :category :vegetable
    :habit :rosette
    :foliage :broadleaf
    :evergreen? false
    :color "#4A766E"
    :bloom-color nil
    :height-cm 40
    :spread-cm 60
    :spacing-cm 45}

   {:id "broccoli"
    :common-name "Broccoli"
    :scientific-name "Brassica oleracea"
    :category :vegetable
    :habit :rosette
    :foliage :broadleaf
    :evergreen? false
    :color "#2D5016"
    :bloom-color nil
    :height-cm 60
    :spread-cm 45
    :spacing-cm 45}

   {:id "spinach"
    :common-name "Spinach"
    :scientific-name "Spinacia oleracea"
    :category :vegetable
    :habit :rosette
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color nil
    :height-cm 25
    :spread-cm 20
    :spacing-cm 15}

   {:id "kale"
    :common-name "Kale"
    :scientific-name "Brassica oleracea"
    :category :vegetable
    :habit :rosette
    :foliage :broadleaf
    :evergreen? false
    :color "#355E3B"
    :bloom-color nil
    :height-cm 60
    :spread-cm 60
    :spacing-cm 45}

   {:id "zucchini"
    :common-name "Zucchini"
    :scientific-name "Cucurbita pepo"
    :category :vegetable
    :habit :spreading
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFD700"
    :height-cm 60
    :spread-cm 120
    :spacing-cm 90}

   {:id "cucumber"
    :common-name "Cucumber"
    :scientific-name "Cucumis sativus"
    :category :vegetable
    :habit :vining
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFD700"
    :height-cm 30
    :spread-cm 150
    :spacing-cm 60}

   {:id "eggplant"
    :common-name "Eggplant"
    :scientific-name "Solanum melongena"
    :category :vegetable
    :habit :bushy
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#9370DB"
    :height-cm 90
    :spread-cm 60
    :spacing-cm 60}

   {:id "bean-pole"
    :common-name "Pole Bean"
    :scientific-name "Phaseolus vulgaris"
    :category :vegetable
    :habit :vining
    :foliage :compound
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFFFFF"
    :height-cm 200
    :spread-cm 30
    :spacing-cm 15}

   {:id "pea"
    :common-name "Pea"
    :scientific-name "Pisum sativum"
    :category :vegetable
    :habit :vining
    :foliage :compound
    :evergreen? false
    :color "#90EE90"
    :bloom-color "#FFFFFF"
    :height-cm 150
    :spread-cm 30
    :spacing-cm 10}

   {:id "corn"
    :common-name "Sweet Corn"
    :scientific-name "Zea mays"
    :category :vegetable
    :habit :upright
    :foliage :grass
    :evergreen? false
    :color "#228B22"
    :bloom-color nil
    :height-cm 200
    :spread-cm 40
    :spacing-cm 30}

   {:id "potato"
    :common-name "Potato"
    :scientific-name "Solanum tuberosum"
    :category :vegetable
    :habit :bushy
    :foliage :compound
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFFFFF"
    :height-cm 60
    :spread-cm 45
    :spacing-cm 30}

   {:id "onion"
    :common-name "Onion"
    :scientific-name "Allium cepa"
    :category :vegetable
    :habit :clumping
    :foliage :grass
    :evergreen? false
    :color "#4A766E"
    :bloom-color nil
    :height-cm 45
    :spread-cm 15
    :spacing-cm 10}

   {:id "garlic"
    :common-name "Garlic"
    :scientific-name "Allium sativum"
    :category :vegetable
    :habit :clumping
    :foliage :grass
    :evergreen? false
    :color "#4A766E"
    :bloom-color nil
    :height-cm 60
    :spread-cm 15
    :spacing-cm 10}

   {:id "radish"
    :common-name "Radish"
    :scientific-name "Raphanus sativus"
    :category :vegetable
    :habit :rosette
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color nil
    :height-cm 20
    :spread-cm 10
    :spacing-cm 5}

   {:id "beet"
    :common-name "Beet"
    :scientific-name "Beta vulgaris"
    :category :vegetable
    :habit :rosette
    :foliage :broadleaf
    :evergreen? false
    :color "#722F37"
    :bloom-color nil
    :height-cm 35
    :spread-cm 20
    :spacing-cm 10}

   {:id "squash-butternut"
    :common-name "Butternut Squash"
    :scientific-name "Cucurbita moschata"
    :category :vegetable
    :habit :vining
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFD700"
    :height-cm 45
    :spread-cm 300
    :spacing-cm 150}

   ;; =========================================================================
   ;; HERBS
   ;; =========================================================================

   {:id "basil"
    :common-name "Basil"
    :scientific-name "Ocimum basilicum"
    :category :herb
    :habit :bushy
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFFFFF"
    :height-cm 45
    :spread-cm 30
    :spacing-cm 25}

   {:id "rosemary"
    :common-name "Rosemary"
    :scientific-name "Salvia rosmarinus"
    :category :herb
    :habit :upright
    :foliage :needle
    :evergreen? true
    :color "#4A766E"
    :bloom-color "#87CEEB"
    :height-cm 120
    :spread-cm 90
    :spacing-cm 60}

   {:id "thyme"
    :common-name "Thyme"
    :scientific-name "Thymus vulgaris"
    :category :herb
    :habit :prostrate
    :foliage :broadleaf
    :evergreen? true
    :color "#87AE73"
    :bloom-color "#E6E6FA"
    :height-cm 15
    :spread-cm 30
    :spacing-cm 20}

   {:id "oregano"
    :common-name "Oregano"
    :scientific-name "Origanum vulgare"
    :category :herb
    :habit :mounding
    :foliage :broadleaf
    :evergreen? true
    :color "#87AE73"
    :bloom-color "#E6E6FA"
    :height-cm 30
    :spread-cm 45
    :spacing-cm 30}

   {:id "mint"
    :common-name "Mint"
    :scientific-name "Mentha"
    :category :herb
    :habit :spreading
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#E6E6FA"
    :height-cm 45
    :spread-cm 90
    :spacing-cm 45}

   {:id "parsley"
    :common-name "Parsley"
    :scientific-name "Petroselinum crispum"
    :category :herb
    :habit :rosette
    :foliage :compound
    :evergreen? false
    :color "#228B22"
    :bloom-color nil
    :height-cm 30
    :spread-cm 25
    :spacing-cm 20}

   {:id "cilantro"
    :common-name "Cilantro"
    :scientific-name "Coriandrum sativum"
    :category :herb
    :habit :rosette
    :foliage :compound
    :evergreen? false
    :color "#90EE90"
    :bloom-color "#FFFFFF"
    :height-cm 50
    :spread-cm 20
    :spacing-cm 15}

   {:id "dill"
    :common-name "Dill"
    :scientific-name "Anethum graveolens"
    :category :herb
    :habit :upright
    :foliage :compound
    :evergreen? false
    :color "#90EE90"
    :bloom-color "#FFD700"
    :height-cm 90
    :spread-cm 30
    :spacing-cm 25}

   {:id "sage"
    :common-name "Sage"
    :scientific-name "Salvia officinalis"
    :category :herb
    :habit :mounding
    :foliage :broadleaf
    :evergreen? true
    :color "#87AE73"
    :bloom-color "#9370DB"
    :height-cm 60
    :spread-cm 60
    :spacing-cm 45}

   {:id "chives"
    :common-name "Chives"
    :scientific-name "Allium schoenoprasum"
    :category :herb
    :habit :clumping
    :foliage :grass
    :evergreen? false
    :color "#228B22"
    :bloom-color "#E6E6FA"
    :height-cm 30
    :spread-cm 20
    :spacing-cm 15}

   ;; =========================================================================
   ;; GRASSES
   ;; =========================================================================

   {:id "bamboo"
    :common-name "Bamboo"
    :scientific-name "Phyllostachys"
    :category :grass
    :habit :clumping
    :foliage :grass
    :evergreen? true
    :color "#7CFC00"
    :bloom-color nil
    :height-cm 600
    :spread-cm 200
    :spacing-cm 100}

   {:id "fountain-grass"
    :common-name "Fountain Grass"
    :scientific-name "Pennisetum"
    :category :grass
    :habit :fountain
    :foliage :grass
    :evergreen? false
    :color "#DEB887"
    :bloom-color "#F5DEB3"
    :height-cm 90
    :spread-cm 90
    :spacing-cm 75}

   {:id "miscanthus"
    :common-name "Maiden Grass"
    :scientific-name "Miscanthus sinensis"
    :category :grass
    :habit :fountain
    :foliage :grass
    :evergreen? false
    :color "#9ACD32"
    :bloom-color "#F5F5DC"
    :height-cm 180
    :spread-cm 120
    :spacing-cm 100}

   {:id "blue-fescue"
    :common-name "Blue Fescue"
    :scientific-name "Festuca glauca"
    :category :grass
    :habit :clumping
    :foliage :grass
    :evergreen? true
    :color "#4A766E"
    :bloom-color nil
    :height-cm 30
    :spread-cm 30
    :spacing-cm 25}

   {:id "lemongrass"
    :common-name "Lemongrass"
    :scientific-name "Cymbopogon"
    :category :grass
    :habit :clumping
    :foliage :grass
    :evergreen? false
    :color "#9ACD32"
    :bloom-color nil
    :height-cm 120
    :spread-cm 90
    :spacing-cm 60}

   {:id "pampas-grass"
    :common-name "Pampas Grass"
    :scientific-name "Cortaderia selloana"
    :category :grass
    :habit :fountain
    :foliage :grass
    :evergreen? true
    :color "#9ACD32"
    :bloom-color "#FFFDD0"
    :height-cm 300
    :spread-cm 200
    :spacing-cm 180}

   ;; =========================================================================
   ;; GROUNDCOVERS
   ;; =========================================================================

   {:id "moss"
    :common-name "Moss"
    :scientific-name "Bryophyta"
    :category :groundcover
    :habit :prostrate
    :foliage :broadleaf
    :evergreen? true
    :color "#4a5d23"
    :bloom-color nil
    :height-cm 5
    :spread-cm 30
    :spacing-cm 10}

   {:id "creeping-thyme"
    :common-name "Creeping Thyme"
    :scientific-name "Thymus serpyllum"
    :category :groundcover
    :habit :prostrate
    :foliage :broadleaf
    :evergreen? true
    :color "#87AE73"
    :bloom-color "#FF69B4"
    :height-cm 5
    :spread-cm 45
    :spacing-cm 30}

   {:id "ajuga"
    :common-name "Ajuga"
    :scientific-name "Ajuga reptans"
    :category :groundcover
    :habit :prostrate
    :foliage :broadleaf
    :evergreen? true
    :color "#355E3B"
    :bloom-color "#4169E1"
    :height-cm 15
    :spread-cm 45
    :spacing-cm 30}

   {:id "sedum-groundcover"
    :common-name "Stonecrop"
    :scientific-name "Sedum acre"
    :category :groundcover
    :habit :prostrate
    :foliage :succulent
    :evergreen? true
    :color "#9ACD32"
    :bloom-color "#FFD700"
    :height-cm 10
    :spread-cm 45
    :spacing-cm 25}

   {:id "irish-moss"
    :common-name "Irish Moss"
    :scientific-name "Sagina subulata"
    :category :groundcover
    :habit :prostrate
    :foliage :broadleaf
    :evergreen? true
    :color "#228B22"
    :bloom-color "#FFFFFF"
    :height-cm 3
    :spread-cm 30
    :spacing-cm 15}

   ;; =========================================================================
   ;; VINES
   ;; =========================================================================

   {:id "wisteria"
    :common-name "Wisteria"
    :scientific-name "Wisteria floribunda"
    :category :vine
    :habit :vining
    :foliage :compound
    :evergreen? false
    :color "#228B22"
    :bloom-color "#C9A0DC"
    :height-cm 1000
    :spread-cm 400
    :spacing-cm 200}

   {:id "clematis"
    :common-name "Clematis"
    :scientific-name "Clematis"
    :category :vine
    :habit :vining
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#9370DB"
    :height-cm 400
    :spread-cm 150
    :spacing-cm 90}

   {:id "ivy-english"
    :common-name "English Ivy"
    :scientific-name "Hedera helix"
    :category :vine
    :habit :vining
    :foliage :broadleaf
    :evergreen? true
    :color "#2D5016"
    :bloom-color nil
    :height-cm 2000
    :spread-cm 300
    :spacing-cm 60}

   {:id "jasmine"
    :common-name "Jasmine"
    :scientific-name "Jasminum"
    :category :vine
    :habit :vining
    :foliage :broadleaf
    :evergreen? true
    :color "#228B22"
    :bloom-color "#FFFFFF"
    :height-cm 300
    :spread-cm 150
    :spacing-cm 90}

   {:id "grape"
    :common-name "Grape Vine"
    :scientific-name "Vitis vinifera"
    :category :vine
    :habit :vining
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color nil
    :height-cm 800
    :spread-cm 400
    :spacing-cm 200}

   ;; =========================================================================
   ;; SUCCULENTS
   ;; =========================================================================

   {:id "agave"
    :common-name "Agave"
    :scientific-name "Agave americana"
    :category :succulent
    :habit :spiky
    :foliage :succulent
    :evergreen? true
    :color "#4A766E"
    :bloom-color "#FFD700"
    :height-cm 150
    :spread-cm 200
    :spacing-cm 150}

   {:id "aloe"
    :common-name "Aloe Vera"
    :scientific-name "Aloe vera"
    :category :succulent
    :habit :spiky
    :foliage :succulent
    :evergreen? true
    :color "#87AE73"
    :bloom-color "#FFA500"
    :height-cm 60
    :spread-cm 60
    :spacing-cm 45}

   {:id "echeveria"
    :common-name "Echeveria"
    :scientific-name "Echeveria"
    :category :succulent
    :habit :rosette
    :foliage :succulent
    :evergreen? true
    :color "#4A766E"
    :bloom-color "#FF7F50"
    :height-cm 15
    :spread-cm 20
    :spacing-cm 15}

   {:id "yucca"
    :common-name "Yucca"
    :scientific-name "Yucca filamentosa"
    :category :succulent
    :habit :spiky
    :foliage :succulent
    :evergreen? true
    :color "#87AE73"
    :bloom-color "#FFFDD0"
    :height-cm 150
    :spread-cm 120
    :spacing-cm 100}

   {:id "sempervivum"
    :common-name "Hens and Chicks"
    :scientific-name "Sempervivum"
    :category :succulent
    :habit :rosette
    :foliage :succulent
    :evergreen? true
    :color "#87AE73"
    :bloom-color "#FF69B4"
    :height-cm 10
    :spread-cm 30
    :spacing-cm 15}

   ;; =========================================================================
   ;; AQUATIC
   ;; =========================================================================

   {:id "water-lily"
    :common-name "Water Lily"
    :scientific-name "Nymphaea"
    :category :aquatic
    :habit :rosette
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFB6C1"
    :height-cm 10
    :spread-cm 150
    :spacing-cm 90}

   {:id "lotus"
    :common-name "Lotus"
    :scientific-name "Nelumbo nucifera"
    :category :aquatic
    :habit :upright
    :foliage :broadleaf
    :evergreen? false
    :color "#228B22"
    :bloom-color "#FFB6C1"
    :height-cm 120
    :spread-cm 120
    :spacing-cm 90}

   {:id "cattail"
    :common-name "Cattail"
    :scientific-name "Typha"
    :category :aquatic
    :habit :upright
    :foliage :grass
    :evergreen? false
    :color "#87AE73"
    :bloom-color "#8B4513"
    :height-cm 200
    :spread-cm 60
    :spacing-cm 45}

   {:id "papyrus"
    :common-name "Papyrus"
    :scientific-name "Cyperus papyrus"
    :category :aquatic
    :habit :fountain
    :foliage :grass
    :evergreen? true
    :color "#9ACD32"
    :bloom-color nil
    :height-cm 300
    :spread-cm 120
    :spacing-cm 90}

   ;; =========================================================================
   ;; FERNS
   ;; =========================================================================

   {:id "fern-boston"
    :common-name "Boston Fern"
    :scientific-name "Nephrolepis exaltata"
    :category :fern
    :habit :fountain
    :foliage :fern
    :evergreen? true
    :color "#228B22"
    :bloom-color nil
    :height-cm 60
    :spread-cm 90
    :spacing-cm 60}

   {:id "fern-maidenhair"
    :common-name "Maidenhair Fern"
    :scientific-name "Adiantum"
    :category :fern
    :habit :mounding
    :foliage :fern
    :evergreen? true
    :color "#90EE90"
    :bloom-color nil
    :height-cm 45
    :spread-cm 45
    :spacing-cm 35}

   {:id "fern-japanese"
    :common-name "Japanese Painted Fern"
    :scientific-name "Athyrium niponicum"
    :category :fern
    :habit :mounding
    :foliage :fern
    :evergreen? false
    :color "#8FBC8F"
    :bloom-color nil
    :height-cm 45
    :spread-cm 60
    :spacing-cm 45}])

;; =============================================================================
;; Lookup index (computed once for O(1) lookups)

(def plants-by-id
  "Map from plant ID to plant data for O(1) lookups."
  (into {} (map (juxt :id identity) plant-library)))

;; =============================================================================
;; Lookup functions

(defn get-plant
  "Look up a plant by its ID. O(1) using pre-computed index."
  [id]
  (get plants-by-id id))

(defn plants-by-category
  "Get all plants of a given category."
  [category]
  (filter #(= (:category %) category) plant-library))

(defn plants-by-habit
  "Get all plants with a given growth habit."
  [habit]
  (filter #(= (:habit %) habit) plant-library))

(defn search-plants
  "Search plants by name (common or scientific)."
  [query]
  (let [q (clojure.string/lower-case query)]
    (filter #(or (clojure.string/includes? (clojure.string/lower-case (:common-name %)) q)
                 (clojure.string/includes? (clojure.string/lower-case (:scientific-name %)) q))
            plant-library)))

(defn all-categories
  "Get all unique categories."
  []
  (distinct (map :category plant-library)))

(defn all-habits
  "Get all unique growth habits."
  []
  (distinct (map :habit plant-library)))
