clojure-protobuf provides a Clojure interface to Google's [protocol buffers](http://code.google.com/p/protobuf).
Protocol buffers can be used to communicate with other languages over the network, and
they are WAY faster to serialize and deserialize than standard Clojure objects.

## Getting started

You'll probably want to use [Leiningen](https://github.com/technomancy/leiningen) with the
[lein-protobuf](https://github.com/flatland/lein-protobuf) plugin for compiling `.proto` files. Add
the following to your `project.clj` file:

    :dependencies [[org.flatland/protobuf "0.7.2"]]
    :plugins [[lein-protobuf "0.3.1"]]

Be sure to replace `"0.7.2"` and `"0.3.1"` with the latest versions listed at
https://clojars.org/org.flatland/protobuf and https://clojars.org/lein-protobuf.

*Note: lein-protobuf requires at least version 2.0 of Leiningen.*

## Usage

Assuming you have the following in `resources/proto/person.proto`:

```proto
message Person {
  required int32  id    = 1;
  required string name  = 2;
  optional string email = 3;
  repeated string likes = 4;
}
```

You can run the following to compile the `.proto` file:

    lein protobuf

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

## Extensions

Clojure-protobuf supports extensions to protocol buffers which provide sets and maps using
repeated fields. You can also provide metadata on protobuf fields using clojure syntax. To
use these, you must import the extension file and include it when compiling. For example:

```proto
import "flatland/protobuf/core/extensions.proto";

message Photo {
  required int32  id     = 1;
  required string path   = 2;
  repeated Label  labels = 3 [(set)    = true];
  repeated Attr   attrs  = 4 [(map)    = true];
  repeated Tag    tags   = 5 [(map_by) = "person_id"];

  message Label {
    required string item   = 1;
    required bool   exists = 2;
  }

  message Attr {
    required string key = 1;
    optional string val = 2;
  }

  message Tag {
    required int32 person_id = 1;
    optional int32 x_coord   = 2 [(meta) = "{:max 100.0 :min -100.0}"];
    optional int32 y_coord   = 3;
    optional int32 width     = 4;
    optional int32 height    = 5;
  }
}
```
Then you can access the extension fields in Clojure:

```clojure
(use 'flatland.protobuf.core)
(import Example$Photo)
(import Example$Photo$Tag)

(def Photo (protodef Example$Photo))
(def Tag (protodef Example$Photo$Tag))

(def p (protobuf Photo :id 7  :path "/photos/h2k3j4h9h23" :labels #{"hawaii" "family" "surfing"}
                       :attrs {"dimensions" "1632x1224", "alpha" "no", "color space" "RGB"}
                       :tags  {4 {:person_id 4, :x_coord 607, :y_coord 813, :width 25, :height 27}}))
=> {:id 7 :path "/photos/h2k3j4h9h23" :labels #{"hawaii" "family" "surfing"}...}

(def b (protobuf-dump p))
=> #<byte[] [B@7cbe41ec>

(protobuf-load Photo b)
=> {:id 7 :path "/photos/h2k3j4h9h23" :labels #{"hawaii" "family" "surfing"}...}

(:x-coord (protobuf-schema Tag))
=> {:max 100.0 :min -100.0}
```

## Getting Help

If you have any questions or need help, you can find us on IRC in [#flatland](irc://irc.freenode.net/#flatland).
