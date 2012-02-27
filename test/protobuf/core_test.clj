(ns protobuf.core-test
  (:use protobuf.core clojure.test)
  (:import (java.io PipedInputStream PipedOutputStream)))

(def Foo      (protodef protobuf.test.Core$Foo))
(def Bar      (protodef protobuf.test.Core$Bar))
(def Response (protodef protobuf.test.Core$Response))
(def ErrorMsg (protodef protobuf.test.Core$ErrorMsg))

(defn catbytes [& args]
  (.getBytes (apply str (map (fn [#^bytes b] (String. b)) args))))

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

(deftest test-append
  (let [p (protobuf Foo :id 5 :tags ["little" "yellow"] :doubles [1.2] :floats [0.01])]
    (let [p (append p {:label "bar"})]
      (is (= 5     (:id p)))
      (is (= "bar" (:label p)))
      (is (= false (:deleted p)))
      (is (= ["little" "yellow"] (:tags p))))
    (let [p (append (assoc p :deleted true) p)]
      (is (= true (:deleted p))))
    (let [p (append p {:tags ["different"]})]
      (is (= ["little" "yellow" "different"] (:tags p))))
    (let [p (append p {:tags ["different"] :label "very"})]
      (is (= ["little" "yellow" "different"] (:tags p)))
      (is (= "very" (:label p))))
    (let [p (append p {:doubles [3.4] :floats [0.02]})]
      (is (= [1.2 3.4] (:doubles p)))
      (is (= [(float 0.01) (float 0.02)] (:floats  p))))))

(deftest test-assoc
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

(deftest test-dissoc
  (let [p (protobuf Foo :id 5 :tags ["fast" "shiny"] :label "nice")]
    (let [p (dissoc p :label :tags)]
      (is (= nil (:tags p)))
      (is (= nil (:label p))))))

(deftest test-equality
  (let [m {:id 5 :tags ["fast" "shiny"] :label "nice"}
        p (protobuf Foo :id 5 :tags ["fast" "shiny"] :label "nice")
        q (protobuf Foo :id 5 :tags ["fast" "shiny"] :label "nice")]
    (is (= m p))
    (is (= q p))))

(deftest test-meta
  (let [p (protobuf Foo :id 5 :tags ["fast" "shiny"] :label "nice")
        m {:foo :bar}
        q (with-meta p m)]
    (is (empty? (meta p)))
    (is (= p q))
    (is (= m (meta q)))))

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
  (let [p (protobuf Foo :id 5  :label "rad" :tags ["sweet"] :tag-set #{"foo" "bar" "baz"})
        q (protobuf Foo :id 43 :tags ["savory"] :tag-set {"bar" false "foo" false "bap" true})
        r (protobuf Foo :label "bad")
        s (protobuf-load Foo (catbytes (protobuf-dump p) (protobuf-dump q)))
        t (protobuf-load Foo (catbytes (protobuf-dump p) (protobuf-dump r)))]
    (is (= 43 (s :id)))                 ; make sure an explicit default overwrites on append
    (is (= 5  (t :id)))                 ; make sure a missing default doesn't overwrite on append
    (is (= "rad" (s :label)))
    (is (= "bad" (t :label)))
    (is (= ["sweet"] (t :tags)))
    (is (= ["sweet" "savory"] (s :tags)))
    (is (= #{"foo" "bar" "baz"} (p :tag-set)))
    (is (= #{"bap" "baz"} (s :tag-set)))))

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
    (let [p (append p {:counts {"foo" {:i 2 :d -2.4} "bar" {:i 99}}})]
      (is (= 7   (get-in p [:counts "foo" :i])))
      (is (= 2.6 (get-in p [:counts "foo" :d])))
      (is (= 99  (get-in p [:counts "bar" :i])))
      (let [p (append p {:counts {"foo" {:i -8 :d 4.06} "bar" {:i -66}}})]
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
    (let [p (append p {:time {:year 1974 :month 1}})]
      (is (= 1974 (get-in p [:time :year])))
      (is (= 1    (get-in p [:time :month])))
      (is (= nil  (get-in p [:time :day])))
      (is (= [{:year 1978, :month 11, :day 24} {:year 1974, :month 1}]
             (get-raw p :time))))))

(deftest test-nullable
  (let [p (protobuf Bar :int 1 :long 330000000000 :flt 1.23 :dbl 9.87654321 :str "foo")]
    (is (= 1            (get p :int)))
    (is (= 330000000000 (get p :long)))
    (is (= (float 1.23) (get p :flt)))
    (is (= 9.87654321   (get p :dbl)))
    (is (= "foo"        (get p :str)))
    (is (= [:int :long :flt :dbl :str] (keys p)))
    (let [p (append p {:int nil :long nil :flt nil :dbl nil :str nil})]
      (is (= nil (get p :int)))
      (is (= nil (get p :long)))
      (is (= nil (get p :flt)))
      (is (= nil (get p :dbl)))
      (is (= nil (get p :str)))
      (is (= nil (keys p))))
    (testing "nullable successions"
      (let [p (protobuf Bar :label "foo")]
        (is (= "foo" (get p :label)))
        (let [p (append p {:label nil})]
          (is (= nil        (get     p :label)))
          (is (= ["foo" ""] (get-raw p :label))))))
    (testing "repeated nullable"
      (let [p (protobuf Bar :labels ["foo" "bar"])]
        (is (= ["foo" "bar"] (get p :labels)))
        (let [p (append p {:labels [nil]})]
          (is (= ["foo" "bar" nil] (get     p :labels)))
          (is (= ["foo" "bar" ""]  (get-raw p :labels))))))))

(deftest test-protobuf-schema
  (let [fields
        {:type :struct
         :name "protobuf.test.core.Foo"
         :fields {:id      {:default 43, :type :int}
                  :deleted {:default false, :type :boolean}
                  :lat     {:type :double}
                  :long    {:type :float}
                  :parent  {:type :struct, :name "protobuf.test.core.Foo"}
                  :floats  {:type :list, :values {:type :float}}
                  :doubles {:type :list, :values {:type :double}}
                  :label   {:type :string, :c 3, :b 2, :a 1}
                  :tags    {:type :list, :values {:type :string}}
                  :tag-set {:type :set,  :values {:type :string}}
                  :counts  {:type   :map
                            :keys   {:type :string}
                            :values {:type :struct, :name "protobuf.test.core.Count"
                                     :fields {:key {:type :string}
                                              :i {:counter true, :type :int}
                                              :d {:counter true, :type :double}}}}
                  :foo-by-id {:type :map
                              :keys   {:default 43, :type :int}
                              :values {:type :struct, :name "protobuf.test.core.Foo"}}
                  :attr-map {:type :map
                             :keys   {:type :string}
                             :values {:type :string}}
                  :pair-map {:type :map
                             :keys   {:type :string}
                             :values {:type :struct, :name "protobuf.test.core.Pair"
                                      :fields {:key {:type :string}
                                               :val {:type :string}}}}
                  :groups {:type :map
                           :keys   {:type :string}
                           :values {:type :list
                                    :values {:type :struct, :name "protobuf.test.core.Foo"}}}
                  :responses {:type :list
                              :values {:type :enum, :values #{:no :yes :maybe :not-sure}}}
                  :time {:type :struct, :name "protobuf.test.core.Time", :succession true
                         :fields {:year   {:type :int}
                                  :month  {:type :int}
                                  :day    {:type :int}
                                  :hour   {:type :int}
                                  :minute {:type :int}}}
                  :item-map {:type :map
                             :keys   {:type :string}
                             :values {:type :struct, :name "protobuf.test.core.Item"
                                      :fields {:item   {:type :string},
                                               :exists {:default true, :type :boolean}}}}}}]
    (is (= fields (protobuf-schema Foo)))
    (is (= fields (protobuf-schema protobuf.test.Core$Foo)))))

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
  (is (= {}    (default-protobuf protobuf.test.Core$Foo :groups))))

(deftest test-use-underscores
  (let [p (protobuf Foo {:tag_set ["odd"] :responses [:yes :not-sure :maybe :not-sure :no]})]
    (is (= '(:id :responses :tag-set :deleted)   (keys p)))
    (is (= [:yes :not-sure :maybe :not-sure :no] (:responses p)))

    (protobuf.core.PersistentProtocolBufferMap/setUseUnderscores true)
    (is (= '(:id :responses :tag_set :deleted)   (keys p)))
    (is (= [:yes :not_sure :maybe :not_sure :no] (:responses p)))

    (is (= #{:id :label :tags :parent :responses :tag_set :deleted :attr_map :foo_by_id
             :pair_map :groups :doubles :floats :item_map :counts :time :lat :long}
           (-> (protobuf-schema Foo) :fields keys set)))

    (protobuf.core.PersistentProtocolBufferMap/setUseUnderscores false)))

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
    (is (= [{:id 1, :label "foo"} {:id 2, :label "bar"} {:id 3, :label "baz"}]
           (protobuf-seq Foo in)))))

(deftest test-encoding-errors
  (is (thrown-with-msg? IllegalArgumentException #"error setting string field protobuf.test.core.Foo.label to 8"
        (protobuf Foo :label 8)))
  (is (thrown-with-msg? IllegalArgumentException #"error adding 1 to string field protobuf.test.core.Foo.tags"
        (protobuf Foo :tags [1 2 3]))))