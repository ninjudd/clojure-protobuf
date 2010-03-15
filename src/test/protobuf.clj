(ns test.protobuf
  (:use protobuf)
  (:use clojure.test))

(defprotobuf Foo test.protobuf.Proto Foo)
(defprotobuf Bar test.protobuf.Proto Bar)

(deftest protobuf-methods
  (testing "conj"
    (let [p (protobuf Foo :num 5 :tags ["little" "yellow"])]
      (let [p (conj p {:info "bar"})]
        (is (= 5     (:num p)))
        (is (= "bar" (:info p)))
        (is (= ["little" "yellow"] (:tags p))))
      (let [p (conj p {:tags ["different"]})]
        (is (= ["little" "yellow" "different"] (:tags p))))
      (let [p (conj p {:tags ["different"] :info "very"})]
        (is (= ["little" "yellow" "different"] (:tags p)))
        (is (= "very" (:info p))))
      ))
  (testing "assoc"
    (let [p (protobuf Foo :num 5 :tags ["little" "yellow"])]
      (let [p (assoc p :info "baz" :tags ["nuprin"])]
        (is (= ["nuprin"] (:tags p)))
        (is (= "baz"      (:info p))))
      (let [p (assoc p "responses" [:yes :no :maybe :no "yes"])]
        (is (= [:yes :no :maybe :no :yes] (:responses p))))
      (let [p (assoc p :tags "aspirin")]
        (is (= ["aspirin"] (:tags p))))
      ))
  (testing "dissoc"
    (let [p (protobuf Foo :num 5 :tags ["fast" "shiny"])]
      (let [p (dissoc p :info :tags)]
        (is (= [] (:tags p)))
        (is (= "" (:info p))))
      ))
  (testing "contains?"
    (let [p (protobuf Foo :num 5 :tags ["little" "yellow"])]
      (is (contains? p :num))
      (is (contains? p :tags))
      (is (contains? p {:num 5}))
      (is (contains? p {:tags "yellow"}))
      (is (not (contains? p {:info "nuprin"})))
      (is (not (contains? p :sdsfsdfd)))
      (is (not (contains? p {:num 6})))
      (is (not (contains? p {:tags "big"})))
      ))

  )
