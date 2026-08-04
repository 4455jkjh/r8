// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DalvikAnnotationThrowsPreservationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withMinimumApiLevel().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addProgramClasses(Main.class)
        .addProgramClassFileData(
            transformer(DalvikAnnotationThrows.class)
                .setClassDescriptor("Ldalvik/annotation/Throws;")
                .transform())
        .addDontObfuscate()
        .addDontOptimize()
        .addDontShrink()
        .addKeepAttributeExceptions()
        .compile()
        .inspect(
            inspector -> {
              AnnotationSubject annotation =
                  inspector.clazz(Main.class).mainMethod().annotation("dalvik.annotation.Throws");
              assertThat(annotation, isPresent());

              DexEncodedAnnotation encodedAnnotation = annotation.getAnnotation();
              assertEquals(1, encodedAnnotation.getNumberOfElements());

              DexAnnotationElement annotationElement = encodedAnnotation.getElement(0);
              assertEquals("value", annotationElement.getName().toString());
              assertTrue(annotationElement.getValue().isDexValueArray());

              DexValueArray valueArray = annotationElement.getValue().asDexValueArray();
              assertEquals(1, valueArray.size());
              assertTrue(valueArray.getValue(0).isDexValueType());

              DexValueType valueType = valueArray.getValue(0).asDexValueType();
              assertEquals("java.lang.Exception", valueType.getValue().getTypeName());
            });
  }

  public static class Main {

    public static void main(String[] args) throws Exception {}
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.ANNOTATION_TYPE)
  @interface DalvikAnnotationThrows {}
}
