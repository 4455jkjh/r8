#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import os
import subprocess
import sys

import gradle
import jdk
import utils

TOOLS_JAR = os.path.join(utils.REPO_ROOT, 'd8_r8', 'tools', 'build', 'libs',
                         'tools.jar')


def parse_options(argv):
    parser = argparse.ArgumentParser(description='Analyze APK.')
    parser.add_argument('--apk',
                        help='Path to the APK to analyze',
                        required=True)
    parser.add_argument('--no-build',
                        '--no_build',
                        help='Run without building first (default false)',
                        default=False,
                        action='store_true')
    return parser.parse_args(argv)


def main(argv):
    options = parse_options(argv)

    if not options.no_build:
        gradle.run_gradle(['r8', ':tools:jar'])

    if not os.path.exists(utils.R8_JAR):
        print(
            f"Error: Could not find {utils.R8_JAR}. Build failed or --no-build was specified incorrectly."
        )
        return 1
    if not os.path.exists(TOOLS_JAR):
        print(
            f"Error: Could not find {TOOLS_JAR}. Build failed or --no-build was specified incorrectly."
        )
        return 1

    java = jdk.GetJavaExecutable(jdk.GetDefaultJdkHome())
    classpath = [TOOLS_JAR, utils.R8_JAR]

    cmd = [
        java, '-cp',
        os.pathsep.join(classpath),
        'com.android.tools.r8.tools.apkanalyzer.ApkAnalyzer', options.apk
    ]

    res = subprocess.run(cmd)
    return res.returncode


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
