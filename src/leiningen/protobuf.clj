(ns leiningen.protobuf
  (:use [clojure.java.shell :only [sh]]
        [clojure.string :only [join]]
        [leiningen.help :only [help-for]]
        [leiningen.javac :only [javac]]
        [leiningen.util.paths :only [get-os]]
        [leiningen.core :only [prepend-tasks]]
        uncle.core)
  (:require [clojure.java.io :as io])
  (:import [org.apache.tools.ant.taskdefs Chmod Copy ExecTask Get Javac Mkdir Untar]
           java.util.zip.ZipFile))

(def version "2.3.0")
(def srcdir  (format "lib/protobuf-%s" version))
(def zipfile (format "lib/protobuf-%s.zip" version))
(def url     (java.net.URL.
              (format
               "http://protobuf.googlecode.com/files/protobuf-%s.zip"
               version)))

(defn- unzip [source target-dir] 
  (let [zip (ZipFile. source)
        entries (enumeration-seq (.entries zip))
        target-file #(io/file target-dir (.getName %))]
    (doseq [entry entries :when (not (.isDirectory entry))
            :let [f (target-file entry)]]
      (.mkdirs (.getParentFile f))
      (io/copy (.getInputStream zip entry) f))))

(defn- proto-dependencies "look for lines starting with import in proto-file"
  [proto-file]
  (for [line (line-seq (io/reader proto-file)) :when (.startsWith line "import")]
    (second (re-matches #".*\"(.*)\".*" line))))

(defn extract-dependencies "extract all files proto is dependent on"
  [proto-file]
  (loop [files (vec (proto-dependencies proto-file))]
    (when-not (empty? files)
      (let [proto (peek files)
            files (pop files)]
        (if (or (.exists (io/file "proto" proto)) (.exists (io/file "build/proto" proto)))
          (recur files)
          (let [location (str "proto/" proto)
                proto-file (io/file "build" location)]
            (io/copy (io/reader (io/resource location)) proto-file)
            (recur (into files (proto-dependencies proto-file)))))))))

(defn modtime [dir]
  (let [files (rest (file-seq (io/file dir)))]
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

(defn fetch [project]
  (when-not (.exists (io/file srcdir))
    (with-open [stream (.openStream url)]
      (io/copy stream (io/file zipfile)))
    (unzip (io/file zipfile) "lib")))

(defn uninstall
  "Remove protoc if it is installed."
  [project]
  (when (installed?)
    (let [password (read-pass)]
      (sh "sudo" "-S" "make" "uninstall"
          :dir srcdir :in (str password "\n")))))

(defn install
  "Compile and install protoc to /usr/local."
  [project]
  (when-not (installed?)
    (fetch nil)
    (when-not (.exists (io/file srcdir "src" "protoc"))
      (.setExecutable (io/file srcdir "configure") true)
      (.setExecutable (io/file srcdir "install-sh") true)
      (println "Configuring protoc...")
      (sh "./configure" :dir srcdir)
      (println "Running 'make'...")
      (sh "make" :dir srcdir))
    (let [password (str (read-pass) "\n")
          opts     {:dir srcdir :input-string (str password "\n")}]
      (println "Installing...")
      (if (= :linux (get-os))
        (sh "script" "-q" "-c" "sudo -S make install" "/dev/null"
            :dir srcdir :in password)
        (sh "sudo" "-S" "make" "install"
            :dir srcdir :in password)))))

(defn protoc
  ([project protos] (protoc project "build/protosrc" protos))
  ([project dest protos]
     (when (or (> (modtime "proto") (modtime dest))
               (> (modtime "proto") (modtime "classes")))
       (.mkdirs (io/file dest))
       (.mkdir (io/file "build" "proto"))
       (doseq [proto protos]
         (println "Compiling" proto "to" dest)
         (extract-dependencies (io/file "proto" proto))
         (try (ant ExecTask {:executable "protoc" :dir "proto" :failonerror true}
                (args [proto (str "--java_out=../" dest) "-I." "-I../build/proto"]))
              (catch org.apache.tools.ant.BuildException e
                (throw (Exception. (str "error compiling " proto))))))
       (javac project dest))))

(defn compile
  "Compile protocol buffer files located in proto dir."
  ([project] (compile project (proto-files (io/file "proto"))))
  ([project files]
     (install)
     (protoc project files)))

(defn google
  [project]
  (let [proto-files (io/file "proto/google/protobuf")]
    (.mkdirs proto-files)
    (io/copy
     (io/reader
      (str srcdir "/src/google/protobuf/descriptor.proto"))
     proto-files))
  (protoc ["google/protobuf/descriptor.proto"] (str srcdir "/java/src/main/java")))

(prepend-tasks #'javac compile)

(defn ^{:doc "Tasks for installing and uninstalling protobuf libraries."
        :help-arglists '([subtask & args])
        :subtasks [#'fetch #'install #'uninstall #'compile]}
  protobuf
  ([project] (println (help-for "protobuf")))
  ([project subtask & args]
     (case subtask
       "proto"     (apply google project args)
       "fetch"     (apply fetch project args)
       "install"   (apply install project args)
       "uninstall" (apply uninstall project args)
       "compile"   (apply compile project args))))