// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.retrace.internal.RetraceStackTraceContextImpl;
import com.android.tools.r8.retrace.internal.RetraceStackTraceCurrentEvaluationInformation;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.function.Consumer;

public class RewriteFrameMappingInformation extends MappingInformation {

  public static final MapVersion SUPPORTED_VERSION = MapVersion.MAP_VERSION_EXPERIMENTAL;
  public static final String ID = "com.android.tools.r8.rewriteFrame";
  private static final String CONDITIONS_KEY = "conditions";
  private static final String ACTIONS_KEY = "actions";

  private final List<Condition> conditions;
  private final List<RewriteAction> actions;

  private RewriteFrameMappingInformation(List<Condition> conditions, List<RewriteAction> actions) {
    this.conditions = conditions;
    this.actions = actions;
  }

  public List<Condition> getConditions() {
    return conditions;
  }

  public List<RewriteAction> getActions() {
    return actions;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String serialize() {
    JsonObject object = new JsonObject();
    object.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    JsonArray conditionsArray = new JsonArray();
    conditions.forEach(condition -> conditionsArray.add(condition.serialize()));
    object.add(CONDITIONS_KEY, conditionsArray);
    JsonArray actionsArray = new JsonArray();
    actions.forEach(action -> actionsArray.add(action.serialize()));
    object.add(ACTIONS_KEY, actionsArray);
    return object.toString();
  }

  public static boolean isSupported(MapVersion version) {
    return version.isGreaterThanOrEqualTo(SUPPORTED_VERSION);
  }

  @Override
  public boolean allowOther(MappingInformation information) {
    return !information.isRewriteFrameMappingInformation();
  }

  public static void deserialize(
      MapVersion mapVersion, JsonObject object, Consumer<MappingInformation> onMappingInfo) {
    if (!isSupported(mapVersion)) {
      return;
    }
    ImmutableList.Builder<Condition> conditions = ImmutableList.builder();
    object
        .get(CONDITIONS_KEY)
        .getAsJsonArray()
        .forEach(
            element -> {
              conditions.add(Condition.deserialize(element));
            });
    ImmutableList.Builder<RewriteAction> actions = ImmutableList.builder();
    object
        .get(ACTIONS_KEY)
        .getAsJsonArray()
        .forEach(element -> actions.add(RewriteAction.deserialize(element)));
    onMappingInfo.accept(new RewriteFrameMappingInformation(conditions.build(), actions.build()));
  }

  @Override
  public boolean isRewriteFrameMappingInformation() {
    return true;
  }

  @Override
  public RewriteFrameMappingInformation asRewriteFrameMappingInformation() {
    return this;
  }

  public abstract static class Condition {

    protected abstract JsonPrimitive serialize();

    private static Condition deserialize(JsonElement element) {
      String elementString = element.getAsString();
      int argIndex = elementString.indexOf('(');
      if (argIndex < 1 || !elementString.endsWith(")")) {
        throw new CompilationError("Invalid formatted condition: " + elementString);
      }
      String functionName = elementString.substring(0, argIndex);
      String contents = elementString.substring(argIndex + 1, elementString.length() - 1);
      if (ThrowsCondition.FUNCTION_NAME.equals(functionName)) {
        return ThrowsCondition.deserialize(contents);
      }
      throw new Unimplemented("Unexpected condition: " + elementString);
    }

    public boolean isThrowsCondition() {
      return false;
    }

    public ThrowsCondition asThrowsCondition() {
      return null;
    }

    public abstract boolean evaluate(RetraceStackTraceContextImpl context);
  }

  public static class ThrowsCondition extends Condition {

    static final String FUNCTION_NAME = "throws";

    private final String descriptor;

    private ThrowsCondition(String descriptor) {
      this.descriptor = descriptor;
    }

    @Override
    protected JsonPrimitive serialize() {
      return new JsonPrimitive(FUNCTION_NAME + "(" + asThrowsCondition().getDescriptor() + ")");
    }

    @Override
    public boolean isThrowsCondition() {
      return true;
    }

    public String getDescriptor() {
      return descriptor;
    }

    @Override
    public ThrowsCondition asThrowsCondition() {
      return this;
    }

    @Override
    public boolean evaluate(RetraceStackTraceContextImpl context) {
      return context.getThrownException() != null
          && context.getThrownException().getDescriptor().equals(descriptor);
    }

    public static ThrowsCondition deserialize(String conditionString) {
      if (!DescriptorUtils.isClassDescriptor(conditionString)) {
        throw new CompilationError("Unexpected throws-descriptor: " + conditionString);
      }
      return new ThrowsCondition(conditionString);
    }
  }

  public abstract static class RewriteAction {

    static final String REMOVE_INNER_FRAMES_SERIALIZED_NAME = "removeInnerFrames";

    private static final String FUNCTION_KEY = "function";
    private static final String ARGUMENTS_KEY = "arguments";

    abstract String serializeName();

    abstract JsonArray getArguments();

    JsonElement serialize() {
      JsonObject jsonObject = new JsonObject();
      jsonObject.add(FUNCTION_KEY, new JsonPrimitive(serializeName()));
      jsonObject.add(ARGUMENTS_KEY, getArguments());
      return jsonObject;
    }

    private static RewriteAction deserialize(JsonElement element) {
      String functionString = element.getAsString();
      int startArgsIndex = functionString.indexOf("(");
      int endArgsIndex = functionString.indexOf(")");
      if (endArgsIndex <= startArgsIndex) {
        throw new Unimplemented("Unexpected action: " + functionString);
      }
      String functionName = functionString.substring(0, startArgsIndex);
      String args = functionString.substring(startArgsIndex + 1, endArgsIndex);
      if (REMOVE_INNER_FRAMES_SERIALIZED_NAME.equals(functionName)) {
        return RemoveInnerFramesAction.create(args);
      }
      assert false : "Unknown function " + functionName;
      throw new Unimplemented("Unexpected action: " + functionName);
    }

    public boolean isRemoveInnerFramesAction() {
      return false;
    }

    public RemoveInnerFramesAction asRemoveInnerFramesRewriteAction() {
      return null;
    }

    public abstract void evaluate(RetraceStackTraceCurrentEvaluationInformation.Builder builder);
  }

  public static class RemoveInnerFramesAction extends RewriteAction {

    private final int numberOfFrames;

    public RemoveInnerFramesAction(int numberOfFrames) {
      this.numberOfFrames = numberOfFrames;
    }

    public int getNumberOfFrames() {
      return numberOfFrames;
    }

    @Override
    String serializeName() {
      return REMOVE_INNER_FRAMES_SERIALIZED_NAME;
    }

    @Override
    JsonArray getArguments() {
      JsonArray arguments = new JsonArray();
      arguments.add(numberOfFrames);
      return arguments;
    }

    static RemoveInnerFramesAction create(String args) {
      return new RemoveInnerFramesAction(Integer.parseInt(args));
    }

    @Override
    public boolean isRemoveInnerFramesAction() {
      return true;
    }

    @Override
    public RemoveInnerFramesAction asRemoveInnerFramesRewriteAction() {
      return this;
    }

    @Override
    public void evaluate(RetraceStackTraceCurrentEvaluationInformation.Builder builder) {
      builder.incrementRemoveInnerFramesCount(numberOfFrames);
    }
  }
}
