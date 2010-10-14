(defproject clojure-protobuf "0.2.11"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :dependencies [[clojure "1.2.0"]
                 [clojure-useful "0.3.0-SNAPSHOT"]]
  :tasks [protobuf.tasks]
  :jar-files ["proto"])