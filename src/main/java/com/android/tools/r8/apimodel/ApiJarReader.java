// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.ApiDatabaseGeneratorCommand;
import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.internal.ThrowingBiConsumer;
import com.android.tools.r8.utils.internal.ThrowingConsumer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ApiJarReader {

  public static ApiJarInfo extractJarInfo(ApiDatabaseGeneratorCommand command)
      throws ApiDatabaseGeneratorException {
    var isInterfaceMap = new HashMap<ClassReference, Boolean>();
    var isStaticMap = new HashMap<MethodReference, Boolean>();
    var fields = new HashSet<FieldTypelessReference>();
    iterateClassFiles(
        command,
        classReader -> {
          JarInfoClassVisitor visitor =
              new JarInfoClassVisitor(
                  (classReference, isInterface) -> {
                    if (isInterfaceMap.containsKey(classReference)) {
                      throw new ApiDatabaseGeneratorException(
                          "Duplicate jar definitions found for: " + classReference);
                    }
                    isInterfaceMap.put(classReference, isInterface);
                  },
                  (methodReference, isStatic) -> {
                    if (isStaticMap.containsKey(methodReference)) {
                      throw new ApiDatabaseGeneratorException(
                          "Duplicate jar definitions found for: " + methodReference);
                    }
                    isStaticMap.put(methodReference, isStatic);
                  },
                  (fieldReference) -> {
                    if (fields.contains(fieldReference)) {
                      throw new ApiDatabaseGeneratorException(
                          "Duplicate jar definitions found for: " + fieldReference);
                    }
                    fields.add(fieldReference);
                  });
          int parsingOptions =
              ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;
          JarInfoClassVisitor.handleVisitCall(() -> classReader.accept(visitor, parsingOptions));
        });
    return new ApiJarInfo(isInterfaceMap, isStaticMap, fields);
  }

  private static void iterateClassFiles(
      ApiDatabaseGeneratorCommand command,
      ThrowingConsumer<ClassReader, ApiDatabaseGeneratorException> action)
      throws ApiDatabaseGeneratorException {
    for (Path jarPath : command.getJarPaths()) {
      try {
        ZipUtils.iterWithZipFileAndInputStream(
            jarPath,
            (zipFile, entry, inputStream) -> {
              if (ZipUtils.isClassFile(entry.getName())) {
                action.accept(new ClassReader(inputStream));
              }
            });
      } catch (IOException e) {
        throw new ApiDatabaseGeneratorException("Failed to extract JAR info", e);
      }
    }
  }

  private static class JarInfoClassVisitor extends ClassVisitor {

    private final ThrowingBiConsumer<ClassReference, Boolean, ApiDatabaseGeneratorException>
        isInterfaceConsumer;
    private final ThrowingBiConsumer<MethodReference, Boolean, ApiDatabaseGeneratorException>
        isStaticConsumer;
    private final ThrowingConsumer<FieldTypelessReference, ApiDatabaseGeneratorException>
        fieldConsumer;
    private ClassReference classRef;

    public JarInfoClassVisitor(
        ThrowingBiConsumer<ClassReference, Boolean, ApiDatabaseGeneratorException>
            isInterfaceConsumer,
        ThrowingBiConsumer<MethodReference, Boolean, ApiDatabaseGeneratorException>
            isStaticConsumer,
        ThrowingConsumer<FieldTypelessReference, ApiDatabaseGeneratorException> fieldConsumer) {
      super(Opcodes.ASM9);
      this.isInterfaceConsumer = isInterfaceConsumer;
      this.isStaticConsumer = isStaticConsumer;
      this.fieldConsumer = fieldConsumer;
    }

    // Checked exceptions cannot go through ClassVisitor so they have to be wrapped.
    private static class UncheckedApiDatabaseGeneratorException extends RuntimeException {
      final ApiDatabaseGeneratorException exception;

      public UncheckedApiDatabaseGeneratorException(ApiDatabaseGeneratorException exception) {
        this.exception = exception;
      }
    }

    public static void handleVisitCall(Runnable action) throws ApiDatabaseGeneratorException {
      try {
        action.run();
      } catch (UncheckedApiDatabaseGeneratorException e) {
        throw e.exception;
      }
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      classRef = Reference.classFromBinaryName(name);
      boolean isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
      try {
        isInterfaceConsumer.accept(classRef, isInterface);
      } catch (ApiDatabaseGeneratorException e) {
        throw new UncheckedApiDatabaseGeneratorException(e);
      }
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      assert classRef != null; // Visit should always be called first, making this != null;
      MethodReference methodRef = Reference.methodFromDescriptor(classRef, name, descriptor);
      boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
      try {
        isStaticConsumer.accept(methodRef, isStatic);
      } catch (ApiDatabaseGeneratorException e) {
        throw new UncheckedApiDatabaseGeneratorException(e);
      }
      return null;
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      assert classRef != null; // Visit should always be called first, making this != null;
      var fieldRef = new FieldTypelessReference(classRef, name);
      try {
        fieldConsumer.accept(fieldRef);
      } catch (ApiDatabaseGeneratorException e) {
        throw new UncheckedApiDatabaseGeneratorException(e);
      }
      return null;
    }
  }
}
