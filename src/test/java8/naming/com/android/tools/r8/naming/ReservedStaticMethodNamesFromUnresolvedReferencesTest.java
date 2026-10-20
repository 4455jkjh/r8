// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

// This is a regression test for the static method case of b/533167364.
@RunWith(Parameterized.class)
public class ReservedStaticMethodNamesFromUnresolvedReferencesTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ReservedStaticMethodNamesFromUnresolvedReferencesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  // Class containing both methods for compiling code below before subsequent transformation.
  public static class CompileTimeClass {
    public static int dummy() {
      return 1;
    }

    public static int existingMethod() {
      return 2;
    }
  }

  // Runtime class containing only existingMethod.
  public static class RuntimeClass {
    public static int existingMethod() {
      return 2;
    }
  }

  public interface LibraryInterface {}

  public static class LibrarySuperClass {}

  public interface ProgramInterface extends LibraryInterface {
    void foo();
  }

  public static class ClassWithUnresolvedReferenceToMethodNamedA {
    @NeverInline
    @NoMethodStaticizing
    public int callA() {
      return CompileTimeClass.dummy(); // Rewritten to RuntimeClass.a().
    }

    @NeverInline
    @NoMethodStaticizing
    public int callExistingMethod() {
      return CompileTimeClass.existingMethod(); // Rewritten to RuntimeClass.existingMethod().
    }
  }

  public static class Main {
    public static void main(String[] args) {
      ClassWithUnresolvedReferenceToMethodNamedA o =
          new ClassWithUnresolvedReferenceToMethodNamedA();
      try {
        System.out.println(o.callA());
      } catch (NoSuchMethodError e) {
        System.out.println("Could not call callA()");
      }
      System.out.println(o.callExistingMethod());
    }
  }

  private byte[] getClassWithUnresolvedReferenceToMethodNamedA() throws Exception {
    // Rewrite ClassWithUnresolvedReferenceToMethodNamedA to reference RuntimeClass instead of
    // CompileTimeClass, including the unresolved reference RuntimeClass.a()
    String runtimeClassDescriptor = Reference.classFromClass(RuntimeClass.class).getDescriptor();
    return transformer(ClassWithUnresolvedReferenceToMethodNamedA.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(CompileTimeClass.class), runtimeClassDescriptor)
        .replaceClassDescriptorInMembers(descriptor(CompileTimeClass.class), runtimeClassDescriptor)
        .addMethodTransformer(
            new MethodTransformer() {
              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String descriptor, boolean isInterface) {
                super.visitMethodInsn(
                    opcode, owner, name.equals("dummy") ? "a" : name, descriptor, isInterface);
              }
            })
        .transform();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, RuntimeClass.class)
        .addProgramClassFileData(getClassWithUnresolvedReferenceToMethodNamedA())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(ClassWithUnresolvedReferenceToMethodNamedA.class)
        .addKeepRules(
            "-keepclassmembers,allowobfuscation class "
                + RuntimeClass.class.getTypeName()
                + " { *; }")
        .enableInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .inspect(
            inspector -> {
              ClassSubject runtimeClass = inspector.clazz(RuntimeClass.class);
              MethodSubject existingMethod =
                  runtimeClass.uniqueMethodWithOriginalName("existingMethod");
              assertEquals("b", existingMethod.getFinalName());
            })
        .assertSuccessWithOutputLines("Could not call callA()", "2");
  }

  @Test
  public void testLibrary() throws Exception {
    byte[] runtimeClassBytes =
        transformer(RuntimeClass.class)
            .setSuper(descriptor(LibrarySuperClass.class))
            .replaceClassDescriptorInMethodInstructions(
                descriptor(Object.class), descriptor(LibrarySuperClass.class))
            .setImplements(LibraryInterface.class)
            .transform();

    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, ProgramInterface.class)
        .addProgramClassFileData(getClassWithUnresolvedReferenceToMethodNamedA())
        .addLibraryClasses(LibraryInterface.class, LibrarySuperClass.class)
        .addLibraryClassFileData(runtimeClassBytes)
        .addDefaultRuntimeLibrary(parameters)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(ClassWithUnresolvedReferenceToMethodNamedA.class)
        .addKeepClassAndMembersRulesWithAllowObfuscation(ProgramInterface.class)
        .enableInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .setMinApi(parameters)
        .compile()
        .addRunClasspathClasses(LibraryInterface.class, LibrarySuperClass.class)
        .addRunClasspathClassFileData(runtimeClassBytes)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Could not call callA()", "2");
  }
}
