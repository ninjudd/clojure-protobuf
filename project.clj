(defproject clojure-protobuf "0.3.1-SNAPSHOT"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :dependencies [[clojure "1.2.0"]]
  :tasks [protobuf.tasks]
  :jar-files ["proto"])