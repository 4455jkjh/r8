// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;

import com.android.tools.r8.UnorderedCollectionMatcher;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.IdentifiedParticipant;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.IdentifiedProvenanceGroup;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.Participant;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.ProvenanceGroup;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.ProvenanceNode;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.UnidentifiedProvenanceGroup;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

final class MergedClassesProvenanceMatchers {

  private MergedClassesProvenanceMatchers() {}

  static Matcher<ProvenanceNode> classNode(DexType expectedType) {
    return new TypeSafeMatcher<ProvenanceNode>() {
      @Override
      protected boolean matchesSafely(ProvenanceNode node) {
        return node.isClass() && node.getType().isIdenticalTo(expectedType);
      }

      @Override
      public void describeTo(Description description) {
        description
            .appendText("Class(")
            .appendText(expectedType.toDescriptorString())
            .appendText(")");
      }

      @Override
      protected void describeMismatchSafely(ProvenanceNode node, Description mismatchDescription) {
        mismatchDescription.appendText("was ").appendText(formatNode(node));
      }
    };
  }

  static Matcher<ProvenanceNode> internalSyntheticClassNode() {
    return new TypeSafeMatcher<ProvenanceNode>() {
      @Override
      protected boolean matchesSafely(ProvenanceNode node) {
        return node.isClass()
            && SyntheticItemsTestUtils.containsInternalSyntheticReference()
                .matches(node.getType().toDescriptorString());
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("Class(<internal synthetic>)");
      }

      @Override
      protected void describeMismatchSafely(ProvenanceNode node, Description mismatchDescription) {
        mismatchDescription.appendText("was ").appendText(formatNode(node));
      }
    };
  }

  @SafeVarargs
  static Matcher<Iterable<? extends ProvenanceNode>> matchesGroups(
      Matcher<? super ProvenanceNode>... expectedGroups) {
    return UnorderedCollectionMatcher.matchesOneToOne(
        Arrays.asList(expectedGroups), MergedClassesProvenanceMatchers::formatNode);
  }

  @SafeVarargs
  static Matcher<Iterable<? extends Participant>> matchesParticipants(
      Matcher<? super Participant>... expectedParticipants) {
    return UnorderedCollectionMatcher.matchesOneToOne(
        Arrays.asList(expectedParticipants), MergedClassesProvenanceMatchers::formatParticipant);
  }

  @SafeVarargs
  static Matcher<ProvenanceNode> group(Matcher<? super Participant>... expectedParticipants) {
    Matcher<Iterable<? extends Participant>> participantsMatcher =
        matchesParticipants(expectedParticipants);
    return new TypeSafeMatcher<ProvenanceNode>() {
      @Override
      protected boolean matchesSafely(ProvenanceNode node) {
        return node.isGroup() && participantsMatcher.matches(node.asGroup().getParticipants());
      }

      @Override
      public void describeTo(Description description) {
        description
            .appendText("ProvenanceGroup(participants=")
            .appendDescriptionOf(participantsMatcher)
            .appendText(")");
      }

      @Override
      protected void describeMismatchSafely(ProvenanceNode node, Description mismatchDescription) {
        mismatchDescription.appendText("was ").appendText(formatNode(node));
      }
    };
  }

  @SafeVarargs
  static Matcher<ProvenanceNode> unidentifiedGroup(
      DexType expectedTarget, Matcher<? super Participant>... expectedParticipants) {
    Matcher<Iterable<? extends Participant>> participantsMatcher =
        matchesParticipants(expectedParticipants);
    return new TypeSafeMatcher<ProvenanceNode>() {
      @Override
      protected boolean matchesSafely(ProvenanceNode node) {
        if (!node.isGroup() || !node.asGroup().isUnidentified()) {
          return false;
        }
        UnidentifiedProvenanceGroup group = node.asGroup().asUnidentified();
        return group.getTarget().isIdenticalTo(expectedTarget)
            && participantsMatcher.matches(group.getParticipants());
      }

      @Override
      public void describeTo(Description description) {
        description
            .appendText("UnidentifiedGroup(target=")
            .appendText(expectedTarget.toDescriptorString())
            .appendText(", participants=")
            .appendDescriptionOf(participantsMatcher)
            .appendText(")");
      }

      @Override
      protected void describeMismatchSafely(ProvenanceNode node, Description mismatchDescription) {
        if (!node.isGroup() || !node.asGroup().isUnidentified()) {
          mismatchDescription.appendText("was ").appendText(formatNode(node));
          return;
        }
        UnidentifiedProvenanceGroup group = node.asGroup().asUnidentified();
        if (!group.getTarget().isIdenticalTo(expectedTarget)) {
          mismatchDescription
              .appendText("had target ")
              .appendText(group.getTarget().toDescriptorString());
          return;
        }
        mismatchDescription.appendText("participants ");
        participantsMatcher.describeMismatch(group.getParticipants(), mismatchDescription);
      }
    };
  }

  @SafeVarargs
  static Matcher<ProvenanceNode> identifiedGroup(
      DexType expectedTarget,
      DexField expectedClassIdField,
      Matcher<? super Participant>... expectedParticipants) {
    Matcher<Iterable<? extends Participant>> participantsMatcher =
        matchesParticipants(expectedParticipants);
    return new TypeSafeMatcher<ProvenanceNode>() {
      @Override
      protected boolean matchesSafely(ProvenanceNode node) {
        if (!node.isGroup() || !node.asGroup().isIdentified()) {
          return false;
        }
        IdentifiedProvenanceGroup group = node.asGroup().asIdentified();
        return group.getTarget().isIdenticalTo(expectedTarget)
            && group.getClassIdField().isIdenticalTo(expectedClassIdField)
            && participantsMatcher.matches(group.getParticipants());
      }

      @Override
      public void describeTo(Description description) {
        description
            .appendText("IdentifiedGroup(target=")
            .appendText(expectedTarget.toDescriptorString())
            .appendText(", classIdField=")
            .appendText(expectedClassIdField.toSourceString())
            .appendText(", participants=")
            .appendDescriptionOf(participantsMatcher)
            .appendText(")");
      }

      @Override
      protected void describeMismatchSafely(ProvenanceNode node, Description mismatchDescription) {
        if (!node.isGroup() || !node.asGroup().isIdentified()) {
          mismatchDescription.appendText("was ").appendText(formatNode(node));
          return;
        }
        IdentifiedProvenanceGroup group = node.asGroup().asIdentified();
        if (!group.getTarget().isIdenticalTo(expectedTarget)) {
          mismatchDescription
              .appendText("had target ")
              .appendText(group.getTarget().toDescriptorString());
          return;
        }
        if (!group.getClassIdField().isIdenticalTo(expectedClassIdField)) {
          mismatchDescription
              .appendText("had classIdField ")
              .appendText(group.getClassIdField().toSourceString());
          return;
        }
        mismatchDescription.appendText("participants ");
        participantsMatcher.describeMismatch(group.getParticipants(), mismatchDescription);
      }
    };
  }

  static Matcher<ProvenanceNode> internalSyntheticVerticalGroup(DexType expectedMergedInterface) {
    return new TypeSafeMatcher<ProvenanceNode>() {
      @Override
      protected boolean matchesSafely(ProvenanceNode node) {
        if (!node.isGroup() || !node.asGroup().isUnidentified()) {
          return false;
        }
        ProvenanceGroup group = node.asGroup();
        DexType target = group.getTarget();
        if (!SyntheticItemsTestUtils.containsInternalSyntheticReference()
            .matches(target.toDescriptorString())) {
          return false;
        }
        return matchesParticipants(
                unidentifiedParticipant(target, true),
                unidentifiedParticipant(expectedMergedInterface, false))
            .matches(group.getParticipants());
      }

      @Override
      public void describeTo(Description description) {
        description
            .appendText("UnidentifiedGroup(target=<internal synthetic>, merged=")
            .appendText(expectedMergedInterface.toDescriptorString())
            .appendText(")");
      }

      @Override
      protected void describeMismatchSafely(ProvenanceNode node, Description mismatchDescription) {
        mismatchDescription.appendText("was ").appendText(formatNode(node));
      }
    };
  }

  static Matcher<Participant> participant(Matcher<? super ProvenanceNode> nodeMatcher) {
    return participant(nodeMatcher, Collections.emptyMap());
  }

  static Matcher<Participant> participant(
      DexType expectedLeafType, Map<DexField, DexField> expectedMergedFields) {
    return participant(classNode(expectedLeafType), expectedMergedFields);
  }

  static Matcher<Participant> participant(
      Matcher<? super ProvenanceNode> nodeMatcher, Map<DexField, DexField> expectedMergedFields) {
    return participantMatcher(null, null, null, nodeMatcher, expectedMergedFields);
  }

  static Matcher<Participant> unidentifiedParticipant(
      DexType expectedLeafType, boolean instantiated) {
    return unidentifiedParticipant(expectedLeafType, instantiated, Collections.emptyMap());
  }

  static Matcher<Participant> unidentifiedParticipant(
      Matcher<? super ProvenanceNode> nodeMatcher, boolean instantiated) {
    return unidentifiedParticipant(nodeMatcher, instantiated, Collections.emptyMap());
  }

  static Matcher<Participant> unidentifiedParticipant(
      DexType expectedLeafType,
      boolean instantiated,
      Map<DexField, DexField> expectedMergedFields) {
    return unidentifiedParticipant(classNode(expectedLeafType), instantiated, expectedMergedFields);
  }

  static Matcher<Participant> unidentifiedParticipant(
      Matcher<? super ProvenanceNode> nodeMatcher,
      boolean instantiated,
      Map<DexField, DexField> expectedMergedFields) {
    return participantMatcher(false, null, instantiated, nodeMatcher, expectedMergedFields);
  }

  static Matcher<Participant> identifiedParticipant(
      DexType expectedLeafType, int classId, boolean instantiated) {
    return identifiedParticipant(expectedLeafType, classId, instantiated, Collections.emptyMap());
  }

  static Matcher<Participant> identifiedParticipant(
      Matcher<? super ProvenanceNode> nodeMatcher, int classId, boolean instantiated) {
    return identifiedParticipant(nodeMatcher, classId, instantiated, Collections.emptyMap());
  }

  static Matcher<Participant> identifiedParticipant(
      DexType expectedLeafType,
      int classId,
      boolean instantiated,
      Map<DexField, DexField> expectedMergedFields) {
    return identifiedParticipant(
        classNode(expectedLeafType), classId, instantiated, expectedMergedFields);
  }

  static Matcher<Participant> identifiedParticipant(
      Matcher<? super ProvenanceNode> nodeMatcher,
      int classId,
      boolean instantiated,
      Map<DexField, DexField> expectedMergedFields) {
    return participantMatcher(true, classId, instantiated, nodeMatcher, expectedMergedFields);
  }

  static Matcher<Participant> participantWithOwnMergedField(
      Matcher<? super ProvenanceNode> nodeMatcher, DexField expectedTarget) {
    return new TypeSafeMatcher<Participant>() {
      @Override
      protected boolean matchesSafely(Participant participant) {
        if (!nodeMatcher.matches(participant.getNode())) {
          return false;
        }
        Matcher<Map.Entry<DexField, DexField>> entryMatcher =
            new TypeSafeMatcher<Map.Entry<DexField, DexField>>() {
              @Override
              protected boolean matchesSafely(Map.Entry<DexField, DexField> entry) {
                return entry.getKey().isIdenticalTo(expectedTarget)
                    && entry.getValue().getHolderType().isIdenticalTo(participant.getType());
              }

              @Override
              public void describeTo(Description description) {
                description
                    .appendText(expectedTarget.toSourceString())
                    .appendText(" -> <field on participant>");
              }
            };
        return UnorderedCollectionMatcher.matchesOneToOne(
                ImmutableList.of(entryMatcher), MergedClassesProvenanceMatchers::formatFieldEntry)
            .matches(participant.getMergedFields().entrySet());
      }

      @Override
      public void describeTo(Description description) {
        description
            .appendText("Participant(node=")
            .appendDescriptionOf(nodeMatcher)
            .appendText(", mergedFields={")
            .appendText(expectedTarget.toSourceString())
            .appendText(" -> <own field>})");
      }

      @Override
      protected void describeMismatchSafely(
          Participant participant, Description mismatchDescription) {
        mismatchDescription.appendText("was ").appendText(formatParticipant(participant));
      }
    };
  }

  static Matcher<Iterable<? extends DexField>> matchesMergedFieldContributors(
      DexField expectedTarget) {
    return UnorderedCollectionMatcher.matchesOneToOne(
        ImmutableList.of(
            equalTo(expectedTarget),
            allOf(notNullValue(DexField.class), not(equalTo(expectedTarget)))));
  }

  private static Matcher<Participant> participantMatcher(
      Boolean expectedIdentified,
      Integer expectedClassId,
      Boolean expectedInstantiated,
      Matcher<? super ProvenanceNode> nodeMatcher,
      Map<DexField, DexField> expectedMergedFields) {
    Matcher<Iterable<? extends Map.Entry<DexField, DexField>>> mergedFieldsMatcher =
        matchesFieldEntries(expectedMergedFields);
    return new TypeSafeMatcher<Participant>() {
      @Override
      protected boolean matchesSafely(Participant participant) {
        if (expectedIdentified != null) {
          if (expectedIdentified && !participant.isIdentified()) {
            return false;
          }
          if (!expectedIdentified && !participant.isUnidentified()) {
            return false;
          }
        }
        if (expectedClassId != null
            && (!participant.isIdentified()
                || participant.asIdentified().getClassId() != expectedClassId)) {
          return false;
        }
        if (expectedInstantiated != null && participant.isInstantiated() != expectedInstantiated) {
          return false;
        }
        return nodeMatcher.matches(participant.getNode())
            && mergedFieldsMatcher.matches(participant.getMergedFields().entrySet());
      }

      @Override
      public void describeTo(Description description) {
        if (expectedIdentified == null) {
          description.appendText("Participant(");
        } else if (expectedIdentified) {
          description.appendText("IdentifiedParticipant(");
        } else {
          description.appendText("UnidentifiedParticipant(");
        }
        if (expectedClassId != null) {
          description.appendText("classId=").appendValue(expectedClassId).appendText(", ");
        }
        if (expectedInstantiated != null) {
          description
              .appendText("instantiated=")
              .appendValue(expectedInstantiated)
              .appendText(", ");
        }
        description
            .appendText("node=")
            .appendDescriptionOf(nodeMatcher)
            .appendText(", mergedFields=")
            .appendText(formatMergedFields(expectedMergedFields))
            .appendText(")");
      }

      @Override
      protected void describeMismatchSafely(
          Participant participant, Description mismatchDescription) {
        mismatchDescription.appendText("was ").appendText(formatParticipant(participant));
      }
    };
  }

  static Matcher<Iterable<? extends Map.Entry<DexField, DexField>>> matchesFieldEntries(
      Map<DexField, DexField> expected) {
    List<Matcher<? super Map.Entry<DexField, DexField>>> entryMatchers =
        new ArrayList<>(expected.size());
    expected.forEach((target, origin) -> entryMatchers.add(fieldEntry(target, origin)));
    return UnorderedCollectionMatcher.matchesOneToOne(
        entryMatchers, MergedClassesProvenanceMatchers::formatFieldEntry);
  }

  private static Matcher<Map.Entry<DexField, DexField>> fieldEntry(
      DexField expectedTarget, DexField expectedOrigin) {
    return new TypeSafeMatcher<Map.Entry<DexField, DexField>>() {
      @Override
      protected boolean matchesSafely(Map.Entry<DexField, DexField> entry) {
        return entry.getKey().isIdenticalTo(expectedTarget)
            && entry.getValue().isIdenticalTo(expectedOrigin);
      }

      @Override
      public void describeTo(Description description) {
        description
            .appendText(expectedTarget.toSourceString())
            .appendText(" -> ")
            .appendText(expectedOrigin.toSourceString());
      }

      @Override
      protected void describeMismatchSafely(
          Map.Entry<DexField, DexField> entry, Description mismatchDescription) {
        mismatchDescription.appendText("was ").appendText(formatFieldEntry(entry));
      }
    };
  }

  static String formatParticipant(Participant participant) {
    StringBuilder builder = new StringBuilder();
    if (participant.isIdentified()) {
      IdentifiedParticipant identified = participant.asIdentified();
      builder.append("IdentifiedParticipant(classId=").append(identified.getClassId()).append(", ");
    } else if (participant.isUnidentified()) {
      builder.append("UnidentifiedParticipant(");
    } else {
      builder.append("Participant(");
    }
    builder
        .append("instantiated=")
        .append(participant.isInstantiated())
        .append(", node=")
        .append(formatNode(participant.getNode()))
        .append(", mergedFields=")
        .append(formatMergedFields(participant.getMergedFields()))
        .append(")");
    return builder.toString();
  }

  static String formatNode(ProvenanceNode node) {
    if (node.isClass()) {
      return "Class(" + node.getType().toDescriptorString() + ")";
    }
    ProvenanceGroup group = node.asGroup();
    String kind = group.isIdentified() ? "IdentifiedGroup" : "UnidentifiedGroup";
    String participants =
        group.getParticipants().stream()
            .map(MergedClassesProvenanceMatchers::formatParticipant)
            .collect(Collectors.joining(", "));
    return kind
        + "(target="
        + group.getTarget().toDescriptorString()
        + ", participants=["
        + participants
        + "])";
  }

  private static String formatMergedFields(Map<DexField, DexField> mergedFields) {
    return mergedFields.entrySet().stream()
        .map(MergedClassesProvenanceMatchers::formatFieldEntry)
        .collect(Collectors.joining(", ", "{", "}"));
  }

  static String formatFieldEntry(Map.Entry<DexField, DexField> entry) {
    return entry.getKey().toSourceString() + " -> " + entry.getValue().toSourceString();
  }
}
