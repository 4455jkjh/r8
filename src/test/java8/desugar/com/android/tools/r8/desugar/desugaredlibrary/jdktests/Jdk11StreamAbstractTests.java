// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.getPathsFiles;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.getSafeVarArgsFile;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.getTestNGMainRunner;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.testNGPath;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.testNGSupportProgramFiles;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11TestLibraryDesugaringSpecification.EXTENSION_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11TestLibraryDesugaringSpecification.JDK11_PATH_JAVA_BASE_EXT;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11TestLibraryDesugaringSpecification.JDK8_JAVA_BASE_EXT;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8CF2CF_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK;
import static com.android.tools.r8.utils.internal.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.internal.FileUtils.JAVA_EXTENSION;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.DesugaredLibraryTestCompileResult;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class Jdk11StreamAbstractTests extends DesugaredLibraryTestBase {

  private static final int SPLIT = 3;
  final TestParameters parameters;
  final LibraryDesugaringSpecification libraryDesugaringSpecification;
  final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() throws Exception {
    List<LibraryDesugaringSpecification> specs;
    if (ToolHelper.isWindows()) {
      // The library configuration is not available on windows. Do not run anything.
      specs = ImmutableList.of();
    } else {
      Jdk11TestLibraryDesugaringSpecification.setUp();
      specs = ImmutableList.of(JDK8_JAVA_BASE_EXT, JDK11_PATH_JAVA_BASE_EXT);
    }
    return buildParameters(
        // TODO(134732760): Support Dalvik VMs, currently fails because libjavacrypto is required
        // and present only in ART runtimes.
        getTestParameters()
            // TODO(b/507731439): Test on ART 17.
            .withDexRuntimesRangeIncluding(Version.V5_1_1, Version.V16_0_0)
            .withAllApiLevels()
            .withApiLevel(AndroidApiLevel.N)
            .build(),
        specs,
        ImmutableList.of(D8_L8SHRINK, D8CF2CF_L8DEBUG));
  }

  public Jdk11StreamAbstractTests(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private static Path JDK_11_STREAM_TEST_CLASSES_DIR;
  private static final Path JDK_11_STREAM_TEST_FILES_DIR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR + "openjdk/jdk-11-test/java/util/stream/test");
  private static Path[] JDK_11_STREAM_TEST_COMPILED_FILES;
  private static final Map<String, Path> TESTNG_SUPPORT_DEX_CACHE = new HashMap<>();

  private static Path[] getJdk11StreamTestFiles() {
    Set<String> runnableRelativePaths = new HashSet<>();
    runnableRelativePaths.addAll(Arrays.asList(STREAM_CLOSE_TESTS));
    runnableRelativePaths.addAll(Arrays.asList(SUCCESSFUL_RUNNABLE_TESTS_ON_JDK11_AND_V7));
    runnableRelativePaths.addAll(Arrays.asList(SUCCESSFUL_RUNNABLE_TESTS_ON_JDK11_ONLY));
    runnableRelativePaths.addAll(Arrays.asList(SUCCESSFUL_RUNNABLE_TESTS));
    Path[] files =
        runnableRelativePaths.stream()
            .map(JDK_11_STREAM_TEST_FILES_DIR::resolve)
            .toArray(Path[]::new);
    assert files.length > 0;
    return files;
  }

  // Cannot succeed with JDK 8 desugared library because use J9 features.
  // Stream close issue with try with resource desugaring mixed with partial library desugaring.
  public static final String[] STREAM_CLOSE_TESTS =
      new String[] {"org/openjdk/tests/java/util/stream/StreamCloseTest.java"};

  // Cannot succeed with JDK 8 desugared library because use J9 features.
  public static final String[] SUCCESSFUL_RUNNABLE_TESTS_ON_JDK11_AND_V7 =
      new String[] {
        // Require the virtual method isDefault() in class java/lang/reflect/Method.
        "org/openjdk/tests/java/util/stream/WhileOpStatefulTest.java",
        // Require a Random method not present before Android 7 and not desugared.
        "org/openjdk/tests/java/util/stream/IntPrimitiveOpsTests.java"
      };

  public static final String[] LONG_RUNNING_SUCCESSFUL_RUNNABLE_TESTS_ON_JDK11_AND_V7 =
      new String[] {
        // Require the virtual method isDefault() in class java/lang/reflect/Method.
        "org/openjdk/tests/java/util/stream/WhileOpTest.java",
      };

  // Disabled because time to run > 1 min for each test.
  // Can be used for experimentation/testing purposes.
  private static String[] LONG_RUNNING_TESTS =
      new String[] {
        "org/openjdk/tests/java/util/stream/InfiniteStreamWithLimitOpTest.java",
        "org/openjdk/tests/java/util/stream/CountLargeTest.java",
        "org/openjdk/tests/java/util/stream/RangeTest.java",
        "org/openjdk/tests/java/util/stream/CollectorsTest.java",
        "org/openjdk/tests/java/util/stream/FlatMapOpTest.java",
        "org/openjdk/tests/java/util/stream/StreamSpliteratorTest.java",
        "org/openjdk/tests/java/util/stream/StreamLinkTest.java",
        "org/openjdk/tests/java/util/stream/StreamBuilderTest.java",
        "org/openjdk/tests/java/util/stream/SliceOpTest.java",
        "org/openjdk/tests/java/util/stream/ToArrayOpTest.java"
      };

  private static final String[] SUCCESSFUL_RUNNABLE_TESTS_ON_JDK11_ONLY =
      new String[] {
        // Assertion error
        "org/openjdk/tests/java/util/stream/CollectAndSummaryStatisticsTest.java",
        "org/openjdk/tests/java/util/stream/CountTest.java",
        // J9 Random problem
        "org/openjdk/tests/java/util/stream/DoublePrimitiveOpsTests.java",
      };

  private static String[] SUCCESSFUL_RUNNABLE_TESTS =
      new String[] {
        "org/openjdk/tests/java/util/stream/FindFirstOpTest.java",
        "org/openjdk/tests/java/util/stream/MapOpTest.java",
        "org/openjdk/tests/java/util/stream/DistinctOpTest.java",
        "org/openjdk/tests/java/util/MapTest.java",
        "org/openjdk/tests/java/util/FillableStringTest.java",
        "org/openjdk/tests/java/util/stream/ForEachOpTest.java",
        "org/openjdk/tests/java/util/stream/CollectionAndMapModifyStreamTest.java",
        "org/openjdk/tests/java/util/stream/GroupByOpTest.java",
        "org/openjdk/tests/java/util/stream/PrimitiveAverageOpTest.java",
        "org/openjdk/tests/java/util/stream/TeeOpTest.java",
        "org/openjdk/tests/java/util/stream/MinMaxTest.java",
        "org/openjdk/tests/java/util/stream/ConcatTest.java",
        "org/openjdk/tests/java/util/stream/StreamParSeqTest.java",
        "org/openjdk/tests/java/util/stream/ReduceByOpTest.java",
        "org/openjdk/tests/java/util/stream/ConcatOpTest.java",
        "org/openjdk/tests/java/util/stream/IntReduceTest.java",
        "org/openjdk/tests/java/util/stream/SortedOpTest.java",
        "org/openjdk/tests/java/util/stream/MatchOpTest.java",
        "org/openjdk/tests/java/util/stream/IntSliceOpTest.java",
        "org/openjdk/tests/java/util/stream/SequentialOpTest.java",
        "org/openjdk/tests/java/util/stream/PrimitiveSumTest.java",
        "org/openjdk/tests/java/util/stream/ReduceTest.java",
        "org/openjdk/tests/java/util/stream/IntUniqOpTest.java",
        "org/openjdk/tests/java/util/stream/FindAnyOpTest.java"
      };

  private boolean streamCloseTestShouldSucceed(boolean isNewerThanV4_4_4) {
    if (libraryDesugaringSpecification == JDK8_JAVA_BASE_EXT) {
      return false;
    }
    // TODO(b/216047740): Investigate if this runs on Dalvik VMs.
    // StreamCloseTest relies on suppressed exceptions which may not work on Dalvik VMs.
    return isNewerThanV4_4_4;
  }

  private Map<String, String> getSuccessfulTests(boolean isV7OrNewer, boolean isNewerThanV4_4_4) {
    Map<String, String> runnableTests = getRunnableTests(SUCCESSFUL_RUNNABLE_TESTS);
    if (libraryDesugaringSpecification != JDK8_JAVA_BASE_EXT) {
      runnableTests.putAll(getRunnableTests(SUCCESSFUL_RUNNABLE_TESTS_ON_JDK11_ONLY));
      if (isV7OrNewer) {
        runnableTests.putAll(getRunnableTests(SUCCESSFUL_RUNNABLE_TESTS_ON_JDK11_AND_V7));
      }
    }
    if (streamCloseTestShouldSucceed(isNewerThanV4_4_4)) {
      runnableTests.putAll(getRunnableTests(STREAM_CLOSE_TESTS));
    }
    return runnableTests;
  }

  Map<String, String> getSuccessfulTests() {
    return getSuccessfulTests(
        parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V7_0_0),
        parameters.getDexRuntimeVersion().isNewerThan(Version.V4_4_4));
  }

  private static Map<String, String> getRunnableTests(String[] tests) {
    Map<String, String> pathToName = new LinkedHashMap<>();
    int javaExtSize = JAVA_EXTENSION.length();
    for (String runnableTest : tests) {
      String nameWithoutJavaExt = runnableTest.substring(0, runnableTest.length() - javaExtSize);
      pathToName.put(
          JDK_11_STREAM_TEST_CLASSES_DIR + "/" + nameWithoutJavaExt + CLASS_EXTENSION,
          nameWithoutJavaExt.replace("/", "."));
    }
    return pathToName;
  }

  @BeforeClass
  public static void compileJdk11StreamTests() throws Exception {
    TESTNG_SUPPORT_DEX_CACHE.clear();
    JDK_11_STREAM_TEST_CLASSES_DIR = getStaticTemp().newFolder("stream").toPath();
    List<String> options =
        Arrays.asList(
            "--add-reads",
            "java.base=ALL-UNNAMED",
            "--patch-module",
            "java.base=" + EXTENSION_PATH);
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addOptions(options)
        .addClasspathFiles(testNGPath())
        .addSourceFiles(getJdk11StreamTestFiles())
        .setOutputPath(JDK_11_STREAM_TEST_CLASSES_DIR)
        .compile();
    JDK_11_STREAM_TEST_COMPILED_FILES =
        getAllFilesWithSuffixInDirectory(JDK_11_STREAM_TEST_CLASSES_DIR, CLASS_EXTENSION);
    assert JDK_11_STREAM_TEST_COMPILED_FILES.length > 0;
  }

  @AfterClass
  public static void clearTestNGSupportDexCache() {
    TESTNG_SUPPORT_DEX_CACHE.clear();
  }

  Map<String, String> split(Map<String, String> input, int index) {
    return Jdk11TestInputSplitter.split(input, index, SPLIT);
  }

  public void testStream(Map<String, String> successes) throws Throwable {
    Assume.assumeFalse(
        "getAllFilesWithSuffixInDirectory() seems to find different files on Windows",
        ToolHelper.isWindows());
    assumeTrue(
        "Requires Java base extensions, should add it when not desugaring",
        parameters.getApiLevel().isLessThan(AndroidApiLevel.N));

    DesugaredLibraryTestCompileResult<?> compileResult = compileStreamTestsToDex();
    SingleTestRunResult<?> result = runAllTests(compileResult, successes);
    runSuccessfulTests(result, successes);
  }

  private Path getTestNGSupportDex() throws Exception {
    String key = parameters.getApiLevel() + ":" + libraryDesugaringSpecification;
    Path cached = TESTNG_SUPPORT_DEX_CACHE.get(key);
    if (cached != null) {
      return cached;
    }
    Path dexZip =
        testForD8(getStaticTemp())
            .addProgramFiles(testNGSupportProgramFiles())
            .addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles())
            .setMinApi(parameters)
            .compile()
            .writeToZip();
    TESTNG_SUPPORT_DEX_CACHE.put(key, dexZip);
    return dexZip;
  }

  private static void addSplitPrefixes(Set<String> prefixes, Map<String, String> splitMap) {
    for (String className : splitMap.values()) {
      prefixes.add(JDK_11_STREAM_TEST_CLASSES_DIR.resolve(className.replace('.', '/')).toString());
    }
  }

  private List<Path> getFilesToCompileForSplit() {
    Set<String> prefixes = new HashSet<>();
    for (boolean isV7OrNewer : new boolean[] {false, true}) {
      for (boolean isNewerThanV4_4_4 : new boolean[] {false, true}) {
        addSplitPrefixes(
            prefixes, split(getSuccessfulTests(isV7OrNewer, isNewerThanV4_4_4), getIndex()));
      }
    }
    int classExtLen = CLASS_EXTENSION.length();
    return Arrays.stream(JDK_11_STREAM_TEST_COMPILED_FILES)
        .filter(
            file -> {
              String fileStr = file.toString();
              if (fileStr.contains("lang/invoke")) {
                return false;
              }
              String withoutExt = fileStr.substring(0, fileStr.length() - classExtLen);
              int dollarIdx = withoutExt.indexOf('$');
              String outerPrefix = dollarIdx >= 0 ? withoutExt.substring(0, dollarIdx) : withoutExt;
              return prefixes.contains(outerPrefix);
            })
        .collect(Collectors.toList());
  }

  DesugaredLibraryTestCompileResult<?> compileStreamTestsToDex() throws Exception {
    List<Path> filesToCompile = getFilesToCompileForSplit();
    Path testNGSupportDex = getTestNGSupportDex();
    // Prohibit publicizing LoggingTestCase#setContext. Currently L8 does not support modifying the
    // InternalOptions for the R8 compilation inside L8, so we use a system property.
    System.setProperty(
        "com.android.tools.r8.accessmodification.forcePackagePrivateAndProtected", "0");
    try {
      return testForDesugaredLibrary(
              parameters, libraryDesugaringSpecification, compilationSpecification)
          .addProgramFiles(filesToCompile)
          .applyIf(
              !libraryDesugaringSpecification.hasNioFileDesugaring(parameters),
              b -> b.addProgramFiles(getPathsFiles()))
          .addProgramFiles(getSafeVarArgsFile())
          .applyOnBuilder(b -> b.addClasspathFiles(testNGSupportProgramFiles()))
          .addProgramClassFileData(getTestNGMainRunner())
          .addL8KeepRules(
              // Keep LoggingTestCase#setContext so that it is not publicized.
              // Otherwise, the test runner incorrectly tries to run it as a @Test.
              "-keepclassmembers class j$.util.stream.LoggingTestCase {",
              "  protected void setContext(java.lang.String, java.lang.Object);",
              "}")
          .disableL8AnnotationRemoval()
          .setTrackDesugaredApiConversions()
          .compile()
          .addRunClasspathFiles(testNGSupportDex)
          .withArt6Plus64BitsLib();
    } finally {
      System.clearProperty(
          "com.android.tools.r8.accessmodification.forcePackagePrivateAndProtected");
    }
  }

  private SingleTestRunResult<?> runAllTests(
      DesugaredLibraryTestCompileResult<?> compileResult, Map<String, String> successes)
      throws Exception {
    String verbosity = "2"; // Increase verbosity for debugging.
    List<String> args = new ArrayList<>(1 + successes.size());
    args.add(verbosity);
    for (String path : successes.keySet()) {
      assert successes.get(path) != null;
      args.add(successes.get(path));
    }
    if (args.size() == 1) {
      return null;
    }
    return compileResult.run(
        parameters.getRuntime(), "TestNGMainRunner", args.toArray(new String[0]));
  }

  private static String extractClassStdOut(String stdOut, String className) {
    String startMarker = StringUtils.lines("Running tests in " + className);
    int startIndex = stdOut.indexOf(startMarker);
    if (startIndex < 0) {
      return null;
    }
    String resultPrefix = "Tests result in " + className + ": ";
    int resultIndex = stdOut.indexOf(resultPrefix, startIndex + startMarker.length());
    if (resultIndex < 0) {
      return null;
    }
    String lineSeparator = System.lineSeparator();
    int lineEnd = stdOut.indexOf(lineSeparator, resultIndex + resultPrefix.length());
    if (lineEnd < 0) {
      return null;
    }
    return stdOut.substring(startIndex, lineEnd + lineSeparator.length());
  }

  private void runSuccessfulTests(SingleTestRunResult<?> result, Map<String, String> successes) {
    for (String path : successes.keySet()) {
      String className = successes.get(path);
      assert className != null;
      String classStdOut =
          result != null ? extractClassStdOut(result.getStdOut(), className) : null;
      assertTrue(
          "Failure in " + path + "\n" + result,
          classStdOut != null
              && classStdOut.endsWith(
                  StringUtils.lines("Tests result in " + className + ": SUCCESS")));
    }
  }

  @Test
  public void testStream() throws Throwable {
    Assume.assumeFalse(
        "getAllFilesWithSuffixInDirectory() seems to find different files on Windows",
        ToolHelper.isWindows());
    assumeTrue(
        "Requires Java base extensions, should add it when not desugaring",
        parameters.getApiLevel().isLessThan(AndroidApiLevel.N));

    DesugaredLibraryTestCompileResult<?> compileResult = compileStreamTestsToDex();
    Map<String, String> successes = split(getSuccessfulTests(), getIndex());
    SingleTestRunResult<?> result = runAllTests(compileResult, successes);
    runSuccessfulTests(result, successes);
  }

  abstract int getIndex();
}
