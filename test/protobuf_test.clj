(ns protobuf-test
  (:use protobuf)
  (:use clojure.test))

(defprotobuf Foo clojure.protobuf.Test Foo)

(defn catbytes [& args]
  (.getBytes (apply str (map (fn [#^bytes b] (String. b)) args))))

(deftest protobuf-simple
  (testing "conj"
    (let [p (protobuf Foo :id 5 :tags ["little" "yellow"])]
      (let [p (conj p {:label "bar"})]
        (is (= 5     (:id p)))
        (is (= "bar" (:label p)))
        (is (= ["little" "yellow"] (:tags p))))
      (let [p (conj p {:tags ["different"]})]
        (is (= ["little" "yellow" "different"] (:tags p))))
      (let [p (conj p {:tags ["different"] :label "very"})]
        (is (= ["little" "yellow" "different"] (:tags p)))
        (is (= "very" (:label p))))
      ))
  (testing "assoc"
    (let [p (protobuf Foo :id 5 :tags ["little" "yellow"])]
      (let [p (assoc p :label "baz" :tags ["nuprin"])]
        (is (= ["nuprin"] (:tags p)))
        (is (= "baz"      (:label p))))
      (let [p (assoc p "responses" [:yes :no :maybe :no "yes"])]
        (is (= [:yes :no :maybe :no :yes] (:responses p))))
      (let [p (assoc p :tags "aspirin")]
        (is (= ["aspirin"] (:tags p))))
      ))
  (testing "dissoc"
    (let [p (protobuf Foo :id 5 :tags ["fast" "shiny"])]
      (let [p (dissoc p :label :tags)]
        (is (= [] (:tags p)))
        (is (= "" (:label p))))
      ))
  (testing "string keys"
    (let [p (protobuf Foo "id" 5 "label" "rad")]
      (is (= 5 (p :id)))
      (is (= 5 (p "id")))
      (is (= "rad" (p :label)))
      (is (= "rad" (p "label")))
      (let [p (conj p {"tags" ["check" "it" "out"]})]
        (is (= ["check" "it" "out"] (p :tags)))
        (is (= ["check" "it" "out"] (p "tags"))))
      ))
  (testing "append"
    (let [p (protobuf Foo :id 5  :label "rad" :tags ["sweet"] :tag-set #{"foo" "bar" "baz"})
          q (protobuf Foo :id 43 :tags ["savory"] :tag-set {"bar" false "foo" false "bap" true})
          r (protobuf Foo :label "bad")
          s (protobuf-load Foo (catbytes (protobuf-dump p) (protobuf-dump q)))
          t (protobuf-load Foo (catbytes (protobuf-dump p) (protobuf-dump r)))]
      (is (= 43 (s :id))) ; make sure an explicit default overwrites on append
      (is (= 5  (t :id))) ; make sure a missing default doesn't overwrite on append
      (is (= "rad" (s :label)))
      (is (= "bad" (t :label)))
      (is (= ["sweet"] (t :tags)))
      (is (= ["sweet" "savory"] (s :tags)))
      (is (= #{"foo" "bar" "baz"} (p :tag-set)))
      (is (= #{"bap" "baz"} (s :tag-set)))
    ))
  (testing "protofields"
    (let [fields {:id nil, :label {:a 1, :b 2, :c 3}, :tags nil, :parent nil, :responses nil,
                  :tag_set nil, :attr_map nil, :foo_by_id nil, :groups nil}]
      (is (= fields (protofields Foo)))
      (is (= fields (protofields clojure.protobuf.Test$Foo)))))
  (testing "protodefault"
    (is (= 43  (protodefault Foo :id)))
    (is (= ""  (protodefault Foo :label)))
    (is (= []  (protodefault Foo :tags)))
    (is (= nil (protodefault Foo :parent)))
    (is (= []  (protodefault Foo :responses)))
    (is (= #{} (protodefault Foo :tag_set)))
    (is (= {}  (protodefault Foo :foo_by_id)))
    (is (= {}  (protodefault Foo :groups)))
    (is (= {}  (protodefault clojure.protobuf.Test$Foo :groups)))
  ))

(deftest protobuf-extended
  (testing "create"
    (let [p (protobuf Foo :id 5 :tag-set #{"little" "yellow"} :attr-map {"size" "little", "color" "yellow", "style" "different"})]
      (is (= #{"little" "yellow"} (:tag-set p)))
      (is (associative? (:attr-map p)))
      (is (= "different" (get-in p [:attr-map "style"])))
      (is (= "little"    (get-in p [:attr-map "size" ])))
      (is (= "yellow"    (get-in p [:attr-map "color"])))
      )
    (let [p (protobuf Foo :id 1 :foo-by-id {5 {:label "five"}, 6 {:label "six"}})]
      (let [five ((p :foo-by-id) 5)
            six  ((p :foo-by-id) 6)]
        (is (= 5      (five :id)))
        (is (= "five" (five :label)))
        (is (= 6      (six  :id)))
        (is (= "six"  (six  :label))))
      ))
  (testing "conj"
    (let [p (protobuf Foo :id 1 :foo-by-id {5 {:label "five", :tag-set ["odd"]}, 6 {:label "six" :tags ["even"]}})]
      (let [p (conj p {:foo-by-id {5 {:tag-set ["prime" "odd"]} 6 {:tags ["even"]}}})]
        (is (= #{"prime" "odd"} (get-in p [:foo-by-id 5 :tag-set])))
        (is (= ["even" "even"]  (get-in p [:foo-by-id 6 :tags]))))
      )))

