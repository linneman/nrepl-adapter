(defproject nrepl-adapter "0.1.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [nrepl "0.4.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/core.async "0.4.474"]]
  :main nrepl-adapter.core
  :aot [nrepl-adapter.core])
