// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.MathMembers;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.AffectedValues;
import java.util.Set;

public class JavaLangMathMethodOptimizer extends StatelessLibraryMethodModelCollection
    implements MethodOptimizerCapabilities {

  private final AppView<?> appView;
  private final DexItemFactory factory;
  private final MathMembers mathMembers;

  JavaLangMathMethodOptimizer(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.mathMembers = factory.mathMembers;
  }

  @Override
  public AppView<?> getAppView() {
    return appView;
  }

  @Override
  public DexType getType() {
    return factory.mathType;
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
    switch (Character.toLowerCase(singleTargetReference.getName().getFirstByteAsChar())) {
      case 'a':
        if (singleTargetReference.isIdenticalTo(mathMembers.absDouble)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::abs);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.absFloat)) {
          optimizeFloatToFloatFunction(code, instructionIterator, invoke, Math::abs);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.absInt)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Math::abs);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.absLong)) {
          optimizeLongToLongFunction(code, instructionIterator, invoke, Math::abs);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.acos)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::acos);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.addExactInt)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Math::addExact);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.addExactLong)) {
          optimizeLongLongToLongFunction(code, instructionIterator, invoke, Math::addExact);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.asin)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::asin);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.atan)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::atan);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.atan2)) {
          optimizeDoubleDoubleToDoubleFunction(code, instructionIterator, invoke, Math::atan2);
        }
        break;
      case 'c':
        if (singleTargetReference.isIdenticalTo(mathMembers.cbrt)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::cbrt);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.ceil)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::ceil);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.copySignDouble)) {
          optimizeDoubleDoubleToDoubleFunction(code, instructionIterator, invoke, Math::copySign);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.copySignFloat)) {
          optimizeFloatFloatToFloatFunction(code, instructionIterator, invoke, Math::copySign);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.cos)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::cos);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.cosh)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::cosh);
        }
        break;
      case 'd':
        if (singleTargetReference.isIdenticalTo(mathMembers.decrementExactInt)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Math::decrementExact);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.decrementExactLong)) {
          optimizeLongToLongFunction(code, instructionIterator, invoke, Math::decrementExact);
        }
        break;
      case 'e':
        if (singleTargetReference.isIdenticalTo(mathMembers.exp)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::exp);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.expm1)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::expm1);
        }
        break;
      case 'f':
        if (singleTargetReference.isIdenticalTo(mathMembers.floor)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::floor);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.floorDivInt)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Math::floorDiv);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.floorDivLong)) {
          optimizeLongLongToLongFunction(code, instructionIterator, invoke, Math::floorDiv);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.floorModInt)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Math::floorMod);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.floorModLong)) {
          optimizeLongLongToLongFunction(code, instructionIterator, invoke, Math::floorMod);
        }
        break;
      case 'g':
        if (singleTargetReference.isIdenticalTo(mathMembers.getExponentDouble)) {
          optimizeDoubleToIntFunction(code, instructionIterator, invoke, Math::getExponent);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.getExponentFloat)) {
          optimizeFloatToIntFunction(code, instructionIterator, invoke, Math::getExponent);
        }
        break;
      case 'h':
        if (singleTargetReference.isIdenticalTo(mathMembers.hypot)) {
          optimizeDoubleDoubleToDoubleFunction(code, instructionIterator, invoke, Math::hypot);
        }
        break;
      case 'i':
        if (singleTargetReference.isIdenticalTo(mathMembers.IEEEremainder)) {
          optimizeDoubleDoubleToDoubleFunction(
              code, instructionIterator, invoke, Math::IEEEremainder);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.incrementExactInt)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Math::incrementExact);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.incrementExactLong)) {
          optimizeLongToLongFunction(code, instructionIterator, invoke, Math::incrementExact);
        }
        break;
      case 'l':
        if (singleTargetReference.isIdenticalTo(mathMembers.log)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::log);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.log10)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::log10);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.log1p)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::log1p);
        }
        break;
      case 'm':
        if (singleTargetReference.isIdenticalTo(mathMembers.maxDouble)) {
          optimizeDoubleDoubleToDoubleFunction(code, instructionIterator, invoke, Math::max);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.maxFloat)) {
          optimizeFloatFloatToFloatFunction(code, instructionIterator, invoke, Math::max);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.maxInt)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Math::max);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.maxLong)) {
          optimizeLongLongToLongFunction(code, instructionIterator, invoke, Math::max);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.minDouble)) {
          optimizeDoubleDoubleToDoubleFunction(code, instructionIterator, invoke, Math::min);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.minFloat)) {
          optimizeFloatFloatToFloatFunction(code, instructionIterator, invoke, Math::min);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.minInt)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Math::min);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.minLong)) {
          optimizeLongLongToLongFunction(code, instructionIterator, invoke, Math::min);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.multiplyExactInt)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Math::multiplyExact);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.multiplyExactLong)) {
          optimizeLongLongToLongFunction(code, instructionIterator, invoke, Math::multiplyExact);
        }
        break;
      case 'n':
        if (singleTargetReference.isIdenticalTo(mathMembers.negateExactInt)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Math::negateExact);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.negateExactLong)) {
          optimizeLongToLongFunction(code, instructionIterator, invoke, Math::negateExact);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.nextAfterDouble)) {
          optimizeDoubleDoubleToDoubleFunction(code, instructionIterator, invoke, Math::nextAfter);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.nextAfterFloat)) {
          optimizeFloatDoubleToFloatFunction(code, instructionIterator, invoke, Math::nextAfter);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.nextDownDouble)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::nextDown);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.nextDownFloat)) {
          optimizeFloatToFloatFunction(code, instructionIterator, invoke, Math::nextDown);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.nextUpDouble)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::nextUp);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.nextUpFloat)) {
          optimizeFloatToFloatFunction(code, instructionIterator, invoke, Math::nextUp);
        }
        break;
      case 'p':
        if (singleTargetReference.isIdenticalTo(mathMembers.pow)) {
          optimizeDoubleDoubleToDoubleFunction(code, instructionIterator, invoke, Math::pow);
        }
        break;
      case 'r':
        if (singleTargetReference.isIdenticalTo(mathMembers.rint)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::rint);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.roundDouble)) {
          optimizeDoubleToLongFunction(code, instructionIterator, invoke, Math::round);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.roundFloat)) {
          optimizeFloatToIntFunction(code, instructionIterator, invoke, Math::round);
        }
        break;
      case 's':
        if (singleTargetReference.isIdenticalTo(mathMembers.scalbDouble)) {
          optimizeDoubleIntToDoubleFunction(code, instructionIterator, invoke, Math::scalb);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.scalbFloat)) {
          optimizeFloatIntToFloatFunction(code, instructionIterator, invoke, Math::scalb);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.signumDouble)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::signum);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.signumFloat)) {
          optimizeFloatToFloatFunction(code, instructionIterator, invoke, Math::signum);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.sin)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::sin);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.sinh)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::sinh);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.sqrt)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::sqrt);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.subtractExactInt)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Math::subtractExact);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.subtractExactLong)) {
          optimizeLongLongToLongFunction(code, instructionIterator, invoke, Math::subtractExact);
        }
        break;
      case 't':
        if (singleTargetReference.isIdenticalTo(mathMembers.tan)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::tan);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.tanh)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::tanh);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.toDegrees)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::toDegrees);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.toIntExact)) {
          optimizeLongToIntFunction(code, instructionIterator, invoke, Math::toIntExact);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.toRadians)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::toRadians);
        }
        break;
      case 'u':
        if (singleTargetReference.isIdenticalTo(mathMembers.ulpDouble)) {
          optimizeDoubleToDoubleFunction(code, instructionIterator, invoke, Math::ulp);
        } else if (singleTargetReference.isIdenticalTo(mathMembers.ulpFloat)) {
          optimizeFloatToFloatFunction(code, instructionIterator, invoke, Math::ulp);
        }
        break;
      default:
        break;
    }
    return instructionIterator;
  }
}
