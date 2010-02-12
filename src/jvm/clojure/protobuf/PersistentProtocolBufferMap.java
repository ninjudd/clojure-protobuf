/**
 *   Copyright (c) Justin Balthrop. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package clojure.protobuf;

import clojure.lang.*;
import java.util.*;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.InvocationTargetException;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Descriptors;

public class PersistentProtocolBufferMap extends APersistentMap {
  public static class Def {
    final Descriptors.Descriptor type;
    ConcurrentHashMap<Keyword, Descriptors.FieldDescriptor> keyword_to_field;
    static ConcurrentHashMap<Descriptors.Descriptor, Def> type_to_def = new ConcurrentHashMap<Descriptors.Descriptor, Def>();

    public static Def create(String class_name) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, ClassNotFoundException {
      Class<?> c = Class.forName(class_name);
      return create(c);
    }

   public static Def create(Class<?> c) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
      Descriptors.Descriptor type = (Descriptors.Descriptor) c.getMethod("getDescriptor").invoke(null);
      return create(type);
    }

    public static Def create(Descriptors.Descriptor type) {
      Def def = type_to_def.get(type);
      if (def == null) {
        def = new Def(type);
        type_to_def.putIfAbsent(type, def);
      }
      return def;
    }

    protected Def(Descriptors.Descriptor type) {
      this.type             = type;
      this.keyword_to_field = new ConcurrentHashMap<Keyword, Descriptors.FieldDescriptor>();
    }

    public DynamicMessage parseFrom(byte[] bytes) throws InvalidProtocolBufferException {
      return DynamicMessage.parseFrom(type, bytes);
    }

    public DynamicMessage.Builder newBuilder() {
      return DynamicMessage.newBuilder(type);
    }

    public Descriptors.FieldDescriptor fieldDescriptor(Keyword key) {
      if (key == null) return null;
      Descriptors.FieldDescriptor field = keyword_to_field.get(key);
      if (field == null) {
        String name = key.getName().replaceAll("-","_");
        field = type.findFieldByName(name);
        if (field != null) keyword_to_field.putIfAbsent(key, field);
      }
      return field;
    }

    public String getName() {
      return type.getName();
    }

    public String getFullName() {
      return type.getFullName();
    }

    public Descriptors.Descriptor getMessageType() {
      return type;
    }
  }

  final Def            def;
  final DynamicMessage message;

  DynamicMessage built_message;

  static public PersistentProtocolBufferMap create(Def def, byte[] bytes) throws InvalidProtocolBufferException {
    DynamicMessage message = def.parseFrom(bytes);
    return new PersistentProtocolBufferMap(null, def, message);
  }

  static public PersistentProtocolBufferMap construct(Def def, IPersistentMap keyvals) {
    PersistentProtocolBufferMap protobuf = new PersistentProtocolBufferMap(null, def);
    return (PersistentProtocolBufferMap) protobuf.cons(keyvals);
  }

  protected PersistentProtocolBufferMap(IPersistentMap meta, Def def) {
    super(meta);
    this.def     = def;
    this.message = null;
  }

  protected PersistentProtocolBufferMap(IPersistentMap meta, Def def, DynamicMessage message) {
    super(meta);
    this.def     = def;
    this.message = message;
  }

  protected PersistentProtocolBufferMap(IPersistentMap meta, Def def, DynamicMessage.Builder builder) {
    super(meta);
    this.def     = def;
    this.message = builder.build();
  }

  public byte[] toByteArray() {
    return message().toByteArray();
  }

  public Descriptors.Descriptor getMessageType() {
    return def.getMessageType();
  }

  protected DynamicMessage message() {
    if (message == null) {
      return def.newBuilder().build(); // This will only work if an empty message is valid.
    } else {
      return message;
    }
  }

  protected DynamicMessage.Builder builder() {
    if (message == null) {
      return def.newBuilder();
    } else {
      return message.toBuilder();
    }
  }

  static ConcurrentHashMap<Descriptors.EnumValueDescriptor, Keyword> enum_to_keyword = new ConcurrentHashMap<Descriptors.EnumValueDescriptor, Keyword>();

  static protected Keyword enumToKeyword(Descriptors.EnumValueDescriptor enum_value) {
    Keyword keyword = enum_to_keyword.get(enum_value);
    if (keyword == null) {
      String name = enum_value.getName().toLowerCase().replaceAll("_","-");
      keyword = Keyword.intern(Symbol.intern(name));
      enum_to_keyword.putIfAbsent(enum_value, keyword);
    }
    return keyword;
  }

  static protected Object fromProtoValue(Descriptors.FieldDescriptor field, Object value) {
    if (value instanceof List) {
      List values = (List) value;

      List<Object> items = new ArrayList<Object>(values.size());
      Iterator iterator = values.iterator();

      while (iterator.hasNext()) {
        items.add(fromProtoValue(field, iterator.next()));
      }
      return PersistentVector.create(items);
    } else {
      switch (field.getJavaType()) {
      case ENUM:
        Descriptors.EnumValueDescriptor e = (Descriptors.EnumValueDescriptor) value;
        return enumToKeyword(e);
      case MESSAGE:
        Def def  = PersistentProtocolBufferMap.Def.create(field.getMessageType());
        DynamicMessage message = (DynamicMessage) value;

        // Total hack because getField() doesn't return an empty array for repeated messages.
        if (field.isRepeated() && !message.isInitialized()) return fromProtoValue(field, new ArrayList());

        return new PersistentProtocolBufferMap(null, def, message);
      default:
        return value;
      }
    }
  }

  static protected Object toProtoValue(Descriptors.FieldDescriptor field, Object value) {
    switch (field.getJavaType()) {
    case LONG:
      if (value instanceof Long) return value;
      Integer i = (Integer) value;
      return new Long(i.longValue());
    case INT:
      if (value instanceof Integer) return value;
      Long l = (Long) value;
      return new Integer(l.intValue());
    case ENUM:
      Keyword key = (Keyword) value;
      String name = key.getName().toUpperCase().replaceAll("-","_");
      Descriptors.EnumDescriptor      enum_type  = field.getEnumType();
      Descriptors.EnumValueDescriptor enum_value = enum_type.findValueByName(name);
			if (enum_value == null) {
        PrintWriter err = (PrintWriter) RT.ERR.deref();
        err.format("invalid enum value %s for enum type %s\n", name, enum_type.getFullName());
      }
      return enum_value;
    case MESSAGE:
      PersistentProtocolBufferMap protobuf;
      if (value instanceof PersistentProtocolBufferMap) {
        protobuf = (PersistentProtocolBufferMap) value;
      } else {
        Def def  = PersistentProtocolBufferMap.Def.create(field.getMessageType());
        protobuf = PersistentProtocolBufferMap.construct(def, (IPersistentMap) value);
      }
      return protobuf.message();
    default:
      return value;
    }
  }

  protected void setField(DynamicMessage.Builder builder, Descriptors.FieldDescriptor field, Object val) {
    if (field == null) return;

    if (field.isRepeated()) {
      builder.clearField(field);
      for (ISeq s = RT.seq(val); s != null; s = s.next()) {
        Object value = toProtoValue(field, s.first());
        builder.addRepeatedField(field, value);
      }
    } else {
      Object value = toProtoValue(field, val);
      builder.setField(field, value);
    }
  }

  public Obj withMeta(IPersistentMap meta) {
    if (meta == meta()) return this;
    return new PersistentProtocolBufferMap(meta(), def, message);
  }

  public boolean containsKey(Object key) {
    Descriptors.FieldDescriptor field = def.fieldDescriptor((Keyword) key);
    return message().hasField(field);
  }

  public IMapEntry entryAt(Object key) {
    Object value = valAt(key);
    return (value == null) ? null : new MapEntry(key, value);
  }

  public Object valAt(Object key) {
    Descriptors.FieldDescriptor field = def.fieldDescriptor((Keyword) key);
    if (field == null) return null;
    return fromProtoValue(field, message().getField(field));
  }

  public Object valAt(Object key, Object notFound) {
    Object val = valAt(key);
    return (val == null) ? notFound : val;
  }

  public IPersistentMap assoc(Object key, Object val) {
    DynamicMessage.Builder builder = builder();
    Descriptors.FieldDescriptor field = def.fieldDescriptor((Keyword) key);

    if (field == null) return this;
    setField(builder, field, val);
    return new PersistentProtocolBufferMap(meta(), def, builder);
  }

  public IPersistentMap assocEx(Object key, Object val) throws Exception {
    if(containsKey(key)) throw new Exception("Key already present");
    return assoc(key, val);
  }

  public IPersistentCollection cons(Object o) {
    if (o instanceof Map.Entry) {
      Map.Entry e = (Map.Entry) o;
      return assoc(e.getKey(), e.getValue());
    }	else if (o instanceof IPersistentVector) {
      IPersistentVector v = (IPersistentVector) o;
      if (v.count() != 2) throw new IllegalArgumentException("Vector arg to map conj must be a pair");
      return assoc(v.nth(0), v.nth(1));
    }

    DynamicMessage.Builder builder = builder();

    for(ISeq s = RT.seq(o); s != null; s = s.next()) {
      Map.Entry e = (Map.Entry) s.first();
      Keyword key = (Keyword) e.getKey();
      setField(builder, def.fieldDescriptor(key), e.getValue());
    }
    return new PersistentProtocolBufferMap(meta(), def, builder);
  }

  public IPersistentMap without(Object key) throws Exception {
    Descriptors.FieldDescriptor field = def.fieldDescriptor((Keyword) key);
    if (field == null) return this;
    if (field.isRequired()) throw new Exception("Can't remove required field");

    DynamicMessage.Builder builder = builder();
    builder.clearField(field);
    return new PersistentProtocolBufferMap(meta(), def, builder);
  }

  public Iterator iterator() {
    return new SeqIterator(seq());
  }

  public int count() {
    return message().getAllFields().size();
  }

  public ISeq seq() {
    return Seq.create(message());
  }

  public IPersistentCollection empty() {
    DynamicMessage.Builder builder = builder();
    builder.clear();
    return new PersistentProtocolBufferMap(meta(), def, builder);
  }

  static class Seq extends ASeq {
    final Map<Descriptors.FieldDescriptor, Object> map;
    final Descriptors.FieldDescriptor[] fields;
    final int i;

    static public Seq create(DynamicMessage message) {
      Map<Descriptors.FieldDescriptor, Object> map = message.getAllFields();
      if (map.size() == 0) return null;

      Descriptors.FieldDescriptor[] fields = new Descriptors.FieldDescriptor[map.size()];
      fields = (Descriptors.FieldDescriptor[]) map.keySet().toArray(fields);
      return new Seq(null, map, fields, 0);
    }

    protected Seq(IPersistentMap meta, Map<Descriptors.FieldDescriptor, Object> map, Descriptors.FieldDescriptor[] fields, int i){
      super(meta);
      this.map    = map;
      this.fields = fields;
      this.i      = i;
    }

    public Obj withMeta(IPersistentMap meta) {
      if(meta != meta()) return new Seq(meta, map, fields, i);
      return this;
    }

    public Object first() {
      if (i == fields.length) return null;
      Descriptors.FieldDescriptor field = fields[i];
      String name = field.getName().replaceAll("_","-");
      Keyword key = Keyword.intern(Symbol.intern(name));
      Object  val = PersistentProtocolBufferMap.fromProtoValue(field, map.get(field));
      return new MapEntry(key, val);
    }

    public ISeq next() {
      if (i + 1 < fields.length) return new Seq(meta(), map, fields, i + 1);
      return null;
    }
  }
}