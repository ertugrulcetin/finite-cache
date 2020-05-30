(defproject finite-cache "0.1.0"

  :description "finite-cache is a Clojure caching library that allows you to limit the size of a cache object."

  :url "https://github.com/ertugrulcetin/finite-cache"

  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.logging "0.5.0"]
                 [org.tcrawley/dynapath "1.0.0"]
                 [clj-tuple "0.2.2"]]

  :plugins [[lein-cljfmt "0.6.7"]]

  :cljfmt {:indents {#".*" [[:inner 0]]}
           :remove-consecutive-blank-lines? false}

  :min-lein-version "2.5.3"

  :resource-paths ["resources"]

  :target-path "target/%s/"

  :clean-targets ^{:protect false} ["target"]

  :source-paths ["src"]

  :jvm-opts ["-XX:-OmitStackTraceInFastThrow"
             "-Djdk.attach.allowAttachSelf"])
