// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dump;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.experimental.startup.StartupConfiguration;
import com.android.tools.r8.features.FeatureSplitConfiguration;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class DumpOptions {

  // The following keys and values should not be changed to keep the dump utility backward
  // compatible with previous versions. They are also used by the python script compileDump and
  // the corresponding CompileDumpCompatR8 java class.
  private static final String TOOL_KEY = "tool";
  private static final String MODE_KEY = "mode";
  private static final String DEBUG_MODE_VALUE = "debug";
  private static final String RELEASE_MODE_VALUE = "release";
  private static final String MIN_API_KEY = "min-api";
  private static final String OPTIMIZE_MULTIDEX_FOR_LINEAR_ALLOC_KEY =
      "optimize-multidex-for-linear-alloc";
  private static final String THREAD_COUNT_KEY = "thread-count";
  private static final String DESUGAR_STATE_KEY = "desugar-state";
  private static final String INTERMEDIATE_KEY = "intermediate";
  private static final String INCLUDE_DATA_RESOURCES_KEY = "include-data-resources";
  private static final String TREE_SHAKING_KEY = "tree-shaking";
  private static final String MINIFICATION_KEY = "minification";
  private static final String FORCE_PROGUARD_COMPATIBILITY_KEY = "force-proguard-compatibility";
  private static final String SYSTEM_PROPERTY_PREFIX = "system-property-";

  private final Tool tool;
  private final CompilationMode compilationMode;
  private final int minApi;
  private final boolean optimizeMultidexForLinearAlloc;
  private final int threadCount;
  private final DesugarState desugarState;
  private final Optional<Boolean> intermediate;
  private final Optional<Boolean> includeDataResources;
  private final Optional<Boolean> treeShaking;
  private final Optional<Boolean> minification;
  private final Optional<Boolean> forceProguardCompatibility;

  // Dump if present.
  private final DesugaredLibrarySpecification desugaredLibrarySpecification;
  private final FeatureSplitConfiguration featureSplitConfiguration;
  private final ProguardConfiguration proguardConfiguration;
  private final List<ProguardConfigurationRule> mainDexKeepRules;

  private final Map<String, String> systemProperties;

  // Reporting only.
  private final boolean dumpInputToFile;

  private DumpOptions(
      Tool tool,
      CompilationMode compilationMode,
      int minAPI,
      DesugaredLibrarySpecification desugaredLibrarySpecification,
      boolean optimizeMultidexForLinearAlloc,
      int threadCount,
      DesugarState desugarState,
      Optional<Boolean> intermediate,
      Optional<Boolean> includeDataResources,
      Optional<Boolean> treeShaking,
      Optional<Boolean> minification,
      Optional<Boolean> forceProguardCompatibility,
      FeatureSplitConfiguration featureSplitConfiguration,
      ProguardConfiguration proguardConfiguration,
      List<ProguardConfigurationRule> mainDexKeepRules,
      Map<String, String> systemProperties,
      boolean dumpInputToFile) {
    this.tool = tool;
    this.compilationMode = compilationMode;
    this.minApi = minAPI;
    this.desugaredLibrarySpecification = desugaredLibrarySpecification;
    this.optimizeMultidexForLinearAlloc = optimizeMultidexForLinearAlloc;
    this.threadCount = threadCount;
    this.desugarState = desugarState;
    this.intermediate = intermediate;
    this.includeDataResources = includeDataResources;
    this.treeShaking = treeShaking;
    this.minification = minification;
    this.forceProguardCompatibility = forceProguardCompatibility;
    this.featureSplitConfiguration = featureSplitConfiguration;
    this.proguardConfiguration = proguardConfiguration;
    this.mainDexKeepRules = mainDexKeepRules;
    this.systemProperties = systemProperties;
    this.dumpInputToFile = dumpInputToFile;
  }

  public String getBuildPropertiesFileContent() {
    StringBuilder builder = new StringBuilder();
    getBuildProperties()
        .forEach((key, value) -> builder.append(key).append("=").append(value).append("\n"));
    return builder.toString();
  }

  public Map<String, String> getBuildProperties() {
    Map<String, String> buildProperties = new LinkedHashMap<>();
    addDumpEntry(buildProperties, TOOL_KEY, tool.name());
    // We keep the following values for backward compatibility.
    addDumpEntry(
        buildProperties,
        MODE_KEY,
        compilationMode == CompilationMode.DEBUG ? DEBUG_MODE_VALUE : RELEASE_MODE_VALUE);
    addDumpEntry(buildProperties, MIN_API_KEY, minApi);
    addDumpEntry(
        buildProperties, OPTIMIZE_MULTIDEX_FOR_LINEAR_ALLOC_KEY, optimizeMultidexForLinearAlloc);
    if (threadCount != ThreadUtils.NOT_SPECIFIED) {
      addDumpEntry(buildProperties, THREAD_COUNT_KEY, threadCount);
    }
    addDumpEntry(buildProperties, DESUGAR_STATE_KEY, desugarState);
    addOptionalDumpEntry(buildProperties, INTERMEDIATE_KEY, intermediate);
    addOptionalDumpEntry(buildProperties, INCLUDE_DATA_RESOURCES_KEY, includeDataResources);
    addOptionalDumpEntry(buildProperties, TREE_SHAKING_KEY, treeShaking);
    addOptionalDumpEntry(buildProperties, MINIFICATION_KEY, minification);
    addOptionalDumpEntry(
        buildProperties, FORCE_PROGUARD_COMPATIBILITY_KEY, forceProguardCompatibility);
    ArrayList<String> sortedKeys = new ArrayList<>(systemProperties.keySet());
    sortedKeys.sort(String::compareTo);
    sortedKeys.forEach(
        key ->
            addDumpEntry(buildProperties, SYSTEM_PROPERTY_PREFIX + key, systemProperties.get(key)));
    return buildProperties;
  }

  public static void parse(String content, DumpOptions.Builder builder) {
    String[] lines = content.split("\n");
    for (String line : lines) {
      String trimmed = line.trim();
      int i = trimmed.indexOf('=');
      if (i < 0) {
        throw new RuntimeException("Invalid dump line. Expected = in line: '" + trimmed + "'");
      }
      String key = trimmed.substring(0, i).trim();
      String value = trimmed.substring(i + 1).trim();
      parseKeyValue(builder, key, value);
    }
  }

  private static void parseKeyValue(Builder builder, String key, String value) {
    switch (key) {
      case TOOL_KEY:
        builder.setTool(Tool.valueOf(value));
        return;
      case MODE_KEY:
        if (value.equals(DEBUG_MODE_VALUE)) {
          builder.setCompilationMode(CompilationMode.DEBUG);
        } else if (value.equals(RELEASE_MODE_VALUE)) {
          builder.setCompilationMode(CompilationMode.RELEASE);
        } else {
          parseKeyValueError(key, value);
        }
        return;
      case MIN_API_KEY:
        builder.setMinApi(Integer.parseInt(value));
        return;
      case OPTIMIZE_MULTIDEX_FOR_LINEAR_ALLOC_KEY:
        builder.setOptimizeMultidexForLinearAlloc(Boolean.parseBoolean(value));
        return;
      case THREAD_COUNT_KEY:
        builder.setThreadCount(Integer.parseInt(value));
        return;
      case DESUGAR_STATE_KEY:
        builder.setDesugarState(DesugarState.valueOf(value));
        return;
      case INTERMEDIATE_KEY:
        builder.setIntermediate(Boolean.parseBoolean(value));
        return;
      case INCLUDE_DATA_RESOURCES_KEY:
        builder.setIncludeDataResources(Optional.of(Boolean.parseBoolean(value)));
        return;
      case TREE_SHAKING_KEY:
        builder.setTreeShaking(Boolean.parseBoolean(value));
        return;
      case MINIFICATION_KEY:
        builder.setMinification(Boolean.parseBoolean(value));
        return;
      case FORCE_PROGUARD_COMPATIBILITY_KEY:
        builder.setForceProguardCompatibility(Boolean.parseBoolean(value));
        return;
      default:
        if (key.startsWith(SYSTEM_PROPERTY_PREFIX)) {
          builder.setSystemProperty(key.substring(SYSTEM_PROPERTY_PREFIX.length()), value);
        } else {
          parseKeyValueError(key, value);
        }
    }
  }

  private static void parseKeyValueError(String key, String value) {
    throw new RuntimeException("Unknown key value pair: " + key + " = " + value);
  }

  public Tool getTool() {
    return tool;
  }

  public CompilationMode getCompilationMode() {
    return compilationMode;
  }

  public int getMinApi() {
    return minApi;
  }

  private void addOptionalDumpEntry(
      Map<String, String> buildProperties, String key, Optional<?> optionalValue) {
    optionalValue.ifPresent(bool -> addDumpEntry(buildProperties, key, bool));
  }

  private void addDumpEntry(Map<String, String> buildProperties, String key, Object value) {
    buildProperties.put(key, Objects.toString(value));
  }

  private boolean hasDesugaredLibraryConfiguration() {
    return desugaredLibrarySpecification != null && !desugaredLibrarySpecification.isEmpty();
  }

  public String getDesugaredLibraryJsonSource() {
    if (hasDesugaredLibraryConfiguration()) {
      return desugaredLibrarySpecification.getJsonSource();
    }
    return null;
  }

  public FeatureSplitConfiguration getFeatureSplitConfiguration() {
    return featureSplitConfiguration;
  }

  public StartupConfiguration getStartupConfiguration() {
    // The startup configuration is not included in dumps.
    return null;
  }

  public String getParsedProguardConfiguration() {
    return proguardConfiguration == null ? null : proguardConfiguration.getParsedConfiguration();
  }

  public boolean hasMainDexKeepRules() {
    return mainDexKeepRules != null && !mainDexKeepRules.isEmpty();
  }

  public List<ProguardConfigurationRule> getMainDexKeepRules() {
    return mainDexKeepRules;
  }

  public boolean dumpInputToFile() {
    return dumpInputToFile;
  }

  public static Builder builder(Tool tool) {
    return new Builder().setTool(tool);
  }

  public static class Builder {
    private Tool tool;
    private CompilationMode compilationMode;
    private int minApi;
    private boolean optimizeMultidexForLinearAlloc;
    private int threadCount;
    private DesugarState desugarState;
    private Optional<Boolean> intermediate = Optional.empty();
    private Optional<Boolean> includeDataResources = Optional.empty();
    private Optional<Boolean> treeShaking = Optional.empty();
    private Optional<Boolean> minification = Optional.empty();
    private Optional<Boolean> forceProguardCompatibility = Optional.empty();
    // Dump if present.
    private DesugaredLibrarySpecification desugaredLibrarySpecification;
    private FeatureSplitConfiguration featureSplitConfiguration;
    private ProguardConfiguration proguardConfiguration;
    private List<ProguardConfigurationRule> mainDexKeepRules;

    private Map<String, String> systemProperties = new HashMap<>();

    // Reporting only.
    private boolean dumpInputToFile;

    public Builder() {}

    public Builder setTool(Tool tool) {
      this.tool = tool;
      return this;
    }

    public Builder setCompilationMode(CompilationMode compilationMode) {
      this.compilationMode = compilationMode;
      return this;
    }

    public Builder setMinApi(int minAPI) {
      this.minApi = minAPI;
      return this;
    }

    public Builder setDesugaredLibraryConfiguration(
        DesugaredLibrarySpecification desugaredLibrarySpecification) {
      this.desugaredLibrarySpecification = desugaredLibrarySpecification;
      return this;
    }

    public Builder setOptimizeMultidexForLinearAlloc(boolean optimizeMultidexForLinearAlloc) {
      this.optimizeMultidexForLinearAlloc = optimizeMultidexForLinearAlloc;
      return this;
    }

    public Builder setThreadCount(int threadCount) {
      this.threadCount = threadCount;
      return this;
    }

    public Builder setDesugarState(DesugarState desugarState) {
      this.desugarState = desugarState;
      return this;
    }

    public Builder setIntermediate(boolean intermediate) {
      this.intermediate = Optional.of(intermediate);
      return this;
    }

    public Builder setIncludeDataResources(Optional<Boolean> includeDataResources) {
      this.includeDataResources = includeDataResources;
      return this;
    }

    public Builder setForceProguardCompatibility(boolean forceProguardCompatibility) {
      this.forceProguardCompatibility = Optional.of(forceProguardCompatibility);
      return this;
    }

    public Builder setMinification(boolean minification) {
      this.minification = Optional.of(minification);
      return this;
    }

    public Builder setTreeShaking(boolean treeShaking) {
      this.treeShaking = Optional.of(treeShaking);
      return this;
    }

    public Builder setDumpInputToFile(boolean dumpInputToFile) {
      this.dumpInputToFile = dumpInputToFile;
      return this;
    }

    public Builder setFeatureSplitConfiguration(
        FeatureSplitConfiguration featureSplitConfiguration) {
      this.featureSplitConfiguration = featureSplitConfiguration;
      return this;
    }

    public Builder setProguardConfiguration(ProguardConfiguration proguardConfiguration) {
      this.proguardConfiguration = proguardConfiguration;
      return this;
    }

    public Builder setMainDexKeepRules(List<ProguardConfigurationRule> mainDexKeepRules) {
      this.mainDexKeepRules = mainDexKeepRules;
      return this;
    }

    public Builder setSystemProperty(String key, String value) {
      this.systemProperties.put(key, value);
      return this;
    }

    public Builder readCurrentSystemProperties() {
      System.getProperties()
          .stringPropertyNames()
          .forEach(
              name -> {
                if (name.startsWith("com.android.tools.r8.")) {
                  String value = System.getProperty(name);
                  setSystemProperty(name, value);
                }
              });
      return this;
    }

    public DumpOptions build() {
      assert tool != null;
      return new DumpOptions(
          tool,
          compilationMode,
          minApi,
          desugaredLibrarySpecification,
          optimizeMultidexForLinearAlloc,
          threadCount,
          desugarState,
          intermediate,
          includeDataResources,
          treeShaking,
          minification,
          forceProguardCompatibility,
          featureSplitConfiguration,
          proguardConfiguration,
          mainDexKeepRules,
          systemProperties,
          dumpInputToFile);
    }
  }
}
