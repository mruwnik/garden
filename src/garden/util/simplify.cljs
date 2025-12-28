(ns garden.util.simplify
  "Point simplification utilities for polygon and path processing.

   Provides algorithms to reduce point count while maintaining shape fidelity.")

;; =============================================================================
;; Distance-Based Simplification

(defn simplify-by-distance
  "Reduce number of points by removing those too close together.
   min-distance is the minimum distance between consecutive points
   (typically in garden cm for this application)."
  [points min-distance]
  (if (< (count points) 3)
    points
    (reduce (fn [acc pt]
              (let [last-pt (peek acc)]
                (if (or (nil? last-pt)
                        (let [[x1 y1] last-pt
                              [x2 y2] pt
                              dist (js/Math.sqrt (+ (* (- x2 x1) (- x2 x1))
                                                    (* (- y2 y1) (- y2 y1))))]
                          (> dist min-distance)))
                  (conj acc pt)
                  acc)))
            []
            points)))
