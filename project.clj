(defproject protobuf "0.6.1-cake"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [ordered-collections "0.4.0"]
                 [useful "0.8.2-alpha1"]
                 [fs "1.0.0"]
                 [conch "0.2.0"]
                 [schematic "0.0.5"]]
  :dev-dependencies [[gloss "0.2.1"]
                     [io "0.2.1"]]
  :cake-plugins [[cake-protobuf "0.5.0"]]
  :java-compile {:target "5"
                 :source "5"}
  :java-source-path "src")
