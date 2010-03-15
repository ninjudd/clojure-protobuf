(use 'clojure.test)

(def test-names [:protobuf])

(def test-namespaces
     (map #(symbol (str "test." (name %)))
          test-names))

(defn run []
  (println "Loading tests...")
  (apply require :reload-all test-namespaces)
  (apply run-tests test-namespaces))

(run)