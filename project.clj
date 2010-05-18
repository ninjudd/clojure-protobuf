(defproject clojure-protobuf "0.1.0-SNAPSHOT"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :dependencies [[clojure         "1.2.0-master-SNAPSHOT"]
                 [clojure-contrib "1.2.0-SNAPSHOT"]
                 [classlojure     "0.1.0-SNAPSHOT"]]
  :source-path      "src/clj"
  :java-source-path "src/jvm"
  :resources-path   "proto")

(use 'clojure.contrib.with-ns)
(require 'leiningen.compile)
(with-ns 'leiningen.compile

  (defn proto [project]
    (try (require 'classlojure)
         (require 'leiningen.proto)
         ((ns-resolve 'leiningen.proto 'proto) project)
       (catch java.io.FileNotFoundException e
         (println "you must run 'lein deps' first")
         (System/exit 1))))

  (defn compile [project]
    (proto project)
    (lancet/javac {:srcdir    (make-path (:java-source-path project))
                   :destdir   (:compile-path project)
                   :classpath (apply make-path (get-classpath project))}))
)