(defproject lein-protobuf "0.6.0-beta15"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :dependencies [[fs "1.0.0"]
                 [conch "0.2.0"]]
  :hooks [leiningen.protobuf]
  :eval-in-leiningen true)
