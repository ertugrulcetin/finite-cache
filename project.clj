(defproject finite-cache "0.1.0-SNAPSHOT"
  :description "FIXME: write description"

  :url "http://example.com/FIXME"

  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.logging "0.5.0"]
                 [org.tcrawley/dynapath "1.0.0"]
                 [clj-tuple "0.2.2"]]

  :min-lein-version "2.5.3"

  :resource-paths ["resources"]

  :target-path "target/%s/"

  :clean-targets ^{:protect false} ["target"]

  :source-paths ["src"]

  :jvm-opts ["-Djdk.attach.allowAttachSelf"])
