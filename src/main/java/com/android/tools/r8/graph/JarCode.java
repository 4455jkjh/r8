// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.InvalidDebugInfoException;
import com.android.tools.r8.graph.JarClassFileReader.ReparseContext;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.ValueNumberGenerator;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.JarSourceCode;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.jar.InliningConstraintVisitor;
import com.android.tools.r8.jar.JarArgumentUseVisitor;
import com.android.tools.r8.jar.JarRegisterEffectsVisitor;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OffOrAuto;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.function.BiFunction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

public class JarCode extends Code {

  // TODO(zerny): Write via the IR.
  public void writeTo(MethodVisitor visitor) {
    node.accept(visitor);
  }

  private final DexMethod method;
  private final Origin origin;
  private MethodNode node;
  protected ReparseContext context;
  protected final JarApplicationReader application;
  private boolean reachabilitySensitive = false;

  public JarCode(
      DexMethod method, Origin origin, ReparseContext context, JarApplicationReader application) {
    this.method = method;
    this.origin = origin;
    this.context = context;
    this.application = application;
    context.codeList.add(this);
  }

  public void markReachabilitySensitive() {
    // We need to mark before we have reparsed so that the method code is reparsed
    // including debug information.
    assert context != null;
    this.reachabilitySensitive = true;
  }

  public MethodNode getNode() {
    triggerDelayedParsingIfNeccessary();
    return node;
  }

  @Override
  public boolean isJarCode() {
    return true;
  }

  @Override
  public JarCode asJarCode() {
    return this;
  }

  @Override
  protected int computeHashCode() {
    triggerDelayedParsingIfNeccessary();
    return node.hashCode();
  }

  @Override
  protected boolean computeEquals(Object other) {
    triggerDelayedParsingIfNeccessary();
    if (this == other) {
      return true;
    }
    if (other instanceof JarCode) {
      JarCode o = (JarCode) other;
      o.triggerDelayedParsingIfNeccessary();
      // TODO(zerny): This amounts to object equality.
      return node.equals(o.node);
    }
    return false;
  }

  @Override
  public boolean isEmptyVoidMethod() {
    for (Iterator<AbstractInsnNode> it = getNode().instructions.iterator(); it.hasNext(); ) {
      AbstractInsnNode insn = it.next();
      if (insn.getType() != Opcodes.RETURN
          && !(insn instanceof LabelNode)
          && !(insn instanceof LineNumberNode)) {
        return false;
      }
    }
    return true;
  }

  public boolean hasLocalVariableTable() {
    return getNode().localVariables != null && !getNode().localVariables.isEmpty();
  }

  @Override
  public IRCode buildIR(
      DexEncodedMethod encodedMethod, AppView<? extends AppInfo> appView, Origin origin) {
    return internalBuildPossiblyWithLocals(encodedMethod, encodedMethod, appView, null, null);
  }

  @Override
  public IRCode buildInliningIR(
      DexEncodedMethod context,
      DexEncodedMethod encodedMethod,
      AppView<? extends AppInfo> appView,
      ValueNumberGenerator generator,
      Position callerPosition,
      Origin origin) {
    assert generator != null;
    return internalBuildPossiblyWithLocals(
        context, encodedMethod, appView, generator, callerPosition);
  }

  private IRCode internalBuildPossiblyWithLocals(
      DexEncodedMethod context,
      DexEncodedMethod encodedMethod,
      AppView<? extends AppInfo> appView,
      ValueNumberGenerator generator,
      Position callerPosition) {
    assert getOwner() == encodedMethod;
    triggerDelayedParsingIfNeccessary();
    if (!keepLocals(encodedMethod, appView.options())) {
      // We strip locals here because we will not be able to recover from invalid info.
      node.localVariables.clear();
      return internalBuild(context, encodedMethod, appView, generator, callerPosition);
    } else {
      return internalBuildWithLocals(context, encodedMethod, appView, generator, callerPosition);
    }
  }

  private IRCode internalBuildWithLocals(
      DexEncodedMethod context,
      DexEncodedMethod encodedMethod,
      AppView<? extends AppInfo> appView,
      ValueNumberGenerator generator,
      Position callerPosition) {
    try {
      return internalBuild(context, encodedMethod, appView, generator, callerPosition);
    } catch (InvalidDebugInfoException e) {
      appView.options().warningInvalidDebugInfo(encodedMethod, origin, e);
      node.localVariables.clear();
      return internalBuild(context, encodedMethod, appView, generator, callerPosition);
    }
  }

  private boolean keepLocals(DexEncodedMethod encodedMethod, InternalOptions options) {
    if (options.testing.noLocalsTableOnInput) {
      return false;
    }
    if (options.debug || encodedMethod.getOptimizationInfo().isReachabilitySensitive()) {
      return true;
    }
    return false;
  }

  private IRCode internalBuild(
      DexEncodedMethod context,
      DexEncodedMethod encodedMethod,
      AppView<? extends AppInfo> appView,
      ValueNumberGenerator generator,
      Position callerPosition) {
    assert node.localVariables.isEmpty() || keepLocals(encodedMethod, appView.options());
    JarSourceCode source =
        new JarSourceCode(
            method.holder,
            node,
            application,
            appView.graphLense().getOriginalMethodSignature(encodedMethod.method),
            callerPosition);
    IRBuilder builder = new IRBuilder(encodedMethod, appView, source, origin, generator);
    return builder.build(context);
  }

  @Override
  public void registerCodeReferences(UseRegistry registry) {
    triggerDelayedParsingIfNeccessary();
    node.instructions.accept(
        new JarRegisterEffectsVisitor(method.holder, registry, application));
    for (TryCatchBlockNode tryCatchBlockNode : node.tryCatchBlocks) {
      // Exception type can be null for "catch all" used for try/finally.
      if (tryCatchBlockNode.type != null) {
        registry.registerTypeReference(application.getTypeFromDescriptor(
            DescriptorUtils.getDescriptorFromClassBinaryName(tryCatchBlockNode.type)));
      }
    }
  }

  @Override
  public void registerArgumentReferences(ArgumentUse registry) {
    triggerDelayedParsingIfNeccessary();
    node.instructions.accept(new JarArgumentUseVisitor(getOwner(), registry));
  }

  public ConstraintWithTarget computeInliningConstraint(
      DexEncodedMethod encodedMethod,
      AppView<AppInfoWithLiveness> appView,
      GraphLense graphLense,
      DexType invocationContext) {
    triggerDelayedParsingIfNeccessary();

    InliningConstraintVisitor visitor =
        new InliningConstraintVisitor(
            application, appView, graphLense, encodedMethod, invocationContext);

    if (appView.options().enableDesugaring
        && appView.options().interfaceMethodDesugaring == OffOrAuto.Auto
        && !appView.options().canUseDefaultAndStaticInterfaceMethods()) {
      // TODO(b/120130831): Conservatively need to say "no" at this point if there are invocations
      // to static interface methods. This should be fixed by making sure that the desugared
      // versions of default and static interface methods are present in the application during
      // IR processing.
      visitor.disallowStaticInterfaceMethodCalls();
    }

    AbstractInsnNode insn = node.instructions.getFirst();
    while (insn != null) {
      insn.accept(visitor);
      if (visitor.isFinished()) {
        return visitor.getConstraint();
      }
      insn = insn.getNext();
    }
    for (TryCatchBlockNode block : node.tryCatchBlocks) {
      visitor.accept(block);
      if (visitor.isFinished()) {
        return visitor.getConstraint();
      }
    }
    return visitor.getConstraint();
  }

  @Override
  public String toString() {
    triggerDelayedParsingIfNeccessary();
    TraceMethodVisitor visitor = new TraceMethodVisitor(new Textifier());
    node.accept(visitor);
    StringWriter writer = new StringWriter();
    visitor.p.print(new PrintWriter(writer));
    return writer.toString();
  }

  @Override
  public String toString(DexEncodedMethod method, ClassNameMapper naming) {
    return toString();
  }

  private void triggerDelayedParsingIfNeccessary() {
    if (this.context != null) {
      // The SecondVisitor is in charge of setting this.context to null.
      ReparseContext context = this.context;
      parseCode(context, false);
      if (hasJsr(context)) {
        parseCode(context, true);
        assert !hasJsr(context);
      }
      assert verifyNoReparseContext(context.owner);
    }
  }

  private void parseCode(ReparseContext context, boolean useJsrInliner) {
    // If the keep attributes do not specify keeping LocalVariableTable, LocalVariableTypeTable or
    // LineNumberTable, then we can skip parsing all the debug related attributes during code read.
    // If the method is reachability sensitive we have to include debug information in order
    // to get locals information which we need to extend the live ranges of locals for their
    // entire scope.
    int parsingOptions = ClassReader.SKIP_FRAMES;

    if (application.options.getProguardConfiguration() != null) {
      ProguardKeepAttributes keep =
          application.options.getProguardConfiguration().getKeepAttributes();
      if (!keep.localVariableTable
          && !keep.localVariableTypeTable
          && !keep.lineNumberTable
          && !reachabilitySensitive) {
        parsingOptions |= ClassReader.SKIP_DEBUG;
      }
    }
    SecondVisitor classVisitor = new SecondVisitor(createCodeLocator(context), useJsrInliner);
    try {
      new ClassReader(context.classCache).accept(classVisitor, parsingOptions);
    } catch (Exception exception) {
      throw new CompilationError(
          "Unable to parse method `" + method.toSourceString() + "`", exception);
    }
  }

  protected BiFunction<String, String, JarCode> createCodeLocator(ReparseContext context) {
    return new DefaultCodeLocator(context, application);
  }

  private boolean hasJsr(ReparseContext context) {
    for (Code code : context.codeList) {
      if (hasJsr(code.asJarCode().node)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasJsr(MethodNode node) {
    Iterator<AbstractInsnNode> it = node.instructions.iterator();
    while (it.hasNext()) {
      int opcode = it.next().getOpcode();
      if (opcode == Opcodes.JSR || opcode == Opcodes.RET) {
        return true;
      }
    }
    return false;
  }

  private static class DefaultCodeLocator implements BiFunction<String, String, JarCode> {
    private final ReparseContext context;
    private final JarApplicationReader application;
    private int methodIndex = 0;

    private DefaultCodeLocator(ReparseContext context, JarApplicationReader application) {
      this.context = context;
      this.application = application;
    }

    @Override
    public JarCode apply(String name, String desc) {
      JarCode code = context.codeList.get(methodIndex++).asJarCode();
      assert code.method == application.getMethod(context.owner.type, name, desc);
      return code;
    }
  }

  /**
   * Fills the MethodNodes of all the methods in the class and removes the ReparseContext.
   */
  private static class SecondVisitor extends ClassVisitor {
    private final BiFunction<String, String, JarCode> codeLocator;
    private final boolean useJsrInliner;

    public SecondVisitor(BiFunction<String, String, JarCode> codeLocator, boolean useJsrInliner) {
      super(Opcodes.ASM6);
      this.codeLocator = codeLocator;
      this.useJsrInliner = useJsrInliner;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
        String[] exceptions) {
      MethodNode node =
          useJsrInliner
              ? new JSRInlinerAdapter(null, access, name, desc, signature, exceptions)
              : new MethodNode(Opcodes.ASM6, access, name, desc, signature, exceptions);
      JarCode code = null;
      MethodAccessFlags flags = JarClassFileReader.createMethodAccessFlags(name, access);
      if (!flags.isAbstract() && !flags.isNative()) {
        code = codeLocator.apply(name, desc);
      }
      if (code != null) {
        code.context = null;
        code.node = node;
        return node;
      }
      return null;
    }
  }

  private static boolean verifyNoReparseContext(DexProgramClass owner) {
    for (DexEncodedMethod method : owner.virtualMethods()) {
      Code code = method.getCode();
      if (code != null && code.isJarCode()) {
        if (code.asJarCode().context != null) {
          return false;
        }
      }
    }

    for (DexEncodedMethod method : owner.directMethods()) {
      Code code = method.getCode();
      if (code != null && code.isJarCode()) {
        if (code.asJarCode().context != null) {
          return false;
        }
      }
    }
    return true;
  }
}
