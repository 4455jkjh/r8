// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestCompileResult;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRenameTest extends KotlinTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRenameTest(TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static final String PKG_PREFIX =
      DescriptorUtils.getBinaryNameFromJavaType(MetadataRenameTest.class.getPackage().getName());
  private static Path supertypeLibJar;

  @BeforeClass
  public static void createLibJar() throws Exception {
    String supertypeLibFolder = PKG_PREFIX + "/supertype_lib";
    supertypeLibJar = getStaticTemp().newFile("supertype_lib.jar").toPath();
    ProcessResult processResult =
        ToolHelper.runKotlinc(
            null,
            null,
            supertypeLibJar,
            null,
            getKotlinFileInTest(supertypeLibFolder, "impl"),
            getKotlinFileInTest(supertypeLibFolder + "/internal", "itf")
        );
    assertEquals(0, processResult.exitCode);
  }

  @Test
  public void b143687784() throws Exception {
    assumeTrue(parameters.getRuntime().isCf());

    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(supertypeLibJar)
            // Keep non-private members except for ones in `internal` definitions.
            .addKeepRules("-keep public class !**.internal.**, * { !private *; }")
            .addKeepAttributes("*Annotation*")
            .compile();
    String pkg = getClass().getPackage().getName();
    final String itfClassName = pkg + ".supertype_lib.internal.Itf";
    final String implClassName = pkg + ".supertype_lib.Impl";
    compileResult.inspect(inspector -> {
      ClassSubject itf = inspector.clazz(itfClassName);
      assertThat(itf, not(isPresent()));

      ClassSubject impl = inspector.clazz(implClassName);
      assertThat(impl, isPresent());
      assertThat(impl, not(isRenamed()));
      // API entry is kept, hence the presence of Metadata.
      DexAnnotation metadata = retrieveMetadata(impl.getDexClass());
      assertNotNull(metadata);
      // TODO(b/143687784): test its metadata doesn't point to shrunken itf.
    });

    Path r8ProcessedLibZip = temp.newFile("r8-lib.zip").toPath();
    compileResult.writeToZip(r8ProcessedLibZip);

    String appFolder = PKG_PREFIX + "/supertype_app";
    KotlinTestCompileResult kotlinTestCompileResult =
        testForKotlin()
            .addClasspathFiles(r8ProcessedLibZip)
            .addSourceFiles(getKotlinFileInTest(appFolder, "main"))
            .compile();
    // TODO(b/143687784): should be able to compile!
    assertNotEquals(0, kotlinTestCompileResult.exitCode());
    assertThat(
        kotlinTestCompileResult.stderr(),
        containsString("unresolved supertypes: " + pkg + ".supertype_lib.internal.Itf"));
  }
}
