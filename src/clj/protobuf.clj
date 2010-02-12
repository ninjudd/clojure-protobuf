(ns protobuf)

(defn protodef [class]
  (clojure.protobuf.PersistentProtocolBufferMap$Def/create class))

(defmacro defprotobuf [sym & args]
  (let [class (apply str (interpose "$" (map name args)))]
    `(def ~sym (protodef ~class))))

(defn protobuf? [obj]
  (instance? clojure.protobuf.PersistentProtocolBufferMap obj))

(defn protodef? [obj]
  (instance? clojure.protobuf.PersistentProtocolBufferMap$Def obj))

(defn protobuf
  ([#^clojure.protobuf.PersistentProtocolBufferMap$Def type]
     (clojure.protobuf.PersistentProtocolBufferMap/construct type {}))
  ([#^clojure.protobuf.PersistentProtocolBufferMap$Def type #^bytes data]
     (if data (clojure.protobuf.PersistentProtocolBufferMap/create type data)))
  ([#^clojure.protobuf.PersistentProtocolBufferMap$Def type k v & kvs]
     (clojure.protobuf.PersistentProtocolBufferMap/construct type (apply hash-map k v kvs))))

(defn protobuf-bytes [#^clojure.protobuf.PersistentProtocolBufferMap p]
  (.toByteArray p))