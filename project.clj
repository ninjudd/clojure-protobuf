(defproject protobuf "0.6.0-beta14"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :dependencies [[clojure "1.2.0"]
                 [ordered-set "0.3.0"]
                 [useful "0.8.0-alpha1"]
                 [fs "1.0.0"]
                 [conch "0.2.0"]
                 [schematic "0.0.5"]]
  :dev-dependencies [[gloss "0.2.0-rc1"]
                     [io "0.1.0-alpha2"]]
  :hooks [leiningen.protobuf]
  :eval-in-leiningen true
  ;; Bug in the current 1.x branch of Leiningen causes
  ;; jar to implicitly clean no matter what, wiping stuff.
  ;; This prevents that.
  :disable-implicit-clean true
  :java-source-path "src")
