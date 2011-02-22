(ns protobuf
  (:use [clojure.java.io :only [input-stream output-stream]])
  (:import (clojure.protobuf PersistentProtocolBufferMap PersistentProtocolBufferMap$Def Extensions)
           (com.google.protobuf Descriptors$Descriptor Descriptors$FieldDescriptor CodedInputStream)
           (java.io InputStream OutputStream)))

(defn protobuf? [obj]
  (instance? PersistentProtocolBufferMap obj))

(defn protodef? [obj]
  (instance? PersistentProtocolBufferMap$Def obj))

(defn protodef [class]
  (if (or (protodef? class) (nil? class))
    class
    (PersistentProtocolBufferMap$Def/create class)))

(defmacro defprotobuf [sym & args]
  (let [class (apply str (interpose "$" (map name args)))]
    `(def ~sym (protodef ~class))))

(defn protobuf
  ([^PersistentProtocolBufferMap$Def type]
     (PersistentProtocolBufferMap/construct type {}))
  ([^PersistentProtocolBufferMap$Def type m]
     (PersistentProtocolBufferMap/construct type m))
  ([^PersistentProtocolBufferMap$Def type k v & kvs]
     (PersistentProtocolBufferMap/construct type (apply array-map k v kvs))))

(defn protodefault [type key]
  (let [type ^PersistentProtocolBufferMap$Def (protodef type)]
    (.defaultValue type key)))

(defn protofields [type]
  (let [type ^PersistentProtocolBufferMap$Def (protodef type)
        type ^Descriptors$Descriptor (.getMessageType type)]
    (into {}
      (for [^Descriptors$FieldDescriptor field (.getFields type)]
        (let [meta-string (.. field getOptions (getExtension (Extensions/meta)))
              field-name  (keyword (PersistentProtocolBufferMap/normalize (.getName field)))]
          [field-name (when-not (empty? meta-string) (read-string meta-string))])))))

(defn protobuf-load
  ([^PersistentProtocolBufferMap$Def type ^bytes data]
     (when data
       (PersistentProtocolBufferMap/create type data)))
  ([^PersistentProtocolBufferMap$Def type ^bytes data ^Integer offset ^Integer length]
     (when data
       (let [^CodedInputStream in (CodedInputStream/newInstance data offset length)]
         (PersistentProtocolBufferMap/parseFrom type in)))))


(defn protobuf-dump [^PersistentProtocolBufferMap p]
  (.toByteArray p))

(defn protobuf-seq [^PersistentProtocolBufferMap$Def type in]
  (lazy-seq
   (io!
    (let [^InputStream in (input-stream in)]
      (when-let [p (PersistentProtocolBufferMap/parseDelimitedFrom type in)]
        (cons p (protobuf-seq type in)))))))

(defn protobuf-write [out & ps]
  (io!
   (let [^OutputStream out (output-stream out)]
     (doseq [^PersistentProtocolBufferMap p ps]
       (.writeDelimitedTo p out))
     (.flush out))))

(defn append [^PersistentProtocolBufferMap p map]
  (.append p map))