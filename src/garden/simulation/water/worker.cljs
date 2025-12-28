(ns garden.simulation.water.worker
  "Web Worker coordination for water simulation.
   Handles communication between main thread and worker.")

;; -----------------------------------------------------------------------------
;; Worker State

(defonce ^:private worker-instance (atom nil))
(defonce ^:private worker-ready? (atom false))
(defonce ^:private message-handlers (atom {}))

;; -----------------------------------------------------------------------------
;; Message Handling

(defn- dispatch-message
  "Dispatch a message from the worker to registered handlers."
  [msg-type data]
  (when-let [handler (get @message-handlers msg-type)]
    (handler data)))

(defn- handle-worker-message
  "Handle messages from the worker."
  [event]
  (let [data (.-data event)
        msg-type (.-type data)]
    (case msg-type
      "loaded" (dispatch-message :loaded nil)
      "ready"  (do (reset! worker-ready? true)
                   (dispatch-message :ready nil))
      "water-update" (dispatch-message :water-update (.-grid data))
      nil)))

(defn- handle-worker-error
  "Handle worker errors."
  [event]
  (js/console.error "Water worker error:" event))

;; -----------------------------------------------------------------------------
;; Worker Lifecycle

(defn init!
  "Initialize the water worker. Idempotent."
  []
  (when-not @worker-instance
    (try
      (let [w (js/Worker. "/js/compiled/water-worker.js")]
        (.addEventListener w "message" handle-worker-message)
        (.addEventListener w "error" handle-worker-error)
        (reset! worker-instance w))
      (catch js/Error e
        (js/console.error "Failed to create water worker:" e)))))

(defn terminate!
  "Terminate the water worker."
  []
  (when-let [w @worker-instance]
    (.terminate w)
    (reset! worker-instance nil)
    (reset! worker-ready? false)))

(defn ready?
  "Check if the worker is ready."
  []
  @worker-ready?)

;; -----------------------------------------------------------------------------
;; Message Sending

(defn send!
  "Send a message to the worker."
  [msg-type & {:as data}]
  (when-let [w @worker-instance]
    (.postMessage w (clj->js (assoc data :type (name msg-type))))))

(defn send-with-transfer!
  "Send a message with transferable objects."
  [msg-type transfer-buffers & {:as data}]
  (when-let [w @worker-instance]
    (.postMessage w
                  (clj->js (assoc data :type (name msg-type)))
                  (clj->js transfer-buffers))))

;; -----------------------------------------------------------------------------
;; Handler Registration

(defn on-message
  "Register a handler for a message type.
   Returns a function to unregister the handler."
  [msg-type handler]
  (swap! message-handlers assoc msg-type handler)
  #(swap! message-handlers dissoc msg-type))
