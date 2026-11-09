// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.gradle.process.ExecOperations

public class BenchmarkProcessRunner(
  private val execOperations: ExecOperations,
  private val jdkExecutable: String,
  private val repoRoot: File,
  private val keepAnnoRuntimePath: String,
  private val testDataLocation: File,
  private val testBaseDataLocation: File,
  private val testDepArgs: List<String>,
  private val iterations: Int?,
  private val warmupIterations: Int?,
  private val extraJvmArgs: List<String>,
  private val heapSize: String?,
  private val verbose: Boolean,
) {
  public fun run(
    job: BenchmarkJobSpec,
    classpathFiles: List<File>,
    workDir: File,
    outputFile: File?,
    workerId: Int?,
  ) {
    val heap =
      heapSize
        ?: when {
          "AGSA" in job.benchmark || "YouTube" in job.benchmark -> "32g"
          job.benchmark == "SystemUIAppGc" -> "1760m"
          else -> "8g"
        }

    val jvmArgs = buildList {
      add("-Xms$heap")
      add("-Xmx$heap")
      add("-XX:+TieredCompilation")
      add("-XX:TieredStopAtLevel=4")
      add("-DBENCHMARK_IGNORE_CODE_SIZE_DIFFERENCES")
      add("-DBUILD_PROP_KEEPANNO_RUNTIME_PATH=$keepAnnoRuntimePath")
      add("-DREPO_ROOT=${repoRoot.absolutePath}")
      add("-DTEST_DATA_LOCATION=${testDataLocation.absolutePath}")
      add("-DTESTBASE_DATA_LOCATION=${testBaseDataLocation.absolutePath}")
      addAll(testDepArgs)
      if (iterations != null) {
        add("-DBENCHMARK_ITERATIONS=$iterations")
      }
      if (warmupIterations != null) {
        add("-DBENCHMARK_WARMUP_ITERATIONS=$warmupIterations")
      }
      addAll(extraJvmArgs)
      if (outputFile != null) {
        add("-DBENCHMARK_OUTPUT=${outputFile.absolutePath}")
      }
    }

    if (verbose) {
      val cmdStr =
        buildList {
            add(jdkExecutable)
            addAll(jvmArgs)
            add("-cp")
            add(classpathFiles.joinToString(File.pathSeparator) { it.absolutePath })
            add("com.android.tools.r8.benchmarks.BenchmarkMainEntryRunner")
            add(job.benchmark)
            add(job.target)
          }
          .joinToString(" ")
      if (workerId != null) {
        println("Worker $workerId: $cmdStr")
      } else {
        println(cmdStr)
      }
    }

    execOperations.javaexec {
      executable = jdkExecutable
      workingDir = workDir
      classpath(classpathFiles)
      this.jvmArgs = jvmArgs
      mainClass.set("com.android.tools.r8.benchmarks.BenchmarkMainEntryRunner")
      args = listOf(job.benchmark, job.target)
    }
  }
}

public object BenchmarkWorkerPool {
  public fun execute(
    jobs: List<BenchmarkJobSpec>,
    workers: Int,
    action: (BenchmarkJobSpec, Int?) -> Unit,
  ) {
    if (jobs.isEmpty()) return

    val numWorkers = workers.coerceAtLeast(1)
    if (numWorkers == 1 || jobs.size == 1) {
      for (job in jobs) {
        action(job, null)
      }
      return
    }

    val aborted = AtomicBoolean(false)
    val nextWorkerId = AtomicInteger(1)
    val threadWorkerId = ThreadLocal.withInitial { nextWorkerId.getAndIncrement() }
    val pool = Executors.newFixedThreadPool(numWorkers.coerceAtMost(jobs.size))

    try {
      val futures =
        jobs.map { job ->
          pool.submit(
            Callable {
              if (aborted.get()) return@Callable
              try {
                action(job, threadWorkerId.get())
              } catch (e: Throwable) {
                aborted.set(true)
                throw e
              }
            }
          )
        }
      for (future in futures) {
        try {
          future.get()
        } catch (e: Exception) {
          pool.shutdownNow()
          throw e.cause ?: e
        }
      }
    } finally {
      pool.shutdownNow()
      pool.awaitTermination(1, TimeUnit.MINUTES)
    }
  }
}
