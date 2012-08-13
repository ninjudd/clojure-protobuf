(defproject protobuf "0.6.1-beta2"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/flatland/clojure-protobuf"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [ordered-collections "0.4.0"]
                 [useful "0.8.2-alpha1"]
                 [schematic "0.0.5"]]
  :plugins [[lein-protobuf "0.2.0-beta6"]]
  :aliases {"testall" ["with-profile" "dev,default:dev,1.3,default:dev,1.5,default" "test"]}
  :profiles {:1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.0-master-SNAPSHOT"]]}
             :dev {:dependencies [[gloss "0.2.1"]
                                  [io "0.2.0-beta2"]]}}
  :repositories {"sonatype-snapshots" {:url "http://oss.sonatype.org/content/repositories/snapshots"
                                       :snapshots true
                                       :releases {:checksum :fail :update :always}}}
  :protobuf-version "2.4.1"
  :checksum-deps true
  :java-source-paths ["src"])
