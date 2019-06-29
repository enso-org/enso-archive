package org.enso.interpreter.util;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import org.enso.interpreter.*;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.StatementNode;
import org.enso.interpreter.node.controlflow.*;
import org.enso.interpreter.node.expression.ForeignCallNode;
import org.enso.interpreter.node.expression.literal.IntegerLiteralNode;
import org.enso.interpreter.node.expression.operator.*;
import org.enso.interpreter.runtime.ArgPointer;
import org.enso.interpreter.runtime.FramePointer;
import org.enso.interpreter.runtime.VarPointer;
import scala.collection.JavaConversions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpressionFactory {

  public static class VariableRedefinitionException extends RuntimeException {
    public VariableRedefinitionException(String name) {
      super("Variable " + name + " was already defined in this scope.");
    }
  }

  public static class VariableDoesNotExistException extends RuntimeException {
    public VariableDoesNotExistException(String name) {
      super("Variable " + name + " is not defined.");
    }
  }

  public static class LocalScope {
    private Map<String, FramePointer> items;
    private FrameDescriptor frameDescriptor;

    public LocalScope getParent() {
      return parent;
    }

    private LocalScope parent;

    public LocalScope() {
      items = new HashMap<>();
      frameDescriptor = new FrameDescriptor();
      parent = null;
    }

    public LocalScope(LocalScope parent) {
      this();
      this.parent = parent;
    }

    public LocalScope createChild() {
      return new LocalScope(this);
    }

    public FramePointer createVarSlot(String name) {
      if (items.containsKey(name)) throw new VariableRedefinitionException(name);
      FrameSlot slot = frameDescriptor.addFrameSlot(name);
      FramePointer ptr = new VarPointer(frameDescriptor, slot);
      items.put(name, ptr);
      return ptr;
    }

    public FramePointer createArgSlot(String name, int idx) {
      if (items.containsKey(name)) throw new VariableRedefinitionException(name);
      FramePointer ptr = new ArgPointer(frameDescriptor, idx);
      items.put(name, ptr);
      return ptr;
    }

    public FramePointer getSlot(String name) {
      LocalScope scope = this;
      while (scope != null) {
        FramePointer ptr = scope.items.get(name);
        if (ptr != null) return ptr;
        scope = scope.parent;
      }
      throw new VariableDoesNotExistException(name);
    }
  }

  private LocalScope scope;
  private Language language;

  public ExpressionFactory(Language language) {
    language = language;
    scope = new LocalScope();
  }

  public ExpressionFactory(LocalScope scope) {
    this.scope = scope;
  }

  public StatementNode runStmt(EnsoAst root) {
    if (root instanceof EnsoAssign) {
      FramePointer ptr = scope.createVarSlot(((EnsoAssign) root).name());
      return new AssignmentNode(ptr, run(((EnsoAssign) root).body()));
    }
    if (root instanceof EnsoPrint) {
      return new PrintNode(run(((EnsoPrint) root).body()));
    }
    if (root instanceof EnsoJsCall) {
      List<EnsoAst> args = JavaConversions.seqAsJavaList(((EnsoJsCall) root).args());
      return new ForeignCallNode(
          "js",
          ((EnsoJsCall) root).code(),
          args.stream().map(this::run).toArray(ExpressionNode[]::new));
    }
    return run(root);
  }

  public ExpressionNode run(EnsoAst root) {
    if (root instanceof EnsoLong) {
      return new IntegerLiteralNode(((EnsoLong) root).l());
    }
    if (root instanceof EnsoReadVar) {
      FramePointer slot = scope.getSlot(((EnsoReadVar) root).name());
      return new ReadLocalVariableNode(slot);
    }
    if (root instanceof EnsoRunBlock) {
      List<EnsoAst> args = JavaConversions.seqAsJavaList(((EnsoRunBlock) root).args());
      return new ExecuteBlockNode(
          run(((EnsoRunBlock) root).block()),
          args.stream().map(this::run).toArray(ExpressionNode[]::new));
    }
    if (root instanceof EnsoBlock) {
      scope = scope.createChild();
      List<String> arguments = JavaConversions.seqAsJavaList(((EnsoBlock) root).arguments());
      for (int i = 0; i < arguments.size(); i++) {
        scope.createArgSlot(arguments.get(i), i + 1);
      }
      List<EnsoAst> statements = JavaConversions.seqAsJavaList(((EnsoBlock) root).statements());
      StatementNode[] statementNodes =
          statements.stream().map(this::runStmt).toArray(StatementNode[]::new);
      ExpressionNode expr = run(((EnsoBlock) root).ret());
      BlockNode blockNode = new BlockNode(language, scope.frameDescriptor, statementNodes, expr);
      ExpressionNode result = new CreateBlockNode(blockNode);
      scope = scope.getParent();
      return result;
    }
    if (root instanceof EnsoArithOp) {
      String operator = ((EnsoArithOp) root).op();
      ExpressionNode left = run(((EnsoArithOp) root).l());
      ExpressionNode right = run(((EnsoArithOp) root).r());
      if (operator.equals("+")) return AddOperatorNodeGen.create(left, right);
      if (operator.equals("-")) return SubtractOperatorNodeGen.create(left, right);
      if (operator.equals("*")) return MultiplyOperatorNodeGen.create(left, right);
      if (operator.equals("/")) return DivideOperatorNodeGen.create(left, right);
    }

    return null;
  }
}
