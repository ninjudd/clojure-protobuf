(defproject clojure-protobuf "0.2.2"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :dependencies [[clojure         "1.2.0-master-SNAPSHOT"]
                 [clojure-contrib "1.2.0-SNAPSHOT"]]
  :tasks [protobuf.tasks]
  :jar-files ["proto"])