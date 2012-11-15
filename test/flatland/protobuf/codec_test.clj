(ns flatland.protobuf.codec-test
  (:use clojure.test gloss.io flatland.protobuf.codec)
  (:import (java.nio ByteBuffer)))

(deftest protobuf-codec-test
  (let [codec (protobuf-codec flatland.protobuf.test.Codec$Foo)]
    (testing "decode an encoded data structure"
      (let [val {:foo 1 :bar 2}]
        (is (= val (decode codec (encode codec val))))))

    (testing "append two simple encoded data structures"
      (let [data1 (encode codec {:foo 1 :bar 2})
            data2 (encode codec {:foo 4 :baz 8})]
        (is (= {:foo 4 :bar 2 :baz 8}
               (decode codec (concat data1 data2))))))

    (testing "concat lists when appending"
      (let [data1 (encode codec {:tags ["foo" "bar"] :foo 1})
            data2 (encode codec {:tags ["baz" "foo"] :foo 2})]
        (is (= {:foo 2 :tags ["foo" "bar" "baz" "foo"]}
               (decode codec (concat data1 data2))))))

    (testing "merge maps when appending"
      (let [data1 (encode codec {:num-map {1 "one" 3 "three"}})
            data2 (encode codec {:num-map {2 "dos" 3 "tres"}})
            data3 (encode codec {:num-map {3 "san" 6 "roku"}})]
        (is (= {:num-map {1 "one" 2 "dos" 3 "san" 6 "roku"}}
               (decode codec (concat data1 data2 data3))))))

    (testing "merge sets when appending"
      (let [data1 (encode codec {:tag-set #{"foo" "bar"}})
            data2 (encode codec {:tag-set #{"baz" "foo"}})]
        (is (= {:tag-set #{"foo" "bar" "baz"}}
               (decode codec (concat data1 data2))))))

    (testing "support set deletion using existence map"
      (let [data1 (encode codec {:tag-set #{"foo" "bar" "baz"}})
            data2 (encode codec {:tag-set {"baz" false "foo" true "zap" true "bam" false}})]
        (is (= {:tag-set #{"foo" "bar" "zap"}}
               (decode codec (concat data1 data2))))))

    (testing "merge and append nested data structures when appending"
      (let [data1 (encode codec {:nested {:foo 1 :tags ["bar"] :nested {:tag-set #{"a" "c"}}}})
            data2 (encode codec {:nested {:foo 4 :tags ["baz"] :bar 3}})
            data3 (encode codec {:nested {:baz 5 :tags ["foo"] :nested {:tag-set {"b" true "c" false}}}})]
        (is (= {:nested {:foo 4 :bar 3 :baz 5 :tags ["bar" "baz" "foo"] :nested {:tag-set #{"a" "b"}}}}
               (decode codec (concat data1 data2 data3))))))))

(deftest repeated-protobufs
  (let [len   (length-prefix flatland.protobuf.test.Codec$Foo)
        codec (protobuf-codec flatland.protobuf.test.Codec$Foo :repeated true)]
    (testing "length-prefix"
      (doseq [i [0 10 100 1000 10000 100000 Integer/MAX_VALUE]]
        (is (= i (decode len (encode len i))))))
    (testing "repeated"
      (let [data1 (encode codec [{:foo 1 :bar 2}])
            data2 (encode codec [{:foo 4 :baz 8}])]
        (is (= [{:foo 1 :bar 2} {:foo 4 :baz 8}]
               (decode codec (concat data1 data2))))))))