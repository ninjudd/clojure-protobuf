clojure-protobuf provides a Clojure interface to Google's [protocol buffers](http://code.google.com/p/protobuf).
Protocol buffers can be used to communicate with other languages over the network, and they are WAY faster to serialize and deserialize than standard Clojure objects.

## Getting started

Add the dependency to your project.clj

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.ghaskins/protobuf.svg)](https://clojars.org/org.clojars.ghaskins/protobuf)

Assuming you have the following in `resources/proto/person.proto`:

```proto
message Person {
  required int32  id    = 1;
  required string name  = 2;
  optional string email = 3;
  repeated string likes = 4;
}
```

Compile the proto using the protobuf compiler and include the resulting .java code in your project

    protoc --java_out=./ -proto_dir=resources/proto person.proto

Now you can use the protocol buffer in Clojure:

```clojure
(use 'flatland.protobuf.core)
(import Example$Person)

(def Person (protodef Example$Person))

(def p (protobuf Person :id 4 :name "Bob" :email "bob@example.com"))
=> {:id 4, :name "Bob", :email "bob@example.com"}

(assoc p :name "Bill"))
=> {:id 4, :name "Bill", :email "bob@example.com"}

(assoc p :likes ["climbing" "running" "jumping"])
=> {:id 4, name "Bob", :email "bob@example.com", :likes ["climbing" "running" "jumping"]}

(def b (protobuf-dump p))
=> #<byte[] [B@7cbe41ec>

(protobuf-load Person b)
=> {:id 4, :name "Bob", :email "bob@example.com"}
```

A protocol buffer map is immutable just like other clojure objects. It is similar to a
struct-map, except you cannot insert fields that aren't specified in the `.proto` file.

