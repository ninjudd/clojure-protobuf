(ns tasks
  (:use [cake.core :only [deftask]]
        [cake.file :only [file cp mkdir]]
        [cake.tasks.protobuf :only [protoc srcdir]]))

(defn build-protobuf []
  (protoc ["protobuf/core/extensions.proto" "clojure/protobuf/test.proto"]))

(deftask protobuf.google #{protobuf.fetch}
  (mkdir "proto/google/protobuf")
  (cp (str srcdir "/src/google/protobuf/descriptor.proto") "proto/google/protobuf")
  (protoc ["google/protobuf/descriptor.proto"] (str srcdir "/java/src/main/java")))

(deftask proto #{protobuf.google})