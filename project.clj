(defproject protobuf "0.6.0-beta18"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [ordered-collections "0.4.0"]
                 [useful "0.8.2-alpha1"]
                 [fs "1.0.0"]
                 [conch "0.2.0"]
                 [schematic "0.0.5"]]
  :dev-dependencies [[gloss "0.2.0-rc1"]
                     [io "0.2.0-beta2"]]
  :hooks [leiningen.protobuf]
  :eval-in-leiningen true
  ;; Bug in the current 1.x branch of Leiningen causes
  ;; jar to implicitly clean no matter what, wiping stuff.
  ;; This prevents that.
  :disable-implicit-clean true
  :checksum-deps true
  :java-source-paths ["src"])
