// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryAmender;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanTopLevelFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.CustomConversionDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineTopLevelFlags;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class HumanToMachineSpecificationConverter {

  private AppView<?> appView;
  private final Set<DexType> missingCustomConversions = Sets.newIdentityHashSet();
  private final Timing timing;

  public HumanToMachineSpecificationConverter(Timing timing) {
    this.timing = timing;
  }

  public MachineDesugaredLibrarySpecification convert(
      HumanDesugaredLibrarySpecification humanSpec,
      List<ProgramResourceProvider> desugaredJDKLib,
      List<ClassFileResourceProvider> library,
      InternalOptions options)
      throws IOException {
    timing.begin("Human to machine convert");
    assert !humanSpec.isLibraryCompilation() || desugaredJDKLib != null;
    AndroidApp.Builder builder = AndroidApp.builder();
    for (ClassFileResourceProvider classFileResourceProvider : library) {
      builder.addLibraryResourceProvider(classFileResourceProvider);
    }
    if (humanSpec.isLibraryCompilation()) {
      for (ProgramResourceProvider programResourceProvider : desugaredJDKLib) {
        builder.addProgramResourceProvider(programResourceProvider);
      }
    }
    MachineDesugaredLibrarySpecification machineSpec =
        internalConvert(humanSpec, builder.build(), options);
    timing.end();
    return machineSpec;
  }

  public MachineDesugaredLibrarySpecification convert(
      HumanDesugaredLibrarySpecification humanSpec,
      Path desugaredJDKLib,
      Path androidLib,
      InternalOptions options)
      throws IOException {
    timing.begin("Human to machine convert");
    assert !humanSpec.isLibraryCompilation() || desugaredJDKLib != null;
    AndroidApp.Builder builder = AndroidApp.builder();
    if (humanSpec.isLibraryCompilation()) {
      builder.addProgramFile(desugaredJDKLib);
    }
    AndroidApp inputApp = builder.addLibraryFile(androidLib).build();
    MachineDesugaredLibrarySpecification machineSpec =
        internalConvert(humanSpec, inputApp, options);
    timing.end();
    return machineSpec;
  }

  private MachineDesugaredLibrarySpecification internalConvert(
      HumanDesugaredLibrarySpecification humanSpec, AndroidApp inputApp, InternalOptions options)
      throws IOException {
    timing.begin("internal convert");
    DexApplication app = readApp(inputApp, options);
    timing.begin("appView creation");
    appView = AppView.createForD8(AppInfo.createInitialAppInfo(app));
    timing.end();
    LibraryValidator.validate(
        app,
        humanSpec.isLibraryCompilation(),
        humanSpec.getTopLevelFlags().getRequiredCompilationAPILevel());
    MachineRewritingFlags machineRewritingFlags =
        convertRewritingFlags(
            humanSpec.getSynthesizedLibraryClassesPackagePrefix(), humanSpec.getRewritingFlags());
    MachineTopLevelFlags topLevelFlags = convertTopLevelFlags(humanSpec.getTopLevelFlags());
    timing.end();
    return new MachineDesugaredLibrarySpecification(
        humanSpec.isLibraryCompilation(), topLevelFlags, machineRewritingFlags);
  }

  private MachineTopLevelFlags convertTopLevelFlags(HumanTopLevelFlags topLevelFlags) {
    return new MachineTopLevelFlags(
        topLevelFlags.getRequiredCompilationAPILevel(),
        topLevelFlags.getSynthesizedLibraryClassesPackagePrefix(),
        topLevelFlags.getIdentifier(),
        topLevelFlags.getJsonSource(),
        topLevelFlags.supportAllCallbacksFromLibrary(),
        topLevelFlags.getExtraKeepRules());
  }

  private MachineRewritingFlags convertRewritingFlags(
      String synthesizedPrefix, HumanRewritingFlags rewritingFlags) {
    timing.begin("convert rewriting flags");
    AppInfoWithClassHierarchy appInfo = appView.appInfoForDesugaring();
    MachineRewritingFlags.Builder builder = MachineRewritingFlags.builder();
    DesugaredLibraryAmender.run(appView, rewritingFlags.getAmendLibraryMethod());
    rewritingFlags.getAmendLibraryMethod().forEach(builder::amendLibraryMethod);
    new HumanToMachineRetargetConverter(appInfo)
        .convertRetargetFlags(rewritingFlags, builder, this::warnMissingReferences);
    new HumanToMachineEmulatedInterfaceConverter(appInfo)
        .convertEmulatedInterfaces(rewritingFlags, appInfo, builder, this::warnMissingReferences);
    new HumanToMachinePrefixConverter(appInfo, builder, synthesizedPrefix, rewritingFlags)
        .convertPrefixFlags(rewritingFlags, this::warnMissingDexString);
    new HumanToMachineWrapperConverter(appInfo)
        .convertWrappers(rewritingFlags, builder, this::warnMissingReferences);
    rewritingFlags
        .getCustomConversions()
        .forEach(
            (type, conversionType) ->
                convertCustomConversion(appInfo, builder, type, conversionType));
    warnMissingReferences(
        "Cannot register custom conversion due to missing type: ", missingCustomConversions);
    rewritingFlags.getDontRetarget().forEach(builder::addDontRetarget);
    rewritingFlags.getLegacyBackport().forEach(builder::putLegacyBackport);
    MachineRewritingFlags machineFlags = builder.build();
    timing.end();
    return machineFlags;
  }

  private void convertCustomConversion(
      AppInfoWithClassHierarchy appInfo,
      MachineRewritingFlags.Builder builder,
      DexType type,
      DexType conversionType) {
    DexType rewrittenType = builder.getRewrittenType(type);
    if (rewrittenType == null) {
      missingCustomConversions.add(type);
      return;
    }
    DexProto fromProto = appInfo.dexItemFactory().createProto(rewrittenType, type);
    DexMethod fromMethod =
        appInfo
            .dexItemFactory()
            .createMethod(conversionType, fromProto, appInfo.dexItemFactory().convertMethodName);
    DexProto toProto = appInfo.dexItemFactory().createProto(type, rewrittenType);
    DexMethod toMethod =
        appInfo
            .dexItemFactory()
            .createMethod(conversionType, toProto, appInfo.dexItemFactory().convertMethodName);
    builder.putCustomConversion(type, new CustomConversionDescriptor(toMethod, fromMethod));
  }

  private DexApplication readApp(AndroidApp inputApp, InternalOptions options) throws IOException {
    timing.begin("Read app");
    ApplicationReader applicationReader = new ApplicationReader(inputApp, options, timing);
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    assert !options.ignoreJavaLibraryOverride;
    options.ignoreJavaLibraryOverride = true;
    DexApplication app = applicationReader.read(executorService);
    options.ignoreJavaLibraryOverride = false;
    timing.end();
    return app;
  }

  void warnMissingReferences(String message, Set<? extends DexReference> missingReferences) {
    List<DexReference> memberList = new ArrayList<>(missingReferences);
    memberList.sort(DexReference::compareTo);
    warn(message, memberList);
  }

  void warnMissingDexString(String message, Set<DexString> missingDexString) {
    List<DexString> memberList = new ArrayList<>(missingDexString);
    memberList.sort(DexString::compareTo);
    warn(message, memberList);
  }

  private void warn(String message, List<?> memberList) {
    if (memberList.isEmpty()) {
      return;
    }
    appView.options().reporter.warning("Specification conversion: " + message + memberList);
  }
}
