// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.AffectedValues;
import java.util.Set;

public class AndroidGraphicsImageFormatMethodOptimizer extends StatelessLibraryMethodModelCollection
    implements MethodOptimizerCapabilities {

  private static final int RGB_565 = 4;
  private static final int YV12 = 0x32315659;
  private static final int Y8 = 0x20203859;
  private static final int Y16 = 0x20363159;
  private static final int YCBCR_P010 = 0x36;
  private static final int YCBCR_P210 = 0x3c;
  private static final int NV16 = 0x10;
  private static final int NV21 = 0x11;
  private static final int YUY2 = 0x14;
  private static final int YUV_420_888 = 0x23;
  private static final int YUV_422_888 = 0x27;
  private static final int YUV_444_888 = 0x28;
  private static final int FLEX_RGB_888 = 0x29;
  private static final int FLEX_RGBA_8888 = 0x2A;
  private static final int RAW_SENSOR = 0x20;
  private static final int RAW10 = 0x25;
  private static final int RAW12 = 0x26;
  private static final int RAW14 = 0x2C;
  private static final int DEPTH16 = 0x44363159;
  private static final int RAW_DEPTH = 0x1002;
  private static final int RAW_DEPTH10 = 0x1003;

  private final AppView<?> appView;
  private final DexItemFactory factory;

  public AndroidGraphicsImageFormatMethodOptimizer(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  @Override
  public AppView<?> getAppView() {
    return appView;
  }

  @Override
  public DexType getType() {
    return factory.androidGraphicsImageFormatType;
  }

  @Override
  public InstructionListIterator optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      AffectedValues affectedValues,
      Set<BasicBlock> blocksToRemove) {
    DexMethod singleTargetReference = singleTarget.getReference();
    DexItemFactory.AndroidGraphicsImageFormatMembers members =
        factory.androidGraphicsImageFormatMembers;
    if (singleTargetReference.isIdenticalTo(members.getBitsPerPixel)) {
      optimizeIntToIntFunction(
          code,
          instructionIterator,
          invoke,
          AndroidGraphicsImageFormatMethodOptimizer::evaluateGetBitsPerPixel);
    }
    return instructionIterator;
  }

  private static int evaluateGetBitsPerPixel(int format) {
    switch (format) {
      case RGB_565:
        return 16;
      case NV16:
        return 16;
      case YUY2:
        return 16;
      case YV12:
        return 12;
      case Y8:
        return 8;
      case Y16:
      case DEPTH16:
        return 16;
      case NV21:
        return 12;
      case YUV_420_888:
        return 12;
      case YUV_422_888:
        return 16;
      case YUV_444_888:
        return 24;
      case FLEX_RGB_888:
        return 24;
      case FLEX_RGBA_8888:
        return 32;
      case RAW_DEPTH:
      case RAW_SENSOR:
        return 16;
      case YCBCR_P010:
        return 24;
      case YCBCR_P210:
        return 32;
      case RAW_DEPTH10:
      case RAW10:
        return 10;
      case RAW12:
        return 12;
      case RAW14:
        return 14;
      default:
        throw new NoModelingForArgumentsException();
    }
  }
}
