// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.internal.collections.Pair;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;

public class BaseCompilerCommandParser {

  private enum AssertionTransformationType {
    ENABLE,
    DISABLE,
    PASSTHROUGH,
    HANDLER
  }

  private static AssertionsConfiguration.Builder prepareBuilderForScope(
      AssertionsConfiguration.Builder builder,
      AssertionTransformationType transformation,
      MethodReference assertionHandler) {
    switch (transformation) {
      case ENABLE:
        return builder.setCompileTimeEnable();
      case DISABLE:
        return builder.setCompileTimeDisable();
      case PASSTHROUGH:
        return builder.setPassthrough();
      case HANDLER:
        return builder.setAssertionHandler(assertionHandler);
      default:
        throw new Unreachable();
    }
  }

  private static <C extends BaseCompilerCommand, B extends BaseCompilerCommand.Builder<C, B>>
      void addAssertionTransformation(
          B builder,
          AssertionTransformationType transformation,
          MethodReference assertionHandler,
          String scope) {
    if (scope == null) {
      builder.addAssertionsConfiguration(
          b -> prepareBuilderForScope(b, transformation, assertionHandler).setScopeAll().build());
    } else {
      assert !scope.isEmpty();
      String packageAssertionPostfix = "...";
      if (scope.endsWith(packageAssertionPostfix)) {
        builder.addAssertionsConfiguration(
            b ->
                prepareBuilderForScope(b, transformation, assertionHandler)
                    .setScopePackage(
                        scope.substring(0, scope.length() - packageAssertionPostfix.length()))
                    .build());
      } else {
        builder.addAssertionsConfiguration(
            b ->
                prepareBuilderForScope(b, transformation, assertionHandler)
                    .setScopeClass(scope)
                    .build());
      }
    }
  }

  private static <C extends BaseCompilerCommand, B extends BaseCompilerCommand.Builder<C, B>>
      String parseAssertionScope(B builder, String suffix, Origin origin) {
    if (suffix.isEmpty()) {
      return null;
    }
    if (suffix.equals(":")) {
      throw builder.fatalError(new StringDiagnostic("Missing optional argument", origin));
    }
    if (!suffix.startsWith(":")) {
      builder.error(new StringDiagnostic("Illegal assertion scope: " + suffix, origin));
      return suffix;
    }
    String classOrPackageScope = suffix.substring(1);
    if (classOrPackageScope.contains(";")
        || classOrPackageScope.contains("[")
        || classOrPackageScope.contains("/")) {
      builder.error(
          new StringDiagnostic("Illegal assertion scope: " + classOrPackageScope, origin));
    }
    return classOrPackageScope;
  }

  private static <C extends BaseCompilerCommand, B extends BaseCompilerCommand.Builder<C, B>>
      Pair<MethodReference, String> parseAssertionHandler(B builder, String suffix, Origin origin) {
    if (suffix.isEmpty() || suffix.equals(":")) {
      throw builder.fatalError(
          new StringDiagnostic("Missing required argument <handler method>", origin));
    }
    if (!suffix.startsWith(":")) {
      throw builder.fatalError(
          new StringDiagnostic("Missing required argument <handler method>", origin));
    }
    String remaining = suffix.substring(1);
    int index = remaining.indexOf(':');
    if (index == 0) {
      throw builder.fatalError(
          new StringDiagnostic("Missing required argument <handler method>", origin));
    }
    String assertionsHandlerString = index > 0 ? remaining.substring(0, index) : remaining;
    int lastDotIndex = assertionsHandlerString.lastIndexOf('.');
    if (assertionsHandlerString.length() < 3
        || lastDotIndex <= 0
        || lastDotIndex == assertionsHandlerString.length() - 1
        || !DescriptorUtils.isValidJavaType(assertionsHandlerString.substring(0, lastDotIndex))) {
      throw builder.fatalError(
          new StringDiagnostic(
              "Invalid argument <handler method>: " + assertionsHandlerString, origin));
    }
    MethodReference assertionsHandler =
        Reference.methodFromDescriptor(
            DescriptorUtils.javaTypeToDescriptor(
                assertionsHandlerString.substring(0, lastDotIndex)),
            assertionsHandlerString.substring(lastDotIndex + 1),
            "(Ljava/lang/Throwable;)V");
    String scopeSuffix = remaining.substring(assertionsHandlerString.length());
    String scope = parseAssertionScope(builder, scopeSuffix, origin);
    return Pair.create(assertionsHandler, scope);
  }

  public static <C extends BaseCompilerCommand, B extends BaseCompilerCommand.Builder<C, B>>
      void parseForceEnableAssertions(B builder, String suffix, Origin origin) {
    String scope = parseAssertionScope(builder, suffix, origin);
    addAssertionTransformation(builder, AssertionTransformationType.ENABLE, null, scope);
  }

  public static <C extends BaseCompilerCommand, B extends BaseCompilerCommand.Builder<C, B>>
      void parseForceDisableAssertions(B builder, String suffix, Origin origin) {
    String scope = parseAssertionScope(builder, suffix, origin);
    addAssertionTransformation(builder, AssertionTransformationType.DISABLE, null, scope);
  }

  public static <C extends BaseCompilerCommand, B extends BaseCompilerCommand.Builder<C, B>>
      void parseForcePassthroughAssertions(B builder, String suffix, Origin origin) {
    String scope = parseAssertionScope(builder, suffix, origin);
    addAssertionTransformation(builder, AssertionTransformationType.PASSTHROUGH, null, scope);
  }

  public static <C extends BaseCompilerCommand, B extends BaseCompilerCommand.Builder<C, B>>
      void parseForceAssertionsHandler(B builder, String suffix, Origin origin) {
    Pair<MethodReference, String> handlerAndScope = parseAssertionHandler(builder, suffix, origin);
    addAssertionTransformation(
        builder,
        AssertionTransformationType.HANDLER,
        handlerAndScope.getFirst(),
        handlerAndScope.getSecond());
  }
}
