// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class DirectoryBuilder implements OutputBuilder {
  private final Path root;
  private final Path normalizedRoot;
  private final Origin origin;

  public DirectoryBuilder(Path root) {
    this.root = root;
    this.normalizedRoot = root.toAbsolutePath().normalize();
    origin = new PathOrigin(root);
  }

  @Override
  public void open() {
  }

  @Override
  public void close(DiagnosticsHandler handler) {
  }

  @Override
  public void addDirectory(String name, DiagnosticsHandler handler) {
    Path target = safeResolve(name, handler);
    if (target == null) return;
    try {
      Files.createDirectories(target);
    } catch (IOException e) {
      handler.error(new ExceptionDiagnostic(e, new PathOrigin(target)));
    }
  }

  @Override
  public void addFile(String name, DataEntryResource content, DiagnosticsHandler handler) {
    try (InputStream in = content.getByteStream()) {
      addFile(name, ByteDataView.of(ByteStreams.toByteArray(in)), handler);
    } catch (IOException e) {
      handler.error(new ExceptionDiagnostic(e, content.getOrigin()));
    } catch (ResourceException e) {
      handler.error(new StringDiagnostic("Failed to open input: " + e.getMessage(),
          content.getOrigin()));
    }
  }

  @Override
  public synchronized void addFile(String name, ByteDataView content, DiagnosticsHandler handler) {
    Path target = safeResolve(name, handler);
    if (target == null) return;
    try {
      Files.createDirectories(target.getParent());
      ByteDataUtils.writeToFile(target, null, content);
    } catch (IOException e) {
      handler.error(new ExceptionDiagnostic(e, new PathOrigin(target)));
    }
  }

  private Path safeResolve(String name, DiagnosticsHandler handler) {
    String fixedName = name.replace(NAME_SEPARATOR, File.separatorChar);
    Path resolved = root.resolve(fixedName).toAbsolutePath().normalize();
    if (!resolved.startsWith(normalizedRoot)) {
      handler.error(
          new StringDiagnostic(
              "Refusing to write output entry outside of output directory: " + name, origin));
      return null;
    }
    return resolved;
  }

  @Override
  public void addIndexedClassFile(
      int index, String name, ByteDataView content, DiagnosticsHandler handler) {
    addFile(name, content, handler);
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Path getPath() {
    return root;
  }
}
