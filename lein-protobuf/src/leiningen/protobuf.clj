(ns leiningen.protobuf
  (:refer-clojure :exclude [compile])
  (:use [clojure.string :only [join]]
        [leiningen.help :only [help-for]]
        [leiningen.javac :only [javac]]
        [leiningen.core.eval :only [get-os]]
        [robert.hooke :only [add-hook]])
  (:require [clojure.java.io :as io]
            [fs.core :as fs]
            [fs.compression :as fs-zip]
            [conch.core :as sh])
  (:import java.util.zip.ZipFile))

(def version "2.3.0")
(def cache   (format "%s/.m2/src/com/google/protobuf/%s" (System/getProperty "user.home") version))
(def zipfile (format "protobuf-%s.zip" version))
(def srcdir  (format "%s/protobuf-%s" cache version))
(def protoc  (format "%s/src/protoc" srcdir))

(def ^{:dynamic true} *compile?* true)

(def url
  (java.net.URL.
   (format "http://protobuf.googlecode.com/files/%s" zipfile)))

(defn target [project]
  (doto (io/file (:target-path project))
    .mkdirs))

(defn- proto-dependencies
  "look for lines starting with import in proto-file"
  [proto-file]
  (for [line (line-seq (io/reader proto-file)) :when (.startsWith line "import")]
    (second (re-matches #".*\"(.*)\".*" line))))

(defn extract-dependencies
  "extract all files proto is dependent on"
  [proto-path proto-file target]
  (let [proto-file (io/file proto-path proto-file)]
    (loop [files (vec (proto-dependencies proto-file))]
      (when-not (empty? files)
        (let [proto (peek files)
              files (pop files)]
          (if (or (.exists (io/file proto-path proto))
                  (.exists (io/file target "proto" proto)))
            (recur files)
            (let [location (str "proto/" proto)
                  proto-file (io/file target location)]
              (.mkdirs (.getParentFile proto-file))
              (io/copy (io/reader (io/resource location)) proto-file)
              (recur (into files (proto-dependencies proto-file))))))))))

(defn modtime [dir]
  (let [files (->> dir io/file file-seq rest)]
    (if (empty? files)
      0
      (apply max (map fs/mod-time files)))))

(defn proto-file? [file]
  (let [name (.getName file)]
    (and (.endsWith name ".proto")
         (not (.startsWith name ".")))))

(defn proto-files [dir]
  (for [file (rest (file-seq dir)) :when (proto-file? file)]
    (.substring (.getPath file) (inc (count (.getPath dir))))))

(defn read-pass []
  (print "Password: ")
  (flush)
  (join (.readPassword (System/console))))

(defn fetch
  "Fetch protocol-buffer source and unzip it."
  [project]
  (let [srcdir (io/file srcdir)]
    (when-not (.exists srcdir)
      (.mkdirs srcdir)
      (let [zipped (io/file cache zipfile)]
        (println "Downloading" zipfile)
        (with-open [stream (.openStream url)]
          (io/copy stream (io/file zipped)))
        (println "Unzipping" zipfile "to" target)
        (fs-zip/unzip zipped cache)))))

(defn build-protoc
  "Compile protoc from source."
  [project]
  (let [srcdir (io/file srcdir)
        protoc (io/file protoc)]
    (when-not (.exists protoc)
      (fetch project)
      (when-not (.exists (io/file srcdir "src" "protoc"))
        (fs/chmod "+x" (io/file srcdir "configure"))
        (fs/chmod "+x" (io/file srcdir "install-sh"))
        (println "Configuring protoc")
        (sh/stream-to-out (sh/proc "./configure" :dir srcdir) :out)
        (println "Running 'make'")
        (sh/stream-to-out (sh/proc "make" :dir srcdir) :out)))))

(defn compile-protobuf
  "Create .java and .class files from the provided .proto files."
  ([project protos]
     (compile-protobuf project protos (io/file (target project) "protosrc")))
  ([project protos dest]
     (let [target (target project)
           dest (.getAbsoluteFile dest)
           dest-path (.getPath dest)
           proto-path (.getAbsoluteFile (io/file (or (:proto-path project) "proto")))]
       (when (or (> (modtime proto-path) (modtime dest))
                 (> (modtime proto-path) (modtime (str target "/classes"))))
         (.mkdirs dest)
         (.mkdirs proto-path)
         (doseq [proto protos]
           (extract-dependencies proto-path proto target)
           (let [args [protoc proto (str "--java_out=" dest-path) "-I."
                       (str "-I" (.getAbsoluteFile (io/file target "proto")))
                       (str "-I" proto-path)]]
             (println " > " (join " " args))
             (let [protoc-result (apply sh/proc (concat args [:dir proto-path]))]
               (if (not (= (sh/exit-code protoc-result) 0))
                 (println "ERROR: " (sh/stream-to-string protoc-result :err))))))
         (binding [*compile?* false]
           (javac (assoc project :java-source-paths [dest-path])))))))

(defn compile-google-protobuf
  "Compile com.google.protobuf.*"
  [project]
  (let [proto-files (io/file (get project :proto-path "proto") "google/protobuf")
        target (target project)
        descriptor (io/file proto-files "descriptor.proto")
        out (io/file srcdir "java/src/main/java")]
    (when-not (and (.exists descriptor)
                   (.exists (io/file out "com/google/protobuf/DescriptorProtos.java")))
      (fetch project)
      (.mkdirs proto-files)
      (io/copy (io/file srcdir "src/google/protobuf/descriptor.proto")
               descriptor)
      (compile-protobuf project
                        ["google/protobuf/descriptor.proto"]
                        out))))

(defn compile
  "Compile protocol buffer files located in proto dir."
  ([project]
     (apply compile project (proto-files (io/file (or (:proto-path project) "proto")))))
  ([project & files]
     (build-protoc project)
     (when (and (= "protobuf" (:name project)))
       (compile-google-protobuf project))
     (compile-protobuf project files)))

(add-hook #'javac
          (fn [f & args]
            (when *compile?*
              (compile (first args)))
            (apply f args)))

(defn protobuf
  "Task for compiling protobuf libraries."
  [project & args]
  (apply compile project args))
