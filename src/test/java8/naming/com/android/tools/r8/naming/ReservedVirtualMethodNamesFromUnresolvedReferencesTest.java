// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

// This is a regression test for the virtual method case of b/533167364.
@RunWith(Parameterized.class)
public class ReservedVirtualMethodNamesFromUnresolvedReferencesTest extends TestBase {

  private final TestParameters parameters;
  private final boolean targetSuperClass;

  @Parameterized.Parameters(name = "{0}, targetSuperClass = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public ReservedVirtualMethodNamesFromUnresolvedReferencesTest(
      TestParameters parameters, boolean targetSuperClass) {
    this.parameters = parameters;
    this.targetSuperClass = targetSuperClass;
  }

  // Compile-time superclass containing both virtual methods before compiling code below.
  public static class CompileTimeSuperClass {
    public int dummy() {
      return 1;
    }

    public int existingMethod() {
      return 2;
    }
  }

  // Runtime superclass containing only existingMethod.
  public static class RuntimeSuperClass {
    public int existingMethod() {
      return 2;
    }
  }

  // Runtime class extending CompileTimeSuperClass in source Java code.
  // Superclass will be rewritten to RuntimeSuperClass during transformation.
  public static class RuntimeClass extends CompileTimeSuperClass {}

  public static class ClassWithUnresolvedReferenceToMethodNamedA {
    @NeverInline
    public int callA(RuntimeClass o) {
      return o.dummy(); // Rewritten to RuntimeSuperClass.a() or RuntimeClass.a()
    }

    @NeverInline
    public int callExistingMethod(RuntimeClass o) {
      return o.existingMethod(); // Rewritten to RuntimeSuperClass.existingMethod() or
      // RuntimeClass.existingMethod()
    }
  }

  public static class Main {
    public static void main(String[] args) {
      ClassWithUnresolvedReferenceToMethodNamedA o =
          new ClassWithUnresolvedReferenceToMethodNamedA();
      RuntimeClass runtimeInstance = new RuntimeClass();
      try {
        System.out.println(o.callA(runtimeInstance));
      } catch (NoSuchMethodError e) {
        System.out.println("Could not call callA()");
      }
      System.out.println(o.callExistingMethod(runtimeInstance));
    }
  }

  @Test
  public void test() throws Exception {
    String runtimeSuperClassDescriptor =
        Reference.classFromClass(RuntimeSuperClass.class).getDescriptor();
    String runtimeClassDescriptor = Reference.classFromClass(RuntimeClass.class).getDescriptor();

    byte[] runtimeClassBytes =
        transformer(RuntimeClass.class)
            // Rewrite RuntimeClass to extend RuntimeSuperClass instead of CompileTimeSuperClass
            .setSuper(runtimeSuperClassDescriptor)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(CompileTimeSuperClass.class), runtimeSuperClassDescriptor)
            .transform();

    // Rewrite ClassWithUnresolvedReferenceToMethodNamedA to reference RuntimeSuperClass instead of
    // CompileTimeSuperClass, including unresolved reference RuntimeSuperClass.a()
    byte[] classWithUnresolvedReferenceToMethodNamedABytes =
        transformer(ClassWithUnresolvedReferenceToMethodNamedA.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(CompileTimeSuperClass.class),
                targetSuperClass ? runtimeSuperClassDescriptor : runtimeClassDescriptor)
            .replaceClassDescriptorInMembers(
                descriptor(CompileTimeSuperClass.class),
                targetSuperClass ? runtimeSuperClassDescriptor : runtimeClassDescriptor)
            .addMethodTransformer(
                new MethodTransformer() {
                  @Override
                  public void visitMethodInsn(
                      int opcode,
                      String owner,
                      String name,
                      String descriptor,
                      boolean isInterface) {
                    if (name.equals("dummy")) {
                      super.visitMethodInsn(opcode, owner, "a", descriptor, isInterface);
                    } else {
                      super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                  }
                })
            .transform();

    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, RuntimeSuperClass.class)
        .addProgramClassFileData(runtimeClassBytes, classWithUnresolvedReferenceToMethodNamedABytes)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(ClassWithUnresolvedReferenceToMethodNamedA.class)
        .addKeepRules(
            "-keepclassmembers,allowobfuscation class "
                + RuntimeSuperClass.class.getTypeName()
                + " { *; }")
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .inspect(
            inspector -> {
              ClassSubject runtimeSuperClass = inspector.clazz(RuntimeSuperClass.class);
              MethodSubject existingMethod =
                  runtimeSuperClass.uniqueMethodWithOriginalName("existingMethod");
              assertEquals("b", existingMethod.getFinalName());
            })
        .assertSuccessWithOutputLines("Could not call callA()", "2");
  }
}
