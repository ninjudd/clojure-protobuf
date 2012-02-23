(ns protobuf.schema
  (:import (protobuf.core PersistentProtocolBufferMap PersistentProtocolBufferMap$Def Extensions)
           (com.google.protobuf Descriptors$Descriptor
                                Descriptors$FieldDescriptor
                                Descriptors$FieldDescriptor$Type)))

(defn extension [ext field]
  (-> field .getOptions (.getExtension ext)))

(defn field-type [field]
  (condp instance? field
    Descriptors$FieldDescriptor
    (if (.isRepeated field)
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

(defn struct-schema [struct & [parents]]
  (let [struct-name (.getFullName struct)]
    (into {:type :struct
           :name struct-name}
          (when (not-any? (partial = struct-name) parents)
            {:fields (into {}
                           (for [field (.getFields struct)]
                             [(PersistentProtocolBufferMap/intern (.getName field))
                              (field-schema field (conj parents struct-name))]))}))))

(defn basic-schema [field & [parents]]
  (let [java-type   (keyword (lower-case (.name (.getJavaType field))))
        meta-string (extension (Extensions/meta) field)]
    (into (case java-type
            :message (struct-schema (.getMessageType field) parents)
            :enum    {:type   :enum
                      :values (set (map #(PersistentProtocolBufferMap/enumToKeyword %)
                                        (.. field getEnumType getValues)))}
            {:type java-type})
          (when meta-string
            (read-string meta-string)))))

(defn subfield [field field-name]
  (.getFieldByName (.getMessageType field) (name field-name)))

(defmulti field-schema (fn [field & _] (field-type field)))

(defmethod field-schema :basic [field & [parents]]
  (basic-schema field parents))

(defmethod field-schema :list [field & [parents]]
  {:type   :list
   :values (basic-schema field parents)})

(defmethod field-schema :succession [field & [parents]]
  (assoc (basic-schema field parents)
    :succession true))

(defmethod field-schema :counter [field & [parents]]
  (assoc (basic-schema field parents)
    :counter true))

(defmethod field-schema :set [field & [parents]]
  {:type   :set
   :values (field-schema (subfield field :item) parents)})

(defmethod field-schema :map [field & [parents]]
  {:type   :map
   :keys   (field-schema (subfield field :key) parents)
   :values (field-schema (subfield field :val) parents)})

(defmethod field-schema :map-by [field & [parents]]
  (let [map-by (extension (Extensions/mapBy) field)]
    {:type   :map-by
     :keys   (field-schema (subfield field map-by) parents)
     :values (basic-schema field parents)}))

(defmethod field-schema :struct [field & [parents]]
  (struct-schema field parents))
