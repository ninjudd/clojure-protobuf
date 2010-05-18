(defproject clojure-protobuf "0.1.0-SNAPSHOT"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :dependencies [[clojure         "1.2.0-master-SNAPSHOT"]
                 [clojure-contrib "1.2.0-SNAPSHOT"]
                 [classlojure     "0.0.4-SNAPSHOT"]]
  :source-path      "src/clj"
  :java-source-path "src/jvm"
  :resources-path   "proto")

(use 'clojure.contrib.with-ns)
(require 'leiningen.compile)
(with-ns 'leiningen.compile
  (use '[leiningen.proto :only [proto]])

  (defn compile [project]
    (deps project :skip-dev)
    (proto project)
    (lancet/javac {:srcdir    (make-path (:java-source-path project))
                   :destdir   (:compile-path project)
                   :classpath (apply make-path (get-classpath project))})))