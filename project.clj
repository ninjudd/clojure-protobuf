(defproject clojure-protobuf "0.0.1-SNAPSHOT"
  :description "Clojure-protobuf provides a clojure interface to Google's protocol buffers."
  :dependencies [[org.clojure/clojure         "1.1.0"]
                 [org.clojure/clojure-contrib "1.1.0"]
                 [classlojure "0.0.4-SNAPSHOT"]]
  :source-path "src/clj"
  :java-source-path "src/jvm")

(ns leiningen.compile
  (:require lancet)
  (:use leiningen.compile
        [leiningen.deps :only [deps]]
        [leiningen.classpath :only [make-path find-lib-jars get-classpath]])
  (:refer-clojure :exclude [compile]))

(defn proto [project]
  ; needs to happen at runtime because it will fail if "lein deps" hasn't been run
  (use ['leiningen.proto :only ['proto]])
  (eval '(proto project)))

(def compile* compile)
(defn compile [project]
  (println (find-lib-jars project))
  (when (empty? (find-lib-jars project))
    (deps project :skip-dev))
  (println (find-lib-jars project))
  (proto project)
  (lancet/javac {:srcdir    (make-path (:java-source-path project))
                 :destdir   (:compile-path project)
                 :classpath (apply make-path (get-classpath project))})
  (compile* project))
