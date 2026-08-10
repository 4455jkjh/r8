// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.AndroidGraphicsColorMembers;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.AffectedValues;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AndroidGraphicsColorMethodOptimizer extends StatelessLibraryMethodModelCollection
    implements MethodOptimizerCapabilities {

  private static final Map<String, Integer> COLOR_NAME_MAP = createColorNameMap();

  private final AppView<?> appView;
  private final DexItemFactory factory;
  private final AndroidGraphicsColorMembers colorMembers;

  AndroidGraphicsColorMethodOptimizer(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.colorMembers = factory.androidGraphicsColorMembers;
  }

  private static Map<String, Integer> createColorNameMap() {
    Map<String, Integer> map = new HashMap<>();
    map.put("black", 0xFF000000);
    map.put("darkgray", 0xFF444444);
    map.put("gray", 0xFF888888);
    map.put("lightgray", 0xFFCCCCCC);
    map.put("white", 0xFFFFFFFF);
    map.put("red", 0xFFFF0000);
    map.put("green", 0xFF00FF00);
    map.put("blue", 0xFF0000FF);
    map.put("yellow", 0xFFFFFF00);
    map.put("cyan", 0xFF00FFFF);
    map.put("magenta", 0xFFFF00FF);
    map.put("aqua", 0xFF00FFFF);
    map.put("fuchsia", 0xFFFF00FF);
    map.put("darkgrey", 0xFF444444);
    map.put("grey", 0xFF888888);
    map.put("lightgrey", 0xFFCCCCCC);
    map.put("lime", 0xFF00FF00);
    map.put("maroon", 0xFF800000);
    map.put("navy", 0xFF000080);
    map.put("olive", 0xFF808000);
    map.put("purple", 0xFF800080);
    map.put("silver", 0xFFC0C0C0);
    map.put("teal", 0xFF008080);
    return map;
  }

  @Override
  public AppView<?> getAppView() {
    return appView;
  }

  @Override
  public DexType getType() {
    return factory.androidGraphicsColorType;
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
    switch (singleTargetReference.getName().getFirstByteAsChar()) {
      case 'a':
        if (singleTargetReference.isIdenticalTo(colorMembers.alphaInt)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, color -> color >>> 24);
        } else if (singleTargetReference.isIdenticalTo(colorMembers.alphaLong)) {
          optimizeLongToFloatFunction(
              code,
              instructionIterator,
              invoke,
              color ->
                  ((color & 0x3fL) == 0L)
                      ? ((color >> 56) & 0xff) / 255.0f
                      : ((color >> 6) & 0x3ff) / 1023.0f);
        } else if (singleTargetReference.isIdenticalTo(colorMembers.argbInt)) {
          optimizeIntIntIntIntToIntFunction(
              code,
              instructionIterator,
              invoke,
              (a, r, g, b) -> (a << 24) | (r << 16) | (g << 8) | b);
        } else if (singleTargetReference.isIdenticalTo(colorMembers.argbFloat)) {
          optimizeFloatFloatFloatFloatToIntFunction(
              code,
              instructionIterator,
              invoke,
              (a, r, g, b) ->
                  ((int) (a * 255.0f + 0.5f) << 24)
                      | ((int) (r * 255.0f + 0.5f) << 16)
                      | ((int) (g * 255.0f + 0.5f) << 8)
                      | (int) (b * 255.0f + 0.5f));
        }
        break;
      case 'b':
        if (singleTargetReference.isIdenticalTo(colorMembers.blueInt)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, color -> color & 0xff);
        } else if (singleTargetReference.isIdenticalTo(colorMembers.blueLong)) {
          optimizeLongToFloatFunction(
              code,
              instructionIterator,
              invoke,
              color ->
                  ((color & 0x3fL) == 0L)
                      ? ((color >> 32) & 0xff) / 255.0f
                      : halfToFloat((short) ((color >> 16) & 0xffff)));
        }
        break;
      case 'g':
        if (singleTargetReference.isIdenticalTo(colorMembers.greenInt)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, color -> (color >> 8) & 0xff);
        } else if (singleTargetReference.isIdenticalTo(colorMembers.greenLong)) {
          optimizeLongToFloatFunction(
              code,
              instructionIterator,
              invoke,
              color ->
                  ((color & 0x3fL) == 0L)
                      ? ((color >> 40) & 0xff) / 255.0f
                      : halfToFloat((short) ((color >> 32) & 0xffff)));
        }
        break;
      case 'i':
        if (singleTargetReference.isIdenticalTo(colorMembers.isSrgb)) {
          optimizeLongToBooleanFunction(
              code,
              instructionIterator,
              invoke,
              AndroidGraphicsColorMethodOptimizer::evaluateIsSrgb);
        } else if (singleTargetReference.isIdenticalTo(colorMembers.isWideGamut)) {
          optimizeLongToBooleanFunction(
              code,
              instructionIterator,
              invoke,
              AndroidGraphicsColorMethodOptimizer::evaluateIsWideGamut);
        }
        break;
      case 'l':
        if (singleTargetReference.isIdenticalTo(colorMembers.luminanceInt)) {
          optimizeIntToFloatFunction(
              code,
              instructionIterator,
              invoke,
              color -> {
                double r = sRgbEotf(((color >> 16) & 0xff) / 255.0);
                double g = sRgbEotf(((color >> 8) & 0xff) / 255.0);
                double b = sRgbEotf((color & 0xff) / 255.0);
                return (float) ((0.2126 * r) + (0.7152 * g) + (0.0722 * b));
              });
        } else if (singleTargetReference.isIdenticalTo(colorMembers.luminanceLong)) {
          optimizeLongToFloatFunction(
              code,
              instructionIterator,
              invoke,
              color -> {
                if ((color & 0x3fL) != 0L) {
                  throw new IllegalArgumentException();
                }
                double r = sRgbEotf(((color >> 48) & 0xff) / 255.0);
                double g = sRgbEotf(((color >> 40) & 0xff) / 255.0);
                double b = sRgbEotf(((color >> 32) & 0xff) / 255.0);
                float lum = (float) ((0.2126 * r) + (0.7152 * g) + (0.0722 * b));
                return lum <= 0.0f ? 0.0f : (lum >= 1.0f ? 1.0f : lum);
              });
        }
        break;
      case 'p':
        if (singleTargetReference.isIdenticalTo(colorMembers.packInt)) {
          optimizeIntToLongFunction(
              code, instructionIterator, invoke, color -> (color & 0xffffffffL) << 32);
        } else if (singleTargetReference.isIdenticalTo(colorMembers.packFloat4)) {
          optimizeFloatFloatFloatFloatToLongFunction(
              code,
              instructionIterator,
              invoke,
              (r, g, b, a) -> {
                int argb =
                    ((int) (a * 255.0f + 0.5f) << 24)
                        | ((int) (r * 255.0f + 0.5f) << 16)
                        | ((int) (g * 255.0f + 0.5f) << 8)
                        | (int) (b * 255.0f + 0.5f);
                return (argb & 0xffffffffL) << 32;
              });
        } else if (singleTargetReference.isIdenticalTo(colorMembers.packFloat3)) {
          optimizeFloatFloatFloatToLongFunction(
              code,
              instructionIterator,
              invoke,
              (r, g, b) -> {
                int argb =
                    0xff000000
                        | ((int) (r * 255.0f + 0.5f) << 16)
                        | ((int) (g * 255.0f + 0.5f) << 8)
                        | (int) (b * 255.0f + 0.5f);
                return (argb & 0xffffffffL) << 32;
              });
        } else if (singleTargetReference.isIdenticalTo(colorMembers.parseColor)) {
          optimizeStringToIntFunction(
              code, instructionIterator, invoke, s -> parseColor(s.toString()));
        }
        break;
      case 'r':
        if (singleTargetReference.isIdenticalTo(colorMembers.redInt)) {
          optimizeIntToIntFunction(
              code, instructionIterator, invoke, color -> (color >> 16) & 0xff);
        } else if (singleTargetReference.isIdenticalTo(colorMembers.redLong)) {
          optimizeLongToFloatFunction(
              code,
              instructionIterator,
              invoke,
              color ->
                  ((color & 0x3fL) == 0L)
                      ? ((color >> 48) & 0xff) / 255.0f
                      : halfToFloat((short) ((color >> 48) & 0xffff)));
        } else if (singleTargetReference.isIdenticalTo(colorMembers.rgbInt)) {
          optimizeIntIntIntToIntFunction(
              code,
              instructionIterator,
              invoke,
              (r, g, b) -> 0xff000000 | (r << 16) | (g << 8) | b);
        } else if (singleTargetReference.isIdenticalTo(colorMembers.rgbFloat)) {
          optimizeFloatFloatFloatToIntFunction(
              code,
              instructionIterator,
              invoke,
              (r, g, b) ->
                  0xff000000
                      | ((int) (r * 255.0f + 0.5f) << 16)
                      | ((int) (g * 255.0f + 0.5f) << 8)
                      | (int) (b * 255.0f + 0.5f));
        }
        break;
      case 't':
        if (singleTargetReference.isIdenticalTo(colorMembers.toArgb)) {
          optimizeLongToIntFunction(
              code,
              instructionIterator,
              invoke,
              color -> {
                if ((color & 0x3fL) != 0L) {
                  throw new IllegalArgumentException();
                }
                return (int) (color >> 32);
              });
        }
        break;
      default:
        break;
    }
    return instructionIterator;
  }

  private static boolean evaluateIsSrgb(long color) {
    int id = (int) (color & 0x3fL);
    if (id == 0) {
      return true;
    }
    if (id > 0 && id <= 17) {
      return false;
    }
    throw new IllegalArgumentException("Unknown or invalid color space ID: " + id);
  }

  private static boolean evaluateIsWideGamut(long color) {
    int id = (int) (color & 0x3fL);
    switch (id) {
      case 0: // SRGB
      case 1: // LINEAR_SRGB
      case 4: // BT709
      case 8: // NTSC_1953
      case 9: // SMPTE_C
        return false;
      case 2: // EXTENDED_SRGB
      case 3: // LINEAR_EXTENDED_SRGB
      case 5: // BT2020
      case 6: // DCI_P3
      case 7: // DISPLAY_P3
      case 10: // ADOBE_RGB
      case 11: // PRO_PHOTO_RGB
      case 12: // ACES
      case 13: // ACESCG
      case 14: // CIE_XYZ
      case 15: // CIE_LAB
      case 16: // BT2020_HLG
      case 17: // BT2020_PQ
        return true;
      default:
        throw new IllegalArgumentException("Unknown or invalid color space ID: " + id);
    }
  }

  private static float halfToFloat(short h) {
    int bits = h & 0xffff;
    int s = bits & 0x8000;
    int e = (bits >> 10) & 0x1f;
    int m = bits & 0x3ff;
    int outSign = s << 16;
    if (e == 0) {
      if (m == 0) {
        return Float.intBitsToFloat(outSign);
      } else {
        while ((m & 0x400) == 0) {
          m <<= 1;
          e--;
        }
        e++;
        m &= ~0x400;
      }
    } else if (e == 31) {
      if (m == 0) {
        return Float.intBitsToFloat(outSign | 0x7f800000);
      } else {
        return Float.intBitsToFloat(outSign | 0x7f800000 | (m << 13));
      }
    }
    e = e + (127 - 15);
    m = m << 13;
    return Float.intBitsToFloat(outSign | (e << 23) | m);
  }

  private static double sRgbEotf(double c) {
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  }

  private static int parseColor(String colorString) {
    if (colorString != null && !colorString.isEmpty()) {
      if (colorString.charAt(0) == '#') {
        long color = Long.parseLong(colorString.substring(1), 16);
        if (colorString.length() == 7) {
          color |= 0xff000000L;
        } else if (colorString.length() != 9) {
          throw new IllegalArgumentException("Unknown color");
        }
        return (int) color;
      } else {
        Integer color = COLOR_NAME_MAP.get(colorString.toLowerCase(Locale.ROOT));
        if (color != null) {
          return color;
        }
      }
    }
    throw new IllegalArgumentException("Unknown color");
  }
}
