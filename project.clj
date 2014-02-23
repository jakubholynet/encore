(defproject com.taoensso/encore "0.8.0"
  :author "Peter Taoussanis <https://www.taoensso.com>"
  :description "Shared support utils for taoensso.com Clojure libs"
  :url "https://github.com/ptaoussanis/encore"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "Same as Clojure"}
  :min-lein-version "2.3.3"
  :global-vars {*warn-on-reflection* true
                *assert* true}
  :dependencies
  [[org.clojure/clojure    "1.4.0"]
   ;; [org.clojure/clojure "1.5.1"] ; Soon...
   ]

  :cljsbuild {:builds []}
  :test-paths ["test" "src"]
  :profiles
  {:build {:hooks ^:replace []} ; Workaround to avoid :dev hooks during deploy
   :1.5  {:dependencies [[org.clojure/clojure "1.5.1"]]}
   :1.6  {:dependencies [[org.clojure/clojure "1.6.0-beta1"]]}
   :test {:dependencies [[expectations            "1.4.56"]
                         [reiddraper/simple-check "0.5.6"]]
          :plugins [[lein-expectations "0.0.8"]
                    [lein-autoexpect   "1.2.2"]]}
   :dev
   [:1.6 :test
    {:jvm-opts ^:replace ["-server"]
     :hooks [cljx.hooks leiningen.cljsbuild]
     :dependencies
     [[org.clojure/clojurescript "0.0-2173"               :scope "provided"]
      [org.clojure/core.async    "0.1.278.0-76b25b-alpha" :scope "provided"]]
     :plugins
     [[lein-cljsbuild                  "1.0.2"]
      [com.cemerick/clojurescript.test "0.2.2"]
      [com.keminglabs/cljx             "0.3.2"] ; Must precede Austin!
      [com.cemerick/austin             "0.1.4"]]

     :cljx
     {:builds
      [{:source-paths ["src" "test"] :rules :clj  :output-path "target/classes"}
       {:source-paths ["src" "test"] :rules :cljs :output-path "target/classes"}]}

     :cljsbuild
     {:test-commands {"node"    ["node" :node-runner "target/main.js"]
                      "phantom" ["phantomjs" :runner "target/main.js"]}
      :builds ; Compiled in parallel
      [{:id :main
        :source-paths ["src" "test" "target/classes"]
        :compiler     {:output-to "target/main.js"
                       :optimizations :advanced
                       :pretty-print false}}]}}]}

  :plugins [[lein-ancient "0.5.4"]
            [codox        "0.6.7"]]

  :codox {:sources ["target/classes"]} ; For use with cljx
  :aliases
  {"test-all"   ["with-profile" "+test:+1.5,+test:+1.6,+test" "expectations"]
   ;; "test-all"   ["with-profile" "+test:+1.6,+test" "expectations"] ; Soon...
   "test-auto"  ["with-profile" "+test" "autoexpect"]
   "start-dev"  ["with-profile" "+dev" "repl" ":headless"]
   "codox"      ["with-profile" "+dev" "doc"]
   "deploy-lib" ["with-profile" "+dev,+build" "deploy" "clojars"]}

  :repositories
  {"sonatype"
   {:url "http://oss.sonatype.org/content/repositories/releases"
    :snapshots false
    :releases {:checksum :fail}}
   "sonatype-snapshots"
   {:url "http://oss.sonatype.org/content/repositories/snapshots"
    :snapshots true
    :releases {:checksum :fail :update :always}}})
