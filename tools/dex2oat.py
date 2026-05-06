#!/usr/bin/env python3
# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import optparse
import os
import subprocess
import sys

import utils

LINUX_DIR = os.path.join(utils.TOOLS_DIR, 'linux')

LATEST = '12.0.0'

VERSIONS = [
    '12.0.0',
    # TODO(b/258170524): Fix the broken dex2oat versions.
    # 'default',
    # '9.0.0',
    # '8.1.0',
    # '7.0.0',
    '6.0.1',
    # '5.1.1',
]

DIRS = {
    '12.0.0': 'host/art-12.0.0-beta4',
    'default': 'art',
    '9.0.0': 'art-9.0.0',
    '8.1.0': 'art-8.1.0',
    '7.0.0': 'art-7.0.0',
    '6.0.1': 'art-6.0.1',
    '5.1.1': 'art-5.1.1',
}

PRODUCTS = {
    '12.0.0': 'redfin',
    'default': 'angler',
    '9.0.0': 'marlin',
    '8.1.0': 'marlin',
    '7.0.0': 'angler',
    '6.0.1': 'angler',
    '5.1.1': 'mako',
}

ARCHS = {
    '12.0.0': 'x86_64',
    'default': 'arm64',
    '9.0.0': 'arm64',
    '8.1.0': 'arm64',
    '7.0.0': 'arm64',
    '6.0.1': 'arm64',
    '5.1.1': 'arm',
}

VERBOSE_OPTIONS = [
    'verifier',
    'compiler',
    'gc',
    'jit',
    'jni',
    'class',
    'all',
]

BOOT_IMAGE = {'12.0.0': 'apex/art_boot_images/javalib/boot.art'}


def ParseOptions():
    parser = optparse.OptionParser()
    parser.add_option('--version',
                      help='Version of dex2oat. (defaults to latest: ' +
                      LATEST + ').',
                      default=LATEST)
    parser.add_option(
        '--device',
        help='Run dex2oat on this device (this is passed as the -s SERIAL.')
    parser.add_option('--all',
                      help='Run dex2oat on all possible versions',
                      default=False,
                      action='store_true')
    parser.add_option(
        '--output',
        help=
        'Where to place the output oat (defaults to no output / temp file).',
        default=None)
    parser.add_option('--verbose',
                      help='Enable verbose dex2oat logging.',
                      choices=VERBOSE_OPTIONS,
                      default=None)
    parser.add_option('--dump-cfg',
                      help='Dump the CFG to this file.',
                      default=None)
    return parser.parse_args()


def Main():
    (options, args) = ParseOptions()
    if len(args) != 1:
        print("Can only take a single dex/zip/jar/apk file as input.")
        return 1
    if options.version not in VERSIONS and not os.path.exists(
            os.path.join(utils.THIRD_PARTY, 'dex2oat', options.version)):
        print("Unknown version %s" % options.version)
        return 1
    if options.dump_cfg and options.version in VERSIONS:
        print(
            "--dump-cfg is only supported for standalone dex2oat versions (not host ART)."
        )
        return 1
    if (options.device):
        return run_device_dex2oat(options, args)
    else:
        return run_host_dex2oat(options, args)


def run_host_dex2oat(options, args):
    if options.all and options.output:
        print("Can't write output when running all versions.")
        return 1
    dexfile = args[0]
    oatfile = options.output
    versions = VERSIONS if options.all else [options.version]
    for version in versions:
        if version in VERSIONS:
            dex2oat_from_host_art(options, dexfile, oatfile, version)
        else:
            dex2oat(options, dexfile, oatfile, version)
        print("")
    return 0


def adb_cmd(serial, *args):
    cmd = ['adb', '-s', serial]
    cmd.extend(args)
    return cmd


def append_dex2oat_verbose_flags(options, cmd):
    verbose = [options.verbose] if options.verbose else []
    if 'all' in verbose:
        verbose = [x for x in VERBOSE_OPTIONS if x != 'all']
    for flag in verbose:
        cmd += ['--runtime-arg', '-verbose:' + flag]
    return cmd


def run_device_dex2oat(options, args):
    serial = options.device
    dexfile = args[0]
    device_dexfile = '/data/local/tmp/' + os.path.basename(dexfile)
    device_oatfile = '/data/local/tmp/unused.oat'
    cmd = adb_cmd(serial, 'shell', 'rm', '-f', device_dexfile, device_oatfile)
    utils.PrintCmd(cmd)
    subprocess.check_call(cmd)
    cmd = adb_cmd(serial, 'push', dexfile, device_dexfile)
    utils.PrintCmd(cmd)
    subprocess.check_call(cmd)
    cmd = adb_cmd(serial, 'logcat', '-c')
    utils.PrintCmd(cmd)
    subprocess.check_call(cmd)
    cmd = adb_cmd(serial, 'shell', 'dex2oat', '--dex-file=' + device_dexfile,
                  '--oat-file=/data/local/tmp/unused.oat')
    append_dex2oat_verbose_flags(options, cmd)
    utils.PrintCmd(cmd)
    subprocess.check_call(cmd)
    cmd = adb_cmd(serial, 'logcat', '-d', '-s', 'dex2oat', 'dex2oat_original')
    utils.PrintCmd(cmd)
    subprocess.check_call(cmd)

    return 0


def dex2oat_from_host_art(options, dexfile, oatfile=None, version=None):
    if not version:
        version = LATEST
    # dex2oat accepts non-existent dex files, check here instead
    if not os.path.exists(dexfile):
        raise Exception('DEX file not found: "{}"'.format(dexfile))
    with utils.TempDir() as temp:
        if not oatfile:
            oatfile = os.path.join(temp, "out.oat")
        base = os.path.join(LINUX_DIR, DIRS[version])
        product = PRODUCTS[version]
        arch = ARCHS[version]
        cmd = [
            os.path.join(base, 'bin', 'dex2oat'),
            '--android-root=' +
            os.path.join(base, 'product', product, 'system'),
            '--runtime-arg',
            '-Xnorelocate',
            '--dex-file=' + dexfile,
            '--oat-file=' + oatfile,
            '--instruction-set=' + arch,
        ]
        append_dex2oat_verbose_flags(options, cmd)
        if version in BOOT_IMAGE:
            cmd += ['--boot-image=' + BOOT_IMAGE[version]]
        env = {"LD_LIBRARY_PATH": os.path.join(base, 'lib')}
        utils.PrintCmd(cmd)
        with utils.ChangedWorkingDirectory(base):
            subprocess.check_call(cmd, env=env)


def dex2oat(options, dexfile, oatfile=None, version=None):
    bootclassjars = [
        'bootjars/core-oj.jar',
        'bootjars/core-libart.jar',
        'bootjars/okhttp.jar',
        'bootjars/bouncycastle.jar',
        'bootjars/apache-xml.jar',
    ]

    dex2oat_dir = os.path.join(utils.THIRD_PARTY, 'dex2oat', version)
    with utils.TempDir() as temp:
        if not oatfile:
            oatfile = os.path.join(temp, "out.oat")
        appimage = os.path.join(temp, 'classes.art')
        arch = 'x86_64'

        cmd = [
            os.path.join(dex2oat_dir, 'x86_64', 'bin', 'dex2oat64'),
            '--android-root=' + dex2oat_dir,
            '--generate-debug-info',
            '--dex-location=/system/framework/classes.dex',
            '--dex-file=' + dexfile,
            '--copy-dex-files=always',
        ]
        if version != '33.10':
            cmd.extend(['--runtime-arg', '-Xgc:CMC'])

        cmd.extend([
            '--runtime-arg',
            '-Xbootclasspath:' + ':'.join(
                map(lambda x: os.path.join(dex2oat_dir, x), bootclassjars)),
            '--runtime-arg',
            '-Xbootclasspath-locations:/apex/com.android.art/javalib/core-oj.jar'
            + ':/apex/com.android.art/javalib/core-libart.jar' +
            ':/apex/com.android.art/javalib/okhttp.jar' +
            ':/apex/com.android.art/javalib/bouncycastle.jar' +
            ':/apex/com.android.art/javalib/apache-xml.jar',
            '--boot-image=' + dex2oat_dir + '/app/system/framework/boot.art',
            '--oat-file=' + oatfile,
            '--app-image-file=' + appimage,
            '--instruction-set=' + arch,
        ])
        if version != '33.10':
            cmd.append('--force-allow-oj-inlines')
        if options.dump_cfg:
            cmd.append('--dump-cfg=' + options.dump_cfg)
        append_dex2oat_verbose_flags(options, cmd)
        utils.PrintCmd(cmd)
        subprocess.check_call(cmd)


if __name__ == '__main__':
    sys.exit(Main())
