(defproject lein-protobuf "0.1.0"
  :description "Leiningen plugin for clojure-protobuf."
  :dependencies [[fs "1.0.0"]
                 [conch "0.2.0"]]
  :eval-in-leiningen true
  ;; Bug in the current 1.x branch of Leiningen causes
  ;; jar to implicitly clean no matter what, wiping stuff.
  ;; This prevents that.
  :disable-implicit-clean true
  :checksum-deps true)
