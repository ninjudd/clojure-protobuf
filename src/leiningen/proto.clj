(ns leiningen.proto
  (:use leiningen.install-protoc
        [leiningen.classpath :only [make-path get-classpath]]
        [classlojure :only [extract-resource]]
        [clojure.contrib.io :only [file read-lines]]
        lancet))

(defn proto-files [dir]
  (for [file (rest (file-seq (file dir))) :when (re-find #"\.proto$" (.getName file))]
    (.substring (.getPath file) (inc (count dir)))))

(defn- match [p s]
  (when s
    (let [p (if (string? p) (re-pattern p) p)]
      (second (re-matches p s)))))

(defn extract-proto
  "extract proto-file to a temporary directory and return the directory"  
  [proto-file]
  (match (str "(.*)/" proto-file)
         (extract-resource proto-file)))

(defn proto-dependencies
  "look for lines starting with import in proto-file"
  [proto-file]
  (for [line (read-lines (file "proto" proto-file)) :when (.startsWith line "import")]
    (match #".*\"(.*)\".*" line)))

(defn protoc [proto-file src-dir]
  (println "compiling" proto-file "to" src-dir)
  (mkdir {:dir src-dir})
  (let [src-dir (str "../" src-dir)]
    (apply shell :dir "proto"
       "protoc" proto-file (str "--java_out=" src-dir) "-I."
       (for [dir (map extract-proto (proto-dependencies proto-file)) :when dir]
         (str "-I" dir)))))

(declare proto)

(defn build-protobuf [project]       
  (fetch-source)
  (let [src-dir    (str protobuf-dir "/java/src/main/java")
        descriptor "google/protobuf/descriptor.proto"
        todir      "proto/google/protobuf"]
    (mkdir {:dir todir})
    (copy {:file (str protobuf-dir "/src/" descriptor) :todir todir})
    (protoc descriptor src-dir)
    (javac {:srcdir  (make-path src-dir)
            :destdir (:compile-path project)}) 
    (proto project "clojure/protobuf/collections.proto")))

(defn proto
  ([project]
     (if (= "clojure-protobuf" (:name project))
       (build-protobuf project)
       (apply proto project (proto-files "proto"))))
  ([project & files]
     (install)
     (let [src-dir (or (:proto-source-dir project) "lib/src")]
       (doseq [file files]
         (protoc file src-dir))
       (javac {:srcdir    (make-path src-dir)
               :destdir   (:compile-path project)
               :classpath (apply make-path (get-classpath project))}))))
