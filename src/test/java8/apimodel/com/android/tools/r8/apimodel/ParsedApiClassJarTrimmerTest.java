// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.JarTrimmer;
import com.android.tools.r8.apimodel.jar.ApiJarReader;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ParsedApiClassJarTrimmerTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ParsedApiClassJarTrimmerTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @SuppressWarnings("unused")
  public static class TestClassA {
    public void methodA() {}

    public static void staticMethodA() {}

    public int fieldA;
  }

  public static class TestClassB extends TestClassA {
    public void methodB() {}
  }

  @SuppressWarnings("unused")
  public interface TestInterfaceI {
    void interfaceMethod();

    default void defaultMethod() {}

    static void staticInterfaceMethod() {}
  }

  public static class TestClassC implements TestInterfaceI {
    @Override
    public void interfaceMethod() {}
  }

  @Test
  public void testTrimming() throws Exception {
    Path jar =
        writeClassesToJar(
            TestClassA.class, TestClassB.class, TestInterfaceI.class, TestClassC.class);

    // We need a complete hierarchy, so we also pass the android jar (which has Object).
    List<Path> jars = Arrays.asList(jar, ToolHelper.getDefaultAndroidJar());

    ClassReference classARef = Reference.classFromClass(TestClassA.class);
    ClassReference classBRef = Reference.classFromClass(TestClassB.class);
    ClassReference classCRef = Reference.classFromClass(TestClassC.class);
    ClassReference interfaceIRef = Reference.classFromClass(TestInterfaceI.class);

    ApiRange range = new ApiRange(AndroidApiLevel.B);

    // Setup API classes.
    ParsedApiClass apiClassA = new ParsedApiClass(classARef, range);
    apiClassA.registerSupertype(Reference.classFromClass(Object.class), range);
    MethodReference methodA = Reference.methodFromMethod(TestClassA.class.getMethod("methodA"));
    MethodReference staticMethodA =
        Reference.method(classARef, "staticMethodA", Collections.emptyList(), null);
    FieldTypelessReference fieldA = new FieldTypelessReference(classARef, "fieldA");
    MethodReference nonExistentMethod =
        Reference.method(classARef, "nonExistentMethod", Collections.emptyList(), null);
    FieldTypelessReference nonExistentField =
        new FieldTypelessReference(classARef, "nonExistentField");
    apiClassA.registerMethod(methodA, range);
    apiClassA.registerMethod(staticMethodA, range);
    apiClassA.registerField(fieldA, range);
    apiClassA.registerMethod(nonExistentMethod, range);
    apiClassA.registerField(nonExistentField, range);

    ParsedApiClass apiClassB = new ParsedApiClass(classBRef, range);
    apiClassB.registerSupertype(classARef, range);
    MethodReference methodB = Reference.methodFromMethod(TestClassB.class.getMethod("methodB"));
    MethodReference inheritedMethodA =
        Reference.method(classBRef, "methodA", Collections.emptyList(), null);
    MethodReference inheritedStaticMethodA =
        Reference.method(classBRef, "staticMethodA", Collections.emptyList(), null);
    FieldTypelessReference inheritedFieldA = new FieldTypelessReference(classBRef, "fieldA");
    apiClassB.registerMethod(methodB, range);
    apiClassB.registerMethod(inheritedMethodA, range);
    apiClassB.registerMethod(inheritedStaticMethodA, range);
    apiClassB.registerField(inheritedFieldA, range);

    ParsedApiClass apiInterfaceI = new ParsedApiClass(interfaceIRef, range);
    apiInterfaceI.registerSupertype(Reference.classFromClass(Object.class), range);
    MethodReference interfaceMethod =
        Reference.method(interfaceIRef, "interfaceMethod", Collections.emptyList(), null);
    MethodReference defaultMethod =
        Reference.method(interfaceIRef, "defaultMethod", Collections.emptyList(), null);
    MethodReference staticInterfaceMethod =
        Reference.method(interfaceIRef, "staticInterfaceMethod", Collections.emptyList(), null);
    apiInterfaceI.registerMethod(interfaceMethod, range);
    apiInterfaceI.registerMethod(defaultMethod, range);
    apiInterfaceI.registerMethod(staticInterfaceMethod, range);

    ParsedApiClass apiClassC = new ParsedApiClass(classCRef, range);
    apiClassC.registerSupertype(Reference.classFromClass(Object.class), range);
    apiClassC.registerInterface(interfaceIRef, range);
    MethodReference inheritedDefaultMethod =
        Reference.method(classCRef, "defaultMethod", Collections.emptyList(), null);
    MethodReference nonInheritedStaticInterfaceMethod =
        Reference.method(classCRef, "staticInterfaceMethod", Collections.emptyList(), null);
    apiClassC.registerMethod(inheritedDefaultMethod, range);
    apiClassC.registerMethod(nonInheritedStaticInterfaceMethod, range);

    // Class not in JAR,
    ClassReference classDRef =
        Reference.classFromBinaryName("com/android/tools/r8/apimodel/NonExistentClassD");
    ParsedApiClass apiClassD = new ParsedApiClass(classDRef, range);

    Collection<ParsedApiClass> apiClasses =
        Arrays.asList(apiClassA, apiClassB, apiInterfaceI, apiClassC, apiClassD);

    // Run JAR trimmer.
    Collection<ParsedApiClass> trimmed =
        ParsedApiClassTrimming.trim(apiClasses, new JarTrimmer(ApiJarReader.read(jars)));

    // Verify results.
    Map<ClassReference, ParsedApiClass> trimmedMap = new HashMap<>();
    for (ParsedApiClass clazz : trimmed) {
      trimmedMap.put(clazz.getClassReference(), clazz);
    }

    // Verify Class D is discarded.
    assertFalse(trimmedMap.containsKey(classDRef));

    // Verify Class A.
    assertTrue(trimmedMap.containsKey(classARef));
    ParsedApiClass trimmedA = trimmedMap.get(classARef);
    assertTrue(trimmedA.hasMethod(methodA));
    assertTrue(trimmedA.hasMethod(staticMethodA));
    assertTrue(trimmedA.hasField(fieldA));
    assertFalse(trimmedA.hasMethod(nonExistentMethod));
    assertFalse(trimmedA.hasField(nonExistentField));

    // Verify Class B.
    assertTrue(trimmedMap.containsKey(classBRef));
    ParsedApiClass trimmedB = trimmedMap.get(classBRef);
    assertTrue(trimmedB.hasMethod(methodB));
    assertTrue(trimmedB.hasMethod(inheritedMethodA));
    assertTrue(trimmedB.hasMethod(inheritedStaticMethodA));
    assertTrue(trimmedB.hasField(inheritedFieldA));

    // Verify Interface I.
    assertTrue(trimmedMap.containsKey(interfaceIRef));
    ParsedApiClass trimmedI = trimmedMap.get(interfaceIRef);
    assertTrue(trimmedI.hasMethod(interfaceMethod));
    assertTrue(trimmedI.hasMethod(defaultMethod));
    assertTrue(trimmedI.hasMethod(staticInterfaceMethod));

    // Verify Class C.
    assertTrue(trimmedMap.containsKey(classCRef));
    ParsedApiClass trimmedC = trimmedMap.get(classCRef);
    assertTrue(trimmedC.hasMethod(inheritedDefaultMethod));
    assertFalse(trimmedC.hasMethod(nonInheritedStaticInterfaceMethod));
  }
}
