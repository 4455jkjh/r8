// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.FileUtils.DEX_EXTENSION;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.utils.ArchiveBuilder;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.DexFilePerClassFileConsumerUtils;
import com.android.tools.r8.utils.DirectoryBuilder;
import com.android.tools.r8.utils.OutputBuilder;
import com.android.tools.r8.utils.StringDiagnostic;
import java.nio.file.Path;
import java.util.Set;

/**
 * Consumer for DEX encoded programs.
 *
 * <p>This consumer receives DEX file content for each Java class-file input.
 */
@KeepForApi
public interface DexFilePerClassFileConsumer extends ProgramConsumer, ByteBufferProvider {

  static final boolean SHOULD_COMBINE_SYNTHETIC_CLASSES = true;

  /**
   * Callback to receive DEX data for a single Java class-file input and its companion classes.
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
   * @param primaryClassDescriptor Class descriptor of the class from the input class-file.
   * @param data DEX encoded data in a ByteDataView wrapper.
   * @param descriptors Class descriptors for all classes defined in the DEX data.
   * @param handler Diagnostics handler for reporting.
   */
  default void accept(
      String primaryClassDescriptor,
      ByteDataView data,
      Set<String> descriptors,
      DiagnosticsHandler handler) {
    // To avoid breaking binary compatiblity, old consumers not implementing the new API will be
    // forwarded to. New consumers must implement the accept on ByteDataView.
    accept(primaryClassDescriptor, data.copyByteData(), descriptors, handler);
  }

  // Any new implementation should not use or call the deprecated accept method.
  @Deprecated
  default void accept(
      String primaryClassDescriptor,
      byte[] data,
      Set<String> descriptors,
      DiagnosticsHandler handler) {
    handler.error(
        new StringDiagnostic(
            "Deprecated use of DexFilePerClassFileConsumer::accept(..., byte[], ...)"));
  }

  /**
   * Combine synthetic classes with their primary class.
   *
   * If true all synthesized classes are combined together with the primary class they are derived
   * from in a single DEX file. This has the property that a classfile given as input will give rise to at most one DEX file as output.
   *
   * If false every class will give rise to its own DEX file, e.g., every DEX file will contain exactly one class.
   */
  default boolean combineSyntheticClassesWithPrimaryClass() {
    return SHOULD_COMBINE_SYNTHETIC_CLASSES;
  }

  /** Empty consumer to request the production of the resource but ignore its value. */
  static DexFilePerClassFileConsumer emptyConsumer() {
    return ForwardingConsumer.EMPTY_CONSUMER;
  }

  /** Forwarding consumer to delegate to an optional existing consumer. */
  @KeepForApi
  class ForwardingConsumer implements DexFilePerClassFileConsumer {

    private static final DexFilePerClassFileConsumer EMPTY_CONSUMER = new ForwardingConsumer(null);

    private final DexFilePerClassFileConsumer consumer;

    public ForwardingConsumer(DexFilePerClassFileConsumer consumer) {
      this.consumer = consumer;
    }

    @Override
    public DataResourceConsumer getDataResourceConsumer() {
      return consumer != null ? consumer.getDataResourceConsumer() : null;
    }

    @Override
    public void accept(
        String primaryClassDescriptor,
        ByteDataView data,
        Set<String> descriptors,
        DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.accept(primaryClassDescriptor, data, descriptors, handler);
      }
    }

    @Override
    public boolean combineSyntheticClassesWithPrimaryClass() {
      if (consumer == null) {
        return SHOULD_COMBINE_SYNTHETIC_CLASSES;
      } else {
        return consumer.combineSyntheticClassesWithPrimaryClass();
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
    private final OutputBuilder outputBuilder;
    protected final boolean consumeDataResources;

    public ArchiveConsumer(Path archive) {
      this(archive, null, false);
    }

    public ArchiveConsumer(Path archive, boolean consumeDataResources) {
      this(archive, null, consumeDataResources);
    }

    public ArchiveConsumer(Path archive, DexFilePerClassFileConsumer consumer) {
      this(archive, consumer, false);
    }

    public ArchiveConsumer(
        Path archive, DexFilePerClassFileConsumer consumer, boolean consumeDataResources) {
      super(consumer);
      this.outputBuilder = new ArchiveBuilder(archive);
      this.consumeDataResources = consumeDataResources;
      this.outputBuilder.open();
      if (getDataResourceConsumer() != null) {
        this.outputBuilder.open();
      }
    }

    @Override
    public DataResourceConsumer getDataResourceConsumer() {
      return consumeDataResources ? this : null;
    }

    @Override
    public void accept(
        String primaryClassDescriptor,
        ByteDataView data,
        Set<String> descriptors,
        DiagnosticsHandler handler) {
      super.accept(primaryClassDescriptor, data, descriptors, handler);
      outputBuilder.addFile(
          DexFilePerClassFileConsumerUtils.getDexFileName(primaryClassDescriptor), data, handler);
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

  /** Directory consumer to write program resources to a directory. */
  @KeepForApi
  class DirectoryConsumer extends ForwardingConsumer
      implements DataResourceConsumer, InternalProgramOutputPathConsumer {
    private final OutputBuilder outputBuilder;
    protected final boolean consumeDataResources;

    private static String getDexFileName(String classDescriptor) {
      assert classDescriptor != null && DescriptorUtils.isClassDescriptor(classDescriptor);
      return DescriptorUtils.getClassBinaryNameFromDescriptor(classDescriptor) + DEX_EXTENSION;
    }

    public DirectoryConsumer(Path directory) {
      this(directory, null, false);
    }

    public DirectoryConsumer(Path directory, boolean consumeDataResources) {
      this(directory, null, consumeDataResources);
    }

    public DirectoryConsumer(Path directory, DexFilePerClassFileConsumer consumer) {
      this(directory, consumer, false);
    }

    public DirectoryConsumer(
        Path directory, DexFilePerClassFileConsumer consumer, boolean consumeDataResources) {
      super(consumer);
      this.outputBuilder = new DirectoryBuilder(directory);
      this.consumeDataResources = consumeDataResources;
    }

    @Override
    public void accept(
        String primaryClassDescriptor,
        ByteDataView data,
        Set<String> descriptors,
        DiagnosticsHandler handler) {
      super.accept(primaryClassDescriptor, data, descriptors, handler);
      outputBuilder.addFile(getDexFileName(primaryClassDescriptor), data, handler);
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
    }

    @Override
    public Path internalGetOutputPath() {
      return outputBuilder.getPath();
    }
  }
}
