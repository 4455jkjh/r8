// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.assistant.JavaLangClassTestClass.Bar;
import com.android.tools.r8.assistant.JavaLangClassTestClass.Foo;
import com.android.tools.r8.assistant.postprocessing.ReflectiveOperationJsonParser;
import com.android.tools.r8.assistant.postprocessing.model.ClassGetMember;
import com.android.tools.r8.assistant.postprocessing.model.ClassGetMembers;
import com.android.tools.r8.assistant.postprocessing.model.ClassGetName;
import com.android.tools.r8.assistant.postprocessing.model.ClassNewInstance;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationJsonLogger;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver.NameLookupType;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JavaLangClassJsonTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNativeMultidexDexRuntimes().withMaximumApiLevel().build();
  }

  private String names() {
    return parameters.getApiLevel().isLessThan(AndroidApiLevel.O)
        ? "com.android.tools.r8.assistant.JavaLangClassTestClass$Foocom.android.tools.r8.assistant.JavaLangClassTestClass.FooFoo"
        : "com.android.tools.r8.assistant.JavaLangClassTestClass$Foocom.android.tools.r8.assistant.JavaLangClassTestClass.FooFoocom.android.tools.r8.assistant.JavaLangClassTestClass$Foo";
  }

  @Test
  public void testInstrumentationWithCustomOracle() throws Exception {
    Path path = Paths.get(temp.newFile().getAbsolutePath());
    Box<DexItemFactory> factoryBox = new Box<>();
    testForAssistant()
        .addProgramClasses(JavaLangClassTestClass.class, Foo.class, Bar.class)
        .addInstrumentationClasses(Instrumentation.class)
        .setCustomReflectiveOperationReceiver(Instrumentation.class)
        .setMinApi(parameters)
        .addOptionsModification(opt -> factoryBox.set(opt.itemFactory))
        .compile()
        .addVmArguments("-Dcom.android.tools.r8.reflectiveJsonLogger=" + path)
        .run(parameters.getRuntime(), JavaLangClassTestClass.class)
        .assertSuccess();
    List<ReflectiveEvent> reflectiveEvents =
        new ReflectiveOperationJsonParser(factoryBox.get()).parse(path);
    Assert.assertEquals(29, reflectiveEvents.size());

    assertTrue(reflectiveEvents.get(3).isClassGetMember());
    ClassGetMember updater03 = reflectiveEvents.get(3).asClassGetMember();
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_METHOD, updater03.getEventType());
    assertEquals(
        Reference.methodFromMethod(Foo.class.getDeclaredMethod("barr")),
        updater03.getMember().asDexMethod().asMethodReference());

    assertTrue(reflectiveEvents.get(4).isClassGetMember());
    ClassGetMember updater04 = reflectiveEvents.get(4).asClassGetMember();
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_FIELD, updater04.getEventType());
    assertEquals(
        Reference.fieldFromField(Foo.class.getDeclaredField("a")),
        updater04.getMember().asDexField().asFieldReference());

    assertTrue(reflectiveEvents.get(6).isClassGetMembers());
    ClassGetMembers updater06 = reflectiveEvents.get(6).asClassGetMembers();
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_METHODS, updater06.getEventType());
    assertEquals(Foo.class.getName(), updater06.getHolder().toSourceString());

    assertTrue(reflectiveEvents.get(7).isClassGetMembers());
    ClassGetMembers updater07 = reflectiveEvents.get(7).asClassGetMembers();
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_FIELDS, updater07.getEventType());
    assertEquals(Foo.class.getName(), updater07.getHolder().toSourceString());

    assertTrue(reflectiveEvents.get(8).isClassGetMember());
    ClassGetMember updater08 = reflectiveEvents.get(8).asClassGetMember();
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_CONSTRUCTOR, updater08.getEventType());
    assertEquals(
        Reference.methodFromMethod(Foo.class.getDeclaredConstructor()),
        updater08.getMember().asDexMethod().asMethodReference());

    assertTrue(reflectiveEvents.get(9).isClassGetMembers());
    ClassGetMembers updater09 = reflectiveEvents.get(9).asClassGetMembers();
    assertEquals(ReflectiveEventType.CLASS_GET_DECLARED_CONSTRUCTORS, updater09.getEventType());
    assertEquals(Foo.class.getName(), updater09.getHolder().toSourceString());

    assertTrue(reflectiveEvents.get(10).isClassGetName());
    ClassGetName updater10 = reflectiveEvents.get(10).asClassGetName();
    assertEquals(Foo.class.getName(), updater10.getType().toSourceString());
    assertEquals(NameLookupType.NAME, updater10.getNameLookupType());

    assertTrue(reflectiveEvents.get(11).isClassGetName());
    ClassGetName updater11 = reflectiveEvents.get(11).asClassGetName();
    assertEquals(Foo.class.getName(), updater11.getType().toSourceString());
    assertEquals(NameLookupType.CANONICAL_NAME, updater11.getNameLookupType());

    assertTrue(reflectiveEvents.get(12).isClassGetName());
    ClassGetName updater12 = reflectiveEvents.get(12).asClassGetName();
    assertEquals(Foo.class.getName(), updater12.getType().toSourceString());
    assertEquals(NameLookupType.SIMPLE_NAME, updater12.getNameLookupType());

    assertTrue(reflectiveEvents.get(13).isClassGetName());
    ClassGetName updater13 = reflectiveEvents.get(13).asClassGetName();
    assertEquals(Foo.class.getName(), updater13.getType().toSourceString());
    assertEquals(NameLookupType.TYPE_NAME, updater13.getNameLookupType());

    assertTrue(reflectiveEvents.get(19).isClassGetMembers());
    ClassGetMembers updater19 = reflectiveEvents.get(19).asClassGetMembers();
    assertEquals(ReflectiveEventType.CLASS_GET_METHODS, updater19.getEventType());
    assertEquals(Bar.class.getName(), updater19.getHolder().toSourceString());

    assertTrue(reflectiveEvents.get(20).isClassGetMembers());
    ClassGetMembers updater20 = reflectiveEvents.get(20).asClassGetMembers();
    assertEquals(ReflectiveEventType.CLASS_GET_FIELDS, updater20.getEventType());
    assertEquals(Bar.class.getName(), updater20.getHolder().toSourceString());

    assertTrue(reflectiveEvents.get(21).isClassGetMembers());
    ClassGetMembers updater21 = reflectiveEvents.get(21).asClassGetMembers();
    assertEquals(ReflectiveEventType.CLASS_GET_CONSTRUCTORS, updater21.getEventType());
    assertEquals(Bar.class.getName(), updater21.getHolder().toSourceString());

    assertTrue(reflectiveEvents.get(22).isClassGetMember());
    ClassGetMember updater22 = reflectiveEvents.get(22).asClassGetMember();
    assertEquals(ReflectiveEventType.CLASS_GET_METHOD, updater22.getEventType());
    assertEquals(
        Reference.methodFromMethod(Bar.class.getMethod("bar")),
        updater22.getMember().asDexMethod().asMethodReference());

    assertTrue(reflectiveEvents.get(23).isClassGetMember());
    ClassGetMember updater23 = reflectiveEvents.get(23).asClassGetMember();
    assertEquals(ReflectiveEventType.CLASS_GET_FIELD, updater23.getEventType());
    assertEquals(
        Reference.fieldFromField(Bar.class.getField("i")),
        updater23.getMember().asDexField().asFieldReference());

    assertTrue(reflectiveEvents.get(24).isClassGetMember());
    ClassGetMember updater24 = reflectiveEvents.get(24).asClassGetMember();
    assertEquals(ReflectiveEventType.CLASS_GET_CONSTRUCTOR, updater24.getEventType());
    assertEquals(
        Reference.methodFromMethod(Bar.class.getConstructor()),
        updater24.getMember().asDexMethod().asMethodReference());

    assertTrue(reflectiveEvents.get(28).isClassNewInstance());
    ClassNewInstance updater28 = reflectiveEvents.get(28).asClassNewInstance();
    assertEquals(ReflectiveEventType.CLASS_NEW_INSTANCE, updater28.getEventType());
    assertEquals(Bar.class.getName(), updater28.getType().toSourceString());

    Box<KeepInfoCollectionExported> keepInfoBox = new Box<>();
    testForR8(parameters)
        .addProgramClasses(JavaLangClassTestClass.class, Foo.class, Bar.class)
        .addOptionsModification(
            opt -> opt.getAssistantOptions().finalKeepInfoCollectionConsumer = keepInfoBox::set)
        .setMinApi(parameters)
        .addKeepMainRule(JavaLangClassTestClass.class)
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .addKeepRules(
            "-keep class " + Foo.class.getName() + "{ void barr(); void <init>(); int a; int b; }")
        .addKeepRules("-keep class " + Bar.class.getName() + "{ void <init>(); int bar(); int i; }")
        .run(parameters.getRuntime(), JavaLangClassTestClass.class)
        .assertSuccessWithOutputLines(
            "Object",
            "barr",
            "a",
            "b",
            "com.android.tools.r8.assistant.JavaLangClassTestClass$Foo",
            names(),
            "com.android.tools.r8.assistant",
            "public int com.android.tools.r8.assistant.JavaLangClassTestClass$Bar.bar()",
            "public int com.android.tools.r8.assistant.JavaLangClassTestClass$Bar.i",
            "public com.android.tools.r8.assistant.JavaLangClassTestClass$Bar()",
            "true",
            "class com.android.tools.r8.assistant.JavaLangClassTestClass$Bar",
            "11",
            "END");
    KeepInfoCollectionExported keepInfoCollectionExported = keepInfoBox.get();

    assertTrue(updater03.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater04.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater06.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater07.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater08.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater09.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater10.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater11.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater12.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater13.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater19.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater20.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater21.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater22.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater23.isKeptBy(keepInfoCollectionExported));
    assertTrue(updater24.isKeptBy(keepInfoCollectionExported));

    File folder = temp.newFolder();
    keepInfoCollectionExported.exportToDirectory(folder.toPath());
    KeepInfoCollectionExported keepInfoCollectionExported2 =
        KeepInfoCollectionExported.parse(folder.toPath());

    assertEquals(keepInfoCollectionExported, keepInfoCollectionExported2);
  }

  public static class Instrumentation extends ReflectiveOperationJsonLogger {

    public Instrumentation() throws IOException {}
  }
}
