// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

public abstract class DownloadAllDependenciesTask : DefaultTask() {

  // This marker file is used as the configuration tracked output of the task for dependency
  // purposes. If the downloaded files ever need to be handled through gradle directly, each output
  // file needs to be properly routed through the relevant configuration.
  @get:OutputFile public abstract val markerFile: RegularFileProperty

  private var _root: File? = null
  private var _thirdPartyDeps: List<ThirdPartyDependency>? = null

  @InputFiles
  public fun getInputFile(): List<File> {
    return _thirdPartyDeps!!.map { _root!!.resolve(it.sha1File) }
  }

  @OutputDirectories
  public fun getOutputDir(): List<File> {
    return _thirdPartyDeps!!.map { _root!!.resolve(it.path) }
  }

  @OutputFiles
  public fun getOutputFiles(): List<File> {
    return _thirdPartyDeps!!.map { _root!!.resolve(it.tarGzFile) }
  }

  @Inject protected abstract fun getWorkerExecutor(): WorkerExecutor?

  public fun setDependencies(root: File, thirdPartyDeps: List<ThirdPartyDependency>) {
    this._root = root
    this._thirdPartyDeps = thirdPartyDeps
  }

  @TaskAction
  public fun execute() {
    val noIsolation = getWorkerExecutor()!!.noIsolation()
    _thirdPartyDeps?.forEach {
      val root = _root!!
      val sha1File = root.resolve(it.sha1File)
      if (!sha1File.exists()) {
        throw RuntimeException("Missing sha1 file: $sha1File")
      }

      val tarGzFile = root.resolve(it.tarGzFile)
      val outputDir = root.resolve(it.path)
      val successFile = root.resolve(it.successFile)

      if (shouldDownload(outputDir, tarGzFile, sha1File, successFile)) {
        println("Downloading $it")
        noIsolation.submit(RunDownload::class.java) {
          type.set(it.type)
          this.sha1File.set(sha1File)
          this.outputDir.set(outputDir)
          this.successFile.set(successFile)
          this.tarGzFile.set(tarGzFile)
          this.root.set(root)
        }
      }
    }
    markerFile.get().asFile.writeText("done")
  }

  public interface RunDownloadParameters : WorkParameters {
    public val type: Property<DependencyType>
    public val sha1File: RegularFileProperty
    public val outputDir: RegularFileProperty
    public val successFile: RegularFileProperty
    public val tarGzFile: RegularFileProperty
    public val root: RegularFileProperty
  }

  public abstract class RunDownload : WorkAction<RunDownloadParameters> {
    override fun execute() {
      val sha1File = parameters.sha1File.asFile.get()
      val outputDir = parameters.outputDir.asFile.get()
      val successFile = parameters.successFile.asFile.get()

      if (successFile.exists()) {
        if (!successFile.delete()) {
          throw IOException("Failed to delete old success marker file: ${successFile.absolutePath}")
        }
      }

      if (outputDir.exists()) {
        if (!outputDir.deleteRecursively()) {
          throw IOException("Failed to delete old dependency directory: ${outputDir.absolutePath}")
        }
      }

      downloadDependency(parameters, sha1File)

      if (!successFile.createNewFile()) {
        throw IOException("Failed to create success marker file: ${successFile.absolutePath}")
      }
    }

    private fun downloadDependency(parameters: RunDownloadParameters, sha1File: File) {
      when (parameters.type.get()) {
        DependencyType.GOOGLE_STORAGE -> {
          downloadFromGoogleStorage(parameters, sha1File)
        }
        DependencyType.X20 -> {
          downloadFromX20(parameters, sha1File)
        }
      }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadFromGoogleStorage(parameters: RunDownloadParameters, sha1File: File) {
      val args = listOf("-n", "-b", "r8-deps", "-s", "-u", sha1File.toString())
      if (OperatingSystem.current().isWindows) {
        val command: MutableList<String> = ArrayList()
        command.add("download_from_google_storage.bat")
        command.addAll(args)
        runProcess(parameters, ProcessBuilder().command(command))
      } else {
        runProcess(
          parameters,
          ProcessBuilder()
            .command(
              "bash",
              "-c",
              "download_from_google_storage " + java.lang.String.join(" ", args),
            ),
        )
      }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadFromX20(parameters: RunDownloadParameters, sha1File: File) {
      if (OperatingSystem.current().isWindows) {
        throw RuntimeException("Downloading from x20 unsupported on windows")
      }
      runProcess(
        parameters,
        ProcessBuilder().command("bash", "-c", "tools/download_from_x20.py $sha1File"),
      )
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun runProcess(parameters: RunDownloadParameters, builder: ProcessBuilder) {
      builder.directory(parameters.root.asFile.get())
      val command = java.lang.String.join(" ", builder.command())
      val p = builder.start()
      val exit = p.waitFor()
      if (exit != 0) {
        throw IOException(
          "Process failed for $command\n" +
            BufferedReader(InputStreamReader(p.errorStream, StandardCharsets.UTF_8))
              .lines()
              .collect(Collectors.joining("\n"))
        )
      }
    }
  }

  private companion object {
    private fun shouldDownload(
      outputDir: File,
      tgzFile: File,
      sha1File: File,
      successFile: File,
    ): Boolean {
      if (
        outputDir.exists() &&
          outputDir.isDirectory &&
          successFile.exists() &&
          tgzFile.exists() &&
          sha1File.lastModified() <= successFile.lastModified()
      ) {
        return false
      }
      return true
    }
  }
}
