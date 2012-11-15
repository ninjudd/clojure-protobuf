(ns flatland.protobuf.schema
  (:use [flatland.useful.fn :only [fix]]
        [clojure.string :only [lower-case]])
  (:import (flatland.protobuf PersistentProtocolBufferMap
                              PersistentProtocolBufferMap$Def Extensions)
           (com.google.protobuf Descriptors$Descriptor
                                Descriptors$FieldDescriptor
                                Descriptors$FieldDescriptor$Type)))

(defn extension [ext ^Descriptors$FieldDescriptor field]
  (-> (.getOptions field)
      (.getExtension ext)
      (fix string? not-empty)))

(defn field-type [field]
  (condp instance? field
    Descriptors$FieldDescriptor
    (if (.isRepeated ^Descriptors$FieldDescriptor field)
      (condp extension field
        (Extensions/counter)    :counter
        (Extensions/succession) :succession
        (Extensions/map)        :map
        (Extensions/mapBy)      :map-by
        (Extensions/set)        :set
        :list)
      :basic)
    Descriptors$Descriptor
    :struct))

(defmulti field-schema (fn [field def & _] (field-type field)))

(defn struct-schema [^Descriptors$Descriptor          struct
                     ^PersistentProtocolBufferMap$Def def
                     & [parents]]
  (let [struct-name (.getFullName struct)]
    (into {:type :struct
           :name struct-name}
          (when (not-any? (partial = struct-name) parents)
            {:fields (into {}
                           (for [^Descriptors$FieldDescriptor field (.getFields struct)]
                             [(.intern def (.getName field))
                              (field-schema field def (conj parents struct-name))]))}))))

(defn basic-schema [^Descriptors$FieldDescriptor     field
                    ^PersistentProtocolBufferMap$Def def
                     & [parents]]
  (let [java-type   (keyword (lower-case (.name (.getJavaType field))))
        meta-string (extension (Extensions/meta) field)]
    (merge (case java-type
             :message (struct-schema (.getMessageType field) def parents)
             :enum    {:type   :enum
                       :values (set (map #(.clojureEnumValue def %)
                                         (.. field getEnumType getValues)))}
             {:type java-type})
           (when (.hasDefaultValue field)
             {:default (.getDefaultValue field)})
           (when meta-string
             (read-string meta-string)))))

(defn subfield [^Descriptors$FieldDescriptor field field-name]
  (.findFieldByName (.getMessageType field) (name field-name)))

(defmethod field-schema :basic [field def & [parents]]
  (basic-schema field def parents))

(defmethod field-schema :list [field def & [parents]]
  {:type   :list
   :values (basic-schema field def parents)})

(defmethod field-schema :succession [field def & [parents]]
  (assoc (basic-schema field def parents)
    :succession true))

(defmethod field-schema :counter [field def & [parents]]
  (assoc (basic-schema field def parents)
    :counter true))

(defmethod field-schema :set [field def & [parents]]
  {:type   :set
   :values (field-schema (subfield field :item) def parents)})

(defmethod field-schema :map [field def & [parents]]
  {:type   :map
   :keys   (field-schema (subfield field :key) def parents)
   :values (field-schema (subfield field :val) def parents)})

(defmethod field-schema :map-by [field def & [parents]]
  (let [map-by (extension (Extensions/mapBy) field)]
    {:type   :map
     :keys   (field-schema (subfield field map-by) def parents)
     :values (basic-schema field def parents)}))

(defmethod field-schema :struct [field def & [parents]]
  (struct-schema field def parents))
