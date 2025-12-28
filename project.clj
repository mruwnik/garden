(defproject garden "0.1.0-SNAPSHOT"
  :description "Interactive garden planning tool with topographic mapping, water simulation, and plant placement."
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [reagent "1.2.0"]
                 [thheller/shadow-cljs "3.3.4"]]

  :source-paths ["src" "dev" "test"]

  :profiles {:dev {:dependencies [[binaryage/devtools "1.0.7"]]}}

  :aliases {"dev"   ["run" "-m" "shadow.cljs.devtools.cli" "watch" "dev" "water-worker"]
            "build" ["run" "-m" "shadow.cljs.devtools.cli" "release" "min" "water-worker"]})
