// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b124357885;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import org.junit.Test;

public class B124357885Test extends TestBase {

  private void checkSignatureAnnotation(AnnotationSubject signature) {
    DexAnnotationElement[] elements = signature.getAnnotation().elements;
    assertEquals(1, elements.length);
    assertEquals("value", elements[0].name.toString());
    assertTrue(elements[0].value instanceof DexValueArray);
    DexValueArray array = (DexValueArray) elements[0].value;
    StringBuilder builder = new StringBuilder();
    for (DexValue value : array.getValues()) {
      assertTrue(value instanceof DexValueString);
      builder.append(((DexValueString) value).value);
    }
    // TODO(124357885): This should be the minified name for FooImpl instead of Foo.
    String fooDescriptor = DescriptorUtils.javaTypeToDescriptor(Foo.class.getTypeName());
    StringBuilder expected =
        new StringBuilder()
            .append("()")
            .append(fooDescriptor.substring(0, fooDescriptor.length() - 1))  // Remove the ;.
            .append("<Ljava/lang/String;>")
            .append(";");  // Add the ; here.
    assertEquals(expected.toString(), builder.toString());
  }

  @Test
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(Main.class, Service.class, Foo.class, FooImpl.class)
        .addKeepMainRule(Main.class)
        .addKeepRules("-keepattributes Signature,InnerClasses,EnclosingMethod")
        .compile()
        .inspect(inspector -> {
          assertThat(inspector.clazz(Main.class), allOf(isPresent(), not(isRenamed())));
          assertThat(inspector.clazz(Service.class), allOf(isPresent(), isRenamed()));
          assertThat(inspector.clazz(Foo.class), not(isPresent()));
          assertThat(inspector.clazz(FooImpl.class), allOf(isPresent(), isRenamed()));
          // TODO(124477502): Using uniqueMethodWithName("fooList") does not work.
          assertEquals(1, inspector.clazz(Service.class).allMethods().size());
          MethodSubject fooList = inspector.clazz(Service.class).allMethods().get(0);
          AnnotationSubject signature = fooList.annotation("dalvik.annotation.Signature");
          checkSignatureAnnotation(signature);
        })
        .run(Main.class)
        .assertFailureWithErrorThatMatches(
            containsString(
                "java.lang.ClassNotFoundException: "
                    + "Didn't find class \"com.android.tools.r8.naming.b124357885.Foo\""));
  }
}

class Main {
  public static void main(String... args) throws Exception {
    Method method = Service.class.getMethod("fooList");
    ParameterizedType type = (ParameterizedType) method.getGenericReturnType();
    Class<?> rawType = (Class<?>) type.getRawType();
    System.out.println(rawType.getName());

    // Convince R8 we only use subtypes to get class merging of Foo into FooImpl.
    Foo<String> foo = new FooImpl<>();
    System.out.println(foo);
  }
}

interface Service {
  Foo<String> fooList();
}

interface Foo<T> {}

class FooImpl<T> implements Foo<T> {}