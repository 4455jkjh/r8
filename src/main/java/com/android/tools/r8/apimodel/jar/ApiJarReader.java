// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel.jar;

import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.internal.ThrowingConsumer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ApiJarReader {

  public static ApiJarInfo read(Path jarPath) throws ApiDatabaseGeneratorException, IOException {
    Map<String, ApiClassInfo> classes = new HashMap<>();
    readClass(
        jarPath,
        (classInfo) -> {
          if (classes.containsKey(classInfo.getBinaryName())) {
            throw new ApiDatabaseGeneratorException(
                "Duplicate classes found in JAR: " + jarPath + ", " + classInfo.getBinaryName());
          }
          classes.put(classInfo.getBinaryName(), classInfo);
        });
    return new ApiJarInfo(classes);
  }

  private static <E extends Throwable> void readClass(
      Path jarPath, ThrowingConsumer<ApiClassInfo, E> handler) throws IOException, E {
    ZipUtils.iterWithZipFileAndInputStream(
        jarPath,
        (zip, entry, input) -> {
          if (ZipUtils.isClassFile(entry.getName())) {
            handler.accept(readClass(input));
          }
        });
  }

  private static ApiClassInfo readClass(InputStream input) throws IOException {
    ClassReader reader = new ClassReader(input);
    JarClassVisitor visitor = new JarClassVisitor();
    reader.accept(
        visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

    ApiClassInfo classInfo =
        new ApiClassInfo(visitor.className, visitor.superName, visitor.isInterface);
    visitor.interfaces.forEach(classInfo::addInterface);
    visitor.methods.forEach(classInfo::addMethod);
    visitor.fields.forEach(classInfo::addField);
    return classInfo;
  }

  private static class JarClassVisitor extends ClassVisitor {
    String className;
    String superName;
    final Set<String> interfaces = new HashSet<>();
    boolean isInterface;
    final Set<ApiMethodInfo> methods = new HashSet<>();
    final Set<String> fields = new HashSet<>();

    JarClassVisitor() {
      super(Opcodes.ASM9);
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      this.className = name;
      this.superName = superName;
      if (interfaces != null) {
        this.interfaces.addAll(Arrays.asList(interfaces));
      }
      this.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
      methods.add(new ApiMethodInfo(name, descriptor, isStatic));
      return null;
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      fields.add(name);
      return null;
    }
  }
}
