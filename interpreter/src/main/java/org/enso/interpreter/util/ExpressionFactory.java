package org.enso.interpreter.util;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import org.enso.interpreter.*;
import org.enso.interpreter.node.EnsoRootNode;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.StatementNode;
import org.enso.interpreter.node.controlflow.*;
import org.enso.interpreter.node.expression.literal.IntegerLiteralNode;
import org.enso.interpreter.node.expression.operator.*;
import org.enso.interpreter.node.function.CreateFunctionNode;
import org.enso.interpreter.node.function.FunctionBodyNode;
import org.enso.interpreter.node.function.InvokeNode;
import org.enso.interpreter.node.function.ReadArgumentNode;
import org.enso.interpreter.runtime.FramePointer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExpressionFactory
    implements AstExpressionVisitor<ExpressionNode>,
        AstStatementVisitor<StatementNode, ExpressionNode> {

  public static class VariableRedefinitionException extends RuntimeException
      implements TruffleException {
    public VariableRedefinitionException(String name) {
      super("Variable " + name + " was already defined in this scope.");
    }

    @Override
    public Node getLocation() {
      return null;
    }
  }

  public static class VariableDoesNotExistException extends RuntimeException
      implements TruffleException {
    public VariableDoesNotExistException(String name) {
      super("Variable " + name + " is not defined.");
    }

    @Override
    public Node getLocation() {
      return null;
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
      int parentCounter = 0;
      while (scope != null) {
        FrameSlot slot = scope.items.get(name);
        if (slot != null) {
          return new FramePointer(parentCounter, slot);
        }
        scope = scope.parent;
        parentCounter++;
      }
      throw new VariableDoesNotExistException(name);
    }
  }

  private final LocalScope scope;
  private final Language language;

  public ExpressionFactory(Language language, LocalScope scope) {
    this.language = language;
    this.scope = scope;
  }

  public ExpressionFactory(Language language) {
    this(language, new LocalScope());
  }

  public ExpressionFactory createChild() {
    return new ExpressionFactory(language, scope.createChild());
  }

  public ExpressionNode run(AstExpression body) {
    ExpressionNode result = body.visitExpression(this);
    result.markNotTail();
    return result;
  }

  public ExpressionNode visitLong(long l) {
    return new IntegerLiteralNode(l);
  }

  @Override
  public ExpressionNode visitArithOp(
      String operator, AstExpression leftAst, AstExpression rightAst) {
    ExpressionNode left = leftAst.visitExpression(this);
    ExpressionNode right = rightAst.visitExpression(this);
    if (operator.equals("+")) return AddOperatorNodeGen.create(left, right);
    if (operator.equals("-")) return SubtractOperatorNodeGen.create(left, right);
    if (operator.equals("*")) return MultiplyOperatorNodeGen.create(left, right);
    if (operator.equals("/")) return DivideOperatorNodeGen.create(left, right);
    return null;
  }

  @Override
  public ExpressionNode visitForeign(String lang, String code) {
    return null;
  }

  @Override
  public ExpressionNode visitVariable(String name) {
    FramePointer slot = scope.getSlot(name);
    return ReadLocalVariableNodeGen.create(slot);
  }

  public ExpressionNode processFunctionBody(
      List<String> arguments, List<AstStatement> statements, AstExpression retValue) {
    List<StatementNode> argRewrites = new ArrayList<>();
    for (int i = 0; i < arguments.size(); i++) {
      FrameSlot slot = scope.createVarSlot(arguments.get(i));
      ReadArgumentNode readArg = new ReadArgumentNode(i);
      AssignmentNode assignArg = new AssignmentNode(slot, readArg);
      argRewrites.add(assignArg);
    }
    List<StatementNode> statementNodes =
        statements.stream().map(stmt -> stmt.visit(this)).collect(Collectors.toList());
    List<StatementNode> allStatements = new ArrayList<>();
    allStatements.addAll(argRewrites);
    allStatements.addAll(statementNodes);
    ExpressionNode expr = retValue.visitExpression(this);
    FunctionBodyNode functionBodyNode = new FunctionBodyNode(allStatements.toArray(new StatementNode[0]), expr);
    RootNode rootNode =
        new EnsoRootNode(language, scope.frameDescriptor, functionBodyNode, null, "<lambda>");
    RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
    return new CreateFunctionNode(callTarget);
  }

  @Override
  public ExpressionNode visitFunction(
      List<String> arguments, List<AstStatement> statements, AstExpression retValue) {
    ExpressionFactory child = createChild();
    return child.processFunctionBody(arguments, statements, retValue);
  }

  @Override
  public ExpressionNode visitApplication(AstExpression function, List<AstExpression> arguments) {
    return new InvokeNode(
        function.visitExpression(this),
        arguments.stream().map(arg -> arg.visitExpression(this)).toArray(ExpressionNode[]::new));
  }

  @Override
  public ExpressionNode visitIf(AstExpression cond, AstExpression ifTrue, AstExpression ifFalse) {
    return new IfZeroNode(
        cond.visitExpression(this), ifTrue.visitExpression(this), ifFalse.visitExpression(this));
  }

  @Override
  public StatementNode visitAssignment(String varName, AstExpression expr) {
    FrameSlot slot = scope.createVarSlot(varName);
    return new AssignmentNode(slot, expr.visitExpression(this));
  }

  @Override
  public StatementNode visitPrint(AstExpression body) {
    return new PrintNode(body.visitExpression(this));
  }
}
