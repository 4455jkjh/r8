// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.filterisinstance

import com.android.tools.r8.NeverClassInline
import com.android.tools.r8.NeverInline

interface Marker

@NeverClassInline
class Impl : Marker {
  @NeverInline fun name(): String = "Impl"
}

@NeverClassInline
class NonImpl {
  @NeverInline fun name(): String = "NonImpl"
}

fun main() {
  val impl = Impl()
  val nonImpl = NonImpl()
  println(impl.name())
  println(nonImpl.name())
  val items: Array<Any> = arrayOf(impl, nonImpl)
  println(items.filterIsInstance(Marker::class.java).size)
}
