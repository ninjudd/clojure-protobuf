(ns protobuf-test
  (:use protobuf)
  (:use clojure.test)
  (:import (java.io PipedInputStream PipedOutputStream)))

(defprotobuf Foo clojure.protobuf.Test Foo)
(defprotobuf Bar clojure.protobuf.Test Bar)
(defprotobuf Response clojure.protobuf.Test Response)
(defprotobuf ErrorMsg clojure.protobuf.Test ErrorMsg)

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

(deftest test-protofields
  (let [fields {:floats    {:type :float,   :repeated true},
                :doubles   {:type :double,  :repeated true},
                :counts    {:type :message, :repeated true},
                :time      {:type :message, :repeated true},
                :attr-map  {:type :message, :repeated true},
                :tag-set   {:type :message, :repeated true},
                :item-map  {:type :message, :repeated true},
                :groups    {:type :message, :repeated true},
                :responses {:type :enum,    :repeated true, :values #{:yes :no :maybe :not-sure}},
                :pair-map  {:type :message, :repeated true},
                :foo-by-id {:type :message, :repeated true},
                :tags      {:type :string,  :repeated true},
                :label     {:type :string, :a 1, :b 2, :c 3},
                :id        {:type :int},
                :parent    {:type :message},
                :lat       {:type :double},
                :long      {:type :float},
                :deleted   {:type :boolean}}]
    (is (= fields (protofields Foo)))
    (is (= fields (protofields clojure.protobuf.Test$Foo)))))

(deftest test-nested-protofields
  (is (= {:year   {:type :int},
          :month  {:type :int},
          :day    {:type :int},
          :hour   {:type :int},
          :minute {:type :int}}
         (protofields Foo :time)))
  (is (= {:year   {:type :int},
          :month  {:type :int},
          :day    {:type :int},
          :hour   {:type :int},
          :minute {:type :int}}
         (protofields Foo :parent :foo-by-id :time))))

(deftest test-protodefault
  (is (= 43    (protodefault Foo :id)))
  (is (= 0.0   (protodefault Foo :lat)))
  (is (= 0.0   (protodefault Foo :long)))
  (is (= ""    (protodefault Foo :label)))
  (is (= []    (protodefault Foo :tags)))
  (is (= nil   (protodefault Foo :parent)))
  (is (= []    (protodefault Foo :responses)))
  (is (= #{}   (protodefault Foo :tag-set)))
  (is (= {}    (protodefault Foo :foo-by-id)))
  (is (= {}    (protodefault Foo :groups)))
  (is (= {}    (protodefault Foo :item-map)))
  (is (= false (protodefault Foo :deleted)))
  (is (= {}    (protodefault clojure.protobuf.Test$Foo :groups))))

(deftest test-use-underscores
  (let [p (protobuf Foo {:tag_set ["odd"] :responses [:yes :not-sure :maybe :not-sure :no]})]
    (is (= '(:id :responses :tag-set :deleted)   (keys p)))
    (is (= [:yes :not-sure :maybe :not-sure :no] (:responses p)))

    (clojure.protobuf.PersistentProtocolBufferMap/setUseUnderscores true)
    (is (= '(:id :responses :tag_set :deleted)   (keys p)))
    (is (= [:yes :not_sure :maybe :not_sure :no] (:responses p)))

    (is (= #{:id :label :tags :parent :responses :tag_set :deleted :attr_map :foo_by_id
             :pair_map :groups :doubles :floats :item_map :counts :time :lat :long}
           (set (keys (protofields Foo)))))

    (clojure.protobuf.PersistentProtocolBufferMap/setUseUnderscores false)))

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
