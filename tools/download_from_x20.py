#!/usr/bin/env python3
# Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Script for downloading from x20 a dependency in the same way we use cloud
# storage.

import optparse
import os
import shutil
import sys
import utils

GMSCORE_DEPS = '/google/data/ro/teams/r8/deps'


def parse_options():
    return optparse.OptionParser().parse_args()


def download(sha1, dest):
    src = os.path.join(GMSCORE_DEPS, sha1)
    if os.path.exists(src):
        print('Downloading %s to %s' % (src, dest))
        shutil.copyfile(src, dest)
        return
    gcs_src = '%s/%s' % (utils.R8_INTERNAL_DEPS_BUCKET, sha1)
    print('Downloading %s to %s' % (gcs_src, dest))
    utils.download_file_from_cloud_storage(gcs_src, dest)


def Main():
    (options, args) = parse_options()
    assert len(args) == 1
    try:
        utils.ensure_sha1_archive(args[0], download)
    except Exception:
        print('File in %s or %s does not exist for %s' %
              (GMSCORE_DEPS, utils.R8_INTERNAL_DEPS_BUCKET, args[0]))
        print('Maybe pass -Pno_internal to your gradle invocation')
        return 42


if __name__ == '__main__':
    sys.exit(Main())
