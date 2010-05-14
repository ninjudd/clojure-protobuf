(ns leiningen.proto
  (:use leiningen.install-protoc
        [leiningen.classpath :only [make-path get-classpath]]
        [classlojure :only [extract-resource]]
        lancet))

(def proto-src "protos/src")

(defn proto-files [dir]
  (for [file  (rest (file-seq (java.io.File. dir)))
        :when (re-find #"\.proto$" (.getName file))]
    (.getPath file)))

(deftarget clojure-protos "extract collections.proto to a temporary directory and return the directory"
  ((re-matches #"(.*)/clojure/protobuf/collections.proto"
     (extract-resource "protos/clojure/protobuf/collections.proto")) 1))

(defn protoc
  ([file] (protoc file proto-src))
  ([file out]
     (println "compiling" file "to" out)
     (mkdir {:dir out})
     (shell "protoc" file
            (str "--java_out=" out)
            "-I." (str "-I" (clojure-protos)))))

(defn proto [project]
  (install)
  (when (= "clojure-protobuf" (project :name))
    (fetch-source)
    (let [src (str protobuf-dir "/java/src/main/java")]
      (protoc (str protobuf-dir "/src/google/protobuf/descriptor.proto") src)               
      (javac {:srcdir  (make-path src)
              :destdir (:compile-path project)})))
  (doseq [file (proto-files "protos")]
    (protoc file))
  (javac {:srcdir    (make-path proto-src)
          :destdir   (:compile-path project)
          :classpath (apply make-path (get-classpath project))}))

