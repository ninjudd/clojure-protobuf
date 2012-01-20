/**
 *   Copyright (c) Justin Balthrop. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 **/

package protobuf.core;

import clojure.lang.*;
import ordered_set.core.OrderedSet;
import java.util.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.InvocationTargetException;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.GeneratedMessage;

public class PersistentProtocolBufferMap extends APersistentMap {
  public static class Def {
    final Descriptors.Descriptor type;
    ConcurrentHashMap<Keyword, Descriptors.FieldDescriptor> keyword_to_field;
    static ConcurrentHashMap<Descriptors.Descriptor, Def> type_to_def = new ConcurrentHashMap<Descriptors.Descriptor, Def>();

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

    public DynamicMessage parseFrom(CodedInputStream input) throws IOException {
      return DynamicMessage.parseFrom(type, input);
    }

    public DynamicMessage.Builder parseDelimitedFrom(InputStream input) throws IOException {
      DynamicMessage.Builder builder = newBuilder();
      if (builder.mergeDelimitedFrom(input)) {
        return builder;
      } else {
        return null;
      }
    }

    public DynamicMessage.Builder newBuilder() {
      return DynamicMessage.newBuilder(type);
    }

    public Descriptors.FieldDescriptor fieldDescriptor(Object key) {
      if (key == null) return null;

      if (key instanceof Descriptors.FieldDescriptor) {
        return (Descriptors.FieldDescriptor) key;
      } else if (key instanceof Keyword) {
        Keyword keyword = (Keyword) key;
        Descriptors.FieldDescriptor field = keyword_to_field.get(keyword);
        if (field == null) {
          field = fieldDescriptor(keyword.getName());
          if (field != null) keyword_to_field.putIfAbsent(keyword, field);
        }
        return field;
      } else {
        String name = ((String) key).replaceAll("-","_");
        return type.findFieldByName(name);
      }
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

    public Object defaultValue(Keyword key) {
      Descriptors.FieldDescriptor field = fieldDescriptor(key);
      if (field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
        if (!field.isRepeated()) return null;
        return PersistentProtocolBufferMap.fromProtoValue(field, new ArrayList());
      } else {
        return PersistentProtocolBufferMap.fromProtoValue(field, field.getDefaultValue());
      }
    }
  }

  final Def def;
  final DynamicMessage message;
  final IPersistentMap _meta;

  DynamicMessage built_message;

  static public PersistentProtocolBufferMap create(Def def, byte[] bytes) throws InvalidProtocolBufferException {
    DynamicMessage message = def.parseFrom(bytes);
    return new PersistentProtocolBufferMap(null, def, message);
  }

  static public PersistentProtocolBufferMap parseFrom(Def def, CodedInputStream input) throws IOException {
    DynamicMessage message = def.parseFrom(input);
    return new PersistentProtocolBufferMap(null, def, message);
  }

  static public PersistentProtocolBufferMap parseDelimitedFrom(Def def, InputStream input) throws IOException {
    DynamicMessage.Builder builder = def.parseDelimitedFrom(input);
    if (builder != null) {
      return new PersistentProtocolBufferMap(null, def, builder);
    } else {
      return null;
    }
  }

  static public PersistentProtocolBufferMap construct(Def def, Object keyvals) {
    PersistentProtocolBufferMap protobuf = new PersistentProtocolBufferMap(null, def);
    return (PersistentProtocolBufferMap) protobuf.cons(keyvals);
  }

  protected PersistentProtocolBufferMap(IPersistentMap meta, Def def) {
    this._meta   = meta;
    this.def     = def;
    this.message = null;
  }

  protected PersistentProtocolBufferMap(IPersistentMap meta, Def def, DynamicMessage message) {
    this._meta   = meta;
    this.def     = def;
    this.message = message;
  }

  protected PersistentProtocolBufferMap(IPersistentMap meta, Def def, DynamicMessage.Builder builder) {
    this._meta   = meta;
    this.def     = def;
    this.message = builder.build();
  }

  public byte[] toByteArray() {
    return message().toByteArray();
  }

  public void writeTo(CodedOutputStream output) throws IOException {
    message().writeTo(output);
  }

  public void writeDelimitedTo(OutputStream output) throws IOException {
    message().writeDelimitedTo(output);
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

  static boolean use_underscores = false;
  static public void setUseUnderscores(boolean val) {
    use_underscores = val;
    field_name_to_keyword.clear();
    enum_to_keyword.clear();
    map_field_by.clear();
  }

  static ConcurrentHashMap<String, Keyword> field_name_to_keyword =
     new ConcurrentHashMap<String, Keyword>();
  static public Keyword intern(String name) {
    Keyword keyword = field_name_to_keyword.get(name);
    if (keyword == null) {
      name = name.toLowerCase();
      if (!use_underscores) name = name.replaceAll("_","-");
      keyword = Keyword.intern(Symbol.intern(name));
      field_name_to_keyword.putIfAbsent(name, keyword);
    }
    return keyword;
  }

  static ConcurrentHashMap<Descriptors.EnumValueDescriptor, Keyword> enum_to_keyword =
     new ConcurrentHashMap<Descriptors.EnumValueDescriptor, Keyword>();
  static public Keyword enumToKeyword(Descriptors.EnumValueDescriptor enum_value) {
    Keyword keyword = enum_to_keyword.get(enum_value);
    if (keyword == null) {
      keyword = intern(enum_value.getName());
      enum_to_keyword.putIfAbsent(enum_value, keyword);
    }
    return keyword;
  }

  static Keyword k_null = Keyword.intern(Symbol.intern(""));
  static ConcurrentHashMap<Descriptors.FieldDescriptor, Keyword> map_field_by =
     new ConcurrentHashMap<Descriptors.FieldDescriptor, Keyword>();
  static protected Keyword mapFieldBy(Descriptors.FieldDescriptor field) {
    Keyword keyword = map_field_by.get(field);
    if (keyword == null) {
      String name = field.getOptions().getExtension(Extensions.mapBy);
      keyword = intern(name);
      map_field_by.putIfAbsent(field, keyword);
    }
    return keyword == k_null ? null : keyword;
  }

  static Keyword k_key    = Keyword.intern(Symbol.intern("key"));
  static Keyword k_val    = Keyword.intern(Symbol.intern("val"));
  static Keyword k_item   = Keyword.intern(Symbol.intern("item"));
  static Keyword k_exists = Keyword.intern(Symbol.intern("exists"));
  static protected Object fromProtoValue(Descriptors.FieldDescriptor field, Object value) {
    return fromProtoValue(field, value, true);
  }

  static protected Object fromProtoValue(Descriptors.FieldDescriptor field, Object value, boolean use_extensions) {
    if (value instanceof List) {
      List values = (List) value;
      Iterator iterator = values.iterator();

      if (use_extensions) {
        Keyword map_field_by = mapFieldBy(field);
        DescriptorProtos.FieldOptions options = field.getOptions();
        if (map_field_by != null) {
          ITransientMap map = PersistentHashMap.EMPTY.asTransient();
          while (iterator.hasNext()) {
            PersistentProtocolBufferMap v = (PersistentProtocolBufferMap) fromProtoValue(field, iterator.next());
            Object k = v.valAt(map_field_by);
            PersistentProtocolBufferMap existing = (PersistentProtocolBufferMap) map.valAt(k);
            if (existing != null) {
              map.assoc(k, existing.append(v));
            } else {
              map.assoc(k, v);
            }
          }
          return map.persistent();
        } else if (options.getExtension(Extensions.counter)) {
          Object count = iterator.next();
          while (iterator.hasNext()) {
            count = Numbers.add(count, iterator.next());
          }
          return count;
        } else if (options.getExtension(Extensions.succession)) {
          return fromProtoValue(field, values.get(values.size() - 1));
        } else if (options.getExtension(Extensions.map)) {
          Def def = PersistentProtocolBufferMap.Def.create(field.getMessageType());
          Descriptors.FieldDescriptor key_field = def.fieldDescriptor(k_key);
          Descriptors.FieldDescriptor val_field = def.fieldDescriptor(k_val);

          ITransientMap map = PersistentHashMap.EMPTY.asTransient();
          while (iterator.hasNext()) {
            DynamicMessage message = (DynamicMessage) iterator.next();
            Object k = fromProtoValue(key_field, message.getField(key_field));
            Object v = fromProtoValue(val_field, message.getField(val_field));
            Object existing = map.valAt(k);
            if (existing != null && existing instanceof IPersistentCollection) {
              map.assoc(k, ((IPersistentCollection) existing).cons(v));
            } else {
              map.assoc(k, v);
            }
          }
          return map.persistent();
        } else if (options.getExtension(Extensions.set)) {
          Def def = PersistentProtocolBufferMap.Def.create(field.getMessageType());
          Descriptors.FieldDescriptor item_field  = def.fieldDescriptor(k_item);
          Descriptors.FieldDescriptor exists_field = def.fieldDescriptor(k_exists);

          ITransientSet set = (ITransientSet) OrderedSet.EMPTY.asTransient();
          while (iterator.hasNext()) {
            DynamicMessage message = (DynamicMessage) iterator.next();
            Object  item   = fromProtoValue(item_field, message.getField(item_field));
            Boolean exists = (Boolean) message.getField(exists_field);

            if (exists) {
              set.conj(item);
            } else {
              try {
                set.disjoin(item);
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          }
          return set.persistent();
        }
      }
      List<Object> list = new ArrayList<Object>(values.size());
      while (iterator.hasNext()) {
        list.add(fromProtoValue(field, iterator.next(), use_extensions));
      }
      return PersistentVector.create(list);
    } else {
      switch (field.getJavaType()) {
      case ENUM:
        Descriptors.EnumValueDescriptor e = (Descriptors.EnumValueDescriptor) value;
        return enumToKeyword(e);
      case MESSAGE:
        Def def = PersistentProtocolBufferMap.Def.create(field.getMessageType());
        DynamicMessage message = (DynamicMessage) value;

        // Total hack because getField() doesn't return an empty array for repeated messages.
        if (field.isRepeated() && !message.isInitialized()) return fromProtoValue(field, new ArrayList(), use_extensions);

        return new PersistentProtocolBufferMap(null, def, message);
      default:
        if (use_extensions &&
            field.getOptions().getExtension(Extensions.nullable) &&
            field.getOptions().getExtension(nullExtension(field)).equals(value))
          return null;
        return value;
      }
    }
  }

  static protected Object toProtoValue(Descriptors.FieldDescriptor field, Object value) {
    if (value == null && field.getOptions().getExtension(Extensions.nullable))
      value = field.getOptions().getExtension(nullExtension(field));

    switch (field.getJavaType()) {
    case LONG:
      if (value instanceof Long) return value;
      return new Long(((Integer) value).longValue());
    case INT:
      if (value instanceof Integer) return value;
      return new Integer(((Long) value).intValue());
    case FLOAT:
      if (value instanceof Integer) return new Float((Integer) value * 1.0);
      if (value instanceof Double)  return new Float((Double) value);
      return value;
    case DOUBLE:
      if (value instanceof Integer) return new Double((Integer) value * 1.0);
      if (value instanceof Float)   return new Double((Float) value);
      return value;
    case ENUM:
      String name = (value instanceof Keyword) ? ((Keyword) value).getName() : (String) value;
      name = name.toUpperCase().replaceAll("-","_");
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

  static protected GeneratedMessage.GeneratedExtension nullExtension(Descriptors.FieldDescriptor field) {
    switch (field.getJavaType()) {
    case LONG:   return Extensions.nullLong;
    case INT:    return Extensions.nullInt;
    case FLOAT:  return Extensions.nullFloat;
    case DOUBLE: return Extensions.nullDouble;
    case STRING: return Extensions.nullString;
    }
    return null;
  }

  protected void addRepeatedField(DynamicMessage.Builder builder, Descriptors.FieldDescriptor field, Object value) {
    try {
      builder.addRepeatedField(field, value);
    } catch (IllegalArgumentException e) {
      String msg = String.format("error adding %s to %s field %s",
                                 value, field.getJavaType().toString().toLowerCase(),
                                 field.getFullName());
      throw new IllegalArgumentException(msg, e);
    }
  }

  protected void setField(DynamicMessage.Builder builder, Descriptors.FieldDescriptor field, Object value) {
    try {
      builder.setField(field, value);
    } catch (IllegalArgumentException e) {
      String msg = String.format("error setting %s field %s to %s",
                                 field.getJavaType().toString().toLowerCase(),
                                 field.getFullName(), value);
      throw new IllegalArgumentException(msg, e);
    }
  }

  protected void addField(DynamicMessage.Builder builder, Object key, Object value) {
    if (key == null) return;
    Descriptors.FieldDescriptor field = def.fieldDescriptor(key);
    if (field == null) return;
    if (value == null && !(field.getOptions().getExtension(Extensions.nullable))) return;
    boolean set = field.getOptions().getExtension(Extensions.set);

    if (field.isRepeated()) {
      builder.clearField(field);
      if (value instanceof Sequential && !set) {
        for (ISeq s = RT.seq(value); s != null; s = s.next()) {
          Object v = toProtoValue(field, s.first());
          addRepeatedField(builder,field, v);
        }
      } else {
        Keyword map_field_by = mapFieldBy(field);
        if (map_field_by != null) {
          for (ISeq s = RT.seq(value); s != null; s = s.next()) {
            Map.Entry e = (Map.Entry) s.first();
            IPersistentMap map = (IPersistentMap) e.getValue();
            Object k = e.getKey();
            Object v = toProtoValue(field, map.assoc(map_field_by, k).assoc(map_field_by.getName(), k));
            addRepeatedField(builder, field, v);
          }
        } else if (field.getOptions().getExtension(Extensions.map)) {
          for (ISeq s = RT.seq(value); s != null; s = s.next()) {
            Map.Entry e = (Map.Entry) s.first();
            Object[] map = {k_key, e.getKey(), k_val, e.getValue()};
            Object v = toProtoValue(field, new PersistentArrayMap(map));
            addRepeatedField(builder, field, v);
          }
        } else if (set) {
          if (value instanceof IPersistentMap) {
            for (ISeq s = RT.seq(value); s != null; s = s.next()) {
              Map.Entry e = (Map.Entry) s.first();
              Object[] map = {k_item, e.getKey(), k_exists, e.getValue()};
              Object v = toProtoValue(field, new PersistentArrayMap(map));
              addRepeatedField(builder, field, v);
            }
          } else {
            for (ISeq s = RT.seq(value); s != null; s = s.next()) {
              Object[] map = {k_item, s.first(), k_exists, true};
              Object v = toProtoValue(field, new PersistentArrayMap(map));
              addRepeatedField(builder, field, v);
            }
          }
        } else {
          Object v = toProtoValue(field, value);
          addRepeatedField(builder, field, v);
        }
      }
    } else {
      Object v = toProtoValue(field, value);
      if (v instanceof DynamicMessage) {
        v = ((DynamicMessage) builder.getField(field)).toBuilder().mergeFrom((DynamicMessage) v).build();
      }
      setField(builder, field, v);
    }
  }

  public PersistentProtocolBufferMap withMeta(IPersistentMap meta) {
    if (meta == meta()) return this;
    return new PersistentProtocolBufferMap(meta(), def, message);
  }

  public IPersistentMap meta(){
    return _meta;
  }

  public boolean containsKey(Object key) {
    Descriptors.FieldDescriptor field = def.fieldDescriptor(key);
    if (field == null) {
      return false;
    } else if (field.isRepeated()) {
      return message().getRepeatedFieldCount(field) > 0;
    } else {
      return message().hasField(field);
    }
  }

  public IMapEntry entryAt(Object key) {
    Object value = valAt(key);
    return (value == null) ? null : new MapEntry(key, value);
  }

  public Object valAt(Object key) {
    return getValAt(key, true);
  }

  public Object valAt(Object key, Object notFound) {
    Object value = valAt(key);
    return (value == null) ? notFound : value;
  }

  public Object getValAt(Object key, boolean use_extensions) {
    Descriptors.FieldDescriptor field = def.fieldDescriptor(key);
    if (field == null) return null;
    if (field.isRepeated() && message().getRepeatedFieldCount(field) == 0) return null;
    if (!field.isRepeated() && !field.hasDefaultValue() && !message().hasField(field)) return null;
    return fromProtoValue(field, message().getField(field), use_extensions);
  }

  public IPersistentMap assoc(Object key, Object value) {
    DynamicMessage.Builder builder = builder();
    Descriptors.FieldDescriptor field = def.fieldDescriptor(key);

    addField(builder, field, value);
    return new PersistentProtocolBufferMap(meta(), def, builder);
  }

  public IPersistentMap assocEx(Object key, Object value) throws Exception {
    if(containsKey(key)) throw new Exception("Key already present");
    return assoc(key, value);
  }

  public IPersistentCollection cons(Object o) {
    DynamicMessage.Builder builder = builder();
    if (o instanceof Map.Entry) {
      Map.Entry e = (Map.Entry) o;
      addField(builder, e.getKey(), e.getValue());
    } else if (o instanceof IPersistentVector) {
      IPersistentVector v = (IPersistentVector) o;
      if (v.count() != 2) throw new IllegalArgumentException("Vector arg to map conj must be a pair");
      addField(builder, v.nth(0), v.nth(1));
    } else {
      for (ISeq s = RT.seq(o); s != null; s = s.next()) {
        Map.Entry e = (Map.Entry) s.first();
        addField(builder, e.getKey(), e.getValue());
      }
    }
    return new PersistentProtocolBufferMap(meta(), def, builder);
  }

  public PersistentProtocolBufferMap append(IPersistentMap map) {
    PersistentProtocolBufferMap proto;
    if (map instanceof PersistentProtocolBufferMap) {
      proto = (PersistentProtocolBufferMap) map;
    } else {
      proto = construct(def, map);
    }
    return new PersistentProtocolBufferMap(meta(), def, builder().mergeFrom(proto.message()));
  }

  public IPersistentMap without(Object key) throws Exception {
    Descriptors.FieldDescriptor field = def.fieldDescriptor(key);
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
    return Seq.create(null, this, RT.seq(def.type.getFields()));
  }

  public IPersistentCollection empty() {
    DynamicMessage.Builder builder = builder();
    builder.clear();
    return new PersistentProtocolBufferMap(meta(), def, builder);
  }

  static class Seq extends ASeq {
    final PersistentProtocolBufferMap proto;
    final MapEntry first;
    final ISeq fields;

    static public Seq create(IPersistentMap meta, PersistentProtocolBufferMap proto, ISeq fields){
      for (ISeq s = fields; s != null; s = s.next()) {
        Descriptors.FieldDescriptor field = (Descriptors.FieldDescriptor) s.first();
        Keyword k = intern(field.getName());
        Object  v = proto.valAt(k);
        if (v != null) return new Seq(meta, proto, new MapEntry(k, v), s);
      }
      return null;
    }

    protected Seq(IPersistentMap meta, PersistentProtocolBufferMap proto, MapEntry first, ISeq fields){
      super(meta);
      this.proto  = proto;
      this.first  = first;
      this.fields = fields;
    }

    public Obj withMeta(IPersistentMap meta) {
      if (meta != meta()) return new Seq(meta, proto, first, fields);
      return this;
    }

    public Object first() {
      return first;
    }

    public ISeq next() {
      return create(meta(), proto, fields.next());
    }
  }
}