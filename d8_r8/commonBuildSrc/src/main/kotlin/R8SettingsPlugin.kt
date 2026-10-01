// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import androidx.build.gradle.gcpbuildcache.GcpBuildCache
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.apply

public class R8SettingsPlugin : Plugin<Settings> {
  override fun apply(settings: Settings) {
    settings.apply(plugin = "androidx.build.gradle.gcpbuildcache")
    fun enableBuildCache() {
      settings.buildCache {
        remote(GcpBuildCache::class.java) {
          projectId = "r8bot-265908"
          bucketName = "r8-build-cache"
          isPush =
            System.getenv().containsKey("R8_BOT_COMPILE_ONLY") ||
              System.getenv().containsKey("R8_BOT_SIZE")
        }
      }
    }
    if (System.getenv("SWARMING_BOT_ID") != null) {
      enableBuildCache()
    } else {
      val uplinkLinux = File("/usr/bin/uplink-helper")
      val uplinkMac = File("/usr/local/bin/uplink-helper")
      if (uplinkLinux.exists() || uplinkMac.exists()) {
        // We are on a Google machine, enable remote cache automatically
        enableBuildCache()
      }
    }
  }
}
