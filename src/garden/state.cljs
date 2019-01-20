(ns garden.state
  (:refer-clojure :exclude [get get-in update set!])
  (:require [reagent.core :as r]))


(defonce app-state
  (r/atom {:canvas {:width 1200 :height 800 :pixels-per-unit 20 :ctx nil :x-offset 0 :y-offset 60 :zoom 0.5}
           :garden {:name "Bla bla bla" :lat 12.21 :lon 43.2}
           :layers [{:id 1, :name "house", :colour "grey", :points [[190 70] [480 70] [480 290] [390 290] [390 240] [190 240] [190 70]]}
                    {:id 2, :name "patio", :colour "grey", :points [[480 160] [560 160] [560 70] [480 70]]}
                    {:id 3, :name "herbs", :colour "DarkGreen", :points [[530 50] [530 -10] [740 -10] [740 -40] [480 -40] [480 50]]}
                    {:id 4, :name "shed", :colour "SaddleBrown" , :points [[740 -40] [740 110] [860 110] [860 -40] [740 -40]]}
                    {:id 5, :name "dads shed", :colour "Sienna" , :points [[860 110] [1070 110] [1070 -40] [860 -40]]}
                    {:id 6, :name "mums lawn", :colour "LimeGreen" , :points [[530 70] [560 70] [560 160] [540 160] [540 260] [810 260] [810 260] [810 110] [740 110] [740 -10] [530 -10] [530 70]]}
                    {:id 7, :name "mums flowers", :colour "Chartreuse" , :points [[810 110] [810 260] [540 260] [540 160] [480 160] [480 290] [860 290] [860 290] [860 110] [810 110]]}
                    {:id 8, :name "fence", :colour "#000000", :points '([50 230] [60 230] [60 -40] [50 -40] [50 -40] [50 -50] [2890 -50] [2880 -40] [2890 -50] [2890 580] [2880 580] [2880 570] [60 550] [50 560] [60 560] [60 280] [50 280] [50 560] [2880 580] [2880 -40] [50 -40] [50 230])}
                    {:id 9, :name "front lawn", :colour "Chartreuse", :points '([140 290] [120 340] [120 480] [370 480] [370 400] [330 360] [330 290] [140 290])}
                    {:id 10, :name "front hedge", :colour "DarkGreen", :points '([310 550] [60 550] [60 0] [90 0] [90 220] [60 220] [60 280] [90 280] [90 520] [310 520] [310 550])}
                    {:id 11, :name "bushes", :colour "LimeGreen" , :points '([140 280] [140 290] [120 330] [120 480] [370 480] [370 400] [400 380] [400 320] [460 320] [460 520] [90 520] [90 280] [140 280])}
                    {:id 12, :name "driveway", :colour "LightSlateGray" , :points '([50 280] [50 230] [60 230] [60 220] [90 220] [90 -10] [180 -10] [180 240] [330 240] [330 290] [160 290] [140 280] [50 280])}
                    {:id 13, :name "hazelnuts", :colour "GoldenRod" , :points '([470 -10] [480 -10] [480 -40] [60 -40] [60 -10] [470 -10])}
                    {:id 14, :name "grass behind house", :colour "LimeGreen", :points '([180 -10] [180 70] [530 70] [530 50] [480 50] [480 -10] [180 -10] [180 -10])}
                    {:id 15, :name "orchard", :colour "Olive" , :points '([460 290] [860 290] [860 150] [1070 150] [1070 40] [1690 40] [1690 560] [460 550] [460 290])}]
           :current nil}))

;; Accessors
(defn get [& path] (cljs.core/get-in @app-state path))
(defn get-in [path] (cljs.core/get-in @app-state path))

(defn current-accessor [& path] (concat [:layers (get :current :index)] path))
(defn current-layer [] (get-in (current-accessor)))
(defn current-line [] (get-in (current-accessor :points)))

;; Setters
(defn set-val [accessor value]
  (swap! app-state assoc-in accessor value))
(defn update [accessor func & params]
  (set-val accessor (func (get-in accessor) values)))


(defn set-context [ctx] (set-val [:canvas :ctx] ctx))


;; Edit mode handlers
(defn get-mode [] (get :current :mode))
(defn set-mode [mode] (set-val [:current :mode] mode))

(defn set-pointer [pointer] (set-val [:canvas :pointer] pointer))

(defn move
  "Return the offset from the previous movement ([0 0] if there was no movement)."
  [point]
  (if-not (get :current :last-point)
    (set-val [:current :last-point] point)
    (let [[prev-x prev-y] (get :current :last-point)
          [current-x current-y] point]
      (set-val [:current :last-point] point)
      [(- current-x prev-x) (- current-y prev-y)])))
(defn end-move [] (set-val [:current :last-point] nil))

(defn- offset-point [[x-offset y-offset] [x y]] [(+ x x-offset) (+ y y-offset)])
(defn move-current-layer
  "Move the current layer by the given offset."
  [offset]
  (->>
   (current-line)
   (map (partial offset-point offset))
   (set-val (current-accessor :points))))

(defn move-current-point
  "Move the last selected point in the current layer to the new position"
  [pos]
  (->> (current-line)
       (replace {(get :current :last-point) pos})
       (set-val (current-accessor :points)))
  (set-val [:current :last-point] pos))


(defn select-layer
  "Select the layer with the given id as active, or deactivate it if previously chosen."
  [layer-id]
  (set-val [:current]
          (when (not= layer-id (get :current :id))
            (->> (:layers @app-state)
                 (map-indexed #(assoc %2 :index %1))
                 (filter #(= (:id %) layer-id))
                 first)))
  (when (current-layer)
    (set-mode :edit)))

(defn set-canvas-size []
  (let [window-width (or (aget  js/window "innerWidth") (-> js/document (aget "body") (aget "clientWidth")))
        window-height (or (aget  js/window "innerHeight") (-> js/document (aget "body") (aget "clientHeight")))]
    (set-val [:canvas :width] (- window-width 380))
    (set-val [:canvas :height] (- window-height 20))))
