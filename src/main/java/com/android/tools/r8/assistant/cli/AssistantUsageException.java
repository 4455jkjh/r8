// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

/**
 * A command line usage error, such as an unknown option or a missing argument.
 *
 * <p>The message is shown to the user, and the tool exits with exit code 2.
 */
public class AssistantUsageException extends Exception {

  public AssistantUsageException(String message) {
    super(message);
  }
}
