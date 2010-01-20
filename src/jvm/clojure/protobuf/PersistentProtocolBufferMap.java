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
      Descriptors.FieldDescriptor field = keyword_to_field.get(key);
      if (field == null) {
        field = type.findFieldByName(key.getName());
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

  final Def                    def;
  final DynamicMessage         message;
  final DynamicMessage.Builder builder;

  DynamicMessage built_message;

  static public PersistentProtocolBufferMap create(Def def, byte[] bytes) throws InvalidProtocolBufferException {
    DynamicMessage message = def.parseFrom(bytes);
    return new PersistentProtocolBufferMap(null, def, message, null);
  }
    
  static public PersistentProtocolBufferMap construct(Def def, IPersistentMap keyvals) {
    DynamicMessage.Builder builder = def.newBuilder();
    PersistentProtocolBufferMap protobuf = new PersistentProtocolBufferMap(null, def, null, builder);
    return (PersistentProtocolBufferMap) protobuf.cons(keyvals);
  }
  
  protected PersistentProtocolBufferMap(IPersistentMap meta, Def def, DynamicMessage message, DynamicMessage.Builder builder) {
    super(meta);
    this.def     = def;
    this.message = message;
    this.builder = builder;
  }
  
  static protected PersistentProtocolBufferMap makeNew(IPersistentMap meta, Def def, DynamicMessage message, DynamicMessage.Builder builder) {
    return new PersistentProtocolBufferMap(meta, def, message, builder);
  }
  
  public byte[] toByteArray() {
    return message().toByteArray();
  }

  public Descriptors.Descriptor getMessageType() {
    return def.getMessageType();
  }

  protected DynamicMessage message() {
    if (message == null) {
      if (built_message == null) built_message = builder.build();
      return built_message;
    } else {
      return message;
    }
  }
  
  protected DynamicMessage.Builder builder() {
    if (builder == null) {
      return message.toBuilder();
    } else {
      return builder.clone();
    }
  }

  static ConcurrentHashMap<Descriptors.EnumValueDescriptor, Keyword> enum_to_keyword = new ConcurrentHashMap<Descriptors.EnumValueDescriptor, Keyword>();

  static protected Keyword enumToKeyword(Descriptors.EnumValueDescriptor e) {
    Keyword k = enum_to_keyword.get(e);
    if (k == null) {
      k = Keyword.intern(Symbol.intern(e.getName().toLowerCase()));
    }
    return k;
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
        return PersistentProtocolBufferMap.makeNew(null, def, message, null);
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
      Descriptors.EnumDescriptor e = field.getEnumType();
      Keyword key = (Keyword) value;
      return e.findValueByName(key.getName().toUpperCase());
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
    return makeNew(meta, def, message, builder);
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
    return makeNew(meta(), def, null, builder);
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
    return makeNew(meta(), def, null, builder);
  }
  
  public IPersistentMap without(Object key) throws Exception {
    Descriptors.FieldDescriptor field = def.fieldDescriptor((Keyword) key);
    if (field == null) return this;
    if (field.isRequired()) throw new Exception("Can't remove required field");
    
    DynamicMessage.Builder builder = builder();
    builder.clearField(field);
    return makeNew(meta(), def, null, builder);
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
    return makeNew(meta(), def, null, builder);
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
      Keyword key = Keyword.intern(Symbol.intern(field.getName()));
      Object  val = PersistentProtocolBufferMap.fromProtoValue(field, map.get(field));
      return new MapEntry(key, val);
    }
    
    public ISeq next() {
      if (i + 1 < fields.length) return new Seq(meta(), map, fields, i + 1);
      return null;
    }
  }
}