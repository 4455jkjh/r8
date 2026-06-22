// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace.partition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.retrace.RetracePartitionException;
import com.android.tools.r8.retrace.internal.MetadataAdditionalInfo;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class MetadataCombineTest extends TestBase {

  private static final List<String> VALID_HEADER =
      Arrays.asList(
          "# compiler: R8",
          "# compiler_version: main",
          "# min_api: 26",
          "# compiler_hash: engineering",
          "# common_typos_disable",
          "# {\"id\":\"com.android.tools.r8.mapping\",\"version\":\"2.2\"}");

  @Test
  public void testCombineSuccess() {
    List<String> preamble1 = new java.util.ArrayList<>(VALID_HEADER);
    preamble1.add("# pg_map_id: id1");
    preamble1.add("# pg_map_hash: SHA-256 hash1");

    List<String> preamble2 = new java.util.ArrayList<>(VALID_HEADER);
    preamble2.add("# pg_map_id: id2");
    preamble2.add("# pg_map_hash: SHA-256 hash2");

    MetadataAdditionalInfo info1 = MetadataAdditionalInfo.create(preamble1, Collections.emptySet());
    MetadataAdditionalInfo info2 = MetadataAdditionalInfo.create(preamble2, Collections.emptySet());

    MetadataAdditionalInfo combined = info1.combine(info2, "new_id");

    List<String> combinedPreamble = new java.util.ArrayList<>(combined.getPreamble());
    assertEquals(8, combinedPreamble.size());
    for (int i = 0; i <= 5; i++) {
      assertEquals(VALID_HEADER.get(i), combinedPreamble.get(i));
    }
    assertEquals("# pg_map_id: new_id", combinedPreamble.get(6));

    String expectedHash =
        Hashing.sha256()
            .newHasher()
            .putString("hash1", StandardCharsets.UTF_8)
            .putString("hash2", StandardCharsets.UTF_8)
            .hash()
            .toString();
    assertEquals("# pg_map_hash: SHA-256 " + expectedHash, combinedPreamble.get(7));
  }

  @Test
  public void testCombineHeaderMismatch() {
    List<String> preamble1 = new java.util.ArrayList<>(VALID_HEADER);
    preamble1.add("# pg_map_id: id1");
    preamble1.add("# pg_map_hash: SHA-256 hash1");

    List<String> preamble2 = new java.util.ArrayList<>(VALID_HEADER);
    preamble2.set(2, "# min_api: 24"); // Mismatch
    preamble2.add("# pg_map_id: id2");
    preamble2.add("# pg_map_hash: SHA-256 hash2");

    MetadataAdditionalInfo info1 = MetadataAdditionalInfo.create(preamble1, Collections.emptySet());
    MetadataAdditionalInfo info2 = MetadataAdditionalInfo.create(preamble2, Collections.emptySet());

    assertThrows(RetracePartitionException.class, () -> info1.combine(info2, "new_id"));
  }

  @Test
  public void testCombinePreambleTooShort() {
    List<String> preamble1 = Arrays.asList("# compiler: R8");
    List<String> preamble2 = new java.util.ArrayList<>(VALID_HEADER);
    preamble2.add("# pg_map_id: id2");
    preamble2.add("# pg_map_hash: SHA-256 hash2");

    MetadataAdditionalInfo info1 = MetadataAdditionalInfo.create(preamble1, Collections.emptySet());
    MetadataAdditionalInfo info2 = MetadataAdditionalInfo.create(preamble2, Collections.emptySet());

    assertThrows(RetracePartitionException.class, () -> info1.combine(info2, "new_id"));
  }
}
