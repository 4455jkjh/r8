// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.utils.internal.FileUtils.CLASS_EXTENSION;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.DesugaredLibraryTestCompileResult;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.MethodTransformer;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class Jdk11ConcurrentMapTests extends DesugaredLibraryTestBase {

  private static final Path CONCURRENT_TESTS_FOLDER =
      Paths.get(ToolHelper.JDK_11_TESTS_DIR + "java/util/concurrent/ConcurrentMap/");
  private static final Path CONCURRENT_HASH_TESTS_FOLDER =
      Paths.get(ToolHelper.JDK_11_TESTS_DIR + "java/util/concurrent/ConcurrentHashMap/");

  // The OpenJDK tests are all main based tests. The following are not run:
  //  - ConcurrentMap/ConcurrentRemoveIf, ConcurrentHashMap/ConcurrentAssociateTest,
  //    ConcurrentHashMap/ConcurrentContainsKeyTest and ConcurrentHashMap/ToArray due to the non
  //    desugared class CompletableFuture (TODO(b/134732760): Support Java 9+ libraries).
  //  - ConcurrentHashMap/MapLoops due to the non desugared class SplittableRandom.
  //  - ConcurrentHashMap/WhiteBox due to method handles.
  private static final Path[] TESTS_TO_COMPILE =
      new Path[] {
        CONCURRENT_TESTS_FOLDER.resolve("ConcurrentModification.java"),
        CONCURRENT_HASH_TESTS_FOLDER.resolve("MapCheck.java"),
        CONCURRENT_HASH_TESTS_FOLDER.resolve("DistinctEntrySetElements.java")
      };

  private static Path[] COMPILED_TESTS_FILES;
  private static byte[] MAP_CHECK_WITH_SINGLE_TRIAL;

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        // TODO(b/134732760): Skip Android 4.4.4 due to missing libjavacrypto.
        getTestParameters()
            .withDexRuntime(Version.V4_0_4)
            // TODO(b/507731439): Test on ART 17.
            .withDexRuntimesRangeIncluding(Version.V5_1_1, Version.V16_0_0)
            .withAllApiLevels()
            .build(),
        ImmutableList.of(JDK8, JDK11, JDK11_PATH),
        ImmutableList.of(D8_L8DEBUG, D8_L8SHRINK));
  }

  public Jdk11ConcurrentMapTests(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @BeforeClass
  public static void compileConcurrentClasses() throws Exception {
    Path compiledTestsFolder = getStaticTemp().newFolder("concurrentmap").toPath();
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addSourceFiles(TESTS_TO_COMPILE)
        .setOutputPath(compiledTestsFolder)
        .compile();
    Path mapCheckClassFile = compiledTestsFolder.resolve("MapCheck.class");
    COMPILED_TESTS_FILES =
        Arrays.stream(getAllFilesWithSuffixInDirectory(compiledTestsFolder, CLASS_EXTENSION))
            .filter(file -> !file.equals(mapCheckClassFile))
            .toArray(Path[]::new);
    assert COMPILED_TESTS_FILES.length > 0;
    MAP_CHECK_WITH_SINGLE_TRIAL = getMapCheckWithSingleTrial(mapCheckClassFile);
  }

  // MapCheck runs 8 identical trials of all map operations by default, which takes ~50s on ART
  // when using the desugared j$.util.concurrent.ConcurrentHashMap. Run a single trial instead. The
  // number of trials cannot be passed as an argument, as that requires passing the map class name
  // which is then loaded with Class.forName, bypassing library desugaring.
  private static byte[] getMapCheckWithSingleTrial(Path mapCheckClassFile) throws Exception {
    return transformer(mapCheckClassFile, Reference.classFromTypeName("MapCheck"))
        .addMethodTransformer(
            new MethodTransformer() {
              @Override
              public void visitIntInsn(int opcode, int operand) {
                if (getMethod().getMethodName().equals("main")
                    && opcode == Opcodes.BIPUSH
                    && operand == 8) {
                  super.visitIntInsn(opcode, 1);
                } else {
                  super.visitIntInsn(opcode, operand);
                }
              }
            })
        .transform();
  }

  @Test
  public void test() throws Exception {
    DesugaredLibraryTestCompileResult<?> compileResult =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addProgramFiles(COMPILED_TESTS_FILES)
            .addProgramClassFileData(MAP_CHECK_WITH_SINGLE_TRIAL)
            .compile()
            .withArt6Plus64BitsLib();
    // Main jdk tests relies on the main function running without issues.
    // Failure implies a runtime exception.
    compileResult
        .run(parameters.getRuntime(), "ConcurrentModification")
        .assertSuccessWithOutputThatMatches(containsString("failed = 0"));
    compileResult
        .run(parameters.getRuntime(), "MapCheck")
        .assertSuccessWithOutputThatMatches(
            containsString("ConcurrentHashMap trials: 1 size: 50000"));
    compileResult.run(parameters.getRuntime(), "DistinctEntrySetElements").assertSuccess();
  }
}
