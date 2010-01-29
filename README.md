Clojure-protobuf provides a clojure interface to Google's protocol buffers (http://code.google.com/p/protobuf).
Protocol buffers can be used to communicate with other languages over the network, and
they are WAY faster to serialize and deserialize than standard Clojure objects.

## Usage

Write a `.proto` file:

    message Person {
      required int32 id = 1;
      required string name = 2;
      optional string email = 3;
      repeated string nicknames = 4;
    }

Compile the file to Java:

    protoc --java_out=. example.proto

Now you can use the protocol buffer in clojure:

    (use 'protobuf)
    (defprotobuf person Example Person)

    (def p (protobuf person :id 4 :name "Bob" :email "bob@example.com"))
    => {:id 4, :name "Bob", :email "bob@example.com"}

    (assoc p :name "Bill"))
    => {:id 4, :name "Bill", :email "bob@example.com"}

    (def b (protobuf-bytes p))
    => #<byte[] [B@7cbe41ec>

    (protobuf person b)
    => {:id 4, :name "Bob", :email "bob@example.com"}

A protocol buffer map is immutable just like other clojure objects. It is similar to a
struct-map, except you cannot insert fields that aren't specified in the `.proto` file.

## Installation

To download clojure.jar and google's protobuf source automatically and install protoc:
    ant package
    ant install

You can also specify options to configure (like the install prefix):
    ant package -Dconfigure='--prefix=/opt/local'
    ant install

Or you can specify a specific location for clojure.jar or the protobuf source:
    ant package -Dclojure.jar=$HOME/lib/java/clojure-1.1.0.jar -Dprotobuf=$HOME/Downloads/protobuf-2.3.0

This code has been tested with clojure version 1.1.0 and protobuf version 2.3.0.
