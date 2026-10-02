// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.proto;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

public abstract class LargeProtoEnumTestBase extends TestBase {

  public interface ProtocolMessageEnum extends java.io.Serializable {
    int getNumber();

    Descriptors.EnumValueDescriptor getValueDescriptor();

    Descriptors.EnumDescriptor getDescriptorForType();
  }

  public static class Descriptors {
    public static class EnumDescriptor {
      private final String name;

      public EnumDescriptor(String name) {
        this.name = name;
      }

      public String getName() {
        return name;
      }

      public EnumValueDescriptor getValue(int index) {
        return new EnumValueDescriptor(this, index);
      }
    }

    public static class EnumValueDescriptor {
      private final EnumDescriptor enumDescriptor;
      private final int index;

      public EnumValueDescriptor(EnumDescriptor enumDescriptor, int index) {
        this.enumDescriptor = enumDescriptor;
        this.index = index;
      }

      public int getIndex() {
        return index;
      }

      public int getNumber() {
        return index;
      }

      public EnumDescriptor getType() {
        return enumDescriptor;
      }
    }
  }

  public static class Internal {
    public interface EnumLite {
      int getNumber();
    }

    public interface EnumLiteMap<T> {
      T findValueByNumber(int number);
    }

    public interface EnumVerifier {
      boolean isInRange(int number);
    }
  }

  private static final String BASE_INTERNAL_NAME =
      DescriptorUtils.getInternalNameFromJavaType(LargeProtoEnumTestBase.class.getTypeName());

  private static final SimpleRemapper PROTO_REMAPPER =
      new SimpleRemapper(
          ImmutableMap.<String, String>builder()
              .put(
                  DescriptorUtils.getInternalNameFromJavaType(
                      ProtocolMessageEnum.class.getTypeName()),
                  "com/google/protobuf/ProtocolMessageEnum")
              .put(
                  DescriptorUtils.getInternalNameFromJavaType(Descriptors.class.getTypeName()),
                  "com/google/protobuf/Descriptors")
              .put(
                  DescriptorUtils.getInternalNameFromJavaType(
                      Descriptors.EnumDescriptor.class.getTypeName()),
                  "com/google/protobuf/Descriptors$EnumDescriptor")
              .put(
                  DescriptorUtils.getInternalNameFromJavaType(
                      Descriptors.EnumValueDescriptor.class.getTypeName()),
                  "com/google/protobuf/Descriptors$EnumValueDescriptor")
              .put(
                  DescriptorUtils.getInternalNameFromJavaType(Internal.class.getTypeName()),
                  "com/google/protobuf/Internal")
              .put(
                  DescriptorUtils.getInternalNameFromJavaType(
                      Internal.EnumLite.class.getTypeName()),
                  "com/google/protobuf/Internal$EnumLite")
              .put(
                  DescriptorUtils.getInternalNameFromJavaType(
                      Internal.EnumLiteMap.class.getTypeName()),
                  "com/google/protobuf/Internal$EnumLiteMap")
              .put(
                  DescriptorUtils.getInternalNameFromJavaType(
                      Internal.EnumVerifier.class.getTypeName()),
                  "com/google/protobuf/Internal$EnumVerifier")
              .build());

  private static List<byte[]> standardProtoClassFileData;
  private static List<byte[]> protoLiteClassFileData;

  private static synchronized void initializeProtoClasses() {
    if (standardProtoClassFileData != null) {
      return;
    }
    try {
      List<byte[]> standard = new ArrayList<>();
      standard.add(remapProtoClasses(ProtocolMessageEnum.class));
      standard.add(remapProtoClasses(Descriptors.class));
      standard.add(remapProtoClasses(Descriptors.EnumDescriptor.class));
      standard.add(remapProtoClasses(Descriptors.EnumValueDescriptor.class));
      standard.add(remapProtoClasses(Internal.class));
      standard.add(remapProtoClasses(Internal.EnumLiteMap.class));
      standardProtoClassFileData = standard;

      List<byte[]> lite = new ArrayList<>();
      lite.add(remapProtoClasses(Internal.class));
      lite.add(remapProtoClasses(Internal.EnumLite.class));
      lite.add(remapProtoClasses(Internal.EnumLiteMap.class));
      lite.add(remapProtoClasses(Internal.EnumVerifier.class));
      protoLiteClassFileData = lite;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static byte[] remapProtoClasses(Class<?> clazz) throws IOException {
    return remapProtoClasses(ToolHelper.getClassAsBytes(clazz));
  }

  public static byte[] remapProtoClasses(byte[] bytes) {
    ClassReader reader = new ClassReader(bytes);
    ClassWriter writer = new ClassWriter(0);
    ClassRemapper remapper =
        new ClassRemapper(writer, PROTO_REMAPPER) {
          @Override
          public void visitOuterClass(String owner, String name, String descriptor) {
            if (BASE_INTERNAL_NAME.equals(owner)) {
              return;
            }
            super.visitOuterClass(owner, name, descriptor);
          }

          @Override
          public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (BASE_INTERNAL_NAME.equals(outerName)) {
              return;
            }
            super.visitInnerClass(name, outerName, innerName, access);
          }
        };
    reader.accept(remapper, 0);
    return writer.toByteArray();
  }

  public static List<byte[]> getStandardProtoClassFileData() {
    initializeProtoClasses();
    return standardProtoClassFileData;
  }

  public static List<byte[]> getProtoLiteClassFileData() {
    initializeProtoClasses();
    return protoLiteClassFileData;
  }

  public static List<byte[]> getProgramClassFileDataForStandardProto(Class<?> testClass)
      throws IOException {
    List<byte[]> classFileData = new ArrayList<>(getStandardProtoClassFileData());
    for (Path path :
        ToolHelper.getClassFilesForInnerClasses(Collections.singletonList(testClass))) {
      classFileData.add(remapProtoClasses(Files.readAllBytes(path)));
    }
    return classFileData;
  }

  public static List<byte[]> getProgramClassFileDataForProtoLite(Class<?> testClass)
      throws IOException {
    List<byte[]> classFileData = new ArrayList<>(getProtoLiteClassFileData());
    for (Path path :
        ToolHelper.getClassFilesForInnerClasses(Collections.singletonList(testClass))) {
      classFileData.add(remapProtoClasses(Files.readAllBytes(path)));
    }
    return classFileData;
  }
}
