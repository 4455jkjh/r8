# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

from os.path import join
import utils

DEFAULT_ROOT = join(utils.REPO_ROOT, 'build', 'aosp')


def add_root_argument(parser):
    parser.add_argument('--aosp-root',
                        help='Root of the AOSP checkout. ' + 'Defaults to ' +
                        DEFAULT_ROOT + '.',
                        default=DEFAULT_ROOT)
