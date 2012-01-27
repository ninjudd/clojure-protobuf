(ns protobuf.example-test
  (:use protobuf.core clojure.test))

(def Photo (protodef protobuf.test.Example$Photo))
(def Tag   (protodef protobuf.test.Example$Photo$Tag))

;; Haven't seen correct output so I have nothing to compare to.
(deftest example-test
  (is (protobuf Photo :id 7  :path "/photos/h2k3j4h9h23" :labels #{"hawaii" "family" "surfing"}
                      :attrs {"dimensions" "1632x1224", "alpha" "no", "color space" "RGB"}
                      :tags  {4 {:person_id 4, :x_coord 607, :y_coord 813, :width 25, :height 27}})))
