// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestDeps;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MultiDexTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final Path multidexJar;

  @Parameters(name = "{0}, spec: {1}, {2}, {3}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        ImmutableList.of(JDK8, JDK11, JDK11_PATH),
        ImmutableList.of(D8_L8DEBUG),
        ImmutableList.of(
            TestDeps.getMultidex1_0_3Jar(),
            TestDeps.getMultidexInstrumentation1_0_3Jar(),
            TestDeps.getMultidex2_0_1Jar(),
            TestDeps.getMultidexInstrumentation2_0_0Jar()));
  }

  public MultiDexTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification,
      Path multidexJar) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.multidexJar = multidexJar;
  }

  @Test
  public void test() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramFiles(multidexJar)
        .compile()
        .inspect(this::assertNoJ$Reference);
  }

  private void assertNoJ$Reference(CodeInspector inspector) {
    String prefix = "j$";
    for (FoundClassSubject clazz : inspector.allClasses()) {
      clazz.forAllFields(f -> assertFalse(f.type().toString().startsWith(prefix)));
      clazz.forAllMethods(
          m -> {
            if (m.hasCode()) {
              for (InstructionSubject instruction : m.instructions()) {
                if (instruction.isInvoke()) {
                  DexMethod method = instruction.getMethod();
                  for (DexType referencedType : method.getReferencedTypes()) {
                    assertFalse(referencedType.toString().startsWith(prefix));
                  }
                  assertFalse(method.getHolderType().toString().startsWith(prefix));
                }
                if (instruction.isFieldAccess()) {
                  DexField field = instruction.getField();
                  assertFalse(field.getType().toString().startsWith(prefix));
                }
              }
            }
          });
    }
  }
}
