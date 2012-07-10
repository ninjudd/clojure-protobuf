(defproject protobuf "0.6.0-beta19"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/flatland/clojure-protobuf"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [ordered-collections "0.4.0"]
                 [useful "0.8.2-alpha1"]
                 [schematic "0.0.5"]]
  :plugins [[lein-protobuf "0.1.0"]]
  :profiles {:dev {:dependencies [[gloss "0.2.1"]
                                  [io "0.2.0-beta2"]]}}
  :hooks [leiningen.protobuf]
  ;; Bug in the current 1.x branch of Leiningen causes
  ;; jar to implicitly clean no matter what, wiping stuff.
  ;; This prevents that.
  :disable-implicit-clean true
  :checksum-deps true
  :java-source-paths ["src"])
