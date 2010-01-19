(ns protobuf)

(defmacro defprotobuf [sym & args]
  (let [class (symbol (apply str (interpose "$" (map name args))))]
    `(def ~sym (. ~class  getDescriptor))))

(defn protobuf [desc & args]
  (if (= (count args) 1)
    (clojure.protobuf.PersistentProtocolBufferMap/create desc (first args))
    (clojure.protobuf.PersistentProtocolBufferMap/construct desc (apply hash-map args))))