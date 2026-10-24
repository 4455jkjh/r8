// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.TestResourceTable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.BooleanUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Assume;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Tests tracing of constant Resources.getIdentifier() lookups under optimized shrinking. */
@RunWith(Parameterized.class)
public class ResourceGetIdentifierTracingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableTracing;

  @Parameters(name = "{0}, enableTracing: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDefaultDexRuntime()
            .withAllApiLevels()
            .withPartialCompilation()
            .build(),
        BooleanUtils.values());
  }

  // Manifest package, aapt2's --rename-resources-package, and a merged library/split package.
  private static final String PKG = "com.android.tools.r8";
  private static final String RES_TABLE_PKG = "thepackage127.foobar";
  private static final String LIB_PKG = "com.example.lib";

  private static final List<String> TRACED_STRINGS =
      ImmutableList.of(
          "bare_name",
          "resource_table_pkg_name",
          "library_pkg_name",
          "library_prefix_name",
          "library_at_prefix_name",
          "type_first_pkg_name",
          "type_slash_name",
          "pkg_type_name",
          "at_type_name",
          "at_pkg_type_name",
          "any_type_name",
          "phi_def_type_name",
          "phi_def_pkg_name",
          "subclass_receiver_name",
          "super_call_name",
          "dotted.entry",
          "dotted.any_type",
          "decimal_id_name");

  private static final List<String> ALWAYS_UNTRACED_STRINGS =
      ImmutableList.of(
          "non_constant_name",
          "phi_name_a",
          "phi_name_b",
          "framework_pkg_name",
          "framework_prefix_name",
          "framework_at_prefix_name",
          "framework_type_first_name",
          "null_type_name",
          "empty_type_name",
          "null_pkg_name",
          "empty_pkg_name",
          "malformed_colon",
          "malformed_slash",
          "plus_type_name",
          "type_mismatch_name");

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    String xmlWithView =
        "<view xmlns:android=\"http://schemas.android.com/apk/res/android\" class=\""
            + XmlReferencedView.class.getTypeName()
            + "\"/>\n";
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addStringValue("dotted.entry", "dotted.entry")
        .addStringValue("dotted.any_type", "dotted.any_type")
        .addXml("xml_with_view.xml", xmlWithView)
        .addRClassInitializeWithDefaultValues(R.string.class, R.drawable.class, R.xml.class)
        .build(temp);
  }

  private byte[] transformMain(TestResourceTable table) throws IOException {
    Map<String, String> placeholders =
        ImmutableMap.of(
            "__DECIMAL_STRING_ID__", Integer.toString(table.idFor("string", "decimal_id_name")));
    return transformer(Main.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(Resources.class), DexItemFactory.androidResourcesDescriptorString)
        .addMethodTransformer(
            new MethodTransformer() {
              @Override
              public void visitLdcInsn(Object value) {
                super.visitLdcInsn(
                    value instanceof String
                        ? placeholders.getOrDefault(value, (String) value)
                        : value);
              }
            })
        .transform();
  }

  private byte[] transformMyResources() throws IOException {
    return transformer(MyResources.class)
        .setSuper(DexItemFactory.androidResourcesDescriptorString)
        .replaceClassDescriptorInMembers(
            descriptor(Resources.class), DexItemFactory.androidResourcesDescriptorString)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(Resources.class), DexItemFactory.androidResourcesDescriptorString)
        .transform();
  }

  private R8TestCompileResultBase<?> compile(
      ThrowableConsumer<R8TestBuilder<?, ?, ?>> configuration) throws Exception {
    AndroidTestResource testResources = getTestResources(temp);
    Consumer<InternalOptions> optionsModification =
        options -> options.enableResourceGetIdentifierTracing = enableTracing;
    return testForR8(parameters)
        .addProgramClasses(XmlReferencedView.class)
        .addProgramClassFileData(
            transformMain(testResources.getTestResourceTable()),
            AndroidResourceTestingUtils.transformResourcesReferences(Unused.class),
            transformMyResources())
        .addRunClasspathFiles(AndroidResourceTestingUtils.resourcesClassAsDex(temp))
        .addAndroidResources(testResources)
        .enableOptimizedShrinking()
        .apply(configuration)
        .apply(
            builder ->
                builder.applyIf(
                    builder.isR8PartialTestBuilder(),
                    b -> b.addR8PartialR8OptionsModification(optionsModification),
                    b -> b.addOptionsModification(optionsModification)))
        .compile();
  }

  @Test
  public void testR8() throws Exception {
    boolean fullR8TreeShaking =
        parameters.getPartialCompilationTestParameters().isNone()
            || parameters.getPartialCompilationTestParameters().isIncludeAll();

    compile(b -> b.addKeepMainRule(Main.class))
        .inspectShrunkenResources(
            inspector -> {
              if (enableTracing) {
                for (String name : TRACED_STRINGS) {
                  inspector.assertContainsResourceWithName("string", name);
                }
                // An unknown or Phi defType matches the name across all types.
                inspector.assertContainsResourceWithName("drawable", "any_type_name");
                inspector.assertContainsResourceWithName("drawable", "phi_def_type_name");
                inspector.assertContainsResourceWithName("xml", "xml_with_view");
                // Inlined constant arguments are traced in full R8, while dead methods and dead
                // branches are eliminated in full R8 and kept conservatively in the unshrunk D8
                // partition of R8Partial.
                if (fullR8TreeShaking) {
                  inspector.assertContainsResourceWithName("string", "inlined_name");
                  inspector.assertDoesNotContainResourceWithName("string", "dead_call_name");
                  inspector.assertDoesNotContainResourceWithName(
                      "string", "dead_after_inlining_name");
                } else if (parameters.getPartialCompilationTestParameters().isExcludeAll()) {
                  inspector.assertDoesNotContainResourceWithName("string", "inlined_name");
                  inspector.assertContainsResourceWithName("string", "dead_call_name");
                  inspector.assertContainsResourceWithName("string", "dead_after_inlining_name");
                }
              } else {
                for (String name : TRACED_STRINGS) {
                  inspector.assertDoesNotContainResourceWithName("string", name);
                }
                inspector.assertDoesNotContainResourceWithName("drawable", "any_type_name");
                inspector.assertDoesNotContainResourceWithName("drawable", "phi_def_type_name");
                inspector.assertDoesNotContainResourceWithName("xml", "xml_with_view");
                inspector.assertDoesNotContainResourceWithName("string", "inlined_name");
                inspector.assertDoesNotContainResourceWithName("string", "dead_call_name");
                inspector.assertDoesNotContainResourceWithName(
                    "string", "dead_after_inlining_name");
              }
              for (String name : ALWAYS_UNTRACED_STRINGS) {
                inspector.assertDoesNotContainResourceWithName("string", name);
              }
              // Null defType does NOT act as a wildcard across other types.
              inspector.assertDoesNotContainResourceWithName("drawable", "null_type_name");
              // The type in the name takes precedence over defType.
              inspector.assertDoesNotContainResourceWithName("drawable", "at_pkg_type_name");
            })
        .inspect(
            codeInspector -> {
              if (enableTracing) {
                assertThat(codeInspector.clazz(XmlReferencedView.class), isPresentAndNotRenamed());
              } else if (fullR8TreeShaking) {
                assertThat(codeInspector.clazz(XmlReferencedView.class), not(isPresent()));
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess();
  }

  @Test
  public void testIdentifierNameString() throws Exception {
    Assume.assumeTrue(parameters.getPartialCompilationTestParameters().isNone());
    compile(
            b ->
                b.addKeepMainRule(Main.class)
                    .addKeepRules(
                        "-identifiernamestring class android.content.res.Resources {"
                            + " int getIdentifier("
                            + "java.lang.String, java.lang.String, java.lang.String);"
                            + " }")
                    // Non-class resource name strings trigger an undetermined identifier warning.
                    .allowDiagnosticWarningMessages())
        .inspectShrunkenResources(
            inspector -> {
              if (enableTracing) {
                inspector.assertContainsResourceWithName("string", "bare_name");
                inspector.assertContainsResourceWithName("string", "super_call_name");
              } else {
                inspector.assertDoesNotContainResourceWithName("string", "bare_name");
                inspector.assertDoesNotContainResourceWithName("string", "super_call_name");
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess();
  }

  @Test
  public void testSinglePassDontShrinkDontObfuscate() throws Exception {
    Assume.assumeTrue(parameters.getPartialCompilationTestParameters().isNone());
    compile(b -> b.addDontShrink().addDontObfuscate())
        .inspectShrunkenResources(
            inspector -> {
              if (enableTracing) {
                inspector.assertContainsResourceWithName("string", "bare_name");
                inspector.assertContainsResourceWithName("string", "library_pkg_name");
              } else {
                inspector.assertDoesNotContainResourceWithName("string", "bare_name");
                inspector.assertDoesNotContainResourceWithName("string", "library_pkg_name");
              }
              inspector.assertDoesNotContainResourceWithName("string", "null_type_name");
              inspector.assertDoesNotContainResourceWithName("string", "framework_pkg_name");
              // Single-pass tree shaking runs before inlining and does not rerun the Enqueuer.
              inspector.assertDoesNotContainResourceWithName("string", "inlined_name");
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess();
  }

  public static class Main {

    public static void main(String[] args) {
      Resources resources = new Resources();
      // Traced.
      System.out.println(resources.getIdentifier("bare_name", "string", PKG));
      System.out.println(
          resources.getIdentifier("resource_table_pkg_name", "string", RES_TABLE_PKG));
      System.out.println(resources.getIdentifier("library_pkg_name", "string", LIB_PKG));
      System.out.println(
          resources.getIdentifier(LIB_PKG + ":string/library_prefix_name", null, PKG));
      System.out.println(
          resources.getIdentifier("@" + LIB_PKG + ":string/library_at_prefix_name", null, PKG));
      System.out.println(
          resources.getIdentifier("string/" + LIB_PKG + ":type_first_pkg_name", null, null));
      System.out.println(resources.getIdentifier("string/type_slash_name", null, PKG));
      System.out.println(resources.getIdentifier(PKG + ":string/pkg_type_name", null, null));
      System.out.println(resources.getIdentifier("@string/at_type_name", null, PKG));
      System.out.println(
          resources.getIdentifier("@" + PKG + ":string/at_pkg_type_name", "drawable", null));
      System.out.println(
          resources.getIdentifier("any_type_name", System.getProperty("type", "string"), PKG));
      // Phi defType and defPackage arguments are treated as non-constant wildcards.
      String phiType = args.length == 43 ? "string" : "xml";
      System.out.println(resources.getIdentifier("phi_def_type_name", phiType, PKG));
      String phiDefPkg = args.length == 44 ? PKG : "android";
      System.out.println(resources.getIdentifier("phi_def_pkg_name", "string", phiDefPkg));
      MyResources myResources = new MyResources();
      System.out.println(myResources.getIdentifier("subclass_receiver_name", "string", PKG));
      System.out.println(myResources.lookupViaSuper());
      System.out.println(resources.getIdentifier("dotted.entry", "string", PKG));
      System.out.println(
          resources.getIdentifier("dotted.any_type", System.getProperty("type", "string"), PKG));
      System.out.println(resources.getIdentifier("__DECIMAL_STRING_ID__", null, null));
      System.out.println(resources.getIdentifier("xml_with_view", "xml", PKG));
      // Resources whose lookup name folds to a constant during inlining are traced in final tree
      // shaking on two-pass builds, though classes referenced only by those resources may already
      // have been pruned.
      System.out.println(resources.getIdentifier(inlinedName(), "string", PKG));
      // Eliminated between initial and final tree shaking after alwaysFalse() is inlined.
      if (alwaysFalse()) {
        System.out.println(resources.getIdentifier("dead_after_inlining_name", "string", PKG));
      }
      // Not traced.
      System.out.println(
          resources.getIdentifier(System.getProperty("name", "non_constant_name"), "string", PKG));
      String phiName = args.length == 42 ? "phi_name_a" : "phi_name_b";
      System.out.println(resources.getIdentifier(phiName, "string", PKG));
      System.out.println(resources.getIdentifier("framework_pkg_name", "string", "android"));
      System.out.println(
          resources.getIdentifier("android:string/framework_prefix_name", null, PKG));
      System.out.println(
          resources.getIdentifier("@android:string/framework_at_prefix_name", null, null));
      System.out.println(
          resources.getIdentifier("string/android:framework_type_first_name", "string", PKG));
      System.out.println(resources.getIdentifier("null_type_name", null, PKG));
      System.out.println(resources.getIdentifier("empty_type_name", "", PKG));
      System.out.println(resources.getIdentifier("string/null_pkg_name", null, null));
      System.out.println(resources.getIdentifier("empty_pkg_name", "string", ""));
      System.out.println(resources.getIdentifier(":string/malformed_colon", null, PKG));
      System.out.println(resources.getIdentifier("/malformed_slash", "string", PKG));
      System.out.println(resources.getIdentifier("@", "string", PKG));
      System.out.println(resources.getIdentifier("@+string/plus_type_name", null, PKG));
      System.out.println(resources.getIdentifier("type_mismatch_name", "drawable", PKG));
    }

    private static String inlinedName() {
      return "inlined_name";
    }

    private static boolean alwaysFalse() {
      return false;
    }
  }

  public static class MyResources extends Resources {
    public int lookupViaSuper() {
      return super.getIdentifier("super_call_name", "string", PKG);
    }
  }

  public static class XmlReferencedView {}

  public static class Unused {

    public static void deadCall() {
      Resources resources = new Resources();
      System.out.println(resources.getIdentifier("dead_call_name", "string", PKG));
    }
  }

  public static class R {

    public static class string {
      public static int bare_name;
      public static int resource_table_pkg_name;
      public static int library_pkg_name;
      public static int library_prefix_name;
      public static int library_at_prefix_name;
      public static int type_first_pkg_name;
      public static int type_slash_name;
      public static int pkg_type_name;
      public static int at_type_name;
      public static int at_pkg_type_name;
      public static int any_type_name;
      public static int phi_name_a;
      public static int phi_name_b;
      public static int phi_def_type_name;
      public static int phi_def_pkg_name;
      public static int subclass_receiver_name;
      public static int super_call_name;
      public static int decimal_id_name;
      public static int inlined_name;
      public static int non_constant_name;
      public static int framework_pkg_name;
      public static int framework_prefix_name;
      public static int framework_at_prefix_name;
      public static int framework_type_first_name;
      public static int null_type_name;
      public static int empty_type_name;
      public static int null_pkg_name;
      public static int empty_pkg_name;
      public static int malformed_colon;
      public static int malformed_slash;
      public static int plus_type_name;
      public static int type_mismatch_name;
      public static int dead_call_name;
      public static int dead_after_inlining_name;
    }

    public static class drawable {
      public static int any_type_name;
      public static int at_pkg_type_name;
      public static int null_type_name;
      public static int phi_def_type_name;
    }

    public static class xml {
      public static int xml_with_view;
    }
  }
}
