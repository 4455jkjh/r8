# Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import os
import sys

TOOLS_DIR = os.path.abspath(os.path.normpath(os.path.join(__file__, '..',
                                                          '..')))


def get_utils():
    '''Dynamically load the tools/utils.py python module.'''
    if TOOLS_DIR not in sys.path:
        sys.path.insert(0, TOOLS_DIR)
    import utils
    return utils
