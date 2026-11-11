// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.File
import java.io.Serializable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Locale
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.ExecOperations

public data class BenchmarkJobSpec(
  public val benchmark: String,
  public val target: String = "r8-full",
) : Serializable

@UntrackedTask(because = "Benchmarks must always run")
public abstract class RunBenchmarkTask : DefaultTask() {
  @get:Inject public abstract val execOperations: ExecOperations

  @get:InputFiles public abstract val benchmarkClasspath: ConfigurableFileCollection
  @get:InputFiles public abstract val keepAnnoRuntimeClasspath: ConfigurableFileCollection
  @get:InputFiles public abstract val testDataLocation: DirectoryProperty
  @get:InputFiles public abstract val testBaseDataLocation: DirectoryProperty

  @get:Internal public abstract val javaLauncher: Property<JavaLauncher>
  @get:Internal public abstract val testDepsEntries: ListProperty<TestDepEntry>
  @get:Internal public abstract val repoRoot: DirectoryProperty
  @get:Internal public abstract val jobs: ListProperty<BenchmarkJobSpec>
  @get:Internal public abstract val iterations: Property<Int>
  @get:Internal public abstract val warmupIterations: Property<Int>
  @get:Internal public abstract val heapSize: Property<String>
  @get:Internal public abstract val extraJvmArgs: ListProperty<String>
  @get:Internal public abstract val output: Property<File>
  @get:Internal public abstract val outputDir: Property<File>
  @get:Internal public abstract val tempDir: Property<File>
  @get:Internal public abstract val workers: Property<Int>
  @get:Internal public abstract val verbose: Property<Boolean>
  @get:Internal public abstract val requireCustomR8Jar: Property<Boolean>
  @get:Internal public abstract val customR8Jar: Property<File>

  public fun configureCommon(project: Project) {
    val rootDir = project.getRoot()
    val providers = project.providers
    val daemonJvmPropsFile =
      project.rootProject.layout.projectDirectory.file("gradle/gradle-daemon-jvm.properties")
    val javaToolchains = project.extensions.getByType(JavaToolchainService::class.java)

    repoRoot.set(rootDir)
    javaLauncher.set(createBenchmarkJavaLauncher(providers, javaToolchains, daemonJvmPropsFile))
    jobs.set(createJobsProvider(providers))
    iterations.set(providers.gradleProperty("benchmark_iterations").map { it.toInt() })
    warmupIterations.set(providers.gradleProperty("warmup").map { it.toInt() })
    heapSize.set(providers.gradleProperty("heap_size"))
    output.set(resolveRequiredPathProperty(providers, "output", rootDir))
    outputDir.set(resolveRequiredPathProperty(providers, "output_dir", rootDir))
    tempDir.set(resolveRequiredPathProperty(providers, "temp", rootDir))
    workers.set(providers.gradleProperty("workers").map { it.toInt() }.orElse(1))
    verbose.set(providers.gradleProperty("verbose").map { true }.orElse(false))
    requireCustomR8Jar.convention(false)
    customR8Jar.set(resolveRequiredPathProperty(providers, "r8_jar", rootDir))
    configureExtraJvmArgs(extraJvmArgs, providers, rootDir)
  }

  public fun configureCustomR8Jar() {
    requireCustomR8Jar.set(true)
  }

  @TaskAction
  public fun execute() {
    val jobList = jobs.get()
    val classpath = buildList {
      if (requireCustomR8Jar.get()) {
        add(customR8Jar.orNull ?: throw GradleException("Missing required property -Pr8_jar"))
      }
      addAll(benchmarkClasspath.files)
    }
    for (cpFile in classpath) {
      if (!cpFile.exists()) {
        throw GradleException("Benchmark classpath entry does not exist: ${cpFile.absolutePath}")
      }
    }
    val testDataDir = testDataLocation.get().asFile
    if (!testDataDir.isDirectory) {
      throw GradleException("Test data directory does not exist: ${testDataDir.absolutePath}")
    }
    val testBaseDataDir = testBaseDataLocation.get().asFile
    if (!testBaseDataDir.isDirectory) {
      throw GradleException(
        "Testbase data directory does not exist: ${testBaseDataDir.absolutePath}"
      )
    }

    val configuredTempDir = tempDir.orNull
    val managedTempDir =
      if (configuredTempDir == null) Files.createTempDirectory("r8-benchmark-").toFile() else null
    val baseTempDir = configuredTempDir?.apply { mkdirs() } ?: managedTempDir!!

    try {
      val keepAnnoRuntimePath =
        extractClassesPaths("keepanno" + File.separator, keepAnnoRuntimeClasspath.asPath)
      val runner =
        BenchmarkProcessRunner(
          execOperations = execOperations,
          jdkExecutable = javaLauncher.get().executablePath.asFile.absolutePath,
          repoRoot = repoRoot.get().asFile,
          keepAnnoRuntimePath = keepAnnoRuntimePath,
          testDataLocation = testDataDir,
          testBaseDataLocation = testBaseDataDir,
          testDepArgs = TestDepsCommandLineArgumentProvider(testDepsEntries).asArguments().toList(),
          iterations = iterations.orNull,
          warmupIterations = warmupIterations.orNull,
          extraJvmArgs = extraJvmArgs.get(),
          heapSize = heapSize.orNull,
          verbose = verbose.get(),
        )

      if (!outputDir.isPresent) {
        if (jobList.size != 1) {
          throw GradleException("Multiple benchmarks require -Poutput_dir")
        }
        val outFile = output.orNull?.apply { parentFile?.mkdirs() }
        runner.run(jobList.single(), classpath, baseTempDir, outFile, null)
      } else {
        if (output.isPresent) {
          throw GradleException("Cannot specify both -Poutput and -Poutput_dir")
        }
        val outRootDir = outputDir.get().apply { mkdirs() }
        BenchmarkWorkerPool.execute(jobList, workers.get()) { job, workerId ->
          val jobTempDir =
            Files.createTempDirectory(baseTempDir.toPath(), "bench-${job.benchmark}-${job.target}-")
              .toFile()
          try {
            val startNanos = System.nanoTime()
            val iterResultFile = File(jobTempDir, "result.json")
            runner.run(job, classpath, jobTempDir, iterResultFile, workerId)
            val durationSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0

            val targetDir = File(outRootDir, "${job.benchmark}/${job.target}").apply { mkdirs() }
            iterResultFile.copyTo(File(targetDir, "result.json"), overwrite = true)
            File(targetDir, "duration_s")
              .writeText(
                String.format(Locale.ROOT, "%.1f", durationSeconds),
                StandardCharsets.UTF_8,
              )
          } finally {
            jobTempDir.deleteRecursively()
          }
        }
      }
    } finally {
      managedTempDir?.deleteRecursively()
    }
  }

  public companion object {
    private fun createBenchmarkJavaLauncher(
      providers: ProviderFactory,
      javaToolchains: JavaToolchainService,
      daemonJvmPropsFile: RegularFile,
    ): Provider<JavaLauncher> {
      val daemonJvmVersion =
        providers.fileContents(daemonJvmPropsFile).asText.map { text ->
          text
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("toolchainVersion=") }
            ?.substringAfter("=")
            ?.trim()
            ?.toInt()
            ?: throw GradleException(
              "Missing toolchainVersion in gradle/gradle-daemon-jvm.properties"
            )
        }
      return javaToolchains.launcherFor {
        languageVersion.set(daemonJvmVersion.map(JavaLanguageVersion::of))
      }
    }

    private fun createJobsProvider(providers: ProviderFactory): Provider<List<BenchmarkJobSpec>> {
      val defaultTarget = providers.gradleProperty("target").orElse("r8-full")
      val benchmarksCsv = providers.gradleProperty("benchmarks").orElse("")
      val singleBenchmark = providers.gradleProperty("benchmark").orElse("")
      return defaultTarget.zip(
        benchmarksCsv.zip(singleBenchmark) { csv, single -> csv to single }
      ) { target, (csv, single) ->
        parseJobs(target, csv, single)
      }
    }

    private fun resolveRequiredPathProperty(
      providers: ProviderFactory,
      propName: String,
      rootDir: File,
    ): Provider<File> =
      providers.gradleProperty(propName).map {
        require(it.isNotEmpty()) { "Gradle property '$propName' must not be empty" }
        rootDir.resolve(it)
      }

    private fun configureExtraJvmArgs(
      extraJvmArgs: ListProperty<String>,
      providers: ProviderFactory,
      rootDir: File,
    ) {
      fun flagArg(propName: String, arg: String): Provider<List<String>> =
        providers.gradleProperty(propName).map { listOf(arg) }.orElse(emptyList())

      fun pathArg(propName: String, format: (String) -> String): Provider<List<String>> =
        resolveRequiredPathProperty(providers, propName, rootDir)
          .map { listOf(format(it.absolutePath)) }
          .orElse(emptyList())

      extraJvmArgs.addAll(flagArg("enable_assertions", "-ea"))
      extraJvmArgs.addAll(
        flagArg(
          "debug_agent",
          "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005",
        )
      )
      extraJvmArgs.addAll(pathArg("build_output") { "-DBENCHMARK_BUILD_OUTPUT=$it" })
      extraJvmArgs.addAll(
        pathArg("dump_heap_to_directory") { "-Dcom.android.tools.r8.dumpheaptodirectory=$it" }
      )
      extraJvmArgs.addAll(
        pathArg("dump_keep_radius_to_directory") {
          "-Dcom.android.tools.r8.dumpkeepradiustodirectory=$it"
        }
      )
      extraJvmArgs.addAll(
        pathArg("dump_keep_radius_html_to_directory") {
          "-Dcom.android.tools.r8.dumpkeepradiushtmltodirectory=$it"
        }
      )
      extraJvmArgs.addAll(
        pathArg("dump_trace_to_directory") { "-Dcom.android.tools.r8.dumptracetodirectory=$it" }
      )
      extraJvmArgs.addAll(
        pathArg("dump_gc_log_to_file") {
          "-Xlog:gc*,gc+phases=debug:file=$it:time,uptime,level,tags"
        }
      )
      extraJvmArgs.addAll(
        pathArg("dump_jfr_to_file") {
          "-XX:StartFlightRecording=disk=true,dumponexit=true,filename=$it,settings=profile"
        }
      )
    }

    public fun parseJobs(
      defaultTarget: String,
      benchmarksCsv: String,
      singleBenchmark: String,
    ): List<BenchmarkJobSpec> {
      if (benchmarksCsv.isNotBlank() && singleBenchmark.isNotBlank()) {
        throw GradleException("Cannot specify both -Pbenchmarks and -Pbenchmark")
      }
      if (benchmarksCsv.isNotBlank()) {
        val parsed =
          benchmarksCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { item ->
              val parts = item.split(":", limit = 2)
              BenchmarkJobSpec(
                benchmark = parts[0],
                target = if (parts.size > 1) parts[1] else defaultTarget,
              )
            }
        if (parsed.isEmpty()) {
          throw GradleException("No benchmarks found in -Pbenchmarks='$benchmarksCsv'")
        }
        return parsed
      }
      if (singleBenchmark.isNotBlank()) {
        return listOf(BenchmarkJobSpec(benchmark = singleBenchmark, target = defaultTarget))
      }
      throw GradleException("Missing benchmark specification; pass -Pbenchmark or -Pbenchmarks")
    }
  }
}
