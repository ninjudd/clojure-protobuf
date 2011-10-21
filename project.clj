(defproject protobuf "0.5.0-beta2"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :dependencies [[clojure "1.2.0"]
                 [ordered-set "0.2.2"]]
  :cake-plugins [[cake-protobuf "0.5.0-beta1"]]
  :jar-files ["proto"])
