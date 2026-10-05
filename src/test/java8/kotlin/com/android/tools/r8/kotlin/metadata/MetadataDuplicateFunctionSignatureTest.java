// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.kotlin.KotlinMetadataAnnotationWrapper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
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
public class MetadataDuplicateFunctionSignatureTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(
            getKotlinMetadataClassFileData(), getHostWithDuplicateFunctionSignatures())
        .addKeepMainRule(Host.class)
        .addKeepClassAndMembersRules(Host.class)
        .addKeepKotlinMetadata()
        .addKeepRuntimeVisibleAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Host.class)
        .assertSuccessWithOutputLines("Hello!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Host.class);
    assertThat(clazz, isPresent());
    KotlinClassMetadata metadata = clazz.getKotlinClassMetadata();
    assertNotNull(metadata);
    KmClass kmClass = ((KotlinClassMetadata.Class) metadata).getKmClass();
    assertEquals(2, kmClass.getFunctions().size());
  }

  private static byte[] getKotlinMetadataClassFileData() throws IOException {
    return ClassFileTransformer.create(
            StreamUtils.streamToByteArrayClose(
                Metadata.class.getClassLoader().getResourceAsStream("kotlin/Metadata.class")),
            Reference.classFromDescriptor("Lkotlin/Metadata;"))
        .removeAllAnnotations()
        .transform();
  }

  private static byte[] getHostWithDuplicateFunctionSignatures() throws IOException {
    KmClass kmClass = new KmClass();
    kmClass.setName(
        DescriptorUtils.descriptorToKotlinClassifier(
            DescriptorUtils.javaTypeToDescriptor(Host.class.getTypeName())));
    for (String functionName : new String[] {"foo1", "foo2"}) {
      KmFunction kmFunction = new KmFunction(functionName);
      KmType returnType = new KmType();
      returnType.setClassifier(new KmClassifier.Class("kotlin/Unit"));
      kmFunction.setReturnType(returnType);
      JvmExtensionsKt.setSignature(kmFunction, new JvmMethodSignature("foo", "()V"));
      kmClass.getFunctions().add(kmFunction);
    }
    KotlinMetadataAnnotationWrapper metadata =
        KotlinMetadataAnnotationWrapper.wrap(
            new KotlinClassMetadata.Class(kmClass, JvmMetadataVersion.LATEST_STABLE_SUPPORTED, 0));
    return transformer(Host.class)
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

  public static class Host {

    public static void foo() {
      System.out.println("Hello!");
    }

    public static void main(String[] args) {
      foo();
    }
  }
}
