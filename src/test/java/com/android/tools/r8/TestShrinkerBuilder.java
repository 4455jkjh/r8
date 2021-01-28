// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.ir.desugar.InterfaceMethodRewriter.COMPANION_CLASS_NAME_SUFFIX;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.dexsplitter.SplitterTestBase.RunInterface;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public abstract class TestShrinkerBuilder<
        C extends BaseCompilerCommand,
        B extends BaseCompilerCommand.Builder<C, B>,
        CR extends TestCompileResult<CR, RR>,
        RR extends TestRunResult<RR>,
        T extends TestShrinkerBuilder<C, B, CR, RR, T>>
    extends TestCompilerBuilder<C, B, CR, RR, T> {

  protected boolean enableTreeShaking = true;
  protected boolean enableOptimization = true;
  protected boolean enableMinification = true;

  private final Set<Class<? extends Annotation>> addedTestingAnnotations =
      Sets.newIdentityHashSet();

  TestShrinkerBuilder(TestState state, B builder, Backend backend) {
    super(state, builder, backend);
  }

  public boolean isProguardTestBuilder() {
    return false;
  }

  @Override
  public boolean isTestShrinkerBuilder() {
    return true;
  }

  @Override
  public T setMinApi(AndroidApiLevel minApiLevel) {
    if (backend == Backend.DEX) {
      return super.setMinApi(minApiLevel.getLevel());
    }
    return self();
  }

  public T treeShaking(boolean enable) {
    enableTreeShaking = enable;
    return self();
  }

  public T noTreeShaking() {
    return treeShaking(false);
  }

  public T optimization(boolean enable) {
    enableOptimization = enable;
    return self();
  }

  public T noOptimization() {
    return optimization(false);
  }

  public T minification(boolean enable) {
    enableMinification = enable;
    return self();
  }

  public T noMinification() {
    return minification(false);
  }

  public T addClassObfuscationDictionary(String... names) throws IOException {
    Path path = getState().getNewTempFolder().resolve("classobfuscationdictionary.txt");
    FileUtils.writeTextFile(path, StringUtils.join(" ", names));
    return addKeepRules("-classobfuscationdictionary " + path.toString());
  }

  public abstract T addDataEntryResources(DataEntryResource... resources);

  public abstract T addKeepRuleFiles(List<Path> files);

  public T addKeepRuleFiles(Path... files) {
    return addKeepRuleFiles(Arrays.asList(files));
  }

  public abstract T addKeepRules(Collection<String> rules);

  public T addKeepRules(String... rules) {
    return addKeepRules(Arrays.asList(rules));
  }

  public T addDontWarn(Class<?> clazz) {
    return addDontWarn(clazz.getTypeName());
  }

  public T addDontWarn(String className) {
    return addKeepRules("-dontwarn " + className);
  }

  public T addDontWarn(String... classes) {
    for (String clazz : classes) {
      addKeepRules("-dontwarn " + clazz);
    }
    return self();
  }

  @Deprecated
  public T addDontWarnCompanionClass(Class<?> clazz) {
    return addDontWarn(clazz.getTypeName() + COMPANION_CLASS_NAME_SUFFIX);
  }

  @Deprecated
  public T addDontWarnCompanionClasses() {
    return addDontWarn("**" + COMPANION_CLASS_NAME_SUFFIX);
  }

  @Deprecated
  public T addDontWarnCompilerSynthesizedAnnotations() {
    return addDontWarnCompilerSynthesizedClassAnnotation()
        .addDontWarnCompilerSynthesizedClassMapAnnotation();
  }

  @Deprecated
  public T addDontWarnCompilerSynthesizedClassAnnotation() {
    return addDontWarn("com.android.tools.r8.annotations.SynthesizedClass");
  }

  @Deprecated
  public T addDontWarnCompilerSynthesizedClassMapAnnotation() {
    return addDontWarn("com.android.tools.r8.annotations.SynthesizedClassMap");
  }

  // TODO(b/176143558): Should not report missing classes for compiler synthesized classes.
  @Deprecated
  public T addDontWarnEmulatedLibraryClass(Class<?> clazz) {
    return addDontWarn(clazz.getTypeName() + "$-EL");
  }

  // TODO(b/176143558): Should not report missing classes for compiler synthesized classes.
  @Deprecated
  public T addDontWarnEmulatedLibraryClasses() {
    return addDontWarn("**$-EL");
  }

  public T addDontWarnGoogle() {
    return addDontWarn("com.google.**");
  }

  public T addDontWarnJavax() {
    return addDontWarn("javax.**");
  }

  public T addDontWarnJavaxNullableAnnotation() {
    return addDontWarn("javax.annotation.Nullable");
  }

  public T addDontWarnJavaLangInvoke() {
    return addDontWarn("java.lang.invoke.*");
  }

  public T addDontWarnJavaLangInvoke(String className) {
    return addDontWarn("java.lang.invoke." + className);
  }

  public T addDontWarnJavaNioFile() {
    return addDontWarn("java.nio.file.**");
  }

  // TODO(b/176133676): Investigate why there are missing class references to org.jetbrains
  @Deprecated
  public T addDontWarnJetBrains() {
    return addDontWarn("org.jetbrains.**");
  }

  public T addDontWarnJetBrainsAnnotations() {
    return addDontWarnJetBrainsNotNullAnnotation().addDontWarnJetBrainsNullableAnnotation();
  }

  public T addDontWarnJetBrainsNotNullAnnotation() {
    return addDontWarn("org.jetbrains.annotations.NotNull");
  }

  public T addDontWarnJetBrainsNullableAnnotation() {
    return addDontWarn("org.jetbrains.annotations.Nullable");
  }

  // TODO(b/176133676): Should not report missing classes for Kotlin classes.
  @Deprecated
  public T addDontWarnKotlin() {
    return addDontWarn("kotlin.**");
  }

  // TODO(b/176133676): Should not report missing classes for Kotlin metadata.
  @Deprecated
  public T addDontWarnKotlinMetadata() {
    return addDontWarn("kotlin.Metadata");
  }

  // TODO(b/176133676): Investigate kotlinx missing class references.
  @Deprecated
  public T addDontWarnKotlinx() {
    return addDontWarn("kotlinx.**");
  }

  // TODO(b/176144018): Should not report compiler synthesized references as missing.
  @Deprecated
  public T addDontWarnRetargetLibraryMembers() {
    return addDontWarn("j$.retarget.$r8$retargetLibraryMember**");
  }

  @Deprecated
  public T addDontWarnRetargetLibraryMember(String suffix) {
    return addDontWarn("j$.retarget.$r8$retargetLibraryMember$" + suffix);
  }

  // TODO(b/154849103): Should not warn about SerializedLambda.
  @Deprecated
  public T addDontWarnSerializedLambda() {
    return addDontWarn("java.lang.invoke.SerializedLambda");
  }

  // TODO(b/176781593): Should not be reported missing.
  @Deprecated
  public T addDontWarnTimeConversions() {
    return addDontWarn("java.time.TimeConversions");
  }

  // TODO(b/176144018): Should not report compiler synthesized references as missing.
  @Deprecated
  public T addDontWarnVivifiedClass(Class<?> clazz) {
    return addDontWarn("$-vivified-$." + clazz.getTypeName());
  }

  @Deprecated
  public T addDontWarnVivifiedClasses() {
    return addDontWarn("$-vivified-$.**");
  }

  public T addKeepKotlinMetadata() {
    return addKeepRules("-keep class kotlin.Metadata { *; }");
  }

  public T addKeepAllClassesRule() {
    return addKeepRules("-keep class ** { *; }");
  }

  public T addKeepAllClassesRuleWithAllowObfuscation() {
    return addKeepRules("-keep,allowobfuscation class ** { *; }");
  }

  public T addKeepAllInterfacesRule() {
    return addKeepRules("-keep interface ** { *; }");
  }

  public T addKeepClassRules(Class<?>... classes) {
    return addKeepClassRules(
        Arrays.stream(classes).map(Class::getTypeName).toArray(String[]::new));
  }

  public T addKeepClassRules(String... classes) {
    for (String clazz : classes) {
      addKeepRules("-keep class " + clazz);
    }
    return self();
  }

  public T addKeepClassRulesWithAllowObfuscation(Class<?>... classes) {
    return addKeepClassRulesWithAllowObfuscation(
        Arrays.stream(classes).map(Class::getTypeName).toArray(String[]::new));
  }

  public T addKeepClassRulesWithAllowObfuscation(String... classes) {
    for (String clazz : classes) {
      addKeepRules("-keep,allowobfuscation class " + clazz);
    }
    return self();
  }

  public T addKeepClassAndMembersRules(Class<?>... classes) {
    return addKeepClassAndMembersRules(
        Arrays.stream(classes).map(Class::getTypeName).toArray(String[]::new));
  }

  public T addKeepClassAndMembersRules(String... classes) {
    for (String clazz : classes) {
      addKeepRules("-keep class " + clazz + " { *; }");
    }
    return self();
  }

  public T addKeepClassAndMembersRulesWithAllowObfuscation(Class<?>... classes) {
    return addKeepClassAndMembersRulesWithAllowObfuscation(
        Arrays.stream(classes).map(Class::getTypeName).toArray(String[]::new));
  }

  public T addKeepClassAndMembersRulesWithAllowObfuscation(String... classes) {
    for (String clazz : classes) {
      addKeepRules("-keep,allowobfuscation class " + clazz + " { *; }");
    }
    return self();
  }

  public T addKeepClassAndDefaultConstructor(Class<?>... classes) {
    return addKeepClassAndDefaultConstructor(
        Arrays.stream(classes).map(Class::getTypeName).toArray(String[]::new));
  }

  public T addKeepClassAndDefaultConstructor(String... classes) {
    for (String clazz : classes) {
      addKeepRules("-keep class " + clazz + " { <init>(); }");
    }
    return self();
  }

  public T addKeepPackageRules(Package pkg) {
    return addKeepRules("-keep class " + pkg.getName() + ".*");
  }

  public T addKeepPackageNamesRule(Package pkg) {
    return addKeepRules("-keeppackagenames " + pkg.getName());
  }

  public T addKeepMainRule(Class<?> mainClass) {
    return addKeepMainRule(mainClass.getTypeName());
  }

  public T addKeepMainRules(Class<?>... mainClasses) {
    for (Class<?> mainClass : mainClasses) {
      this.addKeepMainRule(mainClass);
    }
    return self();
  }

  public T addKeepMainRule(String mainClass) {
    return addKeepRules(
        "-keep class " + mainClass + " { public static void main(java.lang.String[]); }");
  }

  public T addKeepMainRules(List<String> mainClasses) {
    mainClasses.forEach(this::addKeepMainRule);
    return self();
  }

  public T addKeepFeatureMainRule(Class<?> mainClass) {
    return addKeepFeatureMainRule(mainClass.getTypeName());
  }

  public T addKeepFeatureMainRules(Class<?>... mainClasses) {
    for (Class<?> mainClass : mainClasses) {
      this.addKeepFeatureMainRule(mainClass);
    }
    return self();
  }

  public T addKeepFeatureMainRule(String mainClass) {
    return addKeepRules(
        "-keep public class " + mainClass,
        "    implements " + RunInterface.class.getTypeName() + " {",
        "  public void <init>();",
        "  public void run();",
        "}");
  }

  public T addKeepFeatureMainRules(List<String> mainClasses) {
    mainClasses.forEach(this::addKeepFeatureMainRule);
    return self();
  }

  public T addKeepMethodRules(Class<?> clazz, String... methodSignatures) {
    StringBuilder sb = new StringBuilder();
    sb.append("-keep class " + clazz.getTypeName() + " {\n");
    for (String methodSignature : methodSignatures) {
      sb.append("  " + methodSignature + ";\n");
    }
    sb.append("}");
    addKeepRules(sb.toString());
    return self();
  }

  public T addKeepMethodRules(MethodReference... methods) {
    for (MethodReference method : methods) {
      addKeepRules(
          "-keep class "
              + method.getHolderClass().getTypeName()
              + " { "
              + getMethodLine(method)
              + " }");
    }
    return self();
  }

  public T addPrintSeeds() {
    return addKeepRules("-printseeds");
  }

  public T allowAccessModification() {
    return allowAccessModification(true);
  }

  public T allowAccessModification(boolean allowAccessModification) {
    if (allowAccessModification) {
      return addKeepRules("-allowaccessmodification");
    }
    return self();
  }

  public T addKeepAttributes(String... attributes) {
    return addKeepRules("-keepattributes " + String.join(",", attributes));
  }

  public T addKeepAttributeInnerClassesAndEnclosingMethod() {
    return addKeepAttributes(
        ProguardKeepAttributes.INNER_CLASSES, ProguardKeepAttributes.ENCLOSING_METHOD);
  }

  public T addKeepAttributeLineNumberTable() {
    return addKeepAttributes(ProguardKeepAttributes.LINE_NUMBER_TABLE);
  }

  public T addKeepAttributeSignature() {
    return addKeepAttributes(ProguardKeepAttributes.SIGNATURE);
  }

  public T addKeepAttributeSourceFile() {
    return addKeepAttributes(ProguardKeepAttributes.SOURCE_FILE);
  }

  public T addKeepRuntimeVisibleAnnotations() {
    return addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS);
  }

  public T addKeepRuntimeVisibleParameterAnnotations() {
    return addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS);
  }

  public T addKeepAllAttributes() {
    return addKeepAttributes("*");
  }

  public abstract T addApplyMapping(String proguardMap);

  public final T addAlwaysInliningAnnotations() {
    return addTestingAnnotation(AlwaysInline.class);
  }

  public final T addAssumeNotNullAnnotation() {
    return addTestingAnnotation(AssumeNotNull.class);
  }

  public final T addAssumeNoClassInitializationSideEffectsAnnotation() {
    return addTestingAnnotation(AssumeNoClassInitializationSideEffects.class);
  }

  public final T addAssumeNoSideEffectsAnnotations() {
    return addTestingAnnotation(AssumeNoSideEffects.class);
  }

  public final T addConstantArgumentAnnotations() {
    return addTestingAnnotation(KeepConstantArguments.class);
  }

  public final T addForceInliningAnnotations() {
    return addTestingAnnotation(ForceInline.class);
  }

  public final T addInliningAnnotations() {
    return addTestingAnnotation(NeverInline.class);
  }

  public final T addKeepAnnotation() {
    return addTestingAnnotation(Keep.class);
  }

  public final T addMemberValuePropagationAnnotations() {
    return addTestingAnnotation(NeverPropagateValue.class);
  }

  public final T addNeverClassInliningAnnotations() {
    return addTestingAnnotation(NeverClassInline.class);
  }

  public final T addNeverReprocessClassInitializerAnnotations() {
    return addTestingAnnotation(NeverReprocessClassInitializer.class);
  }

  public final T addNeverReprocessMethodAnnotations() {
    return addTestingAnnotation(NeverReprocessMethod.class);
  }

  public final T addNeverSingleCallerInlineAnnotations() {
    return addTestingAnnotation(NeverSingleCallerInline.class);
  }

  public final T addNoHorizontalClassMergingAnnotations() {
    return addTestingAnnotation(NoHorizontalClassMerging.class);
  }

  public final T addNoStaticClassMergingAnnotations() {
    return addTestingAnnotation(NoStaticClassMerging.class);
  }

  public final T addNoUnusedInterfaceRemovalAnnotations() {
    return addTestingAnnotation(NoUnusedInterfaceRemoval.class);
  }

  public final T addNoVerticalClassMergingAnnotations() {
    return addTestingAnnotation(NoVerticalClassMerging.class);
  }

  public final T addReprocessClassInitializerAnnotations() {
    return addTestingAnnotation(ReprocessClassInitializer.class);
  }

  public final T addReprocessMethodAnnotations() {
    return addTestingAnnotation(ReprocessMethod.class);
  }

  public final T addSideEffectAnnotations() {
    return addTestingAnnotation(AssumeMayHaveSideEffects.class);
  }

  public final T addUnusedArgumentAnnotations() {
    return addTestingAnnotation(KeepUnusedArguments.class);
  }

  private T addTestingAnnotation(Class<? extends Annotation> clazz) {
    return addedTestingAnnotations.add(clazz) ? addProgramClasses(clazz) : self();
  }

  private static String getMethodLine(MethodReference method) {
    // Should we encode modifiers in method references?
    StringBuilder builder = new StringBuilder();
    builder
        .append(method.getReturnType() == null ? "void" : method.getReturnType().getTypeName())
        .append(' ')
        .append(method.getMethodName())
        .append("(");
    boolean first = true;
    for (TypeReference parameterType : method.getFormalTypes()) {
      if (!first) {
        builder.append(", ");
      }
      builder.append(parameterType.getTypeName());
      first = false;
    }
    return builder.append(");").toString();
  }
}
