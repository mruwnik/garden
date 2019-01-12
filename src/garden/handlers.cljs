(ns garden.handlers
  (:require [garden.state :as state]
            [garden.canvas :refer [render add-line round-pos draw-layer]]))


(defn select-point
  "Handle a point being selected on the canvas."
  [e]
  (state/update
   (state/current-accessor :points)
   (conj (state/current-line)
         (round-pos e (state/get :canvas :pixels-per-meter) (state/get :canvas :x-offset) (state/get :canvas :y-offset))))
  (render @state/app-state))


(defn offset-point [[x y]]
  [(+ x (state/get :canvas :x-offset)) (+ y (state/get :canvas :y-offset))])

(defn line-to-point
  "Draw a line from the last clicked point to the current position of the mouse,"
  [e]
  (when (seq (state/current-line))
    (let [canvas (state/get :canvas)
          ctx (:ctx canvas)]
      (render @state/app-state)
      (add-line ctx (-> (state/current-line) last offset-point) (round-pos e (:pixels-per-meter canvas) 0 0))

      (set! (.-lineWidth ctx) 1)
      (.stroke ctx))))


(defn mouse-out
  "Handle the mouse leaving the canvas."
  [e])


(defn event-field
  "Extract the given `field` from the target of the given `event`."
  [event field] (-> event (aget "target") (aget field)))

(defn select-layer
  "Handle a layer being selected."
  [event]
  (state/select-layer (-> event (event-field "id") js/parseInt))
  (render @state/app-state))


(defn random-colour [] (apply str "#" (repeatedly 6 #(rand-nth "01234567890ABCDEF"))))

(defn add-layer [event]
  (let [id (->> (state/get :layers) (map :id) (apply max) (+ 1))
        layer {:id id :name (str "New patch (" id ")") :colour (random-colour)}
        layers (conj (state/get :layers) layer)]
    (state/update [:layers] layers)
    (state/select-layer id)))


(defn update-value [cast accessor event]
  (state/update accessor (cast (event-field event "value"))))

(defn move [dir]
  (condp = dir
    :left (state/update [:canvas :x-offset] (- (state/get :canvas :x-offset) 10))
    :right (state/update [:canvas :x-offset] (+ (state/get :canvas :x-offset) 10))
    :up (state/update [:canvas :y-offset] (- (state/get :canvas :y-offset) 10))
    :down (state/update [:canvas :y-offset] (+ (state/get :canvas :y-offset) 10)))
  (render @state/app-state))
