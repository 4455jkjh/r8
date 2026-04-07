// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult

public class TestConfigurationHelper {

  public companion object {

    private data class ResultSinkInfo(val address: String, val authToken: String)

    private val resultSinkInfo: ResultSinkInfo? by lazy {
      val luciContextPath = System.getenv("LUCI_CONTEXT") ?: return@lazy null
      try {
        val content = File(luciContextPath).readText()
        val json = Gson().fromJson(content, JsonObject::class.java)
        val resultSink = json.getAsJsonObject("result_sink") ?: return@lazy null
        val address = resultSink.get("address")?.asString ?: return@lazy null
        val authToken = resultSink.get("auth_token")?.asString ?: return@lazy null
        ResultSinkInfo(address, authToken)
      } catch (e: Exception) {
        null
      }
    }

    private fun retrace(
      rootDir: File,
      r8jar: File,
      mappingFile: File,
      exception: Throwable,
      printObfuscatedStacktraces: Boolean,
    ): String {
      val out = StringBuilder()
      val header = "RETRACED STACKTRACE: " + System.currentTimeMillis()
      out.append("\n--------------------------------------\n")
      out.append("${header}\n")
      out.append("--------------------------------------\n")
      val retracePath = rootDir.resolveAll("tools", "retrace.py")
      val command =
        mutableListOf(
          "python3",
          retracePath.toString(),
          "--quiet",
          "--map",
          mappingFile.toString(),
          "--r8jar",
          r8jar.toString(),
        )
      val process = ProcessBuilder(command).start()
      process.outputStream.use { exception.printStackTrace(PrintStream(it)) }
      process.outputStream.close()
      val processCompleted = process.waitFor(20L, TimeUnit.SECONDS) && process.exitValue() == 0
      out.append(process.inputStream.bufferedReader().use { it.readText() })
      if (!processCompleted) {
        out.append(command.joinToString(" ") + "\n")
        out.append("ERROR DURING RETRACING: " + System.currentTimeMillis() + "\n")
        out.append(process.errorStream.bufferedReader().use { it.readText() })
      }
      if (printObfuscatedStacktraces || !processCompleted) {
        out.append("\n\n--------------------------------------\n")
        out.append("OBFUSCATED STACKTRACE\n")
        out.append("--------------------------------------\n")
        var baos = ByteArrayOutputStream()
        exception.printStackTrace(PrintStream(baos, true, StandardCharsets.UTF_8))
        out.append(baos.toString())
      }
      return out.toString()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun reportToResultSink(
      test: Test,
      desc: TestDescriptor?,
      result: TestResult?,
      rootDir: File,
      isR8Lib: Boolean,
      r8Jar: File?,
      r8LibMappingFile: File?,
      printObfuscatedStacktraces: Boolean,
    ) {
      val info = resultSinkInfo ?: return
      if (desc == null || result == null || desc.className == null) return

      val testId = "${desc.className}.${desc.name}"
      val status =
        when (result.resultType) {
          TestResult.ResultType.SUCCESS -> "PASSED"
          TestResult.ResultType.FAILURE -> "FAILED"
          TestResult.ResultType.SKIPPED -> "SKIPPED"
          else -> "PRECLUDED"
        }
      val durationMs = result.endTime - result.startTime
      val duration = "${durationMs / 1000.0}s"

      val stackTraceStr: String? =
        if (result.resultType == TestResult.ResultType.FAILURE && result.exception != null) {
          val exception = result.exception as Throwable
          if (isR8Lib && r8Jar != null && r8LibMappingFile != null) {
            retrace(rootDir, r8Jar, r8LibMappingFile, exception, printObfuscatedStacktraces)
          } else {
            val baos = ByteArrayOutputStream()
            exception.printStackTrace(PrintStream(baos, true, StandardCharsets.UTF_8))
            baos.toString()
          }
        } else {
          null
        }

      val argumentString = desc.displayName.substringAfter('[').substringBeforeLast(']')
      val testIdStructuredObj =
        JsonObject().apply {
          addProperty("coarseName", desc.className?.substringBeforeLast(".") ?: "")
          addProperty("fineName", desc.className?.substringAfterLast(".") ?: "")
          add(
            "caseNameComponents",
            JsonArray().apply {
              add(desc.displayName.substringBefore('['))
              desc.displayName
                .substringAfter('[')
                .substringBeforeLast(']')
                // result_sink uses `:` to separate case name components.
                .replace(Regex(": ?"), "=")
                .split(", ")
                .forEach { add(it) }
            },
          )
        }

      val testResultObj =
        JsonObject().apply {
          addProperty("testId", testId)
          addProperty("statusV2", status)
          addProperty("duration", duration)
          add("testIdStructured", testIdStructuredObj)

          if (result.resultType == TestResult.ResultType.FAILURE) {
            add("failureReason", JsonObject().apply { addProperty("kind", "ORDINARY") })
          } else if (result.resultType == TestResult.ResultType.SKIPPED) {
            add(
              "skippedReason",
              JsonObject().apply { addProperty("kind", "DISABLED_AT_DECLARATION") },
            )
          }

          val summaryHtml = "<p>Arguments string: $argumentString</p>"
          if (stackTraceStr != null) {
            val artifactContent =
              JsonObject().apply {
                addProperty("contents", Base64.encode(stackTraceStr.encodeToByteArray()))
              }
            add(
              "artifacts",
              JsonObject().apply { add("artifact-content-in-request", artifactContent) },
            )
            addProperty(
              "summaryHtml",
              summaryHtml +
                "<p>Stack trace:</p>" +
                "<p><text-artifact artifact-id=\"artifact-content-in-request\"></p>",
            )
          } else {
            addProperty("summaryHtml", summaryHtml)
          }
        }

      val payloadObj =
        JsonObject().apply { add("testResults", JsonArray().apply { add(testResultObj) }) }

      try {
        val url = URL("http://${info.address}/prpc/luci.resultsink.v1.Sink/ReportTestResults")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "ResultSink ${info.authToken}")
        conn.outputStream.use {
          it.write(Gson().toJson(payloadObj).toByteArray(StandardCharsets.UTF_8))
        }
        conn.inputStream.use { it.readBytes() }
      } catch (e: Exception) {
        // Ignore
      }
    }

    public fun setupTestTask(test: Test, isR8Lib: Boolean, r8Jar: File?, r8LibMappingFile: File?) {
      // TODO(b/489058560) Enable when we have figured out re-running single test variants.
      // test.useJUnitPlatform()
      test.useJUnit()
      test.systemProperty("junit.jupiter.execution.parallel.enabled", "true")
      val project = test.project
      if (project.hasProperty("testfilter")) {
        val testFilter = project.property("testfilter").toString()
        test.filter.setFailOnNoMatchingTests(false)
        test.filter.setIncludePatterns(*(testFilter.split("|").toTypedArray()))
      }
      if (project.hasProperty("shard_count") && project.hasProperty("shard_number")) {
        val shardCount = project.property("shard_count").toString().toInt()
        val shardNumber = project.property("shard_number").toString().toInt()
        if (shardNumber < 0 || shardNumber >= shardCount) {
          throw org.gradle.api.GradleException(
            "Invalid shard_number $shardNumber for shard_count $shardCount. " +
              "shard_number must be non-negative and less than shard_count."
          )
        }
        println("NOTE: Running shard $shardNumber of $shardCount")
        test.exclude { element ->
          if (element.isDirectory) return@exclude false
          val path = element.path
          if (!path.endsWith(".class")) return@exclude false
          // We use the class name (path) to determine the shard.
          // Inner classes (e.g., MyTest$1.class) must fall into the same shard as the main class.
          val baseName = path.substringBefore("$").substringBefore(".class")
          Math.floorMod(baseName.hashCode(), shardCount) != shardNumber
        }
      }
      if (project.hasProperty("kotlin_compiler_dev")) {
        test.systemProperty("com.android.tools.r8.kotlincompilerdev", "1")
      }

      if (project.hasProperty("kotlin_compiler_old")) {
        test.systemProperty("com.android.tools.r8.kotlincompilerold", "1")
      }

      if (project.hasProperty("dex_vm") && project.property("dex_vm") != "default") {
        project.logger.info("NOTE: Running with non default vm: " + project.property("dex_vm"))
        test.systemProperty("dex_vm", project.property("dex_vm")!!)
      }

      // Forward runtime configurations for test parameters.
      if (project.hasProperty("runtimes")) {
        project.logger.info("NOTE: Running with runtimes: " + project.property("runtimes"))
        test.systemProperty("runtimes", project.property("runtimes")!!)
      }

      if (project.hasProperty("art_profile_rewriting_completeness_check")) {
        test.systemProperty(
          "com.android.tools.r8.artprofilerewritingcompletenesscheck",
          project.property("art_profile_rewriting_completeness_check")!!,
        )
      }

      if (project.hasProperty("disable_assertions")) {
        test.enableAssertions = false
      }

      // Forward project properties into system properties.
      listOf(
          "local_development",
          "slow_tests",
          "desugar_jdk_json_dir",
          "desugar_jdk_libs",
          "test_dir",
          "command_cache_dir",
          "command_cache_stats_dir",
        )
        .forEach {
          val propertyName = it
          if (project.hasProperty(propertyName)) {
            project.property(propertyName)?.let { v -> test.systemProperty(propertyName, v) }
          }
        }

      if (project.hasProperty("no_internal")) {
        test.exclude("com/android/tools/r8/internal/**")
      }

      if (project.hasProperty("only_internal")) {
        test.include("com/android/tools/r8/internal/**")
      }

      // Exclude sanity checks on lib jars when running without R8 lib,
      // since we don't build processkeepruleslib.jar with test.py --no-r8lib.
      if (!isR8Lib) {
        test.exclude("com/android/tools/r8/processkeeprules/sanitychecks/**")
      }

      if (project.hasProperty("no_arttests")) {
        test.exclude("com/android/tools/r8/art/**")
      }

      if (project.hasProperty("test_xmx")) {
        test.maxHeapSize = project.property("test_xmx")!!.toString()
      } else {
        test.maxHeapSize = "4G"
      }

      val printTimes = project.hasProperty("print_times")
      val oneLinePerTest = project.hasProperty("one_line_per_test")
      val hasUpdateTestTimestamp = project.hasProperty("update_test_timestamp")
      val rootDir = project.getRoot()
      val printObfuscatedStacktraces = project.hasProperty("print_obfuscated_stacktraces")

      if (isR8Lib || oneLinePerTest || hasUpdateTestTimestamp) {
        val updateTestTimestampPath =
          if (hasUpdateTestTimestamp) {
            project.property("update_test_timestamp")!!.toString()
          } else {
            null
          }
        test.addTestListener(
          object : TestListener {
            val testTimes = mutableMapOf<TestDescriptor?, Long>()
            val maxPrintTimesCount = 200

            override fun beforeSuite(desc: TestDescriptor?) {}

            override fun afterSuite(desc: TestDescriptor?, result: TestResult?) {
              if (printTimes) {
                // desc.parent == null when we are all done
                if (desc?.parent == null) {
                  testTimes
                    .toList()
                    .sortedByDescending { it.second }
                    .take(maxPrintTimesCount)
                    .forEach { println("${it.first} took: ${it.second}") }
                }
              }
            }

            override fun beforeTest(desc: TestDescriptor?) {
              if (oneLinePerTest) {
                println("Start executing ${desc}")
              }
              if (printTimes) {
                testTimes[desc] = Date().getTime()
              }
            }

            override fun afterTest(desc: TestDescriptor?, result: TestResult?) {
              if (oneLinePerTest) {
                println("Done executing ${desc} with result: ${result?.resultType}")
              }
              if (printTimes) {
                testTimes[desc] = Date().getTime() - testTimes[desc]!!
              }
              if (updateTestTimestampPath != null) {
                File(updateTestTimestampPath).writeText(Date().getTime().toString())
              }
              if (result?.resultType == TestResult.ResultType.FAILURE && result.exception != null) {
                val exception = result.exception as Throwable
                if (isR8Lib) {
                  println(
                    retrace(
                      rootDir,
                      r8Jar!!,
                      r8LibMappingFile!!,
                      exception,
                      printObfuscatedStacktraces,
                    )
                  )
                } else {
                  val baos = ByteArrayOutputStream()
                  exception.printStackTrace(PrintStream(baos, true, StandardCharsets.UTF_8))
                  println(baos)
                }
              }
              reportToResultSink(
                test,
                desc,
                result,
                rootDir,
                isR8Lib,
                r8Jar,
                r8LibMappingFile,
                printObfuscatedStacktraces,
              )
            }
          }
        )
      } else {
        test.addTestListener(
          object : TestListener {

            override fun beforeSuite(desc: TestDescriptor?) {}

            override fun afterSuite(desc: TestDescriptor?, result: TestResult?) {}

            override fun beforeTest(desc: TestDescriptor?) {}

            override fun afterTest(desc: TestDescriptor?, result: TestResult?) {
              if (result?.resultType == TestResult.ResultType.FAILURE && result.exception != null) {
                val exception = result.exception as Throwable
                val baos = ByteArrayOutputStream()
                exception.printStackTrace(PrintStream(baos, true, StandardCharsets.UTF_8))
                println(baos)
              }
              reportToResultSink(
                test,
                desc,
                result,
                rootDir,
                isR8Lib,
                r8Jar,
                r8LibMappingFile,
                printObfuscatedStacktraces,
              )
            }
          }
        )
      }

      val userDefinedCoresPerFork = System.getenv("R8_GRADLE_CORES_PER_FORK")
      val processors = Runtime.getRuntime().availableProcessors()
      // See https://docs.gradle.org/current/dsl/org.gradle.api.tasks.testing.Test.html.
      if (!userDefinedCoresPerFork.isNullOrEmpty()) {
        test.maxParallelForks = processors.div(userDefinedCoresPerFork.toInt())
      } else {
        // On work machines this seems to give the best test execution time (without freezing).
        test.maxParallelForks = maxOf(processors.div(8), 1)
        // On low cpu count machines (bots) we under subscribe, so increase the count.
        if (processors == 32) {
          test.maxParallelForks = 15
        }
      }
    }
  }
}
