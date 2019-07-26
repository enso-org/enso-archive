package org.enso.interpreter.builder;

import com.oracle.truffle.api.frame.FrameSlot;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.enso.interpreter.AstArgDefinition;
import org.enso.interpreter.AstCase;
import org.enso.interpreter.AstCaseFunction;
import org.enso.interpreter.AstExpression;
import org.enso.interpreter.AstExpressionVisitor;
import org.enso.interpreter.Language;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.node.controlflow.CaseNode;
import org.enso.interpreter.node.controlflow.ConstructorCaseNode;
import org.enso.interpreter.node.controlflow.DefaultFallbackNode;
import org.enso.interpreter.node.controlflow.FallbackNode;
import org.enso.interpreter.node.controlflow.IfZeroNode;
import org.enso.interpreter.node.controlflow.MatchNode;
import org.enso.interpreter.node.expression.builtin.PrintNode;
import org.enso.interpreter.node.expression.constant.ConstructorNode;
import org.enso.interpreter.node.expression.literal.IntegerLiteralNode;
import org.enso.interpreter.node.expression.operator.AddOperatorNodeGen;
import org.enso.interpreter.node.expression.operator.DivideOperatorNodeGen;
import org.enso.interpreter.node.expression.operator.ModOperatorNodeGen;
import org.enso.interpreter.node.expression.operator.MultiplyOperatorNodeGen;
import org.enso.interpreter.node.expression.operator.SubtractOperatorNodeGen;
import org.enso.interpreter.node.function.InvokeNodeGen;
import org.enso.interpreter.node.function.argument.ArgumentDefinitionNode;
import org.enso.interpreter.node.scope.AssignmentNodeGen;
import org.enso.interpreter.node.scope.ReadGlobalTargetNode;
import org.enso.interpreter.node.scope.ReadLocalTargetNodeGen;
import org.enso.interpreter.runtime.errors.VariableDoesNotExistException;

public class ExpressionFactory implements AstExpressionVisitor<ExpressionNode> {

  private final LocalScope scope;
  private final Language language;
  private final String scopeName;
  private final GlobalScope globalScope;

  private String currentVarName = "annonymous";

  public ExpressionFactory(
      Language language, LocalScope scope, String name, GlobalScope globalScope) {
    this.language = language;
    this.scope = scope;
    this.scopeName = name;
    this.globalScope = globalScope;
  }

  public ExpressionFactory(Language lang, String scopeName, GlobalScope globalScope) {
    this(lang, new LocalScope(), scopeName, globalScope);
  }

  public ExpressionFactory(Language language, GlobalScope globalScope) {
    this(language, "<root>", globalScope);
  }

  public ExpressionFactory createChild(String name) {
    return new ExpressionFactory(language, scope.createChild(), name, this.globalScope);
  }

  public ExpressionNode run(AstExpression expr) {
    ExpressionNode result = expr.visit(this);
    result.markNotTail();
    return result;
  }

  public ExpressionNode visitLong(long l) {
    return new IntegerLiteralNode(l);
  }

  @Override
  public ExpressionNode visitArithOp(
      String operator, AstExpression leftAst, AstExpression rightAst) {
    ExpressionNode left = leftAst.visit(this);
    ExpressionNode right = rightAst.visit(this);
    if (operator.equals("+")) return AddOperatorNodeGen.create(left, right);
    if (operator.equals("-")) return SubtractOperatorNodeGen.create(left, right);
    if (operator.equals("*")) return MultiplyOperatorNodeGen.create(left, right);
    if (operator.equals("/")) return DivideOperatorNodeGen.create(left, right);
    if (operator.equals("%")) return ModOperatorNodeGen.create(left, right);
    return null;
  }

  @Override
  public ExpressionNode visitForeign(String lang, String code) {
    return null;
  }

  @Override
  public ExpressionNode visitVariable(String name) {
    Supplier<Optional<ExpressionNode>> localNode =
        () -> scope.getSlot(name).map(ReadLocalTargetNodeGen::create);
    Supplier<Optional<ExpressionNode>> constructor =
        () -> globalScope.getConstructor(name).map(ConstructorNode::new);
    Supplier<Optional<ExpressionNode>> globalFun =
        () -> globalScope.getGlobalCallTarget(name).map(ReadGlobalTargetNode::new);

    return Stream.of(localNode, constructor, globalFun)
        .map(Supplier::get)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst()
        .orElseThrow(() -> new VariableDoesNotExistException(name));
  }

  public ExpressionNode processFunctionBody(
      List<AstArgDefinition> arguments, List<AstExpression> statements, AstExpression retValue) {

    ArgDefinitionFactory argFactory =
        new ArgDefinitionFactory(scope, language, scopeName, globalScope);
    List<ArgumentDefinitionNode> argNodes;

    for (int i = 0; i < arguments.size(); i++) {
      ArgumentDefinitionNode arg = arguments.get(i).visit(argFactory, i);
//      FrameSlot slot = scope.createVarSlot()
    }

    // TODO [AA] Fixme
    return null;
    //    List<ExpressionNode> argRewrites = new ArrayList<>();
    //    for (int i = 0; i < arguments.size(); i++) {
    //      FrameSlot slot = scope.createVarSlot(arguments.get(i));
    //      ReadArgumentNode readArg = new ReadArgumentNode(i);
    //      AssignmentNode assignArg = AssignmentNodeGen.create(readArg, slot);
    //      argRewrites.add(assignArg);
    //    }
    //
    //    List<ExpressionNode> statementNodes =
    //        statements.stream().map(stmt -> stmt.visit(this)).collect(Collectors.toList());
    //    List<ExpressionNode> allStatements = new ArrayList<>();
    //    allStatements.addAll(argRewrites);
    //    allStatements.addAll(statementNodes);
    //    ExpressionNode expr = retValue.visit(this);
    //    FunctionBodyNode functionBodyNode =
    //        new FunctionBodyNode(allStatements.toArray(new ExpressionNode[0]), expr);
    //    RootNode rootNode =
    //        new EnsoRootNode(
    //            language, scope.getFrameDescriptor(), functionBodyNode, null, "lambda::" +
    // scopeName);
    //    RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
    //    return new CreateFunctionNode(callTarget);
  }

  @Override
  public ExpressionNode visitFunction(
      List<AstArgDefinition> arguments, List<AstExpression> statements, AstExpression retValue) {
    ExpressionFactory child = createChild(currentVarName);
    ExpressionNode fun = child.processFunctionBody(arguments, statements, retValue);
    fun.markTail();
    return fun;
  }

  @Override
  public ExpressionNode visitApplication(AstExpression function, List<AstExpression> arguments) {
    return InvokeNodeGen.create(
        arguments.stream().map(arg -> arg.visit(this)).toArray(ExpressionNode[]::new),
        function.visit(this));
  }

  @Override
  public ExpressionNode visitNamedCallArg(String name, AstExpression value) {
    // TODO [AA] These need to actually be built into the call arguments.
    return null;
  }

  @Override
  public ExpressionNode visitIf(AstExpression cond, AstExpression ifTrue, AstExpression ifFalse) {
    return new IfZeroNode(cond.visit(this), ifTrue.visit(this), ifFalse.visit(this));
  }

  @Override
  public ExpressionNode visitIgnore(String name) {
    // TODO [AA] This needs to actually become a call expression once currying
    // is a thing.
    return null;
  }

  @Override
  public ExpressionNode visitAssignment(String varName, AstExpression expr) {
    currentVarName = varName;
    FrameSlot slot = scope.createVarSlot(varName);
    return AssignmentNodeGen.create(expr.visit(this), slot);
  }

  @Override
  public ExpressionNode visitPrint(AstExpression body) {
    return new PrintNode(body.visit(this));
  }

  @Override
  public ExpressionNode visitCaseFunction(
      List<AstArgDefinition> arguments, List<AstExpression> statements, AstExpression retValue) {
    return null;
    //    ExpressionFactory child = createChild(currentVarName);
    //    ExpressionNode fun = child.processFunctionBody(arguments, statements, retValue);
    //    return fun;
  }

  @Override
  public ExpressionNode visitMatch(
      AstExpression target, List<AstCase> branches, Optional<AstCaseFunction> fallback) {
    ExpressionNode targetNode = target.visit(this);
    CaseNode[] cases =
        branches.stream()
            .map(
                branch ->
                    new ConstructorCaseNode(
                        branch.cons().visit(this), branch.function().visit(this)))
            .toArray(CaseNode[]::new);
    CaseNode fallbackNode =
        fallback
            .map(fb -> (CaseNode) new FallbackNode(fb.visit(this)))
            .orElseGet(DefaultFallbackNode::new);

    return new MatchNode(targetNode, cases, fallbackNode);
  }
}
