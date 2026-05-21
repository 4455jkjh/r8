// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resourceshrinker.usages;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceShrinker;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.references.MethodReference;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;

public class R8ResourceShrinker implements DexAnalyser {

  @Override
  public void analyse(byte[] dexBytes, String name, AnalysisCallback callback) {
    runResourceShrinkerAnalysis(dexBytes, Paths.get(name), callback);
  }

  public static void runResourceShrinkerAnalysis(
      byte[] bytes, Path file, AnalysisCallback callback) {
    ProgramResource resource =
        ProgramResource.fromBytes(new PathOrigin(file), ProgramResource.Kind.DEX, bytes, null);
    ProgramResourceProvider provider =
        new ProgramResourceProvider() {
          @Override
          public Collection<ProgramResource> getProgramResources() {
            return Collections.singletonList(resource);
          }

          @Override
          public void getProgramResources(Consumer<ProgramResource> consumer) {
            consumer.accept(resource);
          }
        };

    try {
      ResourceShrinker.Command command =
          new ResourceShrinker.Builder().addProgramResourceProvider(provider).build();
      ResourceShrinker.run(command, new AnalysisAdapter(callback));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static class AnalysisAdapter implements ResourceShrinker.ReferenceChecker {
    private final AnalysisCallback impl;

    AnalysisAdapter(AnalysisCallback impl) {
      this.impl = impl;
    }

    @Override
    public boolean shouldProcess(String internalName) {
      return impl.shouldProcess(internalName);
    }

    @Override
    public void referencedInt(int value) {
      impl.referencedInt(value);
    }

    @Override
    public void referencedString(String value) {
      impl.referencedString(value);
    }

    @Override
    public void referencedStaticField(String internalName, String fieldName) {
      impl.referencedStaticField(internalName, fieldName);
    }

    @Override
    public void referencedMethod(String internalName, String methodName, String methodDescriptor) {
      impl.referencedMethod(internalName, methodName, methodDescriptor);
    }

    @Override
    public void startMethodVisit(MethodReference methodReference) {
      impl.startMethodVisit(methodReference.getMethodName());
    }

    @Override
    public void endMethodVisit(MethodReference methodReference) {
      impl.endMethodVisit(methodReference.getMethodName());
    }
  }
}
