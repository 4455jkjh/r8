#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import json
import os
import sys

# Add the tools directory to python path to import defines and jdk
TOOLS_DIR = os.path.abspath(os.path.dirname(__file__))
sys.path.insert(0, TOOLS_DIR)

import defines
import jdk


def main():
    vscode_dir = os.path.join(defines.REPO_ROOT, '.vscode')
    settings_path = os.path.join(vscode_dir, 'settings.json')

    # Resolve absolute paths
    try:
        jdk17_home = jdk.GetJdk17Home()
    except Exception as e:
        print('Error resolving JDK 17 home: %s' % e)
        # Fallback to manual resolution
        jdk17_root = os.path.join(defines.REPO_ROOT, 'third_party', 'openjdk',
                                  'jdk-17')
        if defines.IsLinux():
            jdk17_home = os.path.join(jdk17_root, 'linux')
        elif defines.IsOsX():
            jdk17_home = os.path.join(jdk17_root, 'osx', 'Contents', 'Home')
        else:
            jdk17_home = os.path.join(jdk17_root, 'windows')

    gradle_home = os.path.join(defines.REPO_ROOT, 'third_party', 'gradle')

    settings = {
        "java.jdt.ls.java.home":
            jdk17_home,
        "java.jdt.ls.vmargs":
            "-XX:+UseParallelGC -XX:GCTimeRatio=4 -XX:AdaptiveSizePolicyWeight=90 -Dsun.zip.disableMemoryMapping=true -Xmx8G -Xms100m -Xlog:disable",
        "java.import.gradle.java.home":
            jdk17_home,
        "java.import.gradle.home":
            gradle_home,
        "java.import.gradle.wrapper.enabled":
            False,
        "gradle.java.home":
            jdk17_home,
        "gradle.home":
            gradle_home,
        "java.autobuild.enabled":
            False,
        "java.configuration.runtimes": [{
            "name": "JavaSE-17",
            "path": jdk17_home,
            "default": True
        }]
    }

    if not os.path.exists(vscode_dir):
        os.makedirs(vscode_dir)

    # Write or update settings.json
    existing_settings = {}
    if os.path.exists(settings_path):
        try:
            with open(settings_path, 'r') as f:
                existing_settings = json.load(f)
        except Exception:
            # If file is invalid or empty, start fresh
            pass

    # Merge settings
    existing_settings.update(settings)

    with open(settings_path, 'w') as f:
        json.dump(existing_settings, f, indent=2)
        f.write('\n')

    print('Updated settings in %s.' % settings_path)
    return 0


if __name__ == '__main__':
    sys.exit(main())
