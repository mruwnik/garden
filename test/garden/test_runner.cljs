(ns garden.test-runner
  (:require [cljs.test :refer [run-tests]]
            ;; Core tests
            [garden.util.geometry-test]
            [garden.state-test]
            ;; Canvas tests
            [garden.canvas.viewport-test]
            [garden.canvas.topo-test]
            [garden.canvas.grid-test]
            ;; Tool tests
            [garden.tools.plant-test]
            [garden.tools.trace-test]
            [garden.tools.fill-test]
            [garden.tools.area-test]
            [garden.tools.scatter-test]
            ;; Topo tests
            [garden.topo.core-test]
            [garden.topo.slope-test]
            ;; Simulation tests
            [garden.simulation.water.physics-test]
            [garden.simulation.water.grid-test]
            ;; LLM tests
            [garden.llm-test]))

(defn ^:export run []
  (run-tests 'garden.util.geometry-test
             'garden.state-test
             'garden.canvas.viewport-test
             'garden.canvas.topo-test
             'garden.canvas.grid-test
             'garden.tools.plant-test
             'garden.tools.trace-test
             'garden.tools.fill-test
             'garden.tools.area-test
             'garden.tools.scatter-test
             'garden.topo.core-test
             'garden.topo.slope-test
             'garden.simulation.water.physics-test
             'garden.simulation.water.grid-test
             'garden.llm-test))
