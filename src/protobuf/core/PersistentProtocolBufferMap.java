/**
 *   Copyright (c) Justin Balthrop. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 *       the terms of this license.
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
import com.google.protobuf.DescriptorProtos.FieldOptions;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.GeneratedMessage;

public class PersistentProtocolBufferMap extends APersistentMap implements IObj {
  public static class Def {
    public static interface NamingStrategy {
      /**
       * Given a Clojure map key, return the string to be used as the protobuf message field name.
       */
      String protoName(Object clojureName);
      
      /**
       * Given a protobuf message field name, return a Clojure object suitable for use as a map key.
       */
      Object clojureName(String protoName);
    }

    // we want this to work for anything Named, so use clojure.core/name
    public static final Var NAME_VAR = Var.intern(RT.CLOJURE_NS, Symbol.intern("name"));
    public static final String nameStr(Object named) {
      try {
        return (String)((IFn)NAME_VAR.deref()).invoke(named);
      } catch (Exception e) {
        return null;
      }
    }

    public static final NamingStrategy protobufNames = new NamingStrategy() {
        public String protoName(Object name) {return nameStr(name);}
        public Object clojureName(String name) {return Keyword.intern(name.toLowerCase());}
        public String toString() {return "[protobuf names]";}
      };
    public static final NamingStrategy convertUnderscores = new NamingStrategy() {
        public String protoName(Object name) {return nameStr(name).replaceAll("-", "_");}
        public Object clojureName(String name) {return Keyword.intern(name.replaceAll("_", "-").toLowerCase());}
        public String toString() {return "[convert underscores]";}
      };

    final Descriptors.Descriptor type;
    public final NamingStrategy namingStrategy;

    public static final Object NULL = new Object();
    // keys should be FieldDescriptors, except that NULL is used as a replacement for real null
    ConcurrentHashMap<Object, Object> key_to_field;
    
    static ConcurrentHashMap<NamingStrategy, ConcurrentHashMap<Descriptors.Descriptor, Def>> type_to_def = new ConcurrentHashMap<NamingStrategy, ConcurrentHashMap<Descriptors.Descriptor, Def>>();

    public static Def create(Class<?> c, NamingStrategy strat) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
      Descriptors.Descriptor type = (Descriptors.Descriptor) c.getMethod("getDescriptor").invoke(null);
      return create(type, strat);
    }

    public static Def create(Descriptors.Descriptor type, NamingStrategy strat) {
      ConcurrentHashMap<Descriptors.Descriptor, Def> defCache = type_to_def.get(strat);
      if (defCache == null) {
        defCache = new ConcurrentHashMap<Descriptors.Descriptor, Def>();
        ConcurrentHashMap<Descriptors.Descriptor, Def> previous = type_to_def.putIfAbsent(strat, defCache);
        if (previous != null) defCache = previous;
      }

      Def def = defCache.get(type);
      if (def == null) {
        def = new Def(type, strat);
        defCache.putIfAbsent(type, def);
      }
      return def;
    }

    protected Def(Descriptors.Descriptor type, NamingStrategy strat) {
      this.type           = type;
      this.key_to_field   = new ConcurrentHashMap<Object, Object>();
      this.namingStrategy = strat;
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
        return (Descriptors.FieldDescriptor)key;
      } else {
        Object field = key_to_field.get(key);
        if (field != null) {
          if (field == NULL) {
            return null;
          }
          return (Descriptors.FieldDescriptor)field;
        }
        else {
          field = type.findFieldByName(namingStrategy.protoName(key));
          key_to_field.putIfAbsent(key, field == null ? NULL : field);
        }
        return (Descriptors.FieldDescriptor)field;
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
    static final ConcurrentHashMap<NamingStrategy, ConcurrentHashMap<String, Object>> caches =
      new ConcurrentHashMap<NamingStrategy, ConcurrentHashMap<String, Object>>();
    public Object intern(String name) {
      ConcurrentHashMap<String, Object> nameCache = caches.get(namingStrategy);
      if (nameCache == null) {
        nameCache = new ConcurrentHashMap<String, Object>();
        ConcurrentHashMap<String, Object> existing = caches.putIfAbsent(namingStrategy, nameCache);
        if (existing != null) nameCache = existing;
      }
      Object clojureName = nameCache.get(name);
      if (clojureName == null) {
        clojureName = namingStrategy.clojureName(name);
        Object existing = nameCache.putIfAbsent(name, clojureName);
        if (existing != null) clojureName = existing;
      }
      return clojureName;
    }

    public Object clojureEnumValue(Descriptors.EnumValueDescriptor enum_value) {
      return intern(enum_value.getName());
    }

    static final Object k_null = Keyword.intern(Symbol.intern(""));
    protected Object mapFieldBy(Descriptors.FieldDescriptor field) {
      Object key = intern(field.getOptions().getExtension(Extensions.mapBy));
      return key == k_null ? null : key;
    }
  }

  public final Def def;
  private final DynamicMessage message;
  private final IPersistentMap _meta;
  private final IPersistentMap ext;

  private DynamicMessage built_message;

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
    this.ext     = null;
    this.def     = def;
    this.message = null;
  }

  protected PersistentProtocolBufferMap(IPersistentMap meta, Def def, DynamicMessage message) {
    this._meta   = meta;
    this.ext     = null;
    this.def     = def;
    this.message = message;
  }

  protected PersistentProtocolBufferMap(IPersistentMap meta, IPersistentMap ext, Def def, DynamicMessage message) {
    this._meta   = meta;
    this.ext     = ext;
    this.def     = def;
    this.message = message;
  }

  protected PersistentProtocolBufferMap(IPersistentMap meta, Def def, DynamicMessage.Builder builder) {
    this._meta   = meta;
    this.ext     = null;
    this.def     = def;
    this.message = builder.build();
  }

  protected PersistentProtocolBufferMap(IPersistentMap meta, IPersistentMap ext, Def def, DynamicMessage.Builder builder) {
    this._meta   = meta;
    this.ext     = ext;
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

  protected Object fromProtoValue(Descriptors.FieldDescriptor field, Object value) {
    return fromProtoValue(field, value, true);
  }

  static Keyword k_key    = Keyword.intern("key");
  static Keyword k_val    = Keyword.intern("val");
  static Keyword k_item   = Keyword.intern("item");
  static Keyword k_exists = Keyword.intern("exists");
  protected Object fromProtoValue(Descriptors.FieldDescriptor field, Object value, boolean use_extensions) {
    if (value instanceof List) {
      List<?> values = (List<?>) value;
      Iterator<?> iterator = values.iterator();

      if (use_extensions) {
        Object map_field_by = def.mapFieldBy(field);
        DescriptorProtos.FieldOptions options = field.getOptions();
        if (map_field_by != null) {
          ITransientMap map = PersistentHashMap.EMPTY.asTransient();
          while (iterator.hasNext()) {
            PersistentProtocolBufferMap v = (PersistentProtocolBufferMap) fromProtoValue(field, iterator.next());
            Object k = v.valAt(map_field_by);
            PersistentProtocolBufferMap existing = (PersistentProtocolBufferMap) map.valAt(k);
            if (existing != null) {
              map = map.assoc(k, existing.append(v));
            } else {
              map = map.assoc(k, v);
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
          Descriptors.Descriptor type = field.getMessageType();
          Descriptors.FieldDescriptor key_field = type.findFieldByName("key");
          Descriptors.FieldDescriptor val_field = type.findFieldByName("val");

          ITransientMap map = PersistentHashMap.EMPTY.asTransient();
          while (iterator.hasNext()) {
            DynamicMessage message = (DynamicMessage) iterator.next();
            Object k = fromProtoValue(key_field, message.getField(key_field));
            Object v = fromProtoValue(val_field, message.getField(val_field));
            Object existing = map.valAt(k);
            if (existing instanceof IPersistentCollection) {
              map = map.assoc(k, ((IPersistentCollection) existing).cons(v));
            } else {
              map = map.assoc(k, v);
            }
          }
          return map.persistent();
        } else if (options.getExtension(Extensions.set)) {
          Descriptors.Descriptor type = field.getMessageType();
          Descriptors.FieldDescriptor item_field = type.findFieldByName("item");
          Descriptors.FieldDescriptor exists_field = type.findFieldByName("exists");

          ITransientSet set = (ITransientSet) OrderedSet.EMPTY.asTransient();
          while (iterator.hasNext()) {
            DynamicMessage message = (DynamicMessage) iterator.next();
            Object  item   = fromProtoValue(item_field, message.getField(item_field));
            Boolean exists = (Boolean) message.getField(exists_field);

            if (exists) {
              set = (ITransientSet)set.conj(item);
            } else {
              try {
                set = set.disjoin(item);
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
        if (use_extensions &&
            field.getOptions().getExtension(Extensions.nullable) &&
            field.getOptions().getExtension(nullExtension(field)).equals(e.getNumber())) {
          return null;
        } else {
          return def.clojureEnumValue(e);
        }
      case MESSAGE:
        Def fieldDef = PersistentProtocolBufferMap.Def.create(field.getMessageType(), this.def.namingStrategy);
        DynamicMessage message = (DynamicMessage) value;

        // Total hack because getField() doesn't return an empty array for repeated messages.
        if (field.isRepeated() && !message.isInitialized()) return fromProtoValue(field, new ArrayList<Object>(), use_extensions);

        return new PersistentProtocolBufferMap(null, fieldDef, message);
      default:
        if (use_extensions &&
            field.getOptions().getExtension(Extensions.nullable) &&
            field.getOptions().getExtension(nullExtension(field)).equals(value)) {
          return null;
        } else {
          return value;
        }
      }
    }
  }

  protected Object toProtoValue(Descriptors.FieldDescriptor field, Object value) {
    if (value == null && field.getOptions().getExtension(Extensions.nullable)) {
      value = field.getOptions().getExtension(nullExtension(field));

      if (field.getJavaType() == Descriptors.FieldDescriptor.JavaType.ENUM) {
        Descriptors.EnumDescriptor      enum_type  = field.getEnumType();
        Descriptors.EnumValueDescriptor enum_value = enum_type.findValueByNumber((Integer) value);
        if (enum_value == null) {
          PrintWriter err = (PrintWriter) RT.ERR.deref();
          err.format("invalid enum number %s for enum type %s\n", value, enum_type.getFullName());
        }
        return enum_value;
      }
    }

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
      String name = def.namingStrategy.protoName(value);
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
        Def fieldDef  = PersistentProtocolBufferMap.Def.create(field.getMessageType(), this.def.namingStrategy);
        protobuf = PersistentProtocolBufferMap.construct(fieldDef, (IPersistentMap) value);
      }
      return protobuf.message();
    default:
      return value;
    }
  }

  static protected GeneratedMessage.GeneratedExtension<FieldOptions, ?> nullExtension(Descriptors.FieldDescriptor field) {
    switch (field.getJavaType()) {
    case LONG:   return Extensions.nullLong;
    case INT:    return Extensions.nullInt;
    case FLOAT:  return Extensions.nullFloat;
    case DOUBLE: return Extensions.nullDouble;
    case STRING: return Extensions.nullString;
    case ENUM:   return Extensions.nullEnum;
    }
    return null;
  }

  protected void addRepeatedField(DynamicMessage.Builder builder, Descriptors.FieldDescriptor field, Object value) {
    try {
      builder.addRepeatedField(field, value);
    } catch (Exception e) {
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

  protected DynamicMessage.Builder addField(DynamicMessage.Builder builder, Object key, Object value) {
    if (key == null) return builder;
    Descriptors.FieldDescriptor field = def.fieldDescriptor(key);
    if (field == null) return builder;
    if (value == null && !(field.getOptions().getExtension(Extensions.nullable))) return builder;
    boolean set = field.getOptions().getExtension(Extensions.set);

    if (field.isRepeated()) {
      builder.clearField(field);
      if (value instanceof Sequential && !set) {
        for (ISeq s = RT.seq(value); s != null; s = s.next()) {
          Object v = toProtoValue(field, s.first());
          addRepeatedField(builder, field, v);
        }
      } else {
        Object map_field_by = def.mapFieldBy(field);
        if (map_field_by != null) {
          for (ISeq s = RT.seq(value); s != null; s = s.next()) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) s.first();
            IPersistentMap map = (IPersistentMap) e.getValue();
            Object k = e.getKey();
            Object v = toProtoValue(field, map.assoc(map_field_by, k));
            addRepeatedField(builder, field, v);
          }
        } else if (field.getOptions().getExtension(Extensions.map)) {
          for (ISeq s = RT.seq(value); s != null; s = s.next()) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) s.first();
            Object[] map = {k_key, e.getKey(), k_val, e.getValue()};
            addRepeatedField(builder, field, toProtoValue(field, new PersistentArrayMap(map)));
          }
        } else if (set) {
          Object k, v;
          boolean isMap = (value instanceof IPersistentMap);
          for (ISeq s = RT.seq(value); s != null; s = s.next()) {
            if (isMap) {
              Map.Entry<?, ?> e = (Map.Entry<?, ?>) s.first();
              k = e.getKey(); v = e.getValue();
            } else {
              k = s.first(); v = true;
            }
            Object[] map = {k_item, k, k_exists, v};
            addRepeatedField(builder, field, toProtoValue(field, new PersistentArrayMap(map)));
          }
        } else {
          addRepeatedField(builder, field, toProtoValue(field, value));
        }
      }
    } else {
      Object v = toProtoValue(field, value);
      if (v instanceof DynamicMessage) {
        v = ((DynamicMessage) builder.getField(field)).toBuilder().mergeFrom((DynamicMessage) v).build();
      }
      setField(builder, field, v);
    }

    return builder;
  }

  public PersistentProtocolBufferMap withMeta(IPersistentMap meta) {
    if (meta == meta()) return this;
    return new PersistentProtocolBufferMap(meta, ext, def, message);
  }

  public IPersistentMap meta(){
    return _meta;
  }

  public boolean containsKey(Object key) {
    return protoContainsKey(key) || RT.booleanCast(RT.contains(ext, key));
  }

  private boolean protoContainsKey(Object key) {
    Descriptors.FieldDescriptor field = def.fieldDescriptor(key);
    if (field == null) {
      return false;
    } else if (field.isRepeated()) {
      return message().getRepeatedFieldCount(field) > 0;
    } else {
      return message().hasField(field) || field.hasDefaultValue();
    }
  }

  private static final Object sentinel = new Object();

  public IMapEntry entryAt(Object key) {
    Object value = valAt(key, sentinel);
    return (value == sentinel) ? null : new MapEntry(key, value);
  }

  public Object valAt(Object key) {
    return getValAt(key, true);
  }

  public Object valAt(Object key, Object notFound) {
    return getValAt(key, notFound, true);
  }

  public Object getValAt(Object key, boolean use_extensions) {
    Object val = getValAt(key, sentinel, use_extensions);
    return (val == sentinel) ? null : val;
  }

  public Object getValAt(Object key, Object notFound, boolean use_extensions) {
    Descriptors.FieldDescriptor field = def.fieldDescriptor(key);
    if (protoContainsKey(key)) {
      return fromProtoValue(field, message().getField(field), use_extensions);
    } else {
      return RT.get(ext, key, notFound);
    }
  }

  public IPersistentMap assoc(Object key, Object value) {
    Descriptors.FieldDescriptor field = def.fieldDescriptor(key);

    if (field != null) {
      return new PersistentProtocolBufferMap(meta(), ext, def,
                                             addField(builder(), field, value));
    } else {
      return new PersistentProtocolBufferMap(meta(), (IPersistentMap)RT.assoc(ext, key, value),
                                             def, builder());
    }
  }

  public IPersistentMap assocEx(Object key, Object value) throws Exception {
    if(containsKey(key)) throw new Exception("Key already present");
    return assoc(key, value);
  }

  public IPersistentCollection cons(Object o) {
    DynamicMessage.Builder builder = builder();
    if (o instanceof Map.Entry) {
      Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
      addField(builder, e.getKey(), e.getValue());
    } else if (o instanceof IPersistentVector) {
      IPersistentVector v = (IPersistentVector) o;
      if (v.count() != 2) throw new IllegalArgumentException("Vector arg to map conj must be a pair");
      addField(builder, v.nth(0), v.nth(1));
    } else {
      for (ISeq s = RT.seq(o); s != null; s = s.next()) {
        Map.Entry<?, ?> e = (Map.Entry<?, ?>) s.first();
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
    return new PersistentProtocolBufferMap(meta(), ext, def, builder().mergeFrom(proto.message()));
  }

  public IPersistentMap without(Object key) throws Exception {
    Descriptors.FieldDescriptor field = def.fieldDescriptor(key);
    if (field == null) {
      IPersistentMap newExt = (IPersistentMap)RT.dissoc(ext, key);
      if (newExt == ext) {
        return this;
      }
      return new PersistentProtocolBufferMap(meta(), newExt, def, builder());
    }
    if (field.isRequired()) throw new Exception("Can't remove required field");

    return new PersistentProtocolBufferMap(meta(), ext, def, builder().clearField(field));
  }

  public Iterator<?> iterator() {
    return new SeqIterator(seq());
  }

  public int count() {
    DynamicMessage message = message();
    int count = RT.count(ext);
    for (Descriptors.FieldDescriptor field : def.type.getFields()) {
      if (protoContainsKey(field)) {
        count++;
      }
    }
    return count;
  }

  public ISeq seq() {
    return Seq.create(null, this, RT.seq(def.type.getFields()));
  }

  public IPersistentCollection empty() {
    return new PersistentProtocolBufferMap(meta(), null, def, builder().clear());
  }

  private static class Seq extends ASeq {
    private final PersistentProtocolBufferMap proto;
    private final MapEntry first;
    private final ISeq fields;

    public static ISeq create(IPersistentMap meta, PersistentProtocolBufferMap proto, ISeq fields){
      for (ISeq s = fields; s != null; s = s.next()) {
        Descriptors.FieldDescriptor field = (Descriptors.FieldDescriptor) s.first();
        Object k = proto.def.intern(field.getName());
        Object  v = proto.valAt(k, sentinel);
        if (v != sentinel) return new Seq(meta, proto, new MapEntry(k, v), s);
      }
      return RT.seq(proto.ext);
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