(ns garden.llm
  (:require [garden.state :as state]
            [garden.ui.panels.library :as library]
            [clojure.string :as str]))

;; Configuration - stored in localStorage
(defonce config
  (atom {:api-key (or (.getItem js/localStorage "garden-api-key") "")
         :api-url "https://api.anthropic.com/v1/messages"
         :model "claude-haiku-4-5"}))

;; Maximum number of messages to keep in chat history
;; This prevents context overflow and tool_use/tool_result mismatches
(def max-chat-messages 20)

(defn- trim-chat-messages
  "Trim chat messages to max-chat-messages, keeping most recent.
   Ensures we don't break tool_use/tool_result pairs by trimming
   from the start and always keeping complete conversation turns."
  [messages]
  (if (<= (count messages) max-chat-messages)
    messages
    ;; Find a safe trim point - after a complete assistant turn (no pending tool results)
    (let [excess (- (count messages) max-chat-messages)
          ;; Find indices where it's safe to trim (after assistant messages without tool calls,
          ;; or after tool-result messages)
          safe-points (keep-indexed
                       (fn [idx msg]
                         (when (or (and (= (:role msg) :assistant)
                                        (empty? (:tool-calls msg)))
                                   (= (:role msg) :tool-result))
                           idx))
                       messages)
          ;; Find the first safe point that removes at least 'excess' messages
          trim-point (or (first (filter #(>= % excess) safe-points))
                         excess)]
      (vec (drop (inc trim-point) messages)))))

(defn get-api-key [] (:api-key @config))
(defn set-api-key! [key]
  (.setItem js/localStorage "garden-api-key" key)
  (swap! config assoc :api-key key))

;; Abort controller for cancelling in-flight requests
(defonce current-request (atom nil))

(defn cancel-request!
  "Cancel the current in-flight request if any."
  []
  (when-let [controller @current-request]
    (.abort controller)
    (reset! current-request nil)
    (state/set-state! [:chat :loading?] false)
    true))

(defn has-api-key? []
  (seq (:api-key @config)))

;; ============================================
;; Tool Definitions
;; ============================================

(def garden-tools
  [{:name "add_area"
    :description "Add a new area to the garden (like a bed, path, or structure). Areas are polygons defined by a list of points."
    :input_schema
    {:type "object"
     :properties
     {:name {:type "string" :description "Name for the area (e.g., 'Vegetable Bed', 'Main Path')"}
      :area_type {:type "string" :enum ["bed" "path" "structure"] :description "Type of area"}
      :points {:type "array"
               :items {:type "object"
                       :properties {:x {:type "number"} :y {:type "number"}}
                       :required ["x" "y"]}
               :description "Array of {x, y} points defining the polygon vertices. Use coordinates like 100-800 for x and y."}
      :color {:type "string" :description "Optional hex color (e.g., '#8B4513' for brown)"}}
     :required ["name" "area_type" "points"]}}

   {:name "add_plant"
    :description "Add a plant to the garden at a specific position."
    :input_schema
    {:type "object"
     :properties
     {:species_id {:type "string" :description "Plant species ID (e.g., 'tomato', 'basil', 'apple-tree', 'rose')"}
      :x {:type "number" :description "X coordinate position (100-800 typical range)"}
      :y {:type "number" :description "Y coordinate position (100-600 typical range)"}
      :stage {:type "string" :enum ["seed" "seedling" "mature"] :description "Life stage of the plant"}}
     :required ["species_id" "x" "y"]}}

   {:name "add_plants_row"
    :description "Add multiple plants in a row or grid pattern."
    :input_schema
    {:type "object"
     :properties
     {:species_id {:type "string" :description "Plant species ID"}
      :start_x {:type "number" :description "Starting X coordinate"}
      :start_y {:type "number" :description "Starting Y coordinate"}
      :count {:type "integer" :description "Number of plants"}
      :spacing {:type "number" :description "Space between plants in pixels"}
      :direction {:type "string" :enum ["horizontal" "vertical"] :description "Row direction"}
      :stage {:type "string" :enum ["seed" "seedling" "mature"]}}
     :required ["species_id" "start_x" "start_y" "count" "spacing"]}}

   {:name "remove_area"
    :description "Remove an area from the garden by name."
    :input_schema
    {:type "object"
     :properties
     {:name {:type "string" :description "Name of the area to remove"}}
     :required ["name"]}}

   {:name "clear_garden"
    :description "Remove all areas and plants from the garden. Use with caution!"
    :input_schema
    {:type "object"
     :properties {}}}

   {:name "get_garden_state"
    :description "Get the current state of the garden including all areas and plants."
    :input_schema
    {:type "object"
     :properties {}}}

   {:name "list_species"
    :description "List all available plant species that can be added to the garden. Use this to discover valid species IDs before adding plants."
    :input_schema
    {:type "object"
     :properties
     {:type {:type "string"
             :enum ["vegetable" "herb" "flower" "tree"]
             :description "Optional filter by plant type"}}}}

   {:name "remove_plant"
    :description "Remove a specific plant from the garden by its ID. Use get_garden_state first to find plant IDs."
    :input_schema
    {:type "object"
     :properties
     {:plant_id {:type "string" :description "The unique ID of the plant to remove"}}
     :required ["plant_id"]}}

   {:name "remove_plants_in_area"
    :description "Remove all plants within a rectangular area defined by coordinates."
    :input_schema
    {:type "object"
     :properties
     {:min_x {:type "number" :description "Minimum X coordinate of the area"}
      :min_y {:type "number" :description "Minimum Y coordinate of the area"}
      :max_x {:type "number" :description "Maximum X coordinate of the area"}
      :max_y {:type "number" :description "Maximum Y coordinate of the area"}}
     :required ["min_x" "min_y" "max_x" "max_y"]}}

   {:name "scatter_plants"
    :description "Randomly scatter multiple plants of a species across a rectangular area. Great for creating dense, natural-looking vegetation."
    :input_schema
    {:type "object"
     :properties
     {:species_id {:type "string" :description "Plant species ID"}
      :count {:type "integer" :description "Number of plants to scatter (can be large, e.g. 20-50)"}
      :min_x {:type "number" :description "Minimum X coordinate of scatter area"}
      :min_y {:type "number" :description "Minimum Y coordinate of scatter area"}
      :max_x {:type "number" :description "Maximum X coordinate of scatter area"}
      :max_y {:type "number" :description "Maximum Y coordinate of scatter area"}
      :stage {:type "string" :enum ["seed" "seedling" "mature"]}}
     :required ["species_id" "count" "min_x" "min_y" "max_x" "max_y"]}}

   {:name "add_path"
    :description "Add a winding path through the garden defined by a series of points."
    :input_schema
    {:type "object"
     :properties
     {:name {:type "string" :description "Name for the path"}
      :points {:type "array"
               :items {:type "object"
                       :properties {:x {:type "number"} :y {:type "number"}}
                       :required ["x" "y"]}
               :description "Array of {x, y} points the path passes through"}
      :width {:type "number" :description "Width of the path in pixels (default 20)"}}
     :required ["name" "points"]}}

   {:name "add_water"
    :description "Add a water feature (pond, stream, lake) as a polygon area."
    :input_schema
    {:type "object"
     :properties
     {:name {:type "string" :description "Name for the water feature (e.g., 'Kasumiga-ike Pond')"}
      :points {:type "array"
               :items {:type "object"
                       :properties {:x {:type "number"} :y {:type "number"}}
                       :required ["x" "y"]}
               :description "Array of {x, y} points defining the water boundary"}}
     :required ["name" "points"]}}])

;; ============================================
;; Tool Execution
;; ============================================

(defn- execute-add-area [{:keys [name area_type points color]}]
  (let [parsed-points (mapv (fn [p] [(:x p) (:y p)]) points)
        area-id (state/add-area! {:name name
                                  :type (keyword area_type)
                                  :points parsed-points
                                  :color color})]
    {:success true
     :message (str "Created " area_type " '" name "' with " (count points) " vertices")
     :area_id area-id}))

(defn- execute-add-plant [{:keys [species_id x y stage]}]
  (let [plant-id (state/add-plant! {:species-id species_id
                                    :position [x y]
                                    :stage (keyword (or stage "mature"))
                                    :planted-date (js/Date.)})]
    {:success true
     :message (str "Planted " species_id " at (" x ", " y ")")
     :plant_id plant-id}))

(defn- execute-add-plants-row [{:keys [species_id start_x start_y count spacing direction stage]}]
  (let [dir (or direction "horizontal")
        plant-ids
        (doall
         (for [i (range count)]
           (let [x (if (= dir "horizontal") (+ start_x (* i spacing)) start_x)
                 y (if (= dir "vertical") (+ start_y (* i spacing)) start_y)]
             (state/add-plant! {:species-id species_id
                                :position [x y]
                                :stage (keyword (or stage "mature"))
                                :planted-date (js/Date.)}))))]
    {:success true
     :message (str "Planted " count " " species_id " plants in a " dir " row")
     :plant_ids (vec plant-ids)}))

(defn- execute-remove-area [{:keys [name]}]
  (let [areas (state/areas)
        matching (filter #(= (:name %) name) areas)]
    (if (seq matching)
      (do
        (doseq [area matching]
          (state/remove-area! (:id area)))
        {:success true :message (str "Removed area '" name "'")})
      {:success false :message (str "No area found with name '" name "'")})))

(defn- execute-clear-garden [_]
  (doseq [area (state/areas)]
    (state/remove-area! (:id area)))
  (doseq [plant (state/plants)]
    (state/remove-plant! (:id plant)))
  {:success true :message "Cleared all areas and plants from the garden"})

(defn- execute-remove-plant [{:keys [plant_id]}]
  (let [plant (state/find-plant plant_id)]
    (if plant
      (do
        (state/remove-plant! plant_id)
        {:success true :message (str "Removed plant with ID '" plant_id "'")})
      {:success false :message (str "No plant found with ID '" plant_id "'")})))

(defn- execute-remove-plants-in-area [{:keys [min_x min_y max_x max_y]}]
  (let [plants (state/plants)
        in-area (filter (fn [p]
                          (let [[x y] (:position p)]
                            (and (>= x min_x) (<= x max_x)
                                 (>= y min_y) (<= y max_y))))
                        plants)
        removed-count (count in-area)]
    (doseq [plant in-area]
      (state/remove-plant! (:id plant)))
    {:success true
     :message (str "Removed " removed-count " plant(s) from area ("
                   min_x "," min_y ") to (" max_x "," max_y ")")}))

(defn- execute-scatter-plants [{:keys [species_id count min_x min_y max_x max_y stage]}]
  (let [;; Get plant spacing from library
        plant-data (first (filter #(= (:id %) species_id) library/sample-plants))
        spacing (or (:spacing-cm plant-data) 50)
        ;; Calculate how many can actually fit with proper spacing
        width (- max_x min_x)
        height (- max_y min_y)
        cols (max 1 (int (/ width spacing)))
        rows (max 1 (int (/ height spacing)))
        max-plants (* cols rows)
        actual-count (min count max-plants)
        ;; Generate positions with spacing + jitter
        positions (take actual-count
                        (shuffle
                         (for [col (range cols)
                               row (range rows)]
                           (let [base-x (+ min_x (* col spacing) (/ spacing 2))
                                 base-y (+ min_y (* row spacing) (/ spacing 2))
                                 ;; Add random jitter (up to 30% of spacing)
                                 jitter (* spacing 0.3)
                                 x (+ base-x (- (rand jitter) (/ jitter 2)))
                                 y (+ base-y (- (rand jitter) (/ jitter 2)))]
                             [x y]))))
        plant-ids
        (doall
         (for [[x y] positions]
           (state/add-plant! {:species-id species_id
                              :position [x y]
                              :stage (keyword (or stage "mature"))
                              :planted-date (js/Date.)})))]
    {:success true
     :message (str "Scattered " (clojure.core/count plant-ids) " " species_id
                   " plants (spacing: " spacing "cm, requested: " count ")")
     :plant_ids (vec plant-ids)}))

(defn- execute-add-path [{:keys [name points width]}]
  (let [path-width (or width 20)
        ;; Convert points to polygon by creating offset lines on both sides
        ;; Simple approach: expand each point perpendicular to path direction
        pts (mapv (fn [p] [(:x p) (:y p)]) points)
        n (clojure.core/count pts)
        ;; Create polygon by going forward on one side, then back on other
        left-side
        (for [i (range n)]
          (let [[x y] (nth pts i)
                ;; Get direction vector
                [dx dy] (if (< i (dec n))
                          (let [[nx ny] (nth pts (inc i))]
                            [(- nx x) (- ny y)])
                          (let [[px py] (nth pts (dec i))]
                            [(- x px) (- y py)]))
                len (js/Math.sqrt (+ (* dx dx) (* dy dy)))
                ;; Perpendicular normal (left side)
                nx (if (pos? len) (/ (- dy) len) 0)
                ny (if (pos? len) (/ dx len) 0)
                offset (/ path-width 2)]
            [(+ x (* nx offset)) (+ y (* ny offset))]))
        right-side
        (for [i (range (dec n) -1 -1)]
          (let [[x y] (nth pts i)
                [dx dy] (if (< i (dec n))
                          (let [[nx ny] (nth pts (inc i))]
                            [(- nx x) (- ny y)])
                          (let [[px py] (nth pts (dec i))]
                            [(- x px) (- y py)]))
                len (js/Math.sqrt (+ (* dx dx) (* dy dy)))
                ;; Perpendicular normal (right side)
                nx (if (pos? len) (/ dy len) 0)
                ny (if (pos? len) (/ (- dx) len) 0)
                offset (/ path-width 2)]
            [(+ x (* nx offset)) (+ y (* ny offset))]))
        polygon-points (vec (concat left-side right-side))
        area-id (state/add-area! {:name name
                                  :type :path
                                  :points polygon-points
                                  :color "#d4a574"})]
    {:success true
     :message (str "Created path '" name "' with " n " waypoints")
     :area_id area-id}))

(defn- execute-add-water [{:keys [name points]}]
  (let [parsed-points (mapv (fn [p] [(:x p) (:y p)]) points)
        area-id (state/add-area! {:name name
                                  :type :water
                                  :points parsed-points
                                  :color "#4a90d9"})]
    {:success true
     :message (str "Created water feature '" name "' with " (clojure.core/count points) " vertices")
     :area_id area-id}))

(defn- execute-get-garden-state [_]
  {:areas (mapv (fn [a] {:name (:name a)
                         :type (:type a)
                         :points (:points a)
                         :notes (:notes a)})
                (state/areas))
   :plants (mapv (fn [p] {:species_id (:species-id p)
                          :position (:position p)
                          :stage (:stage p)})
                 (state/plants))})

(defn- execute-list-species [{:keys [type]}]
  (let [type-kw (when type (keyword type))
        plants (cond->> library/sample-plants
                 type-kw (filter #(= (:type %) type-kw)))]
    {:species (mapv (fn [p] {:id (:id p)
                             :common_name (:common-name p)
                             :type (name (:type p))
                             :spacing_cm (:spacing-cm p)})
                    plants)}))

(defn execute-tool
  "Execute a tool call and return the result."
  [tool-name tool-input]
  (js/console.log "Executing tool:" tool-name tool-input)
  (try
    (case tool-name
      "add_area" (execute-add-area tool-input)
      "add_plant" (execute-add-plant tool-input)
      "add_plants_row" (execute-add-plants-row tool-input)
      "remove_area" (execute-remove-area tool-input)
      "remove_plant" (execute-remove-plant tool-input)
      "remove_plants_in_area" (execute-remove-plants-in-area tool-input)
      "clear_garden" (execute-clear-garden tool-input)
      "get_garden_state" (execute-get-garden-state tool-input)
      "list_species" (execute-list-species tool-input)
      "scatter_plants" (execute-scatter-plants tool-input)
      "add_path" (execute-add-path tool-input)
      "add_water" (execute-add-water tool-input)
      {:success false :message (str "Unknown tool: " tool-name)})
    (catch :default e
      {:success false :message (str "Tool error: " (.-message e))})))

;; ============================================
;; Garden Context for System Prompt
;; ============================================

(defn- garden-state->context []
  (let [areas (state/areas)
        plants (state/plants)]
    (str
     "Current garden state:\n"
     (if (empty? areas)
       "- No areas defined yet\n"
       (str "- Areas (" (count areas) "):\n"
            (str/join "\n"
                      (map (fn [a]
                             (str "  * " (or (:name a) "Unnamed")
                                  " (" (name (or (:type a) :bed)) ")"
                                  " - " (count (:points a)) " vertices"
                                  (when (:notes a) (str " - " (:notes a)))))
                           areas))))
     "\n"
     (if (empty? plants)
       "- No plants placed yet\n"
       (str "- Plants (" (count plants) "):\n"
            (str/join "\n"
                      (map (fn [p]
                             (str "  * " (:species-id p)
                                  " at " (mapv int (:position p))
                                  " (" (name (or (:stage p) :mature)) ")"))
                           plants)))))))

(defn- build-system-prompt []
  (str
   "You are a helpful garden planning assistant with the ability to directly modify the garden.\n\n"
   "You have tools to:\n"
   "- Add areas (beds, paths, structures) as polygons\n"
   "- Add plants at specific positions or in rows\n"
   "- scatter_plants: Randomly scatter many plants in an area for dense, natural-looking vegetation\n"
   "- add_path: Create winding paths defined by waypoints\n"
   "- add_water: Create water features (ponds, streams, lakes)\n"
   "- Remove areas, plants, and clear the garden\n"
   "- List available plant species (use list_species to discover valid species IDs)\n\n"
   "IMPORTANT: Before adding plants, use list_species to see what plants are available and get their correct IDs.\n\n"
   "For recreating famous gardens like Kenroku-en:\n"
   "- Use scatter_plants with high counts (30-100) to create dense vegetation\n"
   "- Layer different plant types for depth\n"
   "- Add water features for ponds/streams\n"
   "- Use winding paths that follow natural curves\n\n"
   "When the user asks you to design or modify the garden, USE THE TOOLS to make changes.\n"
   "Don't just describe what to do - actually do it!\n\n"
   "COORDINATE SYSTEM: Garden coordinates are in CENTIMETERS.\n"
   "- Default garden size: 8000 x 6000 cm (80m x 60m)\n"
   "- Plants have spacing-cm which is their mature footprint diameter\n"
   "- Example: cherry-blossom has spacing 500cm (5m), so place them ~500cm apart\n"
   "- When using scatter_plants, the tool will respect plant spacing automatically\n"
   "For areas, provide polygon points in clockwise order.\n\n"
   (garden-state->context)
   "\n\n"
   "After making changes, briefly confirm what you did."))

;; ============================================
;; Message Formatting
;; ============================================

(defn- format-text-content [text]
  {:type "text" :text text})

(defn- format-image-content [{:keys [data media-type]}]
  {:type "image"
   :source {:type "base64"
            :media_type media-type
            :data data}})

(defn- format-user-message [{:keys [content images]}]
  (let [content-parts (cond-> []
                        (seq content) (conj (format-text-content content))
                        (seq images) (into (map format-image-content images)))]
    {:role "user"
     :content (if (= 1 (count content-parts))
                content
                content-parts)}))

(defn- format-assistant-message [{:keys [content tool-calls]}]
  (if (seq tool-calls)
    {:role "assistant"
     :content (into (if (seq content)
                      [{:type "text" :text content}]
                      [])
                    (map (fn [{:keys [id name input]}]
                           {:type "tool_use"
                            :id id
                            :name name
                            :input (or input {})})
                         tool-calls))}
    {:role "assistant"
     :content content}))

(defn- format-tool-results
  "Format tool results - multiple results go in one user message."
  [{:keys [results]}]
  {:role "user"
   :content (mapv (fn [{:keys [tool-use-id result]}]
                    {:type "tool_result"
                     :tool_use_id tool-use-id
                     :content (js/JSON.stringify (clj->js result))})
                  results)})

(defn- build-messages [chat-messages]
  (vec
   (mapcat (fn [msg]
             (case (:role msg)
               :user [(format-user-message msg)]
               :assistant [(format-assistant-message msg)]
               :tool-result [(format-tool-results msg)]
               []))
           chat-messages)))

;; ============================================
;; Streaming Response Handling
;; ============================================

(defn- parse-sse-line [line]
  (let [trimmed (str/trim line)]
    (when (str/starts-with? trimmed "data: ")
      (let [json-str (subs trimmed 6)]
        (when (and (seq json-str) (not= json-str "[DONE]"))
          (try
            (js->clj (js/JSON.parse json-str) :keywordize-keys true)
            (catch :default e
              (js/console.warn "Failed to parse SSE:" json-str e)
              nil)))))))

(defn- process-stream!
  "Process streaming response, accumulating tool calls and text."
  [reader callbacks]
  (let [{:keys [on-text on-tool-start on-tool-done on-complete on-error]} callbacks
        decoder (js/TextDecoder.)
        buffer (atom "")
        ;; Track current tool being built
        current-tool (atom nil)
        tool-input-buffer (atom "")]

    (letfn [(process-event [parsed]
              (case (:type parsed)
                "content_block_start"
                (let [block (:content_block parsed)]
                  (when (= (:type block) "tool_use")
                    (reset! current-tool {:id (:id block)
                                          :name (:name block)
                                          :input nil})
                    (reset! tool-input-buffer "")
                    (when on-tool-start
                      (on-tool-start (:name block)))))

                "content_block_delta"
                (let [delta (:delta parsed)]
                  (cond
                    (:text delta)
                    (when on-text (on-text (:text delta)))

                    (:partial_json delta)
                    (swap! tool-input-buffer str (:partial_json delta))))

                "content_block_stop"
                (when @current-tool
                  (let [input (when (seq @tool-input-buffer)
                                (try
                                  (js->clj (js/JSON.parse @tool-input-buffer) :keywordize-keys true)
                                  (catch :default _ {})))
                        tool (assoc @current-tool :input input)]
                    (when on-tool-done (on-tool-done tool))
                    (reset! current-tool nil)
                    (reset! tool-input-buffer "")))

                "message_stop"
                nil  ; Handled by stream end

                ;; Ignore other event types
                nil))

            (process-lines [text]
              (doseq [line (str/split text #"\n")]
                (when-let [parsed (parse-sse-line line)]
                  (process-event parsed))))

            (read-chunk []
              (-> (.read reader)
                  (.then (fn [result]
                           (if (.-done result)
                             (do
                               (when (seq @buffer) (process-lines @buffer))
                               (on-complete))
                             (let [chunk (.decode decoder (.-value result) #js {:stream true})
                                   full-text (str @buffer chunk)]
                               (if-let [last-nl (str/last-index-of full-text "\n")]
                                 (do
                                   (reset! buffer (subs full-text (inc last-nl)))
                                   (process-lines (subs full-text 0 (inc last-nl))))
                                 (reset! buffer full-text))
                               (read-chunk)))))
                  (.catch on-error)))]
      (read-chunk))))

;; ============================================
;; API Calls
;; ============================================

(defn- call-api!
  "Call the LLM API. Returns a promise. Uses abort-controller for cancellation."
  [messages abort-controller & {:keys [stream?] :or {stream? true}}]
  (let [{:keys [api-key api-url model]} @config]
    (js/fetch api-url
              #js {:method "POST"
                   :headers #js {"Content-Type" "application/json"
                                 "x-api-key" api-key
                                 "anthropic-version" "2023-06-01"
                                 "anthropic-dangerous-direct-browser-access" "true"}
                   :signal (.-signal abort-controller)
                   :body (js/JSON.stringify
                          (clj->js {:model model
                                    :max_tokens 4096
                                    :system (build-system-prompt)
                                    :messages messages
                                    :tools garden-tools
                                    :stream stream?}))})))

;; ============================================
;; Main Send Message Function
;; ============================================

(defn send-message!
  "Send a message and handle the streaming response with tool execution."
  [content & {:keys [images]}]
  (if (empty? (:api-key @config))
    (state/update-state! [:chat :messages] conj
                         {:role :assistant :content "Please set your API key first. Click ⚙️ in the header."})
    (let [user-msg {:role :user :content content :images images}
          current-msgs (trim-chat-messages (vec (state/get-state :chat :messages)))
          messages (conj current-msgs user-msg)
          ;; Create abort controller for this request
          abort-controller (js/AbortController.)]

      ;; Store controller so it can be cancelled
      (reset! current-request abort-controller)

      ;; Add user message and placeholder for assistant
      (state/set-state! [:chat :messages] (conj messages {:role :assistant :content "" :tool-calls []}))
      (state/set-state! [:chat :input] "")
      (state/set-state! [:chat :loading?] true)

      (letfn [(cleanup! []
                (reset! current-request nil)
                (state/set-state! [:chat :loading?] false))

              (update-assistant-content [f]
                (state/update-state! [:chat :messages]
                                     (fn [msgs]
                                       (let [idx (dec (count msgs))]
                                         (update msgs idx f)))))

              (handle-tool-calls [tool-calls current-messages]
                ;; Execute all tool calls
                (let [results (mapv (fn [tool]
                                      {:tool-use-id (:id tool)
                                       :result (execute-tool (:name tool) (:input tool))})
                                    tool-calls)
                      ;; Add tool results to conversation and continue
                      updated-messages (trim-chat-messages
                                        (into current-messages
                                              [{:role :tool-result
                                                :results results}]))]
                  ;; Make another API call with tool results
                  (-> (call-api! (build-messages updated-messages) abort-controller)
                      (.then (fn [response]
                               (if (.-ok response)
                                 (let [reader (.getReader (.-body response))]
                                   ;; Save tool-result to state, then add new assistant placeholder
                                   (state/set-state! [:chat :messages]
                                                     (conj (vec updated-messages)
                                                           {:role :assistant :content "" :tool-calls []}))
                                   (process-response reader nil))
                                 (-> (.text response)
                                     (.then #(do
                                               (update-assistant-content
                                                (fn [m] (assoc m :content (str "API Error: " %))))
                                               (cleanup!)))))))
                      (.catch (fn [e]
                                (when-not (= (.-name e) "AbortError")
                                  (update-assistant-content
                                   (fn [m] (assoc m :content (str "Error: " (.-message e))))))
                                (cleanup!))))))

              (process-response [reader _]
                (let [accumulated-tools (atom [])]
                  (process-stream!
                   reader
                   {:on-text (fn [text]
                               (update-assistant-content
                                (fn [m] (update m :content str text))))

                    :on-tool-start (fn [name]
                                     (update-assistant-content
                                      (fn [m] (update m :content str "\n🔧 Using " name "..."))))

                    :on-tool-done (fn [tool]
                                    (swap! accumulated-tools conj tool)
                                    (update-assistant-content
                                     (fn [m] (update m :tool-calls conj tool))))

                    :on-complete (fn []
                                   (if (seq @accumulated-tools)
                                     ;; Execute tools and continue
                                     (handle-tool-calls @accumulated-tools
                                                        (state/get-state :chat :messages))
                                     ;; No tools, we're done
                                     (cleanup!)))

                    :on-error (fn [e]
                                (when-not (= (.-name e) "AbortError")
                                  (js/console.error "Stream error:" e)
                                  (update-assistant-content
                                   (fn [m] (assoc m :content (str (:content m) "\nError: " e)))))
                                (cleanup!))})))]

        ;; Initial API call
        (-> (call-api! (build-messages messages) abort-controller)
            (.then (fn [response]
                     (if (.-ok response)
                       (let [reader (.getReader (.-body response))]
                         (process-response reader messages))
                       (-> (.text response)
                           (.then (fn [text]
                                    (update-assistant-content
                                     (fn [m] (assoc m :content (str "API Error: " text))))
                                    (cleanup!)))))))
            (.catch (fn [e]
                      (when-not (= (.-name e) "AbortError")
                        (update-assistant-content
                         (fn [m] (assoc m :content (str "Network error: " (.-message e))))))
                      (cleanup!))))))))

(defn clear-chat! []
  (state/set-state! [:chat :messages] [])
  (state/set-state! [:chat :input] ""))
