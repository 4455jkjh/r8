#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

# Script for downloading from Google Cloud Storage (gs://r8-deps).

import optparse
import sys
import urllib.request
import utils


def parse_options():
    return optparse.OptionParser().parse_args()


def download(sha1, dest):
    src = '%s/%s' % (utils.R8_DEPS_URL, sha1)
    print('Downloading %s to %s' % (src, dest))
    urllib.request.urlretrieve(src, dest)


def Main():
    (options, args) = parse_options()
    assert len(args) == 1
    utils.ensure_sha1_archive(args[0], download)


if __name__ == '__main__':
    sys.exit(Main())
