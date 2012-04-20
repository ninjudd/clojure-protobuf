(defproject protobuf "0.6.0-beta15"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :dependencies [[clojure "1.4.0"]
                 [ordered-set "0.2.3"]
                 [useful "0.8.0-alpha1"]
                 [schematic "0.0.5"]]
  :dev-dependencies [[gloss "0.2.1-beta1"]
                     [io "0.1.0-alpha2"]
                     [lein-protobuf "0.6.0-beta15"]]
  :hooks [leiningen.protobuf]
  ;; Bug in the current 1.x branch of Leiningen causes
  ;; jar to implicitly clean no matter what, wiping stuff.
  ;; This prevents that.
  :disable-implicit-clean true
  :java-source-path "src")
