// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.androidx.kt

import androidx.annotation.keep.UsesReflectionToConstruct
import kotlin.reflect.KClass
import kotlin.reflect.full.createType

class IntAndLongArgsConstructorsClassName {

  @UsesReflectionToConstruct(
    className = "com.android.tools.r8.keepanno.androidx.kt.KeptClass",
    params = [Int::class],
  )
  @UsesReflectionToConstruct(
    className = "com.android.tools.r8.keepanno.androidx.kt.KeptClass",
    params = [Long::class],
  )
  fun foo(clazz: KClass<KeptClass>?) {
    val intConstructor =
      clazz?.constructors?.first {
        it.parameters.size == 1 && it.parameters.first().type == Int::class.createType()
      }
    println(intConstructor)
    intConstructor?.call(1)
    val longConstructor =
      clazz?.constructors?.first {
        it.parameters.size == 1 && it.parameters.first().type == Long::class.createType()
      }
    println(longConstructor)
    longConstructor?.call(2L)
  }
}

fun main() {
  IntAndLongArgsConstructorsClassName().foo(if (System.nanoTime() > 0) KeptClass::class else null)
}
