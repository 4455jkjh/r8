// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.getPathsFiles;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.getTestNGMainRunner;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.jcommanderPath;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.testNGPath;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11SupportFiles.testNGSupportProgramFiles;
import static com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11TestLibraryDesugaringSpecification.EXTENSION_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.utils.internal.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.internal.FileUtils.JAVA_EXTENSION;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.L8TestBuilder;
import com.android.tools.r8.TestDeps;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestState;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.DesugaredLibraryTestCompileResult;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public abstract class Jdk11TimeAbstractTests extends DesugaredLibraryTestBase {

  private static final int SPLIT = 2;
  private static Path JDK_11_TIME_TEST_CLASSES_DIR;
  private static Path[] JDK_11_TIME_TEST_COMPILED_FILES;
  private static final Map<String, Path> TESTNG_SUPPORT_DEX_CACHE = new HashMap<>();
  private static final Map<String, DesugaredLibraryTestCompileResult<?>> COMPILED_TIME_TESTS_CACHE =
      new HashMap<>();

  private static final String[] SUPPORT_CLASSES =
      new String[] {
        "test.java.time.AbstractTest",
        "test.java.time.MockSimplePeriod",
        "test.java.time.format.AbstractTestPrinterParser",
        "test.java.time.temporal.MockFieldValue"
      };

  final TestParameters parameters;
  final LibraryDesugaringSpecification libraryDesugaringSpecification;
  final CompilationSpecification compilationSpecification;
  private boolean isFormatChrono;
  private int splitIndex;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        // TODO(134732760): Support Dalvik VMs, currently fails because libjavacrypto is required
        // and present only in ART runtimes.
        getTestParameters()
            // TODO(b/507731439): Test on ART 17.
            .withDexRuntimesRangeIncluding(Version.V5_1_1, Version.V16_0_0)
            .withAllApiLevels()
            .withApiLevel(AndroidApiLevel.O)
            .withoutCollapsedDexRuntimes()
            .build(),
        ImmutableList.of(JDK8, JDK11_PATH),
        ImmutableList.of(D8_L8DEBUG, D8_L8SHRINK));
  }

  public Jdk11TimeAbstractTests(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Override
  public D8TestBuilder testForD8(Backend backend) {
    return testForD8(getStaticTemp(), backend);
  }

  @Override
  public L8TestBuilder testForL8(AndroidApiLevel apiLevel, Backend backend) {
    return L8TestBuilder.create(apiLevel, backend, new TestState(getStaticTemp()));
  }

  private static List<Path> getJdk11TimeTestFiles() {
    Set<String> classNames = new LinkedHashSet<>();
    Collections.addAll(classNames, SUPPORT_CLASSES);
    Collections.addAll(classNames, RAW_TEMPORAL_SUCCESSES);
    Collections.addAll(classNames, RAW_TEMPORAL_SUCCESSES_IF_BRIDGE);
    Collections.addAll(classNames, RAW_TEMPORAL_SUCCESSES_UP_TO_14);
    Collections.addAll(classNames, RAW_TEMPORAL_ISO_TESTS);
    Collections.addAll(classNames, FORMAT_CHRONO_ISO_TESTS);
    Collections.addAll(classNames, FORMAT_CHRONO_SUCCESSES);
    Collections.addAll(classNames, FORMAT_CHRONO_SUCCESSES_UP_TO_11);
    Path jdk11TimeTestsRootDir = TestDeps.getJdk11TestPath("java", "time");
    List<Path> files =
        classNames.stream()
            .map(name -> jdk11TimeTestsRootDir.resolve(name.replace('.', '/') + JAVA_EXTENSION))
            .collect(Collectors.toList());
    assert !files.isEmpty();
    return files;
  }

  // TestOffsetDateTime_instants#factory_ofInstant_allDaysInCycle already tests every day in a full
  // 400-year Gregorian cycle (146,097 days). Narrow factory_ofInstant_history from 5,640 years
  // (-2820..2820, 2.06M days) to (-5..5) and minYear/maxYear from 420 years to 5 years to avoid
  // ~27s of redundant day-by-day iteration on ART per run.
  private static void transformTestOffsetDateTimeInstants(Path classesDir) throws Exception {
    Path classFile = classesDir.resolve("test/java/time/TestOffsetDateTime_instants.class");
    byte[] transformed =
        transformer(
                classFile,
                Reference.classFromTypeName("test.java.time.TestOffsetDateTime_instants"))
            .addMethodTransformer(
                new MethodTransformer() {
                  @Override
                  public void visitLdcInsn(Object value) {
                    String methodName = getMethod().getMethodName();
                    if (methodName.equals("factory_ofInstant_history")) {
                      if (Long.valueOf(-2820L).equals(value)) {
                        super.visitLdcInsn(-5L);
                        return;
                      }
                      if (Long.valueOf(2820L).equals(value)) {
                        super.visitLdcInsn(5L);
                        return;
                      }
                    } else if (methodName.equals("factory_ofInstant_minYear")
                        && Long.valueOf(Year.MIN_VALUE + 420L).equals(value)) {
                      super.visitLdcInsn(Year.MIN_VALUE + 5L);
                      return;
                    } else if (methodName.equals("factory_ofInstant_maxYear")
                        && Long.valueOf(Year.MAX_VALUE - 420L).equals(value)) {
                      super.visitLdcInsn(Year.MAX_VALUE - 5L);
                      return;
                    }
                    super.visitLdcInsn(value);
                  }
                })
            .transform();
    Files.write(classFile, transformed);
  }

  // TestIsoChronoImpl#provider_rangeVersusCalendar steps day-by-day from 1583 to 2100 (517 years =
  // 188,822 days) twice (~16s on ART per run). Narrow to 1995..2005 (covering leap century 2000,
  // standard leap years 1996/2004, and 53-week ISO years 1998/2004).
  private static void transformTestIsoChronoImpl(Path classesDir) throws Exception {
    Path classFile = classesDir.resolve("test/java/time/chrono/TestIsoChronoImpl.class");
    byte[] transformed =
        transformer(
                classFile, Reference.classFromTypeName("test.java.time.chrono.TestIsoChronoImpl"))
            .addMethodTransformer(
                new MethodTransformer() {
                  @Override
                  public void visitIntInsn(int opcode, int operand) {
                    if (getMethod().getMethodName().equals("provider_rangeVersusCalendar")
                        && opcode == Opcodes.SIPUSH) {
                      if (operand == 1583) {
                        super.visitIntInsn(opcode, 1995);
                        return;
                      }
                      if (operand == 2100) {
                        super.visitIntInsn(opcode, 2005);
                        return;
                      }
                    }
                    super.visitIntInsn(opcode, operand);
                  }
                })
            .transform();
    Files.write(classFile, transformed);
  }

  @BeforeClass
  public static void compileJdk11TimeTests() throws Exception {
    TESTNG_SUPPORT_DEX_CACHE.clear();
    COMPILED_TIME_TESTS_CACHE.clear();
    JDK_11_TIME_TEST_CLASSES_DIR = getStaticTemp().newFolder("time").toPath();
    List<String> options =
        Arrays.asList(
            "--add-reads",
            "java.base=ALL-UNNAMED",
            "--patch-module",
            "java.base=" + EXTENSION_PATH);
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addOptions(options)
        .addClasspathFiles(testNGPath(), jcommanderPath())
        .addSourceFiles(getJdk11TimeTestFiles())
        .setOutputPath(JDK_11_TIME_TEST_CLASSES_DIR)
        .compile();
    transformTestOffsetDateTimeInstants(JDK_11_TIME_TEST_CLASSES_DIR);
    transformTestIsoChronoImpl(JDK_11_TIME_TEST_CLASSES_DIR);
    JDK_11_TIME_TEST_COMPILED_FILES =
        getAllFilesWithSuffixInDirectory(JDK_11_TIME_TEST_CLASSES_DIR, CLASS_EXTENSION);
    assert JDK_11_TIME_TEST_COMPILED_FILES.length > 0;
  }

  @AfterClass
  public static void clearCaches() {
    TESTNG_SUPPORT_DEX_CACHE.clear();
    COMPILED_TIME_TESTS_CACHE.clear();
  }

  // Following tests are also failing on the Bazel build, they cannot be run easily on
  // Android (difference in time precision, iAndroid printing, etc.).
  private static final String[] WONT_FIX_FAILURES =
      new String[] {
        "test.java.time.TestZoneTextPrinterParser.java",
        // Removed by gradle (compile-time error).
        "tck.java.time.TCKZoneId.java",
        "tck.java.time.TCKZoneOffset.java",
        "tck.java.time.TCKChronology.java",
        "tck.java.time.chrono.TCKTestServiceLoader.java",
        "tck.java.time.TCKCopticSerialization.java",
        "tck.java.time.TCKFormatStyle.java",
        "tck.java.time.TCKZoneRules.java",
        "test.java.time.chrono.TestServiceLoader.java",
        "test.java.time.TestJapaneseChronoImpl.java",
        "test.java.time.TestThaiBuddhistChronoImpl.java",
        "test.java.time.TestDateTimeFormatterBuilder.java",
        "test.java.time.TestDateTimeTextProvider.java",
        "test.java.time.TestNonIsoFormatter.java",
        "test.java.time.TestTextParser.java",
        "test.java.time.TestTextPrinter.java",
        "test.java.time.TestChronoField.java",
        "test.java.util.TestFormatter.java",
        // Following also fails using the default libs on P...
        "test.java.time.chrono.TestEraDisplayName",
        "test.java.time.format.TestDateTimeFormatter",
        "test.java.time.TestLocalDate",
        // Formatting problem
        "test.java.time.format.TestNarrowMonthNamesAndDayNames",
      };
  static final String[] RAW_TEMPORAL_SUCCESSES =
      new String[] {
        "test.java.time.TestYearMonth",
        "test.java.time.TestZonedDateTime",
        "test.java.time.TestClock_Tick",
        "test.java.time.TestMonthDay",
        "test.java.time.zone.TestFixedZoneRules",
        "test.java.time.TestOffsetDateTime",
        "test.java.time.TestInstant",
        "test.java.time.TestDuration",
        "test.java.time.TestZoneOffset",
        "test.java.time.TestLocalDateTime",
        "test.java.time.TestClock_Fixed",
        "test.java.time.TestYear",
        "test.java.time.TestLocalTime",
        "test.java.time.TestZoneId",
        "test.java.time.TestOffsetTime",
        "test.java.time.TestClock_Offset",
        "test.java.time.TestPeriod",
        "test.java.time.TestOffsetDateTime_instants",
        "test.java.time.temporal.TestDateTimeBuilderCombinations",
        "test.java.time.temporal.TestJulianFields",
        "test.java.time.temporal.TestChronoUnit",
        "test.java.time.temporal.TestDateTimeValueRange"
      };
  static final String[] RAW_TEMPORAL_SUCCESSES_IF_BRIDGE =
      new String[] {"tck.java.time.TestIsoChronology"};

  static final String[] RAW_TEMPORAL_SUCCESSES_UP_TO_14 =
      new String[] {
        // Reflective lookup Class.forName("java.time.Clock$SystemClock").getDeclaredField("offset")
        // fails.
        "test.java.time.TestClock_System"
      };

  static final String[] RAW_TEMPORAL_ISO_TESTS =
      new String[] {"test.java.time.temporal.TestIsoWeekFields"};
  static final String[] FORMAT_CHRONO_ISO_TESTS =
      new String[] {
        "test.java.time.chrono.TestIsoChronoImpl", "test.java.time.chrono.TestJapaneseChronology",
        // The test was written before the japanese REIWA era, so recent VM have a more accurate
        // result, and old VMs are disabled.
        // "test.java.time.chrono.TestUmmAlQuraChronology",
        // Disabled: missing quarter data on devices where it could be enabled.
        // "test.java.time.format.TestDateTimeFormatterBuilderWithLocale"
      };
  static final String[] FORMAT_CHRONO_SUCCESSES =
      new String[] {
        "test.java.time.format.TestFractionPrinterParser",
        "test.java.time.format.TestStringLiteralParser",
        "test.java.time.format.TestZoneOffsetPrinter",
        "test.java.time.format.TestDecimalStyle",
        "test.java.time.format.TestCharLiteralPrinter",
        "test.java.time.format.TestStringLiteralPrinter",
        "test.java.time.format.TestPadPrinterDecorator",
        "test.java.time.format.TestNumberPrinter",
        "test.java.time.format.TestZoneOffsetParser",
        "test.java.time.format.TestReducedParser",
        "test.java.time.format.TestDateTimeParsing",
        "test.java.time.format.TestSettingsParser",
        "test.java.time.format.TestNumberParser",
        "test.java.time.format.TestReducedPrinter",
        "test.java.time.format.TestCharLiteralParser",
        "test.java.time.chrono.TestChronologyPerf",
        "test.java.time.chrono.TestExampleCode",
        "test.java.time.chrono.TestChronoLocalDate"
      };
  static final String[] FORMAT_CHRONO_SUCCESSES_UP_TO_11 =
      new String[] {
        "test.java.time.format.TestDateTimeTextProviderWithLocale",
        "test.java.time.format.TestUnicodeExtension",
        "test.java.time.format.TestTextParserWithLocale",
        "test.java.time.format.TestTextPrinterWithLocale"
      };

  // The iso implementation in desugared library is based on jdk 12, hence the jdk11 tests related
  // to it fail (Some features are implemented in desugared library and not in jdk11). On Android
  // 12+, the jdk12 behavior is implemented. Time desugaring is enabled up to api 26, so in between
  // 26 and 31 (26 <= api < 31), the iso implementation follows jdk11 implementation, outside, it
  // follows the jdk12 implementation.
  // Below V8, the tests rely on GregorianCalendar#getCalendarType which is not supported.
  private boolean isJdk11IsoCompliant() {
    return (libraryDesugaringSpecification.hasCompleteTimeDesugaring(parameters)
            && parameters.getDexRuntimeVersion().isNewerThan(Version.V8_1_0))
        || parameters.getDexRuntimeVersion().isNewerThan(Version.V10_0_0);
  }

  private static String[] getFormatChronoSuccesses(boolean isOlderThanV12, boolean isIsoCompliant) {
    List<String> allTests = new ArrayList<>();
    Collections.addAll(allTests, FORMAT_CHRONO_SUCCESSES);
    if (isOlderThanV12) {
      // Formatting issues starting from 12.
      Collections.addAll(allTests, FORMAT_CHRONO_SUCCESSES_UP_TO_11);
    }
    if (isIsoCompliant) {
      Collections.addAll(allTests, FORMAT_CHRONO_ISO_TESTS);
    }
    return allTests.toArray(new String[0]);
  }

  public String[] getFormatChronoSuccesses() {
    this.isFormatChrono = true;
    return getFormatChronoSuccesses(
        parameters.getDexRuntimeVersion().isOlderThan(Version.V12_0_0), isJdk11IsoCompliant());
  }

  private static String[] getRawTemporalSuccesses(
      boolean includeIsoTests, boolean isOlderThanV14, boolean includeBridgeTests) {
    List<String> allTests = new ArrayList<>();
    if (includeIsoTests) {
      Collections.addAll(allTests, RAW_TEMPORAL_ISO_TESTS);
    }
    Collections.addAll(allTests, RAW_TEMPORAL_SUCCESSES);
    if (isOlderThanV14) {
      // In 14 some reflection used in test fails.
      Collections.addAll(allTests, RAW_TEMPORAL_SUCCESSES_UP_TO_14);
    }
    if (includeBridgeTests) {
      Collections.addAll(allTests, RAW_TEMPORAL_SUCCESSES_IF_BRIDGE);
    }
    return allTests.toArray(new String[0]);
  }

  public String[] getRawTemporalSuccesses() {
    this.isFormatChrono = false;
    // The bridge is always present with JDK11 due to partial desugaring between 26 and 33.
    // On JDK8 the bridge is absent in between 26 and 33.
    boolean includeBridgeTests =
        libraryDesugaringSpecification != JDK8
            || !parameters
                .getApiLevel()
                .isBetweenBothIncluded(AndroidApiLevel.O, AndroidApiLevel.Sv2);
    return getRawTemporalSuccesses(
        !libraryDesugaringSpecification.hasCompleteTimeDesugaring(parameters)
            && parameters.getDexRuntimeVersion().isNewerThan(Version.V12_0_0),
        parameters.getDexRuntimeVersion().isOlderThan(Version.V14_0_0),
        includeBridgeTests);
  }

  String[] split(String[] input, int index) {
    this.splitIndex = index;
    return Jdk11TestInputSplitter.split(input, index, SPLIT);
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

  private List<Path> getFilesToCompileForSplit() {
    Set<String> classNames = new HashSet<>();
    Collections.addAll(classNames, SUPPORT_CLASSES);
    if (isFormatChrono) {
      for (boolean isOlderThanV12 : new boolean[] {false, true}) {
        for (boolean isIsoCompliant : new boolean[] {false, true}) {
          Collections.addAll(
              classNames,
              Jdk11TestInputSplitter.split(
                  getFormatChronoSuccesses(isOlderThanV12, isIsoCompliant), splitIndex, SPLIT));
        }
      }
    } else {
      for (boolean includeIsoTests : new boolean[] {false, true}) {
        for (boolean isOlderThanV14 : new boolean[] {false, true}) {
          for (boolean includeBridgeTests : new boolean[] {false, true}) {
            Collections.addAll(
                classNames,
                Jdk11TestInputSplitter.split(
                    getRawTemporalSuccesses(includeIsoTests, isOlderThanV14, includeBridgeTests),
                    splitIndex,
                    SPLIT));
          }
        }
      }
    }
    Set<String> prefixes = new HashSet<>();
    for (String className : classNames) {
      prefixes.add(JDK_11_TIME_TEST_CLASSES_DIR.resolve(className.replace('.', '/')).toString());
    }
    int classExtLen = CLASS_EXTENSION.length();
    return Arrays.stream(JDK_11_TIME_TEST_COMPILED_FILES)
        .filter(
            file -> {
              String fileStr = file.toString();
              String withoutExt = fileStr.substring(0, fileStr.length() - classExtLen);
              int dollarIdx = withoutExt.indexOf('$');
              String outerPrefix = dollarIdx >= 0 ? withoutExt.substring(0, dollarIdx) : withoutExt;
              return prefixes.contains(outerPrefix);
            })
        .collect(Collectors.toList());
  }

  private DesugaredLibraryTestCompileResult<?> compileTimeTestsToDex() throws Exception {
    String key =
        isFormatChrono
            + ":"
            + splitIndex
            + ":"
            + parameters.getApiLevel()
            + ":"
            + libraryDesugaringSpecification
            + ":"
            + compilationSpecification;
    DesugaredLibraryTestCompileResult<?> cached = COMPILED_TIME_TESTS_CACHE.get(key);
    if (cached != null) {
      return cached;
    }
    List<Path> filesToCompile = getFilesToCompileForSplit();
    Path testNGSupportDex = getTestNGSupportDex();
    DesugaredLibraryTestCompileResult<?> compileResult =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addProgramFiles(filesToCompile)
            .applyOnBuilder(b -> b.addClasspathFiles(testNGSupportProgramFiles()))
            .addProgramClassFileData(getTestNGMainRunner())
            .applyIf(
                !libraryDesugaringSpecification.hasNioFileDesugaring(parameters),
                b -> b.addProgramFiles(getPathsFiles()))
            .apply(
                b ->
                    // JDK 11 time tests inspect the visibility of private fields and constructors
                    // using reflection.
                    forEachImmutableClass(
                        className ->
                            b.addL8KeepRules(
                                "-keepclassmembers class " + className + " {",
                                "  private final <fields>;",
                                "  private <init>(...);",
                                "}")))
            .compile()
            .addRunClasspathFiles(testNGSupportDex)
            .withArt6Plus64BitsLib();
    COMPILED_TIME_TESTS_CACHE.put(key, compileResult);
    return compileResult;
  }

  void compileAndTestTime(String[] toRun) throws Exception {
    if (toRun.length == 0) {
      System.out.println("No tests to run, may happen while debugging.");
      return;
    }
    // The compilation time is significantly higher than the test time, it is important to compile
    // once and test multiple times on the same artifact for test performance.
    String verbosity = "2";
    DesugaredLibraryTestCompileResult<?> compileResult = compileTimeTestsToDex();
    List<String> args = new ArrayList<>(1 + toRun.length);
    args.add(verbosity);
    Collections.addAll(args, toRun);
    TestRunResult<?> result =
        compileResult.run(parameters.getRuntime(), "TestNGMainRunner", args.toArray(new String[0]));
    for (String success : toRun) {
      if (result.asSingleRuntimeResult().getStdErr().contains("Couldn't find any tzdata")) {
        // TODO(b/134732760): fix missing time zone data.
      } else if (result.asSingleRuntimeResult().getStdErr().contains("no microsecond precision")) {
        // Emulator precision, won't fix.
      } else {
        assertTrue(
            "Failure in " + success + "\n" + result,
            result
                .asSingleRuntimeResult()
                .getStdOut()
                .contains(StringUtils.lines(success + ": SUCCESS")));
      }
    }
  }

  private static void forEachImmutableClass(Consumer<String> consumer) {
    consumer.accept("j$.time.Duration");
    consumer.accept("j$.time.Instant");
    consumer.accept("j$.time.LocalDate");
    consumer.accept("j$.time.LocalDateTime");
    consumer.accept("j$.time.LocalTime");
    consumer.accept("j$.time.MonthDay");
    consumer.accept("j$.time.OffsetDateTime");
    consumer.accept("j$.time.OffsetTime");
    consumer.accept("j$.time.Period");
    consumer.accept("j$.time.Year");
    consumer.accept("j$.time.YearMonth");
    consumer.accept("j$.time.ZonedDateTime");
    consumer.accept("j$.time.ZoneOffset");
    consumer.accept("j$.time.temporal.ValueRange");
  }
}
