(ns leiningen.proto
  (:use leiningen.install-protoc
        [leiningen.classpath :only [make-path get-classpath]]
        [classlojure :only [extract-resource]]
        [clojure.contrib.io :only [file read-lines]]
        lancet))

(def proto-dir         "proto")
(def proto-extract-dir "lib/proto")
(def proto-src-dir     "lib/proto_src")

(defn- match [p s]
  (when s
    (let [p (if (string? p) (re-pattern p) p)]
      (second (re-matches p s)))))

(defn- proto-exists? [proto]
  (or (.exists (file proto-dir proto))
      (.exists (file proto-extract-dir proto))))

(defn- proto-dependencies
  "look for lines starting with import in proto-file"
  [proto-file]
  (for [line (read-lines proto-file) :when (.startsWith line "import")]
    (match #".*\"(.*)\".*" line)))

(defn extract-dependencies
  "extract all files proto is dependent on"
  [proto]
  (loop [files (vec (proto-dependencies (file proto-dir proto)))]
    (when-not (empty? files)
      (let [proto (peek files)
            files (pop files)]
        (if (proto-exists? proto)
          (recur files)
          (let [proto-file (extract-resource proto proto-extract-dir)]
            (recur (into files (proto-dependencies proto-file)))))))))

(defn protoc [proto src-dir]
  (println "compiling" proto "to" src-dir)
  (extract-dependencies proto)
  (mkdir {:dir src-dir})
  (shell :dir proto-dir
     "protoc" proto (str "--java_out=../" src-dir) "-I." (str "-I../" proto-extract-dir)))

(declare proto)

(defn build-protobuf [project]
  (fetch-source)
  (let [src-dir    (str protobuf-dir "/java/src/main/java")
        descriptor "google/protobuf/descriptor.proto"
        todir      (str proto-dir "/google/protobuf")]
    (mkdir {:dir todir})
    (copy {:file (str protobuf-dir "/src/" descriptor) :todir todir})
    (protoc descriptor src-dir)
    (javac {:srcdir  (make-path src-dir)
            :destdir (:compile-path project)})
    (proto project "clojure/protobuf/collections.proto")))

(defn- proto-files [dir]
  (for [file (rest (file-seq (file dir))) :when (re-find #"\.proto$" (.getName file))]
    (.substring (.getPath file) (inc (count dir)))))

(defn proto
  ([project]
     (if (= "clojure-protobuf" (:name project))
       (build-protobuf project)
       (apply proto project (proto-files proto-dir))))
  ([project & files]
     (install)
     (doseq [file files]
       (protoc file proto-src-dir))
     (javac {:srcdir    (make-path proto-src-dir)
             :destdir   (:compile-path project)
             :classpath (apply make-path (get-classpath project))})))
