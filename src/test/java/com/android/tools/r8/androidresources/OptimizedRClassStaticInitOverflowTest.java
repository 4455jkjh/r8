// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.ResourceTableInspector;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.TestResourceTable;
import com.android.tools.r8.androidresources.staticinit.R;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.internal.BooleanBox;
import com.android.tools.r8.utils.internal.BooleanUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

@RunWith(Parameterized.class)
public class OptimizedRClassStaticInitOverflowTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withDefaultDexRuntime().withAllApiLevels().build();
  }

  private static AndroidTestResource splitResources;
  private static AndroidTestResource splitResourcesWithNonOverflowHelpers;
  private static AndroidTestResource largeResources;

  @BeforeClass
  public static void setup() throws Exception {
    splitResources =
        buildSplitResources(false, R.string.class, R.drawable.class, R.styleable.class);
    splitResourcesWithNonOverflowHelpers =
        buildSplitResources(true, R.string.class, R.drawable.class, R.styleable.class);
    largeResources = buildLargeResources();
  }

  private void assertShrunkenResources(ResourceTableInspector inspector) {
    inspector.assertContainsResourceWithName("string", "used_clinit");
    inspector.assertContainsResourceWithName("string", "used_0");
    inspector.assertContainsResourceWithName("string", "used_1");
    inspector.assertDoesNotContainResourceWithName("string", "unused_clinit");
    inspector.assertDoesNotContainResourceWithName("string", "unused_0");
    inspector.assertDoesNotContainResourceWithName("string", "unused_1");

    inspector.assertContainsResourceWithName("drawable", "used_drawable_clinit");
    inspector.assertContainsResourceWithName("drawable", "used_drawable_0");
    inspector.assertContainsResourceWithName("drawable", "used_drawable_1");
    inspector.assertDoesNotContainResourceWithName("drawable", "unused_drawable_clinit");
    inspector.assertDoesNotContainResourceWithName("drawable", "unused_drawable_0");
    inspector.assertDoesNotContainResourceWithName("drawable", "unused_drawable_1");

    inspector.assertDoesNotContainResourceWithName("styleable", "used_styleable_clinit");
    inspector.assertDoesNotContainResourceWithName("styleable", "used_styleable_0");
    inspector.assertDoesNotContainResourceWithName("styleable", "used_styleable_1");
    inspector.assertDoesNotContainResourceWithName("styleable", "unused_styleable_clinit");
    inspector.assertDoesNotContainResourceWithName("styleable", "unused_styleable_0");
    inspector.assertDoesNotContainResourceWithName("styleable", "unused_styleable_1");

    for (int i = 0; i < 4; i++) {
      inspector.assertContainsResourceWithName("attr", "attr_used_styleable_clinit" + i);
      inspector.assertContainsResourceWithName("attr", "attr_used_styleable_0" + i);
      inspector.assertContainsResourceWithName("attr", "attr_used_styleable_1" + i);
      inspector.assertDoesNotContainResourceWithName("attr", "attr_unused_styleable_clinit" + i);
      inspector.assertDoesNotContainResourceWithName("attr", "attr_unused_styleable_0" + i);
      inspector.assertDoesNotContainResourceWithName("attr", "attr_unused_styleable_1" + i);
    }
  }

  private static String expectedOutput(AndroidTestResource testResources) throws Exception {
    TestResourceTable table = testResources.getTestResourceTable();
    return StringUtils.lines(
        Integer.toString(table.idFor("string", "used_clinit")),
        Integer.toString(table.idFor("string", "used_0")),
        Integer.toString(table.idFor("string", "used_1")),
        Integer.toString(table.idFor("drawable", "used_drawable_clinit")),
        Integer.toString(table.idFor("drawable", "used_drawable_0")),
        Integer.toString(table.idFor("drawable", "used_drawable_1")),
        Integer.toString(table.idFor("attr", "attr_used_styleable_clinit0")),
        Integer.toString(table.idFor("attr", "attr_used_styleable_00")),
        Integer.toString(table.idFor("attr", "attr_used_styleable_10")));
  }

  @Test
  public void testR8Release() throws Exception {
    AndroidTestResource testResources = splitResources;
    testForR8(parameters)
        .addProgramClasses(TestClass.class)
        .addAndroidResources(testResources)
        .enableOptimizedShrinking()
        .addKeepMainRule(TestClass.class)
        .compile()
        .inspectShrunkenResources(this::assertShrunkenResources)
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(R.string.class), isAbsent());
              assertThat(inspector.clazz(R.drawable.class), isAbsent());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput(testResources));
  }

  @Test
  public void testR8Debug() throws Exception {
    AndroidTestResource testResources = splitResources;
    testForR8(parameters)
        .debug()
        .addProgramClasses(TestClass.class)
        .addAndroidResources(testResources)
        .enableOptimizedShrinking()
        .addKeepMainRule(TestClass.class)
        .compile()
        .inspectShrunkenResources(this::assertShrunkenResources)
        .inspect(
            inspector -> {
              ClassSubject rString = inspector.clazz(R.string.class);
              assertThat(rString, isPresent());
              assertThat(rString.uniqueMethodWithOriginalName("staticInit0"), isPresent());
              assertThat(rString.uniqueMethodWithOriginalName("staticInit1"), isPresent());
              ClassSubject rDrawable = inspector.clazz(R.drawable.class);
              assertThat(rDrawable, isPresent());
              assertThat(rDrawable.uniqueMethodWithOriginalName("staticInit0"), isPresent());
              assertThat(rDrawable.uniqueMethodWithOriginalName("staticInit1"), isPresent());
              ClassSubject rStyleable = inspector.clazz(R.styleable.class);
              assertThat(rStyleable, isPresent());
              assertThat(rStyleable.uniqueMethodWithOriginalName("staticInit0"), isPresent());
              assertThat(rStyleable.uniqueMethodWithOriginalName("staticInit1"), isPresent());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput(testResources));
  }

  @Test
  public void testR8KeepAll() throws Exception {
    AndroidTestResource testResources = splitResources;
    testForR8(parameters)
        .addProgramClasses(TestClass.class)
        .addAndroidResources(testResources)
        .enableOptimizedShrinking()
        .addKeepMainRule(TestClass.class)
        .addKeepAllClassesRule()
        .compile()
        .inspectShrunkenResources(this::assertShrunkenResources)
        .inspect(
            inspector -> {
              ClassSubject rString = inspector.clazz(R.string.class);
              assertThat(rString, isPresent());
              assertThat(rString.uniqueMethodWithOriginalName("staticInit0"), isPresent());
              assertThat(rString.uniqueMethodWithOriginalName("staticInit1"), isPresent());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput(testResources));
  }

  @Test
  public void testR8KeepFieldsOnly() throws Exception {
    AndroidTestResource testResources = splitResources;
    testForR8(parameters)
        .addProgramClasses(TestClass.class)
        .addAndroidResources(testResources)
        .enableOptimizedShrinking()
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-keep class " + R.string.class.getTypeName() + " { <fields>; }")
        .compile()
        .inspectShrunkenResources(this::assertShrunkenResources)
        .inspect(
            inspector -> {
              ClassSubject rString = inspector.clazz(R.string.class);
              assertThat(rString, isPresent());
              assertThat(rString.clinit(), isAbsent());
              assertThat(rString.uniqueMethodWithOriginalName("staticInit0"), isAbsent());
              assertThat(rString.uniqueMethodWithOriginalName("staticInit1"), isAbsent());
              FieldSubject used0 = rString.uniqueFieldWithOriginalName("used_0");
              assertTrue(used0.hasExplicitStaticValue());
              assertEquals(
                  testResources.getTestResourceTable().idFor("string", "used_0"),
                  used0.getStaticValue().asDexValueInt().getValue());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput(testResources));
  }

  @Test
  public void testR8KeepWithObfuscationAndUnreadFiltering() throws Exception {
    AndroidTestResource testResources = splitResources;
    testForR8(parameters)
        .addProgramClasses(TestClass.class)
        .addAndroidResources(testResources)
        .enableOptimizedShrinking()
        .addKeepMainRule(TestClass.class)
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addDontOptimize()
        .compile()
        .inspectShrunkenResources(this::assertShrunkenResources)
        .inspect(
            inspector -> {
              ClassSubject rString = inspector.clazz(R.string.class);
              assertThat(rString, isPresentAndRenamed());
              assertThat(
                  rString.uniqueMethodWithOriginalName("staticInit0"), isPresentAndRenamed());
              assertThat(
                  rString.uniqueMethodWithOriginalName("staticInit1"), isPresentAndRenamed());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput(testResources));
  }

  @Test
  public void testNonStaticInitMethodsOnRClassNotIgnored() throws Exception {
    AndroidTestResource testResources = splitResourcesWithNonOverflowHelpers;
    for (boolean removeUnread : BooleanUtils.values()) {
      testForR8(parameters)
          .addProgramClasses(TestClass.class)
          .addAndroidResources(testResources)
          .enableOptimizedShrinking()
          .addOptionsModification(o -> o.removeUnreadKeptRClassResources = removeUnread)
          .addKeepMainRule(TestClass.class)
          .addKeepAllClassesRuleWithAllowObfuscation()
          .addDontOptimize()
          .compile()
          .inspectShrunkenResources(
              inspector -> {
                if (removeUnread) {
                  assertShrunkenResources(inspector);
                } else {
                  inspector.assertContainsResourceWithName("string", "unused_0");
                }
                inspector.assertContainsResourceWithName("string", "used_by_static_init_0");
                inspector.assertContainsResourceWithName("string", "used_by_static_init_foo_0");
                inspector.assertDoesNotContainResourceWithName(
                    "string", "only_in_non_overflow_helper");
              })
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(expectedOutput(testResources));
    }
  }

  @Test
  public void testR8KeepFieldWithoutUnreadFiltering() throws Exception {
    AndroidTestResource testResources = splitResources;
    for (boolean dontOptimize : BooleanUtils.values()) {
      testForR8(parameters)
          .addProgramClasses(TestClass.class)
          .addAndroidResources(testResources)
          .enableOptimizedShrinking()
          .addOptionsModification(o -> o.removeUnreadKeptRClassResources = false)
          .applyIf(dontOptimize, b -> b.addDontOptimize())
          .addKeepMainRule(TestClass.class)
          .addKeepRules("-keep class " + R.string.class.getTypeName() + " { int unused_0; }")
          .compile()
          .inspectShrunkenResources(
              inspector -> {
                inspector.assertContainsResourceWithName("string", "used_clinit");
                inspector.assertContainsResourceWithName("string", "used_0");
                inspector.assertContainsResourceWithName("string", "used_1");
                // Explicitly kept in staticInit0() with removeUnreadKeptRClassResources = false.
                inspector.assertContainsResourceWithName("string", "unused_0");
                inspector.assertDoesNotContainResourceWithName("string", "unused_clinit");
                inspector.assertDoesNotContainResourceWithName("string", "unused_1");
              })
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(expectedOutput(testResources));
    }
  }

  @Test
  public void testR8PartialWithRClassInR8() throws Exception {
    runR8PartialTest(false);
  }

  @Test
  public void testR8PartialWithRClassInD8() throws Exception {
    runR8PartialTest(true);
  }

  private void runR8PartialTest(boolean rClassInD8) throws Exception {
    parameters.assumeCanUseR8Partial();
    AndroidTestResource testResources = splitResources;
    TestResourceTable table = testResources.getTestResourceTable();
    String expectedPartialOutput =
        StringUtils.lines(
            Integer.toString(table.idFor("string", "used_0")),
            Integer.toString(table.idFor("attr", "attr_used_styleable_00")),
            Integer.toString(table.idFor("string", "used_1")),
            Integer.toString(table.idFor("attr", "attr_used_styleable_10")));
    testForR8Partial(parameters)
        .addR8IncludedClasses(InR8.class)
        .addR8ExcludedClasses(InD8.class)
        .addAndroidResources(testResources)
        .enableOptimizedShrinking()
        .applyIf(
            rClassInD8,
            b -> b.addR8ExcludedClasses(false, R.string.class, R.drawable.class, R.styleable.class),
            b -> b.addR8IncludedClasses(false, R.string.class, R.drawable.class, R.styleable.class))
        .addKeepMainRule(InR8.class)
        .compile()
        .inspectShrunkenResources(
            inspector -> {
              inspector.assertContainsResourceWithName("string", "used_0");
              inspector.assertContainsResourceWithName("string", "used_1");
              inspector.assertDoesNotContainResourceWithName("string", "used_clinit");
              inspector.assertDoesNotContainResourceWithName("string", "unused_clinit");
              inspector.assertDoesNotContainResourceWithName("string", "unused_0");
              inspector.assertDoesNotContainResourceWithName("string", "unused_1");
              for (int i = 0; i < 4; i++) {
                inspector.assertContainsResourceWithName("attr", "attr_used_styleable_0" + i);
                inspector.assertContainsResourceWithName("attr", "attr_used_styleable_1" + i);
                inspector.assertDoesNotContainResourceWithName(
                    "attr", "attr_used_styleable_clinit" + i);
                inspector.assertDoesNotContainResourceWithName(
                    "attr", "attr_unused_styleable_clinit" + i);
                inspector.assertDoesNotContainResourceWithName(
                    "attr", "attr_unused_styleable_0" + i);
                inspector.assertDoesNotContainResourceWithName(
                    "attr", "attr_unused_styleable_1" + i);
              }
            })
        .inspect(
            inspector -> {
              if (rClassInD8) {
                ClassSubject rString = inspector.clazz(R.string.class);
                assertThat(rString, isPresent());
                assertThat(rString.uniqueMethodWithOriginalName("staticInit0"), isPresent());
                assertThat(rString.uniqueMethodWithOriginalName("staticInit1"), isPresent());
                ClassSubject rStyleable = inspector.clazz(R.styleable.class);
                assertThat(rStyleable, isPresent());
                assertThat(rStyleable.uniqueMethodWithOriginalName("staticInit0"), isPresent());
                assertThat(rStyleable.uniqueMethodWithOriginalName("staticInit1"), isPresent());
              } else {
                // InD8 reads R.string.used_1 and R.styleable.used_styleable_1 across the D8/R8
                // boundary, while R8 folds staticInit0/staticInit1 in the R8 partition.
                ClassSubject rString = inspector.clazz(R.string.class);
                assertThat(rString, isPresent());
                assertThat(rString.clinit(), isAbsent());
                assertThat(rString.uniqueMethodWithOriginalName("staticInit0"), isAbsent());
                assertThat(rString.uniqueMethodWithOriginalName("staticInit1"), isAbsent());
                assertThat(rString.uniqueFieldWithOriginalName("used_0"), isAbsent());
                assertThat(rString.uniqueFieldWithOriginalName("used_1"), isPresent());
                ClassSubject rStyleable = inspector.clazz(R.styleable.class);
                assertThat(rStyleable, isPresent());
                assertThat(rStyleable.uniqueMethodWithOriginalName("staticInit0"), isAbsent());
                assertThat(rStyleable.uniqueMethodWithOriginalName("staticInit1"), isAbsent());
                assertThat(inspector.clazz(R.drawable.class), isAbsent());
              }
            })
        .run(parameters.getRuntime(), InR8.class)
        .assertSuccessWithOutput(expectedPartialOutput);
  }

  @Test
  public void testLargeRClassExceeding64KB() throws Exception {
    AndroidTestResource testResources = largeResources;
    testForR8(parameters)
        .addProgramClasses(LargeTestClass.class)
        .addAndroidResources(testResources)
        .enableOptimizedShrinking()
        .addKeepMainRule(LargeTestClass.class)
        .compile()
        .inspectShrunkenResources(
            inspector -> {
              inspector.assertContainsResourceWithName("string", "app_name");
              inspector.assertContainsResourceWithName("string", "used_clinit");
              inspector.assertContainsResourceWithName("string", "used_0");
              inspector.assertDoesNotContainResourceWithName("string", "str_0");
              inspector.assertDoesNotContainResourceWithName("string", "str_10920");
              inspector.assertDoesNotContainResourceWithName("string", "str_10921");
              inspector.assertDoesNotContainResourceWithName(
                  "string", "str_" + (LARGE_UNUSED_STRING_COUNT - 1));
              assertEquals(3, inspector.entries("string").size());
            })
        .inspect(inspector -> assertThat(inspector.clazz(R.string.class), isAbsent()))
        .run(parameters.getRuntime(), LargeTestClass.class)
        .assertSuccessWithOutput(expectedLargeOutput(testResources));
  }

  public static class TestClass {
    public static void main(String[] args) {
      System.out.println(R.string.used_clinit);
      System.out.println(R.string.used_0);
      System.out.println(R.string.used_1);
      System.out.println(R.drawable.used_drawable_clinit);
      System.out.println(R.drawable.used_drawable_0);
      System.out.println(R.drawable.used_drawable_1);
      System.out.println(R.styleable.used_styleable_clinit[0]);
      System.out.println(R.styleable.used_styleable_0[0]);
      System.out.println(R.styleable.used_styleable_1[0]);
    }
  }

  public static class InR8 {
    public static void main(String[] args) {
      System.out.println(R.string.used_0);
      System.out.println(R.styleable.used_styleable_0[0]);
      InD8.callMe();
    }
  }

  public static class InD8 {
    public static void callMe() {
      System.out.println(R.string.used_1);
      System.out.println(R.styleable.used_styleable_1[0]);
    }
  }

  // 11,050 fields * 6 bytes/field (> 66,200 bytes) exceeds the 65,535-byte JVM method Code limit.
  private static final int LARGE_UNUSED_STRING_COUNT = 11_050;
  private static final int MAX_BYTES_BEFORE_INVOKE_AND_RETURN = 65_531;

  private static AndroidTestResource buildSplitResources(
      boolean addNonOverflowHelpers, Class<?>... rClasses) throws Exception {
    TemporaryFolder resourceTemp = new TemporaryFolder(getStaticTemp().newFolder());
    resourceTemp.create();
    AndroidTestResourceBuilder builder =
        new AndroidTestResourceBuilder()
            .withSimpleManifestAndAppNameString()
            .addRClassInitializeWithDefaultValues(rClasses);
    if (addNonOverflowHelpers) {
      builder
          .addStringValue("used_by_static_init_0", "used_by_static_init_0")
          .addStringValue("used_by_static_init_foo_0", "used_by_static_init_foo_0")
          .addStringValue("only_in_non_overflow_helper", "only_in_non_overflow_helper");
    }
    AndroidTestResource testResources = builder.build(resourceTemp);
    if (addNonOverflowHelpers) {
      stripFieldFromRString(testResources, "only_in_non_overflow_helper");
    }
    splitClinitIntoStaticInitChain(testResources);
    if (addNonOverflowHelpers) {
      addNonOverflowStaticInitHelpers(testResources);
    }
    return testResources;
  }

  private static AndroidTestResource buildLargeResources() throws Exception {
    TemporaryFolder resourceTemp = new TemporaryFolder(getStaticTemp().newFolder());
    resourceTemp.create();
    AndroidTestResourceBuilder builder =
        new AndroidTestResourceBuilder()
            .withSimpleManifestAndAppNameString()
            .useStaticValueForNonFinalFields()
            .addStringValue("used_clinit", "used_clinit")
            .addStringValue("used_0", "used_0");
    for (int i = 0; i < LARGE_UNUSED_STRING_COUNT; i++) {
      builder.addStringValue("str_" + i, "v" + i);
    }
    AndroidTestResource testResources = builder.build(resourceTemp);
    TestResourceTable resourceTable = testResources.getTestResourceTable();
    List<String> orderedFields = new ArrayList<>(LARGE_UNUSED_STRING_COUNT + 2);
    orderedFields.add("used_clinit");
    for (int i = 0; i < LARGE_UNUSED_STRING_COUNT; i++) {
      orderedFields.add("str_" + i);
    }
    orderedFields.add("used_0");

    String internalName = binaryName(R.string.class);
    byte[] largeRStringBytes =
        generateOverflowRStringClass(internalName, orderedFields, resourceTable);
    testResources.getRClass().getClassFileData().clear();
    testResources.getRClass().getClassFileData().put(internalName + ".class", largeRStringBytes);
    return testResources;
  }

  /**
   * Rewrites {@code <clinit>()} on each {@code R$<type>} class into {@code <clinit>() ->
   * staticInit0() -> staticInit1()} matching {@code ResourceProcessorBusyBox} ({@code
   * RClassGenerator}) when {@code <clinit>()} exceeds the 64 KB JVM {@code Code} limit.
   */
  private static void splitClinitIntoStaticInitChain(AndroidTestResource testResources) {
    testResources
        .getRClass()
        .getClassFileData()
        .replaceAll(
            (unused, bytes) -> {
              ClassNode classNode = new ClassNode();
              new ClassReader(bytes).accept(classNode, ClassReader.SKIP_DEBUG);
              // ResourceProcessorBusyBox emits Java 7 (V1_7) class files.
              classNode.version = Opcodes.V1_7;
              // Unlike aapt2, RClassGenerator also emits non-final R$styleable int[] fields when
              // generating non-final ids, which is the only mode that splits <clinit>().
              classNode.fields.forEach(field -> field.access &= ~Opcodes.ACC_FINAL);
              MethodNode clinit =
                  classNode.methods.stream()
                      .filter(m -> m.name.equals("<clinit>"))
                      .findFirst()
                      .orElseThrow();
              int access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
              MethodNode staticInit0 = new MethodNode(access, "staticInit0", "()V", null, null);
              MethodNode staticInit1 = new MethodNode(access, "staticInit1", "()V", null, null);
              InsnList clinitInsns = new InsnList();
              List<AbstractInsnNode> pending = new ArrayList<>();
              for (AbstractInsnNode insn : clinit.instructions) {
                if (insn.getOpcode() == Opcodes.RETURN) {
                  break;
                }
                pending.add(insn);
                if (insn.getOpcode() == Opcodes.PUTSTATIC) {
                  FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                  InsnList target =
                      fieldInsn.name.endsWith("_0")
                          ? staticInit0.instructions
                          : fieldInsn.name.endsWith("_1") ? staticInit1.instructions : clinitInsns;
                  for (AbstractInsnNode p : pending) {
                    target.add(p.clone(null));
                  }
                  pending.clear();
                }
              }
              clinitInsns.add(
                  new MethodInsnNode(
                      Opcodes.INVOKESTATIC, classNode.name, "staticInit0", "()V", false));
              clinitInsns.add(new InsnNode(Opcodes.RETURN));
              staticInit0.instructions.add(
                  new MethodInsnNode(
                      Opcodes.INVOKESTATIC, classNode.name, "staticInit1", "()V", false));
              staticInit0.instructions.add(new InsnNode(Opcodes.RETURN));
              staticInit1.instructions.add(new InsnNode(Opcodes.RETURN));
              clinit.instructions = clinitInsns;
              classNode.methods.add(staticInit0);
              classNode.methods.add(staticInit1);

              ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
              classNode.accept(writer);
              return writer.toByteArray();
            });
  }

  private static void stripFieldFromRString(AndroidTestResource testResources, String fieldName) {
    BooleanBox stripped = new BooleanBox();
    testResources
        .getRClass()
        .getClassFileData()
        .replaceAll(
            (entryName, bytes) -> {
              if (!entryName.endsWith("R$string.class")) {
                return bytes;
              }
              ClassNode classNode = new ClassNode();
              new ClassReader(bytes).accept(classNode, ClassReader.SKIP_DEBUG);
              classNode.fields.removeIf(f -> f.name.equals(fieldName));
              MethodNode clinit =
                  classNode.methods.stream()
                      .filter(m -> m.name.equals("<clinit>"))
                      .findFirst()
                      .orElseThrow();
              for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; ) {
                AbstractInsnNode next = insn.getNext();
                if (insn.getOpcode() == Opcodes.PUTSTATIC
                    && ((FieldInsnNode) insn).name.equals(fieldName)) {
                  clinit.instructions.remove(insn.getPrevious());
                  clinit.instructions.remove(insn);
                  stripped.set();
                }
                insn = next;
              }
              ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
              classNode.accept(writer);
              return writer.toByteArray();
            });
    assertTrue(stripped.isTrue());
  }

  private static void addNonOverflowStaticInitHelpers(AndroidTestResource testResources)
      throws Exception {
    int helperOnlyId =
        testResources.getTestResourceTable().idFor("string", "only_in_non_overflow_helper");
    BooleanBox transformed = new BooleanBox();
    testResources
        .getRClass()
        .getClassFileData()
        .replaceAll(
            (entryName, bytes) -> {
              if (!entryName.endsWith("R$string.class")) {
                return bytes;
              }
              transformed.set();
              ClassNode classNode = new ClassNode();
              new ClassReader(bytes).accept(classNode, ClassReader.SKIP_DEBUG);
              int publicStatic = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
              classNode.methods.add(
                  buildNonOverflowHelperMethod(
                      publicStatic,
                      classNode.name,
                      "staticInit",
                      "()V",
                      "used_by_static_init_0",
                      helperOnlyId));
              classNode.methods.add(
                  buildNonOverflowHelperMethod(
                      publicStatic,
                      classNode.name,
                      "staticInitFoo",
                      "()V",
                      "used_by_static_init_foo_0",
                      helperOnlyId));
              classNode.methods.add(
                  buildNonOverflowHelperMethod(
                      publicStatic,
                      classNode.name,
                      "staticInitFoo0",
                      "()V",
                      "used_by_static_init_foo_0",
                      helperOnlyId));
              classNode.methods.add(
                  buildNonOverflowHelperMethod(
                      publicStatic,
                      classNode.name,
                      "customInit0",
                      "()V",
                      "used_by_static_init_0",
                      helperOnlyId));
              classNode.methods.add(
                  buildNonOverflowHelperMethod(
                      publicStatic,
                      classNode.name,
                      "staticInit2",
                      "(I)V",
                      "used_by_static_init_0",
                      helperOnlyId));
              classNode.methods.add(
                  buildNonOverflowHelperMethod(
                      publicStatic,
                      classNode.name,
                      "staticInit3",
                      "()I",
                      "used_by_static_init_0",
                      helperOnlyId));
              classNode.methods.add(
                  buildNonOverflowHelperMethod(
                      Opcodes.ACC_PUBLIC,
                      classNode.name,
                      "staticInit4",
                      "()V",
                      "used_by_static_init_0",
                      helperOnlyId));

              ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
              classNode.accept(writer);
              return writer.toByteArray();
            });
    assertTrue(transformed.isTrue());
  }

  private static MethodNode buildNonOverflowHelperMethod(
      int access,
      String owner,
      String methodName,
      String methodDesc,
      String fieldName,
      int helperOnlyId) {
    MethodNode method = new MethodNode(access, methodName, methodDesc, null, null);
    method.instructions.add(
        new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
    method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, owner, fieldName, "I"));
    method.instructions.add(
        new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false));
    // Also place helperOnlyId into an int[] ArrayPut so ConstResourceNumberRewriter would rewrite
    // it to a ResourceConstNumber if this method were misidentified as a staticInit<N>() helper.
    method.instructions.add(new InsnNode(Opcodes.ICONST_1));
    method.instructions.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT));
    method.instructions.add(new InsnNode(Opcodes.DUP));
    method.instructions.add(new InsnNode(Opcodes.ICONST_0));
    method.instructions.add(new LdcInsnNode(helperOnlyId));
    method.instructions.add(new InsnNode(Opcodes.IASTORE));
    method.instructions.add(
        new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Arrays", "hashCode", "([I)I", false));
    if (methodDesc.endsWith(")I")) {
      method.instructions.add(new InsnNode(Opcodes.IRETURN));
    } else {
      method.instructions.add(new InsnNode(Opcodes.POP));
      method.instructions.add(new InsnNode(Opcodes.RETURN));
    }
    return method;
  }

  private static byte[] generateOverflowRStringClass(
      String internalName, List<String> fieldNames, TestResourceTable resourceTable) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    cw.visit(
        Opcodes.V1_7,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
        internalName,
        null,
        "java/lang/Object",
        null);
    for (String fieldName : fieldNames) {
      cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, fieldName, "I", null, null).visitEnd();
    }

    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
    mv.visitCode();
    int methodIndex = 0;
    int estimatedBytes = 0;
    for (String fieldName : fieldNames) {
      // Conservatively budget 6 bytes per field (3-byte ldc_w + 3-byte putstatic), matching
      // ResourceProcessorBusyBox's RClassGenerator.writeStaticClassInit.
      if (estimatedBytes + 6 > MAX_BYTES_BEFORE_INVOKE_AND_RETURN) {
        String overflowMethodName = "staticInit" + methodIndex++;
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, internalName, overflowMethodName, "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();
        mv =
            cw.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                overflowMethodName,
                "()V",
                null,
                null);
        mv.visitCode();
        estimatedBytes = 0;
      }
      mv.visitLdcInsn(resourceTable.idFor("string", fieldName));
      mv.visitFieldInsn(Opcodes.PUTSTATIC, internalName, fieldName, "I");
      estimatedBytes += 6;
    }
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(1, 0);
    mv.visitEnd();
    cw.visitEnd();
    assertTrue("Expected <clinit> to spill into staticInit0()", methodIndex >= 1);
    return cw.toByteArray();
  }

  private static String expectedLargeOutput(AndroidTestResource testResources) throws Exception {
    TestResourceTable table = testResources.getTestResourceTable();
    return StringUtils.lines(
        Integer.toString(table.idFor("string", "used_clinit")),
        Integer.toString(table.idFor("string", "used_0")));
  }

  public static class LargeTestClass {
    public static void main(String[] args) {
      System.out.println(R.string.used_clinit);
      System.out.println(R.string.used_0);
    }
  }
}
