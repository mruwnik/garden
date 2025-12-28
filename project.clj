(defproject garden "0.1.0-SNAPSHOT"
  :description "A HTML5 app to plan gardens with"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.async "1.6.681"]
                 [reagent "1.2.0"]
                 [thheller/shadow-cljs "3.3.4"]]

  :source-paths ["src" "dev" "test"]

  :profiles {:dev {:dependencies [[binaryage/devtools "1.0.7"]]}}

  :aliases {"dev"   ["run" "-m" "shadow.cljs.devtools.cli" "watch" "dev"]
            "build" ["run" "-m" "shadow.cljs.devtools.cli" "release" "min"]})
