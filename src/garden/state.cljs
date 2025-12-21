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
        :grid {:visible? true :spacing 50 :snap? false :labels? true}
        :spacing-circles {:visible? false}
        :background {:visible? true}
        :reference-image {:visible? false
                          :url nil         ; data URL or file URL
                          :image nil       ; loaded Image object
                          :position [0 0]  ; top-left position in garden coords
                          :scale 1.0       ; scale factor (cm per image pixel)
                          :opacity 0.5
                          :bar-meters 50}  ; what the scale bar represents
        :reference-modal-open? false
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

;; History/Undo helpers

(def max-history-size 50)

(defn- save-history!
  "Save current areas/plants state to history before a mutation."
  []
  (let [current {:areas (:areas @app-state)
                 :plants (:plants @app-state)}
        past (get-in @app-state [:history :past])]
    (swap! app-state
           #(-> %
                (assoc-in [:history :past]
                          (vec (take-last max-history-size (conj past current))))
                (assoc-in [:history :future] [])))))

(defn can-undo? []
  (seq (get-in @app-state [:history :past])))

(defn can-redo? []
  (seq (get-in @app-state [:history :future])))

(defn undo!
  "Restore previous state from history."
  []
  (when (can-undo?)
    (let [current {:areas (:areas @app-state)
                   :plants (:plants @app-state)}
          past (get-in @app-state [:history :past])
          previous (peek past)]
      (swap! app-state
             #(-> %
                  (assoc :areas (:areas previous))
                  (assoc :plants (:plants previous))
                  (assoc-in [:history :past] (pop past))
                  (update-in [:history :future] conj current))))))

(defn redo!
  "Restore next state from future history."
  []
  (when (can-redo?)
    (let [current {:areas (:areas @app-state)
                   :plants (:plants @app-state)}
          future-states (get-in @app-state [:history :future])
          next-state (peek future-states)]
      (swap! app-state
             #(-> %
                  (assoc :areas (:areas next-state))
                  (assoc :plants (:plants next-state))
                  (assoc-in [:history :future] (pop future-states))
                  (update-in [:history :past] conj current))))))

;; Area mutations

(defn gen-id []
  (str (random-uuid)))

(defn add-area! [area]
  (save-history!)
  (let [area-with-id (if (:id area) area (assoc area :id (gen-id)))]
    (update-state! [:areas] conj area-with-id)
    (:id area-with-id)))

(defn update-area! [id updates]
  (save-history!)
  (swap! app-state update :areas
         (fn [areas]
           (mapv #(if (= (:id %) id) (merge % updates) %) areas))))

(defn remove-area! [id]
  (save-history!)
  (swap! app-state update :areas
         (fn [areas] (filterv #(not= (:id %) id) areas))))

;; Plant mutations

(defn add-plant! [plant]
  (save-history!)
  (let [plant-with-id (if (:id plant) plant (assoc plant :id (gen-id)))]
    (update-state! [:plants] conj plant-with-id)
    (:id plant-with-id)))

(defn update-plant! [id updates]
  (save-history!)
  (swap! app-state update :plants
         (fn [plants]
           (mapv #(if (= (:id %) id) (merge % updates) %) plants))))

(defn remove-plant! [id]
  (save-history!)
  (swap! app-state update :plants
         (fn [plants] (filterv #(not= (:id %) id) plants))))

;; Viewport mutations

(defn pan! [dx dy]
  (update-state! [:viewport :offset]
                 (fn [[x y]] [(+ x dx) (+ y dy)])))

(defn set-zoom! [new-zoom]
  (set-state! [:viewport :zoom] (max 0.01 (min 10.0 new-zoom))))

(defn zoom-at!
  "Zoom centered on screen point."
  [[sx sy] factor]
  (let [{:keys [offset zoom]} (viewport)
        [ox oy] offset
        ;; Convert screen point to canvas coordinates before zoom
        cx (/ (- sx ox) zoom)
        cy (/ (- sy oy) zoom)
        ;; Calculate new zoom (min 0.01 = 1%, max 10 = 1000%)
        new-zoom (max 0.01 (min 10.0 (* zoom factor)))
        ;; Adjust offset to keep canvas point stationary
        new-ox (- sx (* cx new-zoom))
        new-oy (- sy (* cy new-zoom))]
    (swap! app-state #(-> %
                          (assoc-in [:viewport :zoom] new-zoom)
                          (assoc-in [:viewport :offset] [new-ox new-oy])))))

;; Reference image helpers

(defn load-reference-image!
  "Load a reference image from a File object."
  [file]
  (let [reader (js/FileReader.)]
    (set! (.-onload reader)
          (fn [e]
            (let [data-url (.. e -target -result)
                  img (js/Image.)]
              (set! (.-onload img)
                    (fn []
                      ;; Calculate scale to fit image within garden (max 40m = 4000cm)
                      (let [max-size 4000
                            img-w (.-width img)
                            img-h (.-height img)
                            scale (min (/ max-size img-w) (/ max-size img-h) 10)
                            ;; Center image on current viewport
                            {:keys [offset zoom size]} (viewport)
                            [ox oy] offset
                            vw (:width size)
                            vh (:height size)
                            ;; Calculate center of visible area in garden coords
                            center-x (/ (- (/ vw 2) ox) zoom)
                            center-y (/ (- (/ vh 2) oy) zoom)
                            ;; Position image so its center is at viewport center
                            img-canvas-w (* img-w scale)
                            img-canvas-h (* img-h scale)
                            pos-x (- center-x (/ img-canvas-w 2))
                            pos-y (- center-y (/ img-canvas-h 2))]
                        (set-state! [:ui :reference-image :url] data-url)
                        (set-state! [:ui :reference-image :image] img)
                        (set-state! [:ui :reference-image :scale] scale)
                        (set-state! [:ui :reference-image :position] [pos-x pos-y])
                        (set-state! [:ui :reference-image :visible?] true))))
              (set! (.-src img) data-url))))
    (.readAsDataURL reader file)))

(defn clear-reference-image! []
  (swap! app-state update-in [:ui :reference-image]
         assoc :url nil :image nil :visible? false))

(defn set-reference-opacity! [opacity]
  (set-state! [:ui :reference-image :opacity] (max 0.1 (min 1.0 opacity))))

(defn set-reference-scale! [scale]
  (set-state! [:ui :reference-image :scale] (max 0.1 (min 10.0 scale))))

(defn toggle-reference-visible! []
  (update-state! [:ui :reference-image :visible?] not))
