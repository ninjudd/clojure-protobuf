(ns protobuf)

(set! *warn-on-reflection* true)

(defmacro defprotobuf [sym & args]
  (let [class (apply str (interpose "$" (map name args)))]
    `(def ~sym (clojure.protobuf.PersistentProtocolBufferMap$Def/create ~class))))

(defn protobuf 
  ([#^clojure.protobuf.PersistentProtocolBufferMap$Def type #^bytes data]
     (clojure.protobuf.PersistentProtocolBufferMap/create type data))
  ([#^clojure.protobuf.PersistentProtocolBufferMap$Def type k v & kvs]
     (clojure.protobuf.PersistentProtocolBufferMap/construct type (apply hash-map k v kvs))))

(defn dump-protobuf [#^clojure.protobuf.PersistentProtocolBufferMap p]
  (.toByteArray p))