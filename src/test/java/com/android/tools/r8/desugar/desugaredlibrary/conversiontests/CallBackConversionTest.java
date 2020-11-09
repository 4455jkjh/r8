// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.utils.InternalOptions.ASM_VERSION;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class CallBackConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;
  private static final String EXPECTED_RESULT = StringUtils.lines("0", "1", "0", "1");
  private static Path CUSTOM_LIB;

  @Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED), BooleanUtils.values());
  }

  public CallBackConversionTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @BeforeClass
  public static void compileCustomLib() throws Exception {
    CUSTOM_LIB =
        testForD8(getStaticTemp())
            .setMinApi(MIN_SUPPORTED)
            .addProgramClasses(CustomLibClass.class)
            .compile()
            .writeToZip();
  }

  private static void assertDuplicatedAPI(CodeInspector i) {
    List<FoundMethodSubject> virtualMethods = i.clazz(Impl.class).virtualMethods();
    assertEquals(2, virtualMethods.size());
    assertTrue(
        virtualMethods.stream()
            .anyMatch(
                m ->
                    m.getMethod()
                        .method
                        .proto
                        .parameters
                        .values[0]
                        .toString()
                        .equals("j$.util.function.Consumer")));
    assertTrue(
        virtualMethods.stream()
            .anyMatch(
                m ->
                    m.getMethod()
                        .method
                        .proto
                        .parameters
                        .values[0]
                        .toString()
                        .equals("java.util.function.Consumer")));
  }

  @Test
  public void testCallBack() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(Impl.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .inspect(CallBackConversionTest::assertDuplicatedAPI)
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), Impl.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private static class ClassInfo {
    private final String classBinaryName;
    private final List<String> methodNames;

    ClassInfo(String classBinaryNamename, List<String> methodNames) {
      this.classBinaryName = classBinaryNamename;
      this.methodNames = methodNames;
    }

    public String getClassBinaryName() {
      return classBinaryName;
    }

    public List<String> getMethodNames() {
      return methodNames;
    }
  }

  private static ClassInfo extractClassInfo(byte[] ccc) {
    class ClassNameExtractor extends ClassVisitor {
      private String classBinaryName;
      private final List<String> methodNames = new ArrayList<>();

      private ClassNameExtractor() {
        super(ASM_VERSION);
      }

      @Override
      public void visit(
          int version,
          int access,
          String name,
          String signature,
          String superName,
          String[] interfaces) {
        classBinaryName = name;
        super.visit(version, access, name, signature, superName, interfaces);
      }

      @Override
      public MethodVisitor visitMethod(
          int access, String name, String desc, String signature, String[] exceptions) {
        methodNames.add(name);
        return null;
      }

      ClassInfo getClassInfo() {
        return new ClassInfo(classBinaryName, methodNames);
      }
    }

    ClassReader reader = new ClassReader(ccc);
    ClassNameExtractor extractor = new ClassNameExtractor();
    reader.accept(
        extractor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return extractor.getClassInfo();
  }

  @Test
  public void testCallBackD8Cf() throws Exception {
    // Use D8 to desugar with Java classfile output.
    Path firstJar =
        testForD8(Backend.CF)
            .setMinApi(parameters.getApiLevel())
            .addProgramClasses(Impl.class)
            .addLibraryClasses(CustomLibClass.class)
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), new AbsentKeepRuleConsumer())
            .compile()
            .inspect(CallBackConversionTest::assertDuplicatedAPI)
            .writeToZip();

    ClassInfo info =
        extractClassInfo(
            ZipUtils.readSingleEntry(firstJar, ZipUtils.zipEntryNameForClass(Impl.class)));
    assertEquals(
        Impl.class.getTypeName(),
        DescriptorUtils.getJavaTypeFromBinaryName(info.getClassBinaryName()));
    assertEquals(2, info.methodNames.stream().filter(name -> name.equals("foo")).count());

    // Use D8 to desugar with Java classfile output.
    Path secondJar =
        testForD8(Backend.CF)
            .setMinApi(parameters.getApiLevel())
            .addProgramFiles(firstJar)
            .addLibraryClasses(CustomLibClass.class)
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), new AbsentKeepRuleConsumer())
            .compile()
            .inspect(CallBackConversionTest::assertDuplicatedAPI)
            .writeToZip();

    info =
        extractClassInfo(
            ZipUtils.readSingleEntry(secondJar, ZipUtils.zipEntryNameForClass(Impl.class)));
    assertEquals(
        Impl.class.getTypeName(),
        DescriptorUtils.getJavaTypeFromBinaryName(info.getClassBinaryName()));
    // TODO(b/171867367): This should only be 2.
    assertEquals(3, info.getMethodNames().stream().filter(name -> name.equals("foo")).count());

    // Convert to DEX without desugaring and run.
    testForD8()
        .addProgramFiles(firstJar)
        .setMinApi(parameters.getApiLevel())
        .disableDesugaring()
        .compile()
        .inspect(CallBackConversionTest::assertDuplicatedAPI)
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            collectKeepRulesWithTraceReferences(
                firstJar, buildDesugaredLibraryClassFile(parameters.getApiLevel())),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), Impl.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testCallBackR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(Backend.DEX)
        .addKeepMainRule(Impl.class)
        .noMinification()
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(Impl.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .inspect(this::assertLibraryOverridesThere)
        .addDesugaredCoreLibraryRunClassPath(this::buildDesugaredLibrary, parameters.getApiLevel())
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), Impl.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testCallBackR8Minifying() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(Backend.DEX)
        .addKeepMainRule(Impl.class)
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(Impl.class)
        .addLibraryClasses(CustomLibClass.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .inspect(this::assertLibraryOverridesThere)
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .addRunClasspathFiles(CUSTOM_LIB)
        .run(parameters.getRuntime(), Impl.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void assertLibraryOverridesThere(CodeInspector i) {
    // The j$ method can be optimized, but the java method should be present to be called
    // through the library.
    List<FoundMethodSubject> virtualMethods = i.clazz(Impl.class).virtualMethods();
    assertTrue(
        virtualMethods.stream()
            .anyMatch(
                m ->
                    m.getMethod().method.name.toString().equals("foo")
                        && m.getMethod()
                            .method
                            .proto
                            .parameters
                            .values[0]
                            .toString()
                            .equals("java.util.function.Consumer")));
  }

  static class Impl extends CustomLibClass {

    public int foo(Consumer<Object> o) {
      o.accept(0);
      return 1;
    }

    public static void main(String[] args) {
      Impl impl = new Impl();
      // Call foo through java parameter.
      System.out.println(CustomLibClass.callFoo(impl, System.out::println));
      // Call foo through j$ parameter.
      System.out.println(impl.foo(System.out::println));
    }
  }

  abstract static class CustomLibClass {

    public abstract int foo(Consumer<Object> consumer);

    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    public static int callFoo(CustomLibClass object, Consumer<Object> consumer) {
      return object.foo(consumer);
    }
  }
}
