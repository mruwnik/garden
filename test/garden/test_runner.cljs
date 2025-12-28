(ns garden.test-runner
  (:require [cljs.test :refer [run-tests]]
            ;; Core tests
            [garden.util.geometry-test]
            [garden.state-test]
            ;; Canvas tests
            [garden.canvas.viewport-test]
            [garden.canvas.topo-test]
            ;; Tool tests
            [garden.tools.plant-test]
            [garden.tools.trace-test]
            [garden.tools.fill-test]
            ;; Topo tests
            [garden.topo.core-test]
            ;; LLM tests
            [garden.llm-test]))

(defn ^:export run []
  (run-tests 'garden.util.geometry-test
             'garden.state-test
             'garden.canvas.viewport-test
             'garden.canvas.topo-test
             'garden.tools.plant-test
             'garden.tools.trace-test
             'garden.tools.fill-test
             'garden.topo.core-test
             'garden.llm-test))
