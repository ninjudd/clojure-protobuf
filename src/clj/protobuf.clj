(ns protobuf
  (:import (clojure.protobuf PersistentProtocolBufferMap PersistentProtocolBufferMap$Def Extensions)
           (com.google.protobuf Descriptors$Descriptor Descriptors$FieldDescriptor)))

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
  ([#^PersistentProtocolBufferMap$Def type]
     (PersistentProtocolBufferMap/construct type {}))
  ([#^PersistentProtocolBufferMap$Def type m]
     (PersistentProtocolBufferMap/construct type m))
  ([#^PersistentProtocolBufferMap$Def type k v & kvs]
     (PersistentProtocolBufferMap/construct type (apply array-map k v kvs))))

(defn protodefault [type key]
  (let [type #^PersistentProtocolBufferMap$Def (protodef type)]
    (.defaultValue type key)))

(defn protofields [type]
  (let [type #^PersistentProtocolBufferMap$Def (protodef type)
        type #^Descriptors$Descriptor (.getMessageType type)]
    (into {}
      (for [#^Descriptors$FieldDescriptor field (.getFields type)]
        (let [meta-string (.. field getOptions (getExtension (Extensions/meta)))
              field-name  (keyword (.replaceAll (.getName field) "_" "-"))]
          [field-name (when-not (empty? meta-string) (read-string meta-string))])))))

(defn protobuf-load
  ([#^PersistentProtocolBufferMap$Def type #^bytes data]
     (if data (PersistentProtocolBufferMap/create type data)))
  ([#^PersistentProtocolBufferMap$Def type #^bytes data #^Integer offset #^Integer length]
     (if data (PersistentProtocolBufferMap/create type data offset length))))

(defn protobuf-dump [#^PersistentProtocolBufferMap p]
  (.toByteArray p))

(defn append [#^PersistentProtocolBufferMap p map]
  (.append p map))