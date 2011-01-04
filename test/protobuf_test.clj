(ns protobuf-test
  (:use protobuf)
  (:use clojure.test))

(defprotobuf Foo clojure.protobuf.Test Foo)
(defprotobuf Response clojure.protobuf.Test Response)
(defprotobuf ErrorMsg clojure.protobuf.Test ErrorMsg)

(defn catbytes [& args]
  (.getBytes (apply str (map (fn [#^bytes b] (String. b)) args))))

(deftest protobuf-simple

  (testing "conj"
    (let [p (protobuf Foo :id 5 :tags ["little" "yellow"] :doubles [1.2 3.4 5.6] :floats [0.01 0.02 0.03])]
      (let [p (conj p {:label "bar"})]
        (is (= 5     (:id p)))
        (is (= "bar" (:label p)))
        (is (= ["little" "yellow"] (:tags p)))
        (is (= [1.2 3.4 5.6] (:doubles p)))
        (is (= [(float 0.01) (float 0.02) (float 0.03)] (:floats  p)))
        )
      (let [p (conj p {:tags ["different"]})]
        (is (= ["different"] (:tags p))))
      (let [p (conj p {:tags ["little" "yellow" "different"] :label "very"})]
        (is (= ["little" "yellow" "different"] (:tags p)))
        (is (= "very" (:label p))))))

  (testing "append"
    (let [p (protobuf Foo :id 5 :tags ["little" "yellow"] :doubles [1.2] :floats [0.01])]
      (let [p (append p {:label "bar"})]
        (is (= 5     (:id p)))
        (is (= "bar" (:label p)))
        (is (= ["little" "yellow"] (:tags p))))
      (let [p (append p {:tags ["different"]})]
        (is (= ["little" "yellow" "different"] (:tags p))))
      (let [p (append p {:tags ["different"] :label "very"})]
        (is (= ["little" "yellow" "different"] (:tags p)))
        (is (= "very" (:label p))))
      (let [p (append p {:doubles [3.4] :floats [0.02]})]
        (is (= [1.2 3.4] (:doubles p)))
        (is (= [(float 0.01) (float 0.02)] (:floats  p))))))

  (testing "assoc"
    (let [p (protobuf Foo :id 5 :tags ["little" "yellow"] :foo-by-id {1 {:label "one"} 2 {:label "two"}})]
      (let [p (assoc p :label "baz" :tags ["nuprin"])]
        (is (= ["nuprin"] (:tags p)))
        (is (= "baz"      (:label p))))
      (let [p (assoc p "responses" [:yes :no :maybe :no "yes"])]
        (is (= [:yes :no :maybe :no :yes] (:responses p))))
      (let [p (assoc p :tags "aspirin")]
        (is (= ["aspirin"] (:tags p))))
      (let [p (assoc p :foo-by-id {3 {:label "three"} 2 {:label "two"}})]
        (is (= {3 {:id 3, :label "three"} 2 {:id 2, :label "two"}} (:foo-by-id p))))))

  (testing "dissoc"
    (let [p (protobuf Foo :id 5 :tags ["fast" "shiny"] :label "nice")]
      (let [p (dissoc p :label :tags)]
        (is (= []  (:tags p)))
        (is (= nil (:label p))))))

  (testing "string keys"
    (let [p (protobuf Foo "id" 5 "label" "rad")]
      (is (= 5 (p :id)))
      (is (= 5 (p "id")))
      (is (= "rad" (p :label)))
      (is (= "rad" (p "label")))
      (let [p (conj p {"tags" ["check" "it" "out"]})]
        (is (= ["check" "it" "out"] (p :tags)))
        (is (= ["check" "it" "out"] (p "tags"))))))

  (testing "append bytes"
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
      (is (= #{"bap" "baz"} (s :tag-set)))))

  (testing "coercing doubles and floats"
    (let [p (protobuf Foo :lat 5 :long 6)]
      (is (= 5.0 (p :lat)))
      (is (= 6.0 (p :long))))
    (let [p (protobuf Foo :lat (float 5.0) :long (double 6.0))]
      (is (= 5.0 (p :lat)))
      (is (= 6.0 (p :long))))
    ))

(deftest protobuf-extended

  (testing "create"
    (let [p (protobuf Foo :id 5 :tag-set #{"little" "yellow"} :attr-map {"size" "little", "color" "yellow", "style" "different"})]
      (is (= #{"little" "yellow"} (:tag-set p)))
      (is (associative? (:attr-map p)))
      (is (= "different" (get-in p [:attr-map "style"])))
      (is (= "little"    (get-in p [:attr-map "size" ])))
      (is (= "yellow"    (get-in p [:attr-map "color"]))))
    (let [p (protobuf Foo :id 1 :foo-by-id {5 {:label "five"}, 6 {:label "six"}})]
      (let [five ((p :foo-by-id) 5)
            six  ((p :foo-by-id) 6)]
        (is (= 5      (five :id)))
        (is (= "five" (five :label)))
        (is (= 6      (six  :id)))
        (is (= "six"  (six  :label))))))

  (testing "map-by with required field"
    (let [p (protobuf Foo :id 1 :item-map {"foo" {:exists true} "bar" {:exists false}})]
      (is (= "foo" (get-in p [:item-map "foo" :item])))
      (is (= "bar" (get-in p [:item-map "bar" :item])))))

  (testing "conj"
    (let [p (protobuf Foo :id 1 :foo-by-id {5 {:label "five", :tag-set ["odd"]}, 6 {:label "six" :tags ["even"]}})]
      (let [p (conj p {:foo-by-id {5 {:tag-set ["prime" "odd"]} 6 {:tags ["odd"]}}})]
        (is (= #{"prime" "odd"} (get-in p [:foo-by-id 5 :tag-set])))
        (is (= ["odd"]          (get-in p [:foo-by-id 6 :tags])))
        (is (= nil              (get-in p [:foo-by-id 6 :label])))))))

(deftest protofields-and-defaults

  (testing "protofields"
    (let [fields {:id nil, :label {:a 1, :b 2, :c 3}, :tags nil, :parent nil, :responses nil, :tag-set nil,
                  :attr-map nil, :foo-by-id nil, :groups nil, :doubles nil, :floats nil, :item-map nil, :lat nil, :long nil}]
      (is (= fields (protofields Foo)))
      (is (= fields (protofields clojure.protobuf.Test$Foo)))))

  (testing "protodefault"
    (is (= 43  (protodefault Foo :id)))
    (is (= 0.0 (protodefault Foo :lat)))
    (is (= 0.0 (protodefault Foo :long)))
    (is (= ""  (protodefault Foo :label)))
    (is (= []  (protodefault Foo :tags)))
    (is (= nil (protodefault Foo :parent)))
    (is (= []  (protodefault Foo :responses)))
    (is (= #{} (protodefault Foo :tag-set)))
    (is (= {}  (protodefault Foo :foo-by-id)))
    (is (= {}  (protodefault Foo :groups)))
    (is (= {}  (protodefault Foo :item-map)))
    (is (= {}  (protodefault clojure.protobuf.Test$Foo :groups)))))

(deftest protobuf-nested-message
  (let [p (protobuf Response :ok false :error (protobuf ErrorMsg :code -10 :data "abc"))]))

(deftest protobuf-nil-field
  (let [p (protobuf Response :ok true :error (protobuf ErrorMsg :code -10 :data nil))]))
