// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.filterisinstance

import com.android.tools.r8.NeverClassInline
import com.android.tools.r8.NeverInline

@NeverClassInline
class SingleA {
  @NeverInline fun msg(): String = "A"
}

@NeverClassInline
class SingleB {
  @NeverInline fun msg(): String = "B"
}

@NeverClassInline
class SingleC(val value: Int) {
  @NeverInline fun msg(): String = "C:$value"
}

@NeverClassInline
class SingleD(val value: Int) {
  @NeverInline fun msg(): String = "D:$value"
}

fun main() {
  val items: Array<Any> = arrayOf(SingleA(), SingleB(), SingleC(1), SingleD(2))
  println((items[0] as SingleA).msg())
  println((items[2] as SingleC).msg())
  println((items[3] as SingleD).msg())
  for (b in items.filterIsInstance(SingleB::class.java)) {
    println(b.msg())
  }
}
