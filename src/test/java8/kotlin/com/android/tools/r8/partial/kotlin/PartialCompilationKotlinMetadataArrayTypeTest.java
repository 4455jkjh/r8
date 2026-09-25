// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial.kotlin;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.kotlin.KotlinMetadataAnnotationWrapper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.internal.StreamUtils;
import java.io.IOException;
import kotlin.Metadata;
import kotlin.metadata.KmClass;
import kotlin.metadata.KmClassifier;
import kotlin.metadata.KmFunction;
import kotlin.metadata.KmType;
import kotlin.metadata.jvm.JvmExtensionsKt;
import kotlin.metadata.jvm.JvmMetadataVersion;
import kotlin.metadata.jvm.JvmMethodSignature;
import kotlin.metadata.jvm.KotlinClassMetadata;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.AnnotationVisitor;

@RunWith(Parameterized.class)
public class PartialCompilationKotlinMetadataArrayTypeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
        .build();
  }

  @Test
  public void test() throws Exception {
    testForR8Partial(parameters)
        .addProgramClasses(IncludedClass.class, Main.class)
        .addProgramClassFileData(
            getKotlinMetadataClassFileData(), getExcludedClassWithArrayJvmSignature())
        .addKeepMainRule(Main.class)
        .setR8PartialConfiguration(
            builder -> builder.includeAll().excludeClasses(ExcludedClass.class))
        .apply(b -> assertThrows(CompilationFailedException.class, b::compile));
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(IncludedClass.class), isPresent());
    assertThat(inspector.clazz(ExcludedClass.class), isPresent());
  }

  private static byte[] getKotlinMetadataClassFileData() throws IOException {
    return ClassFileTransformer.create(
            StreamUtils.streamToByteArrayClose(
                Metadata.class.getClassLoader().getResourceAsStream("kotlin/Metadata.class")),
            Reference.classFromDescriptor("Lkotlin/Metadata;"))
        .removeAllAnnotations()
        .transform();
  }

  private static byte[] getExcludedClassWithArrayJvmSignature() throws IOException {
    KmClass kmClass = new KmClass();
    kmClass.setName(
        DescriptorUtils.descriptorToKotlinClassifier(
            DescriptorUtils.javaTypeToDescriptor(ExcludedClass.class.getTypeName())));
    KmFunction kmFunction = new KmFunction("foo");
    KmType returnType = new KmType();
    returnType.setClassifier(new KmClassifier.Class("kotlin/Unit"));
    kmFunction.setReturnType(returnType);
    String includedDescriptor = DescriptorUtils.javaTypeToDescriptor(IncludedClass.class.getName());
    JvmExtensionsKt.setSignature(
        kmFunction, new JvmMethodSignature("foo", "([" + includedDescriptor + "[Lpkg/Missing;)V"));
    kmClass.getFunctions().add(kmFunction);
    KotlinMetadataAnnotationWrapper metadata =
        KotlinMetadataAnnotationWrapper.wrap(
            new KotlinClassMetadata.Class(kmClass, JvmMetadataVersion.LATEST_STABLE_SUPPORTED, 0));
    return transformer(ExcludedClass.class)
        .addClassTransformer(
            new ClassTransformer() {
              @Override
              public void visitEnd() {
                AnnotationVisitor av = super.visitAnnotation("Lkotlin/Metadata;", true);
                av.visit("k", metadata.k());
                av.visit("mv", metadata.mv());
                AnnotationVisitor d1 = av.visitArray("d1");
                for (String s : metadata.d1()) {
                  d1.visit(null, s);
                }
                d1.visitEnd();
                AnnotationVisitor d2 = av.visitArray("d2");
                for (String s : metadata.d2()) {
                  d2.visit(null, s);
                }
                d2.visitEnd();
                av.visit("xs", metadata.xs());
                av.visit("pn", metadata.pn());
                av.visit("xi", metadata.xi());
                av.visitEnd();
                super.visitEnd();
              }
            })
        .transform();
  }

  public static class IncludedClass {}

  public static class ExcludedClass {}

  public static class Main {

    public static void main(String[] args) {
      System.out.println("Hello!");
    }
  }
}
