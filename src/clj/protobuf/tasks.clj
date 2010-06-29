(ns protobuf.tasks
  (:use cake cake.ant
        [clojure.java.shell :only [sh]]
        [clojure.java.io :only [reader]]
        [useful.io :only [extract-resource]])
  (:import [org.apache.tools.ant.taskdefs Chmod Copy ExecTask Get Javac Mkdir Untar]
           [java.net URL URLConnection JarURLConnection]
           [java.io File]))

(def version "2.3.0")
(def srcdir  (format "build/protobuf-%s" version))
(def tarfile (format "build/protobuf-%s.tar.gz" version))
(def url     (URL. (format "http://protobuf.googlecode.com/files/protobuf-%s.tar.gz" version)))

(defn installed? []
  (try (.contains (:out (sh "protoc" "--version")) version)
       (catch java.io.IOException e)))

(deftask fetch-protoc
  (when-not (.exists (file srcdir))
    (ant Get   {:src url :dest tarfile})
    (ant Untar {:src tarfile :dest "build" :compression "gzip"})))

(deftask install-protoc
  (when-not (installed?)
    (run-task 'fetch-protoc)
    (when-not (.exists (file srcdir "src" "protoc"))
      (ant Chmod {:file (file srcdir "configure")  :perm "+x"})
      (ant Chmod {:file (file srcdir "install-sh") :perm "+x"})
      (ant ExecTask {:dir srcdir :executable "./configure"})
      (ant ExecTask {:dir srcdir :executable "make"}))
    (ant ExecTask {:dir srcdir :executable "sudo"}
         (args ["make" "install"]))))

(defn- proto-dependencies "look for lines starting with import in proto-file"
  [proto-file]
  (for [line (line-seq (reader proto-file)) :when (.startsWith line "import")]
    (second (re-matches #".*\"(.*)\".*" line))))

(defn extract-dependencies "extract all files proto is dependent on"
  [proto-file]
  (loop [files (vec (proto-dependencies proto-file))]
    (when-not (empty? files)
      (let [proto (peek files)
            files (pop files)]
        (if (or (.exists (file "proto" proto)) (.exists (file "build/proto" proto)))
          (recur files)
          (let [proto-file (extract-resource (str "proto/" proto) "build")]
            (recur (into files (proto-dependencies proto-file)))))))))

(defn protoc
  ([protos] (protoc protos "build/protosrc"))
  ([protos dest]
      (doseq [proto protos]
        (log "compiling" proto "to" dest)
        (extract-dependencies (file "proto" proto))
        (ant Mkdir {:dir dest})
        (ant Mkdir {:dir "build/proto"})
        (ant ExecTask {:executable "protoc" :dir "proto"}
             (args [proto (str "--java_out=../" dest) "-I." "-I../build/proto"])))
      (ant Javac {:srcdir    (path dest)
                  :destdir   (file "classes")
                  :classpath (classpath project)})))

(defn build-protobuf []
  (ant Mkdir {:dir "proto/google/protobuf"})
  (ant Copy {:file (str srcdir "/src/google/protobuf/descriptor.proto") :todir "proto/google/protobuf"})
  (protoc ["google/protobuf/descriptor.proto"] (str srcdir "/java/src/main/java"))
  (protoc ["clojure/protobuf/collections.proto"]))

(defn proto-files [dir]
  (for [file (rest (file-seq dir)) :when (.endsWith (.getName file) ".proto")]
    (.substring (.getPath file) (inc (count (.getPath dir))))))

(deftask compile #{proto})
(deftask proto
  (if (= "clojure-protobuf" (:artifact-id project))
    (do (run-task 'fetch-protoc)
        (build-protobuf))
    (protoc (or (:proto opts) (proto-files (file "proto"))))))
