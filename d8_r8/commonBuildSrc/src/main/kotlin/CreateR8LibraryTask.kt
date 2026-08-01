// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI.create
import java.net.URLClassLoader
import java.nio.charset.Charset
import java.nio.file.FileSystems.newFileSystem
import java.nio.file.Files.deleteIfExists
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByName
import org.gradle.process.ExecOperations

public abstract class CreateR8LibraryTask : DefaultTask() {
  @get:Inject public abstract val execOperations: ExecOperations
  @get:Classpath public abstract val r8compilerClasspath: ConfigurableFileCollection
  @get:InputFile public abstract val inputJar: RegularFileProperty
  @get:InputFile public abstract val pgConfig: RegularFileProperty
  @get:Input public abstract val enableKeepAnnotations: Property<Boolean>
  @get:Input
  public val gitHeadSha: Provider<String> =
    project.providers.of(GitHeadShaSource::class.java) {
      parameters.workingDir.set(project.rootDir)
    }
  @get:Input public val javaVersion: Int = JvmCompatibility.release
  /** Represented by [javaVersion], we do not want to index entire java home */
  @get:Internal
  public val javaHome: Provider<Directory> =
    project.extensions
      .getByName<JavaToolchainService>("javaToolchains")
      .launcherFor { languageVersion.set(JavaLanguageVersion.of(JvmCompatibility.release)) }
      .map { it.metadata.installationPath }

  @get:OutputFile public abstract val outputJar: RegularFileProperty
  @get:OutputFile public abstract val outputPgConfig: RegularFileProperty
  @get:OutputFile public abstract val outputPgMap: RegularFileProperty
  @get:OutputFile public abstract val outputPartitionMap: RegularFileProperty

  /**
   * Sets up [outputJar], [outputPgConfig], [outputPgMap], and [outputPartitionMap] using [jarFile]
   * as a name basis.
   */
  public fun setOutputJarFile(jarFile: File) {
    outputJar.set(jarFile)
    outputPgConfig.set(File(jarFile.absolutePath + ".config"))
    outputPgMap.set(File(jarFile.absolutePath + ".map"))
    outputPartitionMap.set(File(jarFile.absolutePath + "_map.zip"))
  }

  @TaskAction
  public fun create() {
    val versionLabel = getVersionLabel(r8compilerClasspath.single())
    val r8Version = if (versionLabel == "main") gitHeadSha.get() else versionLabel

    execOperations.javaexec {
      classpath = r8compilerClasspath
      mainClass.set("com.android.tools.r8.R8")

      jvmArgs("-Xmx8g", "-ea")

      systemProperty("com.android.tools.r8.enableKeepAnnotations", enableKeepAnnotations.get())
      systemProperty("com.android.tools.r8.enableEmptyMemberRulesToDefaultInitRuleConversion", "0")
      systemProperty("com.android.tools.r8.tracereferences.obfuscateAllEnums", "true")

      val myArgs = mutableListOf(inputJar.get().asFile.absolutePath)
      myArgs.apply {
        add("--classfile")
        add("--map-id-template")
        add(r8Version)
        add("--source-file-template")
        add("R8_%MAP_ID_%MAP_HASH")
        add("--output")
        add(outputJar.get().asFile.absolutePath)
        add("--pg-conf-output")
        add(outputPgConfig.get().asFile.absolutePath)
        add("--pg-map-output")
        add(outputPgMap.get().asFile.absolutePath)
        add("--partition-map-output")
        add(outputPartitionMap.get().asFile.absolutePath)
        add("--lib")
        add(javaHome.get().asFile.absolutePath)
        add("--pg-conf")
        add(pgConfig.get().asFile.absolutePath)
      }
      args = myArgs
    }
    if (!enableKeepAnnotations.get()) { // delete api database if we do not enable keep annotations
      // The URI must be prefixed with "jar:" to tell the FileSystem provider to treat it as a
      // ZIP/JAR
      val uri = create("jar:${outputJar.get().asFile.toPath().toUri()}")
      val env = mapOf("create" to "false")
      newFileSystem(uri, env).use { zipfs ->
        val pathInZip = zipfs.getPath("resources/api_database.ser")
        deleteIfExists(pathInZip)
      }
    }
  }
}

private fun getVersionLabel(jarFile: File): String {
  val classLoader =
    URLClassLoader(
      arrayOf(jarFile.toURI().toURL()),
      // Pass null as the parent to prevent loading classes from the current Gradle/system classpath
      null,
    )

  try {
    val versionClass = classLoader.loadClass("com.android.tools.r8.Version")
    val labelField = versionClass.getField("LABEL")
    val labelValue = labelField.get(null) as String
    return labelValue
  } catch (e: ClassNotFoundException) {
    throw GradleException(
      "The class 'com.android.tools.r8.Version' was not found in the provided JAR.",
      e,
    )
  } catch (e: NoSuchFieldException) {
    throw GradleException("The field 'LABEL' does not exist in 'com.android.tools.r8.Version'.", e)
  } finally {
    classLoader.close()
  }
}

/** Provides HEAD SHA by calling git in [Parameters.workingDir]. */
public abstract class GitHeadShaSource : ValueSource<String, GitHeadShaSource.Parameters> {
  public interface Parameters : ValueSourceParameters {
    public val workingDir: DirectoryProperty
  }

  @get:Inject internal abstract val execOperations: ExecOperations

  override fun obtain(): String {
    val output = ByteArrayOutputStream()
    execOperations.exec {
      commandLine("git", "rev-parse", "HEAD")
      standardOutput = output
      workingDir = parameters.workingDir.get().asFile
    }
    return String(output.toByteArray(), Charset.defaultCharset()).trim()
  }
}
