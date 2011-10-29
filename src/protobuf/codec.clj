(ns protobuf.codec
  (:use protobuf.core
        [gloss.core.protocols :only [Reader Writer]]
        [gloss.core.formats :only [to-buf-seq]]
        [clojure.java.io :only [input-stream]])
  (:require io.core))

(defn protobuf-codec [proto & {:keys [validator]}]
  (let [proto (protodef proto)]
    (reify
      Reader
      (read-bytes [this buf-seq]
        [true (protobuf-load-stream proto (input-stream buf-seq)) nil])

      Writer
      (sizeof [this] nil)
      (write-bytes [this _ val]
        (when (and validator (not (validator val)))
          (throw (IllegalStateException. "Invalid value in protobuf-codec")))
        (to-buf-seq
         (protobuf-dump
          (if (protobuf? val)
            val
            (protobuf proto val))))))))
