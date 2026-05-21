// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resourceshrinker.usages;

import com.android.ide.common.resources.usage.ResourceUsageModel;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.android.resources.ResourceType;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerModel;
import com.android.tools.r8.resourceshrinker.obfuscation.ClassAndMethod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class DexUsageRecorder {

  private final Path root;
  private final DexAnalyser analyser;

  public DexUsageRecorder(Path root, DexAnalyser analyser) {
    this.root = root;
    this.analyser = analyser;
  }

  public void recordUsages(ResourceShrinkerModel model) {
    try (Stream<Path> walk = Files.walk(root)) {
      walk.filter(Files::isRegularFile)
          .filter(path -> path.toString().toLowerCase().endsWith(".dex"))
          .forEach(
              path -> {
                try {
                  analyser.analyse(
                      Files.readAllBytes(path),
                      path.toString(),
                      new DexFileAnalysisCallback(path, model));
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Predicate<String> getRequiredClassNamesPredicate() {
    return className -> className.contains("R$");
  }

  public static class DexFileAnalysisCallback implements AnalysisCallback {
    private static final String ANDROID_RES = "android_res/";

    private final Path path;
    private final ResourceShrinkerModel model;
    private boolean isRClass = false;
    private final MethodVisitingStatus visitingMethod = new MethodVisitingStatus();

    public DexFileAnalysisCallback(Path path, ResourceShrinkerModel model) {
      this.path = path;
      this.model = model;
    }

    private static String toSourceClassName(String internalName) {
      return internalName.replace('/', '.');
    }

    @Override
    public boolean shouldProcess(String internalName) {
      isRClass = isResourceClass(internalName);
      return true;
    }

    private boolean isResourceClass(String internalName) {
      String realClassName =
          model.getObfuscatedClasses().resolveOriginalClass(toSourceClassName(internalName));
      int lastDot = realClassName.lastIndexOf('.');
      String lastPart = lastDot == -1 ? realClassName : realClassName.substring(lastDot + 1);
      if (lastPart.startsWith("R$")) {
        String typeName = lastPart.substring(2);
        return ResourceType.fromClassName(typeName) != null;
      }
      return false;
    }

    @Override
    public void referencedInt(int value) {
      if (shouldIgnoreField()) {
        return;
      }
      Resource resource = model.getResourceStore().getResource(value);
      if (ResourceUsageModel.markReachable(resource)) {
        model
            .getDebugReporter()
            .debug(() -> "Marking " + resource + " reachable: referenced from " + path);
      }
    }

    @Override
    public void referencedStaticField(String internalName, String fieldName) {
      if (shouldIgnoreField()) {
        return;
      }
      ClassAndMethod realMethod =
          model
              .getObfuscatedClasses()
              .resolveOriginalMethod(
                  new ClassAndMethod(toSourceClassName(internalName), fieldName));

      if (isValidResourceType(realMethod.getClassName())) {
        int lastDollar = realMethod.getClassName().lastIndexOf('$');
        String typePart =
            lastDollar == -1
                ? realMethod.getClassName()
                : realMethod.getClassName().substring(lastDollar + 1);
        ResourceType type = ResourceType.fromClassName(typePart);
        if (type != null) {
          for (Resource resource :
              model.getResourceStore().getResources(type, realMethod.getMethodName())) {
            ResourceUsageModel.markReachable(resource);
          }
        }
      }
    }

    @Override
    public void referencedString(String value) {
      if (shouldIgnoreField()) {
        return;
      }
      if (value.isEmpty() || value.length() > 80) {
        return;
      }
      boolean allValid = true;
      boolean hasIdentifierPart = false;
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        if (Character.isJavaIdentifierPart(c) || c == '.' || c == ':' || c == '/' || c == '%') {
          if (Character.isJavaIdentifierPart(c)) {
            hasIdentifierPart = true;
          }
        } else {
          allValid = false;
          break;
        }
      }
      if (allValid && hasIdentifierPart) {
        model.addStringConstant(value);
        model.setFoundWebContent(model.isFoundWebContent() || value.contains(ANDROID_RES));
      }
    }

    @Override
    public void referencedMethod(String internalName, String methodName, String methodDescriptor) {
      if (isRClass && visitingMethod.isVisiting && "<clinit>".equals(visitingMethod.methodName)) {
        return;
      }
      if ("android/content/res/Resources".equals(internalName)
          && "getIdentifier".equals(methodName)
          && "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I".equals(methodDescriptor)) {
        if (AppCompat.INSTANCE.isAppCompatClass(
            toSourceClassName(internalName), model.getObfuscatedClasses())) {
          return;
        }
        model.setFoundGetIdentifier(true);
      }
      if ("android/webkit/WebView".equals(internalName) && methodName.startsWith("load")) {
        model.setFoundWebContent(true);
      }
    }

    @Override
    public void startMethodVisit(String methodName) {
      visitingMethod.isVisiting = true;
      visitingMethod.methodName = methodName;
    }

    @Override
    public void endMethodVisit(String methodName) {
      visitingMethod.isVisiting = false;
      visitingMethod.methodName = null;
    }

    private boolean shouldIgnoreField() {
      boolean visitingFromStaticInitRClass =
          (isRClass && visitingMethod.isVisiting && "<clinit>".equals(visitingMethod.methodName));
      return visitingFromStaticInitRClass || (isRClass && !visitingMethod.isVisiting);
    }

    private boolean isValidResourceType(String className) {
      int lastDot = className.lastIndexOf('.');
      String lastPart = lastDot == -1 ? className : className.substring(lastDot + 1);
      return lastPart.startsWith("R$");
    }
  }
}
