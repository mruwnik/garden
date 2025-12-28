(ns garden.constants
  "Shared constants used throughout the application.

   Centralizes magic numbers to avoid duplication and make them
   easier to find and modify.")

;; =============================================================================
;; Scale Bar Constants

(def bar-image-pixels
  "The reference bar width in image pixels.
   Used for scale calculations: bar-image-pixels of image = bar-meters in real world."
  150)

(def default-bar-meters
  "Default scale bar length in meters when no reference image is loaded."
  50)

;; =============================================================================
;; UI Constants

(def vertex-hit-radius
  "Click radius for hitting vertices in the select tool (in screen pixels)."
  12)

(def point-hit-radius
  "Click radius for hitting points like elevation markers (in screen pixels)."
  15)

;; =============================================================================
;; Drawing Constants

(def trace-simplify-tolerance-cm
  "Simplification tolerance for freehand tracing (in cm).
   Points closer than this are merged to reduce polygon complexity."
  50)

;; =============================================================================
;; Performance Constants

(def topo-cache-debounce-ms
  "Debounce time for topo cache updates in milliseconds."
  3000)

(def max-cache-dimension
  "Maximum dimension for cached canvases (width or height in pixels)."
  2048)

;; =============================================================================
;; Drag and Drop Constants

(def species-drag-mime-type
  "MIME type for dragging plant species from library to canvas."
  "application/x-garden-species")

;; =============================================================================
;; Grid Constants

(def max-grid-lines
  "Maximum grid lines per axis before skipping render to prevent slowdown."
  500)

;; =============================================================================
;; Math Constants

(def TWO-PI
  "2π constant to avoid recalculating (* 2 Math/PI) in hot paths."
  (* 2 Math/PI))
