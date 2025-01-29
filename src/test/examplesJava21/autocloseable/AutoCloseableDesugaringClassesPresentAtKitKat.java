// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package autocloseable;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryTypeRewriter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.AutoCloseableRetargeterHelper;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.util.Set;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

public class AutoCloseableDesugaringClassesPresentAtKitKat extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    InternalOptions options = new InternalOptions();
    Path androidJarK = ToolHelper.getAndroidJar(AndroidApiLevel.K);
    AndroidApp app = AndroidApp.builder().addProgramFile(androidJarK).build();
    DirectMappedDexApplication libHolder =
        new ApplicationReader(app, options, Timing.empty()).read().toDirect();
    AppInfo initialAppInfo =
        AppInfo.createInitialAppInfo(libHolder, GlobalSyntheticsStrategy.forNonSynthesizing());
    AppView<AppInfo> appView =
        AppView.createForD8(initialAppInfo, DesugaredLibraryTypeRewriter.empty(), Timing.empty());

    AutoCloseableRetargeterHelper data =
        new AutoCloseableRetargeterHelper(AndroidApiLevel.B, appView.dexItemFactory());
    Set<DexType> missing = Sets.newIdentityHashSet();
    for (DexType dexType : data.superTargetsToRewrite()) {
      if (appView.definitionFor(dexType) == null) {
        missing.add(dexType);
      }
    }
    assertEquals(1, missing.size());
    // ForkJoinPool is missing at Android Api level 19 but that's ok since it implements
    // ExecutorService.close in a more optimized way. We rely on ExecutorService for the
    // emulated dispatch.
    assertEquals(
        options.dexItemFactory().javaUtilConcurrentForkJoinPoolType, missing.iterator().next());
  }
}
