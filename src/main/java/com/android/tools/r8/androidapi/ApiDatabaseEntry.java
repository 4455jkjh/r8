// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import static com.android.tools.r8.lightir.ByteUtils.isU2;

import com.android.tools.r8.apimodel.FieldTypelessReference;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.internal.ThrowingFunction;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Represents an entry in the API database. This can be a type, a field, or a method.
 *
 * <p>To save space in the database, package names, class names, method names, and signatures are
 * pooled in a constant pool (see {@link ConstantPoolEntry}).
 *
 * <p>Each entry can be serialized into a "unique descriptor" which is a compact byte array
 * containing identifiers and constant pool indices. See {@link
 * com.android.tools.r8.apimodel.AndroidApiHashingDatabaseBuilderGenerator} for the serialization
 * format.
 */
public abstract class ApiDatabaseEntry {

  private static final byte TYPE_IDENTIFIER = 0;
  private static final byte FIELD_IDENTIFIER = 1;
  private static final byte METHOD_IDENTIFIER = 2;

  private static final byte[] NON_EXISTING_DESCRIPTOR = new byte[0];

  public static byte[] getNonExistingDescriptor() {
    return NON_EXISTING_DESCRIPTOR;
  }

  public abstract int apiDatabaseHashCode();

  public abstract byte[] getUniqueDescriptor(
      ThrowingFunction<ConstantPoolEntry, Integer, IOException> constantPoolLookup)
      throws IOException;

  @Override
  public final int hashCode() {
    return apiDatabaseHashCode();
  }

  @Override
  public abstract boolean equals(Object obj);

  public static ApiDatabaseEntry of(DexReference reference) {
    if (reference.isDexType()) {
      return new TypeEntry(reference.asDexType().getDescriptor().content);
    } else if (reference.isDexMethod()) {
      DexMethod method = reference.asDexMethod();
      byte[][] parameters = new byte[method.proto.parameters.values.length][];
      for (int i = 0; i < parameters.length; i++) {
        parameters[i] = method.proto.parameters.values[i].getDescriptor().content;
      }
      return new MethodEntry(
          method.getHolderType().getDescriptor().content,
          method.getName().content,
          parameters,
          method.getReturnType().getDescriptor().content);
    } else {
      assert reference.isDexField();
      DexField field = reference.asDexField();
      return new FieldEntry(field.getHolderType().getDescriptor().content, field.getName().content);
    }
  }

  public static ApiDatabaseEntry of(FieldTypelessReference reference) {
    return new FieldEntry(
        DexString.encodeToMutf8(reference.getHolderClass().getDescriptor()),
        DexString.encodeToMutf8(reference.getFieldName()));
  }

  public static ApiDatabaseEntry of(ClassReference reference) {
    return new TypeEntry(DexString.encodeToMutf8(reference.getDescriptor()));
  }

  public static ApiDatabaseEntry of(MethodReference reference) {
    byte[][] parameters = new byte[reference.getFormalTypes().size()][];
    for (int i = 0; i < parameters.length; i++) {
      parameters[i] = DexString.encodeToMutf8(reference.getFormalTypes().get(i).getDescriptor());
    }
    return new MethodEntry(
        DexString.encodeToMutf8(reference.getHolderClass().getDescriptor()),
        DexString.encodeToMutf8(reference.getMethodName()),
        parameters,
        DexString.encodeToMutf8(
            reference.getReturnType() == null ? "V" : reference.getReturnType().getDescriptor()));
  }

  public static ApiDatabaseEntry of(FieldReference reference) {
    return new FieldEntry(
        DexString.encodeToMutf8(reference.getHolderClass().getDescriptor()),
        DexString.encodeToMutf8(reference.getFieldName()));
  }

  private static byte getFirstByteFromShort(int value) {
    assert isU2(value);
    return (byte) (value >> 8);
  }

  private static byte getSecondByteFromShort(int value) {
    assert isU2(value);
    return (byte) value;
  }

  /**
   * Represents an entry in the database's constant pool. Wraps the raw UTF-8 bytes of a string
   * (e.g., class descriptor, method name, or type descriptor).
   */
  public static final class ConstantPoolEntry {
    private final byte[] bytes;
    private final int hashCode;

    private ConstantPoolEntry(byte[] bytes) {
      this.bytes = bytes;
      this.hashCode = Arrays.hashCode(bytes);
    }

    byte[] getBytes() {
      return bytes;
    }

    public int getLength() {
      return bytes.length;
    }

    public void writeTo(OutputStream os) throws IOException {
      os.write(bytes);
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof ConstantPoolEntry) {
        return Arrays.equals(this.bytes, ((ConstantPoolEntry) obj).bytes);
      }
      return false;
    }
  }

  private static class TypeEntry extends ApiDatabaseEntry {
    private final byte[] type;

    public TypeEntry(byte[] type) {
      this.type = type;
    }

    @Override
    public int apiDatabaseHashCode() {
      return Arrays.hashCode(type);
    }

    @Override
    public byte[] getUniqueDescriptor(
        ThrowingFunction<ConstantPoolEntry, Integer, IOException> constantPoolLookup)
        throws IOException {
      int typeId = constantPoolLookup.apply(new ConstantPoolEntry(type));
      if (typeId < 0) {
        return NON_EXISTING_DESCRIPTOR;
      }
      return new byte[] {
        TYPE_IDENTIFIER, getFirstByteFromShort(typeId), getSecondByteFromShort(typeId)
      };
    }


    @Override
    public boolean equals(Object obj) {
      return obj instanceof TypeEntry && Arrays.equals(this.type, ((TypeEntry) obj).type);
    }
  }

  private static class MethodEntry extends ApiDatabaseEntry {
    private final byte[] holder;
    private final byte[] name;
    private final byte[][] parameters;
    private final byte[] returnType;

    public MethodEntry(byte[] holder, byte[] name, byte[][] parameters, byte[] returnType) {
      this.holder = holder;
      this.name = name;
      this.parameters = parameters;
      this.returnType = returnType;
    }

    @Override
    public int apiDatabaseHashCode() {
      return Arrays.deepHashCode(new Object[] {holder, name, parameters, returnType});
    }

    @Override
    public byte[] getUniqueDescriptor(
        ThrowingFunction<ConstantPoolEntry, Integer, IOException> constantPoolLookup)
        throws IOException {
      int holderId = constantPoolLookup.apply(new ConstantPoolEntry(holder));
      if (holderId < 0) {
        return NON_EXISTING_DESCRIPTOR;
      }
      int nameId = constantPoolLookup.apply(new ConstantPoolEntry(name));
      if (nameId < 0) {
        return NON_EXISTING_DESCRIPTOR;
      }
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      baos.write(METHOD_IDENTIFIER);
      baos.write(getFirstByteFromShort(holderId));
      baos.write(getSecondByteFromShort(holderId));
      baos.write(getFirstByteFromShort(nameId));
      baos.write(getSecondByteFromShort(nameId));
      for (byte[] parameter : parameters) {
        int parameterId = constantPoolLookup.apply(new ConstantPoolEntry(parameter));
        if (parameterId < 0) {
          return NON_EXISTING_DESCRIPTOR;
        }
        baos.write(getFirstByteFromShort(parameterId));
        baos.write(getSecondByteFromShort(parameterId));
      }
      int returnTypeId = constantPoolLookup.apply(new ConstantPoolEntry(returnType));
      if (returnTypeId < 0) {
        return NON_EXISTING_DESCRIPTOR;
      }
      baos.write(getFirstByteFromShort(returnTypeId));
      baos.write(getSecondByteFromShort(returnTypeId));
      return baos.toByteArray();
    }


    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof MethodEntry)) {
        return false;
      }
      MethodEntry o = (MethodEntry) obj;
      return Arrays.equals(this.holder, o.holder)
          && Arrays.equals(this.name, o.name)
          && Arrays.deepEquals(this.parameters, o.parameters)
          && Arrays.equals(this.returnType, o.returnType);
    }
  }

  private static class FieldEntry extends ApiDatabaseEntry {
    private final byte[] holder;
    private final byte[] name;

    public FieldEntry(byte[] holder, byte[] name) {
      this.holder = holder;
      this.name = name;
    }

    @Override
    public int apiDatabaseHashCode() {
      return Arrays.deepHashCode(new Object[] {holder, name});
    }

    @Override
    public byte[] getUniqueDescriptor(
        ThrowingFunction<ConstantPoolEntry, Integer, IOException> constantPoolLookup)
        throws IOException {
      int holderId = constantPoolLookup.apply(new ConstantPoolEntry(holder));
      if (holderId < 0) {
        return NON_EXISTING_DESCRIPTOR;
      }
      int nameId = constantPoolLookup.apply(new ConstantPoolEntry(name));
      if (nameId < 0) {
        return NON_EXISTING_DESCRIPTOR;
      }
      return new byte[] {
        FIELD_IDENTIFIER,
        getFirstByteFromShort(holderId),
        getSecondByteFromShort(holderId),
        getFirstByteFromShort(nameId),
        getSecondByteFromShort(nameId)
      };
    }


    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof FieldEntry)) {
        return false;
      }
      FieldEntry o = (FieldEntry) obj;
      return Arrays.equals(this.holder, o.holder) && Arrays.equals(this.name, o.name);
    }
  }
}
