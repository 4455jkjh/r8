// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ArchiveProgramResourceProvider.ZipFileSupplier;
import com.android.tools.r8.DataDirectoryResource;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.internal.BooleanBox;
import com.android.tools.r8.utils.internal.FileUtils;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class ArchiveResourceProviderUtils {

  // Conservative cap on pre-allocating a byte[] from a ZIP header's reported uncompressed size
  // so corrupt headers do not trigger huge allocations; larger entries fall back to streaming.
  private static final int MAX_PREALLOCATE_SIZE = 100_000_000;

  // Flag to disable the use of one-shot byte resources to support using R8 9.x with AGP 8.y.
  private static final boolean enableOneShotByteResource =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.enableOneShotByteResource", true);

  public static List<Kind> readArchive(
      Path archive,
      Origin origin,
      BiPredicate<String, Kind> predicate,
      Consumer<ProgramResource> consumer)
      throws IOException {
    ZipFileSupplier zipFileSupplier =
        () -> FileUtils.createZipFile(archive.toFile(), StandardCharsets.UTF_8);
    return readArchive(
        zipFileSupplier,
        origin,
        predicate,
        consumer,
        ArchiveResourceProviderUtils::createProgramResource);
  }

  public static List<Kind> readArchive(
      ZipFileSupplier zipFileSupplier,
      Origin origin,
      BiPredicate<String, Kind> predicate,
      Consumer<ProgramResource> consumer,
      ProgramResourceFactory programResourceFactory)
      throws IOException {
    BooleanBox seenCf = new BooleanBox();
    BooleanBox seenDex = new BooleanBox();
    try (ZipFile zipFile = zipFileSupplier.open()) {
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();
        if (ZipUtils.isDexFile(name)) {
          if (predicate.test(name, Kind.DEX)) {
            Origin entryOrigin = new ArchiveEntryOrigin(name, origin);
            consumer.accept(
                programResourceFactory.create(
                    entryOrigin, Kind.DEX, readZipEntryBytes(zipFile, entry), null));
            seenDex.set();
          }
        } else if (ZipUtils.isClassFile(name)) {
          if (predicate.test(name, Kind.CF)) {
            Origin entryOrigin = new ArchiveEntryOrigin(name, origin);
            String descriptor = DescriptorUtils.guessTypeDescriptor(name);
            consumer.accept(
                programResourceFactory.create(
                    entryOrigin,
                    Kind.CF,
                    readZipEntryBytes(zipFile, entry),
                    Collections.singleton(descriptor)));
            seenCf.set();
          }
        }
      }
    }
    List<Kind> seenKinds = new ArrayList<>(seenCf.intValue() + seenDex.intValue());
    if (seenCf.isTrue()) {
      seenKinds.add(Kind.CF);
    }
    if (seenDex.isTrue()) {
      seenKinds.add(Kind.DEX);
    }
    return seenKinds;
  }

  private static byte[] readZipEntryBytes(ZipFile zipFile, ZipEntry entry) throws IOException {
    try (InputStream stream = zipFile.getInputStream(entry)) {
      long size = entry.getSize();
      if (size < 0 || size > MAX_PREALLOCATE_SIZE) {
        // Entry size is unknown (-1) or exceeds the pre-allocation cap (e.g., ZIP64 or bogus
        // header).
        return ByteStreams.toByteArray(stream);
      }
      byte[] bytes = new byte[(int) size];
      // Use ByteStreams rather than Java 9+ InputStream.readNBytes/transferTo so R8 runs on ART.
      int n = ByteStreams.read(stream, bytes, 0, bytes.length);
      if (n < bytes.length) {
        // Stream reached EOF early because the ZIP header over-reported the uncompressed size.
        return Arrays.copyOf(bytes, n);
      }
      int next = stream.read();
      if (next == -1) {
        // Normal case: stream length matched entry.getSize() exactly.
        return bytes;
      }
      // Stream has more data because the ZIP header under-reported the uncompressed size.
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      out.write(bytes);
      out.write(next);
      ByteStreams.copy(stream, out);
      return out.toByteArray();
    }
  }

  private static ProgramResource createProgramResource(
      Origin origin, Kind kind, byte[] bytes, Set<String> classDescriptors) {
    return enableOneShotByteResource
        ? OneShotByteResource.create(origin, kind, bytes, classDescriptors)
        : ProgramResource.fromBytes(origin, kind, bytes, classDescriptors);
  }

  public static void readDataResources(
      Path archive,
      Origin origin,
      Predicate<ZipEntry> predicate,
      DataResourceProvider.Visitor visitor)
      throws ResourceException {
    try (ZipFile zipFile = FileUtils.createZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
      final Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (predicate.test(entry)) {
          if (entry.isDirectory()) {
            visitor.visit(DataDirectoryResource.fromZip(zipFile, entry, origin));
          } else {
            visitor.visit(DataEntryResource.fromZip(zipFile, entry, origin));
          }
        }
      }
    } catch (ZipException e) {
      throw new ResourceException(
          origin,
          new CompilationError("Zip error while reading '" + archive + "': " + e.getMessage(), e));
    } catch (IOException e) {
      throw new ResourceException(
          origin,
          new CompilationError(
              "I/O exception while reading '" + archive + "': " + e.getMessage(), e));
    }
  }

  public interface ProgramResourceFactory {

    ProgramResource create(Origin origin, Kind kind, byte[] bytes, Set<String> classDescriptors)
        throws IOException;
  }
}
