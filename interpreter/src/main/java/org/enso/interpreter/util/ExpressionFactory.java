package org.enso.interpreter.util;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import org.enso.interpreter.*;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.StatementNode;
import org.enso.interpreter.node.controlflow.*;
import org.enso.interpreter.node.expression.ForeignCallNode;
import org.enso.interpreter.node.expression.literal.IntegerLiteralNode;
import org.enso.interpreter.node.expression.operator.*;
import org.enso.interpreter.runtime.FramePointer;
import scala.collection.JavaConversions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private Map<String, FrameSlot> items;
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

    public FrameSlot createVarSlot(String name) {
      if (items.containsKey(name)) throw new VariableRedefinitionException(name);
      FrameSlot slot = frameDescriptor.addFrameSlot(name);
      items.put(name, slot);
      return slot;
    }

    public FramePointer getSlot(String name) {
      LocalScope scope = this;
      while (scope != null) {
        FrameSlot slot = scope.items.get(name);
        if (slot != null) return new FramePointer(scope.frameDescriptor, slot);
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
      FrameSlot slot = scope.createVarSlot(((EnsoAssign) root).name());
      return new AssignmentNode(slot, run(((EnsoAssign) root).body()));
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
      List<StatementNode> argRewrites = new ArrayList<>();
      for (int i = 0; i < arguments.size(); i++) {
        FrameSlot slot = scope.createVarSlot(arguments.get(i));
        ReadArgumentNode readArg = new ReadArgumentNode(i + 1);
        AssignmentNode assignArg = new AssignmentNode(slot, readArg);
        argRewrites.add(assignArg);
      }
      List<EnsoAst> statements = JavaConversions.seqAsJavaList(((EnsoBlock) root).statements());
      List<StatementNode> statementNodes =
          statements.stream().map(this::runStmt).collect(Collectors.toList());
      argRewrites.addAll(statementNodes);
      ExpressionNode expr = run(((EnsoBlock) root).ret());
      BlockNode blockNode =
          new BlockNode(
              language,
              scope.frameDescriptor,
              argRewrites.stream().toArray(StatementNode[]::new),
              expr);
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
