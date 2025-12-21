(ns garden.state
  (:require [reagent.core :as r]))

(def initial-state
  {:garden {:name ""
            :location {:lat nil :lon nil}}

   ;; Domain data
   :areas []    ; [{:id :type :name :points :color :properties}]
   :plants []   ; [{:id :species-id :position :planted-date :source}]

   ;; Plant library
   :library {:plants {}
             :filter {:search "" :type nil}}

   ;; Viewport
   :viewport {:offset [0 0]
              :zoom 1.0
              :size {:width 800 :height 600}
              :ctx nil}

   ;; Tool state
   :tool {:active :select
          :state nil
          :cursor :default}

   ;; Selection
   :selection {:type nil
               :ids #{}}

   ;; History (undo/redo)
   :history {:past []
             :future []}

   ;; UI state
   :ui {:panels {:left {:open? true :width 260}
                 :right {:open? true :width 260}
                 :bottom {:open? false :height 120}}
        :grid {:visible? true :spacing 50 :snap? false}
        :spacing-circles {:visible? false}
        :background {:visible? true}
        :mode :plan
        :hover {:position nil :plant-id nil}
        :mouse {:canvas-pos nil}}

   ;; Chat state
   :chat {:open? false
          :messages []  ; [{:role :user/:assistant :content "..."}]
          :input ""
          :loading? false}})

(defonce app-state (r/atom initial-state))

;; State accessors

(defn get-state
  "Get the current app state or a path within it."
  ([] @app-state)
  ([& path] (get-in @app-state (vec path))))

;; Viewport helpers

(defn viewport [] (:viewport @app-state))
(defn zoom [] (get-in @app-state [:viewport :zoom]))
(defn offset [] (get-in @app-state [:viewport :offset]))
(defn canvas-ctx [] (get-in @app-state [:viewport :ctx]))

;; Tool helpers

(defn active-tool [] (get-in @app-state [:tool :active]))
(defn tool-state [] (get-in @app-state [:tool :state]))

;; Selection helpers

(defn selection [] (:selection @app-state))
(defn selected-ids [] (get-in @app-state [:selection :ids]))
(defn selected? [id] (contains? (selected-ids) id))

;; Domain data helpers

(defn areas [] (:areas @app-state))
(defn plants [] (:plants @app-state))

(defn find-area [id]
  (first (filter #(= (:id %) id) (areas))))

(defn find-plant [id]
  (first (filter #(= (:id %) id) (plants))))

(defn find-plant-at
  "Find a plant at the given canvas position."
  [pos radius-fn]
  (let [[px py] pos]
    (first (filter (fn [plant]
                     (let [[x y] (:position plant)
                           radius (radius-fn plant)
                           dx (- px x)
                           dy (- py y)]
                       (<= (+ (* dx dx) (* dy dy)) (* radius radius))))
                   (plants)))))

;; State mutations

(defn set-state! [path value]
  (swap! app-state assoc-in path value))

(defn update-state! [path f & args]
  (swap! app-state update-in path #(apply f % args)))

(defn set-viewport-ctx! [ctx]
  (set-state! [:viewport :ctx] ctx))

(defn set-viewport-size! [width height]
  (set-state! [:viewport :size] {:width width :height height}))

(defn set-tool! [tool-id]
  (swap! app-state #(-> %
                        (assoc-in [:tool :active] tool-id)
                        (assoc-in [:tool :state] nil))))

(defn set-tool-state! [state]
  (set-state! [:tool :state] state))

(defn update-tool-state! [f & args]
  (swap! app-state update-in [:tool :state] #(apply f % args)))

(defn set-cursor! [cursor]
  (set-state! [:tool :cursor] cursor))

;; Selection mutations

(defn select! [type ids]
  (swap! app-state assoc :selection {:type type :ids (set ids)}))

(defn clear-selection! []
  (swap! app-state assoc :selection {:type nil :ids #{}}))

(defn toggle-selection! [type id]
  (let [current-ids (selected-ids)]
    (if (contains? current-ids id)
      (let [new-ids (disj current-ids id)]
        (if (empty? new-ids)
          (clear-selection!)
          (select! type new-ids)))
      (select! type (conj current-ids id)))))

;; Area mutations

(defn gen-id []
  (str (random-uuid)))

(defn add-area! [area]
  (let [area-with-id (if (:id area) area (assoc area :id (gen-id)))]
    (update-state! [:areas] conj area-with-id)
    (:id area-with-id)))

(defn update-area! [id updates]
  (swap! app-state update :areas
         (fn [areas]
           (mapv #(if (= (:id %) id) (merge % updates) %) areas))))

(defn remove-area! [id]
  (swap! app-state update :areas
         (fn [areas] (filterv #(not= (:id %) id) areas))))

;; Plant mutations

(defn add-plant! [plant]
  (let [plant-with-id (if (:id plant) plant (assoc plant :id (gen-id)))]
    (update-state! [:plants] conj plant-with-id)
    (:id plant-with-id)))

(defn update-plant! [id updates]
  (swap! app-state update :plants
         (fn [plants]
           (mapv #(if (= (:id %) id) (merge % updates) %) plants))))

(defn remove-plant! [id]
  (swap! app-state update :plants
         (fn [plants] (filterv #(not= (:id %) id) plants))))

;; Viewport mutations

(defn pan! [dx dy]
  (update-state! [:viewport :offset]
                 (fn [[x y]] [(+ x dx) (+ y dy)])))

(defn set-zoom! [new-zoom]
  (set-state! [:viewport :zoom] (max 0.1 (min 10.0 new-zoom))))

(defn zoom-at!
  "Zoom centered on screen point."
  [[sx sy] factor]
  (let [{:keys [offset zoom]} (viewport)
        [ox oy] offset
        ;; Convert screen point to canvas coordinates before zoom
        cx (/ (- sx ox) zoom)
        cy (/ (- sy oy) zoom)
        ;; Calculate new zoom
        new-zoom (max 0.1 (min 10.0 (* zoom factor)))
        ;; Adjust offset to keep canvas point stationary
        new-ox (- sx (* cx new-zoom))
        new-oy (- sy (* cy new-zoom))]
    (swap! app-state #(-> %
                          (assoc-in [:viewport :zoom] new-zoom)
                          (assoc-in [:viewport :offset] [new-ox new-oy])))))
