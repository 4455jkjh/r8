#!/usr/bin/env python3
# Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Script for downloading from x20 a dependency in the same way we use cloud
# storage.

import argparse
import os
import shutil
import sys
import utils

GMSCORE_DEPS = '/google/data/ro/teams/r8/deps'


def parse_options(argv=None):
    parser = argparse.ArgumentParser(
        description='Download a dependency from x20 or internal GCS.')
    parser.add_argument('sha1_file', help='Path to the .tar.gz.sha1 file.')
    return parser.parse_args(argv)


def download(sha1, dest):
    src = os.path.join(GMSCORE_DEPS, sha1)
    if os.path.exists(src):
        print('Downloading %s to %s' % (src, dest))
        shutil.copyfile(src, dest)
        return
    gcs_src = '%s/%s' % (utils.R8_INTERNAL_DEPS_BUCKET, sha1)
    print('Downloading %s to %s' % (gcs_src, dest))
    utils.download_file_from_cloud_storage(gcs_src, dest)


def main():
    options = parse_options()
    try:
        utils.ensure_sha1_archive(options.sha1_file, download)
    except Exception:
        print('File in %s or %s does not exist for %s' %
              (GMSCORE_DEPS, utils.R8_INTERNAL_DEPS_BUCKET, options.sha1_file))
        print('Maybe pass -Pno_internal to your gradle invocation')
        return 42


if __name__ == '__main__':
    sys.exit(main())
