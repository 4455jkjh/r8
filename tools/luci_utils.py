#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import json
import os
import utils


def get_luci_invocation_id(force_invocation_id=None):
    if not os.environ.get('LUCI_CONTEXT') and not force_invocation_id:
        raise Exception('Environment variable LUCI_CONTEXT not set')
    with utils.TempDir() as temp:
        if force_invocation_id:
            luci_context_path = os.path.join(temp, 'luci_context')
            with open(luci_context_path, 'w') as version_writer:
                version_writer.write(
                    '{"resultdb": {"current_invocation": {"name": "' +
                    force_invocation_id + '"}}}')
        else:
            luci_context_path = os.environ.get('LUCI_CONTEXT')

        with open(luci_context_path, 'r') as f:
            json_string = f.read()
            luci_context = json.loads(json_string)
            # The structure is typically:
            # {"resultdb": {"current_invocation": {"name": "invocations/build-123..."}}}
            luci_invocation_id = luci_context.get('resultdb', {}).get(
                'current_invocation', {}).get('name')
            if not luci_invocation_id:
                raise Exception(
                    'LUCI invocation_id not found through environment variable LUCI_CONTEXT: '
                    + luci_context_path + " with content '" + json_string + "'")
            return luci_invocation_id
