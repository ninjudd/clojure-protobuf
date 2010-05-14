(ns leiningen.install-protoc
  (:use clojure.contrib.shell-out
        clojure.contrib.str-utils
        lancet))

(def proto-version "protobuf-2.3.0")
(def build-dir     "lib/build/")
(def protobuf-dir  (str build-dir proto-version))

(defn shell [& args]
  (let [return (apply sh :return-map true args)]
    (if (= 0 (return :exit))
      (return :out)
      (throw (Exception. (str "command failed: " (str-join " " args) "\n" (return :err)))))))

(defn exists? [file]
  (println file)
  (.exists (java.io.File. file)))

(deftarget fetch-source "fetch google protocol buffer source"
  (let [url      (java.net.URL. (str "http://protobuf.googlecode.com/files/" proto-version ".tar.gz"))
        tarfile  (str protobuf-dir ".tar.gz")
        gzip     (doto (org.apache.tools.ant.taskdefs.Untar$UntarCompressionMethod.) (.setValue "gzip"))]
    (when-not (exists? protobuf-dir)
      (mkdir {:dir build-dir})
      (ant-get {:src url :dest tarfile})
      (untar   {:src tarfile :dest build-dir :compression gzip}))))

(deftarget install "install google protocol buffers"
  (when (empty? (sh "which" "protoc"))
    (fetch-source)
    (with-sh-dir protobuf-dir
      (when-not (exists? (str protobuf-dir "/src/protoc"))
        (println "building google protobuf compiler...")
        (chmod   {:file (str protobuf-dir "/configure")  :perm "+x"})
        (chmod   {:file (str protobuf-dir "/install-sh") :perm "+x"})
        (shell "./configure")
        (shell "make"))
      (println "installing google protobuf compiler...")
      (shell "sudo" "make" "install"))))

(defn install-protoc [project]
  (install))