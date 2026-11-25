// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

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
import java.util.Arrays;
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
    return participantMatcher(null, null, null, nodeMatcher);
  }

  static Matcher<Participant> unidentifiedParticipant(
      DexType expectedLeafType, boolean instantiated) {
    return unidentifiedParticipant(classNode(expectedLeafType), instantiated);
  }

  static Matcher<Participant> unidentifiedParticipant(
      Matcher<? super ProvenanceNode> nodeMatcher, boolean instantiated) {
    return participantMatcher(false, null, instantiated, nodeMatcher);
  }

  static Matcher<Participant> identifiedParticipant(
      DexType expectedLeafType, int classId, boolean instantiated) {
    return identifiedParticipant(classNode(expectedLeafType), classId, instantiated);
  }

  static Matcher<Participant> identifiedParticipant(
      Matcher<? super ProvenanceNode> nodeMatcher, int classId, boolean instantiated) {
    return participantMatcher(true, classId, instantiated, nodeMatcher);
  }

  private static Matcher<Participant> participantMatcher(
      Boolean expectedIdentified,
      Integer expectedClassId,
      Boolean expectedInstantiated,
      Matcher<? super ProvenanceNode> nodeMatcher) {
    return new TypeSafeMatcher<Participant>() {
      @Override
      protected boolean matchesSafely(Participant participant) {
        if (expectedIdentified != null && participant.isIdentified() != expectedIdentified) {
          return false;
        }
        if (expectedClassId != null
            && (!participant.isIdentified()
                || participant.asIdentified().getClassId() != expectedClassId)) {
          return false;
        }
        if (expectedInstantiated != null && participant.isInstantiated() != expectedInstantiated) {
          return false;
        }
        return nodeMatcher.matches(participant.getNode());
      }

      @Override
      public void describeTo(Description description) {
        if (Boolean.TRUE.equals(expectedIdentified)) {
          description.appendText("IdentifiedParticipant(");
          if (expectedClassId != null) {
            description.appendText("classId=").appendValue(expectedClassId).appendText(", ");
          }
        } else if (Boolean.FALSE.equals(expectedIdentified)) {
          description.appendText("UnidentifiedParticipant(");
        } else {
          description.appendText("Participant(");
        }
        if (expectedInstantiated != null) {
          description
              .appendText("instantiated=")
              .appendValue(expectedInstantiated)
              .appendText(", ");
        }
        description.appendText("node=").appendDescriptionOf(nodeMatcher).appendText(")");
      }

      @Override
      protected void describeMismatchSafely(
          Participant participant, Description mismatchDescription) {
        mismatchDescription.appendText("was ").appendText(formatParticipant(participant));
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
}
