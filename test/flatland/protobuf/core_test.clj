(ns flatland.protobuf.core-test
  (:use flatland.protobuf.core clojure.test
        [flatland.io.core :only [catbytes]]
        [flatland.useful.utils :only [adjoin]]
        ordered-map.core)
  (:import (java.io PipedInputStream PipedOutputStream)))

(def Foo      (protodef flatland.protobuf.test.Core$Foo))
(def FooUnder (protodef flatland.protobuf.test.Core$Foo
                        {:naming-strategy flatland.protobuf.PersistentProtocolBufferMap$Def/protobufNames}))
(def Bar      (protodef flatland.protobuf.test.Core$Bar))
(def Response (protodef flatland.protobuf.test.Core$Response))
(def ErrorMsg (protodef flatland.protobuf.test.Core$ErrorMsg))
(def Maps     (protodef flatland.protobuf.test.Maps$Struct))

(deftest test-conj
  (let [p (protobuf Foo :id 5 :tags ["little" "yellow"] :doubles [1.2 3.4 5.6] :floats [0.01 0.02 0.03])]
    (let [p (conj p {:label "bar"})]
      (is (= 5     (:id p)))
      (is (= "bar" (:label p)))
      (is (= ["little" "yellow"] (:tags p)))
      (is (= [1.2 3.4 5.6] (:doubles p)))
      (is (= [(float 0.01) (float 0.02) (float 0.03)] (:floats  p))))
    (let [p (conj p {:tags ["different"]})]
      (is (= ["different"] (:tags p))))
    (let [p (conj p {:tags ["little" "yellow" "different"] :label "very"})]
      (is (= ["little" "yellow" "different"] (:tags p)))
      (is (= "very" (:label p))))))

(deftest test-adjoin
  (let [p (protobuf Foo :id 5 :tags ["little" "yellow"] :doubles [1.2] :floats [0.01])]
    (let [p (adjoin p {:label "bar"})]
      (is (= 5     (:id p)))
      (is (= "bar" (:label p)))
      (is (= false (:deleted p)))
      (is (= ["little" "yellow"] (:tags p))))
    (let [p (adjoin (assoc p :deleted true) p)]
      (is (= true (:deleted p))))
    (let [p (adjoin p {:tags ["different"]})]
      (is (= ["little" "yellow" "different"] (:tags p))))
    (let [p (adjoin p {:tags ["different"] :label "very"})]
      (is (= ["little" "yellow" "different"] (:tags p)))
      (is (= "very" (:label p))))
    (let [p (adjoin p {:doubles [3.4] :floats [0.02]})]
      (is (= [1.2 3.4] (:doubles p)))
      (is (= [(float 0.01) (float 0.02)] (:floats  p)))))
  (testing "adjoining works with set extension"
    (let [p (protobuf Foo :tag-set #{"foo" "bar" "baz"})
          q (protobuf Foo :tag-set {"bar" false "foo" false "bap" true})
          r (adjoin p q)]
      (is (= #{"foo" "bar" "baz"} (p :tag-set)))
      (is (= #{"bap"}             (q :tag-set)))
      (is (= #{"bap" "baz"}       (r :tag-set)))))
  (testing "adjoining works with counters"
    (let [p (protobuf Foo :counts {"foo" {:i 5 :d  5.0}})
          q (protobuf Foo :counts {"foo" {:i 8 :d -3.0}})
          r (adjoin p q)]
      (is (=  5   (get-in p [:counts "foo" :i])))
      (is (=  5.0 (get-in p [:counts "foo" :d])))
      (is (=  8   (get-in q [:counts "foo" :i])))
      (is (= -3.0 (get-in q [:counts "foo" :d])))
      (is (=  13  (get-in r [:counts "foo" :i])))
      (is (=  2.0 (get-in r [:counts "foo" :d]))))))

(deftest test-ordered-adjoin
  (let [inputs (apply ordered-map (for [x (range 26)
                                        entry [(str x) (str (char (+ (int \a) x)))]]
                                    entry))]
    (= (seq inputs)
       (seq (reduce (fn [m [k v]]
                      (adjoin m {:attr_map {k v}}))
                    (protobuf Foo)
                    inputs)))))

(deftest test-assoc
  (let [p (protobuf Foo :id 5 :tags ["little" "yellow"] :foo-by-id {1 {:label "one"} 2 {:label "two"}})]
    (let [p (assoc p :label "baz" :tags ["nuprin"])]
      (is (= ["nuprin"] (:tags p)))
      (is (= "baz"      (:label p))))
    (let [p (assoc p :responses [:yes :no :maybe :no "yes"])]
      (is (= [:yes :no :maybe :no :yes] (:responses p))))
    (let [p (assoc p :tags "aspirin")]
      (is (= ["aspirin"] (:tags p))))
    (let [p (assoc p :foo-by-id {3 {:label "three"} 2 {:label "two"}})]
      (is (= {3 {:id 3, :label "three", :deleted false}
              2 {:id 2, :label "two", :deleted false}}
             (:foo-by-id p))))))

(deftest test-dissoc
  (let [p (protobuf Foo :id 5 :tags ["fast" "shiny"] :label "nice")]
    (let [p (dissoc p :label :tags)]
      (is (= nil (:tags p)))
      (is (= nil (:label p))))))

(deftest test-equality
  (let [m {:id 5 :tags ["fast" "shiny"] :label "nice" :deleted false}
        p (protobuf Foo :id 5 :tags ["fast" "shiny"] :label "nice")
        q (protobuf Foo :id 5 :tags ["fast" "shiny"] :label "nice")]
    (is (= m p))
    (is (= p m))
    (is (= q p))))

(deftest test-meta
  (let [p (protobuf Foo :id 5 :tags ["fast" "shiny"] :label "nice")
        m {:foo :bar}
        q (with-meta p m)]
    (is (empty? (meta p)))
    (is (= p q))
    (is (= m (meta q)))))

(deftest test-extmap
  (let [p (protobuf Foo :id 5 :tags ["fast" "shiny"] :label "nice")
        m {:id 5 :tags ["fast" "shiny"] :label "nice" :deleted false}
        p2 (assoc p :some-key 10)
        m2 (assoc m :some-key 10)]
    (is (= p m))
    (is (= p2 m2))
    (is (= m2 p2))
    (is (= (into {} m2) (into {} p2)))
    (is (= (set (keys m2)) (set (keys p2)))))

  (let [m {:id 5 :wat 10}
        p (protobuf Foo m)]
    (testing "protobuf function uses extmap"
      (is (= (:wat m) (:wat p)))
      (let [p (conj p {:stuff 15})]
        (is (= 15 (:stuff p)))))

    ;; TODO add test back once we re-enable this check
    (comment
      (is (thrown? Exception (protobuf-dump p))
          "Should refuse to serialize with stuff in extmap"))))

(deftest test-string-keys
  (let [p (protobuf Foo "id" 5 "label" "rad")]
    (is (= 5 (p :id)))
    (is (= 5 (p "id")))
    (is (= "rad" (p :label)))
    (is (= "rad" (p "label")))
    (let [p (conj p {"tags" ["check" "it" "out"]})]
      (is (= ["check" "it" "out"] (p :tags)))
      (is (= ["check" "it" "out"] (p "tags"))))))

(deftest test-append-bytes
  (let [p (protobuf Foo :id 5 :label "rad" :deleted true
                    :tags ["sweet"] :tag-set #{"foo" "bar" "baz"}
                    :things {"first" {:marked false} "second" {:marked false}})
        q (protobuf Foo :id 43 :deleted false
                    :tags ["savory"] :tag-set {"bar" false "foo" false "bap" true}
                    :things {"first" {:marked true}})
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
    (is (= (s :things) {"first" {:id "first" :marked true}
                        "second" {:id "second" :marked false}}))
    (is (= false (r :deleted)))))

(deftest test-manual-append
  (let [p (protobuf Foo :id 5 :label "rad" :deleted true
                    :tags ["sweet"] :tag-set #{"foo" "bar" "baz"})
        q (protobuf Foo :id 43 :deleted false
                    :tags ["savory"] :tag-set {"bar" false "foo" false "bap" true})
        r (protobuf Foo :label "bad")
        s (.append p q)
        t (.append p r)]
    (is (= 43 (s :id))) ; make sure an explicit default overwrites on append
    (is (= 5  (t :id))) ; make sure a missing default doesn't overwrite on append
    (is (= "rad" (s :label)))
    (is (= "bad" (t :label)))
    (is (= ["sweet"] (t :tags)))
    (is (= ["sweet" "savory"] (s :tags)))
    (is (= #{"foo" "bar" "baz"} (p :tag-set)))
    (is (= #{"bap" "baz"} (s :tag-set)))
    (is (= false (r :deleted)))))

(deftest test-map-exists
  (doseq [map-key [:element-map-e :element-by-id-e]]
    (let [p (protobuf Maps map-key {"A" {:foo 1}
                                    "B" {:foo 2}
                                    "C" {:foo 3}
                                    "D" {:foo 4 :exists true}
                                    "E" {:foo 5 :exists true}
                                    "F" {:foo 6 :exists true}
                                    "G" {:foo 7 :exists false}
                                    "H" {:foo 8 :exists false}
                                    "I" {:foo 9 :exists false}})
          q (protobuf Maps map-key {"A" {:bar 1}
                                    "B" {:bar 2 :exists true}
                                    "C" {:bar 3 :exists false}
                                    "D" {:bar 4}
                                    "E" {:bar 5 :exists true}
                                    "F" {:bar 6 :exists false}
                                    "G" {:bar 7}
                                    "H" {:bar 8 :exists true}
                                    "I" {:bar 9 :exists false}})
          r (protobuf-load Maps (catbytes (protobuf-dump p) (protobuf-dump q)))]
      (are [key vals] (= vals (map (get-in r [map-key key])
                                   [:foo :bar :exists]))
           "A" [1   1 nil  ]
           "B" [2   2 true ]
           "C" [3   3 false]
           "D" [4   4 true ]
           "E" [5   5 true ]
           "F" [6   6 false]
           "G" [7   7 false]
           "H" [nil 8 true ]
           "I" [9   9 false]))))

(deftest test-map-deleted
  (doseq [map-key [:element-map-d :element-by-id-d]]
    (let [p (protobuf Maps map-key {"A" {:foo 1}
                                    "B" {:foo 2}
                                    "C" {:foo 3}
                                    "D" {:foo 4 :deleted true}
                                    "E" {:foo 5 :deleted true}
                                    "F" {:foo 6 :deleted true}
                                    "G" {:foo 7 :deleted false}
                                    "H" {:foo 8 :deleted false}
                                    "I" {:foo 9 :deleted false}})
          q (protobuf Maps map-key {"A" {:bar 1}
                                    "B" {:bar 2 :deleted true}
                                    "C" {:bar 3 :deleted false}
                                    "D" {:bar 4}
                                    "E" {:bar 5 :deleted true}
                                    "F" {:bar 6 :deleted false}
                                    "G" {:bar 7}
                                    "H" {:bar 8 :deleted true}
                                    "I" {:bar 9 :deleted false}})
          r (protobuf-load Maps (catbytes (protobuf-dump p) (protobuf-dump q)))]
      (are [key vals] (= vals (map (get-in r [map-key key])
                                   [:foo :bar :deleted]))
           "A" [1   1 nil  ]
           "B" [2   2 true ]
           "C" [3   3 false]
           "D" [4   4 true ]
           "E" [5   5 true ]
           "F" [nil 6 false]
           "G" [7   7 false]
           "H" [8   8 true ]
           "I" [9   9 false]))))

(deftest test-coercing
  (let [p (protobuf Foo :lat 5 :long 6)]
    (is (= 5.0 (p :lat)))
    (is (= 6.0 (p :long))))
  (let [p (protobuf Foo :lat (float 5.0) :long (double 6.0))]
    (is (= 5.0 (p :lat)))
    (is (= 6.0 (p :long)))))

(deftest test-create
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

(deftest test-map-by-with-required-field
  (let [p (protobuf Foo :id 1 :item-map {"foo" {:exists true} "bar" {:exists false}})]
    (is (= "foo" (get-in p [:item-map "foo" :item])))
    (is (= "bar" (get-in p [:item-map "bar" :item])))))

(deftest test-map-by-with-inconsistent-keys
  (let [p (protobuf Foo :pair-map {"foo" {"key" "bar" "val" "hmm"}})]
    (is (= "hmm" (get-in p [:pair-map "foo" :val])))
    (is (= nil   (get-in p [:pair-map "bar" :val]))))
  (let [p (protobuf Foo :pair-map {"foo" {:key "bar" :val "hmm"}})]
    (is (= "hmm" (get-in p [:pair-map "foo" :val])))
    (is (= nil   (get-in p [:pair-map "bar" :val])))))

(deftest test-conj
  (let [p (protobuf Foo :id 1 :foo-by-id {5 {:label "five", :tag-set ["odd"]}, 6 {:label "six" :tags ["even"]}})]
    (let [p (conj p {:foo-by-id {5 {:tag-set ["prime" "odd"]} 6 {:tags ["odd"]}}})]
      (is (= #{"prime" "odd"} (get-in p [:foo-by-id 5 :tag-set])))
      (is (= ["odd"]          (get-in p [:foo-by-id 6 :tags])))
      (is (= nil              (get-in p [:foo-by-id 6 :label]))))))

(deftest test-nested-counters
  (let [p (protobuf Foo :counts {"foo" {:i 5 :d 5.0}})]
    (is (= 5   (get-in p [:counts "foo" :i])))
    (is (= 5.0 (get-in p [:counts "foo" :d])))
    (let [p (adjoin p {:counts {"foo" {:i 2 :d -2.4} "bar" {:i 99}}})]
      (is (= 7   (get-in p [:counts "foo" :i])))
      (is (= 2.6 (get-in p [:counts "foo" :d])))
      (is (= 99  (get-in p [:counts "bar" :i])))
      (let [p (adjoin p {:counts {"foo" {:i -8 :d 4.06} "bar" {:i -66}}})]
        (is (= -1   (get-in p [:counts "foo" :i])))
        (is (= 6.66 (get-in p [:counts "foo" :d])))
        (is (= 33   (get-in p [:counts "bar" :i])))
        (is (= [{:key "foo", :i 5, :d 5.0}
                {:key "foo", :i 2, :d -2.4}
                {:key "bar", :i 99}
                {:key "foo", :i -8, :d 4.06}
                {:key "bar", :i -66}]
               (get-raw p :counts)))))))

(deftest test-succession
  (let [p (protobuf Foo :time {:year 1978 :month 11 :day 24})]
    (is (= 1978 (get-in p [:time :year])))
    (is (= 11   (get-in p [:time :month])))
    (is (= 24   (get-in p [:time :day])))
    (let [p (adjoin p {:time {:year 1974 :month 1}})]
      (is (= 1974 (get-in p [:time :year])))
      (is (= 1    (get-in p [:time :month])))
      (is (= nil  (get-in p [:time :day])))
      (is (= [{:year 1978, :month 11, :day 24} {:year 1974, :month 1}]
             (get-raw p :time))))))

(deftest test-nullable
  (let [p (protobuf Bar :int 1 :long 330000000000 :flt 1.23 :dbl 9.87654321 :str "foo" :enu :a)
        keyset #{:int :long :flt :dbl :str :enu}]
    (is (= 1            (get p :int)))
    (is (= 330000000000 (get p :long)))
    (is (= (float 1.23) (get p :flt)))
    (is (= 9.87654321   (get p :dbl)))
    (is (= "foo"        (get p :str)))
    (is (= :a           (get p :enu)))
    (is (= keyset (set (keys p))))
    (let [p (adjoin p {:int nil :long nil :flt nil :dbl nil :str nil :enu nil})]
      (is (= nil (get p :int)))
      (is (= nil (get p :long)))
      (is (= nil (get p :flt)))
      (is (= nil (get p :dbl)))
      (is (= nil (get p :str)))
      (is (= nil (get p :enu)))
      (is (= keyset (set (keys p)))))
    (testing "nullable successions"
      (let [p (protobuf Bar :label "foo")]
        (is (= "foo" (get p :label)))
        (let [p (adjoin p {:label nil})]
          (is (= nil        (get     p :label)))
          (is (= ["foo" ""] (get-raw p :label))))))
    (testing "repeated nullable"
      (let [p (protobuf Bar :labels ["foo" "bar"])]
        (is (= ["foo" "bar"] (get p :labels)))
        (let [p (adjoin p {:labels [nil]})]
          (is (= ["foo" "bar" nil] (get     p :labels)))
          (is (= ["foo" "bar" ""]  (get-raw p :labels))))))))

(deftest test-protobuf-schema
  (let [fields
        {:type :struct
         :name "flatland.protobuf.test.core.Foo"
         :fields {:id      {:default 43, :type :int}
                  :deleted {:default false, :type :boolean}
                  :lat     {:type :double}
                  :long    {:type :float}
                  :parent  {:type :struct, :name "flatland.protobuf.test.core.Foo"}
                  :floats  {:type :list, :values {:type :float}}
                  :doubles {:type :list, :values {:type :double}}
                  :label   {:type :string, :c 3, :b 2, :a 1}
                  :tags    {:type :list, :values {:type :string}}
                  :tag-set {:type :set,  :values {:type :string}}
                  :counts  {:type   :map
                            :keys   {:type :string}
                            :values {:type :struct, :name "flatland.protobuf.test.core.Count"
                                     :fields {:key {:type :string}
                                              :i {:counter true, :type :int}
                                              :d {:counter true, :type :double}}}}
                  :foo-by-id {:type :map
                              :keys   {:default 43, :type :int}
                              :values {:type :struct, :name "flatland.protobuf.test.core.Foo"}}
                  :attr-map {:type :map
                             :keys   {:type :string}
                             :values {:type :string}}
                  :pair-map {:type :map
                             :keys   {:type :string}
                             :values {:type :struct, :name "flatland.protobuf.test.core.Pair"
                                      :fields {:key {:type :string}
                                               :val {:type :string}}}}
                  :groups {:type :map
                           :keys   {:type :string}
                           :values {:type :list
                                    :values {:type :struct, :name "flatland.protobuf.test.core.Foo"}}}
                  :responses {:type :list
                              :values {:type :enum, :values #{:no :yes :maybe :not-sure}}}
                  :time {:type :struct, :name "flatland.protobuf.test.core.Time", :succession true
                         :fields {:year   {:type :int}
                                  :month  {:type :int}
                                  :day    {:type :int}
                                  :hour   {:type :int}
                                  :minute {:type :int}}}
                  :item-map {:type :map
                             :keys   {:type :string}
                             :values {:type :struct, :name "flatland.protobuf.test.core.Item"
                                      :fields {:item   {:type :string},
                                               :exists {:default true, :type :boolean}}}}
                  :things {:type :map
                           :keys {:type :string}
                           :values {:type :struct, :name "flatland.protobuf.test.core.Thing"
                                    :fields {:id {:type :string}
                                             :marked {:type :boolean}}}}}}]
    (is (= fields (protobuf-schema Foo)))
    (is (= fields (protobuf-schema flatland.protobuf.test.Core$Foo)))))

(comment deftest test-default-protobuf
  (is (= 43    (default-protobuf Foo :id)))
  (is (= 0.0   (default-protobuf Foo :lat)))
  (is (= 0.0   (default-protobuf Foo :long)))
  (is (= ""    (default-protobuf Foo :label)))
  (is (= []    (default-protobuf Foo :tags)))
  (is (= nil   (default-protobuf Foo :parent)))
  (is (= []    (default-protobuf Foo :responses)))
  (is (= #{}   (default-protobuf Foo :tag-set)))
  (is (= {}    (default-protobuf Foo :foo-by-id)))
  (is (= {}    (default-protobuf Foo :groups)))
  (is (= {}    (default-protobuf Foo :item-map)))
  (is (= false (default-protobuf Foo :deleted)))
  (is (= {}    (default-protobuf flatland.protobuf.test.Core$Foo :groups))))

(deftest test-use-underscores
  (let [dashes      (protobuf Foo      {:tag_set ["odd"]
                                        :responses [:yes :not-sure :maybe :not-sure :no]})
        underscores (protobuf FooUnder {:tag_set ["odd"]
                                        :responses [:yes :not_sure :maybe :not_sure :no]})]
    (is (= '(:id :responses :tag-set :deleted)   (keys dashes)))
    (is (= [:yes :not-sure :maybe :not-sure :no] (:responses dashes)))

    (is (= '(:id :responses :tag_set :deleted)   (keys underscores)))
    (is (= [:yes :not_sure :maybe :not_sure :no] (:responses underscores)))

    (is (= #{:id :label :tags :parent :responses :tag_set :deleted :attr_map :foo_by_id
             :pair_map :groups :doubles :floats :item_map :counts :time :lat :long :things}
           (-> (protobuf-schema FooUnder) :fields keys set)))))

(deftest test-protobuf-nested-message
  (let [p (protobuf Response :ok false :error (protobuf ErrorMsg :code -10 :data "abc"))]
    (is (= "abc" (get-in p [:error :data])))))

(deftest test-protobuf-nested-null-field
  (let [p (protobuf Response :ok true :error (protobuf ErrorMsg :code -10 :data nil))]
    (is (:ok p))))

(deftest test-protobuf-seq-and-write-protobuf
  (let [in  (PipedInputStream.)
        out (PipedOutputStream. in)
        foo (protobuf Foo :id 1 :label "foo")
        bar (protobuf Foo :id 2 :label "bar")
        baz (protobuf Foo :id 3 :label "baz")]
    (protobuf-write out foo bar baz)
    (.close out)
    (is (= [{:id 1, :label "foo", :deleted false}
            {:id 2, :label "bar", :deleted false}
            {:id 3, :label "baz", :deleted false}]
           (protobuf-seq Foo in)))))

(deftest test-encoding-errors
  (is (thrown-with-msg? IllegalArgumentException #"error setting string field flatland.protobuf.test.core.Foo.label to 8"
        (protobuf Foo :label 8)))
  (is (thrown-with-msg? IllegalArgumentException #"error adding 1 to string field flatland.protobuf.test.core.Foo.tags"
        (protobuf Foo :tags [1 2 3]))))