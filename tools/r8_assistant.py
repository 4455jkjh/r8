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


def parse_options(argv):
    parser = argparse.ArgumentParser(description='Run R8 Assistant on an APK.',
                                     add_help=False)
    parser.add_argument('--no-build',
                        action='store_true',
                        default=False,
                        help='Do not build r8-assistantlib.jar before running')
    parser.add_argument('--worktree',
                        action='store_true',
                        default=False,
                        help='Gradle is running in a git worktree')
    return parser.parse_known_args(argv)


def main(argv):
    options, args = parse_options(argv)

    if not options.no_build:
        gradle_args = [utils.GRADLE_TASK_R8_ASSISTANT]
        utils.append_gradle_user_home_for_worktree(options.worktree, True,
                                                   gradle_args)
        gradle.run_gradle(gradle_args)

    if not os.path.exists(utils.R8_ASSISTANT_LIB_JAR):
        print(f'Error: Could not find {utils.R8_ASSISTANT_LIB_JAR}. '
              'Build failed or --no-build was specified.')
        return 1

    java = jdk.GetJavaExecutable(jdk.GetDefaultJdkHome())
    cmd = [java, '-jar', utils.R8_ASSISTANT_LIB_JAR] + args
    utils.PrintCmd(cmd)
    res = subprocess.run(cmd)
    return res.returncode


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
