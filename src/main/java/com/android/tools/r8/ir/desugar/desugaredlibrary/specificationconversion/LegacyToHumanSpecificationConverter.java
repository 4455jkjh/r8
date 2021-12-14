// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanTopLevelFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.MultiAPILevelHumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.MultiAPILevelHumanDesugaredLibrarySpecificationJsonExporter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyTopLevelFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.MultiAPILevelLegacyDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.MultiAPILevelLegacyDesugaredLibrarySpecificationParser;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class LegacyToHumanSpecificationConverter implements SpecificationConverter {

  @Override
  public void convertAllAPILevels(
      StringResource inputSpecification, Path androidLib, StringConsumer output)
      throws IOException {
    InternalOptions options = new InternalOptions();
    MultiAPILevelLegacyDesugaredLibrarySpecification legacySpec =
        new MultiAPILevelLegacyDesugaredLibrarySpecificationParser(
                options.dexItemFactory(), options.reporter)
            .parseMultiLevelConfiguration(inputSpecification);
    MultiAPILevelHumanDesugaredLibrarySpecification humanSpec =
        convertAllAPILevels(legacySpec, androidLib, options);
    MultiAPILevelHumanDesugaredLibrarySpecificationJsonExporter.export(humanSpec, output);
  }

  public MultiAPILevelHumanDesugaredLibrarySpecification convertAllAPILevels(
      MultiAPILevelLegacyDesugaredLibrarySpecification legacySpec,
      Path androidLib,
      InternalOptions options)
      throws IOException {
    DexApplication app = readApp(androidLib, options);
    HumanTopLevelFlags humanTopLevelFlags = convertTopLevelFlags(legacySpec.getTopLevelFlags());
    Int2ObjectArrayMap<HumanRewritingFlags> commonFlags =
        convertRewritingFlagMap(legacySpec.getCommonFlags(), app);
    Int2ObjectArrayMap<HumanRewritingFlags> programFlags =
        convertRewritingFlagMap(legacySpec.getProgramFlags(), app);
    Int2ObjectArrayMap<HumanRewritingFlags> libraryFlags =
        convertRewritingFlagMap(legacySpec.getLibraryFlags(), app);

    legacyLibraryFlagHacks(libraryFlags, app);

    return new MultiAPILevelHumanDesugaredLibrarySpecification(
        humanTopLevelFlags, commonFlags, libraryFlags, programFlags);
  }

  public HumanDesugaredLibrarySpecification convert(
      LegacyDesugaredLibrarySpecification legacySpec, Path androidLib, InternalOptions options)
      throws IOException {
    DexApplication app = readApp(androidLib, options);
    HumanTopLevelFlags humanTopLevelFlags = convertTopLevelFlags(legacySpec.getTopLevelFlags());
    HumanRewritingFlags humanRewritingFlags =
        convertRewritingFlags(legacySpec.getRewritingFlags(), app);
    return new HumanDesugaredLibrarySpecification(
        humanTopLevelFlags,
        humanRewritingFlags,
        legacySpec.isLibraryCompilation(),
        app.dexItemFactory());
  }

  private void legacyLibraryFlagHacks(
      Int2ObjectArrayMap<HumanRewritingFlags> libraryFlags, DexApplication app) {
    HumanRewritingFlags humanRewritingFlags = libraryFlags.get(AndroidApiLevel.N_MR1.getLevel());
    HumanRewritingFlags.Builder builder =
        humanRewritingFlags.newBuilder(
            app.dexItemFactory(), app.options.reporter, Origin.unknown());
    DexItemFactory itemFactory = app.dexItemFactory();

    // TODO(b/177977763): This is only a workaround rewriting invokes of j.u.Arrays.deepEquals0
    // to j.u.DesugarArrays.deepEquals0.
    DexString name = itemFactory.createString("deepEquals0");
    DexProto proto =
        itemFactory.createProto(
            itemFactory.booleanType, itemFactory.objectType, itemFactory.objectType);
    DexMethod source =
        itemFactory.createMethod(itemFactory.createType(itemFactory.arraysDescriptor), proto, name);
    DexType target = itemFactory.createType("Ljava/util/DesugarArrays;");
    builder.putRetargetCoreLibMember(source, target);

    // TODO(b/181629049): This is only a workaround rewriting invokes of
    //  j.u.TimeZone.getTimeZone taking a java.time.ZoneId.
    name = itemFactory.createString("getTimeZone");
    proto =
        itemFactory.createProto(
            itemFactory.createType("Ljava/util/TimeZone;"),
            itemFactory.createType("Ljava/time/ZoneId;"));
    source = itemFactory.createMethod(itemFactory.createType("Ljava/util/TimeZone;"), proto, name);
    target = itemFactory.createType("Ljava/util/DesugarTimeZone;");
    builder.putRetargetCoreLibMember(source, target);

    libraryFlags.put(25, builder.build());
  }

  private DirectMappedDexApplication readApp(Path androidLib, InternalOptions options)
      throws IOException {
    AndroidApp androidApp = AndroidApp.builder().addLibraryFile(androidLib).build();
    ApplicationReader applicationReader =
        new ApplicationReader(androidApp, options, Timing.empty());
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    return applicationReader.read(executorService).toDirect();
  }

  private Int2ObjectArrayMap<HumanRewritingFlags> convertRewritingFlagMap(
      Int2ObjectMap<LegacyRewritingFlags> libFlags, DexApplication app) {
    Int2ObjectArrayMap<HumanRewritingFlags> map = new Int2ObjectArrayMap<>();
    libFlags.forEach((key, flags) -> map.put((int) key, convertRewritingFlags(flags, app)));
    return map;
  }

  private HumanRewritingFlags convertRewritingFlags(
      LegacyRewritingFlags flags, DexApplication app) {
    HumanRewritingFlags.Builder builder =
        HumanRewritingFlags.builder(app.dexItemFactory(), app.options.reporter, Origin.unknown());

    flags.getRewritePrefix().forEach(builder::putRewritePrefix);
    flags.getEmulateLibraryInterface().forEach(builder::putEmulateLibraryInterface);
    flags.getBackportCoreLibraryMember().forEach(builder::putBackportCoreLibraryMember);
    flags.getCustomConversions().forEach(builder::putCustomConversion);
    flags.getDontRetargetLibMember().forEach(builder::addDontRetargetLibMember);
    flags.getWrapperConversions().forEach(builder::addWrapperConversion);

    flags
        .getRetargetCoreLibMember()
        .forEach((name, typeMap) -> convertRetargetCoreLibMember(builder, app, name, typeMap));
    flags
        .getDontRewriteInvocation()
        .forEach(pair -> convertDontRewriteInvocation(builder, app, pair));

    return builder.build();
  }

  private void convertDontRewriteInvocation(
      HumanRewritingFlags.Builder builder, DexApplication app, Pair<DexType, DexString> pair) {
    DexClass dexClass = app.definitionFor(pair.getFirst());
    assert dexClass != null;
    List<DexClassAndMethod> methodsWithName = findMethodsWithName(pair.getSecond(), dexClass);
    for (DexClassAndMethod dexClassAndMethod : methodsWithName) {
      builder.addDontRewriteInvocation(dexClassAndMethod.getReference());
    }
  }

  private void convertRetargetCoreLibMember(
      HumanRewritingFlags.Builder builder,
      DexApplication app,
      DexString name,
      Map<DexType, DexType> typeMap) {
    typeMap.forEach(
        (type, rewrittenType) -> {
          DexClass dexClass = app.definitionFor(type);
          assert dexClass != null;
          List<DexClassAndMethod> methodsWithName = findMethodsWithName(name, dexClass);
          for (DexClassAndMethod dexClassAndMethod : methodsWithName) {
            builder.putRetargetCoreLibMember(dexClassAndMethod.getReference(), rewrittenType);
          }
        });
  }

  private List<DexClassAndMethod> findMethodsWithName(DexString methodName, DexClass clazz) {
    List<DexClassAndMethod> found = new ArrayList<>();
    clazz.forEachClassMethodMatching(definition -> definition.getName() == methodName, found::add);
    assert !found.isEmpty()
        : "Should have found a method (library specifications) for "
            + clazz.toSourceString()
            + "."
            + methodName
            + ". Maybe the library used for the compilation should be newer.";
    return found;
  }

  private HumanTopLevelFlags convertTopLevelFlags(LegacyTopLevelFlags topLevelFlags) {
    return HumanTopLevelFlags.builder()
        .setDesugaredLibraryIdentifier(topLevelFlags.getIdentifier())
        .setExtraKeepRules(topLevelFlags.getExtraKeepRules())
        .setJsonSource(topLevelFlags.getJsonSource())
        .setRequiredCompilationAPILevel(topLevelFlags.getRequiredCompilationAPILevel())
        .setSupportAllCallbacksFromLibrary(topLevelFlags.supportAllCallbacksFromLibrary())
        .setSynthesizedLibraryClassesPackagePrefix(
            topLevelFlags.getSynthesizedLibraryClassesPackagePrefix())
        .build();
  }
}
