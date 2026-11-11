#!/usr/bin/env python3
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Run ProGuard, Google's internal version

import os
import subprocess
import sys

import jdk
import utils

# Internal constants, these should not be used outside this script.
# Use the friendly utility methods below.
PG_DIR = os.path.join(utils.THIRD_PARTY, 'proguard')
DEFAULT = 'pg7'
DEFAULT_ALIAS = 'pg'
VERSIONS = {
    'pg7':
        os.path.join(PG_DIR, 'proguard-7.0.0', 'lib', 'proguard.jar'),
    'pg_internal':
        os.path.join(PG_DIR, 'proguard_internal_159423826',
                     'ProGuard_deploy.jar'),
}
# Add alias for the default version.
VERSIONS[DEFAULT_ALIAS] = VERSIONS[DEFAULT]


# Get versions sorted (nice for argument lists)
def get_versions():
    versions = list(VERSIONS.keys())
    versions.sort()
    return versions


def is_valid_version(version):
    return version in VERSIONS


def get_validated_version(version):
    if not is_valid_version(version):
        raise ValueError("Invalid PG version: '%s'" % version)
    return version


def get_jar(version=DEFAULT):
    return VERSIONS[get_validated_version(version)]


def get_retrace_jar(version=DEFAULT):
    if version == 'pg_internal':
        raise ValueError("No retrace in internal distribution")
    return get_jar().replace('proguard.jar', 'retrace.jar')


def get_cmd(args, version=DEFAULT, jvm_args=None):
    cmd = []
    if jvm_args:
        cmd.extend(jvm_args)
    cmd.extend([
        jdk.GetJavaExecutable(jdk.GetDefaultJdkHome()), '-jar',
        get_jar(version)
    ])
    cmd.extend(args)
    return cmd


def run(args,
        version=DEFAULT,
        track_memory_file=None,
        stdout=None,
        stderr=None):
    cmd = []
    if track_memory_file:
        cmd.extend(['tools/track_memory.sh', track_memory_file])
    cmd.extend(get_cmd(args, version))
    utils.PrintCmd(cmd)
    subprocess.call(cmd, stdout=stdout, stderr=stderr)


def main():
    run(sys.argv[1:])


if __name__ == '__main__':
    sys.exit(main())
