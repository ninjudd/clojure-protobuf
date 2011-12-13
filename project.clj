(defproject protobuf "0.6.0-beta2"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :dependencies [[clojure "1.2.0"]
                 [ordered-set "0.2.2"]
                 [useful "0.7.4-alpha4"]
                 [classlojure "0.6.3"]]
  :dev-dependencies [[gloss "0.2.0-rc1"]
                     [io "0.1.0-alpha2"]]
  :eval-in-project true
  ;; Bug in the current 1.x branch of Leiningen causes
  ;; jar to implicitly clean no matter what, wiping stuff.
  ;; This prevents that.
  :disable-implicit-clean true
  :java-source-path "src")
