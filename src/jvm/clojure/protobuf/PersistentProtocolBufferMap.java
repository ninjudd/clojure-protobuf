/**
 *   Copyright (c) Justin Balthrop. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

/* Based on rhickey's clojure.lang.PersistentStructMap - Jan 17, 2010 */

package clojure.protobuf;

import clojure.lang.*;
import java.util.*;
import java.io.InputStream;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Descriptors;

public class PersistentProtocolBufferMap extends APersistentMap {
  final Descriptors.Descriptor type;
  final DynamicMessage         message;
  final DynamicMessage.Builder builder;

  static public PersistentProtocolBufferMap create(Descriptors.Descriptor type, byte[] bytes)
    throws com.google.protobuf.InvalidProtocolBufferException
  {
    DynamicMessage message = DynamicMessage.parseFrom(type, bytes);
    return new PersistentProtocolBufferMap(null, type, message, null);
  }
  
  static public PersistentProtocolBufferMap create(Descriptors.Descriptor type, String string)
    throws com.google.protobuf.InvalidProtocolBufferException
  {
    return create(type, string.getBytes());
  }
  
  static public PersistentProtocolBufferMap create(Descriptors.Descriptor type, InputStream input)
    throws com.google.protobuf.InvalidProtocolBufferException, java.io.IOException
  {
    DynamicMessage message = DynamicMessage.parseFrom(type, input);
    return new PersistentProtocolBufferMap(null, type, message, null);
  }
  
  static public PersistentProtocolBufferMap construct(Descriptors.Descriptor type, IPersistentMap keyvals){
    DynamicMessage.Builder builder = DynamicMessage.newBuilder(type);
    PersistentProtocolBufferMap protobuf = new PersistentProtocolBufferMap(null, type, null, builder);
    return (PersistentProtocolBufferMap) protobuf.cons(keyvals);
  }
  
  protected PersistentProtocolBufferMap(IPersistentMap meta, Descriptors.Descriptor type,
                                        DynamicMessage message, DynamicMessage.Builder builder) {
    super(meta);
    this.type    = type;
    this.message = message;
    this.builder = builder;
  }
  
  protected PersistentProtocolBufferMap makeNew(IPersistentMap meta, Descriptors.Descriptor type,
                                                DynamicMessage message, DynamicMessage.Builder builder) {
    return new PersistentProtocolBufferMap(meta, type, message, builder);
  }
  
  protected DynamicMessage message() {
    if (message == null) {
      return builder.build();
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
  
  protected Descriptors.FieldDescriptor fieldDescriptor(Keyword key) {
    return type.findFieldByName(key.getName());
  }
  
  protected Object fromFieldValue(Descriptors.FieldDescriptor.JavaType type, Object value) {
    switch (type) {
    case ENUM:
      break;
    case MESSAGE:
      break;
    default:
      return value;
    }
    return null;
  }
  
  protected Object toFieldValue(Descriptors.FieldDescriptor.JavaType type, Object value) {
    switch (type) {
    case LONG:
      if (value instanceof Long) return value;
      Integer i = (Integer) value;
      return new Long(i.longValue());      
    case INT:
      return (Integer)value;
    case ENUM:
      break;
    case MESSAGE:
      break;
    default:
      return value;
    }
    return null;
  }
  
  protected void setField(DynamicMessage.Builder builder, Descriptors.FieldDescriptor field, Object val) {
    if (field == null) return;

    if (field.isRepeated()) {
      builder.clearField(field);
      for (ISeq s = RT.seq(val); s != null; s = s.next()) {
        Object value = toFieldValue(field.getJavaType(), s.first());
        builder.addRepeatedField(field, value);
      }
    } else {
      Object value = toFieldValue(field.getJavaType(), val);
      builder.setField(field, value);
    }
  }
  
  public Obj withMeta(IPersistentMap meta) {
    if (meta == meta()) return this;
    return makeNew(meta, type, message, builder);
  }
  
  public boolean containsKey(Object key) {
    Descriptors.FieldDescriptor field = fieldDescriptor((Keyword) key);
    return message().hasField(field);
  }
  
  public IMapEntry entryAt(Object key) {
    Object value = valAt(key);
    return (value == null) ? null : new MapEntry(key, value);
  }
  
  public Object valAt(Object key) {
    Descriptors.FieldDescriptor field = fieldDescriptor((Keyword) key);
    if (field == null) return null;
    
    DynamicMessage message = message();
    
    if (field.isRepeated()) {
      int num_items = message.getRepeatedFieldCount(field);
      List<Object> items = new ArrayList<Object>(num_items);
      
      for (int i = 0; i < num_items; i++) {
        Object value = fromFieldValue(field.getJavaType(), message.getRepeatedField(field, i));
        items.add(value);
      }
      return PersistentVector.create(items);
    } else {
      return fromFieldValue(field.getJavaType(), message.getField(field));
    }
  }
  
  public Object valAt(Object key, Object notFound) {
    Object val = valAt(key);
    return (val == null) ? notFound : val;
  }
  
  public IPersistentMap assoc(Object key, Object val) {
    DynamicMessage.Builder builder = builder();
    Descriptors.FieldDescriptor field = fieldDescriptor((Keyword) key);
    
    if (field == null) return this;
    setField(builder, field, val);
    return makeNew(meta(), type, null, builder);
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
      setField(builder, fieldDescriptor(key), e.getValue());
    }
    return makeNew(meta(), type, null, builder);
  }
  
  public IPersistentMap without(Object key) throws Exception {
    Descriptors.FieldDescriptor field = fieldDescriptor((Keyword) key);
    if (field == null) return this;
    if (field.isRequired()) throw new Exception("Can't remove required field");
    
    DynamicMessage.Builder builder = builder();
    builder.clearField(field);
    return makeNew(meta(), type, null, builder);
  }
  
  public Iterator iterator() {
    return new SeqIterator(seq());
  }
  
  public int count() {
    return message().getAllFields().size();
  }
  
  public ISeq seq() {
    return new Seq(null, message());
  }
  
  public IPersistentCollection empty() {
    DynamicMessage.Builder builder = builder();
    builder.clear();
    return makeNew(meta(), type, null, builder);
  }
  
  static class Seq extends ASeq {
    final Map<Descriptors.FieldDescriptor, Object> map;
    final Descriptors.FieldDescriptor[] fields;
    final int i;
    
    public Seq(IPersistentMap meta, DynamicMessage message) {    
      super(meta);
      this.map    = message.getAllFields();
      this.fields = (Descriptors.FieldDescriptor[])map.keySet().toArray(new Descriptors.FieldDescriptor[map.size()]);
      this.i      = 0;
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
      Descriptors.FieldDescriptor field = fields[i];
      Keyword key = Keyword.intern(Symbol.intern(field.getName()));
      return new MapEntry(key, map.get(field));
    }
    
    public ISeq next() {
      if (i + 1 < fields.length) return new Seq(meta(), map, fields, i + 1);
      return null;
    }
  }
}