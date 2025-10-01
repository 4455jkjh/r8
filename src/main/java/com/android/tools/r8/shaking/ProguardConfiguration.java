// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.dontwarn.DontWarnConfiguration;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.naming.DictionaryReader;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.InternalOptions.PackageObfuscationMode;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class ProguardConfiguration {

  public static class Builder implements ProguardConfigurationParserConsumer {

    private final List<String> parsedConfiguration = new ArrayList<>();
    private final List<FilteredClassPath> injars = new ArrayList<>();

    private final List<FilteredClassPath> libraryJars = new ArrayList<>();

    private final Reporter reporter;
    private boolean allowAccessModification;
    private boolean ignoreWarnings;
    private boolean optimizing = true;
    private boolean obfuscating = true;
    private boolean shrinking = true;
    private boolean printConfiguration;
    private Path printConfigurationFile;
    private boolean printUsage;
    private Path printUsageFile;
    private boolean printMapping;
    private Path printMappingFile;
    private Path applyMappingFile;
    private String renameSourceFileAttribute;
    private final List<String> keepAttributePatterns = new ArrayList<>();
    private final ProguardClassFilter.Builder keepPackageNamesPatterns =
        ProguardClassFilter.builder();
    private final ProguardClassFilter.Builder dontWarnPatterns = ProguardClassFilter.builder();
    private final ProguardClassFilter.Builder dontNotePatterns = ProguardClassFilter.builder();
    protected final Set<ProguardConfigurationRule> rules = Sets.newLinkedHashSet();
    private final DexItemFactory dexItemFactory;
    private boolean printSeeds;
    private Path seedFile;
    private Path obfuscationDictionary;
    private Path classObfuscationDictionary;
    private Path packageObfuscationDictionary;
    private boolean keepParameterNames;
    private Origin keepParameterNamesOptionOrigin;
    private Position keepParameterNamesOptionPosition;
    private final ProguardClassFilter.Builder adaptClassStrings = ProguardClassFilter.builder();
    private final ProguardPathFilter.Builder adaptResourceFilenames =
        ProguardPathFilter.builder()
            .addPattern(ProguardPathList.builder().addFileName("META-INF/services/*").build());
    private final ProguardPathFilter.Builder adaptResourceFileContents =
        ProguardPathFilter.builder()
            .addPattern(ProguardPathList.builder().addFileName("META-INF/services/*").build());
    private final ProguardPathFilter.Builder keepDirectories =
        ProguardPathFilter.builder().disable();
    private boolean forceProguardCompatibility = false;
    private boolean protoShrinking = false;
    private int maxRemovedAndroidLogLevel = MaximumRemovedAndroidLogLevelRule.NOT_SET;
    PackageObfuscationMode packageObfuscationMode = PackageObfuscationMode.NONE;
    String packagePrefix = "";

    private Builder(DexItemFactory dexItemFactory, Reporter reporter) {
      this.dexItemFactory = dexItemFactory;
      this.reporter = reporter;
    }

    public List<FilteredClassPath> getInjars() {
      return injars;
    }

    @Override
    public void addParsedConfiguration(String source) {
      parsedConfiguration.add(source);
    }

    @Override
    public void addInjars(List<FilteredClassPath> injars, Origin origin, Position position) {
      this.injars.addAll(injars);
    }

    @Override
    public void addLibraryJars(
        List<FilteredClassPath> libraryJars, Origin origin, Position position) {
      this.libraryJars.addAll(libraryJars);
    }

    @Override
    public void enableAllowAccessModification(Origin origin, Position position) {
      this.allowAccessModification = true;
    }

    @Override
    public void setIgnoreWarnings(boolean ignoreWarnings) {
      this.ignoreWarnings = ignoreWarnings;
    }

    @Override
    public void disableOptimization(Origin origin, Position position) {
      this.optimizing = false;
    }

    @Override
    public void disableObfuscation(Origin origin, Position position) {
      this.obfuscating = false;
    }

    @Override
    public void disableShrinking(Origin origin, Position position) {
      this.shrinking = false;
    }

    public Builder disableOptimization() {
      this.optimizing = false;
      return this;
    }

    public Builder disableObfuscation() {
      this.obfuscating = false;
      return this;
    }

    boolean isAccessModificationEnabled() {
      return allowAccessModification;
    }

    boolean isObfuscating() {
      return obfuscating;
    }

    public boolean isOptimizing() {
      return optimizing;
    }

    public boolean isShrinking() {
      return shrinking;
    }

    public Builder disableShrinking() {
      shrinking = false;
      return this;
    }

    @Override
    public void enablePrintConfiguration(Origin origin, Position position) {
      this.printConfiguration = true;
    }

    @Override
    public void setPrintConfigurationFile(Path file) {
      assert printConfiguration;
      this.printConfigurationFile = file;
    }

    @Override
    public void setPrintUsage(boolean printUsage) {
      this.printUsage = printUsage;
    }

    @Override
    public void setPrintUsageFile(Path printUsageFile) {
      this.printUsageFile = printUsageFile;
    }

    @Override
    public void enablePrintMapping(Origin origin, Position position) {
      this.printMapping = true;
    }

    @Override
    public void setPrintMappingFile(Path file) {
      assert printMapping;
      this.printMappingFile = file;
    }

    @Override
    public void setApplyMappingFile(Path file, Origin origin, Position position) {
      this.applyMappingFile = file;
    }

    public boolean hasApplyMappingFile() {
      return applyMappingFile != null;
    }

    @Override
    public void setRenameSourceFileAttribute(
        String renameSourceFileAttribute, Origin origin, Position position) {
      this.renameSourceFileAttribute = renameSourceFileAttribute;
    }

    @Override
    public void addKeepAttributePatterns(
        List<String> keepAttributePatterns, Origin origin, Position position) {
      this.keepAttributePatterns.addAll(keepAttributePatterns);
    }

    public Builder addKeepAttributePatterns(List<String> keepAttributePatterns) {
      this.keepAttributePatterns.addAll(keepAttributePatterns);
      return this;
    }

    @Override
    public void addRule(ProguardConfigurationRule rule) {
      this.rules.add(rule);
    }

    @Override
    public void addKeepPackageNamesPattern(ProguardClassNameList pattern) {
      keepPackageNamesPatterns.addPattern(pattern);
    }

    @Override
    public void joinMaxRemovedAndroidLogLevel(int maxRemovedAndroidLogLevel) {
      assert maxRemovedAndroidLogLevel >= MaximumRemovedAndroidLogLevelRule.NONE;
      if (this.maxRemovedAndroidLogLevel == MaximumRemovedAndroidLogLevelRule.NOT_SET) {
        this.maxRemovedAndroidLogLevel = maxRemovedAndroidLogLevel;
      } else {
        // If there are multiple -maximumremovedandroidloglevel rules we only allow removing logging
        // calls that are removable according to all rules.
        this.maxRemovedAndroidLogLevel =
            Math.min(this.maxRemovedAndroidLogLevel, maxRemovedAndroidLogLevel);
      }
    }

    public int getMaxRemovedAndroidLogLevel() {
      return maxRemovedAndroidLogLevel;
    }

    @Override
    public PackageObfuscationMode getPackageObfuscationMode() {
      return packageObfuscationMode;
    }

    @Override
    public void setPackagePrefix(String packagePrefix) {
      this.packagePrefix = packagePrefix;
    }

    @Override
    public void setFlattenPackagePrefix(String packagePrefix) {
      this.packagePrefix = packagePrefix;
    }

    @Override
    public void enableFlattenPackageHierarchy(Origin origin, Position position) {
      packageObfuscationMode = PackageObfuscationMode.FLATTEN;
    }

    @Override
    public void enableRepackageClasses(Origin origin, Position position) {
      packageObfuscationMode = PackageObfuscationMode.REPACKAGE;
    }

    @Override
    public void addDontWarnPattern(ProguardClassNameList pattern) {
      dontWarnPatterns.addPattern(pattern);
    }

    @Override
    public void addDontNotePattern(ProguardClassNameList pattern) {
      dontNotePatterns.addPattern(pattern);
    }

    @Override
    public void setSeedFile(Path seedFile) {
      this.seedFile = seedFile;
    }

    @Override
    public void setPrintSeeds(boolean printSeeds, Origin origin, Position position) {
      this.printSeeds = printSeeds;
    }

    @Override
    public void setObfuscationDictionary(
        Path obfuscationDictionary, Origin origin, Position position) {
      this.obfuscationDictionary = obfuscationDictionary;
    }

    @Override
    public void setClassObfuscationDictionary(
        Path classObfuscationDictionary, Origin origin, Position position) {
      this.classObfuscationDictionary = classObfuscationDictionary;
    }

    @Override
    public void setPackageObfuscationDictionary(
        Path packageObfuscationDictionary, Origin origin, Position position) {
      this.packageObfuscationDictionary = packageObfuscationDictionary;
    }

    @Override
    public void setKeepParameterNames(
        boolean keepParameterNames, Origin optionOrigin, Position optionPosition) {
      assert optionOrigin != null || !keepParameterNames;
      this.keepParameterNames = keepParameterNames;
      this.keepParameterNamesOptionOrigin = optionOrigin;
      this.keepParameterNamesOptionPosition = optionPosition;
    }

    boolean isKeepParameterNames() {
      return keepParameterNames;
    }

    Origin getKeepParameterNamesOptionOrigin() {
      return keepParameterNamesOptionOrigin;
    }

    Position getKeepParameterNamesOptionPosition() {
      return keepParameterNamesOptionPosition;
    }

    @Override
    public void addAdaptClassStringsPattern(ProguardClassNameList pattern) {
      adaptClassStrings.addPattern(pattern);
    }

    @Override
    public void addAdaptResourceFilenames(ProguardPathList pattern) {
      adaptResourceFilenames.addPattern(pattern);
    }

    public Builder applyAdaptResourceFilenamesBuilder(
        Consumer<ProguardPathFilter.Builder> consumer) {
      consumer.accept(adaptResourceFilenames);
      return this;
    }

    @Override
    public void addAdaptResourceFileContents(ProguardPathList pattern) {
      adaptResourceFileContents.addPattern(pattern);
    }

    @Override
    public void enableKeepDirectories() {
      keepDirectories.enable();
    }

    @Override
    public void addKeepDirectories(ProguardPathList pattern) {
      keepDirectories.addPattern(pattern);
    }

    public boolean isForceProguardCompatibility() {
      return forceProguardCompatibility;
    }

    public Builder setForceProguardCompatibility(boolean forceProguardCompatibility) {
      this.forceProguardCompatibility = forceProguardCompatibility;
      return this;
    }

    @Override
    public void enableProtoShrinking() {
      protoShrinking = true;
    }

    public ProguardConfiguration buildRaw() {
      ProguardKeepAttributes proguardKeepAttributes =
          ProguardKeepAttributes.fromPatterns(keepAttributePatterns);
      // For Proguard -keepattributes are only applicable when obfuscating.
      if (forceProguardCompatibility && !isObfuscating()) {
        proguardKeepAttributes.keepAllAttributesExceptRuntimeInvisibleAnnotations();
      }
      ProguardConfiguration configuration =
          new ProguardConfiguration(
              String.join(System.lineSeparator(), parsedConfiguration),
              dexItemFactory,
              injars,
              libraryJars,
              packageObfuscationMode,
              packagePrefix,
              allowAccessModification,
              ignoreWarnings,
              optimizing,
              obfuscating,
              shrinking,
              printConfiguration,
              printConfigurationFile,
              printUsage,
              printUsageFile,
              printMapping,
              printMappingFile,
              applyMappingFile,
              renameSourceFileAttribute,
              proguardKeepAttributes,
              keepPackageNamesPatterns.build(),
              dontWarnPatterns.build(),
              dontNotePatterns.build(),
              rules,
              printSeeds,
              seedFile,
              DictionaryReader.readAllNames(obfuscationDictionary, reporter),
              DictionaryReader.readAllNames(classObfuscationDictionary, reporter),
              DictionaryReader.readAllNames(packageObfuscationDictionary, reporter),
              keepParameterNames,
              adaptClassStrings.build(),
              adaptResourceFilenames.build(),
              adaptResourceFileContents.build(),
              keepDirectories.build(),
              protoShrinking,
              getMaxRemovedAndroidLogLevel());

      reporter.failIfPendingErrors();

      return configuration;
    }

    public ProguardConfiguration build() {

      if (packageObfuscationMode == PackageObfuscationMode.NONE && obfuscating) {
        packageObfuscationMode = PackageObfuscationMode.MINIFICATION;
      }

      return buildRaw();
    }
  }

  private final String parsedConfiguration;
  private final DexItemFactory dexItemFactory;
  private final ImmutableList<FilteredClassPath> injars;
  private final ImmutableList<FilteredClassPath> libraryJars;
  private final PackageObfuscationMode packageObfuscationMode;
  private final String packagePrefix;
  private final boolean allowAccessModification;
  private final boolean ignoreWarnings;
  private final boolean optimizing;
  private final boolean obfuscating;
  private final boolean shrinking;
  private final boolean printConfiguration;
  private final Path printConfigurationFile;
  private final boolean printUsage;
  private final Path printUsageFile;
  private final boolean printMapping;
  private final Path printMappingFile;
  private final Path applyMappingFile;
  private final String renameSourceFileAttribute;
  private final ProguardKeepAttributes keepAttributes;
  private ProguardClassFilter keepPackageNamesPatterns;
  private final ProguardClassFilter dontWarnPatterns;
  private final ProguardClassFilter dontNotePatterns;
  protected final ImmutableList<ProguardConfigurationRule> rules;
  private final boolean printSeeds;
  private final Path seedFile;
  private final ImmutableList<String> obfuscationDictionary;
  private final ImmutableList<String> classObfuscationDictionary;
  private final ImmutableList<String> packageObfuscationDictionary;
  private final boolean keepParameterNames;
  private final ProguardClassFilter adaptClassStrings;
  private final ProguardPathFilter adaptResourceFilenames;
  private final ProguardPathFilter adaptResourceFileContents;
  private final ProguardPathFilter keepDirectories;
  private final boolean protoShrinking;
  private final int maxRemovedAndroidLogLevel;

  private ProguardConfiguration(
      String parsedConfiguration,
      DexItemFactory factory,
      List<FilteredClassPath> injars,
      List<FilteredClassPath> libraryJars,
      PackageObfuscationMode packageObfuscationMode,
      String packagePrefix,
      boolean allowAccessModification,
      boolean ignoreWarnings,
      boolean optimizing,
      boolean obfuscating,
      boolean shrinking,
      boolean printConfiguration,
      Path printConfigurationFile,
      boolean printUsage,
      Path printUsageFile,
      boolean printMapping,
      Path printMappingFile,
      Path applyMappingFile,
      String renameSourceFileAttribute,
      ProguardKeepAttributes keepAttributes,
      ProguardClassFilter keepPackageNamesPatterns,
      ProguardClassFilter dontWarnPatterns,
      ProguardClassFilter dontNotePatterns,
      Set<ProguardConfigurationRule> rules,
      boolean printSeeds,
      Path seedFile,
      ImmutableList<String> obfuscationDictionary,
      ImmutableList<String> classObfuscationDictionary,
      ImmutableList<String> packageObfuscationDictionary,
      boolean keepParameterNames,
      ProguardClassFilter adaptClassStrings,
      ProguardPathFilter adaptResourceFilenames,
      ProguardPathFilter adaptResourceFileContents,
      ProguardPathFilter keepDirectories,
      boolean protoShrinking,
      int maxRemovedAndroidLogLevel) {
    this.parsedConfiguration = parsedConfiguration;
    this.dexItemFactory = factory;
    this.injars = ImmutableList.copyOf(injars);
    this.libraryJars = ImmutableList.copyOf(libraryJars);
    this.packageObfuscationMode = packageObfuscationMode;
    this.packagePrefix = packagePrefix;
    this.allowAccessModification = allowAccessModification;
    this.ignoreWarnings = ignoreWarnings;
    this.optimizing = optimizing;
    this.obfuscating = obfuscating;
    this.shrinking = shrinking;
    this.printConfiguration = printConfiguration;
    this.printConfigurationFile = printConfigurationFile;
    this.printUsage = printUsage;
    this.printUsageFile = printUsageFile;
    this.printMapping = printMapping;
    this.printMappingFile = printMappingFile;
    this.applyMappingFile = applyMappingFile;
    this.renameSourceFileAttribute = renameSourceFileAttribute;
    this.keepAttributes = keepAttributes;
    this.keepPackageNamesPatterns = keepPackageNamesPatterns;
    this.dontWarnPatterns = dontWarnPatterns;
    this.dontNotePatterns = dontNotePatterns;
    this.rules = ImmutableList.copyOf(rules);
    this.printSeeds = printSeeds;
    this.seedFile = seedFile;
    this.obfuscationDictionary = obfuscationDictionary;
    this.classObfuscationDictionary = classObfuscationDictionary;
    this.packageObfuscationDictionary = packageObfuscationDictionary;
    this.keepParameterNames = keepParameterNames;
    this.adaptClassStrings = adaptClassStrings;
    this.adaptResourceFilenames = adaptResourceFilenames;
    this.adaptResourceFileContents = adaptResourceFileContents;
    this.keepDirectories = keepDirectories;
    this.protoShrinking = protoShrinking;
    this.maxRemovedAndroidLogLevel = maxRemovedAndroidLogLevel;
  }

  /**
   * Create a new empty builder.
   */
  public static Builder builder(DexItemFactory dexItemFactory,
      Reporter reporter) {
    return new Builder(dexItemFactory, reporter);
  }

  public String getParsedConfiguration() {
    return parsedConfiguration;
  }

  public DexItemFactory getDexItemFactory() {
    return dexItemFactory;
  }

  public List<FilteredClassPath> getInjars() {
    return injars;
  }

  public List<FilteredClassPath> getLibraryjars() {
    return libraryJars;
  }

  public PackageObfuscationMode getPackageObfuscationMode() {
    return packageObfuscationMode;
  }

  public String getPackagePrefix() {
    return packagePrefix;
  }

  public boolean isAccessModificationAllowed() {
    return allowAccessModification;
  }

  public boolean isPrintMapping() {
    return printMapping;
  }

  public Path getPrintMappingFile() {
    return printMappingFile;
  }

  public boolean hasApplyMappingFile() {
    return applyMappingFile != null;
  }

  public Path getApplyMappingFile() {
    return applyMappingFile;
  }

  public boolean isIgnoreWarnings() {
    return ignoreWarnings;
  }

  public boolean isOptimizing() {
    return optimizing;
  }

  public boolean isObfuscating() {
    return obfuscating;
  }

  public boolean isShrinking() {
    return shrinking;
  }

  public boolean isPrintConfiguration() {
    return printConfiguration;
  }

  public Path getPrintConfigurationFile() {
    return printConfigurationFile;
  }

  public boolean isPrintUsage() {
    return printUsage;
  }

  public Path getPrintUsageFile() {
    return printUsageFile;
  }

  public String getRenameSourceFileAttribute() {
    return renameSourceFileAttribute;
  }

  public ProguardKeepAttributes getKeepAttributes() {
    return keepAttributes;
  }

  public ProguardClassFilter getKeepPackageNamesPatterns() {
    return keepPackageNamesPatterns;
  }

  public void setKeepPackageNamesPatterns(ProguardClassFilter keepPackageNamesPatterns) {
    this.keepPackageNamesPatterns = keepPackageNamesPatterns;
  }

  public boolean hasDontWarnPatterns() {
    return !dontWarnPatterns.isEmpty();
  }

  public ProguardClassFilter getDontWarnPatterns(DontWarnConfiguration.Witness witness) {
    assert witness != null;
    return dontWarnPatterns;
  }

  public ProguardClassFilter getDontNotePatterns() {
    return dontNotePatterns;
  }

  public List<ProguardConfigurationRule> getRules() {
    return rules;
  }

  public List<String> getObfuscationDictionary() {
    return obfuscationDictionary;
  }

  public List<String> getClassObfuscationDictionary() {
    return classObfuscationDictionary;
  }

  public List<String> getPackageObfuscationDictionary() {
    return packageObfuscationDictionary;
  }

  public boolean isKeepParameterNames() {
    return keepParameterNames;
  }

  public ProguardClassFilter getAdaptClassStrings() {
    return adaptClassStrings;
  }

  public ProguardPathFilter getAdaptResourceFilenames() {
    return adaptResourceFilenames;
  }

  public ProguardPathFilter getAdaptResourceFileContents() {
    return adaptResourceFileContents;
  }

  public ProguardPathFilter getKeepDirectories() {
    return keepDirectories;
  }

  public boolean isPrintSeeds() {
    return printSeeds;
  }

  public Path getSeedFile() {
    return seedFile;
  }

  public boolean isProtoShrinkingEnabled() {
    return protoShrinking;
  }

  public int getMaxRemovedAndroidLogLevel() {
    return maxRemovedAndroidLogLevel;
  }

  public boolean hasMaximumRemovedAndroidLogLevelRules() {
    return Iterables.any(rules, ProguardConfigurationRule::isMaximumRemovedAndroidLogLevelRule);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    if (!keepAttributes.isEmpty()) {
      keepAttributes.append(builder);
      builder.append(StringUtils.LINE_SEPARATOR);
    }
    for (ProguardConfigurationRule rule : rules) {
      rule.append(builder);
      builder.append(StringUtils.LINE_SEPARATOR);
    }
    return builder.toString();
  }
}
