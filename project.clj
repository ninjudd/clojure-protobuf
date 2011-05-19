(defproject clojure-protobuf "0.4.0"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :dependencies [[clojure "1.2.0"]
                 [ordered-set "0.2.2"]]
  :tasks [protobuf.tasks]
  :jar-files ["proto"])
