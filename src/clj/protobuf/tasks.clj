(ns protobuf.tasks
  (:use cake cake.core cake.ant
        [cake.file :only [file]]
        [bake.core :only [log]]
        [cake.utils :only [os-name prompt-read]]
        [cake.tasks.compile :only [compile-java]]
        [clojure.java.shell :only [sh]]
        [clojure.java.io :only [reader]]
        [bake.io :only [extract-resource]])
  (:import [org.apache.tools.ant.taskdefs Chmod Copy ExecTask Get Javac Mkdir Untar]))

(def version "2.3.0")
(def srcdir  (format "lib/protobuf-%s" version))
(def tarfile (format "lib/protobuf-%s.tar.gz" version))
(def url     (java.net.URL. (format "http://protobuf.googlecode.com/files/protobuf-%s.tar.gz" version)))

(defn installed? []
  (try (.contains (:out (sh "protoc" "--version")) version)
       (catch java.io.IOException e)))

(deftask fetch-protoc
  (when-not (.exists (file srcdir))
    (ant Get   {:src url :dest tarfile})
    (ant Untar {:src tarfile :dest "lib" :compression "gzip"})))

(deftask install-protoc
  "Compile and install protoc to /usr/local."
  (when-not (installed?)
    (invoke fetch-protoc)
    (when-not (.exists (file srcdir "src" "protoc"))
      (ant Chmod {:file (file srcdir "configure")  :perm "+x"})
      (ant Chmod {:file (file srcdir "install-sh") :perm "+x"})
      (ant ExecTask {:dir srcdir :executable "./configure"})
      (ant ExecTask {:dir srcdir :executable "make"}))
    (let [password (prompt-read "Password" :echo false)
          opts     {:dir srcdir :input-string (str password "\n")}]
      (if (= "linux" (os-name))
        (ant ExecTask (assoc opts :executable "script")
             (argline "-q -c 'sudo -S make install' /dev/null"))
        (ant ExecTask (assoc opts :executable "sudo")
             (args ["-S" "make" "install"]))))))

(deftask uninstall-protoc
  "Remove protoc if it is installed."
  (when (installed?)
    (let [password (prompt-read "Password" :echo false)]
      (ant ExecTask {:dir srcdir :executable "sudo" :input-string (str password "\n")}
           (args ["-S" "make" "uninstall"])))))

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

(defn modtime [dir]
  (let [files (rest (file-seq (file dir)))]
    (if (empty? files)
      0
      (apply max (map #(.lastModified %) files)))))

(defn protoc
  ([protos] (protoc protos "build/protosrc"))
  ([protos dest]
     (when (or (:force *opts*)
               (> (modtime "proto") (modtime dest))
               (> (modtime "proto") (modtime "classes")))       
       (ant Mkdir {:dir dest})
       (ant Mkdir {:dir "build/proto"})
       (doseq [proto protos]
         (log "Compiling" proto "to" dest)
         (extract-dependencies (file "proto" proto))
         (try (ant ExecTask {:executable "protoc" :dir "proto" :failonerror true}
                   (args [proto (str "--java_out=../" dest) "-I." "-I../build/proto"]))
              (catch org.apache.tools.ant.BuildException e
                (throw (Exception. (str "error compiling " proto))))))
       (compile-java (file dest)))))

(defn build-protobuf []
  (ant Mkdir {:dir "proto/google/protobuf"})
  (ant Copy {:file (str srcdir "/src/google/protobuf/descriptor.proto") :todir "proto/google/protobuf"})
  (protoc ["google/protobuf/descriptor.proto"] (str srcdir "/java/src/main/java"))
  (protoc ["clojure/protobuf/extensions.proto" "clojure/protobuf/test.proto"]))

(defn proto-file? [file]
  (let [name (.getName file)]
    (and (.endsWith name ".proto")
         (not (.startsWith name ".")))))

(defn proto-files [dir]
  (for [file (rest (file-seq dir)) :when (proto-file? file)]
    (.substring (.getPath file) (inc (count (.getPath dir))))))

(deftask compile #{proto})
(deftask proto #{deps install-protoc}
  "Compile protocol buffer files located in proto dir."
  (if (= "clojure-protobuf" (:artifact-id *project*))
    (do (invoke fetch-protoc)
        (build-protobuf))
    (protoc (or (:proto *opts*) (proto-files (file "proto"))))))
