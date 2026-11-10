/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.r8.resourceshrinker.obfuscation;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;

/** Contains mappings between obfuscated classes and methods to original ones. */
public final class ObfuscatedClasses {

  public static final ObfuscatedClasses NO_OBFUSCATION = new Builder().build();

  private final ImmutableMap<String, String> obfuscatedClasses;
  private final ImmutableMap<ClassAndMethod, ClassAndMethod> obfuscatedMethods;

  private ObfuscatedClasses(Builder builder) {
    this.obfuscatedClasses = ImmutableMap.copyOf(builder.obfuscatedClasses);
    this.obfuscatedMethods = ImmutableMap.copyOf(builder.obfuscatedMethods);
  }

  public ClassAndMethod resolveOriginalMethod(ClassAndMethod obfuscatedMethod) {
    ClassAndMethod resolved = obfuscatedMethods.get(obfuscatedMethod);
    if (resolved != null) {
      return resolved;
    }
    String realClassName =
        obfuscatedClasses.getOrDefault(
            obfuscatedMethod.getClassName(), obfuscatedMethod.getClassName());
    return new ClassAndMethod(realClassName, obfuscatedMethod.getMethodName());
  }

  public String resolveOriginalClass(String obfuscatedClass) {
    return obfuscatedClasses.getOrDefault(obfuscatedClass, obfuscatedClass);
  }

  /**
   * Builder that allows to build obfuscated mappings in a way when next method mapping is added to
   * previous class mapping. Example:
   *
   * <pre>{@code
   * builder
   *     .addClassMapping(classA, obfuscatedClassA)
   *     .addMethodMapping(classAMethod1, obfuscatedClassAMethod1)
   *     .addMethodMapping(classAMethod2, obfuscatedClassAMethod2)
   *     .addClassMapping(classB, obfuscatedClassB)
   *     .addMethodMapping(classBMethod1, obfuscatedClassBMethod1);
   * }</pre>
   */
  public static final class Builder {

    private final Map<String, String> obfuscatedClasses = new HashMap<>();
    private final Map<ClassAndMethod, ClassAndMethod> obfuscatedMethods = new HashMap<>();
    private String currentOriginalClass;
    private String currentObfuscatedClass;

    /**
     * Adds class mapping: original class name -&gt; obfuscated class name.
     *
     * @param originalClassName original class name.
     * @param obfuscatedClassName obfuscated class name.
     */
    public Builder addClassMapping(String originalClassName, String obfuscatedClassName) {
      currentOriginalClass = originalClassName;
      currentObfuscatedClass = obfuscatedClassName;
      obfuscatedClasses.put(obfuscatedClassName, originalClassName);
      return this;
    }

    /**
     * Adds method mapping: original method name -&gt; obfuscated method name to the latest added
     * class mapping.
     *
     * @param originalMethodName original method name.
     * @param obfuscatedMethodName obfuscated method name.
     */
    public Builder addMethodMapping(String originalMethodName, String obfuscatedMethodName) {
      if (currentOriginalClass != null) {
        obfuscatedMethods.put(
            new ClassAndMethod(currentObfuscatedClass, obfuscatedMethodName),
            new ClassAndMethod(currentOriginalClass, originalMethodName));
      }
      return this;
    }

    public ObfuscatedClasses build() {
      return new ObfuscatedClasses(this);
    }
  }
}
