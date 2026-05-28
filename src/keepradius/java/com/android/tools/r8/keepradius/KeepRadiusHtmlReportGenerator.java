// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepradius;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.keepradius.proto.BuildInfo;
import com.android.tools.r8.keepradius.proto.GlobalKeepRuleKeepRadius;
import com.android.tools.r8.keepradius.proto.GlobalKeepRuleKeepRadiusSummary;
import com.android.tools.r8.keepradius.proto.KeepConstraint;
import com.android.tools.r8.keepradius.proto.KeepConstraints;
import com.android.tools.r8.keepradius.proto.KeepInfoCollectionSummary;
import com.android.tools.r8.keepradius.proto.KeepRadiusContainer;
import com.android.tools.r8.keepradius.proto.KeepRadiusSummary;
import com.android.tools.r8.keepradius.proto.KeepRuleKeepRadius;
import com.android.tools.r8.keepradius.proto.KeepRuleKeepRadiusSummary;
import com.android.tools.r8.keepradius.proto.KeepRuleTag;
import com.android.tools.r8.keepradius.proto.KeptClassInfo;
import com.android.tools.r8.keepradius.proto.KeptFieldInfo;
import com.android.tools.r8.keepradius.proto.KeptMethodInfo;
import com.google.protobuf.AbstractMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@KeepForApi
public class KeepRadiusHtmlReportGenerator {

  /**
   * Convert a {@code keepradius.pb} file to HTML using {@code KeepRadiusHtmlReportGenerator
   * <keepradius.pb> <keepradius.html>}.
   *
   * <p>Convert all {@code keepradius.pb} files in a given directory to HTML and create a summary
   * using {@code KeepRadiusHtmlReportGenerator <keepradius dir> <out dir>}. </code>
   */
  public static void main(String[] args) throws IOException {
    // TODO(b/486097941): Remove.
    System.out.println("NOTE: Running experimental KeepRadiusHtmlReportGenerator.");

    Path input = Paths.get(args[0]);
    Path output = Paths.get(args[1]);
    List<Path> keepRadiusFiles = new ArrayList<>();
    List<Path> keepRadiusOutputFiles = new ArrayList<>();

    // If the first argument is a directory, find all *keepradius*.pb files inside the directory.
    boolean summarize = false;
    if (Files.isDirectory(input)) {
      if (Files.exists(output) && !Files.isDirectory(output)) {
        throw new IllegalArgumentException("Expected directory, but was: " + output);
      }
      try (var stream = Files.walk(input)) {
        stream
            .filter(Files::isRegularFile)
            .forEach(
                path -> {
                  String name = path.getFileName().toString();
                  if (name.endsWith(".pb") && name.contains("keepradius")) {
                    String htmlName = name.substring(0, name.lastIndexOf('.')) + ".html";
                    keepRadiusFiles.add(path);
                    keepRadiusOutputFiles.add(
                        output.resolve(input.relativize(path.resolveSibling(htmlName))));
                  }
                });
      }
      summarize = true;
    } else {
      keepRadiusFiles.add(input);
      keepRadiusOutputFiles.add(output);
    }

    // Generate HTML for each *keepradius*.pb file.
    List<KeepRadiusSummary> keepRadiusSummaries = summarize ? new ArrayList<>() : null;
    for (int i = 0; i < keepRadiusFiles.size(); i++) {
      Path keepRadiusFile = keepRadiusFiles.get(i);
      Path keepRadiusOutputFile = keepRadiusOutputFiles.get(i);
      KeepRadiusContainer keepRadius;
      try (InputStream is = Files.newInputStream(keepRadiusFile)) {
        keepRadius = KeepRadiusContainer.parseFrom(is);
      }
      Files.createDirectories(keepRadiusOutputFile.getParent());
      Files.write(
          keepRadiusOutputFile, generateHtmlReport(keepRadius).getBytes(StandardCharsets.UTF_8));
      if (summarize) {
        Path relPath = input.relativize(keepRadiusFile);
        String name = relPath.getParent().toString();
        String link = relPath.toString().replace(".pb", ".html");
        keepRadiusSummaries.add(getSummary(keepRadius, name, link));
      }
    }

    // Output summary.
    if (summarize) {
      Files.write(
          output.resolve("keepradius.html"),
          generateSummary(keepRadiusSummaries).getBytes(StandardCharsets.UTF_8));
    }
  }

  public static String generateHtmlReport(KeepRadiusContainer keepRadius) {
    String html = KeepRadiusHtmlReportTemplate.getHtmlTemplate();
    return html.replace(
        "<script id=\"keepradius-data\" type=\"application/octet-stream\"></script>",
        String.join(
            "",
            "<script id=\"keepradius-data\" type=\"application/octet-stream\">",
            encodeMessageToString(keepRadius),
            "</script>"));
  }

  private static String generateSummary(List<KeepRadiusSummary> keepRadiusSummaries) {
    String html = KeepRadiusHtmlReportTemplate.getSummaryHtmlTemplate();
    return html.replace(
        "<script id=\"keepradius-data\" type=\"application/json\"></script>",
        String.join(
            "",
            "<script id=\"keepradius-data\" type=\"application/json\">[",
            keepRadiusSummaries.stream()
                .map(KeepRadiusHtmlReportGenerator::encodeMessageToString)
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(",")),
            "]</script>"));
  }

  private static String encodeMessageToString(AbstractMessage message) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      message.writeTo(baos);
    } catch (IOException e) {
      // Should not happen.
      throw new UncheckedIOException(e);
    }
    return Base64.getEncoder().encodeToString(baos.toByteArray());
  }

  // Helper method for converting a proto to a string in tests. Should only be used for testing.
  public static String encodeMessageToStringForTesting(Object object) {
    return encodeMessageToString((AbstractMessage) object);
  }

  private static KeepRadiusSummary getSummary(
      KeepRadiusContainer keepRadius, String name, String link) {
    KeepRadiusSummary.Builder summaryBuilder =
        KeepRadiusSummary.newBuilder().setName(name).setLink(link);

    // Info about number of items.
    BuildInfo buildInfo = keepRadius.getBuildInfo();
    summaryBuilder.setLiveClassCount(buildInfo.getClassCount());
    summaryBuilder.setLiveFieldCount(buildInfo.getFieldCount());
    summaryBuilder.setLiveMethodCount(buildInfo.getMethodCount());

    // Info about number of keep rules.
    summaryBuilder.setKeepRuleCount(keepRadius.getKeepRuleKeepRadiusTableCount());
    summaryBuilder.setKeepRulePackageWideCount(
        (int)
            keepRadius.getKeepRuleKeepRadiusTableList().stream()
                .filter(rule -> rule.getTagsList().contains(KeepRuleTag.PACKAGE_WIDE))
                .count());

    // Info about number of kept items.
    Map<Integer, KeepConstraints> constraintsById = new HashMap<>();
    for (KeepConstraints keepConstraints : keepRadius.getKeepConstraintsTableList()) {
      constraintsById.put(keepConstraints.getId(), keepConstraints);
    }
    Map<Integer, KeepConstraints> ruleIdToConstraints = new HashMap<>();
    for (KeepRuleKeepRadius rule : keepRadius.getKeepRuleKeepRadiusTableList()) {
      ruleIdToConstraints.put(rule.getId(), constraintsById.get(rule.getConstraintsId()));
    }
    summaryBuilder.setKeptClasses(
        getKeptItemsSummary(
            keepRadius.getKeptClassInfoTableList(),
            KeptClassInfo::getId,
            KeptClassInfo::getKeptByList,
            ruleIdToConstraints));
    summaryBuilder.setKeptFields(
        getKeptItemsSummary(
            keepRadius.getKeptFieldInfoTableList(),
            KeptFieldInfo::getId,
            KeptFieldInfo::getKeptByList,
            ruleIdToConstraints));
    summaryBuilder.setKeptMethods(
        getKeptItemsSummary(
            keepRadius.getKeptMethodInfoTableList(),
            KeptMethodInfo::getId,
            KeptMethodInfo::getKeptByList,
            ruleIdToConstraints));

    // Info about problematic rules.
    List<KeepRuleKeepRadius> rulesWithRadius =
        new ArrayList<>(keepRadius.getKeepRuleKeepRadiusTableList());
    rulesWithRadius.sort(
        (a, b) -> {
          int radiusA =
              a.getKeepRadius().getClassKeepRadiusCount()
                  + a.getKeepRadius().getFieldKeepRadiusCount()
                  + a.getKeepRadius().getMethodKeepRadiusCount();
          int radiusB =
              b.getKeepRadius().getClassKeepRadiusCount()
                  + b.getKeepRadius().getFieldKeepRadiusCount()
                  + b.getKeepRadius().getMethodKeepRadiusCount();
          return Integer.compare(radiusB, radiusA);
        });

    for (int i = 0; i < rulesWithRadius.size(); i++) {
      KeepRuleKeepRadius rule = rulesWithRadius.get(i);
      int radius =
          rule.getKeepRadius().getClassKeepRadiusCount()
              + rule.getKeepRadius().getFieldKeepRadiusCount()
              + rule.getKeepRadius().getMethodKeepRadiusCount();
      summaryBuilder.addKeepRuleKeepRadius(
          KeepRuleKeepRadiusSummary.newBuilder().setSource(rule.getSource()).setItemCount(radius));
    }

    // Info about problematic global rules.
    for (GlobalKeepRuleKeepRadius rule : keepRadius.getGlobalKeepRuleKeepRadiusTableList()) {
      summaryBuilder.addGlobalKeepRuleKeepRadius(
          GlobalKeepRuleKeepRadiusSummary.newBuilder().setSource(rule.getSource()).build());
    }

    return summaryBuilder.build();
  }

  private static <T> KeepInfoCollectionSummary getKeptItemsSummary(
      List<T> items,
      Function<T, Integer> getId,
      Function<T, List<Integer>> getKeptBy,
      Map<Integer, KeepConstraints> ruleIdToConstraints) {
    Set<Integer> noObfuscation = new HashSet<>();
    Set<Integer> noOptimization = new HashSet<>();
    Set<Integer> noShrinking = new HashSet<>();
    for (T item : items) {
      int id = getId.apply(item);
      List<Integer> keptBy = getKeptBy.apply(item);
      for (int ruleId : keptBy) {
        KeepConstraints constraints = ruleIdToConstraints.get(ruleId);
        for (KeepConstraint constraint : constraints.getConstraintsList()) {
          switch (constraint) {
            case DONT_OBFUSCATE:
              noObfuscation.add(id);
              break;
            case DONT_OPTIMIZE:
              noOptimization.add(id);
              break;
            case DONT_SHRINK:
              noShrinking.add(id);
              break;
            default:
              throw new RuntimeException("Unexpected constraint: " + constraint);
          }
        }
      }
    }
    return KeepInfoCollectionSummary.newBuilder()
        .setItemCount(items.size())
        .setNoObfuscationCount(noObfuscation.size())
        .setNoOptimizationCount(noOptimization.size())
        .setNoShrinkingCount(noShrinking.size())
        .build();
  }
}
