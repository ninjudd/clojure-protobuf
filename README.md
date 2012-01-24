Clojure-protobuf provides a clojure interface to Google's [protocol buffers](http://code.google.com/p/protobuf).
Protocol buffers can be used to communicate with other languages over the network, and
they are WAY faster to serialize and deserialize than standard Clojure objects.

## Usage

Write a `.proto` file:

```java
option java_package = "yourpackage.core";
option java_outer_classname = "Example";

message Person {
  required int32  id    = 1;
  required string name  = 2;
  optional string email = 3;
  repeated string likes = 4;
}
```

If you put it in project/proto/yourpackage/core/example.proto, you can compile it with [leiningen](https://github.com/technomancy/leiningen):

```
lein protobuf compile
```

Now you can use the protocol buffer in clojure:

```clojure
(use 'protobuf.core)
(def Person (protodef yourpackage.core.Example$Person))

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
```clojure

A protocol buffer map is immutable just like other clojure objects. It is similar to a
struct-map, except you cannot insert fields that aren't specified in the `.proto` file.

## Extensions

Clojure-protobuf supports extensions to protocol buffers which provide sets and maps using
repeated fields. You can also provide metadata on protobuf fields using clojure syntax. To
use these, you must import the extension file and include it when compiling. For example:

```java
import "clojure/protobuf/extensions.proto";
message Photo {
  required int32  id     = 1;
  required string path   = 2;
  repeated string labels = 3 [(set)    = true];
  repeated Attr   attrs  = 4 [(map)    = true];
  repeated Tag    tags   = 5 [(map_by) = "person_id"];

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

Compile the file:

```
lein protobuf compile example.proto
```

Then you can access the maps in clojure:

```clojure
(use 'protobuf.core)
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

(:x-coord (protofields Tag))
=> {:max 100.0 :min -100.0}
```

## Installation

You'll want to use this with the Leiningen build tool. You can get it by
putting it in your `:dependencies` and/or `:dev-dependencies`. If you
want to use the Leiningen plugin portion of clojure-protobuf, it has to
be in your dev-dependencies.

```clojure
:dev-dependencies [[protobuf "x.x.x"]]
```

Replace `"x.x.x"` with the actual latest version, which you can find on
[clojars](http://clojars.org/protobuf) 

**NOTE: clojure-protobuf requires bugfixes introduced in the 1.x branch
of Leiningen. Until the 1.7.0 release, you'll have to use Leiningen off
of the latest 1.x branch in order to use the Leiningen tasks provided
with this library.**

## History

The build tool tasks provided with this library were originally for the
cake build tool. In 2011, the authors of that tool decided that their
time would be better spent working on a single, canonical build tool.
Leiningen was already the standard for Clojure, so that's where we are
now. As of version 0.6.0 (which has yet to see an final release, but is
usable), all of the cake-specific functionality has been rewritten for
Leiningen. Any version before that will not work with Leiningen.

## Getting Help

If you have any questions or need help, you can find us on IRC in [#flatland](irc://irc.freenode.net/#flatland).
