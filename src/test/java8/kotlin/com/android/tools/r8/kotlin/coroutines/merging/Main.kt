// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.coroutines.merging

import com.android.tools.r8.NeverClassInline
import com.android.tools.r8.NeverInline
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

// Saves the continuation instead of resuming inside suspendCoroutine, forcing each state machine
// to return COROUTINE_SUSPENDED and resume through invokeSuspend() from runCoroutine().
private var pendingContinuation: Continuation<String>? = null
private var resumeCount = 0

@NeverInline
suspend fun suspendWithNextValue(): String = suspendCoroutine { continuation ->
  pendingContinuation = continuation
}

fun runCoroutine(block: suspend () -> Unit) {
  block.startCoroutine(
    object : Continuation<Unit> {
      override val context: CoroutineContext = EmptyCoroutineContext

      override fun resumeWith(result: Result<Unit>) {
        result.getOrThrow()
      }
    }
  )
  while (pendingContinuation != null) {
    val next = pendingContinuation!!
    pendingContinuation = null
    next.resume("v${++resumeCount}")
  }
}

// Top-level suspend function: compiles to a ContinuationImpl with no final fields (no `this$0`
// receiver), so it cannot merge with Worker's member suspend functions.
@NeverInline
suspend fun topLevelSuspend(label: String) {
  val value = suspendWithNextValue()
  println("topLevel: $label, $value")
}

@NeverClassInline
class Worker(val name: String) {
  // Compiles to a ContinuationImpl with `final Worker this$0` and one suspension point.
  // Calls twoSuspends() passing its own continuation as the caller continuation; when merged with
  // twoSuspends(), the `continuation instanceof Worker$twoSuspends$1` check in twoSuspends() relies
  // on $r8$classId to allocate a fresh state machine instead of reusing oneSuspend's frame.
  @NeverInline
  suspend fun oneSuspend() {
    twoSuspends(name.length)
    println("oneSuspend: $name")
  }

  // Compiles to a ContinuationImpl with the same `final Worker this$0` field as oneSuspend(), so
  // the two classes can merge even though twoSuspends() spills extra non-final locals (`count: Int`
  // and `first: String`) across a second suspension point.
  @NeverInline
  suspend fun twoSuspends(count: Int) {
    val first = suspendWithNextValue()
    val second = suspendWithNextValue()
    println("twoSuspends: $name, $count, $first, $second")
  }
}

fun main(args: Array<String>) {
  val worker = Worker(if (args.isEmpty()) "worker" else args[0])
  val tag = if (args.isEmpty()) "tag" else args[0]

  // SuspendLambda capturing two final fields (`$worker` and `$tag`); cannot merge with the
  // single-capture SuspendLambdas below.
  val twoCapturesLambda: suspend () -> Unit = {
    worker.oneSuspend()
    topLevelSuspend(tag)
  }
  runCoroutine(twoCapturesLambda)

  // SuspendLambda capturing one final field (`$tag`) with one suspension point and no spilled
  // locals.
  val oneSuspendLambda: suspend () -> Unit = {
    val first = suspendWithNextValue()
    println("oneSuspendLambda: $tag, $first")
  }
  runCoroutine(oneSuspendLambda)

  // SuspendLambda capturing the same single final field (`$tag`), so it can merge with
  // oneSuspendLambda despite spilling extra non-final locals (`count: Int` and `first: String`)
  // across a second suspension point.
  val twoSuspendsLambda: suspend () -> Unit = {
    val first = suspendWithNextValue()
    val count = first.length
    val second = suspendWithNextValue()
    println("twoSuspendsLambda: $tag, $count, $first, $second")
  }
  runCoroutine(twoSuspendsLambda)
}
