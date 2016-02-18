(defproject org.clojars.ghaskins/protobuf "0.1-SNAPSHOT"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :url "https://github.com/ghaskins/clojure-protobuf"
  :javac-options ["-target" "1.7" "-source" "1.7"]
  :java-source-paths ["src"]
  :lein-release {:deploy-via :clojars}
  :repositories [["releases" {:url "https://clojars.org/repo"
                              :creds :gpg}]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.google.protobuf/protobuf-java "2.6.1"]
                 [org.flatland/useful "0.11.5"]
                 [org.flatland/schematic "0.1.5"]
                 [org.flatland/io "0.3.0"]
                 [ordered-collections "0.4.2"]
                 [gloss "0.2.1"]]
  :aliases {"testall" ["with-profile" "dev,default:dev,1.3,default:dev,1.5,default" "test"]}
  :checksum-deps true)
