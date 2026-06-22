// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class KeepAnnoChainingTest extends TestBase {

  @UsedByReflection(kind = KeepItemKind.CLASS_AND_MEMBERS)
  static class AnnotatedClass {
    @UsedByReflection(kind = KeepItemKind.CLASS_AND_MEMBERS)
    public int field;

    @UsedByReflection(kind = KeepItemKind.CLASS_AND_MEMBERS)
    public void method() {}
  }

  private static class RecordingVisitor extends ClassVisitor {
    int visitCount = 0;
    int visitAnnotationCount = 0;
    int visitFieldCount = 0;
    int visitMethodCount = 0;
    List<String> visitedAnnotations = new ArrayList<>();
    List<String> visitedFields = new ArrayList<>();
    List<String> visitedMethods = new ArrayList<>();
    List<String> visitedFieldAnnotations = new ArrayList<>();
    List<String> visitedMethodAnnotations = new ArrayList<>();

    RecordingVisitor() {
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
      visitCount++;
      super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      visitAnnotationCount++;
      visitedAnnotations.add(descriptor);
      return super.visitAnnotation(descriptor, visible);
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      visitFieldCount++;
      visitedFields.add(name);
      FieldVisitor fieldVisitor = super.visitField(access, name, descriptor, signature, value);
      return new FieldVisitor(Opcodes.ASM9, fieldVisitor) {
        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
          visitedFieldAnnotations.add(descriptor);
          return super.visitAnnotation(descriptor, visible);
        }
      };
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      visitMethodCount++;
      visitedMethods.add(name);
      MethodVisitor methodVisitor =
          super.visitMethod(access, name, descriptor, signature, exceptions);
      return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
          visitedMethodAnnotations.add(descriptor);
          return super.visitAnnotation(descriptor, visible);
        }
      };
    }
  }

  @Test
  public void testChainingRulesExtractor() throws Exception {
    byte[] bytes = Files.readAllBytes(ToolHelper.getClassFileForTestClass(AnnotatedClass.class));
    ClassReader reader = new ClassReader(bytes);

    List<String> rules = new ArrayList<>();
    RecordingVisitor recorder = new RecordingVisitor();
    ClassVisitor extractor =
        KeepAnno.createClassVisitorForKeepRulesExtraction(rules::add, recorder);

    reader.accept(extractor, ClassReader.SKIP_CODE);

    // Verify rules were extracted
    assertTrue(rules.size() > 0);

    // Verify recorder saw everything
    assertEquals(1, recorder.visitCount);
    // AnnotatedClass has @UsedByReflection on class
    assertTrue(recorder.visitedAnnotations.stream().anyMatch(d -> d.contains("UsedByReflection")));
    // And it has field and method
    assertTrue(recorder.visitedFields.contains("field"));
    assertTrue(recorder.visitedMethods.contains("method"));

    // Verify field and method annotations were delegated
    assertTrue(
        recorder.visitedFieldAnnotations.stream().anyMatch(d -> d.contains("UsedByReflection")));
    assertTrue(
        recorder.visitedMethodAnnotations.stream().anyMatch(d -> d.contains("UsedByReflection")));
  }

  @Test
  public void testChainingRulesExtractorWithItself() throws Exception {
    byte[] bytes = Files.readAllBytes(ToolHelper.getClassFileForTestClass(AnnotatedClass.class));
    ClassReader reader = new ClassReader(bytes);

    List<String> rules = new ArrayList<>();
    ClassVisitor extractor = KeepAnno.createClassVisitorForKeepRulesExtraction(rules::add);

    List<String> rules2 = new ArrayList<>();
    ClassVisitor extractor2 =
        KeepAnno.createClassVisitorForKeepRulesExtraction(rules2::add, extractor);

    reader.accept(extractor2, ClassReader.SKIP_CODE);

    // As the visitor is not making any changes but only reading the annotations chaining it with
    // itself should extract the same rules twice.
    assertTrue(rules.size() > 0);
    assertEquals(rules2, rules);
  }
}
