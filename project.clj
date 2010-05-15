(defproject clojure-protobuf "0.0.1-SNAPSHOT"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :dependencies [[clojure         "1.2.0-master-SNAPSHOT"]
                 [clojure-contrib "1.2.0-SNAPSHOT"]]
  :dev-dependencies [[lein-protobuf "0.0.2-SNAPSHOT"]]
  :source-path "src/clj"
  :java-source-path "src/jvm")

(ns leiningen.compile
  (:require lancet)
  (:use [leiningen.classpath :only [make-path get-classpath]])
  (:refer-clojure :exclude [compile]))

(defn compile-protobuf [project]
  (try
   (require 'leiningen.proto)
   ((ns-resolve 'leiningen.proto 'proto) project)
   (catch java.io.FileNotFoundException e
     (println "you must run 'lein deps' before compile")
     (System/exit 1))))

(defn compile [project]
  (println (get-classpath project))
  (compile-protobuf project)
  (lancet/javac {:srcdir    (make-path (:java-source-path project))
                 :destdir   (:compile-path project)
                 :classpath (apply make-path (get-classpath project))}))