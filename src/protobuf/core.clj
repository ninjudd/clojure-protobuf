(ns protobuf.core
  (:use [clojure.java.io :only [input-stream output-stream]]
        [clojure.string :only [lower-case]])
  (:import (protobuf.core PersistentProtocolBufferMap PersistentProtocolBufferMap$Def Extensions)
           (com.google.protobuf Descriptors$Descriptor Descriptors$FieldDescriptor CodedInputStream)
           (java.io InputStream OutputStream)))

(defn protobuf?
  "Is the given object a PersistentProtocolBufferMap?"
  [obj]
  (instance? PersistentProtocolBufferMap obj))

(defn protodef?
  "Is the given object a PersistentProtocolBufferMap$Def?"
  [obj]
  (instance? PersistentProtocolBufferMap$Def obj))

(defn protodef
  "Create a protodef from a string or protobuf class."
  ([class]
     (if (or (protodef? class) (nil? class))
       class
       (PersistentProtocolBufferMap$Def/create class)))
  ([class & fields]
     (loop [type   (protodef class)
            fields fields]
       (if (empty? fields)
         type
         (recur (-> type
                    (.fieldDescriptor (first fields))
                    .getMessageType
                    protodef)
                (rest fields))))))

(defmacro defprotobuf
  "Helper macro for defining a protodef object."
  [sym & args]
  (let [class (apply str (interpose "$" (map name args)))]
    `(def ~sym (protodef ~class))))

(defn protobuf
  "Construct a protobuf of the given type."
  ([^PersistentProtocolBufferMap$Def type]
     (PersistentProtocolBufferMap/construct type {}))
  ([^PersistentProtocolBufferMap$Def type m]
     (PersistentProtocolBufferMap/construct type m))
  ([^PersistentProtocolBufferMap$Def type k v & kvs]
     (PersistentProtocolBufferMap/construct type (apply array-map k v kvs))))

(defn protodefault
  "Return the default empty protobuf of the given type."
  [type key]
  (let [type ^PersistentProtocolBufferMap$Def (protodef type)]
    (.defaultValue type key)))

(defn protofields
  "Return a map of the protobuf fields to the clojure.protobuf.Extensions metadata for each field."
  [& args]
  (let [type (.getMessageType ^PersistentProtocolBufferMap$Def (apply protodef args))]
    (into {}
          (for [^Descriptors$FieldDescriptor field (.getFields ^Descriptors$Descriptor type)]
            (let [meta-string (.. field getOptions (getExtension (Extensions/meta)))
                  field-name  (PersistentProtocolBufferMap/intern (.getName field))
                  field-type  (keyword (lower-case (.name (.getJavaType field))))]
              [field-name (merge (when (seq meta-string) (read-string meta-string))
                                 {:type field-type}
                                 (when (.isRepeated field)
                                   {:repeated true})
                                 (when (= :enum field-type)
                                   {:values (set (map #(PersistentProtocolBufferMap/enumToKeyword %)
                                                      (.. field getEnumType getValues)))}))])))))

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

(defn protobuf-dump
  "Return the byte representation of the given protobuf."
  [^PersistentProtocolBufferMap p]
  (.toByteArray p))

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

(defn append
  "Merge the given map into the protobuf. Equivalent to appending the byte representations."
  [^PersistentProtocolBufferMap p map]
  (.append p map))

(defn get-raw
  "Get value at key ignoring extension fields."
  [^PersistentProtocolBufferMap p key]
  (.getValAt p key false))
