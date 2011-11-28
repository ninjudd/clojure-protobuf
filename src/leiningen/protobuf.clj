(ns leiningen.protobuf
  (:refer-clojure :exclude [compile])
  (:use [clojure.java.shell :only [sh]]
        [clojure.string :only [join]]
        [leiningen.help :only [help-for]]
        [leiningen.javac :only [javac]]
        [leiningen.util.paths :only [get-os]]
        [leiningen.core :only [prepend-tasks]])
  (:require [clojure.java.io :as io])
  (:import java.util.zip.ZipFile))

(def version "2.3.0")
(def srcdir  (str "protobuf-" version))
(def zipfile (format "protobuf-%s.zip" version))

(def url
  (java.net.URL.
   (format "http://protobuf.googlecode.com/files/protobuf-%s.zip"  version)))

(defn- unzip [source target-dir]
  (let [zip (ZipFile. source)
        entries (enumeration-seq (.entries zip))
        target-file #(io/file target-dir (.getName %))]
    (doseq [entry entries :when (not (.isDirectory entry))
            :let [f (target-file entry)]]
      (.mkdirs (.getParentFile f))
      (io/copy (.getInputStream zip entry) f))))

(defn- proto-dependencies
  "look for lines starting with import in proto-file"
  [proto-file]
  (for [line (line-seq (io/reader proto-file)) :when (.startsWith line "import")]
    (second (re-matches #".*\"(.*)\".*" line))))

(defn extract-dependencies
  "extract all files proto is dependent on"
  [proto-file target]
  (loop [files (vec (proto-dependencies proto-file))]
    (when-not (empty? files)
      (let [proto (peek files)
            files (pop files)]
        (if (or (.exists (io/file "proto" proto))
                (.exists (io/file target "proto" proto)))
          (recur files)
          (let [location (str "proto/" proto)
                proto-file (io/file target location)]
            (io/copy (io/reader (io/resource location)) proto-file)
            (recur (into files (proto-dependencies proto-file)))))))))

(defn modtime [dir]
  (let [files (-> dir io/file file-seq rest)]
    (if (empty? files)
      0
      (apply max (map #(.lastModified %) files)))))

(defn proto-file? [file]
  (let [name (.getName file)]
    (and (.endsWith name ".proto")
         (not (.startsWith name ".")))))

(defn proto-files [dir]
  (for [file (rest (file-seq dir)) :when (proto-file? file)]
    (.substring (.getPath file) (inc (count (.getPath dir))))))

(defn installed? []
  (try (.contains (:out (sh "protoc" "--version")) version)
       (catch java.io.IOException e)))

(defn read-pass []
  (print "Password: ")
  (flush)
  (join (.readPassword (System/console))))

(defn fetch
  "Fetch protocol-buffer source and unzip it."
  [project]
  (let [target (:target-dir project)]
    (when-not (.exists (io/file target srcdir)) 
      (let [zipped (io/file target zipfile)]
        (with-open [stream (.openStream url)]
          (io/copy stream (io/file zipped)))
        (unzip (io/file zipped) (:target-dir project))))))

(defn uninstall
  "Remove protoc if it is installed."
  [project]
  (when (installed?)
    (let [password (read-pass)]
      (sh "sudo" "-S" "make" "uninstall"
          :dir (io/file (:target-dir project) srcdir)
          :in (str password "\n")))))

(defn install
  "Compile and install protoc to /usr/local."
  [project]
  (when-not (installed?)
    (fetch project)
    (let [source (io/file (:target-dir project) srcdir)]
      (when-not (.exists (io/file source "src" "protoc"))
        (.setExecutable (io/file source "configure") true)
        (.setExecutable (io/file source "install-sh") true)
        (println "Configuring protoc...")
        (sh "./configure" :dir source)
        (println "Running 'make'...")
        (sh "make" :dir source))
      (let [password (str (read-pass) "\n")
            opts     {:dir source :input-string (str password "\n")}]
        (println "Installing...")
        (if (= :linux (get-os))
          (sh "script" "-q" "-c" "sudo -S make install" "/dev/null"
              :dir source :in password)
          (sh "sudo" "-S" "make" "install"
              :dir source :in password))))))

(defn protoc
  "Create .java and .class files from the provided .proto files."
  ([project protos]
     (protoc project protos (io/file (:target-dir project) "protosrc")))
  ([project protos dest]
     (let [target (:target-dir project)
           dest-path (.getPath dest)]
       (when (or (> (modtime "proto") (modtime dest))
                 (> (modtime "proto") (modtime "classes")))
         (.mkdirs dest)
         (.mkdir (io/file target "proto"))
         (doseq [proto protos]
           (println "Compiling" proto "to" dest-path)
           (extract-dependencies (io/file "proto" proto) target)
           (sh "protoc"
               proto
               (str "--java_out=../" dest-path)
               "-I."
               (str "-I../" target "/proto")
               :dir "proto"))
         (javac (assoc project :java-source-path dest-path))))))

(defn compile-google-protobuf
  "Compile com.google.protobuf.*"
  [project]
  (let [proto-files (io/file "proto/google/protobuf")
        target (:target-dir project)]
    (.mkdirs proto-files)
    (io/copy (io/file target srcdir "src/google/protobuf/descriptor.proto")
             (io/file proto-files "descriptor.proto"))
    (protoc project
            ["google/protobuf/descriptor.proto"]
            (io/file target srcdir "java/src/main/java"))))

(defn compile
  "Compile protocol buffer files located in proto dir."
  ([project] (compile project (proto-files (io/file "proto"))))
  ([project files]
     (install project)
     (fetch project)
     (when (= "protobuf" (:name project))
       (compile-google-protobuf project))
     (protoc project files)))

(defn ^{:doc "Tasks for installing and uninstalling protobuf libraries."
        :help-arglists '([subtask & args])
        :subtasks [#'fetch #'install #'uninstall #'compile]}
  protobuf
  ([project] (println (help-for "protobuf")))
  ([project subtask & args]
     (case subtask
       "install"   (apply install project args)
       "uninstall" (apply uninstall project args)
       "compile"   (apply compile project args))))