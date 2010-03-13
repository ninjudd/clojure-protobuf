(ns protobuf)

(defn protobuf? [obj]
  (instance? clojure.protobuf.PersistentProtocolBufferMap obj))

(defn protodef? [obj]
  (instance? clojure.protobuf.PersistentProtocolBufferMap$Def obj))

(defn protodef [class]
  (if (or (protodef? class) (nil? class))
    class
    (clojure.protobuf.PersistentProtocolBufferMap$Def/create class)))

(defmacro defprotobuf [sym & args]
  (let [class (apply str (interpose "$" (map name args)))]
    `(def ~sym (protodef ~class))))

(defn protobuf
  ([#^clojure.protobuf.PersistentProtocolBufferMap$Def type]
     (clojure.protobuf.PersistentProtocolBufferMap/construct type {}))
  ([#^clojure.protobuf.PersistentProtocolBufferMap$Def type m]
     (clojure.protobuf.PersistentProtocolBufferMap/construct type m))
  ([#^clojure.protobuf.PersistentProtocolBufferMap$Def type k v & kvs]
     (clojure.protobuf.PersistentProtocolBufferMap/construct type (apply array-map k v kvs))))

(defn protobuf-load
  ([#^clojure.protobuf.PersistentProtocolBufferMap$Def type #^bytes data]
     (if data (clojure.protobuf.PersistentProtocolBufferMap/create type data)))
  ([#^clojure.protobuf.PersistentProtocolBufferMap$Def type #^bytes data #^Integer length]
     (if data (clojure.protobuf.PersistentProtocolBufferMap/create type data length))))

(defn protobuf-dump [#^clojure.protobuf.PersistentProtocolBufferMap p]
  (.toByteArray p))