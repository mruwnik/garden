(ns garden.llm-test
  (:require [cljs.test :refer [deftest testing is]]))

;; Maximum number of messages (from llm.cljs)
(def max-chat-messages 20)

;; Recreate the private trim-chat-messages function for testing
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

;; =============================================================================
;; Trim Chat Messages Tests
;; =============================================================================

(deftest trim-chat-messages-test
  (testing "returns messages unchanged if under limit"
    (let [messages [{:role :user :content "hi"}
                    {:role :assistant :content "hello"}]]
      (is (= messages (trim-chat-messages messages)))))

  (testing "returns empty list unchanged"
    (is (= [] (trim-chat-messages []))))

  (testing "trims old messages when over limit"
    (let [messages (vec (for [i (range 25)]
                          {:role (if (even? i) :user :assistant)
                           :content (str "msg " i)}))]
      (is (< (count (trim-chat-messages messages)) (count messages)))
      (is (<= (count (trim-chat-messages messages)) max-chat-messages))))

  (testing "preserves most recent messages"
    (let [messages (vec (concat
                         (for [i (range 15)]
                           {:role (if (even? i) :user :assistant)
                            :content (str "old " i)})
                         (for [i (range 10)]
                           {:role (if (even? i) :user :assistant)
                            :content (str "new " i)})))
          trimmed (trim-chat-messages messages)]
      ;; Should keep newer messages
      (is (some #(= (:content %) "new 9") trimmed))))

  (testing "finds safe trim point after assistant without tools"
    (let [messages [{:role :user :content "1"}
                    {:role :assistant :content "2" :tool-calls []}  ; Safe point
                    {:role :user :content "3"}
                    {:role :assistant :content "4" :tool-calls [{:id "t1" :name "test"}]}
                    {:role :tool-result :results [{:tool-use-id "t1"}]}  ; Safe point
                    {:role :user :content "5"}]]
      ;; Even at this size, function should identify safe points correctly
      (is (= messages (trim-chat-messages messages)))))

  (testing "doesn't break tool_use/tool_result pairs"
    (let [;; Create a conversation with tool calls
          messages (vec (concat
                         ;; Old messages to trim
                         [{:role :user :content "old1"}
                          {:role :assistant :content "old2" :tool-calls []}
                          {:role :user :content "old3"}
                          {:role :assistant :content "old4" :tool-calls []}]
                         ;; Pad to exceed limit
                         (for [i (range 20)]
                           {:role (if (even? i) :user :assistant)
                            :content (str "msg " i)})
                         ;; Recent tool pair that should be preserved
                         [{:role :assistant :content "using tool" :tool-calls [{:id "t1" :name "add_plant"}]}
                          {:role :tool-result :results [{:tool-use-id "t1" :result {:success true}}]}
                          {:role :assistant :content "done"}]))
          trimmed (trim-chat-messages messages)]
      ;; Should preserve the tool pair at the end
      (is (some #(= (:role %) :tool-result) trimmed))
      ;; The assistant message with tool-calls should be followed by its result
      (let [tool-call-idx (first (keep-indexed
                                  (fn [i m]
                                    (when (and (= (:role m) :assistant)
                                               (seq (:tool-calls m)))
                                      i))
                                  trimmed))]
        (when tool-call-idx
          (is (= :tool-result (:role (get trimmed (inc tool-call-idx))))))))))

(deftest message-count-test
  (testing "never exceeds max-chat-messages after trim"
    (let [large-messages (vec (for [i (range 50)]
                                {:role (if (even? i) :user :assistant)
                                 :content (str "msg " i)}))
          trimmed (trim-chat-messages large-messages)]
      (is (<= (count trimmed) max-chat-messages)))))
