// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;


import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import com.android.tools.r8.utils.internal.exceptions.Unimplemented;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import com.android.tools.r8.utils.internal.structural.Ordered;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

// Base class for the runtime structure in the test parameters.
public abstract class TestRuntime {

  // Enum describing the possible/supported CF runtimes.
  public enum CfVm implements Ordered<CfVm> {
    JDK8("jdk8", 52),
    JDK9("jdk9", 53),
    JDK10("jdk10", 54),
    // From JDK-11 only include LTS and latest non-LTS.
    JDK11("jdk11", 55),
    JDK17("jdk17", 61),
    JDK21("jdk21", 65),
    JDK25("jdk25", 69),
    JDK27("jdk27", 71);

    /** This should generally be the latest checked in CF runtime we fully support. */
    private static final CfVm DEFAULT = JDK11;

    private final String name;
    private final int classfileVersion;

    CfVm(String name, int classfileVersion) {
      this.name = name;
      this.classfileVersion = classfileVersion;
    }

    public int getClassfileVersion() {
      return classfileVersion;
    }

    public static CfVm first() {
      return JDK8;
    }

    public static CfVm last() {
      return JDK17;
    }

    public boolean lessThan(CfVm other) {
      return isLessThan(other);
    }

    public boolean lessThanOrEqual(CfVm other) {
      return isLessThanOrEqualTo(other);
    }

    @Override
    public String toString() {
      return name;
    }

    public static CfVm getMinimumSystemVersion() {
      return JDK11;
    }

    // Records was experimental from JDK-15 (requiring turning on experimental feaures), and GA
    // in JDK-17.
    public boolean hasRecordsSupport() {
      return isGreaterThanOrEqualTo(JDK17);
    }

    public boolean hasSealedClassesSupport() {
      return isGreaterThanOrEqualTo(JDK17);
    }
  }

  private static final Map<CfVm, Supplier<Path>> jdkPaths =
      ImmutableMap.of(
          CfVm.JDK8, TestDeps::getJdk8Dir,
          CfVm.JDK9, TestDeps::getJdk9Dir,
          CfVm.JDK11, TestDeps::getJdk11Dir,
          CfVm.JDK17, TestDeps::getJdk17Dir,
          CfVm.JDK21, TestDeps::getJdk21Dir,
          CfVm.JDK25, TestDeps::getJdk25Dir,
          CfVm.JDK27, TestDeps::getJdk27Dir);

  public static CfRuntime getCheckedInJdk(CfVm vm) {
    return new CfRuntime(vm, () -> getCheckedInJdkHome(vm));
  }

  public static CfRuntime getCheckedInJdk8() {
    return getCheckedInJdk(CfVm.JDK8);
  }

  private static Path getCheckedInJdkHome(CfVm vm) {
    Supplier<Path> path = jdkPaths.get(vm);
    assert path != null : "No JDK path defined for " + vm;
    if (ToolHelper.isMac() && vm.isGreaterThan(CfVm.JDK9)) {
      return path.get().resolve("Contents/Home");
    }
    return path.get();
  }

  public static CfRuntime getCheckedInJdk9() {
    return getCheckedInJdk(CfVm.JDK9);
  }

  public static CfRuntime getCheckedInJdk11() {
    return getCheckedInJdk(CfVm.JDK11);
  }

  public static CfRuntime getCheckedInJdk17() {
    return getCheckedInJdk(CfVm.JDK17);
  }

  public static CfRuntime getCheckedInJdk21() {
    return getCheckedInJdk(CfVm.JDK21);
  }

  public static CfRuntime getCheckedInJdk25() {
    return getCheckedInJdk(CfVm.JDK25);
  }

  public static CfRuntime getCheckedInJdk27() {
    return getCheckedInJdk(CfVm.JDK27);
  }

  public static List<CfRuntime> getCheckedInCfRuntimes() {
    return ImmutableList.of(
        getCheckedInJdk8(),
        getCheckedInJdk11(),
        getCheckedInJdk17(),
        getCheckedInJdk21(),
        getCheckedInJdk25());
  }

  private static List<DexRuntime> getCheckedInDexRuntimes() {
    if (ToolHelper.isLinux()) {
      return ListUtils.map(Arrays.asList(DexVm.Version.values()), DexRuntime::new);
    }
    assert ToolHelper.isMac() || ToolHelper.isWindows();
    return ImmutableList.of();
  }

  // For compatibility with old tests not specifying a Java runtime
  @Deprecated
  public static TestRuntime getDefaultJavaRuntime() {
    return getCheckedInJdk9();
  }

  public static CfRuntime getDefaultCfRuntime() {
    return TestRuntime.getCheckedInJdk(CfVm.DEFAULT);
  }

  public static DexRuntime getDefaultDexRuntime() {
    return new DexRuntime(DexVm.Version.NEW_DEFAULT);
  }

  public static List<TestRuntime> getCheckedInRuntimes() {
    return ImmutableList.<TestRuntime>builder()
        .addAll(getCheckedInCfRuntimes())
        .addAll(getCheckedInDexRuntimes())
        .build();
  }

  public static CfRuntime getSystemRuntime() {
    String version = System.getProperty("java.version");
    String home = System.getProperty("java.home");
    if (version == null || version.isEmpty() || home == null || home.isEmpty()) {
      throw new Unimplemented("Unable to create a system runtime");
    }
    if (version.startsWith("1.8.")) {
      return new CfRuntime(CfVm.JDK8, Paths.get(home));
    }
    if (version.startsWith("9.")) {
      return new CfRuntime(CfVm.JDK9, Paths.get(home));
    }
    if (version.equals("11") || version.startsWith("11.")) {
      return new CfRuntime(CfVm.JDK11, Paths.get(home));
    }
    if (version.equals("17") || version.startsWith("17.")) {
      return new CfRuntime(CfVm.JDK17, Paths.get(home));
    }
    if (version.equals("21") || version.startsWith("21.")) {
      return new CfRuntime(CfVm.JDK21, Paths.get(home));
    }
    if (version.equals("25") || version.startsWith("25.")) {
      return new CfRuntime(CfVm.JDK25, Paths.get(home));
    }
    throw new Unimplemented("No support for JDK version: " + version);
  }

  public static class NoneRuntime extends TestRuntime {

    private static final String NAME = "none";
    private static final NoneRuntime INSTANCE = new NoneRuntime();

    private NoneRuntime() {}

    public static NoneRuntime getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isNone() {
      return true;
    }

    @Override
    public String name() {
      return NAME;
    }

    @Override
    public AndroidApiLevel maxSupportedApiLevel() {
      // The "none" runtime trivally supports all api levels as nothing is run.
      return AndroidApiLevel.LATEST;
    }

    @Override
    public String toString() {
      return NAME;
    }

    @Override
    public boolean equals(Object other) {
      return this == other;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  // Wrapper for the DEX runtimes.
  public static class DexRuntime extends TestRuntime {

    private final DexVm vm;

    public DexRuntime(DexVm.Version version) {
      this(DexVm.fromVersion(version));
    }

    public DexRuntime(DexVm vm) {
      assert vm != null;
      this.vm = vm;
    }

    @Override
    public String name() {
      return "dex-" + vm.getVersion().toString();
    }

    @Override
    public boolean isDex() {
      return true;
    }

    @Override
    public DexRuntime asDex() {
      return this;
    }

    @Override
    public List<DexRuntime> asDexRuntimes() {
      return ImmutableList.of(this);
    }

    public DexVm getVm() {
      return vm;
    }

    public DexVm.Version getVersion() {
      return vm.getVersion();
    }

    @Override
    public AndroidApiLevel maxSupportedApiLevel() {
      return getMinApiLevel();
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof DexRuntime)) {
        return false;
      }
      DexRuntime dexRuntime = (DexRuntime) other;
      return vm == dexRuntime.vm;
    }

    @Override
    public int hashCode() {
      return vm.hashCode();
    }

    @Override
    public String toString() {
      return "dex-" + vm.getVersion().toString();
    }

    public AndroidApiLevel getMinApiLevel() {
      return ToolHelper.getMinApiLevelForDexVm(vm);
    }

    public boolean hasRecordsSupport() {
      return getVersion().hasRecordsSupport();
    }
  }

  // Wrapper for multiple DEX runtimes sharing the same compilation parameters.
  public static class CollapsedDexRuntimes extends TestRuntime {

    private final List<DexRuntime> runtimes;

    public CollapsedDexRuntimes(List<DexRuntime> runtimes) {
      assert runtimes.size() > 1;
      this.runtimes = ImmutableList.copyOf(runtimes);
    }

    @Override
    public String name() {
      return "dex-[" + StringUtils.join(",", runtimes, r -> r.getVersion().toString()) + "]";
    }

    @Override
    public boolean isDex() {
      return true;
    }

    @Override
    public DexRuntime asDex() {
      throw new Unreachable("Cannot get single DexRuntime from CollapsedDexRuntimes: " + this);
    }

    @Override
    public List<DexRuntime> asDexRuntimes() {
      return runtimes;
    }

    @Override
    public AndroidApiLevel maxSupportedApiLevel() {
      AndroidApiLevel max = AndroidApiLevel.LATEST;
      for (DexRuntime runtime : runtimes) {
        AndroidApiLevel level = runtime.maxSupportedApiLevel();
        if (level.isLessThan(max)) {
          max = level;
        }
      }
      return max;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof CollapsedDexRuntimes)) {
        return false;
      }
      CollapsedDexRuntimes collapsedDexRuntimes = (CollapsedDexRuntimes) other;
      return runtimes.equals(collapsedDexRuntimes.runtimes);
    }

    @Override
    public int hashCode() {
      return runtimes.hashCode();
    }

    @Override
    public String toString() {
      return name();
    }
  }

  // Wrapper for the CF runtimes.
  public static class CfRuntime extends TestRuntime {
    private final CfVm vm;
    private final Supplier<Path> home;

    public CfRuntime(CfVm vm, Path home) {
      this(vm, home::toAbsolutePath);
    }

    public CfRuntime(CfVm vm, Supplier<Path> home) {
      assert vm != null;
      this.vm = vm;
      this.home = home;
    }

    @Override
    public String name() {
      return StringUtils.toLowerCase(vm.name());
    }

    public Path getJavaHome() {
      return home.get().toAbsolutePath();
    }

    public Path getJavaExecutable() {
      return getJavaHome().resolve("bin").resolve("java");
    }

    @Override
    public boolean isCf() {
      return true;
    }

    @Override
    public CfRuntime asCf() {
      return this;
    }

    public CfVm getVm() {
      return vm;
    }

    @Override
    public AndroidApiLevel maxSupportedApiLevel() {
      // TODO: define the mapping from android API levels back to JDKs.
      return AndroidApiLevel.LATEST;
    }

    @Override
    public String toString() {
      return vm.toString();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof CfRuntime)) {
        return false;
      }
      CfRuntime cfRuntime = (CfRuntime) obj;
      return vm == cfRuntime.vm;
    }

    @Override
    public int hashCode() {
      return vm.hashCode();
    }

    public boolean isOlderThan(CfVm version) {
      return vm.lessThan(version);
    }

    public boolean isNewerThan(CfVm version) {
      return !vm.lessThanOrEqual(version);
    }

    public boolean isNewerThanOrEqual(CfVm version) {
      return vm == version || !vm.lessThanOrEqual(version);
    }

    public boolean hasRecordsSupport() {
      return getVm().hasRecordsSupport();
    }

    public boolean hasSealedClassesSupport() {
      return getVm().hasSealedClassesSupport();
    }
  }

  public <T> T match(Function<CfRuntime, T> onCf, BiFunction<DexRuntime, DexVm.Version, T> onDex) {
    if (isCf()) {
      return onCf.apply(asCf());
    }
    if (isDex()) {
      return onDex.apply(asDex(), asDex().getVersion());
    }
    throw new Unreachable();
  }

  public void match(Consumer<CfRuntime> onCf, BiConsumer<DexRuntime, DexVm.Version> onDex) {
    if (isCf()) {
      onCf.accept(asCf());
    } else if (isDex()) {
      onDex.accept(asDex(), asDex().getVersion());
    } else {
      throw new Unreachable();
    }
  }

  public boolean isDex() {
    return false;
  }

  public boolean isCf() {
    return false;
  }

  public boolean isNone() {
    return false;
  }

  public DexRuntime asDex() {
    return null;
  }

  public List<DexRuntime> asDexRuntimes() {
    throw new Unreachable();
  }

  public CfRuntime asCf() {
    return null;
  }

  public Backend getBackend() {
    if (isDex()) {
      return Backend.DEX;
    }
    if (isCf()) {
      return Backend.CF;
    }
    throw new Unreachable("Unexpected runtime without backend: " + this);
  }

  public abstract AndroidApiLevel maxSupportedApiLevel();

  @Override
  public abstract boolean equals(Object other);

  @Override
  public abstract int hashCode();

  public abstract String name();
}
