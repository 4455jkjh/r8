// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.ArchiveBuilder;
import com.android.tools.r8.utils.DexIndexedConsumerUtils;
import com.android.tools.r8.utils.DexUtils;
import com.android.tools.r8.utils.DirectoryBuilder;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.OutputBuilder;
import com.android.tools.r8.utils.StringDiagnostic;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/**
 * Consumer for DEX encoded programs.
 *
 * <p>This consumer receives DEX file content using standard indexed-multidex for programs larger
 * than a single DEX file. This is the default consumer for DEX programs.
 */
@KeepForApi
public interface DexIndexedConsumer extends ProgramConsumer, ByteBufferProvider {

  /**
   * Callback to receive DEX data for a compilation output.
   *
   * <p>This is the equivalent to writing out the files classes.dex, classes2.dex, etc., where
   * fileIndex gives the current file count (with the first file having index zero).
   *
   * <p>There is no guaranteed order and files might be written concurrently.
   *
   * <p>The consumer is expected not to throw, but instead report any errors via the diagnostics
   * {@param handler}. If an error is reported via {@param handler} and no exceptions are thrown,
   * then the compiler guaranties to exit with an error.
   *
   * <p>The {@link ByteDataView} {@param data} object can only be assumed valid during the duration
   * of the accept. If the bytes are needed beyond that, a copy must be made elsewhere.
   *
   * @param fileIndex Index of the DEX file for multi-dexing. Files are zero-indexed.
   * @param data DEX encoded data in a ByteDataView wrapper.
   * @param descriptors Class descriptors for all classes defined in the DEX data.
   * @param handler Diagnostics handler for reporting.
   */
  default void accept(
      int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
    // To avoid breaking binary compatiblity, old consumers not implementing the new API will be
    // forwarded to. New consumers must implement the accept on ByteDataView.
    accept(fileIndex, data.copyByteData(), descriptors, handler);
  }

  // Any new implementation should not use or call the deprecated accept method.
  @Deprecated
  default void accept(
      int fileIndex, byte[] data, Set<String> descriptors, DiagnosticsHandler handler) {
    handler.error(
        new StringDiagnostic("Deprecated use of DexIndexedConsumer::accept(..., byte[], ...)"));
  }

  /** Empty consumer to request the production of the resource but ignore its value. */
  static DexIndexedConsumer emptyConsumer() {
    return ForwardingConsumer.EMPTY_CONSUMER;
  }

  /** Forwarding consumer to delegate to an optional existing consumer. */
  @KeepForApi
  class ForwardingConsumer implements DexIndexedConsumer {

    private static final DexIndexedConsumer EMPTY_CONSUMER = new ForwardingConsumer(null);

    private final DexIndexedConsumer consumer;

    public ForwardingConsumer(DexIndexedConsumer consumer) {
      this.consumer = consumer;
    }

    @Override
    public DataResourceConsumer getDataResourceConsumer() {
      return consumer != null ? consumer.getDataResourceConsumer() : null;
    }

    @Override
    public void accept(
        int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.accept(fileIndex, data, descriptors, handler);
      }
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.finished(handler);
      }
    }
  }

  /** Consumer to write program resources to an output. */
  @KeepForApi
  class ArchiveConsumer extends ForwardingConsumer
      implements DataResourceConsumer, InternalProgramOutputPathConsumer {
    protected final OutputBuilder outputBuilder;
    protected final boolean consumeDataResources;

    public ArchiveConsumer(Path archive) {
      this(archive, null, false);
    }

    public ArchiveConsumer(Path archive, boolean consumeDataResources) {
      this(archive, null, consumeDataResources);
    }

    public ArchiveConsumer(Path archive, DexIndexedConsumer consumer) {
      this(archive, consumer, false);
    }

    public ArchiveConsumer(
        Path archive, DexIndexedConsumer consumer, boolean consumeDataResources) {
      super(consumer);
      this.outputBuilder = new ArchiveBuilder(archive);
      this.consumeDataResources = consumeDataResources;
      this.outputBuilder.open();
      if (getDataResourceConsumer() != null) {
        this.outputBuilder.open();
      }
    }

    public Origin getOrigin() {
      return outputBuilder.getOrigin();
    }

    @Override
    public DataResourceConsumer getDataResourceConsumer() {
      return consumeDataResources ? this : null;
    }

    @Override
    public void accept(
        int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
      super.accept(fileIndex, data, descriptors, handler);
      outputBuilder.addIndexedClassFile(
          fileIndex, DexUtils.getDefaultDexFileName(fileIndex), data, handler);
    }

    @Override
    public void accept(DataDirectoryResource directory, DiagnosticsHandler handler) {
      outputBuilder.addDirectory(directory.getName(), handler);
    }

    @Override
    public void accept(DataEntryResource file, DiagnosticsHandler handler) {
      outputBuilder.addFile(file.getName(), file, handler);
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      super.finished(handler);
      outputBuilder.close(handler);
    }

    @Override
    public Path internalGetOutputPath() {
      return outputBuilder.getPath();
    }
  }

  @KeepForApi
  class DirectoryConsumer extends ForwardingConsumer
      implements DataResourceConsumer, InternalProgramOutputPathConsumer {
    private final Path directory;
    private boolean preparedDirectory = false;
    private final OutputBuilder outputBuilder;
    protected final boolean consumeDataResources;

    public DirectoryConsumer(Path directory) {
      this(directory, null, false);
    }

    public DirectoryConsumer(Path directory, boolean consumeDataResources) {
      this(directory, null, consumeDataResources);
    }

    public DirectoryConsumer(Path directory, DexIndexedConsumer consumer) {
      this(directory, consumer, false);
    }

    public DirectoryConsumer(
        Path directory, DexIndexedConsumer consumer, boolean consumeDataResources) {
      super(consumer);
      this.directory = directory;
      this.outputBuilder = new DirectoryBuilder(directory);
      this.consumeDataResources = consumeDataResources;
    }

    @Override
    public DataResourceConsumer getDataResourceConsumer() {
      return consumeDataResources ? this : null;
    }

    @Override
    public void accept(
        int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
      super.accept(fileIndex, data, descriptors, handler);
      try {
        prepareDirectory();
      } catch (IOException e) {
        handler.error(new ExceptionDiagnostic(e, new PathOrigin(directory)));
      }
      outputBuilder.addFile(DexUtils.getDefaultDexFileName(fileIndex), data, handler);
    }

    @Override
    public void accept(DataDirectoryResource directory, DiagnosticsHandler handler) {
      outputBuilder.addDirectory(directory.getName(), handler);
    }

    @Override
    public void accept(DataEntryResource file, DiagnosticsHandler handler) {
      outputBuilder.addFile(file.getName(), file, handler);
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      super.finished(handler);
      outputBuilder.close(handler);
    }

    private synchronized void prepareDirectory() throws IOException {
      if (preparedDirectory) {
        return;
      }
      preparedDirectory = true;
      DexIndexedConsumerUtils.DirectoryConsumerUtils.deleteClassesDexFiles(directory);
    }

    @Override
    public Path internalGetOutputPath() {
      return outputBuilder.getPath();
    }
  }
}
