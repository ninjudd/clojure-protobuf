(defproject protobuf "0.5.0-alpha1"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :dependencies [[clojure "1.2.0"]
                 [ordered-set "0.2.2"]]
  :dev-dependencies [[org.clojars.flatland/cake-marginalia "0.6.1"]]
  :tasks [protobuf.tasks cake-marginalia.tasks]
  :source-path ["src/clj" "src/jvm"]
  :jar-files ["proto"])
