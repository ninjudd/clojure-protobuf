(ns leiningen.protobuf
  (:refer-clojure :exclude [compile])
  (:use [clojure.string :only [join]]
        [leiningen.help :only [help-for]]
        [leiningen.javac :only [javac]]
        [leiningen.core.eval :only [get-os]]
        [leiningen.core.user :only [leiningen-home]]
        [robert.hooke :only [add-hook]])
  (:require [clojure.java.io :as io]
            [fs.core :as fs]
            [fs.compression :as fs-zip]
            [conch.core :as sh]))

(def cache (io/file (leiningen-home) "cache" "lein-protobuf"))
(def default-version "2.3.0")

(defn version [project]
  (or (:protobuf-version project) default-version))

(defn zipfile [project]
  (io/file cache (format "protobuf-%s.zip" (version project))))

(defn srcdir [project]
  (io/file cache (str "protobuf-" (version project))))

(defn protoc [project]
  (io/file (srcdir project) "src" "protoc"))

(defn url [project]
  (java.net.URL.
   (format "http://protobuf.googlecode.com/files/protobuf-%s.zip" (version project))))

(defn proto-path [project]
  (io/file (get project :proto-path "resources/proto")))

(def ^{:dynamic true} *compile?* true)



(defn target [project]
  (doto (io/file (:target-path project))
    .mkdirs))

(defn- proto-dependencies
  "Look for lines starting with import in proto-file."
  [proto-file]
  (when (.exists proto-file)
    (for [line (line-seq (io/reader proto-file)) :when (.startsWith line "import")]
      (second (re-matches #".*\"(.*)\".*" line)))))

(defn extract-dependencies
  "Extract all files proto depends on into dest."
  [proto-path proto dest]
  (loop [deps (proto-dependencies (io/file proto-path proto))]
    (when-let [[dep & deps] (seq deps)]
      (let [proto-file (io/file dest dep)]
        (if (or (.exists (io/file proto-path dep))
                (.exists proto-file))
          (recur deps)
          (do (.mkdirs (.getParentFile proto-file))
              (io/copy (io/reader (io/resource "proto" proto))
                       proto-file)
              (recur (concat deps (proto-dependencies proto-file)))))))))

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

(defn fetch
  "Fetch protocol-buffer source and unzip it."
  [project]
  (let [zipfile (zipfile project)
        srcdir  (srcdir project)]
    (when-not (.exists zipfile)
      (.mkdirs cache)
      (println (format "Downloading %s to %s" (.getName zipfile) zipfile))
      (with-open [stream (.openStream (url project))]
        (io/copy stream zipfile)))
    (when-not (.exists srcdir)
      (println (format "Unzipping %s to %s" zipfile srcdir))
      (fs-zip/unzip zipfile cache))))

(defn build-protoc
  "Compile protoc from source."
  [project]
  (let [srcdir (srcdir project)
        protoc (protoc project)]
    (when-not (.exists protoc)
      (fetch project)
      (fs/chmod "+x" (io/file srcdir "configure"))
      (fs/chmod "+x" (io/file srcdir "install-sh"))
      (println "Configuring protoc")
      (sh/stream-to-out (sh/proc "./configure" :dir srcdir) :out)
      (println "Running 'make'")
      (sh/stream-to-out (sh/proc "make" :dir srcdir) :out))))

(defn compile-protobuf
  "Create .java and .class files from the provided .proto files."
  ([project protos]
     (compile-protobuf project protos (io/file (target project) "protosrc")))
  ([project protos dest]
     (let [target     (target project)
           class-dest (io/file target "classes")
           proto-dest (io/file target "proto")
           proto-path (proto-path project)]
       (when (or (> (modtime proto-path) (modtime dest))
                 (> (modtime proto-path) (modtime class-dest)))
         (.mkdirs dest)
         (doseq [proto protos]
           (extract-dependencies proto-path proto proto-dest)
           (let [args (into [(.getPath (protoc project)) proto
                             (str "--java_out=" (.getAbsoluteFile dest)) "-I."]
                            (map #(str "-I" (.getAbsoluteFile %))
                                 [proto-dest proto-path]))]
             (println " > " (join " " args))
             (let [result (apply sh/proc (concat args [:dir proto-path]))]
               (when-not (= (sh/exit-code result) 0)
                 (println "ERROR: " (sh/stream-to-string result :err))))))
         (binding [*compile?* false]
           (javac (assoc project :java-source-paths [(.getPath dest)])))))))

(defn compile-google-protobuf
  "Compile com.google.protobuf.*"
  [project]
  (fetch project)
  (let [descriptor (io/file (proto-path project) "google" "protobuf" "descriptor.proto")
        srcdir     (srcdir project)]
    (when-not (.exists descriptor)
      (.mkdirs (.getParentFile descriptor))
      (io/copy (io/file srcdir "src/google/protobuf/descriptor.proto")
               descriptor))
    (compile-protobuf project
                      ["google/protobuf/descriptor.proto"]
                      (io/file srcdir "java" "src" "main" "java"))))

(defn compile
  "Compile protocol buffer files located in proto dir."
  ([project]
     (apply compile project (proto-files (proto-path project))))
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
