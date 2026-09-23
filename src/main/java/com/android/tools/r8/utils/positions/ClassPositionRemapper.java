// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.positions;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SourcePosition;
import com.android.tools.r8.kotlin.KotlinInlineMethodMap;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser.KotlinSourceDebugExtensionParserResult;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.KotlinSourceDebugExtensionCollection;
import com.android.tools.r8.utils.internal.collections.Pair;

// PositionRemapper is a stateful function which takes a position (represented by a
// DexDebugPositionState) and returns a remapped Position.
public interface ClassPositionRemapper {

  MethodPositionRemapper createMethodPositionRemapper();

  class IdentityPositionRemapper
      implements AppPositionRemapper, ClassPositionRemapper, MethodPositionRemapper {

    @Override
    public ClassPositionRemapper createClassPositionRemapper(DexProgramClass clazz) {
      return this;
    }

    @Override
    public MethodPositionRemapper createMethodPositionRemapper() {
      return this;
    }

    @Override
    public Pair<Position, Position> createRemappedPosition(Position position) {
      assert position.getOutlineCallee() == null : "Cannot identity-map outline calls: " + position;
      return new Pair<>(position, position);
    }

    @Override
    public void setNextOptimizedLineNumber(int nextOptimizedLineNumber) {
      // Intentionally empty.
    }
  }

  class OptimizingPositionRemapper implements AppPositionRemapper, ClassPositionRemapper {

    private final boolean sequentialLines;

    OptimizingPositionRemapper(InternalOptions options) {
      // Sequential lines are better for smaller debug info.
      // Offset lines are better for smaller mapping files.
      sequentialLines = options.isGeneratingDex();
    }

    @Override
    public ClassPositionRemapper createClassPositionRemapper(DexProgramClass clazz) {
      return this;
    }

    @Override
    public MethodPositionRemapper createMethodPositionRemapper() {
      return sequentialLines
          ? new SequentialMethodPositionRemapper()
          : new OffsetMethodPositionRemapper();
    }

    private abstract static class OptimizingMethodPositionRemapper
        implements MethodPositionRemapper {

      protected int nextOptimizedLineNumber = 0;

      @Override
      public Pair<Position, Position> createRemappedPosition(Position position) {
        advanceNextOptimizedLineNumber(position);
        Position newPosition =
            position
                .builderWithCopy()
                .setLine(nextOptimizedLineNumber)
                .setCallerPosition(null)
                .build();
        return new Pair<>(position, newPosition);
      }

      protected abstract void advanceNextOptimizedLineNumber(Position position);

      @Override
      public void setNextOptimizedLineNumber(int nextOptimizedLineNumber) {
        assert 1 <= nextOptimizedLineNumber : "Invalid line number: " + nextOptimizedLineNumber;
        // -1 to neutralize the advancement before the next assignment.
        this.nextOptimizedLineNumber = nextOptimizedLineNumber - 1;
      }
    }

    /** Re-numbers lines, but matches the original skips (e.g. 12, 13, 15 becomes 1, 2, 4). */
    private static class OffsetMethodPositionRemapper extends OptimizingMethodPositionRemapper {

      private DexMethod previousMethod = null;
      private int previousSourceLine = -1;

      @Override
      protected void advanceNextOptimizedLineNumber(Position position) {
        assert position.getMethod() != null : "Position has no method: " + position;
        if (position.getMethod().isIdenticalTo(previousMethod)) {
          // Follow the original increments to produce more compact mapping entries.
          assert previousSourceLine >= 0 : "Negative previous line: " + previousSourceLine;
          int lineIncrement = position.getLine() - previousSourceLine;
          if (0 < lineIncrement) {
            nextOptimizedLineNumber += lineIncrement;
          } else {
            nextOptimizedLineNumber++;
          }
        } else {
          nextOptimizedLineNumber++;
        }
        previousSourceLine = position.getLine();
        previousMethod = position.getMethod();
      }
    }

    private static class SequentialMethodPositionRemapper extends OptimizingMethodPositionRemapper {
      @Override
      protected void advanceNextOptimizedLineNumber(Position position) {
        nextOptimizedLineNumber++;
      }
    }
  }

  class KotlinInlineFunctionAppPositionRemapper implements AppPositionRemapper {

    private final AppPositionRemapper baseRemapper;
    private final DexItemFactory factory;
    private final KotlinInlineMethodMap kotlinInlineMethodMap;
    private final KotlinSourceDebugExtensionCollection kotlinSourceDebugExtensions;

    KotlinInlineFunctionAppPositionRemapper(
        AppView<?> appView,
        AppPositionRemapper baseRemapper,
        KotlinSourceDebugExtensionCollection kotlinSourceDebugExtensions) {
      this.baseRemapper = baseRemapper;
      this.factory = appView.dexItemFactory();
      this.kotlinInlineMethodMap = appView.getKotlinInlineMethodMap();
      assert this.kotlinInlineMethodMap != null;
      this.kotlinSourceDebugExtensions = kotlinSourceDebugExtensions;
    }

    @Override
    public ClassPositionRemapper createClassPositionRemapper(DexProgramClass clazz) {
      ClassPositionRemapper baseClassRemapper = baseRemapper.createClassPositionRemapper(clazz);
      assert baseClassRemapper == baseRemapper;
      KotlinSourceDebugExtensionParserResult kotlinSourceDebugExtension =
          kotlinSourceDebugExtensions.get(clazz);
      return kotlinSourceDebugExtension != null
          ? new KotlinInlineFunctionClassPositionRemapper(
              baseClassRemapper, kotlinSourceDebugExtension)
          : baseClassRemapper;
    }

    class KotlinInlineFunctionClassPositionRemapper implements ClassPositionRemapper {

      private final ClassPositionRemapper baseRemapper;
      private final KotlinSourceDebugExtensionParserResult kotlinSourceDebugExtension;

      private KotlinInlineFunctionClassPositionRemapper(
          ClassPositionRemapper baseRemapper,
          KotlinSourceDebugExtensionParserResult kotlinSourceDebugExtension) {
        assert kotlinSourceDebugExtension != null;
        this.baseRemapper = baseRemapper;
        this.kotlinSourceDebugExtension = kotlinSourceDebugExtension;
      }

      @Override
      public MethodPositionRemapper createMethodPositionRemapper() {
        MethodPositionRemapper baseMethodRemapper = baseRemapper.createMethodPositionRemapper();
        return new KotlinInlineFunctionMethodPositionRemapper(baseMethodRemapper);
      }

      class KotlinInlineFunctionMethodPositionRemapper implements MethodPositionRemapper {

        private final MethodPositionRemapper baseRemapper;

        private KotlinInlineFunctionMethodPositionRemapper(MethodPositionRemapper baseRemapper) {
          this.baseRemapper = baseRemapper;
        }

        @Override
        public Pair<Position, Position> createRemappedPosition(Position position) {
          int line = position.getLine();
          var inlinedPosition = kotlinSourceDebugExtension.lookupInlinedPositionEntry(line);
          if (inlinedPosition == null) {
            return baseRemapper.createRemappedPosition(position);
          }
          int inlineeLineDelta = line - inlinedPosition.start;
          int originalInlineeLine = inlinedPosition.value.getRange().from + inlineeLineDelta;
          String binaryName = inlinedPosition.value.getSource().getPath();
          String clazzDescriptor = DescriptorUtils.getDescriptorFromClassInternalName(binaryName);
          DexType inlineeType = factory.createType(clazzDescriptor);
          DexMethod inlinee = kotlinInlineMethodMap.lookup(inlineeType, originalInlineeLine);
          if (inlinee == null) {
            return baseRemapper.createRemappedPosition(position);
          }
          if (!inlinee.equals(position.getMethod())) {
            // We have an inline from a different method than the current position.
            var calleePosition = kotlinSourceDebugExtension.lookupCalleePosition(line);
            if (calleePosition != null) {
              // Take the first line as the callee position
              int calleeLine = Math.max(0, calleePosition.getRange().from);
              position = position.builderWithCopy().setLine(calleeLine).build();
            }
            return baseRemapper.createRemappedPosition(
                SourcePosition.builder()
                    .setLine(originalInlineeLine)
                    .setMethod(inlinee)
                    .setCallerPosition(position)
                    .build());
          }
          // This is the same position, so we should really not mark this as an inline position.
          // Fall through to the default case.
          return baseRemapper.createRemappedPosition(position);
        }

        @Override
        public void setNextOptimizedLineNumber(int nextOptimizedLineNumber) {
          baseRemapper.setNextOptimizedLineNumber(nextOptimizedLineNumber);
        }
      }
    }
  }
}
