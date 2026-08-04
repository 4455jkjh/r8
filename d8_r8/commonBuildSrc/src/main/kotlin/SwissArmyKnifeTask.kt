// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

@CacheableTask
public abstract class SwissArmyKnifeTask : DefaultTask() {
  @get:Inject public abstract val execOperations: ExecOperations

  @get:Classpath public abstract val swissArmyKnifeClasspath: ConfigurableFileCollection

  @get:Input public abstract val compiler: Property<String>

  @get:[Optional Classpath]
  public abstract val inputFiles: ConfigurableFileCollection

  @get:[Optional Classpath]
  public abstract val inputNoResFiles: ConfigurableFileCollection

  @get:OutputFile public abstract val outputFile: RegularFileProperty

  @get:Input public abstract val extraArgs: ListProperty<String>

  @TaskAction
  public fun relocate() {
    execOperations.javaexec {
      classpath = swissArmyKnifeClasspath
      jvmArgs = listOf("-Xmx8g", "-ea")
      mainClass.set("com.android.tools.r8.SwissArmyKnife")
      val myArgs = mutableListOf(compiler.get())
      myArgs.addAll(inputFiles.flatMap { listOf("--input", it.absolutePath) })
      myArgs.addAll(inputNoResFiles.flatMap { listOf("--input-no-res", it.absolutePath) })
      myArgs.add("--output")
      myArgs.add(outputFile.get().asFile.absolutePath)
      myArgs.addAll(extraArgs.get())
      args = myArgs
    }
  }
}
