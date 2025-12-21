(ns garden.canvas.reference
  "Render a reference image overlay for tracing.")

;; Fixed bar size in image pixels (roughly 2cm when viewing full image)
(def ^:private bar-image-pixels 150)

(defn render!
  "Render the reference image overlay if visible and loaded.
   Uses multiply blend mode so white becomes transparent."
  [ctx state]
  (let [ref-img (get-in state [:ui :reference-image])]
    (when (and (:visible? ref-img)
               (:image ref-img))
      (let [{:keys [image position opacity bar-meters]} ref-img
            [x y] position
            bar-m (or bar-meters 50)
            ;; Scale: bar-image-pixels of the image = bar-meters in real world
            ;; So each image pixel = (bar-meters * 100 cm) / bar-image-pixels
            effective-scale (/ (* bar-m 100) bar-image-pixels)
            w (* (.-width image) effective-scale)
            h (* (.-height image) effective-scale)]
        ;; Draw the image
        (.save ctx)
        (set! (.-globalAlpha ctx) (or opacity 0.5))
        (.drawImage ctx image x y w h)
        (.restore ctx)))))

(def ^:private nice-distances
  "Nice round distances for scale bar (in meters)"
  [1 2 5 10 20 50 100 200 500 1000])

(defn- pick-nice-distance
  "Pick a nice round distance that results in a bar close to target-pixels wide."
  [zoom target-pixels]
  ;; At zoom=1, 1 pixel = 1 cm, so target-pixels cm = target-pixels/100 m
  ;; At any zoom, target-pixels screen pixels = (target-pixels / zoom) cm = (target-pixels / zoom / 100) m
  (let [raw-meters (/ target-pixels zoom 100)
        ;; Find the nice distance closest to raw-meters
        best (reduce (fn [best candidate]
                       (if (< (Math/abs (- candidate raw-meters))
                              (Math/abs (- best raw-meters)))
                         candidate
                         best))
                     (first nice-distances)
                     nice-distances)]
    best))

(defn render-scale-bar!
  "Render the scale bar in fixed screen position (lower-right corner).
   Called after viewport transform is restored."
  [ctx state]
  (let [ref-img (get-in state [:ui :reference-image])]
    (when (and (:visible? ref-img)
               (:image ref-img))
      (let [{:keys [width height]} (get-in state [:viewport :size])
            zoom (get-in state [:viewport :zoom])
            ;; Pick a nice distance that gives ~120px bar
            target-pixels 120
            distance-m (pick-nice-distance zoom target-pixels)
            ;; Calculate actual bar length: distance in cm * zoom
            bar-length (* distance-m 100 zoom)
            bar-height 4
            font-size 12
            padding 8
            margin 20
            ;; Position in lower-right corner
            bar-x (- width margin bar-length)
            bar-y (- height margin bar-height)]
        (.save ctx)
        ;; Bar background
        (set! (.-fillStyle ctx) "rgba(255,255,255,0.9)")
        (.fillRect ctx (- bar-x padding) (- bar-y font-size padding)
                   (+ bar-length (* 2 padding)) (+ bar-height font-size (* 2 padding)))
        ;; Bar itself
        (set! (.-fillStyle ctx) "#333")
        (.fillRect ctx bar-x bar-y bar-length bar-height)
        ;; End caps
        (.fillRect ctx bar-x (- bar-y 4) 2 (+ bar-height 8))
        (.fillRect ctx (+ bar-x bar-length -2) (- bar-y 4) 2 (+ bar-height 8))
        ;; Label
        (set! (.-font ctx) (str font-size "px sans-serif"))
        (set! (.-fillStyle ctx) "#333")
        (set! (.-textAlign ctx) "center")
        (set! (.-textBaseline ctx) "bottom")
        (.fillText ctx (str distance-m "m") (+ bar-x (/ bar-length 2)) (- bar-y 4))
        (.restore ctx)))))
