// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

public interface DownloadWorkParameters : WorkParameters {
  public val sha1File: RegularFileProperty
  public val outputDir: DirectoryProperty
  public val successFile: RegularFileProperty
  public val dependencyType: Property<DependencyType>
  public val rootDir: Property<File>
}

public abstract class DownloadWorkAction : WorkAction<DownloadWorkParameters> {
  override fun execute() {
    val sha1File = parameters.sha1File.asFile.get()
    val outputDir = parameters.outputDir.asFile.get()
    val successFile = parameters.successFile.asFile.get()
    val root = parameters.rootDir.get()

    if (successFile.exists()) {
      successFile.delete()
    }
    if (outputDir.exists()) {
      outputDir.deleteRecursively()
    }

    when (parameters.dependencyType.get()) {
      DependencyType.GOOGLE_STORAGE -> {
        downloadFromGoogleStorage(sha1File, root)
      }
      DependencyType.X20 -> {
        downloadFromX20(sha1File, root)
      }
    }
    successFile.createNewFile()
  }

  @Throws(IOException::class, InterruptedException::class)
  private fun downloadFromGoogleStorage(sha1File: File, root: File) {
    val args = listOf("-n", "-b", "r8-deps", "-s", "-u", sha1File.toString())
    if (OperatingSystem.current().isWindows) {
      val command: MutableList<String> = ArrayList()
      command.add("download_from_google_storage.bat")
      command.addAll(args)
      runProcess(ProcessBuilder().command(command), root)
    } else {
      runProcess(
        ProcessBuilder()
          .command(
            "bash",
            "-c",
            "download_from_google_storage " + java.lang.String.join(" ", args),
          ),
        root,
      )
    }
  }

  @Throws(IOException::class, InterruptedException::class)
  private fun downloadFromX20(sha1File: File, root: File) {
    if (OperatingSystem.current().isWindows) {
      throw RuntimeException("Downloading from x20 unsupported on windows")
    }
    runProcess(ProcessBuilder().command("bash", "-c", "tools/download_from_x20.py $sha1File"), root)
  }

  @Throws(IOException::class, InterruptedException::class)
  private fun runProcess(builder: ProcessBuilder, root: File) {
    builder.directory(root)
    builder.redirectErrorStream(true)
    val command = java.lang.String.join(" ", builder.command())
    val p = builder.start()
    val exit = p.waitFor()
    if (exit != 0) {
      throw IOException(
        "Process failed for $command\n" +
          BufferedReader(InputStreamReader(p.inputStream, StandardCharsets.UTF_8))
            .lines()
            .collect(Collectors.joining("\n"))
      )
    }
  }
}

public abstract class DownloadDependency : DefaultTask() {

  @get:InputFile public abstract val sha1File: RegularFileProperty

  @get:OutputDirectory public abstract val outputDir: DirectoryProperty

  @get:OutputFile public abstract val tarGzFile: RegularFileProperty

  @get:OutputFile public abstract val successFile: RegularFileProperty

  @get:Input public abstract val dependencyType: Property<DependencyType>

  @get:Inject public abstract val workerExecutor: WorkerExecutor

  @TaskAction
  public fun execute() {
    val sha1File = this.sha1File.asFile.get()
    val outputDir = this.outputDir.asFile.get()
    val tarGzFile = this.tarGzFile.asFile.get()
    val successFile = this.successFile.asFile.get()

    if (!sha1File.exists()) {
      throw RuntimeException("Missing sha1 file: $sha1File")
    }
    if (!shouldExecute(outputDir, tarGzFile, sha1File, successFile)) {
      return
    }

    workerExecutor.noIsolation().submit(DownloadWorkAction::class.java) {
      this.sha1File.set(this@DownloadDependency.sha1File)
      this.outputDir.set(this@DownloadDependency.outputDir)
      this.successFile.set(this@DownloadDependency.successFile)
      this.dependencyType.set(this@DownloadDependency.dependencyType)
      this.rootDir.set(project.getRoot())
    }
  }
}

private fun shouldExecute(
  outputDir: File,
  tarGzFile: File,
  sha1File: File,
  successFile: File,
): Boolean {
  if (
    outputDir.exists() &&
      tarGzFile.exists() &&
      successFile.exists() &&
      sha1File.lastModified() <= successFile.lastModified()
  ) {
    return false
  }
  return true
}
