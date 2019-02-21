// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatibilityTestBase;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfOnAccessModifierTest extends ProguardCompatibilityTestBase {
  private static final List<Class<?>> CLASSES =
      ImmutableList.of(ClassForIf.class, ClassForSubsequent.class, MainForAccessModifierTest.class);

  private final Shrinker shrinker;
  private final MethodSignature nonPublicMethod;
  private final MethodSignature publicMethod;

  public IfOnAccessModifierTest(Shrinker shrinker) {
    this.shrinker = shrinker;
    nonPublicMethod = new MethodSignature("nonPublicMethod", "void", ImmutableList.of());
    publicMethod = new MethodSignature("publicMethod", "void", ImmutableList.of());
  }

  @Parameters(name = "shrinker: {0}")
  public static Collection<Object> data() {
    return ImmutableList.of(Shrinker.R8_CF, Shrinker.PROGUARD6, Shrinker.R8);
  }

  @Test
  public void ifOnPublic_noPublicClassForIfRule() throws Exception {
    List<String> config = ImmutableList.of(
        "-repackageclasses 'top'",
        "-keep class **.Main* {",
        "  public static void callIfNonPublic();",
        "}",
        "-if public class **.ClassForIf {",
        "  <methods>;",
        "}",
        "-keep,allowobfuscation class **.ClassForSubsequent {",
        "  public <methods>;",
        "}"
    );
    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);
    ClassSubject classSubject = codeInspector.clazz(ClassForIf.class);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, not(isPresent()));
    methodSubject = classSubject.method(nonPublicMethod);
    assertThat(methodSubject, isPresent());
    assertFalse(methodSubject.getMethod().accessFlags.isPublic());

    classSubject = codeInspector.clazz(ClassForSubsequent.class);
    assertThat(classSubject, not(isPresent()));
  }

  @Test
  public void ifOnNonPublic_keepOnPublic() throws Exception {
    List<String> config = ImmutableList.of(
        "-printmapping",
        "-repackageclasses 'top'",
        "-allowaccessmodification",
        "-keep class **.Main* {",
        "  public static void callIfNonPublic();",
        "}",
        "-if class **.ClassForIf {",
        "  !public <methods>;",
        "}",
        "-keep,allowobfuscation class **.ClassForSubsequent {",
        "  public <methods>;",
        "}"
    );
    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);
    ClassSubject classSubject = codeInspector.clazz(ClassForIf.class);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, not(isPresent()));
    methodSubject = classSubject.method(nonPublicMethod);
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getMethod().accessFlags.isPublic());

    classSubject = codeInspector.clazz(ClassForSubsequent.class);
    assertThat(classSubject, isPresent());
    methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, isPresent());
    methodSubject = classSubject.method(nonPublicMethod);
    assertThat(methodSubject, not(isPresent()));
  }

  @Test
  public void ifOnNonPublic_keepOnNonPublic() throws Exception {
    List<String> config = ImmutableList.of(
        "-printmapping",
        "-repackageclasses 'top'",
        "-allowaccessmodification",
        "-keep class **.Main* {",
        "  public static void callIfNonPublic();",
        "}",
        "-if class **.ClassForIf {",
        "  !public <methods>;",
        "}",
        "-keep,allowobfuscation class **.ClassForSubsequent {",
        "  !public <methods>;",
        "}"
    );
    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);
    ClassSubject classSubject = codeInspector.clazz(ClassForIf.class);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, not(isPresent()));
    methodSubject = classSubject.method(nonPublicMethod);
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getMethod().accessFlags.isPublic());

    classSubject = codeInspector.clazz(ClassForSubsequent.class);
    assertThat(classSubject, isPresent());
    methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, not(isPresent()));
    methodSubject = classSubject.method(nonPublicMethod);
    assertThat(methodSubject, isPresent());
    assertEquals(shrinker.isR8(), methodSubject.getMethod().accessFlags.isPublic());
  }

  @Test
  public void ifOnPublic_keepOnPublic() throws Exception {
    List<String> config = ImmutableList.of(
        "-printmapping",
        "-repackageclasses 'top'",
        "-allowaccessmodification",
        "-keep class **.Main* {",
        "  public static void callIfPublic();",
        "}",
        "-if class **.ClassForIf {",
        "  public <methods>;",
        "}",
        "-keep,allowobfuscation class **.ClassForSubsequent {",
        "  public <methods>;",
        "}"
    );
    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);
    ClassSubject classSubject = codeInspector.clazz(ClassForIf.class);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, isPresent());
    methodSubject = classSubject.method(nonPublicMethod);
    assertThat(methodSubject, not(isPresent()));

    classSubject = codeInspector.clazz(ClassForSubsequent.class);
    assertThat(classSubject, isPresent());
    methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, isPresent());
    methodSubject = classSubject.method(nonPublicMethod);
    assertThat(methodSubject, not(isPresent()));
  }

  @Test
  public void ifOnPublic_keepOnNonPublic() throws Exception {
    List<String> config = ImmutableList.of(
        "-printmapping",
        "-repackageclasses 'top'",
        "-allowaccessmodification",
        "-keep class **.Main* {",
        "  public static void callIfPublic();",
        "}",
        "-if class **.ClassForIf {",
        "  public <methods>;",
        "}",
        "-keep,allowobfuscation class **.ClassForSubsequent {",
        "  !public <methods>;",
        "}"
    );
    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);
    ClassSubject classSubject = codeInspector.clazz(ClassForIf.class);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, isPresent());
    methodSubject = classSubject.method(nonPublicMethod);
    assertThat(methodSubject, not(isPresent()));

    classSubject = codeInspector.clazz(ClassForSubsequent.class);
    assertThat(classSubject, isPresent());
    methodSubject = classSubject.method(publicMethod);
    assertThat(methodSubject, not(isPresent()));
    methodSubject = classSubject.method(nonPublicMethod);
    assertThat(methodSubject, isPresent());
    assertEquals(shrinker.isR8(), methodSubject.getMethod().accessFlags.isPublic());
  }
}
