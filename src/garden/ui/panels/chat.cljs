(ns garden.ui.panels.chat
  (:require [reagent.core :as r]
            [garden.state :as state]
            [garden.llm :as llm]))

;; Local state for pending images and settings modal
(defonce local-state
  (r/atom {:pending-images []
           :show-settings? false
           :api-key-input ""}))

(defn- read-file-as-base64
  "Read a file and return base64 data via callback."
  [file callback]
  (let [reader (js/FileReader.)]
    (set! (.-onload reader)
          (fn [e]
            (let [result (.-result (.-target e))
                  ;; Remove data URL prefix to get pure base64
                  base64 (second (re-find #"base64,(.+)" result))]
              (callback {:data base64
                         :media-type (.-type file)
                         :name (.-name file)}))))
    (.readAsDataURL reader file)))

(defn- handle-image-select
  "Handle image file selection."
  [e]
  (let [files (array-seq (.. e -target -files))]
    (doseq [file files]
      (when (re-find #"^image/" (.-type file))
        (read-file-as-base64
         file
         (fn [img-data]
           (swap! local-state update :pending-images conj img-data)))))))

(defn- remove-pending-image
  "Remove a pending image by index."
  [idx]
  (swap! local-state update :pending-images
         (fn [imgs] (vec (concat (take idx imgs) (drop (inc idx) imgs))))))

(defn- pending-images-preview
  "Show preview of pending image attachments."
  []
  (let [images (:pending-images @local-state)]
    (when (seq images)
      [:div.pending-images
       (for [[idx img] (map-indexed vector images)]
         ^{:key idx}
         [:div.pending-image
          [:img {:src (str "data:" (:media-type img) ";base64," (:data img))}]
          [:button.remove-image
           {:on-click #(remove-pending-image idx)}
           "×"]])])))

(defn- message-images
  "Render images attached to a message."
  [images]
  (when (seq images)
    [:div.message-images
     (for [[idx img] (map-indexed vector images)]
       ^{:key idx}
       [:img.message-image
        {:src (str "data:" (:media-type img) ";base64," (:data img))}])]))

(defn- message-bubble
  "Render a single chat message."
  [{:keys [role content images]}]
  [:div.chat-message {:class (name role)}
   (when (seq images)
     [message-images images])
   [:div.message-content content]])

(defn- settings-modal
  "Modal for API key settings."
  []
  (let [{:keys [show-settings? api-key-input]} @local-state]
    (when show-settings?
      [:div.settings-overlay
       {:on-click #(swap! local-state assoc :show-settings? false)}
       [:div.settings-modal
        {:on-click #(.stopPropagation %)}
        [:h3 "Settings"]
        [:div.form-field
         [:label "Anthropic API Key"]
         [:input.text-input
          {:type "password"
           :value api-key-input
           :placeholder "sk-ant-..."
           :on-change #(swap! local-state assoc :api-key-input (-> % .-target .-value))}]]
        [:div.settings-buttons
         [:button.btn-secondary
          {:on-click #(swap! local-state assoc :show-settings? false)}
          "Cancel"]
         [:button.btn-primary
          {:on-click (fn []
                       (llm/set-api-key! api-key-input)
                       (swap! local-state assoc :show-settings? false))}
          "Save"]]]])))

(defn- chat-input
  "Chat input field with send button and image attachment."
  []
  (let [input-value (state/get-state :chat :input)
        loading? (state/get-state :chat :loading?)
        pending-images (:pending-images @local-state)
        can-send? (and (not loading?)
                       (or (seq input-value) (seq pending-images)))
        do-send! (fn []
                   (let [images (:pending-images @local-state)]
                     (llm/send-message! input-value :images (when (seq images) images))
                     (swap! local-state assoc :pending-images [])))]
    [:div.chat-input-area
     [pending-images-preview]
     [:div.chat-input-container
      [:label.image-attach-btn
       {:title "Attach image"}
       "📎"
       [:input
        {:type "file"
         :accept "image/*"
         :multiple true
         :style {:display "none"}
         :on-change handle-image-select}]]
      [:textarea.chat-input
       {:value (or input-value "")
        :placeholder "Ask about your garden..."
        :disabled loading?
        :on-change #(state/set-state! [:chat :input] (-> % .-target .-value))
        :on-key-down (fn [e]
                       (cond
                         ;; Escape cancels request
                         (= (.-key e) "Escape")
                         (when loading?
                           (llm/cancel-request!))
                         ;; Enter sends (without shift)
                         (and (= (.-key e) "Enter")
                              (not (.-shiftKey e)))
                         (do
                           (.preventDefault e)
                           (when can-send?
                             (do-send!)))))}]
      [:button.chat-send-btn
       {:class (when loading? "cancel")
        :on-click (fn []
                    (if loading?
                      (llm/cancel-request!)
                      (when can-send?
                        (do-send!))))}
       (if loading? "Cancel" "Send")]]]))

(defn- chat-messages
  "Scrollable chat messages container."
  []
  (let [messages (state/get-state :chat :messages)
        loading? (state/get-state :chat :loading?)]
    [:div.chat-messages
     (if (empty? messages)
       [:div.chat-empty
        [:p "Hi! I can help you plan your garden."]
        [:p.hint "Try asking me to suggest plants, design a layout, or explain gardening concepts."]
        [:p.hint "You can also attach images of your garden or reference photos."]]
       (for [[idx msg] (map-indexed vector messages)]
         ^{:key idx}
         [message-bubble msg]))
     ;; Show typing indicator when streaming
     (when (and loading?
                (or (empty? messages)
                    (empty? (:content (last messages)))))
       [:div.chat-message.assistant
        [:div.message-content.loading "Thinking..."]])]))

(defn chat-panel
  "The main chat panel component."
  []
  [:div.chat-panel
   [:div.chat-header
    [:span "Garden Assistant"]
    [:div.chat-header-buttons
     [:button.chat-header-btn
      {:title "Clear chat"
       :on-click llm/clear-chat!}
      "🗑️"]
     [:button.chat-header-btn
      {:title "Settings"
       :on-click (fn []
                   (swap! local-state assoc
                          :show-settings? true
                          :api-key-input (llm/get-api-key)))}
      "⚙️"]
     [:button.chat-close
      {:on-click #(state/set-state! [:chat :open?] false)}
      "×"]]]
   [chat-messages]
   [chat-input]
   [settings-modal]])

(defn chat-toggle-button
  "Button to open/close the chat panel."
  []
  (let [open? (state/get-state :chat :open?)]
    [:button.chat-toggle
     {:class (when open? "active")
      :title "Garden Assistant"
      :on-click #(state/update-state! [:chat :open?] not)}
     "💬 Ask AI"]))
