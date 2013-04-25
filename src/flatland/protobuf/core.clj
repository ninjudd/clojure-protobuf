(ns flatland.protobuf.core
  (:use [flatland.protobuf.schema :only [field-schema]]
        [flatland.useful.fn :only [fix]]
        [clojure.java.io :only [input-stream output-stream]])
  (:require flatland.useful.utils)
  (:import (flatland.protobuf PersistentProtocolBufferMap PersistentProtocolBufferMap$Def PersistentProtocolBufferMap$Def$NamingStrategy Extensions)
           (com.google.protobuf GeneratedMessage CodedInputStream Descriptors$Descriptor)
           (java.io InputStream OutputStream)
           (clojure.lang Reflector)))

(defn protobuf?
  "Is the given object a PersistentProtocolBufferMap?"
  [obj]
  (instance? PersistentProtocolBufferMap obj))

(defn protodef?
  "Is the given object a PersistentProtocolBufferMap$Def?"
  [obj]
  (instance? PersistentProtocolBufferMap$Def obj))

(defn ^PersistentProtocolBufferMap$Def protodef
  "Create a protodef from a string or protobuf class."
  ([def]
     (if (or (protodef? def) (nil? def))
       def
       (protodef def {})))
  ([def opts]
     (when def
       (let [{:keys [^PersistentProtocolBufferMap$Def$NamingStrategy naming-strategy
                     size-limit]
              :or {naming-strategy PersistentProtocolBufferMap$Def/convertUnderscores
                   size-limit 67108864}} opts ;; 64MiB
             ^Descriptors$Descriptor descriptor
             (if (instance? Descriptors$Descriptor def)
               def
               (Reflector/invokeStaticMethod ^Class def "getDescriptor" (to-array nil)))]
         (PersistentProtocolBufferMap$Def/create descriptor naming-strategy size-limit)))))

(defn protobuf
  "Construct a protobuf of the given type."
  ([^PersistentProtocolBufferMap$Def type]
     (PersistentProtocolBufferMap/construct type {}))
  ([^PersistentProtocolBufferMap$Def type m]
     (PersistentProtocolBufferMap/construct type m))
  ([^PersistentProtocolBufferMap$Def type k v & kvs]
     (PersistentProtocolBufferMap/construct type (apply array-map k v kvs))))

(defn protobuf-schema
  "Return the schema for the given protodef."
  [& args]
  (let [^PersistentProtocolBufferMap$Def def (apply protodef args)]
    (field-schema (.getMessageType def) def)))

(defn protobuf-load
  "Load a protobuf of the given type from an array of bytes."
  ([^PersistentProtocolBufferMap$Def type ^bytes data]
     (when data
       (PersistentProtocolBufferMap/create type data)))
  ([^PersistentProtocolBufferMap$Def type ^bytes data ^Integer offset ^Integer length]
     (when data
       (let [^CodedInputStream in (CodedInputStream/newInstance data offset length)]
         (PersistentProtocolBufferMap/parseFrom type in)))))

(defn protobuf-load-stream
  "Load a protobuf of the given type from an InputStream."
  [^PersistentProtocolBufferMap$Def type ^InputStream stream]
  (when stream
    (let [^CodedInputStream in (CodedInputStream/newInstance stream)]
      (PersistentProtocolBufferMap/parseFrom type in))))

(defn ^"[B" protobuf-dump
  "Return the byte representation of the given flatland.protobuf."
  ([^PersistentProtocolBufferMap p]
     (.toByteArray p))
  ([^PersistentProtocolBufferMap$Def type m]
     (protobuf-dump (PersistentProtocolBufferMap/construct type m))))

(defn protobuf-seq
  "Lazily read a sequence of length-delimited protobufs of the specified type from the given input stream."
  [^PersistentProtocolBufferMap$Def type in]
  (lazy-seq
   (io!
    (let [^InputStream in (input-stream in)]
      (if-let [p (PersistentProtocolBufferMap/parseDelimitedFrom type in)]
        (cons p (protobuf-seq type in))
        (.close in))))))

(defn protobuf-write
  "Write the given protobufs to the given output stream, prefixing each with its length to delimit them."
  [out & ps]
  (io!
   (let [^OutputStream out (output-stream out)]
     (doseq [^PersistentProtocolBufferMap p ps]
       (.writeDelimitedTo p out))
     (.flush out))))

(extend-protocol flatland.useful.utils/Adjoin
  PersistentProtocolBufferMap
  (adjoin-onto [^PersistentProtocolBufferMap this other]
    (.append this other)))

;; TODO make this nil-safe? Or just delete it?
(defn get-raw
  "Get value at key ignoring extension fields."
  [^PersistentProtocolBufferMap p key]
  (.getValAt p key false))
