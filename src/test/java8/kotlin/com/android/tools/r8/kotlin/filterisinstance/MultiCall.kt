// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.filterisinstance

import com.android.tools.r8.NeverClassInline
import com.android.tools.r8.NeverInline

@NeverClassInline
class First {
  @NeverInline fun msg(): String = "First"
}

@NeverClassInline
class Second {
  @NeverInline fun msg(): String = "Second"
}

open class Super {
  @NeverInline open fun kind(): String = "Super"
}

@NeverClassInline
class Sub : Super() {
  @NeverInline override fun kind(): String = "Sub"
}

@NeverClassInline
class Unrelated {
  @NeverInline fun kind(): String = "Unrelated"
}

open class MergedSuper {
  @NeverInline open fun tag(): String = "MergedSuper"
}

@NeverClassInline
class MergedSub : MergedSuper() {
  @NeverInline override fun tag(): String = "MergedSub"
}

@NeverClassInline
class SiblingA(val value: Int) {
  @NeverInline fun msg(): String = "SiblingA:$value"
}

@NeverClassInline
class SiblingB(val value: Int) {
  @NeverInline fun msg(): String = "SiblingB:$value"
}

@NeverClassInline
class Reified {
  @NeverInline fun msg(): String = "Reified"
}

fun main() {
  val items: Array<Any> =
    arrayOf(First(), Second(), Sub(), Unrelated(), MergedSub(), SiblingA(1), SiblingB(2), Reified())
  for (first in items.filterIsInstance(First::class.java)) {
    println(first.msg())
  }
  val seconds = items.filterIsInstanceTo(ArrayList(), Second::class.java)
  for (second in seconds) {
    println(second.msg())
  }
  for (s in items.filterIsInstance(Super::class.java)) {
    println(s.kind())
  }
  for (reified in items.filterIsInstance<Reified>()) {
    println(reified.msg())
  }
  println((items[3] as Unrelated).kind())
  println((items[4] as MergedSuper).tag())
  println((items[5] as SiblingA).msg())
  println((items[6] as SiblingB).msg())
}
