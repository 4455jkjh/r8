// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

// This is a regression test for b/533167364.
@RunWith(Parameterized.class)
public class ReservedFieldNamesFromUnresolvedReferencesTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ReservedFieldNamesFromUnresolvedReferencesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  // Class containing both fields for compiling code below for subsequent transformation.
  public static class CompileTimeClass {
    public static int dummy = 1;
    public static int existingProperty = 2;
  }

  // Runtime class containing only myRealProperty.
  public static class RuntimeClass {
    public static int existingProperty = 2;
  }

  public static class ClassWithUnresolvedReferenceToFieldNamedA {
    @NeverInline
    @NoMethodStaticizing
    public int getA() {
      return CompileTimeClass.dummy; // Rewritten to RuntimeClass.a
    }

    @NeverInline
    @NoMethodStaticizing
    public int getExistingProperty() {
      return CompileTimeClass.existingProperty; // Rewritten to RuntimeClass.existingProperty
    }
  }

  public static class Main {
    public static void main(String[] args) {
      ClassWithUnresolvedReferenceToFieldNamedA o = new ClassWithUnresolvedReferenceToFieldNamedA();
      try {
        System.out.println(o.getA());
      } catch (NoSuchFieldError e) {
        System.out.println("Could not call getA()");
      }
      System.out.println(o.getExistingProperty());
    }
  }

  @Test
  public void test() throws Exception {
    // Rewrite ClassWithUnresolvedReferenceToFieldNamedA to reference RuntimeClass instead of
    // CompileTimeClass, including the unresolved reference RuntimeClass.a
    String runtimeClassDescriptor = Reference.classFromClass(RuntimeClass.class).getDescriptor();
    byte[] classWithUnresolvedReferenceToFieldNamedABytes =
        transformer(ClassWithUnresolvedReferenceToFieldNamedA.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(CompileTimeClass.class), runtimeClassDescriptor)
            .replaceClassDescriptorInMembers(
                descriptor(CompileTimeClass.class), runtimeClassDescriptor)
            .remapField((name, descriptor) -> name.equals("dummy"), "a")
            .transform();

    TestCompileResult<?, ?> result =
        testForR8(parameters.getBackend())
            .addProgramClasses(Main.class, RuntimeClass.class)
            .addProgramClassFileData(classWithUnresolvedReferenceToFieldNamedABytes)
            .addKeepMainRule(Main.class)
            .addKeepClassAndMembersRules(ClassWithUnresolvedReferenceToFieldNamedA.class)
            .addKeepRules(
                "-keepclassmembers,allowobfuscation class "
                    + RuntimeClass.class.getTypeName()
                    + " { *; }")
            .enableInliningAnnotations()
            .enableNoMethodStaticizingAnnotations()
            .setMinApi(parameters)
            .compile()
            // Can't inspect on DEX, as the generated DEX is invalid.
            .inspectIf(
                parameters.isCfRuntime(),
                inspector -> {
                  ClassSubject brClass = inspector.clazz(RuntimeClass.class);
                  FieldSubject myRealPropertyField =
                      brClass.uniqueFieldWithOriginalName("existingProperty");
                  // TODO(b/533167364): Renaming existingProperty to a makes the unresolved field a
                  //  in the input resolve.
                  assertEquals("a", myRealPropertyField.getFinalName());
                });
    if (parameters.isCfRuntime()) {
      result
          .run(parameters.getRuntime(), Main.class)
          // Should be assertSuccessWithOutputLines("Could not call getA()", "2")
          .assertSuccessWithOutputLines("2", "2");
    } else {
      // Running on Art hits an AssertionError when loading the DEX into the inspector to
      // check for the final name of the main class.
      assertThrows(AssertionError.class, () -> result.run(parameters.getRuntime(), Main.class));
    }
  }
}
