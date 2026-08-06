// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ParsedApiClassVerifierTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ParsedApiClassVerifierTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static final ClassReference objectRef = Reference.classFromClass(Object.class);
  private static final ClassReference classARef =
      Reference.classFromBinaryName("com/example/ClassA");
  private static final ClassReference classBRef =
      Reference.classFromBinaryName("com/example/ClassB");
  private static final ClassReference interfaceIRef =
      Reference.classFromBinaryName("com/example/InterfaceI");
  private static final ClassReference classCRef =
      Reference.classFromBinaryName("com/example/ClassC");
  private static final ApiRange rangeB = new ApiRange(AndroidApiLevel.B);
  private static final ApiRange rangeL = new ApiRange(AndroidApiLevel.L);

  private ParsedApiClass createObject() {
    ParsedApiClass object = new ParsedApiClass(objectRef, rangeB);
    object.registerMethod(Reference.method(objectRef, "<init>", ImmutableList.of(), null), rangeB);
    return object;
  }

  @Test
  public void testValid() throws Exception {
    ParsedApiClass object = createObject();

    ParsedApiClass apiClassA = new ParsedApiClass(classARef, rangeB);
    apiClassA.registerSupertype(objectRef, rangeB);
    apiClassA.registerMethod(
        Reference.method(classARef, "<init>", ImmutableList.of(), null), rangeB);

    ParsedApiClass apiClassB = new ParsedApiClass(classBRef, rangeL);
    apiClassB.registerSupertype(classARef, rangeL);
    apiClassB.registerMethod(
        Reference.method(classBRef, "<init>", ImmutableList.of(), null), rangeL);

    ParsedApiClass apiInterfaceI = new ParsedApiClass(interfaceIRef, rangeB);
    apiInterfaceI.registerSupertype(objectRef, rangeB);

    ParsedApiClass apiClassC = new ParsedApiClass(classCRef, rangeB);
    apiClassC.registerSupertype(objectRef, rangeB);
    apiClassC.registerInterface(interfaceIRef, rangeB);
    apiClassC.registerMethod(
        Reference.method(classCRef, "<init>", ImmutableList.of(), null), rangeB);

    ParsedApiClassVerifier.verify(
        ImmutableList.of(object, apiClassA, apiClassB, apiInterfaceI, apiClassC));
  }

  @Test
  public void testMissingSupertype() {
    ParsedApiClass apiClassB = new ParsedApiClass(classBRef, rangeL);
    apiClassB.registerSupertype(classARef, rangeL);

    ApiDatabaseGeneratorException e =
        assertThrows(
            ApiDatabaseGeneratorException.class,
            () -> ParsedApiClassVerifier.verify(ImmutableList.of(apiClassB)));
    assertThat(
        e.getMessage(),
        containsString("Missing supertype Lcom/example/ClassA; for Lcom/example/ClassB;"));
  }

  @Test
  public void testMissingInterface() {
    ParsedApiClass object = createObject();

    ParsedApiClass apiClassC = new ParsedApiClass(classCRef, rangeB);
    apiClassC.registerSupertype(objectRef, rangeB);
    apiClassC.registerInterface(interfaceIRef, rangeB);

    ApiDatabaseGeneratorException e =
        assertThrows(
            ApiDatabaseGeneratorException.class,
            () -> ParsedApiClassVerifier.verify(ImmutableList.of(object, apiClassC)));
    assertThat(
        e.getMessage(),
        containsString("Missing interface Lcom/example/InterfaceI; for Lcom/example/ClassC;"));
  }

  @Test
  public void testInvalidMemberRangeIntro() {
    ParsedApiClass object = createObject();
    ParsedApiClass apiClassA = new ParsedApiClass(classARef, rangeL);
    apiClassA.registerSupertype(objectRef, rangeL);
    // Method introduced before class (B < L)
    apiClassA.registerMethod(Reference.method(classARef, "foo", ImmutableList.of(), null), rangeB);

    ApiDatabaseGeneratorException e =
        assertThrows(
            ApiDatabaseGeneratorException.class,
            () -> ParsedApiClassVerifier.verify(ImmutableList.of(object, apiClassA)));
    assertThat(e.getMessage(), containsString("Method range [Android B, infinity["));
    assertThat(
        e.getMessage(),
        containsString("is not within class range [Android L, infinity[ of Lcom/example/ClassA;"));
  }

  @Test
  public void testInvalidMemberRangeRemoved() {
    ParsedApiClass object = createObject();
    ApiRange rangeBtoL = new ApiRange(AndroidApiLevel.B, AndroidApiLevel.L);
    ParsedApiClass apiClassA = new ParsedApiClass(classARef, rangeBtoL);
    apiClassA.registerSupertype(objectRef, rangeBtoL);
    apiClassA.registerMethod(Reference.method(classARef, "foo", ImmutableList.of(), null), rangeB);

    ApiDatabaseGeneratorException e =
        assertThrows(
            ApiDatabaseGeneratorException.class,
            () -> ParsedApiClassVerifier.verify(ImmutableList.of(object, apiClassA)));
    assertThat(e.getMessage(), containsString("Method range [Android B, infinity["));
    assertThat(
        e.getMessage(),
        containsString("is not within class range [Android B, Android L[ of Lcom/example/ClassA;"));
  }

  @Test
  public void testInvalidSupertypeRelationRange() {
    ParsedApiClass object = createObject();
    ParsedApiClass apiClassA = new ParsedApiClass(classARef, rangeB);
    apiClassA.registerSupertype(objectRef, rangeB);

    ParsedApiClass apiClassB = new ParsedApiClass(classBRef, rangeL);
    apiClassB.registerSupertype(classARef, rangeB);

    ApiDatabaseGeneratorException e =
        assertThrows(
            ApiDatabaseGeneratorException.class,
            () -> ParsedApiClassVerifier.verify(ImmutableList.of(object, apiClassA, apiClassB)));
    assertThat(
        e.getMessage(),
        containsString(
            "Supertype relation range [Android B, infinity[ for Lcom/example/ClassA; is not within"
                + " class range [Android L, infinity[ of Lcom/example/ClassB;"));
  }

  @Test
  public void testInvalidSupertypeClassRange() {
    ParsedApiClass object = createObject();
    ApiRange rangeBtoL = new ApiRange(AndroidApiLevel.B, AndroidApiLevel.L);
    ParsedApiClass apiClassA = new ParsedApiClass(classARef, rangeBtoL);
    apiClassA.registerSupertype(objectRef, rangeBtoL);

    ParsedApiClass apiClassB = new ParsedApiClass(classBRef, rangeB);
    // B extends A during [Android B, infinity[, but A is removed at L
    apiClassB.registerSupertype(classARef, rangeB);

    ApiDatabaseGeneratorException e =
        assertThrows(
            ApiDatabaseGeneratorException.class,
            () -> ParsedApiClassVerifier.verify(ImmutableList.of(object, apiClassA, apiClassB)));
    assertThat(
        e.getMessage(),
        containsString(
            "Supertype relation range [Android B, infinity[ for Lcom/example/ClassA; is not within"
                + " superclass range [Android B, Android L[ of Lcom/example/ClassA;"));
  }

  @Test
  public void testMultipleInheritanceClass() {
    ParsedApiClass object = createObject();
    ParsedApiClass apiClassA = new ParsedApiClass(classARef, rangeB);
    apiClassA.registerSupertype(objectRef, rangeB);
    apiClassA.registerMethod(
        Reference.method(classARef, "<init>", ImmutableList.of(), null), rangeB);

    ParsedApiClass apiClassB = new ParsedApiClass(classBRef, rangeB);
    apiClassB.registerSupertype(objectRef, rangeB);
    apiClassB.registerMethod(
        Reference.method(classBRef, "<init>", ImmutableList.of(), null), rangeB);

    ParsedApiClass apiClassC = new ParsedApiClass(classCRef, rangeB);
    apiClassC.registerSupertype(classARef, rangeB);
    apiClassC.registerSupertype(classBRef, rangeB); // Multiple inheritance
    apiClassC.registerMethod(
        Reference.method(classCRef, "<init>", ImmutableList.of(), null), rangeB);

    ApiDatabaseGeneratorException e =
        assertThrows(
            ApiDatabaseGeneratorException.class,
            () ->
                ParsedApiClassVerifier.verify(
                    ImmutableList.of(object, apiClassA, apiClassB, apiClassC)));
    assertThat(
        e.getMessage(),
        containsString("Inconsistent class/interface usage involving Lcom/example/ClassC;"));
  }

  @Test
  public void testMultipleInheritanceInterface() throws Exception {
    ParsedApiClass object = createObject();

    ParsedApiClass apiClassA = new ParsedApiClass(classARef, rangeB);
    apiClassA.registerSupertype(objectRef, rangeB);

    ParsedApiClass apiClassB = new ParsedApiClass(classBRef, rangeB);
    apiClassB.registerSupertype(objectRef, rangeB);

    ParsedApiClass apiClassC = new ParsedApiClass(classCRef, rangeB);
    apiClassC.registerSupertype(classARef, rangeB);
    apiClassC.registerSupertype(classBRef, rangeB);
    // C has no <init> and is not forced to be CLASS, so it is inferred as INTERFACE.
    // Multiple inheritance is allowed for INTERFACE.

    ParsedApiClassVerifier.verify(ImmutableList.of(object, apiClassA, apiClassB, apiClassC));
  }

  @Test
  public void testClassExtendsInterface() {
    ParsedApiClass object = createObject();
    ParsedApiClass apiInterfaceI = new ParsedApiClass(interfaceIRef, rangeB);
    apiInterfaceI.registerSupertype(objectRef, rangeB);

    ParsedApiClass apiClassA = new ParsedApiClass(classARef, rangeB);
    apiClassA.registerSupertype(interfaceIRef, rangeB); // Class extends interface
    apiClassA.registerMethod(
        Reference.method(classARef, "<init>", ImmutableList.of(), null), rangeB);

    ParsedApiClass apiClassC = new ParsedApiClass(classCRef, rangeB);
    apiClassC.registerSupertype(objectRef, rangeB);
    apiClassC.registerInterface(interfaceIRef, rangeB); // Forces I to be INTERFACE
    apiClassC.registerMethod(
        Reference.method(classCRef, "<init>", ImmutableList.of(), null), rangeB);

    ApiDatabaseGeneratorException e =
        assertThrows(
            ApiDatabaseGeneratorException.class,
            () ->
                ParsedApiClassVerifier.verify(
                    ImmutableList.of(object, apiInterfaceI, apiClassA, apiClassC)));
    assertThat(
        e.getMessage(),
        containsString("Inconsistent class/interface usage involving Lcom/example/ClassA;"));
  }
}
