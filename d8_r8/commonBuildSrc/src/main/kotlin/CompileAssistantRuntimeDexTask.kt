// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

@CacheableTask
public abstract class CompileAssistantRuntimeDexTask : DefaultTask() {
  @get:Inject public abstract val execOperations: ExecOperations

  @get:Classpath public abstract val d8Classpath: ConfigurableFileCollection

  @get:Classpath public abstract val assistantJar: ConfigurableFileCollection

  @get:OutputFile public abstract val runtimeDex: RegularFileProperty

  @TaskAction
  public fun compile() {
    val outDex = runtimeDex.get().asFile
    execOperations.javaexec {
      classpath = d8Classpath
      mainClass.set("com.android.tools.r8.D8")
      args =
        listOf(
          "--no-desugaring",
          "--min-api",
          "1",
          "--output",
          temporaryDir.absolutePath,
          assistantJar.singleFile.absolutePath,
        )
    }
    val generatedDex = File(temporaryDir, "classes.dex")
    Files.copy(generatedDex.toPath(), outDex.toPath(), StandardCopyOption.REPLACE_EXISTING)
  }
}
