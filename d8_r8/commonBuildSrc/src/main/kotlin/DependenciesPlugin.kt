// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import DependenciesPlugin.Companion.computeRoot
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.internal.DefaultJavaLanguageVersion
import org.gradle.nativeplatform.platform.OperatingSystem
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

public class DependenciesPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    // Setup all test tasks to listen after system properties passed in by test.py.
    val testTask = target.tasks.findByName("test") as Test?
    testTask?.configure(isR8Lib = false, r8Jar = null, r8LibPartitionMapFile = null)

    target.tasks.withType(Test::class.java).configureEach {
      outputs.doNotCacheIf("Test runs should not and cannot not be cached") { true }
    }
  }

  public companion object {
    public fun computeRoot(file: File): File {
      // The root folder name is setup dependent.
      // It is instead identified by being a folder with a child folder called 'd8_r8'.
      var current: File? = file
      while (current != null) {
        if (current.resolve("d8_r8").isDirectory) {
          return current
        } else {
          current = current.getParentFile()
        }
      }
      throw RuntimeException("Could not find root dir from $file")
    }
  }
}

public enum class Jdk(public val folder: String, public val version: Int) {
  // Only include LTS and latest non-LTS GA.
  JDK_8("jdk8", 8),
  JDK_9("openjdk-9.0.4", 9), // JDK-9 not LTS, but still used.
  JDK_11("jdk-11", 11),
  JDK_17("jdk-17", 17),
  JDK_21("jdk-21", 21),
  JDK_25("jdk-25", 25),
  JDK_27("jdk-27", 27),
}

public fun Test.configure(isR8Lib: Boolean, r8Jar: File?, r8LibPartitionMapFile: File? = null) {
  project.plugins.apply("org.gradle.test-retry")
  TestConfigurationHelper.setupTestTask(this, isR8Lib, r8Jar, r8LibPartitionMapFile)
}

public fun Project.getRoot(): File {
  return computeRoot(this.projectDir)
}

// See https://datatracker.ietf.org/doc/html/rfc4122#section-4.3 for the algorithm.
public fun uuid5(namespace: UUID, name: String): UUID {
  val md = MessageDigest.getInstance("SHA-1")
  md.update(uuidToBytes(namespace))
  md.update(name.encodeToByteArray())
  val sha1Bytes = md.digest()
  // Set version 5 (upper 4 bits of octet 6).
  sha1Bytes[6] = (sha1Bytes[6].toInt() and 0x0f).toByte()
  sha1Bytes[6] = (sha1Bytes[6].toInt() or 0x50).toByte()
  // Set two upper bits of octet 8 to 10.
  sha1Bytes[8] = (sha1Bytes[8].toInt() and 0x3f).toByte()
  sha1Bytes[8] = (sha1Bytes[8].toInt() or 0x80).toByte()
  return uuidFromBytes(sha1Bytes)
}

private fun uuidFromBytes(data: ByteArray): UUID {
  assert(data.size >= 16)
  return UUID(toNetworkOrder(data, 0), toNetworkOrder(data, 8))
}

private fun uuidToBytes(uuid: UUID): ByteArray {
  val result = ByteArray(16)
  fromNetworkByteOrder(uuid.mostSignificantBits, result, 0)
  fromNetworkByteOrder(uuid.leastSignificantBits, result, 8)
  return result
}

private fun toNetworkOrder(data: ByteArray, dataIndex: Int): Long {
  var result: Long = 0
  for (i in 0..7) result = result shl 8 or (data[dataIndex + i].toInt() and 0xff).toLong()
  return result
}

private fun fromNetworkByteOrder(value: Long, dest: ByteArray, destIndex: Int) {
  for (i in 0..7) dest[i + destIndex] = (value shr (7 - i) * 8 and 0xffL).toByte()
}

public fun File.resolveAll(vararg xs: String): File {
  var that = this
  for (x in xs) {
    that = that.resolve(x)
  }
  return that
}

public fun Project.getJavaHome(jdk: Jdk): File {
  val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
  var osFolder = "linux"
  if (os.isWindows) {
    osFolder = "windows"
  }
  if (os.isMacOsX) {
    osFolder = "osx/Contents/Home"
  }
  return getRoot().resolveAll("third_party", "openjdk", jdk.folder, osFolder)
}

public fun Project.getCompilerPath(jdk: Jdk): String {
  val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
  val binary = if (os.isWindows) "javac.exe" else "javac"
  return getJavaHome(jdk).resolveAll("bin", binary).toString()
}

private fun Project.getJavaPath(jdk: Jdk): String {
  val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
  val binary = if (os.isWindows) "java.exe" else "java"
  return getJavaHome(jdk).resolveAll("bin", binary).toString()
}

public fun Project.getJavaLauncher(jdk: Jdk): JavaLauncher {
  val installationPath = project.layout.projectDirectory.dir(getJavaHome(jdk).toString())
  val executablePath = project.layout.projectDirectory.file(getJavaPath(jdk))
  return object : JavaLauncher {
    override fun getMetadata(): JavaInstallationMetadata {
      return object : JavaInstallationMetadata {
        override fun getLanguageVersion(): JavaLanguageVersion {
          return DefaultJavaLanguageVersion.of(jdk.version)
        }

        override fun getJavaRuntimeVersion(): String {
          return jdk.name
        }

        override fun getJvmVersion(): String {
          return jdk.name
        }

        override fun getVendor(): String {
          return "vendor"
        }

        override fun getInstallationPath(): Directory {
          return installationPath
        }

        override fun isCurrentJvm(): Boolean {
          return false
        }
      }
    }

    override fun getExecutablePath(): RegularFile {
      return executablePath
    }
  }
}

public object JvmCompatibility {
  public const val release: Int = 11
}
