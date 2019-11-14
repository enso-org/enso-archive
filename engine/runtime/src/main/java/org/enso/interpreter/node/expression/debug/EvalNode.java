package org.enso.interpreter.node.expression.debug;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import org.enso.compiler.Compiler;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.node.ClosureRootNode;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.callable.argument.ThunkExecutorNode;
import org.enso.interpreter.node.expression.builtin.debug.EvalFunNode;
import org.enso.interpreter.runtime.Context;
import org.enso.interpreter.runtime.callable.CallerInfo;
import org.enso.interpreter.runtime.callable.argument.Thunk;
import org.enso.interpreter.runtime.scope.LocalScope;
import org.enso.interpreter.runtime.scope.ModuleScope;
import org.enso.interpreter.runtime.state.Stateful;
import org.enso.syntax.text.AST;

public abstract class EvalNode extends BaseNode {
  private @CompilerDirectives.CompilationFinal boolean captureFrame = false;

  public static EvalNode build() {
    return EvalNodeGen.create();
  }

  public EvalNode captureFrame() {
    CompilerDirectives.transferToInterpreterAndInvalidate();
    this.captureFrame = true;
    return this;
  }

  public abstract Stateful execute(CallerInfo callerInfo, Object state, String expression);

  RootCallTarget parseExpression(LocalScope scope, ModuleScope moduleScope, String expression) {
    LocalScope localScope = new LocalScope(scope);
    Language language = lookupLanguageReference(Language.class).get();
    ExpressionNode expr =
        lookupContextReference(Language.class)
            .get()
            .compiler()
            .runInline(expression, language, localScope, moduleScope);
    ClosureRootNode framedNode =
        new ClosureRootNode(
            lookupLanguageReference(Language.class).get(),
            localScope,
            moduleScope,
            captureFrame ? new CaptureFrameNode(expr) : expr,
            null,
            "interactive");
    framedNode.setTail(isTail());
    RootCallTarget ct = Truffle.getRuntime().createCallTarget(framedNode);
    return ct;
  }

  @Specialization(guards = "expression == cachedExpression", limit = "10")
  Stateful doCached(
      CallerInfo callerInfo,
      Object state,
      String expression,
      @Cached("expression") String cachedExpression,
      @Cached(
              "parseExpression(callerInfo.getLocalScope(), callerInfo.getModuleScope(), expression)")
          RootCallTarget cachedCallTarget,
      @Cached("build(isTail())") ThunkExecutorNode thunkExecutorNode) {
    Thunk thunk = new Thunk(cachedCallTarget, callerInfo.getFrame());
    return thunkExecutorNode.executeThunk(thunk, state);
  }
}
