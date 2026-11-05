// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysisCollection;
import com.android.tools.r8.graph.analysis.TraceCheckCastEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.TraceExceptionGuardEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.TraceInstanceOfEnqueuerAnalysis;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.utils.internal.SetUtils;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.Set;
import java.util.function.Function;

public class RuntimeTypeCheckInfo {

  private final Reference2IntMap<DexType> instanceOfTypes;
  private final Set<DexType> instanceOfArrayTypes;
  private final Reference2IntMap<DexType> checkCastTypes;
  private final Set<DexType> checkCastArrayTypes;
  private final Set<DexType> exceptionGuardTypes;

  public RuntimeTypeCheckInfo(
      Reference2IntMap<DexType> instanceOfTypes,
      Set<DexType> instanceOfArrayTypes,
      Reference2IntMap<DexType> checkCastTypes,
      Set<DexType> checkCastArrayTypes,
      Set<DexType> exceptionGuardTypes) {
    this.instanceOfTypes = instanceOfTypes;
    this.instanceOfArrayTypes = instanceOfArrayTypes;
    this.checkCastTypes = checkCastTypes;
    this.checkCastArrayTypes = checkCastArrayTypes;
    this.exceptionGuardTypes = exceptionGuardTypes;
  }

  public static void register(Builder builder, EnqueuerAnalysisCollection.Builder analysesBuilder) {
    if (builder != null) {
      analysesBuilder
          .addTraceCheckCastAnalysis(builder)
          .addTraceExceptionGuardAnalysis(builder)
          .addTraceInstanceOfAnalysis(builder);
    }
  }

  public int getNumberOfCheckCasts(DexProgramClass clazz) {
    return checkCastTypes.getInt(clazz.getType());
  }

  public boolean isCheckCastType(DexProgramClass clazz) {
    return checkCastTypes.containsKey(clazz.getType());
  }

  public boolean isCheckCastArrayType(DexProgramClass clazz) {
    return checkCastArrayTypes.contains(clazz.getType());
  }

  public int getNumberOfInstanceOfs(DexProgramClass clazz) {
    return instanceOfTypes.getInt(clazz.getType());
  }

  public boolean isInstanceOfType(DexProgramClass clazz) {
    return instanceOfTypes.containsKey(clazz.getType());
  }

  public boolean isInstanceOfArrayType(DexProgramClass clazz) {
    return instanceOfArrayTypes.contains(clazz.getType());
  }

  public boolean isExceptionGuardType(DexProgramClass clazz) {
    return exceptionGuardTypes.contains(clazz.getType());
  }

  public boolean isRuntimeCheckType(DexProgramClass clazz) {
    return isInstanceOfType(clazz)
        || isInstanceOfArrayType(clazz)
        || isCheckCastType(clazz)
        || isCheckCastArrayType(clazz)
        || isExceptionGuardType(clazz);
  }

  public RuntimeTypeCheckInfo rewriteWithLens(
      NonIdentityGraphLens graphLens, GraphLens appliedGraphLens) {
    Function<DexType, DexType> typeRewriter = type -> graphLens.lookupType(type, appliedGraphLens);
    return new RuntimeTypeCheckInfo(
        rewriteCounts(instanceOfTypes, typeRewriter),
        SetUtils.mapIdentityHashSet(instanceOfArrayTypes, typeRewriter),
        rewriteCounts(checkCastTypes, typeRewriter),
        SetUtils.mapIdentityHashSet(checkCastArrayTypes, typeRewriter),
        SetUtils.mapIdentityHashSet(exceptionGuardTypes, typeRewriter));
  }

  private static Reference2IntMap<DexType> rewriteCounts(
      Reference2IntMap<DexType> counts, Function<DexType, DexType> typeRewriter) {
    Reference2IntOpenHashMap<DexType> rewrittenCounts =
        new Reference2IntOpenHashMap<>(counts.size());
    for (Entry<DexType> entry : counts.reference2IntEntrySet()) {
      DexType rewrittenType = typeRewriter.apply(entry.getKey());
      rewrittenCounts.addTo(rewrittenType, entry.getIntValue());
    }
    return rewrittenCounts;
  }

  public static class Builder
      implements TraceInstanceOfEnqueuerAnalysis,
          TraceCheckCastEnqueuerAnalysis,
          TraceExceptionGuardEnqueuerAnalysis {

    private final GraphLens appliedGraphLens;

    private final Reference2IntOpenHashMap<DexType> instanceOfTypes =
        new Reference2IntOpenHashMap<>();
    private final Set<DexType> instanceOfArrayTypes = Sets.newIdentityHashSet();
    private final Reference2IntOpenHashMap<DexType> checkCastTypes =
        new Reference2IntOpenHashMap<>();
    private final Set<DexType> checkCastArrayTypes = Sets.newIdentityHashSet();
    private final Set<DexType> exceptionGuardTypes = Sets.newIdentityHashSet();

    public Builder(AppView<?> appView) {
      this.appliedGraphLens = appView.graphLens();
    }

    @SuppressWarnings("ReferenceEquality")
    public RuntimeTypeCheckInfo build(GraphLens graphLens) {
      RuntimeTypeCheckInfo runtimeTypeCheckInfo =
          new RuntimeTypeCheckInfo(
              instanceOfTypes,
              instanceOfArrayTypes,
              checkCastTypes,
              checkCastArrayTypes,
              exceptionGuardTypes);
      return graphLens.isNonIdentityLens() && graphLens != appliedGraphLens
          ? runtimeTypeCheckInfo.rewriteWithLens(graphLens.asNonIdentityLens(), appliedGraphLens)
          : runtimeTypeCheckInfo;
    }

    @Override
    public void traceCheckCast(DexType type, DexClass clazz, ProgramMethod context) {
      addAndCount(type, checkCastTypes, checkCastArrayTypes);
    }

    @Override
    public void traceSafeCheckCast(DexType type, DexClass clazz, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void traceInstanceOf(DexType type, DexClass clazz, ProgramMethod context) {
      addAndCount(type, instanceOfTypes, instanceOfArrayTypes);
    }

    @Override
    public void traceExceptionGuard(DexType guard, DexClass clazz, ProgramMethod context) {
      add(guard, exceptionGuardTypes, exceptionGuardTypes);
    }

    private void addAndCount(
        DexType type, Reference2IntOpenHashMap<DexType> counts, Set<DexType> arraySet) {
      DexType baseType = type.getBaseType();
      if (baseType.isClassType()) {
        if (type.isIdenticalTo(baseType)) {
          counts.addTo(baseType, 1);
        } else {
          arraySet.add(baseType);
        }
      }
    }

    private void add(DexType type, Set<DexType> set, Set<DexType> arraySet) {
      DexType baseType = type.getBaseType();
      if (baseType.isClassType()) {
        if (type.isIdenticalTo(baseType)) {
          set.add(baseType);
        } else {
          arraySet.add(baseType);
        }
      }
    }
  }
}
